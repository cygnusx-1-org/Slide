package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import me.edgan.redditslide.ImgurAlbum.AlbumUtils;
import me.edgan.redditslide.Tumblr.TumblrUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Pins the thread the album loaders report a failure on.
 *
 * <p>Both loaders have two failure paths: {@code doWithData} being handed nothing, which always ran
 * on the main thread, and {@code doInBackground} finding nothing to hand it, which called {@code
 * onError()} straight from the worker. The overrides are not background-safe — the Tumblr activity's
 * starts an activity and finishes itself, the Imgur one shows a modal dialog, and the peek overlay
 * rebuilds its whole view — so the second path posts now.
 *
 * <p>The assertion is deliberately in two halves. That nothing has run when {@code doInBackground}
 * returns is what proves the call was posted rather than made inline, which is exactly what a
 * regression would undo; idling the looper afterwards proves it is not simply dropped.
 *
 * <p>Neither loader reaches the network: {@code Reddit.client} is null under test, so {@code
 * HttpUtil.getJsonObject} short-circuits to null and both take their "nothing came back" path.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class LoaderErrorThreadTest {

    private TestActivity activity;

    /**
     * Captured and put back: {@code tumblrRequests} is an app-wide static, and Robolectric caches one
     * sandbox per {@code @Config} — which every suite here shares — so a value left behind is visible
     * to whatever class runs next in the same JVM.
     */
    private SharedPreferences tumblrRequestsWas;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(TestActivity.class).setup().get();
        tumblrRequestsWas = TumblrUtils.tumblrRequests;
        // Empty, so the Tumblr loader misses the cache and takes the fetch branch.
        TumblrUtils.tumblrRequests =
                activity.getSharedPreferences("tumblrRequestsTest", Activity.MODE_PRIVATE);
        TumblrUtils.tumblrRequests.edit().clear().commit();
    }

    @After
    public void tearDown() {
        TumblrUtils.tumblrRequests = tumblrRequestsWas;
    }

    @Test
    public void aTumblrPostThatResolvesNothingReportsOnTheMainThread() throws Exception {
        final RecordingTumblrCallback callback = new RecordingTumblrCallback(activity);

        runOffTheMainThread(callback::runInBackground);

        assertNull("reported inline from the worker thread", callback.reportedOn);

        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, callback.errors);
        assertSame(Looper.getMainLooper().getThread(), callback.reportedOn);
    }

    @Test
    public void anImgurAlbumThatResolvesNothingReportsOnTheMainThread() throws Exception {
        final RecordingAlbumCallback callback = new RecordingAlbumCallback(activity);

        runOffTheMainThread(callback::runInBackground);

        assertNull("reported inline from the worker thread", callback.reportedOn);

        shadowOf(Looper.getMainLooper()).idle();

        assertEquals(1, callback.errors);
        assertSame(Looper.getMainLooper().getThread(), callback.reportedOn);
    }

    /** Runs {@code work} on a thread that is not the main one and waits for it. */
    private static void runOffTheMainThread(final Runnable work) throws InterruptedException {
        final Thread worker = new Thread(work, "loader-worker");
        worker.start();
        worker.join();
    }

    private static class RecordingTumblrCallback extends TumblrUtils.GetTumblrPostWithCallback {
        int errors;
        Thread reportedOn;

        RecordingTumblrCallback(@NonNull final Activity host) {
            super("https://example.tumblr.com/post/1", host);
        }

        /** doInBackground is protected on AsyncTask, so only a subclass can drive it. */
        void runInBackground() {
            doInBackground();
        }

        @Override
        public void onError() {
            errors++;
            reportedOn = Thread.currentThread();
        }
    }

    private static class RecordingAlbumCallback extends AlbumUtils.GetAlbumWithCallback {
        int errors;
        Thread reportedOn;

        RecordingAlbumCallback(@NonNull final Activity host) {
            super("https://imgur.com/a/abc123", host);
        }

        void runInBackground() {
            doInBackground();
        }

        @Override
        public void onError() {
            errors++;
            reportedOn = Thread.currentThread();
        }
    }

    public static class TestActivity extends AppCompatActivity {}
}
