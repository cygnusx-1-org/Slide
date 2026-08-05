package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/** Created by l3d00m on 11/13/2015. */
@NullMarked
public class SettingsFont extends BaseActivityAnim {

    private SettingsFontFragment fragment = new SettingsFontFragment(this);

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_font);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_font, true, true);

        ((ViewGroup) findViewById(R.id.settings_font))
                .addView(getLayoutInflater().inflate(R.layout.activity_settings_font_child, null));

        fragment.Bind();
    }
}
