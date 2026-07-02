package me.edgan.redditslide.Fragments;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import java.util.ArrayList;
import me.edgan.redditslide.Activities.Album;
import me.edgan.redditslide.Activities.AlbumPager;
import me.edgan.redditslide.Activities.CommentsScreen;
import me.edgan.redditslide.Activities.FullscreenVideo;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Activities.RedditGallery;
import me.edgan.redditslide.Activities.RedditGalleryPager;
import me.edgan.redditslide.Activities.Shadowbox;
import me.edgan.redditslide.Activities.Tumblr;
import me.edgan.redditslide.Activities.TumblrPager;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SecretConstants;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionViews.PopulateShadowboxInfo;
import me.edgan.redditslide.Views.ExoVideoView;
import me.edgan.redditslide.Views.SubsamplingScaleImageView;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.util.FileUtil;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.JsonUtil;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.SubmissionThumbnailHelper;
import net.dean.jraw.models.Submission;
import org.apache.commons.text.StringEscapeUtils;

/** Created by ccrama on 6/2/2015. */
public class MediaFragment extends BaseMediaFragment {

    public String firstUrl;
    public String sub;
    public int i;
    private ExoVideoView videoView;
    private long stopPosition;
    public boolean isGif;
    private GifUtils.AsyncLoadGif gif;
    private Submission s;

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.v("Destroying");
        if (rootView != null && rootView.findViewById(R.id.submission_image) != null) {
            ((SubsamplingScaleImageView) rootView.findViewById(R.id.submission_image)).recycle();
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (videoView != null) {
            if (isVisibleToUser) {
                videoView.seekTo(0);
                videoView.play();
            } else {
                videoView.pause();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (videoView != null) {
            videoView.seekTo((int) stopPosition);
            videoView.play();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (videoView != null) {
            stopPosition = videoView.getCurrentPosition();
            videoView.pause();
            ((SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout))
                    .setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
            outState.putLong("position", stopPosition);
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = (ViewGroup) inflater.inflate(R.layout.submission_mediacard, container, false);
        if (savedInstanceState != null && savedInstanceState.containsKey("position")) {
            stopPosition = savedInstanceState.getLong("position");
        }

        PopulateShadowboxInfo.doActionbar(s, rootView, getActivity(), true);
        View thumbnailView = (rootView.findViewById(R.id.thumbimage2));

        thumbnailView.setVisibility(View.GONE);

        ImageView typeImage = rootView.findViewById(R.id.type);
        typeImage.setVisibility(View.VISIBLE);
        SubsamplingScaleImageView img = rootView.findViewById(R.id.submission_image);

        final SlidingUpPanelLayout slideLayout = rootView.findViewById(R.id.sliding_layout);
        ContentType.Type type = ContentType.getContentType(s);

        if (type == ContentType.Type.VREDDIT_REDIRECT || type == ContentType.Type.VREDDIT_DIRECT) {
            if ((!s.getDataNode().has("media") || !s.getDataNode().get("media").has("reddit_video"))
                    && !s.getDataNode().has("crosspost_parent_list")) {
                type = ContentType.Type.LINK;
            }
        }

        img.setAlpha(1f);

        if (Strings.isNullOrEmpty(s.getThumbnail())
                || Strings.isNullOrEmpty(firstUrl)
                || (s.isNsfw() && SettingValues.getIsNSFWEnabled())) {
            thumbnailView.setVisibility(View.VISIBLE);
            ((ImageView) thumbnailView).setImageResource(R.drawable.web);
            addClickFunctions(thumbnailView, slideLayout, rootView, type, getActivity(), s);
            addClickFunctions(typeImage, slideLayout, rootView, type, getActivity(), s);
            (rootView.findViewById(R.id.progress)).setVisibility(View.GONE);

            if ((s.isNsfw() && SettingValues.getIsNSFWEnabled())) {
                ((ImageView) thumbnailView).setImageResource(R.drawable.nsfw);
            } else {
                if (Strings.isNullOrEmpty(firstUrl) && !Strings.isNullOrEmpty(s.getThumbnail())) {
                    ((Reddit) getContext().getApplicationContext())
                            .getImageLoader()
                            .displayImage(s.getThumbnail(), ((ImageView) thumbnailView));
                }
            }

        } else {
            thumbnailView.setVisibility(View.GONE);
            addClickFunctions(img, slideLayout, rootView, type, getActivity(), s);
        }

        if (!s.isNsfw() || !SettingValues.getIsNSFWEnabled()) {
            if (type == ContentType.Type.EXTERNAL
                    || type == ContentType.Type.LINK
                    || type == ContentType.Type.REDDIT
                    || type == ContentType.Type.VIDEO) {
                // REDDIT posts link to another Reddit page (post/comment/subreddit), not media.
                // Load the post's preview image like a LINK; the destination is only opened when
                // the user taps (addClickFunctions -> openRedditContent). Loading contentUrl here
                // would fetch the linked page, see text/html, and open the internal browser over
                // the post.
                doLoad(firstUrl, type);
            } else {
                doLoad(contentUrl, type);
            }
        }

        switch (type) {
            case ALBUM:
            case REDDIT_GALLERY:
                typeImage.setImageResource(R.drawable.ic_photo_library);
                break;
            case EXTERNAL:
            case LINK:
            case REDDIT:
                typeImage.setImageResource(R.drawable.ic_public);
                rootView.findViewById(R.id.submission_image).setAlpha(0.5f);
                break;
            case SELF:
                typeImage.setImageResource(R.drawable.ic_text_fields);
                break;
            case EMBEDDED:
            case VIDEO:
                typeImage.setImageResource(R.drawable.ic_play_arrow);
                rootView.findViewById(R.id.submission_image).setAlpha(0.5f);
                break;
            default:
                typeImage.setVisibility(View.GONE);
                break;
        }

        rootView.findViewById(R.id.base)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {

                                Intent i2 = new Intent(getActivity(), CommentsScreen.class);
                                i2.putExtra(CommentsScreen.EXTRA_PAGE, i);
                                i2.putExtra(CommentsScreen.EXTRA_SUBREDDIT, sub);
                                getActivity().startActivity(i2);
                            }
                        });
        final View.OnClickListener openClick =
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout))
                                .setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
                    }
                };
        rootView.findViewById(R.id.base).setOnClickListener(openClick);
        final View title = rootView.findViewById(R.id.title);
        title.getViewTreeObserver()
                .addOnGlobalLayoutListener(
                        new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                slideLayout.setPanelHeight(title.getMeasuredHeight());
                                title.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            }
                        });
        slideLayout.addPanelSlideListener(
                new SlidingUpPanelLayout.SimplePanelSlideListener() {
                    @Override
                    public void onPanelStateChanged(
                            View panel,
                            SlidingUpPanelLayout.PanelState previousState,
                            SlidingUpPanelLayout.PanelState newState) {
                        if (newState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                            rootView.findViewById(R.id.base)
                                    .setOnClickListener(
                                            new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    Intent i2 =
                                                            new Intent(
                                                                    getActivity(),
                                                                    CommentsScreen.class);
                                                    i2.putExtra(CommentsScreen.EXTRA_PAGE, i);
                                                    i2.putExtra(
                                                            CommentsScreen.EXTRA_SUBREDDIT, sub);
                                                    getActivity().startActivity(i2);
                                                }
                                            });
                        } else {
                            rootView.findViewById(R.id.base).setOnClickListener(openClick);
                        }
                    }
                });
        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = this.getArguments();
        firstUrl = bundle.getString("firstUrl");
        sub = ((Shadowbox) getActivity()).subreddit;
        i = bundle.getInt("page");
        if (((Shadowbox) getActivity()).subredditPosts.getPosts().size() != 0) {
            s = ((Shadowbox) getActivity()).subredditPosts.getPosts().get(i);
        } else {
            getActivity().finish();
        }
        contentUrl = bundle.getString("contentUrl");

        client = Reddit.client;
        gson = new Gson();
        imgurKey = SecretConstants.getImgurApiKey(getContext());
    }

    public void doLoad(final String contentUrl, ContentType.Type type) {
        switch (type) {
            case DEVIANTART:
                doLoadDeviantArt(contentUrl);
                break;
            case IMAGE:
            case LINK:
            case REDDIT:
                doLoadImage(contentUrl);
                break;
            case IMGUR:
                doLoadImgur(contentUrl);
                break;
            case XKCD:
                doLoadXKCD(contentUrl);
                break;
            case STREAMABLE:
            case VREDDIT_REDIRECT:
            case VREDDIT_DIRECT:
            case GIF:
                doLoadGif(s);
                break;
        }
    }

    private static void addClickFunctions(
            final View base,
            final SlidingUpPanelLayout slidingPanel,
            final View clickingArea,
            final ContentType.Type type,
            final Activity contextActivity,
            final Submission submission) {
        base.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (slidingPanel.getPanelState()
                                == SlidingUpPanelLayout.PanelState.EXPANDED) {
                            slidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
                        } else {
                            switch (type) {
                                case STREAMABLE:
                                    if (SettingValues.video) {
                                        Intent myIntent =
                                                new Intent(contextActivity, MediaView.class);
                                        myIntent.putExtra(MediaView.EXTRA_URL, submission.getUrl());
                                        myIntent.putExtra(
                                                MediaView.SUBREDDIT, submission.getSubredditName());
                                        contextActivity.startActivity(myIntent);

                                    } else {
                                        LinkUtil.openExternally(submission.getUrl());
                                    }

                                case EMBEDDED:
                                    if (SettingValues.video) {
                                        LinkUtil.openExternally(submission.getUrl());
                                        String data =
                                                submission
                                                        .getDataNode()
                                                        .get("media_embed")
                                                        .get("content")
                                                        .asText();
                                        {
                                            Intent i =
                                                    new Intent(
                                                            contextActivity, FullscreenVideo.class);
                                            i.putExtra(FullscreenVideo.EXTRA_HTML, data);
                                            contextActivity.startActivity(i);
                                        }
                                    } else {
                                        LinkUtil.openExternally(submission.getUrl());
                                    }
                                    break;
                                case REDDIT:
                                    SubmissionThumbnailHelper.openRedditContent(
                                            submission.getUrl(), contextActivity);

                                    break;
                                case LINK:
                                    LinkUtil.openUrl(
                                            submission.getUrl(),
                                            Palette.getColor(submission.getSubredditName()),
                                            contextActivity);

                                    break;
                                case SELF:
                                case NONE:
                                    break;
                                case ALBUM:
                                    if (SettingValues.album) {
                                        Intent i;
                                        if (SettingValues.albumSwipe) {
                                            i = new Intent(contextActivity, AlbumPager.class);
                                            i.putExtra(Album.EXTRA_URL, submission.getUrl());
                                            i.putExtra(
                                                    AlbumPager.SUBREDDIT,
                                                    submission.getSubredditName());
                                        } else {
                                            i = new Intent(contextActivity, Album.class);
                                            i.putExtra(Album.EXTRA_URL, submission.getUrl());
                                            i.putExtra(
                                                    Album.SUBREDDIT, submission.getSubredditName());
                                        }
                                        i.putExtra(
                                                EXTRA_SUBMISSION_TITLE,
                                                FileUtil.buildDownloadName(submission));
                                        contextActivity.startActivity(i);
                                    } else {
                                        LinkUtil.openExternally(submission.getUrl());
                                    }
                                    break;
                                case REDDIT_GALLERY:
                                    if (SettingValues.album) {
                                        Intent i;
                                        if (SettingValues.albumSwipe) {
                                            i =
                                                    new Intent(
                                                            contextActivity,
                                                            RedditGalleryPager.class);
                                            i.putExtra(
                                                    AlbumPager.SUBREDDIT,
                                                    submission.getSubredditName());
                                        } else {
                                            i = new Intent(contextActivity, RedditGallery.class);
                                            i.putExtra(
                                                    Album.SUBREDDIT, submission.getSubredditName());
                                        }
                                        i.putExtra(
                                                EXTRA_SUBMISSION_TITLE,
                                                FileUtil.buildDownloadName(submission));

                                        i.putExtra(
                                                RedditGallery.SUBREDDIT,
                                                submission.getSubredditName());

                                        ArrayList<GalleryImage> urls = new ArrayList<>();

                                        JsonNode dataNode = submission.getDataNode();
                                        if (dataNode.has("gallery_data")) {
                                            JsonUtil.getGalleryData(dataNode, urls);
                                        }

                                        Bundle urlsBundle = new Bundle();
                                        urlsBundle.putSerializable(
                                                RedditGallery.GALLERY_URLS, urls);
                                        LogUtil.v("Opening gallery with " + urls.size());
                                        i.putExtras(urlsBundle);

                                        contextActivity.startActivity(i);
                                    } else {
                                        LinkUtil.openExternally(submission.getUrl());
                                    }
                                    break;
                                case TUMBLR:
                                    if (SettingValues.image) {
                                        Intent i;
                                        if (SettingValues.albumSwipe) {
                                            i = new Intent(contextActivity, TumblrPager.class);
                                            i.putExtra(Album.EXTRA_URL, submission.getUrl());
                                            i.putExtra(
                                                    TumblrPager.SUBREDDIT,
                                                    submission.getSubredditName());
                                        } else {
                                            i = new Intent(contextActivity, Tumblr.class);
                                            i.putExtra(Album.EXTRA_URL, submission.getUrl());
                                            i.putExtra(
                                                    Tumblr.SUBREDDIT,
                                                    submission.getSubredditName());
                                        }
                                        contextActivity.startActivity(i);
                                    } else {
                                        LinkUtil.openExternally(submission.getUrl());
                                    }
                                    break;
                                case DEVIANTART:
                                case XKCD:
                                case IMAGE:
                                    SubmissionThumbnailHelper.openImage(
                                            type, contextActivity, submission, null, -1);
                                    break;
                                case GIF:
                                    SubmissionThumbnailHelper.openGif(
                                            contextActivity, submission, -1);
                                    break;
                                case VIDEO:
                                    if (!LinkUtil.tryOpenWithVideoPlugin(submission.getUrl())) {
                                        LinkUtil.openUrl(
                                                submission.getUrl(),
                                                Palette.getStatusBarColor(),
                                                contextActivity);
                                    }
                            }
                        }
                    }
                });
    }

    public void doLoadGif(final Submission s) {
        isGif = true;
        videoView = rootView.findViewById(R.id.gif);
        videoView.clearFocus();
        rootView.findViewById(R.id.gifarea).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.submission_image).setVisibility(View.GONE);
        final ProgressBar loader = rootView.findViewById(R.id.gifprogress);
        gif =
                new GifUtils.AsyncLoadGif(
                        getActivity(),
                        videoView,
                        loader,
                        rootView.findViewById(R.id.placeholder),
                        false,
                        !(getActivity() instanceof Shadowbox)
                                || ((Shadowbox) (getActivity())).pager.getCurrentItem() == i,
                        sub);
        String videoUrl = GifUtils.AsyncLoadGif.getVideoUrlFromSubmission(s);
        GifUtils.AsyncLoadGif.VideoType t = GifUtils.AsyncLoadGif.getVideoType(videoUrl);

        String toLoadURL;
        if (t == GifUtils.AsyncLoadGif.VideoType.VREDDIT) {
            toLoadURL = videoUrl;

        } else if ((t.shouldLoadPreview()
                && s.getDataNode().has("preview")
                && s.getDataNode().get("preview").get("images").get(0).has("variants")
                && s.getDataNode()
                        .get("preview")
                        .get("images")
                        .get(0)
                        .get("variants")
                        .has("mp4"))) {
            toLoadURL =
                    StringEscapeUtils.unescapeJson(
                                    s.getDataNode()
                                            .get("preview")
                                            .get("images")
                                            .get(0)
                                            .get("variants")
                                            .get("mp4")
                                            .get("source")
                                            .get("url")
                                            .asText())
                            .replace("&amp;", "&");
        } else if (t.shouldLoadPreview()
                && s.getDataNode().has("preview")
                && s.getDataNode().get("preview").has("reddit_video_preview")
                && (t != GifUtils.AsyncLoadGif.VideoType.REDGIFS
                        || (s.getDataNode()
                                        .get("preview")
                                        .get("reddit_video_preview")
                                        .has("has_audio")
                                && s.getDataNode()
                                        .get("preview")
                                        .get("reddit_video_preview")
                                        .get("has_audio")
                                        .asBoolean()))) {
            toLoadURL =
                    StringEscapeUtils.unescapeJson(
                            s.getDataNode()
                                    .get("preview")
                                    .get("reddit_video_preview")
                                    .get("dash_url")
                                    .asText());
        } else if (t == GifUtils.AsyncLoadGif.VideoType.DIRECT
                && s.getDataNode().has("media")
                && s.getDataNode().get("media").has("reddit_video")
                && s.getDataNode().get("media").get("reddit_video").has("fallback_url")) {
            toLoadURL =
                    StringEscapeUtils.unescapeJson(
                                    s.getDataNode()
                                            .get("media")
                                            .get("reddit_video")
                                            .get("fallback_url")
                                            .asText())
                            .replace("&amp;", "&");

        } else if (t != GifUtils.AsyncLoadGif.VideoType.OTHER) {
            toLoadURL = s.getUrl();
        } else {
            doLoadImage(firstUrl);
            return;
        }
        gif.execute(toLoadURL);
        rootView.findViewById(R.id.progress).setVisibility(View.GONE);
    }

    public void doLoadGifDirect(final String s) {
        isGif = true;
        videoView = rootView.findViewById(R.id.gif);
        videoView.clearFocus();
        rootView.findViewById(R.id.gifarea).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.submission_image).setVisibility(View.GONE);
        final ProgressBar loader = rootView.findViewById(R.id.gifprogress);
        gif =
                new GifUtils.AsyncLoadGif(
                        getActivity(),
                        videoView,
                        loader,
                        rootView.findViewById(R.id.placeholder),
                        false,
                        !(getActivity() instanceof Shadowbox)
                                || ((Shadowbox) (getActivity())).pager.getCurrentItem() == i,
                        sub);

        gif.execute(s);
        rootView.findViewById(R.id.progress).setVisibility(View.GONE);
    }

    @Override
    protected void playGifDirect(String url) {
        doLoadGifDirect(url);
    }

    @Override
    protected String prepareUrl(String url) {
        return StringEscapeUtils.unescapeHtml4(url);
    }

}
