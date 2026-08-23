package me.edgan.redditslide.SwipeLayout.app;

import androidx.annotation.Nullable;

import me.edgan.redditslide.SwipeLayout.SwipeBackLayout;
import org.jspecify.annotations.NullMarked;

/**
 * @author Yrom
 */
@NullMarked
public interface SwipeBackActivityBase {
    /**
     * @return the SwipeBackLayout associated with this activity, or null where the implementation
     *     has swipe-back switched off — BaseActivity answers null when enableSwipeBackLayout is
     *     false, so this cannot be declared non-null.
     */
    @Nullable SwipeBackLayout getSwipeBackLayout();

    void setSwipeBackEnable(boolean enable);

    /** Scroll out contentView and finish the activity */
    void scrollToFinishActivity();
}
