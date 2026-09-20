package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/** Settings for Megareddits, the saved filters over r/all. */
@NullMarked
public class SettingsMegareddits extends BaseActivityAnim {

    private SettingsMegaredditsFragment fragment = new SettingsMegaredditsFragment(this);

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_megareddits);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.title_megareddits, true, true);

        ((ViewGroup) requireViewById(R.id.settings_megareddits))
                .addView(
                        getLayoutInflater()
                                .inflate(R.layout.activity_settings_megareddits_child, null));

        fragment.Bind();
    }
}
