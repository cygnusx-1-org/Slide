package me.edgan.redditslide.util;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;

/**
 * Utility class for handling content filtering dialogs
 */
public class FilterContentUtil {

    /**
     * Shows a dialog to filter content for a specific subreddit
     *
     * @param activity The activity context
     * @param subreddit The subreddit to filter content for
     * @param onSaveCallback Callback to execute after filters are saved
     */
    public static void showFilterDialog(Activity activity, String subreddit, Runnable onSaveCallback) {
        // Create a custom dialog view with our own button layout
        LinearLayout customDialogView = new LinearLayout(activity);
        customDialogView.setOrientation(LinearLayout.VERTICAL);

        // Add the original filter dialog content
        View filterContent = LayoutInflater.from(activity).inflate(R.layout.dialog_two_column_filter, null);
        customDialogView.addView(filterContent);

        // Get references to the list views
        final ListView regularListView = filterContent.findViewById(R.id.regular_content_list);
        final ListView nsfwListView = filterContent.findViewById(R.id.nsfw_content_list);

        // Create a wrapper class to hold the lists reference that can be modified
        class ListsHolder {
            FilterUtil.FilterLists lists;
        }

        final ListsHolder listsHolder = new ListsHolder();
        listsHolder.lists = FilterUtil.setupFilterLists(activity, subreddit, false);

        FilterUtil.setupListViews(activity, regularListView, nsfwListView, listsHolder.lists);

        final String displayName;
        if (subreddit.equals("frontpage")) {
            displayName = "frontpage";
        } else if (subreddit.contains("/m/")) {
            displayName = subreddit.substring(subreddit.indexOf("/m/"));
        } else {
            displayName = "/r/" + subreddit;
        }

        final String FILTER_TITLE = activity.getString(R.string.content_to_show, displayName);

        // Create the dialog with no buttons (we'll add our own)
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(FILTER_TITLE)
                .setView(customDialogView);

        final AlertDialog dialog = builder.create();

        // Create button bar at the bottom
        LinearLayout buttonBar = new LinearLayout(activity);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Left side group for toggle and reset
        LinearLayout leftButtonGroup = new LinearLayout(activity);
        leftButtonGroup.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        leftButtonGroup.setOrientation(LinearLayout.HORIZONTAL);

        // Right side group for cancel and save
        LinearLayout rightButtonGroup = new LinearLayout(activity);
        rightButtonGroup.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        rightButtonGroup.setOrientation(LinearLayout.HORIZONTAL);

        // Create the toggle button (left)
        Button toggleButton = new Button(activity, null, android.R.attr.buttonBarButtonStyle);
        toggleButton.setText(R.string.btn_toggle);
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleButton.setLayoutParams(toggleParams);

        // Create the reset button
        Button resetButton = new Button(activity, null, android.R.attr.buttonBarButtonStyle);
        resetButton.setText(R.string.btn_reset);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        resetButton.setLayoutParams(resetParams);

        // Create spacer view to fill left side when reset button is hidden
        View spacerView = new View(activity);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(
                0, 0, 1.0f); // Set height to zero to prevent taking vertical space
        spacerView.setLayoutParams(spacerParams);

        // Hide reset button if NSFW content is disabled
        if (!SettingValues.showNSFWContent) {
            resetButton.setVisibility(View.GONE);
        }

        // Create the cancel button
        Button cancelButton = new Button(activity, null, android.R.attr.buttonBarButtonStyle);
        cancelButton.setText(R.string.btn_cancel);
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cancelButton.setLayoutParams(cancelParams);

        // Create the save button
        Button saveButton = new Button(activity, null, android.R.attr.buttonBarButtonStyle);
        saveButton.setText(R.string.btn_save);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        saveButton.setLayoutParams(saveParams);

        // Create spacer view to match left side spacing
        View rightSpacerView = new View(activity);
        LinearLayout.LayoutParams rightSpacerParams = new LinearLayout.LayoutParams(
                0, 0, 1.0f); // Set height to zero to prevent taking vertical space
        rightSpacerView.setLayoutParams(rightSpacerParams);

        // Add buttons to left group
        leftButtonGroup.addView(toggleButton);
        leftButtonGroup.addView(resetButton);
        leftButtonGroup.addView(spacerView);

        rightButtonGroup.addView(rightSpacerView);
        rightButtonGroup.addView(cancelButton);
        rightButtonGroup.addView(saveButton);

        // Set gravity on button groups to properly align buttons
        leftButtonGroup.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        rightButtonGroup.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);

        // Add button groups to the button bar
        buttonBar.addView(leftButtonGroup);
        buttonBar.addView(rightButtonGroup);

        // Add the button bar to the dialog
        customDialogView.addView(buttonBar);

        // Set up button click listeners
        toggleButton.setOnClickListener(view -> {
            if (SettingValues.showNSFWContent) {
                // Use the existing advanced toggle when NSFW content is enabled
                FilterToggleUtil.handleFilterToggle(regularListView, nsfwListView, listsHolder.lists,
                        true, 0);
            } else {
                // Simple toggle for just the regular content when NSFW is disabled
                // Toggle between all selected and none selected for regular items
                boolean allSelected = true;
                for (int i = 0; i < listsHolder.lists.regularList.size(); i++) {
                    if (!regularListView.isItemChecked(i)) {
                        allSelected = false;
                        break;
                    }
                }

                // If all are selected, unselect all. Otherwise, select all.
                for (int i = 0; i < listsHolder.lists.regularList.size(); i++) {
                    regularListView.setItemChecked(i, !allSelected);
                }
            }
        });

        resetButton.setOnClickListener(view -> {
            listsHolder.lists = FilterUtil.setupFilterLists(activity, subreddit, true);
            FilterUtil.setupListViews(activity, regularListView, nsfwListView, listsHolder.lists);
        });

        saveButton.setOnClickListener(view -> {
            FilterUtil.saveFilters(regularListView, nsfwListView, listsHolder.lists, subreddit);
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
            dialog.dismiss();
        });

        cancelButton.setOnClickListener(view -> {
            dialog.dismiss();
        });

        // Prevent dismissing when clicking outside
        dialog.setCanceledOnTouchOutside(false);

        dialog.show();

        // Make dialog width match screen width
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
        }
    }
}
