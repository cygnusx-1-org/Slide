package me.edgan.redditslide.Adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import com.lusfold.androidkeyvaluestore.KVStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.FakeReddit;
import me.edgan.redditslide.util.FakeReddit.Answer;
import net.dean.jraw.RedditClient;
import net.dean.jraw.models.Contribution;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * History, signed in, against a client id Reddit blocks {@code /api/info} for.
 *
 * <p>History keeps read posts as bare ids, so every row is looked up by id. With "Use /api/info
 * the Old Way" off, the default, each post is fetched on its own through the account's client;
 * on, and only while Enable Overrides is on too, one {@code /api/info} request does it as before.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class HistoryPostsLookupTest {
    @Nullable private RedditClient clientWas;
    private boolean loggedInWas;
    private SharedPreferences prefsWas;
    private SharedPreferences prefs;

    /** Ids the fake answers with a 404, as Reddit does for a post that is gone. */
    private final Set<String> gone = new HashSet<>();

    private boolean infoBlocked = true;
    private FakeReddit reddit;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        KVStore.init(context, "SEEN");
        KVStore.getInstance().clearTable();
        clientWas = Authentication.reddit;
        loggedInWas = Authentication.isLoggedIn;
        prefsWas = SettingValues.prefs;
        prefs = context.getSharedPreferences("HistoryPostsLookupTest", 0);
        prefs.edit().clear().commit();
        SettingValues.prefs = prefs;
        reddit = new FakeReddit(this::answer);
        Authentication.reddit = reddit.client;
        Authentication.isLoggedIn = true;
    }

    @After
    public void tearDown() {
        Authentication.reddit = clientWas;
        Authentication.isLoggedIn = loggedInWas;
        SettingValues.prefs = prefsWas;
        KVStore.getInstance().clearTable();
    }

    private Answer answer(Request request) {
        String path = request.url().encodedPath();
        if (path.startsWith("/api/info")) {
            if (infoBlocked) {
                return Answer.blocked();
            }
            String[] ids =
                    String.valueOf(request.url().queryParameter("id"))
                            .replace("t3_", "")
                            .split(",");
            return Answer.json(200, FakeReddit.infoListing(ids));
        }
        String id = path.substring("/comments/".length());
        if (gone.contains(id)) {
            return Answer.json(404, FakeReddit.NOT_FOUND);
        }
        return Answer.json(200, FakeReddit.postListing(id));
    }

    /** Read posts, newest first, each a minute older than the last. */
    private static List<String> read(int count) {
        List<String> ids = new ArrayList<>();
        long now = 1_700_000_000_000L;
        for (int i = 0; i < count; i++) {
            String id = String.format("h%03d", i);
            KVStore.getInstance().insert(id, String.valueOf(now - i * 60_000L));
            ids.add(id);
        }
        return ids;
    }

    private void setting(boolean overrides, boolean oldWay) {
        prefs.edit()
                .putBoolean(SettingValues.PREF_REDDIT_ENABLE_OVERRIDES, overrides)
                .putBoolean(SettingValues.PREF_USE_OLD_API_INFO, oldWay)
                .commit();
    }

    private static List<String> idsOf(List<Contribution> page) {
        List<String> out = new ArrayList<>();
        for (Contribution c : page) {
            out.add(c.getId());
        }
        return out;
    }

    /** One load, as the scroll listener or a refresh starts it; null is the error page. */
    @Nullable
    private static List<Contribution> loadOrError(HistoryPosts posts, boolean reset) {
        return posts.new LoadData(reset).doInBackground();
    }

    private static List<Contribution> load(HistoryPosts posts, boolean reset) {
        return Objects.requireNonNull(loadOrError(posts, reset));
    }

    @Test
    public void settingOffFetchesEachPostThroughTheAccountInOrder() {
        List<String> ids = read(3);
        setting(true, false);

        List<Contribution> page = load(new HistoryPosts(), true);

        assertEquals(ids, idsOf(page));
        assertEquals(
                Arrays.asList(
                        "/comments/h000?limit=1",
                        "/comments/h001?limit=1",
                        "/comments/h002?limit=1"),
                sorted(reddit.requests));
        for (String auth : reddit.authorizations) {
            assertEquals(FakeReddit.TOKEN, auth);
        }
    }

    @Test
    public void settingOnAsksInfoAloneAsBefore() {
        infoBlocked = false;
        List<String> ids = read(3);
        setting(true, true);

        List<Contribution> page = load(new HistoryPosts(), true);

        assertEquals(ids, idsOf(page));
        assertEquals(1, reddit.requests.size());
        assertTrue(reddit.requests.get(0), reddit.requests.get(0).startsWith("/api/info?id="));
    }

    @Test
    public void settingOnAndBlockedIsTheErrorPage() {
        read(3);
        setting(true, true);

        assertNull(loadOrError(new HistoryPosts(), true));
        assertEquals(1, reddit.requests.size());
    }

    @Test
    public void settingStoredOnWithOverridesOffFetchesEachPost() {
        List<String> ids = read(3);
        setting(false, true);

        List<Contribution> page = load(new HistoryPosts(), true);

        assertEquals(ids, idsOf(page));
        for (String request : reddit.requests) {
            assertFalse(request, request.startsWith("/api/info"));
        }
    }

    @Test
    public void aGonePostDoesNotEndTheHistory() {
        // 26 rows, one gone among the newest 25: the oldest has to be reached.
        List<String> ids = read(26);
        gone.add(ids.get(3));
        HistoryPosts posts = new HistoryPosts();

        List<Contribution> first = load(posts, true);
        assertEquals(24, first.size());
        assertFalse(posts.nomore);

        List<Contribution> second = load(posts, false);
        assertEquals(ids.subList(25, 26), idsOf(second));

        assertTrue(load(posts, false).isEmpty());
        assertTrue(posts.nomore);
    }

    @Test
    public void aPageOfNothingButGonePostsDoesNotEndTheHistory() {
        List<String> ids = read(30);
        gone.addAll(ids.subList(0, 25));
        HistoryPosts posts = new HistoryPosts();

        List<Contribution> first = load(posts, true);

        assertEquals(ids.subList(25, 30), idsOf(first));
        assertFalse(posts.nomore);
    }

    @Test
    public void otherRowsInTheStoreAreNeitherFetchedNorBreakIt() {
        List<String> ids = read(2);
        // Last Comments' count, and a Local Saved comment with its post id after the save time.
        KVStore.getInstance().insert("commentst3_h000", "12");
        KVStore.getInstance().insert("localsaved_t1_c1", "1700000000000:h000");

        List<Contribution> page = load(new HistoryPosts(), true);

        assertEquals(ids, idsOf(page));
        assertEquals(2, reddit.requests.size());
    }

    private static List<String> sorted(List<String> in) {
        List<String> out = new ArrayList<>(in);
        Collections.sort(out);
        return out;
    }
}
