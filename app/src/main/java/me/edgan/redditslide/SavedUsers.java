package me.edgan.redditslide;

import android.content.SharedPreferences;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.PrefUtil;
import me.edgan.redditslide.util.StringUtil;

import net.dean.jraw.RedditClient;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.UserRecord;
import net.dean.jraw.paginators.ImportantUserPaginator;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The locally stored list of usernames shown by the "Users" drawer item, and which of them are
 * marked as reddit friends.
 *
 * <p>Both lists are stored per account, keyed by {@link Authentication#nameOrEmpty()} as a comma
 * separated string, the same format {@link UserSubscriptions} uses for subscriptions and pinned
 * subreddits. They live in separate preference files rather than under two keys of one file because
 * reddit usernames allow {@code _} and {@code -}, so any suffixed key can collide with a real
 * account name.
 *
 * <p>Saving a user is purely local, so it stores whatever it is handed; the "is this a real
 * redditor" check lives at the point of entry in {@code Users}. A saved account that later stops
 * being reachable stays in the list and falls through to the profile error dialog when opened.
 *
 * <p>The friend marks mirror the account's reddit friends, so that {@code /r/friends} keeps working.
 * {@link #syncFriendsFromReddit()} pulls them in, {@link #removeUser(String)} ends a friendship on
 * its way out, and anything else that flips a mark has to follow it with
 * {@link #pushFriendToReddit(String, boolean)} from a background thread.
 */
@NullMarked
public class SavedUsers {

    @SuppressWarnings("NullAway.Init") // Reddit.onCreate assigns this
    public static SharedPreferences users;

    @SuppressWarnings("NullAway.Init") // Reddit.onCreate assigns this
    public static SharedPreferences friends;

    // isFriend() runs for every bound row of the feed, comment and inbox lists, so the parsed list
    // is kept in memory instead of re-reading and re-splitting the preference every time.
    // friendCacheAccount is the account it was built for; the stored value is keyed by account name,
    // so switching accounts has to rebuild it.
    //
    // Written copy-on-write, never in place: SubmissionCache.cacheSubmissions reads this from the
    // feed-loading thread while the Users screen can be marking a friend on the main thread, and
    // mutating the list a reader is iterating throws ConcurrentModificationException. Volatile so a
    // reader on another thread sees the replacement rather than an indefinitely stale list.
    @Nullable private static volatile CaseInsensitiveArrayList friendCache;

    @Nullable private static volatile String friendCacheAccount;

    // The account whose reddit friends have already been pulled this process. Only set on success,
    // so a pull that failed is retried on the next launch. Volatile because the two callers are
    // both background threads, and the guard is pointless if one cannot see the other's write.
    @Nullable private static volatile String friendsSyncedAccount;

    // Bumped by every local mark change. syncFriendsFromReddit reads it either side of its fetch so
    // that a star tapped while the pull was in flight is not undone by the older list that comes
    // back. Not atomic, and does not need to be: setFriend is only ever called from the UI thread,
    // so there is a single writer.
    private static volatile int friendMarkGeneration;

    /**
     * Every friend push runs here, one at a time, so they reach reddit in the order the taps
     * happened. Starring a user and then deleting the row fires an add and a remove for the same
     * name; run in parallel their completion order is undefined, and if the remove lands first the
     * friendship survives on reddit and the next sync puts the name straight back into the list.
     *
     * <p>Its own daemon thread rather than the shared AsyncTask serial executor, so a stalled push
     * neither blocks nor is blocked by unrelated background work.
     */
    private static final ExecutorService FRIEND_PUSHER =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "SavedUsers-friend-pusher");
                        t.setDaemon(true);
                        return t;
                    });

    /** The executor {@link #pushFriendToReddit} has to run on; see {@link #FRIEND_PUSHER}. */
    public static Executor friendPusher() {
        return FRIEND_PUSHER;
    }

    private SavedUsers() {}

    /** The saved usernames for the current account, sorted case-insensitively. */
    public static CaseInsensitiveArrayList getUsers() {
        return read(users);
    }

    /**
     * Adds a username to the current account's list.
     *
     * @return false when the name is blank or already saved under any casing
     */
    public static boolean addUser(String username) {
        final String trimmed = username.trim();

        if (trimmed.isEmpty()) {
            return false;
        }

        final CaseInsensitiveArrayList saved = getUsers();

        if (saved.contains(trimmed)) {
            return false;
        }

        saved.add(trimmed);
        write(users, saved);
        return true;
    }

    /**
     * Removes a username from the current account's list, ignoring case.
     *
     * <p>Ends the reddit friendship too, when there was one. Dropping only the local mark would let
     * {@link #syncFriendsFromReddit()} put the name straight back into the list on the next launch,
     * so the removal would not stick. The push is fire and forget: every caller has already taken
     * the row off screen, so there is nothing left to report to or put back.
     */
    public static void removeUser(String username) {
        final CaseInsensitiveArrayList saved = getUsers();
        final CaseInsensitiveArrayList kept = new CaseInsensitiveArrayList();

        for (String s : saved) {
            if (!s.equalsIgnoreCase(username)) {
                kept.add(s);
            }
        }

        write(users, kept);

        final boolean wasFriend = isFriend(username);
        setFriend(username, false);

        if (wasFriend) {
            FRIEND_PUSHER.execute(() -> pushFriendToReddit(username, false));
        }
    }

    /** Whether the username is saved for the current account, ignoring case. */
    public static boolean contains(String username) {
        return getUsers().contains(username);
    }

    /** Whether the username is marked as a friend of the current account, ignoring case. */
    public static boolean isFriend(String username) {
        return cachedFriends().contains(username);
    }

    /**
     * Marks or unmarks a username as a friend of the current account.
     *
     * <p>Local only; the caller pushes the matching change to reddit.
     */
    public static void setFriend(String username, boolean friend) {
        final CaseInsensitiveArrayList marked = new CaseInsensitiveArrayList(cachedFriends());
        boolean changed = false;

        if (friend) {
            final String trimmed = username.trim();

            if (!trimmed.isEmpty() && !marked.contains(trimmed)) {
                marked.add(trimmed);
                changed = true;
            }
        } else {
            for (int i = marked.size() - 1; i >= 0; i--) {
                if (marked.get(i).equalsIgnoreCase(username)) {
                    marked.remove(i);
                    changed = true;
                }
            }
        }

        if (changed) {
            Collections.sort(marked, String.CASE_INSENSITIVE_ORDER);
            write(friends, marked);
            friendCache = marked;
            friendCacheAccount = Authentication.nameOrEmpty();
            friendMarkGeneration++;
        }
    }

    /**
     * Mirrors a friend mark onto reddit, so {@code /r/friends} and other clients stay in step.
     *
     * <p>Blocking, and has to run on {@link #friendPusher()} so that pushes for the same name
     * cannot overtake each other.
     *
     * @return whether the friendship actually changed. False means it is still exactly as it was,
     *     and the local mark is now out of step with reddit.
     */
    public static boolean pushFriendToReddit(String username, boolean friend) {
        final RedditClient reddit = Authentication.reddit;

        if (reddit == null) {
            return false;
        }

        try {
            final AccountManager manager = new AccountManager(reddit);

            try {
                if (friend) {
                    manager.updateFriend(username);
                } else {
                    manager.deleteFriend(username);
                }
            } catch (NetworkException e) {
                // reddit answered, and refused. The friendship is exactly as it was.
                throw e;
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException) {
                    // RestClient wraps a request that never left the device -- offline, DNS,
                    // timeout -- in a bare RuntimeException. Nothing changed.
                    throw e;
                }

                // Anything else means reddit took the request and JRAW then tripped over the reply,
                // because it builds its RestResponse eagerly: a body with no Content-Type header
                // throws IllegalStateException, and a 204 with no body leaves the media type null,
                // which RestClient dereferences into a NullPointerException. reddit answers an
                // accepted unfriend that way today; the same is applied to the add so that a reply
                // JRAW cannot parse is never reported as a friendship that did not happen.
                //
                // The one case this reads wrong is an HTTP error whose response carries no
                // Content-Type at all, which is indistinguishable here because the exception does
                // not carry the status code. reddit sends one on its error pages, and the next
                // sync reconciles regardless.
            }

            return true;
        } catch (Exception e) {
            LogUtil.e(e, "SavedUsers.pushFriendToReddit failed");
            return false;
        }
    }

    /**
     * Pulls the current account's reddit friends in, marking each one and adding any that are not
     * saved yet. The first run is what migrates a friends list made before the Users screen existed.
     *
     * <p>Blocking, so callers have to already be off the main thread. Runs once per account per
     * process; a failed pull leaves the stored marks alone and is retried next launch, because
     * "could not reach reddit" must not read as "you have no friends".
     *
     * <p>Synchronized because the two callers are independent background threads that both reach
     * here at cold start, and the "already pulled" flag is only set at the very end: without the
     * lock they both pass the guard and each issue the request. Whichever arrives second waits,
     * then re-reads the flag and returns, so a pull that failed is still retried by that second
     * caller rather than waiting for the next launch. Holding the monitor across the request is
     * safe only because nothing else in this class takes it -- in particular {@link
     * #setFriend(String, boolean)} and {@link #isFriend(String)} do not, so the UI thread can
     * never end up blocked behind a network call.
     */
    public static synchronized void syncFriendsFromReddit() {
        final String account = Authentication.nameOrEmpty();

        if (account.equals(friendsSyncedAccount)
                || !Authentication.isLoggedIn
                || Authentication.reddit == null) {
            return;
        }

        final CaseInsensitiveArrayList fetched = new CaseInsensitiveArrayList();
        final int generation = friendMarkGeneration;

        try {
            final ImportantUserPaginator pag =
                    new ImportantUserPaginator(Authentication.reddit, "friends");
            pag.setLimit(100);

            while (pag.hasNext()) {
                for (UserRecord s : pag.next()) {
                    // UserRecord is a Thing, so getFullName() is the "name" field, which reddit
                    // fills with the username rather than a t2_ id for this endpoint.
                    final String name = s.getFullName();

                    if (name != null && !name.isEmpty() && !fetched.contains(name)) {
                        fetched.add(name);
                    }
                }
            }
        } catch (Exception e) {
            LogUtil.e(e, "SavedUsers.syncFriendsFromReddit failed");
            return;
        }

        // The fetch is not instant, and the Users screen is reachable while it runs. A mark flipped
        // in that window has already been written locally and pushed to reddit, so everything that
        // came back describes the account as it was before: writing the marks would drop what the
        // user just did, and adding the names would put back a row they just deleted. Both come off
        // the same stale list, so both wait for the next launch to reconcile.
        if (generation == friendMarkGeneration) {
            Collections.sort(fetched, String.CASE_INSENSITIVE_ORDER);
            write(friends, fetched);
            friendCache = fetched;
            friendCacheAccount = account;

            final CaseInsensitiveArrayList saved = getUsers();
            boolean added = false;

            for (String name : fetched) {
                if (!saved.contains(name)) {
                    saved.add(name);
                    added = true;
                }
            }

            if (added) {
                Collections.sort(saved, String.CASE_INSENSITIVE_ORDER);
                write(users, saved);
            }
        }

        friendsSyncedAccount = account;
    }

    /** The friend list held in memory, rebuilt when the signed in account changes. */
    private static CaseInsensitiveArrayList cachedFriends() {
        final String account = Authentication.nameOrEmpty();
        CaseInsensitiveArrayList cached = friendCache;

        if (cached == null || !account.equals(friendCacheAccount)) {
            cached = read(friends);
            friendCache = cached;
            friendCacheAccount = account;
        }

        return cached;
    }

    private static CaseInsensitiveArrayList read(SharedPreferences prefs) {
        final CaseInsensitiveArrayList saved = new CaseInsensitiveArrayList();
        final String stored = PrefUtil.getString(prefs, Authentication.nameOrEmpty(), "");

        if (!stored.isEmpty()) {
            for (String username : stored.split(",")) {
                if (!username.isEmpty() && !saved.contains(username)) {
                    saved.add(username);
                }
            }
        }

        Collections.sort(saved, String.CASE_INSENSITIVE_ORDER);
        return saved;
    }

    private static void write(SharedPreferences prefs, CaseInsensitiveArrayList saved) {
        prefs.edit()
                .putString(Authentication.nameOrEmpty(), StringUtil.arrayToString(saved))
                .apply();
    }
}
