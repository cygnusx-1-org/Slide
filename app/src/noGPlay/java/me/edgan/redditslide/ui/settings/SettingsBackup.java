package me.edgan.redditslide.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.snackbar.Snackbar;
import com.jakewharton.processphoenix.ProcessPhoenix;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.BackupArchive;
import me.edgan.redditslide.util.BackupPasswordPrompt;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.MaterialProgressDialog;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.StorageUtil;

/**
 * Handles local (SAF-based) backup and restore of app settings, and stripped of all Google Drive
 * code.
 */
public class SettingsBackup extends BaseActivityAnim {

    private static final String TAG = "SettingsBackup";

    // Request codes for SAF-based actions
    private static final int RC_OPEN_DOCUMENT = 102;
    private static final int RC_CREATE_DOCUMENT = 103;

    // Backups are encrypted zips; see BackupArchive.
    private static final String BACKUP_MIME_TYPE = "application/zip";

    // Progress dialog
    private MaterialProgressDialog progress;

    // We’ll store the final URI of the newly created local backup file so we can offer to "View" it.
    private Uri localBackupFileUri = null;

    // Password for a local backup, held only while the SAF file picker is up. Wiped as soon as the
    // picker returns, and by onDestroy() if it never does.
    private @Nullable char[] mPendingFileBackupPassword;

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

    @Override
    public void onDestroy() {
        if (mPendingFileBackupPassword != null) {
            Arrays.fill(mPendingFileBackupPassword, '\0');
            mPendingFileBackupPassword = null;
        }
        super.onDestroy();
    }

    /** Initialize button click listeners for local backup/restore only. */
    private void setupUI() {
        // Create a local backup with SAF (user chooses directory)
        findViewById(R.id.backfile).setOnClickListener(v -> showBackupToDirDialog());

        // Restore from a local backup file (user chooses file)
        findViewById(R.id.restorefile).setOnClickListener(v -> openRestoreFile());
    }

    /** Ask user for confirmation, then for the password, then where to put the backup. */
    private void showBackupToDirDialog() {
        DialogUtil.showWithCardBackground(new AlertDialog.Builder(this)
                .setTitle(R.string.backup_question)
                .setPositiveButton(R.string.btn_ok, (dialog, which) -> askPasswordThenPickFile())
                .setNeutralButton(R.string.btn_cancel, null)
                .setCancelable(false)
                );
    }

    /**
     * The password is asked for before the file picker, not after. SAF creates the document as
     * soon as the user names it, so a prompt that came afterwards would leave an empty file behind
     * every time it was dismissed.
     */
    private void askPasswordThenPickFile() {
        BackupPasswordPrompt.forNewBackup(
                this,
                password -> {
                    mPendingFileBackupPassword = password;
                    launchCreateBackupFile();
                });
    }

    /** Launch SAF ACTION_CREATE_DOCUMENT to let the user choose where to save the backup. */
    private void launchCreateBackupFile() {
        String timeStamp = new SimpleDateFormat("-yyyy-MM-dd-HH-mm-ss").format(new Date());
        String fileName = "Slide" + timeStamp + ".zip";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(BACKUP_MIME_TYPE);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        // If a storage location is configured, use it as the initial directory
        Uri treeUri = StorageUtil.getStorageUri(this);
        if (treeUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri);
        }

        startActivityForResult(intent, RC_CREATE_DOCUMENT);
    }

    /**
     * Removes a document SAF has created that no backup was written into: a write that failed part
     * way, or a picker result that came back with the password gone. Without this the user is left
     * with an empty or truncated file sitting where a real backup should be.
     */
    private void discardAbandonedBackupFile(Uri fileUri) {
        // Off the main thread: the document can belong to a cloud provider, where deleting it is a
        // network round trip.
        AsyncTask.execute(
                () -> {
                    try {
                        DocumentsContract.deleteDocument(getContentResolver(), fileUri);
                        Log.d(TAG, "Removed the abandoned backup file.");
                    } catch (Exception e) {
                        Log.w(TAG, "Could not remove the abandoned backup file", e);
                    }
                });
    }

    /** Performs the actual local backup writing to the user-chosen file URI. */
    private void writeBackupToFile(Uri fileUri, char[] password) {
        progress =
                new MaterialProgressDialog.Builder(SettingsBackup.this)
                        .title(R.string.backup_backing_up)
                        .content(R.string.misc_please_wait)
                        .cancelable(false)
                        .progress(true, 0)
                        .build();
        progress.show();

        localBackupFileUri = fileUri;

        new AsyncTask<Void, Void, Boolean>() {
            private String errorMessage = null;

            @Override
            protected Boolean doInBackground(Void... params) {
                try (OutputStream out = getContentResolver().openOutputStream(fileUri)) {
                    if (out == null) {
                        errorMessage = "OutputStream was null for: " + fileUri;
                        return false;
                    }
                    BackupArchive.write(SettingsBackup.this, password, out);
                    return true;
                } catch (IOException e) {
                    Log.e(TAG, "Error creating or writing backup file", e);
                    errorMessage = e.getMessage();
                    return false;
                } finally {
                    Arrays.fill(password, '\0');
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (progress != null) {
                    progress.dismiss();
                }
                if (!success) {
                    Log.w(TAG, "Local backup failed: " + errorMessage);
                    // SAF created the document before the write began, so a failed write leaves a
                    // truncated file behind that still looks like a backup. Drop it, as a
                    // cancelled prompt does.
                    discardAbandonedBackupFile(fileUri);
                }
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (!success) {
                    showErrorDialog(R.string.err_general, R.string.err_general);
                    return;
                }

                // Show success dialog with a "View" button
                DialogUtil.showWithCardBackground(new AlertDialog.Builder(SettingsBackup.this)
                        .setTitle(R.string.backup_complete)
                        .setMessage(R.string.backup_saved_downloads)
                        .setPositiveButton(
                                R.string.btn_view,
                                (dialog, which) -> {
                                    if (localBackupFileUri != null) {
                                        // Attempt to open with a viewer
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        intent.setDataAndType(localBackupFileUri, BACKUP_MIME_TYPE);
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
                        .setCancelable(false));
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
        } else if (requestCode == RC_CREATE_DOCUMENT) {
            handleCreateDocumentResult(resultCode, data);
        }
    }

    /** SAF create document result for local backup. */
    private void handleCreateDocumentResult(int resultCode, Intent data) {
        char[] password = mPendingFileBackupPassword;
        mPendingFileBackupPassword = null;
        if (password == null) {
            Log.w(TAG, "Document created with no backup password pending.");
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                // The screen was rebuilt while the picker was up -- the process being killed takes
                // the password with it. SAF has already created the document by now, so remove it
                // rather than leave an empty file where a backup should be, and say so instead of
                // returning to the screen as though nothing had been asked for.
                discardAbandonedBackupFile(data.getData());
                showErrorDialog(R.string.err_general, R.string.backup_failed_msg);
            }
            return;
        }

        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri fileUri = data.getData();
            Log.d(TAG, "Created backup file URI: " + fileUri);
            writeBackupToFile(fileUri, password);
        } else {
            Log.w(TAG, "Backup file creation canceled or failed.");
            Arrays.fill(password, '\0');
        }
    }

    /** SAF file picker result for local restore. */
    private void handleFilePickerResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri fileUri = data.getData();
            Log.d(TAG, "Selected local backup file URI: " + fileUri);
            new ReadBackupFileAsyncTask(fileUri).execute();
        } else {
            Log.w(TAG, "No file chosen or result not OK.");
            showErrorDialog(R.string.err_file_not_found, R.string.err_file_not_found_msg);
        }
    }

    /**
     * Reads the chosen file into memory so its format can be told apart before anything is asked of
     * the user: an encrypted zip needs a password, a legacy plain-text backup does not.
     */
    private class ReadBackupFileAsyncTask extends AsyncTask<Void, Void, byte[]> {
        private final Uri fileUri;

        ReadBackupFileAsyncTask(Uri fileUri) {
            this.fileUri = fileUri;
        }

        @Override
        protected byte[] doInBackground(Void... voids) {
            Log.d(TAG, "Reading backup file: " + fileUri);
            try (InputStream is = getContentResolver().openInputStream(fileUri)) {
                if (is == null) {
                    Log.e(TAG, "Could not open InputStream for fileUri: " + fileUri);
                    return null;
                }
                return BackupArchive.readFully(is);
            } catch (Exception e) {
                Log.e(TAG, "Exception while reading fileUri: " + fileUri, e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(byte[] backupData) {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (backupData == null || backupData.length == 0) {
                showErrorDialog(R.string.err_not_valid_backup, R.string.err_not_valid_backup_msg);
                return;
            }
            Log.d(TAG, "Read " + backupData.length + " bytes from backup file.");

            if (BackupArchive.isZip(backupData)) {
                promptAndRestore(backupData, false);
            } else {
                // A backup written before the encrypted format; it carries no password.
                new RestoreBackupAsyncTask(backupData, null).execute();
            }
        }
    }

    /** Asks for the archive password, re-asking as long as the one given is rejected. */
    private void promptAndRestore(byte[] backupData, boolean retry) {
        BackupPasswordPrompt.forRestore(
                this, retry, password -> new RestoreBackupAsyncTask(backupData, password).execute());
    }

    /** Applies a backup that has already been read into memory. */
    private class RestoreBackupAsyncTask extends AsyncTask<Void, Void, Boolean> {
        private final byte[] backupData;
        private final @Nullable char[] password;
        private boolean wrongPassword = false;

        RestoreBackupAsyncTask(byte[] backupData, @Nullable char[] password) {
            this.backupData = backupData;
            this.password = password;
        }

        @Override
        protected void onPreExecute() {
            progress =
                    new MaterialProgressDialog.Builder(SettingsBackup.this)
                            .title(R.string.backup_restoring)
                            .content(R.string.misc_please_wait)
                            .cancelable(false)
                            .progress(true, 1)
                            .build();
            progress.show();
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                if (password == null) {
                    return BackupArchive.restoreLegacyText(
                            SettingsBackup.this, new String(backupData, StandardCharsets.UTF_8));
                }
                BackupArchive.restore(SettingsBackup.this, backupData, password);
                return true;
            } catch (BackupArchive.WrongPasswordException e) {
                Log.w(TAG, "Backup did not decrypt with the password given.");
                wrongPassword = true;
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Exception while restoring backup", e);
                return false;
            } finally {
                if (password != null) {
                    Arrays.fill(password, '\0');
                }
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (progress != null) {
                progress.dismiss();
            }
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (wrongPassword) {
                promptAndRestore(backupData, true);
                return;
            }
            if (success) {
                DialogUtil.showWithCardBackground(new AlertDialog.Builder(SettingsBackup.this)
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
                        );
            } else {
                Log.w(TAG, "Restore from local file failed or invalid file.");
                showErrorDialog(R.string.err_not_valid_backup, R.string.err_not_valid_backup_msg);
            }
        }
    }

    /** Show an error dialog with the specified title and message. */
    private void showErrorDialog(int titleResId, int messageResId) {
        DialogUtil.showWithCardBackground(new AlertDialog.Builder(this)
                .setTitle(titleResId)
                .setMessage(messageResId)
                .setPositiveButton(R.string.btn_ok, null)
                .setCancelable(false)
                );
    }
}
