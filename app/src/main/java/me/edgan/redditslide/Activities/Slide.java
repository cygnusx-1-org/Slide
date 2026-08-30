package me.edgan.redditslide.Activities;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import me.edgan.redditslide.HibernateState;
import me.edgan.redditslide.util.LogUtil;
import org.jspecify.annotations.NullMarked;

/** Created by ccrama on 9/28/2015. */
@NullMarked
public class Slide extends Activity {

    public static boolean hasStarted;

    @Override
    public void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);
        if (!hasStarted) {
            hasStarted = true;
            // With hibernate on, the launcher rebuilds the whole stack the user left behind rather
            // than just MainActivity. startActivities builds the back stack in one go and resumes
            // only the last entry, so Back walks out through the screens in the order they were
            // opened. Null when there is nothing to restore, which is every launch with the
            // setting off.
            final Intent[] stack = HibernateState.buildRestoreStack(this);
            boolean restored = false;
            if (stack != null && stack.length > 0) {
                try {
                    // No transition animations. The screens below the one being restored are
                    // started too, so that Back walks out through them, and with the default
                    // animations each one visibly slides into place: the user watches the feed
                    // appear and then the screen they were actually on arrive on top of it.
                    startActivities(
                            stack, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
                    restored = true;
                } catch (RuntimeException e) {
                    // The snapshot names its screens by class. An update that renames or removes
                    // one leaves a document that still parses and still passes its version check,
                    // and every launch from then on throws here -- an app that cannot be opened
                    // at all until its data is cleared. Falling through to a normal start costs
                    // one restored session and keeps the icon working.
                    LogUtil.e(e, "Slide.onCreate could not replay the saved stack");
                }
            }
            if (!restored) {
                startActivity(new Intent(this, MainActivity.class));
            }
        }
        finish();
    }
}
