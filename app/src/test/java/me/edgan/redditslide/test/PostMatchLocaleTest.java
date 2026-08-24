package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SettingValues;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Content filters on a phone that is not set to English.
 *
 * <p>Filters are stored lowercase and compared against a lowercased title, body, author or domain,
 * so every one of those conversions has to be pinned to a fixed locale. In Turkish the default
 * mapping sends {@code I} to {@code ı} rather than {@code i}, so a filter for "instagram" stops
 * matching a title that shouts "INSTAGRAM" -- the filter looks configured and quietly does nothing.
 *
 * <p>The rest of the suite cannot see this: it runs in whatever single locale the build machine
 * has, so {@code Locale.ENGLISH} and {@code Locale.getDefault()} are the same value and swapping
 * one for the other everywhere in {@code PostMatch} leaves every other test green. These run the
 * same assertions with the default locale moved out from under them.
 */
public class PostMatchLocaleTest {

    /** Turkish is the standard case for this: it is the locale whose lowercase of I is not i. */
    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    private Locale defaultWas;
    private Set<String> alwaysExternalWas;

    private static Set<String> set(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    @Before
    public void useTurkishAsTheDeviceLocale() {
        defaultWas = Locale.getDefault();
        alwaysExternalWas = SettingValues.alwaysExternal;
        Locale.setDefault(TURKISH);
    }

    @After
    public void restoreTheDeviceLocale() {
        // Locale.setDefault is process-wide and this JVM is shared with every other test class.
        Locale.setDefault(defaultWas);
        SettingValues.alwaysExternal = alwaysExternalWas;
    }

    /** The dotted capital I is the character the default mapping gets wrong. */
    @Test
    public void anUppercaseTitleStillMatchesItsFilterInTurkish() {
        assertTrue(
                "\"INSTAGRAM\" has to match the stored filter \"instagram\"",
                PostMatch.contains("INSTAGRAM post of the day", set("instagram"), false));
    }

    @Test
    public void anExactSubredditMatchSurvivesTurkishCasing() {
        assertTrue(
                "the subreddit filter is a total match, so the casing has to line up exactly",
                PostMatch.contains("PICSANDVIDEOS", set("picsandvideos"), true));
    }

    @Test
    public void aFilterThatShouldNotMatchStillDoesNotMatchInTurkish() {
        assertFalse(
                "pinning the locale must not turn every comparison true",
                PostMatch.contains("CATS", set("instagram"), false));
    }

    /** {@code openExternal} lowercases the url before comparing it against the domain list. */
    @Test
    public void anUppercaseDomainStillMatchesInTurkish() {
        SettingValues.alwaysExternal = set("instagram.com");

        assertTrue(
                "\"INSTAGRAM.COM\" has to match the stored domain \"instagram.com\"",
                PostMatch.openExternal("https://INSTAGRAM.COM/p/12345"));
    }

    @Test
    public void anUnlistedDomainIsStillExternalFreeInTurkish() {
        SettingValues.alwaysExternal = set("instagram.com");

        assertFalse(PostMatch.openExternal("https://EXAMPLE.COM/p/12345"));
    }
}
