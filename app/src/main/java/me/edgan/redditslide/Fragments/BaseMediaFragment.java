package me.edgan.redditslide.Fragments;


import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import me.edgan.redditslide.Activities.Website;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.Views.ImageSource;
import me.edgan.redditslide.Views.SubsamplingScaleImageView;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.GsonUtil;
import me.edgan.redditslide.util.HttpUtil;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;
import okhttp3.OkHttpClient;

/** Created by ccrama on 6/2/2015. */

/**
 * Shared implementation for the two media fragments (submission media and comment-link media):
 * DeviantArt/Imgur/XKCD API resolution, generic image loading, and the zoomable image display
 * with progress reporting. Subclasses keep their own doLoad entry points, gif playback, and
 * click wiring, and route the differences through the three hooks below.
 */
public abstract class BaseMediaFragment extends Fragment {
    @Nullable public String contentUrl;
    @Nullable public String actuallyLoaded;

    // rootView is bound by the subclass's onCreateView; client, gson and imgurKey by its
    // onCreate. Both run before anything below can touch them.
    @SuppressWarnings("NullAway.Init")
    protected ViewGroup rootView;

    protected boolean imageShown;
    protected float previous;
    protected boolean hidden;

    @SuppressWarnings("NullAway.Init")
    protected OkHttpClient client;

    @SuppressWarnings("NullAway.Init")
    protected Gson gson;

    @SuppressWarnings("NullAway.Init")
    protected String imgurKey;

    /** Plays a gif/mp4 from a direct media url; the two fragments route this differently. */
    protected abstract void playGifDirect(String url);

    /** Url pre-processing before loading; MediaFragment HTML-unescapes here. */
    protected @Nullable String prepareUrl(@Nullable String url) {
        return url;
    }

    /** Called when the imgur API lookup fails; by default opens the link in the web view. */
    protected void onImgurLoadFailed(String finalUrl) {
        if (getContext() != null) {
            Intent i = new Intent(getContext(), Website.class);
            i.putExtra(LinkUtil.EXTRA_URL, finalUrl);
            getContext().startActivity(i);
        }
    }

    public void doLoadDeviantArt(@Nullable String url) {
        final String apiUrl = "http://backend.deviantart.com/oembed?url=" + url;
        LogUtil.v(apiUrl);
        new AsyncTask<Void, Void, JsonObject>() {
            @Override
            protected @Nullable JsonObject doInBackground(Void... params) {
                return HttpUtil.getJsonObject(client, gson, apiUrl);
            }

            @Override
            protected void onPostExecute(@Nullable JsonObject result) {
                LogUtil.v("doLoad onPostExecute() called with: " + "result = [" + result + "]");
                if (getActivity() == null || rootView == null) {
                    return; // response arrived after the fragment was torn down
                }
                if (result != null
                        && !result.isJsonNull()
                        && !deviantArtImageUrl(result).isEmpty()) {
                    doLoadImage(deviantArtImageUrl(result));
                } else {
                    Intent i = new Intent(getActivity(), Website.class);
                    i.putExtra(LinkUtil.EXTRA_URL, contentUrl);
                    getActivity().startActivity(i);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void doLoadImgur(@Nullable String url) {
        if (url == null) {
            return;
        }

        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        final String finalUrl = url;
        String hash = url.substring(url.lastIndexOf("/"));

        if (NetworkUtil.isConnected(getActivity())) {

            if (hash.startsWith("/")) hash = hash.substring(1);
            final String apiUrl = "https://api.imgur.com/3/image/" + hash;
            LogUtil.v(apiUrl);

            new AsyncTask<Void, Void, JsonObject>() {
                @Override
                protected @Nullable JsonObject doInBackground(Void... params) {
                    return HttpUtil.getImgurJsonObject(client, gson, apiUrl, imgurKey);
                }

                @Override
                protected void onPostExecute(@Nullable JsonObject result) {
                    if (getActivity() == null || rootView == null) {
                        return; // response arrived after the fragment was torn down
                    }
                    if (result != null && !result.isJsonNull() && result.has("error")) {
                        LogUtil.v("Error loading content");
                        (getActivity()).finish();
                    } else {
                        try {
                            HttpUtil.ImgurMedia media = HttpUtil.parseImgurMedia(result);
                            if (media == null) {
                                if (!imageShown) doLoadImage(finalUrl);
                            } else if (media.isGif()) {
                                playGifDirect(media.getGifUrl());
                            } else if (!imageShown) { // only load if there is no image
                                // doLoadImage (not displayImage): it rechecks the content type,
                                // catching gifs the imgur API labels as images.
                                doLoadImage(media.getImageUrl());
                            }
                        } catch (Exception e) {
                            LogUtil.e(
                                    e,
                                    "Error loading Imgur image finalUrl = ["
                                            + finalUrl
                                            + "], apiUrl = ["
                                            + apiUrl
                                            + "]");
                            onImgurLoadFailed(finalUrl);
                        }
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void doLoadXKCD(@Nullable String url) {
        if (url == null) {
            return;
        }

        if (!url.endsWith("/")) {
            url = url + "/";
        }

        if (NetworkUtil.isConnected(getContext())) {
            final String apiUrl = url + "info.0.json";
            LogUtil.v(apiUrl);

            final String finalUrl = url;
            new AsyncTask<Void, Void, JsonObject>() {
                @Override
                protected @Nullable JsonObject doInBackground(Void... params) {
                    return HttpUtil.getJsonObject(client, gson, apiUrl);
                }

                @Override
                protected void onPostExecute(final @Nullable JsonObject result) {
                    if (getActivity() == null || rootView == null) {
                        return; // response arrived after the fragment was torn down
                    }
                    if (result != null && !result.isJsonNull() && result.has("error")) {
                        LogUtil.v("Error loading content");
                    } else {
                        try {
                            // Absent, JSON null, or a non-string "img" all mean there is no comic to
                            // show, and an empty uri reads to the image loader as a completed load
                            // with a null bitmap. Fall through to the web view instead.
                            if (result != null
                                    && !result.isJsonNull()
                                    && !GsonUtil.string(result, "img", "").isEmpty()) {
                                doLoadImage(GsonUtil.string(result, "img", ""));
                                rootView.findViewById(R.id.submission_image)
                                        .setOnLongClickListener(
                                                new View.OnLongClickListener() {
                                                    @Override
                                                    public boolean onLongClick(View v) {
                                                        try {
                                                            DialogUtil.showWithCardBackground(new AlertDialog.Builder(getContext())
                                                                    .setTitle(
                                                                            GsonUtil.string(result, "safe_title", ""))
                                                                    .setMessage(
                                                                            GsonUtil.string(result, "alt", ""))
                                                                    );
                                                        } catch (Exception ignored) {
                                                            // Alt-text dialog on a detached fragment.
                                                        }
                                                        return true;
                                                    }
                                                });
                            } else {
                                Intent i = new Intent(getContext(), Website.class);
                                i.putExtra(LinkUtil.EXTRA_URL, finalUrl);
                                getContext().startActivity(i);
                            }
                        } catch (Exception e2) {
                            LogUtil.e(e2, getClass().getSimpleName() + ".onLongClick failed");
                            Intent i = new Intent(getContext(), Website.class);
                            i.putExtra(LinkUtil.EXTRA_URL, finalUrl);
                            getContext().startActivity(i);
                        }
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void doLoadImage(@Nullable String contentUrl) {
        if (contentUrl != null && contentUrl.contains("bildgur.de")) {
            contentUrl = contentUrl.replace("b.bildgur.de", "i.imgur.com");
        }
        if (contentUrl != null && ContentType.isImgurLink(contentUrl)) {
            contentUrl = contentUrl + ".png";
        }

        rootView.findViewById(R.id.gifprogress).setVisibility(View.GONE);

        if (contentUrl != null && contentUrl.contains("m.imgur.com")) {
            contentUrl = contentUrl.replace("m.imgur.com", "i.imgur.com");
        }

        contentUrl = prepareUrl(contentUrl);

        if ((contentUrl != null
                && !contentUrl.startsWith("https://i.redditmedia.com")
                && !contentUrl.startsWith("https://i.reddituploads.com")
                && !contentUrl.contains(
                        "imgur.com"))) { // we can assume redditmedia and imgur links are to direct
            // images and not websites
            rootView.findViewById(R.id.progress).setVisibility(View.VISIBLE);
            ((ProgressBar) rootView.findViewById(R.id.progress)).setIndeterminate(true);

            final String finalUrl2 = contentUrl;
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        URL obj = new URL(finalUrl2);
                        URLConnection conn = obj.openConnection();
                        final String type = conn.getHeaderField("Content-Type");
                        if (getActivity() != null) {
                            getActivity()
                                    .runOnUiThread(
                                            new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!imageShown
                                                            && !Strings.isNullOrEmpty(type)
                                                            && type.startsWith("image/")) {
                                                        // is image
                                                        if (type.contains("gif")) {
                                                            playGifDirect(
                                                                    finalUrl2
                                                                            .replace(".jpg", ".gif")
                                                                            .replace(
                                                                                    ".png",
                                                                                    ".gif"));
                                                        } else if (!imageShown) {
                                                            displayImage(finalUrl2);
                                                        }
                                                        actuallyLoaded = finalUrl2;
                                                    } else if (!imageShown) {
                                                        Intent i =
                                                                new Intent(
                                                                        getActivity(),
                                                                        Website.class);
                                                        i.putExtra(LinkUtil.EXTRA_URL, finalUrl2);
                                                        getActivity().startActivity(i);
                                                    }
                                                }
                                            });
                        }

                    } catch (IOException e) {
                        LogUtil.e(e, "Error loading image finalUrl2 = [" + finalUrl2 + "]");
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    rootView.findViewById(R.id.progress).setVisibility(View.GONE);
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        } else {
            displayImage(contentUrl);
        }

        actuallyLoaded = contentUrl;
    }

    public void displayImage(final @Nullable String urlB) {
        if (getActivity() == null || rootView == null) {
            return; // load finished after the fragment was torn down
        }
        final String url = prepareUrl(urlB);

        if (!imageShown) {
            actuallyLoaded = url;
            final SubsamplingScaleImageView i = rootView.findViewById(R.id.submission_image);

            i.setMinimumDpi(70);
            i.setMinimumTileDpi(240);
            final ProgressBar bar = rootView.findViewById(R.id.progress);
            bar.setIndeterminate(false);
            LogUtil.v("Displaying image " + url);
            bar.setProgress(0);

            final Handler handler = new Handler();
            final Runnable progressBarDelayRunner =
                    new Runnable() {
                        @Override public void run() {
                            bar.setVisibility(View.VISIBLE);
                        }
                    };
            handler.postDelayed(progressBarDelayRunner, 500);

            ImageView fakeImage = new ImageView(getActivity());
            fakeImage.setLayoutParams(new LinearLayout.LayoutParams(i.getWidth(), i.getHeight()));
            fakeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

            File f =
                    ((Reddit) getActivity().getApplicationContext())
                            .getImageLoader()
                            .getDiskCache()
                            .get(url);
            if (f != null && f.exists()) {
                imageShown = true;

                try {
                    i.loader.setImage(ImageSource.uri(f.getAbsolutePath()));
                } catch (Exception e) {
                    // todo  i.setImage(ImageSource.bitmap(loadedImage));
                }
                (rootView.findViewById(R.id.progress)).setVisibility(View.GONE);
                handler.removeCallbacks(progressBarDelayRunner);

                previous = i.scale;
                final float base = i.scale;
                i.setOnStateChangedListener(
                        new SubsamplingScaleImageView.DefaultOnStateChangedListener() {
                            @Override
                            public void onScaleChanged(float newScale, int origin) {
                                if (newScale > previous && !hidden && newScale > base) {
                                    hidden = true;
                                    final View base = rootView.findViewById(R.id.base);

                                    ValueAnimator va = ValueAnimator.ofFloat(1.0f, 0.2f);
                                    int mDuration = 250; // in millis
                                    va.setDuration(mDuration);
                                    va.addUpdateListener(
                                            new ValueAnimator.AnimatorUpdateListener() {
                                                @Override public void onAnimationUpdate(
                                                        ValueAnimator animation) {
                                                    Float value =
                                                            (Float) animation.getAnimatedValue();
                                                    base.setAlpha(value);
                                                }
                                            });
                                    va.start();
                                    // hide
                                } else if (newScale <= previous && hidden) {
                                    hidden = false;
                                    final View base = rootView.findViewById(R.id.base);

                                    ValueAnimator va = ValueAnimator.ofFloat(0.2f, 1.0f);
                                    int mDuration = 250; // in millis
                                    va.setDuration(mDuration);
                                    va.addUpdateListener(
                                            new ValueAnimator.AnimatorUpdateListener() {
                                                @Override public void onAnimationUpdate(
                                                        ValueAnimator animation) {
                                                    Float value =
                                                            (Float) animation.getAnimatedValue();
                                                    base.setAlpha(value);
                                                }
                                            });
                                    va.start();
                                    // unhide
                                }
                                previous = newScale;
                            }
                        });
            } else {
                ((Reddit) getActivity().getApplicationContext())
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
                                    }

                                    @Override
                                    public void onLoadingFailed(
                                            String imageUri, View view, FailReason failReason) {
                                        Log.v(LogUtil.getTag(), getClass().getSimpleName() + ": LOADING FAILED");
                                    }

                                    @Override
                                    public void onLoadingComplete(
                                            String imageUri, View view, @Nullable Bitmap loadedImage) {
                                        imageShown = true;
                                        File f = null;
                                        if (getActivity() != null) {
                                            f =
                                                    ((Reddit) getActivity().getApplicationContext())
                                                            .getImageLoader()
                                                            .getDiskCache()
                                                            .get(url);
                                        }
                                        if (f != null && f.exists()) {
                                            i.loader.setImage(ImageSource.uri(f.getAbsolutePath()));
                                        } else if (loadedImage != null) {
                                            // A completed load with no bitmap is how the loader
                                            // reports an unusable uri, and ImageSource.bitmap
                                            // throws on a null rather than ignoring it.
                                            i.loader.setImage(ImageSource.bitmap(loadedImage));
                                        }
                                        (rootView.findViewById(R.id.progress))
                                                .setVisibility(View.GONE);
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
                                                                    rootView.findViewById(
                                                                            R.id.base);

                                                            ValueAnimator va =
                                                                    ValueAnimator.ofFloat(
                                                                            1.0f, 0.2f);
                                                            int mDuration = 250; // in millis
                                                            va.setDuration(mDuration);
                                                            va.addUpdateListener(
                                                                    new ValueAnimator
                                                                            .AnimatorUpdateListener() {
                                                                        @Override public void
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
                                                                    rootView.findViewById(
                                                                            R.id.base);

                                                            ValueAnimator va =
                                                                    ValueAnimator.ofFloat(
                                                                            0.2f, 1.0f);
                                                            int mDuration = 250; // in millis
                                                            va.setDuration(mDuration);
                                                            va.addUpdateListener(
                                                                    new ValueAnimator
                                                                            .AnimatorUpdateListener() {
                                                                        @Override public void
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
                                        Log.v(LogUtil.getTag(), getClass().getSimpleName() + ": LOADING CANCELLED");
                                    }
                                },
                                new ImageLoadingProgressListener() {
                                    @Override
                                    public void onProgressUpdate(
                                            String imageUri, View view, int current, int total) {
                                        ((ProgressBar) rootView.findViewById(R.id.progress))
                                                .setProgress(Math.round(100.0f * current / total));
                                    }
                                });
            }
        }
    }

    /**
     * The oEmbed image url, preferring the full-size one. Empty when the response carries neither as
     * a string — callers treat that as "not an image" rather than handing the loader an empty uri,
     * which it reads as a completed load with a null bitmap.
     */
    private static String deviantArtImageUrl(JsonObject result) {
        final String fullsize = GsonUtil.string(result, "fullsize_url", "");
        return fullsize.isEmpty() ? GsonUtil.string(result, "url", "") : fullsize;
    }

}
