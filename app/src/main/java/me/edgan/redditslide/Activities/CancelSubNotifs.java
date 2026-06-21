package me.edgan.redditslide.Activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.Locale;
import me.edgan.redditslide.Notifications.CheckForMail;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.util.StringUtil;

/** Created by ccrama on 9/28/2015. */
public class CancelSubNotifs extends Activity {

    public static final String EXTRA_SUB = "sub";

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        String subName;

        if (extras != null) {
            subName = extras.getString(EXTRA_SUB, "");
            subName = subName.toLowerCase(Locale.ENGLISH);

            ArrayList<String> subs =
                    StringUtil.stringToArray(
                            Reddit.appRestart
                                    .getString(CheckForMail.SUBS_TO_GET, "")
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
