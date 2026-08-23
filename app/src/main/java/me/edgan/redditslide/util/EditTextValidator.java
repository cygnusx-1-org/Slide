package me.edgan.redditslide.util;

import android.text.InputFilter;
import android.text.Spanned;
import android.widget.EditText;
import androidx.annotation.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Created by Fernando Barillas on 5/2/16.
 *
 * <p>Allows easier validation of EditText input via the use of an InputFilter. This way invalid
 * text is not allowed to be input.
 */
@NullMarked
public class EditTextValidator {

    private EditTextValidator() {}

    /**
     * Whether the text is shaped like a reddit username, applying the same rule reddit itself does.
     *
     * @param user The candidate username
     * @return Boolean for whether reddit would accept the name
     */
    public static boolean isValidUsername(String user) {
        /* https://github.com/reddit/reddit/blob/master/r2/r2/lib/validator/validator.py#L261 */
        return user.matches("^[a-zA-Z0-9_-]{3,20}$");
    }

    /**
     * Validates EditTexts intended for reddit username input. Valid characters include: A-Z, a-z
     * 0-9 - (hyphen) _ (underscore)
     *
     * <p>Disallowed characters are dropped from the insertion rather than rejecting the whole of
     * it. Rejecting outright breaks IME word completion: Gboard commits a picked suggestion as the
     * word plus a trailing space, so a filter that throws away any chunk containing one bad
     * character silently discards the entire word the user just chose.
     *
     * @param editText The EditText to validate a username for
     */
    public static void validateUsername(final EditText editText) {
        InputFilter filter =
                new InputFilter() {
                    @Override
                    @Nullable
                    public CharSequence filter(
                            CharSequence source,
                            int start,
                            int end,
                            Spanned dest,
                            int dstart,
                            int dend) {
                        StringBuilder kept = null;

                        for (int i = start; i < end; i++) {
                            char character = source.charAt(i);
                            if (Character.isLetterOrDigit(character)
                                    || character == '_'
                                    || character == '-') {
                                if (kept != null) {
                                    kept.append(character);
                                }
                            } else if (kept == null) {
                                // First rejected character: keep everything accepted so far and
                                // start collecting the remainder.
                                kept = new StringBuilder(end - start);
                                kept.append(source, start, i);
                            }
                        }

                        // null keeps the original insertion, spans and all.
                        return kept == null ? null : kept.toString();
                    }
                };

        editText.setFilters(new InputFilter[] {filter});
    }
}
