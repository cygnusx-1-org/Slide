package me.edgan.redditslide.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.viewpager.widget.ViewPager;
import org.jspecify.annotations.NullMarked;

/**
 * A ViewPager that blocks swiping to the previous page (i.e. "left page") only if you're currently
 * on a specified 'entryPageIndex'.
 */
@NullMarked
public class CustomViewPager extends ViewPager {

    private int entryPageIndex = 0;

    // We'll track the user's initial down position
    private float initialX = 0f;
    // A small threshold to detect if the user is truly swiping horizontally
    private static final float SWIPE_THRESHOLD = 50f;

    public CustomViewPager(Context context) {
        super(context);
    }

    public CustomViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Set which page index is considered the 'entry' page that should not allow swiping left. E.g.,
     * if the user started on page = 2, pass 2 here.
     */
    public void setEntryPageIndex(int index) {
        this.entryPageIndex = index;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // If we're NOT on the entry page, do normal behavior:
        if (getCurrentItem() != entryPageIndex) {
            return super.onInterceptTouchEvent(ev);
        }

        // If we ARE on the entry page, check direction:
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = ev.getX();
                // Let ViewPager pick this event up normally:
                return super.onInterceptTouchEvent(ev);

            case MotionEvent.ACTION_MOVE:
                float diffX = ev.getX() - initialX;
                // A positive diffX means the user is moving
                // their finger from left to right (attempting
                // to go to the "previous" page).
                if (diffX > SWIPE_THRESHOLD) {
                    // Block this gesture: do NOT intercept
                    // => means user cannot drag to previous page
                    return false;
                }
                break;
        }
        // Otherwise do normal
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (getCurrentItem() != entryPageIndex) {
            return super.onTouchEvent(ev);
        }

        // If on the entry page, check direction again
        switch (ev.getAction()) {
            case MotionEvent.ACTION_MOVE:
                float diffX = ev.getX() - initialX;
                // If user tries to move left->right beyond threshold,
                // block the actual scrolling
                if (diffX > SWIPE_THRESHOLD) {
                    return false;
                }
                break;
        }
        return super.onTouchEvent(ev);
    }
}
