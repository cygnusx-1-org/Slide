package me.edgan.redditslide.Views;

import android.content.Context;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import me.edgan.redditslide.util.LogUtil;
import org.jspecify.annotations.NullMarked;

/**
 * Created by carlo_000 on 10/12/2015.
 *
 * <p>See {@link PreCachingLayoutManager}: the extra-layout-space override is long disabled, so this
 * is a LinearLayoutManager that swallows RecyclerView's IndexOutOfBoundsException.
 */
@NullMarked
public class PreCachingLayoutManagerComments extends LinearLayoutManager {
    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            super.onLayoutChildren(recycler, state);
        } catch (IndexOutOfBoundsException e) {
            LogUtil.v("Met a IOOBE in RecyclerView");
        }
    }

    public PreCachingLayoutManagerComments(Context context) {
        super(context);
    }

    public PreCachingLayoutManagerComments(
            Context context, int orientation, boolean reverseLayout) {
        super(context, orientation, reverseLayout);
    }
}
