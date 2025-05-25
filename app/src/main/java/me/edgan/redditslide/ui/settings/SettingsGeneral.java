package me.edgan.redditslide.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.StorageUtil;
import me.edgan.redditslide.util.MiscUtil;

/** Created by ccrama on 3/5/2015. */
public class SettingsGeneral extends BaseActivityAnim implements StorageUtil.DirectoryChooserHost {

    private SettingsGeneralFragment fragment = new SettingsGeneralFragment(this);
    private StorageUtil.OnDirectorySelectedListener directorySelectedListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_general);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_general, true, true);

        ((ViewGroup) findViewById(R.id.settings_general))
                .addView(
                        getLayoutInflater()
                                .inflate(R.layout.activity_settings_general_child, null));

        fragment.Bind();

        // Initialize location view with current path
        TextView locationView =
                (TextView) findViewById(R.id.settings_general_set_save_location_view);
        if (locationView != null) {
            Uri currentUri = StorageUtil.getStorageUri(this);
            if (currentUri != null && StorageUtil.hasStorageAccess(this)) {
                String path = StorageUtil.getDisplayPath(this, currentUri);
                locationView.setText(path);
            } else {
                locationView.setText(R.string.settings_storage_location_unset);
            }
        }

        // Initialize download button switch
        SwitchCompat downloadSwitch =
                (SwitchCompat) findViewById(R.id.settings_general_show_download_button);
        if (downloadSwitch != null) {
            downloadSwitch.setChecked(SettingValues.imageDownloadButton);
        }

        RelativeLayout saveLocationLayout =
                (RelativeLayout) findViewById(R.id.settings_general_set_save_location);
        if (saveLocationLayout != null) {
            saveLocationLayout.setOnClickListener(
                    view -> {
                        showDirectoryChooser();
                    });
        }
    }

    private void showDirectoryChooser() {
        StorageUtil.showDirectoryChooser(
                this,
                uri -> {
                    if (uri != null) {
                        String path = StorageUtil.getDisplayPath(this, uri);

                        TextView locationView =
                                (TextView)
                                        findViewById(R.id.settings_general_set_save_location_view);
                        if (locationView != null) {
                            locationView.setText(path);
                        }

                        SwitchCompat downloadSwitch =
                                (SwitchCompat)
                                        findViewById(R.id.settings_general_show_download_button);
                        if (downloadSwitch != null) {
                            downloadSwitch.setChecked(true);
                        }

                        SettingValues.imageDownloadButton = true;
                        SettingValues.prefs
                                .edit()
                                .putBoolean(SettingValues.PREF_IMAGE_DOWNLOAD_BUTTON, true)
                                .apply();
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == StorageUtil.REQUEST_STORAGE_ACCESS && resultCode == Activity.RESULT_OK) {
            Uri uri = data.getData();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    final int takeFlags =
                            data.getFlags()
                                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri, takeFlags);
                } catch (Exception e) {
                    LogUtil.e(
                            e,
                            "SlideStorage Error taking persistent permission: " + e.getMessage());
                }
            }

            StorageUtil.saveStorageUri(this, uri);

            if (directorySelectedListener != null) {
                directorySelectedListener.onDirectorySelected(uri);
            }

            String path = StorageUtil.getDisplayPath(this, uri);
            TextView locationView =
                    (TextView) findViewById(R.id.settings_general_set_save_location_view);
            if (locationView != null) {
                locationView.setText(path);
            }

            SwitchCompat downloadSwitch =
                    (SwitchCompat) findViewById(R.id.settings_general_show_download_button);
            if (downloadSwitch != null) {
                downloadSwitch.setChecked(true);
            }

            SettingValues.imageDownloadButton = true;
            SettingValues.prefs
                    .edit()
                    .putBoolean(SettingValues.PREF_IMAGE_DOWNLOAD_BUTTON, true)
                    .apply();
        }
    }

    @Override
    public void setDirectorySelectedListener(StorageUtil.OnDirectorySelectedListener listener) {
        this.directorySelectedListener = listener;
    }

    @Override
    public StorageUtil.OnDirectorySelectedListener getDirectorySelectedListener() {
        return directorySelectedListener;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forward permission results to our fragment
        fragment.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Update viewtype display when returning from SettingsViewType
        TextView viewTypeCurrentView = (TextView) findViewById(R.id.settings_general_viewtype_current);
        if (viewTypeCurrentView != null) {
            viewTypeCurrentView.setText(
                    SettingValues.single
                            ? (SettingValues.commentPager
                                    ? getString(R.string.view_type_comments)
                                    : getString(R.string.view_type_none))
                            : getString(R.string.view_type_tabs));
        }
    }
}
