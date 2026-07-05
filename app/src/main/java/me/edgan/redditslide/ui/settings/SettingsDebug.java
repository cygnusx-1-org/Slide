package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;

import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;

public class SettingsDebug extends BaseActivityAnim {

    private SettingsDebugFragment fragment = new SettingsDebugFragment(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_debug);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_debug, true, true);

        ((ViewGroup) findViewById(R.id.settings_debug))
                .addView(
                        getLayoutInflater().inflate(R.layout.activity_settings_debug_child, null));

        fragment.Bind();
    }
}
