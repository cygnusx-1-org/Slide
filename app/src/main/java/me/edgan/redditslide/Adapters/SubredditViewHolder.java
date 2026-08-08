package me.edgan.redditslide.Adapters;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.CommentOverflow;

/** Created by ccrama on 9/17/2015. */
public class SubredditViewHolder extends RecyclerView.ViewHolder {
    public final SpoilerRobotoTextView body;
    public final CommentOverflow overflow;
    public final View color;
    public final TextView name;
    public final TextView subscribers;
    public final View subbed;

    public SubredditViewHolder(View v) {
        super(v);
        color = v.requireViewById(R.id.color);
        name = v.requireViewById(R.id.name);
        subscribers = v.requireViewById(R.id.subscribers);
        subbed = v.requireViewById(R.id.subbed);
        body = v.requireViewById(R.id.body);
        overflow = v.requireViewById(R.id.overflow);
    }
}
