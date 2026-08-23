package me.edgan.redditslide.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/** Created by l3d00m on 11/13/2015. */
@NullMarked
public class SettingsHandling extends BaseActivityAnim {

    private SettingsHandlingFragment fragment = new SettingsHandlingFragment(this);

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_handling);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_link_handling, true, true);

        ((ViewGroup) requireViewById(R.id.settings_handling))
                .addView(
                        getLayoutInflater()
                                .inflate(R.layout.activity_settings_handling_child, null));

        fragment.Bind();
    }

    @Override
    public void onPause() {
        super.onPause();
        SharedPreferences.Editor e = SettingValues.prefs.edit();

        e.putStringSet(SettingValues.PREF_ALWAYS_EXTERNAL, SettingValues.alwaysExternal);
        e.apply();
    }
}
