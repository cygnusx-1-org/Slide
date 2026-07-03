package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.util.JsonUtil;
import org.junit.Test;

/** Tests for {@link JsonUtil}: reddit-link detection, preview-host rewriting, and gallery parsing. */
public class JsonUtilTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode node(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    // ---------------------------------------------------------------------
    // linksToReddit
    // ---------------------------------------------------------------------

    @Test
    public void linksToReddit_exactDomain() throws Exception {
        assertTrue(JsonUtil.linksToReddit(node("{\"domain\":\"reddit.com\"}")));
    }

    @Test
    public void linksToReddit_subdomain() throws Exception {
        assertTrue(JsonUtil.linksToReddit(node("{\"domain\":\"old.reddit.com\"}")));
    }

    @Test
    public void linksToReddit_reddItIsNotReddit() throws Exception {
        assertFalse(JsonUtil.linksToReddit(node("{\"domain\":\"i.redd.it\"}")));
    }

    @Test
    public void linksToReddit_lookalikeDomainRejected() throws Exception {
        assertFalse(JsonUtil.linksToReddit(node("{\"domain\":\"myreddit.com\"}")));
    }

    @Test
    public void linksToReddit_missingDomainIsFalse() throws Exception {
        assertFalse(JsonUtil.linksToReddit(node("{}")));
    }

    @Test
    public void linksToReddit_nullDomainIsFalse() throws Exception {
        assertFalse(JsonUtil.linksToReddit(node("{\"domain\":null}")));
    }

    @Test
    public void linksToReddit_nullNodeIsFalse() {
        assertFalse(JsonUtil.linksToReddit(null));
    }

    // ---------------------------------------------------------------------
    // normalizeRedditPreviewHost
    // ---------------------------------------------------------------------

    @Test
    public void normalizeHost_rewritesWhenRedditLinked() {
        assertThat(
                JsonUtil.normalizeRedditPreviewHost(
                        "https://external-preview.redd.it/abc.png", true),
                is("https://preview.redd.it/abc.png"));
    }

    @Test
    public void normalizeHost_leftAloneWhenNotRedditLinked() {
        String url = "https://external-preview.redd.it/abc.png";
        assertThat(JsonUtil.normalizeRedditPreviewHost(url, false), is(url));
    }

    @Test
    public void normalizeHost_nonExternalPreviewUrlUnchanged() {
        String url = "https://news.example.com/a.png";
        assertThat(JsonUtil.normalizeRedditPreviewHost(url, true), is(url));
    }

    @Test
    public void normalizeHost_nullUrlPassesThrough() {
        assertThat(JsonUtil.normalizeRedditPreviewHost(null, true), is(nullValue()));
    }

    // ---------------------------------------------------------------------
    // getGalleryData
    // ---------------------------------------------------------------------

    @Test
    public void getGalleryData_parsesInItemOrderSkippingBadEntries() throws Exception {
        JsonNode data = node(TestUtils.getResource("gallery/gallery_submission_data.json"));
        ArrayList<GalleryImage> images = new ArrayList<>();
        JsonUtil.getGalleryData(data, images);

        // "nosource" (no s node) and "notinmetadata" (absent from media_metadata) are skipped.
        assertThat(images.size(), is(2));

        GalleryImage first = images.get(0);
        assertThat(first.mediaId, is("img1"));
        assertThat(first.caption, is("First image"));
        assertFalse(first.isAnimated());
        // The &amp; in the source URL is unescaped.
        assertThat(
                first.getImageUrl(), is("https://preview.redd.it/img1.png?width=100&s=abc"));
        assertThat(first.metadata.p.length, is(1));

        GalleryImage second = images.get(1);
        assertThat(second.mediaId, is("gif1"));
        assertTrue(second.isAnimated());
        assertThat(second.getImageUrl(), is("https://i.redd.it/gif1.mp4"));
    }

    @Test
    public void getGalleryData_nullDataLeavesListEmpty() {
        ArrayList<GalleryImage> images = new ArrayList<>();
        JsonUtil.getGalleryData(null, images);
        assertTrue(images.isEmpty());
    }

    @Test
    public void getGalleryData_missingGalleryDataLeavesListEmpty() throws Exception {
        ArrayList<GalleryImage> images = new ArrayList<>();
        JsonUtil.getGalleryData(node("{}"), images);
        assertTrue(images.isEmpty());
    }

    @Test
    public void getGalleryData_missingItemsLeavesListEmpty() throws Exception {
        ArrayList<GalleryImage> images = new ArrayList<>();
        JsonUtil.getGalleryData(node("{\"gallery_data\":{}}"), images);
        assertTrue(images.isEmpty());
    }
}
