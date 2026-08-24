package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import me.edgan.redditslide.ContentType;
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
 * The content-type half of {@code doesMatch}: the switch that turns a resolved
 * {@link ContentType.Type} into the per-subreddit checkbox that decides whether the post is hidden.
 *
 * <p>There are two such switches, one for NSFW posts and one for the rest, and they have to name
 * the same types. The NSFW one had no {@code case ALBUM}, so {@code isNsfwAlbum} was written by the
 * dialog, read back by its accessor and never reached the feed -- ticking "NSFW albums" did
 * nothing. Neither switch named {@code VREDDIT_DIRECT}, so a v.redd.it link carrying a DASH url
 * (the type every v.redd.it post resolves to once its direct url is known) escaped the video
 * filter in both.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class PostMatchNsfwContentTypeTest {

    private static final String SUB = "nsfwcontenttypes";

    /** An imgur album, which resolves to {@link ContentType.Type#ALBUM}. */
    private static final String ALBUM_URL = "https://imgur.com/a/abcdef";

    /** A v.redd.it link with the DASH url in it, which resolves to {@code VREDDIT_DIRECT}. */
    private static final String VREDDIT_DIRECT_URL = "https://v.redd.it/abcdef/DASH_720.mp4";

    /** Slot indexes into the {@code boolean[]} {@code setChosen} takes from the dialog. */
    private static final int VIDEOS = 7;

    private static final int NSFW_ALBUMS = 8;
    private static final int NSFW_VIDEOS = 15;

    @SuppressWarnings("NullAway.Init")
    private TestUtils.SettingValuesSnapshot settingValuesWere;

    @Before
    public void setUp() {
        settingValuesWere = TestUtils.SettingValuesSnapshot.capture();

        SettingValues.filterOldPosts = false;
        // The NSFW switch only runs when NSFW content is shown at all; with it off every NSFW post
        // is filtered before the switch is reached.
        SettingValues.showNSFWContent = true;
        SettingValues.subredditFiltersTillRestart = true;
        SettingValues.titleFilters = Collections.emptySet();
        SettingValues.textFilters = Collections.emptySet();
        SettingValues.userFilters = Collections.emptySet();
        SettingValues.domainFilters = Collections.emptySet();
        SettingValues.subredditFilters = Collections.emptySet();
        SettingValues.flairFilters = Collections.emptySet();
        SettingValues.alwaysExternal = Collections.emptySet();

        clearMemoryContentFilters();
    }

    @After
    public void tearDown() {
        settingValuesWere.restore();
        clearMemoryContentFilters();
        ContentType.invalidateTypeCache();
    }

    /** The map is a private static with no reset hook, and this JVM is shared. */
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

    /** Turns on one checkbox of the sixteen and leaves the other fifteen off. */
    private static void turnOnFilter(int slot) {
        boolean[] values = new boolean[16];
        values[slot] = true;
        PostMatch.setChosen(values, SUB);
    }

    /** The fullname is the content-type cache key, so each post needs its own. */
    private static Submission post(String fullname, String url, boolean nsfw) throws Exception {
        final ObjectNode data;
        try (InputStream input =
                PostMatchNsfwContentTypeTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) new ObjectMapper().readTree(input);
        }
        data.put("name", fullname);
        data.put("is_gallery", false);
        data.put("is_self", false);
        data.put("subreddit", SUB);
        data.put("author", "someone");
        data.put("selftext", "");
        data.put("url", url);
        data.put("domain", "example.com");
        data.put("over_18", nsfw);
        data.putNull("link_flair_text");
        return new Submission(data);
    }

    @Test
    public void anNsfwAlbumIsFilteredWhenTheNsfwAlbumFilterIsOn() throws Exception {
        turnOnFilter(NSFW_ALBUMS);

        assertTrue(
                "\"NSFW albums\" is ticked for this subreddit",
                PostMatch.doesMatch(post("t3_nsfwalbum1", ALBUM_URL, true), SUB, false));
    }

    @Test
    public void anNsfwAlbumIsKeptWhenTheNsfwAlbumFilterIsOff() throws Exception {
        turnOnFilter(NSFW_VIDEOS);

        assertFalse(
                "another slot must not filter albums",
                PostMatch.doesMatch(post("t3_nsfwalbum2", ALBUM_URL, true), SUB, false));
    }

    @Test
    public void anNsfwVredditDirectVideoIsFilteredByTheNsfwVideoFilter() throws Exception {
        turnOnFilter(NSFW_VIDEOS);

        assertTrue(
                "a DASH v.redd.it url is a video like any other",
                PostMatch.doesMatch(post("t3_nsfwvreddit", VREDDIT_DIRECT_URL, true), SUB, false));
    }

    @Test
    public void anOrdinaryVredditDirectVideoIsFilteredByTheVideoFilter() throws Exception {
        turnOnFilter(VIDEOS);

        assertTrue(
                "the ordinary switch had no VREDDIT_DIRECT case either",
                PostMatch.doesMatch(post("t3_vreddit", VREDDIT_DIRECT_URL, false), SUB, false));
    }

    /** The resolved types the two cases above depend on, stated rather than assumed. */
    @Test
    public void theFixtureUrlsResolveToTheTypesTheseTestsAreAbout() throws Exception {
        assertEquals(ContentType.Type.ALBUM, ContentType.getContentType(ALBUM_URL));
        assertEquals(
                ContentType.Type.VREDDIT_DIRECT, ContentType.getContentType(VREDDIT_DIRECT_URL));
    }
}
