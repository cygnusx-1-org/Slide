package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.text.Spanned;
import androidx.test.core.app.ApplicationProvider;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.markdown.RedditMarkwon;
import me.edgan.redditslide.markdown.RedditSpoilerSpan;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * End-to-end check that legacy CSS spoiler links render as tappable {@link RedditSpoilerSpan}s
 * through the full {@link RedditMarkwon} pipeline (post-processor → SpoilerNode → span), not as
 * plain links. Issue #179.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class LegacySpoilerRenderTest {

    private static Spanned render(String markdown) {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        RedditMarkwon.setMarkdown(tv, "test", markdown);
        return (Spanned) tv.getText();
    }

    private static RedditSpoilerSpan[] spoilerSpans(Spanned s) {
        return s.getSpans(0, s.length(), RedditSpoilerSpan.class);
    }

    @Test
    public void linkStyleSpoilerBecomesSpoilerSpan() {
        Spanned s = render("a [secret](/s) b");
        assertEquals(1, spoilerSpans(s).length);
    }

    @Test
    public void titleAttrSpoilerBecomesSpoilerSpanAndKeepsTeaser() {
        Spanned s = render("see [Kaguya manga](/s \"the culture festival confession\") soon");
        assertEquals(1, spoilerSpans(s).length);
        assertTrue("teaser stays visible", s.toString().contains("Kaguya manga"));
    }

    @Test
    public void normalLinkIsNotASpoiler() {
        Spanned s = render("a [link](https://example.com) b");
        assertEquals(0, spoilerSpans(s).length);
    }
}
