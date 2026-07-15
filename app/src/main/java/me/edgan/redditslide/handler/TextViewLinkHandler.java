package me.edgan.redditslide.handler;

import android.os.Handler;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.method.BaseMovementMethod;
import android.text.style.ImageSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.widget.TextView;
import io.noties.markwon.ext.tables.TableRowSpan;
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

    // The Markwon table-cell link resolved on ACTION_DOWN, reused by that gesture's high-frequency
    // ACTION_MOVE events so they don't re-run the (comparatively expensive) cell hit-test. Null when
    // the gesture didn't start on a cell link. Only touched from the touch thread, so no sync needed.
    private URLSpan[] gestureTableCellSpans;

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

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            // New gesture: drop any cell link cached from the previous one.
            gestureTableCellSpans = null;
        }

        // Markwon renders a whole table row as a single TableRowSpan (a ReplacementSpan) that draws
        // each cell's text — including its link URLSpans — into the span's own internal Layout, not
        // into the outer buffer. So a tap over a table cell finds no URLSpan above; resolve it from
        // the cell's own layout the way Markwon's TableAwareMovementMethod does, then route it
        // through the same onLinkClick path as any other link.
        boolean tableCellLink = false;
        if (link.length == 0) {
            if (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_UP) {
                // Resolve fresh only on the two events that consume it: DOWN (to schedule the
                // long-press) and UP (to click at the release position). Cache the DOWN result so
                // the gesture's high-frequency MOVE events can reuse it — re-running the hit-test
                // per move is wasteful, and letting `link` fall back to empty on a move would leave
                // the pending long-press with nothing to fire on.
                URLSpan[] cellLinks = resolveTableCellLink(buffer, layout, line, off, x, y);
                if (cellLinks != null) {
                    link = cellLinks;
                    tableCellLink = true;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    gestureTableCellSpans = cellLinks;
                }
            } else if (gestureTableCellSpans != null) {
                // MOVE/CANCEL within a table-cell gesture: reuse DOWN's resolution.
                link = gestureTableCellSpans;
                tableCellLink = true;
            }
        }

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
                        // A link resolved from inside a Markwon table cell lives in that cell's own
                        // layout, not the outer buffer, so the buffer-offset image handling below
                        // doesn't apply (table cells carry no inline images). Route it straight
                        // through onLinkClick like any other link. The xOffset arg only feeds the
                        // snudown spoiler path (setOrRemoveSpoilerSpans), which indexes the outer
                        // buffer; a cell link has no buffer offset, so pass -1 (not applicable).
                        if (tableCellLink) {
                            clickableText.onLinkClick(
                                    tappedUrlSpan.getURL(), -1, subreddit, tappedUrlSpan);
                            break;
                        }
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

    /**
     * Resolve the URLSpan(s) of the Markwon table cell under the touch, or {@code null} if the touch
     * isn't over a cell link. Markwon draws each cell's text (including its link URLSpans) into the
     * row span's own internal {@link Layout} rather than the outer buffer, so a normal buffer
     * hit-test misses them; this mirrors Markwon's {@code TableAwareMovementMethod}.
     *
     * @param off outer-layout offset at the touch (already used to find the {@link TableRowSpan})
     * @param x horizontal touch position in the outer layout's content coordinates
     * @param y vertical touch position in the outer layout's content coordinates
     */
    private static URLSpan[] resolveTableCellLink(
            Spannable buffer, Layout layout, int line, int off, int x, int y) {
        TableRowSpan[] rows = buffer.getSpans(off, off, TableRowSpan.class);
        if (rows.length == 0 || rows[0].cellWidth() <= 0) {
            return null;
        }
        TableRowSpan row = rows[0];
        Layout cellLayout = row.findLayoutForHorizontalOffset(x);
        if (cellLayout == null) {
            return null;
        }
        // The cell text is drawn inset by the theme's cell padding on both axes and vertically
        // centered within the row, so shift the touch into the cell's coordinate space before
        // mapping it to an offset. cellPadding is recovered from the gap between the row's cell
        // width and the cell layout's own (padding-shrunk) width; heightDiff mirrors the vertical
        // centering in TableRowSpan.draw ((rowHeight - cellHeight) / 4), using this cell's height as
        // the stand-in for the row's tallest cell (exact for that cell; shorter cells are usually
        // single-line, where the line lookup clamps and the excess is harmless).
        int cellPadding = (row.cellWidth() - cellLayout.getWidth()) / 2;
        int rowHeight = layout.getLineBottom(line) - layout.getLineTop(line);
        int heightDiff = (rowHeight - cellLayout.getHeight()) / 4;
        int cellLine =
                cellLayout.getLineForVertical(y - layout.getLineTop(line) - cellPadding - heightDiff);
        int cellOffset =
                cellLayout.getOffsetForHorizontal(cellLine, (x % row.cellWidth()) - cellPadding);
        CharSequence cellText = cellLayout.getText();
        if (cellText instanceof Spanned) {
            URLSpan[] cellLinks =
                    ((Spanned) cellText).getSpans(cellOffset, cellOffset, URLSpan.class);
            if (cellLinks.length > 0) {
                return cellLinks;
            }
        }
        return null;
    }
}
