package me.edgan.redditslide.util

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.min
import me.edgan.redditslide.Authentication
import me.edgan.redditslide.SettingValues
import net.dean.jraw.RedditClient
import net.dean.jraw.http.NetworkException
import net.dean.jraw.models.Listing
import net.dean.jraw.models.Thing

/** The same ceiling as Load More Comments, which leaves a slot of OkHttp's 5 per host for votes. */
const val LOOKUP_MAX_IN_FLIGHT: Int = 4

/**
 * One answer from Reddit. A non-2xx status is an answer like any other here: [json] is whatever
 * JSON came with it, null for the HTML block page, and [error] is what to report. A [code] of 0
 * means no answer came back at all.
 */
class LookupResponse
@JvmOverloads
constructor(
    @JvmField val code: Int,
    @JvmField val json: JsonNode?,
    @JvmField val error: Exception? = null,
) {
    val isSuccessful: Boolean
        get() = code in 200..299 && json != null

    /** A rate limit, a server error or no answer at all: asking again later may well work. */
    val isTransient: Boolean
        get() = code == 429 || code >= 500 || code <= 0

    /** This answer as an exception to throw. */
    fun failure(): Exception = error ?: IOException("HTTP $code")
}

/** How a lookup by id uses `/api/info`. */
enum class InfoMode {
    /** Ask `/api/info`; on a 403, remember that for the process and fetch each thing on its own. */
    FIRST,

    /** Ask `/api/info` and nothing else, whatever it answers. */
    ONLY,

    /** Never ask `/api/info`; fetch each thing on its own. */
    NEVER,
}

/** The `/api/info` request for a comma-joined list of fullnames. */
fun interface InfoRequest {
    fun fetch(fullnames: String): LookupResponse
}

/** One thing's own request, or null when it cannot be fetched on its own, which costs no request. */
fun interface ItemFetcher<T> {
    fun fetch(thing: T): LookupResponse?
}

/** The thing's child object (`kind` and `data`) out of its own response, or null to leave it out. */
fun interface ItemParser<T> {
    fun parse(thing: T, json: JsonNode): JsonNode?
}

/**
 * Looks things up by id, answering with a listing shaped like `/api/info`'s whichever way the
 * things were fetched, so every parser written for `/api/info` keeps working.
 *
 * Reddit answers every form of `/api/info` with a 403 block page for some client ids, RedReader's
 * among them, while each post's and comment's own listing still answers. Blocks, so it has to run
 * off the main thread.
 *
 * @param blocked set once `/api/info` has answered [InfoMode.FIRST] with a 403
 */
class ThingLookup<T>(
    private val mode: InfoMode,
    private val blocked: AtomicBoolean,
    private val info: InfoRequest,
    private val fullname: (T) -> String,
    private val fetcher: ItemFetcher<T>,
    private val parser: ItemParser<T>,
) {
    /** @param things in display order, which the children keep */
    fun lookup(things: List<T>): LookupResponse {
        // Old callers sent `id=` with nothing after it; there is nothing to ask.
        if (things.isEmpty()) {
            return listingOf(emptyList())
        }
        when (mode) {
            InfoMode.ONLY -> return askInfo(things)
            InfoMode.FIRST ->
                if (!blocked.get()) {
                    val answer = askInfo(things)
                    // Only the block itself. A timeout or a 5xx says nothing about it, and
                    // fetching one by one for the rest of the process on the strength of one would
                    // cost many times the requests for nothing.
                    if (answer.code != 403) {
                        return answer
                    }
                    blocked.set(true)
                }
            InfoMode.NEVER -> {}
        }
        return perItem(things)
    }

    private fun askInfo(things: List<T>): LookupResponse =
        info.fetch(things.joinToString(",") { fullname(it) })

    private fun perItem(things: List<T>): LookupResponse {
        val answers = fetchAll(things) ?: return LookupResponse(0, null, InterruptedException())
        answers.firstOrNull { it != null && it.isTransient }?.let {
            return it
        }

        val children = ArrayList<JsonNode>()
        var refusal: LookupResponse? = null
        for (i in things.indices) {
            val answer = answers[i] ?: continue
            val json = answer.json
            if (answer.isSuccessful && json != null) {
                parser.parse(things[i], json)?.let { children.add(it) }
            } else if (answer.code != 404 && refusal == null) {
                refusal = answer
            }
        }
        // `/api/info` leaves out what it cannot return, a post in a private subreddit say, and so
        // does this. But when nothing at all came back and something was refused, the block has
        // most likely reached the listings too, and saying so beats an empty list.
        if (children.isEmpty() && refusal != null) {
            return refusal
        }
        return listingOf(children)
    }

    /**
     * Each thing's answer, in input order, with at most [LOOKUP_MAX_IN_FLIGHT] in flight. Things are
     * claimed in order and a transient failure stops further claims, so the caller's retry asks
     * again rather than this spending the rate limit on a page that will be thrown away. A slot is
     * null for a thing never asked. Null overall when interrupted.
     */
    private fun fetchAll(things: List<T>): List<LookupResponse?>? {
        val results = AtomicReferenceArray<LookupResponse>(things.size)
        val next = AtomicInteger()
        val stopped = AtomicReference<LookupResponse>()
        val workers = min(LOOKUP_MAX_IN_FLIGHT, things.size)
        val pool = Executors.newFixedThreadPool(workers)
        try {
            pool.invokeAll(
                List(workers) {
                    Callable {
                        while (stopped.get() == null) {
                            val i = next.getAndIncrement()
                            if (i >= things.size) {
                                break
                            }
                            val answer = fetcher.fetch(things[i]) ?: continue
                            results.set(i, answer)
                            if (answer.isTransient) {
                                stopped.compareAndSet(null, answer)
                            }
                        }
                    }
                }
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return null
        } finally {
            pool.shutdownNow()
        }
        return List(things.size) { results.get(it) }
    }

    companion object {
        /** An `/api/info`-shaped listing of [children]. */
        @JvmStatic
        fun listingOf(children: List<JsonNode>): LookupResponse {
            val root = JsonNodeFactory.instance.objectNode()
            root.put("kind", "Listing")
            val data = root.putObject("data")
            data.putArray("children").addAll(children)
            data.putNull("after")
            return LookupResponse(200, root)
        }
    }
}

/**
 * A post or comment to look up.
 *
 * @param fullname `t3_...` or `t1_...`
 * @param postId a comment's post, with or without `t3_`. A comment cannot be fetched without it:
 *   Reddit answers `/comments/<commentId>` with a 404.
 */
class ThingRef(@JvmField val fullname: String, postId: String?) {
    /** Bare id36, or null when not known. */
    @JvmField val postId: String? = postId?.removePrefix("t3_")?.takeIf { it.isNotEmpty() }

    private val id: String = fullname.substringAfter('_')

    /** The path that fetches this thing alone, or null when there is none. */
    fun path(): String? =
        when {
            !ID36.matches(id) -> null
            fullname.startsWith("t3_") -> "/comments/$id"
            fullname.startsWith("t1_") && postId != null && ID36.matches(postId) ->
                "/comments/$postId/_/$id"
            else -> null
        }

    internal fun isComment(): Boolean = fullname.startsWith("t1_")

    internal fun id(): String = id

    private companion object {
        val ID36 = Regex("[0-9a-z]+")
    }
}

/** [ThingLookup] for posts and comments through [Authentication.reddit], the one client there is. */
object RedditLookups {
    /**
     * Set the first time `/api/info` answers an anonymous lookup with a 403, and from then on for
     * the life of the process. Shared by every anonymous lookup: posts, comments and the random
     * subreddit check.
     */
    @JvmField val anonymousInfoBlocked = AtomicBoolean()

    /**
     * Whoever is signed in now. The anonymous account tries `/api/info` and remembers a block; an
     * account goes by the setting, read here, at the moment of use.
     */
    @JvmStatic
    fun currentMode(): InfoMode = if (Authentication.isLoggedIn) loggedInMode() else InfoMode.FIRST

    /** On: `/api/info` alone, as before Reddit's block, and failing while it lasts. Off: per item. */
    @JvmStatic
    fun loggedInMode(): InfoMode =
        if (SettingValues.useOldApiInfo()) InfoMode.ONLY else InfoMode.NEVER

    /** [refs] through [Authentication.reddit] in [currentMode]. */
    @JvmStatic
    fun lookup(refs: List<ThingRef>): LookupResponse {
        val client =
            Authentication.reddit
                ?: return LookupResponse(0, null, IllegalStateException("No Reddit client"))
        return lookup(client, refs, currentMode())
    }

    @JvmStatic
    fun lookup(client: RedditClient, refs: List<ThingRef>, mode: InfoMode): LookupResponse =
        ThingLookup<ThingRef>(
                mode,
                anonymousInfoBlocked,
                { ids -> get(client, "/api/info", mapOf("id" to ids)) },
                { it.fullname },
                { ref -> fetchOne(client, ref) },
                { ref, json -> parse(ref, json) },
            )
            .lookup(refs)

    /** [lookup] as the [Listing] `RedditClient.get(fullnames)` used to give. */
    @JvmStatic
    @Throws(Exception::class)
    fun things(refs: List<ThingRef>): Listing<Thing> = toListing(lookup(refs))

    /**
     * The one thing [ref] names, as `RedditClient.get(fullname).get(0)` gave it. Throws only
     * unchecked exceptions, as that did, so the callers' handling still applies.
     */
    @JvmStatic
    fun thing(ref: ThingRef): Thing {
        val listing =
            try {
                toListing(lookup(listOf(ref)))
            } catch (e: RuntimeException) {
                throw e
            } catch (e: Exception) {
                throw IllegalStateException(e)
            }
        return listing.firstOrNull() ?: throw NoSuchElementException(ref.fullname)
    }

    @JvmStatic
    @Throws(Exception::class)
    fun toListing(answer: LookupResponse): Listing<Thing> {
        val json = answer.json
        if (!answer.isSuccessful || json == null) {
            throw answer.failure()
        }
        return Listing(json.path("data"), Thing::class.java)
    }

    /**
     * One GET as a [LookupResponse], never thrown.
     *
     * Leaves out `raw_json=1`, as `/api/info` through JRAW always did: everything that renders a
     * post or comment expects Reddit's html-escaped fields.
     */
    @JvmStatic
    fun get(client: RedditClient, path: String, query: Map<String, String>): LookupResponse =
        try {
            val response = client.execute(client.request().path(path).query(query).get().build())
            LookupResponse(response.statusCode, response.json)
        } catch (e: NetworkException) {
            LookupResponse(e.response.statusCode, e.response.json, e)
        } catch (e: Exception) {
            // RedditClient gives up on a run of 5xx with an IllegalStateException around the last.
            val cause = e.cause
            if (cause is NetworkException) {
                LookupResponse(cause.response.statusCode, cause.response.json, e)
            } else {
                LookupResponse(0, null, e)
            }
        }

    private fun fetchOne(client: RedditClient, ref: ThingRef): LookupResponse? {
        val path = ref.path() ?: return null
        // limit=1 keeps the comment tree out: one sample post was 61 KB against 365 KB.
        val query =
            if (ref.isComment()) mapOf("limit" to "1", "context" to "0")
            else mapOf("limit" to "1")
        return get(client, path, query)
    }

    @JvmStatic
    fun parse(ref: ThingRef, json: JsonNode): JsonNode? =
        if (ref.isComment()) commentChild(json, ref.id()) else postChild(json, ref.id())

    /** The post in a `/comments/<id>` answer: the first listing's only child. */
    @JvmStatic
    fun postChild(json: JsonNode, id: String): JsonNode? {
        val child = json.path(0).path("data").path("children").path(0)
        if (child.path("kind").asText() != "t3" || child.path("data").path("id").asText() != id) {
            return null
        }
        return child
    }

    /**
     * The comment in a `/comments/<postId>/_/<id>` answer, the second listing's only child, made to
     * look like `/api/info`'s: no replies, since the focused thread carries the first one and
     * `/api/info` never did, and the post's title, which the Saved list shows.
     */
    @JvmStatic
    fun commentChild(json: JsonNode, id: String): JsonNode? {
        val found = json.path(1).path("data").path("children").path(0)
        if (found.path("kind").asText() != "t1" || found.path("data").path("id").asText() != id) {
            return null
        }
        val child = found.deepCopy<JsonNode>()
        val data = child.path("data") as? ObjectNode ?: return null
        data.put("replies", "")
        val linkTitle = data.get("link_title")
        if (linkTitle == null || linkTitle.isNull) {
            val title = json.path(0).path("data").path("children").path(0).path("data").get("title")
            if (title != null && !title.isNull) {
                data.set<JsonNode>("link_title", title)
            }
        }
        return child
    }
}
