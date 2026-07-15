package me.edgan.redditslide.test;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import me.edgan.redditslide.Reddit;
import org.apache.commons.io.IOUtils;

public class TestUtils {

    public static String getResource(String path) throws IOException {
        return IOUtils.toString(
                TestUtils.class.getClassLoader().getResourceAsStream(path), "utf-8");
    }

    /**
     * Seeds the private static {@code Reddit.mApplication} with the Robolectric application context.
     * Some classes (notably anything that class-loads {@code Toolbox}, whose static initializer calls
     * {@code Reddit.getAppContext().getSharedPreferences(...)}) otherwise throw an {@code
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

    /** Reflectively set the private static {@code Reddit.mApplication} (there is no public setter). */
    private static void setMApplication(Application value) {
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
            return null;
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
            return null;
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {}
    }
}
