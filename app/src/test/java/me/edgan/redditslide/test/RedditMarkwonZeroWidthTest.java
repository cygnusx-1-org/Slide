package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.markdown.RedditMarkwon;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The new-Reddit (Markwon) renderer takes RAW markdown. Authors paste {@code &#x200B;} (a zero-width
 * space from new Reddit's fancy editor) as a blank-line spacer; it must render as the invisible
 * U+200B char, never as the literal text {@code &#x200B;}.
 *
 * <p>In practice Slide fetches {@code selftext}/{@code body} without {@code raw_json=1}, so Reddit
 * html-escapes them and the entity actually arrives as {@code &amp;#x200B;} (commonmark then resolves
 * {@code &amp;}→{@code &}, leaving literal {@code &#x200B;} on screen). The renderer must handle both
 * the escaped and unescaped forms — see {@link #escapedHexEntityIsHandled}, the real-world case. The
 * old snudown path already handled this via html-unescape, so it is not covered here.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RedditMarkwonZeroWidthTest {

    private static final String ZWSP = "\u200B";

    /** Render {@code markdown} through the Markwon path and return the resulting text. */
    private static String render(String markdown) {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        RedditMarkwon.setMarkdown(tv, "test", markdown);
        return tv.getText().toString();
    }

    @Test
    public void hexEntityBecomesInvisibleCharNotLiteralText() {
        String out = render("before\n\n&#x200B;\n\nafter");
        assertFalse("hex entity must not leak as visible text", out.contains("&#x200B;"));
        assertTrue("entity should become a real zero-width space", out.contains(ZWSP));
        assertTrue(out.contains("before"));
        assertTrue(out.contains("after"));
    }

    @Test
    public void escapedHexEntityIsHandled() {
        // The real-world input: Reddit html-escapes selftext (no raw_json=1), so the author's
        // &#x200B; arrives as &amp;#x200B;. This is the form that the screenshot bug came from.
        String out = render("before\n\n&amp;#x200B;\n\nafter");
        assertFalse("escaped entity must not leak as visible text", out.contains("&#x200B;"));
        assertTrue("escaped entity should become a real zero-width space", out.contains(ZWSP));
        assertTrue(out.contains("before"));
        assertTrue(out.contains("after"));
    }

    @Test
    public void escapedDecimalEntityIsHandled() {
        String out = render("before&amp;#8203;after");
        assertFalse(out.contains("&#8203;"));
        assertTrue(out.contains(ZWSP));
    }

    @Test
    public void lowercaseAndZeroPaddedHexAreHandled() {
        String out = render("x&#x0200b;y");
        assertFalse(out.contains("&#x0200b;"));
        assertTrue(out.contains(ZWSP));
    }

    @Test
    public void decimalEntityBecomesInvisibleChar() {
        String out = render("before&#8203;after");
        assertFalse(out.contains("&#8203;"));
        assertTrue(out.contains(ZWSP));
    }

    @Test
    public void ordinaryTextIsUnchanged() {
        // Guard against the rewrite being too greedy: no zero-width space should be injected.
        String out = render("Just some text.");
        assertTrue(out.contains("Just some text."));
        assertFalse(out.contains(ZWSP));
    }
}
