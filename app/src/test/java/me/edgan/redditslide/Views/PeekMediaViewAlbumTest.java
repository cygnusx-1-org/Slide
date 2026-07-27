package me.edgan.redditslide.Views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.edgan.redditslide.ForceTouch.PeekViewActivity;
import me.edgan.redditslide.ImgurAlbum.Image;
import me.edgan.redditslide.Tumblr.Photo;
import me.edgan.redditslide.Tumblr.PhotoSize;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * What the force-touch peek does with the first entry of an album, on both of its album paths.
 *
 * <p>{@link Image#getImageUrl()} concatenates hash and extension without checking either, so an
 * entry missing one yields "https://i.imgur.com/null.jpg" — a url that resolves to nothing and that
 * the peek would sit on. The album list screens entries with {@link Image#hasImageUrl()}; this pins
 * that the peek does too, rather than leaving that a property of {@code Image} alone that the peek
 * happens not to consult.
 *
 * <p>The Tumblr path has the same shape and the same hazard from the other direction: {@code
 * original_size} has no default, so {@link Photo#getOriginalUrl()} is null for a photo that carried
 * none and walking to the url without it is an NPE. Both halves are covered here because both were
 * hardened, and covering one of two identical screens is how the other stops being maintained.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class PeekMediaViewAlbumTest {

    private RecordingPeekMediaView view;

    @Before
    public void setUp() {
        final PeekViewActivity host = Robolectric.buildActivity(HostActivity.class).setup().get();
        view = new RecordingPeekMediaView(host);
    }

    @Test
    public void anEntryWithNoHashIsNotPeekedAt() {
        // Extension but no hash: getImageUrl() would hand back "https://i.imgur.com/null.png".
        final Image image = new Image();
        image.setExt(".png");

        assertTrue(callback(new ArrayList<>(Arrays.asList(image))));

        assertNull("peeked at a url built from a null hash", view.displayed);
    }

    @Test
    public void anEntryWithNoExtensionIsNotPeekedAt() {
        final Image image = new Image();
        image.setHash("abc123");

        assertTrue(callback(new ArrayList<>(Arrays.asList(image))));

        assertNull("peeked at a url built from a null extension", view.displayed);
    }

    @Test
    public void aNullFirstEntryIsNotPeekedAt() {
        // Jackson leaves a null in the list for a null element in the "images" array.
        final List<Image> images = new ArrayList<>();
        images.add(null);

        assertTrue(callback(images));

        assertNull(view.displayed);
    }

    @Test
    public void aCompleteEntryIsStillPeekedAt() {
        final Image image = new Image();
        image.setHash("abc123");
        image.setExt(".png");

        assertTrue(callback(new ArrayList<>(Arrays.asList(image))));

        assertEquals("https://i.imgur.com/abc123.png", view.displayed);
    }

    @Test
    public void aTumblrPhotoWithNoOriginalSizeIsNotPeekedAt() {
        // The field has no default, so getOriginalSize() is null for a photo whose JSON carried no
        // original_size and walking to its url is an NPE.
        assertTrue(tumblrCallback(new ArrayList<>(Arrays.asList(new Photo()))));

        assertNull("peeked at a photo with no size to read a url from", view.displayed);
    }

    @Test
    public void aNullFirstTumblrPhotoIsNotPeekedAt() {
        // Jackson leaves a null in the list for a null element in the "photos" array, exactly as it
        // does for Imgur's "images".
        final List<Photo> photos = new ArrayList<>();
        photos.add(null);

        assertTrue(tumblrCallback(photos));

        assertNull(view.displayed);
    }

    @Test
    public void aTumblrPhotoWithASizeButNoUrlIsNotPeekedAt() {
        final Photo photo = new Photo();
        photo.setOriginalSize(new PhotoSize());

        assertTrue(tumblrCallback(new ArrayList<>(Arrays.asList(photo))));

        assertNull(view.displayed);
    }

    @Test
    public void aCompleteTumblrPhotoIsStillPeekedAt() {
        final PhotoSize size = new PhotoSize();
        size.setUrl("https://64.media.tumblr.com/abc123/photo_1280.jpg");
        final Photo photo = new Photo();
        photo.setOriginalSize(size);

        assertTrue(tumblrCallback(new ArrayList<>(Arrays.asList(photo))));

        assertEquals("https://64.media.tumblr.com/abc123/photo_1280.jpg", view.displayed);
    }

    /** Drives the loader's callback with a hand-built album and no fetch behind it. */
    private boolean callback(final List<Image> images) {
        return view.albumCallback("https://imgur.com/a/abc123").doWithData(images);
    }

    /** The same, for the Tumblr half of the peek. */
    private boolean tumblrCallback(final List<Photo> photos) {
        return view.tumblrCallback("https://example.tumblr.com/post/1").doWithData(photos);
    }

    /** Records what the peek was asked to show instead of loading it. */
    private static class RecordingPeekMediaView extends PeekMediaView {
        String displayed;

        RecordingPeekMediaView(final Context context) {
            super(context);
        }

        @Override
        public void displayImage(final String url) {
            displayed = url;
        }
    }

    public static class HostActivity extends PeekViewActivity {}
}
