package me.edgan.redditslide.ui.settings;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/** Created by ccrama on 3/5/2015. */
@NullMarked
public class SettingsViewType extends BaseActivityAnim {

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_viewtype);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_view_type, true, true);

        // View type multi choice
        ((TextView) requireViewById(R.id.currentViewType))
                .setText(
                        SettingValues.single
                                ? (SettingValues.commentPager
                                        ? getString(R.string.view_type_comments)
                                        : getString(R.string.view_type_none))
                                : getString(R.string.view_type_tabs));

        requireViewById(R.id.viewtype)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                PopupMenu popup = new PopupMenu(SettingsViewType.this, v);
                                popup.getMenuInflater()
                                        .inflate(R.menu.view_type_settings, popup.getMenu());

                                popup.setOnMenuItemClickListener(
                                        new PopupMenu.OnMenuItemClickListener() {
                                            @Override public boolean onMenuItemClick(MenuItem item) {
                                                int itemId = item.getItemId();
                                                if (itemId == R.id.tabs) {
                                                    SettingValues.single = false;
                                                    SettingValues.prefs
                                                            .edit()
                                                            .putBoolean(
                                                                    SettingValues.PREF_SINGLE, false)
                                                            .apply();
                                                } else if (itemId == R.id.notabs) {
                                                    SettingValues.single = true;
                                                    SettingValues.commentPager = false;
                                                    SettingValues.prefs
                                                            .edit()
                                                            .putBoolean(
                                                                    SettingValues.PREF_SINGLE, true)
                                                            .apply();
                                                    SettingValues.prefs
                                                            .edit()
                                                            .putBoolean(
                                                                    SettingValues.PREF_COMMENT_PAGER,
                                                                    false)
                                                            .apply();
                                                } else if (itemId == R.id.comments) {
                                                    SettingValues.single = true;
                                                    SettingValues.commentPager = true;
                                                    SettingValues.prefs
                                                            .edit()
                                                            .putBoolean(
                                                                    SettingValues.PREF_SINGLE, true)
                                                            .apply();
                                                    SettingValues.prefs
                                                            .edit()
                                                            .putBoolean(
                                                                    SettingValues.PREF_COMMENT_PAGER,
                                                                    true)
                                                            .apply();
                                                }
                                                ((TextView) requireViewById(R.id.currentViewType))
                                                        .setText(
                                                                SettingValues.single
                                                                        ? (SettingValues
                                                                                        .commentPager
                                                                                ? getString(
                                                                                        R.string
                                                                                                .view_type_comments)
                                                                                : getString(
                                                                                        R.string
                                                                                                .view_type_none))
                                                                        : getString(
                                                                                R.string
                                                                                        .view_type_tabs));
                                                SettingsThemeFragment.changed = true;
                                                return true;
                                            }
                                        });

                                popup.show();
                            }
                        });
    }
}
