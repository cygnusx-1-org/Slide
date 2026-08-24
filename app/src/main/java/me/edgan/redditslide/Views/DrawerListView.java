package me.edgan.redditslide.Views;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ListView;
import org.jspecify.annotations.NullMarked;

/**
 * The drawer's subreddit list, with one guard: it will not scroll itself while it is laying out.
 *
 * <p>The drawer list carries focusable children -- the search box in its header view, wrapped in a
 * {@code focusableInTouchMode} container. {@link ListView#layoutChildren()} takes focus back to
 * itself part-way through a pass, and because this list is not focusable the focus lands on one of
 * those descendants instead. That child's {@code onFocusChanged} asks to be scrolled into view,
 * which the list obliges by detaching children -- in the middle of the pass that is still walking
 * them. The scrap loop a few lines later then reads a child that is no longer there:
 *
 * <pre>
 * NullPointerException: 'ViewGroup$LayoutParams View.getLayoutParams()' on a null object reference
 *     at android.widget.AbsListView$RecycleBin.addScrapView
 *     at android.widget.ListView.layoutChildren
 *     at androidx.drawerlayout.widget.DrawerLayout.onLayout
 * </pre>
 *
 * <p>Declining the request while laying out breaks that re-entry. Returning false is honest -- no
 * scrolling happened -- and the position the child wanted is what the pass in progress is already
 * computing. Requests from outside a layout are handled normally.
 */
@NullMarked
public class DrawerListView extends ListView {

    private boolean inLayout;

    public DrawerListView(Context context) {
        super(context);
    }

    public DrawerListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DrawerListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        inLayout = true;
        try {
            super.onLayout(changed, l, t, r, b);
        } finally {
            inLayout = false;
        }
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
        if (inLayout) {
            return false;
        }
        return super.requestChildRectangleOnScreen(child, rect, immediate);
    }
}
