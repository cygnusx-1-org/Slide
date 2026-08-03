package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import java.time.Duration;
import me.edgan.redditslide.ClickableText;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.handler.TextViewLinkHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/**
 * {@link TextViewLinkHandler#onTouchEvent} hit-tests against {@code TextView.getLayout()}, which is
 * declared nullable, so the handler guards it. These tests pin what that guard does: return "not
 * handled" rather than dereferencing null, and cancel the long-press that ACTION_DOWN posted, since
 * every other exit from the method cancels it.
 *
 * <p>Reaching the guard takes a deliberate setup. {@code onTouchEvent} calls {@code
 * getTotalPaddingLeft()/getTotalPaddingTop()} before it reads the layout, and in the default
 * lines-based max mode those call {@code assumeLayout()}, which materializes a layout — so an
 * unmeasured view still reports one by the time the guard runs. Only a pixel-based max height
 * ({@code setMaxHeight}) makes {@code getExtendedPaddingTop} return early without assuming a
 * layout, leaving {@code getLayout()} genuinely null.
 *
 * <p>No {@code SpoilerRobotoTextView} in the app sets a pixel max height today, so this is the
 * guard's contract under a configuration the app does not currently produce, not a reproduction of
 * a shipped crash. It is worth pinning because the guard is otherwise untested code that a future
 * layout change could start reaching.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class TextViewLinkHandlerNullLayoutTest {

    private static final String URL = "https://example.com/target";
    private static final int WIDTH_PX = 600;

    @Before
    public void setUp() {
        // SpoilerRobotoTextView's construction path reads these statics; the stock Application does
        // not run Reddit.onCreate. An empty store is enough (everything resolves to defaults).
        TestUtils.seedRedditApplication();
        Reddit.colors =
                ((Context) ApplicationProvider.getApplicationContext())
                        .getSharedPreferences("COLOR", Context.MODE_PRIVATE);
    }

    @After
    public void tearDown() {
        Reddit.colors = null;
        TestUtils.clearRedditApplication();
    }

    /** Records what the handler routes, without SpoilerRobotoTextView's real side effects. */
    private static final class RecordingClickableText implements ClickableText {
        String clickedUrl;
        String longClickedUrl;

        @Override
        public void onLinkClick(String url, int xOffset, String subreddit, URLSpan span) {
            clickedUrl = url;
        }

        @Override
        public void onLinkLongClick(String url, MotionEvent event) {
            longClickedUrl = url;
        }
    }

    private static Spannable linkText() {
        SpannableString s = new SpannableString("tap here");
        s.setSpan(new URLSpan(URL), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static SpoilerRobotoTextView newView() {
        SpoilerRobotoTextView tv =
                new SpoilerRobotoTextView(ApplicationProvider.getApplicationContext());
        // BufferType.SPANNABLE: the default stores an immutable SpannedString, but the handler's
        // buffer parameter is a Spannable (Selection.removeSelection writes to it).
        tv.setText(linkText(), TextView.BufferType.SPANNABLE);
        return tv;
    }

    /** A view holding a link, measured and laid out, so getLayout() is non-null. */
    private static SpoilerRobotoTextView laidOutView() {
        SpoilerRobotoTextView tv = newView();
        tv.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        tv.layout(0, 0, tv.getMeasuredWidth(), tv.getMeasuredHeight());
        return tv;
    }

    /**
     * A view whose {@code getLayout()} stays null through {@code onTouchEvent}. The max height is
     * what does it: it switches the view to pixel max mode, so the padding getters the handler
     * calls first no longer call {@code assumeLayout()}. See the class javadoc.
     */
    private static SpoilerRobotoTextView noLayoutView() {
        SpoilerRobotoTextView tv = newView();
        tv.setMaxHeight(1000);
        return tv;
    }

    private static MotionEvent event(int action, float x, float y, long when) {
        return MotionEvent.obtain(when, when, action, x, y, 0);
    }

    /** Run the looper past the longest long-press delay the handler can post. */
    private static void advancePastLongPress() {
        Shadows.shadowOf(Looper.getMainLooper())
                .idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout() + 500L));
    }

    @Test
    public void touchWithNoLayoutIsNotHandled() {
        SpoilerRobotoTextView tv = noLayoutView();
        Spannable buffer = (Spannable) tv.getText();
        RecordingClickableText rec = new RecordingClickableText();
        TextViewLinkHandler handler = new TextViewLinkHandler(rec, "test", buffer);

        MotionEvent down = event(MotionEvent.ACTION_DOWN, 10f, 10f, 100L);
        try {
            // Without the guard this dereferenced a null Layout and threw.
            assertFalse(
                    "a touch with nothing to hit-test against is not handled",
                    handler.onTouchEvent(tv, buffer, down));
        } finally {
            down.recycle();
        }
        assertNull("no link may be routed from a touch that was never hit-tested", rec.clickedUrl);
        assertNull(
                "premise: the layout must still be null after the call, or this test is not"
                        + " exercising the guard",
                tv.getLayout());
    }

    @Test
    public void losingTheLayoutCancelsThePendingLongPress() {
        // ACTION_DOWN on a laid-out link posts the long-press. The next event arrives against a
        // view with no layout, which takes the guard's early return. The pending callback must not
        // survive it and fire against the stale link.
        SpoilerRobotoTextView laidOut = laidOutView();
        assertNotNull("premise: a laid-out TextView has a layout", laidOut.getLayout());
        Spannable buffer = (Spannable) laidOut.getText();

        RecordingClickableText rec = new RecordingClickableText();
        TextViewLinkHandler handler = new TextViewLinkHandler(rec, "test", buffer);

        float x = laidOut.getTotalPaddingLeft() + 5f;
        float y = laidOut.getTotalPaddingTop() + 5f;

        MotionEvent down = event(MotionEvent.ACTION_DOWN, x, y, 100L);
        try {
            handler.onTouchEvent(laidOut, buffer, down);
        } finally {
            down.recycle();
        }

        SpoilerRobotoTextView noLayout = noLayoutView();
        MotionEvent move = event(MotionEvent.ACTION_MOVE, x, y, 150L);
        try {
            handler.onTouchEvent(noLayout, buffer, move);
        } finally {
            move.recycle();
        }

        advancePastLongPress();

        assertNull(
                "the long-press posted before the layout was lost must not fire",
                rec.longClickedUrl);
    }

    @Test
    public void longPressStillFiresWhenTheViewKeepsItsLayout() {
        // The complement: the cancellation must be specific to the no-layout path, not something
        // that defeats long-press generally.
        SpoilerRobotoTextView tv = laidOutView();
        Spannable buffer = (Spannable) tv.getText();

        RecordingClickableText rec = new RecordingClickableText();
        TextViewLinkHandler handler = new TextViewLinkHandler(rec, "test", buffer);

        MotionEvent down =
                event(
                        MotionEvent.ACTION_DOWN,
                        tv.getTotalPaddingLeft() + 5f,
                        tv.getTotalPaddingTop() + 5f,
                        100L);
        try {
            handler.onTouchEvent(tv, buffer, down);
        } finally {
            down.recycle();
        }

        advancePastLongPress();

        assertNotNull("a normal long-press must still route its url", rec.longClickedUrl);
    }
}
