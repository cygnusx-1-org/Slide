package me.edgan.redditslide.ImgurAlbum;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import androidx.annotation.NonNull;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SecretConstants;
import me.edgan.redditslide.util.HttpUtil;
import me.edgan.redditslide.util.LogUtil;
import okhttp3.OkHttpClient;
import org.jspecify.annotations.Nullable;

/** Created by carlo_000 on 2/1/2016. */
public class AlbumUtils {

    // Populated by Reddit.onCreate (Reddit.java:471), before anything can request an album.
    @SuppressWarnings("NullAway.Init")
    public static SharedPreferences albumRequests;

    /**
     * @return the text after the last dash, or null when the string holds no dash.
     */
    @Nullable
    public static String substringAfterLastDash(String s) {
        int lastDashIndex = s.lastIndexOf('-');

        // Only return the substring if a dash is found.
        if (lastDashIndex != -1) {
            return s.substring(lastDashIndex + 1);
        } else {
            return null;
        }
    }

    @Nullable
    private static String getHash(@Nullable String s) {
        if (s == null) {
            return null;
        }
        String last = substringAfterLastDash(s);
        LogUtil.v(s);
        LogUtil.v("1 " + last);

        if (last != null) {
            return last;
        }

        if (s.contains("/comment/")) {
            s = s.substring(0, s.indexOf("/comment"));
        }

        String next = s.substring(s.lastIndexOf("/"));

        if (next.contains(".")) {
            next = next.substring(0, next.indexOf("."));
        }

        if (next.startsWith("/")) {
            next = next.substring(1);
        }

        LogUtil.v("2 " + next);

        if (next.length() < 5) {
            return getHash(s.replace(next, ""));
        } else {
            return next;
        }
    }

    private static String cutEnds(String s) {
        if (s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        } else {
            return s;
        }
    }

    // The imgur album hash for a share URL. Extracted from GetAlbumWithCallback's constructor so the
    // feed's tap-target prefetch derives the identical hash the viewer uses.
    @Nullable
    static String deriveAlbumHash(@Nullable String url) {
        if (url == null) {
            return null;
        }
        if (url.contains("/layout/")) {
            url = url.substring(0, url.indexOf("/layout"));
        }
        String rawDat = cutEnds(url);
        if (rawDat.endsWith("/")) {
            rawDat = rawDat.substring(0, rawDat.length() - 1);
        }
        if (rawDat.substring(rawDat.lastIndexOf("/") + 1).length() < 4) {
            rawDat = rawDat.replace(rawDat.substring(rawDat.lastIndexOf("/")), "");
        }
        if (rawDat.contains("?")) {
            rawDat = rawDat.substring(0, rawDat.indexOf("?"));
        }
        return getHash(rawDat);
    }

    /**
     * Resolve the display URL of an imgur album's first still image by calling the imgur API on the
     * CURRENT thread — callers MUST be off the main thread. Returns the same URL the album viewer
     * requests ({@code Image.getImageUrl()} = {@code i.imgur.com/{hash}{ext}}), or null if the album
     * can't be resolved or its first item is animated/absent. Used by the feed's tap-target prefetch
     * to warm an album's first image ahead of a tap, mirroring the reddit-gallery path. Reads the
     * image {@code link} straight from the JSON (no SingleImage/Image round-trip) and reuses this
     * class's {@code getHash} so the reconstructed URL matches the viewer's cache entry exactly.
     */
    @Nullable
    public static String getFirstAlbumImageUrlBlocking(
            final Context context, @Nullable final String url) {
        try {
            String hash = deriveAlbumHash(url);
            if (hash == null) {
                return null;
            }
            if (hash.startsWith("/")) {
                hash = hash.substring(1);
            }
            // Albums are a single hash; if a comma list slipped through, the leading entry is the
            // album whose first image we want.
            final int comma = hash.indexOf(",");
            if (comma != -1) {
                hash = hash.substring(0, comma);
            }
            if (hash.trim().isEmpty()) {
                return null;
            }

            final String apiUrl = "https://api.imgur.com/3/album/" + hash;
            final JsonObject result =
                    HttpUtil.getImgurJsonObject(
                            Reddit.client,
                            new Gson(),
                            apiUrl,
                            SecretConstants.getImgurApiKey(context));
            if (result == null
                    || !result.has("success")
                    || !result.get("success").getAsBoolean()
                    || !result.has("data")) {
                return null;
            }

            final JsonObject data = result.getAsJsonObject("data");
            final JsonObject firstImage;
            if (data.has("is_album") && data.get("is_album").getAsBoolean()) {
                if (!data.has("images")
                        || !data.get("images").isJsonArray()
                        || data.getAsJsonArray("images").size() == 0
                        || !data.getAsJsonArray("images").get(0).isJsonObject()) {
                    return null;
                }
                firstImage = data.getAsJsonArray("images").get(0).getAsJsonObject();
            } else {
                firstImage = data; // a bare imgur image, not an album
            }

            if (!firstImage.has("link")) {
                return null;
            }
            final String link = firstImage.get("link").getAsString();
            if (link == null || !link.contains(".")) {
                return null;
            }
            // Skip an animated first item — the viewer opens those as gif/mp4, not a still-image warm
            // (mirrors convertToSingle's detection: the "animated" flag or a gif/mp4 link).
            final boolean animated =
                    (firstImage.has("animated") && firstImage.get("animated").getAsBoolean())
                            || link.contains(".gif")
                            || link.endsWith(".mp4");
            if (animated) {
                return null;
            }
            final String imgHash = getHash(link);
            if (imgHash == null || imgHash.isEmpty()) {
                return null;
            }
            final String ext = link.substring(link.lastIndexOf("."));
            return "https://i.imgur.com/" + imgHash + ext;
        } catch (Exception e) {
            LogUtil.e(e, "Album first-image prefetch resolve failed for " + url);
            return null;
        }
    }

    public static class GetAlbumWithCallback
            extends AsyncTask<String, Void, ArrayList<JsonElement>> {

        // Null when the share URL held nothing hash-shaped; doInBackground reports that as an
        // unresolvable album rather than dereferencing it.
        @Nullable public String hash;

        public String type;

        public Activity baseActivity;

        private OkHttpClient client;
        private Gson gson;
        private String imgurKey;

        public void onError() {}

        public GetAlbumWithCallback(@NonNull String url, @NonNull Activity baseActivity) {

            this.baseActivity = baseActivity;

            hash = deriveAlbumHash(url);
            type = "album";

            client = Reddit.client;
            gson = new Gson();
            imgurKey = SecretConstants.getImgurApiKey(baseActivity);
        }

        /**
         * Hands the album's images to the caller.
         *
         * @return whether {@code data} holds anything to work with. False means nothing usable came
         *     back and {@link #onError()} has already been told; an override must return without
         *     touching the list, since indexing it would throw. The boolean is the contract rather
         *     than a bare void — matching {@link
         *     me.edgan.redditslide.Tumblr.TumblrUtils.GetTumblrPostWithCallback#doWithData} — because
         *     every override calls {@code super} first and the earlier void version let them carry on
         *     into an empty list.
         */
        public boolean doWithData(@Nullable List<Image> data) {
            if (data == null || data.isEmpty()) {
                onError();
                return false;
            }
            return true;
        }

        public void doWithDataSingle(final SingleImage data) {
            // convertToSingle returns null when the response is unusable. Adding that null would
            // hand the adapters a list containing null, which crashes them while the list measures;
            // an empty list routes to the failure path doWithData already has.
            final Image converted = convertToSingle(data);
            final ArrayList<Image> images = new ArrayList<>();
            if (converted != null) {
                images.add(converted);
            }
            doWithData(images);
        }

        /**
         * One API entry as an {@link Image}, or null when it has nothing usable in it.
         *
         * <p>Reports nothing itself, deliberately: {@link #onError()} is one report per load, and this
         * runs per entry — the album loop below calls it once for every image in the album. Reporting
         * from here put a modal "album not found" dialog over an album that had loaded, because one
         * of its entries was unusable, and reported twice for a single image (once here, once from
         * {@link #doWithData} being handed the empty list). Every caller already handles the null:
         * the album loop skips and logs it, {@code jsons.isEmpty()} reports an album where nothing
         * parsed, and {@link #doWithDataSingle} routes it into {@code doWithData}'s own error path.
         */
        @Nullable
        public Image convertToSingle(SingleImage data) {
            try {
                final Image toDo = new Image();
                final String link = data.getLink();
                boolean animated = data.getAnimated() != null ? data.getAnimated() : false;
                toDo.setAnimated(animated || (link != null && link.contains(".gif")));

                final Object mp4 = data.getAdditionalProperties().get("mp4");
                // "".equals(mp4) rather than mp4.equals(""): the key can be present with a null
                // value, which containsKey does not rule out.
                if (mp4 != null && !"".equals(mp4)) {
                    toDo.setHash(getHash(mp4.toString()));
                } else {
                    toDo.setHash(getHash(link));
                }

                toDo.setTitle(data.getTitle());
                // Without an extension there is no url to build: Image.getImageUrl() concatenates
                // hash and ext unconditionally, and its consumers (the grid, both pager pages, the
                // save paths, the peek view) are not null-tolerant. Exclude the entry rather than
                // letting a half-built one out, which doWithDataSingle turns into the error path.
                final int dot = link == null ? -1 : link.lastIndexOf('.');
                if (link == null || dot < 0) {
                    LogUtil.e("convertToSingle: no extension in link [" + link + "]");
                    return null;
                }
                toDo.setExt(link.substring(dot));
                // SingleImage's width and height are nullable Integers and are copied through as
                // such; AlbumView null-checks them before using them to reserve a row's height.
                toDo.setHeight(data.getHeight());
                toDo.setWidth(data.getWidth());

                return toDo;
            } catch (Exception e) {
                ObjectMapper objectMapper = new ObjectMapper();

                try {
                    String dataJson = objectMapper.writeValueAsString(data);
                    LogUtil.e(e, "convertToSingle error, data [" + dataJson + "]");
                } catch (JsonProcessingException ex) {
                    LogUtil.e(ex, "Error serializing data to JSON for logging");
                }

                return null;
            }
        }

        @Override
        @Nullable
        protected ArrayList<JsonElement> doInBackground(final String... sub) {
            String hash = this.hash;
            if (hash == null) {
                LogUtil.w("No imgur hash could be derived; nothing to fetch");
                if (baseActivity != null) {
                    baseActivity.runOnUiThread(this::onError);
                }
                return null;
            }
            if (hash.startsWith("/")) {
                hash = hash.substring(1);
                this.hash = hash;
            }

            // Entries stay null for a hash that was empty or whose fetch threw; the loop
            // below skips them.
            final @Nullable JsonElement[] target;
            final ArrayList<Image> jsons = new ArrayList<>();

            String[] hashes = hash.split(",");
            target = new JsonElement[hashes.length];

            for (int i = 0; i < hashes.length; i++) {
                final int pos = i;
                final String currentHash = hashes[i];

                if (currentHash == null || currentHash.trim().isEmpty()) {
                    LogUtil.w("Skipping empty hash part found in: " + hash);
                    target[pos] = null;
                    continue;
                }

                String apiUrl = "https://api.imgur.com/3/" + type + "/" + currentHash;
                LogUtil.v("Unified Imgur API call: " + apiUrl);

                try {
                    JsonObject result = HttpUtil.getImgurJsonObject(client, gson, apiUrl, imgurKey);
                    target[pos] = result;
                } catch (Exception e) {
                    LogUtil.e(e, "Error fetching from Imgur API for hash " + currentHash + ": " + apiUrl);
                    target[pos] = null;
                }
            }

            for (int i = 0; i < target.length; i++) {
                JsonElement el = target[i];

                if (el == null) continue;

                String currentHash = hashes[i];

                if (el.isJsonObject()) {
                    JsonObject resultObj = el.getAsJsonObject();

                    if (resultObj.has("success") && resultObj.get("success").getAsBoolean() && resultObj.has("data")) {
                        JsonObject dataObj = resultObj.getAsJsonObject("data");
                        boolean isAlbum = dataObj.has("is_album") && dataObj.get("is_album").getAsBoolean();

                        if (isAlbum) {
                            if (dataObj.has("images") && dataObj.get("images").isJsonArray()) {
                                for (JsonElement imageElement : dataObj.getAsJsonArray("images")) {
                                    try {
                                        SingleImage imageInData = new ObjectMapper().readValue(imageElement.toString(), SingleImage.class);

                                        if (imageInData != null) {
                                            Image convertedImage = convertToSingle(imageInData);

                                            if (convertedImage != null) {
                                                jsons.add(convertedImage);
                                                LogUtil.v("Parsed image " + imageInData.getId() + " from album " + currentHash);
                                            } else {
                                                LogUtil.w("convertToSingle returned null for image " + imageInData.getId() + " in album " + currentHash);
                                            }
                                        } else {
                                            LogUtil.w("Parsed SingleImage was null for an image within album " + currentHash + ". Element: " + imageElement.toString());
                                        }
                                    } catch (IOException e) {
                                        LogUtil.e(e, "Error parsing an image within Imgur album " + currentHash + ": " + imageElement.toString());
                                    } catch (Exception e) {
                                        LogUtil.e(e, "Unexpected error parsing an image within Imgur album " + currentHash + ": " + imageElement.toString());
                                    }
                                }
                            } else {
                                LogUtil.w("Imgur album response for hash " + currentHash + " is missing 'images' array or it's not an array. Data: " + dataObj.toString());
                            }
                        } else {
                            try {
                                SingleImage single = new ObjectMapper().readValue(dataObj.toString(), SingleImage.class);

                                if (single != null) {
                                    Image convertedImage = convertToSingle(single);

                                    if (convertedImage != null) {
                                        jsons.add(convertedImage);
                                        LogUtil.v("Parsed single image data for hash " + currentHash);
                                    } else {
                                        LogUtil.w("convertToSingle returned null for single image hash " + currentHash);
                                    }
                                } else {
                                    LogUtil.w("Parsed SingleImage was null for single image hash " + currentHash + ". Data: " + dataObj.toString());
                                }
                            } catch (IOException e) {
                                LogUtil.e(e, "Error parsing Imgur single image JSON response for hash " + currentHash + ": " + dataObj.toString());
                            } catch (Exception e) {
                                LogUtil.e(e, "Unexpected error parsing Imgur single image response for hash " + currentHash + ": " + dataObj.toString());
                            }
                        }
                    } else {
                        int status = resultObj.has("status") ? resultObj.get("status").getAsInt() : -1;
                        LogUtil.w("Imgur API call failed or missing 'data' for hash " + currentHash + ". Success: " + (resultObj.has("success") ? resultObj.get("success").getAsBoolean() : "N/A") + ", Status: " + status + ". Response: " + resultObj.toString());
                    }
                } else {
                    LogUtil.w("Non-object JSON element received for Imgur API call for hash: " + currentHash + ". Element: " + el.toString());
                }
            }

            if (baseActivity != null) {
                if (jsons.isEmpty()) {
                    LogUtil.w("No images successfully processed from hash(es): " + hash);
                    // Main thread, like the doWithData path below: the overrides show a dialog,
                    // start an activity or rebuild the peek view.
                    baseActivity.runOnUiThread(this::onError);
                } else {
                    baseActivity.runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                doWithData(jsons);
                            }
                        });
                }
            } else {
                LogUtil.w("baseActivity became null before processing Imgur results for hash(es): " + hash);
            }

            return null;
        }
    }
}
