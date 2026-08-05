package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SettingsHistory extends BaseActivityAnim {

    private SettingsHistoryFragment fragment = new SettingsHistoryFragment(this);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
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
