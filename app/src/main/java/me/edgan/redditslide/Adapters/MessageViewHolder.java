package me.edgan.redditslide.Adapters;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.CommentOverflow;

/** Created by ccrama on 9/17/2015. */
public class MessageViewHolder extends RecyclerView.ViewHolder {
    public final TextView title;
    public final SpoilerRobotoTextView content;
    public final TextView time;
    public final TextView user;
    public final CommentOverflow commentOverflow;

    public MessageViewHolder(View v) {
        super(v);
        title = v.requireViewById(R.id.title);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        content = v.requireViewById(R.id.content);
        time = v.requireViewById(R.id.time);
        commentOverflow = v.requireViewById(R.id.commentOverflow);
        user = v.requireViewById(R.id.user);
    }
}
