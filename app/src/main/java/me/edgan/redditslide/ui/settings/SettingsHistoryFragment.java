package me.edgan.redditslide.ui.settings;

import android.app.Activity;
import android.widget.RelativeLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import com.lusfold.androidkeyvaluestore.KVStore;
import me.edgan.redditslide.R;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.CaseInsensitiveArrayList;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.util.DialogUtil;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class SettingsHistoryFragment {

    private final Activity context;

    public SettingsHistoryFragment(Activity context) {
        this.context = context;
    }

    public void Bind() {
        final SwitchCompat storeHistorySwitch =
                context.requireViewById(R.id.settings_history_storehistory);
        final SwitchCompat storeNsfwHistorySwitch =
                context.requireViewById(R.id.settings_history_storensfw);
        final SwitchCompat scrollSeenSwitch =
                context.requireViewById(R.id.settings_history_scrollseen);

        final RelativeLayout clearPostsLayout =
                context.requireViewById(R.id.settings_history_clearposts);
        final RelativeLayout clearSubsLayout =
                context.requireViewById(R.id.settings_history_clearsubs);
        final RelativeLayout manageSubsLayout =
                context.requireViewById(R.id.settings_history_managesubs);
                
        // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // * Save history */
        storeHistorySwitch.setChecked(SettingValues.storeHistory);
        storeHistorySwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    SettingValues.storeHistory = isChecked;
                    editSharedBooleanPreference(SettingValues.PREF_STORE_HISTORY, isChecked);

                    if (isChecked) {
                        scrollSeenSwitch.setEnabled(true);
                        storeNsfwHistorySwitch.setEnabled(true);
                    } else {
                        storeNsfwHistorySwitch.setChecked(false);
                        storeNsfwHistorySwitch.setEnabled(false);
                        SettingValues.storeNSFWHistory = false;
                        editSharedBooleanPreference(SettingValues.PREF_STORE_NSFW_HISTORY, false);

                        scrollSeenSwitch.setChecked(false);
                        scrollSeenSwitch.setEnabled(false);
                        SettingValues.scrollSeen = false;
                        editSharedBooleanPreference(SettingValues.PREF_SCROLL_SEEN, false);
                    }
                });
        // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        storeNsfwHistorySwitch.setChecked(SettingValues.storeNSFWHistory);
        storeNsfwHistorySwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    SettingValues.storeNSFWHistory = isChecked;
                    editSharedBooleanPreference(SettingValues.PREF_STORE_NSFW_HISTORY, isChecked);
                });
        // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        scrollSeenSwitch.setChecked(SettingValues.scrollSeen);
        scrollSeenSwitch.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    SettingValues.scrollSeen = isChecked;
                    editSharedBooleanPreference(SettingValues.PREF_SCROLL_SEEN, isChecked);
                });

        // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        // * Clear history */
        clearPostsLayout.setOnClickListener(
                v -> {
                    KVStore.getInstance().clearTable();
                    // The seen sets are loaded once and are what the post lists consult
                    HasSeen.hasSeen.clear();
                    HasSeen.seenTimes.clear();
                    showHistoryClearedDialog();
                });
        // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        manageSubsLayout.setOnClickListener(v -> showManageSubsDialog());
        // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        clearSubsLayout.setOnClickListener(
                v -> {
                    UserSubscriptions.subscriptions.edit().remove("subhistory").apply();
                    showHistoryClearedDialog();
                });
    }

    private void showManageSubsDialog() {
        final CaseInsensitiveArrayList manual = UserSubscriptions.getManualHistory(context);
        if (manual.isEmpty()) {
            DialogUtil.showWithCardBackground(new AlertDialog.Builder(context)
                    .setMessage(R.string.manage_subreddit_history_empty)
                    .setPositiveButton(android.R.string.ok, null));
            return;
        }

        final boolean[] checked = new boolean[manual.size()];
        DialogUtil.showWithCardBackground(new AlertDialog.Builder(context)
                .setTitle(R.string.manage_subreddit_history)
                .setMultiChoiceItems(
                        manual.toArray(new String[0]),
                        checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(
                        R.string.btn_delete,
                        (dialog, which) -> {
                            final List<String> toRemove = new ArrayList<>();
                            for (int i = 0; i < checked.length; i++) {
                                if (checked[i]) {
                                    toRemove.add(manual.get(i));
                                }
                            }
                            UserSubscriptions.removeSubsFromHistory(toRemove);
                        })
                .setNegativeButton(R.string.btn_cancel, null));
    }

    private void showHistoryClearedDialog() {
        DialogUtil.showWithCardBackground(new AlertDialog.Builder(context)
                .setTitle(R.string.alert_history_cleared)
                .setPositiveButton(android.R.string.ok, null)
                );
    }

    private void editSharedBooleanPreference(
            final String settingValueString, final boolean isChecked) {
        SettingValues.prefs.edit().putBoolean(settingValueString, isChecked).apply();
    }
}
