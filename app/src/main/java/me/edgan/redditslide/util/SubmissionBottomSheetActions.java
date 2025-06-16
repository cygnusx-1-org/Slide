package me.edgan.redditslide.util;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.cocosw.bottomsheet.BottomSheet;
import com.google.android.material.snackbar.Snackbar;

import net.dean.jraw.ApiException;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Ruleset;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.SubredditRule;

import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import me.edgan.redditslide.ActionStates;
import me.edgan.redditslide.Activities.PostReadLater;
import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Activities.SubredditView;
import me.edgan.redditslide.Adapters.SubmissionViewHolder;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.CommentCacheAsync;
import me.edgan.redditslide.Hidden;
import me.edgan.redditslide.OfflineSubreddit;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SubmissionViews.ReadLater;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.Palette;

/**
 * Handles Bottom Sheet actions for Submission views.
 */
public class SubmissionBottomSheetActions {

    public static String reason;
    public static boolean[] chosen = new boolean[] {false, false, false, false, false};
    public static boolean[] oldChosen = new boolean[] {false, false, false, false, false};


    public static <T extends Contribution> void showBottomSheet(
            final Activity mContext,
            final Submission submission,
            final SubmissionViewHolder holder,
            final List<T> posts,
            final String baseSub,
            final RecyclerView recyclerview,
            final boolean full) {

        int[] attrs = new int[] {R.attr.tintColor};
        TypedArray ta = mContext.obtainStyledAttributes(attrs);

        int color = ta.getColor(0, Color.WHITE);
        Drawable profile = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_account_circle, null);
        final Drawable sub = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_bookmark_border, null);
        Drawable saved = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_star, null);
        Drawable hide = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_visibility_off, null);
        final Drawable report = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_report, null);
        Drawable copy = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_content_copy, null);
        final Drawable readLater = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_download, null);
        Drawable open = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_open_in_browser, null);
        Drawable link = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_link, null);
        Drawable reddit = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_forum, null);
        Drawable filter = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_filter_list, null);
        Drawable crosspost = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_forward, null);

        final List<Drawable> drawableSet = Arrays.asList(profile, sub, saved, hide, report, copy, open, link, reddit, readLater, filter, crosspost);
        BlendModeUtil.tintDrawablesAsSrcAtop(drawableSet, color);

        ta.recycle();

        final BottomSheet.Builder b = new BottomSheet.Builder(mContext).title(CompatUtil.fromHtml(submission.getTitle()));

        final boolean isReadLater = mContext instanceof PostReadLater;
        final boolean isAddedToReadLaterList = ReadLater.isToBeReadLater(submission);
        if (Authentication.didOnline) {
            b.sheet(1, profile, "/u/" + submission.getAuthor()).sheet(2, sub, "/r/" + submission.getSubredditName());
            String save = mContext.getString(R.string.btn_save);
            if (ActionStates.isSaved(submission)) {
                save = mContext.getString(R.string.comment_unsave);
            }
            if (Authentication.isLoggedIn) {
                b.sheet(3, saved, save);
            }
        }

        if (isAddedToReadLaterList) {
            CharSequence markAsReadCs = mContext.getString(R.string.mark_as_read);
            b.sheet(28, readLater, markAsReadCs);
        } else {
            CharSequence readLaterCs = mContext.getString(R.string.read_later);
            b.sheet(28, readLater, readLaterCs);
        }

        if (Authentication.didOnline) {
            if (Authentication.isLoggedIn) {
                b.sheet(12, report, mContext.getString(R.string.btn_report));
                b.sheet(13, crosspost, mContext.getString(R.string.btn_crosspost));
            }
        }

        if (submission.getSelftext() != null && !submission.getSelftext().isEmpty() && full) {
            b.sheet(25, copy, mContext.getString(R.string.submission_copy_text));
        }

        boolean hidden = submission.isHidden();
        if (!full && Authentication.didOnline) {
            if (!hidden) {
                b.sheet(5, hide, mContext.getString(R.string.submission_hide));
            } else {
                b.sheet(5, hide, mContext.getString(R.string.submission_unhide));
            }
        }
        b.sheet(7, open, mContext.getString(R.string.open_externally));
        b.sheet(4, link, mContext.getString(R.string.submission_share_permalink)).sheet(8, reddit, mContext.getString(R.string.submission_share_reddit_url));
        if ((mContext instanceof me.edgan.redditslide.Activities.MainActivity) || (mContext instanceof SubredditView)) {
            b.sheet(10, filter, mContext.getString(R.string.filter_content));
        }

        b.listener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 1:
                        {
                            Intent i = new Intent(mContext, Profile.class);
                            i.putExtra(Profile.EXTRA_PROFILE, submission.getAuthor());
                            mContext.startActivity(i);
                        }

                        break;
                    case 2:
                        {
                            Intent i = new Intent(mContext, SubredditView.class);
                            i.putExtra(SubredditView.EXTRA_SUBREDDIT, submission.getSubredditName());
                            mContext.startActivityForResult(i, 14);
                        }

                        break;
                    case 10:
                        String[] choices;
                        final String flair = submission.getSubmissionFlair().getText() != null ? submission.getSubmissionFlair().getText() : "";

                        if (flair.isEmpty()) {
                            choices = new String[] {
                                mContext.getString(R.string.filter_posts_sub, submission.getSubredditName()),
                                mContext.getString(R.string.filter_posts_user, submission.getAuthor()),
                                mContext.getString(R.string.filter_posts_urls, submission.getDomain()),
                                mContext.getString(R.string.filter_open_externally, submission.getDomain())
                            };

                            chosen = new boolean[] {
                                SettingValues.subredditFilters.contains(submission.getSubredditName().toLowerCase(Locale.ENGLISH)),
                                SettingValues.userFilters.contains(submission.getAuthor().toLowerCase(Locale.ENGLISH)),
                                SettingValues.domainFilters.contains(submission.getDomain().toLowerCase(Locale.ENGLISH)),
                                SettingValues.alwaysExternal.contains(submission.getDomain().toLowerCase(Locale.ENGLISH)),
                                false // Placeholder for flair filter
                            };

                            oldChosen = chosen.clone();
                        } else {
                            choices = new String[] {
                                mContext.getString(R.string.filter_posts_sub, submission.getSubredditName()),
                                mContext.getString(R.string.filter_posts_user, submission.getAuthor()),
                                mContext.getString(R.string.filter_posts_urls, submission.getDomain()),
                                mContext.getString(R.string.filter_open_externally, submission.getDomain()),
                                mContext.getString(R.string.filter_posts_flair, flair, baseSub)
                            };

                            chosen = new boolean[] {
                                SettingValues.subredditFilters.contains(submission.getSubredditName().toLowerCase(Locale.ENGLISH)),
                                SettingValues.userFilters.contains(submission.getAuthor().toLowerCase(Locale.ENGLISH)),
                                SettingValues.domainFilters.contains(submission.getDomain().toLowerCase(Locale.ENGLISH)),
                                SettingValues.alwaysExternal.contains(submission.getDomain().toLowerCase(Locale.ENGLISH)),
                                SettingValues.flairFilters.contains(baseSub + ":" + flair.toLowerCase(Locale.ENGLISH).trim())
                            };

                            oldChosen = chosen.clone();
                        }


                        new AlertDialog.Builder(mContext)
                            .setTitle(R.string.filter_title)
                            .setMultiChoiceItems(choices, chosen, (dialog1, which1, isChecked) -> chosen[which1] = isChecked)
                            .setPositiveButton(R.string.filter_btn, (dialog12, which12) -> {
                                boolean filtered = false;
                                SharedPreferences.Editor e = SettingValues.prefs.edit();
                                if (chosen[0] && chosen[0] != oldChosen[0]) {
                                    SettingValues.subredditFilters.add(submission.getSubredditName().toLowerCase(Locale.ENGLISH).trim());
                                    filtered = true;
                                    e.putStringSet(SettingValues.PREF_SUBREDDIT_FILTERS, SettingValues.subredditFilters);
                                } else if (!chosen[0] && chosen[0] != oldChosen[0]) {
                                    SettingValues.subredditFilters.remove(submission.getSubredditName().toLowerCase(Locale.ENGLISH).trim());
                                    filtered = false;
                                    e.putStringSet(SettingValues.PREF_SUBREDDIT_FILTERS, SettingValues.subredditFilters);
                                    e.apply();
                                }

                                if (chosen[1] && chosen[1] != oldChosen[1]) {
                                    SettingValues.userFilters.add(submission.getAuthor().toLowerCase(Locale.ENGLISH).trim());
                                    filtered = true;
                                    e.putStringSet(SettingValues.PREF_USER_FILTERS, SettingValues.userFilters);
                                } else if (!chosen[1] && chosen[1] != oldChosen[1]) {
                                    SettingValues.userFilters.remove(submission.getAuthor().toLowerCase(Locale.ENGLISH).trim());
                                    filtered = false;
                                    e.putStringSet(SettingValues.PREF_USER_FILTERS, SettingValues.userFilters);
                                    e.apply();
                                }

                                if (chosen[2] && chosen[2] != oldChosen[2]) {
                                    SettingValues.domainFilters.add(submission.getDomain().toLowerCase(Locale.ENGLISH).trim());
                                    filtered = true;
                                    e.putStringSet(SettingValues.PREF_DOMAIN_FILTERS, SettingValues.domainFilters);
                                } else if (!chosen[2] && chosen[2] != oldChosen[2]) {
                                    SettingValues.domainFilters.remove(submission.getDomain().toLowerCase(Locale.ENGLISH).trim());
                                    filtered = false;
                                    e.putStringSet(SettingValues.PREF_DOMAIN_FILTERS, SettingValues.domainFilters);
                                    e.apply();
                                }

                                if (chosen[3] && chosen[3] != oldChosen[3]) {
                                    SettingValues.alwaysExternal.add(submission.getDomain().toLowerCase(Locale.ENGLISH).trim());
                                    e.putStringSet(SettingValues.PREF_ALWAYS_EXTERNAL, SettingValues.alwaysExternal);
                                    e.apply();
                                } else if (!chosen[3] && chosen[3] != oldChosen[3]) {
                                    SettingValues.alwaysExternal.remove(submission.getDomain().toLowerCase(Locale.ENGLISH).trim());
                                    e.putStringSet(SettingValues.PREF_ALWAYS_EXTERNAL, SettingValues.alwaysExternal);
                                    e.apply();
                                }

                                if (chosen.length > 4 && !flair.isEmpty()) {
                                    String s = (baseSub + ":" + flair).toLowerCase(Locale.ENGLISH).trim();

                                    if (chosen[4] && chosen[4] != oldChosen[4]) {
                                        SettingValues.flairFilters.add(s);
                                        e.putStringSet(SettingValues.PREF_FLAIR_FILTERS, SettingValues.flairFilters);
                                        e.apply();
                                        filtered = true;
                                    } else if (!chosen[4] && chosen[4] != oldChosen[4]) {
                                        SettingValues.flairFilters.remove(s);
                                        e.putStringSet(SettingValues.PREF_FLAIR_FILTERS, SettingValues.flairFilters);
                                        e.apply();
                                    }
                                }

                                if (filtered) {
                                    e.apply();
                                    ArrayList<Contribution> toRemove = new ArrayList<>();

                                    for (Contribution s : posts) {
                                        if (s instanceof Submission && PostMatch.doesMatch((Submission) s)) {
                                            toRemove.add(s);
                                        }
                                    }

                                    OfflineSubreddit s = OfflineSubreddit.getSubreddit(baseSub, false, mContext);

                                    for (Contribution remove : toRemove) {
                                        final int pos = posts.indexOf(remove);
                                        posts.remove(pos);
                                        if (baseSub != null) {
                                            s.hideMulti(pos);
                                        }
                                    }

                                    s.writeToMemoryNoStorage();
                                    recyclerview.getAdapter().notifyDataSetChanged();
                                }
                            }).setNegativeButton(R.string.btn_cancel, null).show();

                        break;
                    case 3:
                        saveSubmission(submission, mContext, holder, full);

                        break;
                    case 5:
                        hideSubmission(submission, posts, baseSub, recyclerview, mContext);

                        break;
                    case 7:
                        LinkUtil.openExternally(submission.getUrl());

                        if (submission.isNsfw() && !SettingValues.storeNSFWHistory) {
                            // Do nothing if the post is NSFW and storeNSFWHistory is not enabled
                        } else if (SettingValues.storeHistory) {
                            me.edgan.redditslide.HasSeen.addSeen(submission.getFullName());
                        }

                        break;
                    case 13:
                        LinkUtil.crosspost(submission, mContext);

                        break;
                    case 28:
                        if (!isAddedToReadLaterList) {
                            ReadLater.setReadLater(submission, true);
                            Snackbar s = Snackbar.make(holder.itemView, "Added to read later!", Snackbar.LENGTH_SHORT);
                            View view = s.getView();
                            TextView tv = view.findViewById(com.google.android.material.R.id.snackbar_text);
                            tv.setTextColor(Color.WHITE);
                            s.setAction(
                                    R.string.btn_undo,
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            ReadLater.setReadLater(submission, false);
                                            Snackbar s2 = Snackbar.make(holder.itemView, "Removed from read later", Snackbar.LENGTH_SHORT);
                                            LayoutUtils.showSnackbar(s2);
                                        }
                                    }
                            );

                            if (NetworkUtil.isConnected(mContext)) {
                                new CommentCacheAsync(
                                    Collections.singletonList(submission),
                                    mContext,
                                    CommentCacheAsync.SAVED_SUBMISSIONS,
                                    new boolean[] {true, true}
                                ).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            }

                            s.show();
                        } else {
                            ReadLater.setReadLater(submission, false);
                            if (isReadLater || !Authentication.didOnline) {
                                final int pos = posts.indexOf(submission);
                                posts.remove(submission);

                                recyclerview.getAdapter().notifyItemRemoved(holder.getBindingAdapterPosition());

                                Snackbar s2 = Snackbar.make(holder.itemView, "Removed from read later", Snackbar.LENGTH_SHORT);
                                View view2 = s2.getView();
                                TextView tv2 = view2.findViewById(com.google.android.material.R.id.snackbar_text);
                                tv2.setTextColor(Color.WHITE);
                                s2.setAction(
                                    R.string.btn_undo,
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            posts.add(pos, (T) submission);
                                            recyclerview.getAdapter().notifyDataSetChanged();
                                        }
                                    }
                                );
                            } else {
                                Snackbar s2 = Snackbar.make(holder.itemView, "Removed from read later", Snackbar.LENGTH_SHORT);
                                View view2 = s2.getView();
                                TextView tv2 = view2.findViewById(com.google.android.material.R.id.snackbar_text);
                                s2.show();
                            }
                            OfflineSubreddit.newSubreddit(CommentCacheAsync.SAVED_SUBMISSIONS).deleteFromMemory(submission.getFullName());
                        }

                        break;
                    case 4:
                        Reddit.defaultShareText(CompatUtil.fromHtml(submission.getTitle()).toString(), StringEscapeUtils.escapeHtml4(submission.getUrl()), mContext);

                        break;
                    case 12:
                        final MaterialDialog reportDialog =
                            new MaterialDialog.Builder(mContext)
                                .customView(R.layout.report_dialog, true)
                                .title(R.string.report_post)
                                .positiveText(R.string.btn_report)
                                .negativeText(R.string.btn_cancel)
                                .onPositive(
                                    new MaterialDialog.SingleButtonCallback() {
                                        @Override
                                        public void onClick(MaterialDialog dialog, DialogAction which) {
                                            RadioGroup reasonGroup = dialog.getCustomView().findViewById(R.id.report_reasons);
                                            String reportReason;
                                            if (reasonGroup.getCheckedRadioButtonId() == R.id.report_other) {
                                                reportReason = ((EditText) dialog.getCustomView().findViewById(R.id.input_report_reason)).getText().toString();
                                            } else {
                                                reportReason = ((RadioButton) reasonGroup.findViewById(reasonGroup.getCheckedRadioButtonId())).getText().toString();
                                            }

                                            new AsyncReportTask(submission, holder.itemView).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, reportReason);
                                        }
                                    }
                                ).build();

                        final RadioGroup reasonGroup = reportDialog.getCustomView().findViewById(R.id.report_reasons);

                        reasonGroup.setOnCheckedChangeListener(
                            new RadioGroup.OnCheckedChangeListener() {
                                @Override
                                public void onCheckedChanged(RadioGroup group, int checkedId) {
                                    if (checkedId == R.id.report_other) {
                                        reportDialog.getCustomView().findViewById(R.id.input_report_reason).setVisibility(View.VISIBLE);
                                    } else {
                                        reportDialog.getCustomView().findViewById(R.id.input_report_reason).setVisibility(View.GONE);
                                    }
                                }
                            }
                        );

                        // Load sub's report reasons and show the appropriate ones
                        new AsyncTask<Void, Void, Ruleset>() {
                            @Override
                            protected Ruleset doInBackground(Void... voids) {
                                return Authentication.reddit.getRules(
                                        submission.getSubredditName());
                            }

                            @Override
                            protected void onPostExecute(Ruleset rules) {
                                reportDialog.getCustomView().findViewById(R.id.report_loading).setVisibility(View.GONE);
                                if (rules.getSubredditRules().size() > 0) {
                                    TextView subHeader = new TextView(mContext);
                                    subHeader.setText(mContext.getString(R.string.report_sub_rules, submission.getSubredditName()));
                                    reasonGroup.addView(subHeader, reasonGroup.getChildCount() - 2);
                                }

                                for (SubredditRule rule : rules.getSubredditRules()) {
                                    if (rule.getKind() == SubredditRule.RuleKind.LINK || rule.getKind() == SubredditRule.RuleKind.ALL) {
                                        RadioButton btn = new RadioButton(mContext);
                                        btn.setText(rule.getViolationReason());
                                        reasonGroup.addView(btn, reasonGroup.getChildCount() - 2);
                                        btn.getLayoutParams().width = WindowManager.LayoutParams.MATCH_PARENT;
                                    }
                                }

                                if (rules.getSiteRules().size() > 0) {
                                    TextView siteHeader = new TextView(mContext);
                                    siteHeader.setText(R.string.report_site_rules);
                                    reasonGroup.addView(siteHeader, reasonGroup.getChildCount() - 2);
                                }

                                for (String rule : rules.getSiteRules()) {
                                    RadioButton btn = new RadioButton(mContext);
                                    btn.setText(rule);
                                    reasonGroup.addView(btn, reasonGroup.getChildCount() - 2);
                                    btn.getLayoutParams().width = WindowManager.LayoutParams.MATCH_PARENT;
                                }
                            }
                        }.execute();

                        reportDialog.show();

                        break;
                    case 8:
                        if (SettingValues.shareLongLink) {
                            Reddit.defaultShareText(submission.getTitle(), "https://reddit.com" + submission.getPermalink(), mContext);
                        } else {
                            Reddit.defaultShareText(submission.getTitle(), "https://reddit.com/comments/" + submission.getId(), mContext);
                        }

                        break;
                    case 6:
                        ClipboardUtil.copyToClipboard(mContext, "Link", submission.getUrl());
                        Toast.makeText(mContext, R.string.submission_link_copied, Toast.LENGTH_SHORT).show();

                        break;
                    case 25:
                        final TextView showText = new TextView(mContext);
                        showText.setText(StringEscapeUtils.unescapeHtml4(submission.getTitle() + "\n\n" + submission.getSelftext()));
                        showText.setTextIsSelectable(true);
                        int sixteen = DisplayUtil.dpToPxVertical(24);
                        showText.setPadding(sixteen, 0, sixteen, 0);
                        new AlertDialog.Builder(mContext)
                            .setView(showText)
                            .setTitle("Select text to copy")
                            .setCancelable(true)
                            .setPositiveButton(
                                "COPY SELECTED",
                                (dialog13, which13) -> {
                                    String selected = showText.getText().toString().substring(showText.getSelectionStart(), showText.getSelectionEnd());
                                    if (!selected.isEmpty()) {
                                        ClipboardUtil.copyToClipboard(mContext, "Selftext", selected);
                                    } else {
                                        ClipboardUtil.copyToClipboard(mContext, "Selftext", CompatUtil.fromHtml(submission.getTitle() + "\n\n" + submission.getSelftext()));
                                    }
                                    Toast.makeText(mContext, R.string.submission_comment_copied, Toast.LENGTH_SHORT).show();
                                })
                            .setNegativeButton(R.string.btn_cancel, null)
                            .setNeutralButton("COPY ALL", (dialog14, which14) -> {
                                ClipboardUtil.copyToClipboard(mContext, "Selftext", StringEscapeUtils.unescapeHtml4(submission.getTitle() + "\n\n" + submission.getSelftext()));
                                Toast.makeText(mContext, R.string.submission_text_copied, Toast.LENGTH_SHORT).show();
                            }).show();

                        break;
                }
            }
        });
        b.show();
    }

    public static void saveSubmission(final Submission submission, final Activity mContext, final SubmissionViewHolder holder, final boolean full) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    if (ActionStates.isSaved(submission)) {
                        new net.dean.jraw.managers.AccountManager(Authentication.reddit).unsave(submission);
                        ActionStates.setSaved(submission, false);
                    } else {
                        new net.dean.jraw.managers.AccountManager(Authentication.reddit).save(submission);
                        ActionStates.setSaved(submission, true);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                Snackbar s;
                try {
                    if (ActionStates.isSaved(submission)) {
                        BlendModeUtil.tintImageViewAsSrcAtop((ImageView) holder.save, ContextCompat.getColor(mContext, R.color.md_amber_500));
                        holder.save.setContentDescription(mContext.getString(R.string.btn_unsave));
                        s = Snackbar.make(holder.itemView, R.string.submission_info_saved, Snackbar.LENGTH_LONG);
                        if (Authentication.me.hasGold()) {
                            s.setAction(
                                    R.string.category_categorize,
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            categorizeSaved(submission, holder.itemView, mContext);
                                        }
                                    });
                        }

                        AnimatorUtil.setFlashAnimation(holder.itemView, holder.save, ContextCompat.getColor(mContext, R.color.md_amber_500));
                    } else {
                        s = Snackbar.make(holder.itemView, R.string.submission_info_unsaved, Snackbar.LENGTH_SHORT);
                        final int getTintColor =
                                holder.itemView.getTag(holder.itemView.getId()) != null
                                                        && holder.itemView
                                                                .getTag(holder.itemView.getId())
                                                                .equals("none")
                                                || full
                                        ? Palette.getCurrentTintColor(mContext)
                                        : Palette.getWhiteTintColor();
                        BlendModeUtil.tintImageViewAsSrcAtop((ImageView) holder.save, getTintColor);
                        holder.save.setContentDescription(mContext.getString(R.string.btn_save));
                    }
                    LayoutUtils.showSnackbar(s);
                } catch (Exception ignored) {

                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void categorizeSaved(
            final Submission submission, View itemView, final Context mContext) {
        new AsyncTask<Void, Void, List<String>>() {

            Dialog d;

            @Override
            public void onPreExecute() {
                d = new MaterialDialog.Builder(mContext).progress(true, 100).title(R.string.profile_category_loading).content(R.string.misc_please_wait).show();
            }

            @Override
            protected List<String> doInBackground(Void... params) {
                try {
                    List<String> categories = new ArrayList<String>(new net.dean.jraw.managers.AccountManager(Authentication.reddit).getSavedCategories());
                    categories.add("New category");
                    return categories;
                } catch (Exception e) {
                    e.printStackTrace();
                    return new ArrayList<String>() {
                        {
                            add("New category");
                        }
                    };
                    // sub probably has no flairs?
                }
            }

            @Override
            public void onPostExecute(final List<String> data) {
                try {
                    new MaterialDialog.Builder(mContext).items(data).title(R.string.sidebar_select_flair).itemsCallback(new MaterialDialog.ListCallback() {
                        @Override
                        public void onSelection(MaterialDialog dialog, final View itemView, int which, CharSequence text) {
                            final String t = data.get(which);
                            if (which == data.size() - 1) {
                                new MaterialDialog.Builder(mContext)
                                    .title(R.string.category_set_name)
                                    .input(mContext.getString(R.string.category_set_name_hint), null, false, (dialog1, input) -> {})
                                    .positiveText(R.string.btn_set)
                                    .onPositive(
                                        new MaterialDialog.SingleButtonCallback() {
                                            @Override
                                            public void onClick(MaterialDialog dialog, DialogAction which) {
                                                final String flair = dialog.getInputEditText().getText().toString();
                                                new AsyncTask<Void, Void, Boolean>() {
                                                    @Override
                                                    protected Boolean doInBackground(Void... params) {
                                                        try {
                                                            new net.dean.jraw.managers.AccountManager(Authentication.reddit).save(submission, flair);
                                                            return true;
                                                        } catch (ApiException e) {
                                                            e.printStackTrace();

                                                            return false;
                                                        }
                                                    }

                                                    @Override
                                                    protected void onPostExecute(Boolean done) {
                                                        Snackbar s;
                                                        if (done) {
                                                            if (itemView != null) {
                                                                s = Snackbar.make(itemView, R.string.submission_info_saved, Snackbar.LENGTH_SHORT);
                                                                LayoutUtils.showSnackbar(s);
                                                            }
                                                        } else {
                                                            if (itemView != null) {
                                                                s = Snackbar.make(itemView, R.string.category_set_error, Snackbar.LENGTH_SHORT);
                                                                LayoutUtils.showSnackbar(s);
                                                            }
                                                        }
                                                    }
                                                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                            }
                                        }).negativeText(R.string.btn_cancel).show();
                            } else {
                                new AsyncTask<Void, Void, Boolean>() {
                                    @Override
                                    protected Boolean doInBackground(Void... params) {
                                        try {
                                            new net.dean.jraw.managers.AccountManager(Authentication.reddit).save(submission, t);

                                            return true;
                                        } catch (ApiException e) {
                                            e.printStackTrace();

                                            return false;
                                        }
                                    }

                                    @Override
                                    protected void onPostExecute(Boolean done) {
                                        Snackbar s;
                                        if (done) {
                                            if (itemView != null) {
                                                s = Snackbar.make(itemView, R.string.submission_info_saved, Snackbar.LENGTH_SHORT);
                                                LayoutUtils.showSnackbar(s);
                                            }
                                        } else {
                                            if (itemView != null) {
                                                s = Snackbar.make(itemView, R.string.category_set_error, Snackbar.LENGTH_SHORT);
                                                LayoutUtils.showSnackbar(s);
                                            }
                                        }
                                    }
                                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            }
                        }
                    }).show();

                    if (d != null) {
                        d.dismiss();
                    }
                } catch (Exception ignored) {}
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static <T extends Contribution> void hideSubmission(final Submission submission, final List<T> posts, final String baseSub, final RecyclerView recyclerview, Context c) {
        final int pos = posts.indexOf(submission);
        if (pos != -1) {
            if (submission.isHidden()) {
                posts.remove(pos);
                Hidden.undoHidden(submission);
                recyclerview.getAdapter().notifyItemRemoved(pos + 1);
                Snackbar snack = Snackbar.make(recyclerview, R.string.submission_info_unhidden, Snackbar.LENGTH_LONG);
                LayoutUtils.showSnackbar(snack);
            } else {
                final T t = posts.get(pos);
                posts.remove(pos);
                Hidden.setHidden(t);
                final OfflineSubreddit s;
                boolean success = false;
                if (baseSub != null) {
                    s = OfflineSubreddit.getSubreddit(baseSub, false, c);
                    try {
                        s.hide(pos);
                        success = true;
                    } catch (Exception e) {}
                } else {
                    success = false;
                    s = null;
                }

                recyclerview.getAdapter().notifyItemRemoved(pos + 1);

                final boolean finalSuccess = success;
                Snackbar snack = Snackbar.make(recyclerview, R.string.submission_info_hidden, Snackbar.LENGTH_LONG)
                    .setAction(
                        R.string.btn_undo,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (baseSub != null && s != null && finalSuccess) {
                                    s.unhideLast();
                                }
                                posts.add(pos, t);
                                recyclerview.getAdapter().notifyItemInserted(pos + 1);
                                Hidden.undoHidden(t);
                            }
                        }
                    );
                LayoutUtils.showSnackbar(snack);
            }
        }
    }

    public static class AsyncReportTask extends AsyncTask<String, Void, Void> {
        private Submission submission;
        private View contextView;

        public AsyncReportTask(Submission submission, View contextView) {
            this.submission = submission;
            this.contextView = contextView;
        }

        @Override
        protected Void doInBackground(String... reason) {
            try {
                new net.dean.jraw.managers.AccountManager(Authentication.reddit).report(submission, reason[0]);
            } catch (ApiException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (contextView != null) {
                try {
                    Snackbar s = Snackbar.make(contextView, R.string.msg_report_sent, Snackbar.LENGTH_SHORT);
                    Snackbar.make(contextView, R.string.msg_report_sent, Snackbar.LENGTH_SHORT);
                    LayoutUtils.showSnackbar(s);
                } catch (Exception ignored) {

                }
            }
        }
    }
}