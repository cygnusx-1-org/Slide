package me.edgan.redditslide.Random

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.Channels
import java.util.concurrent.ThreadLocalRandom
import me.edgan.redditslide.Reddit
import me.edgan.redditslide.util.LogUtil
import me.edgan.redditslide.util.NetworkUtil
import okhttp3.Request
import okhttp3.ResponseBody

/**
 * The on-disk half of the random-subreddit feature: fetching, caching and sampling the two hosted
 * name lists.
 *
 * Two sources, in order: a copy downloaded from the git repository `cygnusx-1-org/subreddit-lists`
 * (branch `master`) into the app cache directory, and — when there is no downloaded copy yet — the
 * build's own bundled asset. The download always wins once it exists, so a list update reaches
 * users by a push to that repository rather than by an app release, while the bundled copy makes
 * the very first pick instant and works with no network at all.
 *
 * Bundling costs roughly 2 MB of APK, and buys away the one bad first-run experience the
 * download-only design had: raw.githubusercontent.com is occasionally slow, and a user tapping
 * r/random for the first time would sit and wait on a 4.4 MB transfer before anything happened.
 * The assets in `src/main/assets` are symlinks into the sibling repo, and a Gradle check fails the
 * build if either dangles — see `verifyRandomSubredditAssets` in app/build.gradle.
 *
 * The names from either source are only *plausible* candidates. The list is an archive and archived
 * liveness decays fast — a sixth of it was already banned when it was last measured — so nothing
 * read out of this object is usable until [RandomSubreddits] has checked it against `/api/info`.
 * That is also why a stale bundled copy is harmless: correctness comes from the check at use, not
 * from the list being current.
 */
internal object RandomSubredditList {

    private const val BASE_URL =
        "https://raw.githubusercontent.com/cygnusx-1-org/subreddit-lists/master/"

    private const val USER_AGENT = "Slide random subreddits"

    private const val PREFS = "RANDOMLISTS"

    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Flavours with a download in flight. Guarded by itself.
     *
     * Two threads can now reach [download] for the same flavour: the app-open pass and the one
     * fired off a pick. They would open the same `.tmp` file, truncating it under each other and
     * interleaving 64 KB blocks, and whichever renamed last would publish the result as the cached
     * list. Nothing would crash — the spliced names simply fail the `/api/info` check — so it would
     * surface only as a flavour that quietly stopped producing subreddits until the next refresh.
     */
    private val downloading: MutableSet<Flavour> = java.util.EnumSet.noneOf(Flavour::class.java)

    /** Which of the two hosted lists to sample from. */
    enum class Flavour(val fileName: String) {
        SFW("subreddits-sfw.txt"),
        NSFW("subreddits-nsfw.txt");

        val url: String
            get() = BASE_URL + fileName

        fun key(suffix: String): String = "${fileName}_$suffix"
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, 0)

    private fun cacheFile(context: Context, flavour: Flavour): File =
        File(File(context.applicationContext.cacheDir, "random-subreddits"), flavour.fileName)

    private fun isCached(context: Context, flavour: Flavour): Boolean {
        val file = cacheFile(context, flavour)
        return file.exists() && file.length() > 0
    }

    /** Whether a downloaded copy exists, i.e. whether the bundled asset has been superseded. */
    fun hasDownloadedCopy(context: Context, flavour: Flavour): Boolean = isCached(context, flavour)

    /**
     * Runs [block] against a list source whose passes are guaranteed to see the same bytes, from a
     * downloaded copy if there is one and otherwise from the bundled asset.
     *
     * The source is handed over as a factory rather than a stream because the list is read twice —
     * once to count lines, once to pull out the chosen ones — and neither source can be rewound
     * from the outside.
     *
     * The guarantee is the point. Reopening the cache file by path between those two passes can
     * land on two different files: a refresh renames a freshly downloaded list over that path, and
     * the two lists differ by a day of subreddits being created and banned. Counting one and
     * reading the other silently drops every index past the shorter file's end, so a batch comes
     * back short for no visible reason.
     */
    private fun <T> withSource(
        context: Context,
        flavour: Flavour,
        downloaded: Boolean,
        block: (() -> InputStream) -> T,
    ): T {
        // An asset is fixed for the life of the build, so reopening it is already consistent and
        // there is nothing to pin.
        val assets = context.applicationContext.assets
        val fromAsset = { block { assets.open(flavour.fileName) } }
        if (!downloaded) return fromAsset()

        // Open before committing to the downloaded copy. Android clears the cache directory
        // whenever it likes, including between the check that found the file and this line, and
        // failing the pick there would waste the one thing bundling buys: something to read no
        // matter what. Caught around the open alone, so a block that has already started running
        // is never run a second time.
        val pinned =
            try {
                FileInputStream(cacheFile(context, flavour))
            } catch (e: FileNotFoundException) {
                LogUtil.e(e, "RandomSubredditList: cache vanished for $flavour, using the asset")
                null
            } ?: return fromAsset()

        return pinned.use { withPinnedStream(it, block) }
    }

    /**
     * Opens [file] once and rewinds between passes, so every pass reads the file that was there
     * when the first one started.
     *
     * A rename replaces the directory entry, not the open description underneath it, so a
     * descriptor taken before the rename keeps reading the original bytes. Reopening by path after
     * the rename would not.
     */
    @VisibleForTesting
    internal fun <T> withPinnedFile(file: File, block: (() -> InputStream) -> T): T =
        FileInputStream(file).use { pinned -> withPinnedStream(pinned, block) }

    /** [withPinnedFile] over a descriptor the caller already holds and will close. */
    private fun <T> withPinnedStream(pinned: FileInputStream, block: (() -> InputStream) -> T): T {
        val channel = pinned.channel
        return block {
            channel.position(0L)
            // The passes close what they are given; the descriptor stays the caller's, or the
            // second pass would find it already shut.
            NonClosingInputStream(Channels.newInputStream(channel))
        }
    }

    /** A stream whose close() is a no-op, so a reader cannot shut a descriptor it does not own. */
    private class NonClosingInputStream(source: InputStream) : FilterInputStream(source) {
        override fun close() = Unit
    }

    /**
     * Picks up to [count] distinct names at random, from the downloaded copy if there is one and
     * otherwise from the bundled asset. Blocking on I/O but never on the network — background
     * threads only.
     *
     * Nothing is downloaded here. The bundled asset means a pick always has something to read, so
     * making the user wait on a transfer before the first random subreddit appears buys nothing;
     * [refreshIfDue] replaces the bundle in the background instead.
     */
    fun pickNames(context: Context, flavour: Flavour, count: Int): List<String> {
        return try {
            // Resolve the source once and hold it open across both passes, so the count and the
            // contents always come from the same list.
            //
            // The count is taken fresh every time rather than remembered. Remembering it needs the
            // stored number to be tied to the identity of the file it was counted from, because a
            // refresh replaces that file underneath: a read already in flight would count the old
            // list, write that number back after the refresh had published the new one, and leave
            // the two mismatched until the next download a day later — indices from a longer list
            // falling past the end of a shorter one, silently returning a short batch. Counting is
            // a sequential scan the read is about to do anyway, and the pool means it happens once
            // per hundred or so picks, so paying it beats keeping two things in step.
            val downloaded = isCached(context, flavour)
            withSource(context, flavour, downloaded) { open ->
                val lines = countLines(open)
                if (lines <= 0) emptyList() else readLines(open, pickIndices(lines, count))
            }
        } catch (e: IOException) {
            LogUtil.e(e, "RandomSubredditList.pickNames failed for $flavour")
            emptyList()
        }
    }

    /**
     * Conditional re-check against the hosted list, when [RandomUpdatePolicy] says one is due.
     * Blocking — background threads only.
     *
     * This is the only thing that ever downloads, and it is what makes the hosted list override the
     * bundled asset: the first successful run writes a copy into the cache directory, and [open]
     * prefers that copy from then on.
     *
     * It does not check whether a download already exists — a flavour still on its bundled asset is
     * exactly the one most worth refreshing. Deciding *when* that is worth several megabytes is the
     * caller's job: [RandomSubreddits.refreshCachedLists] runs on app open and only for flavours
     * already downloaded, so a user who never opens a random subreddit never fetches anything.
     */
    fun refreshIfDue(context: Context, flavour: Flavour) {
        val prefs = prefs(context)

        // Whether a downloaded copy exists is part of the decision: a recorded success with no
        // file behind it describes a cache the system evicted, so the daily wait is meaningless.
        // A recorded failure still backs off — see RandomUpdatePolicy.
        val due =
            RandomUpdatePolicy.isDue(
                prefs.getLong(flavour.key("success"), 0L),
                prefs.getLong(flavour.key("attempt"), 0L),
                System.currentTimeMillis(),
                isCached(context, flavour),
            )
        if (!due) return

        // Not an attempt: recording one here would push the retry an hour out for a check that
        // never left the device.
        if (!NetworkUtil.isConnected(context)) return

        synchronized(downloading) {
            // Someone else is already fetching this one; a second transfer would only fight it.
            if (!downloading.add(flavour)) return
        }
        try {
            download(context, flavour)
        } finally {
            synchronized(downloading) { downloading.remove(flavour) }
        }
    }

    /**
     * One conditional GET. `304` and `200` both count as success; everything else — including a
     * `404`, which can be transient mid-push — is a failure and retries in an hour.
     *
     * @return true when a usable cache file exists afterwards
     */
    private fun download(context: Context, flavour: Flavour): Boolean {
        val client = Reddit.client ?: return false
        val prefs = prefs(context)
        val target = cacheFile(context, flavour)
        val etag = prefs.getString(flavour.key("etag"), "").orEmpty()

        val builder = Request.Builder().url(flavour.url).header("User-Agent", USER_AGENT)
        if (etag.isNotEmpty() && isCached(context, flavour)) {
            // Opaque: raw.githubusercontent.com's ETag looks like a SHA-256 of the file but is
            // neither that nor the git blob hash. Echo it back, never recompute it.
            builder.header("If-None-Match", etag)
        }

        return try {
            client.newCall(builder.build()).execute().use { response ->
                when {
                    response.code == 304 && isCached(context, flavour) -> {
                        markSuccess(prefs, flavour)
                        true
                    }
                    !response.isSuccessful -> {
                        markAttempt(prefs, flavour)
                        false
                    }
                    else -> {
                        if (!writeCache(target, response.body)) {
                            markAttempt(prefs, flavour)
                            false
                        } else {
                            prefs
                                .edit()
                                .putString(flavour.key("etag"), response.header("ETag").orEmpty())
                                .apply()
                            markSuccess(prefs, flavour)
                            true
                        }
                    }
                }
            }
        } catch (e: IOException) {
            LogUtil.e(e, "RandomSubredditList.download failed for $flavour")
            markAttempt(prefs, flavour)
            false
        }
    }

    /**
     * Streams the body to a sibling temp file and renames it over the target, so an interrupted
     * download cannot leave a half-written list that later reads would sample from.
     */
    private fun writeCache(target: File, body: ResponseBody): Boolean {
        val directory = target.parentFile
        if (directory != null && !directory.exists() && !directory.mkdirs()) return false

        val temp = File(target.path + ".tmp")
        try {
            body.byteStream().use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output, BUFFER_SIZE) }
            }
        } catch (e: IOException) {
            LogUtil.e(e, "RandomSubredditList.writeCache failed for ${target.name}")
            temp.delete()
            return false
        }

        if (temp.length() == 0L) {
            temp.delete()
            return false
        }

        // Rename straight over the target rather than deleting it first. rename(2) replaces an
        // existing destination atomically, so the cache is never briefly absent — deleting first
        // opens a window in which a concurrent read (the app-open refresh landing while a pick is
        // in flight) opens a file that does not exist and fails the pick. It also means a rename
        // that does fail leaves the previous list intact instead of destroying it.
        if (!temp.renameTo(target)) {
            temp.delete()
            return false
        }
        return true
    }

    private fun markSuccess(prefs: SharedPreferences, flavour: Flavour) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(flavour.key("success"), now).putLong(flavour.key("attempt"), now).apply()
    }

    private fun markAttempt(prefs: SharedPreferences, flavour: Flavour) {
        prefs.edit().putLong(flavour.key("attempt"), System.currentTimeMillis()).apply()
    }

    @VisibleForTesting
    internal fun countLines(open: () -> InputStream): Int {
        var lines = 0
        var lastByte = -1
        val buffer = ByteArray(BUFFER_SIZE)

        BufferedInputStream(open(), BUFFER_SIZE).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                for (i in 0 until read) {
                    if (buffer[i] == NEWLINE) lines++
                }
                if (read > 0) lastByte = buffer[read - 1].toInt()
            }
        }

        // A final name with no trailing newline.
        if (lastByte != -1 && lastByte != NEWLINE.toInt()) lines++
        return lines
    }

    /** [count] distinct line indices in `[0, lineCount)`, ascending. */
    @VisibleForTesting
    internal fun pickIndices(lineCount: Int, count: Int): IntArray {
        val wanted = minOf(count, lineCount)
        val chosen = HashSet<Int>(wanted * 2)
        val random = ThreadLocalRandom.current()
        while (chosen.size < wanted) {
            chosen.add(random.nextInt(lineCount))
        }
        return chosen.toIntArray().also { it.sort() }
    }

    /**
     * Reads only the requested lines. The file is scanned once as bytes and nothing is materialised
     * except the names asked for — turning all 328k names into Strings is avoidable, and worth
     * avoiding on low-end devices.
     *
     * @param indices ascending, distinct line indices
     */
    @VisibleForTesting
    internal fun readLines(open: () -> InputStream, indices: IntArray): List<String> {
        val names = ArrayList<String>(indices.size)
        if (indices.isEmpty()) return names

        val line = ByteArrayOutputStream(32)
        val buffer = ByteArray(BUFFER_SIZE)
        var wanted = 0
        var lineIndex = 0

        BufferedInputStream(open(), BUFFER_SIZE).use { input ->
            while (wanted < indices.size) {
                val read = input.read(buffer)
                if (read == -1) break
                for (i in 0 until read) {
                    val b = buffer[i]
                    if (b == NEWLINE) {
                        if (lineIndex == indices[wanted]) {
                            addName(names, line)
                            line.reset()
                            wanted++
                            if (wanted == indices.size) break
                        }
                        lineIndex++
                    } else if (lineIndex == indices[wanted]) {
                        line.write(b.toInt())
                    }
                }
            }
        }

        // The last line of a file that does not end in a newline.
        if (wanted < indices.size && line.size() > 0) addName(names, line)
        return names
    }

    private fun addName(names: MutableList<String>, line: ByteArrayOutputStream) {
        val name = line.toString(Charsets.UTF_8.name()).trim()
        if (name.isNotEmpty()) names.add(name)
    }

    private const val NEWLINE: Byte = '\n'.code.toByte()
}
