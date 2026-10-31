package me.edgan.redditslide.util;

import static me.edgan.redditslide.util.MoreCommentsFixtures.comment;
import static me.edgan.redditslide.util.MoreCommentsFixtures.ids;
import static me.edgan.redditslide.util.MoreCommentsFixtures.listing;
import static me.edgan.redditslide.util.MoreCommentsFixtures.more;
import static me.edgan.redditslide.util.MoreCommentsFixtures.moreChildren;
import static me.edgan.redditslide.util.MoreCommentsFixtures.ok;
import static me.edgan.redditslide.util.MoreCommentsFixtures.thread;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.content.res.XmlResourceParser;
import android.os.Looper;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.edgan.redditslide.Adapters.CommentAdapter;
import me.edgan.redditslide.Adapters.CommentItem;
import me.edgan.redditslide.Adapters.CommentObject;
import me.edgan.redditslide.Adapters.MoreChildItem;
import me.edgan.redditslide.Adapters.MoreCommentViewHolder;
import me.edgan.redditslide.Adapters.SubmissionComments;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import net.dean.jraw.RedditClient;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.CommentSort;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.xmlpull.v1.XmlPullParser;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class AsyncLoadMoreTaskTest {
    private CommentAdapter adapter;
    private SubmissionComments dataSet;
    private Activity activity;
    private final ArrayList<CommentObject> rows = new ArrayList<>();
    private final HashMap<String, Integer> keys = new HashMap<>();
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).get();
        adapter = mock(CommentAdapter.class);
        dataSet = mock(SubmissionComments.class);
        adapter.dataSet = dataSet;
        adapter.currentComments = rows;
        when(dataSet.getSorting()).thenReturn(CommentSort.CONFIDENCE);
        useSession(new MoreCommentsSession(false, () -> 0L));
    }

    private void useSession(MoreCommentsSession session) {
        when(dataSet.getMoreCommentsSession()).thenReturn(session);
    }

    private MoreChildItem addRows(CommentNode parent) {
        MoreChildItem item = new MoreChildItem(parent, parent.getMoreChildren());
        rows.add(new CommentItem(parent));
        rows.add(item);
        rows.add(new CommentItem(thread(null, comment("after")).get(0)));
        for (int i = 0; i < rows.size(); i++) {
            keys.put(rows.get(i).getName(), i);
        }
        return item;
    }

    private AsyncLoadMoreTask task(MoreChildItem item, FocusedListingFetcher fetcher) {
        item.loading = true;
        return new AsyncLoadMoreTask(
                1,
                mock(MoreCommentViewHolder.class),
                "t1_parent",
                activity,
                adapter,
                mock(RecyclerView.class),
                rows,
                keys,
                item) {
            @Override
            protected FocusedListingFetcher newFetcher(CommentNode parent, CommentSort sort) {
                return fetcher;
            }
        };
    }

    /** Runs a whole load the way the framework would, minus the threads. */
    private void run(MoreChildItem item, FocusedListingFetcher fetcher) {
        AsyncLoadMoreTask task = task(item, fetcher);
        task.onPostExecute(task.doInBackground(item));
    }

    private FocusedListingFetcher perId() {
        return (id, limit) -> {
            requests.add(id);
            return ok(listing(comment(id)));
        };
    }

    private List<String> rowNames() {
        List<String> names = new ArrayList<>();
        for (CommentObject row : rows) {
            names.add(row.getName());
        }
        return names;
    }

    private static CommentNode parentWithMore(int count, List<String> ids) {
        return thread(null, comment("parent", more(count, ids.toArray(new String[0])))).get(0);
    }

    @Test
    public void partialLoadUpdatesTheTreesObjectAndPutsRepliesBeforeThePlaceholder() {
        CommentNode parent = parentWithMore(40, ids("r", 30));
        MoreChildItem item = addRows(parent);
        MoreCommentsSession session = new MoreCommentsSession(false, () -> 0L);
        session.markListed("parent");
        useSession(session);

        run(item, perId());

        assertEquals(25, requests.size());
        assertEquals(25, parent.getChildren().size());
        assertEquals(3, parent.get(0).getDepth() + 1);
        assertSame(parent, parent.get(0).getParent());
        // The tree's own node and the row agree, so a re-expand can't re-request loaded ids.
        assertSame(item.children, parent.getMoreChildren());
        assertEquals(ids("r", 30).subList(25, 30), parent.getMoreChildren().getChildrenIds());
        // Previous pending minus what loaded.
        assertEquals(Integer.valueOf(15), parent.getMoreChildren().getCount());

        List<String> names = rowNames();
        assertEquals(1 + 25 + 1 + 1, names.size());
        assertEquals("t1_r0", names.get(1));
        assertEquals("t1_r24", names.get(25));
        assertEquals("t1_parentmore", names.get(26));
        assertEquals("t1_after", names.get(27));
        assertEquals(Integer.valueOf(26), keys.get("t1_parentmore"));
        assertEquals(Integer.valueOf(27), keys.get("t1_after"));
        verify(adapter).notifyDataSetChanged();
        assertFalse(item.loading);
    }

    @Test
    public void listingRedditsDeferredCountBecomesThePending() {
        CommentNode parent = parentWithMore(200, ids("r", 6));
        MoreChildItem item = addRows(parent);

        run(
                item,
                (id, limit) ->
                        ok(
                                listing(
                                        comment(
                                                "parent",
                                                comment("r0"),
                                                comment("r1"),
                                                more(35, "r2", "r3", "r4", "r5")))));

        assertEquals(Integer.valueOf(35), parent.getMoreChildren().getCount());
        assertEquals(35, CommentTreeEditor.pendingReplies(parent));
        assertEquals(2, parent.getChildren().size());
    }

    @Test
    public void fullLoadRemovesThePlaceholderAndClearsTheTree() {
        CommentNode parent = parentWithMore(2, Arrays.asList("r0", "r1"));
        MoreChildItem item = addRows(parent);

        run(item, perId());

        assertFalse(parent.hasMoreComments());
        assertEquals(Arrays.asList("t1_parent", "t1_r0", "t1_r1", "t1_after"), rowNames());
        assertFalse(keys.containsKey("t1_parentmore"));
    }

    @Test
    public void nothingLoadedAndNothingRemainingRemovesThePlaceholder() {
        CommentNode parent = parentWithMore(2, Arrays.asList("r0", "r1"));
        MoreChildItem item = addRows(parent);

        run(item, (id, limit) -> ok(listing()));

        assertFalse(parent.hasMoreComments());
        assertEquals(Arrays.asList("t1_parent", "t1_after"), rowNames());
    }

    @Test
    public void loadedRepliesWithNestedMoresGetTheirOwnPlaceholders() {
        CommentNode parent = parentWithMore(1, Arrays.asList("r0"));
        MoreChildItem item = addRows(parent);

        run(item, (id, limit) -> ok(listing(comment("r0", comment("n"), more(3, "x")))));

        assertEquals(
                Arrays.asList("t1_parent", "t1_r0", "t1_n", "t1_r0more", "t1_after"), rowNames());
    }

    @Test
    public void topLevelMoreLoadsPerIdAtDepthOne() {
        CommentNode root = thread(moreChildren(30, ids("t", 30).toArray(new String[0])));
        MoreChildItem item = new MoreChildItem(root, root.getMoreChildren());
        rows.add(item);
        keys.put(item.getName(), 0);

        run(item, perId());

        assertEquals(25, requests.size());
        assertEquals(1, root.get(0).getDepth());
        assertTrue(root.get(0).isTopLevel());
        assertEquals("t3_postmore", rows.get(25).getName());
    }

    @Test
    public void failureKeepsThePlaceholderAndAllowsARetry() {
        CommentNode parent = parentWithMore(2, Arrays.asList("r0", "r1"));
        MoreChildItem item = addRows(parent);

        run(
                item,
                (id, limit) -> {
                    requests.add(id);
                    return new FocusedListing(403, null);
                });

        assertEquals(3, rows.size());
        assertFalse(item.loading);
        assertTrue(parent.hasMoreComments());
        requests.clear();

        run(item, perId());

        assertEquals(Arrays.asList("r0", "r1"), requests);
        assertEquals(Arrays.asList("t1_parent", "t1_r0", "t1_r1", "t1_after"), rowNames());
    }

    @Test
    public void aParentCollapsedMidLoadHasItsNewRowsHiddenAndIsRedrawn() {
        CommentNode parent = parentWithMore(1, Arrays.asList("r0"));
        MoreChildItem item = addRows(parent);

        run(item, perId());

        verify(adapter)
                .inheritHidden(eq("t1_parentmore"), eq(false), any());
        verify(adapter).invalidatePositionCache();
        verify(adapter).notifyDataSetChanged();
    }

    @Test
    public void aPlaceholderIsNotStuckAfterItsAncestorCollapsesMidLoad() {
        CommentNode parent = parentWithMore(2, Arrays.asList("r0", "r1"));
        MoreChildItem item = addRows(parent);
        AsyncLoadMoreTask task = task(item, perId());
        Object loaded = task.doInBackground(item);

        // hideNumber cancels the load; the framework then skips onPostExecute.
        task.cancel(true);
        task.onCancelled();

        assertFalse(item.loading);
        // A dropped load leaves the tree exactly as it was.
        assertTrue(parent.getChildren().isEmpty());
        assertTrue(parent.hasMoreComments());
        assertEquals(3, rows.size());
        assertTrue(loaded != null);
    }

    @Test
    public void aResultForAListARefreshReplacedIsDropped() {
        CommentNode parent = parentWithMore(2, Arrays.asList("r0", "r1"));
        MoreChildItem item = addRows(parent);
        AsyncLoadMoreTask task = task(item, perId());
        Integer loaded = task.doInBackground(item);

        adapter.currentComments = new ArrayList<>();
        task.onPostExecute(loaded);

        assertTrue(parent.getChildren().isEmpty());
        assertFalse(item.loading);
        verify(adapter, never()).inheritHidden(any(), anyBoolean(), any());
    }

    private FocusedListingFetcher parentListing() {
        return (id, limit) -> {
            requests.add(id + "@" + limit);
            return ok(
                    listing(
                            comment(
                                    "parent",
                                    comment("r0"),
                                    more(5, "r1", "r2", "r3", "r4", "r5"))));
        };
    }

    @Test
    public void aListingDroppedByACancelLetsTheNextLoadListAgain() {
        CommentNode parent = parentWithMore(10, ids("r", 6));
        MoreChildItem item = addRows(parent);
        AsyncLoadMoreTask task = task(item, parentListing());
        task.doInBackground(item);

        // What collapsing an ancestor does to a load whose listing has already come back.
        task.cancel(true);
        task.onCancelled();
        assertTrue(parent.getChildren().isEmpty());

        run(item, parentListing());
        assertEquals(Arrays.asList("parent@500", "parent@500"), requests);
        assertEquals(1, parent.getChildren().size());
    }

    @Test
    public void aListingDroppedByARefreshLetsTheNextLoadListAgain() {
        CommentNode parent = parentWithMore(10, ids("r", 6));
        MoreChildItem item = addRows(parent);
        AsyncLoadMoreTask task = task(item, parentListing());
        Integer loaded = task.doInBackground(item);

        adapter.currentComments = new ArrayList<>();
        task.onPostExecute(loaded);
        adapter.currentComments = rows;

        run(item, parentListing());
        assertEquals(Arrays.asList("parent@500", "parent@500"), requests);
    }

    /** Hosts the real error dialog, which needs an AppCompat theme. */
    public static class ThemedActivity extends AppCompatActivity {}

    private String rateLimitDialogFor(String reset) {
        ActivityController<ThemedActivity> controller =
                Robolectric.buildActivity(ThemedActivity.class);
        controller.get().setTheme(R.style.Theme_DARK);
        activity = controller.setup().get();
        MoreCommentsSession session = new MoreCommentsSession(false, () -> 0L);
        session.onResponse(200, "3.0", reset);
        useSession(session);
        MoreChildItem item = addRows(parentWithMore(2, Arrays.asList("r0", "r1")));

        run(item, perId());
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(requests.isEmpty());
        TextView message = ShadowDialog.getLatestDialog().findViewById(android.R.id.message);
        return message.getText().toString();
    }

    @Test
    public void aRateLimitPauseSaysHowLongToWait() {
        assertEquals(
                "Reddit's request limit is almost used up. Try loading more comments again in"
                        + " 2 minutes.",
                rateLimitDialogFor("90"));
    }

    @Test
    public void aShortRateLimitPauseCountsInSeconds() {
        assertEquals(
                "Reddit's request limit is almost used up. Try loading more comments again in"
                        + " 10 seconds.",
                rateLimitDialogFor("2"));
    }

    @Test
    public void theOptionPicksTheOldRoute() {
        CommentNode parent = parentWithMore(2, Arrays.asList("r0", "r1"));
        MoreChildItem item = addRows(parent);
        useSession(new MoreCommentsSession(true, () -> 0L));
        RedditClient clientWas = Authentication.reddit;
        Authentication.reddit = null;
        try {
            // No client means the old route gives up; what matters is it never asked the new one.
            assertEquals(null, task(item, perId()).doInBackground(item));
        } finally {
            Authentication.reddit = clientWas;
        }
        assertTrue(requests.isEmpty());
    }

    @Test
    public void theOptionIsOffByDefaultAndOnTheCommentSettingsScreen() throws Exception {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("MoreCommentsTest", 0);
        // setAllValues rewrites every setting, and the sandbox outlives this class, so all of them
        // go back afterwards or later tests would run under these defaults.
        Map<Field, Object> settingsWere = new HashMap<>();
        for (Field field : SettingValues.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                field.setAccessible(true);
                settingsWere.put(field, field.get(null));
            }
        }
        try {
            SettingValues.setAllValues(prefs);
            assertFalse(SettingValues.useOldLoadMoreComments);
        } finally {
            for (Map.Entry<Field, Object> setting : settingsWere.entrySet()) {
                setting.getKey().set(null, setting.getValue());
            }
        }

        String wanted = "@" + R.id.settings_comments_useOldLoadMoreComments;
        boolean found = false;
        try (XmlResourceParser parser =
                context.getResources().getLayout(R.layout.activity_settings_comments_child)) {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG
                        && wanted.equals(
                                parser.getAttributeValue(
                                        "http://schemas.android.com/apk/res/android", "id"))) {
                    found = true;
                }
            }
        }
        assertTrue(found);
    }
}
