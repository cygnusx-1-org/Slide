package me.edgan.redditslide.test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.edgan.redditslide.util.PhotoLoader;
import org.junit.Test;

/**
 * Tests for the feed-image URL selection shared between the preloader and the feed card
 * ({@link me.edgan.redditslide.SubmissionViews.HeaderImageLinkView}, which delegates here). Keeping
 * these two in lock-step is what prevents first-view pop-in, so the extracted helpers are pinned.
 * Nodes are built inline with Jackson; the helpers touch no Android APIs, so no Robolectric is
 * required.
 */
public class PhotoLoaderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode node(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    // ---------------------------------------------------------------------
    // getValidThumbnailUrl
    // ---------------------------------------------------------------------

    @Test
    public void validThumbnail_returnsUrl() throws Exception {
        assertThat(
                PhotoLoader.getValidThumbnailUrl(
                        node("{\"thumbnail\":\"https://b.thumbs.redditmedia.com/x.jpg\"}")),
                is("https://b.thumbs.redditmedia.com/x.jpg"));
    }

    @Test
    public void validThumbnail_rejectsPlaceholders() throws Exception {
        assertThat(PhotoLoader.getValidThumbnailUrl(node("{\"thumbnail\":\"self\"}")), is(nullValue()));
        assertThat(
                PhotoLoader.getValidThumbnailUrl(node("{\"thumbnail\":\"default\"}")), is(nullValue()));
        assertThat(PhotoLoader.getValidThumbnailUrl(node("{\"thumbnail\":\"nsfw\"}")), is(nullValue()));
        assertThat(PhotoLoader.getValidThumbnailUrl(node("{\"thumbnail\":\"\"}")), is(nullValue()));
    }

    @Test
    public void validThumbnail_handlesMissingNullAndNullNode() throws Exception {
        assertThat(PhotoLoader.getValidThumbnailUrl(node("{}")), is(nullValue()));
        assertThat(PhotoLoader.getValidThumbnailUrl(node("{\"thumbnail\":null}")), is(nullValue()));
        assertThat(PhotoLoader.getValidThumbnailUrl(null), is(nullValue()));
    }

    // ---------------------------------------------------------------------
    // getPreviewUrl
    // ---------------------------------------------------------------------

    @Test
    public void preview_extractsSourceUrl() throws Exception {
        assertThat(
                PhotoLoader.getPreviewUrl(
                        node(
                                "{\"preview\":{\"images\":[{\"source\":"
                                        + "{\"url\":\"https://preview.redd.it/a.jpg?s=xyz\"}}]}}")),
                is("https://preview.redd.it/a.jpg?s=xyz"));
    }

    @Test
    public void preview_rewritesExternalHostForRedditLinks() throws Exception {
        assertThat(
                PhotoLoader.getPreviewUrl(
                        node(
                                "{\"domain\":\"reddit.com\",\"preview\":{\"images\":[{\"source\":"
                                        + "{\"url\":\"https://external-preview.redd.it/a.jpg?s=xyz\"}}]}}")),
                is("https://preview.redd.it/a.jpg?s=xyz"));
    }

    @Test
    public void preview_keepsExternalHostForNonRedditLinks() throws Exception {
        assertThat(
                PhotoLoader.getPreviewUrl(
                        node(
                                "{\"domain\":\"example.com\",\"preview\":{\"images\":[{\"source\":"
                                        + "{\"url\":\"https://external-preview.redd.it/a.jpg\"}}]}}")),
                is("https://external-preview.redd.it/a.jpg"));
    }

    @Test
    public void preview_prefersCrosspostParent() throws Exception {
        assertThat(
                PhotoLoader.getPreviewUrl(
                        node(
                                "{\"domain\":\"reddit.com\","
                                        + "\"crosspost_parent_list\":[{\"preview\":{\"images\":[{\"source\":"
                                        + "{\"url\":\"https://preview.redd.it/parent.jpg\"}}]}}],"
                                        + "\"preview\":{\"images\":[{\"source\":"
                                        + "{\"url\":\"https://preview.redd.it/child.jpg\"}}]}}")),
                is("https://preview.redd.it/parent.jpg"));
    }

    @Test
    public void preview_nullWhenAbsentOrNullNode() throws Exception {
        assertThat(PhotoLoader.getPreviewUrl(node("{\"domain\":\"reddit.com\"}")), is(nullValue()));
        assertThat(PhotoLoader.getPreviewUrl(null), is(nullValue()));
    }

    // ---------------------------------------------------------------------
    // Sized preview selection — a list thumbnail must fetch a small reddit "resolutions" variant,
    // never the full-size source. sizedResolutionUrl is shared by getHighQualityUrl (image posts)
    // and the preview path (link/video posts), so testing it covers both.
    // ---------------------------------------------------------------------

    // A reddit preview image: three sized "resolutions" plus a huge full-size "source".
    private static final String PREVIEW_IMAGE =
            "{\"resolutions\":["
                    + "{\"url\":\"https://prev/r108.jpg\",\"width\":108,\"height\":80},"
                    + "{\"url\":\"https://prev/r320.jpg\",\"width\":320,\"height\":240},"
                    + "{\"url\":\"https://prev/r640.jpg\",\"width\":640,\"height\":480}],"
                    + "\"source\":{\"url\":\"https://prev/source.jpg\",\"width\":4000,\"height\":3000}}";

    private static String previewData(String imageJson) {
        return "{\"preview\":{\"images\":[" + imageJson + "]}}";
    }

    @Test
    public void sized_picksSmallestResolutionCoveringWidth() throws Exception {
        final JsonNode image = node(PREVIEW_IMAGE);
        assertThat(PhotoLoader.sizedResolutionUrl(image, 300), is("https://prev/r320.jpg"));
        assertThat(PhotoLoader.sizedResolutionUrl(image, 108), is("https://prev/r108.jpg"));
        assertThat(PhotoLoader.sizedResolutionUrl(image, 640), is("https://prev/r640.jpg"));
        // Target just above 108: the next rung (320) is a 2.9x overshoot, so the near-exact 108 is
        // preferred over downloading a much larger image (the sparse-rung cap).
        assertThat(PhotoLoader.sizedResolutionUrl(image, 109), is("https://prev/r108.jpg"));
    }

    @Test
    public void sized_nullWhenNoResolutionCoversWidth() throws Exception {
        // Card sizes: nothing is that wide, so the callers fall back to the full-size source.
        assertThat(PhotoLoader.sizedResolutionUrl(node(PREVIEW_IMAGE), 2000), is(nullValue()));
        assertThat(
                PhotoLoader.sizedResolutionUrl(node(PREVIEW_IMAGE), Integer.MAX_VALUE),
                is(nullValue()));
    }

    @Test
    public void sized_sparseRungsAvoidHugeOvershoot() throws Exception {
        // Only 108/216 then a jump to 1080: a ~300px thumbnail must take 216 (slightly soft), not
        // download a 1080px image. This is what makes sparse-preview thumbnails fast.
        final JsonNode sparse =
                node(
                        "{\"resolutions\":["
                                + "{\"url\":\"https://prev/r108.jpg\",\"width\":108,\"height\":80},"
                                + "{\"url\":\"https://prev/r216.jpg\",\"width\":216,\"height\":160},"
                                + "{\"url\":\"https://prev/r1080.jpg\",\"width\":1080,\"height\":800}]}");
        assertThat(PhotoLoader.sizedResolutionUrl(sparse, 300), is("https://prev/r216.jpg"));
        // But a covering rung within 2x the target is fine to use (no overshoot).
        final JsonNode ok =
                node(
                        "{\"resolutions\":["
                                + "{\"url\":\"https://prev/r216.jpg\",\"width\":216,\"height\":160},"
                                + "{\"url\":\"https://prev/r320.jpg\",\"width\":320,\"height\":240}]}");
        assertThat(PhotoLoader.sizedResolutionUrl(ok, 300), is("https://prev/r320.jpg"));
        // If the only rung is a huge one, there's no smaller choice, so it's used.
        final JsonNode onlyHuge =
                node("{\"resolutions\":[{\"url\":\"https://prev/r1080.jpg\",\"width\":1080}]}");
        assertThat(PhotoLoader.sizedResolutionUrl(onlyHuge, 300), is("https://prev/r1080.jpg"));
    }

    @Test
    public void sized_duplicateWidthsPickFirst() throws Exception {
        final JsonNode image =
                node(
                        "{\"resolutions\":["
                                + "{\"url\":\"https://prev/a.jpg\",\"width\":320,\"height\":240},"
                                + "{\"url\":\"https://prev/b.jpg\",\"width\":320,\"height\":240}]}");
        assertThat(PhotoLoader.sizedResolutionUrl(image, 300), is("https://prev/a.jpg"));
    }

    @Test
    public void sized_skipsMalformedAndHandlesMissingResolutions() throws Exception {
        // An entry without a width is skipped; the next valid one is used.
        final JsonNode image =
                node(
                        "{\"resolutions\":["
                                + "{\"url\":\"https://prev/nowidth.jpg\"},"
                                + "{\"url\":\"https://prev/r320.jpg\",\"width\":320,\"height\":240}]}");
        assertThat(PhotoLoader.sizedResolutionUrl(image, 300), is("https://prev/r320.jpg"));
        assertThat(PhotoLoader.sizedResolutionUrl(node("{\"source\":{}}"), 300), is(nullValue()));
        assertThat(PhotoLoader.sizedResolutionUrl(node("{}"), 300), is(nullValue()));
        assertThat(PhotoLoader.sizedResolutionUrl(null, 300), is(nullValue()));
    }

    @Test
    public void preview_thumbnailWidthAvoidsOversizedSource() throws Exception {
        // The whole point: a thumbnail must NOT download the multi-thousand-pixel source.
        assertThat(
                PhotoLoader.getPreviewUrl(node(previewData(PREVIEW_IMAGE)), 300),
                is("https://prev/r320.jpg"));
    }

    @Test
    public void preview_cardWidthUsesFullSource() throws Exception {
        assertThat(
                PhotoLoader.getPreviewUrl(node(previewData(PREVIEW_IMAGE)), Integer.MAX_VALUE),
                is("https://prev/source.jpg"));
        // The no-arg overload is the card / fullscreen path.
        assertThat(
                PhotoLoader.getPreviewUrl(node(previewData(PREVIEW_IMAGE))),
                is("https://prev/source.jpg"));
    }

    @Test
    public void preview_widthBeyondAllResolutionsFallsBackToSource() throws Exception {
        assertThat(
                PhotoLoader.getPreviewUrl(node(previewData(PREVIEW_IMAGE)), 2000),
                is("https://prev/source.jpg"));
    }

    @Test
    public void preview_thumbnailAndCardResolveDifferentSizesDeterministically() throws Exception {
        // Guards against a regression where a thumbnail silently pulls the full-size image, and
        // pins that the preload and the display (same width) never resolve mismatched URLs.
        final JsonNode data = node(previewData(PREVIEW_IMAGE));
        final String thumb = PhotoLoader.getPreviewUrl(data, 300);
        final String card = PhotoLoader.getPreviewUrl(data, Integer.MAX_VALUE);
        assertThat(thumb, is("https://prev/r320.jpg"));
        assertThat(card, is("https://prev/source.jpg"));
        assertThat(thumb.equals(card), is(false));
        // Same width is deterministic → preload and display always agree.
        assertThat(PhotoLoader.getPreviewUrl(data, 300), is(thumb));
    }

    @Test
    public void preview_crosspostParentSizedForThumbnail() throws Exception {
        // The crosspost parent's preview is preferred, and it is sized to the thumbnail too.
        final String data =
                "{\"crosspost_parent_list\":["
                        + previewData(PREVIEW_IMAGE)
                        + "],\"preview\":{\"images\":[{\"source\":"
                        + "{\"url\":\"https://child/source.jpg\"}}]}}";
        assertThat(PhotoLoader.getPreviewUrl(node(data), 300), is("https://prev/r320.jpg"));
    }
}
