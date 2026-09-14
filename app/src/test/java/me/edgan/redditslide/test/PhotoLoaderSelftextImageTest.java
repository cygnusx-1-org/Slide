package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import me.edgan.redditslide.util.PhotoLoader;
import org.junit.Test;

/**
 * The lead image of a selftext post that inlines a reddit-hosted picture in its body — the one case
 * reddit publishes no {@code preview} node for, so {@code media_metadata} is the only description
 * of the picture there is.
 *
 * <p>The fixtures are six real r/test posts, one per body shape (text/image, image/text,
 * text/image/text, text/image/image, image/image/text, image/text/image), so the ordering and
 * sizing rules are pinned against the json reddit actually serves rather than a hand-written
 * sketch. No Android APIs are involved, so no Robolectric.
 */
public class PhotoLoaderSelftextImageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** How the feed card asks when the user has not limited image resolution. */
    private static final int UNCAPPED = Integer.MAX_VALUE;

    // ---------------------------------------------------------------------
    // Which image: the body's first, not the map's
    // ---------------------------------------------------------------------

    @Test
    public void picksTheImageTheBodyOpensWith() throws Exception {
        // t3_1wfusrn ("Image Image Text") is the one post of the six whose media_metadata disagrees
        // with its body: the map opens with u80t999ebfph1 (the 2948x2020 jpg) while the body opens
        // with g7lc0ajdbfph1 (the 640x657 png). Reddit cuts that post's own thumbnail from the png,
        // which is the answer to match.
        final JsonNode data = fixture("selftext_image_image_text");
        assertThat(
                data.path("media_metadata").fieldNames().next(), is("u80t999ebfph1"));
        assertThat(data.path("thumbnail").asText().contains("g7lc0ajdbfph1"), is(true));

        final PhotoLoader.GalleryPreview preview =
                PhotoLoader.getSelftextImagePreview(data, UNCAPPED);
        assertNotNull(preview);
        assertThat(preview.url.contains("g7lc0ajdbfph1"), is(true));
        assertThat(preview.width, is(640));
        assertThat(preview.height, is(657));
    }

    @Test
    public void everyShapeResolvesToItsOwnFirstImage() throws Exception {
        assertFirstImage("selftext_text_image", "of804o39afph1", 1344, 2992);
        assertFirstImage("selftext_image_text", "n9adtvxlafph1", 2948, 2020);
        assertFirstImage("selftext_text_image_text", "6r72e2gvafph1", 2948, 2020);
        assertFirstImage("selftext_text_image_image", "3ttqo837bfph1", 2948, 2020);
        assertFirstImage("selftext_image_image_text", "g7lc0ajdbfph1", 640, 657);
        assertFirstImage("selftext_image_text_image", "odcdtgjmbfph1", 2948, 2020);
    }

    @Test
    public void anImageTheBodyNeverNamesIsAFallback() throws Exception {
        // An entry no longer referenced by the body (an edit that removed the link) sorts last, but
        // is still better than no lead image at all when it is the only one left.
        final JsonNode data =
                node(
                        "{\"selftext_html\":\"<p>no links here</p>\",\"media_metadata\":{"
                                + entry("orphan", 400, 300)
                                + "}}");
        final PhotoLoader.GalleryPreview preview =
                PhotoLoader.getSelftextImagePreview(data, UNCAPPED);
        assertNotNull(preview);
        assertThat(preview.url.contains("orphan"), is(true));
    }

    @Test
    public void aProcessingPlaceholderStillNamesItsImage() throws Exception {
        // An ![img](id) reference reddit has not finished processing arrives as "Processing img
        // id..." rather than a link, and the id in it is what orders the entry.
        final JsonNode data =
                node(
                        "{\"selftext_html\":\"<p><em>Processing img second...</em></p>\","
                                + "\"media_metadata\":{"
                                + entry("first", 100, 100)
                                + ","
                                + entry("second", 200, 200)
                                + "}}");
        final PhotoLoader.GalleryPreview preview =
                PhotoLoader.getSelftextImagePreview(data, UNCAPPED);
        assertNotNull(preview);
        assertThat(preview.url.contains("second"), is(true));
    }

    // ---------------------------------------------------------------------
    // Which url: source uncapped, the covering rung when capped
    // ---------------------------------------------------------------------

    @Test
    public void uncappedTakesTheSourceWithItsAmpersandsUnescaped() throws Exception {
        final PhotoLoader.GalleryPreview preview =
                PhotoLoader.getSelftextImagePreview(fixture("selftext_text_image"), UNCAPPED);
        assertNotNull(preview);
        // The CDN answers 403 to the html-escaped form of the signed query.
        assertThat(preview.url.contains("&amp;"), is(false));
        assertThat(preview.url.contains("&format=png"), is(true));
        assertThat(preview.url.contains("width=1344"), is(true));
        assertThat(preview.width, is(1344));
        assertThat(preview.height, is(2992));
    }

    @Test
    public void cappedTakesTheSmallestRungThatCoversTheCap() throws Exception {
        // t3_1wfuohp's rungs are 108, 216, 320, 640, 960 and 1080 wide.
        final JsonNode data = fixture("selftext_text_image");
        assertThat(widthOf(data, 320), is(320));
        assertThat(widthOf(data, 200), is(216));
        assertThat(widthOf(data, 108), is(108));
    }

    @Test
    public void aCapAboveEveryRungTakesTheLargest() throws Exception {
        assertThat(widthOf(fixture("selftext_text_image"), 4000), is(1080));
    }

    @Test
    public void cappedFallsBackToTheSourceWhenThereAreNoRungs() throws Exception {
        final JsonNode data =
                node(
                        "{\"selftext_html\":\"<p>only</p>\",\"media_metadata\":{\"only\":{"
                                + "\"status\":\"valid\",\"e\":\"Image\",\"s\":{\"x\":500,\"y\":400,"
                                + "\"u\":\"https://preview.redd.it/only.png?s=1\"}}}}");
        final PhotoLoader.GalleryPreview preview = PhotoLoader.getSelftextImagePreview(data, 320);
        assertNotNull(preview);
        assertThat(preview.url, is("https://preview.redd.it/only.png?s=1"));
        assertThat(preview.width, is(500));
    }

    // ---------------------------------------------------------------------
    // What is not a lead image
    // ---------------------------------------------------------------------

    @Test
    public void emotesAreNeverTheLeadImage() throws Exception {
        // An emote is part of the text, and its key is what a body carries for it, so it would
        // otherwise sort first.
        final JsonNode data =
                node(
                        "{\"selftext_html\":\"<p>emote|t5_2qh1i|2180 then the picture"
                                + " real</p>\",\"media_metadata\":{"
                                + "\"emote|t5_2qh1i|2180\":{\"status\":\"valid\",\"e\":\"Image\","
                                + "\"s\":{\"x\":60,\"y\":60,\"u\":\"https://preview.redd.it/e.png\"}},"
                                + entry("real", 800, 600)
                                + "}}");
        final PhotoLoader.GalleryPreview preview =
                PhotoLoader.getSelftextImagePreview(data, UNCAPPED);
        assertNotNull(preview);
        assertThat(preview.url.contains("real"), is(true));
    }

    @Test
    public void failedAndAnimatedEntriesAreSkipped() throws Exception {
        final JsonNode data =
                node(
                        "{\"selftext_html\":\"<p>gone moving still</p>\",\"media_metadata\":{"
                                + "\"gone\":{\"status\":\"failed\",\"e\":\"Image\",\"s\":{\"x\":1,"
                                + "\"y\":1,\"u\":\"https://preview.redd.it/gone.png\"}},"
                                + "\"moving\":{\"status\":\"valid\",\"e\":\"AnimatedImage\","
                                + "\"s\":{\"x\":2,\"y\":2,\"gif\":\"https://preview.redd.it/m.gif\"}},"
                                + entry("still", 300, 200)
                                + "}}");
        final PhotoLoader.GalleryPreview preview =
                PhotoLoader.getSelftextImagePreview(data, UNCAPPED);
        assertNotNull(preview);
        assertThat(preview.url.contains("still"), is(true));
    }

    @Test
    public void aPostWithNothingInlinedHasNoLeadImage() throws Exception {
        assertThat(PhotoLoader.getSelftextImagePreview(null, UNCAPPED), is(nullValue()));
        assertThat(
                PhotoLoader.getSelftextImagePreview(
                        node("{\"selftext_html\":\"<p>words</p>\"}"), UNCAPPED),
                is(nullValue()));
        assertThat(
                PhotoLoader.getSelftextImagePreview(
                        node("{\"media_metadata\":{}}"), UNCAPPED),
                is(nullValue()));
    }

    // ---------------------------------------------------------------------

    private static void assertFirstImage(String name, String id, int width, int height)
            throws Exception {
        final PhotoLoader.GalleryPreview preview =
                PhotoLoader.getSelftextImagePreview(fixture(name), UNCAPPED);
        assertNotNull(name, preview);
        assertThat(name, preview.url.contains(id), is(true));
        assertThat(name, preview.width, is(width));
        assertThat(name, preview.height, is(height));
    }

    private static int widthOf(JsonNode data, int maxWidth) {
        final PhotoLoader.GalleryPreview preview =
                PhotoLoader.getSelftextImagePreview(data, maxWidth);
        assertNotNull(preview);
        return preview.width;
    }

    /** A minimal valid still entry, sized {@code w}x{@code h} with no smaller rungs. */
    private static String entry(String id, int w, int h) {
        return "\""
                + id
                + "\":{\"status\":\"valid\",\"e\":\"Image\",\"s\":{\"x\":"
                + w
                + ",\"y\":"
                + h
                + ",\"u\":\"https://preview.redd.it/"
                + id
                + ".png?s=1\"}}";
    }

    private static JsonNode node(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    private static JsonNode fixture(String name) throws Exception {
        try (InputStream input =
                PhotoLoaderSelftextImageTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/" + name + ".json")) {
            assertNotNull(name, input);
            return MAPPER.readTree(input);
        }
    }
}
