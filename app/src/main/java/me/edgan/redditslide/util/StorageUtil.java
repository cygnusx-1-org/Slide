package me.edgan.redditslide.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import me.edgan.redditslide.R;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class StorageUtil {
    private static final String TAG = "SlideStorage";
    private static final String PREF_STORAGE_URI = "storage_uri";
    private static final String PREF_NAME = "reddit_slide";
    public static final int REQUEST_STORAGE_ACCESS = 1;
    public static final int REQUEST_STORAGE_PERMISSION = 42;

    public static boolean hasStorageAccess(Context context) {
        Uri uri = getStorageUri(context);
        if (uri == null) {
            Log.d(TAG, "No URI found, no access");
            return false;
        }

        try {
            final int takeFlags =
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
            DocumentFile dir = DocumentFile.fromTreeUri(context, uri);
            boolean hasAccess = dir != null && dir.exists() && dir.canWrite();
            return hasAccess;
        } catch (Exception e) {
            LogUtil.e(e, TAG + " Error checking storage access: " + e.getMessage());
            return false;
        }
    }

    public static void saveStorageUri(Activity activity, Uri uri) {
        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_STORAGE_URI, uri.toString())
                .apply();
    }

    public static @Nullable Uri getStorageUri(Context context) {
        String uriStr =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_STORAGE_URI, null);

        if (uriStr != null) {
            Uri uri = Uri.parse(uriStr);
            Log.d(TAG, "Parsed URI: " + uri);
            return uri;
        }
        return null;
    }

    public static void showDirectoryChooser(
            Activity activity, @Nullable OnDirectorySelectedListener listener) {
        if (activity instanceof DirectoryChooserHost) {
            ((DirectoryChooserHost) activity).setDirectorySelectedListener(listener);
        } else {
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            activity.startActivityForResult(intent, REQUEST_STORAGE_ACCESS);
        } catch (Exception e) {
            LogUtil.e(e, TAG + "Error showing directory chooser: " + e.getMessage());
        }
    }

    // Interface for directory selection callback
    public interface OnDirectorySelectedListener {
        void onDirectorySelected(Uri uri);
    }

    // Host interface for activities that use directory chooser
    public interface DirectoryChooserHost {
        void setDirectorySelectedListener(@Nullable OnDirectorySelectedListener listener);

        @Nullable OnDirectorySelectedListener getDirectorySelectedListener();
    }

    public static void showDirectoryChooser(Activity activity) {
        showDirectoryChooser(activity, null);
    }

    public static String getDisplayPath(Context context, Uri uri) {
        try {
            DocumentFile docFile = DocumentFile.fromTreeUri(context, uri);
            if (docFile != null) {
                // Get the full path from the URI
                String path = uri.getPath();
                if (path != null) {
                    // Convert /tree/primary:Download/Slide to /Download/Slide
                    path = path.replace("/tree/primary:", "/");
                    return path;
                }

                // Fallback to just the display name if path parsing fails
                String name = docFile.getName();
                if (name != null) {
                    return name;
                }
            }
        } catch (Exception e) {
            LogUtil.e(e, TAG + "Error getting display path: " + e.getMessage());
        }
        return context.getString(R.string.settings_storage_location_unset);
    }

    public static void clearStorageUri(Activity activity) {
        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_STORAGE_URI)
                .apply();
    }

    public static boolean validateOrRequestStorageAccess(Activity activity) {
        if (!hasStorageAccess(activity)) {
            showDirectoryChooser(activity);
            return false;
        }
        return true;
    }

    public static void setStorageUri(Context context, Uri uri) {
        context.getSharedPreferences("storage", Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_STORAGE_URI, uri.toString())
                .apply();
    }
}
