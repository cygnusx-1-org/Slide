package me.edgan.redditslide.util;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import android.os.SystemClock;
import java.util.concurrent.atomic.AtomicReference;

import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceInputStream;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.dash.manifest.Representation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Activities.Website;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.ExoVideoView;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaCodec;

import org.apache.commons.io.IOUtils;

/** GIF handling utilities */
public class GifUtils {
    private static final String TAG = "GifUtils";

    /**
     * Create a notification that opens a newly-saved GIF
     *
     * @param docFile File referencing the GIF
     * @param c
     */
    private static final Object DIRECTORY_LOCK = new Object();

    private static void showErrorDialog(final Activity a) {
        DialogUtil.showErrorDialog((MediaView) a);
    }

    private static void showFirstDialog(final Activity a) {
        DialogUtil.showFirstDialog((MediaView) a);
    }

    public static void downloadGif(
            String url, GifDownloadCallback callback, Context context, String submissionTitle) {
        new DownloadGifTask(url, callback, context, submissionTitle)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void downloadGif(String url, GifDownloadCallback callback, Context context) {
        new DownloadGifTask(url, callback, context)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static class DownloadGifTask extends AsyncTask<Void, Void, File> {
        private String url;
        private GifDownloadCallback callback;
        private Context context;
        private Exception exception;
        private final String submissionTitle;

        DownloadGifTask(String url, GifDownloadCallback callback, Context context) {
            this.url = url;
            this.callback = callback;
            this.context = context.getApplicationContext();
            this.submissionTitle = null;
        }

        DownloadGifTask(
                String url, GifDownloadCallback callback, Context context, String submissionTitle) {
            this.url = url;
            this.callback = callback;
            this.context = context.getApplicationContext();
            this.submissionTitle = submissionTitle;
        }

        @Override
        protected File doInBackground(Void... voids) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder().url(url).build();
            Response response = null;
            try {
                response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    throw new Exception("Failed to download GIF: " + response);
                }

                // Create unique filename
                String uniquePartFromUrl;
                try {
                    Uri parsedUri = Uri.parse(url);
                    String lastSegment = parsedUri.getLastPathSegment();
                    if (lastSegment != null && lastSegment.length() > 3 && lastSegment.contains(".")) { // Basic check for a file-like segment
                        uniquePartFromUrl = lastSegment;
                    } else {
                        // Fallback to hash of URL if no good segment found
                        uniquePartFromUrl = String.valueOf(url.hashCode());
                    }
                } catch (Exception e) {
                    // Fallback to hash of URL on any parsing error
                    uniquePartFromUrl = String.valueOf(url.hashCode());
                }

                String fileNamePrefix;
                if (submissionTitle != null && !submissionTitle.trim().isEmpty()) {
                    // Use submission title as part of the prefix, sanitized
                    fileNamePrefix = FileUtil.getValidFileName(submissionTitle, "", "") + "_";
                } else {
                    // If no title, use a timestamp for the prefix
                    fileNamePrefix = System.currentTimeMillis() + "_";
                }

                // Combine prefix, unique part from URL, and ensure .gif extension
                String finalFileName = FileUtil.getValidFileName(fileNamePrefix + uniquePartFromUrl, "", ".gif");
                if (!finalFileName.toLowerCase().endsWith(".gif")) { // Ensure .gif extension if getValidFileName strips it
                    finalFileName += ".gif";
                }

                File gifFile = new File(context.getCacheDir(), finalFileName);
                Log.d(TAG, "Downloading GIF " + url + " to cache file: " + gifFile.getAbsolutePath());

                InputStream inputStream = response.body().byteStream();
                OutputStream outputStream = new FileOutputStream(gifFile);

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();

                return gifFile;
            } catch (Exception e) {
                Log.e("EmoteDebug", "Error downloading GIF", e);
                exception = e;
                return null;
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }

        @Override
        protected void onPostExecute(File gifFile) {
            if (gifFile != null) {
                callback.onGifDownloaded(gifFile);
            } else {
                callback.onGifDownloadFailed(
                        exception != null ? exception : new Exception("Unknown error"));
            }
        }
    }

    public interface GifDownloadCallback {
        void onGifDownloaded(File gifFile);

        void onGifDownloadFailed(Exception e);
    }

    public static void cacheSaveGif(
            Uri uri, Activity activity, String subreddit, String submissionTitle, boolean save, int index) {
        // Add debug logging
        Log.d(TAG, "cacheSaveGif called with submissionTitle: " + (submissionTitle != null ? "'" + submissionTitle + "'" : "null") + ", index: " + index);

        // Convert empty strings to null to avoid treating them as valid titles
        final String finalSubmissionTitle;
        if (submissionTitle != null && submissionTitle.trim().isEmpty()) {
            finalSubmissionTitle = null;
            Log.d(TAG, "Empty submission title, setting to null");
        } else {
            finalSubmissionTitle = submissionTitle;
        }

        final int finalIndex = index;

        if (save) {
            try {
                Toast.makeText(
                                activity,
                                activity.getString(R.string.mediaview_notif_video),
                                Toast.LENGTH_SHORT)
                        .show();
            } catch (Exception ignored) {
            }
        }

        Uri storageUri = StorageUtil.getStorageUri(activity);
        if (storageUri == null || !StorageUtil.hasStorageAccess(activity)) {
            Log.e(TAG, "No valid storage URI found.");
            showFirstDialog(activity);
            return;
        } else {
            new AsyncTask<Void, Integer, DocumentFile>() {
                NotificationManager notifMgr =
                        ContextCompat.getSystemService(activity, NotificationManager.class);
                Exception saveError;

                @Override
                protected DocumentFile doInBackground(Void... voids) {
                    NotificationManager notifMgr =
                            ContextCompat.getSystemService(activity, NotificationManager.class);
                    Exception saveError;
                    InputStream in = null;
                    OutputStream out = null;

                    try {
                        Log.d("GifUtils", "Starting save process for URI: " + uri);
                        DocumentFile parentDir = DocumentFile.fromTreeUri(activity, storageUri);
                        if (parentDir == null) {
                            saveError = new Exception("Could not access storage directory");
                            return null;
                        }

                        synchronized (DIRECTORY_LOCK) {
                            // Create subreddit subfolder if needed
                            if (SettingValues.imageSubfolders && !subreddit.isEmpty()) {
                                // Get all existing files/directories
                                DocumentFile[] existingFiles = parentDir.listFiles();
                                DocumentFile subFolder = null;

                                // First pass: exact match
                                for (DocumentFile file : existingFiles) {
                                    if (file.isDirectory()
                                            && file.getName() != null
                                            && file.getName().equals(subreddit)) {
                                        subFolder = file;
                                        break;
                                    }
                                }

                                // Second pass: case-insensitive match if exact match not found
                                if (subFolder == null) {
                                    for (DocumentFile file : existingFiles) {
                                        if (file.isDirectory()
                                                && file.getName() != null
                                                && file.getName().equalsIgnoreCase(subreddit)) {
                                            subFolder = file;
                                            break;
                                        }
                                    }
                                }

                                // Create only if no matching directory exists
                                if (subFolder == null) {
                                    subFolder = parentDir.createDirectory(subreddit);
                                    if (subFolder == null) {
                                        saveError =
                                                new Exception("Could not create subreddit folder");
                                        return null;
                                    }
                                }
                                parentDir = subFolder;
                            }

                            // Create output file with .mp4 extension
                            String fileName;
                            Log.d("GifUtils", "Creating file with submissionTitle: " + (finalSubmissionTitle != null ? "'" + finalSubmissionTitle + "'" : "null"));
                            if (finalSubmissionTitle != null && !finalSubmissionTitle.trim().isEmpty()) {
                                String fileIndex = finalIndex > -1 ? String.format(Locale.ENGLISH, "_%03d", finalIndex) : "";
                                fileName = FileUtil.getValidFileName(finalSubmissionTitle + fileIndex, "", ".mp4");
                            } else {
                                // If no title available, use a timestamp
                                String fileIndex = finalIndex > -1 ? String.format(Locale.ENGLISH, "_%03d", finalIndex) : "";
                                fileName = System.currentTimeMillis() + fileIndex + ".mp4";
                            }
                            Log.d("GifUtils", "Creating output file: " + fileName);
                            DocumentFile outDocFile = parentDir.createFile("video/mp4", fileName);
                            if (outDocFile == null) {
                                saveError = new Exception("Could not create output file");
                                return null;
                            }

                            // Open output stream first to ensure we have write access
                            out = activity.getContentResolver().openOutputStream(outDocFile.getUri());
                            if (out == null) {
                                saveError = new Exception("Could not open output stream");
                                return null;
                            }

                            String urlStr = uri.toString();
                            if (urlStr.contains("v.redd.it") && urlStr.contains("DASHPlaylist.mpd")) {
                                // Handle DASH video
                                DataSource.Factory downloader = new OkHttpDataSource.Factory(Reddit.client)
                                        .setUserAgent(activity.getString(R.string.app_name));
                                DataSource.Factory cacheDataSourceFactory = new CacheDataSource.Factory()
                                        .setCache(Reddit.videoCache)
                                        .setUpstreamDataSourceFactory(downloader);

                                InputStream dashManifestStream = new DataSourceInputStream(
                                        cacheDataSourceFactory.createDataSource(),
                                        new DataSpec(Uri.parse(urlStr)));

                                DashManifest dashManifest = new DashManifestParser().parse(Uri.parse(urlStr), dashManifestStream);
                                dashManifestStream.close();

                                // Find highest quality video URL and audio URL
                                String videoUrl = null;
                                String audioUrl = null;
                                int maxBitrate = 0;

                                for (int i = 0; i < dashManifest.getPeriodCount(); i++) {
                                    for (AdaptationSet as : dashManifest.getPeriod(i).adaptationSets) {
                                        for (Representation r : as.representations) {
                                            if (MimeTypes.isAudio(r.format.sampleMimeType)) {
                                                audioUrl = r.baseUrls.get(0).url.toString();
                                            } else if (r.format.bitrate > maxBitrate) {
                                                maxBitrate = r.format.bitrate;
                                                videoUrl = r.baseUrls.get(0).url.toString();
                                            }
                                        }
                                    }
                                }

                                if (videoUrl == null) {
                                    saveError = new Exception("Could not find video stream in DASH manifest");
                                    return null;
                                }

                                // Create temporary files for video and audio
                                File videoFile = new File(activity.getCacheDir(), "temp_video.mp4");
                                File audioFile = audioUrl != null ? new File(activity.getCacheDir(), "temp_audio.mp4") : null;
                                File outputFile = new File(activity.getCacheDir(), "temp_output.mp4");

                                // Download video
                                Request videoRequest = new Request.Builder().url(videoUrl).build();
                                Response videoResponse = Reddit.client.newCall(videoRequest).execute();
                                if (!videoResponse.isSuccessful()) {
                                    saveError = new Exception("Failed to download video: " + videoResponse);
                                    return null;
                                }
                                FileOutputStream videoOut = new FileOutputStream(videoFile);
                                IOUtils.copy(videoResponse.body().byteStream(), videoOut);
                                videoOut.close();

                                // Download audio if available
                                if (audioUrl != null) {
                                    Request audioRequest = new Request.Builder().url(audioUrl).build();
                                    Response audioResponse = Reddit.client.newCall(audioRequest).execute();
                                    if (!audioResponse.isSuccessful()) {
                                        saveError = new Exception("Failed to download audio: " + audioResponse);
                                        return null;
                                    }
                                    FileOutputStream audioOut = new FileOutputStream(audioFile);
                                    IOUtils.copy(audioResponse.body().byteStream(), audioOut);
                                    audioOut.close();
                                }

                                // Mux video and audio if needed
                                boolean muxSuccess;
                                if (audioFile != null) {
                                    muxSuccess = mux(videoFile.getAbsolutePath(), audioFile.getAbsolutePath(), outputFile.getAbsolutePath());
                                } else {
                                    // Just copy video file if no audio
                                    try (FileInputStream fis = new FileInputStream(videoFile);
                                        FileOutputStream fos = new FileOutputStream(outputFile)) {
                                        byte[] buffer = new byte[8192];
                                        int bytesRead;
                                        while ((bytesRead = fis.read(buffer)) != -1) {
                                            fos.write(buffer, 0, bytesRead);
                                        }
                                    }
                                    muxSuccess = true;
                                }

                                if (!muxSuccess) {
                                    saveError = new Exception("Failed to mux video and audio");
                                    return null;
                                }

                                // Copy final file to destination using streams
                                try (FileInputStream fis = new FileInputStream(outputFile)) {
                                    byte[] buffer = new byte[8192];
                                    int bytesRead;
                                    while ((bytesRead = fis.read(buffer)) != -1) {
                                        out.write(buffer, 0, bytesRead);
                                    }
                                    out.flush();
                                }

                                // Cleanup temp files
                                videoFile.delete();
                                if (audioFile != null) audioFile.delete();
                                outputFile.delete();

                            } else {
                                // Handle non-DASH video as before
                                Request videoRequest = new Request.Builder().url(urlStr).build();
                                Response videoResponse = Reddit.client.newCall(videoRequest).execute();

                                if (!videoResponse.isSuccessful()) {
                                    saveError = new Exception("Failed to download video: " + videoResponse);
                                    return null;
                                }

                                // Copy response to output using streams
                                in = videoResponse.body().byteStream();
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, bytesRead);
                                }
                                out.flush();
                            }

                            return outDocFile;
                        }
                    } catch (Exception e) {
                        LogUtil.e(e, "Error saving video");
                        saveError = e;
                        return null;
                    } finally {
                        try {
                            if (in != null) in.close();
                        } catch (IOException e) {
                            LogUtil.e(e, "Error closing input stream");
                        }
                        try {
                            if (out != null) out.close();
                        } catch (IOException e) {
                            LogUtil.e(e, "Error closing output stream");
                        }
                    }
                }

                @Override
                protected void onPostExecute(DocumentFile result) {
                    if (save) {
                        notifMgr.cancel(1);
                        if (result == null) {
                            Log.e(
                                    "GifUtils",
                                    "Save failed: "
                                            + (saveError != null
                                                    ? saveError.getMessage()
                                                    : "Unknown error"));
                            showErrorDialog(activity);
                        }
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public static void cacheSaveGif(
            Uri uri, Activity activity, String subreddit, String submissionTitle, boolean save) {
        // Call the new overloaded method with default index of -1
        cacheSaveGif(uri, activity, subreddit, submissionTitle, save, -1);
    }

    public static class AsyncLoadGif extends AsyncTask<String, Void, Uri> {

        private Activity c;
        private ExoVideoView video;
        private ProgressBar progressBar;
        private View placeholder;
        private boolean closeIfNull;
        private boolean autostart;
        public String subreddit;
        public String submissionTitle;

        private TextView size;
        private boolean wasPlayingBeforePause = false;

        public AsyncLoadGif(
                @NonNull Activity c,
                @NonNull ExoVideoView video,
                @Nullable ProgressBar p,
                @Nullable View placeholder,
                boolean closeIfNull,
                boolean autostart,
                String subreddit) {
            this.c = c;
            this.subreddit = subreddit;
            this.video = video;
            this.progressBar = p;
            this.closeIfNull = closeIfNull;
            this.placeholder = placeholder;
            this.autostart = autostart;
        }

        public AsyncLoadGif(
                @NonNull Activity c,
                @NonNull ExoVideoView video,
                @Nullable ProgressBar p,
                @Nullable View placeholder,
                boolean closeIfNull,
                boolean autostart,
                TextView size,
                String subreddit,
                String submissionTitle) {
            this.c = c;
            this.video = video;
            this.subreddit = subreddit;
            this.progressBar = p;
            this.closeIfNull = closeIfNull;
            this.placeholder = placeholder;
            this.autostart = autostart;
            this.size = size;
            this.submissionTitle = submissionTitle;
        }

        public void onError() {}

        public void cancel() {
            LogUtil.v("cancelling");
            video.stop();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        public static Gson gson = new Gson();

        public enum VideoType {
            IMGUR,
            STREAMABLE,
            TUMBLR,
            GFYCAT,
            DIRECT,
            OTHER,
            VREDDIT,
            REDGIFS,
            REDDIT_GALLERY;

            public boolean shouldLoadPreview() {
                return this == OTHER;
            }
        }

        /**
         * Format a video URL correctly and strip unnecessary parts
         *
         * @param s URL to format
         * @return Formatted URL
         */
        public static String formatUrl(String s) {
            if (s.endsWith("v") && !s.contains("streamable.com")) {
                s = s.substring(0, s.length() - 1);
            } else if (s.contains("gfycat") && (!s.contains("mp4") && !s.contains("webm"))) {
                s = s.replace("-size_restricted", "");
                s = s.replace(".gif", "");
            }

            if ((s.contains(".webm") || s.contains(".gif"))
                    && !s.contains(".gifv")
                    && s.contains("imgur.com")) {
                s = s.replace(".gif", ".mp4");
                s = s.replace(".webm", ".mp4");
            }

            if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
            if (s.endsWith("?r")) s = s.substring(0, s.length() - 2);

            if (s.contains("v.redd.it") && !s.contains("DASHPlaylist")) {
                if (s.contains("DASH")) {
                    s = s.substring(0, s.indexOf("DASH"));
                }
                if (s.endsWith("/")) {
                    s = s.substring(0, s.length() - 1);
                }

                s += "/DASHPlaylist.mpd";
            }

            return s;
        }

        /**
         * Identifies the type of a video URL
         *
         * @param url URL to identify the type of
         * @return The type of video
         */
        public static VideoType getVideoType(String url) {
            String realURL = url.toLowerCase(Locale.ENGLISH);

            if (realURL.contains("i.redd.it/gallery/")) {
                return VideoType.REDDIT_GALLERY;
            }

            if (realURL.contains("v.redd.it")) {
                return VideoType.VREDDIT;
            }

            if (realURL.contains(".mp4")
                    || realURL.contains("webm")
                    || realURL.contains("redditmedia.com")
                    || realURL.contains("preview.redd.it")) {
                return VideoType.DIRECT;
            }

            if (realURL.contains("gfycat") && !realURL.contains("mp4")) return VideoType.GFYCAT;

            if (realURL.contains("redgifs.com") && !realURL.contains("mp4")) return VideoType.REDGIFS;

            if (realURL.contains("imgur.com")) return VideoType.IMGUR;

            if (realURL.contains("streamable.com")) return VideoType.STREAMABLE;

            if (realURL.contains("tumblr.com")) return VideoType.TUMBLR;

            return VideoType.OTHER;
        }

        public static Map<String, String> makeHeaderMap(String domain) {
            Map<String, String> map = new HashMap<>();
            map.put("Host", domain);
            map.put("Sec-Fetch-Dest", "empty");
            map.put("Sec-Fetch-Mode", "cors");
            map.put("Sec-Fetch-Site", "same-origin");
            map.put(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; rv:107.0) Gecko/20100101 Firefox/107.0");
            return map;
        }

        /**
         * Get an API response for a given host and gfy name
         *
         * @param host the host to send the req to
         * @param name the name of the gfy
         * @return the result
         */
        public static JsonObject getApiResponse(String host, String name) {
            String domain = "api." + host + ".com";
            String gfycatUrl = "https://" + domain + "/v1/gfycats" + name;

            return HttpUtil.getJsonObject(client, gson, gfycatUrl, makeHeaderMap(domain));
        }

        /**
         * Get the correct mp4/mobile url from a given result JsonObject
         *
         * @param result the result to check
         * @return the video url
         */
        public static String getUrlFromApi(JsonObject result) {
            if (!SettingValues.hqgif && result.getAsJsonObject("gfyItem").has("mobileUrl")) {
                return result.getAsJsonObject("gfyItem").get("mobileUrl").getAsString();
            } else {
                return result.getAsJsonObject("gfyItem").get("mp4Url").getAsString();
            }
        }

        public static OkHttpClient client = Reddit.client;

        public static final AtomicReference<AuthToken> TOKEN = new AtomicReference<>(new AuthToken("", 0));

        public static class AuthToken {
            @NonNull public final String token;
            private final long expireAt;

            private AuthToken(@NonNull String token, final long expireAt) {
                this.token = token;
                this.expireAt = expireAt;
            }

            public static AuthToken expireIn1day(@NonNull String token) {
                // 23 not 24 to give an hour leeway
                long expireTime = 1000 * 60 * 60 * 23;
                return new AuthToken(token, SystemClock.uptimeMillis() + expireTime);
            }

            public boolean isValid() {
                return !token.isEmpty() && expireAt > SystemClock.uptimeMillis();
            }
        }


        public static AuthToken getNewToken(OkHttpClient client) throws IOException {
            Request tokenRequest = new Request.Builder()
                .url("https://api.redgifs.com/v2/auth/temporary")
                .get()
                .build();
            Response tokenResponse = client.newCall(tokenRequest).execute();
            JsonObject tokenResult = gson.fromJson(tokenResponse.body().string(), JsonObject.class);
            String accessToken = tokenResult.get("token").getAsString();
            AuthToken newToken = AuthToken.expireIn1day(accessToken);
            TOKEN.set(newToken);
            return newToken;
        }

        public static Response makeApiCall(OkHttpClient client, String name, AuthToken currentToken) throws IOException {
            Request request = new Request.Builder()
                .url("https://api.redgifs.com/v2/gifs/" + name)
                .header("Authorization", "Bearer " + currentToken.token)
                .build();
            return client.newCall(request).execute();
        }

        public static Uri loadRedGifs(String name, String fullUrl, Activity c, ProgressBar progressBar, boolean closeIfNull, Runnable onErrorCallback) {
            showProgressBar(c, progressBar, true);

            // Remove leading slash if present
            if (name.startsWith("/")) name = name.substring(1);

            try {
                // Check if existing token is valid
                AuthToken currentToken = TOKEN.get();
                if (!currentToken.isValid()) {
                    currentToken = getNewToken(client);
                }

                // Call RedGifs API with token
                Response response = makeApiCall(client, name, currentToken);

                // If we get a 401, try once more with a new token
                if (response.code() == 401) {
                    currentToken = getNewToken(client);
                    response = makeApiCall(client, name, currentToken);
                }

                // Process the response
                JsonObject result = gson.fromJson(response.body().string(), JsonObject.class);
                if (result == null || !result.has("gif")) {
                    if (onErrorCallback != null) onErrorCallback.run();
                    if (closeIfNull && c != null) {
                        c.runOnUiThread(() -> {
                            // TODO: Consider showing a generic error dialog here
                            Log.e(TAG, "Failed to load RedGifs, result invalid: " + fullUrl);
                        });
                    }
                    return null;
                }

                JsonObject gif = result.getAsJsonObject("gif");
                String url = !SettingValues.hqgif && gif.getAsJsonObject("urls").has("sd") ?
                    gif.getAsJsonObject("urls").get("sd").getAsString() :
                    gif.getAsJsonObject("urls").get("hd").getAsString();
                return Uri.parse(url);
            } catch (Exception e) {
                LogUtil.e(e, "Error loading RedGifs video url = [" + fullUrl + "]");
                if (onErrorCallback != null) onErrorCallback.run();
                if (closeIfNull && c != null) {
                    c.runOnUiThread(() -> openWebsite(c, fullUrl));
                }
                return null;
            }
        }

        /**
         * Load the correct URL for a gfycat gif
         *
         * @param name Name of the gfycat gif
         * @param fullUrl full URL to the gfycat
         * @param gson
         * @return Correct URL
         */
        public static Uri loadGfycat(String name, String fullUrl, Activity c, ProgressBar progressBar, boolean closeIfNull, Runnable onErrorCallback) {
            showProgressBar(c, progressBar, true);
            String host = "gfycat";
            if (fullUrl.contains("redgifs")) {
                host = "redgifs";
            }
            if (!name.startsWith("/")) name = "/" + name;
            if (name.contains("-")) {
                name = name.split("-")[0];
            }
            final JsonObject result = getApiResponse(host, name);
            if (result == null
                    || result.get("gfyItem") == null
                    || result.getAsJsonObject("gfyItem").get("mp4Url").isJsonNull()) {
                // If the result null, the gfycat link may be redirecting to gifdeliverynetwork
                // which is powered by redgifs.
                // Try getting the redirected url from gfycat and check if redirected url is
                // gifdeliverynetwork and if it is,
                // we fetch the actual .mp4/.webm url from the redgifs api
                if (result == null) {
                    try {
                        URL newUrl = new URL(fullUrl);
                        HttpURLConnection ucon = (HttpURLConnection) newUrl.openConnection();
                        ucon.setInstanceFollowRedirects(false);
                        String secondURL = new URL(ucon.getHeaderField("location")).toString();
                        if (secondURL.contains("gifdeliverynetwork")) {
                            return Uri.parse(
                                    getUrlFromApi(getApiResponse("redgifs", name.toLowerCase())));
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (onErrorCallback != null) onErrorCallback.run();
                if (closeIfNull && c != null) {
                    c.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        new AlertDialog.Builder(c)
                                                .setTitle(R.string.gif_err_title)
                                                .setMessage(R.string.gif_err_msg)
                                                .setCancelable(false)
                                                .setPositiveButton(
                                                        R.string.btn_ok,
                                                        (dialog, which) -> c.finish())
                                                .setNeutralButton(
                                                        R.string.open_externally,
                                                        (dialog, which) -> {
                                                            LinkUtil.openExternally(fullUrl);
                                                            c.finish();
                                                        })
                                                .create()
                                                .show();
                                    } catch (Exception ignored) {
                                    }
                                }
                            });
                }

                return null;
            }

            return Uri.parse(getUrlFromApi(result));
        }

        // Handles failures of loading a DASH mp4 or muxing a Reddit video
        private void catchVRedditFailure(Exception e, String url) {
            LogUtil.e(e, "Error loading URL " + url); // Most likely is an image, not a gif!
            if (c instanceof MediaView && url.contains("imgur.com") && url.endsWith(".mp4")) {
                c.runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                (c).startActivity(new Intent(c, MediaView.class).putExtra(MediaView.EXTRA_URL, url.replace(".mp4", ".png"))); // Link is likely an image and not a gif
                                (c).finish();
                            }
                        });
            } else {
                openWebsite(c, url); // Pass activity context
            }
        }

        @Override
        protected Uri doInBackground(String... sub) {
            final String url = formatUrl(sub[0]);
            VideoType videoType = getVideoType(url);
            LogUtil.v(url + ", VideoType: " + videoType);
            if (size != null) {
                getRemoteFileSize(url, client, size, c);
            }
            switch (videoType) {
                case REDGIFS:
                    String id = url.substring(url.lastIndexOf("/"));
                    String redgifsUrl = "https://api.redgifs.com/v2/gifs/" + id;

                    return loadRedGifs(id, url, c, progressBar, closeIfNull, this::onError);
                case VREDDIT:
                    return Uri.parse(url);
                case GFYCAT:
                    String name = url.substring(url.lastIndexOf("/"));
                    Uri uri = loadGfycat(name, url, c, progressBar, closeIfNull, this::onError);
                    if (uri != null && uri.toString().contains("gifdeliverynetwork")) {
                        openWebsite(c, url);

                        return null;
                    } else {
                        return uri;
                    }
                case REDDIT_GALLERY:
                    return Uri.parse(url);
                case DIRECT:
                case TUMBLR:
                    return Uri.parse(url);
                case IMGUR:
                    try {
                        return Uri.parse(url);
                    } catch (Exception e) {
                        LogUtil.e(
                                e,
                                "Error loading URL " + url); // Most likely is an image, not a gif!
                        if (c instanceof MediaView
                                && url.contains("imgur.com")
                                && url.endsWith(".mp4")) {
                            c.runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            (c).startActivity(new Intent(c, MediaView.class).putExtra(MediaView.EXTRA_URL, url.replace(".mp4", ".png"))); // Link is likely an image and not a gif
                                            (c).finish();
                                        }
                                    });
                        } else {
                            openWebsite(c, url);
                        }
                    }
                    break;
                case STREAMABLE:
                    String hash = url.substring(url.lastIndexOf("/") + 1);
                    String streamableUrl = "https://api.streamable.com/videos/" + hash;
                    LogUtil.v(streamableUrl);
                    try {
                        final JsonObject result =
                                HttpUtil.getJsonObject(client, gson, streamableUrl);
                        String obj;
                        if (result == null || result.get("files") == null || !(result.getAsJsonObject("files").has("mp4") || result.getAsJsonObject("files").has("mp4-mobile"))) {
                            onError();
                            if (closeIfNull && c != null) {
                                c.runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                try { // Added try-catch for safety
                                                    new AlertDialog.Builder(c)
                                                            .setTitle(R.string.error_video_not_found)
                                                            .setMessage(R.string.error_video_message)
                                                            .setCancelable(false)
                                                            .setPositiveButton(R.string.btn_ok, (dialog, which) -> c.finish())
                                                            .create()
                                                            .show();
                                                } catch (Exception ignored) {}
                                            }
                                        });
                            }
                            // Return null if streamable fails
                            return null;
                        } else {
                            if (result.getAsJsonObject().get("files").getAsJsonObject().has("mp4-mobile") && !result.getAsJsonObject().get("files").getAsJsonObject().get("mp4-mobile").getAsJsonObject().get("url").getAsString().isEmpty()) {
                                obj = result.getAsJsonObject().get("files").getAsJsonObject().get("mp4-mobile").getAsJsonObject().get("url").getAsString();
                            } else {
                                obj = result.getAsJsonObject().get("files").getAsJsonObject().get("mp4").getAsJsonObject().get("url").getAsString();
                            }
                            return Uri.parse(obj);
                        }
                    } catch (Exception e) {
                        LogUtil.e(
                                e,
                                "Error loading streamable video url = ["
                                        + url
                                        + "] streamableUrl = ["
                                        + streamableUrl
                                        + "]");

                        if (c != null) c.runOnUiThread(this::onError);
                        if (closeIfNull && c != null) {
                            c.runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                new AlertDialog.Builder(c)
                                                        .setTitle(R.string.error_video_not_found)
                                                        .setMessage(R.string.error_video_message)
                                                        .setCancelable(false)
                                                        .setPositiveButton(R.string.btn_ok, (dialog, which) -> c.finish())
                                                        .create()
                                                        .show();
                                            } catch (Exception ignored) {
                                            }
                                        }
                                    });
                        }
                    }
                    break;
                case OTHER:
                    LogUtil.e("We shouldn't be here!");
                    openWebsite(c, url);
                    break;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Uri uri) {
            if (uri == null || video == null) {
                cancel();
                return;
            }

            if (progressBar != null) {
                progressBar.setIndeterminate(true);
            }

            try {
                ExoVideoView.VideoType type =
                    uri.getHost().equals("v.redd.it")
                        ? ExoVideoView.VideoType.DASH
                        : ExoVideoView.VideoType.STANDARD;

                video.setVideoURI(uri, type, new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (progressBar == null) return;

                        if (playbackState == Player.STATE_READY) {
                            progressBar.setVisibility(View.GONE);
                            if (size != null) {
                                size.setVisibility(View.GONE);
                            }
                        } else if (playbackState == Player.STATE_BUFFERING) {
                            progressBar.setVisibility(View.VISIBLE);
                            if (size != null) {
                                size.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                });

                if (autostart) {
                    video.play();
                }
            } catch (Exception e) {
                LogUtil.e(e, "Error setting video URI");
                cancel();
            }
        }

        /**
         * Get a remote video's file size
         *
         * @param url URL of video (or v.redd.it DASH manifest) to get
         * @param client OkHttpClient
         * @param sizeText TextView to put size into
         * @param c Activity
         */
        static void getRemoteFileSize(
                String url, OkHttpClient client, final TextView sizeText, Activity c) {
            if (!url.contains("v.redd.it")) {
                Request request = new Request.Builder().url(url).head().build();
                Response response;
                try {
                    response = client.newCall(request).execute();
                    final long size = response.body().contentLength();
                    response.close();
                    c.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    sizeText.setText(FileUtil.readableFileSize(size));
                                }
                            });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                DataSource.Factory downloader =
                        new OkHttpDataSource.Factory(Reddit.client)
                                .setUserAgent(c.getString(R.string.app_name));
                DataSource.Factory cacheDataSourceFactory =
                        new CacheDataSource.Factory()
                                .setCache(Reddit.videoCache)
                                .setUpstreamDataSourceFactory(downloader);
                InputStream dashManifestStream =
                        new DataSourceInputStream(
                                cacheDataSourceFactory.createDataSource(),
                                new DataSpec(Uri.parse(url)));
                try {
                    DashManifest dashManifest =
                            new DashManifestParser().parse(Uri.parse(url), dashManifestStream);
                    dashManifestStream.close();
                    long videoSize = 0;
                    long audioSize = 0;

                    for (int i = 0; i < dashManifest.getPeriodCount(); i++) {
                        for (AdaptationSet as : dashManifest.getPeriod(i).adaptationSets) {
                            boolean isAudio = false;
                            int bitrate = 0;
                            String hqUri = null;
                            for (Representation r : as.representations) {
                                if (r.format.bitrate > bitrate) {
                                    bitrate = r.format.bitrate;
                                    hqUri = r.baseUrls.get(0).url.toString();
                                }
                                if (MimeTypes.isAudio(r.format.sampleMimeType)) {
                                    isAudio = true;
                                }
                            }

                            Request request = new Request.Builder().url(hqUri).head().build();
                            Response response = null;
                            try {
                                response = client.newCall(request).execute();
                                if (isAudio) {
                                    audioSize = response.body().contentLength();
                                } else {
                                    videoSize = response.body().contentLength();
                                }
                                response.close();
                            } catch (IOException e) {
                                if (response != null) response.close();
                            }
                        }
                    }
                    final long totalSize = videoSize + audioSize;
                    c.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    // We can't know which quality will be selected, so we display <= the highest quality size
                                    if (totalSize > 0) {
                                        sizeText.setText("≤ " + FileUtil.readableFileSize(totalSize));
                                    }
                                }
                            });
                } catch (IOException ignored) {
                }
            }
        }

        public static void openWebsite(Activity c, String url) {
            Intent web = new Intent(c, Website.class);
            web.putExtra(LinkUtil.EXTRA_URL, url);
            web.putExtra(LinkUtil.EXTRA_COLOR, Color.BLACK);
            c.startActivity(web);
            c.finish();
        }

        public void onPause() {
            if (video != null) {
                wasPlayingBeforePause = video.isPlaying();
                video.pause();
            }
        }

        public void onResume() {
            if (video != null && !wasPlayingBeforePause) {
                video.pause();
            }
        }
    }

    /**
     * Shows a ProgressBar in the UI. If this method is called from a non-main thread, it will run
     * the UI code on the main thread
     *
     * @param activity The activity context to use to display the ProgressBar
     * @param progressBar The ProgressBar to display
     * @param isIndeterminate True to show an indeterminate ProgressBar, false otherwise
     */
    private static void showProgressBar(
            final Activity activity, final ProgressBar progressBar, final boolean isIndeterminate) {
        if (activity == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // Current Thread is Main Thread.
            if (progressBar != null) progressBar.setIndeterminate(isIndeterminate);
        } else {
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (progressBar != null) progressBar.setIndeterminate(isIndeterminate);
                        }
                    });
        }
    }

    /**
     * Mux a video and audio file (e.g. from DASH) together into a single video using MediaMuxer
     *
     * @param videoFile Video file path
     * @param audioFile Audio file path
     * @param outputFile Output file path
     * @return Whether the muxing completed successfully
     */
    private static boolean mux(String videoFile, String audioFile, String outputFile) {
        MediaMuxer muxer = null;
        MediaExtractor videoExtractor = null;
        MediaExtractor audioExtractor = null;

        try {
            // Create muxer
            muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // Set up video extractor
            videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(videoFile);

            // Set up audio extractor
            audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(audioFile);

            // Add tracks and get track IDs
            int videoTrackIndex = -1;
            int audioTrackIndex = -1;

            // Add video track
            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                MediaFormat format = videoExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i);
                    videoTrackIndex = muxer.addTrack(format);
                    break;
                }
            }

            // Add audio track
            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i);
                    audioTrackIndex = muxer.addTrack(format);
                    break;
                }
            }

            // Start muxing
            muxer.start();

            // Write samples
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024); // 1MB buffer
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            // Write video samples
            while (true) {
                int sampleSize = videoExtractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;

                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                bufferInfo.flags = videoExtractor.getSampleFlags();

                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo);
                videoExtractor.advance();
            }

            // Write audio samples
            while (true) {
                int sampleSize = audioExtractor.readSampleData(buffer, 0);
                if (sampleSize < 0) break;

                bufferInfo.offset = 0;
                bufferInfo.size = sampleSize;
                bufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                bufferInfo.flags = audioExtractor.getSampleFlags();

                muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo);
                audioExtractor.advance();
            }

            return true;

        } catch (Exception e) {
            LogUtil.e(e, "Error muxing video");
            return false;
        } finally {
            // Clean up resources
            if (muxer != null) {
                try {
                    muxer.stop();
                    muxer.release();
                } catch (Exception e) {
                    LogUtil.e(e, "Error releasing muxer");
                }
            }
            if (videoExtractor != null) {
                videoExtractor.release();
            }
            if (audioExtractor != null) {
                audioExtractor.release();
            }
        }
    }
}
