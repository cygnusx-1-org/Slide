package me.edgan.redditslide.ui.settings;

import static me.edgan.redditslide.Constants.BackButtonBehaviorOptions;
import static me.edgan.redditslide.Constants.FAB_DISMISS;
import static me.edgan.redditslide.Constants.FAB_POST;
import static me.edgan.redditslide.Constants.FAB_SEARCH;
import static me.edgan.redditslide.Constants.SUBREDDIT_SEARCH_METHOD_BOTH;
import static me.edgan.redditslide.Constants.SUBREDDIT_SEARCH_METHOD_DRAWER;
import static me.edgan.redditslide.Constants.SUBREDDIT_SEARCH_METHOD_TOOLBAR;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.CaseInsensitiveArrayList;
import me.edgan.redditslide.Fragments.DrawerItemsDialog;
import me.edgan.redditslide.Notifications.CheckForMail;
import me.edgan.redditslide.Notifications.NotificationJobScheduler;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.ImageLoaderUtils;
import me.edgan.redditslide.util.OnSingleClickListener;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.QrCodeScannerHelper;
import me.edgan.redditslide.util.SortingUtil;
import me.edgan.redditslide.util.StorageUtil;
import me.edgan.redditslide.util.StringUtil;
import me.edgan.redditslide.util.TimeUtils;

import net.dean.jraw.models.CommentSort;
import net.dean.jraw.models.Subreddit;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.TimePeriod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import android.content.pm.PackageManager;

/** Created by ccrama on 3/5/2015. */
public class SettingsGeneralFragment<ActivityType extends AppCompatActivity> {

    public static boolean searchChanged; // whether or not the subreddit search method changed
    private final ActivityType context;
    private String input;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1337;

    public SettingsGeneralFragment(ActivityType context) {
        this.context = context;
    }

    public static void setupNotificationSettings(View dialoglayout, final Activity context) {
        final SeekBar landscape = dialoglayout.findViewById(R.id.landscape);
        final CheckBox checkBox = dialoglayout.findViewById(R.id.load);
        final CheckBox sound = dialoglayout.findViewById(R.id.sound);
        final TextView notifCurrentView =
                context.findViewById(R.id.settings_general_notifications_current);

        sound.setChecked(SettingValues.notifSound);
        sound.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        SettingValues.prefs
                                .edit()
                                .putBoolean(SettingValues.PREF_SOUND_NOTIFS, isChecked)
                                .apply();
                        SettingValues.notifSound = isChecked;
                    }
                });

        // Set initial slider position based on stored notification time
        if (Reddit.notificationTime > 0) {
            // Convert from notification time (10-120) to slider progress (0-11)
            int progress = (Reddit.notificationTime - 10) / 10;
            landscape.setProgress(progress);
        } else {
            landscape.setProgress(0);
        }

        if (Reddit.notificationTime == -1) {
            checkBox.setChecked(false);
            checkBox.setText(context.getString(R.string.settings_mail_check));
        } else {
            checkBox.setChecked(true);
            checkBox.setText(
                    context.getString(
                            R.string.settings_notification_newline,
                            TimeUtils.getTimeInHoursAndMins(
                                    Reddit.notificationTime, context.getBaseContext())));
        }
        landscape.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (checkBox.isChecked()) {
                            int value = progress * 10 + 10; // Convert 0-11 to 10-120
                            checkBox.setText(
                                    context.getString(
                                            R.string.settings_notification,
                                            TimeUtils.getTimeInHoursAndMins(
                                                    value, context.getBaseContext())));
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // Not needed
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        // Not needed
                    }
                });

        checkBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (!isChecked) {
                            Reddit.notificationTime = -1;
                            Reddit.colors.edit().putInt("notificationOverride", -1).apply();
                            checkBox.setText(context.getString(R.string.settings_mail_check));
                            landscape.setProgress(0);
                            if (Reddit.notifications != null) {
                                Reddit.notifications.cancel();
                            }
                        } else {
                            Reddit.notificationTime = 10;
                            checkBox.setText(
                                    context.getString(
                                            R.string.settings_notification,
                                            TimeUtils.getTimeInHoursAndMins(
                                                    Reddit.notificationTime,
                                                    context.getBaseContext())));
                        }
                    }
                });

        dialoglayout.findViewById(R.id.title).setBackgroundColor(Palette.getDefaultColor());

        final AlertDialog.Builder builder = new AlertDialog.Builder(context).setView(dialoglayout);
        final Dialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.show();
        dialog.setOnDismissListener(
                new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        if (checkBox.isChecked()) {
                            int value = landscape.getProgress() * 10 + 10; // Convert 0-11 to 10-120
                            Reddit.notificationTime = value;
                            Reddit.colors
                                    .edit()
                                    .putInt("notificationOverride", value)
                                    .apply();
                            if (Reddit.notifications == null) {
                                Reddit.notifications =
                                        new NotificationJobScheduler(context.getApplication());
                            }
                            Reddit.notifications.cancel();
                            Reddit.notifications.start();
                        }
                    }
                });

        dialoglayout
                .findViewById(R.id.save)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View d) {
                                if (checkBox.isChecked()) {
                                    int value = landscape.getProgress() * 10 + 10; // Convert 0-11 to 10-120
                                    Reddit.notificationTime = value;
                                    Reddit.colors
                                            .edit()
                                            .putInt(
                                                    "notificationOverride",
                                                    value)
                                            .apply();
                                    if (Reddit.notifications == null) {
                                        Reddit.notifications =
                                                new NotificationJobScheduler(
                                                        context.getApplication());
                                    }
                                    Reddit.notifications.cancel();
                                    dialog.dismiss();
                                    if (context instanceof SettingsGeneral) {
                                        notifCurrentView.setText(
                                                context.getString(
                                                        R.string.settings_notification_short,
                                                        TimeUtils.getTimeInHoursAndMins(
                                                                Reddit.notificationTime,
                                                                context.getBaseContext())));
                                    }
                                } else {
                                    Reddit.notificationTime = -1;
                                    Reddit.colors.edit().putInt("notificationOverride", -1).apply();
                                    if (Reddit.notifications == null) {
                                        Reddit.notifications =
                                                new NotificationJobScheduler(
                                                        context.getApplication());
                                    }
                                    Reddit.notifications.cancel();
                                    dialog.dismiss();
                                    if (context instanceof SettingsGeneral) {
                                        notifCurrentView.setText(R.string.settings_notifdisabled);
                                    }
                                }
                            }
                        });

        // Add Pause on Audio Focus switch
        SwitchCompat pauseOnAudioFocusSwitch = dialoglayout.findViewById(R.id.pause_on_audio_focus);
        if (pauseOnAudioFocusSwitch != null) {
            pauseOnAudioFocusSwitch.setChecked(SettingValues.pauseOnAudioFocus);
            pauseOnAudioFocusSwitch.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            SettingValues.pauseOnAudioFocus = isChecked;
                            SettingValues.prefs
                                    .edit()
                                    .putBoolean(SettingValues.PREF_PAUSE_ON_AUDIO_FOCUS, isChecked)
                                    .apply();
                        }
                    });
        }

        // Add a description for the setting
        TextView pauseOnAudioFocusDesc = dialoglayout.findViewById(R.id.pause_on_audio_focus_text);
        if (pauseOnAudioFocusDesc != null) {
            pauseOnAudioFocusDesc.setText("Pause video when audio is ducked (when other apps play sounds)");
        }
    }

    public static void doNotifText(final Activity context) {
        {
            View notifs = context.findViewById(R.id.settings_general_redditnotifs);
            if (notifs != null) {
                if (!Reddit.isPackageInstalled("com.reddit.frontpage")
                        || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    notifs.setVisibility(View.GONE);
                    if (context.findViewById(R.id.settings_general_installreddit) != null) {
                        context.findViewById(R.id.settings_general_installreddit)
                                .setVisibility(View.VISIBLE);
                    }
                } else {
                    if (((Reddit) context.getApplication()).isNotificationAccessEnabled()) {
                        SwitchCompat single = context.findViewById(R.id.settings_general_piggyback);
                        if (single != null) {
                            single.setChecked(true);
                            single.setEnabled(false);
                        }
                    } else {
                        final SwitchCompat single =
                                context.findViewById(R.id.settings_general_piggyback);
                        if (single != null) {
                            single.setChecked(false);
                            single.setEnabled(true);
                            single.setOnCheckedChangeListener(
                                    new CompoundButton.OnCheckedChangeListener() {
                                        @Override
                                        public void onCheckedChanged(
                                                CompoundButton compoundButton, boolean b) {
                                            single.setChecked(false);
                                            Snackbar s =
                                                    Snackbar.make(
                                                            single,
                                                            "Give Slide notification access",
                                                            Snackbar.LENGTH_LONG);
                                            s.setAction(
                                                    "Go to settings",
                                                    new View.OnClickListener() {
                                                        @Override
                                                        public void onClick(View view) {
                                                            context.startActivity(
                                                                    new Intent(
                                                                            "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                                                        }
                                                    });
                                            s.show();
                                        }
                                    });
                        }
                    }
                }
            }
        }
    }

    /* Allow SettingsGeneral and Settings Activity classes to use the same XML functionality */
    public void Bind() {
        final RelativeLayout notifLayout =
                context.findViewById(R.id.settings_general_notifications);
        final TextView notifCurrentView =
                context.findViewById(R.id.settings_general_notifications_current);
        final RelativeLayout subNotifLayout =
                context.findViewById(R.id.settings_general_sub_notifications);
        final TextView defaultSortingCurrentView =
                context.findViewById(R.id.settings_general_sorting_current);
        final TextView frontpageSortingCurrentView =
                context.findViewById(R.id.settings_general_sorting_current_frontpage);

        context.findViewById(R.id.settings_general_drawer_items)
                .setOnClickListener(
                        v -> new DrawerItemsDialog(new MaterialDialog.Builder(context)).show());

        {
            SwitchCompat immersiveModeSwitch =
                    context.findViewById(R.id.settings_general_immersivemode);
            if (immersiveModeSwitch != null) {
                immersiveModeSwitch.setChecked(SettingValues.immersiveMode);
                immersiveModeSwitch.setOnCheckedChangeListener(
                        (buttonView, isChecked) -> {
                            SettingsThemeFragment.changed = true;
                            SettingValues.immersiveMode = isChecked;
                            SettingValues.prefs
                                    .edit()
                                    .putBoolean(SettingValues.PREF_IMMERSIVE_MODE, isChecked)
                                    .apply();
                        });
            }
        }

        {
            SwitchCompat oldSwipeModeSwitch =
                    context.findViewById(R.id.settings_general_old_swipe_mode);
            if (oldSwipeModeSwitch != null) {
                oldSwipeModeSwitch.setChecked(SettingValues.oldSwipeMode);
                oldSwipeModeSwitch.setOnCheckedChangeListener(
                        (buttonView, isChecked) -> {
                            SettingsThemeFragment.changed = true;
                            SettingValues.oldSwipeMode = isChecked;
                            SettingValues.prefs
                                    .edit()
                                    .putBoolean(SettingValues.PREF_OLD_SWIPE_MODE, isChecked)
                                    .apply();
                        });
            }
        }

        {
            SwitchCompat highClrSpaceSwitch =
                    context.findViewById(R.id.settings_general_high_colorspace);
            if (highClrSpaceSwitch != null) {
                highClrSpaceSwitch.setChecked(SettingValues.highColorspaceImages);
                highClrSpaceSwitch.setOnCheckedChangeListener(
                        (buttonView, isChecked) -> {
                            SettingsThemeFragment.changed = true;
                            SettingValues.highColorspaceImages = isChecked;

                            Reddit application = (Reddit) context.getApplication();
                            ImageLoaderUtils.initImageLoader(application.getApplicationContext());
                            application.defaultImageLoader = ImageLoaderUtils.imageLoader;

                            SettingValues.prefs
                                    .edit()
                                    .putBoolean(
                                            SettingValues.PREF_HIGH_COLORSPACE_IMAGES, isChecked)
                                    .apply();
                        });
            }
        }

        {
            SwitchCompat commentEmoteAnimationSwitch =
                    context.findViewById(R.id.settings_general_comment_emote_animation);
            if (commentEmoteAnimationSwitch != null) {
                commentEmoteAnimationSwitch.setChecked(SettingValues.commentEmoteAnimation);
                commentEmoteAnimationSwitch.setOnCheckedChangeListener(
                        (buttonView, isChecked) -> {
                            SettingsThemeFragment.changed = true;
                            SettingValues.commentEmoteAnimation = isChecked;

                            SettingValues.prefs
                                    .edit()
                                    .putBoolean(
                                            SettingValues.PREF_COMMENT_EMOTE_ANIMATION, isChecked)
                                    .apply();
                        });
            }
        }

        {
            SwitchCompat noPreviewImageLongClickSwitch =
                    context.findViewById(R.id.settings_general_no_preview_image_longclick);
            if (noPreviewImageLongClickSwitch != null) {
                noPreviewImageLongClickSwitch.setChecked(SettingValues.noPreviewImageLongClick);
                noPreviewImageLongClickSwitch.setOnCheckedChangeListener(
                        (buttonView, isChecked) -> {
                            SettingsThemeFragment.changed = true;
                            SettingValues.noPreviewImageLongClick = isChecked;
                            // When enabling this setting, disable peek
                            if (isChecked) {
                                SettingValues.peek = false;
                                SettingValues.prefs
                                        .edit()
                                        .putBoolean(SettingValues.PREF_PEEK, false)
                                        .apply();
                            }
                            SettingValues.prefs
                                    .edit()
                                    .putBoolean(
                                            SettingValues.PREF_NO_PREVIEW_IMAGE_LONGCLICK,
                                            isChecked)
                                    .apply();
                        });
            }
        }

        {
            SwitchCompat forceLangSwitch =
                    context.findViewById(R.id.settings_general_forcelanguage);

            if (forceLangSwitch != null) {
                forceLangSwitch.setChecked(SettingValues.overrideLanguage);
                forceLangSwitch.setOnCheckedChangeListener(
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(
                                    CompoundButton buttonView, boolean isChecked) {
                                SettingsThemeFragment.changed = true;
                                SettingValues.overrideLanguage = isChecked;
                                SettingValues.prefs
                                        .edit()
                                        .putBoolean(SettingValues.PREF_OVERRIDE_LANGUAGE, isChecked)
                                        .apply();
                            }
                        });
            }
        }

        // hide fab while scrolling
        {
            SwitchCompat alwaysShowFabSwitch =
                    context.findViewById(R.id.settings_general_always_show_fab);

            if (alwaysShowFabSwitch != null) {
                alwaysShowFabSwitch.setChecked(SettingValues.alwaysShowFAB);
                alwaysShowFabSwitch.setOnCheckedChangeListener(
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(
                                    CompoundButton buttonView, boolean isChecked) {
                                SettingsThemeFragment.changed = true;
                                SettingValues.alwaysShowFAB = isChecked;
                                SettingValues.prefs
                                        .edit()
                                        .putBoolean(SettingValues.PREF_ALWAYS_SHOW_FAB, isChecked)
                                        .apply();
                            }
                        });
            }
        }

        // Show image download button
        {
            SwitchCompat showDownloadBtnSwitch =
                    context.findViewById(R.id.settings_general_show_download_button);
            TextView locationView =
                    context.findViewById(R.id.settings_general_set_save_location_view);
            RelativeLayout setSaveLocationLayout =
                    context.findViewById(R.id.settings_general_set_save_location);

            if (showDownloadBtnSwitch != null && setSaveLocationLayout != null) {
                // Remove any existing listener to prevent recursion
                showDownloadBtnSwitch.setOnCheckedChangeListener(null);
                setSaveLocationLayout.setOnClickListener(null);

                // Get current state
                Uri currentUri = StorageUtil.getStorageUri(context);
                boolean hasValidPath = currentUri != null && StorageUtil.hasStorageAccess(context);

                // Update location display first
                if (locationView != null) {
                    String displayPath;
                    if (hasValidPath) {
                        displayPath = StorageUtil.getDisplayPath(context, currentUri);
                    } else {
                        displayPath = context.getString(R.string.settings_storage_location_unset);
                    }

                    locationView.post(
                            () -> {
                                locationView.setText(displayPath);
                                locationView.invalidate();
                            });
                }

                // Set initial switch state
                showDownloadBtnSwitch.setChecked(SettingValues.imageDownloadButton);

                // Handle location layout clicks
                setSaveLocationLayout.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                if (context instanceof StorageUtil.DirectoryChooserHost) {
                                    StorageUtil.showDirectoryChooser(
                                            context,
                                            uri -> {
                                                if (uri != null) {
                                                    String path =
                                                            StorageUtil.getDisplayPath(
                                                                    context, uri);
                                                    if (locationView != null) {
                                                        locationView.post(
                                                                () -> {
                                                                    locationView.setText(path);
                                                                    locationView.invalidate();
                                                                });
                                                    }

                                                    showDownloadBtnSwitch.setChecked(true);
                                                    SettingValues.imageDownloadButton = true;
                                                    SettingValues.prefs
                                                            .edit()
                                                            .putBoolean(
                                                                    SettingValues
                                                                            .PREF_IMAGE_DOWNLOAD_BUTTON,
                                                                    true)
                                                            .apply();

                                                    Toast.makeText(
                                                                    context,
                                                                    context.getString(
                                                                            R.string
                                                                                    .settings_set_storage_location,
                                                                            path),
                                                                    Toast.LENGTH_LONG)
                                                            .show();
                                                }
                                            });
                                } else {
                                    Toast.makeText(
                                                    context,
                                                    "Unable to select directory in this context",
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                }
                            }
                        });

                // Add long click listener to unset the storage location
                setSaveLocationLayout.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        // Clear the storage location
                        StorageUtil.clearStorageUri(context);

                        // Update the display text
                        if (locationView != null) {
                            locationView.post(() -> {
                                locationView.setText(R.string.settings_storage_location_unset);
                                locationView.invalidate();
                            });
                        }

                        // Show confirmation toast
                        Toast.makeText(
                                context,
                                "Storage location has been unset",
                                Toast.LENGTH_SHORT).show();

                        return true; // Return true to indicate the long click was handled
                    }
                });

                // Switch change listener
                showDownloadBtnSwitch.setOnCheckedChangeListener(
                        (buttonView, isChecked) -> {
                            // Simply update the preference without asking for a directory
                            SettingValues.imageDownloadButton = isChecked;
                            SettingValues.prefs
                                    .edit()
                                    .putBoolean(SettingValues.PREF_IMAGE_DOWNLOAD_BUTTON, isChecked)
                                    .apply();

                            // Optional: Show a toast if enabled but no path is set
                            if (isChecked && !hasValidPath) {
                                Toast.makeText(
                                        context,
                                        "Download button enabled. Set a storage location to use it.",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        }

        {
            SwitchCompat subfolderSwitch = context.findViewById(R.id.settings_general_subfolder);

            if (subfolderSwitch != null) {
                subfolderSwitch.setChecked(SettingValues.imageSubfolders);
                subfolderSwitch.setOnCheckedChangeListener(
                        new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(
                                    CompoundButton buttonView, boolean isChecked) {
                                SettingValues.imageSubfolders = isChecked;
                                SettingValues.prefs
                                        .edit()
                                        .putBoolean(SettingValues.PREF_IMAGE_SUBFOLDERS, isChecked)
                                        .apply();
                            }
                        });
            }
        }

        final RelativeLayout setSaveLocationLayout =
                context.findViewById(R.id.settings_general_set_save_location);
        if (setSaveLocationLayout != null) {
            setSaveLocationLayout.setOnClickListener(
                    v -> {
                        Uri storageUri = StorageUtil.getStorageUri(context);
                        if (storageUri == null || !StorageUtil.hasStorageAccess(context)) {
                            StorageUtil.showDirectoryChooser(context);
                        } else {
                            // Show current location - cast context to Context
                            String location =
                                    StorageUtil.getDisplayPath((Context) context, storageUri);
                            ((TextView)
                                            context.findViewById(
                                                    R.id.settings_general_set_save_location_view))
                                    .setText(location);
                            Toast.makeText(
                                            context,
                                            context.getString(
                                                    R.string.settings_set_storage_location,
                                                    location),
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    });
        }

        TextView setSaveLocationView =
                context.findViewById(R.id.settings_general_set_save_location_view);
        if (setSaveLocationView != null) {
            String loc =
                    Reddit.appRestart.getString(
                            "imagelocation",
                            context.getString(R.string.settings_storage_location_unset));
            setSaveLocationView.setText(loc);
        }

        final SwitchCompat expandedMenuSwitch =
                context.findViewById(R.id.settings_general_expandedmenu);
        if (expandedMenuSwitch != null) {
            expandedMenuSwitch.setChecked(SettingValues.expandedToolbar);
            expandedMenuSwitch.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        SettingValues.expandedToolbar = isChecked;
                        SettingValues.prefs
                                .edit()
                                .putBoolean(SettingValues.PREF_EXPANDED_TOOLBAR, isChecked)
                                .apply();
                    });
        }

        final RelativeLayout viewTypeLayout = context.findViewById(R.id.settings_general_viewtype);
        if (viewTypeLayout != null) {
            TextView viewTypeCurrentView = context.findViewById(R.id.settings_general_viewtype_current);
            if (viewTypeCurrentView != null) {
                viewTypeCurrentView.setText(
                        SettingValues.single
                                ? (SettingValues.commentPager
                                        ? context.getString(R.string.view_type_comments)
                                        : context.getString(R.string.view_type_none))
                                : context.getString(R.string.view_type_tabs));
            }

            viewTypeLayout.setOnClickListener(
                    new OnSingleClickListener() {
                        @Override
                        public void onSingleClick(View v) {
                            Intent i = new Intent(context, SettingsViewType.class);
                            context.startActivity(i);
                        }
                    });
        }

        // FAB multi-choice
        final RelativeLayout fabLayout = context.findViewById(R.id.settings_general_fab);
        final TextView currentFabView = context.findViewById(R.id.settings_general_fab_current);
        if (currentFabView != null && fabLayout != null) {
            // Update the text based on the current settings
            if (SettingValues.fab) {
                if (SettingValues.fabType == FAB_DISMISS) {
                    currentFabView.setText(R.string.fab_hide);
                } else if (SettingValues.fabType == FAB_POST) {
                    currentFabView.setText(R.string.fab_create);
                } else if (SettingValues.fabType == FAB_SEARCH) {
                    currentFabView.setText(R.string.fab_search);
                }
            } else {
                currentFabView.setText(R.string.fab_disabled);
            }

            fabLayout.setOnClickListener(
                    v -> {
                        PopupMenu popup = new PopupMenu(context, v);
                        popup.getMenuInflater().inflate(R.menu.fab_settings, popup.getMenu());

                        popup.setOnMenuItemClickListener(
                                item -> {
                                    switch (item.getItemId()) {
                                        case R.id.disabled:
                                            SettingValues.fab = false;
                                            SettingValues.prefs
                                                    .edit()
                                                    .putBoolean(SettingValues.PREF_FAB, false)
                                                    .apply();
                                            break;
                                        case R.id.hide:
                                            SettingValues.fab = true;
                                            SettingValues.fabType = FAB_DISMISS;
                                            SettingValues.prefs
                                                    .edit()
                                                    .putInt(
                                                            SettingValues.PREF_FAB_TYPE,
                                                            FAB_DISMISS)
                                                    .apply();
                                            SettingValues.prefs
                                                    .edit()
                                                    .putBoolean(SettingValues.PREF_FAB, true)
                                                    .apply();
                                            break;
                                        case R.id.create:
                                            SettingValues.fab = true;
                                            SettingValues.fabType = FAB_POST;
                                            SettingValues.prefs
                                                    .edit()
                                                    .putInt(SettingValues.PREF_FAB_TYPE, FAB_POST)
                                                    .apply();
                                            SettingValues.prefs
                                                    .edit()
                                                    .putBoolean(SettingValues.PREF_FAB, true)
                                                    .apply();
                                            break;
                                        case R.id.search:
                                            SettingValues.fab = true;
                                            SettingValues.fabType = FAB_SEARCH;
                                            SettingValues.prefs
                                                    .edit()
                                                    .putInt(SettingValues.PREF_FAB_TYPE, FAB_SEARCH)
                                                    .apply();
                                            SettingValues.prefs
                                                    .edit()
                                                    .putBoolean(SettingValues.PREF_FAB, true)
                                                    .apply();
                                            break;
                                    }
                                    if (SettingValues.fab) {
                                        if (SettingValues.fabType == FAB_DISMISS) {
                                            currentFabView.setText(R.string.fab_hide);
                                        } else if (SettingValues.fabType == FAB_POST) {
                                            currentFabView.setText(R.string.fab_create);
                                        } else {
                                            currentFabView.setText(R.string.fab_search);
                                        }
                                    } else {
                                        currentFabView.setText(R.string.fab_disabled);
                                    }

                                    return true;
                                });

                        popup.show();
                    });
        }

        // SettingValues.subredditSearchMethod == 1 for drawer, 2 for toolbar, 3 for both
        final TextView currentMethodTitle =
                context.findViewById(R.id.settings_general_subreddit_search_method_current);
        if (currentMethodTitle != null) {
            switch (SettingValues.subredditSearchMethod) {
                case SUBREDDIT_SEARCH_METHOD_DRAWER:
                    currentMethodTitle.setText(
                            context.getString(R.string.subreddit_search_method_drawer));
                    break;
                case SUBREDDIT_SEARCH_METHOD_TOOLBAR:
                    currentMethodTitle.setText(
                            context.getString(R.string.subreddit_search_method_toolbar));
                    break;
                case SUBREDDIT_SEARCH_METHOD_BOTH:
                    currentMethodTitle.setText(
                            context.getString(R.string.subreddit_search_method_both));
                    break;
            }
        }

        final RelativeLayout currentMethodLayout =
                context.findViewById(R.id.settings_general_subreddit_search_method);
        if (currentMethodLayout != null) {
            currentMethodLayout.setOnClickListener(
                    v -> {
                        final PopupMenu popup =
                                new PopupMenu(SettingsGeneralFragment.this.context, v);
                        popup.getMenuInflater()
                                .inflate(R.menu.subreddit_search_settings, popup.getMenu());
                        popup.setOnMenuItemClickListener(
                                item -> {
                                    switch (item.getItemId()) {
                                        case R.id.subreddit_search_drawer:
                                            SettingValues.subredditSearchMethod =
                                                    SUBREDDIT_SEARCH_METHOD_DRAWER;
                                            SettingValues.prefs
                                                    .edit()
                                                    .putInt(
                                                            SettingValues
                                                                    .PREF_SUBREDDIT_SEARCH_METHOD,
                                                            SUBREDDIT_SEARCH_METHOD_DRAWER)
                                                    .apply();
                                            searchChanged = true;
                                            break;
                                        case R.id.subreddit_search_toolbar:
                                            SettingValues.subredditSearchMethod =
                                                    SUBREDDIT_SEARCH_METHOD_TOOLBAR;
                                            SettingValues.prefs
                                                    .edit()
                                                    .putInt(
                                                            SettingValues
                                                                    .PREF_SUBREDDIT_SEARCH_METHOD,
                                                            SUBREDDIT_SEARCH_METHOD_TOOLBAR)
                                                    .apply();
                                            searchChanged = true;
                                            break;
                                        case R.id.subreddit_search_both:
                                            SettingValues.subredditSearchMethod =
                                                    SUBREDDIT_SEARCH_METHOD_BOTH;
                                            SettingValues.prefs
                                                    .edit()
                                                    .putInt(
                                                            SettingValues
                                                                    .PREF_SUBREDDIT_SEARCH_METHOD,
                                                            SUBREDDIT_SEARCH_METHOD_BOTH)
                                                    .apply();
                                            searchChanged = true;
                                            break;
                                    }

                                    switch (SettingValues.subredditSearchMethod) {
                                        case SUBREDDIT_SEARCH_METHOD_DRAWER:
                                            currentMethodTitle.setText(
                                                    context.getString(
                                                            R.string
                                                                    .subreddit_search_method_drawer));
                                            break;
                                        case SUBREDDIT_SEARCH_METHOD_TOOLBAR:
                                            currentMethodTitle.setText(
                                                    context.getString(
                                                            R.string
                                                                    .subreddit_search_method_toolbar));
                                            break;
                                        case SUBREDDIT_SEARCH_METHOD_BOTH:
                                            currentMethodTitle.setText(
                                                    context.getString(
                                                            R.string.subreddit_search_method_both));
                                            break;
                                    }
                                    return true;
                                });
                        popup.show();
                    });
        }

        final TextView currentBackButtonTitle =
                context.findViewById(R.id.settings_general_back_button_behavior_current);
        if (SettingValues.backButtonBehavior == BackButtonBehaviorOptions.ConfirmExit.getValue()) {
            currentBackButtonTitle.setText(
                    context.getString(R.string.back_button_behavior_confirm_exit));
        } else if (SettingValues.backButtonBehavior
                == BackButtonBehaviorOptions.OpenDrawer.getValue()) {
            currentBackButtonTitle.setText(context.getString(R.string.back_button_behavior_drawer));
        } else if (SettingValues.backButtonBehavior
                == BackButtonBehaviorOptions.GotoFirst.getValue()) {
            currentBackButtonTitle.setText(
                    context.getString(R.string.back_button_behavior_goto_first));
        } else {
            currentBackButtonTitle.setText(
                    context.getString(R.string.back_button_behavior_default));
        }

        final RelativeLayout currentBackButtonLayout =
                context.findViewById(R.id.settings_general_back_button_behavior);
        currentBackButtonLayout.setOnClickListener(
                v -> {
                    final PopupMenu popup = new PopupMenu(context, v);
                    popup.getMenuInflater()
                            .inflate(R.menu.back_button_behavior_settings, popup.getMenu());

                    popup.setOnMenuItemClickListener(
                            item -> {
                                switch (item.getItemId()) {
                                    case R.id.back_button_behavior_default:
                                        SettingValues.backButtonBehavior =
                                                BackButtonBehaviorOptions.Default.getValue();
                                        SettingValues.prefs
                                                .edit()
                                                .putInt(
                                                        SettingValues.PREF_BACK_BUTTON_BEHAVIOR,
                                                        BackButtonBehaviorOptions.Default
                                                                .getValue())
                                                .apply();
                                        break;
                                    case R.id.back_button_behavior_confirm_exit:
                                        SettingValues.backButtonBehavior =
                                                BackButtonBehaviorOptions.ConfirmExit.getValue();
                                        SettingValues.prefs
                                                .edit()
                                                .putInt(
                                                        SettingValues.PREF_BACK_BUTTON_BEHAVIOR,
                                                        BackButtonBehaviorOptions.ConfirmExit
                                                                .getValue())
                                                .apply();
                                        break;
                                    case R.id.back_button_behavior_open_drawer:
                                        SettingValues.backButtonBehavior =
                                                BackButtonBehaviorOptions.OpenDrawer.getValue();
                                        SettingValues.prefs
                                                .edit()
                                                .putInt(
                                                        SettingValues.PREF_BACK_BUTTON_BEHAVIOR,
                                                        BackButtonBehaviorOptions.OpenDrawer
                                                                .getValue())
                                                .apply();
                                        break;
                                    case R.id.back_button_behavior_goto_first:
                                        SettingValues.backButtonBehavior =
                                                BackButtonBehaviorOptions.GotoFirst.getValue();
                                        SettingValues.prefs
                                                .edit()
                                                .putInt(
                                                        SettingValues.PREF_BACK_BUTTON_BEHAVIOR,
                                                        BackButtonBehaviorOptions.GotoFirst
                                                                .getValue())
                                                .apply();
                                        break;
                                }

                                if (SettingValues.backButtonBehavior
                                        == BackButtonBehaviorOptions.ConfirmExit.getValue()) {
                                    currentBackButtonTitle.setText(
                                            context.getString(
                                                    R.string.back_button_behavior_confirm_exit));
                                } else if (SettingValues.backButtonBehavior
                                        == BackButtonBehaviorOptions.OpenDrawer.getValue()) {
                                    currentBackButtonTitle.setText(
                                            context.getString(
                                                    R.string.back_button_behavior_drawer));
                                } else if (SettingValues.backButtonBehavior
                                        == BackButtonBehaviorOptions.GotoFirst.getValue()) {
                                    currentBackButtonTitle.setText(
                                            context.getString(
                                                    R.string.back_button_behavior_goto_first));
                                } else {
                                    currentBackButtonTitle.setText(
                                            context.getString(
                                                    R.string.back_button_behavior_default));
                                }
                                return true;
                            });
                    popup.show();
                });

        if (notifCurrentView != null
                && context.findViewById(R.id.settings_general_sub_notifs_current) != null) {
            if (Reddit.notificationTime > 0) {
                notifCurrentView.setText(
                        context.getString(
                                R.string.settings_notification_short,
                                TimeUtils.getTimeInHoursAndMins(
                                        Reddit.notificationTime, context.getBaseContext())));
                setSubText();
            } else {
                notifCurrentView.setText(R.string.settings_notifdisabled);
                ((TextView) context.findViewById(R.id.settings_general_sub_notifs_current))
                        .setText(R.string.settings_enable_notifs);
            }
        }

        if (Authentication.isLoggedIn) {
            if (notifLayout != null) {
                notifLayout.setOnClickListener(
                        v -> {
                            final LayoutInflater inflater = context.getLayoutInflater();
                            final View dialoglayout =
                                    inflater.inflate(R.layout.inboxfrequency, null);
                            setupNotificationSettings(
                                    dialoglayout, SettingsGeneralFragment.this.context);
                        });
            }
            if (subNotifLayout != null) {
                subNotifLayout.setOnClickListener(v -> showSelectDialog());
            }
        } else {
            if (notifLayout != null) {
                notifLayout.setEnabled(false);
                notifLayout.setAlpha(0.25f);
            }
            if (subNotifLayout != null) {
                subNotifLayout.setEnabled(false);
                subNotifLayout.setAlpha(0.25f);
            }
        }

        if (defaultSortingCurrentView != null) {
            defaultSortingCurrentView.setText(
                    SortingUtil.getSortingStrings()[SortingUtil.getSortingId("")]);
        }

        {
            if (context.findViewById(R.id.settings_general_sorting) != null) {
                context.findViewById(R.id.settings_general_sorting)
                        .setOnClickListener(
                                v -> {
                                    final DialogInterface.OnClickListener l2 =
                                            (dialogInterface, i) -> {
                                                switch (i) {
                                                    case 0:
                                                        SortingUtil.defaultSorting = Sorting.HOT;
                                                        break;
                                                    case 1:
                                                        SortingUtil.defaultSorting = Sorting.NEW;
                                                        break;
                                                    case 2:
                                                        SortingUtil.defaultSorting = Sorting.RISING;
                                                        break;
                                                    case 3:
                                                        SortingUtil.defaultSorting = Sorting.TOP;
                                                        askTimePeriod();
                                                        return;
                                                    case 4:
                                                        SortingUtil.defaultSorting =
                                                                Sorting.CONTROVERSIAL;
                                                        askTimePeriod();
                                                        return;
                                                }
                                                SettingValues.prefs
                                                        .edit()
                                                        .putString(
                                                                "defaultSorting",
                                                                SortingUtil.defaultSorting.name())
                                                        .apply();
                                                SettingValues.defaultSorting =
                                                        SortingUtil.defaultSorting;

                                                if (defaultSortingCurrentView != null) {
                                                    defaultSortingCurrentView.setText(
                                                            SortingUtil.getSortingStrings()[
                                                                    SortingUtil.getSortingId("")]);
                                                }
                                            };

                                    // Remove the "Best" sorting option from settings because it is
                                    // only supported on the frontpage.
                                    int skip = -1;
                                    List<String> sortingStrings =
                                            new ArrayList<>(
                                                    Arrays.asList(SortingUtil.getSortingStrings()));
                                    for (int i = 0; i < sortingStrings.size(); i++) {
                                        if (sortingStrings
                                                .get(i)
                                                .equals(context.getString(R.string.sorting_best))) {
                                            skip = i;
                                            break;
                                        }
                                    }
                                    if (skip != -1) {
                                        sortingStrings.remove(skip);
                                    }

                                    new AlertDialog.Builder(SettingsGeneralFragment.this.context)
                                            .setTitle(R.string.sorting_choose)
                                            .setSingleChoiceItems(
                                                    sortingStrings.toArray(new String[0]),
                                                    SortingUtil.getSortingId(""),
                                                    l2)
                                            .show();
                                });
            }
        }

        if (frontpageSortingCurrentView != null) {
            frontpageSortingCurrentView.setText(
                    SortingUtil.getSortingStrings()[SortingUtil.getSortingIdFrontpage()]);
        }

        {
            if (context.findViewById(R.id.settings_general_sorting_frontpage) != null) {
                context.findViewById(R.id.settings_general_sorting_frontpage)
                        .setOnClickListener(
                                v -> {
                                    final DialogInterface.OnClickListener l2 =
                                            (dialogInterface, i) -> {
                                                switch (i) {
                                                    case 0:
                                                        SortingUtil.frontpageSorting = Sorting.HOT;
                                                        break;
                                                    case 1:
                                                        SortingUtil.frontpageSorting = Sorting.NEW;
                                                        break;
                                                    case 2:
                                                        SortingUtil.frontpageSorting =
                                                                Sorting.RISING;
                                                        break;
                                                    case 3:
                                                        SortingUtil.frontpageSorting = Sorting.TOP;
                                                        askTimePeriod();
                                                        return;
                                                    case 4:
                                                        SortingUtil.frontpageSorting =
                                                                Sorting.CONTROVERSIAL;
                                                        askTimePeriod();
                                                        return;
                                                    case 5:
                                                        SortingUtil.frontpageSorting = Sorting.BEST;
                                                        break;
                                                }
                                                SettingValues.prefs
                                                        .edit()
                                                        .putString(
                                                                "frontpageSorting",
                                                                SortingUtil.frontpageSorting.name())
                                                        .apply();
                                                SettingValues.frontpageSorting =
                                                        SortingUtil.frontpageSorting;

                                                if (frontpageSortingCurrentView != null) {
                                                    frontpageSortingCurrentView.setText(
                                                            SortingUtil.getSortingStrings()[
                                                                    SortingUtil
                                                                            .getSortingIdFrontpage()]);
                                                }
                                            };

                                    List<String> sortingStrings =
                                            new ArrayList<>(
                                                    Arrays.asList(SortingUtil.getSortingStrings()));
                                    new AlertDialog.Builder(SettingsGeneralFragment.this.context)
                                            .setTitle(R.string.sorting_choose)
                                            .setSingleChoiceItems(
                                                    sortingStrings.toArray(new String[0]),
                                                    SortingUtil.getSortingIdFrontpage(),
                                                    l2)
                                            .show();
                                });
            }
        }

        doNotifText(context);
        {
            final int i2 =
                    SettingValues.defaultCommentSorting == CommentSort.CONFIDENCE
                            ? 0
                            : SettingValues.defaultCommentSorting == CommentSort.TOP
                                    ? 1
                                    : SettingValues.defaultCommentSorting == CommentSort.NEW
                                            ? 2
                                            : SettingValues.defaultCommentSorting
                                                            == CommentSort.CONTROVERSIAL
                                                    ? 3
                                                    : SettingValues.defaultCommentSorting
                                                                    == CommentSort.OLD
                                                            ? 4
                                                            : SettingValues.defaultCommentSorting
                                                                            == CommentSort.QA
                                                                    ? 5
                                                                    : 0;

            final TextView sortingCurrentCommentView =
                    context.findViewById(R.id.settings_general_sorting_current_comment);
            if (sortingCurrentCommentView != null) {
                sortingCurrentCommentView.setText(SortingUtil.getSortingCommentsStrings()[i2]);
            }

            if (context.findViewById(R.id.settings_general_sorting_comment) != null) {
                context.findViewById(R.id.settings_general_sorting_comment)
                        .setOnClickListener(
                                v -> {
                                    final DialogInterface.OnClickListener l2 =
                                            (dialogInterface, i) -> {
                                                CommentSort commentSorting =
                                                        SettingValues.defaultCommentSorting;
                                                switch (i) {
                                                    case 0:
                                                        commentSorting = CommentSort.CONFIDENCE;
                                                        break;
                                                    case 1:
                                                        commentSorting = CommentSort.TOP;
                                                        break;
                                                    case 2:
                                                        commentSorting = CommentSort.NEW;
                                                        break;
                                                    case 3:
                                                        commentSorting = CommentSort.CONTROVERSIAL;
                                                        break;
                                                    case 4:
                                                        commentSorting = CommentSort.OLD;
                                                        break;
                                                    case 5:
                                                        commentSorting = CommentSort.QA;
                                                        break;
                                                }
                                                SettingValues.prefs
                                                        .edit()
                                                        .putString(
                                                                "defaultCommentSortingNew",
                                                                commentSorting.name())
                                                        .apply();
                                                SettingValues.defaultCommentSorting =
                                                        commentSorting;
                                                if (sortingCurrentCommentView != null) {
                                                    sortingCurrentCommentView.setText(
                                                            SortingUtil.getSortingCommentsStrings()[
                                                                    i]);
                                                }
                                            };

                                    Resources res = context.getBaseContext().getResources();

                                    new AlertDialog.Builder(SettingsGeneralFragment.this.context)
                                            .setTitle(R.string.sorting_choose)
                                            .setSingleChoiceItems(
                                                    new String[] {
                                                        res.getString(R.string.sorting_best),
                                                        res.getString(R.string.sorting_top),
                                                        res.getString(R.string.sorting_new),
                                                        res.getString(
                                                                R.string.sorting_controversial),
                                                        res.getString(R.string.sorting_old),
                                                        res.getString(R.string.sorting_ama)
                                                    },
                                                    i2,
                                                    l2)
                                            .show();
                                });
            }
        }

        // * Client id override
        RelativeLayout clientId = context.findViewById(R.id.settings_general_client_id);
        final TextView currentClientId =
                context.findViewById(R.id.settings_general_client_id_current);

        // Update current value display
        String savedClientId =
                SettingValues.prefs.getString(SettingValues.PREF_REDDIT_CLIENT_ID_OVERRIDE, "");
        if (!savedClientId.isEmpty()) {
            currentClientId.setText(savedClientId);
        }

        clientId.setOnClickListener(
                v -> {
                    showClientIDDialog();
                });

        // Add notification permission request button for Android 13+
        RelativeLayout notifPermLayout = context.findViewById(R.id.settings_general_notification_permission);
        if (notifPermLayout != null) {
            LogUtil.v("Found notification permission layout");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermLayout.setVisibility(View.VISIBLE);
                notifPermLayout.setOnClickListener(new OnSingleClickListener() {
                    @Override
                    public void onSingleClick(View v) {
                        LogUtil.v("Notification permission button clicked");
                        // First check notification permission for Android 13+
                        if (ActivityCompat.shouldShowRequestPermissionRationale(context,
                                Manifest.permission.POST_NOTIFICATIONS)) {
                            LogUtil.v("Showing rationale dialog");
                            new MaterialAlertDialogBuilder(context)
                                    .setTitle(R.string.notifications_permission_title)
                                    .setMessage(R.string.notifications_permission_message)
                                    .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                                        LogUtil.v("Requesting permission after rationale");
                                        ActivityCompat.requestPermissions(
                                            context,
                                            new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                            NOTIFICATION_PERMISSION_REQUEST_CODE
                                        );
                                    })
                                    .setNegativeButton(R.string.btn_cancel, null)
                                    .show();
                        } else {
                            LogUtil.v("Requesting permission directly");
                            ActivityCompat.requestPermissions(
                                context,
                                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                NOTIFICATION_PERMISSION_REQUEST_CODE
                            );
                        }
                        // Then check notification listener permission
                        checkNotificationListenerPermission();
                    }
                });
            } else {
                LogUtil.v("Hiding notification permission layout - not Android 13+");
                notifPermLayout.setVisibility(View.GONE);
            }
        } else {
            LogUtil.v("Could not find notification permission layout");
        }
        {
            SwitchCompat unmuteDefaultSwitch =
                    context.findViewById(R.id.settings_general_unmute_default);
            if (unmuteDefaultSwitch != null) {
                unmuteDefaultSwitch.setChecked(SettingValues.unmuteDefault);
                unmuteDefaultSwitch.setOnCheckedChangeListener(
                        (buttonView, isChecked) -> {
                            SettingValues.unmuteDefault = isChecked;
                            SettingValues.prefs
                                    .edit()
                                    .putBoolean(SettingValues.PREF_UNMUTE_DEFAULT, isChecked)
                                    .apply();
                        });
            }
        }

        {
            SwitchCompat hideSubredditTabsSwitch =
                    context.findViewById(R.id.settings_general_hide_subreddit_tabs);
            if (hideSubredditTabsSwitch != null) {
                hideSubredditTabsSwitch.setChecked(SettingValues.hideSubredditTabs);
                hideSubredditTabsSwitch.setOnCheckedChangeListener(
                        (buttonView, isChecked) -> {
                            SettingsThemeFragment.changed = true;
                            SettingValues.hideSubredditTabs = isChecked;
                            SettingValues.prefs
                                    .edit()
                                    .putBoolean(SettingValues.PREF_HIDE_SUBREDDIT_TABS, isChecked)
                                    .apply();
                            // Explicitly re-read all settings to ensure static values are up-to-date
                            SettingValues.setAllValues(SettingValues.prefs);
                        });
            }
        }
    }

    private void askTimePeriod() {
        final TextView defaultSortingCurrentView =
                context.findViewById(R.id.settings_general_sorting_current);
        final DialogInterface.OnClickListener l2 =
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i) {
                            case 0:
                                SortingUtil.timePeriod = TimePeriod.HOUR;
                                break;
                            case 1:
                                SortingUtil.timePeriod = TimePeriod.DAY;
                                break;
                            case 2:
                                SortingUtil.timePeriod = TimePeriod.WEEK;
                                break;
                            case 3:
                                SortingUtil.timePeriod = TimePeriod.MONTH;
                                break;
                            case 4:
                                SortingUtil.timePeriod = TimePeriod.YEAR;
                                break;
                            case 5:
                                SortingUtil.timePeriod = TimePeriod.ALL;
                                break;
                        }
                        SettingValues.prefs
                                .edit()
                                .putString("defaultSorting", SortingUtil.defaultSorting.name())
                                .apply();
                        SettingValues.prefs
                                .edit()
                                .putString("timePeriod", SortingUtil.timePeriod.name())
                                .apply();
                        SettingValues.defaultSorting = SortingUtil.defaultSorting;
                        SettingValues.timePeriod = SortingUtil.timePeriod;
                        defaultSortingCurrentView.setText(
                                SortingUtil.getSortingStrings()[SortingUtil.getSortingId("")]
                                        + " > "
                                        + SortingUtil.getSortingTimesStrings()[
                                                SortingUtil.getSortingTimeId("")]);
                    }
                };

        new AlertDialog.Builder(SettingsGeneralFragment.this.context)
                .setTitle(R.string.sorting_choose)
                .setSingleChoiceItems(
                        SortingUtil.getSortingTimesStrings(), SortingUtil.getSortingTimeId(""), l2)
                .show();
    }

    private void setSubText() {
        ArrayList<String> rawSubs =
                StringUtil.stringToArray(Reddit.appRestart.getString(CheckForMail.SUBS_TO_GET, ""));
        String subText = context.getString(R.string.sub_post_notifs_settings_none);
        StringBuilder subs = new StringBuilder();
        for (String s : rawSubs) {
            if (!s.isEmpty()) {
                try {
                    String[] split = s.split(":");
                    subs.append(split[0]);
                    subs.append("(+").append(split[1]).append(")");
                    subs.append(", ");
                } catch (Exception ignored) {

                }
            }
        }
        if (!subs.toString().isEmpty()) {
            subText = subs.substring(0, subs.toString().length() - 2);
        }
        ((TextView) context.findViewById(R.id.settings_general_sub_notifs_current))
                .setText(subText);
    }

    private void showSelectDialog() {
        ArrayList<String> rawSubs =
                StringUtil.stringToArray(Reddit.appRestart.getString(CheckForMail.SUBS_TO_GET, ""));
        HashMap<String, Integer> subThresholds = new HashMap<>();
        for (String s : rawSubs) {
            try {
                String[] split = s.split(":");
                subThresholds.put(split[0].toLowerCase(Locale.ENGLISH), Integer.valueOf(split[1]));
            } catch (Exception ignored) {

            }
        }

        // Get list of user's subscriptions
        CaseInsensitiveArrayList subs = UserSubscriptions.getSubscriptions(context);
        // Add any subs that the user has notifications for but isn't subscribed to
        for (String s : subThresholds.keySet()) {
            if (!subs.contains(s)) {
                subs.add(s);
            }
        }

        List<String> sorted = UserSubscriptions.sort(subs);

        // Array of all subs
        String[] all = new String[sorted.size()];
        // Contains which subreddits are checked
        boolean[] checked = new boolean[all.length];

        // Remove special subreddits from list and store it in "all"
        int i = 0;
        for (String s : sorted) {
            if (!s.equals("all")
                    && !s.equals("frontpage")
                    && !s.contains("+")
                    && !s.contains(".")
                    && !s.contains("/m/")) {
                all[i] = s.toLowerCase(Locale.ENGLISH);
                i++;
            }
        }

        // Remove empty entries & store which subreddits are checked
        List<String> list = new ArrayList<>();
        i = 0;
        for (String s : all) {
            if (s != null && !s.isEmpty()) {
                list.add(s);
                if (subThresholds.containsKey(s)) {
                    checked[i] = true;
                }
                i++;
            }
        }

        // Convert List back to Array
        all = list.toArray(new String[0]);

        final ArrayList<String> toCheck = new ArrayList<>(subThresholds.keySet());
        final String[] finalAll = all;
        new AlertDialog.Builder(SettingsGeneralFragment.this.context)
                .setMultiChoiceItems(
                        finalAll,
                        checked,
                        (dialog, which, isChecked) -> {
                            if (!isChecked) {
                                toCheck.remove(finalAll[which]);
                            } else {
                                toCheck.add(finalAll[which]);
                            }
                        })
                .setTitle(R.string.sub_post_notifs_title_settings)
                .setPositiveButton(
                        context.getString(R.string.btn_add).toUpperCase(),
                        (dialog, which) -> showThresholdDialog(toCheck, false)
                )
                .setNegativeButton(
                        R.string.sub_post_notifs_settings_search,
                        (dialog, which) ->
                                new MaterialDialog.Builder(SettingsGeneralFragment.this.context)
                                        .title(R.string.reorder_add_subreddit)
                                        .inputRangeRes(2, 21, R.color.md_red_500)
                                        .alwaysCallInputCallback()
                                        .input(
                                                context.getString(R.string.reorder_subreddit_name),
                                                null,
                                                false,
                                                new MaterialDialog.InputCallback() {
                                                    @Override
                                                    public void onInput(
                                                            MaterialDialog dialog,
                                                            CharSequence raw) {
                                                        input =
                                                                raw.toString()
                                                                        .replaceAll(
                                                                                "\\s",
                                                                                ""); // remove
                                                        // whitespace
                                                        // from input
                                                    }
                                                })
                                        .positiveText(R.string.btn_add)
                                        .onPositive(
                                                new MaterialDialog.SingleButtonCallback() {
                                                    @Override
                                                    public void onClick(
                                                            @NonNull MaterialDialog dialog,
                                                            @NonNull DialogAction which) {
                                                        new AsyncGetSubreddit().execute(input);
                                                    }
                                                })
                                        .negativeText(R.string.btn_cancel)
                                        .show())
                .show();
    }

    private void showThresholdDialog(ArrayList<String> strings, boolean search) {
        final ArrayList<String> subsRaw =
                StringUtil.stringToArray(Reddit.appRestart.getString(CheckForMail.SUBS_TO_GET, ""));

        if (!search) {
            // NOT a sub searched for, was instead a list of all subs
            for (String raw : new ArrayList<>(subsRaw)) {
                if (!strings.contains(raw.split(":")[0])) {
                    subsRaw.remove(raw);
                }
            }
        }

        final ArrayList<String> subs = new ArrayList<>();
        for (String s : subsRaw) {
            try {
                subs.add(s.split(":")[0].toLowerCase(Locale.ENGLISH));
            } catch (Exception e) {

            }
        }

        final ArrayList<String> toAdd = new ArrayList<>();
        for (String s : strings) {
            if (!subs.contains(s.toLowerCase(Locale.ENGLISH))) {
                toAdd.add(s.toLowerCase(Locale.ENGLISH));
            }
        }
        if (!toAdd.isEmpty()) {
            final int[] selectedThreshold = {0}; // Default to index 0 ("1")
            final String[] thresholds = new String[] {"1", "5", "10", "20", "40", "50"};
            new MaterialDialog.Builder(SettingsGeneralFragment.this.context)
                    .title(R.string.sub_post_notifs_threshold)
                    .items(thresholds)
                    .alwaysCallSingleChoiceCallback()
                    .itemsCallbackSingleChoice(
                            0,
                            new MaterialDialog.ListCallbackSingleChoice() {
                                @Override
                                public boolean onSelection(
                                        MaterialDialog dialog,
                                        View itemView,
                                        int which,
                                        CharSequence text) {
                                    selectedThreshold[0] = which;
                                    return true;
                                }
                            })
                    .positiveText(R.string.btn_ok)
                    .negativeText(R.string.btn_cancel)
                    .onPositive((dialog, which) -> {
                        for (String s : toAdd) {
                            subsRaw.add(s + ":" + thresholds[selectedThreshold[0]]);
                        }
                        saveAndUpdateSubs(subsRaw);
                    })
                    .cancelable(true)
                    .show();
        } else {
            saveAndUpdateSubs(subsRaw);
        }
    }

    private void saveAndUpdateSubs(ArrayList<String> subs) {
        Reddit.appRestart
                .edit()
                .putString(CheckForMail.SUBS_TO_GET, StringUtil.arrayToString(subs))
                .commit();
        setSubText();
    }

    private class AsyncGetSubreddit extends AsyncTask<String, Void, Subreddit> {
        @Override
        public void onPostExecute(Subreddit subreddit) {
            if (subreddit != null
                    || input.equalsIgnoreCase("friends")
                    || input.equalsIgnoreCase("mod")) {
                ArrayList<String> singleSub = new ArrayList<>();
                singleSub.add(subreddit.getDisplayName().toLowerCase(Locale.ENGLISH));
                showThresholdDialog(singleSub, true);
            }
        }

        @Override
        protected Subreddit doInBackground(final String... params) {
            try {
                return Authentication.reddit.getSubreddit(params[0]);
            } catch (Exception e) {
                context.runOnUiThread(
                        () -> {
                            try {
                                new AlertDialog.Builder(SettingsGeneralFragment.this.context)
                                        .setTitle(R.string.subreddit_err)
                                        .setMessage(
                                                context.getString(
                                                        R.string.subreddit_err_msg, params[0]))
                                        .setPositiveButton(
                                                R.string.btn_ok,
                                                (dialog, which) -> dialog.dismiss())
                                        .setOnDismissListener(null)
                                        .show();
                            } catch (Exception ignored) {
                            }
                        });

                return null;
            }
        }
    }

    private void showClientIDDialog() {
        final Context contextThemeWrapper = new ContextThemeWrapper(context,
                new ColorPreferences(context).getFontStyle().getBaseId());

        final EditText input = new EditText(contextThemeWrapper);
        String savedClientId = SettingValues.prefs.getString(SettingValues.PREF_REDDIT_CLIENT_ID_OVERRIDE, "");
        input.setText(savedClientId);

        // Convert 16dp to pixels and set left padding
        int paddingDp = 16;
        float density = context.getResources().getDisplayMetrics().density;
        int paddingPx = (int)(paddingDp * density);
        input.setPadding(paddingPx, input.getPaddingTop(), input.getPaddingRight(), input.getPaddingBottom());

        // Create container for dialog content
        LinearLayout dialogContainer = new LinearLayout(contextThemeWrapper);
        dialogContainer.setOrientation(LinearLayout.VERTICAL);

        // Add top padding view
        View paddingView = new View(contextThemeWrapper);
        paddingView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, paddingPx));
        dialogContainer.addView(paddingView);

        // Add instructions link
        TextView linkText = new TextView(contextThemeWrapper);
        linkText.setText(R.string.client_id_instructions);
        linkText.setTextColor(new ColorPreferences(contextThemeWrapper).getColor(""));
        linkText.setPaintFlags(linkText.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        linkText.setPadding(paddingPx, 0, 0, paddingPx);
        linkText.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.setup_md_url)));
            context.startActivity(browserIntent);
        });
        dialogContainer.addView(linkText);

        // Create horizontal layout for input field and camera button
        LinearLayout inputLayout = new LinearLayout(contextThemeWrapper);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        inputLayout.setPadding(paddingPx, 0, paddingPx, paddingPx);

        // Configure input field to take most of the space
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        input.setLayoutParams(inputParams);

        // Add themed QR code scan button (camera icon)
        ImageButton scanQrButton = new ImageButton(contextThemeWrapper);
        scanQrButton.setImageResource(R.drawable.ic_camera);
        scanQrButton.setPadding(0,0,0,0); // Remove padding to make it compact

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(paddingPx/2, 0, 0, 0); // Add margin to separate from input
        scanQrButton.setLayoutParams(buttonParams);

        scanQrButton.setOnClickListener(v -> {
            // Check camera permission
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Request camera permission via helper (which handles callback storage)
                QrCodeScannerHelper.startScan(context, new QrCodeScannerHelper.EditTextUpdateCallback(input, context));
            } else {
                // Permission already granted, show the scanner dialog
                QrCodeScannerHelper.startScan(context, new QrCodeScannerHelper.EditTextUpdateCallback(input, context));
            }
        });

        // Add views to horizontal layout
        inputLayout.addView(input);
        inputLayout.addView(scanQrButton);

        // Add horizontal layout to main container
        dialogContainer.addView(inputLayout);

        final TextView currentClientIdView = context.findViewById(R.id.settings_general_client_id_current);

        new MaterialAlertDialogBuilder(contextThemeWrapper)
                .setTitle(R.string.reddit_client_id_override)
                .setView(dialogContainer)
                .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                    String newClientId = input.getText().toString().trim();
                    String oldClientId = SettingValues.prefs.getString(SettingValues.PREF_REDDIT_CLIENT_ID_OVERRIDE, "");

                    // Only proceed if the client ID has changed
                    if (!newClientId.equals(oldClientId)) {
                        // Set the value in memory
                        SettingValues.redditClientIdOverride = newClientId;

                        // Save to preferences
                        if (newClientId.isEmpty()) {
                            SettingValues.prefs.edit()
                                    .remove(SettingValues.PREF_REDDIT_CLIENT_ID_OVERRIDE)
                                    .commit();
                        } else {
                            SettingValues.prefs.edit()
                                    .putString(SettingValues.PREF_REDDIT_CLIENT_ID_OVERRIDE, newClientId)
                                    .commit();
                        }

                        // Update displays
                        currentClientIdView.setText(newClientId.isEmpty() ?
                                context.getString(R.string.click_custom_client_id) : newClientId);

                        // Restart the app immediately
                        ((Reddit) context.getApplicationContext()).forceRestart(context, false);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /**
     * Handle permission request results for camera access
     * @param requestCode the request code
     * @param permissions the requested permissions
     * @param grantResults the permission grant results
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == QrCodeScannerHelper.CAMERA_PERMISSION_REQUEST_CODE) {
            QrCodeScannerHelper.handlePermissionsResult(requestCode, grantResults, context);
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            // Handle notification permission result (if needed, currently handled by system)
            LogUtil.v("Received notification permission result: " + (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED));
        }
    }

    /**
     * Helper method to find an EditText within a view hierarchy
     * @param view the parent view to search in
     * @return the first EditText found, or null
     */
    private EditText findEditTextInView(View view) {
        if (view instanceof EditText) {
            return (EditText) view;
        } else if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                EditText found = findEditTextInView(viewGroup.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void checkNotificationListenerPermission() {
        String packageName = context.getPackageName();
        String flat = Settings.Secure.getString(context.getContentResolver(),
                "enabled_notification_listeners");
        boolean enabled = flat != null && flat.contains(packageName);

        LogUtil.v("Notification Listener enabled: " + enabled);

        if (!enabled) {
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.notification_listener_permission_title)
                    .setMessage(R.string.notification_listener_permission_message)
                    .setPositiveButton(R.string.btn_ok, (dialog, which) -> {
                        Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
                        try {
                            context.startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(context,
                                "Could not open notification listener settings",
                                Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show();
        }
    }
}
