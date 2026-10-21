package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.app.Application;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import me.edgan.redditslide.util.RedditMediaUpload;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for the lease request and reply handling of {@link RedditMediaUpload}, run under Robolectric
 * so the S3 XML is parsed by Android's real {@code XmlPullParser}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class RedditMediaUploadTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String S3_REPLY =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<PostResponse>"
                    + "<Location>https://reddit-uploaded-media.s3-accelerate.amazonaws.com/"
                    + "c13qh99g1wwh1</Location>"
                    + "<Bucket>reddit-uploaded-media</Bucket>"
                    + "<Key>c13qh99g1wwh1</Key>"
                    + "<ETag>\"0123456789abcdef\"</ETag>"
                    + "</PostResponse>";

    private static JsonNode node(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    @Test
    public void leaseParams_sendMimetypeOnly() {
        Map<String, String> params = RedditMediaUpload.leaseParams("image/png");
        assertEquals("image/png", params.get("mimetype"));
        assertFalse(params.containsKey("filepath"));
        assertEquals(1, params.size());
    }

    @Test
    public void uploadForm_readsBareReply() throws Exception {
        JsonNode form =
                RedditMediaUpload.uploadForm(
                        node(
                                "{\"action\":\"//reddit-uploaded-media.s3-accelerate.amazonaws.com\","
                                        + "\"fields\":[{\"name\":\"key\",\"value\":\"c13qh99g1wwh1\"},"
                                        + "{\"name\":\"Content-Type\",\"value\":\"image/png\"}]}"));
        assertNotNull(form);
        assertEquals(
                "//reddit-uploaded-media.s3-accelerate.amazonaws.com",
                form.path("action").asText());
        assertEquals("key", form.path("fields").path(0).path("name").asText());
        assertEquals("c13qh99g1wwh1", form.path("fields").path(0).path("value").asText());
        assertEquals(2, form.path("fields").size());
    }

    @Test
    public void uploadForm_readsArgsWrappedReply() throws Exception {
        JsonNode form =
                RedditMediaUpload.uploadForm(
                        node(
                                "{\"args\":{\"action\":\"//reddit-uploaded-media.s3-accelerate"
                                        + ".amazonaws.com\",\"fields\":[{\"name\":\"key\","
                                        + "\"value\":\"rte_images/abc\"}]},"
                                        + "\"asset\":{\"asset_id\":\"abc\","
                                        + "\"processing_state\":\"incomplete\","
                                        + "\"websocket_url\":\"wss://example\"}}"));
        assertNotNull(form);
        assertEquals(
                "//reddit-uploaded-media.s3-accelerate.amazonaws.com",
                form.path("action").asText());
        assertEquals("rte_images/abc", form.path("fields").path(0).path("value").asText());
    }

    @Test
    public void uploadForm_rejectsReplyWithoutAction() throws Exception {
        assertNull(RedditMediaUpload.uploadForm(null));
        assertNull(RedditMediaUpload.uploadForm(node("{}")));
        assertNull(RedditMediaUpload.uploadForm(node("{\"args\":{\"fields\":[]}}")));
        assertNull(RedditMediaUpload.uploadForm(node("{\"json\":{\"errors\":[]}}")));
    }

    @Test
    public void galleryMediaId_isS3Key() throws Exception {
        assertEquals("c13qh99g1wwh1", RedditMediaUpload.galleryMediaId(S3_REPLY));
    }

    @Test
    public void galleryMediaId_throwsWithoutKey() {
        assertThrows(
                IOException.class,
                () ->
                        RedditMediaUpload.galleryMediaId(
                                "<PostResponse><Bucket>b</Bucket></PostResponse>"));
    }
}
