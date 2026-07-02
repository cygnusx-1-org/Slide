package me.edgan.redditslide.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;

import me.edgan.redditslide.Authentication;

import net.dean.jraw.http.HttpRequest;

import okhttp3.OkHttpClient;
import okhttp3.Request;

public class FlairUtil {

    /**
     * Fetches a subreddit's link-flair list (/api/link_flair_v2.json) through the authenticated
     * JRAW request builder, returning the raw JsonArray (null on failure).
     */
    public static JsonArray fetchLinkFlairs(OkHttpClient client, Gson gson, String subreddit) {
        HttpRequest r =
                Authentication.reddit
                        .request()
                        .path("/r/" + subreddit + "/api/link_flair_v2.json")
                        .get()
                        .build();

        Request request =
                new Request.Builder()
                        .headers(
                                r.getHeaders()
                                        .newBuilder()
                                        .set("User-Agent", "Slide flair search")
                                        .build())
                        .url(r.getUrl())
                        .build();

        return HttpUtil.getJsonArray(client, gson, request);
    }
}
