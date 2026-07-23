package me.edgan.redditslide.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import me.edgan.redditslide.Reddit;
import net.dean.jraw.models.Comment;

/**
 * Recovers the original body and author of removed/deleted comments from the Arctic Shift archive.
 *
 * <p>When a comment is removed by a moderator or deleted by its author, the Reddit API returns the
 * literal placeholder {@code [removed]} / {@code [deleted]} in {@code body}, and an author deletion
 * additionally replaces {@code author} with {@code [deleted]}. Arctic Shift
 * (arctic-shift.photon-reddit.com) is a public Reddit archive that still exposes both by comment id
 * with no authentication.
 *
 * <p>The archive stores markdown only — there is no {@code body_html} in its response — so a
 * recovered body has to be rendered through Markwon rather than the snudown block path. This is the
 * same constraint {@link PostRecovery} hit with {@code selftext_html}.
 *
 * <p>JRAW {@link Comment} objects expose their body and author from an immutable data node, so
 * recovered values are held in a static map keyed by the comment fullname and consulted at bind
 * time. Both ends run on the UI thread today (the fetch stores from {@code onPostExecute}, the
 * adapter reads while binding), but the map is wrapped in {@link Collections#synchronizedMap} and
 * guarded by a lock-free {@code hasRecoveries} fast path so the per-bind lookup stays cheap and the
 * structure is already correct if a background renderer ever reads it. {@link #fetch} performs only
 * the network call and touches no shared state.
 *
 * <p>The placeholder taxonomy itself lives in {@link PostRecovery#isPlaceholder} and is shared
 * rather than copied — it has already been corrected once and two copies would drift.
 */
public final class CommentRecovery {

    private static final String ARCTIC_SHIFT_COMMENTS =
            "https://arctic-shift.photon-reddit.com/api/comments/ids?ids=";

    /** Cap on cached recoveries so a long-lived session can't grow the map without bound. */
    private static final int MAX_ENTRIES = 50;

    /** fullname ("t1_xxx") -> recovered body/author. Accessed while binding and after a fetch. */
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
     * True once anything has been recovered. Lets the per-bind render path skip the synchronized
     * map lookup entirely in the common case where nothing has been recovered. Volatile so a reader
     * on another thread would see the storing thread's {@link #store} publish.
     */
    private static volatile boolean hasRecoveries = false;

    private CommentRecovery() {}

    /**
     * What {@link #fetch} pulled from the archive; either field may be null. {@code body} is the
     * recovered markdown, {@code author} the original username of a comment whose author Reddit now
     * reports as {@code [deleted]}. They are independent: a moderator removal keeps its author and
     * recovers only a body, while a comment left behind by a deleted account keeps its body and
     * recovers only the byline.
     */
    public static final class Result {
        public final String body;
        public final String author;

        Result(String body, String author) {
            this.body = body;
            this.author = author;
        }

        public boolean isEmpty() {
            return body == null && author == null;
        }
    }

    /** Whether Reddit is hiding this comment's body, its author, or both. */
    public static boolean isRemovedOrDeleted(Comment c) {
        return PostRecovery.isPlaceholder(c.getBody())
                || PostRecovery.isPlaceholder(c.getAuthor());
    }

    /** Previously recovered markdown body for this fullname, or null if none. */
    public static String getRecovered(String fullName) {
        if (!hasRecoveries) return null;
        Result r = recovered.get(fullName);
        return r == null ? null : r.body;
    }

    /** Previously recovered author for this fullname, or null if none. */
    public static String getRecoveredAuthor(String fullName) {
        if (!hasRecoveries) return null;
        Result r = recovered.get(fullName);
        return r == null ? null : r.author;
    }

    /**
     * Whether a non-empty recovery has already been stored for this fullname. Lets the UI stop
     * offering "Recover comment" on a comment that's already been recovered — {@link
     * #isRemovedOrDeleted} still reports true afterwards, because the placeholder body and author
     * are left in place on the comment itself.
     */
    public static boolean isRecovered(String fullName) {
        if (!hasRecoveries) return false;
        Result r = recovered.get(fullName);
        return r != null && !r.isEmpty();
    }

    /**
     * Fetches the original body and author from Arctic Shift. Runs on a background thread and does
     * <b>not</b> touch the cache; pass the result to {@link #store} on the main thread.
     */
    public static Result fetch(Comment c) {
        Result r =
                parse(
                        HttpUtil.getJsonObject(
                                Reddit.client, new Gson(), ARCTIC_SHIFT_COMMENTS + c.getId()));
        // Recover only the field(s) Reddit is actually hiding. The entry is offered whenever the
        // body OR the author is a placeholder, so a comment left behind by a deleted account keeps
        // its real, intact body — reinstating that body would gain nothing and would re-render it
        // through the recovered path (Markwon with no body_html), dropping its inline images. When
        // the live body is intact, drop the archive body and recover the author alone; likewise a
        // moderator removal keeps the real author, so drop that. See {@link PostRecovery#recoverField}.
        final String body = PostRecovery.recoverField(c.getBody(), r.body);
        final String author = PostRecovery.recoverField(c.getAuthor(), r.author);
        return new Result(body, author);
    }

    /**
     * Reads the archive's response envelope. Split out from {@link #fetch} so the parse can be
     * tested without a network call.
     *
     * <p>Guards every shape assumption: {@link HttpUtil} only catches malformed JSON, so a
     * valid-but-unexpected payload (error envelope, schema drift, null/non-object element) must not
     * throw out of the background task and crash the app.
     */
    public static Result parse(JsonObject obj) {
        if (obj == null || !obj.has("data") || !obj.get("data").isJsonArray()) {
            return new Result(null, null);
        }
        JsonArray data = obj.getAsJsonArray("data");
        if (data.size() == 0 || !data.get(0).isJsonObject()) {
            return new Result(null, null);
        }
        JsonObject comment = data.get(0).getAsJsonObject();
        // Reject values that are themselves the removal placeholder: when the archive only ever saw
        // the removed version (it never ingested the comment while it was live) there is no
        // original to recover, and returning the placeholder would masquerade as a recovery.
        String body = readString(comment, "body");
        if (PostRecovery.isPlaceholder(body)) {
            body = null;
        }
        String author = readString(comment, "author");
        if (PostRecovery.isPlaceholder(author)) {
            author = null;
        }
        return new Result(body, author);
    }

    /** Stores a non-empty {@link #fetch} result in the cache. Call on the main thread. */
    public static void store(String fullName, Result r) {
        recovered.put(fullName, r);
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
