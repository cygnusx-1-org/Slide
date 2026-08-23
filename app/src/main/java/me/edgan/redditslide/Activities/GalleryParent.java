package me.edgan.redditslide.Activities;

import androidx.annotation.Nullable;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Interface to provide common functionality for gallery parent activities.
 * Implemented by both RedditGallery and RedditGalleryPager to share code.
 */
@NullMarked
public interface GalleryParent {
    @Nullable List<GalleryImage> getGalleryImages();

    @Nullable String getGallerySubreddit();

    @Nullable String getGallerySubmissionTitle();
    void showGalleryBottomSheet(String url, boolean isGif, int position);
    void saveGalleryMedia(boolean isGif, String url, int position);
}