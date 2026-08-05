package me.edgan.redditslide.ImgurAlbum;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/**
 * Counts how many times one Imgur load reports an error.
 *
 * <p>{@code onError()} is a per-load report — the hosts answer it with a modal "album not found"
 * dialog whose only exits are finishing the activity or leaving for the browser, and the peek view
 * answers it by throwing the album away for a web link. {@code convertToSingle} runs per *entry*,
 * once for every image in an album, so reporting from inside it put that dialog over albums that had
 * loaded and reported twice for a single image. These tests pin one report per load, and none for an
 * album that lost only some of its entries.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class AlbumUtilsErrorReportTest {

    private static final String ALBUM_URL = "https://imgur.com/a/abc123";
    private static final String GOOD_LINK = "https://i.imgur.com/abc123.png";

    private TestActivity activity;

    @Before
    public void setUp() {
        final ActivityController<TestActivity> controller =
                Robolectric.buildActivity(TestActivity.class);
        activity = controller.get();
        controller.setup();
    }

    @Test
    public void anEntryWithNoExtensionIsDroppedWithoutReporting() {
        final CountingCallback callback = new CountingCallback(activity);

        // No dot anywhere in the link, which is what an entry whose "link" field is absent looks
        // like: there is no extension to build a url from, so the entry cannot be shown.
        assertNull(callback.convertToSingle(singleImage(null)));

        // The caller decides what a dropped entry means. This one does not know yet.
        assertEquals(0, callback.errors);
    }

    @Test
    public void oneUnusableEntryDoesNotReportAnAlbumThatLoaded() {
        final CountingCallback callback = new CountingCallback(activity);

        // The album loop's shape: convert every entry, keep the ones that came back.
        final List<Image> converted = new ArrayList<>();
        for (final SingleImage entry :
                new SingleImage[] {singleImage(GOOD_LINK), singleImage(null)}) {
            final Image image = callback.convertToSingle(entry);
            if (image != null) {
                converted.add(image);
            }
        }
        callback.doWithData(converted);

        assertEquals(1, converted.size());
        assertEquals(0, callback.errors);
    }

    @Test
    public void anUnusableSingleImageReportsExactlyOnce() {
        final CountingCallback callback = new CountingCallback(activity);

        // Nothing survives the conversion, so this load really did fail — once.
        callback.doWithDataSingle(singleImage(null));

        assertEquals(1, callback.errors);
        // And the empty list it built never reached the override: the callers index element 0 and
        // build adapters over it, so being handed one would throw or show an empty album.
        assertNull(callback.delivered);
    }

    @Test
    public void anEmptyAlbumIsReportedAsNothingToBind() {
        final CountingCallback callback = new CountingCallback(activity);

        assertFalse(callback.doWithData(new ArrayList<Image>()));

        assertEquals(1, callback.errors);
        assertNull(callback.delivered);
    }

    @Test
    public void aNullAlbumIsReportedAsNothingToBind() {
        final CountingCallback callback = new CountingCallback(activity);

        assertFalse(callback.doWithData(null));

        assertEquals(1, callback.errors);
        assertNull(callback.delivered);
    }

    @Test
    public void aPopulatedAlbumIsBindable() {
        final CountingCallback callback = new CountingCallback(activity);

        final List<Image> album = new ArrayList<>();
        album.add(callback.convertToSingle(singleImage(GOOD_LINK)));

        assertTrue(callback.doWithData(album));

        assertEquals(0, callback.errors);
        assertNotNull(callback.delivered);
        assertEquals(1, callback.delivered.size());
    }

    @Test
    public void aUsableSingleImageReportsNothingAndKeepsItsUrl() {
        final CountingCallback callback = new CountingCallback(activity);

        callback.doWithDataSingle(singleImage(GOOD_LINK));

        assertEquals(0, callback.errors);
        assertNotNull(callback.delivered);
        assertEquals(1, callback.delivered.size());
        assertEquals(GOOD_LINK, callback.delivered.get(0).getImageUrl());
    }

    private static SingleImage singleImage(final @Nullable String link) {
        final SingleImage image = new SingleImage();
        image.setLink(link);
        image.setTitle("A title");
        return image;
    }

    /** The real callback with only a tally kept of what it was told. */
    private static class CountingCallback extends AlbumUtils.GetAlbumWithCallback {

        int errors;
        @Nullable List<Image> delivered;

        CountingCallback(@NonNull final Activity activity) {
            super(ALBUM_URL, activity);
        }

        @Override
        public void onError() {
            errors++;
        }

        @Override
        public boolean doWithData(final @Nullable List<Image> data) {
            // The shape every production override has: bail when super says nothing usable came
            // back, so an empty list is never indexed or bound.
            if (!super.doWithData(data)) {
                return false;
            }
            delivered = data;
            return true;
        }
    }

    public static class TestActivity extends AppCompatActivity {}
}
