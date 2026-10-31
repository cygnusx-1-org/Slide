package me.edgan.redditslide.util

import android.os.SystemClock
import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.max
import kotlin.math.min
import me.edgan.redditslide.Authentication
import net.dean.jraw.http.NetworkException
import net.dean.jraw.http.RestResponse
import net.dean.jraw.models.CommentSort

/** A parent with fewer ids than this is cheaper to load per-id than to list. */
const val PARENT_LISTING_MIN_IDS: Int = 5

/** `limit=100` leaves almost everything behind in a `more`; 500 returns nearly the whole subtree. */
const val PARENT_LISTING_LIMIT: Int = 500

const val MORE_COMMENTS_PAGE_SIZE: Int = 25

/** Below OkHttp's default of 5 per host, which leaves a slot for votes and the like. */
const val MORE_COMMENTS_MAX_IN_FLIGHT: Int = 4

/** There's no scroll trigger to come back for the next page, so empty ones are skipped here. */
const val MORE_COMMENTS_MAX_EMPTY_PAGES: Int = 8

const val RATE_LIMIT_RESERVE: Double = 20.0

const val RATE_LIMIT_MIN_PAUSE_MS: Long = 10_000L

/**
 * One response to a focused listing request. A non-2xx status is a response like any other here,
 * because a 429 still carries the rate-limit headers: [json] is null for those, never an attempt
 * to read an HTML block page as content.
 */
class FocusedListing
@JvmOverloads
constructor(
    @JvmField val code: Int,
    @JvmField val json: JsonNode?,
    @JvmField val remaining: String? = null,
    @JvmField val reset: String? = null,
    @JvmField val error: Exception? = null,
)

fun interface FocusedListingFetcher {
    @Throws(Exception::class)
    fun fetch(commentId: String, limit: Int?): FocusedListing
}

/**
 * Fetches through [Authentication.reddit], the one client both logged-in and anonymous accounts
 * use. Omits `raw_json=1` on purpose: the thread itself is fetched without it, and everything that
 * renders a comment expects Reddit's html-escaped fields, so a loaded reply has to match.
 */
class RedditFocusedListingFetcher(private val postId: String, private val sort: CommentSort) :
    FocusedListingFetcher {

    override fun fetch(commentId: String, limit: Int?): FocusedListing {
        val client = Authentication.reddit ?: throw IllegalStateException("Not signed in")
        val args = HashMap<String, String>()
        args["context"] = "0"
        args["sort"] = sort.name.lowercase(Locale.ENGLISH)
        if (limit != null) {
            args["limit"] = limit.toString()
        }
        return try {
            val response =
                client.execute(
                    client.request().path("/comments/$postId/_/$commentId").query(args).build()
                )
            toListing(response, response.json, null)
        } catch (e: NetworkException) {
            toListing(e.response, null, e)
        }
    }

    private fun toListing(
        response: RestResponse,
        json: JsonNode?,
        error: Exception?,
    ): FocusedListing =
        FocusedListing(
            response.statusCode,
            json,
            response.headers.get("x-ratelimit-remaining"),
            response.headers.get("x-ratelimit-reset"),
            error,
        )
}

/**
 * A load stopped because Reddit's rate limit is nearly spent, or already was.
 *
 * @param waitMs how long until loads are allowed again
 */
class RateLimitPausedException(val waitMs: Long) :
    Exception("Paused for ${waitMs}ms until Reddit's rate limit resets")

/**
 * What one open thread remembers between loads.
 *
 * @param useOldMethod read when the thread is opened, so an open thread keeps its method.
 */
class MoreCommentsSession
@JvmOverloads
constructor(
    @JvmField val useOldMethod: Boolean,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val listedParents = HashSet<String>()
    private var pausedUntil = Long.MIN_VALUE

    /** @return false if this parent was already listed, so its listing would find nothing new. */
    @Synchronized fun markListed(parentId: String): Boolean = listedParents.add(parentId)

    @Synchronized fun unmarkListed(parentId: String) {
        listedParents.remove(parentId)
    }

    /** The thread's comments were fetched again, so what they deferred is new. */
    @Synchronized fun onThreadReloaded() {
        listedParents.clear()
    }

    @Synchronized fun isPaused(): Boolean = now() < pausedUntil

    /** How long until loads are allowed again, or 0 when they are now. */
    @Synchronized fun pauseRemainingMs(): Long = if (isPaused()) pausedUntil - now() else 0L

    /** @param remaining a decimal string such as `9993.0`, so an integer parse would never match */
    @Synchronized fun onResponse(code: Int, remaining: String?, reset: String?) {
        val left = remaining?.trim()?.toDoubleOrNull()
        if (code != 429 && (left == null || left > RATE_LIMIT_RESERVE)) {
            return
        }
        val resetMs = ((reset?.trim()?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
        pausedUntil = max(pausedUntil, now() + max(resetMs, RATE_LIMIT_MIN_PAUSE_MS))
    }
}

sealed class MoreCommentsResult {
    /** The ids still to load, in thread order. */
    abstract val remainingIds: List<String>

    /**
     * @param comments the `data` nodes of the top-level comments loaded, each with its subtree
     * @param remainingCount Reddit's count for [remainingIds], when it gave one
     * @param listedParent whether this came from the parent's listing, which the session now
     *   counts as done
     */
    class Loaded(
        val comments: List<JsonNode>,
        override val remainingIds: List<String>,
        val remainingCount: Int?,
        val listedParent: Boolean = false,
    ) : MoreCommentsResult()

    /** Nothing could be loaded, which is what drives the retry UI. */
    class Failed(val cause: Exception, override val remainingIds: List<String>) :
        MoreCommentsResult()
}

/**
 * Loads hidden replies through focused comment listings, since `/api/morechildren` answers some
 * clients with a security block whatever the request looks like.
 *
 * Only reads and returns JSON, so the tree stays untouched until the caller is on the main thread.
 * Blocks, so it has to run off the main thread.
 */
class MoreCommentsLoader(
    private val session: MoreCommentsSession,
    private val fetcher: FocusedListingFetcher,
) {
    /** Reddit has no bulk route, so the top-level `more` costs one request per id. */
    fun loadTopLevel(ids: List<String>): MoreCommentsResult = loadIndividually(ids.distinct())

    /** One listing of the parent returns nearly everything, the first time it's asked. */
    fun loadReplies(parentId: String, ids: List<String>): MoreCommentsResult {
        val requested = ids.distinct()
        if (requested.size < PARENT_LISTING_MIN_IDS || !session.markListed(parentId)) {
            return loadIndividually(requested)
        }

        val outcome = fetchOne(parentId, PARENT_LISTING_LIMIT)
        if (outcome.error != null) {
            session.unmarkListed(parentId)
            return MoreCommentsResult.Failed(outcome.error, requested)
        }

        val parent = focusedComment(outcome.json, parentId) ?: return loadIndividually(requested)

        val wanted = requested.toHashSet()
        val kept = ArrayList<JsonNode>()
        val deferred = HashSet<String>()
        var deferredCount = 0
        for (reply in replies(parent)) {
            when (reply.path("kind").asText()) {
                // The listing repeats the replies already on screen.
                "t1" -> {
                    val data = reply.path("data")
                    if (data.path("id").asText() in wanted) {
                        kept.add(data)
                    }
                }
                "more" -> {
                    val more = reply.path("data")
                    more.path("children").forEach { deferred.add(it.asText()) }
                    deferredCount += more.path("count").asInt()
                }
            }
        }

        // A requested id neither returned nor deferred is a comment that's gone.
        val remaining = requested.filter { it in deferred }
        if (kept.isEmpty() && remaining.isNotEmpty()) {
            return loadIndividually(remaining)
        }
        return MoreCommentsResult.Loaded(
            kept,
            remaining,
            if (remaining.isEmpty()) null else deferredCount,
            true,
        )
    }

    private fun loadIndividually(ids: List<String>): MoreCommentsResult {
        var rest = ids
        var emptyPages = 0
        val descendants = HashSet<String>()
        while (rest.isNotEmpty()) {
            val page = rest.take(MORE_COMMENTS_PAGE_SIZE)
            val outcomes = fetchPage(page)

            val firstBad = outcomes.indexOfFirst { it == null || it.error != null }
            val finished = if (firstBad < 0) outcomes.size else firstBad
            val parsed =
                (0 until finished).mapNotNull { focusedComment(outcomes[it]?.json, page[it]) }

            val nested = HashSet<String>()
            parsed.forEach { collectDescendants(it, nested) }
            descendants.addAll(nested)
            val kept = parsed.filter { it.path("id").asText() !in nested }
            val remaining = rest.drop(finished).filter { it !in descendants }

            if (firstBad >= 0) {
                return if (kept.isEmpty()) {
                    val cause = outcomes.firstNotNullOfOrNull { it?.error }
                    MoreCommentsResult.Failed(cause ?: IOException("Load stopped"), remaining)
                } else {
                    MoreCommentsResult.Loaded(kept, remaining, null)
                }
            }
            if (kept.isNotEmpty() || ++emptyPages >= MORE_COMMENTS_MAX_EMPTY_PAGES) {
                return MoreCommentsResult.Loaded(kept, remaining, null)
            }
            rest = remaining
        }
        return MoreCommentsResult.Loaded(emptyList(), emptyList(), null)
    }

    /**
     * Fetches [ids] with a bounded number in flight, keeping results in input order. Ids are claimed
     * in order and a failure stops further claims, so every slot before the first bad one is
     * complete. A slot left null was never started.
     */
    private fun fetchPage(ids: List<String>): List<Outcome?> {
        val results = AtomicReferenceArray<Outcome>(ids.size)
        val next = AtomicInteger()
        val failed = AtomicBoolean()
        val workers = min(MORE_COMMENTS_MAX_IN_FLIGHT, ids.size)
        val pool = Executors.newFixedThreadPool(workers)
        try {
            pool.invokeAll(
                List(workers) {
                    Callable {
                        while (!failed.get()) {
                            val i = next.getAndIncrement()
                            if (i >= ids.size) {
                                break
                            }
                            val outcome = fetchOne(ids[i], null)
                            results.set(i, outcome)
                            if (outcome.error != null) {
                                failed.set(true)
                            }
                        }
                    }
                }
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            pool.shutdownNow()
        }
        return List(ids.size) { results.get(it) }
    }

    private fun fetchOne(commentId: String, limit: Int?): Outcome {
        val wait = session.pauseRemainingMs()
        if (wait > 0) {
            return Outcome(null, RateLimitPausedException(wait))
        }
        return try {
            val listing = fetcher.fetch(commentId, limit)
            session.onResponse(listing.code, listing.remaining, listing.reset)
            if (listing.code in 200..299) {
                Outcome(listing.json, null)
            } else if (listing.code == 429) {
                Outcome(null, RateLimitPausedException(session.pauseRemainingMs()))
            } else {
                Outcome(null, listing.error ?: IOException("HTTP ${listing.code}"))
            }
        } catch (e: Exception) {
            Outcome(null, e)
        }
    }

    /**
     * The focused comment's `data`, or null for a deleted comment (an empty listing, not an error)
     * and for a malformed or mismatched listing, which is dropped so one odd comment can't block
     * the rest.
     */
    private fun focusedComment(json: JsonNode?, id: String): JsonNode? {
        val children = json?.path(1)?.path("data")?.path("children")
        if (children == null || !children.isArray) {
            Log.w(LogUtil.getTag(), "Dropping unusable listing for comment $id")
            return null
        }
        if (children.isEmpty) {
            return null
        }
        val first = children.path(0)
        if (first.path("kind").asText() != "t1" || first.path("data").path("id").asText() != id) {
            Log.w(LogUtil.getTag(), "Dropping mismatched listing for comment $id")
            return null
        }
        return first.path("data")
    }

    private fun replies(comment: JsonNode): JsonNode =
        comment.path("replies").path("data").path("children")

    private fun collectDescendants(comment: JsonNode, into: MutableSet<String>) {
        for (reply in replies(comment)) {
            if (reply.path("kind").asText() == "t1") {
                into.add(reply.path("data").path("id").asText())
                collectDescendants(reply.path("data"), into)
            }
        }
    }

    private class Outcome(val json: JsonNode?, val error: Exception?)
}
