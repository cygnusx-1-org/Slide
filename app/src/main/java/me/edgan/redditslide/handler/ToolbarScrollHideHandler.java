package me.edgan.redditslide.handler;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import me.edgan.redditslide.Activities.BaseActivity;
import me.edgan.redditslide.HibernateState;

/**
 * Created by ccrama on 2/18/2016. Adapted from
 * http://rylexr.tinbytes.com/2015/04/27/how-to-hideshow-android-toolbar-when-scrolling-google-play-musics-behavior/
 */
public class ToolbarScrollHideHandler extends RecyclerView.OnScrollListener {

    public int verticalOffset;
    public boolean reset = false;
    Toolbar tToolbar;
    View mAppBar;
    /** Both stay null when built with the two-arg constructor; use sites already guard. */
    @Nullable View extra;

    @Nullable View opposite;
    boolean scrollingUp;

    public ToolbarScrollHideHandler(Toolbar t, View appBar) {
        tToolbar = t;
        mAppBar = appBar;
        registerWith(appBar);
    }

    public ToolbarScrollHideHandler(
            Toolbar t, View appBar, View extra, @Nullable View opposite) {
        tToolbar = t;
        mAppBar = appBar;
        this.extra = extra;
        this.opposite = opposite;
        registerWith(appBar);
    }

    /**
     * Tells the screen this bar belongs to that it exists, so a hibernate restore can settle it
     * the way the recorded scroll position left it.
     *
     * <p>The activity cannot find its handlers on its own: they are attached to a RecyclerView,
     * which keeps no public list of its scroll listeners, and on a tabbed screen they are built by
     * pages the activity does not hold references to.
     */
    private void registerWith(View appBar) {
        Context context = appBar.getContext();
        while (!(context instanceof BaseActivity) && context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        if (context instanceof BaseActivity) {
            ((BaseActivity) context).registerScrollHideHandler(this);
        }
    }

    @Override
    public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            if (reset) {
                verticalOffset = 0;
                reset = false;
            }
            if (scrollingUp) {
                if (verticalOffset > tToolbar.getHeight()) {
                    toolbarAnimateHide();
                } else {
                    toolbarAnimateShow();
                }
                if (opposite != null)
                    if (verticalOffset > opposite.getHeight()) {
                        oppositeAnimateHide();
                    } else {
                        oppositeAnimateShow();
                    }
            } else {
                if (mAppBar.getTranslationY() < tToolbar.getHeight() * -0.6
                        && verticalOffset > tToolbar.getHeight()) {
                    toolbarAnimateHide();
                } else {
                    toolbarAnimateShow();
                }
                if (opposite != null)
                    if (opposite.getTranslationY() < opposite.getHeight() * -0.6
                            && verticalOffset > opposite.getHeight()) {
                        oppositeAnimateHide();
                    } else {
                        oppositeAnimateShow();
                    }
            }
            // The list has come to rest somewhere the user might leave from, so record where
            // that is. Here rather than in each fragment: the lifecycle callbacks alone only
            // ever record the moment the user last changed screens, so a process killed for
            // memory with no callback at all came back to the right screen at whatever position
            // it held several screens ago. Every scrolling list in the app hangs its toolbar off
            // this handler, so this is the one place that sees all of them settle -- comment
            // trees and the inbox included, which had no settle capture of their own.
            //
            // When the toolbar animation ends, not now. The recorded "toolbar hidden" flag is
            // read off the header's translationY, and the animation the branches above just
            // started has not moved it yet: capturing at this line records the state the toolbar
            // is leaving, and a kill before the next capture restores the list at the right post
            // with the toolbar the wrong way round. The end action is dropped if a new scroll
            // cancels the animation, and the settle that follows that scroll captures instead.
            // An unchanged snapshot is not rewritten, so a settle where nothing moved costs a
            // comparison.
            final Context settled = recyclerView.getContext();
            mAppBar.animate()
                    .withEndAction(
                            new Runnable() {
                                @Override
                                public void run() {
                                    HibernateState.onContentSettled(settled);
                                }
                            });
        }
    }

    @Override
    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
        if (verticalOffset == 0
                && dy < 0) { // if scrolling begins halfway through an adapter, don't treat it
            // like going negative and instead reset the start position to 0
            dy = 0;
        }
        verticalOffset += dy;
        scrollingUp = dy > 0;
        int toolbarYOffset = (int) (dy - mAppBar.getTranslationY());
        mAppBar.animate().cancel();
        if (scrollingUp) {
            if (toolbarYOffset < tToolbar.getHeight()) {
                mAppBar.setTranslationY(-toolbarYOffset);
                if (extra != null) extra.setTranslationY(-toolbarYOffset);
            } else {
                mAppBar.setTranslationY(-tToolbar.getHeight());
                if (extra != null) extra.setTranslationY(-tToolbar.getHeight());
            }
        } else {
            if (toolbarYOffset < 0) {
                toolbarShow();
                if (extra != null) extra.setTranslationY(0);
            } else {
                mAppBar.setTranslationY(-toolbarYOffset);
                if (extra != null) extra.setTranslationY(-toolbarYOffset);
            }
        }
        if (opposite != null) {
            toolbarYOffset = (int) (dy + opposite.getTranslationY());
            opposite.animate().cancel();
            if (scrollingUp) {
                opposite.setTranslationY(Math.min(toolbarYOffset, opposite.getHeight()));
            } else {
                opposite.setTranslationY(Math.max(toolbarYOffset, 0));
            }
        }
    }

    public void toolbarShow() {
        mAppBar.setTranslationY(0);
    }

    /**
     * Puts the toolbar straight into the state a scroll would have left it in, with no animation,
     * and makes {@link #verticalOffset} agree with it.
     *
     * <p>For a list that has been jumped programmatically to somewhere it was before — a restored
     * scroll position — rather than scrolled there. The jump pushes one large {@code dy} through
     * {@link #onScrolled} and corrupts the offset tracking, which is what {@link #reset} exists
     * for; but {@code reset} zeroes the offset, so the toolbar would spring back into view at the
     * user's first touch even though they are a long way down the list. Setting both halves here
     * leaves the handler consistent instead.
     *
     * @param hidden the state the toolbar was in when the position was recorded.
     */
    public void settleAfterJump(boolean hidden) {
        reset = false;
        if (hidden) {
            verticalOffset = tToolbar.getHeight() + 1;
            mAppBar.setTranslationY(-tToolbar.getHeight());
            if (extra != null) extra.setTranslationY(-tToolbar.getHeight());
            // The bar at the other end moves with the toolbar and is tracked by the same offset,
            // so it has to be settled with it. Left out, a comment screen restored with its
            // toolbar hidden keeps the comment navigation bar showing, and one restored with the
            // toolbar out keeps it hidden -- and neither corrects itself, because a programmatic
            // jump never reaches the idle callback that would.
            if (opposite != null) opposite.setTranslationY(opposite.getHeight());
        } else {
            verticalOffset = 0;
            toolbarShow();
            if (extra != null) extra.setTranslationY(0);
            if (opposite != null) opposite.setTranslationY(0);
        }
    }

    private void toolbarAnimateShow() {
        toolbarAnimate(0);
    }

    private void toolbarAnimateHide() {
        toolbarAnimate(-tToolbar.getHeight());
    }

    private void toolbarAnimate(final int i) {
        animate(mAppBar, i);
        if (extra != null) animate(extra, i);
    }

    private void oppositeAnimateShow() {
        oppositeAnimate(0);
    }

    private void oppositeAnimateHide() {
        if (opposite != null) oppositeAnimate(opposite.getHeight());
    }

    private void oppositeAnimate(final int i) {
        if (opposite != null) animate(opposite, i);
    }

    private void animate(final View v, final int i) {
        v.animate().translationY(i).setInterpolator(new LinearInterpolator()).setDuration(180);
    }
}
