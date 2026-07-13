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

/** Created by carlo_000 on 2/1/2016. */
public class AlbumUtils {

    public static SharedPreferences albumRequests;

    public static String substringAfterLastDash(String s) {
        if (s == null) {
            return null;
        }

        int lastDashIndex = s.lastIndexOf('-');

        // Only return the substring if a dash is found.
        if (lastDashIndex != -1) {
            return s.substring(lastDashIndex + 1);
        } else {
            return null;
        }
    }

    private static String getHash(String s) {
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
    static String deriveAlbumHash(String url) {
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
    public static String getFirstAlbumImageUrlBlocking(final Context context, final String url) {
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

        public String hash;
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

        public void doWithData(List<Image> data) {
            if (data == null || data.isEmpty()) {
                onError();
            }
        }

        public void doWithDataSingle(final SingleImage data) {
            doWithData(
                new ArrayList<Image>() {
                    {
                        this.add(convertToSingle(data));
                    }
                });
        }

        public Image convertToSingle(SingleImage data) {
            try {
                final Image toDo = new Image();
                boolean animated = data.getAnimated() != null ? data.getAnimated() : false;
                toDo.setAnimated(animated || data.getLink().contains(".gif"));

                if (data.getAdditionalProperties().containsKey("mp4")
                        && !data.getAdditionalProperties().get("mp4").equals("")) {
                    toDo.setHash(getHash(data.getAdditionalProperties().get("mp4").toString()));
                } else {
                    toDo.setHash(getHash(data.getLink()));
                }

                toDo.setTitle(data.getTitle());
                toDo.setExt(data.getLink().substring(data.getLink().lastIndexOf(".")));
                toDo.setHeight(data.getHeight());
                toDo.setWidth(data.getWidth());

                return toDo;
            } catch (Exception e) {
                ObjectMapper objectMapper = new ObjectMapper();

                try {
                    String dataJson = data != null ? objectMapper.writeValueAsString(data) : "null";
                    LogUtil.e(e, "convertToSingle error, data [" + dataJson + "]");
                } catch (JsonProcessingException ex) {
                    LogUtil.e(ex, "Error serializing data to JSON for logging");
                }

                onError();

                return null;
            }
        }

        JsonElement[] target;
        int count;
        int done;

        AlbumImage album;

        @Override
        protected ArrayList<JsonElement> doInBackground(final String... sub) {
            if (hash.startsWith("/")) {
                hash = hash.substring(1);
            }

            final JsonElement[] target;
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
                    onError();
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
