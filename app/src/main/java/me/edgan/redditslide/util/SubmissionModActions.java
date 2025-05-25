package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.cocosw.bottomsheet.BottomSheet;
import com.google.android.material.snackbar.Snackbar;

import net.dean.jraw.ApiException;
import net.dean.jraw.fluent.FlairReference;
import net.dean.jraw.fluent.FluentRedditClient;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.http.oauth.InvalidScopeException;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.managers.ModerationManager;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.DistinguishedStatus;
import net.dean.jraw.models.FlairTemplate;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Thing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import me.edgan.redditslide.Activities.ModQueue;
import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Activities.Reauthenticate;
import me.edgan.redditslide.Adapters.SubmissionViewHolder;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionCache;
import me.edgan.redditslide.Toolbox.ToolboxUI;

/**
 * Handles Moderation actions for Submission views.
 */
public class SubmissionModActions {

    // Keep reason variable here as it's used within the mod action flow
    public static String reason;

    public static <T extends Contribution> void showModBottomSheet(
            final Activity mContext,
            final Submission submission,
            final List<T> posts,
            final SubmissionViewHolder holder,
            final RecyclerView recyclerview,
            final Map<String, Integer> reports,
            final Map<String, String> reports2) {

        final Resources res = mContext.getResources();
        int[] attrs = new int[] {R.attr.tintColor};
        TypedArray ta = mContext.obtainStyledAttributes(attrs);

        int color = ta.getColor(0, Color.WHITE);
        Drawable profile = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_account_circle, null);
        final Drawable report = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_report, null);
        final Drawable approve = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_thumb_up, null);
        final Drawable nsfw = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_visibility_off, null);
        final Drawable spoiler = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_remove_circle, null);
        final Drawable pin = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_bookmark_border, null);
        final Drawable lock = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_lock, null);
        final Drawable flair = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_format_quote, null);
        final Drawable remove = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_close, null);
        final Drawable remove_reason = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_announcement, null);
        final Drawable ban = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_gavel, null);
        final Drawable spam = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_flag, null);
        final Drawable distinguish = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_star, null);
        final Drawable note = ResourcesCompat.getDrawable(mContext.getResources(), R.drawable.ic_note, null);

        final List<Drawable> drawableSet = Arrays.asList(profile, report, approve, spam, nsfw, pin, flair, remove, spoiler, remove_reason, ban, spam, distinguish, lock, note);
        BlendModeUtil.tintDrawablesAsSrcAtop(drawableSet, color);

        ta.recycle();

        BottomSheet.Builder b = new BottomSheet.Builder(mContext).title(CompatUtil.fromHtml(submission.getTitle()));

        int reportCount = reports.size() + reports2.size();

        b.sheet(0, report, res.getQuantityString(R.plurals.mod_btn_reports, reportCount, reportCount));

        if (SettingValues.toolboxEnabled) {
            b.sheet(24, note, res.getString(R.string.mod_usernotes_view));
        }

        boolean approved = false;
        String whoApproved = "";
        b.sheet(1, approve, res.getString(R.string.mod_btn_approve));
        b.sheet(6, remove, mContext.getString(R.string.mod_btn_remove))
            .sheet(7, remove_reason, res.getString(R.string.mod_btn_remove_reason)).sheet(30, spam, res.getString(R.string.mod_btn_spam));

        b.sheet(20, flair, res.getString(R.string.mod_btn_submission_flair));

        final boolean isNsfw = submission.isNsfw();
        if (isNsfw) {
            b.sheet(3, nsfw, res.getString(R.string.mod_btn_unmark_nsfw));
        } else {
            b.sheet(3, nsfw, res.getString(R.string.mod_btn_mark_nsfw));
        }

        final boolean isSpoiler = submission.getDataNode().get("spoiler").asBoolean();
        if (isSpoiler) {
            b.sheet(12, nsfw, res.getString(R.string.mod_btn_unmark_spoiler));
        } else {
            b.sheet(12, nsfw, res.getString(R.string.mod_btn_mark_spoiler));
        }

        final boolean locked = submission.isLocked();
        if (locked) {
            b.sheet(9, lock, res.getString(R.string.mod_btn_unlock_thread));
        } else {
            b.sheet(9, lock, res.getString(R.string.mod_btn_lock_thread));
        }

        final boolean stickied = submission.isStickied();
        if (!SubmissionCache.removed.contains(submission.getFullName())) {
            if (stickied) {
                b.sheet(4, pin, res.getString(R.string.mod_btn_unpin));
            } else {
                b.sheet(4, pin, res.getString(R.string.mod_btn_pin));
            }
        }

        final boolean distinguished = submission.getDistinguishedStatus() == DistinguishedStatus.MODERATOR || submission.getDistinguishedStatus() == DistinguishedStatus.ADMIN;
        if (submission.getAuthor().equalsIgnoreCase(Authentication.name)) {
            if (distinguished) {
                b.sheet(5, distinguish, res.getString(R.string.mod_btn_undistinguish));
            } else {
                b.sheet(5, distinguish, res.getString(R.string.mod_btn_distinguish));
            }
        }

        final String finalWhoApproved = whoApproved;
        final boolean finalApproved = approved;
        b.sheet(8, profile, res.getString(R.string.mod_btn_author));
        b.sheet(23, ban, mContext.getString(R.string.mod_ban_user));
        b.listener(
            new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case 0:
                            new AsyncTask<Void, Void, ArrayList<String>>() {
                                @Override
                                protected ArrayList<String> doInBackground(Void... params) {

                                    ArrayList<String> finalReports = new ArrayList<>();
                                    for (Map.Entry<String, Integer> entry : reports.entrySet()) {
                                        finalReports.add(entry.getValue() + "× " + entry.getKey());
                                    }
                                    for (Map.Entry<String, String> entry : reports2.entrySet()) {
                                        finalReports.add(entry.getKey() + ": " + entry.getValue());
                                    }
                                    if (finalReports.isEmpty()) {
                                        finalReports.add(mContext.getString(R.string.mod_no_reports));
                                    }
                                    return finalReports;
                                }

                                @Override
                                public void onPostExecute(ArrayList<String> data) {
                                    new AlertDialog.Builder(mContext)
                                            .setTitle(R.string.mod_reports)
                                            .setItems(data.toArray(new CharSequence[0]), null)
                                            .show();
                                }
                            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

                            break;
                        case 1:
                            if (finalApproved) {
                                Intent i = new Intent(mContext, Profile.class);
                                i.putExtra(Profile.EXTRA_PROFILE, finalWhoApproved);
                                mContext.startActivity(i);
                            } else {
                                approveSubmission(
                                        mContext, posts, submission, recyclerview, holder);
                            }
                            break;
                        case 2:
                            // todo this
                            break;
                        case 3:
                            if (isNsfw) {
                                unNsfwSubmission(mContext, submission, holder);
                            } else {
                                setPostNsfw(mContext, submission, holder);
                            }
                            break;
                        case 12:
                            if (isSpoiler) {
                                unSpoiler(mContext, submission, holder);
                            } else {
                                setSpoiler(mContext, submission, holder);
                            }
                            break;
                        case 9:
                            if (locked) {
                                unLockSubmission(mContext, submission, holder);
                            } else {
                                lockSubmission(mContext, submission, holder);
                            }
                            break;
                        case 4:
                            if (stickied) {
                                unStickySubmission(mContext, submission, holder);
                            } else {
                                stickySubmission(mContext, submission, holder);
                            }
                            break;
                        case 5:
                            if (distinguished) {
                                unDistinguishSubmission(mContext, submission, holder);
                            } else {
                                distinguishSubmission(mContext, submission, holder);
                            }
                            break;
                        case 6:
                            removeSubmission(
                                    mContext, submission, posts, recyclerview, holder, false);
                            break;
                        case 7:
                            if (SettingValues.removalReasonType
                                            == SettingValues.RemovalReasonType.TOOLBOX.ordinal()
                                    && ToolboxUI.canShowRemoval(
                                            submission.getSubredditName())) {
                                ToolboxUI.showRemoval(
                                        mContext,
                                        submission,
                                        new ToolboxUI.CompletedRemovalCallback() {
                                            @Override
                                            public void onComplete(boolean success) {
                                                if (success) {
                                                    SubmissionCache.removed.add(submission.getFullName());
                                                    SubmissionCache.approved.remove(submission.getFullName());

                                                    SubmissionCache.updateInfoSpannable(submission, mContext, submission.getSubredditName());

                                                    if (mContext instanceof ModQueue) {
                                                        final int pos = posts.indexOf(submission);
                                                        posts.remove(submission);

                                                        if (pos == 0) {
                                                            recyclerview.getAdapter().notifyDataSetChanged();
                                                        } else {
                                                            recyclerview.getAdapter().notifyItemRemoved(pos + 1);
                                                        }
                                                    } else {
                                                        recyclerview.getAdapter().notifyItemChanged(holder.getBindingAdapterPosition());
                                                    }

                                                    Snackbar s = Snackbar.make(holder.itemView, R.string.submission_removed, Snackbar.LENGTH_LONG);
                                                    LayoutUtils.showSnackbar(s);
                                                } else {
                                                    new AlertDialog.Builder(mContext)
                                                        .setTitle(R.string.err_general)
                                                        .setMessage(R.string.err_retry_later)
                                                        .show();
                                                }
                                            }
                                        });
                            } else { // Show a Slide reason dialog if we can't show a toolbox or rddit one
                                doRemoveSubmissionReason(mContext, submission, posts, recyclerview, holder);
                            }

                            break;
                        case 30:
                            removeSubmission(mContext, submission, posts, recyclerview, holder, true);

                            break;
                        case 8:
                            Intent i = new Intent(mContext, Profile.class);
                            i.putExtra(Profile.EXTRA_PROFILE, submission.getAuthor());
                            mContext.startActivity(i);

                            break;
                        case 20:
                            doSetFlair(mContext, submission, holder);

                            break;
                        case 23:
                            // ban a user
                            showBan(mContext, holder.itemView, submission, "", "", "", "");

                            break;
                        case 24:
                            ToolboxUI.showUsernotes(mContext, submission.getAuthor(), submission.getSubredditName(), "l," + submission.getId());

                            break;
                    }
                }
            });

        b.show();
    }

    public static <T extends Contribution> void doRemoveSubmissionReason(
            final Activity mContext,
            final Submission submission,
            final List<T> posts,
            final RecyclerView recyclerview,
            final SubmissionViewHolder holder) {
        reason = "";
        new MaterialDialog.Builder(mContext)
                .title(R.string.mod_remove_title)
                .positiveText(R.string.btn_remove)
                .alwaysCallInputCallback()
                .input(
                        mContext.getString(R.string.mod_remove_hint),
                        mContext.getString(R.string.mod_remove_template),
                        false,
                        new MaterialDialog.InputCallback() {
                            @Override
                            public void onInput(MaterialDialog dialog, CharSequence input) {
                                reason = input.toString();
                            }
                        })
                .inputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                .neutralText(R.string.mod_remove_insert_draft)
                .onPositive(
                        new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(final MaterialDialog dialog, DialogAction which) {

                                removeSubmissionReason(
                                        submission, mContext, posts, reason, holder, recyclerview);
                            }
                        })
                .negativeText(R.string.btn_cancel)
                .onNegative(null)
                .show();
    }

    public static <T extends Contribution> void removeSubmissionReason(
            final Submission submission,
            final Activity mContext,
            final List<T> posts,
            final String reason,
            final SubmissionViewHolder holder,
            final RecyclerView recyclerview) {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    SubmissionCache.removed.add(submission.getFullName());
                    SubmissionCache.approved.remove(submission.getFullName());

                    SubmissionCache.updateInfoSpannable(
                            submission, mContext, submission.getSubredditName());

                    if (mContext instanceof ModQueue) {
                        final int pos = posts.indexOf(submission);
                        posts.remove(submission);

                        if (pos == 0) {
                            recyclerview.getAdapter().notifyDataSetChanged();
                        } else {
                            recyclerview.getAdapter().notifyItemRemoved(pos + 1);
                        }
                    } else {
                        recyclerview
                                .getAdapter()
                                .notifyItemChanged(holder.getBindingAdapterPosition());
                    }
                    Snackbar s =
                            Snackbar.make(
                                    holder.itemView,
                                    R.string.submission_removed,
                                    Snackbar.LENGTH_LONG);

                    LayoutUtils.showSnackbar(s);

                } else {
                    new AlertDialog.Builder(mContext)
                            .setTitle(R.string.err_general)
                            .setMessage(R.string.err_retry_later)
                            .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    String toDistinguish =
                            new AccountManager(Authentication.reddit).reply(submission, reason);
                    new ModerationManager(Authentication.reddit).remove(submission, false);
                    new ModerationManager(Authentication.reddit)
                            .setDistinguishedStatus(
                                    Authentication.reddit.get("t1_" + toDistinguish).get(0),
                                    DistinguishedStatus.MODERATOR);
                } catch (ApiException e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static <T extends Contribution> void removeSubmission(
            final Activity mContext,
            final Submission submission,
            final List<T> posts,
            final RecyclerView recyclerview,
            final SubmissionViewHolder holder,
            final boolean spam) {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            public void onPostExecute(Boolean b) {

                SubmissionCache.removed.add(submission.getFullName());
                SubmissionCache.approved.remove(submission.getFullName());

                SubmissionCache.updateInfoSpannable(
                        submission, mContext, submission.getSubredditName());

                if (b) {
                    if (mContext instanceof ModQueue) {
                        final int pos = posts.indexOf(submission);
                        posts.remove(submission);

                        if (pos == 0) {
                            recyclerview.getAdapter().notifyDataSetChanged();
                        } else {
                            recyclerview.getAdapter().notifyItemRemoved(pos + 1);
                        }
                    } else {
                        recyclerview
                                .getAdapter()
                                .notifyItemChanged(holder.getBindingAdapterPosition());
                    }

                    Snackbar s =
                            Snackbar.make(
                                    holder.itemView,
                                    R.string.submission_removed,
                                    Snackbar.LENGTH_LONG);
                    LayoutUtils.showSnackbar(s);

                } else {
                    new AlertDialog.Builder(mContext)
                            .setTitle(R.string.err_general)
                            .setMessage(R.string.err_retry_later)
                            .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit).remove(submission, spam);
                } catch (ApiException | NetworkException e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void doSetFlair(
            final Activity mContext,
            final Submission submission,
            final SubmissionViewHolder holder) {
        new AsyncTask<Void, Void, ArrayList<String>>() {
            ArrayList<FlairTemplate> flair;

            @Override
            protected ArrayList<String> doInBackground(Void... params) {
                FlairReference allFlairs =
                        new FluentRedditClient(Authentication.reddit)
                                .subreddit(submission.getSubredditName())
                                .flair();
                try {
                    flair = new ArrayList<>(allFlairs.options(submission));
                    final ArrayList<String> finalFlairs = new ArrayList<>();
                    for (FlairTemplate temp : flair) {
                        finalFlairs.add(temp.getText());
                    }
                    return finalFlairs;
                } catch (Exception e) {
                    e.printStackTrace();
                    // sub probably has no flairs?
                }
                return null;
            }

            @Override
            public void onPostExecute(final ArrayList<String> data) {
                try {
                    if (data == null || data.isEmpty()) { // Added null check
                        new AlertDialog.Builder(mContext)
                                .setTitle(R.string.mod_flair_none_found)
                                .setPositiveButton(R.string.btn_ok, null)
                                .show();
                    } else {
                        showFlairSelectionDialog(mContext, submission, data, flair, holder);
                    }
                } catch (Exception ignored) {

                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void showFlairSelectionDialog(
            final Activity mContext,
            final Submission submission,
            ArrayList<String> data,
            final ArrayList<FlairTemplate> flair,
            final SubmissionViewHolder holder) {
        new MaterialDialog.Builder(mContext)
                .items(data)
                .title(R.string.sidebar_select_flair)
                .itemsCallback(
                        new MaterialDialog.ListCallback() {
                            @Override
                            public void onSelection(
                                    MaterialDialog dialog,
                                    View itemView,
                                    int which,
                                    CharSequence text) {
                                final FlairTemplate t = flair.get(which);
                                if (t.isTextEditable()) {
                                    showFlairEditDialog(mContext, submission, t, holder);
                                } else {
                                    setFlair(mContext, null, submission, t, holder);
                                }
                            }
                        })
                .show();
    }

    public static void showFlairEditDialog(
            final Activity mContext,
            final Submission submission,
            final FlairTemplate t,
            final SubmissionViewHolder holder) {
        new MaterialDialog.Builder(mContext)
                .title(R.string.sidebar_select_flair_text)
                .input(
                        mContext.getString(R.string.mod_flair_hint),
                        t.getText(),
                        true,
                        (dialog, input) -> {})
                .positiveText(R.string.btn_set)
                .onPositive(
                        new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(MaterialDialog dialog, DialogAction which) {
                                final String flair = dialog.getInputEditText().getText().toString();
                                setFlair(mContext, flair, submission, t, holder);
                            }
                        })
                .negativeText(R.string.btn_cancel)
                .show();
    }

    public static void setFlair(
            final Context mContext,
            final String flair,
            final Submission submission,
            final FlairTemplate t,
            final SubmissionViewHolder holder) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit)
                            .setFlair(submission.getSubredditName(), t, flair, submission);
                    return true;
                } catch (ApiException e) {
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean done) {
                Snackbar s = null;
                if (done) {
                    if (holder.itemView != null) {
                        s = Snackbar.make(holder.itemView, R.string.snackbar_flair_success, Snackbar.LENGTH_SHORT);
                    }
                    if (holder.itemView != null) {
                        SubmissionCache.updateTitleFlair(submission, flair != null ? flair : t.getText(), mContext); // Use flair if not null, else template text
                        doText(holder, submission, mContext, submission.getSubredditName(), false);
                        // Force the title view to re-measure itself
                        holder.title.requestLayout();
                    }
                } else {
                    if (holder.itemView != null) {
                        s = Snackbar.make(holder.itemView, R.string.snackbar_flair_error, Snackbar.LENGTH_SHORT);
                    }
                }
                if (s != null) {
                    LayoutUtils.showSnackbar(s);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void doText(SubmissionViewHolder holder, Submission submission, Context mContext, String baseSub, boolean full) {
        SpannableStringBuilder t = SubmissionCache.getTitleLine(submission, mContext);
        SpannableStringBuilder l = SubmissionCache.getInfoLine(submission, mContext, baseSub);
        SpannableStringBuilder c = SubmissionCache.getCrosspostLine(submission, mContext);

        int[] textSizeAttr = new int[] {R.attr.font_cardtitle, R.attr.font_cardinfo};
        TypedArray a = mContext.obtainStyledAttributes(textSizeAttr);
        int textSizeT = a.getDimensionPixelSize(0, 18);
        int textSizeI = a.getDimensionPixelSize(1, 14);

        t.setSpan(new AbsoluteSizeSpan(textSizeT), 0, t.length(), 0);
        l.setSpan(new AbsoluteSizeSpan(textSizeI), 0, l.length(), 0);

        SpannableStringBuilder s = new SpannableStringBuilder();
        if (SettingValues.titleTop) {
            s.append(t);
            s.append("\n");
            s.append(l);
        } else {
            s.append(l);
            s.append("\n");
            s.append(t);
        }
        if (!full && c != null) {
            c.setSpan(new AbsoluteSizeSpan(textSizeI), 0, c.length(), 0);
            s.append("\n");
            s.append(c);
        }
        a.recycle();

        holder.title.setText(s);

        // Force this TextView to recalculate itself and request a new layout pass
        holder.title.requestLayout();
        holder.title.invalidate();
    }

    public static void stickySubmission(
            final Activity mContext,
            final Submission submission,
            final SubmissionViewHolder holder) {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    Snackbar s = Snackbar.make(holder.itemView, R.string.really_pin_submission_message, Snackbar.LENGTH_LONG);
                    LayoutUtils.showSnackbar(s);

                } else {
                    new AlertDialog.Builder(mContext)
                            .setTitle(R.string.err_general)
                            .setMessage(R.string.err_retry_later)
                            .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit).setSticky(submission, true);
                } catch (ApiException | NetworkException e) {
                    e.printStackTrace();

                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void unStickySubmission(
            final Activity mContext,
            final Submission submission,
            final SubmissionViewHolder holder) {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    Snackbar s = Snackbar.make(holder.itemView, R.string.really_unpin_submission_message, Snackbar.LENGTH_LONG);
                    LayoutUtils.showSnackbar(s);

                } else {
                    new AlertDialog.Builder(mContext)
                        .setTitle(R.string.err_general)
                        .setMessage(R.string.err_retry_later)
                        .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit).setSticky(submission, false);
                } catch (ApiException e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void lockSubmission(
            final Activity mContext,
            final Submission submission,
            final SubmissionViewHolder holder) {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    Snackbar s = Snackbar.make(holder.itemView, R.string.mod_locked, Snackbar.LENGTH_LONG);
                    LayoutUtils.showSnackbar(s);

                } else {
                    new AlertDialog.Builder(mContext)
                        .setTitle(R.string.err_general)
                        .setMessage(R.string.err_retry_later)
                        .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit).setLocked(submission);
                } catch (ApiException e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void unLockSubmission(final Activity mContext, final Submission submission, final SubmissionViewHolder holder) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    Snackbar s = Snackbar.make(holder.itemView, R.string.mod_unlocked, Snackbar.LENGTH_LONG);
                    LayoutUtils.showSnackbar(s);

                } else {
                    new AlertDialog.Builder(mContext)
                        .setTitle(R.string.err_general)
                        .setMessage(R.string.err_retry_later)
                        .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit).setUnlocked(submission);
                } catch (ApiException e) {
                    e.printStackTrace();
                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void distinguishSubmission(final Activity mContext, final Submission submission, final SubmissionViewHolder holder) {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    Snackbar s = Snackbar.make(holder.itemView, R.string.submission_distinguished, Snackbar.LENGTH_LONG);
                    LayoutUtils.showSnackbar(s);

                } else {
                    new AlertDialog.Builder(mContext)
                        .setTitle(R.string.err_general)
                        .setMessage(R.string.err_retry_later)
                        .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit).setDistinguishedStatus(submission, DistinguishedStatus.MODERATOR);
                } catch (ApiException e) {
                    e.printStackTrace();

                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void unDistinguishSubmission(final Activity mContext, final Submission submission, final SubmissionViewHolder holder) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    Snackbar s = Snackbar.make(holder.itemView, R.string.submission_distinguished_removed, Snackbar.LENGTH_LONG);
                    LayoutUtils.showSnackbar(s);
                } else {
                    new AlertDialog.Builder(mContext)
                        .setTitle(R.string.err_general)
                        .setMessage(R.string.err_retry_later)
                        .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    // JRAW requires MODERATOR to undistinguish as well
                    new ModerationManager(Authentication.reddit).setDistinguishedStatus(submission, DistinguishedStatus.MODERATOR);
                } catch (ApiException e) {
                    e.printStackTrace();

                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void setPostNsfw(final Activity mContext, final Submission submission, final SubmissionViewHolder holder) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    Snackbar s = Snackbar.make(holder.itemView, R.string.nsfw_status_set, Snackbar.LENGTH_LONG);
                    LayoutUtils.showSnackbar(s);
                    // TODO: Update UI immediately

                } else {
                    new AlertDialog.Builder(mContext)
                        .setTitle(R.string.err_general)
                        .setMessage(R.string.err_retry_later)
                        .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit).setNsfw(submission, true);
                } catch (ApiException e) {
                    e.printStackTrace();

                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void unNsfwSubmission(final Context mContext, final Submission submission, final SubmissionViewHolder holder) {
        // todo update view with NSFW tag
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    Snackbar s = Snackbar.make(holder.itemView, R.string.nsfw_status_removed, Snackbar.LENGTH_LONG);
                    LayoutUtils.showSnackbar(s);
                    // TODO: Update UI immediately

                } else {
                    new AlertDialog.Builder(mContext)
                        .setTitle(R.string.err_general)
                        .setMessage(R.string.err_retry_later)
                        .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit).setNsfw(submission, false);
                } catch (ApiException e) {
                    e.printStackTrace();

                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void setSpoiler(
            final Activity mContext,
            final Submission submission,
            final SubmissionViewHolder holder) {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    Snackbar s = Snackbar.make(holder.itemView, R.string.spoiler_status_set, Snackbar.LENGTH_LONG);
                    LayoutUtils.showSnackbar(s);
                    // TODO: Update UI immediately

                } else {
                    new AlertDialog.Builder(mContext)
                        .setTitle(R.string.err_general)
                        .setMessage(R.string.err_retry_later)
                        .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit).setSpoiler(submission, true);
                } catch (ApiException e) {
                    e.printStackTrace();

                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void unSpoiler(final Context mContext, final Submission submission, final SubmissionViewHolder holder) {
        // todo update view with NSFW tag
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    Snackbar s = Snackbar.make(holder.itemView, R.string.spoiler_status_removed, Snackbar.LENGTH_LONG);
                    LayoutUtils.showSnackbar(s);
                    // TODO: Update UI immediately

                } else {
                    new AlertDialog.Builder(mContext)
                        .setTitle(R.string.err_general)
                        .setMessage(R.string.err_retry_later)
                        .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit).setSpoiler(submission, false);
                } catch (ApiException e) {
                    e.printStackTrace();

                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static <T extends Thing> void approveSubmission(final Context mContext, final List<T> posts, final Submission submission, final RecyclerView recyclerview, final SubmissionViewHolder holder) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            public void onPostExecute(Boolean b) {
                if (b) {
                    SubmissionCache.approved.add(submission.getFullName());
                    SubmissionCache.removed.remove(submission.getFullName());
                    SubmissionCache.updateInfoSpannable(submission, mContext, submission.getSubredditName());

                    if (mContext instanceof ModQueue) {
                        final int pos = posts.indexOf(submission);
                        posts.remove(submission);

                        if (pos == 0) {
                            recyclerview.getAdapter().notifyDataSetChanged();
                        } else {
                            recyclerview.getAdapter().notifyItemRemoved(pos + 1);
                        }
                    } else {
                        recyclerview.getAdapter().notifyItemChanged(holder.getBindingAdapterPosition());
                    }

                    try {
                        Snackbar s = Snackbar.make(holder.itemView, R.string.mod_approved, Snackbar.LENGTH_LONG);
                        LayoutUtils.showSnackbar(s);
                    } catch (Exception ignored) {}
                } else {
                    new AlertDialog.Builder(mContext)
                        .setTitle(R.string.err_general)
                        .setMessage(R.string.err_retry_later)
                        .show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    new ModerationManager(Authentication.reddit).approve(submission);
                } catch (ApiException e) {
                    e.printStackTrace();

                    return false;
                }
                return true;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public static void showBan(final Context mContext, final View mToolbar, final Submission submission, String rs, String nt, String msg, String t) {
        LinearLayout l = new LinearLayout(mContext);
        l.setOrientation(LinearLayout.VERTICAL);
        int sixteen = DisplayUtil.dpToPxVertical(16);
        l.setPadding(sixteen, 0, sixteen, 0);

        final EditText reason = new EditText(mContext);
        reason.setHint(R.string.mod_ban_reason);
        reason.setText(rs);
        reason.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        l.addView(reason);

        final EditText note = new EditText(mContext);
        note.setHint(R.string.mod_ban_note_mod);
        note.setText(nt);
        note.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        l.addView(note);

        final EditText message = new EditText(mContext);
        message.setHint(R.string.mod_ban_note_user);
        message.setText(msg);
        message.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        l.addView(message);

        final EditText time = new EditText(mContext);
        time.setHint(R.string.mod_ban_time);
        time.setText(t);
        time.setInputType(InputType.TYPE_CLASS_NUMBER);
        l.addView(time);

        new AlertDialog.Builder(mContext)
                .setView(l)
                .setTitle(mContext.getString(R.string.mod_ban_title, submission.getAuthor()))
                .setCancelable(true)
                .setPositiveButton(
                    R.string.mod_btn_ban,
                    (dialog, which) -> {
                        // to ban
                        if (reason.getText().toString().isEmpty()) {
                            new AlertDialog.Builder(mContext)
                                    .setTitle(R.string.mod_ban_reason_required)
                                    .setMessage(R.string.misc_please_try_again)
                                    .setPositiveButton(
                                            R.string.btn_ok,
                                            (dialog1, which1) ->
                                                    showBan(mContext, mToolbar, submission, reason.getText().toString(), note.getText().toString(), message.getText().toString(), time.getText().toString()))
                                    .setCancelable(false)
                                    .show();
                        } else {
                            new AsyncTask<Void, Void, Boolean>() {
                                @Override
                                protected Boolean doInBackground(Void... params) {
                                    try {
                                        String n = note.getText().toString();
                                        String m = message.getText().toString();

                                        if (n.isEmpty()) {
                                            n = null;
                                        }
                                        if (m.isEmpty()) {
                                            m = null;
                                        }
                                        if (time.getText().toString().isEmpty()) {
                                            new ModerationManager(Authentication.reddit)
                                                .banUserPermanently(submission.getSubredditName(), submission.getAuthor(), reason.getText().toString(), n, m);
                                        } else {
                                            new ModerationManager(Authentication.reddit)
                                                .banUser(submission.getSubredditName(), submission.getAuthor(), reason.getText().toString(), n, m, Integer.parseInt(time.getText().toString()));
                                        }
                                        return true;
                                    } catch (Exception e) {
                                        if (e instanceof InvalidScopeException) {
                                            scope = true;
                                        }
                                        e.printStackTrace();
                                        return false;
                                    }
                                }

                                boolean scope;

                                @Override
                                protected void onPostExecute(Boolean done) {
                                    Snackbar s;
                                    if (done) {
                                        s = Snackbar.make(mToolbar, R.string.mod_ban_success, Snackbar.LENGTH_SHORT);
                                    } else {
                                        if (scope) {
                                            new AlertDialog.Builder(mContext)
                                                    .setTitle(R.string.mod_ban_reauth)
                                                    .setMessage(R.string.mod_ban_reauth_question)
                                                    .setPositiveButton(
                                                            R.string.btn_ok,
                                                            (dialog12, which12) -> {
                                                                Intent i = new Intent(mContext, Reauthenticate.class);
                                                                mContext.startActivity(i);
                                                            })
                                                    .setNegativeButton(
                                                            R.string.misc_maybe_later, null)
                                                    .setCancelable(false)
                                                    .show();
                                        }
                                        s = Snackbar.make(mToolbar, R.string.mod_ban_fail, Snackbar.LENGTH_INDEFINITE)
                                            .setAction(
                                                R.string.misc_try_again,
                                                new View.OnClickListener() {
                                                    @Override
                                                    public void onClick(
                                                            View v) {
                                                        showBan(
                                                                mContext,
                                                                mToolbar,
                                                                submission,
                                                                reason.getText()
                                                                        .toString(),
                                                                note.getText()
                                                                        .toString(),
                                                                message.getText()
                                                                        .toString(),
                                                                time.getText()
                                                                        .toString());
                                                        }
                                                    });
                                    }

                                    if (s != null) {
                                        LayoutUtils.showSnackbar(s);
                                    }
                                }
                            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        }
                    }).setNegativeButton(R.string.btn_cancel, null).show();
    }
}