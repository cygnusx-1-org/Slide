package me.edgan.redditslide.Random

import android.content.Context
import com.fasterxml.jackson.databind.JsonNode
import java.util.ArrayDeque
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom
import me.edgan.redditslide.Authentication
import me.edgan.redditslide.Random.RandomSubredditList.Flavour
import me.edgan.redditslide.SettingValues
import me.edgan.redditslide.UserSubscriptions
import me.edgan.redditslide.util.LogUtil
import net.dean.jraw.paginators.Sorting
import net.dean.jraw.paginators.SubredditPaginator
import net.dean.jraw.paginators.TimePeriod

/**
 * Resolves `r/random`, `r/randnsfw` and `r/myrandom` to a real subreddit name.
 *
 * Reddit used to do this itself: `/r/random` redirected to a live subreddit and returned its posts,
 * and Slide read the name back off the first submission. Both endpoints now answer
 * `404 {"reason": "banned"}`, so there are no posts and no name to read. The name is therefore
 * chosen *before* anything is fetched, and the rest of the existing plumbing — `subredditRandom`,
 * `MainActivity.randomoverride`, `executeAsyncSubreddit` — carries on unchanged once one is
 * supplied.
 *
 * Candidates come from a hosted list ([RandomSubredditList]) that is an archive, so a candidate is
 * not yet a subreddit: roughly one name in six was banned by the time the list was last measured,
 * and more go private. A batch of 100 is checked against `/api/info` in a single request and the
 * survivors are pooled, so one round trip covers about 99 further picks.
 */
object RandomSubreddits {

    const val RANDOM = "random"
    const val RANDNSFW = "randnsfw"
    const val MYRANDOM = "myrandom"

    /**
     * Names per `/api/info` request. Reddit accepts a comma-separated `sr_name` list, and one round
     * trip at this size leaves enough survivors pooled that the next several picks need no network
     * at all.
     */
    private const val BATCH_SIZE = 100

    /**
     * How many batches a single pick may draw before giving up. Every name in a batch being dead is
     * essentially impossible, but an unbounded redraw on a persistently empty result would spin.
     */
    private const val MAX_BATCHES = 3

    /**
     * How many posts a candidate must return before it counts as somewhere worth landing. A
     * subreddit that answers with three posts is technically alive and still reads as a broken
     * random button, so "not empty" is too low a bar: the check asks for a screenful.
     */
    private const val MIN_POSTS = 25

    /**
     * How many candidates a single pick may audition before settling for one it could not confirm.
     * Roughly one subreddit in ten answers with an empty first page — some are genuinely empty,
     * others are Reddit answering wrong once — so a handful of tries puts a visible blank listing
     * out of reach without letting a dead network spin here.
     */
    private const val MAX_CANDIDATES = 5

    /** Validated survivors not yet handed out, per flavour. Guarded by itself. */
    private val pools: MutableMap<Flavour, ArrayDeque<String>> = EnumMap(Flavour::class.java)

    /**
     * The name [pick] last returned for each special name, for the life of the process. Guarded by
     * itself.
     *
     * A pick has to outlive the loader that made it. The over-18 interstitial's "Continue" rebuilds
     * the fragment's adapter and loader from scratch, so the resolved name is gone by the time the
     * reload asks for one — and drawing a new subreddit there would discard the consent the user
     * just gave and prompt again for a different subreddit, with no way through.
     */
    private val lastPicks: MutableMap<String, String> = HashMap()

    /** Whether [sub] is one of the three special names this object resolves. */
    @JvmStatic
    fun isRandom(sub: String?): Boolean {
        if (sub == null) return false
        return when (sub.lowercase(Locale.ENGLISH)) {
            RANDOM,
            RANDNSFW,
            MYRANDOM -> true
            else -> false
        }
    }

    /**
     * A real subreddit name for one of the special names, or null when none could be resolved.
     *
     * Blocking: it reads a cached file, may download one, and may make one API request. Background
     * threads only.
     */
    @JvmStatic
    fun pick(context: Context, specialName: String): String? {
        val name = specialName.lowercase(Locale.ENGLISH)
        val sorting = SettingValues.getSubmissionSort(name)
        val timePeriod = SettingValues.getSubmissionTimePeriod(name)

        // The first candidate drawn, kept as the answer of last resort. Something is badly wrong
        // when nothing can be confirmed — no network, an expired token — and handing back a name
        // that may be empty still beats handing back none, which resolves to the special name and
        // a 404.
        var fallback: String? = null

        for (attempt in 0 until MAX_CANDIDATES) {
            val candidate = drawCandidate(context, name) ?: break
            if (fallback == null) fallback = candidate

            // Confirmed here rather than after the listing comes back empty, so a subreddit with
            // nothing to show is never handed to the loader: the title, the sidebar and the
            // over-18 interstitial all act on the resolved name, and a redraw after any of that
            // has run is a redraw the user watches happen.
            if (hasEnoughPosts(candidate, sorting, timePeriod)) {
                synchronized(lastPicks) { lastPicks[name] = candidate }
                return candidate
            }
        }

        if (!fallback.isNullOrEmpty()) {
            synchronized(lastPicks) { lastPicks[name] = fallback }
        }
        return fallback
    }

    /** One unconfirmed candidate for [name], from whichever source that flavour draws on. */
    private fun drawCandidate(context: Context, name: String): String? =
        when (name) {
            // Guest mode, or an account with nothing subscribed, falls through to the general
            // list rather than leaving the tab dead.
            MYRANDOM -> pickFromSubscriptions(context) ?: pickValidated(context, Flavour.SFW)
            RANDNSFW -> pickValidated(context, Flavour.NSFW)
            else -> pickValidated(context, Flavour.SFW)
        }

    /**
     * Whether [name] has at least [MIN_POSTS] to show under the sort this listing will actually
     * use.
     *
     * `/api/info` proves a subreddit exists and is neither banned nor private; it says nothing
     * about whether it holds any posts, and a random pick that resolves to an empty subreddit is
     * indistinguishable from a broken feature. Asking for one page of [MIN_POSTS] is the cheapest
     * question that settles it, and asking with the listing's own sort and time period is what
     * makes the answer mean anything — a subreddit with plenty in `hot` can hold nothing at all
     * under `top` of the last hour.
     *
     * A short answer is a rejection, not a pass: Reddit fills a page up to the limit when it can,
     * so fewer than [MIN_POSTS] back means the subreddit does not have that many, and landing on
     * one with a handful of posts is the same disappointment as landing on an empty one.
     *
     * A failure counts as "no", not as "cannot tell": the request that throws here is the same
     * request the loader is about to make, so a candidate that cannot answer would have shown the
     * user an error. Drawing somebody else is free.
     */
    private fun hasEnoughPosts(name: String, sorting: Sorting, timePeriod: TimePeriod): Boolean {
        val client = Authentication.reddit ?: return true
        return try {
            val paginator = SubredditPaginator(client, name)
            paginator.setSorting(sorting)
            paginator.setTimePeriod(timePeriod)
            paginator.setLimit(MIN_POSTS)
            paginator.next().size >= MIN_POSTS
        } catch (e: Exception) {
            LogUtil.e(e, "RandomSubreddits.hasEnoughPosts failed for $name")
            false
        }
    }

    /**
     * The name [pick] last returned for this special name in this process, or null if there has not
     * been one yet.
     *
     * This is what lets a reload of a listing the user is already looking at stay on the same
     * subreddit. Only a genuinely new listing should draw again.
     */
    @JvmStatic
    fun lastPick(specialName: String): String? =
        synchronized(lastPicks) { lastPicks[specialName.lowercase(Locale.ENGLISH)] }

    /**
     * Opportunistic, app-open refresh of whichever lists are already cached. Returns immediately;
     * the check itself runs on its own thread and its failure is never user-visible.
     *
     * There is no scheduler behind this on purpose. A list a few days stale costs nothing, because
     * every name is checked against `/api/info` at the moment it is used.
     */
    @JvmStatic
    fun refreshCachedLists(context: Context) {
        val application = context.applicationContext
        refreshInBackground("random-subreddit-lists") {
            for (flavour in Flavour.entries) {
                // Only what has already been downloaded. A flavour still on its bundled asset is
                // left alone here: pulling several megabytes on every app open for a feature the
                // user may never touch is the cost the bundling exists to avoid. Using the feature
                // is what triggers that first download, from pickValidated below.
                if (RandomSubredditList.hasDownloadedCopy(application, flavour)) {
                    RandomSubredditList.refreshIfDue(application, flavour)
                }
            }
        }
    }

    /**
     * Refreshes one flavour off the pick path, so the hosted list replaces the bundled asset for
     * someone who actually uses random subreddits.
     *
     * Deliberately after the names have been read and not waited on: the bundled copy already
     * answered the pick, so there is nothing to gain by making the user watch a transfer, and the
     * `/api/info` check means a list a release out of date is not a correctness problem.
     */
    private fun refreshAfterPick(context: Context, flavour: Flavour) {
        val application = context.applicationContext
        refreshInBackground("random-subreddit-list-${flavour.name.lowercase(Locale.ENGLISH)}") {
            RandomSubredditList.refreshIfDue(application, flavour)
        }
    }

    private fun refreshInBackground(name: String, work: () -> Unit) {
        Thread(work, name).start()
    }

    /** Drains the pool, refilling it from a fresh validated batch when it runs dry. */
    private fun pickValidated(context: Context, flavour: Flavour): String? {
        synchronized(pools) {
            val pool = pools.getOrPut(flavour) { ArrayDeque() }
            var refreshFired = false
            repeat(MAX_BATCHES) {
                pool.poll()?.let { return it }

                val candidates = RandomSubredditList.pickNames(context, flavour, BATCH_SIZE)
                // Nothing readable from either the downloaded copy or the bundled asset. Redrawing
                // cannot change that.
                if (candidates.isEmpty()) return null

                // The feature is in use, so it is now worth keeping this flavour current. Once per
                // pick, not once per retry: a batch that validates to nothing loops, and firing a
                // thread on each pass would ask for the same several megabytes three times over.
                if (!refreshFired) {
                    refreshAfterPick(context, flavour)
                    refreshFired = true
                }

                pool.addAll(live(validate(candidates)))
            }
            return pool.poll()
        }
    }

    /**
     * One `GET /api/info?sr_name=...` for the whole batch. Checking names one at a time through
     * `/r/<name>/about.json` would also work and would say *why* each one is unusable, but it costs
     * a hundred times the requests to learn something a random pick does not need.
     */
    private fun validate(names: List<String>): JsonNode? {
        val client = Authentication.reddit ?: return null
        return try {
            val query = mapOf("sr_name" to names.joinToString(","), "raw_json" to "1")
            val request = client.request().path("/api/info").query(query).get().build()
            client.execute(request).json
        } catch (e: Exception) {
            LogUtil.e(e, "RandomSubreddits.validate failed")
            null
        }
    }

    /**
     * The usable subreddits in an `/api/info` response.
     *
     * A subscriber count is the signal: banned, private and deleted subreddits come back with
     * `subscribers` null, and names that no longer exist at all are simply absent from the response.
     * Absent and null are the same answer here — both mean "do not send the user there" — so only
     * what is present and counted survives.
     */
    internal fun live(root: JsonNode?): List<String> {
        val names = ArrayList<String>()
        if (root == null) return names

        val children = root.path("data").path("children")
        if (!children.isArray) return names

        for (child in children) {
            val data = child.path("data")
            if (!data.path("subscribers").isNumber) continue
            val name = data.path("display_name").asText("")
            if (name.isNotEmpty()) names.add(name)
        }

        // The response order is Reddit's, not ours; shuffling keeps consecutive picks from walking
        // one batch in whatever order the API happened to return it.
        names.shuffle()
        return names
    }

    /**
     * `r/myrandom` meant "random from my subscriptions" on Reddit. The subscription list is already
     * on the device and every subreddit in it is one the user can reach, so this needs neither the
     * hosted list nor a validation request.
     */
    private fun pickFromSubscriptions(context: Context): String? {
        val candidates =
            UserSubscriptions.getSubscriptions(context).filter { subscription ->
                val name = subscription.lowercase(Locale.ENGLISH)
                // Multireddits, domain feeds and the special names are not somewhere to land.
                name.isNotEmpty() &&
                    !UserSubscriptions.specialSubreddits.contains(name) &&
                    !name.contains('+') &&
                    !name.contains('/') &&
                    !name.contains('.')
            }
        if (candidates.isEmpty()) return null
        return candidates[ThreadLocalRandom.current().nextInt(candidates.size)]
    }
}
