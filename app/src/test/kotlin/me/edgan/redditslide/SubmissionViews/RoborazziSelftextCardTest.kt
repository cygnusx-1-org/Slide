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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fasterxml.jackson.databind.ObjectMapper
import me.edgan.redditslide.R
import me.edgan.redditslide.SettingValues
import me.edgan.redditslide.SpoilerRobotoTextView
import me.edgan.redditslide.SubmissionCache
import me.edgan.redditslide.Views.CreateCardView
import me.edgan.redditslide.test.RoborazziCapture
import me.edgan.redditslide.util.PhotoLoader
import net.dean.jraw.models.Submission
import org.junit.After
import org.junit.Assert.assertNotNull
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
 * Screenshot tests for a self post's feed card and where its words sit relative to its picture.
 *
 * Two kinds of picture. One came out of the selftext itself — a post that pastes a reddit image
 * into its body, which Reddit publishes no `preview` node for — and keeps the body's order: a body
 * that opens with words keeps its preview above the image in `@id/body`, and a body that opens
 * with the picture moves it below, into the `@id/body_below` seat that only
 * `submission_largecard_middle` has. The other is a preview Reddit built from a link in the body,
 * which is the post's picture rather than a line of it, so the card leads with it and the words go
 * below — the order the comments screen draws — wherever the link sat. The last case is the
 * image-first post with "Show selftext" off, which has to leave the picture and the action row
 * exactly where they were.
 *
 * The picture is a flat colour at the fixture's real aspect ratio: what these goldens are about is
 * the geometry, which the card settles before any image arrives. Everything that decides it is the
 * real code — [PhotoLoader.getSelftextImagePreview] or the `preview` node for the shape,
 * [SubmissionCache.selftextPreviewBelowLeadImage] for the seat and
 * [SubmissionCache.getSelftextPreview] for the words — so only the setters a bind would call are
 * the test's own.
 *
 * ```
 *   ./gradlew recordRoborazziWithGPlayDebug    # goldens are written into the source tree
 *   ./gradlew verifyRoborazziWithGPlayDebug
 * ```
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziSelftextCardTest(
    private val caseLabel: String,
    private val fixture: String,
    private val selftextEnabled: Boolean,
    private val swDp: Int,
    private val themeLabel: String,
    private val themeRes: Int,
) {

    /**
     * [CreateCardView] reads app-wide statics that no test may leave changed — several suites share
     * one JVM, and a stray `defaultCardView` would follow this one into the next.
     */
    private lateinit var savedCardView: CreateCardView.CardEnum
    private var savedMiddleImage = false
    private var savedBigPicEnabled = false
    private var savedBigPicCropped = false
    private var savedBigPicLetterboxed = false
    private var savedNoThumbnails = false
    private var savedBigThumbnails = false
    private var savedSwitchThumb = false
    private var savedSmallTag = 0
    private var savedActionbarVisible = false
    private var savedActionbarTap = false
    private var savedCardText = false
    private var savedCardTextEllipsize = false

    @Before
    fun setUpSettings() {
        savedCardView = SettingValues.defaultCardView ?: CreateCardView.CardEnum.LARGE
        savedMiddleImage = SettingValues.middleImage
        savedBigPicEnabled = SettingValues.bigPicEnabled
        savedBigPicCropped = SettingValues.bigPicCropped
        savedBigPicLetterboxed = SettingValues.bigPicLetterboxed
        savedNoThumbnails = SettingValues.noThumbnails
        savedBigThumbnails = SettingValues.bigThumbnails
        savedSwitchThumb = SettingValues.switchThumb
        savedSmallTag = SettingValues.smallTag
        savedActionbarVisible = SettingValues.actionbarVisible
        savedActionbarTap = SettingValues.actionbarTap
        savedCardText = SettingValues.cardText
        savedCardTextEllipsize = SettingValues.cardTextEllipsize

        // The default picture mode: a big card with the image between the title and the action row,
        // which is the only layout with two seats for the selftext preview.
        SettingValues.defaultCardView = CreateCardView.CardEnum.LARGE
        SettingValues.middleImage = true
        SettingValues.bigPicEnabled = true
        SettingValues.bigPicCropped = false
        SettingValues.bigPicLetterboxed = false
        SettingValues.noThumbnails = false
        SettingValues.bigThumbnails = false
        SettingValues.switchThumb = false
        SettingValues.smallTag = 0
        SettingValues.actionbarVisible = true
        SettingValues.actionbarTap = false
        SettingValues.cardText = selftextEnabled
        SettingValues.cardTextEllipsize = false

        RuntimeEnvironment.setQualifiers("+sw${swDp}dp-w${swDp}dp-h1600dp-port-xxhdpi")
    }

    @After
    fun restoreSettings() {
        SettingValues.defaultCardView = savedCardView
        SettingValues.middleImage = savedMiddleImage
        SettingValues.bigPicEnabled = savedBigPicEnabled
        SettingValues.bigPicCropped = savedBigPicCropped
        SettingValues.bigPicLetterboxed = savedBigPicLetterboxed
        SettingValues.noThumbnails = savedNoThumbnails
        SettingValues.bigThumbnails = savedBigThumbnails
        SettingValues.switchThumb = savedSwitchThumb
        SettingValues.smallTag = savedSmallTag
        SettingValues.actionbarVisible = savedActionbarVisible
        SettingValues.actionbarTap = savedActionbarTap
        SettingValues.cardText = savedCardText
        SettingValues.cardTextEllipsize = savedCardTextEllipsize
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

        val submission = submission(fixture)
        card.requireViewById<TextView>(R.id.title).text = submission.title
        val leadIsInlineImage = bindLeadImage(card, submission)
        bindSelftextPreview(card, submission, leadIsInlineImage)

        val cardWidthPx = activity.resources.displayMetrics.widthPixels
        card.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val height = card.measuredHeight
        assertTrue("the card measured to nothing", height > 0)
        card.layout(0, 0, cardWidthPx, height)

        val bitmap = Bitmap.createBitmap(cardWidthPx, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (themeLabel == "dark") Color.BLACK else Color.WHITE)
        card.draw(Canvas(bitmap))
        RoborazziCapture.captureRoboImage(
            bitmap,
            "src/test/screenshots/selftext_card_${caseLabel}_${themeLabel}_sw${swDp}dp.png",
        )
    }

    /**
     * The post's picture: Reddit's `preview` of a link in the body where there is one, and the
     * picture the body pasted in otherwise, the way [HeaderImageLinkView.doImageAndText] prefers
     * them. A flat colour at the real ratio, because the height the card reserves comes from the
     * ratio alone — which is how the bind reserves it, before any bytes arrive.
     *
     * @return whether the picture came out of the body, which is what the bind reads back through
     *   [HeaderImageLinkView.getSelftextInlineImageUrl]
     */
    private fun bindLeadImage(card: View, submission: Submission): Boolean {
        val previewSource =
            submission.dataNode.path("preview").path("images").path(0).path("source")
        val inline: Boolean
        val width: Int
        val height: Int
        if (previewSource.isObject) {
            inline = false
            width = previewSource.path("width").asInt()
            height = previewSource.path("height").asInt()
        } else {
            val lead = PhotoLoader.getSelftextImagePreview(submission.dataNode, Int.MAX_VALUE)
            assertNotNull("$fixture has neither a preview nor an inlined image", lead)
            inline = true
            width = lead!!.width
            height = lead.height
        }
        assertTrue("$fixture gives its picture no size", width > 0 && height > 0)
        val header = card.requireViewById<HeaderImageLinkView>(R.id.headerimage)
        header.visibility = View.VISIBLE
        header.backdrop.setImageDrawable(ColorDrawable(LEAD_IMAGE_COLOR))
        header.backdrop.setAspectRatio(height.toDouble() / width)
        // What doImageAndText writes into the overlay for a self post.
        card.requireViewById<TextView>(R.id.textimage).text = SELFTEXT_TAG
        card.requireViewById<TextView>(R.id.subtextimage).text = submission.domain
        return inline
    }

    /**
     * The preview, in the seat the bind would choose —
     * [SubmissionCache.selftextPreviewBelowLeadImage] is the bind's own rule — and nowhere at all
     * with "Show selftext" off. The seat not in use is gone either way — a recycled card would
     * otherwise show the previous post's words there.
     */
    private fun bindSelftextPreview(
        card: View,
        submission: Submission,
        leadIsInlineImage: Boolean,
    ) {
        val body = card.requireViewById<SpoilerRobotoTextView>(R.id.body)
        val bodyBelow = card.requireViewById<SpoilerRobotoTextView>(R.id.body_below)
        val preview =
            if (SettingValues.isSelftextEnabled(null)) {
                SubmissionCache.getSelftextPreview(submission, leadIsInlineImage)
            } else {
                ""
            }
        if (preview.isEmpty()) {
            body.visibility = View.GONE
            bodyBelow.visibility = View.GONE
            return
        }
        val below =
            SubmissionCache.selftextPreviewBelowLeadImage(submission, true, leadIsInlineImage)
        val seat = if (below) bodyBelow else body
        val unused = if (seat === body) bodyBelow else body
        seat.setTextHtml(preview, "none ")
        seat.visibility = View.VISIBLE
        unused.visibility = View.GONE
    }

    private fun submission(name: String): Submission {
        val stream =
            RoborazziSelftextCardTest::class
                .java
                .classLoader!!
                .getResourceAsStream("submissions/$name.json")
        assertNotNull(name, stream)
        return stream!!.use { Submission(MAPPER.readTree(it)) }
    }

    class TestActivity : AppCompatActivity()

    companion object {
        private val MAPPER = ObjectMapper()

        /** A flat stand-in for the photograph; the goldens are about the box, not the picture. */
        private const val LEAD_IMAGE_COLOR = 0xFF37474F.toInt()

        /** What ContentType.getContentDescription returns for a self post. */
        private const val SELFTEXT_TAG = "Selftext"

        /** The same buckets as the submission-card goldens, so the sets stay comparable. */
        private val SMALLEST_WIDTHS_DP = intArrayOf(411, 443, 448, 527, 600, 934)

        private val THEMES: Array<Array<Any>> =
            arrayOf(arrayOf("dark", R.style.Theme_DARK), arrayOf("light", R.style.Theme_LIGHT))

        /**
         * r/test t3_1wfuohp ("Test Image") opens with words; t3_1wfupwu ("Image Text") opens with
         * the picture. r/copypasta t3_1w9nv8u is a paragraph and then a YouTube link, and its
         * picture is Reddit's preview of that link: the words go under it although the body opens
         * with them. The last case is the image-first post again with "Show selftext" off, which
         * is the state the picture and the action row must not move between.
         */
        private val CASES: Array<Array<Any>> =
            arrayOf(
                arrayOf("text_first", "selftext_text_image", true),
                arrayOf("image_first", "selftext_image_text", true),
                arrayOf("link_preview", "selftext_linkpreview_text_then_link", true),
                arrayOf("no_text", "selftext_image_text", false),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{4}_sw{3}dp")
        fun cases(): List<Array<Any>> {
            val cases = ArrayList<Array<Any>>()
            for (case in CASES) {
                for (theme in THEMES) {
                    for (dp in SMALLEST_WIDTHS_DP) {
                        cases.add(arrayOf(case[0], case[1], case[2], dp, theme[0], theme[1]))
                    }
                }
            }
            return cases
        }
    }
}
