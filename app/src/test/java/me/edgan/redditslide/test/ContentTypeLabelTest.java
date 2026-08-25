package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

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
 * <p>Both tables must name the same types: {@code XKCD}, {@code DEVIANTART}, {@code NONE},
 * {@code REDDIT}, {@code SELF} and {@code STREAMABLE} once had an ordinary label and no NSFW one,
 * so an NSFW self post fell through to the {@code type_link} default and was badged "Link".
 * {@code SPOILER} was named by neither and both answered "Link" for it; every type in the enum now
 * has its own entry on both sides, so the tables are complete as well as parallel.
 */
public class ContentTypeLabelTest {

    @Test
    public void anNsfwPostIsLabelledFromTheNsfwTable() {
        assertEquals(R.string.type_nsfw_album, ContentType.getContentID(Type.ALBUM, true));
        assertEquals(
                R.string.type_nsfw_gallery, ContentType.getContentID(Type.REDDIT_GALLERY, true));
        assertEquals(R.string.type_nsfw_xkcd, ContentType.getContentID(Type.XKCD, true));
        assertEquals(
                R.string.type_nsfw_deviantart, ContentType.getContentID(Type.DEVIANTART, true));
        assertEquals(R.string.type_nsfw_emb, ContentType.getContentID(Type.EMBEDDED, true));
        assertEquals(R.string.type_nsfw_link, ContentType.getContentID(Type.EXTERNAL, true));
        assertEquals(R.string.type_nsfw_link, ContentType.getContentID(Type.LINK, true));
        assertEquals(R.string.type_nsfw_gif, ContentType.getContentID(Type.GIF, true));
        assertEquals(R.string.type_nsfw_img, ContentType.getContentID(Type.IMAGE, true));
        assertEquals(R.string.type_nsfw_tumblr, ContentType.getContentID(Type.TUMBLR, true));
        assertEquals(R.string.type_nsfw_imgur, ContentType.getContentID(Type.IMGUR, true));
        assertEquals(R.string.type_nsfw_title_only, ContentType.getContentID(Type.NONE, true));
        assertEquals(R.string.type_nsfw_reddit, ContentType.getContentID(Type.REDDIT, true));
        assertEquals(R.string.type_nsfw_selftext, ContentType.getContentID(Type.SELF, true));
        assertEquals(R.string.type_nsfw_spoiler, ContentType.getContentID(Type.SPOILER, true));
        assertEquals(
                R.string.type_nsfw_streamable, ContentType.getContentID(Type.STREAMABLE, true));
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
        assertEquals(R.string.type_spoiler, ContentType.getContentID(Type.SPOILER, false));
        assertEquals(R.string.type_streamable, ContentType.getContentID(Type.STREAMABLE, false));
        assertEquals(R.string.type_youtube, ContentType.getContentID(Type.VIDEO, false));
        assertEquals(R.string.type_vreddit, ContentType.getContentID(Type.VREDDIT_DIRECT, false));
        assertEquals(R.string.type_vreddit, ContentType.getContentID(Type.VREDDIT_REDIRECT, false));
    }

    /**
     * The two tables are chosen by one boolean, so the failure that matters is not a wrong entry
     * but the wrong table: the two answers must differ for every type. This once had to be scoped
     * to the types the NSFW table happened to name; both tables are complete now, so it runs over
     * the whole enum and a type added to one table alone fails here.
     */
    @Test
    public void theNsfwAndOrdinaryLabelsAreNeverTheSameEntry() {
        for (Type type : Type.values()) {
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
