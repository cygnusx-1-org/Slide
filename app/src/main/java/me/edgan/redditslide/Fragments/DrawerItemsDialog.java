package me.edgan.redditslide.Fragments;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.ui.settings.SettingsThemeFragment;

/**
 * Dialog letting the user pick which items appear in the navigation drawer. Migrated from extending
 * the deprecated afollestad {@code MaterialDialog} to a {@link MaterialAlertDialogBuilder}-backed
 * helper.
 */
public class DrawerItemsDialog {

    /** Builds and shows the drawer-items selection dialog. */
    public static void show(final Context base) {
        final Context contextThemeWrapper =
                new ContextThemeWrapper(
                        base, new ColorPreferences(base).getFontStyle().getBaseId());
        final View view =
                LayoutInflater.from(contextThemeWrapper)
                        .inflate(R.layout.dialog_drawer_items, null);

        if (SettingValues.selectedDrawerItems == -1) {
            SettingValues.selectedDrawerItems = 0;
            for (final SettingsDrawerEnum settingDrawerItem : SettingsDrawerEnum.values()) {
                SettingValues.selectedDrawerItems += settingDrawerItem.value;
            }
            SettingValues.prefs
                    .edit()
                    .putLong(
                            SettingValues.PREF_SELECTED_DRAWER_ITEMS,
                            SettingValues.selectedDrawerItems)
                    .apply();
        }

        for (final SettingsDrawerEnum settingDrawerItem : SettingsDrawerEnum.values()) {
            view.findViewById(settingDrawerItem.layoutId)
                    .setOnClickListener(
                            v -> {
                                CheckBox checkBox =
                                        (CheckBox)
                                                view.findViewById(settingDrawerItem.checkboxId);
                                if (checkBox.isChecked()) {
                                    SettingValues.selectedDrawerItems -= settingDrawerItem.value;
                                } else {
                                    SettingValues.selectedDrawerItems += settingDrawerItem.value;
                                }
                                checkBox.setChecked(!checkBox.isChecked());
                            });
            SettingsThemeFragment.changed = true;
            ((CheckBox) view.findViewById(settingDrawerItem.checkboxId))
                    .setChecked((SettingValues.selectedDrawerItems & settingDrawerItem.value) != 0);
        }

        final AlertDialog dialog =
                new MaterialAlertDialogBuilder(contextThemeWrapper)
                        .setView(view)
                        .setTitle(R.string.settings_general_title_drawer_items)
                        .setPositiveButton(
                                android.R.string.ok,
                                (d, which) -> {
                                    if (SettingsThemeFragment.changed) {
                                        SettingValues.prefs
                                                .edit()
                                                .putLong(
                                                        SettingValues.PREF_SELECTED_DRAWER_ITEMS,
                                                        SettingValues.selectedDrawerItems)
                                                .apply();
                                    }
                                })
                        // Mirror the previous onStop() persistence.
                        .setOnDismissListener(
                                d ->
                                        SettingValues.prefs
                                                .edit()
                                                .putLong(
                                                        SettingValues.PREF_SELECTED_DRAWER_ITEMS,
                                                        SettingValues.selectedDrawerItems)
                                                .apply())
                        .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    public enum SettingsDrawerEnum {
        PROFILE(
                1,
                R.id.settings_drawer_profile,
                R.id.settings_drawer_profile_checkbox,
                R.id.prof_click),
        INBOX(1 << 1, R.id.settings_drawer_inbox, R.id.settings_drawer_inbox_checkbox, R.id.inbox),
        MULTIREDDITS(
                1 << 2,
                R.id.settings_drawer_multireddits,
                R.id.settings_drawer_multireddits_checkbox,
                R.id.multi),
        GOTO_PROFILE(
                1 << 3,
                R.id.settings_drawer_goto_profile,
                R.id.settings_drawer_goto_profile_checkbox,
                R.id.prof),
        DISCOVER(
                1 << 4,
                R.id.settings_drawer_discover,
                R.id.settings_drawer_discover_checkbox,
                R.id.discover);

        public long value;
        @IdRes public int layoutId;
        @IdRes public int checkboxId;
        @IdRes public int drawerId;

        SettingsDrawerEnum(
                long value, @IdRes int layoutId, @IdRes int checkboxId, @IdRes int drawerId) {
            this.value = value;
            this.layoutId = layoutId;
            this.checkboxId = checkboxId;
            this.drawerId = drawerId;
        }
    }
}
