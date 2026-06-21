package me.edgan.redditslide.handler;

import android.os.Handler;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.method.BaseMovementMethod;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.widget.TextView;
import me.edgan.redditslide.ClickableText;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;

public class TextViewLinkHandler extends BaseMovementMethod {
    private final ClickableText clickableText;
    String subreddit;
    SpoilerRobotoTextView comm;
    Spannable sequence;
    float position;
    boolean clickHandled;
    Handler handler;
    Runnable longClicked;
    URLSpan[] link;
    MotionEvent event;

    public TextViewLinkHandler(ClickableText clickableText, String subreddit, Spannable sequence) {
        this.clickableText = clickableText;
        this.subreddit = subreddit;
        this.sequence = sequence;

        clickHandled = false;
        handler = new Handler();
        longClicked =
                new Runnable() {
                    @Override
                    public void run() {
                        // long click
                        clickHandled = true;

                        handler.removeCallbacksAndMessages(null);
                        if (link != null && link.length > 0 && link[0] != null) {
                            TextViewLinkHandler.this.clickableText.onLinkLongClick(
                                    link[0].getURL(), event);
                        }
                    }
                };
    }

    @Override
    public boolean canSelectArbitrarily() {
        return false;
    }

    @Override
    public boolean onTouchEvent(TextView widget, final Spannable buffer, MotionEvent event) {
        comm = (SpoilerRobotoTextView) widget;

        int x = (int) event.getX();
        int y = (int) event.getY();
        x -= widget.getTotalPaddingLeft();
        y -= widget.getTotalPaddingTop();
        x += widget.getScrollX();
        y += widget.getScrollY();

        Layout layout = widget.getLayout();
        int line = layout.getLineForVertical(y);
        int off = layout.getOffsetForHorizontal(line, x);

        link = buffer.getSpans(off, off, URLSpan.class);
        if (link.length > 0) {
            comm.setLongClickable(false);

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                position = event.getY(); // used to see if the user scrolled or not
            }
            if (!(event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_DOWN)) {
                if (Math.abs((position - event.getY())) > 25) {
                    handler.removeCallbacksAndMessages(null);
                }
                return super.onTouchEvent(widget, buffer, event);
            }

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    clickHandled = false;
                    this.event = event;
                    if (SettingValues.peek) {
                        handler.postDelayed(
                                longClicked, android.view.ViewConfiguration.getTapTimeout() + 50);
                    } else {
                        handler.postDelayed(
                                longClicked, android.view.ViewConfiguration.getLongPressTimeout());
                    }

                    break;
                case MotionEvent.ACTION_UP:
                    comm.setLongClickable(true);
                    handler.removeCallbacksAndMessages(null);

                    if (!clickHandled) {
                        URLSpan tappedUrlSpan = link[0];
                        ImageSpan[] imageSpansAtTapOffset = buffer.getSpans(off, off, ImageSpan.class);
                        int urlSpanStart = buffer.getSpanStart(tappedUrlSpan);
                        int urlSpanEnd = buffer.getSpanEnd(tappedUrlSpan);

                        ImageSpan[] allImageSpansInUrl = buffer.getSpans(urlSpanStart, urlSpanEnd, ImageSpan.class);
                        boolean hasImageInUrl = allImageSpansInUrl.length > 0;
                        boolean isEffectivelyImageOnlyLink = false;

                        if (hasImageInUrl) {
                            isEffectivelyImageOnlyLink = true;
                            for (int i = urlSpanStart; i < urlSpanEnd; i++) {
                                boolean charIsImage = false;
                                for (ImageSpan imgSpan : allImageSpansInUrl) {
                                    if (i >= buffer.getSpanStart(imgSpan) && i < buffer.getSpanEnd(imgSpan)) {
                                        charIsImage = true;
                                        break;
                                    }
                                }
                                if (!charIsImage && !Character.isWhitespace(buffer.charAt(i))) {
                                    isEffectivelyImageOnlyLink = false;
                                    break;
                                }
                            }
                        }

                        if (isEffectivelyImageOnlyLink) {
                            if (imageSpansAtTapOffset.length > 0) {
                                ImageSpan tappedImageSpan = imageSpansAtTapOffset[0];
                                android.graphics.drawable.Drawable drawable = tappedImageSpan.getDrawable();

                                if (drawable != null && drawable.getBounds().width() > 0 && drawable.getBounds().height() > 0) {
                                    int spanStartOffset = buffer.getSpanStart(tappedImageSpan);

                                    float imageDrawStartX = layout.getPrimaryHorizontal(spanStartOffset);
                                    float imageDrawEndX = imageDrawStartX + drawable.getBounds().width();

                                    int imageStartLine = layout.getLineForOffset(spanStartOffset);
                                    float imageDrawEndY = layout.getLineBottom(imageStartLine);
                                    float imageDrawStartY = imageDrawEndY - drawable.getBounds().height();

                                    if (x >= imageDrawStartX && x < imageDrawEndX &&
                                        y >= imageDrawStartY && y < imageDrawEndY) {
                                        clickableText.onLinkClick(tappedUrlSpan.getURL(), urlSpanEnd, subreddit, tappedUrlSpan);
                                    } else {
                                        Selection.removeSelection(buffer);
                                        return false;
                                    }
                                } else {
                                    Selection.removeSelection(buffer);
                                    return false;
                                }
                            } else {
                                Selection.removeSelection(buffer);
                                return false;
                            }
                        } else {
                            clickableText.onLinkClick(tappedUrlSpan.getURL(), urlSpanEnd, subreddit, tappedUrlSpan);
                        }
                    }
                    break;
            }
            return true;

        } else {
            Selection.removeSelection(buffer);
            return false;
        }
    }
}
