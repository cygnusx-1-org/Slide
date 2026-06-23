package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.Spanned;
import androidx.test.core.app.ApplicationProvider;
import io.noties.markwon.core.spans.BlockQuoteSpan;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.markdown.MarkdownImages;
import me.edgan.redditslide.markdown.RedditMarkwon;
import me.edgan.redditslide.markdown.RedditSpoilerSpan;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Slide fetches {@code body}/{@code selftext} without {@code raw_json=1}, so Reddit html-escapes
 * them and an author's {@code >} blockquote marker arrives as {@code &gt;}. commonmark decides block
 * structure before decoding inline entities, so {@code &gt;text} was never recognized as a blockquote
 * — it leaked as a literal leading {@code >} (the screenshot bug). {@link MarkdownImages} must undo
 * that transport escaping before the text reaches the parser. See issue #179.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MarkdownImagesBlockquoteTest {

    /** Render the (already transport-decoded) markdown through Markwon, as renderInto does. */
    private static Spanned render(String escapedBody) {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        RedditMarkwon.setMarkdown(
                tv, "test", MarkdownImages.unescapeTransportEntities(escapedBody));
        return (Spanned) tv.getText();
    }

    private static boolean hasBlockQuote(Spanned text) {
        return text.getSpans(0, text.length(), BlockQuoteSpan.class).length > 0;
    }

    @Test
    public void escapedBlockquoteMarkerRendersAsBlockquote() {
        Spanned out = render("&gt;Rocket Lab quietly launched a small satellite.");
        assertFalse("the > marker must be consumed, not shown literally", out.toString().contains(">"));
        assertTrue("text content survives", out.toString().contains("Rocket Lab"));
        assertTrue("a blockquote span should be applied", hasBlockQuote(out));
    }

    @Test
    public void adjacentQuoteParagraphsRenderAsOneContinuousStripe() {
        // Two escaped quote paragraphs separated by a blank line must merge into a single
        // blockquote span (one unbroken stripe), matching snudown — not two segments.
        Spanned out = render("&gt;first paragraph\n\n&gt;second paragraph");
        assertEquals(
                "adjacent quotes should be one blockquote, not segmented",
                1,
                out.getSpans(0, out.length(), BlockQuoteSpan.class).length);
        assertTrue(out.toString().contains("first paragraph"));
        assertTrue(out.toString().contains("second paragraph"));
    }

    @Test
    public void escapedBlockquoteWithSpaceAfterMarker() {
        Spanned out = render("&gt; respects the other cultures");
        assertFalse(out.toString().contains(">"));
        assertTrue(out.toString().contains("respects the other cultures"));
        assertTrue(hasBlockQuote(out));
    }

    @Test
    public void escapedSpoilerRendersAsSpoilerNotBlockquote() {
        // Reddit escapes ">!secret!<" to "&gt;!secret!&lt;". After transport-decoding it must be
        // recognized as a spoiler (hidden span), NOT leak as a literal ">!" / blockquote.
        Spanned out = render("&gt;!secret!&lt;");
        assertFalse("the >! / !< markers must be consumed", out.toString().contains(">!"));
        assertFalse(out.toString().contains("!<"));
        assertTrue("spoiler text is present (hidden, not removed)", out.toString().contains("secret"));
        assertEquals(
                "a spoiler span should be applied",
                1,
                out.getSpans(0, out.length(), RedditSpoilerSpan.class).length);
        assertFalse(
                "a spoiler is not a blockquote",
                hasBlockQuote(out));
    }

    @Test
    public void unescapeIsSinglePass() {
        // An author who typed the literal text "&gt;" has it escaped to "&amp;gt;"; one decode pass
        // must yield "&gt;" (commonmark then keeps it literal), NOT a blockquote-forming ">".
        assertEquals("&gt;not a quote", MarkdownImages.unescapeTransportEntities("&amp;gt;not a quote"));
    }

    @Test
    public void decodesAngleBracketsAndAmpersand() {
        assertEquals("a & b < c > d", MarkdownImages.unescapeTransportEntities("a &amp; b &lt; c &gt; d"));
    }

    @Test
    public void leavesEntityFreeTextUntouched() {
        assertEquals("just some text", MarkdownImages.unescapeTransportEntities("just some text"));
        assertNull(MarkdownImages.unescapeTransportEntities(null));
    }
}
