package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.SortingUtil;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.Sorting;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The "hide posts older than N days" feed filter, at its boundary.
 *
 * <p>The setting reads "filter posts older than N days", and the age it compares against is a whole
 * number of days from integer division -- so a post whose age has just reached N is the first post
 * the setting is describing, and it has to be filtered. Off by one in the other direction and
 * "1 day" keeps everything under 48 hours, which is not what the row says.
 *
 * <p>The threshold was wrong in exactly that way until 0fad054d0 changed {@code >} to {@code >=},
 * and nothing in the suite covered {@code doesMatch} at all -- {@link PostMatchTest} says so in its
 * own header. Reverting that one character left the whole suite green.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class PostMatchOldPostsTest {

    /** Not "frontpage", so the sort lookup answers from SortingUtil rather than SettingValues.prefs. */
    private static final String MULTI = "programming+coding";

    @SuppressWarnings("NullAway.Init")
    private TestUtils.SettingValuesSnapshot settingValuesWere;

    @Before
    public void setUp() {
        // Every SettingValues static this method writes is app-wide and the test task forks one
        // JVM for the whole run, so the snapshot has to cover the filter sets too, not just the
        // three flags the cases below vary.
        settingValuesWere = TestUtils.SettingValuesSnapshot.capture();

        SettingValues.filterOldPosts = true;
        SettingValues.filterOldPostsDays = 7;
        // Content-filter lookups then read the in-memory map instead of the `filters`
        // SharedPreferences, which no unit test can assign.
        SettingValues.subredditFiltersTillRestart = true;
        SettingValues.titleFilters = Collections.emptySet();
        SettingValues.textFilters = Collections.emptySet();
        SettingValues.userFilters = Collections.emptySet();
        SettingValues.domainFilters = Collections.emptySet();
        SettingValues.subredditFilters = Collections.emptySet();
        SettingValues.flairFilters = Collections.emptySet();
        SettingValues.alwaysExternal = Collections.emptySet();

        // The age branch only runs when the current sort is neither Top nor Controversial.
        SortingUtil.setSorting(MULTI, Sorting.HOT);
    }

    @After
    public void tearDown() {
        settingValuesWere.restore();
        SortingUtil.sorting.remove(MULTI);
    }

    /**
     * A submission whose only interesting property is how long ago it was posted. Built from the
     * shared scalar fixture because JRAW's Submission unboxes several of those fields, so a bare
     * node of the ones this test cares about throws in the constructor.
     */
    private static Submission postedDaysAgo(double days) throws Exception {
        final ObjectNode data;
        try (InputStream input =
                PostMatchOldPostsTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) new ObjectMapper().readTree(input);
        }
        data.put(
                "created_utc",
                TimeUnit.MILLISECONDS.toSeconds(
                        System.currentTimeMillis() - (long) (days * 24 * 60 * 60 * 1000)));
        data.put("name", "t3_age" + Math.abs(Double.hashCode(days)));
        data.put("is_gallery", false);
        data.put("url", "https://example.com/story");
        data.put("domain", "example.com");
        return new Submission(data);
    }

    @Test
    public void aPostYoungerThanTheThresholdIsKept() throws Exception {
        assertFalse(
                "six days old, threshold seven: still inside the window",
                PostMatch.doesMatch(postedDaysAgo(6), MULTI, false));
    }

    /**
     * The boundary the fix moved. A post that has just turned seven days old is the youngest post
     * "older than 7 days" is meant to hide.
     */
    @Test
    public void aPostExactlyAtTheThresholdIsFiltered() throws Exception {
        assertTrue(
                "seven days old, threshold seven: the first post the setting describes",
                PostMatch.doesMatch(postedDaysAgo(7.5), MULTI, false));
    }

    @Test
    public void aPostWellPastTheThresholdIsFiltered() throws Exception {
        assertTrue(
                "thirty days old, threshold seven",
                PostMatch.doesMatch(postedDaysAgo(30), MULTI, false));
    }

    /** With the setting off, age decides nothing. */
    @Test
    public void ageIsIgnoredWhenTheSettingIsOff() throws Exception {
        SettingValues.filterOldPosts = false;

        assertFalse(
                "the age filter must not run when it is switched off",
                PostMatch.doesMatch(postedDaysAgo(365), MULTI, false));
    }

    /**
     * Top and Controversial are the sorts people use precisely to reach old posts, so the age
     * filter is skipped for them -- otherwise "Top of all time" comes back empty.
     */
    @Test
    public void ageIsIgnoredUnderTopSort() throws Exception {
        SortingUtil.setSorting(MULTI, Sorting.TOP);

        assertFalse(
                "Top must still reach old posts",
                PostMatch.doesMatch(postedDaysAgo(365), MULTI, false));
    }

    @Test
    public void ageIsIgnoredUnderControversialSort() throws Exception {
        SortingUtil.setSorting(MULTI, Sorting.CONTROVERSIAL);

        assertFalse(
                "Controversial must still reach old posts",
                PostMatch.doesMatch(postedDaysAgo(365), MULTI, false));
    }

    /**
     * The filter is for the mixed feeds, where an old post is a stale one somebody else's vote
     * dragged up. Inside a single subreddit the user asked for that subreddit's history.
     */
    @Test
    public void ageIsIgnoredInsideASingleSubreddit() throws Exception {
        SortingUtil.setSorting("programming", Sorting.HOT);
        try {
            assertFalse(
                    "an individual subreddit keeps its old posts",
                    PostMatch.doesMatch(postedDaysAgo(365), "programming", false));
        } finally {
            SortingUtil.sorting.remove("programming");
        }
    }
}
