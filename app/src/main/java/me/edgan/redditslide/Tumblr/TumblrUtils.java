package me.edgan.redditslide.Tumblr;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.Activities.BaseSaveActivity;
import me.edgan.redditslide.BuildConfig;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.util.HttpUtil;
import me.edgan.redditslide.util.LogUtil;
import okhttp3.OkHttpClient;

/** Created by carlo_000 on 2/1/2016. */
public class TumblrUtils {

    // Populated by Reddit.onCreate (Reddit.java:472), before anything can request a post.
    @SuppressWarnings("NullAway.Init")
    public static SharedPreferences tumblrRequests;
    private static final String TAG = "TumblrUtils";

    public static class GetTumblrPostWithCallback
            extends AsyncTask<String, Void, ArrayList<JsonElement>> {

        public String blog, id;
        public Activity baseActivity;

        private OkHttpClient client;
        private Gson gson;

        public void onError() {}

        public GetTumblrPostWithCallback(@NonNull String url, @NonNull Activity baseActivity) {

            this.baseActivity = baseActivity;
            Uri i = Uri.parse(url);

            id = i.getPathSegments().get(1);
            // A URI with no host is not a Tumblr post URL. Leaving the blog empty builds an API URL
            // that fails into onError(), which is where the rest of this class sends a response it
            // cannot use; the previous chain dereferenced the null and crashed the caller instead.
            final String host = i.getHost();
            blog = host == null ? "" : host.split("\\.")[0];

            client = Reddit.client;
            gson = new Gson();
        }

        /**
         * Hands the post's photos to the caller.
         *
         * @return whether {@code data} holds anything to work with. False means the response carried
         *     no photos and {@link #onError()} has already been told; an override must return
         *     without touching the list, since indexing it would throw. The boolean is the contract
         *     rather than a bare void, because every override calls {@code super} first and the
         *     earlier void version let them carry on into an empty list.
         */
        public boolean doWithData(@Nullable List<Photo> data) {
            if (data == null || data.isEmpty()) {
                onError();
                return false;
            }
            return true;
        }

        /**
         * Whether a fetched response is worth handing to {@link #parseJson}: it has a response
         * object, that has a posts array, the first entry of that array is an object, and it carries
         * a photos key.
         *
         * <p>Every step tests the node's type rather than assuming it. This ran inline in
         * doInBackground as one chained expression, where {@code getAsJsonObject()} on a JSON-null
         * response and {@code getAsJsonArray()} on a posts object both threw ClassCastException and
         * {@code get(0)} on an empty array threw IndexOutOfBoundsException — on the worker thread,
         * uncaught, so a malformed response crashed instead of reaching {@link #onError()}.
         *
         * <p>Package-private and a method rather than the chain it replaced because the only way to
         * reach it from a test is to call it: the caller sits behind a live HTTP request.
         */
        static boolean hasPhotos(@Nullable final JsonObject result) {
            if (result == null) {
                return false;
            }
            final JsonElement response = result.get("response");
            if (response == null || !response.isJsonObject()) {
                return false;
            }
            final JsonElement posts = response.getAsJsonObject().get("posts");
            if (posts == null || !posts.isJsonArray() || posts.getAsJsonArray().isEmpty()) {
                return false;
            }
            final JsonElement first = posts.getAsJsonArray().get(0);
            return first != null && first.isJsonObject() && first.getAsJsonObject().has("photos");
        }

        /**
         * A cached response body as a JsonObject, or null when the stored string is not one.
         *
         * <p>The cache is read with {@code getString(apiUrl, "")}, and both the default and any
         * value that is not an object make {@code getAsJsonObject()} throw — {@code parseString("")}
         * is JsonNull, an array is an array, and a truncated write is not JSON at all, which
         * {@code parseString} throws on rather than reporting. Nothing this class writes can be any
         * of those, since it only ever stores a JsonObject it has already screened, so the guard the
         * chain relied on was "the prefs file contains only what we put there".
         */
        @Nullable static JsonObject asObject(@Nullable final String cached) {
            if (cached == null) {
                return null;
            }
            try {
                final JsonElement parsed = JsonParser.parseString(cached);
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            } catch (JsonSyntaxException e) {
                LogUtil.e(e, "TumblrUtils: unparseable cached response");
                return null;
            }
        }

        // Null until parseJson has read a response, and after one that would not deserialize.
        @Nullable TumblrPost post;

        public void parseJson(JsonElement baseData) {
            try {
                post = new ObjectMapper().readValue(baseData.toString(), TumblrPost.class);

                // Extract post title (summary or caption) to use for file naming
                final String postTitle = extractPostTitle(post);

                // Set the submission title in the activity if it's a BaseSaveActivity
                if (baseActivity instanceof BaseSaveActivity) {
                    try {
                        BaseSaveActivity activity = (BaseSaveActivity) baseActivity;
                        // Only set if not already set
                        if (postTitle != null
                                && (activity.submissionTitle == null
                                        || activity.submissionTitle.isEmpty())) {
                            Log.d(TAG, "Setting Tumblr post title: " + postTitle);
                            activity.submissionTitle = postTitle;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting post title", e);
                    }
                }

                // Unpacked before the post to the main thread: this walk is four levels deep and any
                // of them can be absent, and inside the Runnable neither the catch below nor
                // anything else would catch the throw.
                final List<Post> posts =
                        post.getResponse() == null ? null : post.getResponse().getPosts();
                // The element, not just the size: Jackson leaves a null in the list for a null
                // element in the JSON array, exactly as it does for photos and alt_sizes, so an
                // array of one null passes isEmpty() and then NPEs on getPhotos().
                final Post first = (posts == null || posts.isEmpty()) ? null : posts.get(0);
                final List<Photo> photos = first == null ? null : first.getPhotos();

                baseActivity.runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                // A null reaches doWithData rather than short-circuiting to
                                // onError() here, so that the error path stays on the main thread
                                // like the success path.
                                doWithData(photos);
                            }
                        });
            } catch (IOException e) {
                LogUtil.e(e, "TumblrUtils.run failed");
                LogUtil.e(e, "parseJson error, baseData [" + baseData + "]");
            }
        }

        /**
         * Extract a suitable title from the post
         */
        @Nullable private String extractPostTitle(@Nullable TumblrPost post) {
            try {
                if (post != null && post.getResponse() != null &&
                    post.getResponse().getPosts() != null &&
                    !post.getResponse().getPosts().isEmpty()) {

                    Post firstPost = post.getResponse().getPosts().get(0);

                    // Try to get the summary first as it's usually a better title
                    if (firstPost.getSummary() != null && !firstPost.getSummary().trim().isEmpty()) {
                        return firstPost.getSummary().trim();
                    }

                    // Fall back to caption if available
                    if (firstPost.getCaption() != null && !firstPost.getCaption().trim().isEmpty()) {
                        // Caption might contain HTML, so we'll just use the first 50 chars
                        String caption = firstPost.getCaption().trim();
                        // Remove HTML tags
                        caption = caption.replaceAll("<[^>]*>", "");
                        // Truncate if too long
                        if (caption.length() > 50) {
                            caption = caption.substring(0, 50) + "...";
                        }
                        return caption;
                    }

                    // If no good title found, use blog name and post ID
                    if (firstPost.getBlogName() != null && firstPost.getId() != null) {
                        return firstPost.getBlogName() + "_" + firstPost.getId().intValue();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting post title", e);
            }

            // Default fallback
            return "tumblr_post";
        }

        @Override
        @Nullable protected ArrayList<JsonElement> doInBackground(final String... sub) {
            if (baseActivity != null) {
                String apiUrl =
                        "https://api.tumblr.com/v2/blog/"
                                + blog
                                + "/posts?api_key="
                                + Constants.TUMBLR_API_KEY
                                + "&id="
                                + id;
                LogUtil.v(apiUrl);
                // Screened the same way a fetched response is, and by the same method: only a
                // photo-bearing object is ever written here, so anything else in the cache did not
                // come from us and belongs in a fresh fetch rather than in parseJson.
                final JsonObject cached =
                        tumblrRequests.contains(apiUrl)
                                ? asObject(tumblrRequests.getString(apiUrl, ""))
                                : null;
                if (cached != null && hasPhotos(cached)) {
                    // Guarded, not merely quiet: Log.d survives into release builds, and the
                    // argument is a whole API response serialised on every album open.
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "parseJson: 1" + cached);
                    }
                    parseJson(cached);
                } else {
                    LogUtil.v(apiUrl);
                    final JsonObject result = HttpUtil.getJsonObject(client, gson, apiUrl);
                    if (result != null && hasPhotos(result)) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "parseJson: 2" + result.toString());
                        }
                        tumblrRequests.edit().putString(apiUrl, result.toString()).apply();
                        parseJson(result);
                    } else {
                        // Main thread, like the doWithData path: the overrides start an activity
                        // and finish this one, and the peek view rebuilds its whole view from here.
                        baseActivity.runOnUiThread(this::onError);
                    }
                }
                return null;
            }
            return null;
        }
    }
}
