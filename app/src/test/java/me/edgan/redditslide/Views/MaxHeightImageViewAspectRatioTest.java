package me.edgan.redditslide.Views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The slot a feed lead image reserves before its bitmap arrives.
 *
 * <p>The height is derived from the measured width and the post's aspect ratio at measure time, so
 * an image that loads a second later drops into a slot that is already the right size. That was the
 * fix for the feed jumping while scrolling up, and it has two halves: computing the height, and
 * asking for a new layout pass when the ratio changes on a recycled view. The second half is a
 * bare side effect -- {@code requestLayout()} returns nothing and changes no field this class
 * exposes -- so nothing observed it, and deleting the call left not just the unit suite but
 * {@code :app:checks} with its screenshot goldens green: a golden inflates a fresh view and
 * measures it once, which is exactly the case a missing relayout does not break.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class MaxHeightImageViewAspectRatioTest {

    private static final int WIDTH = 600;

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    /** A view that has been through a full measure/layout pass, so nothing is pending on it. */
    private MaxHeightImageView settledView() {
        FrameLayout parent = new FrameLayout(context);
        MaxHeightImageView view = new MaxHeightImageView(context);
        parent.addView(
                view, new FrameLayout.LayoutParams(WIDTH, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST));
        parent.layout(0, 0, WIDTH, parent.getMeasuredHeight());
        assertFalse("the view starts settled", view.isLayoutRequested());
        return view;
    }

    private static int measuredHeightAt(MaxHeightImageView view, int width) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return view.getMeasuredHeight();
    }

    /**
     * The half nothing watched. A recycled row is handed a new post's ratio while it is already
     * laid out; without this the row keeps the previous post's height until something else happens
     * to trigger a pass, which is the jump the reservation exists to prevent.
     */
    @Test
    public void aNewRatioAsksForANewLayoutPass() {
        MaxHeightImageView view = settledView();

        view.setAspectRatio(0.75);

        assertTrue(
                "a changed ratio has to request a layout or the slot keeps the old height",
                view.isLayoutRequested());
    }

    /** The same ratio changes nothing, so it must not schedule a pass on every rebind. */
    @Test
    public void theSameRatioAgainAsksForNothing() {
        MaxHeightImageView view = settledView();
        view.setAspectRatio(0.75);
        // Settle again with the ratio in place.
        view.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, WIDTH, view.getMeasuredHeight());
        assertFalse(view.isLayoutRequested());

        view.setAspectRatio(0.75);

        assertFalse("an unchanged ratio must not schedule a pass", view.isLayoutRequested());
    }

    /** The other half: the reserved height is width times ratio. */
    @Test
    public void theReservedHeightIsTheWidthTimesTheRatio() {
        MaxHeightImageView view = new MaxHeightImageView(context);
        view.setAspectRatio(0.5);

        assertEquals(WIDTH / 2, measuredHeightAt(view, WIDTH));
    }

    /** A very tall image is capped rather than reserving a slot taller than the cap. */
    @Test
    public void aVeryTallRatioIsCappedAtTheMaximum() {
        MaxHeightImageView view = new MaxHeightImageView(context);
        view.setAspectRatio(50.0);

        assertEquals(MaxHeightImageView.maxHeight, measuredHeightAt(view, WIDTH));
    }
}
