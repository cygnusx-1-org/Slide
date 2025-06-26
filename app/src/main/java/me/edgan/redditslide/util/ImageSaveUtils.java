package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask; // Added
import android.util.Log;
import android.widget.Toast; // Added

// Added
import com.google.gson.JsonObject; // Added

import me.edgan.redditslide.Notifications.ImageDownloadNotificationService;
import me.edgan.redditslide.R; // Added
// Added
// Added

// Added

public class ImageSaveUtils {
    private static final String TAG = "ImageSaveUtils";
    public static final String EXTRA_SUBMISSION_TITLE = ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

    /**
     * Saves an image or gif to the device storage
     *
     * @param activity The activity context
     * @param isGif Whether the content is a gif/video
     * @param contentUrl The URL of the content to save
     * @param index The index of the image in a gallery
     * @param subreddit The subreddit the content is from
     * @param submissionTitle The title of the submission
     * @param showFirstDialogCallback Callback to show the storage access dialog
     */
    public static void doImageSave(
            Activity activity,
            boolean isGif,
            String contentUrl,
            int index,
            String subreddit,
            String submissionTitle,
            Runnable showFirstDialogCallback) {

        Uri storageUri = StorageUtil.getStorageUri(activity);
        boolean hasAccess = StorageUtil.hasStorageAccess(activity);

        if (storageUri == null || !hasAccess) {
            Log.e(TAG, "No valid storage URI found or no access. URI=" + storageUri + ", hasAccess=" + hasAccess);
            if (showFirstDialogCallback != null) {
                showFirstDialogCallback.run();
            }
            return;
        } else {
            if (isGif) {
                // Handle video/gif save asynchronously
                new ResolveAndSaveGifTask(
                        activity,
                        contentUrl,
                        subreddit,
                        submissionTitle,
                        showFirstDialogCallback,
                        index
                ).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                // Handle image save (remains synchronous start of service)
                Intent i = new Intent(activity, ImageDownloadNotificationService.class);
                i.putExtra("actuallyLoaded", contentUrl);
                i.putExtra("downloadUri", storageUri.toString());

                if (subreddit != null && !subreddit.isEmpty()) {
                    i.putExtra("subreddit", subreddit);
                }

                Log.d(TAG, "ImageSaveUtils - Saving with submissionTitle: " + (submissionTitle != null ? "'" + submissionTitle + "'" : "null"));
                if (submissionTitle != null) {
                    i.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
                }
                i.putExtra("index", index); // Index is not used for GIFs, but keep for images

                activity.startService(i);
            }
        }
    }

    // Inner AsyncTask to resolve GIF/Video URI and then save
    private static class ResolveAndSaveGifTask extends AsyncTask<Void, Void, Uri> {
        private final Activity activity;
        private final String initialUrl;
        private final String subreddit;
        private final String submissionTitle;
        private final Runnable showFirstDialogCallback;
        private final int index;
        private Exception error = null;

        ResolveAndSaveGifTask(Activity activity, String initialUrl, String subreddit, String submissionTitle, Runnable showFirstDialogCallback) {
            this(activity, initialUrl, subreddit, submissionTitle, showFirstDialogCallback, -1);
        }

        ResolveAndSaveGifTask(Activity activity, String initialUrl, String subreddit, String submissionTitle, Runnable showFirstDialogCallback, int index) {
            this.activity = activity;
            this.initialUrl = initialUrl;
            this.subreddit = subreddit;
            this.submissionTitle = submissionTitle;
            this.showFirstDialogCallback = showFirstDialogCallback;
            this.index = index;
        }

        @Override
        protected Uri doInBackground(Void... params) {
            // Logic adapted from GifUtils.AsyncLoadGif.doInBackground
            final String url = GifUtils.AsyncLoadGif.formatUrl(initialUrl);
            GifUtils.AsyncLoadGif.VideoType videoType = GifUtils.AsyncLoadGif.getVideoType(url);

            try {
                switch (videoType) {
                    case REDGIFS:
                        String id = url.substring(url.lastIndexOf("/"));
                        return GifUtils.AsyncLoadGif.loadRedGifs(id, url, null, null, false, null);
                    case VREDDIT:
                        return Uri.parse(url);
                    case GFYCAT:
                        String name = url.substring(url.lastIndexOf("/"));
                        Uri gfyUri = GifUtils.AsyncLoadGif.loadGfycat(name, url, null, null, false, null);
                        if (gfyUri != null && gfyUri.toString().contains("gifdeliverynetwork")) {
                            Log.w(TAG, "Gfycat resolved to gifdeliverynetwork, saving might fail or be incorrect: " + url);
                            return gfyUri;
                        }
                        return gfyUri;
                    case REDDIT_GALLERY: // Galleries aren't single videos, saving doesn't make sense here
                        Log.w(TAG, "Attempted to save a Reddit Gallery link as a single GIF: " + url);
                        error = new IllegalArgumentException("Cannot save Reddit Gallery as single video.");
                        return null;
                    case DIRECT:
                    case IMGUR:
                        return Uri.parse(url);
                    case STREAMABLE:
                        String hash = url.substring(url.lastIndexOf("/") + 1);
                        String streamableUrl = "https://api.streamable.com/videos/" + hash;
                        JsonObject result = HttpUtil.getJsonObject(GifUtils.AsyncLoadGif.client, GifUtils.AsyncLoadGif.gson, streamableUrl);
                        if (result == null
                                || result.get("files") == null
                                || !(result.getAsJsonObject("files").has("mp4")
                                || result.getAsJsonObject("files").has("mp4-mobile"))) {
                            error = new Exception("Streamable API response invalid for: " + url);
                            return null;
                        } else {
                            String obj;
                            if (result.getAsJsonObject("files").getAsJsonObject().has("mp4-mobile")
                                    && !result.getAsJsonObject("files").getAsJsonObject().get("mp4-mobile").getAsJsonObject().get("url").getAsString().isEmpty()) {
                                obj = result.getAsJsonObject("files").getAsJsonObject().get("mp4-mobile").getAsJsonObject().get("url").getAsString();
                            } else {
                                obj = result.getAsJsonObject("files").getAsJsonObject().get("mp4").getAsJsonObject().get("url").getAsString();
                            }
                            return Uri.parse(obj);
                        }
                    case TUMBLR:
                        // For Tumblr URLs, treat as direct download
                        return Uri.parse(url);
                    case OTHER:
                        error = new IllegalArgumentException("Cannot resolve video URL for type OTHER: " + url);
                        return null; // Cannot resolve
                }
            } catch (Exception e) {
                LogUtil.e(e, "Error resolving GIF/Video URI for saving: " + initialUrl);
                error = e;
                return null;
            }
            // Add default return statement outside try-catch to satisfy compiler
            return null;
        }

        @Override
        protected void onPostExecute(Uri resolvedUri) {
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                Log.d(TAG, "Activity is null, finishing, or destroyed - aborting save");
                return; // Activity gone
            }

            if (resolvedUri != null) {
                // Show toast before starting the potentially long save operation
                try {
                    Toast.makeText(activity, activity.getString(R.string.mediaview_notif_video), Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {} // Ignore if toast fails

                // Proceed with saving using the resolved URI
                GifUtils.cacheSaveGif(resolvedUri, activity, subreddit != null ? subreddit : "", submissionTitle != null ? submissionTitle : "", true, index);
            } else {
                // Handle errors during resolution
                Log.e(TAG, "Failed to resolve URI for " + initialUrl + (error != null ? ": " + error.getMessage() : ""));
                // Show error dialog or toast
                if (activity instanceof androidx.appcompat.app.AppCompatActivity) {
                    Log.d(TAG, "Showing error dialog for failed URI resolution");
                    DialogUtil.showErrorDialog((androidx.appcompat.app.AppCompatActivity) activity);
                } else {
                     // Fallback or log error if casting is not possible (shouldn't happen in practice)
                    Log.e(TAG, "Activity is not an AppCompatActivity, cannot show error dialog.");
                }
            }
        }
    }

}