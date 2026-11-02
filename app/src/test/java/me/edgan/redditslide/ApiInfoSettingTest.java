package me.edgan.redditslide;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParser;

/**
 * "Use /api/info the Old Way": where it lives, when it applies, and how the Comments screen's old
 * "Use Old Load More Comments Method" switch it replaced is folded into it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ApiInfoSettingTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    private Context context;
    private SharedPreferences prefs;
    private SharedPreferences prefsWas;
    private final List<String> changed = new ArrayList<>();
    private final SharedPreferences.OnSharedPreferenceChangeListener listener =
            (p, key) -> changed.add(key);

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefs = context.getSharedPreferences("ApiInfoSettingTest", 0);
        prefs.edit().clear().commit();
        prefsWas = SettingValues.prefs;
    }

    @After
    public void tearDown() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
        SettingValues.prefs = prefsWas;
    }

    private void watch() {
        changed.clear();
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    // ~~~ Reading it

    @Test
    public void offByDefault() {
        SettingValues.prefs = prefs;
        prefs.edit().putBoolean(SettingValues.PREF_REDDIT_ENABLE_OVERRIDES, true).commit();

        assertFalse(SettingValues.useOldApiInfo());
    }

    @Test
    public void onOnlyWhileOverridesAreOn() {
        SettingValues.prefs = prefs;
        prefs.edit().putBoolean(SettingValues.PREF_USE_OLD_API_INFO, true).commit();

        // Greyed out on the screen, so a stored "on" there is no reaching must not apply.
        assertFalse(SettingValues.useOldApiInfo());

        prefs.edit().putBoolean(SettingValues.PREF_REDDIT_ENABLE_OVERRIDES, true).commit();
        assertTrue(SettingValues.useOldApiInfo());
    }

    @Test
    @SuppressWarnings("NullAway") // the state before Reddit.doMainStuff has run
    public void offBeforeTheSettingsAreLoaded() {
        SettingValues.prefs = null;

        assertFalse(SettingValues.useOldApiInfo());
    }

    // ~~~ The screens

    @Test
    public void theSwitchIsOnTheApiKeysRowsAndTheOldOneIsGone() throws Exception {
        assertTrue(
                hasId(
                        R.layout.activity_settings_general_child,
                        "@" + R.id.settings_general_use_old_api_info));
        assertEquals(
                0,
                context.getResources()
                        .getIdentifier(
                                "settings_comments_useOldLoadMoreComments",
                                "id",
                                context.getPackageName()));
    }

    @Test
    public void theSwitchComesAfterTheRedditKeysAndBeforeAnythingElse() throws Exception {
        List<String> ids = new ArrayList<>();
        try (XmlResourceParser parser =
                context.getResources().getLayout(R.layout.activity_settings_general_child)) {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    String id = parser.getAttributeValue(ANDROID, "id");
                    if (id != null) {
                        ids.add(id);
                    }
                }
            }
        }
        int userAgent = ids.indexOf("@" + R.id.settings_general_user_agent);
        int oldWay = ids.indexOf("@" + R.id.settings_general_use_old_api_info_row);
        int nextSection = ids.indexOf("@" + R.id.settings_general_hibernationHeader);
        assertTrue(userAgent >= 0);
        assertTrue(userAgent < oldWay);
        assertTrue(oldWay < nextSection);
    }

    private boolean hasId(int layout, String wanted) throws Exception {
        try (XmlResourceParser parser = context.getResources().getLayout(layout)) {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG
                        && wanted.equals(parser.getAttributeValue(ANDROID, "id"))) {
                    return true;
                }
            }
        }
        return false;
    }

    // ~~~ Folding the old Load More Comments switch in

    @Test
    public void anOldOnTurnsTheSettingOnAndGoes() {
        prefs.edit().putBoolean(SettingValues.PREF_USE_OLD_LOAD_MORE_COMMENTS, true).commit();

        SettingValues.migrateOldLoadMoreComments(prefs);

        assertTrue(prefs.getBoolean(SettingValues.PREF_USE_OLD_API_INFO, false));
        assertFalse(prefs.contains(SettingValues.PREF_USE_OLD_LOAD_MORE_COMMENTS));
    }

    @Test
    public void anOldOffGoesWithoutWritingTheSetting() {
        prefs.edit().putBoolean(SettingValues.PREF_USE_OLD_LOAD_MORE_COMMENTS, false).commit();

        SettingValues.migrateOldLoadMoreComments(prefs);

        assertFalse(prefs.contains(SettingValues.PREF_USE_OLD_API_INFO));
        assertFalse(prefs.contains(SettingValues.PREF_USE_OLD_LOAD_MORE_COMMENTS));
    }

    @Test
    public void anOldOffNeverTurnsTheSettingOff() {
        prefs.edit()
                .putBoolean(SettingValues.PREF_USE_OLD_API_INFO, true)
                .putBoolean(SettingValues.PREF_USE_OLD_LOAD_MORE_COMMENTS, false)
                .commit();

        SettingValues.migrateOldLoadMoreComments(prefs);

        assertTrue(prefs.getBoolean(SettingValues.PREF_USE_OLD_API_INFO, false));
    }

    @Test
    public void otherSettingsAreLeftAlone() {
        prefs.edit()
                .putBoolean(SettingValues.PREF_USE_OLD_LOAD_MORE_COMMENTS, true)
                .putBoolean(SettingValues.PREF_REDDIT_ENABLE_OVERRIDES, false)
                .putString("unrelated", "kept")
                .commit();

        SettingValues.migrateOldLoadMoreComments(prefs);

        assertEquals("kept", prefs.getString("unrelated", null));
        assertFalse(prefs.getBoolean(SettingValues.PREF_REDDIT_ENABLE_OVERRIDES, true));
        // The three written above, the old switch now replaced by the new one.
        assertEquals(3, prefs.getAll().size());
    }

    @Test
    public void nothingToFoldInWritesNothing() {
        prefs.edit().putString("unrelated", "kept").commit();
        watch();

        SettingValues.migrateOldLoadMoreComments(prefs);
        SettingValues.migrateOldLoadMoreComments(prefs);

        assertTrue(changed.toString(), changed.isEmpty());
    }

    @Test
    public void runningItAgainChangesNothing() {
        prefs.edit().putBoolean(SettingValues.PREF_USE_OLD_LOAD_MORE_COMMENTS, true).commit();
        SettingValues.migrateOldLoadMoreComments(prefs);
        watch();

        SettingValues.migrateOldLoadMoreComments(prefs);

        assertTrue(changed.toString(), changed.isEmpty());
        assertTrue(prefs.getBoolean(SettingValues.PREF_USE_OLD_API_INFO, false));
    }
}
