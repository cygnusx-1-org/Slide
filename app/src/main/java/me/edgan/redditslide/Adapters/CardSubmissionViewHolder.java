package me.edgan.redditslide.Adapters;

import android.view.View;
import android.widget.ImageView;

import androidx.annotation.Nullable;

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

    /**
     * The body's seat under the lead image, for a post whose card leads with its picture — see
     * SubmissionCache.selftextPreviewBelowLeadImage. Only submission_largecard_middle has one: the
     * other three cards already draw the image above the title, so their body is under it wherever
     * it goes.
     */
    @Nullable public final SpoilerRobotoTextView bodyBelow;

    public CardSubmissionViewHolder(View v) {
        super(v);
        body = v.requireViewById(R.id.body);
        hide = v.requireViewById(R.id.hide);
        bodyBelow = v.findViewById(R.id.body_below);
    }

    /**
     * Fades the selftext preview, for a post that has been seen. Both seats, because the one the
     * preview is not in is gone: dimming only {@link #body} left a seen post whose text sits under
     * its picture reading at full strength beside a faded title.
     */
    public void setBodyAlpha(float alpha) {
        body.setAlpha(alpha);
        if (bodyBelow != null) {
            bodyBelow.setAlpha(alpha);
        }
    }
}
