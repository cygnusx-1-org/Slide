package me.edgan.redditslide.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.ExoVideoView;
import org.jspecify.annotations.NullMarked;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Covers what a media load does when it ends up with nothing to play. Every row and page that tracks
 * what it asked to load hangs off this: the progress indicator is stopped here or not at all, since
 * only reaching STATE_READY hides it and a load that never reaches the player never reports that;
 * and {@code onError()} is the only signal that lets a caller forget the url and try again.
 *
 * <p>Also pins the release that makes {@code ExoVideoView.setVideoURI} rebuild its player — the
 * failure path takes the player away while the view is still attached.
 */
@NullMarked
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class AsyncLoadGifFailureTest {

    private TestActivity activity;
    private ExoVideoView video;
    private ProgressBar bar;
    private TextView size;

    @Before
    public void setUp() {
        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        activity = controller.get();
        activity.setTheme(R.style.Theme_LIGHT);
        controller.setup();

        video = new ExoVideoView(activity);
        bar = new ProgressBar(activity);
        // As the layouts ship it: visible from the moment the row appears.
        bar.setVisibility(View.VISIBLE);
        size = new TextView(activity);
        // Shown by the size lookup while the download runs; only a completed load hides it.
        size.setVisibility(View.VISIBLE);
    }

    @Test
    public void aLoadThatResolvesNothingStopsTheBarAndReportsOnce() {
        final CountingLoad load = new CountingLoad();

        load.finishWithNothing();

        assertEquals(View.GONE, bar.getVisibility());
        assertEquals(View.GONE, size.getVisibility());
        assertEquals(1, load.errors);
    }

    @Test
    public void playbackFailingAfterTheHandoffStopsTheBarAndReports() {
        final CapturingVideo capturing = new CapturingVideo();
        final CountingLoad load = new CountingLoad(capturing);

        // The uri resolved and the player took it: nothing has failed yet, and the bar is showing
        // because the media is still buffering.
        load.finishWith(Uri.parse("https://v.redd.it/abc123/DASHPlaylist.mpd"));
        assertNotNull("the load has to register a listener to hear about failures", capturing.listener);
        assertEquals(View.VISIBLE, bar.getVisibility());

        // Now playback dies. STATE_READY never arrives, so nothing else will ever stop the bar.
        capturing.listener.onPlayerError(
                new PlaybackException(
                        "test", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED));

        assertEquals(View.GONE, bar.getVisibility());
        assertEquals(View.GONE, size.getVisibility());
        assertEquals(1, load.errors);
    }

    @Test
    public void aHandoffThatThrowsStopsTheBarAndReports() {
        final CountingLoad load = new CountingLoad(new ThrowingVideo());

        load.finishWith(Uri.parse("https://i.imgur.com/abc123.mp4"));

        assertEquals(View.GONE, bar.getVisibility());
        assertEquals(View.GONE, size.getVisibility());
        assertEquals(1, load.errors);
    }

    @Test
    public void aLoadHandedToAnotherScreenReportsNothing() {
        final CountingLoad load = new CountingLoad();
        // What the gifdeliverynetwork, misidentified-imgur and unknown-host paths set before opening
        // the url somewhere else. They return no uri either, but there is nothing here to retry.
        load.handedOff = true;

        load.finishWithNothing();

        // The bar still has to stop: whatever happens next happens on another screen.
        assertEquals(View.GONE, bar.getVisibility());
        assertEquals(0, load.errors);
    }

    @Test
    public void aGfycatHandoffOpensTheBrowserForAHostThatOwnsTheScreen() {
        // The end-to-end shape of the marker: loadGfycat says "this really is gifdeliverynetwork and
        // redgifs has nothing", doInBackground turns that into a hand-off rather than a failure, and
        // the caller is never told to retry a url that has gone to another screen.
        final HandingOffLoad load = new HandingOffLoad(true);

        assertNull(load.resolve("https://gfycat.com/somename"));
        assertTrue("the hand-off has to be recorded, or onPostExecute reports a failure",
                load.handedOff);

        load.finishWithNothing();

        assertEquals(View.GONE, bar.getVisibility());
        assertEquals(0, load.errors);
        assertNotNull(
                "the url has to actually go somewhere",
                Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void aGfycatHandoffLeavesAHostThatDoesNotOwnTheScreenAlone() {
        // Every host but MediaView passes closeIfNull=false, because the load is one item among many
        // — a Shadowbox page, an album row, a peek — and often not even the one in front of the user.
        // openWebsite finishes whatever host it is handed, so from here it would close the shadowbox
        // out from under them because a single gif was dead.
        final HandingOffLoad load = new HandingOffLoad(false);

        assertNull(load.resolve("https://gfycat.com/somename"));
        assertFalse("navigated away from a host that does not own the screen", load.handedOff);

        load.finishWithNothing();

        // A plain failure instead, which is the signal those callers already handle.
        assertEquals(1, load.errors);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void anUnrecognisedUrlIsHandedToTheBrowserForAHostThatOwnsTheScreen() {
        // getVideoType's fallback, reached by any url none of the other cases claim. Unlike the
        // gfycat marker this is not a dead link — the browser is very likely to render it — so the
        // hand-off happens whatever the host is; only the finish() behind it is conditional.
        final HandingOffLoad load = new HandingOffLoad(true);

        assertNull(load.resolve("https://example.com/thing"));
        assertTrue(load.handedOff);

        load.finishWithNothing();

        assertEquals(0, load.errors);
        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());
        assertTrue("a host that owns its screen is replaced by the browser", activity.isFinishing());
    }

    @Test
    public void anUnrecognisedUrlLeavesAHostThatDoesNotOwnTheScreenStanding() {
        final HandingOffLoad load = new HandingOffLoad(false);

        assertNull(load.resolve("https://example.com/thing"));
        // Not handed off: the browser opened, but this host is still on screen still showing a load
        // that failed, so it has to be told. The peek overlay answers onError by falling back to a
        // link preview; suppressing it left the peek on a stopped spinner behind the browser.
        assertFalse("claimed a hand-off that left the host standing", load.handedOff);

        load.finishWithNothing();

        assertEquals(1, load.errors);
        assertNotNull(
                "dropping the hand-off loses content the browser could show",
                Shadows.shadowOf(activity).getNextStartedActivity());
        assertFalse(
                "finished a shadowbox/peek/album because one item failed", activity.isFinishing());
    }

    @Test
    public void anUnrecognisedUrlWithNoHostAtAllIsAPlainFailure() {
        // c is @NonNull in the constructors, but this class does not trust that annotation
        // elsewhere — cancel() and nothingIsComing() both guard `video`, which is declared the same
        // way. openWebsite dereferences the host immediately, and marking a load handed-off that
        // went nowhere would suppress the onError its caller needs.
        final HandingOffLoad load = new HandingOffLoad(false, null);

        assertNull(load.resolve("https://example.com/thing"));
        assertFalse("handed off to a host that does not exist", load.handedOff);

        load.finishWithNothing();

        assertEquals(1, load.errors);
        assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void theHandoffNavigatesOnTheMainThreadNotTheWorker() throws Exception {
        // doInBackground runs on an AsyncTask worker, and openWebsite calls startActivity and, for a
        // host that owns its screen, finish(). Activity.finish() is not documented as thread-safe,
        // and loadRedGifs's own hand-off at GifUtils:984 already posts for this reason. Same shape as
        // LoaderErrorThreadTest: that nothing has happened when resolve() returns is what proves the
        // call was posted rather than made inline.
        final HandingOffLoad load = new HandingOffLoad(true);

        final Thread worker = new Thread(() -> load.resolve("https://example.com/thing"));
        worker.start();
        worker.join();

        assertNull(
                "navigated from the worker thread",
                Shadows.shadowOf(activity).getNextStartedActivity());

        shadowOf(Looper.getMainLooper()).idle();

        assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void aGfycatLookupThatResolvesNothingIsAFailureNotAHandoff() {
        // The other side of the same branch: a plain null is a failure the caller must hear about.
        final HandingOffLoad load = new HandingOffLoad(true);
        load.gfycatResult = null;

        assertNull(load.resolve("https://gfycat.com/somename"));
        assertFalse(load.handedOff);

        load.finishWithNothing();

        assertEquals(1, load.errors);
    }

    @Test
    public void aLoadThatResolvesNothingReleasesThePlayer() {
        final CountingLoad load = new CountingLoad();
        assertTrue(video.hasPlayer());

        load.finishWithNothing();

        // cancel() -> ExoVideoView.stop(). This is why setVideoURI has to rebuild: the row is still
        // attached and is allowed to retry, but its player is gone.
        assertFalse(video.hasPlayer());
    }

    /** The real task with its error callback counted and its failure path callable directly. */
    private class CountingLoad extends GifUtils.AsyncLoadGif {

        int errors;

        CountingLoad() {
            this(video);
        }

        CountingLoad(final ExoVideoView target) {
            super(activity, target, bar, false, false, size, "pics", "A title");
        }

        @Override
        public void onError() {
            errors++;
        }

        /** onPostExecute with no uri: every failure in doInBackground arrives here as a null. */
        void finishWithNothing() {
            onPostExecute(null);
        }

        /** onPostExecute with a uri: the load resolved and is handed to the player. */
        void finishWith(final Uri uri) {
            onPostExecute(uri);
        }
    }

    /**
     * Stands in for the gfycat lookup, so the GFYCAT branch of doInBackground can be driven without a
     * network. {@code size} is null so the task skips its file-size fetch, which is the only other
     * thing that would go out to the wire.
     */
    private class HandingOffLoad extends GifUtils.AsyncLoadGif {

        int errors;
        @Nullable Uri gfycatResult =
                Uri.parse(GifUtils.AsyncLoadGif.HANDOFF_SCHEME + "://gifdeliverynetwork");

        HandingOffLoad(final boolean closeIfNull) {
            this(closeIfNull, activity);
        }

        // AsyncLoadGif declares its Activity @NonNull. anUnrecognisedUrlWithNoHostAtAllIsAPlainFailure
        // deliberately violates that to pin what the class does when a caller hands it a dead host,
        // so this one super call has to be exempt from the contract it is characterising.
        @SuppressWarnings("NullAway")
        HandingOffLoad(final boolean closeIfNull, final @Nullable Activity host) {
            super(host, video, bar, closeIfNull, false, null, "pics", "A title");
        }

        @Override
        @Nullable Uri resolveGfycat(final String name, final String url) {
            return gfycatResult;
        }

        @Override
        public void onError() {
            errors++;
        }

        @Nullable Uri resolve(final String url) {
            return doInBackground(url);
        }

        void finishWithNothing() {
            onPostExecute(null);
        }
    }

    /**
     * Keeps the listener the load registers, instead of handing the uri to a real player. Robolectric
     * has no decoder, so a real load would never reach STATE_READY or fail on its own — the point here
     * is what the listener does when it is told playback died.
     */
    private class CapturingVideo extends ExoVideoView {

        @Nullable Player.Listener listener;

        CapturingVideo() {
            super(activity);
        }

        @Override
        public void setVideoURI(
                final @Nullable Uri uri,
                final VideoType type,
                final @Nullable Player.Listener l) {
            listener = l;
        }
    }

    /** Fails the hand-off itself, for the catch around setVideoURI. */
    private class ThrowingVideo extends ExoVideoView {

        ThrowingVideo() {
            super(activity);
        }

        @Override
        public void setVideoURI(
                final @Nullable Uri uri,
                final VideoType type,
                final @Nullable Player.Listener l) {
            throw new IllegalStateException("no media source for " + uri);
        }
    }

    public static class TestActivity extends AppCompatActivity {}
}
