package me.edgan.redditslide.util;

import android.content.Context;
import android.graphics.Bitmap;
import com.fasterxml.jackson.databind.JsonNode;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.MaxHeightImageView;
import net.dean.jraw.models.Submission;

/** Created by TacoTheDank on 12/11/2020. */
public class PhotoLoader {

    /** Disk-only warm (offline caching); does not populate the memory cache. */
    public static void loadPhoto(final Context c, final Submission submission) {
        loadPhoto(c, submission, false);
    }

    public static void loadPhoto(
            final Context c, final Submission submission, final boolean warmMemory) {
        // The live feed (warmMemory) skips nsfw/spoiler posts that render a static drawable; offline
        // mass caching (!warmMemory) still downloads their images so a reveal works while offline.
        final String url = resolveFeedImageUrl(c, submission, warmMemory);
        if (url != null && !PLACEHOLDER_URLS.contains(url)) {
            loadImage(c, url, warmMemory);
        }
    }

    private static final List<String> PLACEHOLDER_URLS =
            Arrays.asList("self", "default", "image", "nsfw", "spoiler", "");

    /**
     * The image URL the feed card (HeaderImageLinkView.doImageAndText) will display for this
     * submission, or null if the card shows no downloaded image (a static nsfw/spoiler drawable, a
     * self post with a hidden lead image, or no usable media). Kept in lock-step with the display
     * routing so the preload warms the exact cache entry the card later binds; a mismatch reappears
     * as first-view pop-in.
     */
    public static String resolveFeedImageUrl(
            final Context c, final Submission submission, final boolean skipDrawableOnlyPosts) {
        return resolveFeedImageUrl(
                c, submission, skipDrawableOnlyPosts, feedImageWidth(c, SettingValues.bigPicEnabled));
    }

    public static String resolveFeedImageUrl(
            final Context c,
            final Submission submission,
            final boolean skipDrawableOnlyPosts,
            final int maxW) {
        final ContentType.Type type = ContentType.getContentType(submission);
        final JsonNode dataNode = submission.getDataNode();

        // Posts the card renders as a static drawable instead of a downloaded image (mirrors
        // doImageAndText's nsfw/spoiler branches). Only skipped for the live feed; offline caching
        // still wants the underlying image so a reveal works without a connection.
        if (skipDrawableOnlyPosts) {
            if (submission.isNsfw() && SettingValues.getIsNSFWEnabled()) {
                return null;
            }
            final JsonNode spoilerNode = (dataNode != null) ? dataNode.get("spoiler") : null;
            if (spoilerNode != null && spoilerNode.asBoolean()) {
                return null;
            }
        }

        final boolean loadLq =
                (!NetworkUtil.isConnectedWifi(c) && SettingValues.lowResMobile)
                        || SettingValues.lowResAlways;

        // maxW sizes the URL to what will actually be shown — the thumbnail cell for a list feed, or
        // the full width for a card / a pre-warmed detail header. Reddit already serves
        // thumbnail-sized "resolutions", so a list thumbnail must not download the ≈1080px image.
        switch (type) {
            case REDDIT_GALLERY: {
                final GalleryPreview gallery = getGalleryPreview(dataNode);
                return gallery != null ? gallery.url : null;
            }
            case ALBUM:
            case GIF:
            case LINK:
            case REDDIT:
            case TUMBLR:
            case STREAMABLE:
            case XKCD:
            case VREDDIT_DIRECT:
            case VREDDIT_REDIRECT: {
                // handleTypes / handleVRedditType prefer a real preview (crosspost-parent aware and
                // host-normalized), falling back to the small thumbnail.
                final String preview = getPreviewUrl(dataNode, maxW);
                if (preview != null && !preview.isEmpty()) {
                    return preview;
                }
                String thumb = getValidThumbnailUrl(dataNode);
                if (thumb == null
                        && dataNode != null
                        && dataNode.has("crosspost_parent_list")
                        && dataNode.get("crosspost_parent_list").size() > 0) {
                    thumb = getValidThumbnailUrl(dataNode.get("crosspost_parent_list").get(0));
                }
                return thumb;
            }
            case IMAGE: {
                final JsonNode thumbnailNode =
                        (dataNode != null) ? dataNode.get("thumbnail") : null;
                if (thumbnailNode != null
                        && !thumbnailNode.isNull()
                        && !thumbnailNode.asText().isEmpty()) {
                    final boolean lowQ =
                            loadLq
                                    && submission.getThumbnails() != null
                                    && submission.getThumbnails().getVariations().length > 0;
                    return lowQ
                            ? getLowQualityUrl(submission)
                            : getHighQualityUrl(submission, maxW);
                }
                break;
            }
            default:
                break;
        }

        // handleThumbnailDisplay: thumbnails present but not handled above (e.g. SELF posts, or an
        // IMAGE post with an empty thumbnail field). getSubmissionUrl mirrors that path exactly.
        if (submission.getThumbnails() != null) {
            return getSubmissionUrl(submission, loadLq, maxW);
        }

        // Direct thumbnail-URL fallback (HeaderImageLinkView's thumbnailType == URL branch).
        return getValidThumbnailUrl(dataNode);
    }

    /** Mirrors HeaderImageLinkView.getSubmissionUrl so the warm matches the displayed URL. */
    private static String getSubmissionUrl(
            final Submission submission, final boolean loadLq, final int maxWidth) {
        if (loadLq && submission.getThumbnails().getVariations().length != 0) {
            return getLowQualityUrl(submission);
        }
        return getHighQualityUrl(submission, maxWidth);
    }

    /**
     * Real preview image for the submission, preferring the crosspost parent's preview and
     * normalizing the host, or null if none. Shared with HeaderImageLinkView (which delegates here)
     * so the feed card and the preloader resolve the identical URL.
     */
    public static String getPreviewUrl(final JsonNode dataNode) {
        // No width bound: the full-resolution source (card / fullscreen behavior).
        return getPreviewUrl(dataNode, Integer.MAX_VALUE);
    }

    public static String getPreviewUrl(final JsonNode dataNode, final int maxWidth) {
        if (dataNode == null) {
            return null;
        }
        String previewUrl = null;
        if (dataNode.has("crosspost_parent_list")
                && dataNode.get("crosspost_parent_list").size() > 0) {
            previewUrl =
                    extractPreviewUrl(dataNode.get("crosspost_parent_list").get(0), maxWidth);
        }
        if (previewUrl == null) {
            previewUrl = extractPreviewUrl(dataNode, maxWidth);
        }
        return JsonUtil.normalizeRedditPreviewHost(previewUrl, JsonUtil.linksToReddit(dataNode));
    }

    private static String extractPreviewUrl(final JsonNode node, final int maxWidth) {
        if (node != null
                && node.has("preview")
                && node.get("preview").has("images")
                && node.get("preview").get("images").size() > 0) {
            final JsonNode image = node.get("preview").get("images").get(0);
            // Smallest sized preview covering the display width (thumbnails). Cards pass
            // Integer.MAX_VALUE, so no resolution matches and we fall through to the full source.
            final String sized = sizedResolutionUrl(image, maxWidth);
            if (sized != null) {
                return sized;
            }
            final JsonNode sourceNode = image.get("source");
            if (sourceNode != null && sourceNode.has("url")) {
                return sourceNode.get("url").asText();
            }
        }
        return null;
    }

    /**
     * The node's thumbnail URL if it is a usable image URL, or null for Reddit's placeholder values
     * ("self", "default", "nsfw") or a missing/empty value. Shared with HeaderImageLinkView.
     */
    public static String getValidThumbnailUrl(final JsonNode node) {
        if (node != null && node.has("thumbnail") && !node.get("thumbnail").isNull()) {
            final String thumbnail = node.get("thumbnail").asText();
            if (!thumbnail.equals("self")
                    && !thumbnail.equals("default")
                    && !thumbnail.equals("nsfw")
                    && !thumbnail.isEmpty()) {
                return thumbnail;
            }
        }
        return null;
    }

    // --- Shared feed-image URL selection ---------------------------------------------------------
    // HeaderImageLinkView delegates to these so the preloader warms exactly the entry the card
    // displays. Keep the two in lock-step; divergence reintroduces first-view pop-in.

    /**
     * URL of the smallest reddit "resolutions" preview under {@code imageNode} whose width covers
     * {@code maxWidth}, or null if there are no resolutions or none is that wide. Reddit orders
     * resolutions smallest-to-largest, so the first match is the smallest that still fills the
     * display — a list thumbnail gets a few-hundred-pixel image instead of the full-size source, and
     * a card (maxWidth == Integer.MAX_VALUE) matches nothing and the callers fall back to the full
     * image. Shared by getHighQualityUrl and the preview path so both size identically.
     */
    public static String sizedResolutionUrl(final JsonNode imageNode, final int maxWidth) {
        if (imageNode == null) {
            return null;
        }
        final JsonNode resolutions = imageNode.get("resolutions");
        if (resolutions == null || resolutions.size() == 0) {
            return null;
        }
        // resolutions are ordered smallest-to-largest: the first at/above the target is the smallest
        // covering rung; the last below it is the largest under-sized rung.
        JsonNode covering = null;
        JsonNode largestBelow = null;
        for (final JsonNode r : resolutions) {
            if (r == null || !r.has("width") || !r.has("url")) {
                continue;
            }
            final int w = r.get("width").asInt();
            if (w >= maxWidth) {
                if (covering == null) {
                    covering = r;
                }
            } else {
                largestBelow = r;
            }
        }
        // Cards pass Integer.MAX_VALUE: nothing covers, so return null and let the caller use the
        // full source. For a thumbnail, take the covering rung — unless it massively overshoots the
        // cell (sparse resolutions, e.g. 216 then a jump to 1080 for a ~240px thumbnail), in which
        // case a slightly-soft smaller rung beats downloading a 1080px image for a thumbnail.
        if (covering == null) {
            return null;
        }
        if (largestBelow == null || covering.get("width").asInt() <= 2L * maxWidth) {
            return covering.get("url").asText();
        }
        return largestBelow.get("url").asText();
    }

    public static String getHighQualityUrl(Submission submission) {
        // No width bound: the largest sized preview (card / fullscreen behavior).
        return getHighQualityUrl(submission, Integer.MAX_VALUE);
    }

    /**
     * Feed image URL for {@code submission}, sized to {@code maxWidth}: the smallest reddit
     * "resolutions" preview whose width covers maxWidth, so a list thumbnail downloads a
     * few-hundred-pixel image instead of the ≈1080px card preview (reddit already serves these
     * sizes). Falls back to the largest resolution (cards), then the full source, then reddit's own
     * thumbnail field.
     */
    public static String getHighQualityUrl(Submission submission, int maxWidth) {
        if (submission.getDataNode().has("preview")) {
            final JsonNode images = submission.getDataNode().get("preview").get("images");
            final JsonNode image = (images != null && images.size() > 0) ? images.get(0) : null;
            if (image != null) {
                // Smallest sized preview that covers the display width so a thumbnail never pulls the
                // full card-sized preview; otherwise the largest sized preview (cards).
                final String sized = sizedResolutionUrl(image, maxWidth);
                if (sized != null) {
                    return sized;
                }
                final JsonNode resolutions = image.get("resolutions");
                if (resolutions != null && resolutions.size() > 0) {
                    final JsonNode largest = resolutions.get(resolutions.size() - 1);
                    if (largest != null && largest.has("url")) {
                        return largest.get("url").asText();
                    }
                }
                final JsonNode source = image.get("source");
                if (source != null && source.has("height")) {
                    return source.get("url").asText();
                }
            }
        }
        if (submission.getThumbnails() != null
                && submission.getThumbnails().getSource() != null) {
            String sourceUrl = submission.getThumbnails().getSource().getUrl();
            return CompatUtil.fromHtml(
                            sourceUrl.isEmpty() ? submission.getThumbnail() : sourceUrl)
                    .toString();
        } else {
            return submission.getThumbnail();
        }
    }

    public static String getLowQualityUrl(Submission submission) {
        if (ContentType.isImgurImage(submission.getUrl())) {
            String url = submission.getUrl();
            return url.substring(0, url.lastIndexOf("."))
                    + (SettingValues.lqLow ? "m" : (SettingValues.lqMid ? "l" : "h"))
                    + url.substring(url.lastIndexOf("."));
        }
        int length = submission.getThumbnails().getVariations().length;
        if (SettingValues.lqLow && length >= 3) {
            return getThumbnailVariationUrl(submission, 2);
        } else if (SettingValues.lqMid && length >= 4) {
            return getThumbnailVariationUrl(submission, 3);
        } else if (length >= 5) {
            return getThumbnailVariationUrl(submission, length - 1);
        } else {
            return CompatUtil.fromHtml(submission.getThumbnails().getSource().getUrl()).toString();
        }
    }

    private static String getThumbnailVariationUrl(Submission submission, int index) {
        return CompatUtil.fromHtml(submission.getThumbnails().getVariations()[index].getUrl())
                .toString();
    }

    // Lightweight options for preloading: warm the disk cache without the heavy full-resolution
    // ARGB decode the global options would otherwise do (NONE_SAFE + ARGB when highColorspace is
    // on produced discarded bitmaps up to ~15 MB each during scroll). The downloaded file still
    // lands in the disk cache; only the throwaway decode is shrunk.
    private static final DisplayImageOptions PRELOAD_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cacheOnDisk(true)
                    .cacheInMemory(false)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .imageScaleType(ImageScaleType.IN_SAMPLE_POWER_OF_2)
                    .build();

    // Live-feed variant: the same lightweight decode, but also retained in the memory cache so the
    // card can bind the bitmap synchronously as the row scrolls in
    // (HeaderImageLinkView.displayImageCachedFirst) instead of kicking off an async disk decode
    // that pops in while the row is already on screen. Offline mass caching keeps using
    // PRELOAD_OPTIONS (disk only) to avoid thrashing the memory LRU with hundreds of posts.
    private static final DisplayImageOptions FEED_PRELOAD_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cacheOnDisk(true)
                    .cacheInMemory(true)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .imageScaleType(ImageScaleType.IN_SAMPLE_POWER_OF_2)
                    .build();

    /**
     * The decode target the feed preloader uses. Shared with HeaderImageLinkView's synchronous
     * disk-decode fallback so that fallback produces (and keys) a bitmap that collapses onto the
     * same memory-cache entry instead of adding a second, differently sized one.
     *
     * <p>List mode shows small thumbnails, so decode (and cache) those at a fraction of the screen
     * width rather than the full-width lead-image size — otherwise every warmed thumbnail is a
     * multi-MB bitmap the memory cache can barely hold. Card mode keeps the full lead-image size.
     * Keyed off the global pics setting (per-subreddit overrides are handled by the display path
     * falling back to an async load rather than sync-binding an undersized bitmap).
     */
    public static ImageSize feedDecodeSize(final Context context) {
        final boolean big = SettingValues.bigPicEnabled;
        final int w = feedImageWidth(context, big);
        if (!big) {
            return new ImageSize(w, w);
        }
        // Bound the decode to roughly the feed display size so the preload doesn't decode at full
        // source resolution just to throw the bitmap away.
        return new ImageSize(w, MaxHeightImageView.maxHeight);
    }

    /**
     * The feed image target width: the full screen width for a card, or the thumbnail cell width for
     * list mode. Drives both the decode size and which reddit "resolutions" preview is downloaded,
     * so a list thumbnail fetches a small sized image rather than the full-width card preview.
     */
    public static int feedImageWidth(final Context context, final boolean bigImages) {
        if (bigImages) {
            return context.getResources().getDisplayMetrics().widthPixels;
        }
        // The list thumbnail cell is 70dp (100dp with big thumbnails) — target that, not the wider
        // big_thumbnail_width, so we fetch the reddit "resolutions" rung just above the cell (≈320px)
        // instead of the ≈640px one. Smaller downloads let the background warm keep pace with fast
        // scrolling, and the cell is far smaller than the screen so quality is unaffected.
        final float density = context.getResources().getDisplayMetrics().density;
        final int cellDp = SettingValues.bigThumbnails ? 100 : 70;
        return Math.round(cellDp * density);
    }

    private static void loadImage(
            final Context context, final String url, final boolean warmMemory) {
        final Reddit appContext = (Reddit) context.getApplicationContext();
        appContext
                .getImageLoader()
                .loadImage(
                        url,
                        feedDecodeSize(context),
                        warmMemory ? FEED_PRELOAD_OPTIONS : PRELOAD_OPTIONS,
                        null);
    }

    /**
     * Warm the FULL-size header image for a post that is about to open to its comments screen, so
     * the detail header renders in place instead of popping in. Resolves the same URL the full-view
     * header will request (full width — its own path, never the thumbnail), then fires an async warm
     * into the memory cache. Safe to call on the main thread from a click handler (fire-and-forget);
     * if the download lands before the header binds it sync-binds, otherwise it simply falls back to
     * the header's own async load as before.
     */
    public static void warmFull(final Context context, final Submission submission) {
        if (submission == null || SettingValues.shouldSkipImages(context)) {
            return;
        }
        final int fullW = feedImageWidth(context, true);
        final String url = resolveFeedImageUrl(context, submission, false, fullW);
        if (url == null || PLACEHOLDER_URLS.contains(url)) {
            return;
        }
        ((Reddit) context.getApplicationContext())
                .getImageLoader()
                .loadImage(
                        url,
                        new ImageSize(fullW, MaxHeightImageView.maxHeight),
                        FEED_PRELOAD_OPTIONS,
                        null);
    }

    // How many of a freshly-loaded page's images to download+decode synchronously before the rows
    // are shown (covers the first screenful plus a little buffer), and how long to wait for them.
    private static final int FIRST_SCREEN_WARM = 12;
    // Measured first-screen warms complete in <400ms, so a short cap just bounds the worst-case
    // blank feed on a slow network (a straggler pops in rather than holding the whole feed).
    private static final int WARM_TIMEOUT_SECONDS = 2;
    // Parallel downloads for the warm. Higher than the default so the background warm keeps pace
    // with fast scrolling instead of falling behind and letting later rows pop in.
    private static final int WARM_THREADS = 8;

    /**
     * Warm a freshly-loaded page into the memory cache. Called from the page-load background thread,
     * this BLOCKS until the first screenful of images is downloaded and decoded (or a short timeout),
     * so that when the rows bind their thumbnails are already cached and render in place instead of
     * popping in. Sized thumbnails (feedImageWidth) keep each download small enough for this to be
     * quick. The rest of the page keeps warming in the background so later rows are ready on scroll.
     */
    public static void loadPhotos(final Context c, final List<Submission> submissions) {
        final ArrayList<String> urls = new ArrayList<>(submissions.size());
        for (final Submission submission : submissions) {
            final String url = resolveFeedImageUrl(c, submission, true);
            if (url != null && !PLACEHOLDER_URLS.contains(url)) {
                urls.add(url);
            }
        }
        if (urls.isEmpty()) {
            return;
        }

        final ImageLoader loader = ((Reddit) c.getApplicationContext()).getImageLoader();
        final ImageSize size = feedDecodeSize(c);
        final ExecutorService pool =
                Executors.newFixedThreadPool(Math.min(WARM_THREADS, Math.max(1, urls.size())));

        // Block only on the first screenful; the remaining downloads finish in the background.
        final int blockCount = Math.min(urls.size(), FIRST_SCREEN_WARM);
        final CountDownLatch firstScreen = new CountDownLatch(blockCount);
        // TEMP pop-in diagnostics.
        final long t0 = System.currentTimeMillis();
        LogUtil.v("POPIN-PRELOAD start urls=" + urls.size() + " block=" + blockCount + " size=" + size.getWidth());
        for (int i = 0; i < urls.size(); i++) {
            final String url = urls.get(i);
            final boolean counted = i < blockCount;
            pool.execute(
                    () -> {
                        try {
                            loader.loadImageSync(url, size, FEED_PRELOAD_OPTIONS);
                        } catch (Throwable ignored) {
                        } finally {
                            if (counted) {
                                firstScreen.countDown();
                            }
                        }
                    });
        }
        // Orderly shutdown lets the already-submitted background warms finish after we return.
        pool.shutdown();
        try {
            firstScreen.await(WARM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // TEMP: remaining>0 means the timeout fired before the first screenful finished.
            LogUtil.v("POPIN-PRELOAD done waited=" + (System.currentTimeMillis() - t0)
                    + "ms remaining=" + firstScreen.getCount());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** Display URL plus reserved dimensions of the first usable image in a Reddit gallery. */
    public static final class GalleryPreview {
        public final String url;
        public final int width; // -1 if unknown
        public final int height; // -1 if unknown

        GalleryPreview(String url, int width, int height) {
            this.url = url;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Resolve the first usable gallery image, preferring a reddit-sized preview variant over the
     * full-resolution source. Shared by the feed card (HeaderImageLinkView) and the preloader so
     * both reference the same cache entry. Returns null if no usable image exists.
     */
    public static GalleryPreview getGalleryPreview(JsonNode dataNode) {
        if (dataNode == null) return null;
        // A crosspost keeps its gallery data in the parent submission. Mirror the display path,
        // which always prefers the parent when a crosspost parent is present.
        if (dataNode.has("crosspost_parent_list")
                && dataNode.get("crosspost_parent_list").size() > 0) {
            dataNode = dataNode.get("crosspost_parent_list").get(0);
        }
        final JsonNode galleryData = dataNode.get("gallery_data");
        final JsonNode mediaMetadata = dataNode.get("media_metadata");
        if (galleryData == null
                || mediaMetadata == null
                || !galleryData.has("items")
                || galleryData.get("items").size() == 0) {
            return null;
        }

        for (final JsonNode item : galleryData.get("items")) {
            if (!item.has("media_id")) continue;
            final String mediaId = item.get("media_id").asText();
            if (!mediaMetadata.has(mediaId)) continue;
            final JsonNode mediaInfo = mediaMetadata.get(mediaId);
            if (mediaInfo.has("status") && "failed".equals(mediaInfo.get("status").asText())) {
                continue;
            }

            // Prefer the largest reddit-sized preview ("p" is ordered smallest-to-largest).
            if (mediaInfo.has("p") && mediaInfo.get("p").size() > 0) {
                final JsonNode largest = mediaInfo.get("p").get(mediaInfo.get("p").size() - 1);
                if (largest.has("u")) {
                    return new GalleryPreview(
                            largest.get("u").asText(), dimOf(largest, "x"), dimOf(largest, "y"));
                }
            }
            // Fall back to the full-resolution source, normalized to the unsigned i.redd.it host
            // (its signed preview query can't be reused).
            if (mediaInfo.has("s") && mediaInfo.get("s").has("u")) {
                final JsonNode s = mediaInfo.get("s");
                final String url =
                        s.get("u").asText().replace("preview.redd.it", "i.redd.it").replaceAll("\\?.*", "");
                return new GalleryPreview(url, dimOf(s, "x"), dimOf(s, "y"));
            }
        }
        return null;
    }

    private static int dimOf(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asInt() : -1;
    }
}
