package me.edgan.redditslide.Activities;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import android.view.MotionEvent;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.cocosw.bottomsheet.BottomSheet;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener;

import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.Notifications.ImageDownloadNotificationService;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SecretConstants;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionViews.OpenVRedditTask;
import me.edgan.redditslide.Views.ExoVideoView;
import me.edgan.redditslide.Views.ImageSource;
import me.edgan.redditslide.Views.SubsamplingScaleImageView;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.util.AnimatorUtil;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.FileUtil;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.HttpUtil;
import me.edgan.redditslide.util.ImageSaveUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.ShareUtil;
import me.edgan.redditslide.util.StorageUtil;
import me.edgan.redditslide.util.MiscUtil;

import org.apache.commons.text.StringEscapeUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import android.graphics.Movie;
import me.edgan.redditslide.util.GifDrawable;
import androidx.annotation.NonNull;

/** Created by ccrama on 3/5/2015. */
public class MediaView extends BaseSaveActivity implements ExoVideoView.OnSingleTapListener {
    public static final String EXTRA_URL = "url";
    public static final String SUBREDDIT = "sub";
    public static final String ADAPTER_POSITION = "adapter_position";
    public static final String SUBMISSION_URL = "submission";
    public static final String EXTRA_DISPLAY_URL = "displayUrl";
    public static final String EXTRA_LQ = "lq";
    public static final String EXTRA_SHARE_URL = "urlShare";

    public static String fileLoc;
    public String subreddit;
    private String submissionTitle;
    private int index;
    public static boolean didLoadGif;

    public float previous;
    public boolean hidden;
    public boolean imageShown;
    public String actuallyLoaded;
    public boolean isGif;

    private NotificationManager mNotifyManager;
    private NotificationCompat.Builder mBuilder;
    private long stopPosition;
    private GifUtils.AsyncLoadGif gif;
    private String contentUrl;
    private ExoVideoView videoView;
    private Gson gson;
    private String imgurKey;
    private String lastContentUrl;

    // Fields for direct GIF handling
    private ImageView directGifViewer;
    private GifDrawable activeGifDrawable; // To manage its lifecycle

    private static final String TAG = "MediaView";

    private static boolean shouldTruncate(String url) {
        try {
            final URI uri = new URI(url);
            final String path = uri.getPath();

            return !ContentType.isGif(uri) && !ContentType.isImage(uri) && path.contains(".");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (videoView != null) {
            videoView.seekTo(stopPosition);
            if (videoView.isPlaying() || gif != null) {
                videoView.play();
            }
        }
    }

    public void showBottomSheetImage() {
        int[] attrs = new int[] {R.attr.tintColor};
        TypedArray ta = obtainStyledAttributes(attrs);

        int color = ta.getColor(0, Color.WHITE);
        Drawable external = getResources().getDrawable(R.drawable.ic_open_in_browser);
        Drawable share = getResources().getDrawable(R.drawable.ic_share);
        Drawable image = getResources().getDrawable(R.drawable.ic_image);
        Drawable save = getResources().getDrawable(R.drawable.ic_download);
        Drawable collection = getResources().getDrawable(R.drawable.ic_folder);
        Drawable file = getResources().getDrawable(R.drawable.ic_save);
        Drawable thread = getResources().getDrawable(R.drawable.ic_forum);

        final List<Drawable> drawableSet =
                Arrays.asList(external, share, image, save, collection, file, thread);
        BlendModeUtil.tintDrawablesAsSrcAtop(drawableSet, color);

        ta.recycle();

        contentUrl = contentUrl.replace("/DASHPlaylist.mpd", "");

        BottomSheet.Builder b = new BottomSheet.Builder(this).title(contentUrl);

        b.sheet(2, external, getString(R.string.open_externally));
        b.sheet(5, share, getString(R.string.submission_link_share));

        if (!isGif) b.sheet(3, image, getString(R.string.share_image));
        b.sheet(4, save, "Save " + (isGif ? "MP4" : "image"));
        Drawable folder = getResources().getDrawable(R.drawable.ic_folder);
        if (isGif
                && !contentUrl.contains(".mp4")
                && !contentUrl.contains("streamable.com")
                && !contentUrl.contains("gfycat.com")
                && !contentUrl.contains("redgifs.com")
                && !contentUrl.contains("v.redd.it")) {
            String type = contentUrl.substring(contentUrl.lastIndexOf(".") + 1).toUpperCase();
            try {
                if (type.equals("GIFV") && new URL(contentUrl).getHost().equals("i.imgur.com")) {
                    type = "GIF";
                    contentUrl = contentUrl.replace(".gifv", ".gif");
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            b.sheet(6, file, getString(R.string.mediaview_save, type));
        }
        if (contentUrl.contains("v.redd.it")) {
            b.sheet(15, thread, "View video thread");
        }
        b.listener(
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case (2):
                                {
                                    LinkUtil.openExternally(contentUrl);
                                    break;
                                }
                            case (3):
                                {
                                    ShareUtil.shareImage(actuallyLoaded, MediaView.this);
                                    break;
                                }
                            case (5):
                                {
                                    Reddit.defaultShareText(
                                            "",
                                            StringEscapeUtils.unescapeHtml4(contentUrl),
                                            MediaView.this);
                                    break;
                                }
                            case (6):
                                {
                                    saveFile(contentUrl);
                                }
                                break;
                            case (15):
                                {
                                    new OpenVRedditTask(MediaView.this, subreddit)
                                            .executeOnExecutor(
                                                    AsyncTask.THREAD_POOL_EXECUTOR, contentUrl);
                                }
                                break;
                            case (9):
                                {
                                    shareGif(contentUrl);
                                }
                                break;
                            case (4):
                                {
                                    String urlToSave =
                                            actuallyLoaded != null ? actuallyLoaded : contentUrl;
                                    doImageSave(isGif, urlToSave, index);
                                    break;
                                }
                            case (16):
                                {
                                    // Launch system directory picker for default save location
                                    StorageUtil.showDirectoryChooser(MediaView.this);
                                    break;
                                }
                        }
                    }
                });
        b.show();
    }

    public void doImageSave(boolean isGif, String contentUrl, int index) {
        ImageSaveUtils.doImageSave(
                this,
                isGif,
                contentUrl,
                index,
                subreddit,
                submissionTitle,
                this::showFirstDialog
        );
    }
    public void saveFile(final String baseUrl) {
        Uri storageUri = StorageUtil.getStorageUri(this);

        if (storageUri == null || !StorageUtil.hasStorageAccess(this)) {
            Log.e(TAG, "No storage URI available");
            DialogUtil.showErrorDialog(this);
            return;
        }

        Intent i = new Intent(this, ImageDownloadNotificationService.class);
        // always download the original file, or use the cached original if that is currently
        // displayed
        i.putExtra("actuallyLoaded", contentUrl);
        i.putExtra("downloadUri", storageUri.toString());
        if (subreddit != null && !subreddit.isEmpty()) i.putExtra("subreddit", subreddit);
        if (submissionTitle != null) i.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
        i.putExtra("index", index);

        ComponentName component = startService(i);

        if (component == null) {
            Log.e(TAG, "Failed to start download service");
            DialogUtil.showErrorDialog(this);
        } else {
            Toast.makeText(this, getString(R.string.mediaview_downloading), Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public void shareGif(final String baseUrl) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (Reddit.appRestart.getString("imagelocation", "").isEmpty()) {
                    showFirstDialog();
                } else if (!new File(Reddit.appRestart.getString("imagelocation", "")).exists()) {
                    showErrorDialog();
                } else {
                    final File f =
                            new File(
                                    Reddit.appRestart.getString("imagelocation", "")
                                            + File.separator
                                            + UUID.randomUUID().toString()
                                            + baseUrl.substring(baseUrl.lastIndexOf(".")));
                    mNotifyManager =
                            ContextCompat.getSystemService(
                                    MediaView.this, NotificationManager.class);
                    mBuilder = new NotificationCompat.Builder(MediaView.this, Reddit.CHANNEL_IMG);
                    mBuilder.setContentTitle(getString(R.string.mediaview_saving, baseUrl))
                            .setSmallIcon(R.drawable.ic_download);
                    try {

                        final URL url =
                                new URL(baseUrl); // wont exist on server yet, just load the full
                        // version
                        URLConnection ucon = url.openConnection();
                        ucon.setReadTimeout(5000);
                        ucon.setConnectTimeout(10000);
                        InputStream is = ucon.getInputStream();
                        BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);
                        int length = ucon.getContentLength();
                        f.createNewFile();
                        FileOutputStream outStream = new FileOutputStream(f);
                        byte[] buff = new byte[5 * 1024];

                        int len;
                        int last = 0;
                        while ((len = inStream.read(buff)) != -1) {
                            outStream.write(buff, 0, len);
                            int percent = Math.round(100.0f * f.length() / length);
                            if (percent > last) {
                                last = percent;
                                mBuilder.setProgress(length, (int) f.length(), false);
                                mNotifyManager.notify(1, mBuilder.build());
                            }
                        }
                        outStream.flush();
                        outStream.close();
                        inStream.close();
                        MediaScannerConnection.scanFile(
                                MediaView.this,
                                new String[] {f.getAbsolutePath()},
                                null,
                                new MediaScannerConnection.OnScanCompletedListener() {
                                    public void onScanCompleted(String path, Uri uri) {
                                        Intent mediaScanIntent =
                                                FileUtil.getFileIntent(
                                                        f,
                                                        new Intent(
                                                                Intent
                                                                        .ACTION_MEDIA_SCANNER_SCAN_FILE),
                                                        MediaView.this);
                                        MediaView.this.sendBroadcast(mediaScanIntent);

                                        final Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                        startActivity(
                                                Intent.createChooser(shareIntent, "Share GIF"));
                                        NotificationManager mNotificationManager =
                                                ContextCompat.getSystemService(
                                                        MediaView.this, NotificationManager.class);
                                        if (mNotificationManager != null) {
                                            mNotificationManager.cancel(1);
                                        }
                                    }
                                });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (findViewById(R.id.submission_image) != null && ((SubsamplingScaleImageView) findViewById(R.id.submission_image)) != null) {
            ((SubsamplingScaleImageView) findViewById(R.id.submission_image)).recycle();
        }
        if (gif != null) { // This is GifUtils.AsyncLoadGif for ExoVideoView
            gif.cancel();
            gif.cancel(true);
        }

        // Cleanup for direct GifDrawable
        if (activeGifDrawable != null) {
            activeGifDrawable.setCallback(null);
            activeGifDrawable.stop();
            activeGifDrawable = null;
        }
        if (directGifViewer != null) {
            directGifViewer.setImageDrawable(null);
        }

        if (!didLoadGif && fileLoc != null && !fileLoc.isEmpty()) {
            // This fileLoc seems related to an old way of handling gifs, review if still needed
            // For now, keeping it as is.
            new File(fileLoc).delete();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (videoView != null) {
            stopPosition = videoView.getCurrentPosition();
            videoView.pause();
            outState.putLong("position", stopPosition);
        }
    }

    public void hideOnLongClick() {
        (findViewById(R.id.gifheader))
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (findViewById(R.id.gifheader).getVisibility() == View.GONE) {
                                    AnimatorUtil.animateIn(findViewById(R.id.gifheader), 56);
                                    AnimatorUtil.fadeOut(findViewById(R.id.black));
                                    getWindow().getDecorView().setSystemUiVisibility(0);
                                } else {
                                    AnimatorUtil.animateOut(findViewById(R.id.gifheader));
                                    AnimatorUtil.fadeIn(findViewById(R.id.black));
                                    getWindow()
                                            .getDecorView()
                                            .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
                                }
                            }
                        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        overrideRedditSwipeAnywhere();
        super.onCreate(savedInstanceState);
        getTheme().applyStyle(new ColorPreferences(this).getDarkThemeSubreddit(""), true);

        gson = new Gson();
        imgurKey = SecretConstants.getImgurApiKey(this);

        if (savedInstanceState != null && savedInstanceState.containsKey("position")) {
            stopPosition = savedInstanceState.getLong("position");
        }

        setContentView(R.layout.activity_media);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());
        // Initialize the new ImageView for direct GIFs and cast it
        directGifViewer = (ImageView) findViewById(R.id.direct_gif_viewer);

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Hide speed button by default
        ImageView speedBtn = (ImageView) findViewById(R.id.speed);

        if (speedBtn != null) {
            speedBtn.setVisibility(View.GONE);
        }

        final String firstUrl = getIntent().getExtras().getString(EXTRA_DISPLAY_URL, "");
        contentUrl = getIntent().getExtras().getString(EXTRA_URL);

        if (contentUrl == null || contentUrl.isEmpty()) {
            finish();
            return;
        }

        if (contentUrl.contains("reddituploads.com")) {
            contentUrl = CompatUtil.fromHtml(contentUrl).toString();
        }
        if (contentUrl != null && shouldTruncate(contentUrl)) {
            contentUrl = contentUrl.substring(0, contentUrl.lastIndexOf("."));
        }

        actuallyLoaded = contentUrl;
        if (getIntent().hasExtra(SUBMISSION_URL)) {
            final int commentUrl = getIntent().getExtras().getInt(ADAPTER_POSITION);
            findViewById(R.id.comments)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    finish();
                                    SubmissionsView.datachanged(commentUrl);
                                }
                            });
        } else {
            findViewById(R.id.comments).setVisibility(View.GONE);
        }
        if (getIntent().hasExtra(SUBREDDIT)) {
            subreddit = getIntent().getExtras().getString(SUBREDDIT);
        }
        if (getIntent().hasExtra(EXTRA_SUBMISSION_TITLE)) {
            submissionTitle = getIntent().getExtras().getString(EXTRA_SUBMISSION_TITLE);
        }
        index = getIntent().getIntExtra("index", -1);
        findViewById(R.id.mute).setVisibility(View.GONE);

        if (getIntent().hasExtra(EXTRA_LQ)) {
            String lqUrl = getIntent().getStringExtra(EXTRA_DISPLAY_URL);
            displayImage(lqUrl);
            findViewById(R.id.hq)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    imageShown = false;
                                    doLoad(contentUrl);
                                    findViewById(R.id.hq).setVisibility(View.GONE);
                                }
                            });
        } else if (ContentType.isImgurImage(contentUrl)
                && SettingValues.loadImageLq
                && (SettingValues.lowResAlways
                        || (!NetworkUtil.isConnectedWifi(this) && SettingValues.lowResMobile))) {
            String url = contentUrl;
            url =
                    url.substring(0, url.lastIndexOf("."))
                            + (SettingValues.lqLow ? "m" : (SettingValues.lqMid ? "l" : "h"))
                            + url.substring(url.lastIndexOf("."));

            displayImage(url);
            findViewById(R.id.hq)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    imageShown = false;
                                    doLoad(contentUrl);
                                    findViewById(R.id.hq).setVisibility(View.GONE);
                                }
                            });
        } else {
            if (!firstUrl.isEmpty()
                    && contentUrl != null
                    && ContentType.displayImage(ContentType.getContentType(contentUrl))) {
                ((ProgressBar) findViewById(R.id.progress)).setIndeterminate(true);
                if (ContentType.isImgurHash(firstUrl)) {
                    displayImage(firstUrl + ".png");
                } else {
                    displayImage(firstUrl);
                }
            } else if (firstUrl.isEmpty()) {
                imageShown = false;
                ((ProgressBar) findViewById(R.id.progress)).setIndeterminate(true);
            }
            findViewById(R.id.hq).setVisibility(View.GONE);
            doLoad(contentUrl);
        }

        findViewById(R.id.more)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                showBottomSheetImage();
                            }
                        });
        findViewById(R.id.save)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String urlToSave =
                                        actuallyLoaded != null ? actuallyLoaded : contentUrl;
                                doImageSave(isGif, urlToSave, index);
                            }
                        });
        if (!SettingValues.imageDownloadButton) {
            findViewById(R.id.save).setVisibility(View.INVISIBLE);
        }

        hideOnLongClick();
    }

    public void doLoad(final String contentUrl) {
        ContentType.Type contentType = ContentType.getContentType(contentUrl);
        Log.v(TAG, "contentType: " + contentType.toString());
        switch (contentType) {
            case DEVIANTART:
                doLoadDeviantArt(contentUrl);
                break;
            case IMAGE:
                doLoadImage(contentUrl);
                break;
            case IMGUR:
                doLoadImgur(contentUrl);
                break;
            case LINK:
                if (contentUrl.startsWith("https://giphy.com/")) {
                    doLoadGif(contentUrl);
                }
                break;
            case XKCD:
                doLoadXKCD(contentUrl);
                break;
            case STREAMABLE:
            case VREDDIT_DIRECT:
            case VREDDIT_REDIRECT:
            case GIF:
                doLoadGif(contentUrl);
                break;
        }
    }

    public void doLoadGif(final String dat) {
        isGif = true;
        final ProgressBar loader = (ProgressBar) findViewById(R.id.gifprogress);
        final String gifUrl = GifUtils.AsyncLoadGif.formatUrl(dat); // Corrected static call

        if (gifUrl.toLowerCase().endsWith(".gif")) {
            // Handle direct .gif URLs with Movie/GifDrawable
            Log.v(TAG, "Loading direct GIF: " + gifUrl); // Changed to Log.v
            findViewById(R.id.gifarea).setVisibility(View.VISIBLE); // Ensure gifarea is visible for progress bar
            findViewById(R.id.submission_image).setVisibility(View.GONE);
            if (videoView != null) videoView.setVisibility(View.GONE); // Hide ExoVideoView
            directGifViewer.setVisibility(View.VISIBLE); // Show our ImageView
            loader.setVisibility(View.VISIBLE);
            loader.setIndeterminate(true); // Indeterminate for download phase

            // Clear any previous direct GIF
            if (activeGifDrawable != null) {
                activeGifDrawable.setCallback(null);
                activeGifDrawable.stop();
            }
            directGifViewer.setImageDrawable(null);

            GifUtils.downloadGif(gifUrl, new GifUtils.GifDownloadCallback() {
                @Override
                public void onGifDownloaded(File gifFile) {
                    if (isFinishing() || isDestroyed()) return;
                    runOnUiThread(() -> {
                        loader.setVisibility(View.GONE);
                        Movie movie = Movie.decodeFile(gifFile.getAbsolutePath());
                        if (movie != null) {
                            activeGifDrawable = new GifDrawable(movie, new Drawable.Callback() {
                                @Override
                                public void invalidateDrawable(@NonNull Drawable who) {
                                    directGifViewer.invalidate();
                                }

                                @Override
                                public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                                    directGifViewer.postDelayed(what, when - SystemClock.uptimeMillis());
                                }

                                @Override
                                public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                                    directGifViewer.removeCallbacks(what);
                                }
                            });
                            directGifViewer.setImageDrawable(activeGifDrawable);
                            activeGifDrawable.start();
                            didLoadGif = true; // Mark that a GIF was successfully loaded this way
                            fileLoc = gifFile.getAbsolutePath(); // Potentially for cleanup, though this might need review
                        } else {
                            Log.e(TAG, "Failed to decode direct GIF: " + gifUrl);
                            Toast.makeText(MediaView.this, "Failed to load GIF.", Toast.LENGTH_SHORT).show();
                            // Optionally, try to open externally or show a specific error view
                             finish(); // Or handle error more gracefully
                        }
                    });
                }

                @Override
                public void onGifDownloadFailed(Exception e) {
                    if (isFinishing() || isDestroyed()) return;
                    runOnUiThread(() -> {
                        loader.setVisibility(View.GONE);
                        Log.e(TAG, "Failed to download direct GIF: " + gifUrl, e);
                        Toast.makeText(MediaView.this, "Failed to download GIF.", Toast.LENGTH_SHORT).show();
                        finish(); // Or handle error more gracefully
                    });
                }
            }, this, submissionTitle);

        } else {
            // Existing logic for Gfycat, Streamable, v.redd.it, etc., using ExoVideoView via AsyncLoadGif
            Log.v(TAG, "Loading GIF/video via AsyncLoadGif (ExoPlayer): " + gifUrl); // Changed to Log.v
            if (directGifViewer != null) directGifViewer.setVisibility(View.GONE); // Hide our direct ImageViewer
            videoView = (ExoVideoView) findViewById(R.id.gif);
            videoView.setVisibility(View.VISIBLE); // Ensure ExoVideoView is visible
            videoView.setOnSingleTapListener(this); // Set the single tap listener

            videoView.clearFocus();
            findViewById(R.id.gifarea).setVisibility(View.VISIBLE);
            findViewById(R.id.submission_image).setVisibility(View.GONE);
            loader.setVisibility(View.VISIBLE); // Progress bar for AsyncLoadGif
            findViewById(R.id.progress).setVisibility(View.GONE); // Main progress bar for images

            // Ensure this.gif (AsyncLoadGif) is not mixed up with activeGifDrawable (GifDrawable)
            if (this.gif != null) { // Cancel previous AsyncLoadGif if any
                this.gif.cancel(true);
            }
            this.gif = // Assign to the class field 'gif'
                    new GifUtils.AsyncLoadGif(
                            this,
                            videoView,
                            loader,
                            findViewById(R.id.placeholder),
                            true, // closeIfNull
                            true, // autostart
                            ((TextView) findViewById(R.id.size)),
                            subreddit,
                            submissionTitle);
            // Show and attach speed button for GIFs (relevant for ExoVideoView)
            ImageView speedBtn = (ImageView) findViewById(R.id.speed);
            if (speedBtn != null) speedBtn.setVisibility(View.VISIBLE);
            videoView.attachMuteButton((ImageView) findViewById(R.id.mute));
            videoView.attachHqButton((ImageView) findViewById(R.id.hq));
            videoView.attachSpeedButton(speedBtn, this);
            this.gif.execute(gifUrl); // Use the formatted gifUrl
        }

    }

    public void doLoadImgur(String url) {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        final String finalUrl = url;
        String hash = url.substring(url.lastIndexOf("/"));

        if (NetworkUtil.isConnected(this)) {
            if (hash.startsWith("/")) hash = hash.substring(1);
            final String apiUrl = "https://api.imgur.com/3/image/" + hash;
            LogUtil.v(apiUrl);

            new AsyncTask<Void, Void, JsonObject>() {
                @Override
                protected JsonObject doInBackground(Void... params) {
                    return HttpUtil.getImgurJsonObject(Reddit.client, gson, apiUrl, imgurKey);
                }

                @Override
                protected void onPostExecute(JsonObject result) {
                    if (result != null && !result.isJsonNull() && result.has("error")) {
                        LogUtil.v("Error loading content");
                        (MediaView.this).finish();
                    } else {
                        try {
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

                                if (type.contains("gif")) {
                                    doLoadGif(urls);
                                } else if (!imageShown) { // only load if there is no image
                                    displayImage(urls);
                                }
                            } else if (result != null && result.has("data")) {
                                String type =
                                        result.get("data")
                                                .getAsJsonObject()
                                                .get("type")
                                                .getAsString();
                                String urls =
                                        result.get("data")
                                                .getAsJsonObject()
                                                .get("link")
                                                .getAsString();
                                String mp4 = "";
                                if (result.get("data").getAsJsonObject().has("mp4")) {
                                    mp4 =
                                            result.get("data")
                                                    .getAsJsonObject()
                                                    .get("mp4")
                                                    .getAsString();
                                }

                                if (type.contains("gif")) {
                                    doLoadGif(((mp4 == null || mp4.isEmpty()) ? urls : mp4));
                                } else if (!imageShown) { // only load if there is no image
                                    displayImage(urls);
                                }
                            } else {
                                if (!imageShown) doLoadImage(finalUrl);
                            }
                        } catch (Exception e2) {
                            e2.printStackTrace();
                            Intent i = new Intent(MediaView.this, Website.class);
                            i.putExtra(LinkUtil.EXTRA_URL, finalUrl);
                            MediaView.this.startActivity(i);
                            finish();
                        }
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void doLoadXKCD(String url) {
        if (!url.endsWith("/")) {
            url = url + "/";
        }

        if (NetworkUtil.isConnected(this)) {
            final String apiUrl = url + "info.0.json";
            LogUtil.v(apiUrl);

            final String finalUrl = url;
            new AsyncTask<Void, Void, JsonObject>() {
                @Override
                protected JsonObject doInBackground(Void... params) {
                    return HttpUtil.getJsonObject(Reddit.client, gson, apiUrl);
                }

                @Override
                protected void onPostExecute(final JsonObject result) {
                    if (result != null && !result.isJsonNull() && result.has("error")) {
                        LogUtil.v("Error loading content");
                        (MediaView.this).finish();
                    } else {
                        try {
                            if (result != null && !result.isJsonNull() && result.has("img")) {
                                doLoadImage(result.get("img").getAsString());
                                findViewById(R.id.submission_image)
                                        .setOnLongClickListener(
                                                new View.OnLongClickListener() {
                                                    @Override
                                                    public boolean onLongClick(View v) {
                                                        try {
                                                            new AlertDialog.Builder(MediaView.this)
                                                                    .setTitle(
                                                                            result.get("safe_title")
                                                                                    .getAsString())
                                                                    .setMessage(
                                                                            result.get("alt")
                                                                                    .getAsString())
                                                                    .show();
                                                        } catch (Exception ignored) {

                                                        }
                                                        return true;
                                                    }
                                                });
                            } else {
                                Intent i = new Intent(MediaView.this, Website.class);
                                i.putExtra(LinkUtil.EXTRA_URL, finalUrl);
                                MediaView.this.startActivity(i);
                                finish();
                            }
                        } catch (Exception e2) {
                            e2.printStackTrace();
                            Intent i = new Intent(MediaView.this, Website.class);
                            i.putExtra(LinkUtil.EXTRA_URL, finalUrl);
                            MediaView.this.startActivity(i);
                            finish();
                        }
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void doLoadDeviantArt(String url) {
        final String apiUrl = "http://backend.deviantart.com/oembed?url=" + url;
        LogUtil.v(apiUrl);
        new AsyncTask<Void, Void, JsonObject>() {
            @Override
            protected JsonObject doInBackground(Void... params) {
                return HttpUtil.getJsonObject(Reddit.client, gson, apiUrl);
            }

            @Override
            protected void onPostExecute(JsonObject result) {
                LogUtil.v("doLoad onPostExecute() called with: " + "result = [" + result + "]");
                if (result != null
                        && !result.isJsonNull()
                        && (result.has("fullsize_url") || result.has("url"))) {
                    String url;
                    if (result.has("fullsize_url")) {
                        url = result.get("fullsize_url").getAsString();
                    } else {
                        url = result.get("url").getAsString();
                    }
                    doLoadImage(url);
                } else {
                    Intent i = new Intent(MediaView.this, Website.class);
                    i.putExtra(LinkUtil.EXTRA_URL, contentUrl);
                    MediaView.this.startActivity(i);
                    finish();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void doLoadImage(String contentUrl) {
        if (contentUrl != null && contentUrl.contains("bildgur.de")) {
            contentUrl = contentUrl.replace("b.bildgur.de", "i.imgur.com");
        }
        if (contentUrl != null && ContentType.isImgurLink(contentUrl)) {
            contentUrl = contentUrl + ".png";
        }
        findViewById(R.id.gifprogress).setVisibility(View.GONE);

        if (contentUrl != null && contentUrl.contains("m.imgur.com")) {
            contentUrl = contentUrl.replace("m.imgur.com", "i.imgur.com");
        }
        if (contentUrl == null) {
            finish();
        }

        if ((contentUrl != null
                && !contentUrl.startsWith("https://i.redditmedia.com")
                && !contentUrl.startsWith("https://i.reddituploads.com")
                && !contentUrl.contains(
                        "imgur.com"))) { // we can assume redditmedia and imgur links are to direct
            // images and not websites
            findViewById(R.id.progress).setVisibility(View.VISIBLE);
            ((ProgressBar) findViewById(R.id.progress)).setIndeterminate(true);

            final String finalUrl2 = contentUrl;
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        URL obj = new URL(finalUrl2);
                        URLConnection conn = obj.openConnection();
                        final String type = conn.getHeaderField("Content-Type");
                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!imageShown
                                                && type != null
                                                && !type.isEmpty()
                                                && type.startsWith("image/")) {
                                            // is image
                                            if (type.contains("gif")) {
                                                doLoadGif(
                                                        finalUrl2
                                                                .replace(".jpg", ".gif")
                                                                .replace(".png", ".gif"));
                                            } else if (!imageShown) {
                                                displayImage(finalUrl2);
                                            }
                                            actuallyLoaded = finalUrl2;
                                        } else if (!imageShown) {
                                            Intent i = new Intent(MediaView.this, Website.class);
                                            i.putExtra(LinkUtil.EXTRA_URL, finalUrl2);
                                            MediaView.this.startActivity(i);
                                            finish();
                                        }
                                    }
                                });

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    findViewById(R.id.progress).setVisibility(View.GONE);
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        } else {
            displayImage(contentUrl);
        }

        actuallyLoaded = contentUrl;
    }

    public void displayImage(final String urlB) {
        LogUtil.v("Displaying " + urlB);
        final String url = StringEscapeUtils.unescapeHtml4(urlB);

        if (!imageShown) {
            actuallyLoaded = url;
            final SubsamplingScaleImageView i =
                    (SubsamplingScaleImageView) findViewById(R.id.submission_image);

            i.setMinimumDpi(70);
            i.setMinimumTileDpi(240);
            final ProgressBar bar = (ProgressBar) findViewById(R.id.progress);
            bar.setIndeterminate(false);
            bar.setProgress(0);

            final Handler handler = new Handler();
            final Runnable progressBarDelayRunner =
                    new Runnable() {
                        public void run() {
                            bar.setVisibility(View.VISIBLE);
                        }
                    };
            handler.postDelayed(progressBarDelayRunner, 500);

            ImageView fakeImage = new ImageView(MediaView.this);
            fakeImage.setLayoutParams(new LinearLayout.LayoutParams(i.getWidth(), i.getHeight()));
            fakeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

            File f = ((Reddit) getApplicationContext()).getImageLoader().getDiskCache().get(url);
            if (f != null && f.exists()) {
                imageShown = true;

                i.setOnImageEventListener(
                        new SubsamplingScaleImageView.DefaultOnImageEventListener() {
                            @Override
                            public void onImageLoadError(Exception e) {
                                imageShown = false;
                                LogUtil.v("No image displayed");
                            }
                        });
                try {
                    i.loader.setImage(ImageSource.uri(f.getAbsolutePath()));
                } catch (Exception e) {
                    imageShown = false;
                }
                (findViewById(R.id.progress)).setVisibility(View.GONE);
                handler.removeCallbacks(progressBarDelayRunner);

                previous = i.scale;
                final float base = i.scale;
                i.postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                i.setOnStateChangedListener(
                                        new SubsamplingScaleImageView
                                                .DefaultOnStateChangedListener() {
                                            @Override
                                            public void onScaleChanged(float newScale, int origin) {
                                                if (newScale > previous
                                                        && !hidden
                                                        && newScale > base) {
                                                    hidden = true;
                                                    final View base = findViewById(R.id.gifheader);

                                                    ValueAnimator va =
                                                            ValueAnimator.ofFloat(1.0f, 0.2f);
                                                    int mDuration = 250; // in millis
                                                    va.setDuration(mDuration);
                                                    va.addUpdateListener(
                                                            new ValueAnimator
                                                                    .AnimatorUpdateListener() {
                                                                public void onAnimationUpdate(
                                                                        ValueAnimator animation) {
                                                                    Float value =
                                                                            (Float)
                                                                                    animation
                                                                                            .getAnimatedValue();
                                                                    base.setAlpha(value);
                                                                }
                                                            });
                                                    va.start();
                                                    // hide
                                                } else if (newScale <= previous && hidden) {
                                                    hidden = false;
                                                    final View base = findViewById(R.id.gifheader);

                                                    ValueAnimator va =
                                                            ValueAnimator.ofFloat(0.2f, 1.0f);
                                                    int mDuration = 250; // in millis
                                                    va.setDuration(mDuration);
                                                    va.addUpdateListener(
                                                            new ValueAnimator
                                                                    .AnimatorUpdateListener() {
                                                                public void onAnimationUpdate(
                                                                        ValueAnimator animation) {
                                                                    Float value =
                                                                            (Float)
                                                                                    animation
                                                                                            .getAnimatedValue();
                                                                    base.setAlpha(value);
                                                                }
                                                            });
                                                    va.start();
                                                    // unhide
                                                }
                                                previous = newScale;
                                            }
                                        });
                            }
                        },
                        2000);

            } else {
                final TextView size = (TextView) findViewById(R.id.size);

                ((Reddit) getApplication())
                        .getImageLoader()
                        .displayImage(
                                url,
                                new ImageViewAware(fakeImage),
                                new DisplayImageOptions.Builder()
                                        .resetViewBeforeLoading(true)
                                        .cacheOnDisk(true)
                                        .imageScaleType(ImageScaleType.NONE)
                                        .cacheInMemory(false)
                                        .build(),
                                new ImageLoadingListener() {

                                    @Override
                                    public void onLoadingStarted(String imageUri, View view) {
                                        imageShown = true;
                                        if (size != null) size.setVisibility(View.VISIBLE);
                                    }

                                    @Override
                                    public void onLoadingFailed(
                                            String imageUri, View view, FailReason failReason) {
                                        Log.v(LogUtil.getTag(), "MediaView: LOADING FAILED");
                                        imageShown = false;
                                    }

                                    @Override
                                    public void onLoadingComplete(
                                            String imageUri, View view, Bitmap loadedImage) {
                                        imageShown = true;
                                        if (size != null) size.setVisibility(View.GONE);

                                        File f =
                                                ((Reddit) getApplicationContext())
                                                        .getImageLoader()
                                                        .getDiskCache()
                                                        .get(url);
                                        if (f != null && f.exists()) {
                                            i.loader.setImage(ImageSource.uri(f.getAbsolutePath()));
                                        } else {
                                            i.loader.setImage(ImageSource.bitmap(loadedImage));
                                        }
                                        (findViewById(R.id.progress)).setVisibility(View.GONE);
                                        handler.removeCallbacks(progressBarDelayRunner);

                                        previous = i.scale;
                                        final float base = i.scale;
                                        i.setOnStateChangedListener(
                                                new SubsamplingScaleImageView
                                                        .DefaultOnStateChangedListener() {
                                                    @Override
                                                    public void onScaleChanged(
                                                            float newScale, int origin) {
                                                        if (newScale > previous
                                                                && !hidden
                                                                && newScale > base) {
                                                            hidden = true;
                                                            final View base =
                                                                    findViewById(R.id.gifheader);

                                                            ValueAnimator va =
                                                                    ValueAnimator.ofFloat(
                                                                            1.0f, 0.2f);
                                                            int mDuration = 250; // in millis
                                                            va.setDuration(mDuration);
                                                            va.addUpdateListener(
                                                                    new ValueAnimator
                                                                            .AnimatorUpdateListener() {
                                                                        public void
                                                                                onAnimationUpdate(
                                                                                        ValueAnimator
                                                                                                animation) {
                                                                            Float value =
                                                                                    (Float)
                                                                                            animation
                                                                                                    .getAnimatedValue();
                                                                            base.setAlpha(value);
                                                                        }
                                                                    });
                                                            va.start();
                                                            // hide
                                                        } else if (newScale <= previous && hidden) {
                                                            hidden = false;
                                                            final View base =
                                                                    findViewById(R.id.gifheader);

                                                            ValueAnimator va =
                                                                    ValueAnimator.ofFloat(
                                                                            0.2f, 1.0f);
                                                            int mDuration = 250; // in millis
                                                            va.setDuration(mDuration);
                                                            va.addUpdateListener(
                                                                    new ValueAnimator
                                                                            .AnimatorUpdateListener() {
                                                                        public void
                                                                                onAnimationUpdate(
                                                                                        ValueAnimator
                                                                                                animation) {
                                                                            Float value =
                                                                                    (Float)
                                                                                            animation
                                                                                                    .getAnimatedValue();
                                                                            base.setAlpha(value);
                                                                        }
                                                                    });
                                                            va.start();
                                                            // unhide
                                                        }
                                                        previous = newScale;
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onLoadingCancelled(String imageUri, View view) {
                                        Log.v(LogUtil.getTag(), "MediaView: LOADING CANCELLED");
                                    }
                                },
                                new ImageLoadingProgressListener() {
                                    @Override
                                    public void onProgressUpdate(
                                            String imageUri, View view, int current, int total) {
                                        TextView size = (TextView) findViewById(R.id.size);
                                        if (size != null) {
                                            size.setText(String.format("%d%%", (int) (100.0 * current / total)));
                                        }
                                    }
                                });
            }
        }
    }

    private void showFirstDialog() {
        runOnUiThread(() -> DialogUtil.showFirstDialog(MediaView.this));
    }

    private void showErrorDialog() {
        runOnUiThread(() -> DialogUtil.showErrorDialog(MediaView.this));
    }

    @Override
    public boolean onSingleTap(MotionEvent event) {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float tapY = event.getRawY();

        View gifHeader = findViewById(R.id.gifheader);

        int thresholdY;
        if (gifHeader != null && gifHeader.getVisibility() == View.VISIBLE) {
            int[] location = new int[2];
            gifHeader.getLocationOnScreen(location);
            thresholdY = location[1];
        } else {
            thresholdY = (int) (screenHeight * 0.75);
        }

        if (tapY >= thresholdY) {
            View blackOverlay = findViewById(R.id.black);

            if (gifHeader != null && blackOverlay != null) {
                if (gifHeader.getVisibility() == View.GONE) {
                    AnimatorUtil.animateIn(gifHeader, 56);
                    AnimatorUtil.fadeOut(blackOverlay);
                    getWindow().getDecorView().setSystemUiVisibility(0);
                } else {
                    AnimatorUtil.animateOut(gifHeader);
                    AnimatorUtil.fadeIn(blackOverlay);
                    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onStoragePermissionGranted() {
        super.onStoragePermissionGranted();
        // Retry the save operation with the new permissions
        if (lastContentUrl != null) {
            Intent i = new Intent(this, ImageDownloadNotificationService.class);
            // always download the original file, or use the cached original if that is currently
            // displayed
            i.putExtra("actuallyLoaded", lastContentUrl);
            i.putExtra("downloadUri", StorageUtil.getStorageUri(this).toString());
            if (subreddit != null && !subreddit.isEmpty()) i.putExtra("subreddit", subreddit);
            if (submissionTitle != null) i.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
            i.putExtra("index", index);

            Log.d(TAG, "Starting download service with URI: " + StorageUtil.getStorageUri(this));
            ComponentName component = startService(i);
            if (component == null) {
                Log.e(TAG, "Failed to start download service");
                DialogUtil.showErrorDialog(this);
            } else {
                Toast.makeText(this, getString(R.string.mediaview_downloading), Toast.LENGTH_SHORT)
                        .show();
            }
            lastContentUrl = null;
        }
    }
}
