package me.edgan.redditslide.util;

import androidx.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.dean.jraw.RedditClient;
import net.dean.jraw.http.HttpRequest;
import net.dean.jraw.http.RestResponse;
import net.dean.jraw.models.Contribution;

import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.markdown.RichTextJSONConverter;
import me.edgan.redditslide.markdown.UploadedImage;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Submits comments and self-posts using the Reddit {@code richtext_json} parameter, which is what
 * allows Reddit-hosted inline images to render. JRAW's {@code AccountManager.reply()/submit()} only
 * support plain {@code text}, so these calls are made directly against the API.
 *
 * <p>Used only when the body contains at least one uploaded image; otherwise the normal JRAW path
 * is kept.
 */
public final class RichtextSubmission {

    private RichtextSubmission() {}

    /** Thrown when Reddit returns an API-level error (HTTP 200 with an {@code errors} array). */
    public static class RedditApiError extends Exception {
        public RedditApiError(String message) {
            super(message);
        }
    }

    /**
     * Replies to {@code parent} with markdown plus inline images.
     *
     * @return the bare id (no {@code t1_} prefix) of the created comment
     */
    public static String reply(
            RedditClient client,
            Contribution parent,
            String markdown,
            List<UploadedImage> images)
            throws Exception {
        String richtextJson = new RichTextJSONConverter().constructRichTextJSON(markdown, images);

        Map<String, String> params = new HashMap<>();
        params.put("api_type", "json");
        params.put("thing_id", parent.getFullName());
        params.put("richtext_json", richtextJson);
        params.put("text", "");

        HttpRequest request =
                client.request().path("/api/comment").post(params).build();
        RestResponse response = client.execute(request);
        JsonNode root = response.getJson();

        throwIfError(root);

        // Fast path: the standard /api/comment shape.
        JsonNode things = root.path("json").path("data").path("things");
        if (things.isArray() && things.size() > 0) {
            String id = things.get(0).path("data").path("id").asText(null);
            if (id != null && !id.isEmpty()) {
                return id;
            }
        }
        // Fallback: the richtext_json response nests differently, so search the whole tree for the
        // created comment's fullname (t1_...).
        String id = findThingId(root, "t1_");
        if (id != null) {
            return id;
        }
        throw new RedditApiError("No comment returned by Reddit");
    }

    /** Recursively searches a response for the first {@code name} field with the given prefix. */
    private static String findThingId(JsonNode node, String prefix) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            JsonNode name = node.get("name");
            if (name != null && name.isTextual() && name.asText().startsWith(prefix)) {
                return name.asText().substring(prefix.length());
            }
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                String found = findThingId(child, prefix);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Submits a self-post whose selftext is markdown plus inline images.
     *
     * @return the fullname ({@code t3_...}) of the created submission
     */
    public static String submitSelf(
            RedditClient client,
            String subreddit,
            String title,
            String markdown,
            List<UploadedImage> images,
            boolean sendReplies,
            @Nullable String flairId)
            throws Exception {
        String richtextJson = new RichTextJSONConverter().constructRichTextJSON(markdown, images);

        Map<String, String> params = new HashMap<>();
        params.put("api_type", "json");
        params.put("sr", subreddit);
        params.put("title", title);
        params.put("kind", "self");
        params.put("richtext_json", richtextJson);
        params.put("text", "");
        params.put("sendreplies", Boolean.toString(sendReplies));
        params.put("resubmit", "true");
        if (flairId != null) {
            params.put("flair_id", flairId);
        }

        HttpRequest request = client.request().path("/api/submit").post(params).build();
        RestResponse response = client.execute(request);
        JsonNode root = response.getJson();

        throwIfError(root);

        JsonNode data = root.path("json").path("data");
        String name = data.path("name").asText(null);
        if (name != null && !name.isEmpty()) {
            return name;
        }
        String id = findThingId(root, "t3_");
        if (id != null) {
            return "t3_" + id;
        }
        throw new RedditApiError("No submission returned by Reddit");
    }

    /**
     * Submits a native Reddit image post ({@code kind=image}) pointing at a previously uploaded
     * Reddit media URL.
     *
     * @return the post permalink if Reddit returned one, otherwise {@code null} (image posts are
     *     often finalized asynchronously and only return a websocket URL)
     */
    public static String submitImage(
            RedditClient client,
            String subreddit,
            String title,
            String imageUrl,
            boolean sendReplies,
            @Nullable String flairId)
            throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("api_type", "json");
        params.put("sr", subreddit);
        params.put("title", title);
        params.put("kind", "image");
        params.put("url", imageUrl);
        params.put("sendreplies", Boolean.toString(sendReplies));
        params.put("resubmit", "true");
        if (flairId != null) {
            params.put("flair_id", flairId);
        }

        HttpRequest request = client.request().path("/api/submit").post(params).build();
        RestResponse response = client.execute(request);
        JsonNode root = response.getJson();

        throwIfError(root);

        String url = root.path("json").path("data").path("url").asText(null);
        // Only return it if it looks like a real post permalink, not a websocket / status URL.
        if (url != null && url.startsWith("http") && url.contains("/comments/")) {
            return url;
        }
        return null;
    }

    /**
     * Submits a Reddit gallery post from a list of uploaded media asset ids. The gallery endpoint
     * takes a JSON body (not form-encoded), so this is sent with raw okhttp using the OAuth header
     * borrowed from a JRAW request.
     *
     * @return the post permalink if Reddit returned one, otherwise {@code null}
     */
    public static String submitGallery(
            RedditClient client,
            String subreddit,
            String title,
            List<String> assetIds,
            @Nullable List<String> captions,
            boolean sendReplies,
            @Nullable String flairId)
            throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("sr", subreddit);
        payload.put("submit_type", "subreddit");
        payload.put("api_type", "json");
        payload.put("show_error_list", true);
        payload.put("title", title);
        payload.put("text", "");
        payload.put("spoiler", false);
        payload.put("nsfw", false);
        payload.put("kind", "self");
        payload.put("original_content", false);
        payload.put("post_to_twitter", false);
        payload.put("sendreplies", sendReplies);
        payload.put("validate_on_submit", true);
        if (flairId != null) {
            payload.put("flair_id", flairId);
        }
        JSONArray items = new JSONArray();
        for (int i = 0; i < assetIds.size(); i++) {
            String caption =
                    (captions != null && i < captions.size() && captions.get(i) != null)
                            ? captions.get(i)
                            : "";
            JSONObject item = new JSONObject();
            item.put("caption", caption);
            item.put("outbound_url", "");
            item.put("media_id", assetIds.get(i));
            items.put(item);
        }
        payload.put("items", items);

        // Build the request through JRAW only to borrow the authenticated headers and oauth URL.
        HttpRequest probe =
                client.request()
                        .path("/api/submit_gallery_post.json")
                        .query("resubmit", "true", "raw_json", "1")
                        .get()
                        .build();

        Request request =
                new Request.Builder()
                        .headers(probe.getHeaders())
                        .url(probe.getUrl())
                        .post(
                                RequestBody.create(
                                        payload.toString(),
                                        MediaType.parse("application/json; charset=utf-8")))
                        .build();

        String body;
        try (Response response = Reddit.client.newCall(request).execute()) {
            body = response.body() != null ? response.body().string() : null;
        }
        if (body == null) {
            throw new RedditApiError("Empty response from Reddit");
        }

        JsonNode root = new ObjectMapper().readTree(body);
        throwIfError(root);

        String url = root.path("json").path("data").path("url").asText(null);
        if (url != null && !url.isEmpty()) {
            return url;
        }
        String id = findThingId(root, "t3_");
        return id != null ? "t3_" + id : null;
    }

    private static void throwIfError(JsonNode root) throws RedditApiError {
        if (root == null) {
            throw new RedditApiError("Empty response from Reddit");
        }
        JsonNode errors = root.path("json").path("errors");
        if (errors.isArray() && errors.size() > 0) {
            JsonNode first = errors.get(0);
            // errors entries look like ["ERROR_CODE", "human readable", "field"]
            String message = first.size() > 1 ? first.get(1).asText() : first.toString();
            throw new RedditApiError(message);
        }
    }
}
