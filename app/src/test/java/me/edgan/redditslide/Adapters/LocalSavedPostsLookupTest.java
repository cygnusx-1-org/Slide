package me.edgan.redditslide.Adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import com.lusfold.androidkeyvaluestore.KVStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionViews.LocalSaved;
import me.edgan.redditslide.util.FakeReddit;
import me.edgan.redditslide.util.FakeReddit.Answer;
import net.dean.jraw.RedditClient;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Contribution;
import okhttp3.Request;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Local Saved, signed in, with {@code /api/info} blocked: a promoted post and a promoted comment
 * with its post id both show, and a comment saved before post ids were kept is quietly left out.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class LocalSavedPostsLookupTest {
    @Nullable private RedditClient clientWas;
    private boolean loggedInWas;
    private SharedPreferences prefsWas;
    private FakeReddit reddit;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        KVStore.init(context, "SEEN");
        KVStore.getInstance().clearTable();
        clientWas = Authentication.reddit;
        loggedInWas = Authentication.isLoggedIn;
        prefsWas = SettingValues.prefs;
        SharedPreferences prefs = context.getSharedPreferences("LocalSavedPostsLookupTest", 0);
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
            return Answer.blocked();
        }
        if (path.equals("/comments/post1/_/c1")) {
            return Answer.json(
                    200,
                    "[{\"kind\":\"Listing\",\"data\":{\"children\":["
                            + FakeReddit.post("post1")
                            + "]}},{\"kind\":\"Listing\",\"data\":{\"children\":[{\"kind\":\"t1\","
                            + "\"data\":{\"id\":\"c1\",\"name\":\"t1_c1\",\"link_id\":\"t3_post1\","
                            + "\"body\":\"hi\",\"author\":\"me\",\"created_utc\":1}}]}}]");
        }
        return Answer.json(200, FakeReddit.postListing(path.substring("/comments/".length())));
    }

    @Test
    public void promotedThingsLoadEachOnItsOwn() {
        KVStore.getInstance().insert(LocalSaved.PROMOTED_PREFIX + "t3_p1", "3000");
        KVStore.getInstance().insert(LocalSaved.PROMOTED_PREFIX + "t1_old", "2000");
        KVStore.getInstance().insert(LocalSaved.PROMOTED_PREFIX + "t1_c1", "1000:post1");

        List<Contribution> loaded =
                Objects.requireNonNull(new LocalSavedPosts().new LoadData(false).doInBackground());

        List<String> names = new ArrayList<>();
        for (Contribution c : loaded) {
            names.add(c.getFullName());
        }
        assertEquals(Arrays.asList("t3_p1", "t1_c1"), names);
        // What the Saved row shows above a comment.
        assertEquals("Title post1", ((Comment) loaded.get(1)).getSubmissionTitle());
        for (String request : reddit.requests) {
            assertFalse(request, request.contains("old"));
        }
        assertEquals(2, reddit.requests.size());
    }
}
