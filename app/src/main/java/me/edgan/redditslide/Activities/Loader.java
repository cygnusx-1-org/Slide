package me.edgan.redditslide.Activities;

/** Created by carlo_000 on 1/20/2016. */
import android.os.Bundle;

import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;

/** Created by ccrama on 9/17/2015. */
public class Loader extends BaseActivity {

    @Override
    public void onCreate(Bundle savedInstance) {
        disableSwipeBackLayout();
        super.onCreate(savedInstance);
        applyColorTheme();
        setContentView(R.layout.activity_loading);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        MainActivity.loader = this;
    }
}
