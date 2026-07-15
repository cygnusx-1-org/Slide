package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.text.Layout;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import io.noties.markwon.ext.tables.TableRowSpan;
import me.edgan.redditslide.ClickableText;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.handler.TextViewLinkHandler;
import me.edgan.redditslide.markdown.RedditMarkwon;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Issue #299: links inside a Markwon-rendered table must be tappable. Markwon draws each table
 * cell's text — including its link {@link URLSpan}s — into the row's own internal {@link Layout}
 * rather than the outer text buffer, so a plain buffer hit-test misses them. {@link
 * TextViewLinkHandler} resolves cell links from that internal layout and routes them through the
 * normal onLinkClick path.
 *
 * <p>These tests render a real table, measure/lay out/draw it (so each {@link TableRowSpan} builds
 * its per-cell layouts), then drive taps through the handler's public {@code onTouchEvent} against a
 * recording {@link ClickableText} and assert which URL — if any — is routed.
 */
@RunWith(RobolectricTestRunner.class)
// Stock Application (like RoborazziLayoutTest): we only render a TextView, so avoid instantiating
// the real .Reddit app singleton and its background media-cache init. Native graphics so
// view.draw() actually builds the table row's per-cell layouts.
@Config(sdk = 33, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TableCellLinkTest {

    @Before
    public void setUp() {
        // The stock Application doesn't run Reddit.onCreate, and Robolectric gives each test a fresh
        // sandbox, so seed the statics the render path reads. Palette.getColor/getDefaultColor (via
        // RedditMarkwon.setParsedMarkdown) dereference Reddit.colors — an empty prefs store is enough
        // (every subreddit then resolves to the default color). mApplication is seeded defensively so
        // any future getAppContext() on the render path doesn't NPE.
        TestUtils.seedRedditApplication();
        Reddit.colors =
                ((Context) ApplicationProvider.getApplicationContext())
                        .getSharedPreferences("COLOR", Context.MODE_PRIVATE);
        // RedditMarkwon caches one Markwon instance (with theme colors baked in) across the whole
        // JVM. Drop it so each test builds its own against this test's context rather than
        // inheriting whatever a prior test class left cached.
        RedditMarkwon.invalidate();
    }

    @After
    public void tearDown() {
        // Restore the pristine (stock-Application) state so this test doesn't leave global statics
        // dirty for a same-config test sharing the Robolectric sandbox (e.g. RoborazziLayoutTest).
        Reddit.colors = null;
        TestUtils.clearRedditApplication();
        RedditMarkwon.invalidate();
    }

    private static final int WIDTH_PX = 1000;

    /** Two-column table: left cell is plain text, right cell is a link. */
    private static final String TABLE =
            "Intro paragraph before the table.\n\n"
                    + "Left|Right\n"
                    + ":-:|:-:\n"
                    + "plain|[Go](https://example.com/target)\n";

    private static final String TARGET = "https://example.com/target";

    /** Records the URL the handler routes, without SpoilerRobotoTextView.onLinkClick's side effects. */
    private static final class RecordingClickableText implements ClickableText {
        String clickedUrl;

        @Override
        public void onLinkClick(String url, int xOffset, String subreddit, URLSpan span) {
            clickedUrl = url;
        }

        @Override
        public void onLinkLongClick(String url, MotionEvent event) {}
    }

    /** Render {@code markdown} into a measured, laid-out, drawn TextView so table cell layouts exist. */
    private static SpoilerRobotoTextView renderAndLayout(String markdown) {
        SpoilerRobotoTextView tv =
                new SpoilerRobotoTextView(ApplicationProvider.getApplicationContext());
        RedditMarkwon.setMarkdown(tv, "test", markdown);
        tv.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        tv.layout(0, 0, tv.getMeasuredWidth(), tv.getMeasuredHeight());
        // Drawing makes each TableRowSpan build its per-cell StaticLayouts and set its width, which
        // the cell hit-test relies on. Draw, then drain the looper so any invalidation Markwon posts
        // (TableRowsScheduler) settles, then draw once more so the final geometry is stable — rather
        // than depending on it all happening within a single synchronous draw.
        Bitmap bmp =
                Bitmap.createBitmap(
                        Math.max(tv.getMeasuredWidth(), 1),
                        Math.max(tv.getMeasuredHeight(), 1),
                        Bitmap.Config.ARGB_8888);
        tv.draw(new Canvas(bmp));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        tv.draw(new Canvas(bmp));
        return tv;
    }

    /** The row span whose cells contain a link to {@code url}, or {@code null} if none does. */
    private static TableRowSpan rowWithUrl(SpoilerRobotoTextView tv, String url) {
        Spanned buffer = (Spanned) tv.getText();
        for (TableRowSpan row : buffer.getSpans(0, buffer.length(), TableRowSpan.class)) {
            if (row.cellWidth() <= 0) {
                continue;
            }
            for (int col = 0; ; col++) {
                Layout cell =
                        row.findLayoutForHorizontalOffset(
                                col * row.cellWidth() + row.cellWidth() / 2);
                if (cell == null) {
                    break;
                }
                Spanned cellText = (Spanned) cell.getText();
                for (URLSpan u : cellText.getSpans(0, cellText.length(), URLSpan.class)) {
                    if (url.equals(u.getURL())) {
                        return row;
                    }
                }
            }
        }
        return null;
    }

    /** Content-space center (x,y) of the given column's cell in {@code row}. */
    private static int[] cellCenter(SpoilerRobotoTextView tv, TableRowSpan row, int col) {
        Layout layout = tv.getLayout();
        Spanned buffer = (Spanned) tv.getText();
        int line = layout.getLineForOffset(buffer.getSpanStart(row));
        int y = (layout.getLineTop(line) + layout.getLineBottom(line)) / 2;
        int x = col * row.cellWidth() + row.cellWidth() / 2;
        return new int[] {x, y};
    }

    /** Dispatch a DOWN+UP tap at a content-space point through a fresh handler; return the record. */
    private static RecordingClickableText tapAt(SpoilerRobotoTextView tv, int contentX, int contentY) {
        RecordingClickableText rec = new RecordingClickableText();
        Spannable buffer = (Spannable) tv.getText();
        TextViewLinkHandler handler = new TextViewLinkHandler(rec, "test", buffer);
        // onTouchEvent subtracts padding and adds scroll; feed raw event coords so it recovers the
        // content point (scroll is 0 on a freshly laid-out view).
        float ex = contentX + tv.getTotalPaddingLeft();
        float ey = contentY + tv.getTotalPaddingTop();
        long t = 100L;
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, ex, ey, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 1, MotionEvent.ACTION_UP, ex, ey, 0);
        try {
            handler.onTouchEvent(tv, buffer, down);
            handler.onTouchEvent(tv, buffer, up);
        } finally {
            down.recycle();
            up.recycle();
        }
        return rec;
    }

    @Test
    public void tapOnCellLinkRoutesUrl() {
        SpoilerRobotoTextView tv = renderAndLayout(TABLE);
        TableRowSpan dataRow = rowWithUrl(tv, TARGET);
        assertNotNull("table should render a row span whose cell holds the link", dataRow);
        int[] p = cellCenter(tv, dataRow, 1); // right column holds [Go](...)
        RecordingClickableText rec = tapAt(tv, p[0], p[1]);
        assertEquals("tapping the link cell should route its url", TARGET, rec.clickedUrl);
    }

    @Test
    public void tapOnNonLinkCellRoutesNothing() {
        SpoilerRobotoTextView tv = renderAndLayout(TABLE);
        TableRowSpan dataRow = rowWithUrl(tv, TARGET);
        assertNotNull(dataRow);
        int[] p = cellCenter(tv, dataRow, 0); // left column is plain text
        RecordingClickableText rec = tapAt(tv, p[0], p[1]);
        assertNull("tapping a cell with no link must not route a click", rec.clickedUrl);
    }

    @Test
    public void tapOutsideTableRoutesNothing() {
        SpoilerRobotoTextView tv = renderAndLayout(TABLE);
        Layout layout = tv.getLayout();
        int y = (layout.getLineTop(0) + layout.getLineBottom(0)) / 2; // the intro paragraph line
        RecordingClickableText rec = tapAt(tv, 10, y);
        assertNull("tapping non-table text must not route a click", rec.clickedUrl);
    }

    @Test
    public void tableCellLinkIsNotInOuterBuffer() {
        // Guards the premise the whole feature rests on: the cell link lives inside the row span's
        // own layout, not the outer text buffer, so a normal buffer hit-test would miss it. If a
        // future Markwon change surfaced it in the buffer, this would flag that the special-case
        // path may double-handle it.
        SpoilerRobotoTextView tv = renderAndLayout(TABLE);
        Spanned buffer = (Spanned) tv.getText();
        for (URLSpan u : buffer.getSpans(0, buffer.length(), URLSpan.class)) {
            assertNotEquals(TARGET, u.getURL());
        }
    }
}
