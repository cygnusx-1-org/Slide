package me.edgan.redditslide.util;

import android.util.Log;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import me.edgan.redditslide.Activities.GalleryImage;
import org.apache.commons.text.StringEscapeUtils;

/** Created by TacoTheDank on 04/04/2021. */
public class JsonUtil {
    private static final String TAG = "JsonUtil";

    /** Whether this post links to another Reddit post (domain reddit.com or *.reddit.com). */
    public static boolean linksToReddit(JsonNode dataNode) {
        if (dataNode == null || !dataNode.has("domain") || dataNode.get("domain").isNull()) {
            return false;
        }
        String domain = dataNode.get("domain").asText("");
        return domain.equals("reddit.com") || domain.endsWith(".reddit.com");
    }

    /**
     * A link post that points at another Reddit post carries that post's image as a preview, but
     * served from the external-preview host, which isn't reliably loadable and leaves a blank lead
     * image. The canonical preview.redd.it host (the same signed asset, used by the linked post
     * itself) loads fine, so rewrite to it — but only for reddit links, so genuine external-link
     * previews (news sites, etc.) keep their external-preview URLs.
     */
    public static String normalizeRedditPreviewHost(String url, boolean linksToReddit) {
        if (linksToReddit && url != null) {
            return url.replace("://external-preview.redd.it/", "://preview.redd.it/");
        }
        return url;
    }

    public static void getGalleryData(final JsonNode data, final ArrayList<GalleryImage> urls) {
        if (data == null || !data.has("gallery_data") || data.get("gallery_data") == null ||
            !data.get("gallery_data").has("items") || data.get("gallery_data").get("items") == null) {
            Log.w(TAG, "Missing or null gallery_data or items in gallery data");
            return;
        }

        for (JsonNode identifier : data.get("gallery_data").get("items")) {
            if (identifier == null || !identifier.has("media_id")) {
                continue;
            }

            String mediaId = identifier.get("media_id").asText();
            if (data.has("media_metadata") && data.get("media_metadata").has(mediaId)) {
                JsonNode mediaNode = data.get("media_metadata").get(mediaId);
                if (mediaNode == null || !mediaNode.has("s")) {
                    continue;
                }

                // Create a base GalleryImage with the source data
                GalleryImage image = new GalleryImage(mediaNode.get("s"));

                // Set mediaId explicitly
                image.mediaId = mediaId;

                // Caption lives on the gallery_data item, not in media_metadata
                if (identifier.has("caption") && !identifier.get("caption").isNull()) {
                    image.caption = identifier.get("caption").asText();
                }

                // Make sure metadata exists
                if (image.metadata == null) {
                    image.metadata = new GalleryImage.MediaMetadata();
                }

                // Set metadata fields that determine animation status
                if (mediaNode.has("e")) {
                    image.metadata.e = mediaNode.get("e").asText();

                    // Detect animated content based on the e field
                    if ("AnimatedImage".equals(image.metadata.e)) {
                        image.metadata.animated = true;
                    }
                }

                if (mediaNode.has("m")) {
                    image.metadata.m = mediaNode.get("m").asText();

                    // Also check MIME type for animation
                    if (image.metadata.m != null && image.metadata.m.contains("gif")) {
                        image.metadata.animated = true;
                    }
                }

                // Check URL for animation as additional fallback
                if (image.url != null && (image.url.endsWith(".gif") || image.url.endsWith(".gifv") || image.url.endsWith(".mp4"))) {
                    image.metadata.animated = true;
                }

                // Create the source properly if it doesn't exist
                if (image.metadata.source == null) {
                    image.metadata.source = new GalleryImage.MediaMetadata.Source();
                }

                // Ensure source URLs are correct for animated content
                JsonNode s = mediaNode.get("s");
                if (s != null) {
                    if (s.has("mp4")) {
                        image.metadata.source.mp4 = StringEscapeUtils.unescapeHtml4(s.get("mp4").asText());
                    }
                    if (s.has("gif")) {
                        image.metadata.source.gif = StringEscapeUtils.unescapeHtml4(s.get("gif").asText());
                    }
                    if (s.has("u")) {
                        image.metadata.source.u = StringEscapeUtils.unescapeHtml4(s.get("u").asText());
                    }
                }

                // Add preview images if available
                if (mediaNode.has("p") && mediaNode.get("p").isArray() && mediaNode.get("p").size() > 0) {
                    JsonNode previewArray = mediaNode.get("p");
                    image.metadata.p = new GalleryImage.MediaMetadata.Preview[previewArray.size()];

                    for (int i = 0; i < previewArray.size(); i++) {
                        JsonNode preview = previewArray.get(i);
                        if (preview == null) {
                            continue;
                        }

                        GalleryImage.MediaMetadata.Preview p = new GalleryImage.MediaMetadata.Preview();

                        if (preview.has("u")) {
                            p.u = StringEscapeUtils.unescapeHtml4(preview.get("u").asText());
                        }
                        if (preview.has("x")) {
                            p.x = preview.get("x").asInt();
                        }
                        if (preview.has("y")) {
                            p.y = preview.get("y").asInt();
                        }

                        image.metadata.p[i] = p;
                    }
                } else {
                    Log.d(TAG, "No preview array found in mediaNode for mediaId: " + mediaId);
                }

                urls.add(image);
            }
        }
    }
}
