package me.edgan.redditslide.util;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import org.jspecify.annotations.NullMarked;

/**
 * A scroll position that survives the list being rebuilt: the adapter position of the topmost
 * partially-visible row plus that row's exact pixel offset.
 *
 * <p>This is the save/restore pair that {@code SubmissionsView.onConfigurationChanged} worked out
 * for the span-count change, lifted here so every list screen restores the same way. Two details
 * in it are the whole reason it works and are easy to lose:
 *
 * <ul>
 *   <li>The offset is measured from {@code paddingTop}, because that is what
 *       {@code scrollToPositionWithOffset} measures from — an offset of 0 lands the row at
 *       {@code top == paddingTop}, not at {@code top == 0}.
 *   <li>It is the row's decorated top <em>minus its top margin</em>, not {@code child.getTop()}.
 *       That is what {@code OrientationHelper.getDecoratedStart} computes, and it is the
 *       coordinate both layout managers lay a row out by. Capture the raw top instead and every
 *       round trip lands the list exactly one decoration-plus-margin too low — a small constant
 *       drift (12px in this app's feed) that is identical every single time, which is what makes
 *       it a bug rather than a rounding artefact.
 *   <li>The scroll is deferred through one layout pass and then one more posted tick, and it has
 *       to be. Issuing it before the list lays out is tempting — a pending scroll costs no visible
 *       movement — but the offset is a pixel depth into a row whose height is not known until it
 *       has been measured, so an early scroll lands short by however much that row grows. Callers
 *       that must not show the intermediate frame hide the list until the callback runs rather
 *       than moving the scroll earlier.
 * </ul>
 */
@NullMarked
public final class ScrollAnchor {

    public static final int NO_POSITION = RecyclerView.NO_POSITION;

    /** Adapter position of the anchored row, or {@link #NO_POSITION} when nothing was anchored. */
    public final int position;

    /** The anchored row's top, relative to the RecyclerView's {@code paddingTop}. */
    public final int offset;

    public ScrollAnchor(int position, int offset) {
        this.position = position;
        this.offset = offset;
    }

    public boolean isValid() {
        return position != NO_POSITION;
    }

    /**
     * The topmost row that is at least partly inside the viewport, and how far its top sits from
     * {@code paddingTop}. Rows entirely above the padding are skipped: they are scrolled off, and
     * anchoring to one would restore the user further up the list than they left off.
     */
    public static ScrollAnchor capture(@Nullable RecyclerView rv) {
        if (rv == null) {
            return new ScrollAnchor(NO_POSITION, 0);
        }
        final RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (lm == null) {
            return new ScrollAnchor(NO_POSITION, 0);
        }
        final int paddingTop = rv.getPaddingTop();
        int anchor = NO_POSITION;
        int anchorTop = 0;
        for (int i = 0; i < lm.getChildCount(); i++) {
            final View child = lm.getChildAt(i);
            if (child == null) continue;
            if (decoratedEnd(lm, child) <= paddingTop) continue;
            final int childTop = decoratedStart(lm, child);
            if (anchor == NO_POSITION || childTop < anchorTop) {
                anchor = lm.getPosition(child);
                anchorTop = childTop;
            }
        }
        if (anchor == NO_POSITION) {
            return new ScrollAnchor(NO_POSITION, 0);
        }
        return new ScrollAnchor(anchor, anchorTop - paddingTop);
    }

    /**
     * Scrolls {@code rv} so that {@code position} sits {@code offset} pixels below its
     * {@code paddingTop}, once the pending layout has settled.
     *
     * @param afterScroll run on the same tick as the scroll — the point at which the list is at its
     *     restored position and is safe to show. Callers also use it to repair state a programmatic
     *     jump corrupts (the toolbar-hide handler's offset tracking). May be null, and is not run
     *     at all when there is nothing to scroll.
     */
    public static void apply(
            @Nullable RecyclerView rv,
            final int position,
            final int offset,
            @Nullable final Runnable afterScroll) {
        if (rv == null || position == NO_POSITION) {
            return;
        }
        final RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (lm == null) {
            return;
        }
        final RecyclerView target = rv;
        target.addOnLayoutChangeListener(
                new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(
                            View v,
                            int left,
                            int top,
                            int right,
                            int bottom,
                            int oldLeft,
                            int oldTop,
                            int oldRight,
                            int oldBottom) {
                        target.removeOnLayoutChangeListener(this);
                        target.post(
                                () -> {
                                    scrollTo(lm, position, offset);
                                    if (afterScroll != null) {
                                        afterScroll.run();
                                    }
                                });
                    }
                });
        target.requestLayout();
    }

    /** The coordinate {@code scrollToPositionWithOffset} places at {@code paddingTop + offset}. */
    private static int decoratedStart(RecyclerView.LayoutManager lm, View child) {
        return lm.getDecoratedTop(child) - marginsOf(child).topMargin;
    }

    private static int decoratedEnd(RecyclerView.LayoutManager lm, View child) {
        return lm.getDecoratedBottom(child) + marginsOf(child).bottomMargin;
    }

    private static ViewGroup.MarginLayoutParams marginsOf(View child) {
        return (ViewGroup.MarginLayoutParams) child.getLayoutParams();
    }

    /**
     * {@link #apply}, with the list hidden until it is in the right place.
     *
     * <p>The accurate placement can only happen after a layout — the offset is a pixel depth into a
     * row whose height is not known until it has been measured — and a list that lays out at the
     * top and then jumps reads as the screen scrolling itself. Hiding it for that one frame is what
     * makes a restored screen simply appear where it was.
     */
    public static void applyHidden(
            @Nullable RecyclerView rv,
            int position,
            int offset,
            @Nullable final Runnable afterScroll) {
        if (rv == null || position == NO_POSITION) {
            return;
        }
        final RecyclerView target = rv;
        hideUntilSettled(target, REVEAL_TIMEOUT_MS);
        apply(
                target,
                position,
                offset,
                () -> {
                    target.setVisibility(View.VISIBLE);
                    if (afterScroll != null) {
                        afterScroll.run();
                    }
                });
    }

    /**
     * Restores a {@code ScrollView}'s vertical offset once its content has been laid out, without
     * showing it at the top first. A scroll offset set before layout does not stick: there is
     * nothing measured yet to scroll within.
     */
    public static void applyScrollY(@Nullable final View scroller, final int scrollY) {
        if (scroller == null || scrollY <= 0) {
            return;
        }
        hideUntilSettled(scroller, REVEAL_TIMEOUT_MS);
        scroller.addOnLayoutChangeListener(
                new View.OnLayoutChangeListener() {
                    @Override
                    public void onLayoutChange(
                            View v,
                            int left,
                            int top,
                            int right,
                            int bottom,
                            int oldLeft,
                            int oldTop,
                            int oldRight,
                            int oldBottom) {
                        scroller.removeOnLayoutChangeListener(this);
                        scroller.post(
                                () -> {
                                    scroller.scrollTo(0, scrollY);
                                    scroller.setVisibility(View.VISIBLE);
                                });
                    }
                });
        scroller.requestLayout();
    }

    /**
     * Hides {@code v} and arms a timer to show it again regardless. INVISIBLE rather than GONE so
     * nothing around it moves, and the timer because a view that never lays out would otherwise
     * stay blank forever — far worse than a visible jump.
     *
     * <p>Public because a screen whose content is fetched rather than restored from cache has to
     * hide it from the moment it is created, not from the moment the content arrives: otherwise it
     * draws the top of the list first and then jumps, which is the very thing being avoided. Such a
     * screen wants {@link #FETCH_REVEAL_TIMEOUT_MS}, since what it is waiting on is a request.
     */
    public static void hideUntilSettled(final View v, final long timeoutMs) {
        v.setVisibility(View.INVISIBLE);
        v.postDelayed(() -> v.setVisibility(View.VISIBLE), timeoutMs);
    }

    /**
     * How long a view stays hidden waiting for a position restored from cache. A backstop against a
     * layout that never arrives, not a schedule.
     */
    private static final long REVEAL_TIMEOUT_MS = 1000L;

    /**
     * The same backstop for a screen waiting on a network fetch before it can be positioned. Longer
     * because the wait genuinely is; the refresh spinner sits outside the hidden view, so the
     * screen reads as loading rather than as blank.
     */
    public static final long FETCH_REVEAL_TIMEOUT_MS = 8000L;

    /**
     * The two layout managers this app uses both offer {@code scrollToPositionWithOffset}, but on
     * neither a shared supertype nor a shared interface, so the branch cannot be avoided. Anything
     * else falls back to a plain snap-to-top, which is still better than not scrolling at all.
     */
    private static void scrollTo(RecyclerView.LayoutManager lm, int position, int offset) {
        if (lm instanceof StaggeredGridLayoutManager) {
            ((StaggeredGridLayoutManager) lm).scrollToPositionWithOffset(position, offset);
        } else if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(position, offset);
        } else {
            lm.scrollToPosition(position);
        }
    }
}
