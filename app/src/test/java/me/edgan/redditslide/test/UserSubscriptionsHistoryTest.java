package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import java.util.Arrays;
import me.edgan.redditslide.CaseInsensitiveArrayList;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The subreddit history is one comma separated string, so "already recorded" has to mean the whole
 * entry is present. A substring check silently drops any sub whose name sits inside a stored one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class UserSubscriptionsHistoryTest {

    private SharedPreferences subscriptionsWas;

    @Before
    public void setUp() {
        subscriptionsWas = UserSubscriptions.subscriptions;
        final SharedPreferences prefs =
                ((Context) ApplicationProvider.getApplicationContext())
                        .getSharedPreferences("UserSubscriptionsHistoryTest", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        UserSubscriptions.subscriptions = prefs;
    }

    @After
    public void tearDown() {
        UserSubscriptions.subscriptions = subscriptionsWas;
    }

    private static void assertHistory(String... expected) {
        assertEquals(Arrays.asList(expected), UserSubscriptions.getHistory());
    }

    @Test
    public void addSubToHistorySubstringOfStoredSubIsRecorded() {
        UserSubscriptions.addSubToHistory("pics");
        UserSubscriptions.addSubToHistory("pic");
        assertTrue(UserSubscriptions.getHistory().contains("pic"));
        assertTrue(UserSubscriptions.getHistory().contains("pics"));
    }

    @Test
    public void addSubToHistoryPrefixAndSuffixSubstringsAreRecorded() {
        UserSubscriptions.addSubToHistory("androidapps");
        UserSubscriptions.addSubToHistory("android");
        UserSubscriptions.addSubToHistory("apps");
        assertHistory("", "androidapps", "android", "apps");
    }

    @Test
    public void addSubToHistoryExactDuplicateIsNotAddedTwice() {
        UserSubscriptions.addSubToHistory("pics");
        UserSubscriptions.addSubToHistory("pics");
        UserSubscriptions.addSubToHistory("PICS");
        assertHistory("", "pics");
    }

    @Test
    public void addSubToHistoryEmptyNameIsIgnored() {
        UserSubscriptions.addSubToHistory("pics");
        UserSubscriptions.addSubToHistory("");
        assertHistory("", "pics");
    }

    @Test
    public void addSubsToHistoryListSubstringOfStoredSubIsRecorded() {
        UserSubscriptions.addSubToHistory("androidapps");
        UserSubscriptions.addSubsToHistory(new CaseInsensitiveArrayList(Arrays.asList("android")));
        assertHistory("", "androidapps", "android");
    }

    @Test
    public void addSubsToHistoryListSubstringOfEarlierEntryInSameBatchIsRecorded() {
        UserSubscriptions.addSubsToHistory(
                new CaseInsensitiveArrayList(Arrays.asList("pics", "pic", "pics")));
        assertHistory("", "pics", "pic");
    }

    @Test
    public void removeSubsFromHistoryDropsOnlyTheNamedEntries() {
        UserSubscriptions.addSubToHistory("pics");
        UserSubscriptions.addSubToHistory("pic");
        UserSubscriptions.addSubToHistory("funny");
        UserSubscriptions.removeSubsFromHistory(Arrays.asList("PIC", "funny"));
        assertHistory("", "pics");
    }

    @Test
    public void removedSubCanBeAddedAgain() {
        UserSubscriptions.addSubToHistory("pics");
        UserSubscriptions.removeSubsFromHistory(Arrays.asList("pics"));
        UserSubscriptions.addSubToHistory("pics");
        assertHistory("", "pics");
    }

    @Test
    public void getUnsubscribedSubredditHistoryLeavesOutSubscribedSubsAndIsSorted() {
        UserSubscriptions.setSubscriptions(new CaseInsensitiveArrayList(Arrays.asList("pics")));
        UserSubscriptions.addSubToHistory("zelda");
        UserSubscriptions.addSubToHistory("pics");
        UserSubscriptions.addSubToHistory("Android");
        assertEquals(
                Arrays.asList("android", "zelda"),
                UserSubscriptions.getUnsubscribedSubredditHistory(
                        ApplicationProvider.getApplicationContext()));
    }

    @Test
    public void historyVersionChangesOnEveryWrite() {
        int version = UserSubscriptions.getHistoryVersion();
        UserSubscriptions.addSubToHistory("pics");
        assertNotEquals(version, version = UserSubscriptions.getHistoryVersion());
        UserSubscriptions.removeSubsFromHistory(Arrays.asList("pics"));
        assertNotEquals(version, version = UserSubscriptions.getHistoryVersion());
        UserSubscriptions.clearHistory();
        assertNotEquals(version, UserSubscriptions.getHistoryVersion());
        assertHistory("");
    }

    @Test
    public void unsubscribedSubShowsUpInUnsubscribedHistory() {
        final Context context = ApplicationProvider.getApplicationContext();
        final boolean storeHistoryWas = SettingValues.storeHistory;
        SettingValues.storeHistory = true;
        try {
            UserSubscriptions.setSubscriptions(
                    new CaseInsensitiveArrayList(Arrays.asList("pics", "funny")));
            UserSubscriptions.removeSubreddit("pics", context);
            assertEquals(
                    Arrays.asList("pics"), UserSubscriptions.getUnsubscribedSubredditHistory(context));
        } finally {
            SettingValues.storeHistory = storeHistoryWas;
        }
    }

    @Test
    public void removeSubredditDoesNotRecordHistoryWhenHistoryIsOff() {
        final Context context = ApplicationProvider.getApplicationContext();
        final boolean storeHistoryWas = SettingValues.storeHistory;
        SettingValues.storeHistory = false;
        try {
            UserSubscriptions.setSubscriptions(
                    new CaseInsensitiveArrayList(Arrays.asList("pics", "funny")));
            UserSubscriptions.removeSubreddit("pics", context);
            assertHistory("");
        } finally {
            SettingValues.storeHistory = storeHistoryWas;
        }
    }
}
