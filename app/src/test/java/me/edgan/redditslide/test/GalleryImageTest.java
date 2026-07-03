package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.edgan.redditslide.Activities.GalleryImage;
import org.junit.Test;

/**
 * Tests for the Reddit gallery-JSON parsing in {@link GalleryImage}. Nodes are built inline with
 * Jackson; the only Android dependency is {@code Log}, neutralized by {@code returnDefaultValues} in
 * the unit-test config, so no Robolectric is required.
 */
public class GalleryImageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static GalleryImage from(String json) throws Exception {
        JsonNode node = MAPPER.readTree(json);
        return new GalleryImage(node);
    }

    // ---------------------------------------------------------------------
    // Constructor: null / empty handling
    // ---------------------------------------------------------------------

    @Test
    public void nullDataYieldsEmptyButNonNullMetadata() {
        GalleryImage gi = new GalleryImage(null);
        assertThat(gi.metadata, is(notNullValue()));
        assertThat(gi.url, is(nullValue()));
        assertThat(gi.mediaId, is(nullValue()));
        assertThat(gi.width, is(0));
    }

    @Test
    public void jsonNullNodeYieldsEmptyMetadata() throws Exception {
        GalleryImage gi = from("null");
        assertThat(gi.metadata, is(notNullValue()));
        assertThat(gi.url, is(nullValue()));
    }

    // ---------------------------------------------------------------------
    // Constructor: basic field parsing
    // ---------------------------------------------------------------------

    @Test
    public void parsesMediaId() throws Exception {
        assertThat(from("{\"media_id\":\"abc123\"}").mediaId, is("abc123"));
    }

    @Test
    public void directSNodeParsesUrlAndDimensions() throws Exception {
        GalleryImage gi = from("{\"u\":\"https://i.redd.it/x.png\",\"x\":100,\"y\":200}");
        assertThat(gi.url, is("https://i.redd.it/x.png"));
        assertThat(gi.width, is(100));
        assertThat(gi.height, is(200));
        // No "s" wrapper -> no source object.
        assertThat(gi.metadata.source, is(nullValue()));
    }

    @Test
    public void sWrappedNodePopulatesSource() throws Exception {
        GalleryImage gi = from("{\"s\":{\"u\":\"https://i.redd.it/y.png\",\"x\":10,\"y\":20}}");
        assertThat(gi.url, is("https://i.redd.it/y.png"));
        assertThat(gi.width, is(10));
        assertThat(gi.metadata.source, is(notNullValue()));
        assertThat(gi.metadata.source.u, is("https://i.redd.it/y.png"));
    }

    @Test
    public void urlPrefersUThenGifThenMp4() throws Exception {
        assertThat(
                from("{\"u\":\"https://u.png\",\"gif\":\"https://g.gif\",\"mp4\":\"https://m.mp4\"}")
                        .url,
                is("https://u.png"));
        assertThat(from("{\"gif\":\"https://g.gif\",\"mp4\":\"https://m.mp4\"}").url, is("https://g.gif"));
        assertThat(from("{\"mp4\":\"https://m.mp4\"}").url, is("https://m.mp4"));
    }

    @Test
    public void unescapesHtmlEntitiesInUrl() throws Exception {
        assertThat(
                from("{\"u\":\"https://i.redd.it/x.png?a=1&amp;b=2\"}").url,
                is("https://i.redd.it/x.png?a=1&b=2"));
    }

    @Test
    public void parsesTypeAndMimeMetadata() throws Exception {
        GalleryImage gi = from("{\"e\":\"Image\",\"m\":\"image/png\"}");
        assertThat(gi.metadata.e, is("Image"));
        assertThat(gi.metadata.m, is("image/png"));
        assertFalse(gi.metadata.animated);
    }

    @Test
    public void animatedFlagSetForAnimatedImageType() throws Exception {
        assertTrue(from("{\"e\":\"AnimatedImage\"}").metadata.animated);
    }

    @Test
    public void parsesPreviewArray() throws Exception {
        GalleryImage gi =
                from(
                        "{\"p\":[{\"u\":\"https://p1.png\",\"x\":1,\"y\":2},"
                                + "{\"u\":\"https://p2.png\",\"x\":3,\"y\":4}]}");
        assertThat(gi.metadata.p.length, is(2));
        assertThat(gi.metadata.p[0].u, is("https://p1.png"));
        assertThat(gi.metadata.p[0].x, is(1));
        assertThat(gi.metadata.p[0].y, is(2));
        assertThat(gi.metadata.p[1].u, is("https://p2.png"));
    }

    // ---------------------------------------------------------------------
    // isAnimated
    // ---------------------------------------------------------------------

    @Test
    public void isAnimated_viaAnimatedImageType() throws Exception {
        assertTrue(from("{\"e\":\"AnimatedImage\"}").isAnimated());
    }

    @Test
    public void isAnimated_viaMimeContainingGif() throws Exception {
        assertTrue(from("{\"m\":\"image/gif\"}").isAnimated());
    }

    @Test
    public void isAnimated_viaSourceGif() throws Exception {
        assertTrue(from("{\"s\":{\"gif\":\"https://g.gif\"}}").isAnimated());
    }

    @Test
    public void isAnimated_viaUrlExtension() throws Exception {
        assertTrue(from("{\"u\":\"https://x.mp4\"}").isAnimated());
    }

    @Test
    public void isAnimated_falseForPlainImage() throws Exception {
        assertFalse(from("{\"s\":{\"u\":\"https://x.png\"},\"e\":\"Image\"}").isAnimated());
    }

    // ---------------------------------------------------------------------
    // getImageUrl
    // ---------------------------------------------------------------------

    @Test
    public void getImageUrl_animatedPrefersSourceMp4() throws Exception {
        GalleryImage gi =
                from(
                        "{\"s\":{\"mp4\":\"https://s.mp4\",\"gif\":\"https://s.gif\","
                                + "\"u\":\"https://s.png\"},\"e\":\"AnimatedImage\"}");
        assertThat(gi.getImageUrl(), is("https://s.mp4"));
    }

    @Test
    public void getImageUrl_animatedFallsBackToSourceGif() throws Exception {
        GalleryImage gi =
                from("{\"s\":{\"gif\":\"https://s.gif\",\"u\":\"https://s.png\"},\"e\":\"AnimatedImage\"}");
        assertThat(gi.getImageUrl(), is("https://s.gif"));
    }

    @Test
    public void getImageUrl_animatedUsesUrlExtensionWhenNoSource() throws Exception {
        // Direct node (no "s") so source is null; animated by type, url ends with .gif.
        GalleryImage gi = from("{\"u\":\"https://x.gif\",\"e\":\"AnimatedImage\"}");
        assertThat(gi.getImageUrl(), is("https://x.gif"));
    }

    @Test
    public void getImageUrl_staticPrefersSourceU() throws Exception {
        GalleryImage gi = from("{\"s\":{\"u\":\"https://s.png\"},\"e\":\"Image\"}");
        assertThat(gi.getImageUrl(), is("https://s.png"));
    }

    @Test
    public void getImageUrl_fallsBackToUrlWhenNoSource() throws Exception {
        GalleryImage gi = from("{\"u\":\"https://x.png\"}");
        assertThat(gi.getImageUrl(), is("https://x.png"));
    }
}
