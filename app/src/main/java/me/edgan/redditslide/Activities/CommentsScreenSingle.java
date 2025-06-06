package me.edgan.redditslide.Activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Autocache.AutoCacheScheduler;
import me.edgan.redditslide.Fragments.BlankFragment;
import me.edgan.redditslide.Fragments.CommentPage;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.LastComments;
import me.edgan.redditslide.Notifications.NotificationJobScheduler;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SwipeLayout.Utils;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;

import net.dean.jraw.models.Submission;

/**
 * Created by ccrama on 9/17/2015.
 *
 * <p>This activity takes parameters for a submission id (through intent or direct link), retrieves
 * the Submission object, and then displays the submission with its comments.
 */
public class CommentsScreenSingle extends BaseActivityAnim {
    CommentsScreenSinglePagerAdapter comments;
    boolean np;
    private ViewPager pager;
    private String subreddit;
    private String name;
    private String context;
    private int contextNumber;
    private Boolean doneTranslucent = false;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 14 && comments != null) {
            comments.notifyDataSetChanged();
        }
    }

    public static final String EXTRA_SUBREDDIT = "subreddit";
    public static final String EXTRA_CONTEXT = "context";
    public static final String EXTRA_CONTEXT_NUMBER = "contextNumber";
    public static final String EXTRA_SUBMISSION = "submission";
    public static final String EXTRA_NP = "np";
    public static final String EXTRA_LOADMORE = "loadmore";

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (SettingValues.commentVolumeNav) {

            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_UP:
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    return ((CommentPage) comments.getCurrentFragment()).onKeyDown(keyCode, event);
                default:
                    return super.dispatchKeyEvent(event);
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onCreate(Bundle savedInstance) {
        disableSwipeBackLayout();
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().getDecorView().setBackground(null);
        super.onCreate(savedInstance);
        applyColorTheme();
        setContentView(R.layout.activity_slide);
        name = getIntent().getExtras().getString(EXTRA_SUBMISSION, "");

        subreddit = getIntent().getExtras().getString(EXTRA_SUBREDDIT, "");
        np = getIntent().getExtras().getBoolean(EXTRA_NP, false);
        context = getIntent().getExtras().getString(EXTRA_CONTEXT, "");

        contextNumber = getIntent().getExtras().getInt(EXTRA_CONTEXT_NUMBER, 5);

        if (subreddit.equals(Reddit.EMPTY_STRING)) {
            new AsyncGetSubredditName().execute(name);
            TypedValue typedValue = new TypedValue();
            getTheme().resolveAttribute(R.attr.activity_background, typedValue, true);
            int color = typedValue.data;
            findViewById(R.id.content_view).setBackgroundColor(color);
        } else {
            setupAdapter();
        }
        if (Authentication.isLoggedIn && Authentication.me == null) {

            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    if (Authentication.reddit == null) {
                        new Authentication(getApplicationContext());
                    } else {
                        try {
                            Authentication.me = Authentication.reddit.me();
                            Authentication.mod = Authentication.me.isMod();

                            Authentication.authentication
                                    .edit()
                                    .putBoolean(Reddit.SHARED_PREF_IS_MOD, Authentication.mod)
                                    .apply();

                            if (Reddit.notificationTime != -1) {
                                Reddit.notifications =
                                        new NotificationJobScheduler(CommentsScreenSingle.this);
                                Reddit.notifications.start();
                            }

                            if (Reddit.cachedData.contains("toCache")) {
                                Reddit.autoCache =
                                        new AutoCacheScheduler(CommentsScreenSingle.this);
                                Reddit.autoCache.start();
                            }

                            final String name = Authentication.me.getFullName();
                            Authentication.name = name;
                            LogUtil.v("AUTHENTICATED");
                            UserSubscriptions.doCachedModSubs();

                            if (Authentication.reddit.isAuthenticated()) {
                                final Set<String> accounts =
                                        Authentication.authentication.getStringSet(
                                                "accounts", new HashSet<String>());
                                if (accounts.contains(name)) { // convert to new system
                                    accounts.remove(name);
                                    accounts.add(name + ":" + Authentication.refresh);
                                    Authentication.authentication
                                            .edit()
                                            .putStringSet("accounts", accounts)
                                            .apply(); // force commit
                                }
                                Authentication.isLoggedIn = true;
                                Reddit.notFirst = true;
                            }
                        } catch (Exception e) {
                            new Authentication(getApplicationContext());
                        }
                    }
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private class CommonPageChangeListener extends ViewPager.SimpleOnPageChangeListener {
        @Override
        public void onPageScrollStateChanged(int state) {
            if (!doneTranslucent) {
                doneTranslucent = true;
                Utils.convertActivityToTranslucent(CommentsScreenSingle.this);
            }
        }
    }

    private void setupAdapter() {
        themeSystemBars(subreddit);
        setRecentBar(subreddit);

        pager = (ViewPager) findViewById(R.id.content_view);
        comments = new CommentsScreenSinglePagerAdapter(getSupportFragmentManager());
        pager.setAdapter(comments);
        pager.setCurrentItem(1);

        if (SettingValues.oldSwipeMode) {
            MiscUtil.setupOldSwipeModeBackground(this, pager);

            pager.addOnPageChangeListener(new CommonPageChangeListener() {
                @Override
                public void onPageScrolled(
                        int position, float positionOffset, int positionOffsetPixels) {
                    if (position == 0 && positionOffsetPixels == 0) {
                        finish();
                    }
                    if (position == 0
                            && ((CommentsScreenSinglePagerAdapter) pager.getAdapter())
                                        .blankPage
                                != null) {
                        ((CommentsScreenSinglePagerAdapter) pager.getAdapter())
                                .blankPage.doOffset(positionOffset);
                    }
                }
            });
        } else {
            pager.addOnPageChangeListener(new CommonPageChangeListener());
        }
    }

    boolean locked;
    boolean archived;
    boolean contest;

    private class AsyncGetSubredditName extends AsyncTask<String, Void, String> {

        @Override
        protected void onPostExecute(String s) {
            subreddit = s;
            setupAdapter();
        }

        @Override
        protected String doInBackground(String... params) {
            try {
                final Submission s = Authentication.reddit.getSubmission(params[0]);
                if (SettingValues.storeHistory) {
                    if (SettingValues.storeNSFWHistory && s.isNsfw() || !s.isNsfw()) {
                        HasSeen.addSeen(s.getFullName());
                    }
                    LastComments.setComments(s);
                }
                HasSeen.setHasSeenSubmission(
                        new ArrayList<Submission>() {
                            {
                                this.add(s);
                            }
                        });
                locked = s.isLocked();
                archived = s.isArchived();
                contest = s.getDataNode().get("contest_mode").asBoolean();
                if (s.getSubredditName() == null) {
                    subreddit = "Promoted";
                } else {
                    subreddit = s.getSubredditName();
                }
                return subreddit;

            } catch (Exception e) {
                try {
                    runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    new AlertDialog.Builder(CommentsScreenSingle.this)
                                            .setTitle(R.string.submission_not_found)
                                            .setMessage(R.string.submission_not_found_msg)
                                            .setPositiveButton(
                                                    R.string.btn_ok, (dialog, which) -> finish())
                                            .setOnDismissListener(dialog -> finish())
                                            .show();
                                }
                            });
                } catch (Exception ignored) {

                }
                return null;
            }
        }
    }

    private class CommentsScreenSinglePagerAdapter extends FragmentStatePagerAdapter {
        private Fragment mCurrentFragment;
        public BlankFragment blankPage;

        CommentsScreenSinglePagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        Fragment getCurrentFragment() {
            return mCurrentFragment;
        }

        @Override
        public void setPrimaryItem(
                @NonNull ViewGroup container, int position, @NonNull Object object) {
            if (mCurrentFragment != object) {
                mCurrentFragment = (Fragment) object;
            }
            super.setPrimaryItem(container, position, object);
        }

        private void processNameAndHistory(String name, String context, Bundle args) {
            if (name.contains("t3_")) name = name.substring(3);
            args.putString("id", name);
            args.putString("context", context);
            if (SettingValues.storeHistory) {
                if (context != null
                        && !context.isEmpty()
                        && !context.equals(Reddit.EMPTY_STRING)) {
                    HasSeen.addSeen("t1_" + context);
                } else {
                    HasSeen.addSeen(name);
                }
            }
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            Fragment f = new CommentPage();
            Bundle args = new Bundle();

            if (SettingValues.oldSwipeMode) {
                if (i == 0) {
                    blankPage = new BlankFragment();
                    return blankPage;
                } else {
                    processNameAndHistory(name, context, args);
                }
            } else {
                processNameAndHistory(name, context, args);
            }

            args.putBoolean("archived", archived);
            args.putBoolean("locked", locked);
            args.putBoolean("contest", contest);
            args.putInt("contextNumber", contextNumber);
            args.putString("subreddit", subreddit);
            args.putBoolean("single", getIntent().getBooleanExtra(EXTRA_LOADMORE, true));
            args.putBoolean("np", np);
            f.setArguments(args);

            return f;
        }

        @Override
        public int getCount() {
            if (SettingValues.oldSwipeMode) {
                return 2;
            } else {
                return 1;
            }
        }
    }
}
