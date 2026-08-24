package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import androidx.annotation.Nullable;
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
 * The four remaining kinds of feed filter: body text, author, domain and flair.
 *
 * <p>{@code doesMatch} applies seven kinds of filter in sequence and, before this, three of them
 * had a test. Each is a separate list with its own matching rule -- title and body match on a
 * substring, subreddit on the whole name, domain through a URL comparison, flair through a
 * {@code subreddit:flair} entry split on the colon -- so they fail independently. Each of these
 * was broken on its own with the rest of the suite green.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class PostMatchFilterKindsTest {

    private static final String SUB = "pics";

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
        SettingValues.subredditFilters = Collections.emptySet();
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

    private static Submission post(
            String author, String selftext, String url, @Nullable String flair)
            throws Exception {
        final ObjectNode data;
        try (InputStream input =
                PostMatchFilterKindsTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) new ObjectMapper().readTree(input);
        }
        data.put("name", "t3_kinds");
        data.put("is_gallery", false);
        data.put("is_self", false);
        data.put("subreddit", SUB);
        data.put("author", author);
        data.put("selftext", selftext);
        data.put("url", url);
        data.put("domain", "example.com");
        if (flair == null) {
            data.putNull("link_flair_text");
        } else {
            data.put("link_flair_text", flair);
        }
        return new Submission(data);
    }

    private static Submission ordinaryPost() throws Exception {
        return post("someone", "nothing special", "https://example.com/story", null);
    }

    @Test
    public void anOrdinaryPostPassesEveryFilter() throws Exception {
        assertFalse(PostMatch.doesMatch(ordinaryPost(), SUB, false));
    }

    /** The body filter matches a substring of the selftext, not the whole thing. */
    @Test
    public void aSelftextFilterMatchesPartOfTheBody() throws Exception {
        SettingValues.textFilters = set("crypto");

        assertTrue(
                PostMatch.doesMatch(
                        post("someone", "check out my crypto thing", "https://example.com/s", null),
                        SUB,
                        false));
    }

    @Test
    public void aSelftextFilterThatMatchesNothingLeavesThePost() throws Exception {
        SettingValues.textFilters = set("crypto");

        assertFalse(PostMatch.doesMatch(ordinaryPost(), SUB, false));
    }

    /** The author filter is a substring rule too, so a filter for part of a name matches it. */
    @Test
    public void anAuthorFilterMatchesPartOfTheUsername() throws Exception {
        SettingValues.userFilters = set("spambot");

        assertTrue(
                PostMatch.doesMatch(
                        post("spambot9000", "hello", "https://example.com/s", null), SUB, false));
    }

    @Test
    public void anUnrelatedAuthorIsNotFiltered() throws Exception {
        SettingValues.userFilters = set("spambot");

        assertFalse(PostMatch.doesMatch(ordinaryPost(), SUB, false));
    }

    /** The domain filter goes through the URL comparison, so a subdomain matches its parent. */
    @Test
    public void aDomainFilterMatchesThePostsHost() throws Exception {
        SettingValues.domainFilters = set("example.com");

        assertTrue(
                PostMatch.doesMatch(
                        post("someone", "", "https://news.example.com/story", null), SUB, false));
    }

    @Test
    public void anUnlistedDomainIsNotFiltered() throws Exception {
        SettingValues.domainFilters = set("example.org");

        assertFalse(PostMatch.doesMatch(ordinaryPost(), SUB, false));
    }

    /** A flair filter is stored as {@code subreddit:flair} and only applies to that subreddit. */
    @Test
    public void aFlairFilterMatchesThePostsFlairInItsOwnSubreddit() throws Exception {
        SettingValues.flairFilters = set(SUB + ":spoiler");

        assertTrue(
                PostMatch.doesMatch(
                        post("someone", "", "https://example.com/s", "Spoiler"), SUB, false));
    }

    @Test
    public void aFlairFilterDoesNotMatchADifferentFlair() throws Exception {
        SettingValues.flairFilters = set(SUB + ":spoiler");

        assertFalse(
                PostMatch.doesMatch(
                        post("someone", "", "https://example.com/s", "Discussion"), SUB, false));
    }

    /**
     * The entries are scanned with {@code startsWith(baseSubreddit)} and then confirmed by
     * splitting on the colon, so a filter belonging to a subreddit whose name merely starts with
     * this one's has to be rejected by the second check.
     */
    @Test
    public void aFlairFilterForASubredditWhoseNameStartsWithThisOneDoesNotApply() throws Exception {
        SettingValues.flairFilters = set(SUB + "andvideos:spoiler");

        assertFalse(
                "r/picsandvideos' flair filter is not r/pics'",
                PostMatch.doesMatch(
                        post("someone", "", "https://example.com/s", "Spoiler"), SUB, false));
    }

    /** A flair filter written for another subreddit must not reach this one. */
    @Test
    public void aFlairFilterForAnotherSubredditDoesNotApply() throws Exception {
        SettingValues.flairFilters = set("videos:spoiler");

        assertFalse(
                PostMatch.doesMatch(
                        post("someone", "", "https://example.com/s", "Spoiler"), SUB, false));
    }
}
