package me.edgan.redditslide.Adapters;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.CommentOverflow;

/** Created by ccrama on 9/17/2015. */
public class ProfileCommentViewHolder extends RecyclerView.ViewHolder {
    public final TextView title;
    public final TextView user;
    public final TextView score;
    public final TextView time;
    public final View gild;
    public final SpoilerRobotoTextView content;
    public final CommentOverflow overflow;

    public ProfileCommentViewHolder(View v) {
        super(v);
        title = v.requireViewById(R.id.title);
        user = v.requireViewById(R.id.user);
        score = v.requireViewById(R.id.score);
        time = v.requireViewById(R.id.time);
        gild = v.requireViewById(R.id.gildtext);
        content = v.requireViewById(R.id.content);
        overflow = v.requireViewById(R.id.commentOverflow);
    }
}
