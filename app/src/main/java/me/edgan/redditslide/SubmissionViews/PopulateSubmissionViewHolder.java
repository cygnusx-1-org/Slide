package me.edgan.redditslide.SubmissionViews;


import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.cocosw.bottomsheet.BottomSheet;
import com.devspark.robototextview.RobotoTypefaces;
import com.google.android.material.snackbar.Snackbar;

import me.edgan.redditslide.ActionStates;
import me.edgan.redditslide.Activities.MainActivity;
import me.edgan.redditslide.Adapters.CommentAdapter;
import me.edgan.redditslide.Adapters.SubmissionViewHolder;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.LastComments;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionCache;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Views.CreateCardView;
import me.edgan.redditslide.Views.DoEditorActions;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.Vote;
import me.edgan.redditslide.util.AnimatorUtil;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.OnSingleClickListener;
import me.edgan.redditslide.util.SubmissionBottomSheetActions;
import me.edgan.redditslide.util.SubmissionModActions;
import me.edgan.redditslide.util.SubmissionParser;

import net.dean.jraw.ApiException;
import net.dean.jraw.fluent.FlairReference;
import net.dean.jraw.fluent.FluentRedditClient;
import net.dean.jraw.managers.AccountManager;
import net.dean.jraw.managers.ModerationManager;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.FlairTemplate;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.VoteDirection;

import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Created by ccrama on 9/19/2015. */
public class PopulateSubmissionViewHolder {

    public PopulateSubmissionViewHolder() {}

    public <T extends Contribution> void populateSubmissionViewHolder(
            final SubmissionViewHolder holder,
            final Submission submission,
            final Activity mContext,
            boolean fullscreen,
            final boolean full,
            final List<T> posts,
            final RecyclerView recyclerview,
            final boolean same,
            final boolean offline,
            final String baseSub,
            @Nullable final CommentAdapter adapter) {
        holder.itemView.findViewById(R.id.vote).setVisibility(View.GONE);

        if (!offline
                && UserSubscriptions.modOf != null
                && submission.getSubredditName() != null
                && UserSubscriptions.modOf.contains(
                        submission.getSubredditName().toLowerCase(Locale.ENGLISH))) {
            holder.mod.setVisibility(View.VISIBLE);
            final Map<String, Integer> reports = submission.getUserReports();
            final Map<String, String> reports2 = submission.getModeratorReports();
            if (reports.size() + reports2.size() > 0) {
                BlendModeUtil.tintImageViewAsSrcAtop(
                        (ImageView) holder.mod,
                        ContextCompat.getColor(mContext, R.color.md_red_300));
            } else {
                final int getTintColor =
                        holder.itemView.getTag(holder.itemView.getId()) != null
                                        && holder.itemView
                                                .getTag(holder.itemView.getId())
                                                .equals("none")
                                || full
                        ? Palette.getCurrentTintColor(mContext)
                        : Palette.getWhiteTintColor();
                BlendModeUtil.tintImageViewAsSrcAtop((ImageView) holder.mod, getTintColor);
            }
            holder.mod.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            SubmissionModActions.showModBottomSheet(
                                    mContext,
                                    submission,
                                    posts,
                                    holder,
                                    recyclerview,
                                    reports,
                                    reports2);
                        }
                    });
        } else {
            holder.mod.setVisibility(View.GONE);
        }

        holder.menu.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        SubmissionBottomSheetActions.showBottomSheet(
                                mContext, submission, holder, posts, baseSub, recyclerview, full);
                    }
                });

        // Use this to offset the submission score
        int submissionScore = submission.getScore();

        final int commentCount = submission.getCommentCount();
        final int more = LastComments.commentsSince(submission);
        holder.comments.setText(
                String.format(
                        Locale.getDefault(),
                        "%d %s",
                        commentCount,
                        ((more > 0 && SettingValues.commentLastVisit) ? "(+" + more + ")" : "")));
        String scoreRatio =
                (SettingValues.upvotePercentage && full && submission.getUpvoteRatio() != null)
                        ? "(" + (int) (submission.getUpvoteRatio() * 100) + "%)"
                        : "";

        if (!scoreRatio.isEmpty()) {
            TextView percent = holder.itemView.findViewById(R.id.percent);
            percent.setVisibility(View.VISIBLE);
            percent.setText(scoreRatio);

            final double numb = (submission.getUpvoteRatio());
            if (numb <= .5) {
                if (numb <= .1) {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_blue_500));
                } else if (numb <= .3) {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_blue_400));
                } else {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_blue_300));
                }
            } else {
                if (numb >= .9) {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_orange_500));
                } else if (numb >= .7) {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_orange_400));
                } else {
                    percent.setTextColor(ContextCompat.getColor(mContext, R.color.md_orange_300));
                }
            }
        }

        final ImageView downvotebutton = (ImageView) holder.downvote;
        final ImageView upvotebutton = (ImageView) holder.upvote;

        if (submission.isArchived()) {
            downvotebutton.setVisibility(View.GONE);
            upvotebutton.setVisibility(View.GONE);
        } else if (Authentication.isLoggedIn && Authentication.didOnline) {
            if (SettingValues.actionbarVisible && downvotebutton.getVisibility() != View.VISIBLE) {
                downvotebutton.setVisibility(View.VISIBLE);
                upvotebutton.setVisibility(View.VISIBLE);
            }
        }

        // Set the colors and styles for the score text depending on what state it is in
        // Also set content descriptions
        switch (ActionStates.getVoteDirection(submission)) {
            case UPVOTE:
                {
                    holder.score.setTextColor(
                            ContextCompat.getColor(mContext, R.color.md_orange_500));
                    BlendModeUtil.tintImageViewAsSrcAtop(
                            upvotebutton, ContextCompat.getColor(mContext, R.color.md_orange_500));
                    upvotebutton.setContentDescription(mContext.getString(R.string.btn_upvoted));
                    holder.score.setTypeface(null, Typeface.BOLD);
                    final int getTintColor =
                            holder.itemView.getTag(holder.itemView.getId()) != null
                                                    && holder.itemView
                                                            .getTag(holder.itemView.getId())
                                                            .equals("none")
                                            || full
                                    ? Palette.getCurrentTintColor(mContext)
                                    : Palette.getWhiteTintColor();
                    BlendModeUtil.tintImageViewAsSrcAtop(downvotebutton, getTintColor);
                    downvotebutton.setContentDescription(mContext.getString(R.string.btn_downvote));
                    if (submission.getVote() != VoteDirection.UPVOTE) {
                        if (submission.getVote() == VoteDirection.DOWNVOTE) ++submissionScore;
                        ++submissionScore; // offset the score by +1
                    }
                    break;
                }
            case DOWNVOTE:
                {
                    holder.score.setTextColor(
                            ContextCompat.getColor(mContext, R.color.md_blue_500));
                    BlendModeUtil.tintImageViewAsSrcAtop(
                            downvotebutton, ContextCompat.getColor(mContext, R.color.md_blue_500));
                    downvotebutton.setContentDescription(
                            mContext.getString(R.string.btn_downvoted));
                    holder.score.setTypeface(null, Typeface.BOLD);
                    final int getTintColor =
                            holder.itemView.getTag(holder.itemView.getId()) != null
                                                    && holder.itemView
                                                            .getTag(holder.itemView.getId())
                                                            .equals("none")
                                            || full
                                    ? Palette.getCurrentTintColor(mContext)
                                    : Palette.getWhiteTintColor();
                    BlendModeUtil.tintImageViewAsSrcAtop(upvotebutton, getTintColor);
                    upvotebutton.setContentDescription(mContext.getString(R.string.btn_upvote));
                    if (submission.getVote() != VoteDirection.DOWNVOTE) {
                        if (submission.getVote() == VoteDirection.UPVOTE) --submissionScore;
                        --submissionScore; // offset the score by +1
                    }
                    break;
                }
            case NO_VOTE:
                {
                    holder.score.setTextColor(holder.comments.getCurrentTextColor());
                    holder.score.setTypeface(null, Typeface.NORMAL);
                    final int getTintColor =
                            holder.itemView.getTag(holder.itemView.getId()) != null
                                                    && holder.itemView
                                                            .getTag(holder.itemView.getId())
                                                            .equals("none")
                                            || full
                                    ? Palette.getCurrentTintColor(mContext)
                                    : Palette.getWhiteTintColor();
                    final List<ImageView> imageViewSet =
                            Arrays.asList(downvotebutton, upvotebutton);
                    BlendModeUtil.tintImageViewsAsSrcAtop(imageViewSet, getTintColor);
                    upvotebutton.setContentDescription(mContext.getString(R.string.btn_upvote));
                    downvotebutton.setContentDescription(mContext.getString(R.string.btn_downvote));
                    break;
                }
        }

        // if the submission is already at 0pts, keep it at 0pts
        submissionScore = Math.max(submissionScore, 0);
        if (submissionScore >= 10000 && SettingValues.abbreviateScores) {
            holder.score.setText(
                    String.format(
                            Locale.getDefault(), "%.1fk", (((double) submissionScore) / 1000)));
        } else {
            holder.score.setText(String.format(Locale.getDefault(), "%d", submissionScore));
        }

        // Save the score so we can use it in the OnClickListeners for the vote buttons
        final int SUBMISSION_SCORE = submissionScore;

        final ImageView hideButton = (ImageView) holder.hide;

        if (hideButton != null) {
            if (SettingValues.hideButton && Authentication.isLoggedIn) {
                hideButton.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                SubmissionBottomSheetActions.hideSubmission(submission, posts, baseSub, recyclerview, mContext);
                            }
                        });
            } else {
                hideButton.setVisibility(View.GONE);
            }
        }

        if (Authentication.isLoggedIn && Authentication.didOnline) {
            if (ActionStates.isSaved(submission)) {
                BlendModeUtil.tintImageViewAsSrcAtop((ImageView) holder.save, ContextCompat.getColor(mContext, R.color.md_amber_500));
                holder.save.setContentDescription(mContext.getString(R.string.btn_unsave));
            } else {
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
            holder.save.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            SubmissionBottomSheetActions.saveSubmission(submission, mContext, holder, full);
                        }
                    });
        }

        if (!SettingValues.saveButton && !full || !Authentication.isLoggedIn || !Authentication.didOnline) {
            holder.save.setVisibility(View.GONE);
        }

        ImageView thumbImage2 = ((ImageView) holder.thumbimage);

        if (holder.leadImage.thumbImage2 == null) {
            holder.leadImage.setThumbnail(thumbImage2);
        }

        final ContentType.Type type = ContentType.getContentType(submission);

        SubmissionClickActions.addClickFunctions(holder.leadImage, type, mContext, submission, holder, full);

        if (thumbImage2 != null) {
            SubmissionClickActions.addClickFunctions(thumbImage2, type, mContext, submission, holder, full);
        }

        if (full) {
            SubmissionClickActions.addClickFunctions(
                    holder.itemView.findViewById(R.id.wraparea),
                    type,
                    mContext,
                    submission,
                    holder,
                    full);
        }

        if (full) {
            holder.leadImage.setWrapArea(holder.itemView.findViewById(R.id.wraparea));
        }

        if (full
                && (submission.getDataNode() != null
                        && submission.getDataNode().has("crosspost_parent_list")
                        && submission.getDataNode().get("crosspost_parent_list") != null
                        && submission.getDataNode().get("crosspost_parent_list").get(0) != null)) {
            holder.itemView.findViewById(R.id.crosspost).setVisibility(View.VISIBLE);
            ((TextView) holder.itemView.findViewById(R.id.crossinfo))
                    .setText(SubmissionCache.getCrosspostLine(submission, mContext));
            ((Reddit) mContext.getApplicationContext())
                    .getImageLoader()
                    .displayImage(
                            submission
                                    .getDataNode()
                                    .get("crosspost_parent_list")
                                    .get(0)
                                    .get("thumbnail")
                                    .asText(),
                            ((ImageView) holder.itemView.findViewById(R.id.crossthumb)));
            holder.itemView
                    .findViewById(R.id.crosspost)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    OpenRedditLink.openUrl(
                                            mContext,
                                            submission
                                                    .getDataNode()
                                                    .get("crosspost_parent_list")
                                                    .get(0)
                                                    .get("permalink")
                                                    .asText(),
                                            true);
                                }
                            });
        }

        holder.leadImage.setSubmission(submission, full, baseSub, type);

        holder.itemView.setOnLongClickListener(
                new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {

                        if (offline) {
                            Snackbar s =
                                    Snackbar.make(
                                            holder.itemView,
                                            mContext.getString(R.string.offline_msg),
                                            Snackbar.LENGTH_SHORT);
                            LayoutUtils.showSnackbar(s);
                        } else {
                            if (SettingValues.actionbarTap && !full) {
                                CreateCardView.toggleActionbar(holder.itemView);
                            } else {
                                holder.itemView.findViewById(R.id.menu).callOnClick();
                            }
                        }
                        return true;
                    }
                });

        SubmissionModActions.doText(holder, submission, mContext, baseSub, full);

        if (!full
                && SettingValues.isSelftextEnabled(baseSub)
                && submission.isSelfPost()
                && !submission.getSelftext().isEmpty()
                && !submission.isNsfw()
                && !submission.getDataNode().get("spoiler").asBoolean()
                && !submission.getDataNode().get("selftext_html").asText().trim().isEmpty()) {
            holder.body.setVisibility(View.VISIBLE);
            String text = submission.getDataNode().get("selftext_html").asText();
            int typef = new FontPreferences(mContext).getFontTypeComment().getTypeface();
            Typeface typeface;
            if (typef >= 0) {
                typeface = RobotoTypefaces.obtainTypeface(mContext, typef);
            } else {
                typeface = Typeface.DEFAULT;
            }
            holder.body.setTypeface(typeface);

            holder.body.setTextHtml(
                    CompatUtil.fromHtml(
                                    text.substring(
                                            0,
                                            text.contains("\n")
                                                    ? text.indexOf("\n")
                                                    : text.length()))
                            .toString()
                            .replace("<sup>", "<sup><small>")
                            .replace("</sup>", "</small></sup>"),
                    "none ",
                    submission);
            holder.body.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            holder.itemView.callOnClick();
                        }
                    });
            holder.body.setOnLongClickListener(
                    new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            holder.menu.callOnClick();
                            return true;
                        }
                    });
        } else if (!full) {
            holder.body.setVisibility(View.GONE);
        }

        if (full) {
            if (!submission.getSelftext().isEmpty()) {
                int typef = new FontPreferences(mContext).getFontTypeComment().getTypeface();
                Typeface typeface;
                if (typef >= 0) {
                    typeface = RobotoTypefaces.obtainTypeface(mContext, typef);
                } else {
                    typeface = Typeface.DEFAULT;
                }
                holder.firstTextView.setTypeface(typeface);

                setViews(
                        submission.getDataNode().get("selftext_html").asText(),
                        submission.getSubredditName() == null
                                ? "all"
                                : submission.getSubredditName(),
                        holder,
                        submission);
                holder.itemView.findViewById(R.id.body_area).setVisibility(View.VISIBLE);
            } else {
                holder.itemView.findViewById(R.id.body_area).setVisibility(View.GONE);
            }
        }

        try {
            final TextView points = holder.score;
            final TextView comments = holder.comments;

            if (Authentication.isLoggedIn && !offline && Authentication.didOnline) {
                {
                    downvotebutton.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (SettingValues.storeHistory && !full) {
                                        if (!submission.isNsfw()
                                                || SettingValues.storeNSFWHistory) {
                                            HasSeen.addSeen(submission.getFullName());
                                            if (mContext instanceof MainActivity) {
                                                holder.title.setAlpha(0.54f);
                                                holder.body.setAlpha(0.54f);
                                            }
                                        }
                                    }
                                    final int getTintColor =
                                            holder.itemView.getTag(holder.itemView.getId()) != null
                                                                    && holder.itemView
                                                                            .getTag(
                                                                                    holder.itemView
                                                                                            .getId())
                                                                            .equals("none")
                                                            || full
                                                    ? Palette.getCurrentTintColor(mContext)
                                                    : Palette.getWhiteTintColor();
                                    if (ActionStates.getVoteDirection(submission)
                                            != VoteDirection.DOWNVOTE) { // has not been downvoted
                                        points.setTextColor(
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_blue_500));
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                downvotebutton,
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_blue_500));
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                upvotebutton, getTintColor);
                                        downvotebutton.setContentDescription(
                                                mContext.getString(R.string.btn_downvoted));

                                        AnimatorUtil.setFlashAnimation(
                                                holder.itemView,
                                                downvotebutton,
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_blue_500));
                                        holder.score.setTypeface(null, Typeface.BOLD);
                                        final int DOWNVOTE_SCORE =
                                                (SUBMISSION_SCORE == 0)
                                                        ? 0
                                                        : SUBMISSION_SCORE
                                                                - 1; // if a post is at 0 votes,
                                        // keep it at 0 when downvoting
                                        new Vote(false, points, mContext).execute(submission);
                                        ActionStates.setVoteDirection(
                                                submission, VoteDirection.DOWNVOTE);
                                    } else { // un-downvoted a post
                                        points.setTextColor(comments.getCurrentTextColor());
                                        new Vote(points, mContext).execute(submission);
                                        holder.score.setTypeface(null, Typeface.NORMAL);
                                        ActionStates.setVoteDirection(
                                                submission, VoteDirection.NO_VOTE);
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                downvotebutton, getTintColor);
                                        downvotebutton.setContentDescription(
                                                mContext.getString(R.string.btn_downvote));
                                    }
                                    setSubmissionScoreText(submission, holder);
                                    if (!full
                                            && !SettingValues.actionbarVisible
                                            && SettingValues.defaultCardView
                                                    != CreateCardView.CardEnum.DESKTOP) {
                                        CreateCardView.toggleActionbar(holder.itemView);
                                    }
                                }
                            });
                }
                {
                    upvotebutton.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    if (SettingValues.storeHistory && !full) {
                                        if (!submission.isNsfw()
                                                || SettingValues.storeNSFWHistory) {
                                            HasSeen.addSeen(submission.getFullName());
                                            if (mContext instanceof MainActivity) {
                                                holder.title.setAlpha(0.54f);
                                                holder.body.setAlpha(0.54f);
                                            }
                                        }
                                    }

                                    final int getTintColor =
                                            holder.itemView.getTag(holder.itemView.getId()) != null
                                                                    && holder.itemView
                                                                            .getTag(
                                                                                    holder.itemView
                                                                                            .getId())
                                                                            .equals("none")
                                                            || full
                                                    ? Palette.getCurrentTintColor(mContext)
                                                    : Palette.getWhiteTintColor();
                                    if (ActionStates.getVoteDirection(submission)
                                            != VoteDirection.UPVOTE) { // has not been upvoted
                                        points.setTextColor(
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_orange_500));
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                upvotebutton,
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_orange_500));
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                downvotebutton, getTintColor);
                                        upvotebutton.setContentDescription(
                                                mContext.getString(R.string.btn_upvoted));

                                        AnimatorUtil.setFlashAnimation(
                                                holder.itemView,
                                                upvotebutton,
                                                ContextCompat.getColor(
                                                        mContext, R.color.md_orange_500));
                                        holder.score.setTypeface(null, Typeface.BOLD);

                                        new Vote(true, points, mContext).execute(submission);
                                        ActionStates.setVoteDirection(
                                                submission, VoteDirection.UPVOTE);

                                    } else { // un-upvoted a post
                                        points.setTextColor(comments.getCurrentTextColor());
                                        new Vote(points, mContext).execute(submission);
                                        holder.score.setTypeface(null, Typeface.NORMAL);
                                        ActionStates.setVoteDirection(
                                                submission, VoteDirection.NO_VOTE);
                                        BlendModeUtil.tintImageViewAsSrcAtop(
                                                upvotebutton, getTintColor);
                                        upvotebutton.setContentDescription(
                                                mContext.getString(R.string.btn_upvote));
                                    }
                                    setSubmissionScoreText(submission, holder);
                                    if (!full
                                            && !SettingValues.actionbarVisible
                                            && SettingValues.defaultCardView
                                                    != CreateCardView.CardEnum.DESKTOP) {
                                        CreateCardView.toggleActionbar(holder.itemView);
                                    }
                                }
                            });
                }
            } else {
                upvotebutton.setVisibility(View.GONE);
                downvotebutton.setVisibility(View.GONE);
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        final View edit = holder.edit;

        if (Authentication.name != null
                && Authentication.name
                        .toLowerCase(Locale.ENGLISH)
                        .equals(submission.getAuthor().toLowerCase(Locale.ENGLISH))
                && Authentication.didOnline) {
            edit.setVisibility(View.VISIBLE);
            edit.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            new AsyncTask<Void, Void, ArrayList<String>>() {
                                List<FlairTemplate> flairlist;

                                @Override
                                protected ArrayList<String> doInBackground(Void... params) {
                                    FlairReference allFlairs =
                                            new FluentRedditClient(Authentication.reddit)
                                                    .subreddit(submission.getSubredditName())
                                                    .flair();
                                    try {
                                        flairlist = allFlairs.options(submission);
                                        final ArrayList<String> finalFlairs = new ArrayList<>();
                                        for (FlairTemplate temp : flairlist) {
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
                                    final boolean flair = (data != null && !data.isEmpty());

                                    int[] attrs = new int[] {R.attr.tintColor};
                                    TypedArray ta = mContext.obtainStyledAttributes(attrs);

                                    final int color2 = ta.getColor(0, Color.WHITE);
                                    Drawable edit_drawable =
                                            mContext.getResources().getDrawable(R.drawable.ic_edit);
                                    Drawable nsfw_drawable =
                                            mContext.getResources()
                                                    .getDrawable(R.drawable.ic_visibility_off);
                                    Drawable delete_drawable =
                                            mContext.getResources()
                                                    .getDrawable(R.drawable.ic_delete);
                                    Drawable flair_drawable =
                                            mContext.getResources()
                                                    .getDrawable(R.drawable.ic_text_fields);

                                    final List<Drawable> drawableSet =
                                            Arrays.asList(
                                                    edit_drawable,
                                                    nsfw_drawable,
                                                    delete_drawable,
                                                    flair_drawable);
                                    BlendModeUtil.tintDrawablesAsSrcAtop(drawableSet, color2);

                                    ta.recycle();

                                    BottomSheet.Builder b =
                                            new BottomSheet.Builder(mContext)
                                                    .title(
                                                            CompatUtil.fromHtml(
                                                                    submission.getTitle()));

                                    if (submission.isSelfPost()) {
                                        b.sheet(
                                                1,
                                                edit_drawable,
                                                mContext.getString(R.string.edit_selftext));
                                    }
                                    if (submission.isNsfw()) {
                                        b.sheet(
                                                4,
                                                nsfw_drawable,
                                                mContext.getString(R.string.mod_btn_unmark_nsfw));
                                    } else {
                                        b.sheet(
                                                4,
                                                nsfw_drawable,
                                                mContext.getString(R.string.mod_btn_mark_nsfw));
                                    }
                                    if (submission.getDataNode().get("spoiler").asBoolean()) {
                                        b.sheet(
                                                5,
                                                nsfw_drawable,
                                                mContext.getString(
                                                        R.string.mod_btn_unmark_spoiler));
                                    } else {
                                        b.sheet(
                                                5,
                                                nsfw_drawable,
                                                mContext.getString(R.string.mod_btn_mark_spoiler));
                                    }

                                    b.sheet(
                                            2,
                                            delete_drawable,
                                            mContext.getString(R.string.delete_submission));

                                    if (flair) {
                                        b.sheet(
                                                3,
                                                flair_drawable,
                                                mContext.getString(R.string.set_submission_flair));
                                    }

                                    b.listener(
                                                    new DialogInterface.OnClickListener() {

                                                        @Override
                                                        public void onClick(
                                                                DialogInterface dialog, int which) {
                                                            switch (which) {
                                                                case 1:
                                                                    {
                                                                        LayoutInflater inflater =
                                                                                mContext
                                                                                        .getLayoutInflater();

                                                                        final View dialoglayout =
                                                                                inflater.inflate(
                                                                                        R.layout
                                                                                                .edit_comment,
                                                                                        null);

                                                                        final EditText e =
                                                                                dialoglayout
                                                                                        .findViewById(
                                                                                                R.id
                                                                                                        .entry);
                                                                        e.setText(
                                                                                StringEscapeUtils
                                                                                        .unescapeHtml4(
                                                                                                submission
                                                                                                        .getSelftext()));

                                                                        DoEditorActions.doActions(
                                                                                e,
                                                                                dialoglayout,
                                                                                ((AppCompatActivity)
                                                                                                mContext)
                                                                                        .getSupportFragmentManager(),
                                                                                mContext,
                                                                                null,
                                                                                null);

                                                                        final AlertDialog.Builder
                                                                                builder =
                                                                                        new AlertDialog
                                                                                                        .Builder(
                                                                                                        mContext)
                                                                                                .setCancelable(
                                                                                                        false)
                                                                                                .setView(
                                                                                                        dialoglayout);
                                                                        final Dialog d =
                                                                                builder.create();
                                                                        d.getWindow()
                                                                                .setSoftInputMode(
                                                                                        WindowManager
                                                                                                .LayoutParams
                                                                                                .SOFT_INPUT_ADJUST_RESIZE);

                                                                        d.show();
                                                                        dialoglayout
                                                                                .findViewById(
                                                                                        R.id.cancel)
                                                                                .setOnClickListener(
                                                                                        new View
                                                                                                .OnClickListener() {
                                                                                            @Override
                                                                                            public
                                                                                            void
                                                                                                    onClick(
                                                                                                            View
                                                                                                                    v) {
                                                                                                d
                                                                                                        .dismiss();
                                                                                            }
                                                                                        });
                                                                        dialoglayout
                                                                                .findViewById(
                                                                                        R.id.submit)
                                                                                .setOnClickListener(
                                                                                        new View
                                                                                                .OnClickListener() {
                                                                                            @Override
                                                                                            public
                                                                                            void
                                                                                                    onClick(
                                                                                                            View
                                                                                                                    v) {
                                                                                                final
                                                                                                String
                                                                                                        text =
                                                                                                                e.getText()
                                                                                                                        .toString();
                                                                                                new AsyncTask<
                                                                                                        Void,
                                                                                                        Void,
                                                                                                        Void>() {
                                                                                                    @Override
                                                                                                    protected
                                                                                                    Void
                                                                                                            doInBackground(
                                                                                                                    Void
                                                                                                                                    ...
                                                                                                                            params) {
                                                                                                        try {
                                                                                                            new AccountManager(
                                                                                                                            Authentication
                                                                                                                                    .reddit)
                                                                                                                    .updateContribution(
                                                                                                                            submission,
                                                                                                                            text);
                                                                                                            if (adapter
                                                                                                                    != null) {
                                                                                                                adapter
                                                                                                                        .dataSet
                                                                                                                        .reloadSubmission(
                                                                                                                                adapter);
                                                                                                            }
                                                                                                            d
                                                                                                                    .dismiss();
                                                                                                        } catch (
                                                                                                                Exception
                                                                                                                        e) {
                                                                                                            (mContext)
                                                                                                                    .runOnUiThread(
                                                                                                                            new Runnable() {
                                                                                                                                @Override
                                                                                                                                public
                                                                                                                                void
                                                                                                                                        run() {
                                                                                                                                    new AlertDialog
                                                                                                                                                    .Builder(
                                                                                                                                                    mContext)
                                                                                                                                            .setTitle(
                                                                                                                                                    R
                                                                                                                                                            .string
                                                                                                                                                            .comment_delete_err)
                                                                                                                                            .setMessage(
                                                                                                                                                    R
                                                                                                                                                            .string
                                                                                                                                                            .comment_delete_err_msg)
                                                                                                                                            .setPositiveButton(
                                                                                                                                                    R
                                                                                                                                                            .string
                                                                                                                                                            .btn_yes,
                                                                                                                                                    (dialog1,
                                                                                                                                                            which1) -> {
                                                                                                                                                        dialog1
                                                                                                                                                                .dismiss();
                                                                                                                                                        doInBackground();
                                                                                                                                                    })
                                                                                                                                            .setNegativeButton(
                                                                                                                                                    R
                                                                                                                                                            .string
                                                                                                                                                            .btn_no,
                                                                                                                                                    (dialog12,
                                                                                                                                                            which12) ->
                                                                                                                                                            dialog12
                                                                                                                                                                    .dismiss())
                                                                                                                                            .show();
                                                                                                                                }
                                                                                                                            });
                                                                                                        }
                                                                                                        return null;
                                                                                                    }

                                                                                                    @Override
                                                                                                    protected
                                                                                                    void
                                                                                                            onPostExecute(
                                                                                                                    Void
                                                                                                                            aVoid) {
                                                                                                        if (adapter
                                                                                                                != null) {
                                                                                                            adapter
                                                                                                                    .notifyItemChanged(
                                                                                                                            1);
                                                                                                        }
                                                                                                    }
                                                                                                }.executeOnExecutor(
                                                                                                        AsyncTask
                                                                                                                .THREAD_POOL_EXECUTOR);
                                                                                            }
                                                                                        });
                                                                    }
                                                                    break;
                                                                case 2:
                                                                    {
                                                                        new AlertDialog.Builder(
                                                                                        mContext)
                                                                                .setTitle(
                                                                                        R.string
                                                                                                .really_delete_submission)
                                                                                .setPositiveButton(
                                                                                        R.string
                                                                                                .btn_yes,
                                                                                        (dialog13,
                                                                                                which13) ->
                                                                                                new AsyncTask<
                                                                                                        Void,
                                                                                                        Void,
                                                                                                        Void>() {
                                                                                                    @Override
                                                                                                    protected
                                                                                                    Void
                                                                                                            doInBackground(
                                                                                                                    Void
                                                                                                                                    ...
                                                                                                                            params) {
                                                                                                        try {
                                                                                                            new ModerationManager(
                                                                                                                            Authentication
                                                                                                                                    .reddit)
                                                                                                                    .delete(
                                                                                                                            submission);
                                                                                                        } catch (
                                                                                                                ApiException
                                                                                                                        e) {
                                                                                                            e
                                                                                                                    .printStackTrace();
                                                                                                        }
                                                                                                        return null;
                                                                                                    }

                                                                                                    @Override
                                                                                                    protected
                                                                                                    void
                                                                                                            onPostExecute(
                                                                                                                    Void
                                                                                                                            aVoid) {
                                                                                                        (mContext)
                                                                                                                .runOnUiThread(
                                                                                                                        new Runnable() {
                                                                                                                            @Override
                                                                                                                            public
                                                                                                                            void
                                                                                                                                    run() {
                                                                                                                                (holder.title)
                                                                                                                                        .setTextHtml(
                                                                                                                                                mContext
                                                                                                                                                        .getString(
                                                                                                                                                                R
                                                                                                                                                                        .string
                                                                                                                                                                        .content_deleted));
                                                                                                                                if (holder.firstTextView
                                                                                                                                        != null) {
                                                                                                                                    holder
                                                                                                                                            .firstTextView
                                                                                                                                            .setText(
                                                                                                                                                    R
                                                                                                                                                            .string
                                                                                                                                                            .content_deleted);
                                                                                                                                    holder
                                                                                                                                            .commentOverflow
                                                                                                                                            .setVisibility(
                                                                                                                                                    View
                                                                                                                                                            .GONE);
                                                                                                                                } else {
                                                                                                                                    if (holder
                                                                                                                                                    .itemView
                                                                                                                                                    .findViewById(
                                                                                                                                                            R
                                                                                                                                                                    .id
                                                                                                                                                                    .body)
                                                                                                                                            != null) {
                                                                                                                                        ((TextView)
                                                                                                                                                        holder
                                                                                                                                                                .itemView
                                                                                                                                                                .findViewById(
                                                                                                                                                                        R
                                                                                                                                                                                .id
                                                                                                                                                                                .body))
                                                                                                                                                .setText(
                                                                                                                                                        R
                                                                                                                                                                .string
                                                                                                                                                                .content_deleted);
                                                                                                                                    }
                                                                                                                                }
                                                                                                                            }
                                                                                                                        });
                                                                                                    }
                                                                                                }.executeOnExecutor(
                                                                                                        AsyncTask
                                                                                                                .THREAD_POOL_EXECUTOR))
                                                                                .setNegativeButton(
                                                                                        R.string
                                                                                                .btn_cancel,
                                                                                        null)
                                                                                .show();
                                                                    }
                                                                    break;
                                                                case 3:
                                                                    {
                                                                        new MaterialDialog.Builder(
                                                                                        mContext)
                                                                                .items(data)
                                                                                .title(
                                                                                        R.string
                                                                                                .sidebar_select_flair)
                                                                                .itemsCallback(
                                                                                        new MaterialDialog
                                                                                                .ListCallback() {
                                                                                            @Override
                                                                                            public
                                                                                            void
                                                                                                    onSelection(
                                                                                                            MaterialDialog
                                                                                                                    dialog,
                                                                                                            View
                                                                                                                    itemView,
                                                                                                            int
                                                                                                                    which,
                                                                                                            CharSequence
                                                                                                                    text) {
                                                                                                final
                                                                                                FlairTemplate
                                                                                                        t =
                                                                                                                flairlist
                                                                                                                        .get(
                                                                                                                                which);
                                                                                                if (t
                                                                                                        .isTextEditable()) {
                                                                                                    new MaterialDialog
                                                                                                                    .Builder(
                                                                                                                    mContext)
                                                                                                            .title(
                                                                                                                    R
                                                                                                                            .string
                                                                                                                            .mod_btn_submission_flair_text)
                                                                                                            .input(
                                                                                                                    mContext
                                                                                                                            .getString(
                                                                                                                                    R
                                                                                                                                            .string
                                                                                                                                            .mod_flair_hint),
                                                                                                                    t
                                                                                                                            .getText(),
                                                                                                                    true,
                                                                                                                    (dialog14,
                                                                                                                            input) -> {})
                                                                                                            .positiveText(
                                                                                                                    R
                                                                                                                            .string
                                                                                                                            .btn_set)
                                                                                                            .onPositive(
                                                                                                                    new MaterialDialog
                                                                                                                            .SingleButtonCallback() {
                                                                                                                        @Override
                                                                                                                        public
                                                                                                                        void
                                                                                                                                onClick(
                                                                                                                                        MaterialDialog
                                                                                                                                                dialog,
                                                                                                                                        DialogAction
                                                                                                                                                which) {
                                                                                                                            final
                                                                                                                            String
                                                                                                                                    flair =
                                                                                                                                            dialog.getInputEditText()
                                                                                                                                                    .getText()
                                                                                                                                                    .toString();
                                                                                                                            new AsyncTask<
                                                                                                                                    Void,
                                                                                                                                    Void,
                                                                                                                                    Boolean>() {
                                                                                                                                @Override
                                                                                                                                protected
                                                                                                                                Boolean
                                                                                                                                        doInBackground(
                                                                                                                                                Void
                                                                                                                                                                ...
                                                                                                                                                        params) {
                                                                                                                                    try {
                                                                                                                                        new ModerationManager(
                                                                                                                                                        Authentication
                                                                                                                                                                .reddit)
                                                                                                                                                .setFlair(
                                                                                                                                                        submission
                                                                                                                                                                .getSubredditName(),
                                                                                                                                                        t,
                                                                                                                                                        flair,
                                                                                                                                                        submission);
                                                                                                                                        return true;
                                                                                                                                    } catch (
                                                                                                                                            ApiException
                                                                                                                                                    e) {
                                                                                                                                        e
                                                                                                                                                .printStackTrace();
                                                                                                                                        return false;
                                                                                                                                    }
                                                                                                                                }

                                                                                                                                @Override
                                                                                                                                protected
                                                                                                                                void
                                                                                                                                        onPostExecute(
                                                                                                                                                Boolean
                                                                                                                                                        done) {
                                                                                                                                    Snackbar
                                                                                                                                            s =
                                                                                                                                                    null;
                                                                                                                                    if (done) {
                                                                                                                                        if (holder.itemView
                                                                                                                                                != null) {
                                                                                                                                            s =
                                                                                                                                                    Snackbar
                                                                                                                                                            .make(
                                                                                                                                                                    holder.itemView,
                                                                                                                                                                    R
                                                                                                                                                                            .string
                                                                                                                                                                            .snackbar_flair_success,
                                                                                                                                                                    Snackbar
                                                                                                                                                                            .LENGTH_SHORT);
                                                                                                                                            SubmissionCache
                                                                                                                                                    .updateTitleFlair(
                                                                                                                                                            submission,
                                                                                                                                                            flair,
                                                                                                                                                            mContext);
                                                                                                                                            holder
                                                                                                                                                    .title
                                                                                                                                                    .setText(
                                                                                                                                                            SubmissionCache
                                                                                                                                                                    .getTitleLine(
                                                                                                                                                                            submission,
                                                                                                                                                                            mContext));
                                                                                                                                            // Force the title view to re-measure itself
                                                                                                                                            holder.title.requestLayout();
                                                                                                                                        }
                                                                                                                                    } else {
                                                                                                                                        if (holder.itemView
                                                                                                                                                != null) {
                                                                                                                                            s =
                                                                                                                                                    Snackbar
                                                                                                                                                            .make(
                                                                                                                                                                    holder.itemView,
                                                                                                                                                                    R
                                                                                                                                                                            .string
                                                                                                                                                                            .snackbar_flair_error,
                                                                                                                                                                    Snackbar
                                                                                                                                                                            .LENGTH_SHORT);
                                                                                                                                        }
                                                                                                                                    }
                                                                                                                                    if (s
                                                                                                                                            != null) {
                                                                                                                                        LayoutUtils
                                                                                                                                                .showSnackbar(
                                                                                                                                                        s);
                                                                                                                                    }
                                                                                                                                }
                                                                                                                            }.executeOnExecutor(
                                                                                                                                    AsyncTask
                                                                                                                                            .THREAD_POOL_EXECUTOR);
                                                                                                                        }
                                                                                                                    })
                                                                                                            .negativeText(
                                                                                                                    R
                                                                                                                            .string
                                                                                                                            .btn_cancel)
                                                                                                            .show();
                                                                                                } else {
                                                                                                    new AsyncTask<
                                                                                                            Void,
                                                                                                            Void,
                                                                                                            Boolean>() {
                                                                                                        @Override
                                                                                                        protected
                                                                                                        Boolean
                                                                                                                doInBackground(
                                                                                                                        Void
                                                                                                                                        ...
                                                                                                                                params) {
                                                                                                            try {
                                                                                                                new ModerationManager(
                                                                                                                                Authentication
                                                                                                                                        .reddit)
                                                                                                                        .setFlair(
                                                                                                                                submission
                                                                                                                                        .getSubredditName(),
                                                                                                                                t,
                                                                                                                                null,
                                                                                                                                submission);
                                                                                                                return true;
                                                                                                            } catch (
                                                                                                                    ApiException
                                                                                                                            e) {
                                                                                                                e
                                                                                                                        .printStackTrace();
                                                                                                                return false;
                                                                                                            }
                                                                                                        }

                                                                                                        @Override
                                                                                                        protected
                                                                                                        void
                                                                                                                onPostExecute(
                                                                                                                        Boolean
                                                                                                                                done) {
                                                                                                            Snackbar
                                                                                                                    s =
                                                                                                                            null;
                                                                                                            if (done) {
                                                                                                                if (holder.itemView
                                                                                                                        != null) {
                                                                                                                    s =
                                                                                                                            Snackbar
                                                                                                                                    .make(
                                                                                                                                            holder.itemView,
                                                                                                                                            R
                                                                                                                                                    .string
                                                                                                                                                    .snackbar_flair_success,
                                                                                                                                            Snackbar
                                                                                                                                                    .LENGTH_SHORT);
                                                                                                                    SubmissionCache
                                                                                                                            .updateTitleFlair(
                                                                                                                                    submission,
                                                                                                                                    t
                                                                                                                                            .getCssClass(),
                                                                                                                                    mContext);
                                                                                                                    holder
                                                                                                                            .title
                                                                                                                            .setText(
                                                                                                                                    SubmissionCache
                                                                                                                                            .getTitleLine(
                                                                                                                                                    submission,
                                                                                                                                                    mContext));
                                                                                                                    // Force the title view to re-measure itself
                                                                                                                    holder.title.requestLayout();
                                                                                                                }
                                                                                                            } else {
                                                                                                                if (holder.itemView
                                                                                                                        != null) {
                                                                                                                    s =
                                                                                                                            Snackbar
                                                                                                                                    .make(
                                                                                                                                            holder.itemView,
                                                                                                                                            R
                                                                                                                                                    .string
                                                                                                                                                    .snackbar_flair_error,
                                                                                                                                            Snackbar
                                                                                                                                                    .LENGTH_SHORT);
                                                                                                                }
                                                                                                            }
                                                                                                            if (s
                                                                                                                    != null) {
                                                                                                                LayoutUtils
                                                                                                                        .showSnackbar(
                                                                                                                                s);
                                                                                                            }
                                                                                                        }
                                                                                                    }.executeOnExecutor(
                                                                                                            AsyncTask
                                                                                                                    .THREAD_POOL_EXECUTOR);
                                                                                                }
                                                                                            }
                                                                                        })
                                                                                .show();
                                                                    }
                                                                    break;
                                                                case 4:
                                                                    if (submission.isNsfw()) {
                                                                        SubmissionModActions.unNsfwSubmission(
                                                                                mContext,
                                                                                submission,
                                                                                holder);
                                                                    } else {
                                                                        SubmissionModActions.setPostNsfw(
                                                                                mContext,
                                                                                submission,
                                                                                holder);
                                                                    }
                                                                    break;
                                                                case 5:
                                                                    if (submission
                                                                            .getDataNode()
                                                                            .get("spoiler")
                                                                            .asBoolean()) {
                                                                        SubmissionModActions.unSpoiler(
                                                                                mContext,
                                                                                submission,
                                                                                holder);
                                                                    } else {
                                                                        SubmissionModActions.setSpoiler(
                                                                                mContext,
                                                                                submission,
                                                                                holder);
                                                                    }
                                                                    break;
                                                            }
                                                        }
                                                    })
                                            .show();
                                }
                            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                        }
                    });
        } else {
            edit.setVisibility(View.GONE);
        }

        if (HasSeen.getSeen(submission) && !full) {
            holder.title.setAlpha(0.54f);
            holder.body.setAlpha(0.54f);
        } else {
            holder.title.setAlpha(1f);
            if (!full) {
                holder.body.setAlpha(1f);
            }
        }
    }

    private void setSubmissionScoreText(Submission submission, SubmissionViewHolder holder) {
        int submissionScore = submission.getScore();
        switch (ActionStates.getVoteDirection(submission)) {
            case UPVOTE:
                {
                    if (submission.getVote() != VoteDirection.UPVOTE) {
                        if (submission.getVote() == VoteDirection.DOWNVOTE) ++submissionScore;
                        ++submissionScore; // offset the score by +1
                    }
                    break;
                }
            case DOWNVOTE:
                {
                    if (submission.getVote() != VoteDirection.DOWNVOTE) {
                        if (submission.getVote() == VoteDirection.UPVOTE) --submissionScore;
                        --submissionScore; // offset the score by +1
                    }
                    break;
                }
            case NO_VOTE:
                if (submission.getVote() == VoteDirection.UPVOTE
                        && submission.getAuthor().equalsIgnoreCase(Authentication.name)) {
                    submissionScore--;
                }
                break;
        }

        // if the submission is already at 0pts, keep it at 0pts
        submissionScore = Math.max(submissionScore, 0);
        if (submissionScore >= 10000 && SettingValues.abbreviateScores) {
            holder.score.setText(
                    String.format(
                            Locale.getDefault(), "%.1fk", (((double) submissionScore) / 1000)));
        } else {
            holder.score.setText(String.format(Locale.getDefault(), "%d", submissionScore));
        }
    }

    private void setViews(String rawHTML, String subredditName, SubmissionViewHolder holder, Submission submission) {
        if (rawHTML.isEmpty()) {
            return;
        }

        List<String> blocks = SubmissionParser.getBlocks(rawHTML);

        int startIndex = 0;
        if (!blocks.get(0).startsWith("<table>") && !blocks.get(0).startsWith("<pre>")) {
            holder.firstTextView.setTextHtml(blocks.get(0), subredditName, submission);
            startIndex = 1;
        }

        if (blocks.size() > 1) {
            if (startIndex == 0) {
                holder.commentOverflow.setViews(blocks, subredditName);
            } else {
                holder.commentOverflow.setViews(
                        blocks.subList(startIndex, blocks.size()), subredditName);
            }
        }
    }

}