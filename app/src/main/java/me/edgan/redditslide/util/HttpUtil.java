package me.edgan.redditslide.util;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import me.edgan.redditslide.Constants;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A class that helps with HTTP requests and response parsing.
 *
 * <p>Created by Fernando Barillas on 7/13/16.
 */
public class HttpUtil {


    /** Media info parsed from an imgur API response: the content type and direct links. */
    public static final class ImgurMedia {
        public final String type;
        private final String url;
        private final String mp4;

        private ImgurMedia(String type, String url, String mp4) {
            this.type = type;
            this.url = url;
            this.mp4 = mp4;
        }

        public boolean isGif() {
            return type.contains("gif");
        }

        /** Best url for gif playback: the mp4 rendition when the API provides one. */
        public String getGifUrl() {
            return (mp4 == null || mp4.isEmpty()) ? url : mp4;
        }

        /** The plain image url. */
        public String getImageUrl() {
            return url;
        }
    }

    /**
     * Extracts the media type and links from either imgur response shape ("image" for the old
     * gallery API, "data" for v3), or returns null when the response has neither.
     */
    @Nullable
    public static ImgurMedia parseImgurMedia(@Nullable JsonObject result) {
        if (result != null && !result.isJsonNull() && result.has("image")) {
            String type =
                    result.get("image")
                            .getAsJsonObject()
                            .get("image")
                            .getAsJsonObject()
                            .get("type")
                            .getAsString();
            String urls =
                    result.get("image")
                            .getAsJsonObject()
                            .get("links")
                            .getAsJsonObject()
                            .get("original")
                            .getAsString();
            return new ImgurMedia(type, urls, null);
        } else if (result != null && result.has("data")) {
            String type = result.get("data").getAsJsonObject().get("type").getAsString();
            String urls = result.get("data").getAsJsonObject().get("link").getAsString();
            String mp4 = "";
            if (result.get("data").getAsJsonObject().has("mp4")) {
                mp4 = result.get("data").getAsJsonObject().get("mp4").getAsString();
            }
            return new ImgurMedia(type, urls, mp4);
        }
        return null;
    }

    /**
     * Gets a JsonObject by calling apiUrl and parsing the JSON response String. This method should
     * be used when calling the Imgur API (https://api.imgur.com/) since it requires special headers
     * in the requests.
     *
     * @param client The OkHTTP client to use to make the request
     * @param gson The GSON instance to use to parse the response String
     * @param apiUrl The URL to call to get the response from
     * @param imgurKey The API key to use when the request is made
     * @return A JsonObject representation of the API response, null when there was an error or
     *     Exception thrown by the HTTP call
     */
    public static JsonObject getImgurJsonObject(
            final OkHttpClient client,
            final Gson gson,
            final String apiUrl,
            final String imgurKey) {
        Map<String, String> imgurHeadersMap = new HashMap<>();
        imgurHeadersMap.put("Authorization", "Client-ID " + Constants.IMGUR_CLIENT_ID);
        //imgurHeadersMap.put("Authorization", "Bearer" + imgurKey);
        return getJsonObject(client, gson, apiUrl, imgurHeadersMap);
    }

    /**
     * Gets a JsonObject by calling apiUrl and parsing the JSON response String. This method accepts
     * a Map that can contain custom headers to include in the request.
     *
     * @param client The OkHTTP client to use to make the request
     * @param gson The GSON instance to use to parse the response String
     * @param apiUrl The URL to call to get the response from
     * @param headersMap The headers to include in the request. Can be null to not add any headers
     * @return A JsonObject representation of the API response, null when there was an error or
     *     Exception thrown by the HTTP call
     */
    public static JsonObject getJsonObject(
            final OkHttpClient client,
            final Gson gson,
            final String apiUrl,
            @Nullable final Map<String, String> headersMap) {
        if (client == null || gson == null || TextUtils.isEmpty(apiUrl)) return null;
        Request.Builder builder = new Request.Builder().url(apiUrl);

        if (headersMap != null && headersMap.size() > 0) {
            // Add headers to the request if headers are available
            for (Map.Entry<String, String> entry : headersMap.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        Request request = builder.build();
        try {
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            ResponseBody responseBody = response.body();
            String json = responseBody.string();
            responseBody.close();
            return gson.fromJson(json, JsonObject.class);
        } catch (JsonSyntaxException | IOException e) {
            LogUtil.e(e, "Error " + apiUrl);
        }
        return null;
    }

    /**
     * Gets a JsonArray by executing a request and parsing the JSON response String. This method
     * accepts a Map that can contain custom headers to include in the request.
     *
     * @param client The OkHTTP client to use to make the request
     * @param gson The GSON instance to use to parse the response String
     * @param request The request to execute
     * @return A JsonObject representation of the API response, null when there was an error or
     *     Exception thrown by the HTTP call
     */
    public static JsonArray getJsonArray(
            final OkHttpClient client, final Gson gson, final Request request) {
        if (client == null || gson == null || request == null) return null;

        try {
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            ResponseBody responseBody = response.body();
            String json = responseBody.string();
            responseBody.close();
            return gson.fromJson(json, JsonArray.class);
        } catch (JsonSyntaxException | IOException e) {
            LogUtil.e(e, "Error " + request.url().toString());
        }
        return null;
    }

    /**
     * Gets a JsonObject by calling apiUrl and parsing the JSON response String
     *
     * @param client The OkHTTP client to use to make the request
     * @param gson The GSON instance to use to parse the response String
     * @param apiUrl The URL to call to get the response from
     * @return A JsonObject representation of the API response, null when there was an error or
     *     Exception thrown by the HTTP call
     */
    public static JsonObject getJsonObject(
            final OkHttpClient client, final Gson gson, final String apiUrl) {
        return getJsonObject(client, gson, apiUrl, null);
    }
}
