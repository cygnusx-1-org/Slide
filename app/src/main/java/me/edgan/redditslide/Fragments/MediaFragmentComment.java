package me.edgan.redditslide.Fragments;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ProgressBar;
import com.google.gson.Gson;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Activities.ShadowboxComments;
import me.edgan.redditslide.Adapters.CommentUrlObject;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SecretConstants;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionViews.PopulateShadowboxInfo;
import me.edgan.redditslide.Views.ExoVideoView;
import me.edgan.redditslide.Views.SubsamplingScaleImageView;
import me.edgan.redditslide.util.FileUtil;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;
import net.dean.jraw.models.Comment;

/** Created by ccrama on 6/2/2015. */
public class MediaFragmentComment extends BaseMediaFragment {

    public String sub;
    public int i;
    private ExoVideoView videoView;
    private long stopPosition;
    public boolean isGif;
    private CommentUrlObject s;

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.v("Destroying");
        ((SubsamplingScaleImageView) rootView.findViewById(R.id.submission_image)).recycle();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser && videoView != null) {
            videoView.seekTo(0);
            videoView.play();
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
        if (s == null) {
            // Arguments were missing in onCreate; nothing to populate.
            return rootView;
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("position")) {
            stopPosition = savedInstanceState.getLong("position");
        }
        final SlidingUpPanelLayout slideLayout = rootView.findViewById(R.id.sliding_layout);

        if (getActivity() != null) {
            PopulateShadowboxInfo.doActionbar(s.comment, rootView, getActivity(), true);
        }
        (rootView.findViewById(R.id.thumbimage2)).setVisibility(View.GONE);

        ContentType.Type type = ContentType.getContentType(contentUrl);

        if (ContentType.fullImage(type)) {
            (rootView.findViewById(R.id.thumbimage2)).setVisibility(View.GONE);
        }
        addClickFunctions(
                (rootView.findViewById(R.id.submission_image)),
                slideLayout,
                rootView,
                type,
                getActivity(),
                s);
        doLoad(contentUrl);

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
                                ((SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout))
                                        .setPanelHeight(title.getMeasuredHeight());
                                title.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            }
                        });
        ((SlidingUpPanelLayout) rootView.findViewById(R.id.sliding_layout))
                .addPanelSlideListener(
                        new SlidingUpPanelLayout.SimplePanelSlideListener() {
                            @Override
                            public void onPanelStateChanged(
                                    View panel,
                                    SlidingUpPanelLayout.PanelState previousState,
                                    SlidingUpPanelLayout.PanelState newState) {
                                if (newState == SlidingUpPanelLayout.PanelState.EXPANDED) {
                                    final Comment c = s.comment.getComment();
                                    rootView.findViewById(R.id.base)
                                            .setOnClickListener(
                                                    new View.OnClickListener() {
                                                        @Override
                                                        public void onClick(View v) {
                                                            String url =
                                                                    "https://reddit.com"
                                                                            + "/r/"
                                                                            + c.getSubredditName()
                                                                            + "/comments/"
                                                                            + c.getDataNode()
                                                                                    .get("link_id")
                                                                                    .asText()
                                                                                    .substring(3)
                                                                            + "/nothing/"
                                                                            + c.getId()
                                                                            + "?context=3";
                                                            OpenRedditLink.openUrl(
                                                                    getActivity(), url, true);
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
        if (bundle != null) {
            i = bundle.getInt("page");
            // ShadowboxComments.comments is a static list that can be cleared/replaced between
            // launch and fragment creation, so guard the index rather than risk
            // IndexOutOfBoundsException. A null s leaves onCreateView to bail out gracefully.
            if (ShadowboxComments.comments != null
                    && i >= 0
                    && i < ShadowboxComments.comments.size()) {
                s = ShadowboxComments.comments.get(i);
                sub = s.comment.getComment().getSubredditName();
                contentUrl = bundle.getString("contentUrl");
            }
        }
        client = Reddit.client;
        gson = new Gson();
        imgurKey = SecretConstants.getImgurApiKey(getContext());
    }

    public void doLoad(final String contentUrl) {
        switch (ContentType.getContentType(contentUrl)) {
            case DEVIANTART:
                doLoadDeviantArt(contentUrl);
                break;
            case IMAGE:
                doLoadImage(contentUrl);
                break;
            case IMGUR:
                doLoadImgur(contentUrl);
                break;
            case XKCD:
                doLoadXKCD(contentUrl);
                break;
            case STREAMABLE:
            case GIF:
                doLoadGif(contentUrl);
                break;
        }
    }


    private static void addClickFunctions(
            final View base,
            final SlidingUpPanelLayout slidingPanel,
            final View clickingArea,
            final ContentType.Type type,
            final Activity contextActivity,
            final CommentUrlObject submission) {
        base.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (slidingPanel.getPanelState()
                                == SlidingUpPanelLayout.PanelState.EXPANDED) {
                            slidingPanel.setPanelState(SlidingUpPanelLayout.PanelState.COLLAPSED);
                        } else {
                            if (type == ContentType.Type.IMAGE) {
                                if (SettingValues.image) {
                                    Intent myIntent = new Intent(contextActivity, MediaView.class);
                                    String url = submission.getUrl();
                                    myIntent.putExtra(
                                            MediaView.EXTRA_DISPLAY_URL, submission.getUrl());
                                    myIntent.putExtra(MediaView.EXTRA_URL, url);
                                    myIntent.putExtra(
                                            MediaView.SUBREDDIT, submission.getSubredditName());
                                    // May be a bug with downloading multiple comment albums off the
                                    // same submission
                                    myIntent.putExtra(
                                            EXTRA_SUBMISSION_TITLE,
                                            FileUtil.buildDownloadName(
                                                    submission.comment.getComment()));
                                    myIntent.putExtra(
                                            MediaView.EXTRA_SHARE_URL, submission.getUrl());

                                    contextActivity.startActivity(myIntent);
                                } else {
                                    LinkUtil.openExternally(submission.getUrl());
                                }
                            }
                        }
                    }
                });
    }

    public void doLoadGif(final String dat) {
        isGif = true;
        videoView = rootView.findViewById(R.id.gif);
        videoView.clearFocus();
        rootView.findViewById(R.id.gifarea).setVisibility(View.VISIBLE);
        rootView.findViewById(R.id.submission_image).setVisibility(View.GONE);
        final ProgressBar loader = rootView.findViewById(R.id.gifprogress);
        rootView.findViewById(R.id.progress).setVisibility(View.GONE);
        GifUtils.AsyncLoadGif gif =
                new GifUtils.AsyncLoadGif(
                        getActivity(),
                        videoView,
                        loader,
                        rootView.findViewById(R.id.placeholder),
                        false,
                        true,
                        sub);
        gif.submissionTitle =
                FileUtil.buildDownloadName(s.comment.getComment());
        gif.execute(dat);
    }



    @Override
    protected void playGifDirect(String url) {
        doLoadGif(url);
    }

    @Override
    protected void onImgurLoadFailed(String finalUrl) {
        // todo open it?
    }

}
