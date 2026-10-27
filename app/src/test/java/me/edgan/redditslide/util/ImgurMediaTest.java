package me.edgan.redditslide.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.NullMarked;
import org.junit.Test;

/**
 * Which imgur API v3 replies {@link HttpUtil.ImgurMedia#isGif} sends to the video player rather
 * than the image loader. The payloads are trimmed from real {@code api.imgur.com/3/image/<hash>}
 * replies for the two links in issue #315.
 */
@NullMarked
public class ImgurMediaTest {

    private static HttpUtil.ImgurMedia parse(String json) {
        JsonObject result = JsonParser.parseString(json).getAsJsonObject();
        HttpUtil.ImgurMedia media = HttpUtil.parseImgurMedia(result);
        assertNotNull(media);
        return media;
    }

    /** A native video upload: typed video/mp4, not a gif type, and its link is the mp4 itself. */
    @Test
    public void videoUploadPlaysAsVideo() {
        HttpUtil.ImgurMedia media =
                parse(
                        "{\"data\":{\"id\":\"kZQSVSu\",\"type\":\"video/mp4\",\"animated\":true,"
                                + "\"link\":\"https://i.imgur.com/kZQSVSu.mp4\","
                                + "\"mp4\":\"https://i.imgur.com/kZQSVSu.mp4\",\"hls\":\"\"},"
                                + "\"success\":true,\"status\":200}");

        assertTrue(media.isGif());
        assertEquals("https://i.imgur.com/kZQSVSu.mp4", media.getGifUrl());
    }

    @Test
    public void stillImageStaysAnImage() {
        HttpUtil.ImgurMedia media =
                parse(
                        "{\"data\":{\"id\":\"tP1IVu0\",\"type\":\"image/jpeg\",\"animated\":false,"
                                + "\"link\":\"https://i.imgur.com/tP1IVu0.jpeg\",\"mp4\":\"\",\"hls\":\"\"},"
                                + "\"success\":true,\"status\":200}");

        assertFalse(media.isGif());
        assertEquals("https://i.imgur.com/tP1IVu0.jpeg", media.getImageUrl());
    }

    @Test
    public void gifPlaysItsMp4Rendition() {
        HttpUtil.ImgurMedia media =
                parse(
                        "{\"data\":{\"id\":\"abc1234\",\"type\":\"image/gif\",\"animated\":true,"
                                + "\"link\":\"https://i.imgur.com/abc1234.gif\","
                                + "\"mp4\":\"https://i.imgur.com/abc1234.mp4\"},"
                                + "\"success\":true,\"status\":200}");

        assertTrue(media.isGif());
        assertEquals("https://i.imgur.com/abc1234.mp4", media.getGifUrl());
    }
}
