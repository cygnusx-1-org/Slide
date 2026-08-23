package me.edgan.redditslide;

import android.content.SharedPreferences;
import android.os.SystemClock;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The number of unread inbox messages for the signed-in account, and the only place that number
 * lives. Everything that changes unread state writes through here; everything that displays a
 * count reads or observes here, so a screen created after a message was read shows the same number
 * as one that was already running.
 *
 * <p>The value is derived from the length of the unread inbox listing ({@code /message/unread}),
 * never from the account's {@code inbox_count} field: reddit reports that field as 0 while
 * {@code has_mail} is true and the unread listing still holds messages.
 *
 * <p>The count is per account. There is no per-account preference file in Slide, so {@link
 * me.edgan.redditslide.UserSubscriptions#switchAccounts()} calls {@link #clear} and the next
 * account-info load stores the new account's number.
 */
@NullMarked
public final class InboxCount {

    /** Preference key, in {@link Reddit#appRestart}. Nothing outside this class touches it. */
    public static final String KEY = "inbox";

    /** Notified with the current count when observing starts and on every later change. */
    public interface Listener {
        void onInboxCountChanged(int count);
    }

    /**
     * Bumped by every write. A fetch reads it before asking reddit and hands it back to {@link
     * #setFromFetch}, which drops the fetched number if anything moved the count in between --
     * a listing describes the inbox as it was when the request went out, so it must not wind
     * back a read that happened while it was in flight.
     */
    private static int generation;

    /**
     * {@link SystemClock#elapsedRealtime()} of the last listing fetch, or 0 for none yet. In
     * memory only: a fresh process has nothing to go stale.
     */
    private static long lastFetchedAt;

    private InboxCount() {}

    /** The stored count, never negative. */
    public static synchronized int get(SharedPreferences prefs) {
        return Math.max(0, prefs.getInt(KEY, 0));
    }

    /** Stores {@code count}, clamped at 0. */
    public static synchronized void set(SharedPreferences prefs, int count) {
        generation++;
        prefs.edit().putInt(KEY, Math.max(0, count)).apply();
    }

    /** The value a fetch has to hand back to {@link #setFromFetch} to have its number accepted. */
    public static synchronized int generation() {
        return generation;
    }

    /**
     * Stores a count taken from a listing that was requested while {@code fetchedAtGeneration} was
     * current, and reports whether it was accepted. A write since then -- a message marked read in
     * the app or from a notification, a "mark all read" -- describes the inbox more recently than
     * this listing does, so the listing is dropped and the next fetch reconciles.
     */
    public static synchronized boolean setFromFetch(
            SharedPreferences prefs, int count, int fetchedAtGeneration) {
        if (fetchedAtGeneration != generation) {
            return false;
        }
        set(prefs, count);
        return true;
    }

    /**
     * Whether a listing was fetched within the last {@code maxAgeMs}. The count only moves when
     * mail arrives or is read, and both of those write it directly, so re-fetching the listing is
     * reconciliation: worth doing regularly, not worth doing on every screen return.
     */
    public static synchronized boolean isFresh(long maxAgeMs) {
        return lastFetchedAt != 0L && SystemClock.elapsedRealtime() - lastFetchedAt < maxAgeMs;
    }

    /** Records a listing fetch that reached reddit. Never called for one that failed. */
    public static synchronized void markFetched() {
        lastFetchedAt = SystemClock.elapsedRealtime();
    }

    /** Subtracts {@code by} from the stored count. Synchronized so overlapping network callbacks
     * cannot both read the same value and write the same result. */
    public static synchronized void decrement(SharedPreferences prefs, int by) {
        set(prefs, get(prefs) - by);
    }

    /** Subtracts one from the stored count. */
    public static synchronized void decrement(SharedPreferences prefs) {
        decrement(prefs, 1);
    }

    /** Adds one to the stored count, for a message toggled back to unread. */
    public static synchronized void increment(SharedPreferences prefs) {
        set(prefs, get(prefs) + 1);
    }

    /**
     * Drops the stored count, so the account being left never shows its number to the next one.
     * Also drops the freshness stamp: the next account's first fetch must not be skipped as a
     * repeat of the previous account's.
     */
    public static synchronized void clear(SharedPreferences prefs) {
        generation++;
        lastFetchedAt = 0L;
        prefs.edit().remove(KEY).apply();
    }

    /**
     * Delivers the current count to {@code listener} and every change after it, until the returned
     * token is passed to {@link #stopObserving}. {@link SharedPreferences} holds change listeners
     * weakly, so the caller has to keep a strong reference to that token for as long as it wants
     * updates.
     */
    public static SharedPreferences.OnSharedPreferenceChangeListener observe(
            final SharedPreferences prefs, final Listener listener) {
        final SharedPreferences.OnSharedPreferenceChangeListener token =
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    @Override
                    public void onSharedPreferenceChanged(
                            SharedPreferences changed, @Nullable String key) {
                        // A cleared preference file is reported with a null key, so re-read
                        // rather than only matching on KEY.
                        if (key == null || KEY.equals(key)) {
                            listener.onInboxCountChanged(get(changed));
                        }
                    }
                };
        prefs.registerOnSharedPreferenceChangeListener(token);
        listener.onInboxCountChanged(get(prefs));
        return token;
    }

    /** Stops updates for a token returned by {@link #observe}. */
    public static void stopObserving(
            SharedPreferences prefs, SharedPreferences.OnSharedPreferenceChangeListener token) {
        prefs.unregisterOnSharedPreferenceChangeListener(token);
    }
}
