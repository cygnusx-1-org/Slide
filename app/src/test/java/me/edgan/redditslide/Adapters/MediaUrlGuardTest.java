package me.edgan.redditslide.Adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import me.edgan.redditslide.ImgurAlbum.Image;
import me.edgan.redditslide.Tumblr.Photo;
import me.edgan.redditslide.Tumblr.PhotoSize;
import org.junit.Test;

/**
 * Covers the "does this entry have anything to show" predicates the vertical album, gallery and
 * Tumblr rows guard on. Each stands in for a crash the adapters actually had: a Jackson model whose
 * field has no default, dereferenced or unboxed without a check.
 *
 * <p>Plain JUnit — the predicates are model logic with no Android in them, which is the reason they
 * were worth pulling out of the adapters in the first place.
 */
public class MediaUrlGuardTest {

    @Test
    public void photoWithNoOriginalSizeHasNoUrl() {
        final Photo photo = new Photo();

        assertFalse(photo.hasOriginalSize());
        assertNull(photo.getOriginalUrl());
    }

    @Test
    public void photoWithAnOriginalSizeButNoUrlHasNoUrl() {
        final Photo photo = new Photo();
        photo.setOriginalSize(new PhotoSize());

        // A size object with a null url is as unusable as no size at all, and the two accessors have
        // to agree about that or a caller guarding on one and reading the other still crashes.
        assertFalse(photo.hasOriginalSize());
        assertNull(photo.getOriginalUrl());
    }

    @Test
    public void photoWithAUrlReportsIt() {
        final PhotoSize size = new PhotoSize();
        size.setUrl("https://example.tumblr.com/a.gif");
        final Photo photo = new Photo();
        photo.setOriginalSize(size);

        assertTrue(photo.hasOriginalSize());
        assertEquals("https://example.tumblr.com/a.gif", photo.getOriginalUrl());
    }

    @Test
    public void imageAnimatedFlagDefaultsToFalseWhenAbsent() {
        // The flag is a Boolean field with no default, so it is null when Imgur omits it. Unboxing
        // that in getItemViewType used to crash the list while it measured; animated() is now the
        // only reader and reports false.
        final Image image = new Image();

        assertFalse(image.animated());
    }

    @Test
    public void imageAnimatedFlagIsReportedWhenPresent() {
        final Image image = new Image();

        image.setAnimated(true);
        assertTrue(image.animated());

        image.setAnimated(false);
        assertFalse(image.animated());
    }

    @Test
    public void imgurEntryNeedsBothHashAndExtensionForARealUrl() {
        // getImageUrl() concatenates the two blindly, so a partial entry yields
        // "https://i.imgur.com/nullnull" rather than null — a url that resolves to nothing and that
        // MediaView does not reject the way it rejects an empty one.
        final Image image = new Image();
        assertFalse(image.hasImageUrl());

        image.setHash("abc123");
        assertFalse(image.hasImageUrl());

        image.setExt(".mp4");
        assertTrue(image.hasImageUrl());
    }
}
