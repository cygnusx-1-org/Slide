package me.edgan.redditslide.markdown;

import android.graphics.Color;
import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

/**
 * Self-contained clickable spoiler span for the new Reddit-style renderer. Subclasses
 * {@link URLSpan} so Slide's existing {@code TextViewLinkHandler} dispatches its taps to
 * {@code SpoilerRobotoTextView.onLinkClick}, which calls {@link #toggle(View)}.
 *
 * <p>Hidden text is painted behind an opaque block in the comment's theme color (matching the
 * snudown path's {@code Palette.getDarkerColor(Palette.getColor(subreddit))} block); a tap reveals
 * it, keeping the block as a highlight with white text so the revealed spoiler stays visually
 * distinct from ordinary comment text. The color is supplied per render via {@link #setMaskColor(int)}
 * because the {@code Markwon} instance is cached across subreddits; when unset it falls back to the
 * text color. See issue #179.
 */
public class RedditSpoilerSpan extends URLSpan {

    private boolean revealed = false;

    /** 0 == not set: fall back to the text color (e.g. the editor preview, which has no subreddit). */
    private int maskColor = 0;

    public RedditSpoilerSpan() {
        super("#spoiler");
    }

    public boolean isRevealed() {
        return revealed;
    }

    /** Set the opaque block color (the comment's theme color); 0 keeps the text-color fallback. */
    public void setMaskColor(int maskColor) {
        this.maskColor = maskColor;
    }

    /** Toggle hidden/revealed and force the view to re-lay-out so the change is drawn. */
    public void toggle(View widget) {
        revealed = !revealed;
        if (widget instanceof TextView) {
            TextView tv = (TextView) widget;
            tv.setText(tv.getText());
        } else if (widget != null) {
            widget.invalidate();
        }
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        ds.setUnderlineText(false);
        // The block color: the comment's theme color when supplied, else the text color. Force full
        // opacity — the source color often carries alpha < 255 (e.g. ~87% primary text), and a
        // translucent block let the glyphs drawn over it stay legible (the "spoiler text readable"
        // bug).
        final int block = (maskColor != 0 ? maskColor : ds.getColor()) | 0xFF000000;
        ds.bgColor = block;
        if (!revealed) {
            // Paint the glyphs in the block color too, so the text vanishes regardless of the
            // ambient text color's alpha.
            ds.setColor(block);
        } else {
            // Once revealed, keep the opaque block as a highlight and paint the text white (like the
            // snudown path's white underneath span), so the spoiler stays visually distinct instead
            // of blending into the surrounding comment text.
            ds.setColor(Color.WHITE);
        }
    }
}
