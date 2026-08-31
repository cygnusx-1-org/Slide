package me.edgan.redditslide.Views

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import me.edgan.redditslide.Flair.RichFlairParser
import me.edgan.redditslide.Flair.Richtext
import me.edgan.redditslide.R
import me.edgan.redditslide.test.RoborazziCapture
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot goldens for the flair pill on a submission title.
 *
 * These exist because the pill is drawn, not laid out: [RichFlairSpan] and [RoundedBackgroundSpan]
 * are `ReplacementSpan`s that paint their own background, text and images, so nothing about them
 * shows up in a view hierarchy dump and no assertion on bounds would catch a pill drawn at the
 * wrong width. Native graphics mode means the text here is measured and rasterised for real.
 *
 * The cases mirror the shapes Reddit actually sends, taken from live listings: a legacy text flair,
 * the same flair in richtext form, an emoji followed by text (r/Helldivers' `:BRASCH:TIPS /
 * TACTICS`), several emoji in a row (r/Genshin_Impact author flair), and an emoji-only flair with
 * no text at all (r/ffxiv's `:moogle:`).
 *
 * ```
 *   ./gradlew recordRoborazziWithGPlayDebug    # goldens are written into the source tree
 *   ./gradlew verifyRoborazziWithGPlayDebug
 * ```
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoborazziFlairPillTest(
    private val caseLabel: String,
    private val themeLabel: String,
    private val themeRes: Int,
) {

    @Test
    fun capture() {
        RuntimeEnvironment.setQualifiers("+sw411dp-w411dp-h1600dp-port-xxhdpi")

        val bitmap = render(caseLabel)

        RoborazziCapture.captureRoboImage(
            bitmap,
            "src/test/screenshots/flair_pill_${caseLabel}_$themeLabel.png",
        )
    }

    /**
     * The property the whole design rests on: a flair with no emoji must go on being drawn by
     * [RoundedBackgroundSpan], so adopting richtext moves nothing. Same title, same flair, one built
     * the legacy way and one from the richtext array — the two have to be the same pixels, not
     * merely similar ones.
     */
    @Test
    fun aTextOnlyRichtextFlairIsPixelIdenticalToTheLegacyPill() {
        RuntimeEnvironment.setQualifiers("+sw411dp-w411dp-h1600dp-port-xxhdpi")

        assertArrayEquals(pixels(render("legacy_text")), pixels(render("richtext_text")))
    }

    private fun render(case: String): Bitmap {
        val controller = Robolectric.buildActivity(TestActivity::class.java)
        val activity = controller.get()
        activity.setTheme(themeRes)
        controller.create()

        val fontColor = attr(activity, R.attr.fontColor)
        val background = attr(activity, R.attr.activity_background)

        val text = SpannableStringBuilder("A post title")
        text.append(" ")
        text.append(chipFor(case, fontColor, background, activity))

        val view = TextView(activity)
        view.textSize = 18f
        view.setTextColor(fontColor)
        view.setText(text, TextView.BufferType.SPANNABLE)
        activity.setContentView(view)
        controller.start().resume().visible()

        val widthPx = activity.resources.displayMetrics.widthPixels
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        assertTrue("the title measured to nothing", view.measuredHeight > 0)
        view.layout(0, 0, widthPx, view.measuredHeight)

        val bitmap = Bitmap.createBitmap(widthPx, view.measuredHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (themeLabel == "dark") Color.BLACK else Color.WHITE)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun chipFor(
        case: String,
        fontColor: Int,
        background: Int,
        activity: AppCompatActivity,
    ): SpannableStringBuilder =
        when (case) {
            // What Slide draws today for a flair with no richtext at all.
            "legacy_text" -> legacyChip("Discussion", fontColor, background, activity)
            // The same flair as Reddit sends it now. Must not reach RichFlairSpan.
            "richtext_text" ->
                segmentsChip(listOf(text("Discussion")), fontColor, background, activity)
            // r/Helldivers: an emoji then a text run.
            "emoji_then_text" ->
                segmentsChip(
                    listOf(emoji(":BRASCH:"), text("TIPS / TACTICS")),
                    fontColor,
                    background,
                    activity,
                )
            // r/Genshin_Impact author flair: two emoji run together, then text.
            "two_emoji_then_text" ->
                segmentsChip(
                    listOf(emoji(":odette:"), emoji(":alyosha:"), text(" Oh no! Anyways...")),
                    fontColor,
                    background,
                    activity,
                )
            // r/ffxiv: nothing but an emoji. Today this draws the literal ":moogle:".
            "emoji_only" ->
                segmentsChip(listOf(emoji(":moogle:")), fontColor, background, activity)
            // The cold-cache state, before any image has arrived: the alias is drawn instead.
            "unresolved" ->
                SpannableStringBuilder(" " + RichFlairParser.plainText(
                    listOf(emoji(":BRASCH:"), text("TIPS / TACTICS"))
                ) + " ").also {
                    it.setSpan(
                        RichFlairSpan(
                            listOf(emoji(":BRASCH:"), text("TIPS / TACTICS")),
                            fontColor,
                            background,
                            true,
                            activity,
                        ),
                        0,
                        it.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            else -> throw IllegalArgumentException(case)
        }

    private fun text(value: String): Richtext =
        Richtext().apply {
            e = "text"
            t = value
        }

    /** The URL only has to be non-empty — the stub ignores it — but it is what makes it an emoji. */
    private fun emoji(alias: String): Richtext =
        Richtext().apply {
            e = "emoji"
            a = alias
            u = "https://emoji.example/" + alias.trim(':')
        }

    private fun legacyChip(
        label: String,
        fontColor: Int,
        background: Int,
        activity: AppCompatActivity,
    ): SpannableStringBuilder {
        val chip = SpannableStringBuilder(" $label ")
        chip.setSpan(
            RoundedBackgroundSpan(fontColor, background, true, activity),
            0,
            chip.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return chip
    }

    /** Goes through the same branch SubmissionCache does: emoji-bearing flairs get the new span. */
    private fun segmentsChip(
        segments: List<Richtext>,
        fontColor: Int,
        background: Int,
        activity: AppCompatActivity,
    ): SpannableStringBuilder {
        if (!RichFlairParser.hasEmoji(segments)) {
            return legacyChip(RichFlairParser.plainText(segments), fontColor, background, activity)
        }

        val chip =
            SpannableStringBuilder(" " + RichFlairParser.plainText(segments) + " ")
        chip.setSpan(
            StubbedFlairSpan(segments, fontColor, background, true, activity),
            0,
            chip.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return chip
    }

    private fun pixels(bitmap: Bitmap): IntArray {
        val out = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(out, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return out
    }

    private fun attr(activity: AppCompatActivity, attr: Int): Int {
        val value = android.util.TypedValue()
        activity.theme.resolveAttribute(attr, value, true)
        return value.data
    }

    /** Draws a recognisable stand-in rather than reaching for the image loader. */
    private class StubbedFlairSpan(
        segments: List<Richtext>,
        textColor: Int,
        backgroundColor: Int,
        half: Boolean,
        context: android.content.Context,
    ) : RichFlairSpan(segments, textColor, backgroundColor, half, context) {
        override fun lookup(url: String): Bitmap? = STUB
    }

    class TestActivity : AppCompatActivity()

    companion object {
        /**
         * A flat wide-ish glyph stand-in. Deliberately not square: it pins that the pill reserves
         * width from the bitmap's aspect ratio rather than assuming a square emoji.
         */
        private val STUB: Bitmap by lazy {
            // Built on first use, not at class init: Robolectric has to have stood the graphics
            // environment up before a Bitmap can be allocated at all.
            Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888).also { stub ->
                val canvas = Canvas(stub)
                canvas.drawColor(0xFFE53935.toInt())
                val paint = Paint().apply { color = 0xFFFFFFFF.toInt() }
                canvas.drawCircle(24f, 16f, 10f, paint)
            }
        }

        private val CASES =
            arrayOf(
                "legacy_text",
                "richtext_text",
                "emoji_then_text",
                "two_emoji_then_text",
                "emoji_only",
                "unresolved",
            )

        private val THEMES: Array<Array<Any>> =
            arrayOf(arrayOf("dark", R.style.Theme_DARK), arrayOf("light", R.style.Theme_LIGHT))

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> {
            val cases = ArrayList<Array<Any>>()
            for (case in CASES) {
                for (theme in THEMES) {
                    cases.add(arrayOf(case, theme[0], theme[1]))
                }
            }
            return cases
        }
    }
}
