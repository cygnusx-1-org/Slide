package me.edgan.redditslide.Views

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.nostra13.universalimageloader.core.assist.ImageSize
import me.edgan.redditslide.R
import me.edgan.redditslide.test.RoborazziCapture
import me.edgan.redditslide.util.GalleryTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the feed's gallery grid.
 *
 * A static inflate would say nothing: [GalleryGridView] has no children until it is bound, and its
 * whole point is the geometry it derives at bind time from the gallery's size. So these bind the
 * real view and capture what it lays out.
 *
 * Loading is stubbed at the seam the view exposes for it, and each cell is filled with a flat
 * colour instead. Nothing here is about the photographs: the goldens pin the column count per
 * gallery size, the gutters, the corner rounding, the square cells, and the "+N" overflow badge —
 * all of which are settled before an image would arrive, which is exactly the property that keeps a
 * gallery card from resizing under the scroll once its images load.
 *
 * ```
 *   ./gradlew recordRoborazziWithGPlayDebug    # goldens are written into the source tree
 *   ./gradlew verifyRoborazziWithGPlayDebug
 * ```
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziGalleryGridTest(
    private val gallerySize: Int,
    private val swDp: Int,
    private val themeLabel: String,
    private val themeRes: Int,
) {

    @Test
    fun capture() {
        RuntimeEnvironment.setQualifiers("+sw${swDp}dp-w${swDp}dp-h1600dp-port-xxhdpi")

        val controller = Robolectric.buildActivity(TestActivity::class.java)
        val activity = controller.get()
        activity.setTheme(themeRes)
        controller.create()

        val grid = StubbedGalleryGridView(activity)
        activity.setContentView(
            grid,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        controller.start().resume().visible()

        val span = GalleryTiles.spanFor(gallerySize)
        val tiles =
            (0 until GalleryTiles.tileCount(gallerySize)).map {
                GalleryTiles.Tile(it, "https://example.invalid/$it.jpg")
            }
        grid.bind(tiles, span, GalleryTiles.overflowCount(gallerySize), {}, { true })

        // The card's own width, not the screen's: the CardView these sit in has a 4dp margin, which
        // is the inset GalleryTiles.tileWidthPx assumes.
        val cardWidthPx =
            activity.resources.displayMetrics.widthPixels -
                Math.round(8f * activity.resources.displayMetrics.density)
        grid.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val height = grid.measuredHeight
        assertTrue("the grid measured to nothing", height > 0)
        // The reserved height has to be a pure function of the shape, or a card would resize once
        // its tiles load. Same numbers, computed without the view.
        assertEquals(
            GalleryTiles.gutterPx(activity).let { gutter ->
                GalleryGridView.measureHeight(cardWidthPx, tiles.size, span, gutter)
            },
            height,
        )
        grid.layout(0, 0, cardWidthPx, height)

        val bitmap = Bitmap.createBitmap(cardWidthPx, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (themeLabel == "dark") Color.BLACK else Color.WHITE)
        grid.draw(Canvas(bitmap))
        RoborazziCapture.captureRoboImage(
            bitmap,
            "src/test/screenshots/gallery_grid_${gallerySize}_${themeLabel}_sw${swDp}dp.png",
        )
    }

    /** Fills each cell with a flat colour rather than reaching for the image loader. */
    private class StubbedGalleryGridView(activity: AppCompatActivity) : GalleryGridView(activity) {
        override fun loadTile(cell: ImageView, url: String?, decodeSize: ImageSize) {
            cell.setImageDrawable(ColorDrawable(CELL_COLORS[indexOfChild(cell) % CELL_COLORS.size]))
        }

        override fun releaseTile(cell: ImageView) {
            cell.setImageDrawable(null)
        }

        private companion object {
            /** Distinct per cell so the goldens show each tile's bounds, not one flat block. */
            val CELL_COLORS =
                intArrayOf(0xFF37474F.toInt(), 0xFF546E7A.toInt(), 0xFF78909C.toInt(), 0xFF90A4AE.toInt())
        }
    }

    class TestActivity : AppCompatActivity()

    companion object {
        /** The same buckets as the submission-card goldens, so the sets stay comparable. */
        private val SMALLEST_WIDTHS_DP = intArrayOf(411, 443, 448, 527, 600, 934)

        private val THEMES: Array<Array<Any>> =
            arrayOf(arrayOf("dark", R.style.Theme_DARK), arrayOf("light", R.style.Theme_LIGHT))

        /**
         * One case per gallery size that changes the shape: two side by side, three as 2 + 1 with
         * the third tile alone on its row, a full 2x2, and a nine that has to fold into 2x2 with a
         * "+6".
         */
        private val GALLERY_SIZES = intArrayOf(2, 3, 4, 9)

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{2}_sw{1}dp")
        fun cases(): List<Array<Any>> {
            val cases = ArrayList<Array<Any>>()
            for (size in GALLERY_SIZES) {
                for (theme in THEMES) {
                    for (dp in SMALLEST_WIDTHS_DP) {
                        cases.add(arrayOf(size, dp, theme[0], theme[1]))
                    }
                }
            }
            return cases
        }
    }
}
