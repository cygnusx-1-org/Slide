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

import me.edgan.redditslide.Activities.BaseSaveActivity;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.util.HttpUtil;
import me.edgan.redditslide.util.LogUtil;

import okhttp3.OkHttpClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Created by carlo_000 on 2/1/2016. */
public class TumblrUtils {

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
            blog = i.getHost().split("\\.")[0];

            client = Reddit.client;
            gson = new Gson();
        }

        public void doWithData(List<Photo> data) {
            if (data == null || data.isEmpty()) {
                onError();
            }
        }

        TumblrPost post;

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
                        if (activity.submissionTitle == null || activity.submissionTitle.isEmpty()) {
                            Log.d(TAG, "Setting Tumblr post title: " + postTitle);
                            activity.submissionTitle = postTitle;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting post title", e);
                    }
                }

                baseActivity.runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                doWithData(post.getResponse().getPosts().get(0).getPhotos());
                            }
                        });
            } catch (IOException e) {
                e.printStackTrace();
                LogUtil.e(e, "parseJson error, baseData [" + baseData + "]");
            }
        }

        /**
         * Extract a suitable title from the post
         */
        @Nullable
        private String extractPostTitle(TumblrPost post) {
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
                    if (firstPost.getBlogName() != null) {
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
        protected ArrayList<JsonElement> doInBackground(final String... sub) {
            if (baseActivity != null) {
                String apiUrl =
                        "https://api.tumblr.com/v2/blog/"
                                + blog
                                + "/posts?api_key="
                                + Constants.TUMBLR_API_KEY
                                + "&id="
                                + id;
                LogUtil.v(apiUrl);
                if (tumblrRequests.contains(apiUrl) && JsonParser.parseString(tumblrRequests.getString(apiUrl, "")).getAsJsonObject().has("response")) {
                    Log.d(TAG, "parseJson: 1" + tumblrRequests.getString(apiUrl, ""));
                    parseJson(JsonParser.parseString(tumblrRequests.getString(apiUrl, "")).getAsJsonObject());
                } else {
                    LogUtil.v(apiUrl);
                    final JsonObject result = HttpUtil.getJsonObject(client, gson, apiUrl);
                    if (result != null
                            && result.has("response")
                            && result.get("response").getAsJsonObject().has("posts")
                            && result.get("response")
                                    .getAsJsonObject()
                                    .get("posts")
                                    .getAsJsonArray()
                                    .get(0)
                                    .getAsJsonObject()
                                    .has("photos")) {
                        Log.d(TAG, "parseJson: 2" + result.toString());
                        tumblrRequests.edit().putString(apiUrl, result.toString()).apply();
                        parseJson(result);
                    } else {
                        onError();
                    }
                }
                return null;
            }
            return null;
        }
    }
}
