package me.edgan.redditslide.Adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Tumblr.Photo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Covers how a Tumblr GIF row wires into its {@code MediaRowLoadState} — the transitions the state
 * class is tested for in isolation, checked here at the two points the adapter drives them from:
 * recycling a row, and a load that fails.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class TumblrViewGifRowTest {

    private static final String URL = "https://example.tumblr.com/a.gif";

    @Test
    public void recyclingLetsTheRowLoadAgain() {
        final TestActivity activity = createActivity();
        final TumblrView.GifViewHolder holder = inflateGifRow(activity);
        // The adapter is here only to reach onViewRecycled; it never consults its photo list, so an
        // empty one is not a missing fixture.
        final TumblrView adapter = new TumblrView(activity, new ArrayList<>(), "pics", "A title");

        holder.loadState.loadStarted(URL);
        assertFalse(holder.loadState.shouldLoad(URL));

        // Recycling drops the drawable, so whatever was showing is gone and the row must reload.
        adapter.onViewRecycled(holder);

        assertTrue(holder.loadState.isEmpty());
        assertTrue(holder.loadState.shouldLoad(URL));
    }

    @Test
    public void aFailedLoadCollapsesTheRowAndAllowsOneRetry() {
        final TestActivity activity = createActivity();
        final TumblrView.GifViewHolder holder = inflateGifRow(activity);

        holder.loadState.loadStarted(URL);
        holder.gifDisplay.setAspectRatio(9d / 16d);
        holder.gifDisplay.setVisibility(View.VISIBLE);

        TumblrView.onGifLoadFailed(holder, URL, "Failed to load GIF");

        // The reserved height goes back, so the row is its caption rather than an empty box.
        assertEquals(View.GONE, holder.gifDisplay.getVisibility());
        assertEquals(View.VISIBLE, holder.gifCaption.getVisibility());
        assertTrue(holder.loadState.shouldLoad(URL));
    }

    @Test
    public void aSecondFailureStopsRetrying() {
        final TestActivity activity = createActivity();
        final TumblrView.GifViewHolder holder = inflateGifRow(activity);

        holder.loadState.loadStarted(URL);
        TumblrView.onGifLoadFailed(holder, URL, "Failed to load GIF");
        holder.loadState.loadStarted(URL);
        TumblrView.onGifLoadFailed(holder, URL, "Failed to load GIF");

        assertFalse(holder.loadState.shouldLoad(URL));
    }

    @Test
    public void aHostWithNoToolbarHasNoSpacerRowAndMapsPositionsDirectly() {
        // Shadowbox: BaseAlbumFull pads the RecyclerView by its info panel's height, so the list is
        // photo rows and nothing else. Position is the photo index.
        final TestActivity activity = createActivity();
        final TumblrView adapter = new TumblrView(activity, twoPhotos(), "pics", "A title");
        assertFalse(adapter.hasToolbarSpacer);

        assertEquals(2, adapter.getItemCount());
        assertEquals(TumblrView.VIEW_TYPE_IMAGE, adapter.getItemViewType(0));
        assertEquals(TumblrView.VIEW_TYPE_IMAGE, adapter.getItemViewType(1));
    }

    @Test
    public void aHostWithAToolbarLeadsWithASpacerAndShiftsPositionsByOne() {
        final TestActivity activity = createActivity();
        final View toolbar = new View(activity);
        toolbar.setId(R.id.toolbar);
        activity.setContentView(toolbar);

        final TumblrView adapter = new TumblrView(activity, twoPhotos(), "pics", "A title");
        assertTrue(adapter.hasToolbarSpacer);

        assertEquals(3, adapter.getItemCount());
        assertEquals(TumblrView.VIEW_TYPE_SPACER, adapter.getItemViewType(0));
        assertEquals(TumblrView.VIEW_TYPE_IMAGE, adapter.getItemViewType(1));
        assertEquals(TumblrView.VIEW_TYPE_IMAGE, adapter.getItemViewType(2));
    }

    @Test
    public void aNullPhotoClassifiesAsAStillRatherThanCrashing() {
        // Gson leaves a null in the list for a null element in the photos array. It has to keep its
        // slot — every later row would otherwise land on the wrong photo — and it must not reach the
        // GIF branch, which has a url to load and a caption to draw.
        final TestActivity activity = createActivity();
        final ArrayList<Photo> photos = new ArrayList<>();
        photos.add(null);
        photos.add(new Photo());

        final TumblrView adapter = new TumblrView(activity, photos, "pics", "A title");

        assertEquals(2, adapter.getItemCount());
        assertEquals(TumblrView.VIEW_TYPE_IMAGE, adapter.getItemViewType(0));
        assertEquals(TumblrView.VIEW_TYPE_IMAGE, adapter.getItemViewType(1));
    }

    /** Two photos with no original_size, so both classify as stills rather than GIFs. */
    private static ArrayList<Photo> twoPhotos() {
        final ArrayList<Photo> photos = new ArrayList<>();
        photos.add(new Photo());
        photos.add(new Photo());
        return photos;
    }

    private static TumblrView.GifViewHolder inflateGifRow(final TestActivity activity) {
        final View row =
                LayoutInflater.from(activity)
                        .inflate(
                                R.layout.list_item_tumblr_gif, new FrameLayout(activity), false);
        return new TumblrView.GifViewHolder(row);
    }

    private static TestActivity createActivity() {
        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        final TestActivity activity = controller.get();
        activity.setTheme(R.style.Theme_LIGHT);
        controller.setup();
        return activity;
    }

    public static class TestActivity extends AppCompatActivity {}
}
