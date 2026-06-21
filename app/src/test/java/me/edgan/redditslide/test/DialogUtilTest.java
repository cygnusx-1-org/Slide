package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import me.edgan.redditslide.R;
import me.edgan.redditslide.util.DialogUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Guards the dialog-background changes: every AppCompat dialog in the app is shown through {@link
 * DialogUtil#showWithCardBackground} / matched with {@link DialogUtil#matchDialogToCardBackground},
 * so exercising those helpers under every app theme covers the shared crash surface of all the
 * wrapped call sites, and asserts the window actually gets the theme's card_background color.
 *
 * <p>Uses a plain {@link Application} (not the heavy Reddit app class) so the test stays a pure
 * helper/theme check.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class DialogUtilTest {

    /** Every concrete app theme that defines the card_background attribute. */
    private static final int[] THEMES = {
        R.style.Theme_AMOLED,
        R.style.Theme_AMOLED_lighter,
        R.style.Theme_DARK,
        R.style.Theme_DARK_BLUE,
        R.style.Theme_DEEP,
        R.style.Theme_PIXEL,
        R.style.Theme_NIGHT_RED,
        R.style.Theme_SEPIA,
        R.style.Theme_LIGHT,
    };

    /** Minimal AppCompat activity to host real dialogs under a chosen theme. */
    public static class TestActivity extends AppCompatActivity {}

    private TestActivity activityWithTheme(int themeRes) {
        ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class);
        controller.get().setTheme(themeRes);
        controller.setup();
        return controller.get();
    }

    private static int cardBackground(TestActivity activity) {
        TypedValue tv = new TypedValue();
        assertTrue(
                "theme must define card_background",
                activity.getTheme().resolveAttribute(R.attr.card_background, tv, true));
        return tv.data;
    }

    private static int windowColor(AlertDialog dialog) {
        Drawable bg = dialog.getWindow().getDecorView().getBackground();
        assertNotNull("window background should be set", bg);
        assertTrue("window background should be a ColorDrawable", bg instanceof ColorDrawable);
        return ((ColorDrawable) bg).getColor();
    }

    @Test
    public void showWithCardBackground_everyTheme_showsAndMatchesCardBackground() {
        for (int theme : THEMES) {
            TestActivity activity = activityWithTheme(theme);
            int expected = cardBackground(activity);

            AlertDialog dialog =
                    DialogUtil.showWithCardBackground(
                            new AlertDialog.Builder(activity)
                                    .setTitle("title")
                                    .setMessage("message")
                                    .setPositiveButton(android.R.string.ok, null));

            assertNotNull(dialog);
            assertTrue("dialog should be showing for theme " + theme, dialog.isShowing());
            assertEquals(
                    "dialog window should match card_background for theme " + theme,
                    expected,
                    windowColor(dialog));
            dialog.dismiss();
        }
    }

    @Test
    public void matchDialogToCardBackground_setsColorOnExistingDialog() {
        TestActivity activity = activityWithTheme(R.style.Theme_DARK);
        int expected = cardBackground(activity);

        AlertDialog dialog = new AlertDialog.Builder(activity).setMessage("m").create();
        DialogUtil.matchDialogToCardBackground(activity, dialog);
        dialog.show();

        assertEquals(expected, windowColor(dialog));
        dialog.dismiss();
    }

    @Test
    public void matchDialogToCardBackground_dialogOverload_derivesContext() {
        TestActivity activity = activityWithTheme(R.style.Theme_AMOLED);
        int expected = cardBackground(activity);

        AlertDialog dialog = new AlertDialog.Builder(activity).setMessage("m").create();
        DialogUtil.matchDialogToCardBackground(dialog); // context taken from the dialog
        dialog.show();

        assertEquals(expected, windowColor(dialog));
        dialog.dismiss();
    }

    @Test
    public void matchDialogToCardBackground_nullDialog_doesNotThrow() {
        DialogUtil.matchDialogToCardBackground((android.app.Dialog) null);
        DialogUtil.matchDialogToCardBackground(
                activityWithTheme(R.style.Theme_DARK), (android.app.Dialog) null);
    }
}
