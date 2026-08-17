package me.edgan.redditslide.SubmissionViews

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import me.edgan.redditslide.R
import me.edgan.redditslide.SettingValues
import me.edgan.redditslide.Views.CreateCardView
import me.edgan.redditslide.test.RoborazziCapture
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the small content tag ("IMAGE", "GALLERY") in each of its three positions.
 *
 * The other card goldens inflate their layout directly, which never runs
 * [CreateCardView.doHideObjects] — and that is the only thing that applies the tag's position. Under
 * a static inflate the tag therefore always sits at its raw XML anchors, so those goldens say
 * nothing about the setting. These build the card through [CreateCardView.CreateView], the way the
 * adapters do, so the rules under test are actually present.
 *
 * Each case then puts the card through the gallery-grid recycle: the anchors move onto the grid
 * while a gallery is shown, and back onto the lead image when the view is reused for something else.
 * That round trip is where the tag's rules can be corrupted — losing the horizontal anchor drags the
 * tag to the wrong edge, and gaining a second vertical anchor stretches a `wrap_content` TextView
 * down the whole image — and neither shows up without both halves of the trip.
 *
 * Loading is not involved: the lead image is a flat colour at a fixed aspect ratio, so what the
 * goldens pin is geometry.
 *
 * ```
 *   ./gradlew recordRoborazziWithGPlayDebug    # goldens are written into the source tree
 *   ./gradlew verifyRoborazziWithGPlayDebug
 * ```
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziContentTagTest(
    private val tagLabel: String,
    private val smallTag: Int,
    private val swDp: Int,
    private val themeLabel: String,
    private val themeRes: Int,
) {

    /**
     * [CreateCardView] reads app-wide statics that no test may leave changed — several suites share
     * one JVM, and a stray `defaultCardView` would follow this one into the next.
     */
    private lateinit var savedCardView: CreateCardView.CardEnum
    private var savedSmallTag = 0
    private var savedMiddleImage = false
    private var savedBigPicEnabled = false
    private var savedActionbarVisible = false

    @Before
    fun setUpSettings() {
        // Never null in the app (setAllValues assigns it); under a stock Application it is, so
        // fall back to the value this test is about to install.
        savedCardView = SettingValues.defaultCardView ?: CreateCardView.CardEnum.LARGE
        savedSmallTag = SettingValues.smallTag
        savedMiddleImage = SettingValues.middleImage
        savedBigPicEnabled = SettingValues.bigPicEnabled
        savedActionbarVisible = SettingValues.actionbarVisible

        SettingValues.defaultCardView = CreateCardView.CardEnum.LARGE
        SettingValues.smallTag = smallTag
        SettingValues.middleImage = false
        SettingValues.bigPicEnabled = true
        SettingValues.actionbarVisible = true

        RuntimeEnvironment.setQualifiers("+sw${swDp}dp-w${swDp}dp-h1600dp-port-xxhdpi")
    }

    @After
    fun restoreSettings() {
        SettingValues.defaultCardView = savedCardView
        SettingValues.smallTag = savedSmallTag
        SettingValues.middleImage = savedMiddleImage
        SettingValues.bigPicEnabled = savedBigPicEnabled
        SettingValues.actionbarVisible = savedActionbarVisible
    }

    @Test
    fun capture() {
        val controller = Robolectric.buildActivity(TestActivity::class.java)
        val activity = controller.get()
        activity.setTheme(themeRes)
        // BaseActivity layers the font-size overlays onto the theme at runtime, and the submission
        // layouts reference their attrs (?attr/font_cardtitle and friends). Without them
        // TitleTextView fails to inflate, exactly as in RoborazziLayoutTest.
        activity.theme.applyStyle(R.style.FontStyle_MediumPost, true)
        activity.theme.applyStyle(R.style.FontStyle_MediumComment, true)
        controller.create()

        val root = FrameLayout(activity)
        activity.setContentView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        controller.start().resume().visible()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val card = CreateCardView.CreateView(root)
        root.addView(card)

        val header = card.requireViewById<HeaderImageLinkView>(R.id.headerimage)
        // A flat colour standing in for a landscape photo. setAspectRatio is what the real bind uses
        // to reserve the height before the image arrives, so the tag's anchors resolve against the
        // same box they would in the app.
        header.backdrop.setImageDrawable(ColorDrawable(0xFF37474F.toInt()))
        header.backdrop.setAspectRatio(0.6)

        // The recycle: anchors move onto the grid for a gallery post, then back when this view is
        // reused for one that has no grid. Exactly the sequence a feed performs while scrolling.
        header.galleryGrid.visibility = View.VISIBLE
        header.backdrop.visibility = View.GONE
        header.anchorOverlays(R.id.leadimage, R.id.gallerygrid)
        header.galleryGrid.visibility = View.GONE
        header.backdrop.visibility = View.VISIBLE
        header.anchorOverlays(R.id.gallerygrid, R.id.leadimage)

        val cardWidthPx = activity.resources.displayMetrics.widthPixels
        header.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val height = header.measuredHeight
        assertTrue("the header measured to nothing", height > 0)
        header.layout(0, 0, cardWidthPx, height)

        val bitmap = Bitmap.createBitmap(cardWidthPx, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (themeLabel == "dark") Color.BLACK else Color.WHITE)
        header.draw(Canvas(bitmap))
        RoborazziCapture.captureRoboImage(
            bitmap,
            "src/test/screenshots/content_tag_${tagLabel}_${themeLabel}_sw${swDp}dp.png",
        )
    }

    class TestActivity : AppCompatActivity()

    companion object {
        /** The same buckets as the submission-card goldens, so the sets stay comparable. */
        private val SMALLEST_WIDTHS_DP = intArrayOf(411, 443, 448, 527, 600, 934)

        private val THEMES: Array<Array<Any>> =
            arrayOf(arrayOf("dark", R.style.Theme_DARK), arrayOf("light", R.style.Theme_LIGHT))

        /** The three values of the "Smaller content tag" setting: hidden, top-right, bottom-right. */
        private val TAG_POSITIONS: Array<Array<Any>> =
            arrayOf(arrayOf("off", 0), arrayOf("top", 1), arrayOf("bottom", 2))

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{3}_sw{2}dp")
        fun cases(): List<Array<Any>> {
            val cases = ArrayList<Array<Any>>()
            for (position in TAG_POSITIONS) {
                for (theme in THEMES) {
                    for (dp in SMALLEST_WIDTHS_DP) {
                        cases.add(arrayOf(position[0], position[1], dp, theme[0], theme[1]))
                    }
                }
            }
            return cases
        }
    }
}
