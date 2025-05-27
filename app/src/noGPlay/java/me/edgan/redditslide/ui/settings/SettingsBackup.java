package me.edgan.redditslide.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.material.snackbar.Snackbar;
import com.jakewharton.processphoenix.ProcessPhoenix;

import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.StorageUtil;
import me.edgan.redditslide.util.MiscUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Handles local (SAF-based) backup and restore of app settings, and stripped of all Google Drive
 * code.
 */
public class SettingsBackup extends BaseActivityAnim {

    private static final String TAG = "SettingsBackup";

    // Request code for the SAF-based "open document" action
    private static final int RC_OPEN_DOCUMENT = 102;

    // Progress dialog
    private MaterialDialog progress;

    // We’ll store the final URI of the newly created local backup file so we can offer to "View" it.
    private Uri localBackupFileUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyColorTheme();
        setContentView(R.layout.activity_settings_sync);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_backup, true, true);

        // Set up the local backup/restore UI
        setupUI();
    }

    /** Initialize button click listeners for local backup/restore only. */
    private void setupUI() {
        // Create a local backup with SAF (user chooses directory)
        findViewById(R.id.backfile).setOnClickListener(v -> showBackupToDirDialog());

        // Restore from a local backup file (user chooses file)
        findViewById(R.id.restorefile).setOnClickListener(v -> openRestoreFile());
    }

    /** Ask user for confirmation, then perform local backup to a user-chosen directory via SAF. */
    private void showBackupToDirDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.backup_question)
                .setPositiveButton(R.string.btn_ok, (dialog, which) -> backupToDir())
                .setNeutralButton(R.string.btn_cancel, null)
                .setCancelable(false)
                .show();
    }

    /** Performs the actual local backup using the SAF-chosen directory from StorageUtil. */
    private void backupToDir() {
        Uri treeUri = StorageUtil.getStorageUri(this);

        if (treeUri == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.err_general)
                    .setMessage(R.string.set_storage_location)
                    .setPositiveButton(R.string.btn_ok, (dialog, which) -> {})
                    .setCancelable(false)
                    .show();
            return;
        }

        progress =
                new MaterialDialog.Builder(SettingsBackup.this)
                        .title(R.string.backup_backing_up)
                        .content(R.string.misc_please_wait)
                        .cancelable(false)
                        .progress(true, 0)
                        .build();
        progress.show();

        new AsyncTask<Void, Void, Boolean>() {
            private String errorMessage = null;

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    DocumentFile parentDir = DocumentFile.fromTreeUri(SettingsBackup.this, treeUri);
                    if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory()) {
                        errorMessage = "Invalid backup directory.";
                        return false;
                    }

                    // Generate a backup filename
                    String timeStamp =
                            new SimpleDateFormat("-yyyy-MM-dd-HH-mm-ss").format(new Date());
                    String fileName = "Slide" + timeStamp + ".txt";

                    // Create the backup file in the chosen directory
                    DocumentFile backupFile = parentDir.createFile("text/plain", fileName);
                    if (backupFile == null) {
                        errorMessage = "Failed to create backup file via SAF.";
                        return false;
                    }

                    localBackupFileUri = backupFile.getUri();

                    // Start writing
                    OutputStream out = getContentResolver().openOutputStream(localBackupFileUri);
                    if (out == null) {
                        errorMessage = "OutputStream was null for: " + localBackupFileUri;
                        return false;
                    }

                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
                    // Start marker
                    bw.write("Slide_backupEND>");

                    File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
                    if (!prefsDir.exists() || !prefsDir.isDirectory()) {
                        Log.w(TAG, "No shared_prefs directory found for local backup.");
                        bw.close();
                        return true; // It's "valid" but empty
                    }

                    String[] list = prefsDir.list();
                    if (list == null) {
                        Log.w(TAG, "No preference files found to backup locally.");
                        bw.close();
                        return true;
                    }

                    // Copy the content of each eligible pref file into a single big text file
                    for (String s : list) {
                        if (!s.contains("cache")
                                && !s.contains("ion-cookies")
                                && !s.contains("albums")
                                && !s.contains("STACKTRACE")
                                && !s.contains("com.google")) {

                            File fileToBackup = new File(prefsDir, s);
                            if (fileToBackup.exists()) {
                                BufferedReader br =
                                        new BufferedReader(new FileReader(fileToBackup));
                                bw.write("<START" + s + ">");
                                char[] buf = new char[8192];
                                int read;
                                while ((read = br.read(buf)) != -1) {
                                    bw.write(buf, 0, read);
                                }
                                bw.write("END>");
                                br.close();
                                Log.d(TAG, "Backed up local file: " + s);
                            }
                        } else {
                            Log.d(TAG, "Skipping local file: " + s);
                        }
                    }

                    bw.close();
                    return true;

                } catch (IOException e) {
                    Log.e(TAG, "Error creating or writing backup file", e);
                    errorMessage = e.getMessage();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (progress != null) {
                    progress.dismiss();
                }
                if (!success) {
                    Log.w(TAG, "Local backup failed: " + errorMessage);
                    showErrorDialog(R.string.err_general, R.string.set_storage_location);
                    return;
                }

                // Show success dialog with a "View" button
                new AlertDialog.Builder(SettingsBackup.this)
                        .setTitle(R.string.backup_complete)
                        .setMessage(R.string.backup_saved_downloads) // or a generic success message
                        .setPositiveButton(
                                R.string.btn_view,
                                (dialog, which) -> {
                                    if (localBackupFileUri != null) {
                                        // Attempt to open with a viewer
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        intent.setDataAndType(localBackupFileUri, "text/plain");
                                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                                        if (intent.resolveActivityInfo(getPackageManager(), 0)
                                                != null) {
                                            startActivity(
                                                    Intent.createChooser(
                                                            intent,
                                                            getString(
                                                                    R.string
                                                                            .settings_backup_view)));
                                        } else {
                                            Snackbar s =
                                                    Snackbar.make(
                                                            findViewById(R.id.restorefile),
                                                            getString(
                                                                            R.string
                                                                                    .settings_backup_err_no_explorer)
                                                                    + localBackupFileUri,
                                                            Snackbar.LENGTH_INDEFINITE);
                                            LayoutUtils.showSnackbar(s);
                                        }
                                    }
                                })
                        .setNegativeButton(R.string.btn_close, null)
                        .setCancelable(false)
                        .show();
            }
        }.execute();
    }

    /** Open a file picker to select a backup file for local restoration (SAF). */
    private void openRestoreFile() {
        Log.d(TAG, "openRestoreFile() called.");

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*"); // or "text/plain"
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // If you’re storing the user-chosen directory, this tries to open it:
        Uri treeUri = StorageUtil.getStorageUri(this);
        if (treeUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri);
        }

        startActivityForResult(
                Intent.createChooser(intent, getString(R.string.select_backup_file)),
                RC_OPEN_DOCUMENT);
    }

    /** Handle the result from the SAF file picker for local restore only. */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_OPEN_DOCUMENT) {
            handleFilePickerResult(resultCode, data);
        }
    }

    /** SAF file picker result for local restore. */
    private void handleFilePickerResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            Log.d(TAG, "Selected local backup file URI: " + fileUri);

            // Start async restore
            progress =
                    new MaterialDialog.Builder(this)
                            .title(R.string.backup_restoring)
                            .content(R.string.misc_please_wait)
                            .cancelable(false)
                            .progress(true, 1)
                            .build();
            progress.show();

            new RestoreFromFileAsyncTask(fileUri).execute();
        } else {
            Log.w(TAG, "No file chosen or result not OK.");
            showErrorDialog(R.string.err_file_not_found, R.string.err_file_not_found_msg);
        }
    }

    /** Async task to restore from a local SAF-chosen file (single backup file). */
    private class RestoreFromFileAsyncTask extends AsyncTask<Void, Void, Boolean> {
        private final Uri fileUri;

        RestoreFromFileAsyncTask(Uri fileUri) {
            this.fileUri = fileUri;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            Log.d(TAG, "RestoreFromFileAsyncTask started for URI: " + fileUri);
            StringBuilder fw = new StringBuilder();
            try (InputStream is = getContentResolver().openInputStream(fileUri);
                    BufferedReader reader =
                            (is == null) ? null : new BufferedReader(new InputStreamReader(is))) {

                if (reader == null) {
                    Log.e(TAG, "Could not open InputStream for fileUri: " + fileUri);
                    return false;
                }

                char[] buf = new char[8192];
                int read;
                while ((read = reader.read(buf)) != -1) {
                    fw.append(buf, 0, read);
                }

                String readContent = fw.toString();
                Log.d(TAG, "Read " + readContent.length() + " characters from backup file.");

                // Use the same parse logic
                return restoreSharedPrefsFromString(readContent);

            } catch (Exception e) {
                Log.e(TAG, "Exception while restoring from fileUri: " + fileUri, e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (progress != null) {
                progress.dismiss();
            }
            if (success) {
                new AlertDialog.Builder(SettingsBackup.this)
                        .setTitle(R.string.backup_restore_settings)
                        .setMessage(R.string.backup_restarting)
                        .setOnDismissListener(
                                dialog -> {
                                    Log.d(
                                            TAG,
                                            "ProcessPhoenix.triggerRebirth() from onDismiss for"
                                                    + " local file restore.");
                                    ProcessPhoenix.triggerRebirth(SettingsBackup.this);
                                })
                        .setPositiveButton(
                                R.string.btn_ok,
                                (dialog, which) -> {
                                    Log.d(
                                            TAG,
                                            "ProcessPhoenix.triggerRebirth() from OK button for"
                                                    + " local file restore.");
                                    ProcessPhoenix.triggerRebirth(SettingsBackup.this);
                                })
                        .setCancelable(false)
                        .show();
            } else {
                Log.w(TAG, "Restore from local file failed or invalid file.");
                showErrorDialog(R.string.err_not_valid_backup, R.string.err_not_valid_backup_msg);
            }
        }
    }

    /**
     * Parse the single large backup string, writing each <STARTfilename>…END> block into its
     * corresponding shared_prefs file.
     */
    private boolean restoreSharedPrefsFromString(String data) {
        try {
            if (!data.contains("Slide_backupEND>")) {
                Log.w(
                        TAG,
                        "Backup file did not contain 'Slide_backupEND>' marker, likely invalid.");
                return false;
            }

            // Example data:
            // Slide_backupEND><STARTsomefile.xml>filecontentEND><STARTotherfile.xml>filecontentEND>...
            String[] files = data.split("END>");
            // files[0] should contain "Slide_backupEND>", skip it
            for (int i = 1; i < files.length; i++) {
                String innerFile = files[i];
                int startIndex = innerFile.indexOf("<START");
                if (startIndex == -1) {
                    Log.w(TAG, "Skipping malformed file block: " + innerFile);
                    continue;
                }

                // Extract filename
                String name =
                        innerFile.substring(startIndex + 6, innerFile.indexOf(">", startIndex));
                // Extract file content
                String fileContent = innerFile.substring(innerFile.indexOf(">", startIndex) + 1);

                File newF = new File(getApplicationInfo().dataDir + "/shared_prefs/" + name);
                Log.d(
                        TAG,
                        "Restoring local file: " + name + " (size=" + fileContent.length() + ")");
                try (BufferedWriter bw = new BufferedWriter(new FileWriter(newF))) {
                    bw.write(fileContent);
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception while parsing single-file backup data", e);
            return false;
        }
        return true;
    }

    /** Show an error dialog with the specified title and message. */
    private void showErrorDialog(int titleResId, int messageResId) {
        new AlertDialog.Builder(this)
                .setTitle(titleResId)
                .setMessage(messageResId)
                .setPositiveButton(R.string.btn_ok, null)
                .setCancelable(false)
                .show();
    }
}
