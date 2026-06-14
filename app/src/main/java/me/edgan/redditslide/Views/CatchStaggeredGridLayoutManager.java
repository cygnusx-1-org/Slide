package me.edgan.redditslide.Views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import me.edgan.redditslide.util.LogUtil;

/** Created by carlo_000 on 4/8/2016. */
public class CatchStaggeredGridLayoutManager extends StaggeredGridLayoutManager {
    public CatchStaggeredGridLayoutManager(
            Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // Prevent items from being shuffled between spans when re-filling upward,
        // which causes visible "jumping" when scrolling back up.
        setGapStrategy(GAP_HANDLING_NONE);
    }

    public CatchStaggeredGridLayoutManager(int spanCount, int orientation) {
        super(spanCount, orientation);
        // Prevent items from being shuffled between spans when re-filling upward,
        // which causes visible "jumping" when scrolling back up.
        setGapStrategy(GAP_HANDLING_NONE);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            super.onLayoutChildren(recycler, state);
        } catch (IndexOutOfBoundsException e) {
            LogUtil.v("Met a IOOBE in RecyclerView");
        }
    }
}
