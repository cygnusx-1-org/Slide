package me.edgan.redditslide.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.View;
import androidx.annotation.Nullable;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;
import com.nostra13.universalimageloader.utils.MemoryCacheUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import org.jspecify.annotations.NullMarked;

/**
 * Loads the emoji images of a richtext flair through the app's shared image cache, following the
 * same warm-then-read-synchronously shape as {@link CommentImageUtil}: {@link #preloadBlocking}
 * runs on the background thread that builds a feed or comment list, and {@link #cachedBitmap} pulls
 * the result out of memory at bind time without touching disk or network.
 *
 * <p>The shared loader is used rather than {@link me.edgan.redditslide.ImageFlairs}' private one:
 * that one is keyed by {@code "subreddit:cssclass"} rather than by URL, keeps no memory cache, and
 * carries a multi-gigabyte disk cache — none of which suits emoji fetched by URL and drawn every
 * frame.
 */
@NullMarked
public final class FlairEmojiUtil {

    private FlairEmojiUtil() {}

    /**
     * Emoji are drawn at flair-text height (tens of pixels). 128 leaves headroom for a large
     * display density while keeping each bitmap tiny, and the same value is used to warm the cache
     * and to read it back so both agree on the decode.
     */
    private static final ImageSize DECODE_SIZE = new ImageSize(128, 128);

    /**
     * How long a flair preload may hold the list it runs ahead of.
     *
     * <p>Deliberately far below {@link CommentImageUtil#PRELOAD_WAIT_SECONDS}: this runs as a third
     * blocking phase ahead of the comment list, after the image and video preloads, and emoji are
     * single-digit kilobytes rather than photographs. A stalled emoji CDN must not add seconds to
     * opening a thread — anything slower than this still lands in the cache for the next bind, and
     * {@link me.edgan.redditslide.Views.RichFlairSpan#refill} fills in whatever misses.
     */
    private static final int PRELOAD_WAIT_SECONDS = 1;

    /** URLs with an async load already in flight, so a re-bind cannot stack duplicate requests. */
    private static final Set<String> IN_FLIGHT = new HashSet<>();

    /**
     * url -> when it last failed, as a cooldown rather than a blacklist.
     *
     * <p>Without it a dead emoji URL is re-requested on every single bind: {@link #loadAsync} runs
     * from the bind path and clears {@link #IN_FLIGHT} the moment the failure comes back, so
     * scrolling a list re-issues it per row indefinitely. A permanent blacklist would be worse
     * though — a few seconds offline would poison every emoji the user happened to scroll past for
     * the rest of the process — so a failure only suppresses retries for {@link #RETRY_AFTER_MS}.
     */
    private static final Map<String, Long> FAILED_AT = new HashMap<>();

    /** How long a failed emoji is left alone before a bind may try it again. */
    private static final long RETRY_AFTER_MS = 60_000L;

    @Nullable private static DisplayImageOptions options;

    private static DisplayImageOptions options() {
        if (options == null) {
            options =
                    new DisplayImageOptions.Builder()
                            .cacheOnDisk(true)
                            .cacheInMemory(true)
                            .imageScaleType(ImageScaleType.IN_SAMPLE_POWER_OF_2)
                            // Always ARGB_8888: emoji are transparent PNGs, and RGB_565 (which the
                            // shared comment-image options fall back to) would fill the
                            // transparency with black inside the flair pill.
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .resetViewBeforeLoading(false)
                            .build();
        }
        return options;
    }

    /**
     * The bitmap for {@code url} if it is already in the in-memory cache, else null. Safe on the
     * main thread: this never reads disk and never hits the network.
     */
    public static @Nullable Bitmap cachedBitmap(Context context, String url) {
        final ImageLoader loader = loader(context);

        if (loader == null) {
            return null;
        }

        // Exact key first. The prefix search below has to copy the whole memory-cache key set and
        // walk it, and this runs per emoji per bind on the screens that rebuild their titles every
        // time (profile, saved, search) — a linear scan of every cached image, on the main thread,
        // for each row scrolled past. Everything here is warmed with DECODE_SIZE, so the key UIL
        // filed it under is known.
        final Bitmap exact =
                loader.getMemoryCache().get(MemoryCacheUtils.generateKey(url, DECODE_SIZE));

        if (exact != null && !exact.isRecycled()) {
            return exact;
        }

        // Fallback for anything filed under a different target size (a decode that ran before this
        // constant changed, say), so a miss here is never a permanently blank flair.
        final List<Bitmap> cached =
                MemoryCacheUtils.findCachedBitmapsForImageUri(url, loader.getMemoryCache());

        for (Bitmap bitmap : cached) {
            if (bitmap != null && !bitmap.isRecycled()) {
                return bitmap;
            }
        }

        return null;
    }

    /**
     * Downloads and decodes every emoji into the shared cache, blocking until done. Call from the
     * background thread that is already building the list, BEFORE it is shown, so the bitmaps are in
     * memory by the time {@link #cachedBitmap} asks for them.
     */
    public static void preloadBlocking(Context context, Collection<String> urls) {
        if (urls.isEmpty() || SettingValues.shouldSkipImages(context)) {
            return;
        }

        final ImageLoader loader = loader(context);

        if (loader == null) {
            return;
        }

        final DisplayImageOptions opts = options();
        final List<String> pending = new ArrayList<>(urls);
        final int threads = Math.min(6, Math.max(1, pending.size()));
        final ExecutorService pool = Executors.newFixedThreadPool(threads);

        for (final String url : pending) {
            pool.execute(
                    () -> {
                        try {
                            loader.loadImageSync(url, DECODE_SIZE, opts);
                        } catch (Exception ignored) {
                            // One emoji failing must not kill the pool thread; the flair falls back
                            // to drawing that segment's :alias: instead.
                        }
                    });
        }

        pool.shutdown();

        try {
            // The list is blocked on this, so cap the wait low enough that a stalled download
            // cannot hold the screen. Anything slower still lands in the cache for the next bind.
            pool.awaitTermination(PRELOAD_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            pool.shutdownNow();
            // Restore the flag so the task owning this thread still sees the cancellation.
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Fetches {@code url} in the background and runs {@code onLoaded} on the main thread once it is
     * cached. Used by the screens that never run a preload (profile, saved, search), where the flair
     * first renders with its {@code :alias:} fallback and is redrawn when the image arrives.
     *
     * <p>Does nothing if a load for the same URL is already in flight.
     */
    public static void loadAsync(Context context, String url, Runnable onLoaded) {
        final ImageLoader loader = loader(context);

        if (loader == null || SettingValues.shouldSkipImages(context)) {
            return;
        }

        synchronized (IN_FLIGHT) {
            final Long failedAt = FAILED_AT.get(url);

            if (failedAt != null) {
                if (SystemClock.elapsedRealtime() - failedAt < RETRY_AFTER_MS) {
                    return;
                }
                FAILED_AT.remove(url);
            }

            if (!IN_FLIGHT.add(url)) {
                return;
            }
        }

        loader.loadImage(
                url,
                DECODE_SIZE,
                options(),
                new SimpleImageLoadingListener() {
                    @Override
                    public void onLoadingComplete(
                            @Nullable String imageUri,
                            @Nullable View view,
                            @Nullable Bitmap loadedImage) {
                        done();
                        onLoaded.run();
                    }

                    @Override
                    public void onLoadingFailed(
                            @Nullable String imageUri,
                            @Nullable View view,
                            @Nullable FailReason failReason) {
                        synchronized (IN_FLIGHT) {
                            FAILED_AT.put(url, SystemClock.elapsedRealtime());
                        }
                        done();
                    }

                    @Override
                    public void onLoadingCancelled(
                            @Nullable String imageUri, @Nullable View view) {
                        done();
                    }

                    private void done() {
                        synchronized (IN_FLIGHT) {
                            IN_FLIGHT.remove(url);
                        }
                    }
                });
    }

    private static @Nullable ImageLoader loader(Context context) {
        final Context application = context.getApplicationContext();

        if (!(application instanceof Reddit)) {
            return null;
        }

        try {
            return ((Reddit) application).getImageLoader();
        } catch (Exception ignored) {
            // The loader is built lazily and can still be coming up on a cold start. A flair that
            // cannot reach it just draws its :alias: text, which is far better than taking the feed
            // down from inside a title render.
            return null;
        }
    }
}
