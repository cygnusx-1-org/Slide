package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;

import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;

public class SettingsHistory extends BaseActivityAnim {

    private SettingsHistoryFragment fragment = new SettingsHistoryFragment(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_history);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_history, true, true);

        ((ViewGroup) findViewById(R.id.settings_history))
                .addView(
                        getLayoutInflater()
                                .inflate(R.layout.activity_settings_history_child, null));

        fragment.Bind();
    }
}
