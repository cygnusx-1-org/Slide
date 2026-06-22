package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Movie;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;
import com.nostra13.universalimageloader.utils.MemoryCacheUtils;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.MaxHeightImageView;
import org.apache.commons.text.StringEscapeUtils;

/**
 * Renders inline comment images as pre-sized block ImageViews loaded through the app's shared image
 * cache (like Continuum/Infinity), instead of inline text spans. Two things make the image "just
 * there with the comment" rather than popping in:
 *
 * <ul>
 *   <li>{@link #preloadBlocking} downloads every image of a comment thread into the shared cache on
 *       the background thread that builds the comment list, BEFORE the comments are shown, and
 *   <li>{@link #display} pulls the bitmap out of the cache <b>synchronously</b> at bind time and
 *       sets it directly — no asynchronous load, so the image is painted in the same frame as the
 *       rest of the comment.
 * </ul>
 */
public final class CommentImageUtil {

    private CommentImageUtil() {}

    /**
     * "Small" bounds match the previous inline-image bounds. "Medium" (the default) is 1.5x and
     * "Large" is 2x, selected via {@link SettingValues#commentImageSize}.
     */
    private static final int BASE_WIDTH_PX = 500;
    private static final int BASE_HEIGHT_PX = 300;

    /**
     * Display scale for comment images: 1.0 (small), 1.5 (medium, default) or 2.0 (large). Public so
     * the legacy inline-{@link android.text.style.ImageSpan} path ({@code applyImageSpans}) scales by
     * the same factor as the block path here.
     */
    public static double sizeMultiplier() {
        switch (SettingValues.commentImageSize) {
            case SettingValues.COMMENT_IMAGE_SIZE_LARGE:
                return 2.0;
            case SettingValues.COMMENT_IMAGE_SIZE_MEDIUM:
                return 1.5;
            case SettingValues.COMMENT_IMAGE_SIZE_SMALL:
            default:
                return 1.0;
        }
    }

    private static int maxWidthPx() {
        return (int) (BASE_WIDTH_PX * sizeMultiplier());
    }

    private static int maxHeightPx() {
        return (int) (BASE_HEIGHT_PX * sizeMultiplier());
    }

    /** Decode box big enough to keep the largest displayed image sharp (at least 720px). */
    private static ImageSize decodeSize() {
        int box = Math.max(720, maxWidthPx());
        return new ImageSize(box, box);
    }

    /** url -> aspect ratio (height/width), so a not-yet-loaded image can reserve its slot. */
    private static final Map<String, Double> RATIO_CACHE = new ConcurrentHashMap<>();

    private static DisplayImageOptions options;

    private static DisplayImageOptions options() {
        if (options == null) {
            options =
                    new DisplayImageOptions.Builder()
                            .cacheOnDisk(true)
                            .cacheInMemory(true)
                            .imageScaleType(ImageScaleType.IN_SAMPLE_POWER_OF_2)
                            .bitmapConfig(
                                    SettingValues.highColorspaceImages
                                            ? Bitmap.Config.ARGB_8888
                                            : Bitmap.Config.RGB_565)
                            .resetViewBeforeLoading(false)
                            .build();
        }
        return options;
    }

    /**
     * Renders {@code url} into the (already added) image view. If the bitmap is already cached
     * (which it is once {@link #preloadBlocking} has run) it is set synchronously so the image
     * appears in the same frame as the comment. Otherwise the slot is reserved at the right size
     * and the image is loaded asynchronously. Tapping opens the image in the media viewer.
     */
    public static void display(
            final MaxHeightImageView imageView, final String rawUrl, final String subreddit) {
        // Normalize HTML entities so the cache key matches what the preloader warmed (and what the
        // shared loader stores) — escaped vs unescaped here was the cause of images popping in.
        final String url = StringEscapeUtils.unescapeHtml4(rawUrl);
        final Context context = imageView.getContext();
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setOnClickListener(v -> openImage(context, url, subreddit));

        ImageLoader loader = ((Reddit) context.getApplicationContext()).getImageLoader();

        // Animated comment gifs (giphy / i.redd.it .gif): when the user has enabled comment
        // animation, render the gif as a looping Movie instead of a static first frame.
        if (isGifUrl(url)
                && SettingValues.commentEmoteAnimation
                && !SettingValues.shouldSkipImages(context)
                && displayAnimatedGif(imageView, loader, url)) {
            return;
        }

        Bitmap cached = syncBitmap(loader, url);
        if (cached != null) {
            recordRatio(url, cached);
            int[] size = boundedSize(cached.getWidth(), cached.getHeight());
            applySize(imageView, size[0], size[1]);
            imageView.setImageBitmap(cached);
            return;
        }

        // Not cached yet (e.g. data-saving off but preload skipped): reserve the slot from the known
        // ratio so there is no reflow, then load asynchronously.
        int[] reserved = boundedFromRatio(knownRatio(url));
        applySize(imageView, reserved[0], reserved[1]);
        // Whether the slot should animate once the bytes arrive (same gate as the synchronous path).
        final boolean animate =
                isGifUrl(url)
                        && SettingValues.commentEmoteAnimation
                        && !SettingValues.shouldSkipImages(context);
        loader.displayImage(
                url,
                new ImageViewAware(imageView),
                options(),
                new SimpleImageLoadingListener() {
                    @Override
                    public void onLoadingComplete(String uri, View view, Bitmap loadedImage) {
                        if (loadedImage != null && loadedImage.getWidth() > 0) {
                            recordRatio(uri, loadedImage);
                            int[] s = boundedSize(loadedImage.getWidth(), loadedImage.getHeight());
                            applySize(imageView, s[0], s[1]);
                        }
                        // The async load just wrote the gif bytes to disk, so upgrade the static
                        // first frame to the looping animation now instead of waiting for a rebind
                        // (scroll) to pick it up. ImageViewAware already guards against recycling.
                        if (animate) {
                            displayAnimatedGif(imageView, loader, url);
                        }
                    }
                });
    }

    /** Whether {@code url} points at a gif we can animate (giphy media / i.redd.it / preview .gif). */
    private static boolean isGifUrl(String url) {
        return url != null && url.contains(".gif");
    }

    /**
     * Renders {@code url} as a looping animated gif into {@code imageView} using the raw gif bytes
     * already in the shared disk cache (warmed by {@link #preloadBlocking}). The {@link GifDrawable}
     * self-invalidates to drive the animation; the ImageView's FIT_CENTER matrix scales it to the
     * pre-sized slot. Returns false (so the caller falls back to a static frame) if the gif is not on
     * disk yet, is a single still frame, or cannot be decoded by {@link Movie}.
     */
    private static boolean displayAnimatedGif(
            MaxHeightImageView imageView, ImageLoader loader, String url) {
        try {
            File diskFile = loader.getDiskCache().get(url);
            if (diskFile == null || !diskFile.exists()) {
                return false;
            }
            Movie movie;
            try (InputStream in = new BufferedInputStream(new FileInputStream(diskFile))) {
                movie = Movie.decodeStream(in);
            }
            // duration() == 0 means a single-frame gif: nothing to animate, use the static path.
            if (movie == null || movie.width() <= 0 || movie.height() <= 0 || movie.duration() <= 0) {
                return false;
            }
            RATIO_CACHE.put(url, (double) movie.height() / movie.width());
            int[] size = boundedSize(movie.width(), movie.height());
            applySize(imageView, size[0], size[1]);
            // Movie.draw is unreliable on a hardware-accelerated canvas; force software for this view.
            imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            GifDrawable drawable = new GifDrawable(movie, null);
            imageView.setImageDrawable(drawable);
            drawable.start();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Returns the cached bitmap (memory, or synchronously decoded from disk) or null. No network. */
    private static Bitmap syncBitmap(ImageLoader loader, String url) {
        List<Bitmap> mem = MemoryCacheUtils.findCachedBitmapsForImageUri(url, loader.getMemoryCache());
        for (Bitmap b : mem) {
            if (b != null && !b.isRecycled()) {
                return b;
            }
        }
        try {
            File diskFile = loader.getDiskCache().get(url);
            if (diskFile != null && diskFile.exists()) {
                return loader.loadImageSync(url, decodeSize(), options());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Downloads and decodes every image into the shared memory+disk cache, blocking until done.
     * Call from a background thread (e.g. the comment-loading task) BEFORE the comments are shown,
     * so that by the time rows bind the bitmaps are cached and render in place.
     */
    public static void preloadBlocking(Context context, Collection<String> urls) {
        if (urls == null || urls.isEmpty() || SettingValues.shouldSkipImages(context)) {
            return;
        }
        final ImageLoader loader = ((Reddit) context.getApplicationContext()).getImageLoader();
        final DisplayImageOptions opts = options();
        int threads = Math.min(6, Math.max(1, urls.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (final String rawUrl : new ArrayList<>(urls)) {
            final String url = StringEscapeUtils.unescapeHtml4(rawUrl);
            pool.execute(
                    () -> {
                        try {
                            recordRatio(url, loader.loadImageSync(url, decodeSize(), opts));
                        } catch (Exception ignored) {
                        }
                    });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(45, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            pool.shutdownNow();
        }
    }

    private static double knownRatio(String url) {
        Double cached = RATIO_CACHE.get(url);
        return (cached != null && cached > 0) ? cached : 0;
    }

    private static void recordRatio(String url, Bitmap bitmap) {
        if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            RATIO_CACHE.put(url, (double) bitmap.getHeight() / bitmap.getWidth());
        }
    }

    /**
     * Display size for a bitmap, preserving aspect ratio by scaling it to a constant on-screen
     * <b>area</b> (the reference box {@code maxWidthPx() * maxHeightPx()}) rather than fitting it
     * inside that box. This is the Continuum behavior: a wide image and a tall image occupy roughly
     * the same amount of screen, just shaped differently — no tiny slivers, no full-screen monsters.
     * Sources smaller than the target area are <b>upscaled</b>, matching the pre-block (release)
     * inline-image behavior so that low-resolution sources (e.g. small giphy gifs) are not left tiny.
     */
    private static int[] boundedSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return new int[] {maxWidthPx(), maxHeightPx()};
        }
        double targetArea = (double) maxWidthPx() * maxHeightPx();
        double scale = Math.sqrt(targetArea / ((double) width * height));
        int w = (int) (width * scale);
        int h = (int) (height * scale);
        // A very wide image can still exceed the screen width and clip; cap it (keeping ratio).
        int screen = Resources.getSystem().getDisplayMetrics().widthPixels;
        if (screen > 0 && w > screen) {
            h = (int) ((long) h * screen / w);
            w = screen;
        }
        return new int[] {Math.max(1, w), Math.max(1, h)};
    }

    /**
     * Reserve a slot for a not-yet-loaded image from its known aspect {@code ratio} (height/width).
     * Sized to the same constant target area as {@link #boundedSize} so the reserved slot matches the
     * final size and there is no reflow on load.
     */
    private static int[] boundedFromRatio(double ratio) {
        if (ratio <= 0) {
            return new int[] {maxWidthPx(), maxHeightPx()};
        }
        double targetArea = (double) maxWidthPx() * maxHeightPx();
        // area = width * height = width * (width * ratio) => width = sqrt(area / ratio)
        int width = (int) Math.sqrt(targetArea / ratio);
        int height = (int) (width * ratio);
        // Apply the same screen-width cap as boundedSize (keeping ratio).
        int screen = Resources.getSystem().getDisplayMetrics().widthPixels;
        if (screen > 0 && width > screen) {
            height = (int) ((long) height * screen / width);
            width = screen;
        }
        return new int[] {Math.max(1, width), Math.max(1, height)};
    }

    private static void applySize(ImageView imageView, int width, int height) {
        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        if (lp == null) {
            imageView.setLayoutParams(new ViewGroup.LayoutParams(width, height));
        } else if (lp.width != width || lp.height != height) {
            lp.width = width;
            lp.height = height;
            imageView.setLayoutParams(lp);
        }
    }

    private static void openImage(Context context, String url, String subreddit) {
        try {
            Intent intent = new Intent(context, MediaView.class);
            intent.putExtra(MediaView.EXTRA_URL, url);
            if (subreddit != null) {
                intent.putExtra(MediaView.SUBREDDIT, subreddit);
            }
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(view);
            } catch (Exception ignored) {
            }
        }
    }
}
