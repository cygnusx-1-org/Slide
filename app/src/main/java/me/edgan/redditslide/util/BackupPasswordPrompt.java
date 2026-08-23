package me.edgan.redditslide.util;

import android.app.Activity;
import android.text.Editable;
import android.text.InputType;

import me.edgan.redditslide.R;

import org.jspecify.annotations.NullMarked;

/**
 * The password dialogs shared by every backup path -- local file and Google Drive, in both product
 * flavours -- so the prompt looks and behaves the same wherever it is raised.
 *
 * <p>Both prompts are built from the same {@link MaterialInputDialog} configuration, which is what
 * keeps the password row identical between them rather than merely similar.
 */
@NullMarked
public final class BackupPasswordPrompt {

    /** zip4j accepts any length; these are the bounds Slide asks the user to stay inside. */
    public static final int MIN_LENGTH = 6;

    public static final int MAX_LENGTH = 32;

    private BackupPasswordPrompt() {}

    /** Receives the entered password. The callee owns the array and must zero it when done. */
    public interface PasswordCallback {
        void onPassword(char[] password);
    }

    /**
     * Asks for a new backup password. Typed once: the reveal icon on the field lets the user read
     * it back before committing, which is what a second field would otherwise be there to catch.
     *
     * <p>Callers ask this <em>before</em> choosing where the backup goes. Dismissing the prompt
     * then simply ends the backup, with nothing created to tidy up.
     */
    public static void forNewBackup(Activity activity, PasswordCallback callback) {
        if (isGone(activity)) {
            return;
        }
        new MaterialInputDialog.Builder(activity)
                .title(R.string.backup_password_title)
                .content(
                        activity.getString(
                                R.string.backup_password_message, MIN_LENGTH, MAX_LENGTH))
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                .inputRange(MIN_LENGTH, MAX_LENGTH)
                .input(activity.getString(R.string.backup_password_hint), null, null)
                .positiveText(R.string.btn_ok)
                .negativeText(R.string.btn_cancel)
                .onPositive(d -> callback.onPassword(extract(d.getInputEditText().getText())))
                .show();
    }

    /**
     * Asks for the password an existing backup was encrypted with.
     *
     * @param retry true when the previous attempt was rejected, which swaps in the retry message
     */
    public static void forRestore(Activity activity, boolean retry, PasswordCallback callback) {
        if (isGone(activity)) {
            return;
        }
        new MaterialInputDialog.Builder(activity)
                .title(R.string.restore_password_title)
                .content(
                        retry
                                ? R.string.restore_password_wrong
                                : R.string.restore_password_message)
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                .inputRange(MIN_LENGTH, MAX_LENGTH)
                .input(activity.getString(R.string.backup_password_hint), null, null)
                .positiveText(R.string.btn_ok)
                .negativeText(R.string.btn_cancel)
                .onPositive(dialog -> callback.onPassword(extract(dialog.getInputEditText().getText())))
                .show();
    }

    /**
     * Copies the field straight into a {@code char[]}. Going through {@code toString()} would leave
     * the password in an immutable String that cannot be wiped afterwards.
     */
    private static char[] extract(Editable text) {
        final char[] password = new char[text.length()];
        text.getChars(0, text.length(), password, 0);
        return password;
    }

    private static boolean isGone(Activity activity) {
        return activity.isFinishing() || activity.isDestroyed();
    }
}
