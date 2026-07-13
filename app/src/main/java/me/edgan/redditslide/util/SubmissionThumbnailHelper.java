package me.edgan.redditslide.util;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.fasterxml.jackson.databind.JsonNode;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.DataShare;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionViews.HeaderImageLinkView;
import me.edgan.redditslide.SubmissionViews.PopulateBase;
import me.edgan.redditslide.Visuals.Palette;
import net.dean.jraw.models.Submission;
import org.apache.commons.text.StringEscapeUtils;


public class SubmissionThumbnailHelper {

    public static void openRedditContent(String url, Context c) {
        OpenRedditLink.openUrl(c, url, true);
    }

    public static void openImage(
            ContentType.Type type,
            Activity contextActivity,
            Submission submission,
            HeaderImageLinkView baseView,
            int adapterPosition) {
        if (SettingValues.image) {
            Intent myIntent = new Intent(contextActivity, MediaView.class);
            myIntent.putExtra(MediaView.SUBREDDIT, submission.getSubredditName());
            myIntent.putExtra(
                    EXTRA_SUBMISSION_TITLE,
                    FileUtil.buildDownloadName(submission));
            String url = submission.getUrl();

            if (baseView != null && baseView.lq && SettingValues.loadImageLq && type != ContentType.Type.XKCD) {
                myIntent.putExtra(MediaView.EXTRA_LQ, true);
                myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, baseView.loadedUrl);
            } else if (type == ContentType.Type.IMAGE) {
                // MediaView shows the actual full-resolution image, not the feed's byte-optimized
                // preview/thumbnail. The feed downsizes images for scroll speed; MediaView has no such
                // budget, so it loads the sharp original (submission.getUrl()) directly. Passing it as
                // the display URL routes MediaView through displayImage(), which caches it on disk
                // (cacheOnDisk) and decodes that copy on a return visit, so coming back is instant.
                myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, url);
            } else if (type != ContentType.Type.XKCD) {
                // Non-IMAGE preview types (imgur/deviantart): show the preview source. Null-guard each
                // step of the preview JSON so a malformed/partial node can't NPE the tap.
                final JsonNode source = previewSourceNode(submission.getDataNode());
                if (source != null && source.has("height") && source.has("url")) {
                    myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, source.get("url").asText());
                }
            }
            myIntent.putExtra(MediaView.EXTRA_URL, url);
            PopulateBase.addAdaptorPosition(myIntent, submission, adapterPosition, contextActivity);
            myIntent.putExtra(MediaView.EXTRA_SHARE_URL, submission.getUrl());

            contextActivity.startActivity(myIntent);

        } else {
            LinkUtil.openExternally(submission.getUrl());
        }
    }

    /** The first preview image node, or null if the preview JSON is absent or malformed. */
    private static JsonNode previewImageNode(JsonNode dataNode) {
        if (dataNode == null || !dataNode.has("preview")) {
            return null;
        }
        final JsonNode preview = dataNode.get("preview");
        if (preview == null || !preview.has("images")) {
            return null;
        }
        final JsonNode images = preview.get("images");
        if (images == null || images.size() == 0) {
            return null;
        }
        return images.get(0);
    }

    /** The first preview image's source node, or null if the preview JSON is absent or malformed. */
    private static JsonNode previewSourceNode(JsonNode dataNode) {
        final JsonNode image = previewImageNode(dataNode);
        return (image != null && image.has("source")) ? image.get("source") : null;
    }

    /** The preview image's mp4 variant source URL, or null if it is absent or malformed. */
    private static String previewMp4SourceUrl(JsonNode dataNode) {
        final JsonNode image = previewImageNode(dataNode);
        if (image == null || !image.has("variants")) {
            return null;
        }
        final JsonNode variants = image.get("variants");
        if (variants == null || !variants.has("mp4")) {
            return null;
        }
        final JsonNode mp4 = variants.get("mp4");
        if (mp4 == null || !mp4.has("source")) {
            return null;
        }
        final JsonNode source = mp4.get("source");
        if (source == null || !source.has("url")) {
            return null;
        }
        return source.get("url").asText();
    }

    public static void openGif(
            Activity contextActivity, Submission submission, int adapterPosition) {
        if (SettingValues.gif) {
            DataShare.sharedSubmission = submission;

            Intent myIntent = new Intent(contextActivity, MediaView.class);
            myIntent.putExtra(MediaView.SUBREDDIT, submission.getSubredditName());
            myIntent.putExtra(
                    EXTRA_SUBMISSION_TITLE,
                    FileUtil.buildDownloadName(submission));

            String videoUrl = GifUtils.AsyncLoadGif.getVideoUrlFromSubmission(submission);
            GifUtils.AsyncLoadGif.VideoType t =
                    GifUtils.AsyncLoadGif.getVideoType(videoUrl);
            final String mp4PreviewUrl = previewMp4SourceUrl(submission.getDataNode());

            if (t == GifUtils.AsyncLoadGif.VideoType.VREDDIT) {
                myIntent.putExtra(MediaView.EXTRA_URL, videoUrl);

            } else if (t.shouldLoadPreview() && mp4PreviewUrl != null) {
                myIntent.putExtra(
                        MediaView.EXTRA_URL,
                        StringEscapeUtils.unescapeJson(mp4PreviewUrl).replace("&amp;", "&"));
            } else if (t.shouldLoadPreview()
                    && submission.getDataNode().has("preview")
                    && submission.getDataNode().get("preview").has("reddit_video_preview") // Check if reddit_video_preview exists
                    && submission.getDataNode().get("preview").get("reddit_video_preview").has("fallback_url")
                    && (t != GifUtils.AsyncLoadGif.VideoType.REDGIFS
                            || (submission.getDataNode().get("preview").get("reddit_video_preview").has("has_audio")
                                    && submission.getDataNode().get("preview").get("reddit_video_preview").get("has_audio").asBoolean()))) {
                myIntent.putExtra(
                        MediaView.EXTRA_URL,
                        StringEscapeUtils.unescapeJson(
                            submission.getDataNode().get("preview").get("reddit_video_preview").get("fallback_url").asText()).replace("&amp;", "&"));
            } else if (t == GifUtils.AsyncLoadGif.VideoType.DIRECT
                    && submission.getDataNode().has("media")
                    && submission.getDataNode().get("media").has("reddit_video")
                    && submission.getDataNode().get("media").get("reddit_video").has("fallback_url")) {
                myIntent.putExtra(
                        MediaView.EXTRA_URL,
                        StringEscapeUtils.unescapeJson(
                            submission.getDataNode().get("media").get("reddit_video").get("fallback_url").asText()).replace("&amp;", "&"));
            } else if (t != GifUtils.AsyncLoadGif.VideoType.OTHER) {
                myIntent.putExtra(MediaView.EXTRA_URL, submission.getUrl());
            } else {
                LinkUtil.openUrl(
                    submission.getUrl(),
                    Palette.getColor(submission.getSubredditName()),
                    contextActivity,
                    adapterPosition,
                    submission);
                return;
            }

            // Load the preview image which has probably already been cached in memory instead of the
            // direct link. Null-guard each step of the preview JSON so a malformed node can't NPE.
            final JsonNode gifSource = previewSourceNode(submission.getDataNode());
            if (gifSource != null && gifSource.has("height") && gifSource.has("url")) {
                myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, gifSource.get("url").asText());
            }
            PopulateBase.addAdaptorPosition(myIntent, submission, adapterPosition, contextActivity);
            contextActivity.startActivity(myIntent);
        } else {
            LinkUtil.openExternally(submission.getUrl());
        }
    }
}