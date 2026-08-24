package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.List;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.ContentType.Type;
import me.edgan.redditslide.R;
import org.junit.Test;

/**
 * The badge under a post's title ("Album", "NSFW video", "Selftext"), which {@code getContentID}
 * picks from one of two parallel tables -- one keyed by {@link Type} for ordinary posts and one for
 * NSFW ones.
 *
 * <p>Two switch statements over the same enum, written far enough apart to drift, and nothing
 * compared them: the {@code if (nsfw)} that chooses between them could be inverted -- every safe
 * post labelled "NSFW …" and every NSFW post labelled as safe -- with the whole suite still green.
 *
 * <p>The NSFW table covers fewer types than the ordinary one. {@code XKCD}, {@code DEVIANTART},
 * {@code NONE}, {@code REDDIT}, {@code SELF} and {@code STREAMABLE} have an ordinary label but no
 * NSFW one and fall through to the {@code type_link} default, so an NSFW self post is badged
 * "Link". That is a defect in the table, not in these tests, so the pairing below is asserted for
 * the types the NSFW table actually handles; when the missing ones are added, extend this list.
 */
public class ContentTypeLabelTest {

    /** The types the NSFW table has an entry for. */
    private static final List<Type> NSFW_TABLE =
            Arrays.asList(
                    Type.ALBUM,
                    Type.REDDIT_GALLERY,
                    Type.EMBEDDED,
                    Type.EXTERNAL,
                    Type.LINK,
                    Type.GIF,
                    Type.IMAGE,
                    Type.TUMBLR,
                    Type.IMGUR,
                    Type.VIDEO,
                    Type.VREDDIT_DIRECT,
                    Type.VREDDIT_REDIRECT);

    @Test
    public void anNsfwPostIsLabelledFromTheNsfwTable() {
        assertEquals(R.string.type_nsfw_album, ContentType.getContentID(Type.ALBUM, true));
        assertEquals(
                R.string.type_nsfw_gallery, ContentType.getContentID(Type.REDDIT_GALLERY, true));
        assertEquals(R.string.type_nsfw_emb, ContentType.getContentID(Type.EMBEDDED, true));
        assertEquals(R.string.type_nsfw_link, ContentType.getContentID(Type.EXTERNAL, true));
        assertEquals(R.string.type_nsfw_link, ContentType.getContentID(Type.LINK, true));
        assertEquals(R.string.type_nsfw_gif, ContentType.getContentID(Type.GIF, true));
        assertEquals(R.string.type_nsfw_img, ContentType.getContentID(Type.IMAGE, true));
        assertEquals(R.string.type_nsfw_tumblr, ContentType.getContentID(Type.TUMBLR, true));
        assertEquals(R.string.type_nsfw_imgur, ContentType.getContentID(Type.IMGUR, true));
        assertEquals(R.string.type_nsfw_video, ContentType.getContentID(Type.VIDEO, true));
        assertEquals(
                R.string.type_nsfw_video, ContentType.getContentID(Type.VREDDIT_DIRECT, true));
        assertEquals(
                R.string.type_nsfw_video, ContentType.getContentID(Type.VREDDIT_REDIRECT, true));
    }

    @Test
    public void anOrdinaryPostIsLabelledFromTheOrdinaryTable() {
        assertEquals(R.string.type_album, ContentType.getContentID(Type.ALBUM, false));
        assertEquals(R.string.type_gallery, ContentType.getContentID(Type.REDDIT_GALLERY, false));
        assertEquals(R.string.type_xkcd, ContentType.getContentID(Type.XKCD, false));
        assertEquals(R.string.type_deviantart, ContentType.getContentID(Type.DEVIANTART, false));
        assertEquals(R.string.type_emb, ContentType.getContentID(Type.EMBEDDED, false));
        assertEquals(R.string.type_external, ContentType.getContentID(Type.EXTERNAL, false));
        assertEquals(R.string.type_gif, ContentType.getContentID(Type.GIF, false));
        assertEquals(R.string.type_img, ContentType.getContentID(Type.IMAGE, false));
        assertEquals(R.string.type_imgur, ContentType.getContentID(Type.IMGUR, false));
        assertEquals(R.string.type_link, ContentType.getContentID(Type.LINK, false));
        assertEquals(R.string.type_tumblr, ContentType.getContentID(Type.TUMBLR, false));
        assertEquals(R.string.type_title_only, ContentType.getContentID(Type.NONE, false));
        assertEquals(R.string.type_reddit, ContentType.getContentID(Type.REDDIT, false));
        assertEquals(R.string.type_selftext, ContentType.getContentID(Type.SELF, false));
        assertEquals(R.string.type_streamable, ContentType.getContentID(Type.STREAMABLE, false));
        assertEquals(R.string.type_youtube, ContentType.getContentID(Type.VIDEO, false));
        assertEquals(R.string.type_vreddit, ContentType.getContentID(Type.VREDDIT_DIRECT, false));
        assertEquals(R.string.type_vreddit, ContentType.getContentID(Type.VREDDIT_REDIRECT, false));
    }

    /**
     * The two tables are chosen by one boolean, so the failure that matters is not a wrong entry
     * but the wrong table: for every type the NSFW table names, the two answers must differ.
     */
    @Test
    public void theNsfwAndOrdinaryLabelsAreNeverTheSameEntry() {
        for (Type type : NSFW_TABLE) {
            assertNotEquals(
                    "NSFW and ordinary labels must not collide for " + type,
                    ContentType.getContentID(type, true),
                    ContentType.getContentID(type, false));
        }
    }

    /** Every declared type resolves to some label rather than throwing or returning 0. */
    @Test
    public void everyTypeResolvesToALabelInBothTables() {
        for (Type type : Type.values()) {
            assertNotEquals("no ordinary label for " + type, 0, ContentType.getContentID(type, false));
            assertNotEquals("no NSFW label for " + type, 0, ContentType.getContentID(type, true));
        }
    }
}
