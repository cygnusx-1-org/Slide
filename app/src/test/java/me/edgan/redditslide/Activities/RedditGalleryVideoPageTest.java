package me.edgan.redditslide.Activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.view.LayoutInflater;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.edgan.redditslide.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Covers a Reddit gallery gif page whose entry has no url.
 *
 * <p>A removed or failed gallery entry arrives with media_metadata carrying no "s" node, which
 * leaves every branch of {@link GalleryImage#getImageUrl()} returning null, and nothing routes such
 * an entry away from this page. Handing that null to AsyncLoadGif is not harmless: the first thing
 * its background pass does is {@code formatUrl(sub[0])}, whose first statement dereferences the url,
 * on a worker thread where nothing catches the NPE.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class RedditGalleryVideoPageTest {

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
    public void anEntryWithNoMediaNodeReallyHasNoUrl() {
        // The premise the guard rests on, pinned rather than described: media_metadata with no "s"
        // gives the page a null to work with.
        final ObjectNode metadata = JsonNodeFactory.instance.objectNode();
        metadata.put("status", "failed");

        assertNull(new GalleryImage(metadata).getImageUrl());
    }

    @Test
    public void aPageWithNoUrlStopsWaitingRatherThanStartingALoad() {
        RedditGallery.loadVideo(page, activity, null, "pics", "a title");

        // Not just a "did not throw" assertion: the NPE would land on another thread, so what pins
        // the guard is that neither view is left waiting. Take the guard out and AsyncLoadGif is
        // constructed instead, which touches neither, and both stay VISIBLE.
        assertEquals(View.GONE, page.findViewById(R.id.gifprogress).getVisibility());
        assertEquals(View.GONE, page.findViewById(R.id.size).getVisibility());
    }

    public static class TestActivity extends AppCompatActivity {}
}
