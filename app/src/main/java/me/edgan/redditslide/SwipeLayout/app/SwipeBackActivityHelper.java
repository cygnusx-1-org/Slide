package me.edgan.redditslide.SwipeLayout.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.Nullable;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SwipeLayout.SwipeBackLayout;
import me.edgan.redditslide.SwipeLayout.Utils;
import org.jspecify.annotations.NullMarked;

/**
 * @author Yrom
 */
@NullMarked
public class SwipeBackActivityHelper {
    private Activity mActivity;

    // Inflated by onActivityCreate, which both call sites run one statement after constructing
    // this helper — BaseActivity.onCreate and SwipeBackActivity.onCreate.
    @SuppressWarnings("NullAway.Init")
    private SwipeBackLayout mSwipeBackLayout;

    public SwipeBackActivityHelper(Activity activity) {
        mActivity = activity;
    }

    public void onActivityCreate() {
        mActivity.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mActivity.getWindow().getDecorView().setBackground(null);
        mSwipeBackLayout =
                (SwipeBackLayout)
                        LayoutInflater.from(mActivity).inflate(R.layout.swipeback_layout, null);
        mSwipeBackLayout.addSwipeListener(
                new SwipeBackLayout.SwipeListener() {
                    @Override
                    public void onScrollStateChange(int state, float scrollPercent) {}

                    @Override
                    public void onEdgeTouch(int edgeFlag) {
                        Utils.convertActivityToTranslucent(mActivity);
                    }

                    @Override
                    public void onScrollOverThreshold() {}
                });
    }

    public void onPostCreate() {
        mSwipeBackLayout.attachToActivity(mActivity);
    }

    @Nullable public View findViewById(int id) {
        // The guard stays: onActivityCreate runs one statement after the constructor, but it is a
        // promise rather than something the compiler enforces, and a re-entrant lookup from the
        // window calls it made would land in that gap.
        if (mSwipeBackLayout != null) {
            return mSwipeBackLayout.findViewById(id);
        }
        return null;
    }

    public SwipeBackLayout getSwipeBackLayout() {
        return mSwipeBackLayout;
    }
}
