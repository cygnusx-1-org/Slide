package me.edgan.redditslide.Views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import androidx.test.core.app.ApplicationProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import me.edgan.redditslide.Flair.Richtext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The measuring half of the richtext flair pill.
 *
 * <p>{@code getSize} and {@code draw} are called separately by the text layout and have to agree, or
 * the pill's rounded rectangle and its contents come out different widths. The two hazards pinned
 * here are the ones {@link RoundedBackgroundSpan} actually has: it measures at full text size and
 * halves the result while drawing at the halved size (so its width is only approximate), and it
 * leaves the condensed-bold typeface and halved size on the shared {@code Paint} for whatever text
 * follows it on the line.
 *
 * <p>No image cache is available in a unit test, so every emoji here falls back to drawing its
 * {@code :alias:} — which is also exactly the state a real cold-cache bind starts in.
 *
 * <p>Robolectric's {@code Paint} measures text by character count rather than by glyph, so widths
 * here are comparable to each other but not proportional to the text size. Assertions are written
 * as comparisons between two measurements for that reason; that the half-size pill really is drawn
 * at half size is a device check, not a unit one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class RichFlairSpanTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    private static Richtext text(String t) {
        final Richtext segment = new Richtext();
        segment.setE("text");
        segment.setT(t);
        return segment;
    }

    private static Richtext emoji(String alias, String url) {
        final Richtext segment = new Richtext();
        segment.setE("emoji");
        segment.setA(alias);
        segment.setU(url);
        return segment;
    }

    private static Paint paintAt(float textSize) {
        final Paint paint = new Paint();
        paint.setTextSize(textSize);
        paint.setTypeface(Typeface.DEFAULT);
        return paint;
    }

    private int measure(List<Richtext> segments, boolean half, Paint paint) {
        return new RichFlairSpan(segments, 0xFF000000, 0xFFFFFFFF, half, context)
                .getSize(paint, "ignored", 0, 7, null);
    }

    @Test
    public void measuringDoesNotLeaveItsTypefaceOnTheSharedPaint() {
        // The Paint belongs to the TextView's layout, not to the span. Mutating it here is how a
        // flair pill ends up restyling the text after it.
        final Paint paint = paintAt(40f);
        final Typeface before = paint.getTypeface();

        measure(Collections.singletonList(text("Discussion")), true, paint);

        assertSame(before, paint.getTypeface());
        assertEquals(40f, paint.getTextSize(), 0.001f);
    }

    @Test
    public void anUnresolvedEmojiMeasuresAsWideAsTheAliasItDraws() {
        // With no bitmap the span draws the alias text, so the width it reports must be the width
        // of that text — otherwise the pill is drawn one size and filled at another.
        final Paint paint = paintAt(40f);

        final int asEmoji = measure(Collections.singletonList(emoji(":pikachu:", "u")), false, paint);
        final int asText = measure(Collections.singletonList(text(":pikachu:")), false, paint);

        assertEquals(asText, asEmoji);
    }

    @Test
    public void theHalfSizePillAlsoRestoresThePaint() {
        // The half branch is where RoundedBackgroundSpan mutates the text size, so it needs the
        // same guarantee as the full-size one. This is the branch the title line uses.
        final Paint paint = paintAt(40f);
        final Typeface before = paint.getTypeface();

        measure(Arrays.asList(emoji(":tag:", "u"), text(" Meta")), true, paint);

        assertSame(before, paint.getTypeface());
        assertEquals(40f, paint.getTextSize(), 0.001f);
    }

    @Test
    public void widthGrowsWithContent() {
        final Paint paint = paintAt(40f);

        final int one = measure(Collections.singletonList(text("Meta")), false, paint);
        final int two = measure(Arrays.asList(text("Meta"), text(" and more")), false, paint);

        assertTrue("adding a segment must widen the pill", two > one);
    }

    @Test
    public void emptyFlairIsJustItsPadding() {
        final Paint paint = paintAt(40f);

        final int empty = measure(Collections.<Richtext>emptyList(), false, paint);
        final int padded = measure(Collections.singletonList(text("")), false, paint);

        assertEquals(empty, padded);
        assertTrue("the pill keeps its padding either side", empty > 0);
    }
}
