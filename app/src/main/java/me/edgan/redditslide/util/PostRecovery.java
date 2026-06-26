package me.edgan.redditslide.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import me.edgan.redditslide.Reddit;
import net.dean.jraw.models.Submission;

/**
 * Recovers the original title and body of removed/deleted self-posts from the Arctic Shift archive.
 *
 * <p>When a post is removed by a moderator or deleted by its author, the Reddit API returns the
 * literal placeholder {@code [removed]} / {@code [deleted]} in {@code selftext} (and sometimes in
 * the title). Arctic Shift (arctic-shift.photon-reddit.com) is a public Reddit archive that still
 * exposes the originals by post id with no authentication.
 *
 * <p>JRAW {@link Submission} objects are immutable and the body is not cached in {@code
 * SubmissionCache}, so recovered text is held in a static map keyed by the submission fullname and
 * consulted at render time. The map is accessed from more than one thread — titles are rendered
 * from {@code SubmissionCache.cacheSubmissions} on the background feed-loading thread, while {@link
 * #store} writes from the UI thread — so it is wrapped in {@link Collections#synchronizedMap}. A
 * lock-free {@code hasRecoveries} fast-path keeps the hot title-render path off the lock until the
 * user actually recovers something. {@link #fetch} performs only the network call and touches no
 * shared state.
 */
public final class PostRecovery {

    private static final String ARCTIC_SHIFT_POSTS =
            "https://arctic-shift.photon-reddit.com/api/posts/ids?ids=";

    /** Cap on cached recoveries so a long-lived session can't grow the map without bound. */
    private static final int MAX_ENTRIES = 50;

    /** fullname ("t3_xxx") -> recovered title/body. Accessed from the UI and feed-loading threads. */
    private static final Map<String, Result> recovered =
            Collections.synchronizedMap(
                    // Insertion-order (not access-order) so reads never mutate the map.
                    new LinkedHashMap<String, Result>(16, 0.75f, false) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Result> eldest) {
                            return size() > MAX_ENTRIES;
                        }
                    });

    /**
     * True once anything has been recovered. Lets the per-title render path skip the synchronized
     * map lookup entirely in the common case where nothing has been recovered. Volatile so the
     * background feed-loading thread sees the UI thread's {@link #store} publish.
     */
    private static volatile boolean hasRecoveries = false;

    private PostRecovery() {}

    /** What {@link #fetch} pulled from the archive; either field may be null. */
    public static final class Result {
        public final String title;
        public final String body;

        Result(String title, String body) {
            this.title = title;
            this.body = body;
        }

        public boolean isEmpty() {
            return title == null && body == null;
        }
    }

    /** Whether the post's body has been removed by a moderator or deleted by its author. */
    public static boolean isRemovedOrDeleted(Submission s) {
        String b = s.getSelftext();
        return (b != null && (b.equals("[removed]") || b.equals("[deleted]")))
                || s.getBannedBy() != null;
    }

    /** Previously recovered markdown body for this fullname, or null if none. */
    public static String getRecovered(String fullName) {
        if (!hasRecoveries) return null;
        Result r = recovered.get(fullName);
        return r == null ? null : r.body;
    }

    /** Previously recovered title for this fullname, or null if none. */
    public static String getRecoveredTitle(String fullName) {
        if (!hasRecoveries) return null;
        Result r = recovered.get(fullName);
        return r == null ? null : r.title;
    }

    /**
     * Fetches the original title and body from Arctic Shift. Runs on a background thread and does
     * <b>not</b> touch the caches; pass the result to {@link #store} on the main thread.
     */
    public static Result fetch(Submission s) {
        JsonObject obj =
                HttpUtil.getJsonObject(Reddit.client, new Gson(), ARCTIC_SHIFT_POSTS + s.getId());
        // Guard every shape assumption: HttpUtil only catches malformed JSON, so a valid-but-
        // unexpected payload (error envelope, schema drift, null/non-object element) must not throw
        // out of the background AsyncTask and crash the app.
        if (obj == null || !obj.has("data") || !obj.get("data").isJsonArray()) {
            return new Result(null, null);
        }
        JsonArray data = obj.getAsJsonArray("data");
        if (data.size() == 0 || !data.get(0).isJsonObject()) {
            return new Result(null, null);
        }
        JsonObject post = data.get(0).getAsJsonObject();
        String title = readString(post, "title");
        String body = readString(post, "selftext");
        if (body != null && (body.equals("[removed]") || body.equals("[deleted]"))) {
            body = null;
        }
        return new Result(title, body);
    }

    /** Stores a non-empty {@link #fetch} result in the cache. Call on the main thread. */
    public static void store(Submission s, Result r) {
        recovered.put(s.getFullName(), r);
        // Volatile publish: written after the put so a reader that sees the flag sees the entry.
        hasRecoveries = true;
    }

    /** Reads a non-blank string-valued field, or null (also rejects null/object/array values). */
    private static String readString(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return null;
        String value = obj.get(key).getAsString();
        return value.trim().isEmpty() ? null : value;
    }
}
