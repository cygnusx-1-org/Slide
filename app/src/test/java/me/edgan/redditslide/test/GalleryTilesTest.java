package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.util.GalleryTiles;
import me.edgan.redditslide.util.JsonUtil;
import org.junit.Test;

/**
 * Tests for the feed gallery grid's shape and image sizing in {@link GalleryTiles}.
 *
 * <p>Everything here is the context-free half: the preview-rung choice, the grid's span and cell
 * counts, and the promise that a tile's position is the gallery viewer's index. {@code
 * tileWidthPx} needs display metrics and is covered on the device instead.
 *
 * <p>Nodes are built inline with Jackson; the only Android dependency reached is {@code Log}, which
 * {@code returnDefaultValues} neutralizes, so no Robolectric is required — same as {@link
 * GalleryImageTest}.
 */
public class GalleryTilesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * One media_metadata entry with the given preview rungs.
     *
     * @param aspect width divided by height of the source, which the rungs keep
     */
    private static String mediaEntry(final String id, final double aspect, final int... widths) {
        final StringBuilder previews = new StringBuilder();
        for (int width : widths) {
            if (previews.length() > 0) {
                previews.append(',');
            }
            previews
                    .append("{\"u\":\"https://preview.redd.it/")
                    .append(id)
                    .append('-')
                    .append(width)
                    .append(".jpg\",\"x\":")
                    .append(width)
                    .append(",\"y\":")
                    .append((int) Math.round(width / aspect))
                    .append('}');
        }
        return "\""
                + id
                + "\":{\"e\":\"Image\",\"m\":\"image/jpg\","
                + "\"s\":{\"u\":\"https://preview.redd.it/"
                + id
                + ".jpg?width=1080&s=deadbeef\",\"x\":2000,\"y\":"
                + (int) Math.round(2000 / aspect)
                + "},"
                + "\"p\":["
                + previews
                + "]}";
    }

    private static GalleryImage imageWithPreviews(final double aspect, final int... widths)
            throws Exception {
        final String json =
                "{\"gallery_data\":{\"items\":[{\"media_id\":\"a\"}]},"
                        + "\"media_metadata\":{"
                        + mediaEntry("a", aspect, widths)
                        + "}}";
        return imagesOf(json).get(0);
    }

    private static List<GalleryImage> imagesOf(final String json) throws Exception {
        final JsonNode node = MAPPER.readTree(json);
        final ArrayList<GalleryImage> images = new ArrayList<>();
        JsonUtil.getGalleryData(node, images);
        return images;
    }

    // ---------------------------------------------------------------------
    // Preview rung selection
    // ---------------------------------------------------------------------

    @Test
    public void picksTheSmallestRungThatCoversTheTile() throws Exception {
        // 3:2 source, so the 640 rung is 640x427 — the first whose short side clears a 330px tile.
        final GalleryImage image = imageWithPreviews(1.5, 108, 216, 320, 640, 1080);
        assertThat(
                GalleryTiles.previewUrlFor(image, 330),
                is("https://preview.redd.it/a-640.jpg"));
    }

    @Test
    public void neverPullsTheFullPreviewForATile() throws Exception {
        // The 1080 rung (1080x720) is the only one whose short side clears a 500px tile, but it is
        // more than twice the tile wide. Four of those per card is what the feed's sizing exists to
        // avoid, so a slightly soft 640 wins — the same trade sizedResolutionUrl makes.
        final GalleryImage image = imageWithPreviews(1.5, 108, 216, 320, 640, 1080);
        assertThat(
                GalleryTiles.previewUrlFor(image, 500),
                is("https://preview.redd.it/a-640.jpg"));
    }

    @Test
    public void aWidePanoramaTakesAWiderRungThanItsWidthAloneWouldAskFor() throws Exception {
        // 5:1 source: the 216 rung is only 43px tall, so matching on width would crop a 200px tile
        // out of 43 usable pixels. Coverage is judged on the short side, which lands on 640x128.
        final GalleryImage image = imageWithPreviews(5.0, 108, 216, 320, 640, 1080);
        assertThat(
                GalleryTiles.previewUrlFor(image, 200),
                is("https://preview.redd.it/a-640.jpg"));
    }

    @Test
    public void sparseRungsFallBackRatherThanJumpToTheLargest() throws Exception {
        // Reddit sometimes serves 216 and then nothing until 1080.
        final GalleryImage image = imageWithPreviews(1.5, 216, 1080);
        assertThat(
                GalleryTiles.previewUrlFor(image, 330),
                is("https://preview.redd.it/a-216.jpg"));
    }

    @Test
    public void takesTheLargestRungWhenNoneCoversTheTile() throws Exception {
        final GalleryImage image = imageWithPreviews(1.5, 108, 216);
        assertThat(
                GalleryTiles.previewUrlFor(image, 800),
                is("https://preview.redd.it/a-216.jpg"));
    }

    @Test
    public void withNoPreviewsFallsBackToTheUnsignedSource() throws Exception {
        final GalleryImage image = imageWithPreviews(1.5);
        // preview.redd.it's signed query cannot be reused, so the source is normalized the way
        // PhotoLoader.getGalleryPreview normalizes it.
        assertThat(GalleryTiles.previewUrlFor(image, 330), is("https://i.redd.it/a.jpg"));
    }

    @Test
    public void anAnimatedEntryWithNoPreviewsHasNoTileUrl() throws Exception {
        // s carries only an mp4, which the image loader cannot decode: better an empty cell than a
        // cell that spins forever.
        final String json =
                "{\"gallery_data\":{\"items\":[{\"media_id\":\"a\"}]},"
                        + "\"media_metadata\":{\"a\":{\"e\":\"AnimatedImage\",\"m\":\"image/gif\","
                        + "\"s\":{\"mp4\":\"https://i.redd.it/a.mp4\",\"x\":600,\"y\":400}}}}";
        assertThat(GalleryTiles.previewUrlFor(imagesOf(json).get(0), 330), is(nullValue()));
    }

    // ---------------------------------------------------------------------
    // Tile index == gallery viewer index
    // ---------------------------------------------------------------------

    @Test
    public void tilePositionsMatchTheListHandedToTheViewer() throws Exception {
        // The middle item has no media_metadata entry, so getGalleryData drops it — and the viewer
        // is handed that same shortened list. A tile numbered by its position in gallery_data would
        // open the viewer on the wrong image from here on.
        final String json =
                "{\"gallery_data\":{\"items\":["
                        + "{\"media_id\":\"a\"},{\"media_id\":\"gone\"},{\"media_id\":\"c\"}],"
                        + "\"items_unused\":0},"
                        + "\"media_metadata\":{"
                        + mediaEntry("a", 1.5, 320)
                        + ","
                        + mediaEntry("c", 1.5, 320)
                        + "}}";

        final List<GalleryImage> viewerList = imagesOf(json);
        assertThat(viewerList.size(), is(2));
        assertThat(viewerList.get(1).mediaId, is("c"));

        final List<GalleryImage> tileSource = GalleryTiles.imagesFor(MAPPER.readTree(json));
        assertThat(tileSource.size(), is(viewerList.size()));
        assertThat(tileSource.get(1).mediaId, is("c"));
    }

    @Test
    public void aCrosspostReadsTheParentsGallery() throws Exception {
        final String json =
                "{\"crosspost_parent_list\":[{"
                        + "\"gallery_data\":{\"items\":[{\"media_id\":\"a\"},{\"media_id\":\"c\"}]},"
                        + "\"media_metadata\":{"
                        + mediaEntry("a", 1.5, 320)
                        + ","
                        + mediaEntry("c", 1.5, 320)
                        + "}}]}";
        final List<GalleryImage> tiles = GalleryTiles.imagesFor(MAPPER.readTree(json));
        assertThat(tiles.size(), is(2));
        assertThat(tiles.get(0).mediaId, is("a"));
    }

    // ---------------------------------------------------------------------
    // Grid shape
    // ---------------------------------------------------------------------

    @Test
    public void everyGallerySizeGetsTwoColumns() {
        // Three included: it lays out 2 + 1 rather than a row of three, so its tiles are the same
        // size as every other gallery's.
        assertThat(GalleryTiles.spanFor(2), is(2));
        assertThat(GalleryTiles.spanFor(3), is(2));
        assertThat(GalleryTiles.spanFor(4), is(2));
        assertThat(GalleryTiles.spanFor(5), is(2));
        assertThat(GalleryTiles.spanFor(9), is(2));
    }

    @Test
    public void theCardShowsAtMostFourTiles() {
        assertThat(GalleryTiles.tileCount(2), is(2));
        assertThat(GalleryTiles.tileCount(4), is(4));
        assertThat(GalleryTiles.tileCount(5), is(4));
        assertThat(GalleryTiles.tileCount(20), is(4));
    }

    @Test
    public void theOverflowBadgeCountsTheTileItSitsOn() {
        // Nine images, four tiles: three are shown whole and the fourth reads "+6" — itself plus the
        // five it hides. Tapping it opens the viewer on that fourth image.
        assertThat(GalleryTiles.overflowCount(4), is(0));
        assertThat(GalleryTiles.overflowCount(5), is(2));
        assertThat(GalleryTiles.overflowCount(9), is(6));
        assertThat(GalleryTiles.overflowIndex(), is(3));
    }
}
