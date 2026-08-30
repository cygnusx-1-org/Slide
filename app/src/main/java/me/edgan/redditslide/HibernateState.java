package me.edgan.redditslide;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.edgan.redditslide.Activities.MainActivity;
import me.edgan.redditslide.util.LogUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jspecify.annotations.NullMarked;

/**
 * "Hibernate": the screen stack the user was looking at, persisted across process death so that a
 * cold start puts them back on the same screen, at the same scroll position, on the same content.
 *
 * <p>The snapshot is a single JSON document holding one entry per activity in the task, bottom
 * first. Each entry carries the activity's class name, the intent extras it needs to be built
 * again, and a state {@link Bundle} the activity itself supplies through {@link Restorable}.
 *
 * <p>It is written to {@code getFilesDir()}, not to {@link Reddit#cachedData} and not to the cache
 * dir. {@code OfflineSubreddit.getAll()} scans every key in {@code cachedData} filtering on a bare
 * {@code contains(",")}, so a new key there would be read back as a cached subreddit page; and the
 * cache dir is reclaimable by the system, which is the one thing a resume pointer must not be.
 *
 * <p>Two launch paths have to work, and they are why restore is split the way it is:
 *
 * <ul>
 *   <li>The launcher icon goes through {@code Slide}, which replays the whole stack in one
 *       {@code startActivities} call. That rebuilds the stack <em>shape</em>.
 *   <li>Returning to a task whose process died never runs {@code Slide} at all — the system
 *       recreates the top activity from its own persisted intent, and the ones below it only as
 *       the user backs into them, which can be minutes later.
 * </ul>
 *
 * So no activity may depend on {@code Slide} having run: each one claims its own state by matching
 * itself against the snapshot in {@code onCreate}. The snapshot stays armed until the app next goes
 * to background, at which point it is replaced by a fresh one.
 */
@NullMarked
public final class HibernateState {

    /** Bumped when the on-disk shape changes; an older document is discarded rather than migrated. */
    private static final int VERSION = 1;

    private static final String FILE_NAME = "hibernate.json";

    private static final String KEY_VERSION = "version";
    private static final String KEY_SAVED_AT = "savedAt";
    private static final String KEY_ACCOUNT = "account";
    private static final String KEY_STACK = "stack";
    private static final String KEY_CLS = "cls";
    private static final String KEY_EXTRAS = "extras";
    private static final String KEY_STATE = "state";

    /** Type tag and value, so a {@link Bundle} survives the JSON round trip with its types intact. */
    private static final String KEY_TYPE = "t";
    private static final String KEY_VALUE = "v";

    /**
     * Screens that are deliberately never recorded. Some are transient by nature and resuming onto
     * them would be wrong ({@code Login}, {@code Tutorial}). The last three
     * cannot be rebuilt from an intent at all: {@code CommentSearch} and {@code SendMessage} read
     * {@code DataShare}'s statics, which die with the process, and the gallery screens take their
     * image list as a {@code Serializable} extra. An entry here — like any entry that fails to
     * serialize — truncates the snapshot: it and everything above it are dropped, and the stack
     * below it is still restored.
     */
    private static final Set<String> NEVER_RECORD =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    "me.edgan.redditslide.Activities.Loader",
                                    "me.edgan.redditslide.Activities.Login",
                                    "me.edgan.redditslide.Activities.Tutorial",
                                    "me.edgan.redditslide.Activities.Draw",
                                    "me.edgan.redditslide.Activities.Submit",
                                    "me.edgan.redditslide.Activities.SendMessage",
                                    "me.edgan.redditslide.Activities.CommentSearch",
                                    "me.edgan.redditslide.Activities.RedditGallery",
                                    "me.edgan.redditslide.Activities.RedditGalleryPager")));

    /** Shared state keys, so writer and reader cannot drift apart. */
    public static final String STATE_SUBREDDIT = "sub";

    public static final String STATE_PAGE = "page";
    public static final String STATE_ANCHOR_ID = "anchorId";
    public static final String STATE_ANCHOR_POSITION = "anchorPos";
    public static final String STATE_ANCHOR_OFFSET = "anchorOffset";
    public static final String STATE_EXPECTED_COUNT = "expectedCount";
    public static final String STATE_AFTER_TOKEN = "after";
    public static final String STATE_HIDDEN = "hidden";
    public static final String STATE_HIDDEN_PERSONS = "hiddenPersons";
    public static final String STATE_VIDEO_POSITION = "videoPosition";
    public static final String STATE_TOOLBAR_HIDDEN = "toolbarHidden";
    public static final String STATE_SUBMISSION = "submission";
    public static final String STATE_SCROLL_Y = "scrollY";
    public static final String STATE_CONTRIB_KEY = "contribKey";

    /**
     * The comment page's own anchor keys. Separate from the feed's rather than shared, because in
     * the swipe-to-comments pager mode both live on one activity and are written into one bundle --
     * and the comment capture, running second, would otherwise overwrite the feed's scroll position
     * with its own.
     */
    public static final String STATE_COMMENT_ANCHOR_ID = "commentAnchorId";

    public static final String STATE_COMMENT_ANCHOR_POSITION = "commentAnchorPos";
    public static final String STATE_COMMENT_ANCHOR_OFFSET = "commentAnchorOffset";
    public static final String STATE_COMMENT_TOOLBAR_HIDDEN = "commentToolbarHidden";

    /** A screen that has state worth carrying across a restart. */
    public interface Restorable {

        /** Write whatever this screen needs to come back looking the same. */
        void saveHibernateState(Bundle out);

        /**
         * Apply a bundle previously written by {@link #saveHibernateState}. Called by the activity
         * itself, at whatever point in its own startup the state can actually be used.
         */
        void restoreHibernateState(Bundle in);
    }

    /** One activity in the recorded stack. */
    private static final class Entry {
        final String cls;
        final Bundle extras;
        Bundle state;

        /**
         * False when the intent carried something that cannot be written to the snapshot, so this
         * screen could not be rebuilt from it. Truncates the capture rather than restoring a screen
         * with half its arguments.
         */
        boolean recordable = true;

        /** Live only while recording; null on an entry read back from disk. */
        @Nullable WeakReference<Activity> activity;

        /** Set once this entry's state has been handed to a recreated activity. */
        boolean claimed;

        Entry(String cls, Bundle extras, Bundle state) {
            this.cls = cls;
            this.extras = extras;
            this.state = state;
        }
    }

    /**
     * The stack as it is right now, bottom first. Only touched from the main thread (every
     * {@code ActivityLifecycleCallbacks} callback runs there).
     */
    private static final List<Entry> live = new ArrayList<>();

    /** The snapshot read back at launch, bottom first, or null when there is nothing to restore. */
    @Nullable private static List<Entry> restoring;

    private static boolean loaded;

    /** Activities currently between onStart and onStop. Zero means the app is in the background. */
    private static int startedCount;

    /**
     * The document last written, so the repeated captures below cost nothing when nothing has
     * changed. Null until the first write of the process.
     */
    @Nullable private static String lastWritten;

    private HibernateState() {}

    // ---------------------------------------------------------------- recording

    /** Push an activity onto the live stack. Called from {@code Reddit.onActivityCreated}. */
    /**
     * The launcher trampoline, which starts the real stack and finishes itself in the same
     * {@code onCreate}. It is never part of the stack, and treating it as one entry would put it at
     * the bottom, where it truncates every screen above it for as long as it takes its
     * {@code onDestroy} to arrive.
     */
    private static final String TRAMPOLINE = "me.edgan.redditslide.Activities.Slide";

    /**
     * False once the first screen of the process has been recorded. Seeding only ever applies to
     * that screen: after it, the live stack is the authority on what the task holds, and reading
     * the snapshot again would prepend screens to a stack the user has since backed out of.
     */
    private static boolean canSeed = true;

    public static void recordCreated(Activity activity) {
        final String cls = activity.getClass().getName();
        if (TRAMPOLINE.equals(cls)) {
            return;
        }
        if (canSeed) {
            canSeed = false;
            seedFromSnapshot(activity, cls);
        }
        for (Entry e : live) {
            if (e.activity != null && e.activity.get() == activity) {
                return; // already tracked
            }
        }
        // A configuration change destroys and rebuilds the activity in place. recordDestroyed left
        // that entry behind with a dead reference precisely so the replacement can adopt it,
        // instead of the stack growing an extra copy of the same screen on every rotation.
        for (int i = live.size() - 1; i >= 0; i--) {
            final Entry e = live.get(i);
            if (e.cls.equals(cls) && (e.activity == null || e.activity.get() == null)) {
                e.activity = new WeakReference<>(activity);
                return;
            }
        }
        final Bundle extras = extrasOf(activity.getIntent());
        final Entry entry = new Entry(cls, extras == null ? new Bundle() : extras, new Bundle());
        entry.recordable = extras != null;
        entry.activity = new WeakReference<>(activity);
        live.add(entry);
    }

    /**
     * Rebuilds the live stack from the snapshot, for a screen that is being recreated out of it.
     *
     * <p>Returning to a task whose process died never runs the trampoline: the system recreates
     * the top activity alone, and the ones below it only as the user backs into them, which can be
     * minutes later. Recording arrivals in the order they happen would put that top screen at the
     * <em>bottom</em> of the live stack, where {@link #capture} refuses it for not starting at
     * MainActivity — so nothing the user does for the rest of that session is written, and the
     * next cold start silently restores the session before it.
     *
     * <p>Matched on class and extras the way {@link #claim} matches, and only against an entry
     * above the bottom one: a deep link into an arbitrary post must not conjure a feed underneath
     * it that the task does not actually have.
     */
    private static void seedFromSnapshot(Activity activity, String cls) {
        final List<Entry> stack = load(activity);
        if (stack == null) {
            return;
        }
        final Bundle extras = extrasOf(activity.getIntent());
        int found = -1;
        for (int i = stack.size() - 1; i > 0; i--) {
            final Entry e = stack.get(i);
            if (e.cls.equals(cls) && extrasMatch(e.extras, extras)) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            return;
        }
        // Copies, so that clearing the snapshot at the next capture cannot take the live stack
        // with it. The entry for this screen is seeded too, and the adoption loop in
        // recordCreated attaches the activity to it.
        for (int i = 0; i <= found; i++) {
            final Entry e = stack.get(i);
            live.add(new Entry(e.cls, e.extras, e.state));
        }
    }

    /** Drop a finishing activity from the live stack. Called from {@code Reddit.onActivityDestroyed}. */
    public static void recordDestroyed(Activity activity) {
        for (int i = live.size() - 1; i >= 0; i--) {
            final WeakReference<Activity> ref = live.get(i).activity;
            if (ref != null && ref.get() == activity) {
                if (activity.isFinishing()) {
                    live.remove(i);
                } else {
                    // Destroyed for a configuration change, not dismissed: the screen is still on
                    // the stack and is about to be rebuilt. Keep the entry, drop the reference.
                    live.get(i).activity = null;
                }
                return;
            }
        }
    }

    public static void onActivityStarted() {
        startedCount++;
    }

    /**
     * Called from {@code Reddit.onActivityPaused}, which is the earliest warning there is.
     *
     * <p>Opening the recents screen delivers no callback at all — the activity stays resumed behind
     * the overview — so there is no quiet moment beforehand in which to save. Everything arrives at
     * once when the task is dismissed: onPause, onStop and onDestroy inside about 180ms, and then
     * the process is killed. Capturing from the first of those three is most of the margin there is.
     *
     * <p>It fires on ordinary navigation within the app too, which is welcome: it keeps the
     * document on disk current, and an unchanged one is not rewritten.
     */
    public static void onActivityPaused(Context context) {
        capture(context);
    }

    /**
     * Called when a screen has settled somewhere the user might leave from — a feed that has come
     * to rest after a scroll.
     *
     * <p>The lifecycle callbacks are not on their own enough to promise "always where I left off".
     * A process killed out of the recents overview for memory gets no callbacks at all, and the
     * newest state then on disk would be from whenever the user last moved between screens: the
     * right tab, but the wrong place in it. Capturing at rest costs a comparison and, when the
     * position really has moved, one small write.
     */
    public static void onContentSettled(Context context) {
        capture(context);
    }

    /**
     * Called from {@code Reddit.onActivityStopped}. A backstop for the pause above, and the point
     * at which the app has genuinely gone to the background rather than moved between its own
     * screens.
     */
    public static void onActivityStopped(Context context) {
        startedCount--;
        if (startedCount <= 0) {
            startedCount = 0;
            capture(context);
        }
    }

    /** Ask every live screen for its state and write the snapshot. */
    private static void capture(Context context) {
        if (!SettingValues.hibernate) {
            return;
        }
        final List<Entry> snapshot = new ArrayList<>();
        for (Entry e : live) {
            if (!e.recordable || NEVER_RECORD.contains(e.cls)) {
                break; // this screen and everything above it cannot be rebuilt
            }
            final Activity a = e.activity == null ? null : e.activity.get();
            if (a instanceof Restorable) {
                final Bundle out = new Bundle();
                try {
                    ((Restorable) a).saveHibernateState(out);
                } catch (Exception ex) {
                    LogUtil.e(ex, "HibernateState.capture failed for " + e.cls);
                    break;
                }
                // An empty bundle means the screen had nothing to say, not that it has nothing
                // worth remembering. Every implementation writes something as soon as it can --
                // a feed its listing, a thread its submission -- so the only way to come back
                // empty is to be asked before that point, which a capture during startup does.
                // Overwriting the recorded state with it is how a resume loses the position it
                // was two callbacks away from restoring.
                if (!out.isEmpty()) {
                    e.state = out;
                }
            }
            snapshot.add(e);
        }
        if (snapshot.isEmpty() || !MainActivity.class.getName().equals(snapshot.get(0).cls)) {
            // Without MainActivity underneath, backing out of a restored screen would leave the
            // user nowhere. Better to launch normally than to restore half a stack.
            return;
        }
        final String json = toJson(snapshot, Authentication.nameOrEmpty());
        if (json.equals(lastWritten)) {
            return;
        }
        // Only now, with a replacement in hand, is the snapshot read at launch obsolete. Clearing
        // it on every backgrounding instead would break the path where the system recreates a task
        // whose process died: the screens below the top one are only built as the user backs into
        // them, which can be long after the app has been to the background and come back.
        restoring = null;
        // Written on the calling thread, deliberately. Swiping the app off the recents screen
        // delivers onPause, onStop and onDestroy in one ~180ms burst at the moment of dismissal and
        // then kills the process: a write queued onto a background thread in that window loses the
        // race, and the session goes with it. The document is a few hundred bytes to internal
        // storage, and the check above means an unchanged one is not written at all.
        try {
            Files.write(
                    new File(context.getFilesDir(), FILE_NAME).toPath(),
                    json.getBytes(StandardCharsets.UTF_8));
            lastWritten = json;
        } catch (IOException e) {
            LogUtil.e(e, "HibernateState.capture write failed");
        }
    }

    // ---------------------------------------------------------------- restoring

    /**
     * The intents that rebuild the recorded stack, bottom first, or {@code null} when there is
     * nothing to restore. Suitable for {@code Context.startActivities}, which builds the whole back
     * stack in one call and resumes only the last entry.
     */
    @Nullable
    public static Intent[] buildRestoreStack(Context context) {
        final List<Entry> stack = load(context);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        final Intent[] intents = new Intent[stack.size()];
        for (int i = 0; i < stack.size(); i++) {
            final Entry e = stack.get(i);
            final Intent intent = new Intent();
            intent.setClassName(context.getPackageName(), e.cls);
            intent.putExtras(e.extras);
            if (i == 0) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            intents[i] = intent;
        }
        return intents;
    }

    /**
     * The state this activity should restore itself to, or {@code null} if it is not part of the
     * snapshot. Each entry is handed out once.
     *
     * <p>Matching is by class name plus intent extras, rather than by stack position, because on
     * the Recents path the system recreates the activities out of order and lazily — the feed
     * underneath a restored comment screen is only built when the user presses Back.
     */
    @Nullable
    public static Bundle claim(Activity activity) {
        if (!SettingValues.hibernate) {
            return null;
        }
        final List<Entry> stack = load(activity);
        if (stack == null) {
            return null;
        }
        final String cls = activity.getClass().getName();
        final Bundle extras = extrasOf(activity.getIntent());
        for (Entry e : stack) {
            if (e.claimed || !e.cls.equals(cls)) {
                continue;
            }
            // There is only ever one MainActivity, and its intent picks up extras along the way
            // (restartTheme writes EXTRA_PAGE_TO into it, and that intent is what the system
            // persists in the task record), so its extras are not a usable identity.
            final boolean matches =
                    MainActivity.class.getName().equals(cls) || extrasMatch(e.extras, extras);
            if (matches) {
                e.claimed = true;
                return e.state;
            }
        }
        return null;
    }

    /** Forget the snapshot entirely — an account switch or a data restore invalidates it. */
    public static void clear(Context context) {
        restoring = null;
        loaded = true;
        canSeed = false;
        lastWritten = null;
        // The cached profile listings go with it: they are the content the snapshot points at, and
        // an account switch makes them another account's posts.
        ContributionCache.clear();
        final File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.exists() && !file.delete()) {
            LogUtil.e("HibernateState.clear could not delete " + file);
        }
    }

    @Nullable
    private static List<Entry> load(Context context) {
        if (loaded) {
            return restoring;
        }
        loaded = true;
        if (!SettingValues.hibernate) {
            return null;
        }
        final File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.exists()) {
            return null;
        }
        try {
            final String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            restoring = fromJson(json);
        } catch (IOException | JSONException e) {
            LogUtil.e(e, "HibernateState.load failed");
            restoring = null;
        }
        return restoring;
    }

    // ---------------------------------------------------------------- serialization

    private static String toJson(List<Entry> stack, String account) {
        final JSONObject root = new JSONObject();
        try {
            root.put(KEY_VERSION, VERSION);
            root.put(KEY_SAVED_AT, System.currentTimeMillis());
            root.put(KEY_ACCOUNT, account);
            final JSONArray arr = new JSONArray();
            for (Entry e : stack) {
                final JSONObject o = new JSONObject();
                o.put(KEY_CLS, e.cls);
                o.put(KEY_EXTRAS, bundleToJson(e.extras));
                o.put(KEY_STATE, bundleToJson(e.state));
                arr.put(o);
            }
            root.put(KEY_STACK, arr);
        } catch (JSONException e) {
            LogUtil.e(e, "HibernateState.toJson failed");
        }
        return root.toString();
    }

    @Nullable
    private static List<Entry> fromJson(String json) throws JSONException {
        final JSONObject root = new JSONObject(json);
        if (root.optInt(KEY_VERSION, -1) != VERSION) {
            return null;
        }
        // A snapshot belongs to the account that made it: restoring one user's feed and inbox into
        // another user's session would show them somebody else's content.
        if (!root.optString(KEY_ACCOUNT, "").equals(Authentication.nameOrEmpty())) {
            return null;
        }
        final JSONArray arr = root.optJSONArray(KEY_STACK);
        if (arr == null || arr.length() == 0) {
            return null;
        }
        final List<Entry> stack = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            final JSONObject o = arr.getJSONObject(i);
            final String cls = o.optString(KEY_CLS, "");
            if (cls.isEmpty() || NEVER_RECORD.contains(cls)) {
                break;
            }
            stack.add(
                    new Entry(
                            cls,
                            jsonToBundle(o.optJSONObject(KEY_EXTRAS)),
                            jsonToBundle(o.optJSONObject(KEY_STATE))));
        }
        if (stack.isEmpty() || !MainActivity.class.getName().equals(stack.get(0).cls)) {
            return null;
        }
        return stack;
    }

    /**
     * The JSON-safe subset of an intent's extras, or {@code null} if it carries anything else —
     * which is what keeps a screen whose arguments are a {@code Serializable} payload out of the
     * snapshot instead of half-restoring it.
     */
    @Nullable
    private static Bundle extrasOf(@Nullable Intent intent) {
        if (intent == null) {
            return new Bundle();
        }
        // A screen opened from a link carries its argument as the intent's data, not as an extra,
        // so recording the extras alone records it as having no argument at all. Rebuilt from
        // that, OpenContent finds no URI to resolve and starts MainActivity instead -- a second
        // feed pushed on top of the stack that was just restored, with the screen the user
        // actually left buried under it. Unrecordable, so the snapshot truncates here instead.
        if (intent.getData() != null || intent.getClipData() != null) {
            return null;
        }
        final Bundle source = intent.getExtras();
        if (source == null) {
            return new Bundle();
        }
        final Bundle out = new Bundle();
        for (String key : source.keySet()) {
            final Object value = source.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof String) {
                out.putString(key, (String) value);
            } else if (value instanceof Integer) {
                out.putInt(key, (Integer) value);
            } else if (value instanceof Long) {
                out.putLong(key, (Long) value);
            } else if (value instanceof Boolean) {
                out.putBoolean(key, (Boolean) value);
            } else if (value instanceof Float) {
                out.putFloat(key, (Float) value);
            } else if (value instanceof Double) {
                out.putDouble(key, (Double) value);
            } else {
                return null;
            }
        }
        return out;
    }

    private static boolean extrasMatch(Bundle recorded, @Nullable Bundle actual) {
        if (actual == null) {
            return recorded.isEmpty();
        }
        if (recorded.size() != actual.size()) {
            return false;
        }
        for (String key : recorded.keySet()) {
            final Object a = recorded.get(key);
            final Object b = actual.get(key);
            if (a == null ? b != null : !a.equals(b)) {
                return false;
            }
        }
        return true;
    }

    private static JSONObject bundleToJson(Bundle bundle) throws JSONException {
        final JSONObject out = new JSONObject();
        for (String key : bundle.keySet()) {
            final Object value = bundle.get(key);
            if (value == null) {
                continue;
            }
            final JSONObject cell = new JSONObject();
            if (value instanceof String) {
                cell.put(KEY_TYPE, "s").put(KEY_VALUE, value);
            } else if (value instanceof Integer) {
                cell.put(KEY_TYPE, "i").put(KEY_VALUE, value);
            } else if (value instanceof Long) {
                cell.put(KEY_TYPE, "l").put(KEY_VALUE, value);
            } else if (value instanceof Boolean) {
                cell.put(KEY_TYPE, "b").put(KEY_VALUE, value);
            } else if (value instanceof Float) {
                cell.put(KEY_TYPE, "f").put(KEY_VALUE, ((Float) value).doubleValue());
            } else if (value instanceof Double) {
                cell.put(KEY_TYPE, "d").put(KEY_VALUE, value);
            } else if (value instanceof ArrayList<?>) {
                final JSONArray list = new JSONArray();
                for (Object item : (ArrayList<?>) value) {
                    if (item instanceof String) {
                        list.put(item);
                    }
                }
                cell.put(KEY_TYPE, "sl").put(KEY_VALUE, list);
            } else {
                continue;
            }
            out.put(key, cell);
        }
        return out;
    }

    private static Bundle jsonToBundle(@Nullable JSONObject json) {
        final Bundle out = new Bundle();
        if (json == null) {
            return out;
        }
        for (java.util.Iterator<String> it = json.keys(); it.hasNext(); ) {
            final String key = it.next();
            final JSONObject cell = json.optJSONObject(key);
            if (cell == null) {
                continue;
            }
            switch (cell.optString(KEY_TYPE, "")) {
                case "s":
                    out.putString(key, cell.optString(KEY_VALUE, ""));
                    break;
                case "i":
                    out.putInt(key, cell.optInt(KEY_VALUE, 0));
                    break;
                case "l":
                    out.putLong(key, cell.optLong(KEY_VALUE, 0L));
                    break;
                case "b":
                    out.putBoolean(key, cell.optBoolean(KEY_VALUE, false));
                    break;
                case "f":
                    out.putFloat(key, (float) cell.optDouble(KEY_VALUE, 0d));
                    break;
                case "d":
                    out.putDouble(key, cell.optDouble(KEY_VALUE, 0d));
                    break;
                case "sl":
                    final JSONArray list = cell.optJSONArray(KEY_VALUE);
                    final ArrayList<String> values = new ArrayList<>();
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            final String item = list.optString(i, "");
                            if (!item.isEmpty()) {
                                values.add(item);
                            }
                        }
                    }
                    out.putStringArrayList(key, values);
                    break;
                default:
                    break;
            }
        }
        return out;
    }
}
