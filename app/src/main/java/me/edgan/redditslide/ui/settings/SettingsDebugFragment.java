package me.edgan.redditslide.ui.settings;

import android.app.Activity;
import android.widget.RelativeLayout;

import androidx.appcompat.widget.SwitchCompat;

import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;

public class SettingsDebugFragment {

    private final Activity context;

    public SettingsDebugFragment(Activity context) {
        this.context = context;
    }

    public void Bind() {
        final SwitchCompat breakReauthSwitch =
                context.findViewById(R.id.settings_debug_breakreauth);
        final RelativeLayout triggerReauthLayout =
                context.findViewById(R.id.settings_debug_triggerreauth);

        // Toggle that stalls re-authentication so the reauth snackbar can be tested. It is
        // recoverable: turning it off lets the next reauth succeed again.
        breakReauthSwitch.setChecked(SettingValues.debugBreakReauth);
        breakReauthSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    SettingValues.debugBreakReauth = isChecked;
                    SettingValues.prefs
                            .edit()
                            .putBoolean(SettingValues.PREF_DEBUG_BREAK_REAUTH, isChecked)
                            .apply();
                });

        // Kick off a reauth on demand. With the toggle off this is how to recover a broken state.
        // No toast here: the app-wide reauth snackbar already reports progress/failure.
        triggerReauthLayout.setOnClickListener(
                v -> {
                    if (Reddit.authentication != null) {
                        Reddit.authentication.updateToken(context);
                    }
                });
    }
}
