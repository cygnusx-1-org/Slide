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
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
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
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.SubmissionCache;
import me.edgan.redditslide.SubmissionViews.LocalSaved;
import me.edgan.redditslide.SubmissionViews.ReadLater;
import me.edgan.redditslide.Views.CommentOverflow;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.markdown.MarkdownImages;
import net.dean.jraw.ApiException;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Ruleset;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.SubredditRule;
import org.apache.commons.text.StringEscapeUtils;

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
        Drawable viewmode = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_visibility, null);
        Drawable history = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_history, null);
        Drawable translate = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_translate, null);
        Drawable readAloud = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_volume_on, null);

        final List<Drawable> drawableSet = Arrays.asList(profile, sub, saved, hide, report, copy, open, link, reddit, readLater, filter, crosspost, viewmode, history, translate, readAloud);
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

        if (submission.getSelftext() != null && !submission.getSelftext().isEmpty()) {
            b.sheet(60, viewmode, mContext.getString(R.string.comment_render_other));
        }

        b.sheet(62, translate, mContext.getString(R.string.translate_with_google));
        b.sheet(63, readAloud, mContext.getString(R.string.read_aloud));

        if (full && PostRecovery.isRemovedOrDeleted(submission)) {
            b.sheet(61, history, mContext.getString(R.string.recover_post));
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


                        DialogUtil.showWithCardBackground(new AlertDialog.Builder(mContext)
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

                                    RecyclerView.Adapter<?> adapter = recyclerview.getAdapter();
                                    if (adapter == null) {
                                        return;
                                    }

                                    // Operate on the list the adapter is actually displaying;
                                    // the captured reference can be stale after a refresh.
                                    final List<T> livePosts = resolveLivePosts(recyclerview, posts);

                                    ArrayList<Contribution> toRemove = new ArrayList<>();

                                    for (Contribution s : livePosts) {
                                        if (s instanceof Submission && PostMatch.doesMatch((Submission) s)) {
                                            toRemove.add(s);
                                        }
                                    }

                                    OfflineSubreddit s = OfflineSubreddit.getSubreddit(baseSub, false, mContext);

                                    for (Contribution remove : toRemove) {
                                        final int pos = livePosts.indexOf(remove);
                                        if (pos < 0) {
                                            continue;
                                        }
                                        livePosts.remove(pos);
                                        if (baseSub != null && s.submissions != null) {
                                            // The offline cache is a separate list that may not
                                            // be index-aligned with the live feed, so match by
                                            // identity instead of reusing the display index.
                                            final int offlinePos = s.submissions.indexOf(remove);
                                            if (offlinePos >= 0) {
                                                s.hideMulti(offlinePos);
                                            }
                                        }
                                        // Header/spacer at position 0; the helper applies the
                                        // offset and falls back to a full reset if this
                                        // removal empties the list (no transient inconsistent
                                        // state to reconcile afterwards).
                                        notifyRemovedOrReset(adapter, livePosts, pos);
                                    }

                                    s.writeToMemoryNoStorage();
                                }
                            }).setNegativeButton(R.string.btn_cancel, null));

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

                            LayoutUtils.showSnackbar(s);
                        } else {
                            ReadLater.setReadLater(submission, false);
                            if (isReadLater || !Authentication.didOnline) {
                                final RecyclerView.Adapter<?> adapter = recyclerview.getAdapter();

                                // Operate on the list the adapter is actually displaying; the
                                // captured reference can be stale after a refresh.
                                final List<T> livePosts = resolveLivePosts(recyclerview, posts);

                                final int pos = livePosts.indexOf(submission);
                                if (adapter != null && pos != -1) {
                                    livePosts.remove(pos);

                                    if (adapter instanceof me.edgan.redditslide.Adapters.SubmissionAdapter) {
                                        // Feed adapter: header/spacer at 0, so the removed
                                        // row is pos + 1; also handles the list emptying.
                                        notifyRemovedOrReset(adapter, livePosts, pos);
                                    } else {
                                        // Other adapters (e.g. Read Later): use the live
                                        // binding position. getBindingAdapterPosition() returns
                                        // NO_POSITION for a holder that is mid-recycle; fall
                                        // back to a full reset (also covers the list emptying).
                                        final int bindingPos = holder.getBindingAdapterPosition();
                                        if (bindingPos != RecyclerView.NO_POSITION && !livePosts.isEmpty()) {
                                            adapter.notifyItemRemoved(bindingPos);
                                        } else {
                                            adapter.notifyDataSetChanged();
                                        }
                                    }

                                    Snackbar s2 = Snackbar.make(holder.itemView, "Removed from read later", Snackbar.LENGTH_SHORT);
                                    View view2 = s2.getView();
                                    TextView tv2 = view2.findViewById(com.google.android.material.R.id.snackbar_text);
                                    tv2.setTextColor(Color.WHITE);
                                    s2.setAction(
                                        R.string.btn_undo,
                                        new View.OnClickListener() {
                                            @Override
                                            public void onClick(View view) {
                                                // Re-resolve at click time in case a refresh
                                                // swapped in a new adapter list.
                                                final RecyclerView.Adapter<?> undoAdapter =
                                                        recyclerview.getAdapter();
                                                if (undoAdapter != null) {
                                                    final List<T> undoList =
                                                            resolveLivePosts(recyclerview, livePosts);
                                                    if (!undoList.contains(submission)) {
                                                        undoList.add(
                                                                Math.min(pos, undoList.size()),
                                                                (T) submission);
                                                    }
                                                    undoAdapter.notifyDataSetChanged();
                                                }
                                            }
                                        }
                                    );
                                }
                            } else {
                                Snackbar s2 = Snackbar.make(holder.itemView, "Removed from read later", Snackbar.LENGTH_SHORT);
                                LayoutUtils.showSnackbar(s2);
                            }
                            OfflineSubreddit.newSubreddit(CommentCacheAsync.SAVED_SUBMISSIONS).deleteFromMemory(submission.getFullName());
                        }

                        break;
                    case 4:
                        Reddit.defaultShareText(CompatUtil.fromHtml(submission.getTitle()).toString(), StringEscapeUtils.escapeHtml4(submission.getUrl()), mContext);

                        break;
                    case 12:
                        final Context contextThemeWrapper =
        new ContextThemeWrapper(mContext, new ColorPreferences(mContext).getFontStyle().getBaseId());
final View reportView =
        LayoutInflater.from(contextThemeWrapper).inflate(R.layout.report_dialog, null);
final AlertDialog reportDialog =
        new MaterialAlertDialogBuilder(contextThemeWrapper)
                .setView(reportView)
                .setTitle(R.string.report_post)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(
                        R.string.btn_report,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                            RadioGroup reasonGroup = reportView.findViewById(R.id.report_reasons);
                                            String reportReason;
                                            if (reasonGroup.getCheckedRadioButtonId() == R.id.report_other) {
                                                reportReason = ((EditText) reportView.findViewById(R.id.input_report_reason)).getText().toString();
                                            } else {
                                                reportReason = ((RadioButton) reasonGroup.findViewById(reasonGroup.getCheckedRadioButtonId())).getText().toString();
                                            }

                                            new AsyncReportTask(submission, holder.itemView).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, reportReason);
                                        }
                                    }
                                ).create();

                        final RadioGroup reasonGroup = reportView.findViewById(R.id.report_reasons);

                        reasonGroup.setOnCheckedChangeListener(
                            new RadioGroup.OnCheckedChangeListener() {
                                @Override
                                public void onCheckedChanged(RadioGroup group, int checkedId) {
                                    if (checkedId == R.id.report_other) {
                                        reportView.findViewById(R.id.input_report_reason).setVisibility(View.VISIBLE);
                                    } else {
                                        reportView.findViewById(R.id.input_report_reason).setVisibility(View.GONE);
                                    }
                                }
                            }
                        );

                        // Load sub's report reasons and show the appropriate ones
                        new AsyncTask<Void, Void, Ruleset>() {
                            @Override
                            protected Ruleset doInBackground(Void... voids) {
                                try {
                                    return Authentication.reddit.getRules(
                                            submission.getSubredditName());
                                } catch (RuntimeException e) {
                                    // Connection failures surface as a bare RuntimeException
                                    return null;
                                }
                            }

                            @Override
                            protected void onPostExecute(Ruleset rules) {
                                reportView.findViewById(R.id.report_loading).setVisibility(View.GONE);
                                if (rules == null) {
                                    // Could not load rules (offline); leave the dialog as-is
                                    return;
                                }
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
                        TranslateUtil.addToSelectionMenu(showText);
                        int sixteen = DisplayUtil.dpToPxVertical(24);
                        showText.setPadding(sixteen, 0, sixteen, 0);
                        final AlertDialog copyDialog =
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
                            }).create();
                        DialogUtil.matchDialogToCardBackground(mContext, copyDialog);
                        copyDialog.show();

                        break;
                    case 60:
                        // Preview this self-text with the opposite markdown renderer.
                        showOppositeRender(mContext, submission);
                        break;
                    case 62:
                        // Translate the title (and self-text, when present) via Google Translate.
                        TranslateUtil.translate(mContext, submissionPlainText(submission));
                        break;
                    case 63:
                        // Read the title (and self-text, when present) aloud via text-to-speech.
                        ReadAloudUtil.readAloud(mContext, submissionPlainText(submission));
                        break;
                    case 61:
                        // Recover the original body of a removed/deleted post from the archive.
                        final MaterialProgressDialog recoverProgress =
                                new MaterialProgressDialog.Builder(mContext)
                                        .title(R.string.recover_post)
                                        .content(R.string.recover_post_loading)
                                        .progress(true, 0)
                                        .cancelable(false)
                                        .show();
                        new AsyncTask<Void, Void, PostRecovery.Result>() {
                            @Override
                            protected PostRecovery.Result doInBackground(Void... voids) {
                                return PostRecovery.fetch(submission);
                            }

                            @Override
                            protected void onPostExecute(PostRecovery.Result result) {
                                // The fetch can outlive the screen; don't touch a dead Activity.
                                if (mContext.isFinishing() || mContext.isDestroyed()) {
                                    return;
                                }
                                if (recoverProgress.isShowing()) {
                                    recoverProgress.dismiss();
                                }
                                if (result.isEmpty()) {
                                    Toast.makeText(
                                                    mContext,
                                                    R.string.recover_post_failed,
                                                    Toast.LENGTH_LONG)
                                            .show();
                                    return;
                                }
                                PostRecovery.store(submission, result);
                                // Re-render the cached title with the recovered one.
                                SubmissionCache.updateTitle(submission, mContext);
                                // A recovered link flips is_self/url on the node, so the cached
                                // info line (domain) is now stale — refresh it to match.
                                if (result.url != null) {
                                    SubmissionCache.updateInfoSpannable(
                                            submission, mContext, baseSub);
                                }
                                if (recyclerview != null && recyclerview.getAdapter() != null) {
                                    // Full post view: pos 0 = spacer, pos 1 = header.
                                    recyclerview.getAdapter().notifyItemChanged(1);
                                }
                            }
                        }.execute();
                        break;
                }
            }
        });
        b.show();
    }

    /**
     * Show a one-shot dialog rendering {@code submission}'s self-text with the opposite of the
     * current global markdown setting ({@link SettingValues#markdownNewReddit}). Purely a preview:
     * it stores no state and does not change the post or the setting. Mirrors the per-comment
     * "Show other rendering" action. See issue #179.
     */
    /**
     * Returns the post's title plus self-text (when present) as readable plain text for translation
     * / text-to-speech, resolving markdown via Reddit's rendered {@code selftext_html} so raw syntax
     * (asterisks, link URLs) isn't spoken or translated. Falls back to the unescaped raw self-text
     * if no rendered HTML is available; link posts return just the title.
     */
    private static String submissionPlainText(final Submission submission) {
        final String title = CompatUtil.fromHtml(submission.getTitle()).toString().trim();
        final String selftext = submission.getSelftext();
        if (selftext == null || selftext.isEmpty()) {
            return title;
        }
        // Removed/deleted posts keep a non-empty self-text ("[removed]") but may have a null
        // selftext_html, so only render HTML when it's actually present; otherwise use the raw text.
        final JsonNode selfHtml = submission.getDataNode().path("selftext_html");
        String body =
                (selfHtml.isNull() || selfHtml.isMissingNode())
                        ? StringEscapeUtils.unescapeHtml4(selftext)
                        : CompatUtil.htmlToText(selfHtml.asText(""));
        if (body.isEmpty()) {
            body = StringEscapeUtils.unescapeHtml4(selftext);
        }
        return title + "\n\n" + body;
    }

    private static void showOppositeRender(final Activity mContext, final Submission submission) {
        final boolean showNewReddit = !SettingValues.markdownNewReddit;
        final String subreddit =
                submission.getSubredditName() == null ? "all" : submission.getSubredditName();
        final String bodyHtml = submission.getDataNode().path("selftext_html").asText("");

        LinearLayout container = new LinearLayout(mContext);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * mContext.getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);

        SpoilerRobotoTextView first = new SpoilerRobotoTextView(mContext);
        CommentOverflow overflow = new CommentOverflow(mContext);
        container.addView(first);
        container.addView(overflow);

        ScrollView scroll = new ScrollView(mContext);
        scroll.addView(container);

        if (showNewReddit) {
            MarkdownImages.renderInto(
                    first, overflow, subreddit, submission.getSelftext(), bodyHtml,
                    submission.getDataNode());
        } else {
            // Mirror the old-Reddit self-text path (PopulateSubmissionViewHolder.setViews): split
            // selftext_html into blocks, first into the TextView, the rest into the overflow.
            List<String> blocks = SubmissionParser.getBlocks(bodyHtml);
            int startIndex = 0;
            if (!blocks.get(0).startsWith("<table>") && !blocks.get(0).startsWith("<pre>")) {
                first.setTextHtml(blocks.get(0), subreddit);
                startIndex = 1;
            }
            if (blocks.size() > 1) {
                overflow.setViews(blocks.subList(startIndex, blocks.size()), subreddit);
            }
        }

        new MaterialAlertDialogBuilder(mContext)
                .setTitle(
                        showNewReddit
                                ? R.string.markdown_preview_new_reddit
                                : R.string.markdown_preview_old_reddit)
                .setView(scroll)
                .setPositiveButton(R.string.btn_ok, null)
                .show();
    }

    public static void saveSubmission(final Submission submission, final Activity mContext, final SubmissionViewHolder holder, final boolean full) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    if (ActionStates.isSaved(submission)) {
                        new net.dean.jraw.managers.AccountManager(Authentication.reddit).unsave(submission);
                        ActionStates.setSaved(submission, false);
                        LocalSaved.onUnsaved(submission);
                    } else {
                        new net.dean.jraw.managers.AccountManager(Authentication.reddit).save(submission);
                        ActionStates.setSaved(submission, true);
                        LocalSaved.onSaved(submission);
                    }

                } catch (Exception e) {
                    LogUtil.e(e, "SubmissionBottomSheetActions.doInBackground failed");
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
                d = new MaterialProgressDialog.Builder(mContext).progress(true, 100).title(R.string.profile_category_loading).content(R.string.misc_please_wait).show().getDialog();
            }

            @Override
            protected List<String> doInBackground(Void... params) {
                try {
                    List<String> categories = new ArrayList<String>(new net.dean.jraw.managers.AccountManager(Authentication.reddit).getSavedCategories());
                    categories.add("New category");
                    return categories;
                } catch (Exception e) {
                    LogUtil.e(e, "SubmissionBottomSheetActions.doInBackground failed");
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
                    new MaterialAlertDialogBuilder(new ContextThemeWrapper(mContext, new ColorPreferences(mContext).getFontStyle().getBaseId())).setTitle(R.string.sidebar_select_flair).setItems(data.toArray(new CharSequence[0]), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface listDialog, int which) {
                            final String t = data.get(which);
                            if (which == data.size() - 1) {
                                new MaterialInputDialog.Builder(mContext)
                                    .title(R.string.category_set_name)
                                    .input(mContext.getString(R.string.category_set_name_hint), null, null)
                                    .positiveText(R.string.btn_set)
                                    .onPositive(
                                        new MaterialInputDialog.ButtonCallback() {
                                            @Override
                                            public void onClick(MaterialInputDialog dialog) {
                                                final String flair = dialog.getInputEditText().getText().toString();
                                                new AsyncTask<Void, Void, Boolean>() {
                                                    @Override
                                                    protected Boolean doInBackground(Void... params) {
                                                        try {
                                                            new net.dean.jraw.managers.AccountManager(Authentication.reddit).save(submission, flair);
                                                            return true;
                                                        } catch (ApiException | RuntimeException e) {
                                                            LogUtil.e(e, "SubmissionBottomSheetActions.doInBackground failed");

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
                                        } catch (ApiException | RuntimeException e) {
                                            LogUtil.e(e, "SubmissionBottomSheetActions.doInBackground failed");

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
                } catch (Exception e) {
                    LogUtil.e(e, "Failed to dismiss flair dialog");
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static <T extends Contribution> void hideSubmission(final Submission submission, final List<T> posts, final String baseSub, final RecyclerView recyclerview, Context c) {
        final RecyclerView.Adapter<?> adapter = recyclerview.getAdapter();
        if (adapter == null) {
            return;
        }

        // Operate on the list the adapter is actually displaying; the captured
        // reference can be stale after a refresh, so the hidden row would
        // otherwise never leave the screen.
        final List<T> livePosts = resolveLivePosts(recyclerview, posts);

        final int pos = livePosts.indexOf(submission);
        if (pos != -1) {
            if (submission.isHidden()) {
                livePosts.remove(pos);
                Hidden.undoHidden(submission);
                notifyRemovedOrReset(adapter, livePosts, pos);
                Snackbar snack = Snackbar.make(recyclerview, R.string.submission_info_unhidden, Snackbar.LENGTH_LONG);
                LayoutUtils.showSnackbar(snack);
            } else {
                final T t = livePosts.get(pos);
                livePosts.remove(pos);
                Hidden.setHidden(t);
                final OfflineSubreddit s;
                boolean success = false;
                if (baseSub != null) {
                    s = OfflineSubreddit.getSubreddit(baseSub, false, c);
                    // The offline cache is a separate list that may not be
                    // index-aligned with the live feed, so match by identity
                    // instead of reusing the display index.
                    final int offlinePos = s.submissions != null ? s.submissions.indexOf(t) : -1;
                    if (offlinePos >= 0) {
                        try {
                            s.hide(offlinePos);
                            success = true;
                        } catch (Exception e) {
                            LogUtil.e(e, "Failed to hide submission in offline subreddit");
                        }
                    }
                } else {
                    success = false;
                    s = null;
                }

                notifyRemovedOrReset(adapter, livePosts, pos);

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
                                // Re-resolve the live list at click time: a refresh
                                // during the snackbar window can swap in a new adapter
                                // list, and the undo must restore into whatever is
                                // currently displayed.
                                final RecyclerView.Adapter<?> undoAdapter = recyclerview.getAdapter();
                                if (undoAdapter != null) {
                                    final List<T> undoList = resolveLivePosts(recyclerview, livePosts);
                                    if (!undoList.contains(t)) {
                                        undoList.add(Math.min(pos, undoList.size()), t);
                                    }
                                    undoAdapter.notifyDataSetChanged();
                                }
                                Hidden.undoHidden(t);
                            }
                        }
                    );
                LayoutUtils.showSnackbar(snack);
            }
        }
    }

    /**
     * Resolves the list the adapter is actually displaying. A pull-to-refresh
     * reassigns {@code SubredditPosts.posts} to a brand-new list object, so a
     * reference captured by a menu/undo closure at bind time can point at a stale
     * list the adapter no longer shows. Re-resolving from the live adapter avoids
     * mutating that dead list. Falls back to {@code captured} when the adapter is
     * not a feed adapter (e.g. the Read Later screen).
     */
    @SuppressWarnings("unchecked")
    private static <T extends Contribution> List<T> resolveLivePosts(
            final RecyclerView recyclerview, final List<T> captured) {
        final RecyclerView.Adapter<?> adapter = recyclerview.getAdapter();
        if (adapter instanceof me.edgan.redditslide.Adapters.SubmissionAdapter) {
            final List<Submission> live =
                    ((me.edgan.redditslide.Adapters.SubmissionAdapter) adapter).dataSet.posts;
            // SubmissionAdapter guards against a null backing list, so mirror that
            // here and fall back to the captured reference rather than returning
            // null (which callers dereference immediately).
            if (live != null) {
                return (List<T>) live;
            }
        }
        return captured;
    }

    /**
     * Signals removal of the row at {@code pos} in the backing list. Only the feed
     * adapter ({@link me.edgan.redditslide.Adapters.SubmissionAdapter}) is known to
     * carry a spacer at position 0, so the removed row maps to {@code pos + 1}
     * there. For any other adapter (offset unknown), or once the list empties (the
     * feed adapter's getItemCount() collapses to 0, dropping the spacer and footer
     * too), a targeted notifyItemRemoved would desync the count, so fall back to a
     * full reset.
     */
    private static void notifyRemovedOrReset(
            final RecyclerView.Adapter<?> adapter, final List<?> livePosts, final int pos) {
        if (adapter instanceof me.edgan.redditslide.Adapters.SubmissionAdapter
                && !livePosts.isEmpty()) {
            adapter.notifyItemRemoved(pos + 1);
        } else {
            adapter.notifyDataSetChanged();
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
            } catch (ApiException | RuntimeException e) {
                LogUtil.e(e, "SubmissionBottomSheetActions.doInBackground failed");
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
