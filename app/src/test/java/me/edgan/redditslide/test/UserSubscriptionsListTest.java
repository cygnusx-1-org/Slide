package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import java.util.Arrays;
import java.util.Map;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.CaseInsensitiveArrayList;
import me.edgan.redditslide.UserSubscriptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The subscription and pinned lists, which are stored as one comma-joined string per account.
 *
 * <p>That storage carries rules nothing checked: the key is the signed-in account, so one account's
 * list is never the next one's; the split de-duplicates, because the same subreddit arriving twice
 * would give the home pager two identical tabs; the shortcut list lowercases and drops
 * multireddits, because a launcher shortcut cannot open one; and the multireddit name map
 * lowercases its keys and, when asked for all, merges the reverse mapping in.
 *
 * <p>{@code getPinned} also caches its parsed list in a static, which {@code setPinned} exists to
 * drop — a cache with one invalidation point and no test on it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class UserSubscriptionsListTest {

    private static final String ACCOUNT = "account-one";
    private static final String OTHER_ACCOUNT = "account-two";

    @Nullable private String nameWas;
    @SuppressWarnings("NullAway.Init") private SharedPreferences subsWas;
    @SuppressWarnings("NullAway.Init") private SharedPreferences pinnedWas;
    @SuppressWarnings("NullAway.Init") private SharedPreferences multiWas;
    @Nullable private CaseInsensitiveArrayList pinsWas;

    @SuppressWarnings("NullAway.Init") private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        nameWas = Authentication.name;
        subsWas = UserSubscriptions.subscriptions;
        pinnedWas = UserSubscriptions.pinned;
        multiWas = UserSubscriptions.multiNameToSubs;
        pinsWas = UserSubscriptions.pins;

        Authentication.name = ACCOUNT;
        UserSubscriptions.subscriptions = prefs("subs-test");
        UserSubscriptions.pinned = prefs("pinned-test");
        UserSubscriptions.multiNameToSubs = prefs("multi-test");
        UserSubscriptions.pins = null;
    }

    @After
    public void tearDown() {
        // All five are process-wide statics shared with every later test class.
        Authentication.name = nameWas;
        UserSubscriptions.subscriptions = subsWas;
        UserSubscriptions.pinned = pinnedWas;
        UserSubscriptions.multiNameToSubs = multiWas;
        UserSubscriptions.pins = pinsWas;
    }

    private SharedPreferences prefs(String name) {
        SharedPreferences p = context.getSharedPreferences(name, Context.MODE_PRIVATE);
        p.edit().clear().commit();
        return p;
    }

    private static CaseInsensitiveArrayList list(String... items) {
        return new CaseInsensitiveArrayList(Arrays.asList(items));
    }

    // -----------------------------------------------------------------
    // Pinned
    // -----------------------------------------------------------------

    @Test
    public void aPinnedListRoundTripsThroughStorage() {
        UserSubscriptions.setPinned(list("pics", "videos"));

        assertEquals(list("pics", "videos"), UserSubscriptions.getPinned());
    }

    /** The same subreddit arriving twice would give the drawer two identical rows. */
    @Test
    public void aPinnedListWithARepeatIsDeDuplicated() {
        UserSubscriptions.pinned.edit().putString(ACCOUNT, "pics,videos,pics").commit();

        assertEquals(list("pics", "videos"), UserSubscriptions.getPinned());
    }

    @Test
    public void pinnedListsBelongToTheAccountThatSetThem() {
        UserSubscriptions.setPinned(list("pics"));
        assertEquals(list("pics"), UserSubscriptions.getPinned());

        Authentication.name = OTHER_ACCOUNT;
        UserSubscriptions.pins = null;

        assertTrue(
                "the next account starts with no pins of its own",
                UserSubscriptions.getPinned().isEmpty());
    }

    /** The parsed list is cached in a static; setPinned is the one thing that drops it. */
    @Test
    public void savingANewPinnedListReplacesTheCachedOne() {
        UserSubscriptions.setPinned(list("pics"));
        assertEquals(list("pics"), UserSubscriptions.getPinned()); // populates the cache

        UserSubscriptions.setPinned(list("videos"));

        assertEquals(
                "a stale cache would still answer with the old list",
                list("videos"),
                UserSubscriptions.getPinned());
    }

    @Test
    public void anAccountWithNoPinsGetsAnEmptyList() {
        assertTrue(UserSubscriptions.getPinned().isEmpty());
    }

    // -----------------------------------------------------------------
    // Subscriptions
    // -----------------------------------------------------------------

    @Test
    public void aSubscriptionListRoundTripsThroughStorage() {
        UserSubscriptions.setSubscriptions(list("pics", "videos"));

        assertEquals(list("pics", "videos"), UserSubscriptions.getSubscriptions(context));
    }

    /** A repeat would give the home pager two identical tabs. */
    @Test
    public void aSubscriptionListWithARepeatIsDeDuplicated() {
        UserSubscriptions.subscriptions.edit().putString(ACCOUNT, "pics,videos,pics").commit();

        assertEquals(list("pics", "videos"), UserSubscriptions.getSubscriptions(context));
    }

    @Test
    public void subscriptionListsBelongToTheAccountThatSetThem() {
        UserSubscriptions.setSubscriptions(list("pics"));
        UserSubscriptions.subscriptions.edit().putString(OTHER_ACCOUNT, "videos").commit();

        Authentication.name = OTHER_ACCOUNT;

        assertEquals(list("videos"), UserSubscriptions.getSubscriptions(context));
    }

    // -----------------------------------------------------------------
    // Launcher shortcuts
    // -----------------------------------------------------------------

    /** A launcher shortcut opens one subreddit, so the names are normalised. */
    @Test
    public void theShortcutListIsLowercased() {
        UserSubscriptions.subscriptions.edit().putString(ACCOUNT, "AskReddit,Pics").commit();

        assertEquals(
                Arrays.asList("askreddit", "pics"),
                Arrays.asList(UserSubscriptions.getSubscriptionsForShortcut(context).toArray()));
    }

    /** A multireddit is not a subreddit and cannot be the target of one. */
    @Test
    public void theShortcutListDropsMultireddits() {
        UserSubscriptions.subscriptions
                .edit()
                .putString(ACCOUNT, "pics,/m/tech,videos")
                .commit();

        CaseInsensitiveArrayList shortcuts =
                UserSubscriptions.getSubscriptionsForShortcut(context);

        assertEquals(Arrays.asList("pics", "videos"), Arrays.asList(shortcuts.toArray()));
        assertFalse(shortcuts.contains("/m/tech"));
    }

    // -----------------------------------------------------------------
    // Multireddit name map
    // -----------------------------------------------------------------

    @Test
    public void theMultiredditNameMapIsKeyedInLowercase() {
        UserSubscriptions.setSubNameToProperties("MyTechMulti", "android+programming");

        Map<String, String> map = UserSubscriptions.getMultiNameToSubs(false);

        assertEquals("android+programming", map.get("mytechmulti"));
        assertFalse("the display capitalisation is not a key", map.containsKey("MyTechMulti"));
    }

    /** Asking for all also gives the reverse mapping, so a sub list resolves back to its name. */
    @Test
    public void askingForAllAlsoMergesTheReverseMapping() {
        UserSubscriptions.setSubNameToProperties("MyTechMulti", "android+programming");

        assertFalse(
                "not present without all",
                UserSubscriptions.getMultiNameToSubs(false).containsKey("android+programming"));
        assertEquals(
                "MyTechMulti",
                UserSubscriptions.getMultiNameToSubs(true).get("android+programming"));
    }
}
