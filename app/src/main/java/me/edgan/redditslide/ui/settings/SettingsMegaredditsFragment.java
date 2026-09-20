package me.edgan.redditslide.ui.settings;

import android.app.Activity;

import androidx.appcompat.widget.SwitchCompat;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SettingsMegaredditsFragment {

    private final Activity context;

    public SettingsMegaredditsFragment(Activity context) {
        this.context = context;
    }

    public void Bind() {
        final SwitchCompat postCountSwitch =
                context.requireViewById(R.id.settings_megareddits_post_count);
        postCountSwitch.setChecked(SettingValues.megaredditPostCount);
        postCountSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    SettingValues.megaredditPostCount = isChecked;
                    SettingValues.prefs
                            .edit()
                            .putBoolean(SettingValues.PREF_MEGAREDDIT_POST_COUNT, isChecked)
                            .apply();
                });
    }
}
