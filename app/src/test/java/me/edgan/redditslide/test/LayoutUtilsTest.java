package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.LayoutUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Covers the row sizing that keeps a video/GIF entry in a vertical album or gallery from taking a
 * whole screenful. submission_gifcard_album.xml is match_parent because it doubles as a full-screen
 * pager page; as a RecyclerView row that MATCH_PARENT becomes EXACTLY(list height).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class LayoutUtilsTest {

    private static final int ROW_WIDTH = 1080;
    private static final int LIST_HEIGHT = 2280;

    @Test
    public void landscapeMediaGetsProportionalHeight() {
        assertEquals(608, LayoutUtils.getMediaRowHeight(1920, 1080, 1080, 0));
    }

    @Test
    public void portraitMediaGetsProportionalHeight() {
        assertEquals(1920, LayoutUtils.getMediaRowHeight(1080, 1920, 1080, 0));
    }

    @Test
    public void squareMediaGetsSquareHeight() {
        assertEquals(1080, LayoutUtils.getMediaRowHeight(600, 600, 1080, 0));
    }

    @Test
    public void veryTallMediaIsCappedAtOneScreen() {
        // 1080x5000 would want 5000px; a single row may never exceed the list's height.
        assertEquals(
                LIST_HEIGHT, LayoutUtils.getMediaRowHeight(1080, 5000, ROW_WIDTH, LIST_HEIGHT));
    }

    @Test
    public void heightUnderTheCapIsNotClamped() {
        assertEquals(608, LayoutUtils.getMediaRowHeight(1920, 1080, ROW_WIDTH, LIST_HEIGHT));
    }

    @Test
    public void unknownMediaSizeFallsBackToSixteenByNine() {
        final int sixteenByNine = 608;
        assertEquals(sixteenByNine, LayoutUtils.getMediaRowHeight(0, 0, ROW_WIDTH, 0));
        assertEquals(sixteenByNine, LayoutUtils.getMediaRowHeight(1080, 0, ROW_WIDTH, 0));
        assertEquals(sixteenByNine, LayoutUtils.getMediaRowHeight(0, 1920, ROW_WIDTH, 0));
        assertEquals(sixteenByNine, LayoutUtils.getMediaRowHeight(-1, -1, ROW_WIDTH, 0));
    }

    @Test
    public void unknownRowWidthYieldsNoHeight() {
        assertEquals(0, LayoutUtils.getMediaRowHeight(1920, 1080, 0, LIST_HEIGHT));
    }

    @Test
    public void skewedRatioIsClampedRatherThanOverflowing() {
        // media_metadata's s.x/s.y are copied in unchecked, so a malformed entry can ask for a
        // height that does not fit in an int. It must clamp, never wrap into a negative — -1 and -2
        // are the MATCH_PARENT/WRAP_CONTENT sentinels.
        assertEquals(
                LIST_HEIGHT, LayoutUtils.getMediaRowHeight(1, 2000000000, ROW_WIDTH, LIST_HEIGHT));
        assertTrue(LayoutUtils.getMediaRowHeight(1, Integer.MAX_VALUE, ROW_WIDTH, 0) > 0);
    }

    @Test
    public void gifRowInflatesAsMatchParentAndIsResizedToTheVideo() {
        final RecyclerView list = newList();
        final View row = inflateGifRow(list);

        // This MATCH_PARENT is what LinearLayoutManager turns into EXACTLY(list height).
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, row.getLayoutParams().height);
        assertTrue(row.getLayoutParams() instanceof ViewGroup.MarginLayoutParams);

        LayoutUtils.applyAlbumRowSize(row, list, ROW_WIDTH, 1920, 1080, 0);

        assertEquals(608, row.getLayoutParams().height);
    }

    @Test
    public void resizedGifRowGetsTheSameGapAsAnImageRow() {
        final RecyclerView list = newList();
        final View row = inflateGifRow(list);

        LayoutUtils.applyAlbumRowSize(row, list, ROW_WIDTH, 1920, 1080, 0);

        final ViewGroup.MarginLayoutParams lp =
                (ViewGroup.MarginLayoutParams) row.getLayoutParams();
        assertEquals(
                row.getResources().getDimensionPixelSize(R.dimen.album_row_margin_top),
                lp.topMargin);
        assertEquals(
                row.getResources().getDimensionPixelSize(R.dimen.album_row_margin_bottom),
                lp.bottomMargin);
    }

    @Test
    public void captionHeightIsAddedOnTopOfTheVideoHeight() {
        final RecyclerView list = newList();
        final View row = inflateGifRow(list);

        // @id/imagearea sits below @id/galleryCaption, so a caption must not shorten the video.
        LayoutUtils.applyAlbumRowSize(row, list, ROW_WIDTH, 1920, 1080, 120);

        assertEquals(608 + 120, row.getLayoutParams().height);
    }

    @Test
    public void captionCannotPushTheRowPastOneScreen() {
        final RecyclerView list = newList();
        final View row = inflateGifRow(list);

        // A portrait video already fills most of the list; the caption must not grow the row
        // beyond a screenful on top of that.
        LayoutUtils.applyAlbumRowSize(row, list, ROW_WIDTH, 1080, 1920, 600);

        assertEquals(LIST_HEIGHT, row.getLayoutParams().height);
    }

    @Test
    public void rowIsSizedForTheWidthTheCallerPassesIn() {
        final RecyclerView list = newList();
        final View row = inflateGifRow(list);

        // The caller resolves the width once and shares it with whatever else it measures against
        // the row, so the passed width wins over anything the list itself reports.
        LayoutUtils.applyAlbumRowSize(row, list, ROW_WIDTH / 2, 1920, 1080, 0);

        assertEquals(304, row.getLayoutParams().height);
    }

    @Test
    public void paddingWiderThanTheListFallsBackToTheWindowWidth() {
        final RecyclerView list = newUnlaidOutList();
        list.setPadding(ROW_WIDTH, 0, ROW_WIDTH, 0);
        list.measure(
                View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(LIST_HEIGHT, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, ROW_WIDTH, LIST_HEIGHT);
        final View row = inflateGifRow(list);

        // A non-positive content width must not collapse the row to nothing.
        final int rowWidth = LayoutUtils.getAlbumRowWidth(list, list.getContext());
        assertEquals(row.getResources().getDisplayMetrics().widthPixels, rowWidth);
        LayoutUtils.applyAlbumRowSize(row, list, rowWidth, 1920, 1080, 0);
        assertTrue(row.getLayoutParams().height > 0);
    }

    @Test
    public void rebindingIsRequestedOnlyWhenTheWidthChanges() {
        final int[] rebinds = {0};
        final View.OnLayoutChangeListener listener =
                LayoutUtils.rebindOnWidthChange(() -> rebinds[0]++);
        final RecyclerView list = newList();

        // Height-only change: nothing to recompute.
        listener.onLayoutChange(list, 0, 0, ROW_WIDTH, 100, 0, 0, ROW_WIDTH, 200);
        drainMainLooper();
        assertEquals(0, rebinds[0]);

        // Width change, e.g. a rotation in an activity that handles configChanges itself.
        listener.onLayoutChange(list, 0, 0, LIST_HEIGHT, 100, 0, 0, ROW_WIDTH, 100);
        drainMainLooper();
        assertEquals(1, rebinds[0]);
    }

    @Test
    public void aRowWithNoLayoutParamsIsLeftAloneRatherThanThrowing() {
        // Inflated with no parent, so there are no LayoutParams to size. The row holders always have
        // them; anything else has no parent yet, and inventing params here would mean picking a
        // concrete subclass that then ClassCastExceptions in whatever parent it really goes into.
        final RecyclerView list = newList();
        final View row =
                LayoutInflater.from(list.getContext())
                        .inflate(R.layout.submission_gifcard_album, null, false);
        assertNull(row.getLayoutParams());

        LayoutUtils.applyAlbumRowSize(row, list, ROW_WIDTH, 1920, 1080, 0);

        assertNull(row.getLayoutParams());
    }

    @Test
    public void anOverlaidListIsPaddedByTheOverlayAndScrollsThroughIt() {
        final RecyclerView list = newList();
        list.setPadding(7, 11, 13, 0);
        assertTrue(list.getClipToPadding());

        LayoutUtils.insetForOverlay(list, 240);

        // Only the bottom moves; the other three are whatever the layout asked for.
        assertEquals(7, list.getPaddingLeft());
        assertEquals(11, list.getPaddingTop());
        assertEquals(13, list.getPaddingRight());
        assertEquals(240, list.getPaddingBottom());
        // Off, so rows pass under the translucent panel instead of stopping above it.
        assertFalse(list.getClipToPadding());
    }

    @Test
    public void reInsettingAListReplacesThePadRatherThanAddingToIt() {
        final RecyclerView list = newList();

        // The panel is measured again on a later layout pass; the second answer must not stack.
        LayoutUtils.insetForOverlay(list, 240);
        LayoutUtils.insetForOverlay(list, 180);

        assertEquals(180, list.getPaddingBottom());
    }

    @Test
    public void toolbarOffsetIsTheToolbarHeightOrZeroWithoutOne() {
        final TestActivity activity = createActivity();

        // No @id/toolbar in the content view: nothing overlays the list, so nothing to scroll clear
        // of. This is the case the grid dialogs used to dereference straight through.
        assertEquals(0, LayoutUtils.getToolbarOffset(activity));

        final View toolbar = new View(activity);
        toolbar.setId(R.id.toolbar);
        activity.setContentView(toolbar);
        toolbar.measure(
                View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(56, View.MeasureSpec.EXACTLY));
        toolbar.layout(0, 0, ROW_WIDTH, 56);

        assertEquals(56, LayoutUtils.getToolbarOffset(activity));
    }

    @Test
    public void aNullClickListenerLeavesAViewClickable() {
        // The platform behaviour the inert-row guards depend on: View.setOnClickListener turns
        // clickable on before it stores the listener, null included. So setClickable(false) has to
        // come after it, not before, or an unplayable row keeps swallowing taps.
        final View row = new View(createActivity());
        row.setClickable(false);

        row.setOnClickListener(null);

        assertTrue(row.isClickable());

        row.setClickable(false);
        assertFalse(row.isClickable());
    }

    @Test
    public void rowWidthFallsBackToTheWindowWidthBeforeLayout() {
        final RecyclerView list = newUnlaidOutList();
        final View row = inflateGifRow(list);

        // The first bind can run before the list has been laid out, so its width is still 0.
        assertEquals(0, list.getWidth());
        assertEquals(
                row.getResources().getDisplayMetrics().widthPixels,
                LayoutUtils.getAlbumRowWidth(list, list.getContext()));
        assertEquals(
                row.getResources().getDisplayMetrics().widthPixels,
                LayoutUtils.getAlbumRowWidth(null, row.getContext()));
    }

    /** A list already laid out at {@link #ROW_WIDTH} x {@link #LIST_HEIGHT}. */
    private static RecyclerView newList() {
        final RecyclerView list = newUnlaidOutList();
        list.measure(
                View.MeasureSpec.makeMeasureSpec(ROW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(LIST_HEIGHT, View.MeasureSpec.EXACTLY));
        list.layout(0, 0, ROW_WIDTH, LIST_HEIGHT);
        return list;
    }

    private static RecyclerView newUnlaidOutList() {
        // A themed activity, not the bare application context: the ExoVideoView inside the row
        // builds a PlayerControlView, which resolves ?attr/colorAccent while inflating.
        final Context context = createActivity();
        final RecyclerView list = new RecyclerView(context);
        // A LayoutManager is required for inflate(..., list, false) to produce
        // RecyclerView.LayoutParams; every album host sets one before setAdapter.
        list.setLayoutManager(new LinearLayoutManager(context));
        return list;
    }

    private static TestActivity createActivity() {
        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        final TestActivity activity = controller.get();
        activity.setTheme(R.style.Theme_LIGHT);
        controller.setup();
        return activity;
    }

    private static View inflateGifRow(final RecyclerView list) {
        return LayoutInflater.from(list.getContext())
                .inflate(R.layout.submission_gifcard_album, list, false);
    }

    private static void drainMainLooper() {
        ShadowLooper.shadowMainLooper().idle();
    }

    public static class TestActivity extends AppCompatActivity {}
}
