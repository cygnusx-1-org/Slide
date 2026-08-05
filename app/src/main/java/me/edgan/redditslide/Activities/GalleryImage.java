package me.edgan.redditslide.Activities;

import android.util.Log;
import androidx.annotation.Nullable;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;
import java.util.Arrays;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.NullMarked;

/** Created by ccrama on 09/22/2020. */
@NullMarked
public class GalleryImage implements Serializable {
    private static final String TAG = "GalleryImage";
    @Nullable public String url;
    public int width;
    public int height;

    @Nullable public String mediaId;
    @Nullable public String caption;
    public MediaMetadata metadata;

    public GalleryImage(@Nullable JsonNode data) {
        // Add metadata population
        this.metadata = new MediaMetadata();

        if (data == null || data.isNull()) {
            Log.e(TAG, "GalleryImage constructor called with null or JsonNull data. Initializing as empty.");
            return;
        }

        Log.d(TAG, "GalleryImage constructor called with data: " + data.toString());
        if (data.has("media_id")) {
            mediaId = data.path("media_id").asText();
        }

        // Check if this is a mediaNode with 's' property or direct 's' node
        JsonNode sNode = data.has("s") ? data.path("s") : data;

        // Parse the s node that contains the actual image URLs
        if (sNode.has("u")) {
            url = StringEscapeUtils.unescapeHtml4(sNode.path("u").asText());
        } else if (sNode.has("gif")) {
            url = StringEscapeUtils.unescapeHtml4(sNode.path("gif").asText());
        } else if (sNode.has("mp4")) {
            url = StringEscapeUtils.unescapeHtml4(sNode.path("mp4").asText());
        }

        // Get dimensions from the s node
        if (sNode.has("x") && sNode.has("y")) {
            width = sNode.path("x").asInt();
            height = sNode.path("y").asInt();
        }


        if (data.has("e")) {
            metadata.e = data.path("e").asText();
        }
        if (data.has("m")) {
            metadata.m = data.path("m").asText();
        }

        // Parse preview images array if available directly in data
        // Look for p in mediaNode first, then in sNode
        JsonNode pNode = data.has("p") ? data.get("p") : (sNode.has("p") ? sNode.get("p") : null);

        if (pNode != null && pNode.isArray() && pNode.size() > 0) {
            metadata.p = new MediaMetadata.Preview[pNode.size()];
            for (int i = 0; i < pNode.size(); i++) {
                JsonNode preview = pNode.path(i);
                MediaMetadata.Preview p = new MediaMetadata.Preview();
                if (preview.has("u")) {
                    p.u = StringEscapeUtils.unescapeHtml4(preview.path("u").asText());
                }
                if (preview.has("x")) {
                    p.x = preview.path("x").asInt();
                }
                if (preview.has("y")) {
                    p.y = preview.path("y").asInt();
                }
                metadata.p[i] = p;
            }
        } else {
            Log.d(TAG, "No preview array found in data");
        }

        if (data.has("s")) {
            JsonNode s = data.path("s");
            metadata.source = new MediaMetadata.Source();
            if (s.has("mp4")) {
                metadata.source.mp4 = StringEscapeUtils.unescapeHtml4(s.path("mp4").asText());
            }
            if (s.has("gif")) {
                metadata.source.gif = StringEscapeUtils.unescapeHtml4(s.path("gif").asText());
            }
            if (s.has("u")) {
                metadata.source.u = StringEscapeUtils.unescapeHtml4(s.path("u").asText());
            }
            if (s.has("y")) {
                metadata.source.y = s.path("y").asInt();
            }
            if (s.has("x")) {
                metadata.source.x = s.path("x").asInt();
            }
        }

        // Set animated based on type
        metadata.animated = "AnimatedImage".equals(metadata.e);
    }

    public boolean isAnimated() {
        boolean result = false;

        if (metadata != null) {
            // Check metadata first (most reliable)
            if (metadata.animated) {
                result = true;
            }
            else if ("AnimatedImage".equals(metadata.e)) {
                result = true;
            }
            else if (metadata.m != null && metadata.m.contains("gif")) {
                result = true;
            }
            // Check source URLs
            else if (metadata.source != null) {
                if (metadata.source.gif != null && !metadata.source.gif.isEmpty()) {
                    result = true;
                }
                else if (metadata.source.mp4 != null && !metadata.source.mp4.isEmpty()) {
                    result = true;
                }
            }
        }

        // Fallback to URL check if metadata methods don't find animation
        if (!result && url != null) {
            result = url.endsWith(".gif") || url.endsWith(".gifv") || url.endsWith(".mp4");
        }

        return result;
    }

    /**
     * The url to load for this entry, or null when it has none — a removed or failed gallery entry
     * whose "s" node carried no {@code u}, {@code gif} or {@code mp4} leaves every branch here
     * returning the (also null) {@link #url}.
     */
    @Nullable public String getImageUrl() {
        String resultUrl;

        // ANIMATED CONTENT HANDLING
        if (isAnimated()) {
            // For video playback, prioritize MP4 URL from metadata
            if (metadata != null && metadata.source != null) {
                if (metadata.source.mp4 != null && !metadata.source.mp4.isEmpty()) {
                    resultUrl = metadata.source.mp4;

                    return resultUrl;
                } else if (metadata.source.gif != null && !metadata.source.gif.isEmpty()) {
                    resultUrl = metadata.source.gif;

                    return resultUrl;
                }
            }

            // If URL ends with .gif, .gifv, or .mp4, use it directly
            if (url != null && (url.endsWith(".gif") || url.endsWith(".gifv") || url.endsWith(".mp4"))) {
                return url;
            }
        }

        // STATIC CONTENT HANDLING
        // For non-animated content or if no MP4/GIF URL exists
        if (metadata != null && metadata.source != null && metadata.source.u != null) {
            // Use the direct URL from source if available
            resultUrl = metadata.source.u;

            return resultUrl;
        }

        // Fall back to original URL if nothing else works
        return url;
    }

    /**
     * Every field here is optional, because every writer sets it only behind a {@code has()} test on
     * the media_metadata entry it came from — an entry with no {@code "e"} leaves {@link #e} unset,
     * one with no {@code "s"} node leaves {@link #source} null, and {@code GalleryImageTest} pins
     * that last one. Hence {@code @Nullable} rather than {@code NullAway.Init}: nothing populates
     * these on every path, which is the test that suppression is supposed to pass.
     */
    public static class MediaMetadata implements Serializable {
        private static final long serialVersionUID = 1L;

        @Nullable public String e; // type (e.g., "Image", "AnimatedImage")
        @Nullable public String m; // mimetype (e.g., "image/gif", "image/jpg")
        public long id; // media id
        public boolean animated; // whether media is animated

        @Nullable public Preview[] p; // array of preview images
        @Nullable public Source source; // source object containing URLs

        @Override
        public String toString() {
            return "MediaMetadata{"
                    + "e='"
                    + e
                    + '\''
                    + ", m='"
                    + m
                    + '\''
                    + ", id="
                    + id
                    + ", animated="
                    + animated
                    + ", p="
                    + (p != null ? Arrays.toString(p) : "null")
                    + ", source="
                    + source
                    + '}';
        }

        public static class Preview implements Serializable {
            private static final long serialVersionUID = 1L;
            public int y; // height
            public int x; // width
            @Nullable public String u; // preview URL
        }

        public static class Source implements Serializable {
            private static final long serialVersionUID = 1L;
            public int y; // height
            public int x; // width
            @Nullable public String u; // direct URL for non-animated
            @Nullable public String gif; // gif URL for animated
            @Nullable public String mp4; // mp4 URL for animated
        }
    }
}
