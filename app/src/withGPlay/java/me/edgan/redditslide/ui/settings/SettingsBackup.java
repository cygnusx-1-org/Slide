package me.edgan.redditslide.ui.settings;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.ClearTokenRequest;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.snackbar.Snackbar;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.jakewharton.processphoenix.ProcessPhoenix;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
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
 * Created by ccrama on 3/5/2015 and updated by edgan on 1/21/2025.
 *  Handles backing up and restoring app settings both locally via SAF and to Google Drive.
 */
public class SettingsBackup extends BaseActivityAnim {

    private static final String TAG = "SettingsBackup";

    // Request codes
    private static final int RC_AUTHORIZATION = 101;
    private static final int RC_OPEN_DOCUMENT = 102; // used for local restore via SAF
    private static final int RC_CREATE_DOCUMENT = 103; // used for local backup via SAF

    // Drive operations that can be deferred until authorization completes
    private static final int OP_NONE = 0;
    private static final int OP_BACKUP = 1;
    private static final int OP_RESTORE = 2;

    // Google Drive scope authorization. Slide never signs a user in with Google -- Reddit OAuth
    // does that -- it only asks for access to its own private Drive folder.
    private AuthorizationClient mAuthorizationClient;
    private AuthorizationRequest mAuthorizationRequest;

    // Access token backing mDriveService, kept so it can be invalidated when Drive rejects it
    private String mAccessToken;

    // Google Drive service
    private Drive mDriveService;

    // Progress dialog
    private MaterialProgressDialog progress;

    // For counting errors during tasks
    private int errors = 0;

    // Common single file name on Google Drive
    private static final String DRIVE_BACKUP_FILENAME = "shared_prefs_backup.zip";

    // What Drive backups were called before they were encrypted zips. Still read on restore, and
    // deleted once an encrypted one replaces it -- it holds every account's refresh token in the
    // clear, so leaving it behind would undo the point of encrypting.
    private static final String LEGACY_DRIVE_BACKUP_FILENAME = "shared_prefs_backup.txt";

    // Backups are encrypted zips; see BackupArchive.
    private static final String BACKUP_MIME_TYPE = "application/zip";

    private HttpTransport HTTP_TRANSPORT;

    // The Drive operation waiting on authorization, one of the OP_ constants
    private int pendingOperation = OP_NONE;

    // Guards against looping when a freshly issued access token is also rejected
    private boolean reauthorizeRetried = false;

    // We’ll store the final URI of the newly created local backup file so we can offer to "View" it
    private Uri localBackupFileUri = null;

    // Password for a Drive backup, held only across a reauthorize-and-retry so the user is not
    // asked for it twice. Wiped by clearPendingBackupPassword().
    private @Nullable char[] mPendingBackupPassword;

    // Password for a local backup, held only while the SAF file picker is up. Wiped as soon as the
    // picker returns, and by onDestroy() if it never does.
    private @Nullable char[] mPendingFileBackupPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HTTP_TRANSPORT = new NetHttpTransport();

        applyColorTheme();
        setContentView(R.layout.activity_settings_sync);
        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        setupAppBar(R.id.toolbar, R.string.settings_title_backup, true, true);

        // Initialize Google Drive scope authorization
        initializeAuthorization();

        // Setup UI elements and listeners
        setupUI();
    }

    @Override
    public void onDestroy() {
        clearPendingBackupPassword();
        if (mPendingFileBackupPassword != null) {
            Arrays.fill(mPendingFileBackupPassword, '\0');
            mPendingFileBackupPassword = null;
        }
        super.onDestroy();
    }

    /**
     * Build the Drive authorization client and the scope request it runs. Only DRIVE_APPDATA is
     * requested: every Drive call below is scoped to appDataFolder, so the broader DRIVE_FILE
     * consent would go unused.
     */
    private void initializeAuthorization() {
        Log.d(TAG, "Initializing Google Drive authorization client");
        mAuthorizationClient = Identity.getAuthorizationClient(this);
        mAuthorizationRequest =
                AuthorizationRequest.builder()
                        .setRequestedScopes(
                                Collections.singletonList(new Scope(DriveScopes.DRIVE_APPDATA)))
                        .build();
    }

    /**
     * Initialize the Google Drive service with an authorized access token.
     *
     * @param accessToken The bearer token obtained from the AuthorizationResult.
     */
    private void initializeDriveService(String accessToken) {
        try {
            mDriveService =
                    new Drive.Builder(
                                    HTTP_TRANSPORT,
                                    GsonFactory.getDefaultInstance(),
                                    request ->
                                            request.getHeaders()
                                                    .setAuthorization("Bearer " + accessToken))
                            .setApplicationName(getString(R.string.app_name))
                            .build();
            Log.d(TAG, "Drive service initialized successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Drive service", e);
            mDriveService = null;
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
        showThemedDialog(
                new AlertDialog.Builder(this)
                        .setTitle(R.string.general_confirm)
                        .setMessage(R.string.backup_confirm)
                        .setPositiveButton(
                                R.string.btn_ok,
                                (dialog, whichButton) -> startDriveOperation(OP_BACKUP))
                        .setNegativeButton(R.string.btn_no, null)
                        .setCancelable(false));
    }

    /** Handle the Restore-from-Google-Drive button click */
    private void handleRestore() {
        Log.d(TAG, "handleRestore() called.");
        showThemedDialog(
                new AlertDialog.Builder(this)
                        .setTitle(R.string.general_confirm)
                        .setMessage(R.string.backup_restore_confirm)
                        .setPositiveButton(
                                R.string.btn_ok,
                                (dialog, whichButton) -> startDriveOperation(OP_RESTORE))
                        .setNegativeButton(R.string.btn_no, null)
                        .setCancelable(false));
    }

    /** Show dialog to choose backup-to-directory options */
    private void showBackupToDirDialog() {
        Log.d(TAG, "showBackupToDirDialog() called.");
        showThemedDialog(
                new AlertDialog.Builder(this)
                        .setTitle(R.string.backup_question)
                        .setPositiveButton(
                                R.string.btn_ok, (dialog, which) -> askPasswordThenPickFile())
                        .setNeutralButton(R.string.btn_cancel, null)
                        .setCancelable(false));
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

    /**
     * Remember the requested Drive operation and authorize before running it. There is no
     * "last authorized account" to query, so authorize() is both the "do we already have access?"
     * check and the consent launch: an existing grant resolves silently with a token, otherwise the
     * result carries a PendingIntent to show the consent sheet.
     */
    private void startDriveOperation(int operation) {
        pendingOperation = operation;
        reauthorizeRetried = false;
        requestAuthorization();
    }

    /** Ask for the Drive scope, resuming at onAuthorized() once a token is available. */
    private void requestAuthorization() {
        Log.d(TAG, "requestAuthorization() - authorizing Google Drive scope.");
        mAuthorizationClient
                .authorize(mAuthorizationRequest)
                .addOnSuccessListener(
                        this,
                        result -> {
                            if (result.hasResolution()) {
                                launchAuthorizationConsent(result.getPendingIntent());
                            } else {
                                Log.d(TAG, "Authorization resolved silently.");
                                onAuthorized(result);
                            }
                        })
                .addOnFailureListener(
                        this,
                        e -> {
                            Log.e(TAG, "Authorization request failed", e);
                            failAuthorization();
                        });
    }

    /** Show the consent sheet; its outcome arrives in onActivityResult under RC_AUTHORIZATION. */
    private void launchAuthorizationConsent(PendingIntent pendingIntent) {
        if (pendingIntent == null) {
            Log.e(TAG, "Authorization needs resolution but supplied no PendingIntent.");
            failAuthorization();
            return;
        }

        try {
            Log.d(TAG, "Launching Google Drive authorization consent.");
            startIntentSenderForResult(
                    pendingIntent.getIntentSender(), RC_AUTHORIZATION, null, 0, 0, 0, null);
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Could not launch authorization consent", e);
            failAuthorization();
        }
    }

    /**
     * The single continuation point once a Drive access token is in hand, whether it came back
     * silently or from the consent sheet. Runs whatever operation was waiting on it.
     */
    private void onAuthorized(AuthorizationResult result) {
        String accessToken = result.getAccessToken();
        if (accessToken == null) {
            Log.e(TAG, "Authorization succeeded but returned no access token.");
            failAuthorization();
            return;
        }

        Log.d(TAG, "Authorized for scopes: " + result.getGrantedScopes());
        mAccessToken = accessToken;
        initializeDriveService(accessToken);
        if (mDriveService == null) {
            failAuthorization();
            return;
        }

        int operation = pendingOperation;
        pendingOperation = OP_NONE;

        switch (operation) {
            case OP_BACKUP:
                // A retry after a rejected token already has the password; only ask the first time.
                if (mPendingBackupPassword == null) {
                    BackupPasswordPrompt.forNewBackup(
                            this,
                            password -> {
                                mPendingBackupPassword = password;
                                new BackupToDriveAsyncTask(password).execute();
                            });
                } else {
                    new BackupToDriveAsyncTask(mPendingBackupPassword).execute();
                }
                break;
            case OP_RESTORE:
                new DownloadDriveBackupAsyncTask().execute();
                break;
            default:
                Log.d(TAG, "Authorized with no operation pending.");
                break;
        }
    }

    /** Drop any deferred operation and tell the user authorization did not complete. */
    private void failAuthorization() {
        pendingOperation = OP_NONE;
        clearPendingBackupPassword();
        showErrorDialog(
                R.string.drive_authorization_failed, R.string.drive_authorization_failed_msg);
    }

    /**
     * Discard the rejected access token and authorize again so the operation can run once more.
     * Access tokens last about an hour, so a screen left open can outlive one.
     *
     * @return true if a retry was started, false if a retry already happened.
     */
    private boolean reauthorizeAndRetry(int operation) {
        if (reauthorizeRetried) {
            Log.w(TAG, "Drive rejected the access token again after reauthorizing; giving up.");
            return false;
        }

        Log.d(TAG, "Drive rejected the access token; reauthorizing and retrying.");
        reauthorizeRetried = true;
        pendingOperation = operation;

        String staleToken = mAccessToken;
        mAccessToken = null;
        if (staleToken == null) {
            requestAuthorization();
            return true;
        }

        // Without clearing it, authorize() can hand back the same cached, expired token.
        mAuthorizationClient
                .clearToken(ClearTokenRequest.builder().setToken(staleToken).build())
                .addOnCompleteListener(this, task -> requestAuthorization());
        return true;
    }

    /**
     * Merged onActivityResult to handle: - Google Drive authorization - Local file open via SAF
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RC_AUTHORIZATION:
                handleAuthorizationResult(resultCode, data);
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

    /** Handle the consent-sheet result and resume the operation that was waiting on it. */
    private void handleAuthorizationResult(int resultCode, Intent data) {
        Log.d(TAG, "handleAuthorizationResult() called, resultCode=" + resultCode);
        if (data == null) {
            Log.w(TAG, "RC_AUTHORIZATION: no result data, treating as canceled.");
            denyAuthorization();
            return;
        }

        // Parse the result even when resultCode is not RESULT_OK. GMS reports a failed
        // authorization as a canceled resolution, so the status carried by the ApiException is the
        // only thing that separates a user backing out from a configuration failure.
        try {
            onAuthorized(mAuthorizationClient.getAuthorizationResultFromIntent(data));
        } catch (ApiException e) {
            Log.e(
                    TAG,
                    "Authorization failed with status "
                            + e.getStatusCode()
                            + " ("
                            + CommonStatusCodes.getStatusCodeString(e.getStatusCode())
                            + "): "
                            + e.getStatus(),
                    e);
            if (e.getStatusCode() == CommonStatusCodes.CANCELED) {
                denyAuthorization();
            } else {
                failAuthorization();
            }
        }
    }

    /** The user backed out of the consent flow rather than granting access. */
    private void denyAuthorization() {
        pendingOperation = OP_NONE;
        clearPendingBackupPassword();
        showErrorDialog(
                R.string.drive_authorization_denied, R.string.drive_authorization_denied_msg);
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

    /** Handle the result from the local SAF file picker for restore. */
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
                showThemedDialog(
                        new AlertDialog.Builder(SettingsBackup.this)
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
                this,
                retry,
                password -> new RestoreBackupAsyncTask(backupData, password).execute());
    }

    /** Applies a backup that has already been read into memory, from a file or from Drive. */
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
                // Show final restart dialog
                showThemedDialog(
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
                        .setCancelable(false));
            } else {
                Log.w(TAG, "Restore from local file failed or invalid file.");
                showErrorDialog(R.string.err_not_valid_backup, R.string.err_not_valid_backup_msg);
            }
        }
    }

    /** Asynchronous task to upload single-file backup to Google Drive's appDataFolder. */
    private class BackupToDriveAsyncTask extends AsyncTask<Void, Void, Boolean> {

        // Set when Drive rejected the access token, so onPostExecute can reauthorize
        private boolean tokenRejected = false;

        // A copy of mPendingBackupPassword, never the same array. That field is wiped whenever the
        // screen goes away or an authorization fails, and zip4j derives a fresh AES key from the
        // live array for every entry it writes -- so sharing it would let a wipe land mid-write and
        // encrypt the remaining entries under a different key, producing an archive that reports
        // "wrong password" on restore no matter what the user types.
        private final char[] password;

        BackupToDriveAsyncTask(char[] password) {
            this.password = password.clone();
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "BackupToDriveAsyncTask: started");
            progress =
                    new MaterialProgressDialog.Builder(SettingsBackup.this)
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
            byte[] backupData;
            try {
                backupData = BackupArchive.write(SettingsBackup.this, password);
            } catch (IOException e) {
                Log.e(TAG, "Error building the backup archive for Drive", e);
                errors++;
                return false;
            } finally {
                // The archive is built; the upload below does not need the password again.
                Arrays.fill(password, '\0');
            }

            // Upload or update on Drive
            try {
                String fileId = findDriveBackupFileId(DRIVE_BACKUP_FILENAME);
                ByteArrayContent contentStream =
                        new ByteArrayContent(BACKUP_MIME_TYPE, backupData);
                if (fileId == null) {
                    // Create new file
                    com.google.api.services.drive.model.File fileMetadata =
                            new com.google.api.services.drive.model.File();
                    fileMetadata.setName(DRIVE_BACKUP_FILENAME);
                    fileMetadata.setParents(Collections.singletonList("appDataFolder"));

                    mDriveService
                            .files()
                            .create(fileMetadata, contentStream)
                            .setFields("id")
                            .execute();
                    Log.d(TAG, "Created new backup file on Drive: " + DRIVE_BACKUP_FILENAME);
                } else {
                    // Update existing file
                    mDriveService.files().update(fileId, null, contentStream).execute();
                    Log.d(TAG, "Updated existing backup file on Drive: " + DRIVE_BACKUP_FILENAME);
                }
                deleteLegacyDriveBackup();
            } catch (HttpResponseException e) {
                if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_UNAUTHORIZED) {
                    Log.w(TAG, "Drive rejected the access token while uploading the backup.");
                    tokenRejected = true;
                    return false;
                }
                Log.e(TAG, "Error uploading single-file backup to Drive", e);
                errors++;
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
            // The retry re-runs this task with the same password, so keep it until then.
            if (tokenRejected && reauthorizeAndRetry(OP_BACKUP)) {
                return;
            }
            clearPendingBackupPassword();
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (success) {
                showThemedDialog(
                        new AlertDialog.Builder(SettingsBackup.this)
                                .setTitle(R.string.backup_success)
                                .setPositiveButton(R.string.btn_close, (dialog, which) -> finish())
                                .setCancelable(false));
            } else {
                showErrorDialog(R.string.err_general, R.string.backup_failed_msg);
            }
        }
    }

    /** @return the id of the named file in Slide's private Drive folder, or null if it is absent. */
    private String findDriveBackupFileId(String fileName) throws IOException {
        FileList result =
                mDriveService
                        .files()
                        .list()
                        .setSpaces("appDataFolder")
                        .setFields("files(id, name)")
                        .execute();
        if (result.getFiles() != null && !result.getFiles().isEmpty()) {
            for (com.google.api.services.drive.model.File file : result.getFiles()) {
                if (fileName.equals(file.getName())) {
                    return file.getId();
                }
            }
        }
        return null;
    }

    /**
     * Removes the pre-encryption Drive backup once an encrypted one has taken its place. It holds
     * every account's refresh token in the clear, so leaving it there would keep the exposure that
     * encrypting the backup is meant to close.
     */
    private void deleteLegacyDriveBackup() {
        try {
            String legacyId = findDriveBackupFileId(LEGACY_DRIVE_BACKUP_FILENAME);
            if (legacyId != null) {
                mDriveService.files().delete(legacyId).execute();
                Log.d(TAG, "Deleted the pre-encryption Drive backup.");
            }
        } catch (IOException e) {
            // Not fatal: the encrypted backup is already uploaded.
            Log.w(TAG, "Could not delete the pre-encryption Drive backup", e);
        }
    }

    /** Wipes the password retained across a Drive-backup reauthorization retry. */
    private void clearPendingBackupPassword() {
        if (mPendingBackupPassword != null) {
            Arrays.fill(mPendingBackupPassword, '\0');
            mPendingBackupPassword = null;
        }
    }

    /**
     * Asynchronous task to download the single backup file from Drive. Restoring it is left to
     * {@link RestoreBackupAsyncTask}, which is shared with the local-file path, because the format
     * has to be known -- and a password possibly asked for -- before anything can be applied.
     */
    private class DownloadDriveBackupAsyncTask extends AsyncTask<Void, Void, byte[]> {

        // Set when Drive rejected the access token, so onPostExecute can reauthorize
        private boolean tokenRejected = false;

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "DownloadDriveBackupAsyncTask: started");
            progress =
                    new MaterialProgressDialog.Builder(SettingsBackup.this)
                            .title(R.string.backup_restoring)
                            .content(R.string.misc_please_wait)
                            .cancelable(false)
                            .progress(true, 0)
                            .build();
            progress.show();
            errors = 0;
        }

        @Override
        protected byte[] doInBackground(Void... voids) {
            try {
                // Prefer the encrypted backup, falling back to one written before the format
                // changed so an old Drive backup still restores.
                String fileId = findDriveBackupFileId(DRIVE_BACKUP_FILENAME);
                String fileName = DRIVE_BACKUP_FILENAME;
                if (fileId == null) {
                    fileId = findDriveBackupFileId(LEGACY_DRIVE_BACKUP_FILENAME);
                    fileName = LEGACY_DRIVE_BACKUP_FILENAME;
                }

                if (fileId == null) {
                    Log.w(TAG, "No backup file found in Slide's private Drive folder.");
                    errors++;
                    return null;
                }

                // Download the file content
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                mDriveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);
                byte[] data = outputStream.toByteArray();
                Log.d(TAG, "Downloaded " + data.length + " bytes from: " + fileName);
                return data;

            } catch (HttpResponseException e) {
                if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_UNAUTHORIZED) {
                    Log.w(TAG, "Drive rejected the access token while downloading the backup.");
                    tokenRejected = true;
                } else {
                    Log.e(TAG, "Error downloading single-file backup from Drive", e);
                    errors++;
                }
            } catch (IOException e) {
                Log.e(TAG, "Error downloading single-file backup from Drive", e);
                errors++;
            }
            return null;
        }

        @Override
        protected void onPostExecute(byte[] backupData) {
            if (progress != null) {
                progress.dismiss();
            }

            if (tokenRejected && reauthorizeAndRetry(OP_RESTORE)) {
                return;
            }
            if (isFinishing() || isDestroyed()) {
                return;
            }

            if (backupData == null || backupData.length == 0) {
                showErrorDialog(R.string.err_general, R.string.backup_restore_failed_msg);
                return;
            }

            if (BackupArchive.isZip(backupData)) {
                promptAndRestore(backupData, false);
            } else {
                // A backup written before the encrypted format; it carries no password.
                new RestoreBackupAsyncTask(backupData, null).execute();
            }
        }
    }

    /** Show an error dialog with the specified title and message. */
    private void showErrorDialog(int titleResId, int messageResId) {
        Log.d(TAG, "showErrorDialog: title=" + titleResId + ", message=" + messageResId);
        showThemedDialog(
                new AlertDialog.Builder(this)
                        .setTitle(titleResId)
                        .setMessage(messageResId)
                        .setPositiveButton(R.string.btn_ok, null)
                        .setCancelable(false));
    }

    /**
     * Creates the dialog from the builder, matches its window to the app's themed card_background
     * (AppCompat dialogs otherwise show a gray panel), and shows it.
     */
    private void showThemedDialog(AlertDialog.Builder builder) {
        AlertDialog dialog = builder.create();
        DialogUtil.matchDialogToCardBackground(this, dialog);
        dialog.show();
    }
}
