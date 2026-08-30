package me.edgan.redditslide;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.util.ScrollAnchor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The two transports a feed restore travels on, and the rule that it is handed to exactly one page.
 *
 * <p>The intent form is the one a theme restart uses, and it is the harder of the two to exercise
 * by hand: it only happens when an activity finishes itself and immediately starts itself again,
 * which is a sequence no {@code adb} gesture can reliably produce. The bundle form is what the
 * hibernate snapshot carries.
 *
 * <p>Two of these cover defects rather than behaviour. The extras have to come <em>off</em> the
 * intent once read, because the system persists an activity's intent in the task record and a
 * scroll position left on it is replayed on a cold start days later. And {@code applyTo} has to be
 * one-shot, because the pager rebuilds its adapter on any subscription change and would otherwise
 * put the cached listing back on screen every time.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class FeedRestoreStateTest {

    private static Bundle captured() {
        final Bundle state = new Bundle();
        state.putString(HibernateState.STATE_SUBREDDIT, "androiddev");
        state.putInt(HibernateState.STATE_PAGE, 3);
        state.putString(HibernateState.STATE_ANCHOR_ID, "t3_abc123");
        state.putString(HibernateState.STATE_AFTER_TOKEN, "t3_zzz999");
        state.putInt(HibernateState.STATE_ANCHOR_POSITION, 42);
        state.putInt(HibernateState.STATE_ANCHOR_OFFSET, -1490);
        state.putInt(HibernateState.STATE_EXPECTED_COUNT, 100);
        state.putBoolean(HibernateState.STATE_TOOLBAR_HIDDEN, true);
        return state;
    }

    @Test
    public void theBundleFormRoundTrips() {
        final FeedRestoreState restore = new FeedRestoreState();
        restore.read(captured());

        assertTrue(restore.isPending());
        assertEquals("androiddev", restore.subreddit);
        assertEquals(3, restore.page);
        assertEquals("t3_abc123", restore.anchorId);
        assertEquals("t3_zzz999", restore.afterToken);
        assertEquals(42, restore.anchorPosition);
        assertEquals(-1490, restore.anchorOffset);
        assertEquals(100, restore.expectedCount);
        assertTrue(restore.toolbarHidden);
    }

    @Test
    public void aStateWithNoSubredditIsNotPending() {
        final FeedRestoreState restore = new FeedRestoreState();
        restore.read(new Bundle());

        assertFalse(restore.isPending());
    }

    @Test
    public void theIntentFormRoundTripsAndClearsItself() {
        final Intent intent = new Intent();
        FeedRestoreState.writeToIntent(intent, captured());

        final FeedRestoreState restore = new FeedRestoreState();
        restore.readFromIntent(intent);

        assertTrue(restore.isPending());
        assertEquals("androiddev", restore.subreddit);
        assertEquals("t3_abc123", restore.anchorId);
        assertEquals("t3_zzz999", restore.afterToken);
        assertEquals(42, restore.anchorPosition);
        assertEquals(-1490, restore.anchorOffset);
        assertEquals(100, restore.expectedCount);
        assertTrue(restore.toolbarHidden);

        // The whole point of the read: a second activity built from the same intent -- which is
        // what the task record hands back after a process death -- must not be restored again.
        assertFalse(intent.hasExtra(FeedRestoreState.EXTRA_SUB));
        assertFalse(intent.hasExtra(FeedRestoreState.EXTRA_ANCHOR_ID));
        assertFalse(intent.hasExtra(FeedRestoreState.EXTRA_AFTER_TOKEN));
        assertFalse(intent.hasExtra(FeedRestoreState.EXTRA_ANCHOR_POSITION));
        assertFalse(intent.hasExtra(FeedRestoreState.EXTRA_ANCHOR_OFFSET));
        assertFalse(intent.hasExtra(FeedRestoreState.EXTRA_EXPECTED_COUNT));
        assertFalse(intent.hasExtra(FeedRestoreState.EXTRA_TOOLBAR_HIDDEN));

        final FeedRestoreState second = new FeedRestoreState();
        second.readFromIntent(intent);
        assertFalse(second.isPending());
    }

    @Test
    public void nothingIsWrittenForAStateWithNoSubreddit() {
        final Intent intent = new Intent();
        FeedRestoreState.writeToIntent(intent, new Bundle());

        assertFalse(intent.hasExtra(FeedRestoreState.EXTRA_SUB));
    }

    @Test
    public void theRestoreGoesToTheMatchingPageOnlyAndOnlyOnce() {
        final FeedRestoreState restore = new FeedRestoreState();
        restore.read(captured());

        final Bundle wrongPage = new Bundle();
        assertFalse(restore.applyTo("pics", null, wrongPage));
        assertFalse(wrongPage.getBoolean(SubmissionsView.ARG_RESTORE_FROM_CACHE, false));
        assertTrue("a page that did not match must not consume it", restore.isPending());

        final Bundle rightPage = new Bundle();
        assertTrue(restore.applyTo("AndroidDev", null, rightPage));
        assertTrue(rightPage.getBoolean(SubmissionsView.ARG_RESTORE_FROM_CACHE, false));
        assertEquals("t3_abc123", rightPage.getString(SubmissionsView.ARG_RESTORE_ANCHOR_ID));
        assertEquals("t3_zzz999", rightPage.getString(SubmissionsView.ARG_RESTORE_AFTER_TOKEN));
        assertEquals(42, rightPage.getInt(SubmissionsView.ARG_RESTORE_ANCHOR_POSITION));
        assertEquals(-1490, rightPage.getInt(SubmissionsView.ARG_RESTORE_ANCHOR_OFFSET));
        assertEquals(100, rightPage.getInt(SubmissionsView.ARG_RESTORE_EXPECTED_COUNT));
        assertTrue(rightPage.getBoolean(SubmissionsView.ARG_RESTORE_TOOLBAR_HIDDEN, false));

        // Consumed: an adapter rebuild calls getItem again for the same page.
        assertFalse(restore.isPending());
        final Bundle rebuilt = new Bundle();
        assertFalse(restore.applyTo("AndroidDev", null, rebuilt));
        assertFalse(rebuilt.getBoolean(SubmissionsView.ARG_RESTORE_FROM_CACHE, false));
    }

    @Test
    public void aMultiredditMatchesOnItsResolvedPath() {
        final Bundle state = captured();
        state.putString(HibernateState.STATE_SUBREDDIT, "/m/dev");
        final FeedRestoreState restore = new FeedRestoreState();
        restore.read(state);

        // The pager adapter resolves a multireddit to its full API path before it names the page,
        // so the recorded name and the page name are never the same string.
        final Bundle args = new Bundle();
        assertTrue(restore.applyTo("api/user/someone/m/dev", "api/user/someone/m/dev", args));
        assertTrue(args.getBoolean(SubmissionsView.ARG_RESTORE_FROM_CACHE, false));
    }

    @Test
    public void anUnstartedStateHasNoAnchor() {
        final FeedRestoreState restore = new FeedRestoreState();

        assertNull(restore.subreddit);
        assertEquals(ScrollAnchor.NO_POSITION, restore.anchorPosition);
        assertFalse(restore.isPending());
    }
}
