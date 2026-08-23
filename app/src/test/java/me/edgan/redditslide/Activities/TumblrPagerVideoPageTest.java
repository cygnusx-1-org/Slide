package me.edgan.redditslide.Activities;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.view.LayoutInflater;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import me.edgan.redditslide.R;
import org.jspecify.annotations.NullMarked;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Covers a Tumblr video page that has no url to load — the counterpart of {@link
 * TumblrPagerImagePageTest} for the page that plays inline rather than showing a still.
 *
 * <p>A photo can carry no original_size at all, and this page reads its url null-tolerantly. Handing
 * that null to AsyncLoadGif is not harmless: the first thing its background pass does is
 * {@code formatUrl(sub[0])}, whose first statement dereferences the url. That NPE happens on the
 * loader's worker thread, where nothing catches it and the process goes down instead of the page
 * simply showing nothing.
 *
 * <p>The page is deliberately unattached: the guard has to come before anything that needs a live
 * pager, since the crash it prevents happens while the page is still being created.
 */
@NullMarked
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class TumblrPagerVideoPageTest {

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
                        .inflate(R.layout.submission_gifcard_album, null, false);
        // As the layout ships them, both waiting on a load that is about to not happen.
        page.findViewById(R.id.gifprogress).setVisibility(View.VISIBLE);
        page.findViewById(R.id.size).setVisibility(View.VISIBLE);
    }

    @Test
    public void aPageWithNoUrlStopsWaitingRatherThanStartingALoad() {
        TumblrPager.loadVideo(page, activity, null, "pics");

        // Not just a "did not throw" assertion: the NPE would land on another thread, so what pins
        // the guard is that neither view is left waiting. Take the guard out and AsyncLoadGif is
        // constructed instead, which touches neither, and both stay VISIBLE.
        assertEquals(View.GONE, page.findViewById(R.id.gifprogress).getVisibility());
        assertEquals(View.GONE, page.findViewById(R.id.size).getVisibility());
    }

    public static class TestActivity extends AppCompatActivity {}
}
