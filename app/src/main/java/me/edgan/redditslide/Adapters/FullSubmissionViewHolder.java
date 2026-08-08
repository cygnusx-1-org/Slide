package me.edgan.redditslide.Adapters;

import android.view.View;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.CommentOverflow;

/**
 * The holder for submission_fullscreen — the comments screen's header — which is the only layout
 * that renders the whole selftext, so it is the only one with firstTextView and commentOverflow.
 * See NULLAWAY.md phase 14.
 */
public class FullSubmissionViewHolder extends SubmissionViewHolder {
    public final SpoilerRobotoTextView firstTextView;
    public final CommentOverflow commentOverflow;

    public FullSubmissionViewHolder(View v) {
        super(v);
        firstTextView = v.requireViewById(R.id.firstTextView);
        commentOverflow = v.requireViewById(R.id.commentOverflow);
    }
}
