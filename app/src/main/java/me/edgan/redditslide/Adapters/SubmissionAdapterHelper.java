package me.edgan.redditslide.Adapters;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Activities.SubredditView;
import me.edgan.redditslide.Activities.Website;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Hidden;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.BottomSheet;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.LinkUtil;

import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;

/**
 * The long-press "post menu" dialog shared by the contribution and moderator post lists: profile,
 * subreddit, save, share, gild, and hide-with-undo actions for a single submission row.
 */
public class SubmissionAdapterHelper {

    /**
     * Shows the long-press dialog for a submission row.
     *
     * @param titleText the dialog title, already html-decoded (and search-highlighted where the
     *     caller supports it)
     * @param holderItemView the bound row view, used as the save-flash anchor
     * @param posts the adapter's backing list; hide removes/re-adds entries and notifies adapter
     */
    public static <T extends Contribution> void showSubmissionLongPressDialog(
            final Activity mContext,
            final Submission submission,
            final CharSequence titleText,
            final View holderItemView,
            final RecyclerView.Adapter<?> adapter,
            final List<T> posts,
            final RecyclerView listView) {
        LayoutInflater inflater = mContext.getLayoutInflater();
        final View dialoglayout = inflater.inflate(R.layout.postmenu, null);
        final TextView title = dialoglayout.findViewById(R.id.title);
        title.setText(titleText);

        ((TextView) dialoglayout.findViewById(R.id.userpopup))
                .setText("/u/" + submission.getAuthor());
        ((TextView) dialoglayout.findViewById(R.id.subpopup))
                .setText("/r/" + submission.getSubredditName());
        dialoglayout
                .findViewById(R.id.sidebar)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent i = new Intent(mContext, Profile.class);
                                i.putExtra(
                                        Profile.EXTRA_PROFILE,
                                        submission.getAuthor());
                                mContext.startActivity(i);
                            }
                        });

        dialoglayout
                .findViewById(R.id.wiki)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent i =
                                        new Intent(
                                                mContext, SubredditView.class);
                                i.putExtra(
                                        SubredditView.EXTRA_SUBREDDIT,
                                        submission.getSubredditName());
                                mContext.startActivity(i);
                            }
                        });

        dialoglayout
                .findViewById(R.id.save)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (submission.isSaved()) {
                                    ((TextView)
                                                    dialoglayout.findViewById(
                                                            R.id.savedtext))
                                            .setText(R.string.submission_save);
                                } else {
                                    ((TextView)
                                                    dialoglayout.findViewById(
                                                            R.id.savedtext))
                                            .setText(
                                                    R.string
                                                            .submission_post_saved);
                                }
                                new AsyncSave(mContext, holderItemView)
                                        .execute(submission);
                            }
                        });
        dialoglayout.findViewById(R.id.copy).setVisibility(View.GONE);
        if (submission.isSaved()) {
            ((TextView) dialoglayout.findViewById(R.id.savedtext))
                    .setText(R.string.submission_post_saved);
        }
        dialoglayout
                .findViewById(R.id.gild)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                String urlString =
                                        "https://reddit.com"
                                                + submission.getPermalink();
                                Intent i = new Intent(mContext, Website.class);
                                i.putExtra(LinkUtil.EXTRA_URL, urlString);
                                mContext.startActivity(i);
                            }
                        });
        dialoglayout
                .findViewById(R.id.share)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (submission.isSelfPost()) {
                                    if (SettingValues.shareLongLink) {
                                        Reddit.defaultShareText(
                                                "",
                                                "https://reddit.com"
                                                        + submission
                                                                .getPermalink(),
                                                mContext);
                                    } else {
                                        Reddit.defaultShareText(
                                                "",
                                                "https://reddit.com/comments/"
                                                        + submission.getId(),
                                                mContext);
                                    }
                                } else {
                                    new BottomSheet.Builder(mContext)
                                            .title(
                                                    R.string
                                                            .submission_share_title)
                                            .grid()
                                            .sheet(R.menu.share_menu)
                                            .listener(
                                                    new DialogInterface
                                                            .OnClickListener() {
                                                        @Override
                                                        public void onClick(
                                                                DialogInterface
                                                                        dialog,
                                                                int which) {
                                                            switch (which) {
                                                                case R.id
                                                                        .reddit_url:
                                                                    if (SettingValues
                                                                            .shareLongLink) {
                                                                        Reddit
                                                                                .defaultShareText(
                                                                                        submission
                                                                                                .getTitle(),
                                                                                        "https://reddit.com"
                                                                                                + submission
                                                                                                        .getPermalink(),
                                                                                        mContext);
                                                                    } else {
                                                                        Reddit
                                                                                .defaultShareText(
                                                                                        submission
                                                                                                .getTitle(),
                                                                                        "https://reddit.com/comments/"
                                                                                                + submission
                                                                                                        .getId(),
                                                                                        mContext);
                                                                    }
                                                                    break;
                                                                case R.id
                                                                        .link_url:
                                                                    Reddit
                                                                            .defaultShareText(
                                                                                    submission
                                                                                            .getTitle(),
                                                                                    submission
                                                                                            .getUrl(),
                                                                                    mContext);
                                                                    break;
                                                            }
                                                        }
                                                    })
                                            .show();
                                }
                            }
                        });
        if (!Authentication.isLoggedIn || !Authentication.didOnline) {
            dialoglayout.findViewById(R.id.save).setVisibility(View.GONE);
            dialoglayout.findViewById(R.id.gild).setVisibility(View.GONE);
        }
        title.setBackgroundColor(
                Palette.getColor(submission.getSubredditName()));

        final AlertDialog.Builder builder =
                new AlertDialog.Builder(mContext).setView(dialoglayout);
        final Dialog d = DialogUtil.showWithCardBackground(builder);
        dialoglayout
                .findViewById(R.id.hide)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                final int pos =
                                        posts.indexOf(submission);
                                if (pos < 0) {
                                    // The post left the list (refresh/filter) while the
                                    // dialog was open.
                                    d.dismiss();
                                    return;
                                }
                                final T old = posts.get(pos);
                                posts.remove(submission);
                                if (posts.isEmpty()) {
                                    // getItemCount() collapses to 0 for an empty list
                                    // (spacer/footer vanish too), so a single-item
                                    // notify would desync RecyclerView.
                                    adapter.notifyDataSetChanged();
                                } else {
                                    adapter.notifyItemRemoved(pos + 1);
                                }
                                d.dismiss();

                                Hidden.setHidden(old);

                                Snackbar s =
                                        Snackbar.make(
                                                        listView,
                                                        R.string
                                                                .submission_info_hidden,
                                                        Snackbar.LENGTH_LONG)
                                                .setAction(
                                                        R.string.btn_undo,
                                                        new View
                                                                .OnClickListener() {
                                                            @Override
                                                            public void onClick(
                                                                    View v) {
                                                                // The list may have shrunk
                                                                // since the hide.
                                                                final int insertPos =
                                                                        Math.min(
                                                                                pos,
                                                                                posts.size());
                                                                posts.add(
                                                                        insertPos,
                                                                        old);
                                                                if (posts.size() == 1) {
                                                                    // 0 -> size()+N items;
                                                                    // see hide above.
                                                                    adapter
                                                                            .notifyDataSetChanged();
                                                                } else {
                                                                    adapter.notifyItemInserted(
                                                                            insertPos + 1);
                                                                }
                                                                Hidden
                                                                        .undoHidden(
                                                                                old);
                                                            }
                                                        });
                                LayoutUtils.showSnackbar(s);
                            }
                        });
    }
}
