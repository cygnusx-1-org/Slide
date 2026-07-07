package me.edgan.redditslide.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.edgan.redditslide.Reddit;
import net.dean.jraw.models.Submission;

/**
 * Recovers the original title and body (or link) of removed/deleted posts from the Arctic Shift
 * archive.
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

    /** A self-text consisting of a single markdown link {@code [text](href)} — captures the href. */
    private static final Pattern SOLE_MARKDOWN_LINK =
            Pattern.compile("^\\[[^\\]]*]\\((https?://[^)\\s]+)\\)$");

    /** A self-text consisting of a single bare URL. */
    private static final Pattern SOLE_BARE_URL = Pattern.compile("^(https?://\\S+)$");

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

    /**
     * What {@link #fetch} pulled from the archive; any field may be null. {@code body} is the
     * recovered self-text (removed text posts); {@code url} is the recovered destination of a
     * removed link post, applied back onto the submission by {@link #store} so it renders as a real
     * link post again.
     */
    public static final class Result {
        public final String title;
        public final String body;
        public final String url;

        Result(String title, String body, String url) {
            this.title = title;
            this.body = body;
            this.url = url;
        }

        public boolean isEmpty() {
            return title == null && body == null && url == null;
        }
    }

    /** Whether the post was removed by a moderator, deleted by its author, or taken down by Reddit. */
    public static boolean isRemovedOrDeleted(Submission s) {
        return isPlaceholder(s.getSelftext()) || s.getBannedBy() != null;
    }

    /**
     * Recognizes the placeholder text Reddit substitutes for removed content: the bare {@code
     * [removed]} / {@code [deleted]} left by an ordinary moderator removal or author deletion, and
     * the "[ Removed by Reddit … ]" / "[ Removed by moderator ]" sentences that Reddit's admins and
     * subreddit moderators leave (in both the title and the body) on a takedown. Used both to decide
     * a post is removed and to reject archive copies that are themselves post-takedown snapshots
     * rather than the original.
     */
    private static boolean isPlaceholder(String s) {
        if (s == null) return false;
        String lower = s.trim().toLowerCase(Locale.ENGLISH);
        return lower.equals("[removed]")
                || lower.equals("[deleted]")
                || lower.startsWith("[ removed by reddit")
                || lower.startsWith("[ removed by moderator");
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
            return new Result(null, null, null);
        }
        JsonArray data = obj.getAsJsonArray("data");
        if (data.size() == 0 || !data.get(0).isJsonObject()) {
            return new Result(null, null, null);
        }
        JsonObject post = data.get(0).getAsJsonObject();
        // Reject values that are themselves the post-takedown placeholder: when the archive only
        // ever saw the removed version (it never ingested the post while it was live) there is no
        // original to recover, and returning the placeholder would masquerade as a recovery.
        String title = readString(post, "title");
        if (isPlaceholder(title)) {
            title = null;
        }
        String body = readString(post, "selftext");
        if (isPlaceholder(body)) {
            body = null;
        }
        // Recover the original destination of a link post (is_self=false) so it renders as a real
        // link post again — Reddit rewrites `url` to the self permalink once a post is removed, so
        // the link is otherwise lost. Independent of `body`: an image/link post may also carry a
        // self-text caption, which is recovered above and shown alongside the card. Skip self-posts
        // and any url that merely points back at the comments permalink.
        String url = null;
        if (!readBoolean(post, "is_self")) {
            String u = readString(post, "url");
            if (u != null && !u.contains("/comments/")) {
                url = u;
            }
        }
        // If the recovered self-text is nothing but a single link, make THAT link the post's
        // destination (a real link post) rather than rendering it as body text. It wins over a
        // reddit-media `url` so the recovered card opens the meaningful link — e.g. an image post
        // whose only text is a link, where the image itself was purged on takedown.
        if (body != null) {
            String sole = soleLink(body);
            if (sole != null) {
                url = sole;
                body = null;
            }
        }
        return new Result(title, body, url);
    }

    /** Stores a non-empty {@link #fetch} result in the cache. Call on the main thread. */
    public static void store(Submission s, Result r) {
        // Rewrite a recovered link back onto the submission so the whole card renders as a real link
        // post again (ContentType, the lead image and the tap-to-open action all read from the node).
        if (r.url != null) {
            applyRecoveredUrl(s, r.url);
        }
        recovered.put(s.getFullName(), r);
        // Volatile publish: written after the put so a reader that sees the flag sees the entry.
        hasRecoveries = true;
    }

    /**
     * Restores a removed link post: Reddit replaces such a post's {@code url} with the self
     * permalink and flips {@code is_self} to true, so rewrite both on the backing JSON node (and
     * blank the takedown self-text so it isn't drawn under the recovered card). JRAW's getters read
     * this node live, so the change takes effect everywhere on the next bind.
     *
     * <p>Main thread only. Only pre-existing keys are replaced — never added — so the node's map is
     * not restructured and can't race a rehash with the background feed-title renderer that reads
     * the same node.
     */
    private static void applyRecoveredUrl(Submission s, String url) {
        JsonNode node = s.getDataNode();
        if (!(node instanceof ObjectNode)) return;
        ObjectNode obj = (ObjectNode) node;
        if (obj.has("url")) obj.put("url", url);
        if (obj.has("is_self")) obj.put("is_self", false);
        if (obj.has("selftext")) obj.put("selftext", "");
        if (obj.has("selftext_html")) obj.put("selftext_html", "");
    }

    /**
     * Re-applies an already-recovered link to {@code s} at bind time. {@link #store} rewrites the
     * link onto the submission's JSON node, but that mutation lives on a single {@link Submission}
     * instance; when the feed or comments screen re-parses the post into a fresh instance the
     * recovered title still resolves (it is served from the {@link #recovered} map by fullname on
     * every render) yet the rewritten link would otherwise be lost. Calling this while binding
     * re-applies the link to whichever instance is being drawn, so a recovered link post survives
     * leaving and returning without re-recovering. No-op until something is recovered; recovered
     * text/body already persists via {@link #getRecovered}. Main thread only.
     */
    public static void reapplyRecoveredLink(Submission s) {
        if (!hasRecoveries) return;
        Result r = recovered.get(s.getFullName());
        if (r != null && r.url != null) {
            applyRecoveredUrl(s, r.url);
        }
    }

    /** Reads a non-blank string-valued field, or null (also rejects null/object/array values). */
    private static String readString(JsonObject obj, String key) {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) return null;
        String value = obj.get(key).getAsString();
        return value.trim().isEmpty() ? null : value;
    }

    /** Reads a boolean-valued field, defaulting to false for missing/non-primitive values. */
    private static boolean readBoolean(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.get(key).getAsBoolean();
    }

    /**
     * If {@code body} is nothing but a single link — a bare URL or one markdown link — returns that
     * link's target; otherwise null. Lets a removed post whose entire self-text is a link be
     * recovered as a real link post instead of body text.
     */
    private static String soleLink(String body) {
        String trimmed = body.trim();
        Matcher m = SOLE_MARKDOWN_LINK.matcher(trimmed);
        if (m.matches()) return m.group(1);
        m = SOLE_BARE_URL.matcher(trimmed);
        if (m.matches()) return m.group(1);
        return null;
    }
}
