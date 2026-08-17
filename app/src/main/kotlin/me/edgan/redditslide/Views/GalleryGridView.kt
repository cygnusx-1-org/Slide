package me.edgan.redditslide.Views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.assist.ImageScaleType
import com.nostra13.universalimageloader.core.assist.ImageSize
import com.nostra13.universalimageloader.core.display.SimpleBitmapDisplayer
import me.edgan.redditslide.R
import me.edgan.redditslide.Reddit
import me.edgan.redditslide.util.CachedFirstImageBinder
import me.edgan.redditslide.util.GalleryTiles

/**
 * The feed's gallery grid: a Reddit gallery post drawn as square thumbnails instead of a single
 * lead image, shown when [me.edgan.redditslide.SettingValues.galleryGrid] is on.
 *
 * A plain [ViewGroup] rather than a nested RecyclerView. The feed list keeps up to
 * `Constants.FEED_VIEW_CACHE_SIZE` bound rows alive off-screen under a StaggeredGridLayoutManager
 * with `setHasFixedSize(true)`, and there is no shared RecycledViewPool to put a nested list into;
 * with at most [GalleryTiles.MAX_FEED_TILES] cells there is nothing a RecyclerView would buy that
 * would pay for that.
 *
 * The measured height depends only on the width, the span and the cell count — never on a loaded
 * bitmap. That is deliberate: the row's height is settled on its first measure, so an image
 * arriving later cannot resize the card and jump the scroll position.
 */
open class GalleryGridView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
    ViewGroup(context, attrs, defStyle) {

    /** Matches the lead image's options: no fade, kept in memory, RGB_565, decoded to the cell. */
    private val tileOptions: DisplayImageOptions =
        DisplayImageOptions.Builder()
            .resetViewBeforeLoading(false)
            .cacheOnDisk(true)
            .imageScaleType(ImageScaleType.EXACTLY)
            .cacheInMemory(true)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .displayer(SimpleBitmapDisplayer())
            .build()

    private val cornerRadiusPx: Float = dp(CORNER_RADIUS_DP)
    private val gutterPx: Int = GalleryTiles.gutterPx(context)

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SCRIM_COLOR }
    private val overflowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = sp(OVERFLOW_TEXT_SP)
        }

    private var spanCount: Int = 2
    private var tileCount: Int = 0

    /** The "+N" the last cell shows, or 0 when the whole gallery is on screen. */
    private var overflowCount: Int = 0

    /** Text of the overflow badge, held so [dispatchDraw] doesn't format a string per frame. */
    private var overflowLabel: String? = null

    /** See where it is assigned in [bind]. */
    private var rungTargetPx: Int = 0

    /** What a tile tap reports: the gallery index of the cell that was tapped. */
    fun interface OnTileClickListener {
        fun onTileClick(index: Int)
    }

    /**
     * A long press on a tile. Returns whether it was handled, as [View.OnLongClickListener] does.
     *
     * A cell has to carry its own: a clickable child consumes the touch, so the card's long-press
     * handling never sees it, and a tile with no listener of its own turns a long press into an
     * ordinary tap.
     */
    fun interface OnTileLongClickListener {
        fun onTileLongClick(index: Int): Boolean
    }

    /**
     * Bind [tiles] as the grid's cells.
     *
     * [onTileClick] receives the gallery index the cell stands for, which is also the index the
     * gallery viewer opens on — the two agree because [GalleryTiles.imagesFor] and the viewer are
     * fed by the same [me.edgan.redditslide.util.JsonUtil.getGalleryData] traversal.
     */
    fun bind(
        tiles: List<GalleryTiles.Tile>,
        span: Int,
        overflow: Int,
        onTileClick: OnTileClickListener,
        onTileLongClick: OnTileLongClickListener,
    ) {
        spanCount = span.coerceAtLeast(1)
        tileCount = tiles.size
        overflowCount = overflow
        overflowLabel =
            if (overflow > 0) context.getString(R.string.gallery_grid_overflow, overflow) else null

        val tilePx = GalleryTiles.tileWidthPx(context, spanCount)
        val decodeSize = ImageSize(tilePx, tilePx)
        // What the rung was actually chosen against — smaller than the cell under the
        // low-resolution setting, and the yardstick the cached-bitmap check has to use.
        rungTargetPx = GalleryTiles.rungTargetPx(context, spanCount)

        ensureCells(tileCount)
        for (i in 0 until childCount) {
            val cell = getChildAt(i) as ShapeableImageView
            if (i >= tileCount) {
                releaseTile(cell)
                cell.visibility = GONE
                cell.setOnClickListener(null)
                cell.setOnLongClickListener(null)
                continue
            }

            val tile = tiles[i]
            cell.visibility = VISIBLE
            cell.contentDescription =
                context.getString(R.string.gallery_grid_image, tile.index + 1, galleryTotal(tiles))
            cell.setOnClickListener { onTileClick.onTileClick(tile.index) }
            cell.setOnLongClickListener { onTileLongClick.onTileLongClick(tile.index) }
            loadTile(cell, tile.url, decodeSize)
        }
        requestLayout()
        invalidate()
    }

    /** Drop every cell's image and pending load — for a holder being rebound to a non-gallery post. */
    fun clear() {
        for (i in 0 until childCount) {
            val cell = getChildAt(i) as ShapeableImageView
            releaseTile(cell)
            cell.setOnClickListener(null)
            cell.setOnLongClickListener(null)
            cell.visibility = GONE
        }
        tileCount = 0
        overflowCount = 0
        overflowLabel = null
    }

    /**
     * Put [url] into [cell]. The one place a tile reaches the image loader, so a test can render
     * the grid without one — the screenshot suite runs on a stock Application and no network.
     */
    protected open fun loadTile(cell: ImageView, url: String?, decodeSize: ImageSize) {
        releaseTile(cell)
        // Universal Image Loader sizes its decode from the view, and it reads maxWidth/maxHeight to
        // do it — the same hint HeaderImageLinkView gives the lead image. Without it these cells,
        // which are added in code and so carry WRAP_CONTENT layout params, tell it nothing, and it
        // falls back to the whole display: an async (memory-miss) tile then lands in the cache at
        // the screen size beside the tile-sized entry the preloader wrote. Two entries for one
        // tile, and the larger one is the one this decode paid for. No effect on layout — onMeasure
        // gives every cell an EXACTLY spec.
        cell.maxWidth = decodeSize.width
        cell.maxHeight = decodeSize.height
        // Tile-sized, so unlike the full-width lead image a memory miss can afford to decode from
        // disk on this thread rather than swapping the image in later, which reads as a redraw.
        // decodeSize is the same number PhotoLoader warms at, so both share one memory entry.
        CachedFirstImageBinder.display(
            imageLoader(),
            url,
            cell,
            tileOptions,
            null,
            decodeSize,
            true,
            rungTargetPx,
        )
    }

    /**
     * Clear [cell] and cancel anything still loading into it. Always before a bind: this view is
     * recycled, and a load in flight for the previous post would otherwise land in the new cell.
     */
    protected open fun releaseTile(cell: ImageView) {
        imageLoader().cancelDisplayTask(cell)
        cell.setImageDrawable(null)
    }

    private fun imageLoader(): ImageLoader = (context.applicationContext as Reddit).imageLoader

    /** The gallery's real size, which the last cell's "+N" stands in for. */
    private fun galleryTotal(tiles: List<GalleryTiles.Tile>): Int =
        if (overflowCount > 0) tiles.size - 1 + overflowCount else tiles.size

    private fun ensureCells(count: Int) {
        while (childCount < count) {
            val cell = ShapeableImageView(context)
            cell.scaleType = ImageView.ScaleType.CENTER_CROP
            cell.shapeAppearanceModel =
                ShapeAppearanceModel.builder().setAllCornerSizes(cornerRadiusPx).build()
            addView(cell)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (tileCount <= 0 || width <= 0) {
            setMeasuredDimension(width, 0)
            return
        }

        val tile = tileSize(width, spanCount, gutterPx)
        val cellSpec = MeasureSpec.makeMeasureSpec(tile, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) {
            val cell = getChildAt(i)
            if (cell.visibility == GONE) {
                continue
            }
            // Width is set per column in onLayout so the row fills the card exactly; the height is
            // the nominal tile size for every cell, which is what keeps the rows even.
            cell.measure(cellSpec, cellSpec)
        }
        setMeasuredDimension(width, measureHeight(width, tileCount, spanCount, gutterPx))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (tileCount <= 0) {
            return
        }
        val width = r - l
        val tile = tileSize(width, spanCount, gutterPx)
        for (i in 0 until tileCount) {
            val cell = getChildAt(i) ?: continue
            val column = i % spanCount
            val row = i / spanCount
            val left = columnLeft(width, column, spanCount, gutterPx)
            val right = columnRight(width, column, spanCount, gutterPx)
            val top = row * (tile + gutterPx)
            cell.layout(left, top, right, top + tile)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val label = overflowLabel ?: return
        val cell = getChildAt(tileCount - 1) ?: return

        canvas.drawRoundRect(
            cell.left.toFloat(),
            cell.top.toFloat(),
            cell.right.toFloat(),
            cell.bottom.toFloat(),
            cornerRadiusPx,
            cornerRadiusPx,
            scrimPaint,
        )
        // Centre on the cap height rather than the full font box, so the digits sit optically
        // centred in the tile instead of low.
        val metrics = overflowPaint.fontMetrics
        val centerY = (cell.top + cell.bottom) / 2f
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(label, (cell.left + cell.right) / 2f, baseline, overflowPaint)
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    companion object {
        /** Same 2dp rounding the feed's thumbnails use (ShapeAppearanceOverlay.Slide.Corner2dp). */
        private const val CORNER_RADIUS_DP = 2f

        private const val OVERFLOW_TEXT_SP = 20f

        /** ~60% black, dark enough for white digits over any photo. */
        private const val SCRIM_COLOR = 0x99000000.toInt()

        /**
         * The grid's height for a given width — the single place the geometry is defined, so a
         * caller reserving space for the grid and the grid itself cannot disagree.
         */
        @JvmStatic
        fun measureHeight(widthPx: Int, tileCount: Int, span: Int, gutterPx: Int): Int {
            if (tileCount <= 0 || widthPx <= 0 || span <= 0) {
                return 0
            }
            val tile = tileSize(widthPx, span, gutterPx)
            val rows = (tileCount + span - 1) / span
            return rows * tile + (rows - 1) * gutterPx
        }

        private fun tileSize(widthPx: Int, span: Int, gutterPx: Int): Int =
            ((widthPx - (span - 1) * gutterPx) / span).coerceAtLeast(1)

        // Column edges are derived from the full width rather than from a single cell width, so the
        // integer remainder is spread across the columns and the last one still ends on the card's
        // edge. Leaving a 1-2px strip there would show against the card background.
        private fun columnLeft(widthPx: Int, column: Int, span: Int, gutterPx: Int): Int =
            column * (widthPx + gutterPx) / span

        private fun columnRight(widthPx: Int, column: Int, span: Int, gutterPx: Int): Int =
            (column + 1) * (widthPx + gutterPx) / span - gutterPx
    }
}
