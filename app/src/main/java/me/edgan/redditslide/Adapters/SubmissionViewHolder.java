package me.edgan.redditslide.Adapters;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.SubmissionViews.HeaderImageLinkView;

/** Created by ccrama on 9/17/2015. */
public abstract class SubmissionViewHolder extends RecyclerView.ViewHolder {
    // Only the eleven ids that every submission layout actually defines live here. The five
    // layouts this holder is bound to do not agree on the rest: the four feed cards
    // (submission_list, submission_largecard, submission_largecard_middle,
    // submission_list_desktop) have no firstTextView/commentOverflow, and submission_fullscreen
    // has no body/hide. Those four sit on CardSubmissionViewHolder and FullSubmissionViewHolder
    // instead, so the layout variant is carried by the type rather than by
    // PopulateSubmissionViewHolder's `full` boolean.
    //
    // Compute that split from android:id attributes, never from a grep for "@+id/x": a layout
    // param such as android:layout_below="@+id/innerrelative" declares the id without creating a
    // view, which is exactly how innerrelative read as present in submission_fullscreen when it
    // is not. See NULLAWAY.md phase 14.
    public final SpoilerRobotoTextView title;
    public final TextView score;
    public final TextView comments;
    public final View menu;
    public final View mod;
    public final View upvote;
    public final View thumbimage;
    public final View downvote;
    public final View edit;
    public final HeaderImageLinkView leadImage;
    public final View save;

    protected SubmissionViewHolder(View v) {
        super(v);
        title = v.requireViewById(R.id.title);
        menu = v.requireViewById(R.id.menu);
        mod = v.requireViewById(R.id.mod);
        downvote = v.requireViewById(R.id.downvote);
        upvote = v.requireViewById(R.id.upvote);
        leadImage = v.requireViewById(R.id.headerimage);
        thumbimage = v.requireViewById(R.id.thumbimage2);
        save = v.requireViewById(R.id.save);
        edit = v.requireViewById(R.id.edit);
        score = v.requireViewById(R.id.score);
        comments = v.requireViewById(R.id.comments);
    }
}
