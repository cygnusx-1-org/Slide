package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.Spanned;
import android.text.style.URLSpan;
import androidx.test.core.app.ApplicationProvider;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.markdown.RedditMarkwon;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The new-Reddit (Markwon) renderer must autolink bare URLs in a comment body, the same way the
 * snudown {@code body_html} path does. Regression for the bug where adding an explicit
 * {@code CorePlugin.create()} left two CorePlugin instances in the registry, so LinkifyPlugin's
 * text listener attached to a different instance than the one that rendered — and bare URLs
 * silently stopped being linkified.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RedditMarkwonLinkTest {

    private static Spanned render(String markdown) {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        RedditMarkwon.setMarkdown(tv, "test", markdown);
        return (Spanned) tv.getText();
    }

    private static URLSpan[] links(Spanned s) {
        return s.getSpans(0, s.length(), URLSpan.class);
    }

    @Test
    public void bareUrlWithQueryStringBecomesLink() {
        Spanned s = render("Discussion on HN: https://news.ycombinator.com/item?id=48626930");
        URLSpan[] spans = links(s);
        assertEquals("bare url should be linkified exactly once", 1, spans.length);
        assertEquals(
                "https://news.ycombinator.com/item?id=48626930", spans[0].getURL());
    }

    @Test
    public void bareUrlInlineBecomesLink() {
        URLSpan[] spans = links(render("see https://example.com/path here"));
        assertEquals(1, spans.length);
        assertEquals("https://example.com/path", spans[0].getURL());
    }

    @Test
    public void explicitMarkdownLinkStillWorks() {
        URLSpan[] spans = links(render("a [link](https://example.com) here"));
        assertEquals(1, spans.length);
        assertEquals("https://example.com", spans[0].getURL());
    }

    @Test
    public void fencedCodeBlockStillRenders() {
        // Issue #179: the auto-added CorePlugin still renders fenced code as code-box text.
        Spanned s = render("```\ncode here\n```");
        assertTrue("fenced code content should survive", s.toString().contains("code here"));
    }
}
