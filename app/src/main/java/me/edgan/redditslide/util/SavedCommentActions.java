package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.List;

import me.edgan.redditslide.ActionStates;
import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SubmissionViews.LocalSaved;

import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.models.Comment;

import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The overflow sheet behind a comment row's menu button in the profile.
 *
 * <p>Comment rows had no menu at all: {@code ContributionAdapter} wired click listeners and nothing
 * else, so a saved comment could never be tagged after the fact. This is that menu.
 *
 * <p>{@code CommentAdapterHelper.showOverflowBottomSheet} could not be reused -- it takes a {@code
 * CommentAdapter}, a {@code CommentViewHolder} and a {@code CommentNode}, all comment-tree types a
 * profile listing does not have. This is modelled on {@code InboxAdapter}'s sheet instead, which is
 * built over a standalone comment for the same reason.
 */
@NullMarked
public final class SavedCommentActions {

    private SavedCommentActions() {}

    /** Told when the comment was unsaved, so the list showing it can drop the row. */
    public interface OnUnsaved {
        void onUnsaved(Comment comment);
    }

    public static void showBottomSheet(
            final Context context,
            final Comment comment,
            final View anchor,
            final @Nullable OnUnsaved onUnsaved) {

        final int[] attrs = new int[] {R.attr.tintColor};
        final TypedArray ta = context.obtainStyledAttributes(attrs);
        final int color = ta.getColor(0, Color.WHITE);
        ta.recycle();

        final Drawable profile =
                ResourcesCompat.getDrawable(
                        context.getResources(), R.drawable.ic_account_circle, null);
        final Drawable tags =
                ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_folder, null);
        final Drawable saved =
                ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_star, null);
        final Drawable copy =
                ResourcesCompat.getDrawable(
                        context.getResources(), R.drawable.ic_content_copy, null);
        final Drawable link =
                ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_link, null);

        final List<Drawable> drawableSet = Arrays.asList(profile, tags, saved, copy, link);
        BlendModeUtil.tintDrawablesAsSrcAtop(drawableSet, color);

        final BottomSheet.Builder b =
                new BottomSheet.Builder((Activity) context)
                        .title(CompatUtil.fromHtml(comment.getBody()));

        final String author = comment.getAuthor();
        if (author != null) {
            b.sheet(1, profile, "/u/" + author);
        }
        final boolean isSaved = ActionStates.isSaved(comment);
        if (Authentication.isLoggedIn) {
            // Tagging only means something for an item that is actually saved -- the same gate the
            // submission sheet uses. This sheet also serves the profile's other comment tabs, where
            // a comment may not be saved at all.
            if (isSaved) {
                b.sheet(2, tags, context.getString(R.string.profile_tag_select));
            }
            b.sheet(
                    3,
                    saved,
                    context.getString(isSaved ? R.string.comment_unsave : R.string.btn_save));
        }
        b.sheet(4, copy, context.getString(R.string.misc_copy_text));
        b.sheet(5, link, context.getString(R.string.comment_permalink));

        b.listener(
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 1:
                                {
                                    final Intent i = new Intent(context, Profile.class);
                                    i.putExtra(Profile.EXTRA_PROFILE, author);
                                    context.startActivity(i);
                                }
                                break;
                            case 2:
                                SavedTagDialogs.showTagPicker(context, comment, null);
                                break;
                            case 3:
                                toggleSaved(comment, isSaved, anchor, onUnsaved);
                                break;
                            case 4:
                                {
                                    // A comment with no body is not a copy worth reporting; a
                                    // removed one can come back with none at all.
                                    final String body = comment.getBody();
                                    if (body == null) {
                                        break;
                                    }
                                    ClipboardUtil.copyToClipboard(
                                            context,
                                            context.getString(R.string.misc_copy_text),
                                            StringEscapeUtils.unescapeHtml4(body));
                                    LayoutUtils.showSnackbar(
                                            Snackbar.make(
                                                    anchor,
                                                    R.string.submission_comment_copied,
                                                    Snackbar.LENGTH_SHORT));
                                }
                                break;
                            case 5:
                                OpenRedditLink.openUrl(
                                        context,
                                        comment.getSubmissionId(),
                                        comment.getSubredditName(),
                                        comment.getId());
                                break;
                            default:
                                break;
                        }
                    }
                });
        b.show();
    }

    /**
     * Save or unsave, then tell the caller if the row should go. {@link LocalSaved} drops the Saved
     * TTL cache either way, so the tab does not serve a stale blob back.
     */
    private static void toggleSaved(
            final Comment comment,
            final boolean wasSaved,
            final View anchor,
            final @Nullable OnUnsaved onUnsaved) {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    final AccountManager account = new AccountManager(Authentication.reddit);
                    if (wasSaved) {
                        account.unsave(comment);
                        ActionStates.setSaved(comment, false);
                        LocalSaved.onUnsaved(comment);
                    } else {
                        account.save(comment);
                        ActionStates.setSaved(comment, true);
                        LocalSaved.onSaved(comment);
                    }
                    return true;
                } catch (Exception e) {
                    LogUtil.e(e, "SavedCommentActions.toggleSaved failed");
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean done) {
                final int message;
                if (!done) {
                    message = R.string.err_general;
                } else {
                    message =
                            wasSaved
                                    ? R.string.submission_comment_unsaved
                                    : R.string.submission_comment_saved;
                }
                try {
                    LayoutUtils.showSnackbar(
                            Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT));
                } catch (Exception ignored) {
                    // Snackbar needs a view still in a window; the write already went through.
                }
                // Only an unsave takes a row out of a listing, and only the caller knows whether
                // this listing is the one it would leave.
                if (done && wasSaved && onUnsaved != null) {
                    onUnsaved.onUnsaved(comment);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
