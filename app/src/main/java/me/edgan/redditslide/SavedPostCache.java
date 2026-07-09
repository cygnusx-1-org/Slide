package me.edgan.redditslide;

import android.content.Context;
import android.content.SharedPreferences;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.edgan.redditslide.util.LogUtil;

import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;

/**
 * Hard-TTL cache for the Profile "Saved" tab -- server-side saved posts AND comments. The whole
 * ordered list is stored as one JSON blob (a Jackson array of each item's data node) in a file in
 * the cache dir, with a freshness timestamp + "complete" flag in {@code SharedPreferences}. Storing
 * the list as a single blob (rather than one file per fullname) means it can't be half-evicted by
 * the OS and it preserves saved comments, not just submissions.
 *
 * <p>Within {@link #SAVED_CACHE_TTL_MS} the cached list is served with no network call; past it the
 * caller refetches. In-app save/unsave and account switches call {@link #invalidate()}. The cache
 * is keyed by username so one account never sees another's saved items even before invalidation.
 */
public class SavedPostCache {

    /** How long a cached saved list is trusted before a refetch is required. */
    public static final long SAVED_CACHE_TTL_MS = 30 * 60 * 1000L; // 30 minutes

    private static final String PREFS = "savedcache";
    private static final String SUFFIX_TIME = ".t"; // cachedAt, millis (long)
    private static final String SUFFIX_COMPLETE = ".c"; // whole saved list captured (boolean)
    private static final String BLOB_PREFIX = "savedcache_"; // blob file name prefix in the cache dir

    /** Shared across threads; {@link ObjectMapper} is thread-safe once configured. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * All prefs mutations (stamps and clears) and blob writes run here, one at a time, so two
     * {@link #store} calls -- or a store racing an {@link #invalidate} -- can never interleave.
     * Dedicated daemon thread (not the shared AsyncTask serial executor) so it neither blocks nor
     * is blocked by unrelated background work.
     */
    private static final ExecutorService WRITER =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "SavedPostCache-writer");
                        t.setDaemon(true);
                        return t;
                    });

    /**
     * Wall-clock time of the last {@link #invalidate()}, set synchronously so reads reject any
     * entry stamped at-or-before it immediately (no waiting on the async prefs clear) and a store
     * dispatched before it is never allowed to stamp fresh.
     */
    private static volatile long invalidatedAt = 0L;

    private SavedPostCache() {}

    private static SharedPreferences prefs() {
        return Reddit.getAppContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(String username, String category) {
        return (username == null ? "" : username) + "|" + (category == null ? "" : category);
    }

    /** Filename-safe blob name (hex of the key, so arbitrary category names can't collide). */
    private static String blobName(String key) {
        StringBuilder sb = new StringBuilder(BLOB_PREFIX);
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static File blobFile(String key) {
        return new File(OfflineSubreddit.getCacheDirectory(Reddit.getAppContext()), blobName(key));
    }

    /** Result of a successful {@link #load}. */
    public static class Cached {
        public final ArrayList<Contribution> posts;
        public final long cachedAt;
        public final boolean complete;

        Cached(ArrayList<Contribution> posts, long cachedAt, boolean complete) {
            this.posts = posts;
            this.cachedAt = cachedAt;
            this.complete = complete;
        }
    }

    /** True if a cache entry for this key exists, post-dates the last invalidate, and is unexpired. */
    public static boolean isFresh(String username, String category) {
        long t = prefs().getLong(key(username, category) + SUFFIX_TIME, 0L);
        return t > invalidatedAt && (System.currentTimeMillis() - t) < SAVED_CACHE_TTL_MS;
    }

    /**
     * Rebuild the cached saved list from its JSON blob. Reads a file, so it MUST be called off the
     * main thread. Returns {@code null} on any miss (no/invalidated entry, blob gone, or a parse
     * failure), so a partial or corrupt list is never served. Parses straight from the file (UTF-8)
     * rather than slurping it into a String first.
     */
    public static Cached load(String username, String category) {
        String k = key(username, category);
        SharedPreferences prefs = prefs();
        long t = prefs.getLong(k + SUFFIX_TIME, 0L);
        if (t <= invalidatedAt) { // also covers "no entry" (t == 0)
            return null;
        }
        boolean complete = prefs.getBoolean(k + SUFFIX_COMPLETE, false);

        File blob = blobFile(k);
        if (!blob.exists()) {
            return null;
        }
        try {
            JsonNode arr = MAPPER.readTree(blob);
            if (arr == null || !arr.isArray()) {
                return null;
            }
            ArrayList<Contribution> posts = new ArrayList<>(arr.size());
            for (JsonNode node : arr) {
                // Reconstruct by fullname kind: t1_ = comment, everything else = submission.
                String name = node.path("name").asText("");
                posts.add(name.startsWith("t1_") ? new Comment(node) : new Submission(node));
            }
            return new Cached(posts, t, complete);
        } catch (Exception e) {
            LogUtil.e(e, "SavedPostCache.load failed");
            return null;
        }
    }

    /**
     * Persist {@code posts} (submissions and comments, in order) as the saved cache for this key.
     * The write is queued on {@link #WRITER}, so this is safe to call from the main thread. The
     * store is stamped with its dispatch time, so a store that predates an {@link #invalidate} is
     * dropped. {@code complete} records whether the entire saved list was captured.
     */
    public static void store(
            String username, String category, List<Contribution> posts, boolean complete) {
        if (posts == null) {
            return;
        }
        final ArrayList<Contribution> snapshot = new ArrayList<>(posts);
        final String k = key(username, category);
        final long at = System.currentTimeMillis();
        WRITER.execute(() -> writeNow(k, snapshot, complete, at));
    }

    private static void writeNow(
            String k, ArrayList<Contribution> posts, boolean complete, long at) {
        // A later invalidate() supersedes this store; its content predates the invalidation.
        if (at <= invalidatedAt) {
            return;
        }
        ArrayNode arr = MAPPER.createArrayNode();
        for (Contribution contrib : posts) {
            if (contrib != null && contrib.getDataNode() != null) {
                arr.add(contrib.getDataNode());
            }
        }

        // Write to a temp file then atomically rename onto the blob, so a concurrent reader never
        // sees a half-written file. (Writes themselves are already serialized by WRITER.)
        File blob = blobFile(k);
        File tmp = new File(blob.getParentFile(), blob.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(tmp)) {
            writer.write(arr.toString());
        } catch (IOException e) {
            LogUtil.e(e, "SavedPostCache.writeNow failed");
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(blob)) {
            tmp.delete();
            LogUtil.e(
                    new IOException("rename failed"),
                    "SavedPostCache.writeNow could not publish blob");
            return;
        }

        // If an invalidate() landed while we were writing, don't stamp. Without a timestamp newer
        // than invalidatedAt, load()/isFresh() ignore the now-orphaned blob file.
        if (at <= invalidatedAt) {
            return;
        }
        prefs().edit()
                .putLong(k + SUFFIX_TIME, at)
                .putBoolean(k + SUFFIX_COMPLETE, complete)
                .apply();
    }

    /**
     * Drop all cached saved entries. The in-memory timestamp makes reads reject stale entries
     * immediately; the prefs clear (and any orphaned blob files it leaves behind) is cleaned up on
     * {@link #WRITER}, off the calling thread.
     */
    public static void invalidate() {
        invalidatedAt = System.currentTimeMillis();
        WRITER.execute(() -> prefs().edit().clear().apply());
    }
}
