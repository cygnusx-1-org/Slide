package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.Spanned;
import androidx.test.core.app.ApplicationProvider;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.markdown.MarkdownImages;
import me.edgan.redditslide.markdown.RedditMarkwon;
import me.edgan.redditslide.markdown.RedditSpoilerSpan;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Real r/help comment bodies (HTML-escaped, exactly as Reddit serves them without raw_json=1)
 * exercising spoiler edge cases the synthetic tests missed: a spoiler in an indented code block
 * (must stay literal), and a backslash-escaped {@code \>!} (must stay literal). Expected behavior is
 * taken from each comment's {@code body_html} (how Reddit itself renders it). See issue #179.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SpoilerRealDataTest {

    // The private-use sentinel chars RedditSpoilerPreprocessor swaps in for >! / !<; they must
    // never survive into rendered text (they show as tofu boxes — the reported bug).
    private static final char OPEN = (char) 0xE000;
    private static final char CLOSE = (char) 0xE001;

    private static Spanned render(String escapedBody) {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        RedditMarkwon.setMarkdown(tv, "help", MarkdownImages.unescapeTransportEntities(escapedBody));
        return (Spanned) tv.getText();
    }

    private static int spoilerCount(Spanned s) {
        return s.getSpans(0, s.length(), RedditSpoilerSpan.class).length;
    }

    private static void assertNoSentinelLeak(Spanned s) {
        String text = s.toString();
        assertFalse("OPEN sentinel must not leak", text.indexOf(OPEN) >= 0);
        assertFalse("CLOSE sentinel must not leak", text.indexOf(CLOSE) >= 0);
    }

    /** jfpture: one inline spoiler + an indented-code copy that must render literally. */
    @Test
    public void inlineSpoilerPlusIndentedCodeBlock() {
        String body =
                "Use the Markdown. Place your text between these &gt;!secret text!&lt; and"
                        + " it'll show up as spoilers.\n\n    &gt;!secret text!&lt;";
        Spanned out = render(body);
        assertNoSentinelLeak(out);
        assertEquals("only the inline occurrence is a spoiler", 1, spoilerCount(out));
        assertTrue(
                "the indented code block keeps the literal spoiler syntax",
                out.toString().contains(">!secret text!<"));
    }

    /** jfptgmh: a backslash-escaped \>! is literal; only the bare >!...!< is a spoiler. */
    @Test
    public void backslashEscapedMarkerIsLiteral() {
        String body =
                "\\&gt;!spoiler code you need!&lt; to get &gt;!spoiler code you need!&lt;.\n\n"
                        + "\\&gt;!paragraph one\\.!&lt;\n\n\\&gt;!paragraph two\\.!&lt;";
        Spanned out = render(body);
        assertNoSentinelLeak(out);
        assertEquals(
                "only the single un-escaped occurrence is a spoiler", 1, spoilerCount(out));
        assertTrue(
                "escaped opener renders literally",
                out.toString().contains(">!spoiler code you need!<"));
        assertTrue(out.toString().contains(">!paragraph one.!<"));
        assertTrue(out.toString().contains(">!paragraph two.!<"));
    }

    /** jfpsmw2: spaced "> ! ... ! <" is not a spoiler; the tight ">!...!<" is. */
    @Test
    public void spacedMarkersAreNotSpoilers() {
        String body =
                "Surround the spoiler parts with &gt; ! spoiler bits ! &lt;\n\n"
                        + "It should come out looking like this: &gt;!spoiler bits!&lt;.";
        Spanned out = render(body);
        assertNoSentinelLeak(out);
        assertEquals(1, spoilerCount(out));
        assertTrue(out.toString().contains("> ! spoiler bits ! <"));
    }
}
