package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import androidx.test.core.app.ApplicationProvider;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.markdown.RedditMarkwon;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The new Reddit-style search path can't use the snudown {@code [[h[…]h]]} marker trick (it renders
 * from raw markdown), so {@link SpoilerRobotoTextView#highlightOccurrences} highlights matches after
 * rendering instead. Issue #179.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SearchHighlightTest {

    private static SpoilerRobotoTextView render(String markdown) {
        Context c = ApplicationProvider.getApplicationContext();
        SpoilerRobotoTextView tv = new SpoilerRobotoTextView(c);
        RedditMarkwon.setMarkdown(tv, "test", markdown);
        return tv;
    }

    private static BackgroundColorSpan[] highlights(Spanned s) {
        return s.getSpans(0, s.length(), BackgroundColorSpan.class);
    }

    @Test
    public void highlightsMatchedTermExactly() {
        SpoilerRobotoTextView tv = render("the quick brown fox");
        tv.highlightOccurrences("quick", "test");
        Spanned s = (Spanned) tv.getText();
        BackgroundColorSpan[] spans = highlights(s);
        assertEquals(1, spans.length);
        assertEquals(
                "quick",
                s.subSequence(s.getSpanStart(spans[0]), s.getSpanEnd(spans[0])).toString());
    }

    @Test
    public void caseInsensitiveAndAllOccurrences() {
        SpoilerRobotoTextView tv = render("Foo foo FOO");
        tv.highlightOccurrences("foo", "test");
        assertEquals(3, highlights((Spanned) tv.getText()).length);
    }

    @Test
    public void emptyOrAbsentTermIsNoOp() {
        SpoilerRobotoTextView tv = render("nothing to highlight here");
        tv.highlightOccurrences("", "test");
        tv.highlightOccurrences("absent", "test");
        assertEquals(0, highlights((Spanned) tv.getText()).length);
    }
}
