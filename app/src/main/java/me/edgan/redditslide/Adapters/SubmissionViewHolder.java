package me.edgan.redditslide.Adapters;

import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.SubmissionViews.HeaderImageLinkView;
import me.edgan.redditslide.Views.CommentOverflow;

/** Created by ccrama on 9/17/2015. */
public class SubmissionViewHolder extends RecyclerView.ViewHolder {
    public final SpoilerRobotoTextView title;
    public final TextView contentTitle;
    public final TextView contentURL;
    public final TextView score;
    public final TextView comments;
    public final View menu;
    public final View mod;
    public final View hide;
    public final View upvote;
    public final View thumbimage;
    public final View secondMenu;
    public final View downvote;
    public final View edit;
    public final HeaderImageLinkView leadImage;
    public final SpoilerRobotoTextView firstTextView;
    public final CommentOverflow commentOverflow;
    public final View save;
    public final SpoilerRobotoTextView body;
    public final RelativeLayout innerRelative;

    public SubmissionViewHolder(View v) {
        super(v);
        // Deliberately findViewById, not requireViewById. This holder is bound to five layouts and
        // none of them defines all eighteen ids: the four feed cards (submission_list,
        // submission_largecard, submission_largecard_middle, submission_list_desktop) have no
        // contenttitle/contenturl/firstTextView/commentOverflow, and submission_fullscreen has no
        // hide/secondMenu/body. Seven of these fields are therefore null about half the time.
        //
        // That is not a bug: PopulateSubmissionViewHolder's `full` flag already picks the matching
        // branch for whichever layout is in the holder, so the null half is never dereferenced.
        // The invariant just lives in a boolean instead of in the types — annotating the seven
        // @Nullable compiles to 14 findings, every one inside a branch `full` already selected.
        // See NULLAWAY.md phase 14.
        title = v.findViewById(R.id.title);
        hide = v.findViewById(R.id.hide);
        menu = v.findViewById(R.id.menu);
        mod = v.findViewById(R.id.mod);
        downvote = v.findViewById(R.id.downvote);
        upvote = v.findViewById(R.id.upvote);
        leadImage = v.findViewById(R.id.headerimage);
        contentTitle = v.findViewById(R.id.contenttitle);
        secondMenu = v.findViewById(R.id.secondMenu);
        thumbimage = v.findViewById(R.id.thumbimage2);
        contentURL = v.findViewById(R.id.contenturl);
        save = v.findViewById(R.id.save);
        edit = v.findViewById(R.id.edit);
        body = v.findViewById(R.id.body);
        score = v.findViewById(R.id.score);
        comments = v.findViewById(R.id.comments);
        firstTextView = v.findViewById(R.id.firstTextView);
        commentOverflow = v.findViewById(R.id.commentOverflow);
        innerRelative = v.findViewById(R.id.innerrelative);
    }
}
