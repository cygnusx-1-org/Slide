package me.edgan.redditslide.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import me.edgan.redditslide.R;

/**
 * Adds a "Google Translate" entry to the text-selection overflow menu of selectable TextViews
 * (e.g. the "Select text to copy" dialogs for posts and comments) and routes the selected text to
 * the Google Translate app, falling back to the web translator when the app is not installed.
 */
public class TranslateUtil {

    private static final String GOOGLE_TRANSLATE_PACKAGE = "com.google.android.apps.translate";

    /** Unique id for our injected menu item; offset to avoid clashing with the built-in items. */
    private static final int MENU_TRANSLATE_ID = Menu.FIRST + 1000;

    /**
     * Adds a "Google Translate" item to the text-selection overflow menu of the given selectable
     * {@link TextView}. When tapped, the currently selected text is handed to Google Translate.
     *
     * @param textView a TextView with {@code setTextIsSelectable(true)} already set.
     */
    public static void addToSelectionMenu(final TextView textView) {
        textView.setCustomSelectionActionModeCallback(
                new ActionMode.Callback() {
                    @Override
                    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                        menu.add(
                                Menu.NONE,
                                MENU_TRANSLATE_ID,
                                Menu.NONE,
                                R.string.translate_with_google);
                        return true;
                    }

                    @Override
                    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                        return false;
                    }

                    @Override
                    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                        if (item.getItemId() == MENU_TRANSLATE_ID) {
                            final int start = textView.getSelectionStart();
                            final int end = textView.getSelectionEnd();
                            if (start >= 0 && end > start) {
                                translate(
                                        textView.getContext(),
                                        textView.getText().toString().substring(start, end));
                            }
                            mode.finish();
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void onDestroyActionMode(ActionMode mode) {}
                });
    }

    /**
     * Launches Google Translate to translate {@code text}, falling back to the web translator (and
     * finally a toast) when the app is unavailable.
     */
    public static void translate(final Context context, final String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }

        // Preferred: hand the text straight to the Translate app via PROCESS_TEXT.
        final Intent processText = new Intent(Intent.ACTION_PROCESS_TEXT);
        processText.setType("text/plain");
        processText.putExtra(Intent.EXTRA_PROCESS_TEXT, text);
        processText.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
        processText.setPackage(GOOGLE_TRANSLATE_PACKAGE);
        try {
            context.startActivity(processText);
            return;
        } catch (ActivityNotFoundException ignored) {
            // Translate app missing or doesn't handle PROCESS_TEXT; fall through to the web.
        }

        // Fallback: open the Google Translate website in the browser.
        try {
            final String url =
                    "https://translate.google.com/?sl=auto&tl=en&op=translate&text="
                            + Uri.encode(text);
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.translate_no_app, Toast.LENGTH_SHORT).show();
        }
    }
}
