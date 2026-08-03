package me.edgan.redditslide.util;

import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.NullMarked;

/**
 * Null-safe {@link SharedPreferences} reads.
 *
 * <p>{@code getString} and {@code getStringSet} take a {@code @Nullable} default and so are declared
 * {@code @Nullable} on the way out — the annotation cannot say "non-null when the default is". That
 * makes every read an error under NullAway even where the caller passed a default precisely so it
 * could treat the result as present. These wrappers express the missing conditional: pass a non-null
 * default, get a non-null result.
 *
 * <p>The null check itself is defensive, not load-bearing. AOSP's {@code SharedPreferencesImpl}
 * returns {@code defValue} whenever the key is absent, and {@code putString(key, null)} removes the
 * key rather than storing a null, so the platform implementation cannot return null for a non-null
 * default. A non-AOSP {@link SharedPreferences} can, which is why the check stays.
 *
 * <p>Marked at class level rather than through the package, because the rest of {@code util} is not
 * NullAway-checked yet. See NULLAWAY.md.
 */
@NullMarked
public final class PrefUtil {

    private PrefUtil() {}

    /** {@link SharedPreferences#getString}, falling back to {@code defValue} instead of null. */
    public static String getString(SharedPreferences prefs, String key, String defValue) {
        final String value = prefs.getString(key, defValue);
        return value == null ? defValue : value;
    }

    /**
     * {@link SharedPreferences#getStringSet}, falling back to {@code defValue} instead of null.
     *
     * <p>The returned set is the platform's own instance and <b>must not be modified</b> — Android
     * documents the stored data as undefined if you do. Use {@link #getMutableStringSet} whenever
     * the result will be changed and written back.
     */
    public static Set<String> getStringSet(
            SharedPreferences prefs, String key, Set<String> defValue) {
        final Set<String> value = prefs.getStringSet(key, defValue);
        return value == null ? defValue : value;
    }

    /**
     * A modifiable copy of the stored set, safe to change and write back with {@code putStringSet}.
     *
     * <p>Mutating the set from {@link #getStringSet} in place appears to work — later reads in the
     * same process see the change because they get the same cached instance — but the write is not
     * guaranteed to persist, so the edit can be silently lost.
     */
    public static Set<String> getMutableStringSet(
            SharedPreferences prefs, String key, Set<String> defValue) {
        return new HashSet<>(getStringSet(prefs, key, defValue));
    }
}
