package me.edgan.redditslide.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import java.util.List;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.MaxHeightImageView;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Thumbnails;

/** Created by TacoTheDank on 12/11/2020. */
public class PhotoLoader {

    public static void loadPhoto(final Context c, final Submission submission) {
        String url;
        final ContentType.Type type = ContentType.getContentType(submission);
        final Thumbnails thumbnails = submission.getThumbnails();
        final Submission.ThumbnailType thumbnailType = submission.getThumbnailType();

        // Warm the disk cache for the same sized gallery preview the feed card will display, so
        // gallery posts don't pop in on first view (getGalleryPreview resolves crossposts too).
        if (type == ContentType.Type.REDDIT_GALLERY) {
            final GalleryPreview gallery = getGalleryPreview(submission.getDataNode());
            if (gallery != null) {
                loadImage(c, gallery.url);
            }
            return;
        }

        if (thumbnails != null) {

            if (type == ContentType.Type.IMAGE
                    || type == ContentType.Type.SELF
                    || thumbnailType == Submission.ThumbnailType.URL) {
                if (type == ContentType.Type.IMAGE) {
                    // Mirror HeaderImageLinkView's big-image selection exactly (it delegates to the
                    // shared helpers below) so the preload warms the same cache entry the card
                    // shows — otherwise imgur low-res / no-preview images pop in on first view.
                    final boolean loadLq =
                            ((!NetworkUtil.isConnectedWifi(c) && SettingValues.lowResMobile)
                                            || SettingValues.lowResAlways)
                                    && thumbnails.getVariations() != null
                                    && thumbnails.getVariations().length > 0;
                    url = loadLq ? getLowQualityUrl(submission) : getHighQualityUrl(submission);

                } else {

                    if ((!NetworkUtil.isConnectedWifi(c) && SettingValues.lowResMobile
                                    || SettingValues.lowResAlways)
                            && thumbnails.getVariations().length != 0) {

                        final int length = thumbnails.getVariations().length;
                        if (SettingValues.lqLow && length >= 3) {
                            url = getThumbnailUrl(thumbnails.getVariations()[2]);
                        } else if (SettingValues.lqMid && length >= 4) {
                            url = getThumbnailUrl(thumbnails.getVariations()[3]);
                        } else if (length >= 5) {
                            url = getThumbnailUrl(thumbnails.getVariations()[length - 1]);
                        } else {
                            url = getThumbnailUrl(thumbnails.getSource());
                        }
                    } else {
                        url = getThumbnailUrl(thumbnails.getSource());
                    }
                }
                loadImage(c, url);
            }
        }
    }

    private static String getThumbnailUrl(final Thumbnails.Image thumbnail) {
        return CompatUtil.fromHtml(thumbnail.getUrl()).toString(); // unescape url characters
    }

    // --- Shared feed-image URL selection ---------------------------------------------------------
    // HeaderImageLinkView delegates to these so the preloader warms exactly the entry the card
    // displays. Keep the two in lock-step; divergence reintroduces first-view pop-in.

    public static String getHighQualityUrl(Submission submission) {
        if (submission.getDataNode().has("preview")) {
            final JsonNode images = submission.getDataNode().get("preview").get("images");
            final JsonNode image = (images != null && images.size() > 0) ? images.get(0) : null;
            if (image != null) {
                // Prefer the largest *sized* preview ("resolutions", ≈1080px) over the
                // full-resolution "source". The source is a multi-MB original that loses the preload
                // race and pops in on first load; the sized variant (the same kind gallery posts
                // use) downloads fast enough to be ready. Fullscreen uses the real image URL.
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

    private static void loadImage(final Context context, final String url) {
        final Reddit appContext = (Reddit) context.getApplicationContext();

        // Bound the decode to roughly the feed display size so the preload doesn't decode at full
        // source resolution just to throw the bitmap away.
        final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        final ImageSize targetSize =
                new ImageSize(metrics.widthPixels, MaxHeightImageView.maxHeight);

        appContext.getImageLoader().loadImage(url, targetSize, PRELOAD_OPTIONS, null);
    }

    public static void loadPhotos(final Context c, final List<Submission> submissions) {
        for (final Submission submission : submissions) {
            loadPhoto(c, submission);
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
