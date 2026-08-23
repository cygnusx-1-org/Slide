package me.edgan.redditslide.Fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import me.edgan.redditslide.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Covers the wiring between the album card's overlaying info panel and the list underneath it: that
 * the fragment pads its RecyclerView by the panel's height, and re-pads it when that height changes.
 *
 * <p>The arithmetic lives in LayoutUtils.insetForOverlay and is tested there. What is tested here is
 * that this fragment calls it at all, and with the height it just gave the panel — removing either
 * half leaves the last row of a shadowbox album behind an opaque panel, which no other test notices.
 *
 * <p>The base class needs nothing from Shadowbox during onCreateView (only submissionForShadowboxPage
 * and openShadowboxComments cast to it, and neither runs here), so a plain activity hosts it.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class BaseAlbumFullInsetTest {

    private static final int TITLE_HEIGHT = 140;
    /** A padding no measurement would produce, so only a second write could restore the real one. */
    private static final int SENTINEL = 7;
    private static final int WIDTH = 1080;
    private static final int HEIGHT = 2280;

    private TestActivity activity;

    @Before
    public void setUp() {
        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        activity = controller.get();
        activity.setTheme(R.style.Theme_LIGHT);
        // @id/title sizes itself from ?attr/font_cardtitle, which lives in the font-style overlay the
        // activities apply over their theme rather than in the theme itself.
        activity.getTheme().applyStyle(R.style.FontStyle_MediumPost, true);
        activity.getTheme().applyStyle(R.style.FontStyle_MediumComment, true);
        controller.setup();
    }

    @Test
    public void theListIsPaddedByTheMeasuredPanelHeight() {
        final TestAlbumFragment fragment = show();
        final RecyclerView list = (RecyclerView) fragment.list;

        layOutTitle(fragment, TITLE_HEIGHT);

        assertEquals(TITLE_HEIGHT, list.getPaddingBottom());
        // Rows pass under the panel while it is faded rather than stopping above it.
        assertFalse(list.getClipToPadding());
    }

    @Test
    public void aTitleThatChangesHeightRepadsTheList() {
        final TestAlbumFragment fragment = show();
        final RecyclerView list = (RecyclerView) fragment.list;

        layOutTitle(fragment, TITLE_HEIGHT);
        assertEquals(TITLE_HEIGHT, list.getPaddingBottom());

        // What rotation does: these activities declare configChanges for orientation, so the fragment
        // is not recreated and the title simply reflows to a new height.
        layOutTitle(fragment, TITLE_HEIGHT * 2);

        assertEquals(TITLE_HEIGHT * 2, list.getPaddingBottom());
    }

    @Test
    public void aSecondLayoutAtTheSameHeightDoesNotWriteAgain() {
        final TestAlbumFragment fragment = show();
        final RecyclerView list = (RecyclerView) fragment.list;

        layOutTitle(fragment, TITLE_HEIGHT);
        assertEquals(TITLE_HEIGHT, list.getPaddingBottom());

        // A sentinel that only a second write would overwrite.
        list.setPadding(0, 0, 0, SENTINEL);

        // The device's own follow-up callback: setPanelHeight re-lays out the panel, which lays the
        // title out again — at a different position, so the framework dispatches it, but at the same
        // height. Without the guard that call writes again, and on a device it writes from inside a
        // layout pass that triggers the next one, recursing until the stack goes.
        layOutTitleAt(fragment, 100, 100 + TITLE_HEIGHT);

        assertEquals(SENTINEL, list.getPaddingBottom());
    }

    @Test
    public void aRebuiltViewStartsUnpaddedAndUnfadedAndIsPaddedAgain() {
        final TestAlbumFragment fragment = show();
        layOutTitle(fragment, TITLE_HEIGHT);
        fragment.hidden = true;

        // The pager destroys the view of a page it has scrolled away from and rebuilds it on return,
        // keeping the fragment. Both flags describe the old view and must not survive it.
        final View rebuilt = fragment.onCreateView(activity.getLayoutInflater(), null, null);
        final RecyclerView list = (RecyclerView) fragment.list;

        assertFalse(fragment.hidden);
        assertEquals(0, list.getPaddingBottom());

        layOut(rebuilt);
        layOutTitle(fragment, TITLE_HEIGHT);

        assertEquals(TITLE_HEIGHT, list.getPaddingBottom());
    }

    @Test
    public void aStaleListenerCannotStopTheRebuiltViewFromBeingPadded() {
        final TestAlbumFragment fragment = show();
        final TextView oldTitle = fragment.rootView.findViewById(R.id.title);
        layOutTitle(fragment, TITLE_HEIGHT);

        // The pager rebuilds the view. Nothing removes the first view's layout listener, so it is
        // still attached to a title the fragment no longer uses.
        final View rebuilt = fragment.onCreateView(activity.getLayoutInflater(), null, null);
        layOut(rebuilt);
        final RecyclerView list = (RecyclerView) fragment.list;

        // The rebuilt view padded itself from its own natural title height. The test can only tell a
        // stale write from the wanted one while that differs from TITLE_HEIGHT, so say so rather than
        // let a future font or width change turn this into a test that passes without checking.
        assertNotEquals(TITLE_HEIGHT, list.getPaddingBottom());

        // That listener fires with the height it has always had. It must not be able to record the
        // write against the new view, or the new view's own listener will skip its only chance.
        oldTitle.layout(0, 50, WIDTH, 50 + TITLE_HEIGHT);
        layOutTitle(fragment, TITLE_HEIGHT);

        assertEquals(TITLE_HEIGHT, list.getPaddingBottom());
    }

    @Test
    public void theFragmentBindsOnlyWhenItSaysItHasSomethingToShow() {
        final TestAlbumFragment empty = new TestAlbumFragment();
        empty.hasAlbum = false;
        empty.onCreateView(activity.getLayoutInflater(), null, null);
        assertFalse(empty.loaded);

        final TestAlbumFragment full = show();
        assertTrue(full.loaded);
    }

    /** Builds the fragment's view and lays it out, as attaching it to a window would. */
    private TestAlbumFragment show() {
        final TestAlbumFragment fragment = new TestAlbumFragment();
        final View root = fragment.onCreateView(activity.getLayoutInflater(), null, null);
        layOut(root);
        return fragment;
    }

    /**
     * Gives @id/title the height a real measurement would and lays it out, which is what the
     * fragment's layout-change listener reacts to.
     */
    private void layOutTitle(final TestAlbumFragment fragment, final int height) {
        layOutTitleAt(fragment, 0, height);
    }

    /**
     * Lays @id/title out between the given vertical bounds. Position matters as well as height:
     * View.layout only dispatches onLayoutChange when the bounds actually change, so a repeat at the
     * same position is swallowed by the framework and never reaches the fragment.
     */
    private void layOutTitleAt(final TestAlbumFragment fragment, final int top, final int bottom) {
        final TextView title = fragment.rootView.findViewById(R.id.title);
        title.layout(0, top, WIDTH, bottom);
    }

    private static void layOut(final View view) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
    }

    /** The base class with its four abstract hooks stubbed; none of them touches Shadowbox. */
    private static class TestAlbumFragment extends BaseAlbumFull {
        boolean hasAlbum = true;
        boolean loaded;

        @Override
        protected void bindActionbar() {}

        @Override
        protected String getAlbumUrl() {
            return "https://example.com/album";
        }

        @Override
        protected void openComments() {}

        @Override
        protected void loadAlbum(final String url) {
            loaded = true;
        }

        @Override
        protected boolean hasAlbumToShow() {
            return hasAlbum;
        }
    }

    public static class TestActivity extends AppCompatActivity {}
}
