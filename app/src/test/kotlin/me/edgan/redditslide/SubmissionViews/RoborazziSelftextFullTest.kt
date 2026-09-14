package me.edgan.redditslide.SubmissionViews

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.fasterxml.jackson.databind.ObjectMapper
import me.edgan.redditslide.Adapters.FullSubmissionViewHolder
import me.edgan.redditslide.R
import me.edgan.redditslide.Reddit
import me.edgan.redditslide.SettingValues
import me.edgan.redditslide.Views.CommentOverflow
import me.edgan.redditslide.Views.MaxHeightImageView
import me.edgan.redditslide.markdown.MarkdownImages
import me.edgan.redditslide.test.RoborazziCapture
import me.edgan.redditslide.test.TestUtils
import me.edgan.redditslide.util.CommentImageUtil
import me.edgan.redditslide.util.SubmissionParser
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
 * Screenshot tests for the comments screen's body: a post's selftext with its own pictures in it.
 *
 * The card above the comments draws no lead image for such a post — the body renders the picture
 * itself — so what matters here is that each picture takes the body's full width at its own aspect
 * ratio and stays where the author put it, between the paragraphs rather than after them. That is
 * the one thing separating a post's pictures from a comment's, which share a constant on-screen
 * area instead.
 *
 * Everything that decides the layout is production code: [FullSubmissionViewHolder] is what turns
 * full-width sizing on, [MarkdownImages.prepare] splits the body into its text runs and picture
 * blocks in the author's order (the new-Reddit renderer, Slide's default), and
 * [CommentImageUtil.sizeSlot] gives each block its box. Only the bytes are stubbed: the overflow is
 * swapped for one that paints a flat colour instead of reaching for an image loader, which a unit
 * test has no application to provide.
 *
 * ```
 *   ./gradlew recordRoborazziWithGPlayDebug    # goldens are written into the source tree
 *   ./gradlew verifyRoborazziWithGPlayDebug
 * ```
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziSelftextFullTest(
    private val caseLabel: String,
    private val fixture: String,
    private val swDp: Int,
    private val themeLabel: String,
    private val themeRes: Int,
) {

    private var savedNoImages = false
    private var savedLowResAlways = false
    private var savedLowResMobile = false
    private var savedCommentImageSize = 0
    private var savedColors: SharedPreferences? = null

    @Before
    fun setUpSettings() {
        savedNoImages = SettingValues.noImages
        savedLowResAlways = SettingValues.lowResAlways
        savedLowResMobile = SettingValues.lowResMobile
        savedCommentImageSize = SettingValues.commentImageSize
        savedColors = Reddit.colors

        SettingValues.noImages = false
        SettingValues.lowResAlways = false
        SettingValues.lowResMobile = false
        SettingValues.commentImageSize = SettingValues.COMMENT_IMAGE_SIZE_MEDIUM
        // Palette reads this through the renderer's spoiler colouring; it is null until the app
        // itself runs, and a null here is an NPE rather than a default.
        Reddit.colors =
            context().getSharedPreferences("selftext-full-colors", Context.MODE_PRIVATE)

        RuntimeEnvironment.setQualifiers("+sw${swDp}dp-w${swDp}dp-h1600dp-port-xxhdpi")
    }

    @After
    fun restoreSettings() {
        SettingValues.noImages = savedNoImages
        SettingValues.lowResAlways = savedLowResAlways
        SettingValues.lowResMobile = savedLowResMobile
        SettingValues.commentImageSize = savedCommentImageSize
        // Declared non-null, but pristine in a test JVM is null: put that back the way the app's
        // own teardown helper does rather than leaving this suite's file behind for the next class.
        val colors = savedColors
        if (colors != null) {
            Reddit.colors = colors
        } else {
            TestUtils.clearRedditColors()
        }
    }

    @Test
    fun capture() {
        val controller = Robolectric.buildActivity(TestActivity::class.java)
        val activity = controller.get()
        activity.setTheme(themeRes)
        // The font-size overlays BaseActivity applies at runtime; the layout asks for their attrs.
        activity.theme.applyStyle(R.style.FontStyle_MediumPost, true)
        activity.theme.applyStyle(R.style.FontStyle_MediumComment, true)
        controller.create()

        val root =
            LayoutInflater.from(activity)
                .inflate(R.layout.submission_fullscreen, FrameLayout(activity), false)
        activity.setContentView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        controller.start().resume().visible()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val submission = submission(fixture)
        val stub = swapInStubOverflow(activity, root, sizesOf(submission))
        // The holder's constructor is what asks for full-width pictures; the golden is only about
        // this path because of that call.
        val holder = FullSubmissionViewHolder(root)
        assertTrue("the holder did not pick up the stubbed overflow", holder.commentOverflow === stub)

        val prepared =
            MarkdownImages.prepare(
                activity,
                submission.selftext,
                submission.dataNode.path("selftext_html").asText(""),
                submission.dataNode,
            )
        MarkdownImages.renderPrepared(
            holder.firstTextView,
            holder.commentOverflow,
            SUBREDDIT,
            prepared,
            null,
        )

        val bodyArea = root.requireViewById<View>(R.id.body_area)
        bodyArea.visibility = View.VISIBLE

        val widthPx = activity.resources.displayMetrics.widthPixels
        bodyArea.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val height = bodyArea.measuredHeight
        assertTrue("the body measured to nothing", height > 0)
        bodyArea.layout(0, 0, widthPx, height)

        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (themeLabel == "dark") Color.BLACK else Color.WHITE)
        bodyArea.draw(Canvas(bitmap))
        RoborazziCapture.captureRoboImage(
            bitmap,
            "src/test/screenshots/selftext_full_${caseLabel}_${themeLabel}_sw${swDp}dp.png",
        )
    }

    /**
     * Replaces the inflated overflow with one that paints its pictures instead of loading them,
     * keeping the id, layout params and padding the layout gave it so nothing about the geometry
     * comes from this test.
     */
    private fun swapInStubOverflow(
        activity: AppCompatActivity,
        root: View,
        sizes: Map<String, IntArray>,
    ): StubOverflow {
        val real = root.requireViewById<CommentOverflow>(R.id.commentOverflow)
        val parent = real.parent as ViewGroup
        val index = parent.indexOfChild(real)
        val params = real.layoutParams
        val stub = StubOverflow(activity, sizes)
        stub.id = R.id.commentOverflow
        stub.orientation = LinearLayout.VERTICAL
        stub.setPaddingRelative(
            real.paddingStart,
            real.paddingTop,
            real.paddingEnd,
            real.paddingBottom,
        )
        stub.visibility = real.visibility
        parent.removeViewAt(index)
        parent.addView(stub, index, params)
        return stub
    }

    /** media_metadata id -> the picture's real pixel size, which is all the sizing needs. */
    private fun sizesOf(submission: Submission): Map<String, IntArray> {
        val sizes = HashMap<String, IntArray>()
        val mediaMetadata = submission.dataNode.path("media_metadata")
        for (id in mediaMetadata.fieldNames()) {
            val source = mediaMetadata.path(id).path("s")
            sizes[id] = intArrayOf(source.path("x").asInt(), source.path("y").asInt())
        }
        assertTrue("$fixture carries no media_metadata", sizes.isNotEmpty())
        return sizes
    }

    private fun submission(name: String): Submission {
        val stream =
            RoborazziSelftextFullTest::class
                .java
                .classLoader!!
                .getResourceAsStream("submissions/$name.json")
        assertNotNull(name, stream)
        return stream!!.use { Submission(MAPPER.readTree(it)) }
    }

    private fun context(): Context = RuntimeEnvironment.getApplication()

    /**
     * A [CommentOverflow] whose image blocks are flat colour at the picture's real size, sized
     * through the production [CommentImageUtil.sizeSlot] with the full-width flag the holder set.
     * Everything else — text blocks, tables, rules, margins, order — is the real thing.
     */
    private class StubOverflow(ctx: Context, private val sizes: Map<String, IntArray>) :
        CommentOverflow(ctx) {

        private var fullWidth = false

        override fun setFullWidthImages(fullWidth: Boolean) {
            this.fullWidth = fullWidth
            super.setFullWidthImages(fullWidth)
        }

        override fun addBlock(
            block: String,
            subreddit: String,
            click: View.OnClickListener?,
            longClick: View.OnLongClickListener?,
        ) {
            if (!block.startsWith(SubmissionParser.IMAGE_BLOCK_PREFIX)) {
                super.addBlock(block, subreddit, click, longClick)
                return
            }
            val url = block.substring(SubmissionParser.IMAGE_BLOCK_PREFIX.length)
            val size = sizes.entries.firstOrNull { url.contains(it.key) }?.value
            assertNotNull("no media_metadata entry for $url", size)

            val imageView = MaxHeightImageView(getContext())
            val params =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            params.setMargins(0, IMAGE_BLOCK_MARGIN_PX, 0, IMAGE_BLOCK_MARGIN_PX)
            imageView.layoutParams = params
            imageView.setImageDrawable(ColorDrawable(IMAGE_COLORS[childCount % IMAGE_COLORS.size]))
            CommentImageUtil.sizeSlot(imageView, size!![0], size[1], fullWidth)
            addView(imageView)
        }

        private companion object {
            /** The margins CommentOverflow gives a real image block. */
            const val IMAGE_BLOCK_MARGIN_PX = 16

            /** Distinct per block, so a golden shows where one picture ends and the next begins. */
            val IMAGE_COLORS =
                intArrayOf(0xFF37474F.toInt(), 0xFF78909C.toInt(), 0xFF546E7A.toInt())
        }
    }

    class TestActivity : AppCompatActivity()

    companion object {
        private val MAPPER = ObjectMapper()

        /** The fixtures' subreddit; it only reaches link and spoiler theming. */
        private const val SUBREDDIT = "test"

        /** The same buckets as the submission-card goldens, so the sets stay comparable. */
        private val SMALLEST_WIDTHS_DP = intArrayOf(411, 443, 448, 527, 600, 934)

        private val THEMES: Array<Array<Any>> =
            arrayOf(arrayOf("dark", R.style.Theme_DARK), arrayOf("light", R.style.Theme_LIGHT))

        /**
         * r/test t3_1wfuqvm writes text, picture, text — the order that is lost if the pictures are
         * drawn after the words. t3_1wfusrn opens with two pictures of different shapes and ends
         * with a line of text.
         */
        private val CASES: Array<Array<Any>> =
            arrayOf(
                arrayOf("text_image_text", "selftext_text_image_text"),
                arrayOf("image_image_text", "selftext_image_image_text"),
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{3}_sw{2}dp")
        fun cases(): List<Array<Any>> {
            val cases = ArrayList<Array<Any>>()
            for (case in CASES) {
                for (theme in THEMES) {
                    for (dp in SMALLEST_WIDTHS_DP) {
                        cases.add(arrayOf(case[0], case[1], dp, theme[0], theme[1]))
                    }
                }
            }
            return cases
        }
    }
}
