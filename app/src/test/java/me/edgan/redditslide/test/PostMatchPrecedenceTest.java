package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.Hidden;
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
 * The two things that override the rest of {@code doesMatch}: a post the user hid, and the
 * {@code ignore18} argument.
 *
 * <p>Both change the answer for a post that every other rule agrees about, and neither was ever
 * varied: every call in the suite passed {@code ignore18 = false} against an empty {@code Hidden},
 * so inverting the flag and turning the hidden short-circuit into "never filter" -- posts the user
 * explicitly hid coming straight back into the feed -- both left the whole suite green.
 *
 * <p>{@code ignore18} is what the NSFW-preview screens pass to keep an over-18 post visible after
 * the user has agreed to see it; it must suppress the NSFW content filters and nothing else, so it
 * is checked here against an ordinary filter too.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class PostMatchPrecedenceTest {

    private static final String FULLNAME = "t3_precedence";
    private static final String SUB = "frontpage";

    @SuppressWarnings("NullAway.Init")
    private TestUtils.SettingValuesSnapshot settingValuesWere;

    @Before
    public void setUp() {
        settingValuesWere = TestUtils.SettingValuesSnapshot.capture();

        SettingValues.filterOldPosts = false;
        SettingValues.subredditFiltersTillRestart = true;
        SettingValues.showNSFWContent = true;
        SettingValues.titleFilters = Collections.emptySet();
        SettingValues.textFilters = Collections.emptySet();
        SettingValues.userFilters = Collections.emptySet();
        SettingValues.domainFilters = Collections.emptySet();
        SettingValues.subredditFilters = Collections.emptySet();
        SettingValues.flairFilters = Collections.emptySet();
        SettingValues.alwaysExternal = Collections.emptySet();

        Hidden.id.remove(FULLNAME);
        clearMemoryContentFilters();
        ContentType.invalidateTypeCache();
    }

    @After
    public void tearDown() {
        settingValuesWere.restore();
        // Hidden.id, the content-filter map and the type cache are all process-wide statics.
        Hidden.id.remove(FULLNAME);
        clearMemoryContentFilters();
        ContentType.invalidateTypeCache();
    }

    @SuppressWarnings("unchecked")
    private static void clearMemoryContentFilters() {
        try {
            Field f = PostMatch.class.getDeclaredField("memoryContentFilters");
            f.setAccessible(true);
            ((Map<String, Boolean>) f.get(null)).clear();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to clear PostMatch.memoryContentFilters", e);
        }
    }

    private static Submission post(boolean nsfw, String title) throws Exception {
        final ObjectNode data;
        try (InputStream input =
                PostMatchPrecedenceTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) new ObjectMapper().readTree(input);
        }
        data.put("name", FULLNAME);
        data.put("is_gallery", false);
        data.put("is_self", false);
        data.put("over_18", nsfw);
        data.put("title", title);
        data.put("subreddit", "pics");
        data.put("url", "https://example.com/photo.png");
        data.put("domain", "example.com");
        return new Submission(data);
    }

    /** Turns on one NSFW content filter for the feed being browsed. */
    private static void filterNsfwImages() {
        boolean[] values = new boolean[16];
        values[11] = true; // nsfwImages
        PostMatch.setChosen(values, SUB);
    }

    // -----------------------------------------------------------------
    // The hidden short-circuit
    // -----------------------------------------------------------------

    @Test
    public void aHiddenPostIsFilteredEvenWhenNoFilterWouldMatchIt() throws Exception {
        Submission submission = post(false, "an ordinary post");
        assertFalse("nothing filters it to begin with", PostMatch.doesMatch(submission, SUB, false));

        Hidden.id.add(FULLNAME);

        assertTrue(
                "a post the user hid stays out of the feed regardless of the filters",
                PostMatch.doesMatch(submission, SUB, false));
    }

    /** Hiding wins over the argument that lets NSFW posts through, too. */
    @Test
    public void aHiddenPostIsFilteredEvenWhenNsfwIsBeingIgnored() throws Exception {
        Hidden.id.add(FULLNAME);

        assertTrue(PostMatch.doesMatch(post(true, "an ordinary post"), SUB, true));
    }

    @Test
    public void unhidingAPostPutsItBack() throws Exception {
        Submission submission = post(false, "an ordinary post");
        Hidden.id.add(FULLNAME);
        assertTrue(PostMatch.doesMatch(submission, SUB, false));

        Hidden.id.remove(FULLNAME);

        assertFalse(PostMatch.doesMatch(submission, SUB, false));
    }

    // -----------------------------------------------------------------
    // ignore18
    // -----------------------------------------------------------------

    @Test
    public void anNsfwPostIsFilteredByItsContentFilterWhenNsfwIsNotBeingIgnored()
            throws Exception {
        filterNsfwImages();

        assertTrue(
                "the NSFW image filter applies",
                PostMatch.doesMatch(post(true, "an ordinary post"), SUB, false));
    }

    @Test
    public void ignoringNsfwSuppressesTheNsfwContentFilter() throws Exception {
        filterNsfwImages();

        assertFalse(
                "the screen that already asked the user to confirm keeps the post",
                PostMatch.doesMatch(post(true, "an ordinary post"), SUB, true));
    }

    /** It suppresses the NSFW content filters and nothing else. */
    @Test
    public void ignoringNsfwDoesNotSuppressAnOrdinaryTitleFilter() throws Exception {
        SettingValues.titleFilters = new HashSet<>(Arrays.asList("giveaway"));

        assertTrue(
                "a title filter still applies to an over-18 post",
                PostMatch.doesMatch(post(true, "free giveaway inside"), SUB, true));
    }

    /** A safe post is unaffected by the argument either way. */
    @Test
    public void ignoringNsfwChangesNothingForASafePost() throws Exception {
        filterNsfwImages();

        assertFalse(PostMatch.doesMatch(post(false, "an ordinary post"), SUB, true));
        assertFalse(PostMatch.doesMatch(post(false, "an ordinary post"), SUB, false));
    }
}
