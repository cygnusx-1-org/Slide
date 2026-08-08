package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SettingsModeration extends BaseActivityAnim {
    private SettingsModerationFragment fragment = new SettingsModerationFragment(this);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_moderation);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_moderation, true, true);

        ((ViewGroup) requireViewById(R.id.settings_moderation))
                .addView(
                        getLayoutInflater()
                                .inflate(R.layout.activity_settings_moderation_child, null));

        fragment.Bind();
    }
}
