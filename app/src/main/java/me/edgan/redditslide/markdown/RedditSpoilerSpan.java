package me.edgan.redditslide.markdown;

import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.View;
import android.widget.TextView;

/**
 * Self-contained clickable spoiler span for the new Reddit-style renderer. Subclasses
 * {@link URLSpan} so Slide's existing {@code TextViewLinkHandler} dispatches its taps to
 * {@code SpoilerRobotoTextView.onLinkClick}, which calls {@link #toggle(View)}.
 *
 * <p>Hidden text is painted with the background set to the text color (an opaque block); a tap
 * reveals it. No coupling to the snudown spoiler machinery. See issue #179.
 */
public class RedditSpoilerSpan extends URLSpan {

    private boolean revealed = false;

    public RedditSpoilerSpan() {
        super("#spoiler");
    }

    public boolean isRevealed() {
        return revealed;
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
        if (!revealed) {
            // Background == text color hides the glyphs behind a solid block.
            ds.bgColor = ds.getColor();
        }
    }
}
