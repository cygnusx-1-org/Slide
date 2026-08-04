package me.edgan.redditslide.util;

import android.content.res.Resources;
import android.util.DisplayMetrics;
import org.jspecify.annotations.NullMarked;

/** Created by TacoTheDank on 03/15/2021. */
@NullMarked
public class DisplayUtil {
    private static int dpToPx(int dp, float xy) {
        return Math.round(dp * xy / DisplayMetrics.DENSITY_DEFAULT);
    }

    public static int dpToPxVertical(int dp) {
        return dpToPx(dp, Resources.getSystem().getDisplayMetrics().ydpi);
    }

    public static int dpToPxHorizontal(int dp) {
        return dpToPx(dp, Resources.getSystem().getDisplayMetrics().xdpi);
    }
}
