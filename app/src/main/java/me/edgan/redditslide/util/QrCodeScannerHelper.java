package me.edgan.redditslide.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.ContextThemeWrapper;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Visuals.ColorPreferences;

/** Helper class for scanning QR codes, specifically for Reddit Client IDs. */
public class QrCodeScannerHelper {

    public static final int CAMERA_PERMISSION_REQUEST_CODE = 1338;
    private static final Map<Activity, WeakReference<QrScanCallback>> pendingCallbacks = new HashMap<>();

    /** Callback interface for scan results. */
    public interface QrScanCallback {
        void onQrCodeScanned(String result);
        void onScanFailed(String error);
    }

    /**
     * Starts the QR code scanning process. Checks for camera permission first.
     *
     * @param activity The calling activity.
     * @param callback The callback to handle the scan result.
     */
    public static void startScan(final Activity activity, final QrScanCallback callback) {
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Store callback for when permission result is received
            pendingCallbacks.put(activity, new WeakReference<>(callback));
            ActivityCompat.requestPermissions(
                    activity, new String[] {Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            // Permission already granted
            showScanner(activity, callback);
        }
    }

    /**
     * Handles the result of the camera permission request.
     *
     * @param requestCode The request code.
     * @param grantResults The grant results.
     * @param activity The activity that received the result.
     */
    public static void handlePermissionsResult(
            int requestCode, @NonNull int[] grantResults, @NonNull Activity activity) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            WeakReference<QrScanCallback> callbackRef = pendingCallbacks.remove(activity);
            QrScanCallback callback = (callbackRef != null) ? callbackRef.get() : null;

            if (callback != null) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    showScanner(activity, callback);
                } else {
                    // Permission denied
                    callback.onScanFailed(activity.getString(R.string.camera_permission_denied));
                }
            } else {
                LogUtil.w("QrCodeScannerHelper: No callback found for activity after permission result.");
                // Optionally show a generic error toast if callback is lost
                Toast.makeText(activity, "Scan initiation failed.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Shows the actual QR code scanner dialog.
     *
     * @param activity The calling activity.
     * @param callback The callback to handle the scan result.
     */
    private static void showScanner(final Activity activity, final QrScanCallback callback) {
        final Context contextThemeWrapper =
                new ContextThemeWrapper(activity, new ColorPreferences(activity).getFontStyle().getBaseId());

        final DecoratedBarcodeView barcodeView = new DecoratedBarcodeView(contextThemeWrapper);
        LinearLayout.LayoutParams viewParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        barcodeView.setLayoutParams(viewParams);
        barcodeView.setStatusText(activity.getString(R.string.client_id_scan_prompt));

        final AlertDialog scannerDialog =
                new MaterialAlertDialogBuilder(contextThemeWrapper)
                        .setTitle(R.string.client_id_scan_qr)
                        .setView(barcodeView)
                        .setNegativeButton(R.string.btn_cancel, (dialog, which) -> dialog.dismiss())
                        .create();

        barcodeView.decodeSingle(
                new BarcodeCallback() {
                    @Override
                    public void barcodeResult(BarcodeResult result) {
                        scannerDialog.dismiss();
                        if (result.getText() != null) {
                            String contents = result.getText().trim();
                            // Validate that the QR code contains exactly 22 characters
                            if (contents.length() == 22) {
                                callback.onQrCodeScanned(contents);
                            } else {
                                callback.onScanFailed(
                                        activity.getString(R.string.client_id_invalid_length));
                            }
                        } else {
                            callback.onScanFailed("Scan failed: No data found.");
                        }
                    }

                    @Override
                    public void possibleResultPoints(List<com.google.zxing.ResultPoint> resultPoints) {
                        // Optional
                    }
                });

        scannerDialog.setOnDismissListener(dialog -> barcodeView.pause());
        scannerDialog.setOnShowListener(dialog -> barcodeView.resume());
        scannerDialog.show();
    }

    /**
     * Convenience implementation of QrScanCallback that updates an EditText and shows errors via Toast.
     */
    public static class EditTextUpdateCallback implements QrScanCallback {
        private final WeakReference<EditText> editTextRef;
        private final WeakReference<Context> contextRef;

        public EditTextUpdateCallback(EditText editText, Context context) {
            this.editTextRef = new WeakReference<>(editText);
            this.contextRef = new WeakReference<>(context);
        }

        @Override
        public void onQrCodeScanned(String result) {
            EditText editText = editTextRef.get();
            if (editText != null) {
                editText.setText(""); // Clear existing text first
                editText.setText(result);
            }
        }

        @Override
        public void onScanFailed(String error) {
            Context context = contextRef.get();
            if (context != null) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show();
            }
        }
    }
}