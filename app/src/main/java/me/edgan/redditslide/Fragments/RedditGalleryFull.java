package me.edgan.redditslide.Fragments;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.Adapters.RedditGalleryView;
import me.edgan.redditslide.SubmissionViews.PopulateShadowboxInfo;
import me.edgan.redditslide.util.LogUtil;
import net.dean.jraw.models.Submission;

public class RedditGalleryFull extends BaseAlbumFull {

    private int i;
    // Package-private so the fragment's own test can stand them up without a live Shadowbox host.
    @Nullable Submission s;
    @Nullable List<GalleryImage> images;

    List<GalleryImage> extractGalleryImages(Submission submission) {
        List<GalleryImage> galleryImages = new ArrayList<>();
        try {
            LogUtil.v("Extracting gallery images from submission");
            JsonNode galleryData = submission.getDataNode().get("gallery_data");
            JsonNode mediaMetadata = submission.getDataNode().get("media_metadata");

            if (galleryData != null && mediaMetadata != null && galleryData.has("items")) {
                JsonNode items = galleryData.get("items");
                LogUtil.v("Gallery items: " + items.toString());

                for (JsonNode item : items) {
                    String mediaId = item.get("media_id").asText();
                    if (mediaMetadata.has(mediaId)) {
                        JsonNode mediaItem = mediaMetadata.get(mediaId);
                        LogUtil.v("Media item for " + mediaId + ": " + mediaItem.toString());

                        if (mediaItem != null && mediaItem.has("s")) {
                            JsonNode s = mediaItem.get("s");
                            // Create a new object matching GalleryImage's expected structure
                            ObjectNode imageNode = JsonNodeFactory.instance.objectNode();

                            // Add the URL field as "u" instead of "url"
                            if (s.has("u")) {
                                imageNode.put("u", s.get("u").asText());
                            } else if (s.has("mp4")) {
                                imageNode.put("mp4", s.get("mp4").asText());
                            }

                            // Add width and height as x and y
                            imageNode.put("x", s.has("x") ? s.get("x").asInt() : 0);
                            imageNode.put("y", s.has("y") ? s.get("y").asInt() : 0);

                            // Add metadata fields
                            if (mediaItem.has("e")) imageNode.put("e", mediaItem.get("e").asText());
                            if (mediaItem.has("m")) imageNode.put("m", mediaItem.get("m").asText());

                            // Add source information
                            ObjectNode sourceNode = JsonNodeFactory.instance.objectNode();
                            if (s.has("mp4")) sourceNode.put("mp4", s.get("mp4").asText());
                            if (s.has("gif")) sourceNode.put("gif", s.get("gif").asText());
                            if (s.has("x")) sourceNode.put("x", s.get("x").asInt());
                            if (s.has("y")) sourceNode.put("y", s.get("y").asInt());
                            if (s.has("u")) sourceNode.put("u", s.get("u").asText());
                            imageNode.set("s", sourceNode);

                            LogUtil.v("Created image node: " + imageNode.toString());
                            GalleryImage galleryImage = new GalleryImage(imageNode);
                            if (item.has("caption") && !item.get("caption").isNull()) {
                                galleryImage.caption = item.get("caption").asText();
                            }
                            galleryImages.add(galleryImage);
                        }
                    }
                }
            } else {
                LogUtil.v("Missing required gallery data. galleryData: " + galleryData +
                         ", mediaMetadata: " + mediaMetadata);
            }
            LogUtil.v("Found " + galleryImages.size() + " gallery images");
        } catch (Exception e) {
            LogUtil.e("Error extracting gallery images: " + e.getMessage());
            LogUtil.e(e, "RedditGalleryFull.extractGalleryImages failed");
        }
        return galleryImages;
    }

    @Override
    protected void bindActionbar() {
        if (s == null) {
            return;
        }
        PopulateShadowboxInfo.doActionbar(s, rootView, getActivity(), true);
    }

    /**
     * The submission's own url. Nothing fetches it — a gallery has no album endpoint behind it — so
     * it is only here to satisfy the base class; {@link #hasAlbumToShow()} is what decides whether
     * this page renders.
     */
    @Override
    protected @Nullable String getAlbumUrl() {
        return s == null ? null : s.getUrl();
    }

    /** The images, not a url: they came out of the submission in onCreate and are all that matters. */
    @Override
    protected boolean hasAlbumToShow() {
        return images != null && !images.isEmpty();
    }

    @Override
    protected void openComments() {
        openShadowboxComments(i);
    }

    /**
     * Binds the images extracted in onCreate. Unlike the other album fragments this does no loading,
     * so it can set the adapter straight away rather than from a callback.
     */
    @Override
    protected void loadAlbum(String url) {
        final Submission submission = s;
        final List<GalleryImage> galleryImages = images;
        if (submission == null || galleryImages == null) {
            return;
        }

        ((RecyclerView) list)
                .setAdapter(
                        new RedditGalleryView(
                                getActivity(),
                                galleryImages,
                                submission.getSubredditName(),
                                submission.getTitle()));
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        i = this.getArguments().getInt("page", 0);
        s = resolveSubmission();
        if (s != null) {
            images = extractGalleryImages(s);
        }
    }

    /**
     * The submission this page shows. Separated from {@link #onCreate} only so a test can host this
     * fragment without a live Shadowbox behind it — everything else here works off {@code s}.
     */
    @Nullable Submission resolveSubmission() {
        return submissionForShadowboxPage();
    }
}
