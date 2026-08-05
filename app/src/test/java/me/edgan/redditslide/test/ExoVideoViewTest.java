package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerControlView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.ExoVideoView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Covers how ExoVideoView positions and manages its transport controls. The controls used to be added
 * with no LayoutParams at all, which left them at the top left instead of above the
 * {@code @id/gifheader} button bar that every host pins to the bottom.
 */
@OptIn(markerClass = UnstableApi.class)
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ExoVideoViewTest {

    /**
     * The margin setupUI() applies and the button bar it has to clear, both read from resources
     * rather than restated here: the two dimens are what the view and the host layouts share, so
     * copying their values into the test would let all three drift apart while it still passed.
     */
    private static int controlsBottomMargin(final AppCompatActivity activity) {
        return activity.getResources()
                .getDimensionPixelSize(R.dimen.video_controls_bottom_margin);
    }

    private static int buttonBarHeight(final AppCompatActivity activity) {
        return activity.getResources().getDimensionPixelSize(R.dimen.gif_header_height);
    }

    @Test
    public void controlsAreNotBuiltUntilAttached() {
        final TestActivity activity = createActivity();
        final ExoVideoView view = new ExoVideoView(activity);

        // Deferred on purpose: a list row only marks itself after inflation, so building the
        // controls in the constructor would mean building them just to throw them away.
        assertNull(findControls(view));
    }

    @Test
    public void attachedControlsAreBottomAlignedClearOfTheButtonBar() {
        final TestActivity activity = createActivity();
        final ExoVideoView view = attach(activity, new ExoVideoView(activity));

        final PlayerControlView controls = findControls(view);
        assertNotNull("attaching should build the transport controls", controls);

        final RelativeLayout.LayoutParams lp =
                (RelativeLayout.LayoutParams) controls.getLayoutParams();
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, lp.width);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, lp.height);
        assertEquals(
                RelativeLayout.TRUE, lp.getRules()[RelativeLayout.ALIGN_PARENT_BOTTOM]);
        assertEquals(controlsBottomMargin(activity), lp.bottomMargin);
    }

    @Test
    public void controlsClearTheButtonBarTheHostsPinToTheBottom() {
        final TestActivity activity = createActivity();
        // The margin only does its job if it exceeds the bar it has to clear; activity_media.xml and
        // submission_gifcard_album.xml both pin @id/gifheader at @dimen/gif_header_height.
        assertTrue(controlsBottomMargin(activity) > buttonBarHeight(activity));
    }

    @Test
    public void videoFrameStaysBelowTheControlsAcrossAReattach() {
        final TestActivity activity = createActivity();
        final ExoVideoView view = attach(activity, new ExoVideoView(activity));

        // On a first attach this holds however the frame is added, since it goes into an empty view
        // group; asserted anyway to pin the invariant.
        assertFrameIsBelowControls(view);

        // The reattach is the half that constrains addView(frame, 0): setupPlayer() runs again with
        // the controls already present, and appending the new frame would cover them.
        reattach(view);
        assertFrameIsBelowControls(view);
    }

    @Test
    public void reattachRepointsTheControlsAtTheNewPlayer() {
        final TestActivity activity = createActivity();
        final ExoVideoView view = attach(activity, new ExoVideoView(activity));

        final PlayerControlView controls = findControls(view);
        assertNotNull(controls);
        final Player original = controls.getPlayer();
        assertNotNull(original);

        // Detaching releases the player and reattaching builds another one; the controls have to
        // follow, or they keep driving the released instance.
        reattach(view);

        assertNotNull(controls.getPlayer());
        assertNotSame(original, controls.getPlayer());
    }

    @Test
    public void listRowsNeverBuildControls() {
        final TestActivity activity = createActivity();
        final ExoVideoView view = new ExoVideoView(activity);
        view.markVerticalListRow();

        attach(activity, view);

        assertNull("a list row opens MediaView on tap and has no use for controls",
                findControls(view));
    }

    @Test
    public void aLoadArrivingAfterAFailureRebuildsTheReleasedPlayer() {
        final TestActivity activity = createActivity();
        final ExoVideoView view = attach(activity, new ExoVideoView(activity));

        // What a failed load leaves behind: AsyncLoadGif's failure path calls stop(), which releases
        // and nulls the player, and then lets the row retry. The row is still attached, and a rebind
        // is not a detach — a scrapped RecyclerView row loses its parent without
        // onDetachedFromWindow, so nothing else is going to rebuild this.
        view.stop();
        assertFalse(view.hasPlayer());

        // The retry arriving. The uri is null because building a media source needs Reddit's OkHttp
        // client and its on-disk video cache, which is not what this is about: the rebuild happens
        // first, and without it the retried load is dropped and the row's progress bar spins for
        // good over media that never arrives.
        view.setVideoURI(null, ExoVideoView.VideoType.STANDARD, null);

        assertTrue(view.hasPlayer());
    }

    @Test
    public void aDetachedViewIsNotGivenAPlayerNothingWouldRelease() {
        final TestActivity activity = createActivity();
        final ExoVideoView view = new ExoVideoView(activity);
        view.stop();

        // Off screen there is nothing to render and nobody to release it: the attach that puts this
        // view back on screen builds a player itself.
        view.setVideoURI(null, ExoVideoView.VideoType.STANDARD, null);

        assertFalse(view.hasPlayer());
    }

    @Test
    public void aHostThatDoesNotAskForScrubbingDoesNotGetIt() {
        // The gesture used to be on everywhere except a hard-coded list of gallery activity names,
        // which meant it was silently live in every host that happened not to be on the list — and
        // those are all hosts where a horizontal drag already means something: Shadowbox and the
        // comment thread page between posts, the peek views have nothing to seek. Off until asked
        // for is the whole point of the change, so it is what gets pinned.
        final TestActivity activity = createActivity();
        final ExoVideoView view = attach(activity, new ExoVideoView(activity));

        assertNotNull("the gate below is meaningless without controls", findControls(view));
        assertFalse(view.canScrub(false));
    }

    @Test
    public void aHostThatAsksForScrubbingGetsIt() {
        final TestActivity activity = createActivity();
        final ExoVideoView view = attach(activity, new ExoVideoView(activity));

        // MediaView, the full-screen viewer, is the only caller.
        view.setScrubEnabled(true);

        assertTrue(view.canScrub(false));
    }

    @Test
    public void aListRowDoesNotScrubEvenIfItAsksTo() {
        final TestActivity activity = createActivity();
        final ExoVideoView view = new ExoVideoView(activity);
        view.markVerticalListRow();
        view.setScrubEnabled(true);

        attach(activity, view);

        // A row builds no controls, and scrubbing without a seekbar to move would be invisible
        // besides stealing the vertical list's drag.
        assertFalse(view.canScrub(false));
    }

    @Test
    public void scrubbingStandsAsideForAPinch() {
        final TestActivity activity = createActivity();
        final ExoVideoView view = attach(activity, new ExoVideoView(activity));
        view.setScrubEnabled(true);

        assertFalse(view.canScrub(true));
    }

    private static void assertFrameIsBelowControls(final ExoVideoView view) {
        assertTrue(view.getChildAt(0) instanceof AspectRatioFrameLayout);
        final View frame = findFrame(view);
        final PlayerControlView controls = findControls(view);
        // indexOfChild answers -1 for a null child, so without these the ordering assertion below
        // would hold vacuously if either view were missing entirely.
        assertNotNull(frame);
        assertNotNull(controls);
        assertTrue(view.indexOfChild(frame) < view.indexOfChild(controls));
    }

    private static ExoVideoView attach(final TestActivity activity, final ExoVideoView view) {
        final FrameLayout container = new FrameLayout(activity);
        activity.setContentView(container);
        container.addView(view);
        return view;
    }

    private static void reattach(final ExoVideoView view) {
        final ViewGroup parent = (ViewGroup) view.getParent();
        parent.removeView(view);
        parent.addView(view);
    }

    private static @Nullable PlayerControlView findControls(final ExoVideoView view) {
        for (int i = 0; i < view.getChildCount(); i++) {
            final View child = view.getChildAt(i);
            if (child instanceof PlayerControlView) {
                return (PlayerControlView) child;
            }
        }
        return null;
    }

    private static @Nullable View findFrame(final ExoVideoView view) {
        for (int i = 0; i < view.getChildCount(); i++) {
            final View child = view.getChildAt(i);
            if (child instanceof AspectRatioFrameLayout) {
                return child;
            }
        }
        return null;
    }

    private static TestActivity createActivity() {
        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        final TestActivity activity = controller.get();
        // PlayerControlView resolves ?attr/colorAccent while inflating.
        activity.setTheme(R.style.Theme_LIGHT);
        controller.setup();
        return activity;
    }

    public static class TestActivity extends AppCompatActivity {}
}
