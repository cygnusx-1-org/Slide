package me.edgan.redditslide;

import android.text.style.URLSpan;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

public interface ClickableText {
    /**
     * Callback for when a link is clicked
     *
     * @param url the url link (e.g. #s for some spoilers)
     * @param xOffset the last index of the url text (not the link)
     * @param subreddit
     */
    void onLinkClick(@Nullable String url, int xOffset, String subreddit, URLSpan span);

    /**
     * @param event the touch that started the long press, or null if the handler has not seen one
     *     yet. No implementation reads it.
     */
    void onLinkLongClick(@Nullable String url, @Nullable MotionEvent event);
}
