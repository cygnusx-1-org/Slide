package me.edgan.redditslide.Activities;

/** Created by carlo_000 on 1/20/2016. */
import android.os.Bundle;
import androidx.annotation.Nullable;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/** Created by ccrama on 9/17/2015. */
@NullMarked
public class Loader extends BaseActivity {

    @Override
    public void onCreate(@Nullable Bundle savedInstance) {
        disableSwipeBackLayout();
        super.onCreate(savedInstance);
        applyColorTheme();
        setContentView(R.layout.activity_loading);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        MainActivity.loader = this;
    }
}
