package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;

import me.edgan.redditslide.markdown.MentionPostProcessor;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.junit.Test;

/**
 * {@link MentionPostProcessor} checked against snudown's own test suite ({@code test_snudown.py} in
 * {@code github.com/reddit/snudown}), which is the authority on which span of text Reddit turns
 * into a mention link. Every case there that exercises the {@code r/} or {@code u/} autolinker is
 * reproduced below, verbatim on both sides — snudown's input and snudown's expected HTML.
 *
 * <p>Cases whose only difference is a feature that lives outside this post-processor are not part
 * of the corpus: bare-URL autolinking ({@code http://www.reddit.com}), which Slide does at render
 * time with LinkifyPlugin, and the {@code ^} superscript marker ({@code foo^u/me}), which snudown
 * consumes and Slide leaves as text here. In both, the mention itself matches snudown — only the
 * surrounding markup differs.
 *
 * <p>{@link #divergesFromSnudownOnlyWhereIntended()} pins the remaining differences so they stay
 * deliberate — if one of them starts matching snudown, that is a change worth noticing, not a
 * silent improvement.
 */
public class MentionSnudownCorpusTest {

    /** {input, expected html} pairs taken from snudown's {@code cases} dict. */
    private static final String[] SNUDOWN_CASES = {
        "*foo*u/me",
        "<p><em>foo</em><a href=\"/u/me\">u/me</a></p>\n",
        "*u/me*",
        "<p><em><a href=\"/u/me\">u/me</a></em></p>\n",
        "/R/reddit.com",
        "<p>/R/reddit.com</p>\n",
        "/r/",
        "<p>/r/</p>\n",
        "/r/all-irc://foo.bar/",
        "<p><a href=\"/r/all-irc\">/r/all-irc</a>://foo.bar/</p>\n",
        "/r/all-minus-something",
        "<p><a href=\"/r/all-minus-something\">/r/all-minus-something</a></p>\n",
        "/r/foo+irc://foo.bar/",
        "<p><a href=\"/r/foo+irc\">/r/foo+irc</a>://foo.bar/</p>\n",
        "/r/irc://foo.bar/",
        "<p><a href=\"/r/irc\">/r/irc</a>://foo.bar/</p>\n",
        "/r/multireddit+test+yay",
        "<p><a href=\"/r/multireddit+test+yay\">/r/multireddit+test+yay</a></p>\n",
        "/r/not.cool",
        "<p><a href=\"/r/not\">/r/not</a>.cool</p>\n",
        "/r/notall-minus",
        "<p><a href=\"/r/notall\">/r/notall</a>-minus</p>\n",
        "/r/reddit.com",
        "<p><a href=\"/r/reddit.com\">/r/reddit.com</a></p>\n",
        "/r/sr_with_underscores",
        "<p><a href=\"/r/sr_with_underscores\">/r/sr_with_underscores</a></p>\n",
        "/r/test",
        "<p><a href=\"/r/test\">/r/test</a></p>\n",
        "/r/test/comments/test test",
        "<p><a href=\"/r/test/comments/test\">/r/test/comments/test</a> test</p>\n",
        "/r/test/commentscommentscommentscommentscommentscommentscomments/test test",
        "<p><a href=\"/r/test/commentscommentscommentscommentscommentscommentscomments/test\">/r/test/commentscommentscommentscommentscommentscommentscomments/test</a> test</p>\n",
        "/r/test/m/test test",
        "<p><a href=\"/r/test/m/test\">/r/test/m/test</a> test</p>\n",
        "/r/test/w/test test",
        "<p><a href=\"/r/test/w/test\">/r/test/w/test</a> test</p>\n",
        "/r/whatever: fork",
        "<p><a href=\"/r/whatever\">/r/whatever</a>: fork</p>\n",
        "/r/www.example.com",
        "<p><a href=\"/r/www\">/r/www</a>.example.com</p>\n",
        "/u/m",
        "<p>/u/m</p>\n",
        "/u/me",
        "<p><a href=\"/u/me\">/u/me</a></p>\n",
        "/u/test",
        "<p><a href=\"/u/test\">/u/test</a></p>\n",
        "/u/test/commentscommentscommentscommentscommentscommentscomments/test test",
        "<p><a href=\"/u/test/commentscommentscommentscommentscommentscommentscomments/test\">/u/test/commentscommentscommentscommentscommentscommentscomments/test</a> test</p>\n",
        "/u/test/m/test test",
        "<p><a href=\"/u/test/m/test\">/u/test/m/test</a> test</p>\n",
        "Words words /r/test words",
        "<p>Words words <a href=\"/r/test\">/r/test</a> words</p>\n",
        "[A link with a /r/subreddit in it](/lol)",
        "<p><a href=\"/lol\">A link with a /r/subreddit in it</a></p>\n",
        "[r://<http://reddit.com/>](/aa)",
        "<p><a href=\"/aa\">r://<a href=\"http://reddit.com/\">http://reddit.com/</a></a></p>\n",
        "\\\\/u/me",
        "<p>\\<a href=\"/u/me\">/u/me</a></p>\n",
        "\\\\u/me",
        "<p>\\<a href=\"/u/me\">u/me</a></p>\n",
        "\\u/me",
        "<p>\\<a href=\"/u/me\">u/me</a></p>\n",
        "a /r/reddit.com",
        "<p>a <a href=\"/r/reddit.com\">/r/reddit.com</a></p>\n",
        "a /u/reddit",
        "<p>a <a href=\"/u/reddit\">/u/reddit</a></p>\n",
        "a r/reddit.com",
        "<p>a <a href=\"/r/reddit.com\">r/reddit.com</a></p>\n",
        "a u/reddit",
        "<p>a <a href=\"/u/reddit\">u/reddit</a></p>\n",
        "a u/reddit/foobaz",
        "<p>a <a href=\"/u/reddit/foobaz\">u/reddit/foobaz</a></p>\n",
        "foo:r/reddit.com",
        "<p>foo:<a href=\"/r/reddit.com\">r/reddit.com</a></p>\n",
        "foo:u/reddit",
        "<p>foo:<a href=\"/u/reddit\">u/reddit</a></p>\n",
        "foobar/reddit.com",
        "<p>foobar/reddit.com</p>\n",
        "u/m",
        "<p>u/m</p>\n",
        "u/me",
        "<p><a href=\"/u/me\">u/me</a></p>\n",
        "u/reddit",
        "<p><a href=\"/u/reddit\">u/reddit</a></p>\n"
    };

    private static String render(String markdown) {
        Parser parser = Parser.builder().postProcessor(new MentionPostProcessor()).build();
        HtmlRenderer renderer = HtmlRenderer.builder().build();
        return renderer.render(parser.parse(markdown));
    }

    @Test
    public void matchesSnudown() {
        for (int i = 0; i < SNUDOWN_CASES.length; i += 2) {
            assertEquals(SNUDOWN_CASES[i], SNUDOWN_CASES[i + 1], render(SNUDOWN_CASES[i]));
        }
    }

    @Test
    public void divergesFromSnudownOnlyWhereIntended() {
        // t: timereddits are a dead April-Fools feature from 2012 and are deliberately not
        // implemented, so snudown's three t: cases link and Slide's leave plain text.
        assertEquals(
                "<p>/r/t:timereddit</p>\n", render("/r/t:timereddit"));
        assertEquals(
                "<p>/r/t:heatdeathoftheuniverse</p>\n", render("/r/t:heatdeathoftheuniverse"));
        assertEquals("<p>/r/t:irc//foo.bar/</p>\n", render("/r/t:irc//foo.bar/"));
        // ...and a t: component ends a multireddit chain early instead of extending it. The
        // subreddits before it still link, which is the useful half of snudown's answer.
        assertEquals(
                "<p><a href=\"/r/very+clever+multireddit+reddit.com\">"
                        + "/r/very+clever+multireddit+reddit.com</a>+t:fork+yay</p>\n",
                render("/r/very+clever+multireddit+reddit.com+t:fork+yay"));

        // A backslash escape suppresses the link in snudown, whose autolinker sees the raw source.
        // commonmark resolves escapes during parsing, so by the time this post-processor walks the
        // tree the text node is an ordinary "/u/me" and nothing marks it as escaped. Not fixable
        // at this layer.
        assertEquals("<p><a href=\"/u/me\">/u/me</a></p>\n", render("\\/u/me"));
        assertEquals(
                "<p>escaped <a href=\"/r/test\">/r/test</a></p>\n", render("escaped \\/r/test"));

        // snudown's boundary check is byte-oriented ispunct(), so it refuses to link after
        // non-ASCII punctuation -- a limitation its own comments call out. Slide links it.
        assertEquals(
                "<p>a\u3002<a href=\"/u/reddit\">u/reddit</a></p>\n", render("a\u3002u/reddit"));
    }
}
