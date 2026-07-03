package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SettingValues;
import org.junit.After;
import org.junit.Test;

/**
 * Pure/in-memory tests for {@link PostMatch}. The device-bound {@code doesMatch(Submission,…)} is
 * out of scope (needs a JRAW Submission and the full SettingValues graph); this covers the
 * string-matching, domain-matching and in-memory content-filter helpers the subreddit-filter fix
 * flows through.
 *
 * <p>Content-filter tests set {@code subredditFiltersTillRestart=true} so the helpers use the static
 * in-memory map instead of the {@code filters} SharedPreferences (which is null in a unit test).
 * That map cannot be cleared externally, so each content-filter test uses a distinct subreddit name.
 */
public class PostMatchTest {

    private static Set<String> set(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    @After
    public void tearDown() {
        SettingValues.subredditFiltersTillRestart = false;
        SettingValues.showNSFWContent = false;
        SettingValues.alwaysExternal = Collections.emptySet();
        clearMemoryContentFilters();
    }

    /**
     * {@code PostMatch.memoryContentFilters} is a private static map with no reset hook. Clear it
     * reflectively between tests so a future test that reuses a subreddit name can't inherit stale
     * content-filter flags.
     */
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

    // ---------------------------------------------------------------------
    // contains
    // ---------------------------------------------------------------------

    @Test
    public void contains_totalMatchExact() {
        assertTrue(PostMatch.contains("pics", set("pics"), true));
    }

    @Test
    public void contains_totalMatchIsCaseAndWhitespaceInsensitive() {
        assertTrue(PostMatch.contains("  PICS  ", set("pics"), true));
    }

    @Test
    public void contains_totalMatchRejectsPartial() {
        assertFalse(PostMatch.contains("picsandmore", set("pics"), true));
    }

    @Test
    public void contains_partialMatchesSubstring() {
        assertTrue(PostMatch.contains("myPicsHere", set("pics"), false));
    }

    @Test
    public void contains_partialFallsBackToExactMembership() {
        assertTrue(PostMatch.contains("pics", set("pics"), false));
    }

    @Test
    public void contains_partialNoMatch() {
        assertFalse(PostMatch.contains("cats", set("pics"), false));
    }

    @Test
    public void contains_emptySetIsFalse() {
        assertFalse(PostMatch.contains("anything", Collections.emptySet(), true));
        assertFalse(PostMatch.contains("anything", Collections.emptySet(), false));
    }

    // ---------------------------------------------------------------------
    // isDomain
    // ---------------------------------------------------------------------

    @Test
    public void isDomain_exactHost() throws Exception {
        assertTrue(PostMatch.isDomain("https://example.com/x", set("example.com")));
    }

    @Test
    public void isDomain_subdomainMatches() throws Exception {
        assertTrue(PostMatch.isDomain("https://www.example.com/x", set("example.com")));
    }

    @Test
    public void isDomain_siblingHostDoesNotMatch() throws Exception {
        assertFalse(PostMatch.isDomain("https://www.example.com/x", set("notexample.com")));
    }

    @Test
    public void isDomain_pathEntryMatchesPrefix() throws Exception {
        assertTrue(PostMatch.isDomain("https://example.com/path/x", set("example.com/path")));
    }

    @Test
    public void isDomain_pathEntryRejectsDifferentPath() throws Exception {
        assertFalse(PostMatch.isDomain("https://example.com/other", set("example.com/path")));
    }

    @Test
    public void isDomain_schemelessPathEntryGetsHttpPrepended() throws Exception {
        // "example.com/path" has no scheme; isDomain prepends http:// before comparing.
        assertTrue(PostMatch.isDomain("http://example.com/path/deeper", set("example.com/path")));
    }

    @Test(expected = java.net.MalformedURLException.class)
    public void isDomain_malformedTargetThrows() throws Exception {
        PostMatch.isDomain("not a url", set("example.com"));
    }

    // ---------------------------------------------------------------------
    // openExternal
    // ---------------------------------------------------------------------

    @Test
    public void openExternal_matchesConfiguredHost() {
        SettingValues.alwaysExternal = set("twitter.com");
        assertTrue(PostMatch.openExternal("https://twitter.com/user/status/1"));
    }

    @Test
    public void openExternal_nonMatchingHostIsFalse() {
        SettingValues.alwaysExternal = set("twitter.com");
        assertFalse(PostMatch.openExternal("https://github.com/edgan/Slide"));
    }

    @Test
    public void openExternal_malformedUrlIsFalse() {
        SettingValues.alwaysExternal = set("twitter.com");
        assertFalse(PostMatch.openExternal("not-a-valid-url"));
    }

    // ---------------------------------------------------------------------
    // In-memory content filters (setChosen + is*)
    // ---------------------------------------------------------------------

    @Test
    public void setChosen_storesPerTypeFlagsInMemory() {
        SettingValues.subredditFiltersTillRestart = true;
        // albums, galleries, gifs, images, links, selftexts, tumblrs, videos
        boolean[] values = {true, false, true, false, false, false, false, true};
        PostMatch.setChosen(values, "MemTestA");

        assertTrue(PostMatch.isAlbum("memtesta"));
        assertFalse(PostMatch.isGallery("memtesta"));
        assertTrue(PostMatch.isGif("memtesta"));
        assertFalse(PostMatch.isImage("memtesta"));
        assertFalse(PostMatch.isLink("memtesta"));
        assertFalse(PostMatch.isSelftext("memtesta"));
        assertFalse(PostMatch.isTumblr("memtesta"));
        assertTrue(PostMatch.isVideo("memtesta"));
    }

    @Test
    public void setChosen_lowercasesSubredditName() {
        SettingValues.subredditFiltersTillRestart = true;
        boolean[] values = {true, false, false, false, false, false, false, false};
        PostMatch.setChosen(values, "MixedCase");

        // Stored under the lowercased key; lookup must use the lowercase form.
        assertTrue(PostMatch.isAlbum("mixedcase"));
        // The is* helpers do not lowercase their argument, so the mixed-case lookup misses.
        assertFalse(PostMatch.isAlbum("MixedCase"));
    }

    @Test
    public void setChosen_storesNsfwFlagsWhenEnabled() {
        SettingValues.subredditFiltersTillRestart = true;
        SettingValues.showNSFWContent = true;
        // 8 regular + 8 nsfw (albums..videos). Turn on nsfwAlbums (idx 8) and nsfwVideos (idx 15).
        boolean[] values = new boolean[16];
        values[8] = true;
        values[15] = true;
        PostMatch.setChosen(values, "MemTestNsfw");

        assertTrue(PostMatch.isNsfwAlbum("memtestnsfw"));
        assertFalse(PostMatch.isNsfwGallery("memtestnsfw"));
        assertTrue(PostMatch.isNsfwVideo("memtestnsfw"));
    }

    @Test
    public void isType_defaultsFalseForUnknownSubreddit() {
        SettingValues.subredditFiltersTillRestart = true;
        assertFalse(PostMatch.isAlbum("neverconfigured"));
        assertFalse(PostMatch.isVideo("neverconfigured"));
    }
}
