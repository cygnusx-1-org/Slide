package me.edgan.redditslide.SubmissionViews;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;


import com.fasterxml.jackson.databind.JsonNode;
import com.google.android.material.snackbar.Snackbar;

import net.dean.jraw.models.Submission;

import java.util.ArrayList;

import me.edgan.redditslide.Activities.Album;
import me.edgan.redditslide.Activities.AlbumPager;
import me.edgan.redditslide.Activities.FullscreenVideo;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.Activities.MainActivity;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Activities.MultiredditOverview;
import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Activities.RedditGallery;
import me.edgan.redditslide.Activities.RedditGalleryPager;
import me.edgan.redditslide.Activities.Search;
import me.edgan.redditslide.Activities.SubredditView;
import me.edgan.redditslide.Activities.Tumblr;
import me.edgan.redditslide.Activities.TumblrPager;
import me.edgan.redditslide.Adapters.SubmissionViewHolder;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.ForceTouch.PeekViewActivity;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.JsonUtil;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.OnSingleClickListener;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.util.SubmissionThumbnailHelper;

/**
 * Handles click actions for Submission views.
 */
public class SubmissionClickActions {

    public static void addClickFunctions(
            final View base,
            final ContentType.Type type,
            final Activity contextActivity,
            final Submission submission,
            final SubmissionViewHolder holder,
            final boolean full) {
        base.setOnClickListener(
                new OnSingleClickListener() {
                    @Override
                    public void onSingleClick(View v) {
                        if (NetworkUtil.isConnected(contextActivity)
                                || (!NetworkUtil.isConnected(contextActivity)
                                        && ContentType.fullImage(type))) {
                            if (SettingValues.storeHistory && !full) {
                                if (!submission.isNsfw() || SettingValues.storeNSFWHistory) {
                                    HasSeen.addSeen(submission.getFullName());
                                    if (contextActivity instanceof MainActivity
                                            || contextActivity instanceof MultiredditOverview
                                            || contextActivity instanceof SubredditView
                                            || contextActivity instanceof Search
                                            || contextActivity instanceof Profile) {
                                        holder.title.setAlpha(0.54f);
                                        holder.body.setAlpha(0.54f);
                                    }
                                }
                            }
                            if (!(contextActivity instanceof PeekViewActivity)
                                    || !((PeekViewActivity) contextActivity).isPeeking()
                                    || (base instanceof HeaderImageLinkView && ((HeaderImageLinkView) base).popped)) {
                                if (!PostMatch.openExternal(submission.getUrl())
                                        || type == ContentType.Type.VIDEO) {
                                    switch (type) {
                                        case STREAMABLE:
                                            if (SettingValues.video) {
                                                Intent myIntent = new Intent(contextActivity, MediaView.class);
                                                myIntent.putExtra(MediaView.SUBREDDIT, submission.getSubredditName());
                                                myIntent.putExtra(MediaView.EXTRA_URL, submission.getUrl());
                                                myIntent.putExtra(EXTRA_SUBMISSION_TITLE, submission.getTitle());
                                                PopulateBase.addAdaptorPosition(myIntent, submission, holder.getBindingAdapterPosition());
                                                contextActivity.startActivity(myIntent);
                                            } else {
                                                LinkUtil.openExternally(submission.getUrl());
                                            }

                                            break;
                                        case IMGUR:
                                        case DEVIANTART:
                                        case XKCD:
                                        case IMAGE:
                                            SubmissionThumbnailHelper.openImage(type, contextActivity, submission, holder.leadImage, holder.getBindingAdapterPosition());
                                            break;
                                        case EMBEDDED:
                                            if (SettingValues.video) {
                                                String data = CompatUtil.fromHtml(submission.getDataNode().get("media_embed").get("content").asText()).toString();
                                                {
                                                    Intent i = new Intent(contextActivity, FullscreenVideo.class);
                                                    i.putExtra(FullscreenVideo.EXTRA_HTML, data);
                                                    contextActivity.startActivity(i);
                                                }
                                            } else {
                                                LinkUtil.openExternally(submission.getUrl());
                                            }
                                            break;
                                        case REDDIT:
                                            SubmissionThumbnailHelper.openRedditContent(submission.getUrl(), contextActivity);
                                            break;
                                        case REDDIT_GALLERY:
                                            if (SettingValues.album) {
                                                Intent i;
                                                if (SettingValues.albumSwipe) {
                                                    i = new Intent(contextActivity, RedditGalleryPager.class);
                                                    i.putExtra(AlbumPager.SUBREDDIT, submission.getSubredditName());
                                                } else {
                                                    i = new Intent(contextActivity, RedditGallery.class);
                                                    i.putExtra(Album.SUBREDDIT, submission.getSubredditName());
                                                }

                                                i.putExtra(EXTRA_SUBMISSION_TITLE, submission.getTitle());
                                                i.putExtra(RedditGallery.SUBREDDIT, submission.getSubredditName());

                                                ArrayList<GalleryImage> urls = new ArrayList<>();

                                                JsonNode dataNode = submission.getDataNode();

                                                if (dataNode.has("gallery_data")) {
                                                    JsonUtil.getGalleryData(dataNode, urls);
                                                } else if (dataNode.has("crosspost_parent_list")) { // Else, try getting crosspost gallery data
                                                    JsonNode crosspost_parent = dataNode.get("crosspost_parent_list").get(0);
                                                    if (crosspost_parent.has("gallery_data")) {
                                                        JsonUtil.getGalleryData(crosspost_parent, urls);
                                                    }
                                                }

                                                Bundle urlsBundle = new Bundle();
                                                urlsBundle.putSerializable(RedditGallery.GALLERY_URLS, urls);
                                                i.putExtras(urlsBundle);

                                                PopulateBase.addAdaptorPosition(i, submission, holder.getBindingAdapterPosition());
                                                contextActivity.startActivity(i);
                                                contextActivity.overridePendingTransition(R.anim.slideright, R.anim.fade_out);
                                            } else {
                                                LinkUtil.openExternally(submission.getUrl());
                                            }
                                            break;
                                        case LINK:
                                            LinkUtil.openUrl(
                                                    submission.getUrl(),
                                                    Palette.getColor(submission.getSubredditName()),
                                                    contextActivity,
                                                    holder.getBindingAdapterPosition(),
                                                    submission);
                                            break;
                                        case SELF:
                                            if (holder != null) {
                                                OnSingleClickListener.override = true;
                                                holder.itemView.performClick();
                                            }
                                            break;
                                        case ALBUM:
                                            if (SettingValues.album) {
                                                Intent i;
                                                if (SettingValues.albumSwipe) {
                                                    i = new Intent(contextActivity, AlbumPager.class);
                                                    i.putExtra(AlbumPager.SUBREDDIT, submission.getSubredditName());
                                                } else {
                                                    i = new Intent(contextActivity, Album.class);
                                                    i.putExtra(Album.SUBREDDIT, submission.getSubredditName());
                                                }

                                                i.putExtra(EXTRA_SUBMISSION_TITLE, submission.getTitle());
                                                i.putExtra(Album.EXTRA_URL, submission.getUrl());

                                                PopulateBase.addAdaptorPosition(i, submission, holder.getBindingAdapterPosition());
                                                contextActivity.startActivity(i);
                                                contextActivity.overridePendingTransition(R.anim.slideright, R.anim.fade_out);
                                            } else {
                                                LinkUtil.openExternally(submission.getUrl());
                                            }
                                            break;
                                        case TUMBLR:
                                            if (SettingValues.album) {
                                                Intent i;
                                                if (SettingValues.albumSwipe) {
                                                    i = new Intent(contextActivity, TumblrPager.class);
                                                    i.putExtra(TumblrPager.SUBREDDIT, submission.getSubredditName());
                                                } else {
                                                    i = new Intent(contextActivity, Tumblr.class);
                                                    i.putExtra(Tumblr.SUBREDDIT, submission.getSubredditName());
                                                }
                                                i.putExtra(Album.EXTRA_URL, submission.getUrl());

                                                PopulateBase.addAdaptorPosition(i, submission, holder.getBindingAdapterPosition());
                                                contextActivity.startActivity(i);
                                                contextActivity.overridePendingTransition(R.anim.slideright, R.anim.fade_out);
                                            } else {
                                                LinkUtil.openExternally(submission.getUrl());
                                            }
                                            break;
                                        case VREDDIT_REDIRECT:
                                        case GIF:
                                        case VREDDIT_DIRECT:
                                            SubmissionThumbnailHelper.openGif(contextActivity, submission, holder.getBindingAdapterPosition());
                                            break;
                                        case NONE:
                                            if (holder != null) {
                                                holder.itemView.performClick();
                                            }

                                            break;
                                        case VIDEO:
                                            if (!LinkUtil.tryOpenWithVideoPlugin(submission.getUrl())) {
                                                LinkUtil.openUrl(submission.getUrl(), Palette.getStatusBarColor(), contextActivity);
                                            }

                                            break;
                                    }
                                } else {
                                    LinkUtil.openExternally(submission.getUrl());
                                }
                            }
                        } else {
                            if (!(contextActivity instanceof PeekViewActivity) || !((PeekViewActivity) contextActivity).isPeeking()) {

                                Snackbar s = Snackbar.make(holder.itemView, R.string.go_online_view_content, Snackbar.LENGTH_SHORT);
                                LayoutUtils.showSnackbar(s);
                            }
                        }
                    }
                });
    }
}