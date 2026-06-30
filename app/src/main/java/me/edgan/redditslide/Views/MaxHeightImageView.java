package me.edgan.redditslide.Views;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

/** Created by Carlos on 6/2/2016. */
public class MaxHeightImageView extends AppCompatImageView {
    public MaxHeightImageView(Context context) {
        super(context);
    }

    public MaxHeightImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MaxHeightImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // Cap tall feed images at ~1.5x the screen width instead of a flat 3200px. A feed lead image
    // is ~screen-width wide, so the old 3200 cap meant very tall images decoded at near-full
    // resolution (8-15 MB each), which made the in-memory cache hold almost nothing. Capping here
    // shrinks those decodes so the memory cache can actually retain images for scroll-back.
    public static final int maxHeight =
            Math.min((int) (Resources.getSystem().getDisplayMetrics().widthPixels * 1.5), 3200);

    /**
     * Image aspect ratio expressed as height/width. When set (> 0), the view derives its height
     * from its measured width at measure time, independent of the (asynchronously loaded) drawable.
     * This reserves the correct slot height up front so that loading the image never resizes the
     * view, which previously caused the feed to "jump" while scrolling up.
     */
    private double aspectRatio = 0;

    public void setAspectRatio(double ratio) {
        if (aspectRatio != ratio) {
            aspectRatio = ratio;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (aspectRatio > 0) {
            int wSize = MeasureSpec.getSize(widthMeasureSpec);
            int wMode = MeasureSpec.getMode(widthMeasureSpec);
            if (wSize > 0 && (wMode == MeasureSpec.EXACTLY || wMode == MeasureSpec.AT_MOST)) {
                int h = (int) Math.min(wSize * aspectRatio, maxHeight);
                super.onMeasure(
                        MeasureSpec.makeMeasureSpec(wSize, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                return;
            }
        }

        int hSize = MeasureSpec.getSize(heightMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);

        switch (hMode) {
            case MeasureSpec.AT_MOST:
                heightMeasureSpec =
                        MeasureSpec.makeMeasureSpec(
                                Math.min(hSize, maxHeight), MeasureSpec.AT_MOST);
                break;
            case MeasureSpec.UNSPECIFIED:
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
                break;
            case MeasureSpec.EXACTLY:
                heightMeasureSpec =
                        MeasureSpec.makeMeasureSpec(
                                Math.min(hSize, maxHeight), MeasureSpec.EXACTLY);
                break;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
