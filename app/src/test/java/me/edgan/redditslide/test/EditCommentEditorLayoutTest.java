package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.DoEditorActions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * Editing a comment inflates {@code edit_comment.xml} into a dialog that resizes with the IME. A
 * long body must not scroll the image toolbar or the preview and submit buttons out of that
 * dialog, and dismissing the keyboard has to ask the editor to measure again.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class EditCommentEditorLayoutTest {

    private static final int WIDTH_PX = 1080;

    public static class TestActivity extends AppCompatActivity {}

    @Test
    public void tallCommentKeepsTheToolbarAndButtonsOnScreen() {
        View root = inflateEditor();
        EditText entry = root.findViewById(R.id.entry);
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            body.append("A long comment line that fills the editor past the keyboard viewport.\n");
        }
        entry.setText(body.toString());

        layoutWithinKeyboardViewport(root);

        assertChromeWithinRoot(root, root.findViewById(R.id.imagerep));
        assertChromeWithinRoot(root, root.findViewById(R.id.preview));
        assertChromeWithinRoot(root, root.findViewById(R.id.submit));
    }

    @Test
    public void dismissingTheImeRequestsLayoutOnce() {
        ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class);
        TestActivity activity = controller.get();
        activity.setTheme(R.style.Theme_DARK);
        controller.setup();

        View root = activity.getLayoutInflater().inflate(R.layout.edit_comment, null);
        FrameLayout parent = new FrameLayout(activity);
        parent.addView(root);
        activity.setContentView(parent);
        ShadowLooper looper = Shadows.shadowOf(Looper.getMainLooper());
        looper.idle();

        DoEditorActions.relayoutEditorWhenImeChanges(root);
        dispatchImeBottom(root, 400);
        dispatchImeBottom(root, 0);
        for (int i = 0; i < 8 && !parent.isLayoutRequested(); i++) {
            looper.runOneTask();
        }
        assertTrue(parent.isLayoutRequested());

        looper.idle();
        parent.layout(0, 0, parent.getWidth(), parent.getHeight());
        assertFalse(parent.isLayoutRequested());

        dispatchImeBottom(root, 0);
        if (!looper.isIdle()) {
            looper.runOneTask();
        }
        assertFalse(parent.isLayoutRequested());
    }

    @Test
    public void shortCommentKeepsTheEditorActionsAttached() {
        ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class);
        TestActivity activity = controller.get();
        activity.setTheme(R.style.Theme_DARK);
        controller.setup();

        View root = activity.getLayoutInflater().inflate(R.layout.edit_comment, null);
        EditText entry = root.findViewById(R.id.entry);
        entry.setText("ok");
        DoEditorActions.doActions(entry, root, null, activity, null, null);

        layoutWithinKeyboardViewport(root);

        for (int id : new int[] {R.id.entry, R.id.imagerep, R.id.cancel, R.id.preview, R.id.submit}) {
            assertChromeWithinRoot(root, root.findViewById(id));
        }
        assertTrue(root.findViewById(R.id.preview).hasOnClickListeners());
        assertNotNull(root.findViewById(R.id.submit));
        assertEquals(R.id.submit, root.findViewById(R.id.submit).getId());
    }

    private static View inflateEditor() {
        ActivityController<TestActivity> controller = Robolectric.buildActivity(TestActivity.class);
        TestActivity activity = controller.get();
        activity.setTheme(R.style.Theme_DARK);
        controller.setup();
        return activity.getLayoutInflater().inflate(R.layout.edit_comment, null);
    }

    /**
     * The height available once the keyboard is open: the text viewport cap plus the toolbar and
     * the button row, which is shorter than an unwrapped long comment.
     */
    private static void layoutWithinKeyboardViewport(View root) {
        int widthSpec = View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY);
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        View toolbar = (View) root.findViewById(R.id.imagerep).getParent().getParent();
        View buttons = (View) root.findViewById(R.id.submit).getParent();
        toolbar.measure(widthSpec, unspecified);
        buttons.measure(widthSpec, unspecified);
        int atMost =
                dp(root, 240) + toolbar.getMeasuredHeight() + buttons.getMeasuredHeight();
        root.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(atMost, View.MeasureSpec.AT_MOST));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
    }

    private static void assertChromeWithinRoot(View root, View chrome) {
        assertEquals(View.VISIBLE, chrome.getVisibility());
        int bottom = chrome.getBottom();
        ViewGroup parent = (ViewGroup) chrome.getParent();
        while (parent != root) {
            bottom += parent.getTop();
            parent = (ViewGroup) parent.getParent();
        }
        assertTrue(bottom <= root.getMeasuredHeight());
    }

    private static void dispatchImeBottom(View root, int bottom) {
        WindowInsetsCompat insets =
                new WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, bottom))
                        .build();
        ViewCompat.dispatchApplyWindowInsets(root, insets);
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}
