package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;

import android.text.TextPaint;
import me.edgan.redditslide.markdown.RedditSpoilerSpan;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A hidden spoiler must mask its text with a fully opaque block; copying the (often
 * semi-transparent) text color verbatim left a translucent block that the same-coloured glyphs
 * showed through. Revealed spoilers keep a faint tint so the region still reads as a spoiler. See
 * issue #179.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RedditSpoilerSpanTest {

    private static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    @Test
    public void hiddenMaskIsOpaqueEvenWhenTextColorIsTranslucent() {
        RedditSpoilerSpan span = new RedditSpoilerSpan();
        TextPaint p = new TextPaint();
        p.setColor(0x8AFFFFFF); // 54%-alpha white, like Material secondary text
        span.updateDrawState(p);
        assertEquals("mask must be fully opaque", 0xFF, alpha(p.bgColor));
        assertEquals("hue matches the text so glyphs vanish", 0xFFFFFF, p.bgColor & 0xFFFFFF);
    }

    @Test
    public void maskColorOverridesTextColorAndHidesGlyphs() {
        RedditSpoilerSpan span = new RedditSpoilerSpan();
        span.setMaskColor(0xFFAA3322); // a theme color
        TextPaint p = new TextPaint();
        p.setColor(0xFFFFFFFF); // white text underneath
        span.updateDrawState(p);
        assertEquals("block uses the supplied theme color", 0xFFAA3322, p.bgColor);
        assertEquals("glyphs painted in the same color so they vanish", 0xFFAA3322, p.getColor());
    }

    @Test
    public void revealedKeepsOpaqueHighlightWithWhiteText() {
        RedditSpoilerSpan span = new RedditSpoilerSpan();
        span.setMaskColor(0xFFAA3322);
        span.toggle(null); // -> revealed
        TextPaint p = new TextPaint();
        p.setColor(0xFFCCCCCC); // ordinary comment text color
        span.updateDrawState(p);
        assertEquals("revealed keeps the opaque theme-color highlight", 0xFFAA3322, p.bgColor);
        assertEquals("revealed text is white, so it stays distinct", 0xFFFFFFFF, p.getColor());
    }
}
