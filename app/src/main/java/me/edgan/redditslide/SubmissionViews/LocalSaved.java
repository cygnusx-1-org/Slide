package me.edgan.redditslide.SubmissionViews;

import android.os.AsyncTask;

import com.lusfold.androidkeyvaluestore.KVStore;
import com.lusfold.androidkeyvaluestore.core.KVManger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.util.LogUtil;

import net.dean.jraw.models.Contribution;
import net.dean.jraw.paginators.UserSavedPaginator;

/**
 * Local Saved tracks the user's saves locally and detects ones Reddit silently drops from the
 * {@code /saved} listing -- typically a post or comment removed by a subreddit moderator. In that
 * case the save record still exists on Reddit (the post page shows {@code saved: true}) but the
 * item disappears from the listing, so Slide's Saved tab can no longer show it.
 *
 * <p>We record a lightweight marker (fullname + timestamp) on every save and immediately kick a
 * background {@link #reconcile()}: it walks the live {@code /saved} listing and promotes any marker
 * missing from it into the visible Local Saved collection. Doing this at save time means the tab is
 * pre-processed and loads instantly (its own reconcile call is then a no-op). No post/comment body
 * is cached -- promoted items are fetched normally via {@code /api/info} when the viewer loads them.
 *
 * <p>Markers live in {@link KVStore} so the viewer can reuse the same load path as Read Later.
 */
public class LocalSaved {

    /**
     * Key prefix for the visible Local Saved collection (the viewer reads these).
     *
     * <p>The trailing {@code _} matters: {@code getByPrefix(PROMOTED_PREFIX)} must NOT match
     * {@link #PENDING_PREFIX} keys, and {@code "localsavedpending_"} does not start with
     * {@code "localsaved_"} (char 10 is {@code p} vs {@code _}).
     */
    public static final String PROMOTED_PREFIX = "localsaved_";

    /** Key prefix for saves awaiting reconciliation. */
    public static final String PENDING_PREFIX = "localsavedpending_";

    /** Ensures only one {@link #reconcile()} walk runs at a time (overlaps just no-op). */
    private static final AtomicBoolean RECONCILING = new AtomicBoolean(false);

    /** Record a successful save; held as pending until reconcile decides whether Reddit kept it. */
    public static void onSaved(Contribution thing) {
        if (thing == null || thing.getFullName() == null) return;
        KVStore.getInstance()
                .insertOrUpdate(
                        PENDING_PREFIX + thing.getFullName(),
                        String.valueOf(System.currentTimeMillis()));
        // Detect at save time: reconcile in the background now (off any caller thread) so the
        // Local Saved tab is already up to date and loads instantly when the user opens it. A save
        // of an already-removed post is promoted immediately; a still-live post is found near the
        // top of /saved and resolves in ~1 page thanks to reconcile()'s early-stop.
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> reconcile());
    }

    /** Drop a thing from Local Saved entirely (the user unsaved it). */
    public static void onUnsaved(Contribution thing) {
        if (thing == null || thing.getFullName() == null) return;
        KVManger m = KVStore.getInstance();
        m.delete(PENDING_PREFIX + thing.getFullName());
        m.delete(PROMOTED_PREFIX + thing.getFullName());
    }

    /** Promoted fullnames, newest-saved first. */
    public static ArrayList<String> getPromoted() {
        ArrayList<String> ids = new ArrayList<>();
        try {
            Map<String, String> values = KVStore.getInstance().getByPrefix(PROMOTED_PREFIX);
            List<Map.Entry<String, String>> entries = new ArrayList<>(values.entrySet());
            Collections.sort(
                    entries,
                    new Comparator<Map.Entry<String, String>>() {
                        @Override
                        public int compare(
                                Map.Entry<String, String> a, Map.Entry<String, String> b) {
                            return Long.compare(parseTimestamp(b.getValue()), parseTimestamp(a.getValue()));
                        }
                    });
            for (Map.Entry<String, String> e : entries) {
                ids.add(e.getKey().substring(PROMOTED_PREFIX.length()));
            }
        } catch (Exception e) {
            LogUtil.e(e, "LocalSaved.getPromoted failed");
        }
        return ids;
    }

    private static long parseTimestamp(String v) {
        try {
            return Long.parseLong(v);
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * Walk the live {@code /saved} listing and reconcile markers:
     *
     * <ul>
     *   <li>pending fullname missing from the listing -&gt; Reddit dropped it, promote it;
     *   <li>pending fullname present in the listing -&gt; Reddit kept it, prune the marker;
     *   <li>previously-promoted fullname that has reappeared -&gt; reinstated, un-promote it.
     * </ul>
     *
     * <p>Network/auth failures are swallowed without mutating state, so a failed fetch never
     * false-positives a save as "dropped". This walks the {@code /saved} listing and must be
     * called off the main thread (and off the shared serial executor -- it can take many seconds).
     *
     * @return {@code true} if the promoted (visible) set changed, so the viewer should reload.
     */
    public static boolean reconcile() {
        // Single-flight: overlapping reconciles (save-time + tab-load, or rapid successive saves)
        // would run redundant /saved walks and write to KVStore concurrently. Let only one run at
        // a time; others no-op. This is safe because each run re-reads pending fresh and the
        // tab-load call is a backstop, so a skipped run is picked up by the next one.
        if (!RECONCILING.compareAndSet(false, true)) return false;
        try {
            KVManger m = KVStore.getInstance();
            Map<String, String> pending;
            try {
                pending = m.getByPrefix(PENDING_PREFIX);
            } catch (Exception e) {
                LogUtil.e(e, "LocalSaved.reconcile read failed");
                return false;
            }
            // Walking the full /saved listing is the only way to tell a dropped save from a kept
            // one, but it is expensive (many sequential pages). Only do it when there is an
            // unresolved pending save; with none, the promoted set is already settled and the
            // viewer can load it instantly. (Reinstated items are re-checked here whenever the
            // next pending save triggers a walk.)
            if (pending.isEmpty()) return false;
            if (!Authentication.isLoggedIn
                    || Authentication.reddit == null
                    || Authentication.name == null
                    || Authentication.name.isEmpty()
                    || Authentication.name.equalsIgnoreCase("LOGGEDOUT")) {
                return false;
            }

            Set<String> pendingFullnames = new HashSet<>();
            for (String key : pending.keySet()) {
                pendingFullnames.add(key.substring(PENDING_PREFIX.length()));
            }

            Set<String> present = new HashSet<>();
            try {
                UserSavedPaginator paginator =
                        new UserSavedPaginator(
                                Authentication.reddit, "saved", Authentication.name);
                paginator.setLimit(100);
                while (paginator.hasNext()) {
                    for (Contribution c : paginator.next()) {
                        present.add(c.getFullName());
                    }
                    // Once every pending save has been seen, they were all kept -- stop paging. If
                    // any was dropped it is never found, so the absent case still walks to the end
                    // (the only way to confirm absence).
                    if (present.containsAll(pendingFullnames)) {
                        break;
                    }
                }
            } catch (Exception e) {
                // Couldn't fetch the listing -- don't risk classifying a live save as dropped.
                LogUtil.e(e, "LocalSaved.reconcile walk failed");
                return false;
            }

            boolean changed = false;
            try {
                for (Map.Entry<String, String> e : pending.entrySet()) {
                    String fullname = e.getKey().substring(PENDING_PREFIX.length());
                    m.delete(e.getKey());
                    if (!present.contains(fullname)) {
                        // Reddit dropped it from /saved -- keep it locally (preserve the save time).
                        m.insertOrUpdate(PROMOTED_PREFIX + fullname, e.getValue());
                        changed = true;
                    }
                }
                for (Map.Entry<String, String> e : m.getByPrefix(PROMOTED_PREFIX).entrySet()) {
                    String fullname = e.getKey().substring(PROMOTED_PREFIX.length());
                    if (present.contains(fullname)) {
                        // Reinstated -- back in Reddit's saved listing, so drop the local copy.
                        m.delete(e.getKey());
                        changed = true;
                    }
                }
            } catch (Exception e) {
                LogUtil.e(e, "LocalSaved.reconcile write failed");
            }
            return changed;
        } finally {
            RECONCILING.set(false);
        }
    }
}
