package me.edgan.redditslide.Activities;

import android.os.Bundle;
import android.view.ViewTreeObserver;
import android.view.Window;
import androidx.annotation.Nullable;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.Visuals.Palette;
import org.jspecify.annotations.NullMarked;

/**
 * Created by tomer aka rosenpin on 11/27/15.
 *
 * <p>This Activity allows for fullscreen viewing without the statusbar visible
 */
@NullMarked
public class FullScreenActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        // TODO something like this
        // getWindow().setFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
        //   WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        if (Reddit.peek) {
            overridePendingTransition(R.anim.pop_in, 0);
        } else {
            overridePendingTransition(R.anim.slide_in, 0);
        }
        setRecentBar(null, Palette.getDefaultColor());
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(0, R.anim.slide_out);
    }

    @Override
    public void onPostCreate(@Nullable Bundle savedInstanceState) {
        try {
            findViewById(android.R.id.content)
                    .getViewTreeObserver()
                    .addOnGlobalLayoutListener(
                            new ViewTreeObserver.OnGlobalLayoutListener() {
                                @Override
                                public void onGlobalLayout() {
                                    //
                                    // Blurry.with(FullScreenActivity.this).radius(2).sampling(5).animate().color(Color.parseColor("#99000000")).onto((ViewGroup) findViewById(android.R.id.content));
                                }
                            });
        } catch (Exception e) {
            // The blur this listener was for is commented out,
            // so there is nothing to recover.
        }
        super.onPostCreate(savedInstanceState);
    }
}
