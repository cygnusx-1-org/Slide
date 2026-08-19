package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.edgan.redditslide.InboxCount;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * The unread count used to live in three places at once -- a field on the home screen, an event
 * delivered to whatever screens happened to be alive, and a preference written only by the network
 * path -- so a screen created after a message was read showed the pre-read number. These pin the
 * single stored value that replaced all three.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class InboxCountTest {

    private SharedPreferences prefs;

    @Before
    public void setUp() {
        prefs =
                ((Context) ApplicationProvider.getApplicationContext())
                        .getSharedPreferences("InboxCountTest", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        // generation and the freshness stamp are static and the Robolectric sandbox is shared
        // with same-config test classes, so start every test from a known one.
        InboxCount.clear(prefs);
    }

    /** SharedPreferences delivers change callbacks through the main looper. */
    private static void settle() {
        ShadowLooper.shadowMainLooper().idle();
    }

    @Test
    public void unsetCountIsZero() {
        assertEquals(0, InboxCount.get(prefs));
    }

    @Test
    public void decrementPersists() {
        InboxCount.set(prefs, 3);
        InboxCount.decrement(prefs);

        assertEquals(2, InboxCount.get(prefs));
        assertEquals(2, prefs.getInt(InboxCount.KEY, -1));
    }

    @Test
    public void decrementByNPersists() {
        InboxCount.set(prefs, 5);
        InboxCount.decrement(prefs, 3);

        assertEquals(2, InboxCount.get(prefs));
    }

    @Test
    public void decrementClampsAtZero() {
        InboxCount.set(prefs, 1);
        InboxCount.decrement(prefs, 4);

        assertEquals(0, InboxCount.get(prefs));
    }

    @Test
    public void setClampsAtZero() {
        InboxCount.set(prefs, -7);

        assertEquals(0, InboxCount.get(prefs));
    }

    @Test
    public void incrementPersists() {
        InboxCount.set(prefs, 2);
        InboxCount.increment(prefs);

        assertEquals(3, InboxCount.get(prefs));
    }

    @Test
    public void runningObserverSeesEveryChange() {
        final List<Integer> seen = new ArrayList<>();
        InboxCount.set(prefs, 4);

        SharedPreferences.OnSharedPreferenceChangeListener token =
                InboxCount.observe(prefs, seen::add);
        settle();

        InboxCount.decrement(prefs);
        settle();
        InboxCount.set(prefs, 0);
        settle();

        InboxCount.stopObserving(prefs, token);

        assertEquals(Arrays.asList(4, 3, 0), seen);
    }

    @Test
    public void stoppedObserverSeesNoFurtherChanges() {
        final List<Integer> seen = new ArrayList<>();
        InboxCount.set(prefs, 4);

        SharedPreferences.OnSharedPreferenceChangeListener token =
                InboxCount.observe(prefs, seen::add);
        settle();
        InboxCount.stopObserving(prefs, token);

        InboxCount.decrement(prefs);
        settle();

        assertEquals(Arrays.asList(4), seen);
    }

    /**
     * The regression test for the whole issue: an observer created after the change has to report
     * the same number as one that was already running when it happened.
     */
    @Test
    public void observerCreatedAfterAChangeSeesTheSameValue() {
        final List<Integer> running = new ArrayList<>();
        InboxCount.set(prefs, 2);

        SharedPreferences.OnSharedPreferenceChangeListener runningToken =
                InboxCount.observe(prefs, running::add);
        settle();

        InboxCount.decrement(prefs);
        settle();

        final List<Integer> fresh = new ArrayList<>();
        SharedPreferences.OnSharedPreferenceChangeListener freshToken =
                InboxCount.observe(prefs, fresh::add);
        settle();

        InboxCount.stopObserving(prefs, runningToken);
        InboxCount.stopObserving(prefs, freshToken);

        assertEquals(Integer.valueOf(1), running.get(running.size() - 1));
        assertEquals(Arrays.asList(1), fresh);
    }

    @Test
    public void clearResetsTheObservedValue() {
        final List<Integer> seen = new ArrayList<>();
        InboxCount.set(prefs, 6);

        SharedPreferences.OnSharedPreferenceChangeListener token =
                InboxCount.observe(prefs, seen::add);
        settle();

        InboxCount.clear(prefs);
        settle();

        InboxCount.stopObserving(prefs, token);

        assertEquals(Arrays.asList(6, 0), seen);
        assertEquals(0, InboxCount.get(prefs));
    }

    /**
     * A preference file wiped wholesale is reported with a null key rather than the count's own,
     * so the observer has to re-read on null instead of matching the key.
     */
    @Test
    public void aNullKeyIsReadAsAChange() {
        final List<Integer> seen = new ArrayList<>();
        InboxCount.set(prefs, 6);

        SharedPreferences.OnSharedPreferenceChangeListener token =
                InboxCount.observe(prefs, seen::add);
        settle();

        prefs.edit().clear().commit();
        token.onSharedPreferenceChanged(prefs, null);

        InboxCount.stopObserving(prefs, token);

        assertEquals(Integer.valueOf(0), seen.get(seen.size() - 1));
        assertEquals(0, InboxCount.get(prefs));
    }

    @Test
    public void aFetchIsStoredWhenNothingMovedTheCountWhileItWasInFlight() {
        InboxCount.set(prefs, 3);

        final int generation = InboxCount.generation();

        assertTrue(InboxCount.setFromFetch(prefs, 5, generation));
        assertEquals(5, InboxCount.get(prefs));
    }

    /**
     * A listing describes the inbox as it was when the request went out. A message read while it
     * was in flight is newer than that, so the listing must not wind the count back up.
     */
    @Test
    public void aFetchIsDroppedWhenSomethingReadMailWhileItWasInFlight() {
        InboxCount.set(prefs, 3);

        final int generation = InboxCount.generation();
        InboxCount.decrement(prefs);

        assertFalse(InboxCount.setFromFetch(prefs, 3, generation));
        assertEquals(2, InboxCount.get(prefs));
    }

    @Test
    public void aFetchIsFreshUntilTheWindowPasses() {
        assertFalse("nothing fetched yet", InboxCount.isFresh(60_000L));

        InboxCount.markFetched();
        assertTrue(InboxCount.isFresh(60_000L));

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(59_999L));
        assertTrue(InboxCount.isFresh(60_000L));

        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(2L));
        assertFalse(InboxCount.isFresh(60_000L));
    }

    /** Switching accounts must not let the previous account's fetch skip the next one's. */
    @Test
    public void clearDropsFreshness() {
        InboxCount.markFetched();
        assertTrue(InboxCount.isFresh(60_000L));

        InboxCount.clear(prefs);

        assertFalse(InboxCount.isFresh(60_000L));
    }

    /** Keys other than the count's own are ignored. */
    @Test
    public void anUnrelatedKeyIsIgnored() {
        final List<Integer> seen = new ArrayList<>();
        InboxCount.set(prefs, 6);

        SharedPreferences.OnSharedPreferenceChangeListener token =
                InboxCount.observe(prefs, seen::add);
        settle();

        prefs.edit().putString("somethingElse", "x").apply();
        settle();

        InboxCount.stopObserving(prefs, token);

        assertEquals(Arrays.asList(6), seen);
    }
}
