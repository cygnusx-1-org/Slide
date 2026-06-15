package me.edgan.redditslide.Views;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

// Previously backed the io.codetail CircularReveal library's ViewRevealManager to clip
// circular reveals on pre-Lollipop. minSdk is 29, so android.view.ViewAnimationUtils
// clips the reveal at the framework level and the manager is no longer needed. Kept as a
// thin RelativeLayout subclass because it is referenced by class name in the submission
// card layouts (submission_list.xml, submission_largecard.xml, etc.).

public class RevealRelativeLayout extends RelativeLayout {

    public RevealRelativeLayout(Context context) {
        this(context, null);
    }

    public RevealRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RevealRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}
