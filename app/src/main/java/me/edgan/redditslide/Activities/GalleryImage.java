package me.edgan.redditslide.Activities;

import android.util.Log;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.commons.text.StringEscapeUtils;

import java.io.Serializable;
import java.util.Arrays;

/** Created by ccrama on 09/22/2020. */
public class GalleryImage implements Serializable {
    private static final String TAG = "GalleryImage";
    public String url;
    public int width;
    public int height;

    public String mediaId;
    public MediaMetadata metadata;

    public GalleryImage(JsonNode data) {
        // Add metadata population
        this.metadata = new MediaMetadata();

        if (data == null || data.isNull()) {
            Log.e(TAG, "GalleryImage constructor called with null or JsonNull data. Initializing as empty.");
            return;
        }

        Log.d(TAG, "GalleryImage constructor called with data: " + data.toString());
        if (data.has("media_id")) {
            mediaId = data.get("media_id").asText();
        }

        // Check if this is a mediaNode with 's' property or direct 's' node
        JsonNode sNode = data.has("s") ? data.get("s") : data;

        // Parse the s node that contains the actual image URLs
        if (sNode.has("u")) {
            url = StringEscapeUtils.unescapeHtml4(sNode.get("u").asText());
        } else if (sNode.has("gif")) {
            url = StringEscapeUtils.unescapeHtml4(sNode.get("gif").asText());
        } else if (sNode.has("mp4")) {
            url = StringEscapeUtils.unescapeHtml4(sNode.get("mp4").asText());
        }

        // Get dimensions from the s node
        if (sNode.has("x") && sNode.has("y")) {
            width = sNode.get("x").asInt();
            height = sNode.get("y").asInt();
        }


        if (data.has("e")) {
            metadata.e = data.get("e").asText();
        }
        if (data.has("m")) {
            metadata.m = data.get("m").asText();
        }

        // Parse preview images array if available directly in data
        // Look for p in mediaNode first, then in sNode
        JsonNode pNode = data.has("p") ? data.get("p") : (sNode.has("p") ? sNode.get("p") : null);

        if (pNode != null && pNode.isArray() && pNode.size() > 0) {
            metadata.p = new MediaMetadata.Preview[pNode.size()];
            for (int i = 0; i < pNode.size(); i++) {
                JsonNode preview = pNode.get(i);
                MediaMetadata.Preview p = new MediaMetadata.Preview();
                if (preview.has("u")) {
                    p.u = StringEscapeUtils.unescapeHtml4(preview.get("u").asText());
                }
                if (preview.has("x")) {
                    p.x = preview.get("x").asInt();
                }
                if (preview.has("y")) {
                    p.y = preview.get("y").asInt();
                }
                metadata.p[i] = p;
            }
        } else {
            Log.d(TAG, "No preview array found in data");
        }

        if (data.has("s")) {
            JsonNode s = data.get("s");
            metadata.source = new MediaMetadata.Source();
            if (s.has("mp4")) {
                metadata.source.mp4 = StringEscapeUtils.unescapeHtml4(s.get("mp4").asText());
            }
            if (s.has("gif")) {
                metadata.source.gif = StringEscapeUtils.unescapeHtml4(s.get("gif").asText());
            }
            if (s.has("u")) {
                metadata.source.u = StringEscapeUtils.unescapeHtml4(s.get("u").asText());
            }
            if (s.has("y")) {
                metadata.source.y = s.get("y").asInt();
            }
            if (s.has("x")) {
                metadata.source.x = s.get("x").asInt();
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

    public String getImageUrl() {
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

    public static class MediaMetadata implements Serializable {
        private static final long serialVersionUID = 1L;

        public String e; // type (e.g., "Image", "AnimatedImage")
        public String m; // mimetype (e.g., "image/gif", "image/jpg")
        public String s; // status
        public long id; // media id
        public boolean animated; // whether media is animated
        public String ext; // file extension with dot (e.g., ".gif")

        public Preview[] p; // array of preview images
        public Source source; // source object containing URLs

        @Override
        public String toString() {
            return "MediaMetadata{"
                    + "e='"
                    + e
                    + '\''
                    + ", m='"
                    + m
                    + '\''
                    + ", s='"
                    + s
                    + '\''
                    + ", id="
                    + id
                    + ", animated="
                    + animated
                    + ", ext='"
                    + ext
                    + '\''
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
            public String u; // preview URL
        }

        public static class Source implements Serializable {
            private static final long serialVersionUID = 1L;
            public int y; // height
            public int x; // width
            public String u; // direct URL for non-animated
            public String gif; // gif URL for animated
            public String mp4; // mp4 URL for animated
        }
    }
}
