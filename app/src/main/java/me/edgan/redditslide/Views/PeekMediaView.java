package me.edgan.redditslide.Views;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.DownloadListener;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import androidx.annotation.Nullable;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.edgan.redditslide.Adapters.ImageGridAdapter;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.ForceTouch.PeekViewActivity;
import me.edgan.redditslide.ImgurAlbum.AlbumUtils;
import me.edgan.redditslide.ImgurAlbum.Image;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SecretConstants;
import me.edgan.redditslide.Tumblr.Photo;
import me.edgan.redditslide.Tumblr.TumblrUtils;
import me.edgan.redditslide.util.AdBlocker;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.GsonUtil;
import me.edgan.redditslide.util.HttpUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;
import net.dean.jraw.models.Submission;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.NullMarked;

/** Created by ccrama on 3/5/2015. */
@NullMarked
public class PeekMediaView extends RelativeLayout {

    ContentType.Type contentType = ContentType.Type.NONE;
    @Nullable private GifUtils.AsyncLoadGif gif;

    // videoView, website, progress and image are all bound by init(), which every constructor
    // calls, from views the peek_media_view layout always defines.
    @SuppressWarnings("NullAway.Init")
    private ExoVideoView videoView;

    @SuppressWarnings("NullAway.Init") // assigned in init
    public WebView website;

    @SuppressWarnings("NullAway.Init") // assigned in init
    private ProgressBar progress;

    public PeekMediaView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public PeekMediaView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PeekMediaView(Context context) {
        super(context);
        init();
    }

    boolean web;
    float origY = 0;

    public void doClose() {
        website.setVisibility(View.GONE);
        website.loadUrl("about:blank");
        videoView.stop();
        if (gif != null) gif.cancel(true);
    }

    public void doScroll(MotionEvent event) {
        if (origY == 0) {
            origY = event.getY();
        }
        if (web
                && website.canScrollVertically((origY - event.getY()) > 0 ? 0 : 1)
                && Math.abs(origY - event.getY()) > website.getHeight() / 4.0f) {
            website.scrollBy(0, (int) -(origY - event.getY()) / 5);
        }
    }

    public void setUrlOrSubmission(String url, Submission submission) {
        contentType = ContentType.getContentType(url);
        switch (contentType) {
            case ALBUM:
                doLoadAlbum(url);
                progress.setIndeterminate(true);
                break;
            case TUMBLR:
                doLoadTumblr(url);
                progress.setIndeterminate(true);
                break;
            case EMBEDDED:
            case EXTERNAL:
            case LINK:
            case VIDEO:
            case SELF:
            case SPOILER:
            case NONE:
                doLoadLink(url);
                progress.setIndeterminate(false);
                break;
            case REDDIT_GALLERY:
                doLoadRedditGallery(submission);
                progress.setIndeterminate(true);
                break;
            case REDDIT:
                progress.setIndeterminate(true);
                doLoadReddit(url);
                break;
            case DEVIANTART:
                doLoadDeviantArt(url);
                progress.setIndeterminate(false);
                break;
            case IMAGE:
                doLoadImage(url);
                progress.setIndeterminate(false);
                break;
            case XKCD:
                doLoadXKCD(url);
                progress.setIndeterminate(false);
                break;
            case IMGUR:
                doLoadImgur(url);
                progress.setIndeterminate(false);
                break;
            case GIF:
            case VREDDIT_REDIRECT:
            case VREDDIT_DIRECT:
            case STREAMABLE:
                doLoadGif(url);
                progress.setIndeterminate(false);
                break;
        }
    }

    private void doLoadAlbum(final String url) {
        albumCallback(url).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * The loader {@link #doLoadAlbum} runs, separated from running it so the test can drive
     * {@code doWithData} with a hand-built album and no fetch behind it.
     */
    AlbumUtils.GetAlbumWithCallback albumCallback(final String url) {
        return new AlbumUtils.GetAlbumWithCallback(url, (PeekViewActivity) getContext()) {

            @Override
            public void onError() {
                ((PeekViewActivity) getContext())
                        .runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        doLoadLink(url);
                                    }
                                });
            }

            @Override
            public boolean doWithData(final @Nullable List<Image> jsonElements) {
                // Nothing usable came back, so there is nothing to index below; super has already
                // sent this to onError(), which falls back to opening the link.
                if (!super.doWithData(jsonElements)) {
                    return false;
                }
                progress.setVisibility(View.GONE);
                images = new ArrayList<>(jsonElements);
                // Screened like the album list's rows: getImageUrl() concatenates hash and
                // extension blindly, so an entry missing either would peek at
                // "https://i.imgur.com/null.jpg". Skipped rather than reported, as the Tumblr path
                // below does: onError is for an album with nothing in it, and there is nothing to
                // fall back to for one entry out of several.
                final Image first = images.get(0);
                if (first != null && first.hasImageUrl()) {
                    displayImage(first.getImageUrl());
                }
                if (images.size() > 1) {
                    GridView grid = findViewById(R.id.grid_area);
                    grid.setNumColumns(5);
                    grid.setVisibility(VISIBLE);
                    grid.setAdapter(new ImageGridAdapter(getContext(), images));
                }
                return true;
            }
        };
    }

    private void doLoadTumblr(final String url) {
        tumblrCallback(url).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * The loader {@link #doLoadTumblr} runs, separated from running it so the test can drive
     * {@code doWithData} with a hand-built post and no fetch behind it — the same split as
     * {@link #albumCallback}, which screens its first entry the same way.
     */
    TumblrUtils.GetTumblrPostWithCallback tumblrCallback(final String url) {
        return new TumblrUtils.GetTumblrPostWithCallback(url, (PeekViewActivity) getContext()) {

            @Override
            public void onError() {
                ((PeekViewActivity) getContext())
                        .runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        doLoadLink(url);
                                    }
                                });
            }

            @Override
            public boolean doWithData(final @Nullable List<Photo> jsonElements) {
                // A post with no photos leaves nothing to index below; super has already sent this
                // to onError(), which falls back to opening the link.
                if (!super.doWithData(jsonElements)) {
                    return false;
                }
                progress.setVisibility(View.GONE);
                tumblrImages = new ArrayList<>(jsonElements);
                // A photo whose JSON carried no original_size — or that Jackson left null for a null
                // element in the photos array — has nothing to show. Skipped rather than reported:
                // onError above is for a post with no photos at all, and there is nothing to fall
                // back to for one photo out of several.
                final Photo first = tumblrImages.get(0);
                final String firstUrl = first == null ? null : first.getOriginalUrl();
                if (firstUrl != null) {
                    displayImage(firstUrl);
                }
                if (tumblrImages.size() > 1) {
                    GridView grid = findViewById(R.id.grid_area);
                    grid.setNumColumns(5);
                    grid.setVisibility(VISIBLE);
                    grid.setAdapter(new ImageGridAdapter(getContext(), tumblrImages, true));
                }
                return true;
            }
        };
    }

    @Nullable List<Image> images;
    @Nullable List<Photo> tumblrImages;

    @Nullable WebChromeClient client;
    @Nullable WebViewClient webClient;

    public void setValue(int newProgress) {
        progress.setProgress(newProgress);
        if (newProgress == 100) {
            progress.setVisibility(View.GONE);
        } else if (progress.getVisibility() == View.GONE) {
            progress.setVisibility(View.VISIBLE);
        }
    }

    private class MyWebViewClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            setValue(newProgress);
            super.onProgressChanged(view, newProgress);
        }
    }

    public void doLoadXKCD(final String url) {
        if (NetworkUtil.isConnected(getContext())) {
            final String apiUrl = (url.endsWith("/") ? url : (url + "/")) + "info.0.json";

            new AsyncTask<Void, Void, JsonObject>() {
                @Override
                protected @Nullable JsonObject doInBackground(Void... params) {
                    return HttpUtil.getJsonObject(Reddit.client, new Gson(), apiUrl);
                }

                @Override
                protected void onPostExecute(final @Nullable JsonObject result) {
                    if (result != null && !result.isJsonNull() && result.has("error")) {
                        doLoadLink(url);
                    } else {
                        try {
                            // Absent, JSON null, or a non-string "img" all mean there is no comic to
                            // show, and an empty uri reads to the image loader as a completed load
                            // with a null bitmap. Fall through to the web view instead.
                            if (result != null
                                    && !result.isJsonNull()
                                    && !GsonUtil.string(result, "img", "").isEmpty()) {
                                doLoadImage(GsonUtil.string(result, "img", ""));
                            } else {
                                doLoadLink(url);
                            }
                        } catch (Exception e2) {
                            doLoadLink(url);
                        }
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    public void doLoadLink(String url) {
        client = new MyWebViewClient();
        web = true;
        webClient =
                new WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        website.loadUrl(
                                "javascript:(function() {"
                                    + " document.getElementsByTagName('video')[0].play(); })()");
                    }

                    private Map<String, Boolean> loadedUrls = new HashMap<>();

                    @Override
                    public @Nullable WebResourceResponse shouldInterceptRequest(
                            WebView view, String url) {
                        boolean ad;
                        if (!loadedUrls.containsKey(url)) {
                            ad = AdBlocker.isAd(url, getContext());
                            loadedUrls.put(url, ad);
                        } else {
                            ad = loadedUrls.get(url);
                        }
                        return ad
                                ? AdBlocker.createEmptyResource()
                                : super.shouldInterceptRequest(view, url);
                    }
                };
        website.setVisibility(View.VISIBLE);
        website.setWebChromeClient(client);
        website.setWebViewClient(webClient);
        website.getSettings().setBuiltInZoomControls(true);
        website.getSettings().setDisplayZoomControls(false);
        website.getSettings().setJavaScriptEnabled(true);
        website.getSettings().setLoadWithOverviewMode(true);
        website.getSettings().setUseWideViewPort(true);
        website.setDownloadListener(
                new DownloadListener() {
                    @Override public void onDownloadStart(
                            String url,
                            String userAgent,
                            String contentDisposition,
                            String mimetype,
                            long contentLength) {
                        // Downloads using download manager on default browser
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        getContext().startActivity(i);
                    }
                });
        website.loadUrl(url);
    }

    private void doLoadReddit(String url) {
        RedditItemView v = findViewById(R.id.reddit_item);
        v.loadUrl(this, url, progress);
    }

    public void doLoadDeviantArt(String url) {
        final String apiUrl = "http://backend.deviantart.com/oembed?url=" + url;
        LogUtil.v(apiUrl);
        new AsyncTask<Void, Void, JsonObject>() {
            @Override
            protected @Nullable JsonObject doInBackground(Void... params) {
                return HttpUtil.getJsonObject(Reddit.client, new Gson(), apiUrl);
            }

            @Override
            protected void onPostExecute(@Nullable JsonObject result) {
                LogUtil.v("doLoad onPostExecute() called with: " + "result = [" + result + "]");
                if (result != null
                        && !result.isJsonNull()
                        && !deviantArtImageUrl(result).isEmpty()) {
                    doLoadImage(deviantArtImageUrl(result));
                } else {
                    // todo error out
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void doLoadImgur(String url) {
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        final String finalUrl = url;
        String hash = url.substring(url.lastIndexOf("/"));

        if (NetworkUtil.isConnected(getContext())) {
            if (hash.startsWith("/")) hash = hash.substring(1);
            final String apiUrl = "https://api.imgur.com/3/image/" + hash;
            LogUtil.v(apiUrl);

            // Capture the Context on the UI thread; doInBackground() runs on a worker thread.
            final Context context = getContext();
            new AsyncTask<Void, Void, JsonObject>() {
                @Override
                protected @Nullable JsonObject doInBackground(Void... params) {
                    return HttpUtil.getImgurJsonObject(
                            Reddit.client,
                            new Gson(),
                            apiUrl,
                            SecretConstants.getImgurApiKey(context));
                }

                @Override
                protected void onPostExecute(JsonObject result) {
                    if (result != null && !result.isJsonNull() && result.has("error")) {
                        /// todo error out
                    } else {
                        try {
                            HttpUtil.ImgurMedia media = HttpUtil.parseImgurMedia(result);
                            if (media == null) {
                                if (!imageShown) doLoadImage(finalUrl);
                            } else if (media.isGif()) {
                                doLoadGif(media.getGifUrl());
                            } else if (!imageShown) { // only load if there is no image
                                displayImage(media.getImageUrl());
                            }
                        } catch (Exception e2) {
                            // todo error out
                        }
                    }
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    boolean imageShown;

    public void doLoadImage(String contentUrl) {
        if (contentUrl != null && contentUrl.contains("bildgur.de")) {
            contentUrl = contentUrl.replace("b.bildgur.de", "i.imgur.com");
        }
        if (contentUrl != null && ContentType.isImgurLink(contentUrl)) {
            contentUrl = contentUrl + ".png";
        }
        if (contentUrl != null && contentUrl.contains("m.imgur.com")) {
            contentUrl = contentUrl.replace("m.imgur.com", "i.imgur.com");
        }
        if (contentUrl == null) {
            // todo error out
        }

        if ((contentUrl != null
                && !contentUrl.startsWith("https://i.redditmedia.com")
                && !contentUrl.startsWith("https://i.reddituploads.com")
                && !contentUrl.contains(
                        "imgur.com"))) { // we can assume redditmedia and imgur links are to direct
            // images and not websites
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(true);

            final String finalUrl2 = contentUrl;
            // Capture the Context on the UI thread; doInBackground() runs on a worker thread.
            final Context context = getContext();
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        URL obj = new URL(finalUrl2);
                        URLConnection conn = obj.openConnection();
                        final String type = conn.getHeaderField("Content-Type");
                        ((PeekViewActivity) context)
                                .runOnUiThread(
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
                                                    // todo error out
                                                }
                                            }
                                        });

                    } catch (IOException e) {
                        LogUtil.e(e, "PeekMediaView.run failed");
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    progress.setVisibility(View.GONE);
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        } else {
            displayImage(contentUrl);
        }
    }

    @Nullable String actuallyLoaded;

    public void doLoadGif(final String dat) {
        videoView = findViewById(R.id.gif);
        videoView.clearFocus();
        findViewById(R.id.gifarea).setVisibility(View.VISIBLE);
        findViewById(R.id.submission_image).setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        gif =
                new GifUtils.AsyncLoadGif(
                        (PeekViewActivity) getContext(),
                        videoView,
                        progress,
                        false,
                        true,
                        "") {
                    @Override
                    public void onError() {
                        doLoadLink(dat);
                    }
                };
        gif.execute(dat);
    }

    public void displayImage(final String urlB) {
        LogUtil.v("Displaying " + urlB);
        final String url = StringEscapeUtils.unescapeHtml4(urlB);

        if (!imageShown) {
            actuallyLoaded = url;
            final SubsamplingScaleImageView i = findViewById(R.id.submission_image);

            i.setMinimumDpi(70);
            i.setMinimumTileDpi(240);
            progress.setIndeterminate(false);
            progress.setProgress(0);

            final Handler handler = new Handler();
            final Runnable progressBarDelayRunner =
                    new Runnable() {
                        @Override public void run() {
                            progress.setVisibility(View.VISIBLE);
                        }
                    };
            handler.postDelayed(progressBarDelayRunner, 500);

            ImageView fakeImage = new ImageView(getContext());
            fakeImage.setLayoutParams(new LinearLayout.LayoutParams(i.getWidth(), i.getHeight()));
            fakeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

            File f =
                    ((Reddit) getContext().getApplicationContext())
                            .getImageLoader()
                            .getDiskCache()
                            .get(url);
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
                    i.setZoomEnabled(false);
                } catch (Exception e) {
                    imageShown = false;
                    // todo  i.setImage(ImageSource.bitmap(loadedImage));
                }
                (progress).setVisibility(View.GONE);
                handler.removeCallbacks(progressBarDelayRunner);

            } else {
                ((Reddit) getContext().getApplicationContext())
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
                                    public void onLoadingStarted(@Nullable String imageUri, @Nullable View view) {
                                        imageShown = true;
                                    }

                                    @Override
                                    public void onLoadingFailed(
                                            String imageUri, @Nullable View view, FailReason failReason) {
                                        Log.v(LogUtil.getTag(), "PeekMediaView: LOADING FAILED");
                                        imageShown = false;
                                    }

                                    @Override
                                    public void onLoadingComplete(
                                            @Nullable String imageUri, @Nullable View view, @Nullable Bitmap loadedImage) {
                                        imageShown = true;

                                        File f =
                                                ((Reddit) getContext().getApplicationContext())
                                                        .getImageLoader()
                                                        .getDiskCache()
                                                        .get(url);
                                        if (f != null && f.exists()) {
                                            i.loader.setImage(ImageSource.uri(f.getAbsolutePath()));
                                        } else if (loadedImage != null) {
                                            // A completed load with no bitmap is how the loader
                                            // reports an unusable uri, and ImageSource.bitmap
                                            // throws on a null rather than ignoring it.
                                            i.loader.setImage(ImageSource.bitmap(loadedImage));
                                        }
                                        (progress).setVisibility(View.GONE);
                                        handler.removeCallbacks(progressBarDelayRunner);
                                    }

                                    @Override
                                    public void onLoadingCancelled(String imageUri, @Nullable View view) {
                                        Log.v(LogUtil.getTag(), "PeekMediaView: LOADING CANCELLED");
                                    }
                                },
                                new ImageLoadingProgressListener() {
                                    @Override
                                    public void onProgressUpdate(
                                            String imageUri, View view, int current, int total) {
                                        progress.setProgress(Math.round(100.0f * current / total));
                                    }
                                });
            }
        }
    }

    private void init() {
        inflate(getContext(), R.layout.peek_media_view, this);
        this.videoView = findViewById(R.id.gif);
        this.website = findViewById(R.id.website);
        this.progress = findViewById(R.id.progress);
    }

    public void setUrlWithSubmission(String url, Submission submission) {
        contentType = ContentType.getContentType(url);

        // For i.redd.it GIFs, get the MP4 URL from preview data
        if (contentType == ContentType.Type.GIF && url.contains("i.redd.it")) {
            JsonNode dataNode = submission.getDataNode();
            if (dataNode.has("preview") &&
                dataNode.path("preview").has("images") &&
                dataNode.path("preview").path("images").size() > 0) {

                JsonNode variants = dataNode.path("preview")
                    .path("images")
                    .path(0)
                    .path("variants");

                if (variants.has("mp4")) {
                    String mp4Url = variants.path("mp4")
                        .path("source")
                        .path("url")
                        .asText()
                        .replace("&amp;", "&");

                    // An mp4 variant with no source url leaves this empty, which would replace the
                    // working i.redd.it url with nothing.
                    if (!mp4Url.isEmpty()) {
                        url = mp4Url;
                        contentType = ContentType.Type.GIF;
                    }
                }
            }
        }

        setUrlOrSubmission(url, submission);
    }

    private void doLoadRedditGallery(Submission submission) {
        try {
            JsonNode dataNode = submission.getDataNode();

            // Handle crosspost if needed
            if (dataNode.has("crosspost_parent_list") && dataNode.path("crosspost_parent_list").size() > 0) {
                dataNode = dataNode.path("crosspost_parent_list").path(0);
            }

            if (dataNode.has("gallery_data") && dataNode.has("media_metadata")) {
                JsonNode galleryData = dataNode.path("gallery_data");
                JsonNode mediaMetadata = dataNode.path("media_metadata");

                if (galleryData.has("items") && !galleryData.path("items").isNull()
                        && galleryData.path("items").size() > 0) {

                    JsonNode firstItem = galleryData.path("items").path(0);
                    if (firstItem != null && firstItem.has("media_id")) {
                        String mediaId = firstItem.path("media_id").asText();

                        if (mediaMetadata.has(mediaId)) {
                            JsonNode mediaInfo = mediaMetadata.path(mediaId);
                            if (mediaInfo.has("s")) {
                                String url = mediaInfo.path("s").path("u").asText();
                                // An "s" node with no "u" leaves this empty, and the image loader
                                // reads an empty uri as a completed load with a null bitmap. Fall
                                // through to the hide-the-view fallback instead.
                                if (!url.isEmpty()) {
                                    url = url.replace("&amp;", "&");

                                    // Display the first image from the gallery
                                    displayImage(url);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.e("Error loading Reddit gallery preview: " + e.getMessage());
        }
        // Fallback if gallery loading fails
        setVisibility(View.GONE);
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
