package me.edgan.redditslide.ui.settings;

import android.app.Activity;

import androidx.appcompat.widget.SwitchCompat;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;

public class SettingsMarkdownFragment {

    private final Activity context;

    public SettingsMarkdownFragment(Activity context) {
        this.context = context;
    }

    public void Bind() {
        final SwitchCompat newRedditSwitch =
                context.findViewById(R.id.settings_markdown_newReddit);
        newRedditSwitch.setChecked(SettingValues.markdownNewReddit);
        newRedditSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    SettingValues.markdownNewReddit = isChecked;
                    SettingValues.prefs
                            .edit()
                            .putBoolean(SettingValues.PREF_MARKDOWN_NEW_REDDIT, isChecked)
                            .apply();
                });
    }
}
