package me.edgan.redditslide.Activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Locale;
import me.edgan.redditslide.Notifications.CheckForMail;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.util.PrefUtil;
import me.edgan.redditslide.util.StringUtil;
import org.jspecify.annotations.NullMarked;

/** Created by ccrama on 9/28/2015. */
@NullMarked
public class CancelSubNotifs extends Activity {

    public static final String EXTRA_SUB = "sub";

    @Override
    public void onCreate(@Nullable Bundle savedInstance) {
        super.onCreate(savedInstance);
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        String subName;

        if (extras != null) {
            subName = extras.getString(EXTRA_SUB, "");
            subName = subName.toLowerCase(Locale.ENGLISH);

            ArrayList<String> subs =
                    StringUtil.stringToArray(
                            PrefUtil.getString(Reddit.appRestart, CheckForMail.SUBS_TO_GET, "")
                                    .toLowerCase(Locale.ENGLISH));
            String toRemove = "";

            for (String s : subs) {
                if (s.startsWith(subName + ":")) {
                    toRemove = s;
                }
            }
            if (!toRemove.isEmpty()) {
                subs.remove(toRemove);
            }
            Reddit.appRestart
                    .edit()
                    .putString(CheckForMail.SUBS_TO_GET, StringUtil.arrayToString(subs))
                    .apply();
        }

        finish();
    }
}
