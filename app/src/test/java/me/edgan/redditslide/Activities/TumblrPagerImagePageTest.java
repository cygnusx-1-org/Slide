package me.edgan.redditslide.Activities;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.view.LayoutInflater;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.ImageSource;
import org.jspecify.annotations.NullMarked;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Covers a Tumblr image page that has no url to load.
 *
 * <p>A photo can carry no original_size at all, which leaves the page with a null url. Handing that to
 * the image loader is not harmless: it reports an empty uri as a *completed* load with a null bitmap,
 * and the completion handler wraps that in ImageSource.bitmap, which throws. The throw comes back out
 * on the main thread inside the fragment's onCreateView, so paging onto such a photo took the app down
 * instead of showing an empty page.
 *
 * <p>The fragment here is deliberately unattached: the guard has to come before anything that needs an
 * activity, since the crash it prevents happens while the page is still being created.
 */
@NullMarked
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class TumblrPagerImagePageTest {

    private TestActivity activity;
    private View page;

    @Before
    public void setUp() {
        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        activity = controller.get();
        activity.setTheme(R.style.Theme_LIGHT);
        controller.setup();

        page =
                LayoutInflater.from(activity)
                        .inflate(R.layout.album_image_pager, null, false);
        // As the layout ships them, both waiting on a load that is about to not happen.
        page.findViewById(R.id.progress).setVisibility(View.VISIBLE);
        page.findViewById(R.id.size).setVisibility(View.VISIBLE);
    }

    @Test
    public void aPageWithNoUrlStopsWaitingRatherThanCrashing() {
        TumblrPager.loadImage(page, new Fragment(), null);

        assertEquals(View.GONE, page.findViewById(R.id.progress).getVisibility());
        assertEquals(View.GONE, page.findViewById(R.id.size).getVisibility());
    }

    @Test
    public void aPageWithAnEmptyUrlStopsWaitingRatherThanCrashing() {
        // The loader treats "" exactly as it treats null, so the guard has to cover both.
        TumblrPager.loadImage(page, new Fragment(), "");

        assertEquals(View.GONE, page.findViewById(R.id.progress).getVisibility());
        assertEquals(View.GONE, page.findViewById(R.id.size).getVisibility());
    }

    @Test(expected = NullPointerException.class)
    public void aNullBitmapIsWhatTheCompletionHandlerMustNeverHandOn() {
        // The mechanism the guard above exists for, pinned rather than described in a comment: the
        // image loader reports an empty uri as a completed load with a null bitmap, and this is what
        // wrapping that null does. Thrown on the main thread inside onCreateView, it takes the app
        // down — hence both the early return and the null check in the completion handler.
        ImageSource.bitmap(null);
    }

    public static class TestActivity extends AppCompatActivity {}
}
