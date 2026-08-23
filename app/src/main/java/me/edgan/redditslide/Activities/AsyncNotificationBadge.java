package me.edgan.redditslide.Activities;

import static me.edgan.redditslide.UserSubscriptions.modOf;

import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Autocache.AutoCacheScheduler;
import me.edgan.redditslide.InboxCount;
import me.edgan.redditslide.Notifications.NotificationJobScheduler;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SavedUsers;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.OnSingleClickListener;
import net.dean.jraw.models.LoggedInAccount;
import net.dean.jraw.paginators.InboxPaginator;
import net.dean.jraw.paginators.Paginator;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class AsyncNotificationBadge extends AsyncTask<Void, Void, Void> {
    private MainActivity activity;

    /**
     * Unread count for this run, or -1 for "did not get one". Every early return out of {@link
     * #doInBackground} leaves it at -1 so the stored count survives: a 0 here would be written as
     * the truth and wipe a real number that was never re-fetched.
     */
    int count = -1;

    /**
     * How stale the stored count may get before this task re-fetches the listing. MainActivity
     * runs this on every resume, and the listing is the heaviest request here -- a page of full
     * messages -- while the count itself is already kept current by the paths that read mail and
     * by the background poll. So the fetch is reconciliation on a timer, not per screen return.
     */
    private static final long UNREAD_REFETCH_INTERVAL_MS = 60_000L;

    /** {@link InboxCount#generation()} as it stood when the listing was requested. */
    private int fetchedAtGeneration;

    boolean restart;
    boolean isCurrentUserMod = false; // Track if current user is mod

    public AsyncNotificationBadge(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    protected @Nullable Void doInBackground(Void... params) {
        if (Authentication.reddit == null) {
            return null;
        }

        try {
            LoggedInAccount me;
            if (Authentication.me == null) {
                Authentication.me = Authentication.reddit.me();
                me = Authentication.me;
                if (Authentication.nameOrEmpty().equalsIgnoreCase("loggedout")) {
                    Authentication.name = me.getFullName();
                    Reddit.appRestart.edit().putString("name", Authentication.name).apply();
                    restart = true;
                    return null;
                }
                // Update current user's mod status
                Authentication.mod = me.isMod();

                isCurrentUserMod = Authentication.mod;

                Authentication.authentication.edit().putBoolean(Reddit.SHARED_PREF_IS_MOD, Authentication.mod).apply();

                // If this account is a moderator, load the moderated subreddits
                if (Authentication.mod) {
                    UserSubscriptions.modOf = UserSubscriptions.getModeratedSubs();
                } else {
                    UserSubscriptions.modOf = null;
                }

                if (Reddit.notificationTime != -1) {
                    Reddit.notifications = new NotificationJobScheduler(activity);
                    Reddit.notifications.start();
                }
                if (Reddit.cachedData.contains("toCache")) {
                    Reddit.autoCache = new AutoCacheScheduler(activity);
                    Reddit.autoCache.start();
                }
                final String name = me.getFullName();
                Authentication.name = name;
                LogUtil.v("AUTHENTICATED");
                if (Authentication.reddit.isAuthenticated()) {
                    Authentication.migrateAccountToTokenForm(name);
                    Authentication.isLoggedIn = true;
                    Reddit.notFirst = true;
                }
            } else {
                me = Authentication.reddit.me();

                // Update current user's mod status
                Authentication.mod = me.isMod();
                isCurrentUserMod = Authentication.mod;

                // If this account is a moderator, load the moderated subreddits
                if (Authentication.mod) {
                    if (UserSubscriptions.modOf == null || UserSubscriptions.modOf.isEmpty()) {
                        UserSubscriptions.modOf = UserSubscriptions.getModeratedSubs();
                    }
                } else {
                    UserSubscriptions.modOf = null;
                }
            }
            // Ahead of the unread fetch: that fetch is the last thing in this try, so a failure
            // there cannot cost this sync its run. syncFriendsFromReddit swallows its own
            // failures, so it cannot cost the count one either.
            SavedUsers.syncFriendsFromReddit();

            if (InboxCount.isFresh(UNREAD_REFETCH_INTERVAL_MS)) {
                // count stays -1, so onPostExecute leaves the stored number exactly as it is.
                return null;
            }

            // The unread listing is the count. The account's own inbox_count field is not a
            // count of unread messages -- it reports 0 while has_mail is true and
            // /message/unread still lists them -- so it is not read anywhere. The page cap
            // means an inbox with more than RECOMMENDED_MAX_LIMIT unread counts as that many.
            fetchedAtGeneration = InboxCount.generation();
            final InboxPaginator unread = new InboxPaginator(Authentication.reddit, "unread");
            unread.setLimit(Paginator.RECOMMENDED_MAX_LIMIT);
            count = unread.hasNext() ? unread.next().size() : 0;
            InboxCount.markFetched();

        } catch (Exception e) {
            // -1 leaves the stored count alone; the unread count never falls back to zero.
            Log.w(LogUtil.getTag(), "Cannot fetch unread messages");
            count = -1;
        }

        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (restart) {
            activity.restartTheme();
            return;
        }

        // Store the count before anything that can bail out below: the drawer badge is painted
        // from the stored value by MainActivity's observer, not from here, so a header that is
        // not up yet must not cost us the number.
        if (count != -1) {
            int oldCount = InboxCount.get(Reddit.appRestart);
            boolean stored =
                    InboxCount.setFromFetch(Reddit.appRestart, count, fetchedAtGeneration);
            if (stored && count > oldCount) {
                // Ensure mToolbar is not null before using it
                if (activity.mToolbar == null) {
                    Log.e(LogUtil.getTag(), "mToolbar is null in AsyncNotificationBadge.onPostExecute");
                } else {
                    final Snackbar s =
                            Snackbar.make(
                                            activity.mToolbar,
                                            activity.getResources()
                                                    .getQuantityString(
                                                            R.plurals.new_messages,
                                                            count - oldCount,
                                                            count - oldCount),
                                            Snackbar.LENGTH_LONG)
                                    .setAction(
                                            R.string.btn_view,
                                            new OnSingleClickListener() {
                                                @Override
                                                public void onSingleClick(View v) {
                                                    Intent i = new Intent(activity, Inbox.class);
                                                    i.putExtra(Inbox.EXTRA_UNREAD, true);
                                                    activity.startActivity(i);
                                                }
                                            });

                    LayoutUtils.showSnackbar(s);
                }
            }
        }

        // Ensure headerMain is not null before accessing its children
        if (activity.headerMain == null) {
            Log.e(LogUtil.getTag(), "headerMain is null in AsyncNotificationBadge.onPostExecute");
            return; // Cannot proceed without headerMain
        }

        // Always hide the mod button first. Only drawer_loggedin carries R.id.mod, and
        // headerMain can still be the logged-out or offline header when this runs, so the null
        // the guard below already expects has to be honoured here too.
        RelativeLayout mod = activity.headerMain.findViewById(R.id.mod);
        if (mod != null) {
            mod.setVisibility(View.GONE);
        }

        // Only show mod button if user is a mod and has moderated subreddits
        if (isCurrentUserMod && UserSubscriptions.modOf != null && !UserSubscriptions.modOf.isEmpty() && Authentication.didOnline) {
            if (mod != null) {
                mod.setVisibility(View.VISIBLE);

                mod.setOnClickListener(
                        new OnSingleClickListener() {
                            @Override
                            public void onSingleClick(View view) {
                                if (modOf != null && !modOf.isEmpty()) {
                                    Intent inte = new Intent(activity, ModQueue.class);
                                    activity.startActivity(inte);
                                }
                            }
                        });
            } else {
                Log.e(LogUtil.getTag(), "R.id.mod not found in headerMain");
            }
        }
    }
}
