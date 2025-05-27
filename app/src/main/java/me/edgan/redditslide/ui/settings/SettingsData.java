package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;

import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;

/** Created by ccrama on 3/5/2015. */
public class SettingsData extends BaseActivityAnim {

    private SettingsDataFragment fragment = new SettingsDataFragment(this);

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_datasaving);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_data, true, true);

        ((ViewGroup) findViewById(R.id.settings_datasaving))
                .addView(
                        getLayoutInflater()
                                .inflate(R.layout.activity_settings_datasaving_child, null));

        fragment.Bind();
    }
}
