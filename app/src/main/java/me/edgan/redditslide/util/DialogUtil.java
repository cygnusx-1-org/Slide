package me.edgan.redditslide.util;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import me.edgan.redditslide.R;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.Palette;

/** Created by TacoTheDank on 07/14/2021. Updated to use Storage Access Framework */
public class DialogUtil {
    private static final String TAG = "DialogUtil";

    public static void showErrorDialog(final AppCompatActivity activity) {
        Log.d(TAG, "showErrorDialog called from: " + Log.getStackTraceString(new Exception()));

        Uri currentUri = StorageUtil.getStorageUri(activity);
        Log.d(TAG, "Current storage URI: " + currentUri);

        if (currentUri != null) {
            DocumentFile file = DocumentFile.fromTreeUri(activity, currentUri);
            Log.d(TAG, "Current DocumentFile exists: " + (file != null));
            if (file != null) {
                Log.d(TAG, "DocumentFile canWrite: " + file.canWrite());
                Log.d(TAG, "DocumentFile name: " + file.getName());
            }
        }

        showBaseChooserDialog(
                activity, R.string.err_something_wrong, R.string.err_couldnt_save_choose_new);
    }

    public static void showFirstDialog(final AppCompatActivity activity) {
        showBaseChooserDialog(activity, R.string.set_save_location, R.string.set_save_location_msg);
    }

    private static void showBaseChooserDialog(
            final AppCompatActivity activity,
            final @StringRes int titleId,
            final @StringRes int messageId) {
        AlertDialog dialog =
                new AlertDialog.Builder(activity)
                        .setTitle(titleId)
                        .setMessage(messageId)
                        .setPositiveButton(
                                android.R.string.ok,
                                (dialogInterface, which) -> {
                                    try {
                                        StorageUtil.showDirectoryChooser(activity);
                                    } catch (Exception e) {
                                        LogUtil.e(e, TAG + "Error launching directory chooser");
                                    }
                                })
                        .create();

        dialog.show();
    }

    /**
     * Applies a custom accent-colored border to an AlertDialog.
     * IMPORTANT: This method must be called BEFORE dialog.show() to prevent visual "jumping".
     *
     * @param context Context for accessing resources
     * @param dialog The AlertDialog to apply the border to
     */
    public static void applyCustomBorderToAlertDialog(Context context, AlertDialog dialog) {
        // Only apply the border if the setting is enabled
        if(SettingValues.dialogColoredBorder) {
            if (dialog != null && dialog.getWindow() != null) {
                // Create a GradientDrawable with the accent color
                GradientDrawable drawable = new GradientDrawable();

                // Get the appropriate background color from the current theme
                TypedArray ta = context.obtainStyledAttributes(new int[] {android.R.attr.colorBackground});
                int backgroundColor = ta.getColor(0, Color.BLACK);
                ta.recycle();

                // Set the drawable properties
                drawable.setColor(backgroundColor);
                drawable.setStroke(DisplayUtil.dpToPxVertical(1), Palette.getDarkerColor(Palette.getStatusBarColor()));
                drawable.setCornerRadius(DisplayUtil.dpToPxVertical(1));

                // Apply the drawable to the dialog window
                dialog.getWindow().setBackgroundDrawable(drawable);
            }
        }
    }

    /**
     * Matches an AlertDialog's window (and therefore its button bar) to the app's themed
     * card_background color. AppCompat dialogs otherwise keep a gray default background that
     * clashes with the dark/black card content Slide uses, leaving a gray panel behind the
     * dialog buttons. Call before dialog.show() to avoid a visual flash.
     *
     * @param context Context whose theme defines card_background
     * @param dialog The AlertDialog to recolor
     */
    public static void matchDialogToCardBackground(Context context, AlertDialog dialog) {
        if (dialog == null || dialog.getWindow() == null) {
            return;
        }
        TypedValue cardBackground = new TypedValue();
        if (context.getTheme().resolveAttribute(R.attr.card_background, cardBackground, true)) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(cardBackground.data));
        }
    }
}
