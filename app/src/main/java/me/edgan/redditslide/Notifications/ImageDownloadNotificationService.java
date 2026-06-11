package me.edgan.redditslide.Notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.FileUtil;
import me.edgan.redditslide.util.LogUtil;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

public class ImageDownloadNotificationService extends Service {

    public static final String EXTRA_SUBMISSION_TITLE = "submissionTitle";

    // Add static lock object for directory operations
    private static final Object DIRECTORY_LOCK = new Object();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void handleIntent(Intent intent) {
        String actuallyLoaded = intent.getStringExtra("actuallyLoaded");
        String downloadUriString = intent.getStringExtra("downloadUri");

        if (actuallyLoaded == null || downloadUriString == null) {
            stopSelf();
            return;
        }

        if (actuallyLoaded.contains("imgur.com")
                && (!actuallyLoaded.contains(".png") && !actuallyLoaded.contains(".jpg"))) {
            actuallyLoaded = actuallyLoaded + ".png";
        }

        String subreddit = "";
        if (intent.hasExtra("subreddit")) {
            subreddit = intent.getStringExtra("subreddit");
        }

        new PollTask(
                        actuallyLoaded,
                        Uri.parse(downloadUriString),
                        intent.getIntExtra("index", -1),
                        subreddit,
                        intent.getStringExtra(EXTRA_SUBMISSION_TITLE))
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private class PollTask extends AsyncTask<Void, Void, Void> {
        private NotificationManager mNotifyManager;
        private NotificationCompat.Builder mBuilder;
        private final String actuallyLoaded;
        private final Uri baseUri;
        private final int index;
        private final String subreddit;
        private final String submissionTitle;
        private int id;
        private int percentDone, latestPercentDone;

        public PollTask(
                String actuallyLoaded,
                Uri baseUri,
                int index,
                String subreddit,
                String submissionTitle) {
            this.actuallyLoaded = actuallyLoaded;
            this.baseUri = baseUri;
            this.index = index;
            this.subreddit = subreddit;
            this.submissionTitle = submissionTitle;
        }

        private void startNotification() {
            id = (int) (System.currentTimeMillis() / 1000);
            mNotifyManager =
                    ContextCompat.getSystemService(
                            ImageDownloadNotificationService.this, NotificationManager.class);
            mBuilder =
                    new NotificationCompat.Builder(getApplicationContext(), Reddit.CHANNEL_IMG)
                            .setContentTitle(getString(R.string.mediaview_notif_title))
                            .setContentText(getString(R.string.mediaview_notif_text))
                            .setSmallIcon(R.drawable.ic_save);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            try {
                Toast.makeText(
                                ImageDownloadNotificationService.this,
                                getString(R.string.mediaview_downloading),
                                Toast.LENGTH_SHORT)
                        .show();
            } catch (Exception ignored) {
            }

            startNotification();
            mBuilder.setProgress(100, 0, false);
            mNotifyManager.notify(id, mBuilder.build());
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                ((Reddit) getApplication())
                        .getImageLoader()
                        .loadImage(
                                actuallyLoaded,
                                null,
                                new DisplayImageOptions.Builder()
                                        .imageScaleType(ImageScaleType.NONE)
                                        .cacheInMemory(false)
                                        .cacheOnDisk(true)
                                        .build(),
                                new SimpleImageLoadingListener() {
                                    @Override
                                    public void onLoadingComplete(String imageUri, android.view.View view, final Bitmap loadedImage) {
                                        try {
                                            File cachedFile = ((Reddit) getApplicationContext())
                                                    .getImageLoader()
                                                    .getDiskCache()
                                                    .get(actuallyLoaded);

                                            Context activity = ImageDownloadNotificationService.this;
                                            DocumentFile parentDir = DocumentFile.fromTreeUri(activity, baseUri);

                                            if (parentDir == null || !parentDir.canWrite()) {
                                                onError(new IOException("Invalid directory URI or no write permission."));
                                                return;
                                            }

                                            // Create subreddit and/or media-type subfolders if needed.
                                            // Synchronize folder lookup/creation to avoid race conditions.
                                            synchronized (DIRECTORY_LOCK) {
                                                if (SettingValues.imageSubfolders && !subreddit.isEmpty()) {
                                                    String cleanSubredditName = subreddit.replaceAll("[^a-zA-Z0-9.-]", "_");
                                                    parentDir = FileUtil.getOrCreateDirectory(parentDir, cleanSubredditName);
                                                    if (parentDir == null) {
                                                        onError(new IOException("Failed to create subfolder."));
                                                        return;
                                                    }
                                                }

                                                if (SettingValues.imageTypeSubfolders) {
                                                    parentDir = FileUtil.getOrCreateDirectory(parentDir, "images");
                                                    if (parentDir == null) {
                                                        onError(new IOException("Failed to create type subfolder."));
                                                        return;
                                                    }
                                                }
                                            }

                                            if (cachedFile != null && cachedFile.exists()) {
                                                String fileName = getFileName(actuallyLoaded);
                                                String mimeType = getMimeType(fileName);
                                                DocumentFile outDocFile = parentDir.createFile(mimeType, fileName);

                                                if (outDocFile != null) {
                                                    OutputStream out = getContentResolver().openOutputStream(outDocFile.getUri());
                                                    if (out != null) {
                                                        FileUtil.copyFile(cachedFile, out);
                                                        out.close();
                                                        showSuccessNotification(outDocFile.getUri(), loadedImage);
                                                    }
                                                }
                                            } else {
                                                String fileName = getFileName(actuallyLoaded);
                                                String mimeType = getMimeType(fileName);
                                                DocumentFile outDocFile = parentDir.createFile(mimeType, fileName);

                                                if (outDocFile != null) {
                                                    OutputStream out = getContentResolver().openOutputStream(outDocFile.getUri());
                                                    if (out != null) {
                                                        Bitmap.CompressFormat format = mimeType.contains("png")
                                                                ? Bitmap.CompressFormat.PNG
                                                                : Bitmap.CompressFormat.JPEG;
                                                        loadedImage.compress(format, 100, out);
                                                        out.close();
                                                        showSuccessNotification(outDocFile.getUri(), loadedImage);
                                                    }
                                                }
                                            }
                                        } catch (IOException e) {
                                            onError(e);
                                        }
                                    }
                                },
                                new ImageLoadingProgressListener() {
                                    @Override
                                    public void onProgressUpdate(
                                            String imageUri,
                                            android.view.View view,
                                            int current,
                                            int total) {
                                        latestPercentDone = (int) ((current / (float) total) * 100);
                                        if (percentDone <= latestPercentDone + 30
                                                || latestPercentDone == 100) {
                                            percentDone = latestPercentDone;
                                            mBuilder.setProgress(100, percentDone, false);
                                            mNotifyManager.notify(id, mBuilder.build());
                                        }
                                    }
                                });
            } catch (Exception e) {
                onError(e);
            }
            return null;
        }

        private Uri createDocument(Uri baseUri, String fileName) {
            try {
                if (baseUri == null || baseUri.getAuthority() == null) {
                    throw new IllegalArgumentException("Invalid base URI provided.");
                }

                // Ensure it is a valid tree URI by using DocumentFile API
                DocumentFile pickedDir = DocumentFile.fromTreeUri(getApplicationContext(), baseUri);

                if (pickedDir == null || !pickedDir.canWrite()) {
                    if (pickedDir == null) {
                        LogUtil.v("PickedDir is null");
                    }

                    throw new IOException("No write access to the selected directory.");
                }

                // Create the file in the selected directory (or overwrite if it already exists)
                DocumentFile existingFile = pickedDir.findFile(fileName);
                if (existingFile != null) {
                    existingFile.delete(); // Delete if already exists to avoid conflict
                }

                DocumentFile newFile =
                        pickedDir.createFile(FileUtil.getMimeType(fileName), fileName);

                if (newFile == null) {
                    throw new IOException("Failed to create the file: " + fileName);
                }

                return newFile.getUri();
            } catch (Exception e) {
                onError(e);
                return null;
            }
        }

        private String getMimeType(String fileName) {
            String mimeType = "image/png";

            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            }

            return mimeType;
        }

        private String getFileName(String url) {
            // Determine the file extension from the URL
            String extension;
            try {
                URL parsedUrl = new URL(url);
                String path = parsedUrl.getPath();
                if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                    extension = path.substring(path.lastIndexOf("."));
                } else {
                    throw new MalformedURLException();
                }
            } catch (MalformedURLException e) {
                extension = ".png"; // Default to PNG if we can't determine the extension
            }

            // Prepare the file index string (e.g., "_001" for series of images)
            String fileIndex = index > -1 ? String.format(Locale.ENGLISH, "_%03d", index) : "";

            // Use the submission title directly if available, otherwise use a timestamp
            String title;
            if (submissionTitle != null && !submissionTitle.trim().isEmpty()) {
                title = submissionTitle;
            } else {
                // If no title available, use a timestamp to avoid generic names
                title = String.valueOf(System.currentTimeMillis());
            }

            String subfolderPath =
                    subreddit != null && !subreddit.isEmpty() ? File.separator + subreddit : "/";

            // Generate a temporary path just to get the filename
            String tempPath = getApplicationContext().getCacheDir().getAbsolutePath();
            File validFile =
                    FileUtil.getValidFile(
                            tempPath, // We'll use a temp path since we're only interested in the
                            // filename
                            subfolderPath,
                            title,
                            fileIndex,
                            extension);

            // Return just the filename part
            return validFile.getName();
        }

        private void showSuccessNotification(Uri fileUri, Bitmap thumbnail) {
            Notification notif =
                    new NotificationCompat.Builder(getApplicationContext(), Reddit.CHANNEL_IMG)
                            .setContentTitle(getString(R.string.info_photo_saved))
                            .setSmallIcon(R.drawable.ic_save)
                            .setLargeIcon(thumbnail)
                            .setAutoCancel(true)
                            .build();

            if (mNotifyManager != null) {
                mNotifyManager.cancel(id);
                mNotifyManager.notify(id, notif);
            }

            stopSelf();
        }

        private void onError(Exception e) {
            LogUtil.e(e, "ImageDownloadNotificationService.onError failed");
            mNotifyManager.cancel(id);
            stopSelf();
            try {
                Toast.makeText(
                                getBaseContext(),
                                getString(R.string.err_save_image),
                                Toast.LENGTH_LONG)
                        .show();
            } catch (Exception ignored) {
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            mNotifyManager.cancel(id);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleIntent(intent);
        return START_NOT_STICKY;
    }
}
