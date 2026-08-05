package me.edgan.redditslide.util;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSourceInputStream;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.dash.manifest.AdaptationSet;
import androidx.media3.exoplayer.dash.manifest.DashManifest;
import androidx.media3.exoplayer.dash.manifest.DashManifestParser;
import androidx.media3.exoplayer.dash.manifest.Representation;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Activities.Website;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.ExoVideoView;
import net.dean.jraw.models.Submission;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.NullMarked;

/** GIF handling utilities */
@NullMarked
@OptIn(markerClass = UnstableApi.class)
public class GifUtils {
    private static final String TAG = "GifUtils";

    /**
     * Create a notification that opens a newly-saved GIF
     *
     * @param docFile File referencing the GIF
     * @param c
     */
    private static final Object DIRECTORY_LOCK = new Object();

    public static void doNotifGif(DocumentFile docFile, Activity c) {
        try {
            final Intent shareIntent = new Intent(Intent.ACTION_VIEW);
            shareIntent.setDataAndType(docFile.getUri(), "video/mp4");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            PendingIntent contentIntent =
                    PendingIntent.getActivity(
                            c,
                            0,
                            shareIntent,
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

            Notification notif =
                    new NotificationCompat.Builder(c, Reddit.CHANNEL_IMG)
                            .setContentTitle(c.getString(R.string.gif_saved))
                            .setSmallIcon(R.drawable.ic_save)
                            .setContentIntent(contentIntent)
                            .build();

            NotificationManager mNotificationManager =
                    ContextCompat.getSystemService(c, NotificationManager.class);
            if (mNotificationManager != null) {
                mNotificationManager.notify((int) System.currentTimeMillis(), notif);
            }
        } catch (Exception e) {
            Log.e("GifUtils", "Error showing notification", e);
        }
    }

    private static void showErrorDialog(final Activity a) {
        DialogUtil.showErrorDialog((MediaView) a);
    }

    private static void showFirstDialog(final Activity a) {
        DialogUtil.showFirstDialog((MediaView) a);
    }

    public static void downloadGif(
            String url,
            GifDownloadCallback callback,
            Context context,
            @Nullable String submissionTitle) {
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
        @Nullable private Exception exception;
        @Nullable private final String submissionTitle;

        DownloadGifTask(String url, GifDownloadCallback callback, Context context) {
            this.url = url;
            this.callback = callback;
            this.context = context.getApplicationContext();
            this.submissionTitle = null;
        }

        DownloadGifTask(
                String url,
                GifDownloadCallback callback,
                Context context,
                @Nullable String submissionTitle) {
            this.url = url;
            this.callback = callback;
            this.context = context.getApplicationContext();
            this.submissionTitle = submissionTitle;
        }

        @Override
        protected @Nullable File doInBackground(Void... voids) {
            // Reuse the shared client: a new OkHttpClient brings its own dispatcher, thread pool
            // and connection pool, so every GIF download started from scratch.
            OkHttpClient client = Reddit.client;
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
        protected void onPostExecute(@Nullable File gifFile) {
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

    /**
     * Returns true if the given URL will be saved as an mp4 (the GIF/video save path's default).
     * Returns false only when the URL points to a real .gif file we'll save with a .gif extension.
     */
    public static boolean willSaveAsMp4(Uri uri) {
        if (uri == null) return true;
        String lc = uri.toString().toLowerCase(Locale.ENGLISH);
        int q = lc.indexOf('?');
        String path = q < 0 ? lc : lc.substring(0, q);
        boolean isDash = lc.contains("v.redd.it") && lc.contains("dashplaylist.mpd");
        if (isDash || path.endsWith(".mp4") || lc.contains("format=mp4")) return true;
        return !path.endsWith(".gif");
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
                int toastMsg = willSaveAsMp4(uri)
                        ? R.string.mediaview_notif_video
                        : R.string.mediaview_notif_title;
                Toast.makeText(activity, activity.getString(toastMsg), Toast.LENGTH_SHORT).show();
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
                        Objects.requireNonNull(
                                ContextCompat.getSystemService(
                                        activity, NotificationManager.class));
                @Nullable Exception saveError;

                @Override
                protected @Nullable DocumentFile doInBackground(Void... voids) {
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

                            // Create media-type subfolder if needed. Videos and GIFs share the
                            // "videos" folder.
                            if (SettingValues.imageTypeSubfolders) {
                                DocumentFile typeFolder =
                                        FileUtil.getOrCreateDirectory(parentDir, "videos");
                                if (typeFolder == null) {
                                    saveError = new Exception("Could not create type folder");
                                    return null;
                                }
                                parentDir = typeFolder;
                            }

                            // Pick extension/mime from the source URL. DASH and reddit
                            // preview URLs always yield mp4; tumblr .gif URLs yield gif bytes.
                            boolean savesAsMp4 = willSaveAsMp4(uri);
                            String outExt = savesAsMp4 ? ".mp4" : ".gif";
                            String outMime = savesAsMp4 ? "video/mp4" : "image/gif";

                            String fileName;
                            Log.d("GifUtils", "Creating file with submissionTitle: " + (finalSubmissionTitle != null ? "'" + finalSubmissionTitle + "'" : "null"));
                            if (finalSubmissionTitle != null && !finalSubmissionTitle.trim().isEmpty()) {
                                String fileIndex = finalIndex > -1 ? String.format(Locale.ENGLISH, "_%03d", finalIndex) : "";
                                fileName = FileUtil.getValidFileName(finalSubmissionTitle + fileIndex, "", outExt);
                            } else {
                                // If no title available, use a timestamp
                                String fileIndex = finalIndex > -1 ? String.format(Locale.ENGLISH, "_%03d", finalIndex) : "";
                                fileName = System.currentTimeMillis() + fileIndex + outExt;
                            }
                            Log.d("GifUtils", "Creating output file: " + fileName);
                            DocumentFile outDocFile = parentDir.createFile(outMime, fileName);
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
                                        .setCache(Reddit.getVideoCache())
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
                protected void onPostExecute(@Nullable DocumentFile result) {
                    if (save) {
                        notifMgr.cancel(1);
                        if (result != null) {
                            doNotifGif(result, activity);
                        } else {
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
        @Nullable private ProgressBar progressBar;
        @Nullable private View placeholder;
        private boolean closeIfNull;
        private boolean autostart;
        @Nullable public String subreddit;
        @Nullable public String submissionTitle;

        @Nullable private TextView size;
        private boolean wasPlayingBeforePause = false;

        public AsyncLoadGif(
                @NonNull Activity c,
                @NonNull ExoVideoView video,
                @Nullable ProgressBar p,
                @Nullable View placeholder,
                boolean closeIfNull,
                boolean autostart,
                @Nullable String subreddit) {
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
                @Nullable TextView size,
                @Nullable String subreddit,
                @Nullable String submissionTitle) {
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

        /**
         * Called on the main thread when this load did not end up with playable media, whatever the
         * reason — including a playback error raised after the media was handed to the player.
         * Callers that remember what a view already has loaded override this to forget it, so the
         * next attempt is not skipped as a duplicate.
         *
         * <p>Once per load that fails while the task is still wanted. Every dispatch goes through
         * {@link #nothingIsComing()} from onPostExecute or from the player's error callback, both on
         * the main thread, and neither runs for a cancelled task — a row that has moved on is not
         * told about the load it abandoned. Overrides should still be idempotent: a load can resolve
         * media and then have playback fail on it, which reports again.
         *
         * <p>Not called when the url was handed off to another screen instead of failing (see
         * {@link #handedOff}); there is nothing for the caller to retry in that case.
         */
        public void onError() {}

        /**
         * Set when this load ended by sending the url somewhere else — a browser, or MediaView for a
         * misidentified image — rather than failing. Those paths also return no uri, but retrying
         * them would just open the other screen again.
         *
         * <p>Package-private rather than private only so the tests can set it: the paths that set it
         * for real all run deep inside {@code doInBackground} and start another activity on the way.
         */
        @VisibleForTesting boolean handedOff;

        public void cancel() {
            LogUtil.v("cancelling");
            // video is @NonNull in the constructors, but onPostExecute still tests it, so keep the
            // guard rather than turning a failed load into an NPE.
            if (video != null) {
                video.stop();
            }
        }

        /**
         * Stops indicating progress and reports the failure. For every way a load can end with no
         * playable media: it resolved nothing, it threw on the way to the player, or playback died
         * after the player had taken it.
         *
         * <p>The indicators have to be hidden from here. Only STATE_READY hides them in the listener
         * onPostExecute installs, and a failed load never reports it — one that never reaches the
         * player reports nothing at all, and a playback error moves the player to STATE_IDLE — so
         * whichever of them was showing would animate over a row or page for good.
         *
         * <p>Main thread only, like {@link #onError()} itself.
         */
        private void nothingIsComing() {
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            if (size != null) {
                size.setVisibility(View.GONE);
            }
            // A url handed off to another screen is not a failure: the bar still has to go, but
            // there is nothing for the caller to retry.
            if (!handedOff) {
                onError();
            }
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
                return this == OTHER || this == REDGIFS;
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

            if (s.contains("reddit.com/link/") && s.contains("/video/")) {
                s = s.replace("reddit.com/link/", "v.redd.it/link/")
                     .replace("/video/", "/asset/")
                     .replace("/player", "/DASHPlaylist.mpd");
            }

            if (s.contains("v.redd.it") && !s.contains("DASHPlaylist")) {
                // Strip any segment/playlist file (DASH_*.mp4, CMAF_*.mp4,
                // HLSPlaylist.m3u8, etc.) back to https://v.redd.it/<id>
                int idStart = s.indexOf("v.redd.it/") + "v.redd.it/".length();
                int idEnd = s.indexOf('/', idStart);
                if (idEnd == -1) {
                    idEnd = s.indexOf('?', idStart);
                }
                if (idEnd != -1) {
                    s = s.substring(0, idEnd);
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

        /**
         * Returns the best playable video URL from a submission, checking media_metadata,
         * media.reddit_video, and crossposts before falling back to the submission URL.
         */
        public static String getVideoUrlFromSubmission(Submission s) {
            JsonNode data = s.getDataNode();

            // New format: self posts with video in media_metadata
            for (JsonNode entry : data.path("media_metadata")) {
                if ("RedditVideo".equals(entry.path("e").asText())) {
                    String url = StringEscapeUtils.unescapeJson(entry.path("dashUrl").asText("")).replace("&amp;", "&");
                    if (!url.isEmpty()) return url;
                }
            }

            // Standard reddit video
            JsonNode rv = data.path("media").path("reddit_video");
            if (!rv.isMissingNode() && !rv.isNull()) {
                for (String field : new String[]{"dash_url", "fallback_url"}) {
                    String url = rv.path(field).asText("");
                    if (!url.isEmpty())
                        return StringEscapeUtils.unescapeJson(url).replace("&amp;", "&");
                }
            }

            // Crosspost
            JsonNode cpl = data.path("crosspost_parent_list");
            if (cpl.isArray() && cpl.size() > 0) {
                JsonNode crv = cpl.get(0).path("media").path("reddit_video");
                for (String field : new String[]{"dash_url"}) {
                    String url = crv.path(field).asText("");
                    if (!url.isEmpty())
                        return StringEscapeUtils.unescapeJson(url).replace("&amp;", "&");
                }
            }

            return s.getUrl();
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
        public static @Nullable JsonObject getApiResponse(String host, String name) {
            String domain = "api." + host + ".com";
            String gfycatUrl = "https://" + domain + "/v1/gfycats" + name;

            return HttpUtil.getJsonObject(client, gson, gfycatUrl, makeHeaderMap(domain));
        }

        /**
         * Get the correct mp4/mobile url from a given result JsonObject
         *
         * @param result the result to check, as {@link #getApiResponse} returns it
         * @return the video url, or null when the response has none. {@link #getApiResponse} returns
         *     null for a non-2xx response, a network failure or unparsable JSON, and a response that
         *     does parse need not carry a "gfyItem" — the callers reach this straight after the
         *     gfycat lookup has already come back empty, which is when a second lookup failing is
         *     most likely, and an unguarded dereference here escapes doInBackground as an
         *     AsyncTask-rethrown RuntimeException rather than a load failure.
         */
        public static @Nullable String getUrlFromApi(@Nullable JsonObject result) {
            // isJsonObject, not getAsJsonObject: Gson's getAsJsonObject(name) is an unchecked cast,
            // so a member present but JSON-null ("gfyItem": null) hands back JsonNull and the cast
            // throws ClassCastException rather than yielding the null this checks for.
            final JsonElement member = result == null ? null : result.get("gfyItem");
            if (member == null || !member.isJsonObject()) {
                return null;
            }
            final JsonObject item = member.getAsJsonObject();
            // has("mobileUrl") is true for a member that is present but JSON-null, so testing the
            // value rather than the key is what keeps a low-quality request from failing a response
            // that has a perfectly good mp4Url sitting beside a null mobileUrl.
            final JsonElement mobile = item.get("mobileUrl");
            final JsonElement url =
                    (!SettingValues.hqgif && mobile != null && !mobile.isJsonNull())
                            ? mobile
                            : item.get("mp4Url");
            return (url == null || url.isJsonNull()) ? null : url.getAsString();
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

        /**
         * The RedGifs mp4 url for {@code name}, or null when there is none.
         *
         * <p>Null is the whole failure signal, deliberately. This used to take an error callback as
         * well and run it inline — on whatever thread called in, which for the only caller that
         * passed one was an AsyncTask worker — while also returning null for the same failure. That
         * had the caller reporting twice, once off the main thread, and reporting at all for a load
         * that had since been cancelled. The caller now reports once from onPostExecute, which does
         * not run for a cancelled task.
         */
        public static @Nullable Uri loadRedGifs(
                String name,
                String fullUrl,
                @Nullable Activity c,
                @Nullable ProgressBar progressBar,
                boolean closeIfNull) {
            showProgressBar(c, progressBar, true);

            // Remove leading slash if present
            if (name.startsWith("/")) name = name.substring(1);

            try {
                // Check if existing token is valid
                AuthToken currentToken = TOKEN.get();
                if (currentToken == null || !currentToken.isValid()) {
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
                if (closeIfNull && c != null) {
                    c.runOnUiThread(() -> openWebsite(c, fullUrl));
                }
                return null;
            }
        }

        /**
         * Scheme of the uri {@link #loadGfycat} returns for a link that really is gifdeliverynetwork
         * and that redgifs has no media for. Not a playable url and never equal to one: the caller
         * tests the scheme and opens the original link in a browser instead of reporting a failure.
         *
         * <p>The caller used to make that decision by looking for "gifdeliverynetwork" in the
         * returned uri, which no return of that method has ever contained — the recovery below
         * returns a redgifs mp4 url and every other return is a gfycat one — so the browser hand-off
         * was dead code.
         */
        public static final String HANDOFF_SCHEME = "slide-handoff";

        private static Uri handoffUri() {
            return Uri.parse(HANDOFF_SCHEME + "://gifdeliverynetwork");
        }

        /** Whether {@code uri} is the marker {@link #HANDOFF_SCHEME} describes. */
        public static boolean isHandoff(@Nullable Uri uri) {
            return uri != null && HANDOFF_SCHEME.equals(uri.getScheme());
        }

        /**
         * Load the correct URL for a gfycat gif
         *
         * @param name Name of the gfycat gif
         * @param fullUrl full URL to the gfycat
         * @return Correct URL; null when there is none — see {@link #loadRedGifs} for why null is
         *     the whole failure signal and there is no error callback beside it — or the marker
         *     {@link #HANDOFF_SCHEME} describes, which {@link #isHandoff} recognises.
         */
        public static @Nullable Uri loadGfycat(
                String name,
                String fullUrl,
                @Nullable Activity c,
                @Nullable ProgressBar progressBar,
                boolean closeIfNull) {
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
            // One reader for the response, not a hand-walked guard beside it. The guard here used to
            // repeat the traversal without getUrlFromApi's null checks, so it threw on the shapes
            // that reader exists to survive: "gfyItem": null cast JsonNull to JsonObject, and a
            // gfyItem carrying no mp4Url dereferenced a Java null. Neither is caught on the way out —
            // the try below only catches IOException and doInBackground's GFYCAT case has no catch
            // at all — so both escaped as an AsyncTask-rethrown RuntimeException.
            final String directUrl = getUrlFromApi(result);
            if (directUrl == null) {
                // A null response — not merely one with no url in it — may mean the gfycat link
                // redirects to gifdeliverynetwork, which is powered by redgifs. Follow the redirect
                // ourselves, and if that is where it goes, fetch the actual .mp4/.webm url from the
                // redgifs api.
                if (result == null) {
                    try {
                        URL newUrl = new URL(fullUrl);
                        HttpURLConnection ucon = (HttpURLConnection) newUrl.openConnection();
                        ucon.setInstanceFollowRedirects(false);
                        String secondURL = new URL(ucon.getHeaderField("location")).toString();
                        if (secondURL.contains("gifdeliverynetwork")) {
                            final String redirected =
                                    getUrlFromApi(getApiResponse("redgifs", name.toLowerCase()));
                            if (redirected != null) {
                                return Uri.parse(redirected);
                            }
                            // The link really is gifdeliverynetwork and redgifs has nothing for it.
                            // This is where the browser hand-off belongs — the caller cannot see
                            // secondURL, which is why its own attempt to spot this never fired.
                            return handoffUri();
                        }

                    } catch (IOException e) {
                        LogUtil.e(e, "GifUtils.loadGfycat failed");
                    }
                }

                if (closeIfNull && c != null) {
                    c.runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        DialogUtil.showWithCardBackground(new AlertDialog.Builder(c)
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
                                                );
                                    } catch (Exception ignored) {
                                    }
                                }
                            });
                }

                return null;
            }

            return Uri.parse(directUrl);
        }

        /**
         * The gfycat lookup this task's own fields configure, as an instance method so a test can
         * stand in for the network. Production behaviour is the static call and nothing else.
         */
        @VisibleForTesting
        @Nullable Uri resolveGfycat(final String name, final String url) {
            return loadGfycat(name, url, c, progressBar, closeIfNull);
        }

        /**
         * Whether this load's host exists to show this one piece of media, and may therefore be
         * finished when the media does not resolve.
         *
         * <p>Only MediaView passes {@code closeIfNull = true}, and it is the only host for which that
         * holds — everywhere else the load is one item among many (a Shadowbox page, an album row, a
         * force-touch peek), often not even the one in front of the user. The hand-off paths below
         * end in {@link #openWebsite(Activity, String, boolean)}, whose {@code finishHost} argument
         * this answers: finishing from a background load would close the shadowbox, peek or album out
         * from under the user because one item failed.
         *
         * <p>It gates the hand-off itself only where the url is known to be dead — the gfycat marker
         * — since there a browser has nothing to show either. Those loads return null instead, which
         * reaches onError, the signal those callers already handle: the peek falls back to a link
         * preview, a row stops its spinner, a page stays put.
         */
        private boolean ownsTheScreen() {
            return closeIfNull && c != null;
        }

        @Override
        protected @Nullable Uri doInBackground(String... sub) {
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

                    // No error callback: a null return is the failure, and onPostExecute reports it
                    // through nothingIsComing() on the main thread. onError() cannot be called from
                    // here — the peek view rebuilds its whole view from it and the album rows write
                    // their per-row load state, which is unsynchronized for exactly that reason.
                    return loadRedGifs(id, url, c, progressBar, closeIfNull);
                case VREDDIT:
                    return Uri.parse(url);
                case GFYCAT:
                    String name = url.substring(url.lastIndexOf("/"));
                    Uri uri = resolveGfycat(name, url);
                    if (isHandoff(uri)) {
                        if (ownsTheScreen()) {
                            handedOff = true;
                            // Posted, like loadRedGifs's own hand-off: openWebsite calls
                            // startActivity and finish(), and finish() is not documented as
                            // thread-safe. doInBackground is a worker thread.
                            c.runOnUiThread(() -> openWebsite(c, url));
                        }

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
                    // No try/catch: Uri.parse cannot throw for the non-null string formatUrl
                    // returns, so the recovery that used to sit here could never run.
                    return Uri.parse(url);
                case STREAMABLE:
                    String hash = url.substring(url.lastIndexOf("/") + 1);
                    String streamableUrl = "https://api.streamable.com/videos/" + hash;
                    LogUtil.v(streamableUrl);
                    try {
                        final JsonObject result =
                                HttpUtil.getJsonObject(client, gson, streamableUrl);
                        String obj;
                        if (result == null || result.get("files") == null || !(result.getAsJsonObject("files").has("mp4") || result.getAsJsonObject("files").has("mp4-mobile"))) {
                            // No onError() from here: returning null is the failure, and
                            // onPostExecute reports it once, on the main thread, and not at all for
                            // a task the caller has cancelled.
                            if (closeIfNull && c != null) {
                                c.runOnUiThread(
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                try { // Added try-catch for safety
                                                    DialogUtil.showWithCardBackground(new AlertDialog.Builder(c)
                                                            .setTitle(R.string.error_video_not_found)
                                                            .setMessage(R.string.error_video_message)
                                                            .setCancelable(false)
                                                            .setPositiveButton(R.string.btn_ok, (dialog, which) -> c.finish())
                                                            );
                                                } catch (Exception e) {
                                                    LogUtil.e(e, "Failed to show video-not-found dialog");
                                                }
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

                        // As above: the null return is the report.
                        if (closeIfNull && c != null) {
                            c.runOnUiThread(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                DialogUtil.showWithCardBackground(new AlertDialog.Builder(c)
                                                        .setTitle(R.string.error_video_not_found)
                                                        .setMessage(R.string.error_video_message)
                                                        .setCancelable(false)
                                                        .setPositiveButton(R.string.btn_ok, (dialog, which) -> c.finish())
                                                        );
                                            } catch (Exception ignored) {
                                            }
                                        }
                                    });
                        }
                    }
                    break;
                case OTHER:
                    LogUtil.e("We shouldn't be here!");
                    // Hand off wherever there is a host to hand off to, but only finish one that
                    // owns its screen. An unrecognised url is not a dead one — the browser is very
                    // likely to render it — so dropping the hand-off would leave a Shadowbox or
                    // CommentsScreen page blank with content the user could otherwise have read.
                    // The GFYCAT marker above is the opposite case and stays gated: it means the
                    // link is dead and redgifs has nothing for it, so a browser would only show
                    // that.
                    //
                    // The null test guards the dereference inside openWebsite and keeps handedOff
                    // honest: marking a load handed-off that went nowhere would suppress the
                    // onError its caller needs to retry or fall back.
                    if (c != null) {
                        // Only a host we actually replaced counts as handed off. Setting it for a
                        // host left standing suppressed the onError those callers answer — the peek
                        // overlay's is doLoadLink, its fallback to a link preview, so an unrecognised
                        // url left the peek showing a stopped spinner behind the browser it opened.
                        handedOff = ownsTheScreen();
                        c.runOnUiThread(() -> openWebsite(c, url, ownsTheScreen()));
                    }
                    break;
            }
            return null;
        }

        @Override
        protected void onPostExecute(@Nullable Uri uri) {
            if (uri == null || video == null) {
                cancel();
                nothingIsComing();
                return;
            }

            if (progressBar != null) {
                progressBar.setIndeterminate(true);
            }

            try {
                ExoVideoView.VideoType type =
                    uri.toString().contains("DASHPlaylist") ? ExoVideoView.VideoType.DASH
                    : ExoVideoView.VideoType.STANDARD;

                video.setVideoURI(uri, type, new Player.Listener() {
                    @Override
                    public void onPlayerError(@NonNull PlaybackException error) {
                        // The realistic failure: the uri resolved and the player took it, then
                        // playback died (network, 403, unsupported codec). doInBackground never sees
                        // this, so without forwarding it here a caller tracking what it asked to
                        // load would never learn the media is not coming — and the bar it was
                        // showing while buffering would never stop.
                        LogUtil.e(error, "Playback failed for " + uri);
                        nothingIsComing();
                    }

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
                nothingIsComing();
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
                    LogUtil.e(e, "GifUtils.run failed");
                }
            } else {
                DataSource.Factory downloader =
                        new OkHttpDataSource.Factory(Reddit.client)
                                .setUserAgent(c.getString(R.string.app_name));
                DataSource.Factory cacheDataSourceFactory =
                        new CacheDataSource.Factory()
                                .setCache(Reddit.getVideoCache())
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

                            if (hqUri == null) {
                                continue;
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
            openWebsite(c, url, true);
        }

        /**
         * Opens {@code url} in the in-app browser.
         *
         * <p>{@code finishHost} false leaves the caller's screen standing. Only a host dedicated to
         * this one piece of media may be replaced by the browser — inside a load that is the
         * {@code closeIfNull} constructor argument, which only MediaView passes as true. Finishing a
         * Shadowbox page, an album row's activity or a force-touch peek closes far more than the
         * item that failed, and does it from a background load.
         */
        public static void openWebsite(Activity c, String url, boolean finishHost) {
            Intent web = new Intent(c, Website.class);
            web.putExtra(LinkUtil.EXTRA_URL, url);
            web.putExtra(LinkUtil.EXTRA_COLOR, Color.BLACK);
            c.startActivity(web);
            if (finishHost) {
                c.finish();
            }
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
            final @Nullable Activity activity,
            final @Nullable ProgressBar progressBar,
            final boolean isIndeterminate) {
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
    /**
     * Translates {@link MediaExtractor#getSampleFlags()} values (the
     * {@code SAMPLE_FLAG_*} typedef) into {@link MediaCodec.BufferInfo#flags}
     * values (the {@code BUFFER_FLAG_*} typedef) for muxing. The two APIs use
     * distinct {@code @IntDef} typedefs even though the sync-frame bit coincides.
     */
    private static int sampleFlagsToBufferFlags(int sampleFlags) {
        int bufferFlags = 0;
        if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
            bufferFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
        }
        if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0) {
            bufferFlags |= MediaCodec.BUFFER_FLAG_PARTIAL_FRAME;
        }
        return bufferFlags;
    }

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
                if (mime != null && mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i);
                    videoTrackIndex = muxer.addTrack(format);
                    break;
                }
            }

            // Add audio track
            for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
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
                bufferInfo.flags = sampleFlagsToBufferFlags(videoExtractor.getSampleFlags());

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
                bufferInfo.flags = sampleFlagsToBufferFlags(audioExtractor.getSampleFlags());

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
