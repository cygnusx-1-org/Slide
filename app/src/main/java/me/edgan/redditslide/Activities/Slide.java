package me.edgan.redditslide.Activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
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
            Intent i = new Intent(this, MainActivity.class);
            startActivity(i);
        }
        finish();
    }
}
