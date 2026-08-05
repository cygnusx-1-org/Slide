package me.edgan.redditslide.Activities;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.Nullable;
import me.edgan.redditslide.Notifications.ImageDownloadNotificationService;
import me.edgan.redditslide.util.ImageSaveUtils;
import me.edgan.redditslide.util.StorageUtil;
import org.jspecify.annotations.NullMarked;

/**
 * Base activity that implements SAF image saving functionality. This provides common image saving
 * behavior that can be inherited by other activities.
 */
@NullMarked
public abstract class BaseSaveActivity extends FullScreenActivity {

    // Fields that child activities will need for image saving
    @SuppressWarnings("NullAway.Init")
    protected String subreddit;
    @SuppressWarnings("NullAway.Init")
    public String submissionTitle;
    public static final String EXTRA_SUBMISSION_TITLE = ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

    private static final String TAG = "BaseSaveActivity";
    private static final int REQUEST_STORAGE_ACCESS = 1;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == REQUEST_STORAGE_ACCESS && resultCode == Activity.RESULT_OK && data != null) {
            // Persist access permissions
            Uri treeUri = data.getData();
            if (treeUri == null) {
                return;
            }
            Log.d(TAG, "Got tree URI: " + treeUri);

            try {
                getContentResolver()
                        .takePersistableUriPermission(
                                treeUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                Log.d(TAG, "Took persistable permission");

                // Save the URI
                StorageUtil.saveStorageUri(this, treeUri);
                Log.d(TAG, "Saved storage URI");

                // Notify any subclasses that permission was granted
                onStoragePermissionGranted();
            } catch (Exception e) {
                Log.e(TAG, "Error handling storage permission", e);
            }
        }
    }

    /**
     * Shows the directory picker if no directory is selected, otherwise proceeds with save.
     *
     * @param isGif Whether the content is a GIF
     * @param contentUrl URL of the content to save
     * @param index Index in a gallery (if applicable)
     */
    protected void doImageSave(boolean isGif, String contentUrl, int index) {
        // Updated to use the unified ImageSaveUtils, removing the old logic and doOnClick reference
        ImageSaveUtils.doImageSave(
                this,
                isGif,
                contentUrl,
                index,
                subreddit,
                submissionTitle,
                () -> StorageUtil.showDirectoryChooser(this) // Use directory chooser as the callback
        );
    }

    /**
     * Override this to handle when storage permission is granted. Child classes can implement this
     * to retry failed save attempts.
     */
    protected void onStoragePermissionGranted() {
        // Override in subclasses if needed
    }
}
