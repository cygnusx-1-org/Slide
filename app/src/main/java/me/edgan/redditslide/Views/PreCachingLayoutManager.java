package me.edgan.redditslide.Views;

import android.content.Context;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.edgan.redditslide.util.LogUtil;
import org.jspecify.annotations.NullMarked;

/**
 * Created by carlo_000 on 10/12/2015.
 *
 * <p>The extra-layout-space override this is named for was commented out long ago, so what the
 * class actually does now is swallow the IndexOutOfBoundsException RecyclerView throws when an
 * adapter changes under it. The fields that fed the disabled override were dropped with it.
 */
@NullMarked
public class PreCachingLayoutManager extends LinearLayoutManager {
    public PreCachingLayoutManager(Context context) {
        super(context);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            super.onLayoutChildren(recycler, state);
        } catch (IndexOutOfBoundsException e) {
            LogUtil.v("Met a IOOBE in RecyclerView");
        }
    }

    public PreCachingLayoutManager(Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
    }
}
