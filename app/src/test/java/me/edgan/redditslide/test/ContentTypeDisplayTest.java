package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.ContentType.Type;
import org.junit.Test;

/**
 * The two tables that decide whether a post shows a lead image in the feed
 * ({@link ContentType#displayImage}) and whether it opens in the full-screen media viewer
 * ({@link ContentType#fullImage}).
 *
 * <p>Both are switch statements over {@link Type} with a boolean answer, and neither was asserted:
 * dropping {@code IMAGE} from one and {@code VIDEO} from the other -- an image post losing its
 * thumbnail, a YouTube link no longer opening in the viewer -- left the whole suite green.
 */
public class ContentTypeDisplayTest {

    @Test
    public void theTypesThatCarryAThumbnailDisplayAnImage() {
        assertTrue(ContentType.displayImage(Type.ALBUM));
        assertTrue(ContentType.displayImage(Type.REDDIT_GALLERY));
        assertTrue(ContentType.displayImage(Type.DEVIANTART));
        assertTrue(ContentType.displayImage(Type.IMAGE));
        assertTrue(ContentType.displayImage(Type.XKCD));
        assertTrue(ContentType.displayImage(Type.TUMBLR));
        assertTrue(ContentType.displayImage(Type.IMGUR));
        assertTrue(ContentType.displayImage(Type.SELF));
    }

    @Test
    public void aPlainLinkDoesNotDisplayAnImage() {
        assertFalse(ContentType.displayImage(Type.LINK));
        assertFalse(ContentType.displayImage(Type.EXTERNAL));
        assertFalse(ContentType.displayImage(Type.NONE));
        assertFalse(ContentType.displayImage(Type.REDDIT));
    }

    @Test
    public void theTypesThatOpenFullScreenIncludeEveryMediaKind() {
        assertTrue(ContentType.fullImage(Type.ALBUM));
        assertTrue(ContentType.fullImage(Type.REDDIT_GALLERY));
        assertTrue(ContentType.fullImage(Type.GIF));
        assertTrue(ContentType.fullImage(Type.IMAGE));
        assertTrue(ContentType.fullImage(Type.VIDEO));
        assertTrue(ContentType.fullImage(Type.STREAMABLE));
        assertTrue(ContentType.fullImage(Type.VREDDIT_DIRECT));
        assertTrue(ContentType.fullImage(Type.VREDDIT_REDIRECT));
    }

    @Test
    public void aPlainLinkDoesNotOpenFullScreen() {
        assertFalse(ContentType.fullImage(Type.LINK));
        assertFalse(ContentType.fullImage(Type.EXTERNAL));
        assertFalse(ContentType.fullImage(Type.NONE));
        assertFalse(ContentType.fullImage(Type.REDDIT));
        assertFalse(ContentType.fullImage(Type.EMBEDDED));
    }
}
