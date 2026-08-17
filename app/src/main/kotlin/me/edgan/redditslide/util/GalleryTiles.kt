package me.edgan.redditslide.util

import android.content.Context
import com.fasterxml.jackson.databind.JsonNode
import java.util.ArrayList
import me.edgan.redditslide.Activities.GalleryImage
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shape and image sizing for the feed's gallery grid — the square-thumbnail rendering of a Reddit
 * gallery post that [me.edgan.redditslide.Views.GalleryGridView] draws when
 * [me.edgan.redditslide.SettingValues.galleryGrid] is on.
 *
 * No views and no Android drawing here: this is the piece both the display path
 * ([me.edgan.redditslide.SubmissionViews.HeaderImageLinkView]) and the preloader
 * ([PhotoLoader]) call, so that the URL and decode size the preloader warms are byte-identical to
 * the ones the card later binds. A divergence between the two doesn't fail anywhere — it quietly
 * splits UIL's `uri_WxH` memory key in two and the tiles pop in after the row is on screen, which
 * is the behaviour the feed goes to some length to avoid.
 */
object GalleryTiles {

    /**
     * The most tiles a feed card shows. Beyond this the last tile becomes the "+N" overflow, so a
     * twenty-image gallery costs the same card height as a four-image one.
     */
    const val MAX_FEED_TILES: Int = 4

    /**
     * Gap between tiles, in dp.
     *
     * Wide enough to read as a separator rather than a seam: a gallery's images are often near
     * duplicates of each other (the same room from two angles, a before and an after), and a hairline
     * gap leaves them looking like one picture.
     */
    private const val GUTTER_DP: Float = 8f

    /**
     * Total horizontal space the card takes out of the screen — `submission_largecard.xml`'s
     * CardView carries `android:layout_margin="4dp"`, so 4dp on each side.
     */
    private const val CARD_INSET_DP: Float = 8f

    /** One cell of the grid: its position in the gallery, and the url to load, if it has one. */
    data class Tile(@JvmField val index: Int, @JvmField val url: String?)

    /**
     * Everything a card needs to draw the grid, from one pass over the gallery JSON.
     *
     * [gallerySize] is the whole gallery, which [tiles] may be a capped view of; the difference is
     * what [overflowCount] reports.
     */
    data class Grid(
        @JvmField val tiles: List<Tile>,
        @JvmField val span: Int,
        @JvmField val overflowCount: Int,
        @JvmField val gallerySize: Int,
    )

    /**
     * Columns for a gallery of [size].
     *
     * Three only for a gallery of exactly three, so the row comes out even; everything else is two.
     * Continuum's rule (two for 2 or 4, three otherwise) doesn't survive the [MAX_FEED_TILES] cap —
     * it would put a nine-image gallery in three columns and leave a ragged 3 + 1 second row.
     */
    @JvmStatic
    fun spanFor(size: Int): Int = if (size == 3) 3 else 2

    /** How many tiles a feed card draws for a gallery of [size]. */
    @JvmStatic
    fun tileCount(size: Int): Int = min(size, MAX_FEED_TILES)

    /**
     * The number the "+N" overflow tile shows, or 0 when the whole gallery fits.
     *
     * The overflow tile still renders its own image underneath the scrim, so the images it stands
     * for are the ones from its own index onwards: N counts itself.
     */
    @JvmStatic
    fun overflowCount(size: Int): Int =
        if (size > MAX_FEED_TILES) size - (MAX_FEED_TILES - 1) else 0

    /** The gallery index the "+N" tile opens on: the first image not shown in full. */
    @JvmStatic
    fun overflowIndex(): Int = MAX_FEED_TILES - 1

    @JvmStatic
    fun gutterPx(context: Context): Int =
        (GUTTER_DP * context.resources.displayMetrics.density).roundToInt()

    /**
     * The width one tile is loaded at.
     *
     * Computed from the display metrics rather than measured from the view, because the preloader
     * has to arrive at the same number before any view exists. It ignores a multi-column feed for
     * the same reason [PhotoLoader.feedImageWidth] does — over-estimating only picks a slightly
     * larger preview rung, which is never worse to look at, and the warm and the bind still agree.
     */
    @JvmStatic
    fun tileWidthPx(context: Context, span: Int): Int {
        val metrics = context.resources.displayMetrics
        val inset = (CARD_INSET_DP * metrics.density).roundToInt()
        val gutters = (span - 1) * gutterPx(context)
        return ((metrics.widthPixels - inset - gutters) / span).coerceAtLeast(1)
    }

    /**
     * The number of pixels a tile's preview rung is chosen against — half a tile when the user has
     * asked for low-resolution images, a whole tile otherwise.
     *
     * <p>Separate from [tileWidthPx], which stays the size the tile is drawn and decoded at: low
     * resolution changes which image is downloaded, not how big the cell is. Callers that need to
     * judge whether a cached bitmap is big enough have to measure against this rather than the
     * cell, or they reject the very image the setting asked them to fetch.
     */
    @JvmStatic
    fun rungTargetPx(context: Context, span: Int): Int {
        val tile = tileWidthPx(context, span)
        return (if (PhotoLoader.isLowRes(context)) tile / 2 else tile).coerceAtLeast(1)
    }

    /**
     * The gallery images of [dataNode], in the order the gallery viewer will show them.
     *
     * Goes through [JsonUtil.getGalleryData] rather than walking `gallery_data` again, because that
     * is the exact list `SubmissionClickActions` hands to `RedditGallery`/`RedditGalleryPager`.
     * Sharing it is what makes a tile's position usable as the viewer's start index: entries that
     * traversal drops (no `media_metadata` entry, no `s` node) are dropped here too, so tile *k* and
     * viewer image *k* can't drift apart.
     */
    @JvmStatic
    fun imagesFor(dataNode: JsonNode?): List<GalleryImage> {
        val images = ArrayList<GalleryImage>()
        JsonUtil.getGalleryData(galleryNode(dataNode), images)
        return images
    }

    /**
     * The node carrying the gallery: a crosspost keeps it on the parent. Mirrors the display path
     * ([me.edgan.redditslide.SubmissionViews.HeaderImageLinkView.handleRedditGalleryType] and
     * [PhotoLoader.getGalleryPreview]), which prefer the parent whenever one is present.
     */
    @JvmStatic
    fun galleryNode(dataNode: JsonNode?): JsonNode? {
        if (dataNode == null) {
            return null
        }
        if (dataNode.has("crosspost_parent_list") && dataNode.path("crosspost_parent_list").size() > 0) {
            return dataNode.path("crosspost_parent_list").path(0)
        }
        return dataNode
    }

    /**
     * The grid a feed card draws for [dataNode], or null when the post has none to draw — fewer than
     * two usable images, in which case the card keeps its ordinary single lead image.
     */
    @JvmStatic
    fun gridFor(context: Context, dataNode: JsonNode?): Grid? {
        val images = imagesFor(dataNode)
        if (images.size < 2) {
            return null
        }
        val span = spanFor(images.size)
        val targetPx = rungTargetPx(context, span)
        val tiles =
            (0 until tileCount(images.size)).map { i -> Tile(i, previewUrlFor(images[i], targetPx)) }
        return Grid(tiles, span, overflowCount(images.size), images.size)
    }

    /**
     * The urls the grid will bind, for the preloader to warm, with the cells that have none dropped.
     * Empty for a post that shows no grid.
     */
    @JvmStatic
    fun tileUrlsFor(context: Context, dataNode: JsonNode?): List<String> =
        gridFor(context, dataNode)?.tiles?.mapNotNull { it.url } ?: emptyList()

    /**
     * The url to load for one tile at a target of [targetPx] square.
     *
     * Reddit's `p` previews are ordered smallest-to-largest, so the first rung that covers the tile
     * is the smallest one that does. Two details matter:
     *
     * - Coverage is tested on `min(x, y)`, not on the width. Tiles are drawn `CENTER_CROP` into a
     *   square, so it is the *shorter* side of the source that has to cover the tile; matching on
     *   width alone leaves a wide panorama visibly soft once it's cropped down to a square.
     * - Cost is judged on the width, and a rung more than 2x the tile wide is passed over in favour
     *   of the largest one below — the same guard [PhotoLoader.sizedResolutionUrl] applies. `p` is
     *   sometimes sparse (216, then a jump straight to 1080), and a wide source can only cover a
     *   square tile with a rung several times the tile's width; downloading that for each of four
     *   tiles is the thing the feed's sizing exists to prevent. A slightly soft tile is the better
     *   trade, and it is the one the lead image already makes.
     */
    @JvmStatic
    fun previewUrlFor(image: GalleryImage, targetPx: Int): String? {
        val previews = image.metadata?.p
        if (previews != null) {
            var covering: GalleryImage.MediaMetadata.Preview? = null
            var largestBelow: GalleryImage.MediaMetadata.Preview? = null
            var lastUsable: GalleryImage.MediaMetadata.Preview? = null
            // Nullable element type on purpose: JsonUtil.getGalleryData skips a null entry in the
            // "p" array without shrinking the array it already sized, so a slot really can be null.
            for (preview: GalleryImage.MediaMetadata.Preview? in previews) {
                if (preview == null || preview.u.isNullOrEmpty()) {
                    continue
                }
                lastUsable = preview
                val shortest = min(preview.x, preview.y)
                if (shortest <= 0) {
                    continue
                }
                if (shortest >= targetPx) {
                    if (covering == null) {
                        covering = preview
                    }
                } else {
                    largestBelow = preview
                }
            }
            if (covering != null) {
                val overshoots = covering.x > 2 * targetPx
                return if (overshoots && largestBelow != null) largestBelow.u else covering.u
            }
            // Nothing covers the tile: the largest rung there is beats scaling up a tiny one.
            if (lastUsable != null) {
                return lastUsable.u
            }
        }
        return sourceFallbackUrl(image)
    }

    /**
     * The still-image source url, for an entry reddit gave no `p` previews for.
     *
     * Normalized to the unsigned `i.redd.it` host exactly as [PhotoLoader.getGalleryPreview] does —
     * `s.u`'s signed `preview.redd.it` query can't be reused. An animated entry with no previews
     * yields null rather than its `.mp4`, which the image loader cannot decode; the tile draws its
     * placeholder instead.
     */
    private fun sourceFallbackUrl(image: GalleryImage): String? {
        val source = image.metadata?.source?.u ?: image.url ?: return null
        if (source.isEmpty() || source.endsWith(".mp4")) {
            return null
        }
        return source.replace("preview.redd.it", "i.redd.it").replace(Regex("\\?.*"), "")
    }
}
