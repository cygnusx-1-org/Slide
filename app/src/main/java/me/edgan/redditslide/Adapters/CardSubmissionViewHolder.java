package me.edgan.redditslide.Adapters;

import android.view.View;
import android.widget.ImageView;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SpoilerRobotoTextView;

/**
 * The holder for the four feed-card layouts, which are the ones that carry a selftext preview and
 * a hide button. submission_fullscreen has neither {@code body} nor {@code hide}, which is why
 * these two cannot live on the base class. See NULLAWAY.md phase 14.
 */
public class CardSubmissionViewHolder extends SubmissionViewHolder {
    public final SpoilerRobotoTextView body;
    public final ImageView hide;

    public CardSubmissionViewHolder(View v) {
        super(v);
        body = v.requireViewById(R.id.body);
        hide = v.requireViewById(R.id.hide);
    }
}
