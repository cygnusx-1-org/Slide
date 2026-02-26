package me.edgan.redditslide.ui.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.jakewharton.processphoenix.ProcessPhoenix;

import me.edgan.redditslide.Activities.BaseActivityAnim;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.StorageUtil;
import me.edgan.redditslide.util.MiscUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;

/**
 * Created by ccrama on 3/5/2015 and updated by edgan on 1/21/2025.
 *  Handles backing up and restoring app settings both locally via SAF and to Google Drive.
 */
public class SettingsBackup extends BaseActivityAnim {

    private static final String TAG = "SettingsBackup";

    // Request codes
    private static final int RC_SIGN_IN = 100;
    private static final int RC_AUTHORIZATION = 101;
    private static final int RC_OPEN_DOCUMENT = 102; // used for local restore via SAF
    private static final int RC_CREATE_DOCUMENT = 103; // used for local backup via SAF

    // Google sign-in client
    private GoogleSignInClient mGoogleSignInClient;

    // Google Drive service
    private Drive mDriveService;

    // Progress dialog
    private MaterialDialog progress;

    // For counting errors during tasks
    private int errors = 0;

    // Common single file name on Google Drive
    private static final String DRIVE_BACKUP_FILENAME = "shared_prefs_backup.txt";

    private HttpTransport HTTP_TRANSPORT;

    // Flags to track whether user wanted to do certain actions after sign-in
    private boolean backupRequestedAfterSignIn = false;
    private boolean restoreRequestedAfterSignIn = false;

    // We’ll store the final URI of the newly created local backup file so we can offer to "View" it
    private Uri localBackupFileUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HTTP_TRANSPORT = new NetHttpTransport();

        applyColorTheme();
        setContentView(R.layout.activity_settings_sync);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_backup, true, true);

        // Initialize Google sign-in
        initializeGoogleSignIn();

        // Setup UI elements and listeners
        setupUI();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check if already signed in
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && mDriveService == null) {
            Log.d(TAG, "Already signed in, initializing Drive service.");
            initializeDriveService(account);
        } else {
            Log.d(TAG, "Not signed in or service is already initialized.");
        }
    }

    /** Initialize Google sign-in options and client. */
    private void initializeGoogleSignIn() {
        Log.d(TAG, "Initializing Google sign-in options");
        GoogleSignInOptions gso =
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestScopes(
                                new Scope(DriveScopes.DRIVE_FILE),
                                new Scope(DriveScopes.DRIVE_APPDATA))
                        .requestEmail()
                        .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    /**
     * Initialize the Google Drive service with the signed-in account.
     *
     * @param account The GoogleSignInAccount obtained after sign-in.
     */
    private void initializeDriveService(GoogleSignInAccount account) {
        Log.d(TAG, "Initializing Drive service for account: " + account.getEmail());
        GoogleAccountCredential credential =
                GoogleAccountCredential.usingOAuth2(
                        this, Collections.singleton(DriveScopes.DRIVE_APPDATA));
        credential.setSelectedAccount(account.getAccount());

        try {
            mDriveService =
                    new Drive.Builder(HTTP_TRANSPORT, GsonFactory.getDefaultInstance(), credential)
                            .setApplicationName(getString(R.string.app_name))
                            .build();
            Log.d(TAG, "Drive service initialized successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Drive service", e);
            showErrorDialog(R.string.err_general, R.string.err_general);
        }
    }

    /** Setup UI elements and their click listeners (keeping the base UI). */
    private void setupUI() {
        // Backup to Google Drive
        findViewById(R.id.back).setOnClickListener(v -> handleBackup());
        // Restore from Google Drive
        findViewById(R.id.restore).setOnClickListener(v -> handleRestore());

        // SAF-based local backup
        findViewById(R.id.backfile).setOnClickListener(v -> showBackupToDirDialog());
        // SAF-based local restore
        findViewById(R.id.restorefile).setOnClickListener(v -> openRestoreFile());
    }

    /** Handle the Backup-to-Google-Drive button click */
    private void handleBackup() {
        Log.d(TAG, "handleBackup() called.");
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Log.d(TAG, "User not signed in, requesting sign in before backup.");
            backupRequestedAfterSignIn = true;
            requestSignIn();
            return;
        }

        if (mDriveService == null) {
            initializeDriveService(account);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.general_confirm)
                .setMessage(R.string.backup_confirm)
                .setPositiveButton(
                        R.string.btn_ok,
                        (dialog, whichButton) -> new BackupToDriveAsyncTask().execute())
                .setNegativeButton(R.string.btn_no, null)
                .setCancelable(false)
                .show();
    }

    /** Handle the Restore-from-Google-Drive button click */
    private void handleRestore() {
        Log.d(TAG, "handleRestore() called.");
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account == null) {
            Log.d(TAG, "User not signed in, requesting sign in before restore.");
            restoreRequestedAfterSignIn = true;
            requestSignIn();
            return;
        }

        if (mDriveService == null) {
            initializeDriveService(account);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.general_confirm)
                .setMessage(R.string.backup_restore_confirm)
                .setPositiveButton(
                        R.string.btn_ok,
                        (dialog, whichButton) -> new RestoreFromDriveAsyncTask().execute())
                .setNegativeButton(R.string.btn_no, null)
                .setCancelable(false)
                .show();
    }

    /** Show dialog to choose backup-to-directory options */
    private void showBackupToDirDialog() {
        Log.d(TAG, "showBackupToDirDialog() called.");
        new AlertDialog.Builder(this)
                .setTitle(R.string.backup_question)
                .setPositiveButton(R.string.btn_ok, (dialog, which) -> launchCreateBackupFile())
                .setNeutralButton(R.string.btn_cancel, null)
                .setCancelable(false)
                .show();
    }

    /** Launch SAF ACTION_CREATE_DOCUMENT to let the user choose where to save the backup. */
    private void launchCreateBackupFile() {
        String timeStamp = new SimpleDateFormat("-yyyy-MM-dd-HH-mm-ss").format(new Date());
        String fileName = "Slide" + timeStamp + ".txt";

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);

        // If a storage location is configured, use it as the initial directory
        Uri treeUri = StorageUtil.getStorageUri(this);
        if (treeUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri);
        }

        startActivityForResult(intent, RC_CREATE_DOCUMENT);
    }

    /**
     * Open a file picker to select a backup file for local restoration (SAF). This is the same
     * basic UI from base, but we are using ACTION_OPEN_DOCUMENT with RC_OPEN_DOCUMENT. The actual
     * reading is done in onActivityResult -> RestoreFromFileAsyncTask.
     */
    private void openRestoreFile() {
        Log.d(TAG, "openRestoreFile() called.");

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*"); // or "text/plain"
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        Uri treeUri = StorageUtil.getStorageUri(this);
        if (treeUri != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, treeUri);
        }

        startActivityForResult(
                Intent.createChooser(intent, getString(R.string.select_backup_file)),
                RC_OPEN_DOCUMENT);
    }

    /** Request Google sign-in if needed. */
    private void requestSignIn() {
        Log.d(TAG, "requestSignIn() - launching Google sign-in intent.");
        startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }

    /**
     * Merged onActivityResult to handle: - Google sign-in - Google authorization - Local file open
     * via SAF
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RC_SIGN_IN:
                handleSignInResult(data);
                break;
            case RC_AUTHORIZATION:
                if (resultCode == Activity.RESULT_OK) {
                    Log.d(TAG, "RC_AUTHORIZATION: user granted authorization. Retrying if needed.");
                    // Retry last operation if needed
                } else {
                    Log.w(TAG, "RC_AUTHORIZATION: user denied or canceled authorization.");
                }
                break;
            case RC_OPEN_DOCUMENT:
                // SAF file picker result for local restore
                handleFilePickerResult(resultCode, data);
                break;
            case RC_CREATE_DOCUMENT:
                // SAF create document result for local backup
                handleCreateDocumentResult(resultCode, data);
                break;
            default:
                // ...
                break;
        }
    }

    /** Handle the result of the Google sign-in intent */
    private void handleSignInResult(Intent data) {
        Log.d(TAG, "handleSignInResult() called.");
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        task.addOnCompleteListener(
                this,
                new OnCompleteListener<GoogleSignInAccount>() {
                    @Override
                    public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                        try {
                            GoogleSignInAccount account = task.getResult(Exception.class);
                            if (account != null) {
                                Log.d(TAG, "Sign-in successful, email: " + account.getEmail());
                                initializeDriveService(account);

                                // If we had requested a backup or restore after sign-in, proceed
                                // now
                                if (backupRequestedAfterSignIn) {
                                    backupRequestedAfterSignIn = false;
                                    new BackupToDriveAsyncTask().execute();
                                } else if (restoreRequestedAfterSignIn) {
                                    restoreRequestedAfterSignIn = false;
                                    new RestoreFromDriveAsyncTask().execute();
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Sign-in failed", e);
                            showErrorDialog(R.string.sign_in_failed, R.string.sign_in_failed_msg);
                        }
                    }
                });
    }

    /** SAF create document result for local backup. */
    private void handleCreateDocumentResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri fileUri = data.getData();
            Log.d(TAG, "Created backup file URI: " + fileUri);
            backupToFile(fileUri);
        } else {
            Log.w(TAG, "Backup file creation canceled or failed.");
        }
    }

    /** Handle the result from the local SAF file picker for restore. */
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

    /** Performs the actual local backup writing to the user-chosen file URI. */
    private void backupToFile(Uri fileUri) {
        progress =
                new MaterialDialog.Builder(SettingsBackup.this)
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
                try {
                    OutputStream out = getContentResolver().openOutputStream(fileUri);
                    if (out == null) {
                        errorMessage = "OutputStream was null for: " + fileUri;
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
                    showErrorDialog(R.string.err_general, R.string.err_general);
                    return;
                }
                // Show success dialog with a "View" button
                new AlertDialog.Builder(SettingsBackup.this)
                        .setTitle(R.string.backup_complete)
                        .setMessage(R.string.backup_saved_downloads)
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

    /** Async task to restore from a local SAF-chosen file (replacing old restoreFromFile code). */
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

                // Use the same parse logic from base version:
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
                // Show final restart dialog
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

    /** Asynchronous task to upload single-file backup to Google Drive's appDataFolder. */
    private class BackupToDriveAsyncTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "BackupToDriveAsyncTask: started");
            progress =
                    new MaterialDialog.Builder(SettingsBackup.this)
                            .title(R.string.backup_backing_up)
                            .content(R.string.misc_please_wait)
                            .cancelable(false)
                            .progress(true, 0)
                            .build();
            progress.show();
            errors = 0;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            // Build the entire backup as a single string in memory
            File prefsDir = new File(getApplicationInfo().dataDir, "shared_prefs");
            if (!prefsDir.exists() || !prefsDir.isDirectory()) {
                Log.w(TAG, "No shared_prefs directory found for Drive backup.");
                return false;
            }

            String[] list = prefsDir.list();
            if (list == null) {
                Log.w(TAG, "No preference files found to backup for Drive.");
                return false;
            }

            StringBuilder backupBuilder = new StringBuilder();
            backupBuilder.append("Slide_backupEND>"); // starting marker

            for (String s : list) {
                if (!s.contains("cache")
                        && !s.contains("ion-cookies")
                        && !s.contains("albums")
                        && !s.contains("STACKTRACE")
                        && !s.contains("com.google")) {

                    File fileToBackup = new File(prefsDir, s);
                    String content = readFileFully(fileToBackup);
                    if (content != null) {
                        backupBuilder.append("<START").append(s).append(">");
                        backupBuilder.append(content);
                        backupBuilder.append("END>");
                        Log.d(TAG, "Adding file to single backup: " + s);
                    } else {
                        Log.w(TAG, "Content was null for local file: " + s);
                    }
                } else {
                    Log.d(TAG, "Skipping local file: " + s);
                }
            }

            // Convert entire backup string to bytes for upload
            byte[] backupData = backupBuilder.toString().getBytes();

            // Upload or update on Drive
            try {
                String fileId = findDriveBackupFileId();
                if (fileId == null) {
                    // Create new file
                    com.google.api.services.drive.model.File fileMetadata =
                            new com.google.api.services.drive.model.File();
                    fileMetadata.setName(DRIVE_BACKUP_FILENAME);
                    fileMetadata.setParents(Collections.singletonList("appDataFolder"));

                    ByteArrayContent contentStream = new ByteArrayContent("text/plain", backupData);
                    mDriveService
                            .files()
                            .create(fileMetadata, contentStream)
                            .setFields("id")
                            .execute();
                    Log.d(TAG, "Created new backup file on Drive: " + DRIVE_BACKUP_FILENAME);
                } else {
                    // Update existing file
                    ByteArrayContent contentStream = new ByteArrayContent("text/plain", backupData);
                    mDriveService.files().update(fileId, null, contentStream).execute();
                    Log.d(TAG, "Updated existing backup file on Drive: " + DRIVE_BACKUP_FILENAME);
                }
            } catch (UserRecoverableAuthIOException urae) {
                Log.w(
                        TAG,
                        "UserRecoverableAuthIOException while uploading single backup. Requesting"
                                + " auth again.");
                startActivityForResult(urae.getIntent(), RC_AUTHORIZATION);
                return false;
            } catch (IOException e) {
                Log.e(TAG, "Error uploading single-file backup to Drive", e);
                errors++;
                return false;
            }
            return (errors == 0);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (progress != null) {
                progress.dismiss();
            }
            if (success) {
                new AlertDialog.Builder(SettingsBackup.this)
                        .setTitle(R.string.backup_success)
                        .setPositiveButton(R.string.btn_close, (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
            } else {
                showErrorDialog(R.string.err_general, R.string.backup_failed_msg);
            }
        }

        private String findDriveBackupFileId() throws IOException {
            FileList result =
                    mDriveService
                            .files()
                            .list()
                            .setSpaces("appDataFolder")
                            .setFields("files(id, name)")
                            .execute();
            if (result.getFiles() != null && !result.getFiles().isEmpty()) {
                for (com.google.api.services.drive.model.File file : result.getFiles()) {
                    if (DRIVE_BACKUP_FILENAME.equals(file.getName())) {
                        return file.getId();
                    }
                }
            }
            return null;
        }
    }

    /**
     * Asynchronous task to download and restore the single "shared_prefs_backup.txt" from Drive.
     */
    private class RestoreFromDriveAsyncTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "RestoreFromDriveAsyncTask: started");
            progress =
                    new MaterialDialog.Builder(SettingsBackup.this)
                            .title(R.string.backup_restoring)
                            .content(R.string.misc_please_wait)
                            .cancelable(false)
                            .progress(true, 0)
                            .build();
            progress.show();
            errors = 0;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                // Search for our single backup file in Drive
                String fileId = null;
                FileList result =
                        mDriveService
                                .files()
                                .list()
                                .setSpaces("appDataFolder")
                                .setFields("files(id, name)")
                                .execute();
                if (result.getFiles() != null && !result.getFiles().isEmpty()) {
                    for (com.google.api.services.drive.model.File file : result.getFiles()) {
                        if (DRIVE_BACKUP_FILENAME.equals(file.getName())) {
                            fileId = file.getId();
                            break;
                        }
                    }
                }

                if (fileId == null) {
                    Log.w(
                            TAG,
                            "No single backup file named "
                                    + DRIVE_BACKUP_FILENAME
                                    + " found in Drive.");
                    return false;
                }

                // Download the file content
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                mDriveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
                String data = outputStream.toString();
                Log.d(TAG, "Downloaded " + data.length() + " bytes from: " + DRIVE_BACKUP_FILENAME);

                // Parse the single-file backup
                return restoreSharedPrefsFromString(data);

            } catch (UserRecoverableAuthIOException urae) {
                Log.w(
                        TAG,
                        "UserRecoverableAuthIOException while downloading single backup. Requesting"
                                + " auth again.");
                startActivityForResult(urae.getIntent(), RC_AUTHORIZATION);
            } catch (IOException e) {
                Log.e(TAG, "Error downloading single-file backup from Drive", e);
                errors++;
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (progress != null) {
                progress.dismiss();
            }

            if (!success) {
                showErrorDialog(R.string.err_general, R.string.backup_restore_failed_msg);
                return;
            }

            new AlertDialog.Builder(SettingsBackup.this)
                    .setTitle(R.string.backup_restore_settings)
                    .setMessage(R.string.backup_restarting)
                    .setOnDismissListener(
                            dialog -> {
                                Log.d(
                                        TAG,
                                        "ProcessPhoenix.triggerRebirth() called from onDismiss"
                                                + " (Drive restore).");
                                ProcessPhoenix.triggerRebirth(SettingsBackup.this);
                            })
                    .setPositiveButton(
                            R.string.btn_ok,
                            (dialog, which) -> {
                                Log.d(
                                        TAG,
                                        "ProcessPhoenix.triggerRebirth() called from OK button"
                                                + " (Drive restore).");
                                ProcessPhoenix.triggerRebirth(SettingsBackup.this);
                            })
                    .setCancelable(false)
                    .show();
        }
    }

    /**
     * Restore the shared_prefs contents from a single large string that uses the local-file
     * markers.
     */
    private boolean restoreSharedPrefsFromString(String data) {
        try {
            if (!data.contains("Slide_backupEND>")) {
                Log.w(
                        TAG,
                        "Backup file did not contain 'Slide_backupEND>' marker, likely invalid.");
                return false;
            }
            // Example data is:
            // Slide_backupEND><STARTsomefile.xml>filecontentEND><STARTotherfile.xml>filecontentEND>
            // ...
            String[] files = data.split("END>");
            // files[0] will contain "Slide_backupEND>", skip it
            for (int i = 1; i < files.length; i++) {
                String innerFile = files[i];
                int startIndex = innerFile.indexOf("<START");
                if (startIndex == -1) {
                    Log.w(TAG, "Skipping malformed file block: " + innerFile);
                    continue;
                }
                // substring from 6 chars after <START to the next '>'
                String name =
                        innerFile.substring(startIndex + 6, innerFile.indexOf(">", startIndex));
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

    /** Read the entire content of a file into a String. */
    private String readFileFully(File file) {
        if (!file.exists()) {
            Log.w(TAG, "readFileFully: file does not exist - " + file.getAbsolutePath());
            return null;
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
            Log.d(TAG, "readFileFully: " + file.getName() + " (size=" + builder.length() + ")");
            return builder.toString();
        } catch (IOException e) {
            Log.e(TAG, "Error reading file: " + file.getName(), e);
            return null;
        }
    }

    /** Show an error dialog with the specified title and message. */
    private void showErrorDialog(int titleResId, int messageResId) {
        Log.d(TAG, "showErrorDialog: title=" + titleResId + ", message=" + messageResId);
        new AlertDialog.Builder(this)
                .setTitle(titleResId)
                .setMessage(messageResId)
                .setPositiveButton(R.string.btn_ok, null)
                .setCancelable(false)
                .show();
    }
}
