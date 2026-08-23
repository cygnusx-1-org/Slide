package me.edgan.redditslide.Adapters;

import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.CommentOverflow;

/** Created by ccrama on 9/17/2015. */
public class CommentViewHolder extends RecyclerView.ViewHolder {
    public final TextView childrenNumber;
    public final View dot;
    public final LinearLayout menuArea;
    public final int textColorUp;
    public final TextView content;
    public final int textColorDown;
    public final int textColorRegular;
    public final SpoilerRobotoTextView firstTextView;
    public final CommentOverflow commentOverflow;
    public final View background;
    public final ImageView imageFlair;

    public CommentViewHolder(View v) {
        super(v);
        background = v.requireViewById(R.id.background);
        dot = v.requireViewById(R.id.dot);
        menuArea = v.requireViewById(R.id.menuarea);
        childrenNumber = v.requireViewById(R.id.commentnumber);
        firstTextView = v.requireViewById(R.id.firstTextView);
        textColorDown = ContextCompat.getColor(v.getContext(), R.color.md_blue_500);
        textColorRegular = firstTextView.getCurrentTextColor();
        textColorUp = ContextCompat.getColor(v.getContext(), R.color.md_orange_500);
        content = v.requireViewById(R.id.content);
        imageFlair = v.requireViewById(R.id.flair);
        commentOverflow = v.requireViewById(R.id.commentOverflow);
    }
}
