package me.edgan.redditslide.util;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;


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
            myIntent.putExtra(EXTRA_SUBMISSION_TITLE, submission.getTitle());
            String previewUrl;
            String url = submission.getUrl();

            if (baseView != null && baseView.lq && SettingValues.loadImageLq && type != ContentType.Type.XKCD) {
                myIntent.putExtra(MediaView.EXTRA_LQ, true);
                myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, baseView.loadedUrl);
            } else if (submission.getDataNode().has("preview")
                    && submission.getDataNode().get("preview").get("images").get(0).get("source").has("height")
                    && type != ContentType.Type.XKCD) { // Load the preview image which has probably already been cached in memory instead of the direct link
                previewUrl = submission.getDataNode().get("preview").get("images").get(0).get("source").get("url").asText();

                if (baseView == null || (!SettingValues.loadImageLq && baseView.lq)) {
                    myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, previewUrl);
                } else {
                    myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, baseView.loadedUrl);
                }
            }
            myIntent.putExtra(MediaView.EXTRA_URL, url);
            PopulateBase.addAdaptorPosition(myIntent, submission, adapterPosition);
            myIntent.putExtra(MediaView.EXTRA_SHARE_URL, submission.getUrl());

            contextActivity.startActivity(myIntent);

        } else {
            LinkUtil.openExternally(submission.getUrl());
        }
    }

    public static void openGif(
            Activity contextActivity, Submission submission, int adapterPosition) {
        if (SettingValues.gif) {
            DataShare.sharedSubmission = submission;

            Intent myIntent = new Intent(contextActivity, MediaView.class);
            myIntent.putExtra(MediaView.SUBREDDIT, submission.getSubredditName());
            myIntent.putExtra(EXTRA_SUBMISSION_TITLE, submission.getTitle());

            String videoUrl = GifUtils.AsyncLoadGif.getVideoUrlFromSubmission(submission);
            GifUtils.AsyncLoadGif.VideoType t =
                    GifUtils.AsyncLoadGif.getVideoType(videoUrl);

            if (t == GifUtils.AsyncLoadGif.VideoType.VREDDIT) {
                myIntent.putExtra(MediaView.EXTRA_URL, videoUrl);

            } else if (t.shouldLoadPreview()
                    && submission.getDataNode().has("preview")
                    && submission.getDataNode().get("preview").get("images").get(0).has("variants")
                    && submission.getDataNode().get("preview").get("images").get(0).get("variants").has("mp4")) {
                myIntent.putExtra(
                        MediaView.EXTRA_URL,
                        StringEscapeUtils.unescapeJson(
                            submission
                                .getDataNode()
                                .get("preview")
                                .get("images")
                                .get(0)
                                .get("variants")
                                .get("mp4")
                                .get("source")
                                .get("url")
                                .asText()).replace("&amp;", "&"));
            } else if (t.shouldLoadPreview()
                    && submission.getDataNode().has("preview")
                    && submission.getDataNode().get("preview").has("reddit_video_preview") // Check if reddit_video_preview exists
                    && submission.getDataNode().get("preview").get("reddit_video_preview").has("fallback_url")) {
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

            // Load the preview image which has probably already been cached in memory instead of the direct link
            if (submission.getDataNode().has("preview") && submission.getDataNode().get("preview").get("images").get(0).get("source").has("height")) {
                String previewUrl = submission.getDataNode().get("preview").get("images").get(0).get("source").get("url").asText();
                myIntent.putExtra(MediaView.EXTRA_DISPLAY_URL, previewUrl);
            }
            PopulateBase.addAdaptorPosition(myIntent, submission, adapterPosition);
            contextActivity.startActivity(myIntent);
        } else {
            LinkUtil.openExternally(submission.getUrl());
        }
    }
}