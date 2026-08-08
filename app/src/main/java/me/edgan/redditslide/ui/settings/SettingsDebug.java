package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SettingsDebug extends BaseActivityAnim {

    private SettingsDebugFragment fragment = new SettingsDebugFragment(this);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_debug);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_debug, true, true);

        ((ViewGroup) requireViewById(R.id.settings_debug))
                .addView(
                        getLayoutInflater().inflate(R.layout.activity_settings_debug_child, null));

        fragment.Bind();
    }
}
