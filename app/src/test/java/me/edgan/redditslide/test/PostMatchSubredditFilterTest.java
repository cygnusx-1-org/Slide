package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SettingValues;
import net.dean.jraw.models.Submission;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The "match subreddit filters by prefix" setting, on and off.
 *
 * <p>A setting that switches behaviour has two behaviours, and the suite only ever ran whichever
 * one the static happened to default to. Nothing distinguished them: inverting the flag -- so the
 * switch does the opposite of what its row says -- left the whole suite green, and so did changing
 * the six-character threshold that stops a short subreddit name from prefix-matching half of
 * reddit.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class PostMatchSubredditFilterTest {

    @SuppressWarnings("NullAway.Init")
    private TestUtils.SettingValuesSnapshot settingValuesWere;

    @Before
    public void setUp() {
        settingValuesWere = TestUtils.SettingValuesSnapshot.capture();

        SettingValues.filterOldPosts = false;
        SettingValues.subredditFiltersTillRestart = true;
        SettingValues.titleFilters = Collections.emptySet();
        SettingValues.textFilters = Collections.emptySet();
        SettingValues.userFilters = Collections.emptySet();
        SettingValues.domainFilters = Collections.emptySet();
        SettingValues.flairFilters = Collections.emptySet();
        SettingValues.alwaysExternal = Collections.emptySet();
    }

    @After
    public void tearDown() {
        settingValuesWere.restore();
    }

    private static Set<String> set(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    /** A link post in {@code subreddit}, built from the shared scalar fixture. */
    private static Submission postIn(String subreddit) throws Exception {
        final ObjectNode data;
        try (InputStream input =
                PostMatchSubredditFilterTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) new ObjectMapper().readTree(input);
        }
        data.put("name", "t3_sub" + subreddit);
        data.put("is_gallery", false);
        data.put("is_self", false);
        data.put("subreddit", subreddit);
        data.put("url", "https://example.com/story");
        data.put("domain", "example.com");
        return new Submission(data);
    }

    @Test
    public void prefixMatchingOnFiltersASubredditThatStartsWithTheFilter() throws Exception {
        SettingValues.subredditFilterPrefixMatching = true;
        SettingValues.subredditFilters = set("programming");

        assertTrue(
                "r/programminghumor starts with the filter",
                PostMatch.doesMatch(postIn("programminghumor"), "frontpage", false));
    }

    /** With the setting off the same filter has to be an exact name, not a prefix. */
    @Test
    public void prefixMatchingOffLeavesASubredditThatMerelyStartsWithTheFilter() throws Exception {
        SettingValues.subredditFilterPrefixMatching = false;
        SettingValues.subredditFilters = set("programming");

        assertFalse(
                "off means exact names only",
                PostMatch.doesMatch(postIn("programminghumor"), "frontpage", false));
    }

    /** The exact name is filtered either way; that is what makes the two settings comparable. */
    @Test
    public void anExactSubredditNameIsFilteredWithEitherSetting() throws Exception {
        SettingValues.subredditFilters = set("programming");

        SettingValues.subredditFilterPrefixMatching = true;
        assertTrue(PostMatch.doesMatch(postIn("programming"), "frontpage", false));

        SettingValues.subredditFilterPrefixMatching = false;
        assertTrue(PostMatch.doesMatch(postIn("programming"), "frontpage", false));
    }

    /**
     * Prefix matching is only applied to names of six characters or more, so a short filter cannot
     * take out a large slice of reddit by accident.
     */
    @Test
    public void aSubredditNameShorterThanSixCharsIsNotPrefixMatched() throws Exception {
        SettingValues.subredditFilterPrefixMatching = true;
        SettingValues.subredditFilters = set("pic");

        assertFalse(
                "r/pics is five characters, so \"pic\" must not prefix-match it",
                PostMatch.doesMatch(postIn("pics"), "frontpage", false));
    }

    @Test
    public void aSubredditNameOfExactlySixCharsIsPrefixMatched() throws Exception {
        SettingValues.subredditFilterPrefixMatching = true;
        SettingValues.subredditFilters = set("gamin");

        assertTrue(
                "r/gaming is exactly six characters, so the rule applies to it",
                PostMatch.doesMatch(postIn("gaming"), "frontpage", false));
    }

    /** Browsing the subreddit you filtered still shows its posts. */
    @Test
    public void thePostsOfTheSubredditYouAreBrowsingAreNotSubredditFiltered() throws Exception {
        SettingValues.subredditFilterPrefixMatching = true;
        SettingValues.subredditFilters = set("programming");

        assertFalse(
                "opening r/programming has to show r/programming",
                PostMatch.doesMatch(postIn("programming"), "programming", false));
    }
}
