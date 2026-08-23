package me.edgan.redditslide.SubmissionViews;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
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
import me.edgan.redditslide.Adapters.CardSubmissionViewHolder;
import me.edgan.redditslide.Adapters.SubmissionViewHolder;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.ForceTouch.PeekViewActivity;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.FileUtil;
import me.edgan.redditslide.util.GalleryTiles;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.OnSingleClickListener;
import me.edgan.redditslide.util.SubmissionThumbnailHelper;
import net.dean.jraw.models.Submission;

/**
 * Handles click actions for Submission views.
 */
public class SubmissionClickActions {

    /**
     * Record that the user opened this post and dim its card to match.
     *
     * <p>Shared so that every way of opening a post from a card marks it read the same way. The
     * gallery grid's tiles open the viewer without going through {@link #addClickFunctions}'s
     * listener, and before this was shared they left the post unread — so a gallery opened from a
     * tile never turned up in the read history, while the same post opened from its title did.
     */
    public static void markRead(
            final Activity contextActivity,
            final Submission submission,
            final @Nullable SubmissionViewHolder holder) {
        if (!SettingValues.storeHistory
                || !(holder instanceof CardSubmissionViewHolder cardHolder)) {
            return;
        }
        if (submission.isNsfw() && !SettingValues.storeNSFWHistory) {
            return;
        }
        HasSeen.addSeen(submission.getFullName());
        if (contextActivity instanceof MainActivity
                || contextActivity instanceof MultiredditOverview
                || contextActivity instanceof SubredditView
                || contextActivity instanceof Search
                || contextActivity instanceof Profile) {
            holder.title.setAlpha(0.54f);
            cardHolder.body.setAlpha(0.54f);
        }
    }

    /**
     * Open a Reddit gallery post's viewer on image {@code startIndex}, or hand the post to the
     * browser when the in-app album viewer is switched off.
     *
     * <p>{@code startIndex} is an index into {@link GalleryTiles#imagesFor}, which is also what
     * fills {@link RedditGallery#GALLERY_URLS} here — so a caller that identified an image from that
     * list (the feed's gallery grid) can name it by position. Every other entry point passes 0 and
     * behaves exactly as before.
     *
     * <p>{@code adapterPosition} is the feed row, not an image: it is what lets the viewer offer the
     * "comments" menu item for the post it came from. Pass -1 when there is no row.
     */
    public static void openRedditGallery(
            final Activity contextActivity,
            final Submission submission,
            final int startIndex,
            final int adapterPosition) {
        if (!SettingValues.album) {
            LinkUtil.openExternally(submission.getUrl());
            return;
        }

        final Intent i;
        if (SettingValues.albumSwipe) {
            i = new Intent(contextActivity, RedditGalleryPager.class);
            i.putExtra(AlbumPager.SUBREDDIT, submission.getSubredditName());
        } else {
            i = new Intent(contextActivity, RedditGallery.class);
            i.putExtra(Album.SUBREDDIT, submission.getSubredditName());
        }

        i.putExtra(EXTRA_SUBMISSION_TITLE, FileUtil.buildDownloadName(submission));
        i.putExtra(RedditGallery.SUBREDDIT, submission.getSubredditName());
        i.putExtra(RedditGallery.EXTRA_START_INDEX, startIndex);

        // GalleryTiles resolves the crosspost parent the way the card's display path does, so the
        // list handed to the viewer is the same one the card drew its images from. The grid relies
        // on that: tile k has to be viewer image k.
        final ArrayList<GalleryImage> urls =
                new ArrayList<>(GalleryTiles.imagesFor(submission.getDataNode()));

        final Bundle urlsBundle = new Bundle();
        urlsBundle.putSerializable(RedditGallery.GALLERY_URLS, urls);
        i.putExtras(urlsBundle);

        PopulateBase.addAdaptorPosition(i, submission, adapterPosition, contextActivity);
        contextActivity.startActivity(i);
        contextActivity.overridePendingTransition(R.anim.slideright, R.anim.fade_out);
    }

    public static void addClickFunctions(
            final View base,
            final ContentType.Type type,
            final Activity contextActivity,
            final Submission submission,
            final SubmissionViewHolder holder) {
        base.setOnClickListener(
                new OnSingleClickListener() {
                    @Override
                    public void onSingleClick(View v) {
                        if (NetworkUtil.isConnected(contextActivity)
                                || (!NetworkUtil.isConnected(contextActivity)
                                        && ContentType.fullImage(type))) {
                            markRead(contextActivity, submission, holder);
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
                                                myIntent.putExtra(
                                                        EXTRA_SUBMISSION_TITLE,
                                                        FileUtil.buildDownloadName(submission));
                                                PopulateBase.addAdaptorPosition(myIntent, submission, holder.getBindingAdapterPosition(), contextActivity);
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
                                            String data = CompatUtil.fromHtml(submission.getDataNode().path("media_embed").path("content").asText()).toString();
                                            // No media_embed content to play: FullscreenVideo would
                                            // show a blank WebView, so treat it as an ordinary link.
                                            if (SettingValues.video && !data.isEmpty()) {
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
                                            openRedditGallery(
                                                    contextActivity,
                                                    submission,
                                                    0,
                                                    holder.getBindingAdapterPosition());
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

                                                i.putExtra(
                                                        EXTRA_SUBMISSION_TITLE,
                                                        FileUtil.buildDownloadName(submission));
                                                i.putExtra(Album.EXTRA_URL, submission.getUrl());

                                                PopulateBase.addAdaptorPosition(i, submission, holder.getBindingAdapterPosition(), contextActivity);
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

                                                PopulateBase.addAdaptorPosition(i, submission, holder.getBindingAdapterPosition(), contextActivity);
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