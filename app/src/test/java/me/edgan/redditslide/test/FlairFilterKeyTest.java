package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.UpgradeUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The {@code subreddit:flair} entry a "filter this flair" row is stored under, and the two ways a
 * malformed one used to get written.
 *
 * <p>Three sites build or accept this string and they did not agree. The post's own filter dialog
 * pre-checked its box with {@code baseSub + ":" + flair.toLowerCase().trim()} and wrote
 * {@code (baseSub + ":" + flair).toLowerCase().trim()} -- the trim landing on either side of the
 * join -- so a flair with leading whitespace was written under a key the read never asked for and
 * the box opened unchecked while the filter was live. The same misplaced trim turned a
 * whitespace-only flair into a bare {@code "pics:"}, which {@code PostMatch.doesMatch} threw an
 * {@code ArrayIndexOutOfBoundsException} on until it grew a guard, and the legacy comma-string
 * migration let one through on nothing more than "contains a colon".
 *
 * <p>Every site goes through {@link SettingValues#flairFilterKey} now, so the shape it emits is
 * what there is to pin. {@code PostMatchFilterKindsTest} pins the reader's half of the round trip.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class FlairFilterKeyTest {

    @SuppressWarnings("NullAway.Init")
    private TestUtils.SettingValuesSnapshot settingValuesWere;

    @Before
    public void setUp() {
        settingValuesWere = TestUtils.SettingValuesSnapshot.capture();
        TestUtils.seedRedditApplication();
    }

    @After
    public void tearDown() {
        settingValuesWere.restore();
        TestUtils.clearRedditApplication();
    }

    private static Set<String> set(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    /** Both halves are lowercased, so the subreddit as displayed keys the same entry. */
    @Test
    public void theSubredditIsLowercasedWhateverCapitalisationItArrivesIn() {
        assertEquals(
                SettingValues.flairFilterKey("askreddit", "spoiler"),
                SettingValues.flairFilterKey("AskReddit", "Spoiler"));
    }

    /**
     * The whole point of the helper: the trim runs on each half, not on the joined string, so
     * padding around the flair does not survive into the key. The old writer joined first, kept the
     * leading spaces as interior characters and stored {@code "pics:  spoiler"} where the read-back
     * looked for {@code "pics:spoiler"}.
     */
    @Test
    public void paddingAroundTheFlairDoesNotChangeTheKey() {
        assertEquals(
                SettingValues.flairFilterKey("pics", "spoiler"),
                SettingValues.flairFilterKey("pics", "  Spoiler  "));
    }

    /**
     * Reading the box's state and writing what the user ticks are the same key or the box lies.
     * Both call sites in the filter dialog are this one call now.
     */
    @Test
    public void theDialogFindsTheEntryItWrote() {
        SettingValues.flairFilters = new HashSet<>();
        SettingValues.flairFilters.add(SettingValues.flairFilterKey("pics", "  Spoiler  "));

        assertTrue(
                "the box opens ticked for a filter that is live",
                SettingValues.flairFilters.contains(
                        SettingValues.flairFilterKey("pics", "  Spoiler  ")));
    }

    /** A flair of nothing but whitespace has no key: it would be a bare "pics:" that matches nothing. */
    @Test
    public void aWhitespaceOnlyFlairProducesNothingToStore() {
        assertEquals("pics:", SettingValues.flairFilterKey("pics", "   "));
        assertTrue(
                "so the writers have to reject it before they get here",
                SettingValues.flairFilterKey("pics", "   ").endsWith(":"));
    }

    /**
     * The legacy comma-string migration is the other writer that could store one. It accepted
     * anything containing a colon, so {@code "pics:"} came through an upgrade intact; it applies
     * the same {@code .+:.+} test the settings screen applies to a typed entry now.
     */
    @Test
    public void theLegacyMigrationDropsAnEntryWithNothingAfterTheColon() {
        final Context context = ApplicationProvider.getApplicationContext();
        final SharedPreferences settings = context.getSharedPreferences("SETTINGS", 0);
        final SharedPreferences upgradePrefs = context.getSharedPreferences("upgradeUtil", 0);

        // The upgrade bails out on a first start, which it recognises by COLOR having no Tutorial.
        context.getSharedPreferences("COLOR", 0).edit().putBoolean("Tutorial", true).commit();
        upgradePrefs.edit().putInt("VERSION", 1).commit();
        settings.edit()
                .putString(SettingValues.PREF_FLAIR_FILTERS, "pics:,pics:spoiler,videos:")
                .commit();

        UpgradeUtil.upgrade(context);

        final Set<String> migrated =
                settings.getStringSet(SettingValues.PREF_FLAIR_FILTERS, set());

        assertNotNull(migrated);
        assertTrue("a complete entry survives", migrated.contains("pics:spoiler"));
        assertFalse("nothing after the colon", migrated.contains("pics:"));
        assertFalse(migrated.contains("videos:"));
    }
}
