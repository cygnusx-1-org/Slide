package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.Application;
import android.content.Context;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.ColorPreferences.ColorThemeOptions;
import me.edgan.redditslide.Visuals.ColorPreferences.Theme;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Per-subreddit theming: the accent a subreddit is remembered with, and what happens to it when the
 * base theme changes underneath it.
 *
 * <p>`ColorPreferences` had 6 of 370 branches covered. Its storage carries three separate rules
 * that nothing checked -- the key is lowercased, the key is scoped to the signed-in account, and a
 * stored theme of the wrong base type is rewritten to the matching variant of the same colour
 * rather than used as-is -- plus two fallbacks, for an unset subreddit and for a stored value that
 * no longer names a theme. Each was broken on its own with the whole suite green.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ColorPreferencesSubredditThemeTest {

    private static final String SUB_AS_DISPLAYED = "AskReddit";
    private static final String SUB_AS_TYPED = "askreddit";

    @Nullable private String nameWas;

    @SuppressWarnings("NullAway.Init")
    private TestUtils.SettingValuesSnapshot settingValuesWere;
    @SuppressWarnings("NullAway.Init") private ColorPreferences prefs;
    @SuppressWarnings("NullAway.Init") private Context context;

    @Before
    public void setUp() {
        // First, before anything that can throw: seedRedditApplication() raises a RuntimeException
        // if the reflection fails, and an unset snapshot would then make tearDown NPE over the top
        // of the real failure.
        settingValuesWere = TestUtils.SettingValuesSnapshot.capture();
        // ColorPreferences reaches Reddit.getAppContext() through the theme lookups.
        TestUtils.seedRedditApplication();
        nameWas = Authentication.name;
        Authentication.name = "account-one";

        context = ApplicationProvider.getApplicationContext();
        // The file ColorPreferences.open() resolves, and SettingValues.prefs for isNight().
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit();
        SettingValues.prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        SettingValues.nightModeState = 0;

        prefs = new ColorPreferences(context);
        prefs.setFontStyle(Theme.dark_white);
    }

    @After
    public void tearDown() {
        // Authentication.name, SettingValues.prefs and the preference file are process-wide.
        // Leaving prefs non-null would flip Constants.getClientId() off its default branch for
        // every class that runs after this one.
        settingValuesWere.restore();
        Authentication.name = nameWas;
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().clear().commit();
        TestUtils.clearRedditApplication();
    }

    /** Stored under the display capitalisation, read back under any other. */
    @Test
    public void aSubredditThemeIsStoredCaseInsensitively() {
        prefs.setFontStyle(Theme.light_pink, SUB_AS_DISPLAYED);

        assertEquals(
                "the theme saved from the subreddit screen has to be found by the feed",
                Theme.light_pink,
                prefs.getFontStyleSubreddit(SUB_AS_TYPED));
    }

    /** The key carries the account, so one account's themes are not the next one's. */
    @Test
    public void aSubredditThemeBelongsToTheAccountThatSetIt() {
        prefs.setFontStyle(Theme.light_pink, SUB_AS_DISPLAYED);
        assertEquals(Theme.light_pink, prefs.getFontStyleSubreddit(SUB_AS_TYPED));

        Authentication.name = "account-two";

        assertNotEquals(
                "the next account must not inherit the previous one's subreddit themes",
                Theme.light_pink,
                prefs.getFontStyleSubreddit(SUB_AS_TYPED));
    }

    /** With nothing stored for it, a subreddit uses the global style. */
    @Test
    public void anUnthemedSubredditInheritsTheGlobalStyle() {
        prefs.setFontStyle(Theme.dark_pink);

        assertEquals(Theme.dark_pink, prefs.getFontStyleSubreddit("neverthemed"));
    }

    /** A stored value that no longer names a theme falls back rather than throwing. */
    @Test
    public void aStoredThemeThatNoLongerExistsFallsBackToTheDefault() {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .edit()
                .putString(SUB_AS_TYPED + "$USER$" + Authentication.name, "theme_that_was_removed")
                .commit();

        assertEquals(
                Theme.valueOf(Constants.DEFAULT_THEME),
                prefs.getFontStyleSubreddit(SUB_AS_TYPED));
    }

    /**
     * The rule that makes per-subreddit accents survive a base-theme change: a subreddit stored as
     * the light variant of a colour, read while the app is on a dark base, answers with the dark
     * variant of the same colour instead of dragging a light theme into a dark app.
     */
    @Test
    public void aSubredditThemeIsRewrittenToMatchTheCurrentBaseTheme() {
        prefs.setFontStyle(Theme.dark_white); // base type: Dark
        prefs.setFontStyle(Theme.light_pink, SUB_AS_DISPLAYED); // stored as Light

        Theme resolved = prefs.getThemeSubreddit(SUB_AS_TYPED, true);

        assertEquals(
                "the colour is kept",
                ColorThemeOptions.Dark.getValue(),
                resolved.getThemeType());
        assertEquals("and rewritten to the base theme's variant", Theme.dark_pink, resolved);
    }

    /** A subreddit already on the current base type is returned untouched. */
    @Test
    public void aSubredditThemeAlreadyMatchingTheBaseIsLeftAlone() {
        prefs.setFontStyle(Theme.dark_white);
        prefs.setFontStyle(Theme.dark_pink, SUB_AS_DISPLAYED);

        assertEquals(Theme.dark_pink, prefs.getThemeSubreddit(SUB_AS_TYPED, true));
    }

    /**
     * The AMOLED-contrast lookup forces its own base type whatever the subreddit is stored as, so
     * the contrast variant is reached from any starting theme.
     */
    @Test
    public void theContrastLookupForcesItsOwnBaseType() {
        prefs.setFontStyle(Theme.light_pink, SUB_AS_DISPLAYED);

        assertEquals(
                Theme.amoled_light_pink.getBaseId(), prefs.getDarkThemeSubreddit(SUB_AS_TYPED));
    }
}
