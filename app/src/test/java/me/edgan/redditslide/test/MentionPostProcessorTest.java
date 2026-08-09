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

    // ---- Trailing paths (issue #356): snudown links the whole reference, not just the name. ----

    @Test
    public void linksTrailingSubmissionPath() {
        assertEquals(
                "<p><a href=\"/r/sysadmin/comments/1uvz7ns/telstra_x/\">"
                        + "r/sysadmin/comments/1uvz7ns/telstra_x/</a></p>\n",
                render("r/sysadmin/comments/1uvz7ns/telstra_x/"));
    }

    @Test
    public void linksTrailingCommentPermalinkPath() {
        assertEquals(
                "<p><a href=\"/r/sysadmin/comments/1uvz7ns/slug/p28dgah/\">"
                        + "/r/sysadmin/comments/1uvz7ns/slug/p28dgah/</a></p>\n",
                render("/r/sysadmin/comments/1uvz7ns/slug/p28dgah/"));
    }

    @Test
    public void linksTrailingWikiPath() {
        assertEquals(
                "<p><a href=\"/r/sysadmin/wiki/index\">r/sysadmin/wiki/index</a></p>\n",
                render("r/sysadmin/wiki/index"));
    }

    @Test
    public void linksTrailingPathOnUsername() {
        assertEquals(
                "<p><a href=\"/u/spez/comments/abc/slug/\">u/spez/comments/abc/slug/</a></p>\n",
                render("u/spez/comments/abc/slug/"));
    }

    @Test
    public void pathStopsAtSentenceEndingPeriod() {
        assertEquals(
                "<p>see <a href=\"/r/pics\">r/pics</a>. ok</p>\n", render("see r/pics. ok"));
    }

    @Test
    public void pathDoesNotStartAtASchemeSeparator() {
        // The path charset has no ':', and the char after the name here is ':', not '/'.
        assertEquals(
                "<p><a href=\"/r/irc\">/r/irc</a>://foo.bar/</p>\n", render("/r/irc://foo.bar/"));
    }

    @Test
    public void pathStopsAtASpace() {
        assertEquals(
                "<p><a href=\"/r/pics/new\">r/pics/new</a> and more</p>\n",
                render("r/pics/new and more"));
    }

    // ---- Name rules ----

    @Test
    public void linksMultiredditNames() {
        assertEquals("<p><a href=\"/r/aa+bb\">r/aa+bb</a></p>\n", render("r/aa+bb"));
    }

    @Test
    public void linksAllMinusNamesButNotOtherHyphenatedOnes() {
        assertEquals("<p><a href=\"/r/all-pics\">r/all-pics</a></p>\n", render("r/all-pics"));
        // Hyphens only separate names in the "all minus these subs" form, so this links r/notall.
        assertEquals(
                "<p><a href=\"/r/notall\">r/notall</a>-minus</p>\n", render("r/notall-minus"));
    }

    @Test
    public void linksTheRedditComSpecialName() {
        assertEquals(
                "<p><a href=\"/r/reddit.com\">/r/reddit.com</a></p>\n", render("/r/reddit.com"));
        // Snudown pins that name's length exactly, so a longer one links nothing at all.
        assertEquals("<p>/r/reddit.commission</p>\n", render("/r/reddit.commission"));
    }

    @Test
    public void linksLongNamesWholeAndOverLongNamesNotAtAll() {
        // 24 characters is snudown's ceiling; a 25-character name must not link a truncated
        // prefix, which would point at a different subreddit.
        assertEquals(
                "<p><a href=\"/r/abcdefghijklmnopqrstuvwx\">r/abcdefghijklmnopqrstuvwx</a></p>\n",
                render("r/abcdefghijklmnopqrstuvwx"));
        assertEquals(
                "<p>r/abcdefghijklmnopqrstuvwxy</p>\n", render("r/abcdefghijklmnopqrstuvwxy"));
    }

    // ---- Node rebuilding around longer matches ----

    @Test
    public void handlesAMentionWithAPathAsTheWholeBody() {
        assertEquals("<p><a href=\"/r/ab/c\">r/ab/c</a></p>\n", render("r/ab/c"));
    }

    @Test
    public void keepsTextBetweenAndAfterAdjacentMentions() {
        assertEquals(
                "<p><a href=\"/r/ab/c\">r/ab/c</a> <a href=\"/u/d/e\">u/d/e</a> end</p>\n",
                render("r/ab/c u/d/e end"));
    }
}
