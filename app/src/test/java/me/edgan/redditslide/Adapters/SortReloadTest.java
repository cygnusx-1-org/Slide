package me.edgan.redditslide.Adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import me.edgan.redditslide.FeedRestoreState;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.HibernateState;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.SortingUtil;
import net.dean.jraw.paginators.Paginator;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.TimePeriod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Issue #307: a sort picked from the toolbar has to reach the next fetch.
 *
 * <p>The chain a sort travels is short and every link is here. {@code SortingUtil} records the
 * pick; the host rebuilds its pager adapter, which builds a fresh feed page; the page's arguments
 * say whether that feed is to be fetched or rebuilt from the on-disk cache; and only a fetch
 * reaches {@link SubredditPosts#createPaginator}, which is the one place the pick is read. A page
 * built with {@link SubmissionsView#ARG_RESTORE_FROM_CACHE} never gets that far --
 * {@code SubredditPosts.doInBackground} returns the cached listing before the paginator exists --
 * so the user is handed the posts they already had, under the sort they just replaced.
 *
 * <p>That is what the bug was. {@code SubredditView.reloadSubs()}, the screen behind "Go to
 * subreddit", used to restart the activity, and a restart puts the restore on the relaunch intent
 * on purpose: a theme change is meant to come back to the same posts in the same place. The tab
 * hosts never had the defect because they rebuild the adapter in place, which is what
 * {@code SubredditView} does now.
 *
 * <p>The "Resume where I left off" cases are here because the report said the setting made no
 * difference and that is worth pinning: it gates {@link HibernateState}, the snapshot replayed
 * after the process dies, and nothing on the restart path. A fix that gated the restart transport
 * on the setting would look right and leave half the users still broken.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class SortReloadTest {

    /** A subreddit with no tab of its own, which is what the report was about. */
    private static final String SUB = "androiddev";

    private Context context;
    private SharedPreferences prefsWas;
    private Sorting defaultSortingWas;
    private Sorting frontpageSortingWas;
    private TimePeriod timePeriodWas;
    private boolean hibernateWas;
    private boolean hibernateResumeWas;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();

        prefsWas = SettingValues.prefs;
        defaultSortingWas = SortingUtil.defaultSorting;
        frontpageSortingWas = SortingUtil.frontpageSorting;
        timePeriodWas = SortingUtil.timePeriod;
        hibernateWas = SettingValues.hibernate;
        hibernateResumeWas = SettingValues.hibernateResume;

        final SharedPreferences prefs =
                context.getSharedPreferences("sort-reload-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        SettingValues.prefs = prefs;

        // Nothing stored for any of these subreddits, so a sort that shows up in a paginator got
        // there from the pick under test rather than from a neighbouring test's leftovers.
        SortingUtil.defaultSorting = Sorting.HOT;
        SortingUtil.frontpageSorting = Sorting.HOT;
        SortingUtil.timePeriod = TimePeriod.DAY;
        clearRememberedSorts();
    }

    @After
    public void tearDown() {
        SettingValues.prefs = prefsWas;
        SortingUtil.defaultSorting = defaultSortingWas;
        SortingUtil.frontpageSorting = frontpageSortingWas;
        SortingUtil.timePeriod = timePeriodWas;
        SettingValues.hibernate = hibernateWas;
        SettingValues.hibernateResume = hibernateResumeWas;
        clearRememberedSorts();
    }

    /** Both maps are static and shared with every other test in this Robolectric sandbox. */
    private static void clearRememberedSorts() {
        SortingUtil.sorting.remove(SUB);
        SortingUtil.sorting.remove("frontpage");
        SortingUtil.times.remove(SUB);
        SortingUtil.times.remove("frontpage");
    }

    // --- the sort, as the two toolbar menus record it -----------------------------------------

    /**
     * What both sort popups do when the user picks Top, then Month: write the pick through
     * {@code SortingUtil} and rebuild the feed. See {@code SubredditView.openPopup} and
     * {@code SubredditSortController.openPopup}, which pick the same two calls for a named
     * subreddit.
     */
    private static void userPicksTopOfMonth(String subreddit) {
        SortingUtil.setSorting(subreddit, Sorting.TOP);
        SortingUtil.setTime(subreddit, TimePeriod.MONTH);
    }

    // --- the restore, as the two transports carry it -------------------------------------------

    /** The bundle a feed writes about itself; see {@code FeedRestoreState.capture}. */
    private static Bundle recordedFeed(String subreddit) {
        final Bundle state = new Bundle();
        state.putString(HibernateState.STATE_SUBREDDIT, subreddit);
        state.putInt(HibernateState.STATE_PAGE, 0);
        state.putString(HibernateState.STATE_ANCHOR_ID, "t3_abc123");
        state.putString(HibernateState.STATE_AFTER_TOKEN, "t3_zzz999");
        state.putInt(HibernateState.STATE_ANCHOR_POSITION, 12);
        state.putInt(HibernateState.STATE_ANCHOR_OFFSET, -300);
        state.putInt(HibernateState.STATE_EXPECTED_COUNT, 50);
        state.putBoolean(HibernateState.STATE_TOOLBAR_HIDDEN, false);
        return state;
    }

    /**
     * The restore a screen relaunched by {@code restartTheme()} finds waiting for it: written onto
     * the relaunch intent by the screen that finished, read back by the one that replaced it.
     */
    private static FeedRestoreState restoreCarriedByARestart(String subreddit) {
        final Intent relaunch = new Intent();
        FeedRestoreState.writeToIntent(relaunch, recordedFeed(subreddit));
        final FeedRestoreState restore = new FeedRestoreState();
        restore.readFromIntent(relaunch);
        return restore;
    }

    // --- the feed page, as a pager adapter builds it -------------------------------------------

    /**
     * The fragment arguments both hosts build for a feed page: the listing to load, then the
     * pending restore if this is the page it was recorded for. See
     * {@code SubredditView.SubredditPagerAdapter.getItem} and
     * {@code MainActivity.applyRestoreArgs}.
     */
    private static Bundle feedPageArgs(FeedRestoreState restore, String name) {
        final Bundle args = new Bundle();
        args.putString("id", name);
        restore.applyTo(name, null, args);
        return args;
    }

    /**
     * The loader that page builds, wired from those arguments the way {@code SubmissionsView}
     * wires it -- {@code onCreate} reads the keys, {@code doAdapter} hands them to the loader.
     */
    private SubredditPosts loaderFor(Bundle args) {
        final SubredditPosts posts = new SubredditPosts(args.getString("id", ""), context);
        posts.restoreFromCache = args.getBoolean(SubmissionsView.ARG_RESTORE_FROM_CACHE, false);
        posts.restoreExpectedCount = args.getInt(SubmissionsView.ARG_RESTORE_EXPECTED_COUNT, 0);
        posts.restoreAfterToken = args.getString(SubmissionsView.ARG_RESTORE_AFTER_TOKEN);
        return posts;
    }

    /** The request the rebuilt page would send, for a page whose arguments say to fetch. */
    private Paginator fetchFor(Bundle args) {
        final SubredditPosts posts = loaderFor(args);
        assertFalse(
                "a page rebuilt from the cache never reaches the paginator, so the sort the"
                        + " user just picked never reaches reddit",
                posts.restoreFromCache);
        return posts.createPaginator(args.getString("id", ""), Paginator.RECOMMENDED_MAX_LIMIT);
    }

    // --- the tab sort --------------------------------------------------------------------------

    @Test
    public void theTabSortReachesTheNextFetch() {
        // MainActivity rebuilds its pager adapter in place and calls getItem again. The restore
        // was spent when the screen opened, so the rebuilt page carries none and fetches.
        final FeedRestoreState restore = restoreCarriedByARestart(SUB);
        assertTrue(
                "the tab claims its restore on the way in",
                feedPageArgs(restore, SUB)
                        .getBoolean(SubmissionsView.ARG_RESTORE_FROM_CACHE, false));

        userPicksTopOfMonth(SUB);

        final Paginator fetch = fetchFor(feedPageArgs(restore, SUB));
        assertEquals(Sorting.TOP, fetch.getSorting());
        assertEquals(TimePeriod.MONTH, fetch.getTimePeriod());
    }

    @Test
    public void theFrontpageTabSortReachesTheNextFetch() {
        // The frontpage is the one listing the tab menu records differently: it assigns
        // SortingUtil.frontpageSorting rather than calling setSorting, and getSubmissionSort reads
        // it back through a branch of its own. Both halves of that special case, together.
        SortingUtil.frontpageSorting = Sorting.TOP;
        SortingUtil.setTime("frontpage", TimePeriod.MONTH);

        final Paginator fetch =
                new SubredditPosts("frontpage", context)
                        .createPaginator("frontpage", Paginator.RECOMMENDED_MAX_LIMIT);

        assertEquals(Sorting.TOP, fetch.getSorting());
        assertEquals(TimePeriod.MONTH, fetch.getTimePeriod());
    }

    // --- the "Go to subreddit" sort ------------------------------------------------------------

    @Test
    public void theGoToSubredditSortReachesTheNextFetch() {
        // The screen the report was about. reloadSubs() rebuilds the adapter and discards any
        // restore still pending, so the page it builds fetches with the new sort. Routing it
        // back through restartTheme() puts the restore on the relaunch intent and fails here.
        final FeedRestoreState restore = restoreCarriedByARestart(SUB);

        userPicksTopOfMonth(SUB);
        restore.discard();

        final Paginator fetch = fetchFor(feedPageArgs(restore, SUB));
        assertEquals(Sorting.TOP, fetch.getSorting());
        assertEquals(TimePeriod.MONTH, fetch.getTimePeriod());
    }

    @Test
    public void theGoToSubredditSortReachesTheNextFetchAfterARandomResume() {
        // A hibernate entry keeps the extras the screen was opened with, so /r/random is reopened
        // as "random"; the restore in that entry was written after the listing resolved, so it
        // names the subreddit it landed on. Nothing matches and the restore is left pending.
        final FeedRestoreState restore = new FeedRestoreState();
        restore.read(recordedFeed(SUB));
        assertFalse(
                "a resumed random listing claims nothing on the way in",
                restore.applyTo("random", null, new Bundle()));
        assertTrue("so it is still waiting for a page", restore.isPending());

        // The listing resolves the same way again and the screen takes that name, which is now
        // the name the pending restore is holding out for.
        userPicksTopOfMonth(SUB);
        restore.discard();

        final Paginator fetch = fetchFor(feedPageArgs(restore, SUB));
        assertEquals(Sorting.TOP, fetch.getSorting());
        assertEquals(TimePeriod.MONTH, fetch.getTimePeriod());
    }

    @Test
    public void aRestartedScreenStillComesBackToTheFeedItLeft() {
        // The behaviour a sort change must not borrow, and the reason reloadSubs() cannot be
        // restartTheme(): a theme change comes back to the same posts at the same position by
        // rebuilding the listing from the cache -- no request, and so no new sort.
        final FeedRestoreState restore = restoreCarriedByARestart(SUB);

        userPicksTopOfMonth(SUB);

        final Bundle args = feedPageArgs(restore, SUB);
        assertTrue(
                "a theme restart is still meant to restore",
                args.getBoolean(SubmissionsView.ARG_RESTORE_FROM_CACHE, false));
        assertEquals("t3_abc123", args.getString(SubmissionsView.ARG_RESTORE_ANCHOR_ID));
        assertTrue(loaderFor(args).restoreFromCache);
    }

    // --- with and without "Resume where I left off" --------------------------------------------

    /** Both toolbar menus, on the setting the test method has just put in place. */
    private void assertBothMenusReachTheNextFetch() {
        final FeedRestoreState tab = restoreCarriedByARestart(SUB);
        assertTrue(
                "the tab claims its restore on the way in",
                feedPageArgs(tab, SUB).getBoolean(SubmissionsView.ARG_RESTORE_FROM_CACHE, false));
        userPicksTopOfMonth(SUB);
        final Paginator tabFetch = fetchFor(feedPageArgs(tab, SUB));
        assertEquals(Sorting.TOP, tabFetch.getSorting());
        assertEquals(TimePeriod.MONTH, tabFetch.getTimePeriod());

        clearRememberedSorts();

        final FeedRestoreState single = restoreCarriedByARestart(SUB);
        userPicksTopOfMonth(SUB);
        single.discard();
        final Paginator singleFetch = fetchFor(feedPageArgs(single, SUB));
        assertEquals(Sorting.TOP, singleFetch.getSorting());
        assertEquals(TimePeriod.MONTH, singleFetch.getTimePeriod());
    }

    @Test
    public void theSortReachesTheNextFetchWithResumeWhereILeftOffOn() {
        SettingValues.hibernate = true;
        SettingValues.hibernateResume = true;
        assertTrue(SettingValues.hibernateActive());

        assertBothMenusReachTheNextFetch();
    }

    @Test
    public void theSortReachesTheNextFetchWithResumeWhereILeftOffOff() {
        SettingValues.hibernate = false;
        SettingValues.hibernateResume = true;
        assertFalse(SettingValues.hibernateActive());

        assertBothMenusReachTheNextFetch();
    }

    @Test
    public void theSortReachesTheNextFetchWithResumeSwitchedOffFromTheOverflowMenu() {
        // The other half of hibernateActive(): the feature is on in Settings but switched off for
        // this session from the main overflow menu.
        SettingValues.hibernate = true;
        SettingValues.hibernateResume = false;
        assertFalse(SettingValues.hibernateActive());

        assertBothMenusReachTheNextFetch();
    }

    @Test
    public void theRestartTransportIsNotGatedByTheResumeSetting() {
        // Why the setting made no difference to the bug, and the trap in fixing it: "Resume where
        // I left off" gates the hibernate snapshot, the stack replayed after the process dies.
        // restartTheme() writes its restore onto the relaunch intent either way, so gating the
        // transport on the setting would leave everyone who has it on still broken.
        SettingValues.hibernate = false;
        SettingValues.hibernateResume = false;
        assertFalse(SettingValues.hibernateActive());

        final Intent relaunch = new Intent();
        FeedRestoreState.writeToIntent(relaunch, recordedFeed(SUB));

        assertTrue(relaunch.hasExtra(FeedRestoreState.EXTRA_SUB));
        assertTrue(restoreCarriedByARestart(SUB).isPending());
    }
}
