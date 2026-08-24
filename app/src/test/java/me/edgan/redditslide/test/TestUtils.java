package me.edgan.redditslide.test;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import org.apache.commons.io.IOUtils;

public class TestUtils {

    public static String getResource(String path) throws IOException {
        return IOUtils.toString(
                TestUtils.class.getClassLoader().getResourceAsStream(path), "utf-8");
    }

    /**
     * Seeds the private static {@code Reddit.mApplication} with the Robolectric application context.
     * Any class whose static initializer calls {@code
     * Reddit.getAppContext().getSharedPreferences(...)} otherwise throws an {@code
     * ExceptionInInitializerError} in tests. Robolectric-only — needs an Android runtime.
     */
    public static void seedRedditApplication() {
        setMApplication((Application) ApplicationProvider.getApplicationContext());
    }

    /**
     * Undoes {@link #seedRedditApplication()}, restoring {@code Reddit.mApplication} to its pristine
     * null. Use in a test's teardown so it doesn't leave the static seeded for a later test sharing
     * the same Robolectric sandbox.
     */
    public static void clearRedditApplication() {
        setMApplication(null);
    }

    /**
     * Restores {@code Reddit.colors} to its pristine (pre-{@link Reddit#onCreate}) null, alongside
     * {@link #clearRedditApplication()}. The field is {@code @SuppressWarnings("NullAway.Init")} in
     * main because {@code onCreate} always populates it in the app; nothing runs {@code onCreate}
     * here, so a test that seeded it has to put it back for the next test in the sandbox.
     */
    @SuppressWarnings("NullAway")
    public static void clearRedditColors() {
        Reddit.colors = null;
    }

    /** Reflectively set the private static {@code Reddit.mApplication} (there is no public setter). */
    private static void setMApplication(@Nullable Application value) {
        try {
            Field f = Reddit.class.getDeclaredField("mApplication");
            f.setAccessible(true);
            f.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to set Reddit.mApplication for tests", e);
        }
    }

    public static class MockPreferences implements SharedPreferences {
        private String pinned;

        public MockPreferences(String pinned) {
            this.pinned = pinned;
        }

        // Only method we care about
        @Nullable
        @Override
        public String getString(String key, @Nullable String defValue) {
            return pinned;
        }

        @Override
        public Map<String, ?> getAll() {
            return Collections.emptyMap();
        }

        @Nullable
        @Override
        public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
            return defValues;
        }

        @Override
        public int getInt(String key, int defValue) {
            return defValue;
        }

        @Override
        public long getLong(String key, long defValue) {
            return defValue;
        }

        @Override
        public float getFloat(String key, float defValue) {
            return defValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            return defValue;
        }

        @Override
        public boolean contains(String key) {
            return false;
        }

        @Override
        public Editor edit() {
            throw new UnsupportedOperationException("MockPreferences is read-only");
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}
    }

    /**
     * A snapshot of the app-wide {@link SettingValues} statics a filter/theme test has to change to
     * set up its case.
     *
     * <p>The unit-test task forks one JVM for the whole run, so any of these left modified is
     * visible to every class that runs afterwards — and several of them decide behaviour rather
     * than just data: a non-null {@code prefs} flips {@code Constants.getClientId()} off its
     * default branch, and an emptied filter set silently disarms whatever the next class was
     * relying on. Capture in {@code @Before}, restore in {@code @After}, so a class cannot leave
     * a value behind whichever of its tests fails.
     */
    public static final class SettingValuesSnapshot {
        private final Set<String> titleFilters;
        private final Set<String> textFilters;
        private final Set<String> userFilters;
        private final Set<String> domainFilters;
        private final Set<String> subredditFilters;
        private final Set<String> flairFilters;
        private final Set<String> alwaysExternal;
        private final boolean filterOldPosts;
        private final int filterOldPostsDays;
        private final boolean subredditFiltersTillRestart;
        private final boolean subredditFilterPrefixMatching;
        private final boolean showNSFWContent;
        @Nullable private final SharedPreferences prefs;
        private final int nightModeState;

        private SettingValuesSnapshot() {
            titleFilters = SettingValues.titleFilters;
            textFilters = SettingValues.textFilters;
            userFilters = SettingValues.userFilters;
            domainFilters = SettingValues.domainFilters;
            subredditFilters = SettingValues.subredditFilters;
            flairFilters = SettingValues.flairFilters;
            alwaysExternal = SettingValues.alwaysExternal;
            filterOldPosts = SettingValues.filterOldPosts;
            filterOldPostsDays = SettingValues.filterOldPostsDays;
            subredditFiltersTillRestart = SettingValues.subredditFiltersTillRestart;
            subredditFilterPrefixMatching = SettingValues.subredditFilterPrefixMatching;
            showNSFWContent = SettingValues.showNSFWContent;
            prefs = readPrefs();
            nightModeState = SettingValues.nightModeState;
        }

        /** Takes the snapshot. */
        public static SettingValuesSnapshot capture() {
            return new SettingValuesSnapshot();
        }

        /** Puts every captured value back, including the ones the caller never touched. */
        @SuppressWarnings("NullAway")
        public void restore() {
            SettingValues.titleFilters = titleFilters;
            SettingValues.textFilters = textFilters;
            SettingValues.userFilters = userFilters;
            SettingValues.domainFilters = domainFilters;
            SettingValues.subredditFilters = subredditFilters;
            SettingValues.flairFilters = flairFilters;
            SettingValues.alwaysExternal = alwaysExternal;
            SettingValues.filterOldPosts = filterOldPosts;
            SettingValues.filterOldPostsDays = filterOldPostsDays;
            SettingValues.subredditFiltersTillRestart = subredditFiltersTillRestart;
            SettingValues.subredditFilterPrefixMatching = subredditFilterPrefixMatching;
            SettingValues.showNSFWContent = showNSFWContent;
            SettingValues.prefs = prefs;
            SettingValues.nightModeState = nightModeState;
        }

        /**
         * {@code SettingValues.prefs} is declared non-null but is only assigned by the running app,
         * so in a unit-test JVM it is null until some test assigns it. Read it as nullable rather
         * than pretending otherwise.
         */
        @SuppressWarnings("NullAway")
        @Nullable
        private static SharedPreferences readPrefs() {
            return SettingValues.prefs;
        }
    }
}
