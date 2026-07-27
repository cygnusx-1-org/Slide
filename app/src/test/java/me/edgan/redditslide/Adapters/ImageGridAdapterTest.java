package me.edgan.redditslide.Adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.ImgurAlbum.Image;
import me.edgan.redditslide.Tumblr.Photo;
import me.edgan.redditslide.Tumblr.PhotoSize;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Pins which of a Tumblr photo's sizes the grids ask for, and the API ordering that makes it the
 * right one.
 *
 * <p>Two adapters used to disagree — the vertical album took the last alt_sizes entry, the pager grid
 * and the force-touch peek took the first — and consolidating them settled on the last for everyone.
 * That is only correct while Tumblr returns the array largest first, so the fixture is a real response
 * and the ordering is asserted rather than assumed.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class ImageGridAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void tumblrReturnsAltSizesLargestFirst() throws Exception {
        final Photo photo = photoFixture();
        final List<PhotoSize> sizes = photo.getAltSizes();

        // Captured from api.tumblr.com. If Tumblr ever reverses this, the grids below start asking
        // for the largest file per cell and this test is where that shows up.
        assertEquals(
                Arrays.asList(900, 640, 540, 500, 400, 250, 100, 75),
                sizes.stream().map(PhotoSize::getWidth).collect(Collectors.toList()));

        // The first entry is not merely the biggest, it is the original: same url, byte for byte.
        assertEquals(photo.getOriginalSize().getUrl(), sizes.get(0).getUrl());
    }

    @Test
    public void theGridAsksForTheSmallestSizeNotTheOriginal() throws Exception {
        final Photo photo = photoFixture();
        final List<Photo> album = new ArrayList<>();
        album.add(photo);

        final ImageGridAdapter adapter = new ImageGridAdapter(context(), album, true);

        assertEquals(1, adapter.getCount());
        final String chosen = adapter.getItem(0);
        assertEquals(photo.getAltSizes().get(photo.getAltSizes().size() - 1).getUrl(), chosen);
        // 75px, the smallest of the eight — and emphatically not the 900px original.
        assertTrue(chosen.endsWith("_75sq.jpg"));
        assertNotEquals(photo.getOriginalSize().getUrl(), chosen);
    }

    @Test
    public void aNullPhotoContributesANullRatherThanCrashing() {
        // Jackson leaves a null in the list for a null element in the photos array, and the grid
        // maps position to album index, so the slot has to survive even though nothing can be drawn
        // in it.
        final List<Photo> album = new ArrayList<>();
        album.add(null);
        album.add(photoWithUrl("https://64.media.tumblr.com/second.jpg"));

        final ImageGridAdapter adapter = new ImageGridAdapter(context(), album, true);

        assertEquals(2, adapter.getCount());
        assertNull(adapter.getItem(0));
        assertEquals("https://64.media.tumblr.com/second.jpg", adapter.getItem(1));
    }

    @Test
    public void aPhotoWithNoSizesContributesANullRatherThanBeingSkipped() {
        // Callers use the grid position as an album index, so a photo with nothing to show still has
        // to occupy its slot or every later thumbnail lands on the wrong item.
        final List<Photo> album = new ArrayList<>();
        album.add(new Photo());
        album.add(new Photo());

        final ImageGridAdapter adapter = new ImageGridAdapter(context(), album, true);

        assertEquals(2, adapter.getCount());
        assertNull(adapter.getItem(0));
        assertNull(adapter.getItem(1));
    }

    @Test
    public void aNullAltSizeStepsUpOneSizeRatherThanCrashing() {
        // alt_sizes carries a null for a null array element exactly as photos does, and the entry
        // the grid wants is the one it indexes blindly.
        final List<PhotoSize> altSizes = new ArrayList<>();
        altSizes.add(sized("https://64.media.tumblr.com/big.jpg"));
        altSizes.add(sized("https://64.media.tumblr.com/small.jpg"));
        altSizes.add(null);

        final List<Photo> album = new ArrayList<>();
        album.add(photoWith("https://64.media.tumblr.com/original.jpg", altSizes));

        final ImageGridAdapter adapter = new ImageGridAdapter(context(), album, true);

        // The next size up, not the 900px original this method exists to keep out of a grid cell.
        assertEquals("https://64.media.tumblr.com/small.jpg", adapter.getItem(0));
    }

    @Test
    public void anAltSizeWithNoUrlStepsUpOneSize() {
        final List<PhotoSize> altSizes = new ArrayList<>();
        altSizes.add(sized("https://64.media.tumblr.com/big.jpg"));
        altSizes.add(sized("https://64.media.tumblr.com/small.jpg"));
        altSizes.add(new PhotoSize());

        final List<Photo> album = new ArrayList<>();
        album.add(photoWith("https://64.media.tumblr.com/original.jpg", altSizes));

        final ImageGridAdapter adapter = new ImageGridAdapter(context(), album, true);

        assertEquals("https://64.media.tumblr.com/small.jpg", adapter.getItem(0));
    }

    @Test
    public void onlyAnAltSizesArrayWithNothingUsableFallsBackToTheOriginal() {
        final List<PhotoSize> altSizes = new ArrayList<>();
        altSizes.add(null);
        altSizes.add(new PhotoSize());

        final List<Photo> album = new ArrayList<>();
        album.add(photoWith("https://64.media.tumblr.com/original.jpg", altSizes));

        final ImageGridAdapter adapter = new ImageGridAdapter(context(), album, true);

        assertEquals("https://64.media.tumblr.com/original.jpg", adapter.getItem(0));
    }

    @Test
    public void anIndexOffTheEndOfTheSizesHasNoUrl() {
        // TumblrPager asks for size/2, so it depends on the same bounds check the grid does.
        assertNull(ImageGridAdapter.sizeUrl(null, 0));
        assertNull(ImageGridAdapter.sizeUrl(new ArrayList<PhotoSize>(), 0));
        assertNull(ImageGridAdapter.sizeUrl(Arrays.asList(sized("https://x/a.jpg")), 1));
        assertNull(ImageGridAdapter.sizeUrl(Arrays.asList(sized("https://x/a.jpg")), -1));
        assertEquals(
                "https://x/a.jpg",
                ImageGridAdapter.sizeUrl(Arrays.asList(sized("https://x/a.jpg")), 0));
    }

    @Test
    public void aPhotoWithOnlyAnOriginalFallsBackToIt() {
        final List<Photo> album = new ArrayList<>();
        album.add(photoWithUrl("https://64.media.tumblr.com/only-one.jpg"));

        final ImageGridAdapter adapter = new ImageGridAdapter(context(), album, true);

        assertEquals("https://64.media.tumblr.com/only-one.jpg", adapter.getItem(0));
    }

    @Test
    public void aNullImgurEntryContributesANullRatherThanCrashing() {
        // Same shape as the Tumblr case above: Jackson leaves a null in the list for a null element
        // in the "images" array, and the cells are addressed by album index.
        final List<Image> album = new ArrayList<>();
        album.add(null);
        album.add(imgurImage("abc123", ".png"));

        final ImageGridAdapter adapter = new ImageGridAdapter(context(), album);

        assertEquals(2, adapter.getCount());
        assertNull(adapter.getItem(0));
        assertNotNull(adapter.getItem(1));
    }

    @Test
    public void aNullGalleryEntryContributesANullRatherThanCrashing() {
        final List<GalleryImage> gallery = new ArrayList<>();
        gallery.add(null);

        final ImageGridAdapter adapter = new ImageGridAdapter(context(), true, gallery);

        assertEquals(1, adapter.getCount());
        assertNull(adapter.getItem(0));
    }

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static Image imgurImage(final String hash, final String ext) {
        final Image image = new Image();
        image.setHash(hash);
        image.setExt(ext);
        return image;
    }

    private static PhotoSize sized(final String url) {
        final PhotoSize size = new PhotoSize();
        size.setUrl(url);
        return size;
    }

    /** A photo with an original_size and no alt_sizes, as Tumblr's older posts return. */
    private static Photo photoWithUrl(final String url) {
        final Photo photo = new Photo();
        photo.setOriginalSize(sized(url));
        return photo;
    }

    private static Photo photoWith(final String originalUrl, final List<PhotoSize> altSizes) {
        final Photo photo = photoWithUrl(originalUrl);
        photo.setAltSizes(altSizes);
        return photo;
    }

    /** One photo straight out of an api.tumblr.com photo post. */
    private static Photo photoFixture() throws Exception {
        try (InputStream input =
                ImageGridAdapterTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/tumblrPhotoAltSizes.json")) {
            assertNotNull(input);
            return MAPPER.readValue(input, Photo.class);
        }
    }
}
