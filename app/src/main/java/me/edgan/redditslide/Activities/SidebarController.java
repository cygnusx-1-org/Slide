package me.edgan.redditslide.Activities;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.android.material.snackbar.Snackbar;

import net.dean.jraw.ApiException;
import net.dean.jraw.http.MultiRedditUpdateRequest;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.managers.ModerationManager;
import net.dean.jraw.managers.MultiRedditManager;
import net.dean.jraw.models.FlairTemplate;
import net.dean.jraw.models.MultiReddit;
import net.dean.jraw.models.MultiSubreddit;
import net.dean.jraw.models.Subreddit;
import net.dean.jraw.models.UserRecord;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.TimePeriod;
import net.dean.jraw.paginators.UserRecordPaginator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.ImageFlairs;
import me.edgan.redditslide.Notifications.CheckForMail;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.Views.SidebarLayout;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.ui.settings.SettingsSubAdapter;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.OnSingleClickListener;
import me.edgan.redditslide.util.SortingUtil;
import me.edgan.redditslide.util.StringUtil;
import me.edgan.redditslide.util.SubmissionParser;

public class SidebarController {

    private final MainActivity mainActivity;
    private Sorting sorts;
    private TimePeriod time = TimePeriod.DAY;
    private AsyncTask<View, Void, View> currentFlair;
    private final SpoilerRobotoTextView sidebarBody; // Moved from MainActivity
    private final CommentOverflow sidebarOverflow; // Moved from MainActivity
    private AsyncGetSubredditTask mAsyncGetSubreddit = null; // Moved from MainActivity

    public SidebarController(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        // Initialize views moved from MainActivity
        this.sidebarBody = (SpoilerRobotoTextView) mainActivity.findViewById(R.id.sidebar_text);
        this.sidebarOverflow = (CommentOverflow) mainActivity.findViewById(R.id.commentOverflow);
    }

    public void doSubSidebar(final String subreddit) {
        if (this.mAsyncGetSubreddit != null) {
            this.mAsyncGetSubreddit.cancel(true);
        }
        mainActivity.findViewById(R.id.loader).setVisibility(View.VISIBLE);

        mainActivity.invalidateOptionsMenu();

        if (!subreddit.equalsIgnoreCase("all")
                && !subreddit.equalsIgnoreCase("frontpage")
                && !subreddit.equalsIgnoreCase("friends")
                && !subreddit.equalsIgnoreCase("mod")
                && !subreddit.contains("+")
                && !subreddit.contains(".")
                && !subreddit.contains("/m/")) {
            if (mainActivity.drawerLayout != null) {
                mainActivity.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END);
            }

            this.mAsyncGetSubreddit = new AsyncGetSubredditTask(this);
            this.mAsyncGetSubreddit.execute(subreddit);

            final View dialoglayout = mainActivity.findViewById(R.id.sidebarsub);
            {
                View submit = (dialoglayout.findViewById(R.id.submit));

                if (!Authentication.isLoggedIn || !Authentication.didOnline) {
                    submit.setVisibility(View.GONE);
                }
                if (SettingValues.fab && SettingValues.fabType == Constants.FAB_POST) {
                    submit.setVisibility(View.GONE);
                }

                submit.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View view) {
                            Intent inte = new Intent(mainActivity, Submit.class);
                            if (!subreddit.contains("/m/") && mainActivity.canSubmit) {
                                inte.putExtra(Submit.EXTRA_SUBREDDIT, subreddit);
                            }
                            mainActivity.startActivity(inte);
                        }
                    }
                );
            }

            dialoglayout
                .findViewById(R.id.wiki)
                .setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent i = new Intent(mainActivity, Wiki.class);
                            i.putExtra(Wiki.EXTRA_SUBREDDIT, subreddit);
                            mainActivity.startActivity(i);
                        }
                    }
                );
            dialoglayout
                .findViewById(R.id.syncflair)
                .setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            ImageFlairs.syncFlairs(mainActivity, subreddit);
                        }
                    }
                );
            dialoglayout
                .findViewById(R.id.submit)
                .setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent i = new Intent(mainActivity, Submit.class);
                            if ((!subreddit.contains("/m/") || !subreddit.contains(".")) && mainActivity.canSubmit) {
                                i.putExtra(Submit.EXTRA_SUBREDDIT, subreddit);
                            }
                            mainActivity.startActivity(i);
                        }
                    }
                );

            final TextView sort = dialoglayout.findViewById(R.id.sort);
            Sorting sortingis = Sorting.HOT;
            if (SettingValues.hasSort(subreddit)) {
                sortingis = SettingValues.getBaseSubmissionSort(subreddit);
                sort.setText(
                    sortingis.name() + (
                        (sortingis == Sorting.CONTROVERSIAL || sortingis == Sorting.TOP)
                            ? " of " + SettingValues.getBaseTimePeriod(subreddit).name()
                            : ""));
            } else {
                sort.setText("Set default sorting");
            }
            final int sortid = SortingUtil.getSortingId(sortingis);
            dialoglayout
                .findViewById(R.id.sorting)
                .setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            final DialogInterface.OnClickListener l2 =
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(
                                            DialogInterface dialogInterface, int i) {
                                        switch (i) {
                                            case 0:
                                                sorts = Sorting.HOT;
                                                break;
                                            case 1:
                                                sorts = Sorting.NEW;
                                                break;
                                            case 2:
                                                sorts = Sorting.RISING;
                                                break;
                                            case 3:
                                                sorts = Sorting.TOP;
                                                askTimePeriod(sorts, subreddit, dialoglayout);
                                                return;
                                            case 4:
                                                sorts = Sorting.CONTROVERSIAL;
                                                askTimePeriod(sorts, subreddit, dialoglayout);
                                                return;
                                        }

                                        SettingValues.setSubSorting(
                                                sorts, time, subreddit);
                                        Sorting sortingis = SettingValues.getBaseSubmissionSort(subreddit);
                                        sort.setText(
                                                sortingis.name()+ (
                                                    (sortingis == Sorting.CONTROVERSIAL || sortingis == Sorting.TOP)
                                                        ? " of "+ SettingValues.getBaseTimePeriod(subreddit).name()
                                                        : ""
                                                )
                                        );
                                        mainActivity.reloadSubs();
                                    }
                                };

                            new AlertDialog.Builder(mainActivity)
                                .setTitle(R.string.sorting_choose)
                                .setSingleChoiceItems(SortingUtil.getSortingStrings(), sortid, l2)
                                .setNegativeButton(
                                    "Reset default sorting",
                                    (dialog, which) -> {
                                        SettingValues.prefs.edit().remove("defaultSort" + subreddit.toLowerCase(Locale.ENGLISH)).apply();
                                        SettingValues.prefs.edit().remove("defaultTime" + subreddit.toLowerCase(Locale.ENGLISH)).apply();
                                        final TextView sort1 = dialoglayout.findViewById(R.id.sort);

                                        if (SettingValues.hasSort(subreddit)) {
                                            Sorting sortingis1 = SettingValues.getBaseSubmissionSort(subreddit);
                                            sort1.setText(sortingis1.name()
                                                + ((sortingis1 == Sorting.CONTROVERSIAL || sortingis1 == Sorting.TOP)
                                                ? " of " + SettingValues.getBaseTimePeriod(subreddit).name() : ""));
                                        } else {
                                            sort1.setText("Set default sorting");
                                        }

                                        mainActivity.reloadSubs();
                                    })
                                .show();
                        }
                    }
                );

            dialoglayout
                    .findViewById(R.id.theme)
                    .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                int style = new ColorPreferences(mainActivity).getThemeSubreddit(subreddit);
                                final Context contextThemeWrapper = new ContextThemeWrapper(mainActivity, style);
                                LayoutInflater localInflater = mainActivity.getLayoutInflater().cloneInContext(contextThemeWrapper);
                                final View dialoglayout = localInflater.inflate(R.layout.colorsub, null);
                                ArrayList<String> arrayList = new ArrayList<>();
                                arrayList.add(subreddit);
                                SettingsSubAdapter.showSubThemeEditor(arrayList, mainActivity, dialoglayout);
                            }
                        });
            dialoglayout
                .findViewById(R.id.mods)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                final Dialog d =
                                    new MaterialDialog.Builder(mainActivity)
                                        .title(R.string.sidebar_findingmods)
                                        .cancelable(true)
                                        .content(R.string.misc_please_wait)
                                        .progress(true, 100)
                                        .show();
                                new AsyncTask<Void, Void, Void>() {
                                    ArrayList<UserRecord> mods;

                                    @Override
                                    protected Void doInBackground(Void... params) {
                                        mods = new ArrayList<>();
                                        UserRecordPaginator paginator = new UserRecordPaginator(Authentication.reddit, subreddit, "moderators");
                                        paginator.setSorting(Sorting.HOT);
                                        paginator.setTimePeriod(TimePeriod.ALL);

                                        while (paginator.hasNext()) {
                                            mods.addAll(paginator.next());
                                        }

                                        return null;
                                    }

                                    @Override
                                    protected void onPostExecute(Void aVoid) {
                                        final ArrayList<String> names = new ArrayList<>();
                                        for (UserRecord rec : mods) {
                                            names.add(rec.getFullName());
                                        }
                                        d.dismiss();
                                        new MaterialDialog.Builder(mainActivity)
                                            .title(mainActivity.getString(R.string.sidebar_submods, subreddit))
                                            .items(names)
                                            .itemsCallback(
                                                new MaterialDialog.ListCallback() {
                                                    @Override
                                                    public void onSelection(MaterialDialog dialog, View itemView, int which, CharSequence text) {
                                                        Intent i = new Intent(mainActivity, Profile.class);
                                                        i.putExtra(Profile.EXTRA_PROFILE, names.get(which));
                                                        mainActivity.startActivity(i);
                                                    }
                                                })
                                            .positiveText(R.string.btn_message)
                                            .onPositive(
                                                new MaterialDialog.SingleButtonCallback() {
                                                    @Override
                                                    public void onClick(@NonNull MaterialDialog  dialog, @NonNull DialogAction which) {
                                                        Intent i = new Intent(mainActivity, SendMessage.class);
                                                        i.putExtra(SendMessage.EXTRA_NAME, "/r/" + subreddit);
                                                        mainActivity.startActivity(i);
                                                    }
                                                })
                                            .show();
                                    }
                                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            }
                        });

            dialoglayout.findViewById(R.id.flair).setVisibility(View.GONE);

            if (Authentication.didOnline && Authentication.isLoggedIn) {
                if (currentFlair != null) currentFlair.cancel(true);
                currentFlair =
                        new AsyncTask<View, Void, View>() {
                            List<FlairTemplate> flairs;
                            ArrayList<String> flairText;
                            String current;
                            AccountManager m;

                            @Override
                            protected View doInBackground(View... params) {
                                try {
                                    m = new AccountManager(Authentication.reddit);
                                    JsonNode node = m.getFlairChoicesRootNode(subreddit, null);
                                    flairs = m.getFlairChoices(subreddit, node);

                                    FlairTemplate currentF = m.getCurrentFlair(subreddit, node);

                                    if (currentF != null) {
                                        if (currentF.getText().isEmpty()) {
                                            current = ("[" + currentF.getCssClass() + "]");
                                        } else {
                                            current = (currentF.getText());
                                        }
                                    }

                                    flairText = new ArrayList<>();

                                    for (FlairTemplate temp : flairs) {
                                        if (temp.getText().isEmpty()) {
                                            flairText.add("[" + temp.getCssClass() + "]");
                                        } else {
                                            flairText.add(temp.getText());
                                        }
                                    }
                                } catch (Exception e1) {
                                    e1.printStackTrace();
                                }

                                return params[0];
                            }

                            @Override
                            protected void onPostExecute(View flair) {
                                if (flairs != null && !flairs.isEmpty() && flairText != null && !flairText.isEmpty()) {
                                    flair.setVisibility(View.VISIBLE);

                                    if (current != null) {
                                        ((TextView) dialoglayout.findViewById(R.id.flair_text))
                                                .setText(mainActivity.getString(R.string.sidebar_flair, current));
                                    }

                                    flair.setOnClickListener(
                                        new View.OnClickListener() {
                                            @Override
                                            public void onClick(View v) {
                                                new MaterialDialog.Builder(mainActivity)
                                                    .items(flairText)
                                                    .title(R.string.sidebar_select_flair)
                                                    .itemsCallback(
                                                        new MaterialDialog.ListCallback() {
                                                            @Override
                                                            public void onSelection(
                                                                    MaterialDialog dialog, View itemView, int which, CharSequence text) {
                                                                final FlairTemplate t = flairs.get(which);
                                                                if (t.isTextEditable()) {
                                                                    new MaterialDialog.Builder(mainActivity)
                                                                        .title(R.string.sidebar_select_flair_text)
                                                                        .input(mainActivity.getString(R.string.mod_flair_hint), t.getText(), true, (dialog1, input) -> {})
                                                                        .positiveText(R.string.btn_set)
                                                                        .onPositive(
                                                                            new MaterialDialog.SingleButtonCallback() {
                                                                                @Override
                                                                                public
                                                                                void onClick(MaterialDialog dialog, DialogAction which) {
                                                                                    final String flair = dialog.getInputEditText().getText().toString();
                                                                                    new AsyncTask<Void, Void, Boolean>() {
                                                                                        @Override
                                                                                        protected
                                                                                        Boolean doInBackground(Void... params) {
                                                                                            try {
                                                                                                new ModerationManager(Authentication.reddit)
                                                                                                    .setFlair(subreddit, t, flair, Authentication.name);
                                                                                                FlairTemplate currentF = m.getCurrentFlair(subreddit);

                                                                                                if (currentF.getText().isEmpty()) {
                                                                                                    current = ("[" + currentF.getCssClass() + "]");
                                                                                                } else {
                                                                                                    current = (currentF.getText());
                                                                                                }

                                                                                                return true;
                                                                                            } catch (Exception e) {
                                                                                                e.printStackTrace();

                                                                                                return false;
                                                                                            }
                                                                                        }

                                                                                        @Override
                                                                                        protected
                                                                                        void onPostExecute(Boolean done) {
                                                                                            Snackbar s;
                                                                                            if (done) {
                                                                                                if (current != null) {
                                                                                                    ((TextView) dialoglayout.findViewById(R.id.flair_text))
                                                                                                        .setText(mainActivity.getString(R.string.sidebar_flair, current));
                                                                                                }

                                                                                                s = Snackbar.make(
                                                                                                        mainActivity.mToolbar,R.string.snackbar_flair_success,
                                                                                                        Snackbar.LENGTH_SHORT
                                                                                                    );
                                                                                            } else {
                                                                                                s = Snackbar.make(
                                                                                                        mainActivity.mToolbar, R.string.snackbar_flair_error,
                                                                                                        Snackbar.LENGTH_SHORT
                                                                                                    );
                                                                                            }
                                                                                            if (s != null) {
                                                                                                LayoutUtils.showSnackbar(s);
                                                                                            }
                                                                                        }
                                                                                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                                                                }
                                                                            })
                                                                        .negativeText(R.string.btn_cancel)
                                                                        .show();
                                                                } else {
                                                                    new AsyncTask<Void, Void, Boolean>() {
                                                                        @Override
                                                                        protected Boolean doInBackground(Void... params) {
                                                                            try {
                                                                                new ModerationManager(Authentication.reddit).setFlair(subreddit, t, null, Authentication.name);
                                                                                FlairTemplate currentF = m.getCurrentFlair(subreddit);

                                                                                if (currentF.getText().isEmpty()) {
                                                                                    current = ("[" + currentF.getCssClass() + "]");
                                                                                } else {
                                                                                    current = (currentF.getText());
                                                                                }

                                                                                return true;
                                                                            } catch (Exception e) {
                                                                                e.printStackTrace();
                                                                                return false;
                                                                            }
                                                                        }

                                                                        @Override
                                                                        protected void onPostExecute(Boolean done) {
                                                                            Snackbar s;
                                                                            if (done) {
                                                                                if (current != null) {
                                                                                    ((TextView) dialoglayout
                                                                                        .findViewById(R.id.flair_text))
                                                                                        .setText(mainActivity.getString(R.string.sidebar_flair,current));
                                                                                }
                                                                                s = Snackbar.make(
                                                                                        mainActivity.mToolbar,
                                                                                        R.string.snackbar_flair_success,
                                                                                        Snackbar.LENGTH_SHORT
                                                                                    );
                                                                            } else {
                                                                                s = Snackbar.make(
                                                                                        mainActivity.mToolbar, R.string.snackbar_flair_error,
                                                                                        Snackbar.LENGTH_SHORT
                                                                                    );
                                                                            }

                                                                            if (s != null) {
                                                                                LayoutUtils.showSnackbar(s);
                                                                            }
                                                                        }
                                                                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                                                }
                                                            }
                                                        })
                                                    .show();
                                            }
                                        });
                                }
                            }
                        };
                currentFlair.execute((View) dialoglayout.findViewById(R.id.flair));
            }
        } else {
            if (mainActivity.drawerLayout != null) {
                mainActivity.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END);
            }
        }
    }

    private void askTimePeriod(final Sorting sort, final String sub, final View dialoglayout) {
        final DialogInterface.OnClickListener l2 =
            new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    switch (i) {
                        case 0:
                            time = TimePeriod.HOUR;
                            break;
                        case 1:
                            time = TimePeriod.DAY;
                            break;
                        case 2:
                            time = TimePeriod.WEEK;
                            break;
                        case 3:
                            time = TimePeriod.MONTH;
                            break;
                        case 4:
                            time = TimePeriod.YEAR;
                            break;
                        case 5:
                            time = TimePeriod.ALL;
                            break;
                    }
                    SettingValues.setSubSorting(sort, time, sub);
                    SortingUtil.setSorting(sub, sort);
                    SortingUtil.setTime(sub, time);
                    final TextView sortTextView = dialoglayout.findViewById(R.id.sort);
                    if (SettingValues.hasSort(sub)) {
                        Sorting sortingis = SettingValues.getBaseSubmissionSort(sub);
                        sortTextView.setText(
                                sortingis.name() + ((sortingis == Sorting.CONTROVERSIAL || sortingis == Sorting.TOP)
                                                ? " of " + SettingValues.getBaseTimePeriod(sub).name()
                                                : ""));
                    } else {
                        sortTextView.setText("Set default sorting");
                    }
                    mainActivity.reloadSubs();
                }
            };

        new AlertDialog.Builder(mainActivity)
            .setTitle(R.string.sorting_choose)
            .setSingleChoiceItems(SortingUtil.getSortingTimesStrings(), SortingUtil.getSortingTimeId(""), l2)
            .show();
    }

    public void doSubSidebarNoLoad(final String subreddit) {
        if (this.mAsyncGetSubreddit != null) {
            this.mAsyncGetSubreddit.cancel(true);
        }

        mainActivity.findViewById(R.id.loader).setVisibility(View.GONE);

        mainActivity.invalidateOptionsMenu();

        if (!subreddit.equalsIgnoreCase("all")
                && !subreddit.equalsIgnoreCase("frontpage")
                && !subreddit.equalsIgnoreCase("friends")
                && !subreddit.equalsIgnoreCase("mod")
                && !subreddit.contains("+")
                && !subreddit.contains(".")
                && !subreddit.contains("/m/")) {
            if (mainActivity.drawerLayout != null) {
                mainActivity.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END);
            }

            this.sidebarBody.setVisibility(View.GONE);
            mainActivity.findViewById(R.id.sub_title).setVisibility(View.GONE);
            mainActivity.findViewById(R.id.subscribers).setVisibility(View.GONE);
            mainActivity.findViewById(R.id.active_users).setVisibility(View.GONE);

            mainActivity.findViewById(R.id.header_sub).setBackgroundColor(Palette.getColor(subreddit));
            ((TextView) mainActivity.findViewById(R.id.sub_infotitle)).setText(subreddit);

            // Sidebar buttons should use subreddit's accent color
            int subColor = new ColorPreferences(mainActivity).getColor(subreddit);
            ((TextView) mainActivity.findViewById(R.id.theme_text)).setTextColor(subColor);
            ((TextView) mainActivity.findViewById(R.id.wiki_text)).setTextColor(subColor);
            ((TextView) mainActivity.findViewById(R.id.post_text)).setTextColor(subColor);
            ((TextView) mainActivity.findViewById(R.id.mods_text)).setTextColor(subColor);
            ((TextView) mainActivity.findViewById(R.id.flair_text)).setTextColor(subColor);
            ((TextView) mainActivity.drawerLayout.findViewById(R.id.sorting).findViewById(R.id.sort)).setTextColor(subColor);
            ((TextView) mainActivity.findViewById(R.id.sync)).setTextColor(subColor);

        } else {
            if (mainActivity.drawerLayout != null) {
                mainActivity.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END);
            }
        }
    }

    public void doSubOnlyStuff(final Subreddit subreddit) {
        mainActivity.findViewById(R.id.loader).setVisibility(View.GONE);
        if (subreddit.getSubredditType() != null) {
            mainActivity.canSubmit = !subreddit.getSubredditType().equals("RESTRICTED");
        } else {
            mainActivity.canSubmit = true;
        }
        if (subreddit.getSidebar() != null && !subreddit.getSidebar().isEmpty()) {
            mainActivity.findViewById(R.id.sidebar_text).setVisibility(View.VISIBLE);

            final String text = subreddit.getDataNode().get("description_html").asText().trim();
            setViews(text, subreddit.getDisplayName(), this.sidebarBody, this.sidebarOverflow);

            // get all subs that have Notifications enabled
            ArrayList<String> rawSubs = StringUtil.stringToArray(Reddit.appRestart.getString(CheckForMail.SUBS_TO_GET, ""));
            HashMap<String, Integer> subThresholds = new HashMap<>();
            for (String s : rawSubs) {
                try {
                    String[] split = s.split(":");
                    subThresholds.put(split[0].toLowerCase(Locale.ENGLISH), Integer.valueOf(split[1]));
                } catch (Exception ignored) {
                    // do nothing
                }
            }

            // whether or not this subreddit was in the keySet
            boolean isNotified =subThresholds.containsKey(subreddit.getDisplayName().toLowerCase(Locale.ENGLISH));
            ((AppCompatCheckBox) mainActivity.findViewById(R.id.notify_posts_state)).setChecked(isNotified);
        } else {
            this.sidebarBody.setVisibility(View.GONE);
        }
        {
            View collection = mainActivity.findViewById(R.id.collection);
            if (Authentication.isLoggedIn) {
                collection.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            new AsyncTask<Void, Void, Void>() {
                                HashMap<String, MultiReddit> multis = new HashMap<String, MultiReddit>();

                                @Override
                                protected Void doInBackground(Void... params) {
                                    if (UserSubscriptions.multireddits == null) {
                                        UserSubscriptions.syncMultiReddits(mainActivity);
                                    }

                                    for (MultiReddit r : UserSubscriptions.multireddits) {
                                        multis.put(r.getDisplayName(), r);
                                    }

                                    return null;
                                }

                                @Override
                                protected void onPostExecute(Void aVoid) {
                                    new MaterialDialog.Builder(mainActivity)
                                        .title(mainActivity.getString(R.string.multi_add_to, subreddit.getDisplayName()))
                                        .items(multis.keySet())
                                        .itemsCallback(
                                            new MaterialDialog.ListCallback() {
                                                @Override
                                                public void onSelection(MaterialDialog dialog, View itemView, final int which, CharSequence text) {
                                                    new AsyncTask<Void, Void, Void>() {
                                                        @Override
                                                        protected Void doInBackground(Void... params) {
                                                            try {
                                                                final String multiName = multis.keySet().toArray(new String [0])[which];
                                                                List<String> subs = new ArrayList<String>();

                                                                for (MultiSubreddit sub : multis.get(multiName).getSubreddits()) {
                                                                    subs.add(sub.getDisplayName());
                                                                }

                                                                subs.add(subreddit.getDisplayName());
                                                                new MultiRedditManager(Authentication.reddit)
                                                                    .createOrUpdate(new MultiRedditUpdateRequest
                                                                        .Builder(Authentication.name,multiName)
                                                                        .subreddits(subs).build()
                                                                    );

                                                                UserSubscriptions.syncMultiReddits(mainActivity);

                                                                mainActivity.runOnUiThread(
                                                                    new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            mainActivity.drawerLayout.closeDrawers();

                                                                            Snackbar s = Snackbar.make(
                                                                                mainActivity.mToolbar,
                                                                                mainActivity.getString(R.string.multi_subreddit_added,multiName),
                                                                                Snackbar.LENGTH_LONG
                                                                            );

                                                                            LayoutUtils.showSnackbar(s);
                                                                        }
                                                                    });
                                                            } catch (final
                                                                    NetworkException
                                                                    | ApiException e) {
                                                                mainActivity.runOnUiThread(
                                                                    new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            mainActivity.runOnUiThread(
                                                                                new Runnable() {
                                                                                    @Override
                                                                                    public void run() {
                                                                                        Snackbar.make(
                                                                                            mainActivity.mToolbar,
                                                                                            mainActivity.getString(R.string.multi_error),
                                                                                            Snackbar.LENGTH_LONG
                                                                                        )
                                                                                        .setAction(R.string.btn_ok, null)
                                                                                        .show();
                                                                                    }
                                                                                });
                                                                        }
                                                                    });
                                                                e.printStackTrace();
                                                            }
                                                            return null;
                                                        }
                                                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                                }
                                            }
                                        )
                                        .show();
                                }
                            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        }
                    });
            } else {
                collection.setVisibility(View.GONE);
            }
        }
        {
            final AppCompatCheckBox notifyStateCheckBox =
                    (AppCompatCheckBox) mainActivity.findViewById(R.id.notify_posts_state);
            assert notifyStateCheckBox != null;

            notifyStateCheckBox.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (isChecked) {
                                final String sub = subreddit.getDisplayName();

                                if (!sub.equalsIgnoreCase("all")
                                        && !sub.equalsIgnoreCase("frontpage")
                                        && !sub.equalsIgnoreCase("friends")
                                        && !sub.equalsIgnoreCase("mod")
                                        && !sub.contains("+")
                                        && !sub.contains(".")
                                        && !sub.contains("/m/")) {
                                    new AlertDialog.Builder(mainActivity)
                                        .setTitle(mainActivity.getString(R.string.sub_post_notifs_title, sub))
                                        .setMessage(R.string.sub_post_notifs_msg)
                                        .setPositiveButton(
                                            R.string.btn_ok,
                                            (dialog, which) -> {
                                                final int[] selectedThreshold = {0}; // Default to index 0 ("1")
                                                new MaterialDialog.Builder(mainActivity)
                                                    .title(R.string.sub_post_notifs_threshold)
                                                    .items(
                                                        new String[] {
                                                            "1", "5", "10",
                                                            "20", "40", "50"
                                                        }
                                                    )
                                                    .alwaysCallSingleChoiceCallback()
                                                    .itemsCallbackSingleChoice(
                                                        0,
                                                        new MaterialDialog
                                                                .ListCallbackSingleChoice() {
                                                            @Override
                                                            public boolean onSelection(MaterialDialog dialog, View itemView, int which, CharSequence text) {
                                                                selectedThreshold[0] = which;
                                                                return true;
                                                            }
                                                        }
                                                    )
                                                    .positiveText(R.string.btn_ok)
                                                    .negativeText(R.string.btn_cancel)
                                                    .onPositive((dialog1, which1) -> {
                                                        String[] thresholds = new String[] {
                                                            "1", "5", "10",
                                                            "20", "40", "50"
                                                        };
                                                        ArrayList<String> subs = StringUtil.stringToArray(
                                                            Reddit.appRestart.getString(CheckForMail.SUBS_TO_GET, "")
                                                        );
                                                        subs.add(sub + ":" + thresholds[selectedThreshold[0]]);
                                                        Reddit.appRestart.edit().putString(
                                                            CheckForMail.SUBS_TO_GET,
                                                            StringUtil.arrayToString(subs)
                                                        ).commit();
                                                    })
                                                    .onNegative((dialog1, which1) -> {
                                                        notifyStateCheckBox.setChecked(false);
                                                    })
                                                    .cancelable(true)
                                                    .show();
                                            })
                                        .setNegativeButton(R.string.btn_cancel, null)
                                        .setNegativeButton(
                                            R.string.btn_cancel,
                                            (dialog, which) -> notifyStateCheckBox.setChecked(false)
                                        )
                                        .setOnCancelListener(dialog -> notifyStateCheckBox.setChecked(false))
                                        .show();
                                } else {
                                    notifyStateCheckBox.setChecked(false);
                                    Toast.makeText(mainActivity, R.string.sub_post_notifs_err, Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                Intent cancelIntent = new Intent(mainActivity, CancelSubNotifs.class);
                                cancelIntent.putExtra(CancelSubNotifs.EXTRA_SUB, subreddit.getDisplayName());
                                mainActivity.startActivity(cancelIntent);
                            }
                        }
                    });
        }
        {
            final TextView subscribe = (TextView) mainActivity.findViewById(R.id.subscribe);
            mainActivity.sidebarActions.currentlySubbed =
                (!Authentication.isLoggedIn && mainActivity.usedArray.contains(subreddit.getDisplayName().toLowerCase(Locale.ENGLISH)))
                || subreddit.isUserSubscriber();

            MiscUtil.doSubscribeButtonText(mainActivity.sidebarActions.currentlySubbed, subscribe);

            assert subscribe != null;
            subscribe.setOnClickListener(
                new View.OnClickListener() {
                    private void doSubscribe() {
                        if (Authentication.isLoggedIn) {
                            new AlertDialog.Builder(mainActivity)
                                .setTitle(mainActivity.getString(R.string.subscribe_to, subreddit.getDisplayName()))
                                .setPositiveButton(
                                    R.string.reorder_add_subscribe,
                                    (dialog, which) -> new AsyncTask<Void, Void, Boolean>() {
                                        @Override
                                        public void onPostExecute(
                                                Boolean success) {
                                            if (!success) { // If subreddit was removed from account or not
                                                new AlertDialog.Builder(mainActivity)
                                                    .setTitle(R.string.force_change_subscription)
                                                    .setMessage(R.string.force_change_subscription_desc)
                                                    .setPositiveButton(
                                                        R.string.btn_yes,
                                                        (dialog1, which1) -> {
                                                            mainActivity.sidebarActions.changeSubscription( subreddit, true); // Force add the subscription
                                                            Snackbar s = Snackbar.make(
                                                                mainActivity.mToolbar, mainActivity.getString(R.string.misc_subscribed),
                                                                Snackbar.LENGTH_LONG
                                                            );
                                                            LayoutUtils.showSnackbar(s);
                                                        })
                                                    .setNegativeButton(R.string.btn_no, null)
                                                    .setCancelable(false)
                                                    .show();
                                            } else {
                                                mainActivity.sidebarActions.changeSubscription(
                                                        subreddit, true);
                                            }
                                        }

                                        @Override
                                        protected Boolean doInBackground(
                                                Void... params) {
                                            try {
                                                new AccountManager(Authentication.reddit).subscribe(subreddit);
                                            } catch (NetworkException e) {
                                                return false; // Either network crashed or trying to unsubscribe to a subreddit that the account isn't subscribed to
                                            }
                                            return true;
                                        }
                                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR))
                                .setNeutralButton(
                                        R.string.btn_add_to_sublist,
                                        (dialog, which) -> {
                                            mainActivity.sidebarActions.changeSubscription(subreddit, true); // Force add the subscription
                                            Snackbar s = Snackbar.make(mainActivity.mToolbar, R.string.sub_added, Snackbar.LENGTH_LONG);
                                            LayoutUtils.showSnackbar(s);
                                        })
                                .setNegativeButton(R.string.btn_cancel, null)
                                .show();
                        } else {
                            mainActivity.sidebarActions.changeSubscription(subreddit, true);
                        }
                    }

                    private void doUnsubscribe() {
                        if (Authentication.didOnline) {
                            new AlertDialog.Builder(mainActivity)
                                .setTitle(mainActivity.getString(R.string.unsubscribe_from, subreddit.getDisplayName()))
                                .setPositiveButton(
                                    R.string.reorder_remove_unsubscribe,
                                    (dialog, which) -> new AsyncTask<Void, Void, Boolean>() {
                                        @Override
                                        public void onPostExecute(Boolean success) {
                                            if (!success) { // If subreddit was remove from account or not
                                                new AlertDialog.Builder(mainActivity)
                                                    .setTitle(R.string.force_change_subscription)
                                                    .setMessage(R.string.force_change_subscription_desc)
                                                    .setPositiveButton(R.string.btn_yes,
                                                        (dialog12, which12) -> {
                                                            mainActivity.sidebarActions.changeSubscription(subreddit, false); // Force add the subscription
                                                            Snackbar s = Snackbar.make(
                                                                mainActivity.mToolbar,
                                                                mainActivity.getString(R.string.misc_unsubscribed),
                                                                Snackbar.LENGTH_LONG
                                                            );
                                                            LayoutUtils.showSnackbar(s);
                                                        }
                                                    )
                                                    .setNegativeButton(R.string.btn_no, null)
                                                    .setCancelable(false)
                                                    .show();
                                            } else {
                                                mainActivity.sidebarActions.changeSubscription(subreddit, false);
                                            }
                                        }

                                        @Override
                                        protected Boolean doInBackground(
                                                Void... params) {
                                            try {
                                                new AccountManager(Authentication.reddit).unsubscribe(subreddit);
                                            } catch (NetworkException e) {
                                                return false; // Either network crashed or trying to unsubscribe to a subreddit that the account isn't subscribed to
                                            }
                                            return true;
                                        }
                                    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR))
                                .setNeutralButton(
                                    R.string.just_unsub,
                                    (dialog, which) -> { mainActivity.sidebarActions.changeSubscription(subreddit, false); // Force add the subscription
                                        Snackbar s = Snackbar.make(mainActivity.mToolbar, R.string.misc_unsubscribed, Snackbar.LENGTH_LONG);
                                        LayoutUtils.showSnackbar(s);
                                    }
                                )
                                .setNegativeButton(R.string.btn_cancel, null)
                                .show();
                        } else {
                            mainActivity.sidebarActions.changeSubscription(subreddit, false);
                        }
                    }

                    @Override
                    public void onClick(View v) {
                        if (!mainActivity.sidebarActions.currentlySubbed) {
                            doSubscribe();
                        } else {
                            doUnsubscribe();
                        }
                        MiscUtil.doSubscribeButtonText(mainActivity.sidebarActions.currentlySubbed, subscribe);
                    }
                });
        }
        if (!subreddit.getPublicDescription().isEmpty()) {
            mainActivity.findViewById(R.id.sub_title).setVisibility(View.VISIBLE);
            setViews(
                subreddit.getDataNode().get("public_description_html").asText(),
                subreddit.getDisplayName().toLowerCase(Locale.ENGLISH),
                ((SpoilerRobotoTextView) mainActivity.findViewById(R.id.sub_title)), // Keep using findViewById for views not moved
                (CommentOverflow) mainActivity.findViewById(R.id.sub_title_overflow) // Keep using findViewById for views not moved
            );
        } else {
            mainActivity.findViewById(R.id.sub_title).setVisibility(View.GONE);
        }
        ((ImageView) mainActivity.findViewById(R.id.subimage)).setImageResource(0);
        if (subreddit.getDataNode().has("icon_img") && !subreddit.getDataNode().get("icon_img").asText().isEmpty()) {
            mainActivity.findViewById(R.id.subimage).setVisibility(View.VISIBLE);
            ((Reddit) mainActivity.getApplication())
                .getImageLoader()
                .displayImage(
                    subreddit.getDataNode().get("icon_img").asText(),
                    (ImageView) mainActivity.findViewById(R.id.subimage)
                );
        } else {
            mainActivity.findViewById(R.id.subimage).setVisibility(View.GONE);
        }
        String bannerImage = subreddit.getBannerImage();
        if (bannerImage != null && !bannerImage.isEmpty()) {
            mainActivity.findViewById(R.id.sub_banner).setVisibility(View.VISIBLE);
            ((Reddit) mainActivity.getApplication())
                .getImageLoader()
                .displayImage(bannerImage, (ImageView) mainActivity.findViewById(R.id.sub_banner));
        } else {
            mainActivity.findViewById(R.id.sub_banner).setVisibility(View.GONE);
        }
        ((TextView) mainActivity.findViewById(R.id.subscribers))
            .setText(mainActivity.getString(R.string.subreddit_subscribers_string,subreddit.getLocalizedSubscriberCount()));
        mainActivity.findViewById(R.id.subscribers).setVisibility(View.VISIBLE);

        ((TextView) mainActivity.findViewById(R.id.active_users))
            .setText(mainActivity.getString(R.string.subreddit_active_users_string_new, subreddit.getLocalizedAccountsActive()));
        mainActivity.findViewById(R.id.active_users).setVisibility(View.VISIBLE);
    }

    private void setViews(
            String rawHTML,
            String subredditName,
            SpoilerRobotoTextView firstTextView,
            CommentOverflow commentOverflow) {
        if (rawHTML.isEmpty()) {
            return;
        }

        List<String> blocks = SubmissionParser.getBlocks(rawHTML);

        int startIndex = 0;
        // the <div class="md"> case is when the body contains a table or code block first
        if (!blocks.get(0).equals("<div class=\"md\">")) {
            firstTextView.setVisibility(View.VISIBLE);
            firstTextView.setTextHtml(blocks.get(0), subredditName);
            firstTextView.setLinkTextColor(new ColorPreferences(mainActivity).getColor(subredditName));
            startIndex = 1;
        } else {
            firstTextView.setText("");
            firstTextView.setVisibility(View.GONE);
        }

        if (blocks.size() > 1) {
            if (startIndex == 0) {
                commentOverflow.setViews(blocks, subredditName);
            } else {
                commentOverflow.setViews(blocks.subList(startIndex, blocks.size()), subredditName);
            }
            SidebarLayout sidebar = (SidebarLayout) mainActivity.findViewById(R.id.drawer_layout);
            for (int i = 0; i < commentOverflow.getChildCount(); i++) {
                View maybeScrollable = commentOverflow.getChildAt(i);
                if (maybeScrollable instanceof HorizontalScrollView) {
                    sidebar.addScrollable(maybeScrollable);
                }

            }
        } else {
            commentOverflow.removeAllViews();
        }
    }

    /**
     * Helper method to check if the associated MainActivity is still valid.
     * Used by AsyncGetSubredditTask to avoid crashes if the activity is destroyed.
     * @return true if the activity is not null and not destroyed, false otherwise.
     */
    public boolean isActivityValid() {
        return mainActivity != null && !mainActivity.isDestroyed();
    }

    /**
     * Cancels the currently running AsyncGetSubredditTask, if any.
     */
    public void cancelAsyncGetSubredditTask() {
        if (mAsyncGetSubreddit != null) {
            mAsyncGetSubreddit.cancel(true);
        }
    }
}