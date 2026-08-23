package me.edgan.redditslide.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewTreeObserver;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.ImgurAlbum.AlbumUtils;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.MaxHeightImageView;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Thumbnails;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.NullMarked;

/** Created by TacoTheDank on 12/11/2020. */
@NullMarked
public class PhotoLoader {

    /** Disk-only warm (offline caching); does not populate the memory cache. */
    public static void loadPhoto(final Context c, final Submission submission) {
        loadPhoto(c, submission, false);
    }

    public static void loadPhoto(
            final Context c, final Submission submission, final boolean warmMemory) {
        for (final WarmTarget target : feedWarmTargets(c, submission, warmMemory)) {
            loadImage(c, target, warmMemory);
        }
    }

    private static final List<String> PLACEHOLDER_URLS =
            Arrays.asList("self", "default", "image", "nsfw", "spoiler", "");

    /** One image the feed card will display, the size it will be decoded at, and how. */
    public static final class WarmTarget {
        public final String url;
        public final ImageSize size;
        /** A gallery grid tile, which decodes differently from a lead image. See the options below. */
        private final boolean galleryTile;

        WarmTarget(final String url, final ImageSize size) {
            this(url, size, false);
        }

        WarmTarget(final String url, final ImageSize size, final boolean galleryTile) {
            this.url = url;
            this.size = size;
            this.galleryTile = galleryTile;
        }

        /**
         * {@code warmMemory} decides disk-only versus disk-and-memory for either kind of target —
         * offline mass caching must stay disk-only whether or not the image is a gallery tile, or a
         * few hundred cached posts evict everything the user is currently looking at.
         */
        DisplayImageOptions options(final boolean warmMemory) {
            if (galleryTile) {
                return warmMemory ? GALLERY_TILE_PRELOAD_OPTIONS : GALLERY_TILE_DISK_OPTIONS;
            }
            return warmMemory ? FEED_PRELOAD_OPTIONS : PRELOAD_OPTIONS;
        }
    }

    /**
     * Warm options for a gallery grid tile.
     *
     * <p>EXACTLY rather than the IN_SAMPLE_POWER_OF_2 the other feed warms use. Sampling can only
     * halve, so a 960px-wide preview asked for at a 625px tile does not shrink at all and lands in
     * the memory cache at full size — and a gallery card holds four of those, where the lead image
     * it replaced held one. That evicts the rows the user is looking at. EXACTLY scales the decode
     * to the tile, which is all the tile can show anyway.
     */
    private static final DisplayImageOptions GALLERY_TILE_PRELOAD_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cacheOnDisk(true)
                    .cacheInMemory(true)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .imageScaleType(ImageScaleType.EXACTLY)
                    .build();

    /** The disk-only tile variant, for offline mass caching — see {@link #PRELOAD_OPTIONS}. */
    private static final DisplayImageOptions GALLERY_TILE_DISK_OPTIONS =
            new DisplayImageOptions.Builder()
                    .cacheOnDisk(true)
                    .cacheInMemory(false)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .imageScaleType(ImageScaleType.EXACTLY)
                    .build();

    /**
     * Every image the feed card will display for {@code submission}, with the decode size for each.
     *
     * <p>Usually one — the lead image. A gallery post with the grid turned on displays several, so
     * warming only the first would leave the rest of the tiles to pop in. Both the url and the size
     * have to be the ones the card will actually ask for: UIL keys its memory cache by {@code
     * uri_WxH}, so warming the same image at a different size puts the bitmap somewhere the bind
     * won't look.
     *
     * @param skipDrawableOnlyPosts true for the live feed, which has nothing to download for a post
     *     rendered as a static nsfw/spoiler drawable. Offline mass caching passes false, so a reveal
     *     works without a connection.
     */
    public static List<WarmTarget> feedWarmTargets(
            final Context c, final Submission submission, final boolean skipDrawableOnlyPosts) {
        return feedWarmTargets(c, submission, skipDrawableOnlyPosts, null);
    }

    /**
     * As above, for a caller that knows the feed's base subreddit.
     *
     * <p>{@code baseSub} is the listing's own name, exactly as the adapters hand it to
     * {@link me.edgan.redditslide.SubmissionViews.PopulateSubmissionViewHolder} — the card decides
     * whether to draw a grid from {@link SettingValues#isPicsEnabled(String)} on that name, so a
     * warm that consulted the global flag instead would warm four tiles for a post the card renders
     * as a thumbnail. Null (offline caching, profile and moderator listings) resolves to the global
     * flag, which is what those callers' display path uses too.
     */
    public static List<WarmTarget> feedWarmTargets(
            final Context c,
            final Submission submission,
            final boolean skipDrawableOnlyPosts,
            final @Nullable String baseSub) {
        // One gate for both branches below, and the only one that knows the listing: a post the card
        // draws as the nsfw/spoiler icon has no image to warm, whichever branch would have chosen it.
        // resolveFeedImageUrl repeats the listing-blind half of this check, which is all it can do
        // and which is harmless once this has run.
        if (skipDrawableOnlyPosts && rendersStaticDrawable(submission, baseSub)) {
            return Collections.emptyList();
        }

        if (SettingValues.galleryGrid
                && SettingValues.isPicsEnabled(baseSub)
                && ContentType.getContentType(submission) == ContentType.Type.REDDIT_GALLERY) {
            final GalleryTiles.Grid grid =
                    GalleryTiles.gridFor(c, submission.getDataNode());
            if (grid != null) {
                final int tilePx = GalleryTiles.tileWidthPx(c, grid.span);
                final ImageSize size = new ImageSize(tilePx, tilePx);
                final List<WarmTarget> targets = new ArrayList<>(grid.tiles.size());
                for (final GalleryTiles.Tile tile : grid.tiles) {
                    if (tile.url != null && !PLACEHOLDER_URLS.contains(tile.url)) {
                        targets.add(new WarmTarget(tile.url, size, true));
                    }
                }
                return targets;
            }
        }

        final String url = resolveFeedImageUrl(c, submission, skipDrawableOnlyPosts);
        if (url == null || PLACEHOLDER_URLS.contains(url)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new WarmTarget(url, feedDecodeSize(c)));
    }

    /**
     * The user's "low resolution images" choice, for the connection in use right now.
     *
     * <p>One helper rather than the expression repeated per call site, because the feed card and the
     * preloader have to reach the same answer — they pick the image URL independently, and a
     * disagreement shows up as the card downloading a second copy of the same picture.
     */
    public static boolean isLowRes(final Context c) {
        // Settings first, connectivity last. isConnectedWifi is three binder calls into
        // ConnectivityManager with no caching, and this runs on the main thread once per gallery
        // card bind; ordering it after the two flags means a user with neither setting on — the
        // default — never makes those calls at all. Same result either way: the old expression was
        // (!wifi && mobile) || always.
        if (SettingValues.lowResAlways) {
            return true;
        }
        if (!SettingValues.lowResMobile) {
            return false;
        }
        return !NetworkUtil.isConnectedWifi(c);
    }

    /**
     * The preview-width cap for a gallery post's lead image: half the display width when the user
     * has asked for low-resolution images, otherwise uncapped (the largest rung, as before).
     *
     * <p>Deliberately derived from the display alone, not from the width the caller is about to draw
     * at. The preloader sizes off the global "big pictures" flag while the card sizes off the
     * per-subreddit override, so the two reach different widths for the same post — measured as 1280
     * against 210 on a subreddit with pictures turned off — and a cap built on that would have them
     * choose different preview rungs, leaving the card to download an image the preloader never
     * warmed. A cap both sides can compute identically cannot do that.
     */
    public static int galleryMaxWidth(final Context c) {
        if (!isLowRes(c)) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, c.getResources().getDisplayMetrics().widthPixels / 2);
    }

    /**
     * Whether the card renders this post as a static drawable rather than a downloaded image —
     * mirrors doImageAndText's nsfw/spoiler branches, which run before any content-type handling.
     */
    private static boolean rendersStaticDrawable(final Submission submission) {
        if (submission.isNsfw() && SettingValues.getIsNSFWEnabled()) {
            return true;
        }
        final JsonNode dataNode = submission.getDataNode();
        final JsonNode spoilerNode = (dataNode != null) ? dataNode.get("spoiler") : null;
        return spoilerNode != null && spoilerNode.asBoolean();
    }

    /**
     * As above, plus doImageAndText's second nsfw branch, which only a caller that knows the listing
     * can evaluate: an nsfw post is also drawn as the static icon when it is being shown inside a
     * collection ({@code hideNSFWCollection}) rather than on its own subreddit.
     *
     * <p>Missing this made the warm paths disagree with the card for exactly those posts, and a
     * gallery is where that costs the most — four tiles and four full-resolution tap targets fetched
     * for a card that draws one icon and can only ever open image one.
     */
    private static boolean rendersStaticDrawable(
            final Submission submission, final @Nullable String baseSub) {
        if (rendersStaticDrawable(submission)) {
            return true;
        }
        return baseSub != null
                && submission.isNsfw()
                && SettingValues.hideNSFWCollection
                && (baseSub.equals("frontpage")
                        || baseSub.equals("all")
                        || baseSub.contains("+")
                        || baseSub.equals("popular"));
    }

    /**
     * The image URL the feed card (HeaderImageLinkView.doImageAndText) will display for this
     * submission, or null if the card shows no downloaded image (a static nsfw/spoiler drawable, a
     * self post with a hidden lead image, or no usable media). Kept in lock-step with the display
     * routing so the preload warms the exact cache entry the card later binds; a mismatch reappears
     * as first-view pop-in.
     */
    public static @Nullable String resolveFeedImageUrl(
            final Context c, final Submission submission, final boolean skipDrawableOnlyPosts) {
        return resolveFeedImageUrl(
                c, submission, skipDrawableOnlyPosts, feedImageWidth(c, SettingValues.bigPicEnabled));
    }

    public static @Nullable String resolveFeedImageUrl(
            final Context c,
            final Submission submission,
            final boolean skipDrawableOnlyPosts,
            final int maxW) {
        final ContentType.Type type = ContentType.getContentType(submission);
        final JsonNode dataNode = submission.getDataNode();

        // Posts the card renders as a static drawable instead of a downloaded image. Only skipped
        // for the live feed; offline caching still wants the underlying image so a reveal works
        // without a connection.
        if (skipDrawableOnlyPosts && rendersStaticDrawable(submission)) {
            return null;
        }

        final boolean loadLq = isLowRes(c);

        // maxW sizes the URL to what will actually be shown — the thumbnail cell for a list feed, or
        // the full width for a card / a pre-warmed detail header. Reddit already serves
        // thumbnail-sized "resolutions", so a list thumbnail must not download the ≈1080px image.
        switch (type) {
            case REDDIT_GALLERY: {
                final GalleryPreview gallery = getGalleryPreview(dataNode, galleryMaxWidth(c));
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
                        && dataNode.path("crosspost_parent_list").size() > 0) {
                    thumb = getValidThumbnailUrl(dataNode.path("crosspost_parent_list").path(0));
                }
                return thumb;
            }
            case IMAGE: {
                final JsonNode thumbnailNode =
                        (dataNode != null) ? dataNode.get("thumbnail") : null;
                if (thumbnailNode != null
                        && !thumbnailNode.isNull()
                        && !thumbnailNode.asText().isEmpty()) {
                    final Thumbnails lqThumbnails = usableThumbnails(submission);
                    final boolean lowQ =
                            loadLq
                                    && lqThumbnails != null
                                    && lqThumbnails.getVariations().length > 0;
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
        if (usableThumbnails(submission) != null) {
            return getSubmissionUrl(submission, loadLq, maxW);
        }

        // Direct thumbnail-URL fallback (HeaderImageLinkView's thumbnailType == URL branch).
        return getValidThumbnailUrl(dataNode);
    }

    /**
     * {@code submission.getThumbnails()} wrapped so a non-null return is actually usable.
     *
     * <p>JRAW builds it from {@code preview.images[0]} without checking that element exists, and
     * {@code JsonModel} stores a null node without complaint. An empty {@code images} array —
     * which {@link #getHighQualityUrl(Submission, int)} already tests for above — therefore yields
     * a non-null {@code Thumbnails} whose every accessor throws NullPointerException, so a plain
     * {@code != null} test is not enough on its own.
     */
    public static @Nullable Thumbnails usableThumbnails(final Submission submission) {
        final JsonNode image = submission.getDataNode().path("preview").path("images").path(0);
        if (image.isMissingNode() || image.isNull()) {
            return null;
        }
        return submission.getThumbnails();
    }

    /** Mirrors HeaderImageLinkView.getSubmissionUrl so the warm matches the displayed URL. */
    private static @Nullable String getSubmissionUrl(
            final Submission submission, final boolean loadLq, final int maxWidth) {
        final Thumbnails thumbnails = usableThumbnails(submission);
        if (loadLq && thumbnails != null && thumbnails.getVariations().length != 0) {
            return getLowQualityUrl(submission);
        }
        return getHighQualityUrl(submission, maxWidth);
    }

    /**
     * Real preview image for the submission, preferring the crosspost parent's preview and
     * normalizing the host, or null if none. Shared with HeaderImageLinkView (which delegates here)
     * so the feed card and the preloader resolve the identical URL.
     */
    public static @Nullable String getPreviewUrl(final @Nullable JsonNode dataNode) {
        // No width bound: the full-resolution source (card / fullscreen behavior).
        return getPreviewUrl(dataNode, Integer.MAX_VALUE);
    }

    public static @Nullable String getPreviewUrl(
            final @Nullable JsonNode dataNode, final int maxWidth) {
        if (dataNode == null) {
            return null;
        }
        String previewUrl = null;
        if (dataNode.has("crosspost_parent_list")
                && dataNode.path("crosspost_parent_list").size() > 0) {
            previewUrl =
                    extractPreviewUrl(dataNode.path("crosspost_parent_list").path(0), maxWidth);
        }
        if (previewUrl == null) {
            previewUrl = extractPreviewUrl(dataNode, maxWidth);
        }
        return JsonUtil.normalizeRedditPreviewHost(previewUrl, JsonUtil.linksToReddit(dataNode));
    }

    private static @Nullable String extractPreviewUrl(
            final @Nullable JsonNode node, final int maxWidth) {
        if (node != null
                && node.has("preview")
                && node.path("preview").has("images")
                && node.path("preview").path("images").size() > 0) {
            final JsonNode image = node.path("preview").path("images").path(0);
            // Smallest sized preview covering the display width (thumbnails). Cards pass
            // Integer.MAX_VALUE, so no resolution matches and we fall through to the full source.
            final String sized = sizedResolutionUrl(image, maxWidth);
            if (sized != null) {
                return sized;
            }
            final JsonNode sourceNode = image.get("source");
            if (sourceNode != null && sourceNode.has("url")) {
                return sourceNode.path("url").asText();
            }
        }
        return null;
    }

    /**
     * The node's thumbnail URL if it is a usable image URL, or null for Reddit's placeholder values
     * ("self", "default", "nsfw") or a missing/empty value. Shared with HeaderImageLinkView.
     */
    public static @Nullable String getValidThumbnailUrl(final @Nullable JsonNode node) {
        if (node != null && node.has("thumbnail") && !node.path("thumbnail").isNull()) {
            final String thumbnail = node.path("thumbnail").asText();
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
    public static @Nullable String sizedResolutionUrl(
            final @Nullable JsonNode imageNode, final int maxWidth) {
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
            final int w = r.path("width").asInt();
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
        if (largestBelow == null || covering.path("width").asInt() <= 2L * maxWidth) {
            return covering.path("url").asText();
        }
        return largestBelow.path("url").asText();
    }

    public static @Nullable String getHighQualityUrl(Submission submission) {
        // No width bound: the largest sized preview (card / fullscreen behavior).
        return getHighQualityUrl(submission, Integer.MAX_VALUE);
    }

    /**
     * Feed image URL for {@code submission}, sized to {@code maxWidth}: the smallest reddit
     * "resolutions" preview whose width covers maxWidth, so a list thumbnail downloads a
     * few-hundred-pixel image instead of the ≈1080px card preview (reddit already serves these
     * sizes). When no rung covers maxWidth, falls back to the source if it is under 1080px wide (a
     * small original that would otherwise upscale a tiny rung), else the largest resolution, then the
     * full source, then reddit's own thumbnail field.
     */
    public static @Nullable String getHighQualityUrl(Submission submission, int maxWidth) {
        if (submission.getDataNode().has("preview")) {
            final JsonNode images = submission.getDataNode().path("preview").path("images");
            final JsonNode image = (images != null && images.size() > 0) ? images.get(0) : null;
            if (image != null) {
                // Smallest sized preview that covers the display width so a thumbnail never pulls the
                // full card-sized preview; otherwise the largest sized preview (cards).
                final String sized = sizedResolutionUrl(image, maxWidth);
                if (sized != null) {
                    return sized;
                }
                final JsonNode source = image.get("source");
                // No "resolutions" rung is wide enough to cover the display. Reddit only generates a
                // 1080-wide rung when the source is at least that wide, so a source under 1080px means
                // every rung is small (e.g. a 547px meme tops out at a 320px rung) and would be
                // upscaled ~3x across the card — obviously blurry. Use the source itself: under 1080px
                // it is small to download and matches what the media viewer shows. Larger originals
                // still have a 1080 rung (returned below), so the multi-MB source is never pulled here.
                if (source != null
                        && source.has("url")
                        && source.has("width")
                        && source.path("width").asInt() < 1080) {
                    return source.path("url").asText();
                }
                final JsonNode resolutions = image.get("resolutions");
                if (resolutions != null && resolutions.size() > 0) {
                    final JsonNode largest = resolutions.get(resolutions.size() - 1);
                    if (largest != null && largest.has("url")) {
                        return largest.path("url").asText();
                    }
                }
                // has("url") as well as has("height"): a source node carrying dimensions but no
                // url would otherwise return "" from a method whose callers feed it straight to the
                // image loader, which reads an empty uri as a completed load with a null bitmap.
                // Falling through reaches the thumbnail fallback below instead.
                if (source != null && source.has("height") && source.has("url")) {
                    return source.path("url").asText();
                }
            }
        }
        final Thumbnails thumbnails = usableThumbnails(submission);
        if (thumbnails != null && thumbnails.getSource() != null) {
            String sourceUrl = thumbnails.getSource().getUrl();
            return CompatUtil.fromHtml(
                            sourceUrl.isEmpty() ? submission.getThumbnail() : sourceUrl)
                    .toString();
        } else {
            // Null rather than "": the callers hand this straight to the image loader, which
            // treats an empty uri as a completed load and calls onLoadingComplete with a null
            // bitmap. Every consumer already tests for null and hides the view instead.
            return submission.getThumbnail();
        }
    }

    public static @Nullable String getLowQualityUrl(Submission submission) {
        final String submissionUrl = submission.getUrl();
        if (submissionUrl != null && ContentType.isImgurImage(submissionUrl)) {
            return submissionUrl.substring(0, submissionUrl.lastIndexOf("."))
                    + (SettingValues.lqLow ? "m" : (SettingValues.lqMid ? "l" : "h"))
                    + submissionUrl.substring(submissionUrl.lastIndexOf("."));
        }
        final Thumbnails thumbnails = usableThumbnails(submission);
        if (thumbnails == null) {
            // No preview block at all, so there is no low-quality variation to pick.
            return getHighQualityUrl(submission, Integer.MAX_VALUE);
        }
        int length = thumbnails.getVariations().length;
        if (SettingValues.lqLow && length >= 3) {
            return getThumbnailVariationUrl(submission, 2);
        } else if (SettingValues.lqMid && length >= 4) {
            return getThumbnailVariationUrl(submission, 3);
        } else if (length >= 5) {
            return getThumbnailVariationUrl(submission, length - 1);
        } else {
            return CompatUtil.fromHtml(thumbnails.getSource().getUrl()).toString();
        }
    }

    private static @Nullable String getThumbnailVariationUrl(Submission submission, int index) {
        final Thumbnails thumbnails = usableThumbnails(submission);
        if (thumbnails == null) {
            return null;
        }
        return CompatUtil.fromHtml(thumbnails.getVariations()[index].getUrl()).toString();
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
        loadImage(context, new WarmTarget(url, feedDecodeSize(context)), warmMemory);
    }

    private static void loadImage(
            final Context context, final WarmTarget target, final boolean warmMemory) {
        final Reddit appContext = (Reddit) context.getApplicationContext();
        appContext
                .getImageLoader()
                .loadImage(
                        target.url,
                        target.size,
                        target.options(warmMemory),
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
    // Block on the first screenful plus a buffer of prefetch-ahead rows, so a moderate scroll that
    // moves just past the visible screen still finds those rows warm.
    private static final int FIRST_SCREEN_WARM = 16;
    // Measured first-screen warms complete in <400ms (worst ~1s), so a short cap just bounds the
    // worst-case blank feed on a slow network (a straggler pops in rather than holding the feed).
    private static final int WARM_TIMEOUT_SECONDS = 2;
    // Parallel downloads for the warm — high enough that the background warm keeps pace with a
    // moderate scroll instead of falling behind and letting later rows pop in.
    private static final int WARM_THREADS = 12;

    /**
     * Warm a freshly-loaded page into the memory cache. Called from the page-load background thread,
     * this BLOCKS until the first screenful of images is downloaded and decoded (or a short timeout),
     * so that when the rows bind their thumbnails are already cached and render in place instead of
     * popping in. Sized thumbnails (feedImageWidth) keep each download small enough for this to be
     * quick. The rest of the page keeps warming in the background so later rows are ready on scroll.
     */
    public static void loadPhotos(final Context c, final List<Submission> submissions) {
        loadPhotos(c, submissions, null);
    }

    /** As above, for a caller that knows the feed's base subreddit. See {@link #feedWarmTargets}. */
    public static void loadPhotos(
            final Context c, final List<Submission> submissions, final @Nullable String baseSub) {
        // A gallery post with the grid on contributes one target per tile, so this is no longer one
        // entry per submission. The blocking window below stays denominated in images — that is what
        // FIRST_SCREEN_WARM and the timeout were measured against — but it only ever stops on a post
        // boundary, so a card is never released with half its tiles warmed. On a feed without
        // galleries every post is one image and this is exactly the old behaviour.
        final ArrayList<WarmTarget> targets = new ArrayList<>(submissions.size());
        int firstScreenTargets = 0;
        boolean countingFirstScreen = true;
        for (final Submission submission : submissions) {
            final List<WarmTarget> postTargets = feedWarmTargets(c, submission, true, baseSub);
            targets.addAll(postTargets);
            if (countingFirstScreen) {
                firstScreenTargets += postTargets.size();
                countingFirstScreen = firstScreenTargets < FIRST_SCREEN_WARM;
            }
        }
        if (targets.isEmpty()) {
            return;
        }

        final ImageLoader loader = ((Reddit) c.getApplicationContext()).getImageLoader();
        final ExecutorService pool =
                Executors.newFixedThreadPool(Math.min(WARM_THREADS, Math.max(1, targets.size())));

        // Block only on the first screenful; the remaining downloads finish in the background.
        final int blockCount = Math.min(targets.size(), firstScreenTargets);
        final CountDownLatch firstScreen = new CountDownLatch(blockCount);
        for (int i = 0; i < targets.size(); i++) {
            final WarmTarget target = targets.get(i);
            final boolean counted = i < blockCount;
            pool.execute(
                    () -> {
                        try {
                            loader.loadImageSync(target.url, target.size, target.options(true));
                        } catch (Throwable ignored) {
                            // Warming is best-effort, and Throwable covers the OOM a decode
                            // can raise; the finally below still counts this url down.
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
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // Serializes the (JSON/gallery) URL resolution for scroll-ahead prefetch OFF the main thread —
    // the actual downloads are async on UIL's pool. Single thread keeps nearest-first ordering.
    private static final ExecutorService WARM_AHEAD_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * Scroll-aware prefetch: async memory-warm a window of upcoming rows (the scroll landing zone)
     * so they render in place when they enter the viewport, rather than the page-order background
     * warm falling behind a moving scroll. Called from the feed's scroll listener with a COPY of the
     * window. URL resolution runs on {@link #WARM_AHEAD_EXECUTOR}, never the main thread (it does
     * JSON/gallery traversal — doing it per row on the main thread caused an ANR). Already-warmed
     * URLs are a cheap no-op: loadImage hits the memory cache and returns without re-downloading, and
     * UIL de-dupes concurrent same-URI loads against the page-order warm. {@code baseSub} is the
     * feed's base subreddit, which decides whether a row shows a big image at all — see {@link
     * #feedWarmTargets}.
     */
    public static void warmAhead(
            final Context c, final List<Submission> window, final @Nullable String baseSub) {
        if (c == null || window == null || window.isEmpty() || SettingValues.shouldSkipImages(c)) {
            return;
        }
        WARM_AHEAD_EXECUTOR.execute(
                () -> {
                    for (final Submission s : window) {
                        try {
                            for (final WarmTarget target : feedWarmTargets(c, s, true, baseSub)) {
                                loadImage(c, target, true);
                            }
                        } catch (Throwable ignored) {
                            // Warming is best-effort: the image loads on bind instead.
                        }
                    }
                });
    }

    /**
     * Prefetch the full-resolution image the media viewer opens when this card is tapped, so the
     * viewer shows a disk-cached original instead of downloading it on tap. The feed only warms the
     * smaller preview/thumbnail (resolveFeedImageUrl); this warms the exact URL the tap target
     * requests — {@code submission.getUrl()} for a single IMAGE (SubmissionThumbnailHelper.openImage)
     * and the first still image's source for a reddit gallery (RedditGalleryView loads the same URL
     * through this ImageLoader). Fire-and-forget, called from the feed once a row has settled;
     * URL resolution (gallery JSON traversal) runs on {@link #WARM_AHEAD_EXECUTOR}, never the main
     * thread, and the download is async on UIL's pool (a cache hit is a cheap no-op). Disk-only
     * (PRELOAD_OPTIONS) so a multi-MB original doesn't thrash the feed's memory cache. The Wi-Fi /
     * data-saver gate is applied once per sweep by the sole caller ({@link #warmVisibleTapTargets}),
     * so it isn't repeated per row here. {@code app} must be the application context.
     */
    private static void warmTapTarget(
            final Context app, final Submission submission, final @Nullable String baseSub) {
        if (app == null || submission == null) {
            return;
        }
        TAP_TARGET_EXECUTOR.execute(
                () -> {
                    try {
                        for (final String url : tapTargetUrls(app, submission, baseSub)) {
                            if (url != null && !PLACEHOLDER_URLS.contains(url)) {
                                loadImage(app, url, false);
                            }
                        }
                    } catch (Throwable ignored) {
                        // Warming is best-effort: the image loads on tap instead.
                    }
                });
    }

    // Tap-target warms run on their own thread, off the preview warm-ahead pipeline: resolving an
    // imgur album's first image is a blocking imgur API call, which must not stall
    // WARM_AHEAD_EXECUTOR's latency-sensitive preview prefetch. Single-threaded so those album API
    // calls also stay serialized — one imgur request at a time on the shared key.
    private static final ExecutorService TAP_TARGET_EXECUTOR = Executors.newSingleThreadExecutor();

    /**
     * Warm the tap-target (full-resolution media-viewer) image for every currently-visible submission
     * in a settled feed. Call from a feed's scroll listener when it goes idle — never mid-fling — so
     * the heavier full-original / imgur-album downloads only fire for rows the user lands on. Shared by
     * every feed surface (the subreddit feed, the profile Saved/Submitted tabs, …) so they behave
     * identically. Handles both the staggered-grid feed and the linear profile lists; maps adapter
     * position to list index by {@code headerOffset} (the number of non-post rows before index 0, e.g.
     * a header/spacer). {@code warmed} dedupes across repeated settles; items that aren't Submissions
     * (e.g. comments in a profile list) are skipped.
     *
     * <p>{@code context} is nullable because every caller is a feed fragment passing
     * {@code getContext()} from a scroll or content-change callback, which can fire after the
     * fragment has detached. Warming has nothing to do in that case, which is what the guard below
     * already did before the declaration admitted it.
     */
    public static void warmVisibleTapTargets(
            final @Nullable Context context,
            final RecyclerView rv,
            final @Nullable List<?> posts,
            final int headerOffset,
            final Set<String> warmed) {
        warmVisibleTapTargets(context, rv, posts, headerOffset, warmed, null);
    }

    /** As above, for a caller that knows the feed's base subreddit. See {@link #feedWarmTargets}. */
    public static void warmVisibleTapTargets(
            final @Nullable Context context,
            final RecyclerView rv,
            final @Nullable List<?> posts,
            final int headerOffset,
            final Set<String> warmed,
            final @Nullable String baseSub) {
        if (context == null || rv == null || posts == null || warmed == null) {
            return;
        }
        // Gate the whole sweep once — the result is identical for every visible row, so this avoids a
        // per-row ConnectivityManager query on the main thread. Skip only when Data saving is active
        // (always, or on mobile data via isDataSavingActive): the tap-target warm pulls
        // full-resolution originals (and fires imgur album API calls) for images the user may never
        // open, so with Data saving on the viewer still downloads on tap as before. With Data saving
        // off the user has opted into full-quality loading, so prefetch runs on any connection.
        if (SettingValues.isDataSavingActive(context)) {
            return;
        }
        final RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (lm == null) {
            return;
        }
        int firstAdapter;
        int lastAdapter;
        if (lm instanceof StaggeredGridLayoutManager) {
            final int[] first = ((StaggeredGridLayoutManager) lm).findFirstVisibleItemPositions(null);
            final int[] last = ((StaggeredGridLayoutManager) lm).findLastVisibleItemPositions(null);
            if (first == null || first.length == 0 || last == null || last.length == 0) {
                return;
            }
            // Take the min/max over the *valid* per-span positions only: a span with no visible item
            // reports NO_POSITION (-1), which would otherwise drag firstAdapter to -1 and skip the
            // whole sweep. If every span is empty, both stay NO_POSITION and the guard below returns.
            firstAdapter = RecyclerView.NO_POSITION;
            for (final int p : first) {
                if (p != RecyclerView.NO_POSITION
                        && (firstAdapter == RecyclerView.NO_POSITION || p < firstAdapter)) {
                    firstAdapter = p;
                }
            }
            lastAdapter = RecyclerView.NO_POSITION;
            for (final int p : last) {
                if (p != RecyclerView.NO_POSITION && p > lastAdapter) {
                    lastAdapter = p;
                }
            }
        } else if (lm instanceof LinearLayoutManager) {
            firstAdapter = ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
            lastAdapter = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
        } else {
            return;
        }
        if (firstAdapter == RecyclerView.NO_POSITION || lastAdapter == RecyclerView.NO_POSITION) {
            return;
        }
        final Context app = context.getApplicationContext();
        final int size = posts.size();
        for (int adapterPos = firstAdapter; adapterPos <= lastAdapter; adapterPos++) {
            final int index = adapterPos - headerOffset;
            if (index < 0 || index >= size) {
                continue;
            }
            try {
                final Object item = posts.get(index);
                if (item instanceof Submission) {
                    final Submission s = (Submission) item;
                    if (warmed.add(s.getFullName())) {
                        warmTapTarget(app, s, baseSub);
                    }
                }
            } catch (RuntimeException ignored) {
                // The list was swapped under us (background reset/addAll); stop and retry next settle.
                return;
            }
        }
    }

    /**
     * Run {@code sweep} once, on the first layout where the feed actually has content, then
     * unregister. The settle-sweep only fires after a scroll, so this covers a post already visible on
     * initial display (e.g. the top of a Saved/History tab, or the first feed card) that would
     * otherwise never be prefetched until the user scrolls. {@code hasContent} gates the one shot so an
     * early layout showing only a loading spinner doesn't consume it. If content never arrives (empty
     * feed or error), the listener is still pulled off on detach so it can't hold the fragment for the
     * activity's lifetime.
     */
    public static void warmVisibleTapTargetsOnFirstLayout(
            final RecyclerView rv, final BooleanSupplier hasContent, final Runnable sweep) {
        if (rv == null || hasContent == null || sweep == null) {
            return;
        }
        final boolean[] done = {false};
        final ViewTreeObserver.OnGlobalLayoutListener layoutListener =
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (done[0] || rv.getChildCount() == 0 || !hasContent.getAsBoolean()) {
                            return;
                        }
                        done[0] = true;
                        final ViewTreeObserver observer = rv.getViewTreeObserver();
                        if (observer.isAlive()) {
                            observer.removeOnGlobalLayoutListener(this);
                        }
                        sweep.run();
                    }
                };
        // A global-layout listener lives on the window's observer, not the view's. The one shot only
        // unregisters itself once content arrives; if it never does, remove it on detach too so it
        // can't outlive the view. Re-add on a later re-attach in case content arrives then; once it has
        // fired, `done` keeps it from running (or re-registering) again. The attach listener itself
        // lives on the view and is released with it.
        rv.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        if (!done[0]) {
                            rv.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
                        }
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        final ViewTreeObserver observer = rv.getViewTreeObserver();
                        if (observer.isAlive()) {
                            observer.removeOnGlobalLayoutListener(layoutListener);
                        }
                    }
                });
        if (rv.isAttachedToWindow()) {
            rv.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        }
    }

    /**
     * Like {@link #warmVisibleTapTargetsOnFirstLayout} but re-arms across refreshes: runs {@code
     * sweep} whenever the feed hands the view a different non-empty post list than the one last swept.
     * The initial load and each pull-to-refresh/reset replace the list ({@code GeneralPosts} reassigns
     * it on reset), so the visible top rows are warmed without a scroll every time the content is
     * replaced; an append keeps the same list reference, so it doesn't re-fire (the settle-sweep
     * covers appended rows). The listener persists for the RecyclerView's lifetime — the per-layout
     * cost is a reference check. For a source that clears its list in place (same reference) on reset,
     * use the one-shot plus an explicit refresh hook instead.
     */
    public static void warmVisibleTapTargetsOnContentChange(
            final RecyclerView rv, final Supplier<List<?>> currentList, final Runnable sweep) {
        if (rv == null || currentList == null || sweep == null) {
            return;
        }
        // Sentinel distinct from any real list, so the first non-empty list already counts as a change.
        final Object[] lastSwept = {new Object()};
        final ViewTreeObserver.OnGlobalLayoutListener layoutListener =
                () -> {
                    final List<?> list = currentList.get();
                    if (rv.getChildCount() > 0
                            && list != null
                            && !list.isEmpty()
                            && list != lastSwept[0]) {
                        lastSwept[0] = list;
                        sweep.run();
                    }
                };
        // A global-layout listener lives on the window's observer, not the view's, so it must be
        // pulled off when the view leaves the window or it keeps firing and holds the fragment for the
        // activity's lifetime. Add it on attach / remove it on detach (both while getViewTreeObserver()
        // still returns the window observer), so it never outlives the view yet survives a
        // detach/re-attach; the attach listener itself is released with the view.
        rv.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {
                        rv.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
                    }

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        final ViewTreeObserver observer = rv.getViewTreeObserver();
                        if (observer.isAlive()) {
                            observer.removeOnGlobalLayoutListener(layoutListener);
                        }
                    }
                });
        if (rv.isAttachedToWindow()) {
            rv.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
        }
    }

    /**
     * The full-resolution URL the media viewer opens on tap, or null for content whose tap target is
     * not a still image warmed here (videos, links, self posts, animated gallery/album items). A
     * single IMAGE opens on {@code submission.getUrl()} (SubmissionThumbnailHelper.openImage's IMAGE
     * branch), with an imgur URL taking a {@code .png} suffix to mirror MediaView's own display path;
     * a reddit gallery opens on its first still image's source URL; an imgur album opens on its first
     * still image, resolved through the imgur API. Callers run this off the main thread (the album
     * branch does blocking network I/O).
     */
    private static List<String> tapTargetUrls(
            final Context context, final Submission submission, final @Nullable String baseSub) {
        final ContentType.Type type = ContentType.getContentType(submission);
        if (type == ContentType.Type.IMAGE) {
            final String url = submission.getUrl();
            // Mirror MediaView.onCreate's initial displayImage: it appends ".png" for an imgur URL, so
            // warm that exact URL or the disk-cache entry won't match what the viewer requests.
            return singletonOrEmpty(
                    (url != null && ContentType.isImgurHash(url)) ? url + ".png" : url);
        }
        if (type == ContentType.Type.REDDIT_GALLERY) {
            return galleryTapTargetUrls(submission, baseSub);
        }
        if (type == ContentType.Type.ALBUM) {
            // Imgur album: unlike a reddit gallery, the member image URLs aren't in the reddit post,
            // so resolve the first image through the imgur API (blocking — hence TAP_TARGET_EXECUTOR).
            return singletonOrEmpty(
                    AlbumUtils.getFirstAlbumImageUrlBlocking(context, submission.getUrl()));
        }
        return Collections.emptyList();
    }

    private static List<String> singletonOrEmpty(final @Nullable String url) {
        return url == null ? Collections.emptyList() : Collections.singletonList(url);
    }

    /**
     * The full-resolution images a tap on this gallery card can open.
     *
     * <p>One, normally: the card opens on the gallery's first still. With the grid on, every tile is
     * its own tap target and opens the viewer on that image, so each of them needs warming or three
     * of the four tiles download on tap while the first is instant. Bounded by
     * {@link GalleryTiles#MAX_FEED_TILES}, and the caller has already checked that the user has not
     * turned data saving on.
     *
     * <p>Animated entries are skipped for the same reason the single-image path skips them: the
     * viewer opens those as a gif or mp4, not as a still this loader can warm.
     */
    private static List<String> galleryTapTargetUrls(
            final Submission submission, final @Nullable String baseSub) {
        final JsonNode dataNode = submission.getDataNode();
        // rendersStaticDrawable: a card showing the nsfw/spoiler icon has no tiles to tap, and its
        // one tap opens the viewer at image one — so warm that, not four originals nobody can reach.
        if (!SettingValues.galleryGrid
                || !SettingValues.isPicsEnabled(baseSub)
                || rendersStaticDrawable(submission, baseSub)) {
            return singletonOrEmpty(firstGalleryStillSourceUrl(dataNode));
        }
        final List<GalleryImage> images = GalleryTiles.imagesFor(dataNode);
        if (images.size() < 2) {
            // Too few for a grid, so the card still opens on the first still image.
            return singletonOrEmpty(firstGalleryStillSourceUrl(dataNode));
        }
        final int tiles = GalleryTiles.tileCount(images.size());
        final List<String> urls = new ArrayList<>(tiles);
        for (int i = 0; i < tiles; i++) {
            final GalleryImage image = images.get(i);
            if (image.isAnimated() || image.metadata == null || image.metadata.source == null) {
                continue;
            }
            final String url = image.metadata.source.u;
            if (url != null && !url.isEmpty()) {
                urls.add(url);
            }
        }
        return urls;
    }

    /**
     * Source URL of the first still image in a reddit gallery — the URL the gallery viewer requests
     * for image one (GalleryImage.getImageUrl returns the source "u", and RedditGalleryView displays
     * it through this ImageLoader). Animated items are skipped: their tap target is a gif/mp4, not a
     * still-image warm. Null if the gallery JSON is absent or unusable. Mirrors JsonUtil.getGalleryData
     * so the warmed disk entry matches what the viewer later requests.
     */
    private static @Nullable String firstGalleryStillSourceUrl(@Nullable JsonNode dataNode) {
        if (dataNode == null) {
            return null;
        }
        // A crosspost keeps its gallery data on the parent (mirrors the display path).
        if (dataNode.has("crosspost_parent_list")
                && dataNode.path("crosspost_parent_list").size() > 0) {
            dataNode = dataNode.path("crosspost_parent_list").path(0);
        }
        final JsonNode galleryData = dataNode.path("gallery_data");
        final JsonNode mediaMetadata = dataNode.path("media_metadata");
        if (galleryData == null
                || mediaMetadata == null
                || !galleryData.has("items")
                || galleryData.path("items").size() == 0) {
            return null;
        }
        for (final JsonNode item : galleryData.path("items")) {
            if (item == null || !item.has("media_id")) {
                continue;
            }
            final String mediaId = item.path("media_id").asText();
            if (!mediaMetadata.has(mediaId)) {
                continue;
            }
            final JsonNode media = mediaMetadata.path(mediaId);
            if (media == null || !media.has("s")) {
                continue;
            }
            // Skip animated items — the viewer opens those as gif/mp4, not a still-image warm.
            final String e = media.has("e") ? media.path("e").asText() : "";
            final String m = media.has("m") ? media.path("m").asText() : "";
            if ("AnimatedImage".equals(e) || (m != null && m.contains("gif"))) {
                continue;
            }
            final JsonNode s = media.path("s");
            if (s != null && s.has("u")) {
                return StringEscapeUtils.unescapeHtml4(s.path("u").asText());
            }
        }
        return null;
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
     *
     * <p>Capped at {@code maxWidth} — the smallest {@code p} rung that covers it, rather than the
     * largest rung there is. {@link Integer#MAX_VALUE} means uncapped, the same convention
     * {@link #sizedResolutionUrl} uses, and nothing covers it so the largest rung wins. A finite cap
     * is how the "low resolution images" setting reaches gallery posts, whose preview selection
     * otherwise ignored it and always pulled the full-size rung.
     */
    public static @Nullable GalleryPreview getGalleryPreview(
            @Nullable JsonNode dataNode, final int maxWidth) {
        if (dataNode == null) return null;
        // A crosspost keeps its gallery data in the parent submission. Mirror the display path,
        // which always prefers the parent when a crosspost parent is present.
        if (dataNode.has("crosspost_parent_list")
                && dataNode.path("crosspost_parent_list").size() > 0) {
            dataNode = dataNode.path("crosspost_parent_list").path(0);
        }
        final JsonNode galleryData = dataNode.path("gallery_data");
        final JsonNode mediaMetadata = dataNode.path("media_metadata");
        if (galleryData == null
                || mediaMetadata == null
                || !galleryData.has("items")
                || galleryData.path("items").size() == 0) {
            return null;
        }

        for (final JsonNode item : galleryData.path("items")) {
            if (!item.has("media_id")) continue;
            final String mediaId = item.path("media_id").asText();
            if (!mediaMetadata.has(mediaId)) continue;
            final JsonNode mediaInfo = mediaMetadata.path(mediaId);
            if (mediaInfo.has("status") && "failed".equals(mediaInfo.path("status").asText())) {
                continue;
            }

            // Prefer a reddit-sized preview ("p" is ordered smallest-to-largest): the largest one
            // when uncapped, otherwise the smallest that still covers maxWidth.
            if (mediaInfo.has("p") && mediaInfo.path("p").size() > 0) {
                final JsonNode previews = mediaInfo.path("p");
                JsonNode chosen = previews.path(previews.size() - 1);
                if (maxWidth != Integer.MAX_VALUE) {
                    for (final JsonNode rung : previews) {
                        if (rung != null && rung.has("u") && dimOf(rung, "x") >= maxWidth) {
                            chosen = rung;
                            break;
                        }
                    }
                }
                if (chosen.has("u")) {
                    return new GalleryPreview(
                            chosen.path("u").asText(), dimOf(chosen, "x"), dimOf(chosen, "y"));
                }
            }
            // Fall back to the full-resolution source, normalized to the unsigned i.redd.it host
            // (its signed preview query can't be reused).
            if (mediaInfo.has("s") && mediaInfo.path("s").has("u")) {
                final JsonNode s = mediaInfo.path("s");
                final String url =
                        s.path("u").asText().replace("preview.redd.it", "i.redd.it").replaceAll("\\?.*", "");
                return new GalleryPreview(url, dimOf(s, "x"), dimOf(s, "y"));
            }
        }
        return null;
    }

    private static int dimOf(JsonNode node, String field) {
        return node.has(field) ? node.path(field).asInt() : -1;
    }
}
