package me.edgan.redditslide.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import com.nostra13.universalimageloader.core.ImageLoader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jspecify.annotations.NullMarked;

/**
 * The still frame an inline comment-video card shows behind its play arrow.
 *
 * <p>Reddit publishes no poster image for these assets — the comment payload carries the two path
 * ids and nothing else, and every {@code poster}/{@code thumbnail} path under the asset directory
 * 403s — so the frame comes out of the video itself. The DASH manifest lists self-contained mp4
 * renditions, and {@link MediaMetadataRetriever} reads the first keyframe of one over ranged
 * requests rather than downloading it whole.
 *
 * <p>A card that has no frame yet, or whose video yields none, simply keeps the flat fill it was
 * built with — the preview is decoration over a card that already works.
 */
@NullMarked
public final class CommentVideoPreview {

    private CommentVideoPreview() {}

    /**
     * The frame is only ever drawn into a fixed-height card, so past this resolution nothing more
     * is visible. Taking the smallest rendition at or above it keeps the read small without leaving
     * the preview soft on a 3x display.
     */
    private static final int TARGET_HEIGHT = 480;

    /**
     * A video representation of the DASH manifest: its height, then its file name relative to the
     * manifest. Audio representations declare no {@code height}, so this matches only the video
     * ones, and the leading {@code \s} keeps it off the AdaptationSet's own {@code maxHeight}.
     */
    private static final Pattern REPRESENTATION =
            Pattern.compile("\\sheight=\"(\\d+)\"[^>]*>\\s*<BaseURL>([^<]+)</BaseURL>");

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<>(
                    (int) Math.min(8L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 16)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    /**
     * Namespace for the disk-cache key. The frame is something we extracted, not something we
     * downloaded from that address, so it must not collide with a real image url.
     */
    private static final String DISK_KEY_PREFIX = "commentvideoframe:";

    private static final int JPEG_QUALITY = 85;

    /** Urls with no readable frame, so one failure is not retried on every rebind. */
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    /**
     * Views waiting on a frame that is being read right now, keyed by url, so two cards of the same
     * video do not both fetch it <b>and</b> both still get the result. Held weakly: a read has no
     * timeout it can rely on ({@link MediaMetadataRetriever} has none), so a stalled one must not
     * pin a recycled view for as long as it hangs.
     */
    private static final Map<String, List<WeakReference<ImageView>>> PENDING = new HashMap<>();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    /**
     * Draws a still frame of {@code playerUrl} into {@code target}: straight away when it is in
     * memory, otherwise once the background read finishes and only if the view is still showing the
     * same video. The background read tries the disk cache before going near the video, so a frame
     * read in an earlier run is not extracted twice.
     */
    public static void load(final ImageView target, final String playerUrl) {
        // The same video pasted with and without "www." is the same asset, and formatUrl only
        // understands the bare host.
        final String url = CommentVideoUtil.normalizeUrl(playerUrl);
        target.setTag(url);

        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            target.setVisibility(View.VISIBLE);
            return;
        }
        target.setImageDrawable(null);
        target.setVisibility(View.INVISIBLE);

        final Context context = target.getContext();
        if (FAILED.contains(url) || SettingValues.shouldSkipImages(context)) {
            return;
        }
        if (!claimRead(url, target)) {
            // Another card is already reading it; this one is now registered as a waiter and the
            // owning read will hand it the same frame.
            return;
        }
        // Resolved here rather than on the pool thread: the loader is reached through the view.
        final ImageLoader loader = ((Reddit) context.getApplicationContext()).getImageLoader();
        POOL.execute(() -> readAndDeliver(loader, url));
    }

    /**
     * Performs the one read of {@code url} this caller claimed and hands the frame to everything
     * that registered for it meanwhile. The single entry point for a read, so the preload and a
     * card binding mid-preload share one fetch instead of racing two.
     */
    private static void readAndDeliver(ImageLoader loader, String url) {
        Bitmap frame = null;
        List<WeakReference<ImageView>> waiting;
        try {
            frame = resolve(loader, url);
        } finally {
            waiting = releaseRead(url);
        }
        if (frame == null) {
            return;
        }
        for (WeakReference<ImageView> ref : waiting) {
            show(ref.get(), url, frame);
        }
    }

    /**
     * Registers interest in {@code url}'s frame. Returns true for the caller that should perform
     * the read, false for one that only has to wait for it. {@code target} is null for the
     * preload, which wants the frame cached but has no view to paint.
     */
    private static boolean claimRead(String url, @Nullable ImageView target) {
        synchronized (PENDING) {
            List<WeakReference<ImageView>> waiting = PENDING.get(url);
            if (waiting != null) {
                if (target != null) {
                    waiting.add(new WeakReference<>(target));
                }
                return false;
            }
            List<WeakReference<ImageView>> started = new ArrayList<>();
            if (target != null) {
                started.add(new WeakReference<>(target));
            }
            PENDING.put(url, started);
            return true;
        }
    }

    /** Ends the read for {@code url} and returns everything that was waiting on it. */
    private static List<WeakReference<ImageView>> releaseRead(String url) {
        synchronized (PENDING) {
            List<WeakReference<ImageView>> waiting = PENDING.remove(url);
            return waiting == null ? Collections.emptyList() : waiting;
        }
    }

    /** Paints {@code frame} onto {@code view}, if it is still alive and still showing {@code url}. */
    private static void show(@Nullable ImageView view, String url, Bitmap frame) {
        if (view == null) {
            return;
        }
        view.post(
                () -> {
                    // CommentOverflow rebuilds its views on every bind, so this view may be
                    // showing a different comment's video by now.
                    if (url.equals(view.getTag())) {
                        view.setImageBitmap(frame);
                        view.setVisibility(View.VISIBLE);
                    }
                });
    }

    /**
     * Reads and caches the frame for every url, blocking until they are done. Call from the
     * background thread that builds the comment list, BEFORE the comments are shown, so every card
     * binds with its frame already in memory instead of fading one in afterwards — the same shape
     * as {@link CommentImageUtil#preloadBlocking}.
     */
    public static void preloadBlocking(Context context, Collection<String> playerUrls) {
        if (playerUrls == null || playerUrls.isEmpty() || SettingValues.shouldSkipImages(context)) {
            return;
        }
        final ImageLoader loader = ((Reddit) context.getApplicationContext()).getImageLoader();
        int threads = Math.min(3, Math.max(1, playerUrls.size()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (final String rawUrl : new ArrayList<>(playerUrls)) {
            final String url = CommentVideoUtil.normalizeUrl(rawUrl);
            if (CACHE.get(url) != null || FAILED.contains(url) || !claimRead(url, null)) {
                continue;
            }
            pool.execute(() -> readAndDeliver(loader, url));
        }
        pool.shutdown();
        try {
            // The comment list is blocked on this, so the wait is short enough to stay under
            // "slow" even when a read stalls: MediaMetadataRetriever has no timeout of its own and
            // cannot be interrupted, so without this bound one bad asset holds up the whole screen.
            // Frames that arrive after it still reach the cache and show on the next bind.
            pool.awaitTermination(CommentImageUtil.PRELOAD_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            // Deliberately NOT shutdownNow(): every submitted task already holds a claim in
            // PENDING, and dropping a queued one strands that claim forever — every later bind of
            // that video would register as a waiter nothing ever serves. shutdown() above lets the
            // queue drain and each task release its own claim.
            // Restore the flag so the AsyncTask owning this thread still sees the cancellation.
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Resolves the frame for {@code url} — disk first, then the video — and caches it. A url is only
     * recorded in {@link #FAILED} when the read <b>completed</b> and genuinely had no frame; a read
     * that threw (no connectivity, a timeout, a decoder error) is transient, so it is left unmarked
     * and a later bind tries again instead of the card staying blank for the rest of the process.
     * Never throws: one bad asset must not kill the pool thread.
     */
    @Nullable
    private static Bitmap resolve(ImageLoader loader, String url) {
        try {
            Bitmap frame = fromDisk(loader, url);
            if (frame == null) {
                frame = frameFor(url);
                if (frame != null) {
                    saveToDisk(loader, url, frame);
                }
            }
            if (frame == null) {
                FAILED.add(url);
                return null;
            }
            CACHE.put(url, frame);
            return frame;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * The frame this or an earlier run already extracted, or {@code null} when it was never cached
     * or has since been evicted. Entries live in the app's shared image disk cache, so they age out
     * under the same budget as every other cached image.
     */
    @Nullable
    private static Bitmap fromDisk(ImageLoader loader, String url) {
        try {
            File file = loader.getDiskCache().get(DISK_KEY_PREFIX + url);
            if (file == null || !file.exists()) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig =
                    SettingValues.highColorspaceImages
                            ? Bitmap.Config.ARGB_8888
                            : Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception ignored) {
            // A cache read that fails is a miss; the frame is read from the video instead.
            return null;
        }
    }

    /**
     * Stores {@code frame} so it survives a restart. Written as JPEG rather than through
     * {@code DiskCache.save(String, Bitmap)}, which compresses PNG at quality 100 — several times
     * the bytes for what is a photographic video frame.
     */
    private static void saveToDisk(ImageLoader loader, String url, Bitmap frame) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!frame.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, bytes)) {
                return;
            }
            loader.getDiskCache()
                    .save(
                            DISK_KEY_PREFIX + url,
                            new ByteArrayInputStream(bytes.toByteArray()),
                            null);
        } catch (Exception ignored) {
            // Best effort: the preview still shows this session, just not after a restart.
        }
    }

    /** Reads the first keyframe of {@code playerUrl}'s smallest usable rendition. */
    @Nullable
    private static Bitmap frameFor(String playerUrl) throws Exception {
        // GifUtils owns the reddit.com/link -> v.redd.it DASH rewrite; going through it means the
        // preview is read from exactly the stream MediaView plays.
        final String manifestUrl = GifUtils.AsyncLoadGif.formatUrl(playerUrl);
        final int slash = manifestUrl.lastIndexOf('/');
        if (slash == -1) {
            return null;
        }
        final String base = manifestUrl.substring(0, slash + 1);

        final String manifest;
        try (Response response =
                Reddit.client.newCall(new Request.Builder().url(manifestUrl).build()).execute()) {
            if (!response.isSuccessful()) {
                if (isTransient(response.code())) {
                    // The CDN having a moment, not a missing asset. Thrown rather than returned so
                    // resolve() treats it as retryable instead of blacklisting the video.
                    throw new IOException("manifest HTTP " + response.code());
                }
                return null;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return null;
            }
            manifest = body.string();
        }

        final String rendition = smallestUsableRendition(manifest);
        if (rendition == null) {
            return null;
        }

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            // Ranged reads: only the moov atom and the first sync sample are fetched, not the
            // whole rendition.
            retriever.setDataSource(base + rendition, Collections.emptyMap());
            return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } finally {
            retriever.release();
        }
    }

    /** Whether an HTTP status is worth retrying later rather than recording as a dead asset. */
    private static boolean isTransient(int code) {
        return code == 429 || code >= 500;
    }

    /**
     * The smallest video rendition at least {@link #TARGET_HEIGHT} tall, or the tallest one below
     * that when the video never reaches it.
     */
    @Nullable
    private static String smallestUsableRendition(String manifest) {
        String best = null;
        int bestHeight = 0;
        Matcher matcher = REPRESENTATION.matcher(manifest);
        while (matcher.find()) {
            final String name = matcher.group(2);
            if (name == null || matcher.group(1) == null) {
                continue;
            }
            final int height = Integer.parseInt(matcher.group(1));
            if (best == null
                    || (bestHeight < TARGET_HEIGHT && height > bestHeight)
                    || (height >= TARGET_HEIGHT && height < bestHeight)) {
                best = name;
                bestHeight = height;
            }
        }
        return best;
    }
}
