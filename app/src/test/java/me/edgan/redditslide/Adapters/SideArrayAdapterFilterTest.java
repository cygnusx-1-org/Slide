package me.edgan.redditslide.Adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.widget.Filter;
import android.widget.ListView;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import me.edgan.redditslide.Activities.MainActivity;
import me.edgan.redditslide.R;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.test.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * The drawer's subreddit filter, and the state it is allowed to publish.
 *
 * <p>This adapter is unusual: {@code getCount()} measures {@code fitems}, while the rows themselves
 * come from {@link android.widget.ArrayAdapter}'s own backing list, which {@code clear()} and
 * {@code addAll()} maintain. Those are two containers describing one list, so every publish has to
 * move them together. The old {@code publishResults} did not -- it assigned {@code fitems} first,
 * changing the reported count with nothing notified, then called {@code clear()} and {@code
 * addAll()}, each of which notifies on its own. A layout landing on either intermediate state saw a
 * count that did not match the rows the ListView held, and walked off the end of its own children:
 *
 * <pre>
 * NullPointerException: 'ViewGroup$LayoutParams View.getLayoutParams()' on a null object reference
 *     at android.widget.AbsListView$RecycleBin.addScrapView(AbsListView.java:7417)
 *     at android.widget.ListView.layoutChildren(ListView.java:1839)
 *     at androidx.drawerlayout.widget.DrawerLayout.onLayout(DrawerLayout.java:1287)
 * </pre>
 *
 * <p>So the two things pinned here are that a publish notifies exactly once, and that when it does,
 * the count and the rows already agree. A null {@code results.values} is pinned too: it used to
 * leave {@code fitems} null for {@code getCount()}, {@code getView()} and DrawerController's
 * IME-search handler to dereference.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class SideArrayAdapterFilterTest {

    private SideArrayAdapter adapter;
    private SharedPreferences multiWas;

    /** Counts notifications, and records what the adapter reported at each one. */
    private static class RecordingObserver extends DataSetObserver {
        private final SideArrayAdapter adapter;
        final ArrayList<int[]> countsAtNotify = new ArrayList<>();

        RecordingObserver(SideArrayAdapter adapter) {
            this.adapter = adapter;
        }

        @Override
        public void onChanged() {
            // getCount() reads fitems; getItem()'s backing list is ArrayAdapter's. Record both so
            // a disagreement at the moment of notification is visible.
            int rows = 0;
            while (true) {
                try {
                    if (adapter.getItem(rows) == null) break;
                    rows++;
                } catch (IndexOutOfBoundsException e) {
                    break;
                }
            }
            countsAtNotify.add(new int[] {adapter.getCount(), rows});
        }
    }

    @Before
    public void setUp() {
        TestUtils.seedRedditApplication();
        final Context app = ApplicationProvider.getApplicationContext();
        // The constructor reads the multireddit name map straight off this static, which is
        // process-wide and shared with every later test class, so put it back in tearDown.
        multiWas = UserSubscriptions.multiNameToSubs;
        UserSubscriptions.multiNameToSubs =
                app.getSharedPreferences("multi-sidearray-test", Context.MODE_PRIVATE);
        UserSubscriptions.multiNameToSubs.edit().clear().commit();

        // Only the two context calls this adapter's construction and filtering actually make:
        // ArrayAdapter's constructor inflates through the context, and performFiltering reads the
        // "go to" label. Everything else about MainActivity is irrelevant to the filter.
        final MainActivity activity = mock(MainActivity.class);
        when(activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .thenReturn(app.getSystemService(Context.LAYOUT_INFLATER_SERVICE));
        when(activity.getString(R.string.search_goto)).thenReturn(app.getString(R.string.search_goto));

        final ArrayList<String> subs =
                new ArrayList<>(Arrays.asList("frontpage", "all", "android", "pics", "news"));
        adapter = new SideArrayAdapter(activity, subs, new ArrayList<>(subs), mock(ListView.class));
    }

    @After
    public void tearDown() {
        UserSubscriptions.multiNameToSubs = multiWas;
        TestUtils.clearRedditApplication();
    }

    /** Runs the real Filter and waits for its worker thread to publish back. */
    private void filter(String constraint) throws Exception {
        final CountDownLatch published = new CountDownLatch(1);
        adapter.getFilter()
                .filter(
                        constraint,
                        new Filter.FilterListener() {
                            @Override
                            public void onFilterComplete(int count) {
                                published.countDown();
                            }
                        });
        // The filter hands results back through the main looper, which Robolectric holds paused.
        for (int i = 0; i < 100 && published.getCount() > 0; i++) {
            ShadowLooper.idleMainLooper();
            if (published.await(20, TimeUnit.MILLISECONDS)) break;
        }
        ShadowLooper.idleMainLooper();
        assertEquals("the filter never published", 0, published.getCount());
    }

    /**
     * The crash's precondition. Every notification must describe a settled list: if the count and
     * the rows disagree at the moment the ListView is told to re-read, it lays out against one and
     * indexes the other.
     */
    @Test
    public void everyNotificationSeesTheCountAndTheRowsAgree() throws Exception {
        final RecordingObserver observer = new RecordingObserver(adapter);
        adapter.registerDataSetObserver(observer);

        filter("pi");

        assertTrue("the publish notified at all", !observer.countsAtNotify.isEmpty());
        for (int[] seen : observer.countsAtNotify) {
            // getCount() is fitems.size() + 1: the extra entry is the trailing spacer row, which
            // has no backing item, so rows is always one short of the reported count.
            assertEquals(
                    "count and rows disagreed at a notification: count="
                            + seen[0]
                            + " rows="
                            + seen[1],
                    seen[0] - 1,
                    seen[1]);
        }
    }

    /** One publish is one change; the intermediate states must not reach the ListView at all. */
    @Test
    public void aPublishNotifiesExactlyOnce() throws Exception {
        final RecordingObserver observer = new RecordingObserver(adapter);
        adapter.registerDataSetObserver(observer);

        filter("news");

        assertEquals(
                "a publish must notify once, not once per clear()/addAll()",
                1,
                observer.countsAtNotify.size());
    }

    /** Filtering to nothing is a normal outcome, not a null list for callers to trip over. */
    @Test
    public void filteringToNoMatchesLeavesAUsableList() throws Exception {
        filter("zzzznosuchsubreddit");

        assertNotNull("fitems must never be null", adapter.fitems);
        // No subreddit matched, so the only entry is the "go to" row the filter appends.
        assertEquals(1, adapter.fitems.size());
        assertEquals(2, adapter.getCount());
    }

    /** The ordinary case still filters. */
    @Test
    public void filteringNarrowsTheList() throws Exception {
        filter("pics");

        assertTrue(adapter.fitems.contains("pics"));
        assertTrue("unmatched subs are dropped", !adapter.fitems.contains("android"));
    }

    /**
     * getView() inflates a subreddit row for every position but the last, and a spacer for that
     * one. Two layouts is two view types, and AbsListView's RecycleBin pools scrap by type -- told
     * there is only one, it hands a row's recycled view to the spacer and back again.
     */
    @Test
    public void theSpacerAndTheSubredditRowsAreDistinctViewTypes() throws Exception {
        filter("");

        assertEquals("two layouts means two types", 2, adapter.getViewTypeCount());
        final int spacer = adapter.getCount() - 1;
        assertEquals(adapter.getItemViewType(0), adapter.getItemViewType(spacer - 1));
        assertTrue(
                "the trailing spacer is not a subreddit row",
                adapter.getItemViewType(spacer) != adapter.getItemViewType(0));
        assertTrue(
                "every type is inside the declared range",
                adapter.getItemViewType(spacer) < adapter.getViewTypeCount());
    }

    /** An empty constraint restores the full subscription list. */
    @Test
    public void anEmptyConstraintRestoresEverything() throws Exception {
        filter("pics");
        filter("");

        assertEquals(5, adapter.fitems.size());
        assertEquals(6, adapter.getCount());
    }
}
