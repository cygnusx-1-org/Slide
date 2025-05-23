package me.edgan.redditslide.Activities;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;

import me.edgan.redditslide.SettingValues;

import java.net.MalformedURLException;
import java.net.URL;

/** Created by ccrama on 9/28/2015. */
public class MakeExternal extends Activity {
    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        String url = getIntent().getStringExtra("url");
        if (url != null) {
            try {
                URL u = new URL(url);
                SettingValues.alwaysExternal.add(u.getHost());
                SharedPreferences.Editor e = SettingValues.prefs.edit();
                e.putStringSet(SettingValues.PREF_ALWAYS_EXTERNAL, SettingValues.alwaysExternal);
                e.apply();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        finish();
    }
}
