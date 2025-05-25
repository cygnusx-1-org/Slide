package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;

import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;

public class SettingsComments extends BaseActivityAnim {

    private SettingsCommentsFragment fragment = new SettingsCommentsFragment(this);

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_comments);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_comments, true, true);

        ((ViewGroup) findViewById(R.id.settings_comments))
                .addView(
                        getLayoutInflater()
                                .inflate(R.layout.activity_settings_comments_child, null));

        fragment.Bind();
    }
}
