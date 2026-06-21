package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;

import me.edgan.redditslide.markdown.MentionPostProcessor;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.junit.Test;

/**
 * Pure-commonmark tests (no Android) for the clean-room {@link MentionPostProcessor} used by the
 * new Reddit-style renderer (issue #179).
 */
public class MentionPostProcessorTest {

    private static String render(String markdown) {
        Parser parser = Parser.builder().postProcessor(new MentionPostProcessor()).build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        return renderer.render(parser.parse(markdown));
    }

    @Test
    public void linksBareUserAndSubreddit() {
        assertEquals(
                "<p>hi <a href=\"/u/edgan\">u/edgan</a> and <a href=\"/r/test\">r/test</a></p>\n",
                render("hi u/edgan and r/test"));
    }

    @Test
    public void linksSlashPrefixedFormsPreservingTypedText() {
        assertEquals(
                "<p><a href=\"/u/edgan\">/u/edgan</a> <a href=\"/r/test\">/r/test</a></p>\n",
                render("/u/edgan /r/test"));
    }

    @Test
    public void doesNotTouchInlineCode() {
        assertEquals("<p>see <code>r/test</code></p>\n", render("see `r/test`"));
    }

    @Test
    public void doesNotDoubleLinkInsideAnExistingLink() {
        assertEquals(
                "<p><a href=\"https://example.com\">r/test</a></p>\n",
                render("[r/test](https://example.com)"));
    }

    @Test
    public void doesNotMatchInsideUrlPath() {
        // The "/r/test" inside a URL path must not become a separate mention link. (Bare-URL
        // autolinking is handled separately by LinkifyPlugin at render time in the app.)
        assertEquals(
                "<p>https://example.com/r/test</p>\n", render("https://example.com/r/test"));
    }
}
