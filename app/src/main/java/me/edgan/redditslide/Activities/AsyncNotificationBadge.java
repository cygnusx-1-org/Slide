package me.edgan.redditslide.Activities;

import android.app.NotificationManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Autocache.AutoCacheScheduler;
import me.edgan.redditslide.Notifications.NotificationJobScheduler;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.OnSingleClickListener;
import net.dean.jraw.models.LoggedInAccount;

import static me.edgan.redditslide.UserSubscriptions.modOf;

public class AsyncNotificationBadge extends AsyncTask<Void, Void, Void> {
    private MainActivity activity;
    int count;
    boolean restart;
    boolean isCurrentUserMod = false; // Track if current user is mod

    public AsyncNotificationBadge(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    protected Void doInBackground(Void... params) {
        try {
            LoggedInAccount me;
            if (Authentication.me == null) {
                Authentication.me = Authentication.reddit.me();
                me = Authentication.me;
                if (Authentication.name.equalsIgnoreCase("loggedout")) {
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
                    final Set<String> accounts =
                            Authentication.authentication.getStringSet(
                                    "accounts", new HashSet<String>());
                    if (accounts.contains(name)) {
                        accounts.remove(name);
                        accounts.add(name + ":" + Authentication.refresh);
                        Authentication.authentication
                                .edit()
                                .putStringSet("accounts", accounts)
                                .commit(); // force commit
                    }
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
            count = me.getInboxCount(); // Force reload of the LoggedInAccount object
            UserSubscriptions.doFriendsOfMain(activity);

        } catch (Exception e) {
            Log.w(LogUtil.getTag(), "Cannot fetch inbox count");
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

        // Ensure headerMain is not null before accessing its children
        if (activity.headerMain == null) {
            Log.e(LogUtil.getTag(), "headerMain is null in AsyncNotificationBadge.onPostExecute");
            return; // Cannot proceed without headerMain
        }

        // Always hide the mod button first
        RelativeLayout mod = activity.headerMain.findViewById(R.id.mod);
        mod.setVisibility(View.GONE);

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

        if (count != -1) {
            int oldCount = Reddit.appRestart.getInt("inbox", 0);
            if (count > oldCount) {
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
            Reddit.appRestart.edit().putInt("inbox", count).apply();
        }
        View badge = activity.headerMain.findViewById(R.id.count);
        if (badge != null) { // Check if findViewById returned a valid view
            if (count == 0) {
                badge.setVisibility(View.GONE);
                NotificationManager notificationManager =
                        ContextCompat.getSystemService(activity, NotificationManager.class);
                if (notificationManager != null) {
                    notificationManager.cancel(0);
                }
            } else if (count != -1) {
                badge.setVisibility(View.VISIBLE);
                TextView countTextView = activity.headerMain.findViewById(R.id.count);
                if (countTextView != null) {
                    countTextView.setText(String.format(Locale.getDefault(), "%d", count));
                } else {
                    Log.e(LogUtil.getTag(), "R.id.count (TextView) not found in headerMain");
                }
            }
        } else {
            Log.e(LogUtil.getTag(), "R.id.count (View) not found in headerMain");
        }
    }
}
