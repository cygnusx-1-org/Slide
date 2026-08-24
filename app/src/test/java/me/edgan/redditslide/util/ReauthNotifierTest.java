package me.edgan.redditslide.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The reauth state machine, with no activity attached.
 *
 * <p>Its job is to decide whether the session is in trouble, and to tell already-bound screens to
 * re-enable the reply/vote/save buttons that were hidden while {@code Authentication.isLoggedIn}
 * was still false (issue #295). All of that is decided from three flags and a counter, and none of
 * it needs a snackbar: with no foreground activity every {@code showSnackbar} call returns early,
 * so the decisions can be driven and observed directly.
 *
 * <p>The class carries an unusual number of written-down invariants — a success landing while
 * reported reauths are outstanding makes them moot, a fast failure must raise the failure state
 * without waiting for the 30s timer, one listener throwing must not silence the rest — and had
 * 9 of 96 branches covered. Each of the nine below was broken on its own with the suite green.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ReauthNotifierTest {

    /** Records what it was told, and can be asked to throw when notified. */
    private static final class RecordingListener implements ReauthNotifier.Listener {
        int calls;
        final boolean explodes;

        RecordingListener(boolean explodes) {
            this.explodes = explodes;
        }

        @Override
        public void onReauthComplete() {
            calls++;
            if (explodes) throw new IllegalStateException("listener blew up");
        }
    }

    private final List<RecordingListener> registered = new ArrayList<>();

    @Before
    public void setUp() {
        resetState();
    }

    @After
    public void tearDown() {
        for (RecordingListener l : registered) {
            ReauthNotifier.removeListener(l);
        }
        registered.clear();
        resetState();
    }

    private RecordingListener listen(boolean explodes) {
        RecordingListener l = new RecordingListener(explodes);
        registered.add(l);
        ReauthNotifier.addListener(l);
        return l;
    }

    // ReauthNotifier is all static and has no reset hook; this JVM is shared with every later
    // test class, so the flags and the counter are put back by hand.
    private static void resetState() {
        setFlag("reauthFailed", false);
        setFlag("failureDismissed", false);
        setFlag("satisfiedBySuccess", false);
        counter().set(0);
    }

    private static Field field(String name) {
        try {
            Field f = ReauthNotifier.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("ReauthNotifier." + name + " is gone", e);
        }
    }

    private static void setFlag(String name, boolean value) {
        try {
            field(name).setBoolean(null, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean flag(String name) {
        try {
            return field(name).getBoolean(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static AtomicInteger counter() {
        try {
            return (AtomicInteger) field("inProgress").get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // -----------------------------------------------------------------
    // Listeners
    // -----------------------------------------------------------------

    @Test
    public void aSuccessfulReauthTellsListenersToRebind() {
        RecordingListener l = listen(false);

        ReauthNotifier.onStarted();
        ReauthNotifier.onFinished(true);

        assertEquals("the screens gated on isLoggedIn have to be told", 1, l.calls);
    }

    /** The catch around each callback exists so one bad listener cannot silence the others. */
    @Test
    public void aListenerThatThrowsDoesNotStopTheRest() {
        RecordingListener first = listen(true);
        RecordingListener second = listen(false);

        ReauthNotifier.onStarted();
        ReauthNotifier.onFinished(true);

        assertEquals(1, first.calls);
        assertEquals("the second listener still has to be told", 1, second.calls);
    }

    @Test
    public void registeringTheSameListenerTwiceStillNotifiesItOnce() {
        RecordingListener l = listen(false);
        ReauthNotifier.addListener(l);

        ReauthNotifier.onStarted();
        ReauthNotifier.onFinished(true);

        assertEquals(1, l.calls);
    }

    @Test
    public void aRemovedListenerIsNotNotified() {
        RecordingListener l = listen(false);
        ReauthNotifier.removeListener(l);

        ReauthNotifier.onStarted();
        ReauthNotifier.onFinished(true);

        assertEquals(0, l.calls);
    }

    /** A silent refresh is one of the things that flips isLoggedIn true, so it notifies too. */
    @Test
    public void aSilentSuccessAlsoTellsListenersToRebind() {
        RecordingListener l = listen(false);

        ReauthNotifier.onSucceededSilently();

        assertEquals(1, l.calls);
    }

    // -----------------------------------------------------------------
    // The failure state
    // -----------------------------------------------------------------

    /**
     * A reauth that fails quickly never reaches the 30s timer, so the failure has to be raised at
     * once — otherwise the buttons stay silently hidden (issue #295).
     */
    @Test
    public void aReauthThatFailsRaisesTheFailureStateWithoutWaitingForTheTimer() {
        ReauthNotifier.onStarted();
        ReauthNotifier.onFinished(false);

        assertTrue("the failure is live immediately", flag("reauthFailed"));
    }

    @Test
    public void aLaterSuccessClearsAnEarlierFailure() {
        ReauthNotifier.onStarted();
        ReauthNotifier.onFinished(false);
        assertTrue(flag("reauthFailed"));

        ReauthNotifier.onStarted();
        ReauthNotifier.onFinished(true);

        assertFalse("a success is the only thing that clears it", flag("reauthFailed"));
    }

    /**
     * A success landing while reported reauths are still outstanding makes their outcome moot: the
     * token is fresh, so whatever they go on to report says nothing about the session.
     */
    @Test
    public void aFailureReportedAfterASilentSuccessIsIgnored() {
        ReauthNotifier.onStarted();

        ReauthNotifier.onSucceededSilently();
        ReauthNotifier.onFinished(false);

        assertFalse(
                "the session is authed; a late failure must not claim otherwise",
                flag("reauthFailed"));
    }

    /**
     * The next reauth is a new question. Once it has been asked, the previous episode's "moot"
     * verdict must not go on suppressing its answer.
     */
    @Test
    public void aReauthStartedAfterASatisfiedOneCanStillReportFailure() {
        ReauthNotifier.onStarted();
        ReauthNotifier.onSucceededSilently();
        ReauthNotifier.onFinished(false);
        assertFalse(flag("reauthFailed"));

        ReauthNotifier.onStarted();
        ReauthNotifier.onFinished(false);

        assertTrue("this is a fresh episode and it failed", flag("reauthFailed"));
    }

    /**
     * The first of {@code onSucceededSilently}'s three jobs: clear a failure that is already live.
     * That state is sticky until some reauth reports success, and the resume-time refresh only runs
     * once the stored token has expired — which this very refresh prevents — so without this one
     * earlier failure would keep the bar coming back on every resume, forever.
     */
    @Test
    public void aSilentSuccessClearsAFailureThatIsAlreadyLive() {
        ReauthNotifier.onStarted();
        ReauthNotifier.onFinished(false);
        assertTrue("the failure is live", flag("reauthFailed"));

        ReauthNotifier.onSucceededSilently();

        assertFalse(
                "the token is fresh, so the sticky failure has to go", flag("reauthFailed"));
    }

    /**
     * A reauth started while another is still outstanding, after a success has made that one moot.
     * It is a new question, so it must begin a fresh episode rather than inherit the verdict —
     * otherwise its own failure is swallowed.
     */
    @Test
    public void aReauthStartedWhileASatisfiedOneIsStillOutstandingIsAFreshEpisode() {
        ReauthNotifier.onStarted();
        ReauthNotifier.onSucceededSilently();

        ReauthNotifier.onStarted(); // second one, started while the first is still counted
        ReauthNotifier.onFinished(false);
        ReauthNotifier.onFinished(false);

        assertTrue(
                "the later reauth's own failure must still be reported",
                flag("reauthFailed"));
    }

    /** Two overlapping reauths are one episode; it is over when the last one reports. */
    @Test
    public void overlappingReauthsCompleteOnlyWhenTheLastOneFinishes() {
        RecordingListener l = listen(false);

        ReauthNotifier.onStarted();
        ReauthNotifier.onStarted();
        ReauthNotifier.onFinished(true);

        assertEquals("one is still outstanding", 0, l.calls);
        assertEquals(1, counter().get());

        ReauthNotifier.onFinished(true);

        assertEquals(1, l.calls);
        assertEquals(0, counter().get());
    }
}
