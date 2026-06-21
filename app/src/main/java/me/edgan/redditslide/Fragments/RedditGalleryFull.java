package me.edgan.redditslide.Fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.Activities.CommentsScreen;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.Activities.Shadowbox;
import me.edgan.redditslide.Adapters.RedditGalleryView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SubmissionViews.PopulateShadowboxInfo;
import me.edgan.redditslide.util.LogUtil;
import net.dean.jraw.models.Submission;

public class RedditGalleryFull extends Fragment {

    boolean gallery = false;
    private View list;
    private int i;
    private Submission s;
    private View rootView;
    private List<GalleryImage> images;
    boolean hidden;

    private List<GalleryImage> extractGalleryImages(Submission submission) {
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.submission_albumcard, container, false);
        PopulateShadowboxInfo.doActionbar(s, rootView, getActivity(), true);

        list = rootView.findViewById(R.id.images);

        final LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        ((RecyclerView) list).setLayoutManager(layoutManager);

        final View.OnClickListener openClick = view -> {
            if (rootView.findViewById(R.id.base).getAlpha() <= 0.8) {
                rootView.findViewById(R.id.base).setAlpha(1f);
            } else {
                rootView.findViewById(R.id.base).setAlpha(0.2f);
            }
        };

        rootView.findViewById(R.id.base).setOnClickListener(openClick);

        ((SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout))
                .addPanelSlideListener(new SlidingUpPanelLayout.SimplePanelSlideListener() {
                    @Override
                    public void onPanelStateChanged(View panel,
                            SlidingUpPanelLayout.PanelState previousState,
                            SlidingUpPanelLayout.PanelState newState) {
                        if (newState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                            rootView.findViewById(R.id.base).setOnClickListener(v -> {
                                Intent i2 = new Intent(getActivity(), CommentsScreen.class);
                                i2.putExtra(CommentsScreen.EXTRA_PAGE, i);
                                i2.putExtra(CommentsScreen.EXTRA_SUBREDDIT,
                                        ((Shadowbox) getActivity()).subreddit);
                                getActivity().startActivity(i2);
                            });
                        } else {
                            rootView.findViewById(R.id.base).setOnClickListener(openClick);
                        }
                    }
                });

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (getActivity() != null) {
                            Activity activity = getActivity();
                            View toolbar = rootView.findViewById(R.id.toolbar);
                            int toolbarHeight = toolbar != null ? toolbar.getHeight() : 0;

                            ((RecyclerView) list).setAdapter(new RedditGalleryView(
                                    activity,
                                    images,
                                    toolbarHeight,
                                    s.getSubredditName(),
                                    s.getTitle()));
                            rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    }
                });

        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = this.getArguments();
        i = bundle.getInt("page", 0);
        if (((Shadowbox) getActivity()).subredditPosts == null
                || ((Shadowbox) getActivity()).subredditPosts.getPosts().size() < bundle.getInt("page",
                        0)) {
            getActivity().finish();
        } else {
            s = ((Shadowbox) getActivity()).subredditPosts.getPosts().get(bundle.getInt("page", 0));
            images = extractGalleryImages(s);
        }
    }
}
