package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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

import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.MaxHeightImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Renders inline comment images as pre-sized block ImageViews loaded through the app's shared image
 * cache (cf. Continuum/Infinity), instead of inline text spans. Two things make the image "just
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

    /** Matches the previous inline-image bounds so images keep the size they had before. */
    private static final int MAX_WIDTH_PX = 500;
    private static final int MAX_HEIGHT_PX = 300;

    private static final ImageSize DECODE_SIZE = new ImageSize(720, 720);

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
            final MaxHeightImageView imageView, final String url, final String subreddit) {
        final Context context = imageView.getContext();
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setOnClickListener(v -> openImage(context, url, subreddit));

        ImageLoader loader = ((Reddit) context.getApplicationContext()).getImageLoader();

        Bitmap cached = syncBitmap(loader, url);
        android.util.Log.d("InlineImg", "display " + (cached != null ? "SYNC-HIT " : "MISS->async ") + url);
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
                    }
                });
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
                return loader.loadImageSync(url, DECODE_SIZE, options());
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
        android.util.Log.d(
                "InlineImg",
                "preloadBlocking START urls=" + (urls == null ? "null" : urls.size()));
        if (urls == null || urls.isEmpty() || SettingValues.shouldSkipImages(context)) {
            return;
        }
        final ImageLoader loader = ((Reddit) context.getApplicationContext()).getImageLoader();
        final DisplayImageOptions opts = options();
        int threads = Math.min(6, Math.max(1, urls.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (final String url : new ArrayList<>(urls)) {
            pool.execute(
                    () -> {
                        try {
                            recordRatio(url, loader.loadImageSync(url, DECODE_SIZE, opts));
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
        android.util.Log.d("InlineImg", "preloadBlocking DONE");
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

    /** Display size for a bitmap, preserving aspect ratio and bounded like the old inline images. */
    private static int[] boundedSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return new int[] {MAX_WIDTH_PX, MAX_HEIGHT_PX};
        }
        double scale =
                Math.min(
                        1.0,
                        Math.min(
                                (double) MAX_WIDTH_PX / width, (double) MAX_HEIGHT_PX / height));
        return new int[] {
            Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale))
        };
    }

    private static int[] boundedFromRatio(double ratio) {
        if (ratio <= 0) {
            return new int[] {MAX_WIDTH_PX, (int) (MAX_WIDTH_PX * 0.6)};
        }
        int width = MAX_WIDTH_PX;
        int height = (int) (MAX_WIDTH_PX * ratio);
        if (height > MAX_HEIGHT_PX) {
            height = MAX_HEIGHT_PX;
            width = (int) (MAX_HEIGHT_PX / ratio);
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
