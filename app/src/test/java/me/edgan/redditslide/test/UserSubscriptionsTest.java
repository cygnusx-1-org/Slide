package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import me.edgan.redditslide.CaseInsensitiveArrayList;
import me.edgan.redditslide.UserSubscriptions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Created by Alex Macleod on 28/03/2016. */
public class UserSubscriptionsTest {

    /**
     * Captured and put back: {@code pinned} is an app-wide static, and this suite is plain JUnit —
     * no Robolectric sandbox isolates it, and the test task forks a single JVM for every class — so
     * the mock left behind is visible to whatever class runs next. Captured in the field
     * initializer, which runs at class load and so before {@code setUp} overwrites it.
     */
    private static final SharedPreferences PINNED_WAS = UserSubscriptions.pinned;

    private final CaseInsensitiveArrayList subreddits =
            new CaseInsensitiveArrayList(
                    Arrays.asList(
                            "xyy",
                            "xyz",
                            "frontpage",
                            "mod",
                            "friends",
                            "random",
                            "aaa",
                            "pinned",
                            "pinned2"));

    @BeforeClass
    public static void setUp() {
        UserSubscriptions.pinned = new TestUtils.MockPreferences("pinned,pinned2");
    }

    @AfterClass
    public static void tearDown() {
        UserSubscriptions.pinned = PINNED_WAS;
    }

    @Test
    public void sortsSubreddits() {
        assertThat(
                UserSubscriptions.sort(subreddits),
                is(
                        new ArrayList<>(
                                Arrays.asList(
                                        "pinned",
                                        "pinned2",
                                        "frontpage",
                                        "all",
                                        "random",
                                        "friends",
                                        "mod",
                                        "aaa",
                                        "xyy",
                                        "xyz"))));
    }

    @Test
    public void sortsSubredditsNoExtras() {
        assertThat(
                UserSubscriptions.sortNoExtras(subreddits),
                is(
                        new ArrayList<>(
                                Arrays.asList(
                                        "pinned",
                                        "pinned2",
                                        "frontpage",
                                        "random",
                                        "friends",
                                        "mod",
                                        "aaa",
                                        "xyy",
                                        "xyz"))));
    }
}
