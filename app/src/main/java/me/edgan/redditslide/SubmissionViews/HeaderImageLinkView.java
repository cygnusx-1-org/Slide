package me.edgan.redditslide.SubmissionViews;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;

import com.cocosw.bottomsheet.BottomSheet;
import com.fasterxml.jackson.databind.JsonNode;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.ForceTouch.PeekView;
import me.edgan.redditslide.ForceTouch.PeekViewActivity;
import me.edgan.redditslide.ForceTouch.builder.Peek;
import me.edgan.redditslide.ForceTouch.builder.PeekViewOptions;
import me.edgan.redditslide.ForceTouch.callback.OnButtonUp;
import me.edgan.redditslide.ForceTouch.callback.OnPop;
import me.edgan.redditslide.ForceTouch.callback.OnRemove;
import me.edgan.redditslide.ForceTouch.callback.SimpleOnPeek;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.PeekMediaView;
import me.edgan.redditslide.Views.TransparentTagTextView;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;

import net.dean.jraw.models.Submission;

import java.util.Arrays;
import java.util.List;

/** Created by carlo_000 on 2/7/2016. */
public class HeaderImageLinkView extends RelativeLayout {
    public String loadedUrl;
    public boolean lq;
    public ImageView thumbImage2;
    public TextView secondTitle;
    public TextView secondSubTitle;
    public View wrapArea;
    String lastDone = "";
    ContentType.Type type;
    DisplayImageOptions bigOptions =
            new DisplayImageOptions.Builder()
                    .resetViewBeforeLoading(false)
                    .cacheOnDisk(true)
                    .imageScaleType(ImageScaleType.EXACTLY)
                    .cacheInMemory(false)
                    .displayer(new FadeInBitmapDisplayer(250))
                    .build();
    boolean clickHandled;
    Handler handler;
    MotionEvent event;
    Runnable longClicked;
    float position;
    private TextView title;
    private TextView info;
    public ImageView backdrop;
    private boolean forceThumb;

    private static final List<String> PLACEHOLDER_URLS =
            Arrays.asList("self", "default", "image", "nsfw", "spoiler", "");

    public HeaderImageLinkView(Context context) {
        super(context);
        init();
    }

    public HeaderImageLinkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HeaderImageLinkView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    boolean thumbUsed;

    public void doImageAndText(final Submission submission, boolean full, String baseSub, boolean news) {
        backdrop.setLayoutParams(new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        boolean fullImage = ContentType.fullImage(type);
        thumbUsed = false;

        setVisibility(View.VISIBLE);
        String url = "";
        boolean forceThumb = false;
        thumbImage2.setImageResource(android.R.color.transparent);

        boolean loadLq =
                (((!NetworkUtil.isConnectedWifi(getContext()) && SettingValues.lowResMobile)
                        || SettingValues.lowResAlways));

        JsonNode dataNode = submission.getDataNode();
        JsonNode spoiler = (dataNode != null) ? dataNode.get("spoiler") : null;
        JsonNode thumbnail = (dataNode != null) ? dataNode.get("thumbnail") : null;

        if (type == ContentType.Type.SELF && SettingValues.hideSelftextLeadImage
                || SettingValues.noImages && submission.isSelfPost()) {
            setVisibility(View.GONE);
            if (wrapArea != null) wrapArea.setVisibility(View.GONE);
            thumbImage2.setVisibility(View.GONE);
        } else {
            if (submission.getThumbnails() != null && submission.getThumbnails().getSource() != null) {
                int height = submission.getThumbnails().getSource().getHeight();
                int width = submission.getThumbnails().getSource().getWidth();
                setBackdropLayoutParams(height, width, full, fullImage, type);
            } else if (type == ContentType.Type.REDDIT_GALLERY) {
                if (full) {
                    setFixedHeightLayoutParams(200);
                }
            }

            Submission.ThumbnailType thumbnailType;
            if (!submission.getDataNode().get("thumbnail").isNull()) {
                thumbnailType = submission.getThumbnailType();
            } else {
                thumbnailType = Submission.ThumbnailType.NONE;
            }

            if (!SettingValues.ignoreSubSetting
                    && dataNode != null
                    && dataNode.has("sr_detail")
                    && dataNode.get("sr_detail").has("show_media")
                    && !dataNode.get("sr_detail").get("show_media").asBoolean()) {
                thumbnailType = Submission.ThumbnailType.NONE;
            }

            LogUtil.v(type.toString());

            if (SettingValues.noImages && loadLq) {
                setVisibility(View.GONE);
                if (!full && !submission.isSelfPost()) {
                    thumbImage2.setVisibility(View.VISIBLE);
                } else {
                    if (full && !submission.isSelfPost()) wrapArea.setVisibility(View.VISIBLE);
                }
                thumbImage2.setImageDrawable(
                        ContextCompat.getDrawable(getContext(), R.drawable.web));
                thumbUsed = true;
            } else if (submission.isNsfw() && SettingValues.getIsNSFWEnabled()
                    || (baseSub != null
                            && submission.isNsfw()
                            && SettingValues.hideNSFWCollection
                            && (baseSub.equals("frontpage")
                                    || baseSub.equals("all")
                                    || baseSub.contains("+")
                                    || baseSub.equals("popular")))) {
                handleSpecialSubmissionType(submission, full, forceThumb, R.drawable.nsfw);
            } else if (submission.getDataNode().get("spoiler").asBoolean()) {
                handleSpecialSubmissionType(submission, full, forceThumb, R.drawable.spoiler);
            } else if (type == ContentType.Type.ALBUM
                    || type == ContentType.Type.GIF
                    || type == ContentType.Type.LINK
                    || type == ContentType.Type.REDDIT
                    || type == ContentType.Type.TUMBLR
                    || type == ContentType.Type.STREAMABLE
                    || type == ContentType.Type.XKCD) {
                handleTypes(submission, baseSub, full);
            } else if (type == ContentType.Type.REDDIT_GALLERY) {
                handleRedditGalleryType(submission, baseSub, full, forceThumb);
            } else if (type == ContentType.Type.VREDDIT_DIRECT || type == ContentType.Type.VREDDIT_REDIRECT) {
                handleVRedditType(submission, baseSub, full, forceThumb);
            } else if (type != ContentType.Type.IMAGE
                            && type != ContentType.Type.SELF
                            && (!thumbnail.isNull()
                                    && (thumbnailType != Submission.ThumbnailType.URL))
                    || thumbnail.asText().isEmpty() && !submission.isSelfPost()) {
                setVisibility(View.GONE);
                if (!full) {
                    thumbImage2.setVisibility(View.VISIBLE);
                } else {
                    wrapArea.setVisibility(View.VISIBLE);
                }

                thumbImage2.setImageDrawable(
                        ContextCompat.getDrawable(getContext(), R.drawable.web));
                thumbUsed = true;
                loadedUrl = submission.getUrl();
            } else if (type == ContentType.Type.IMAGE
                    && !thumbnail.isNull()
                    && !thumbnail.asText().isEmpty()) {
                handleImageType(submission, baseSub, full, forceThumb, loadLq);
            } else if (submission.getThumbnails() != null) {
                handleThumbnailDisplay(submission, full, forceThumb, loadLq, baseSub, news);
            } else if (!thumbnail.isNull()
                    && submission.getThumbnail() != null
                    && (submission.getThumbnailType() == Submission.ThumbnailType.URL
                            || (!thumbnail.isNull()
                                    && submission.isNsfw()
                                    && SettingValues.getIsNSFWEnabled()))) {
                        url = submission.getThumbnail();
                setThumbAndWrapVisibility(full, true);
                loadedUrl = url;

                ((Reddit) getContext().getApplicationContext())
                        .getImageLoader()
                        .displayImage(url, thumbImage2);
                setVisibility(View.GONE);

            } else {
                setThumbAndWrapVisibility(full, false);
                setVisibility(View.GONE);
            }

            setupTitleAndBottomSheet(submission, full, forceThumb, type);

            if (SettingValues.smallTag != 0 && !full && !news) {
                title = findViewById(R.id.tag);
                findViewById(R.id.tag).setVisibility(View.VISIBLE);
                info = null;
            } else {
                findViewById(R.id.tag).setVisibility(View.GONE);
                title.setVisibility(View.VISIBLE);
                info.setVisibility(View.VISIBLE);
            }

            if (SettingValues.smallTag != 0 && !full && !news) {
                ((TransparentTagTextView) title).init(getContext());
            }

            title.setText(ContentType.getContentDescription(submission, getContext()));

            if (info != null) info.setText(submission.getDomain());
        }
    }

    public int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    boolean popped;

    public double getHeightFromAspectRatio(int imageHeight, int imageWidth) {
        double ratio = (double) imageHeight / (double) imageWidth;
        double width = getWidth();
        return (width * ratio);
    }

    public void onLinkLongClick(final String url, MotionEvent event, final Submission submission) {
        popped = false;

        if (url == null || SettingValues.noPreviewImageLongClick) {
            return;
        }

        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Activity activity = null;
        final Context context = getContext();

        if (context instanceof Activity) {
            activity = (Activity) context;
        } else if (context instanceof ContextThemeWrapper) {
            activity = (Activity) ((ContextThemeWrapper) context).getBaseContext();
        } else if (context instanceof ContextWrapper) {
            Context context1 = ((ContextWrapper) context).getBaseContext();
            if (context1 instanceof Activity) {
                activity = (Activity) context1;
            } else if (context1 instanceof ContextWrapper) {
                Context context2 = ((ContextWrapper) context1).getBaseContext();
                if (context2 instanceof Activity) {
                    activity = (Activity) context2;
                } else if (context2 instanceof ContextWrapper) {
                    activity = (Activity) ((ContextThemeWrapper) context2).getBaseContext();
                }
            }
        } else {
            throw new RuntimeException("Could not find activity from context:" + context);
        }

        if (activity != null && !activity.isFinishing()) {
            if (SettingValues.peek) {
                Peek.into(
                        R.layout.peek_view_submission,
                        new SimpleOnPeek() {
                            @Override
                            public void onInflated(final PeekView peekView, final View rootView) {
                                TextView text = rootView.findViewById(R.id.title);
                                text.setText(url);
                                text.setTextColor(Color.WHITE);

                                ((PeekMediaView) rootView.findViewById(R.id.peek))
                                        .setUrlWithSubmission(url, submission);

                                        peekView.addButton(
                                                (R.id.share),
                                                new OnButtonUp() {
                                                    @Override
                                                    public void onButtonUp() {
                                                        Reddit.defaultShareText(
                                                                "", url, rootView.getContext());
                                                    }
                                                });

                                        peekView.addButton(
                                                (R.id.upvoteb),
                                                new OnButtonUp() {
                                                    @Override
                                                    public void onButtonUp() {
                                                        ((View) getParent())
                                                                .findViewById(R.id.upvote)
                                                                .callOnClick();
                                                    }
                                                });

                                        peekView.setOnRemoveListener(
                                                new OnRemove() {
                                                    @Override
                                                    public void onRemove() {
                                                        ((PeekMediaView)
                                                                        rootView.findViewById(
                                                                                R.id.peek))
                                                                .doClose();
                                                    }
                                                });

                                        peekView.addButton(
                                                (R.id.comments),
                                                new OnButtonUp() {
                                                    @Override
                                                    public void onButtonUp() {
                                                        ((View) getParent().getParent())
                                                                .callOnClick();
                                                    }
                                                });

                                        peekView.setOnPop(
                                                new OnPop() {
                                                    @Override
                                                    public void onPop() {
                                                        popped = true;
                                                        callOnClick();
                                                    }
                                                });
                                    }
                                })
                        .with(new PeekViewOptions().setFullScreenPeek(true))
                        .show((PeekViewActivity) activity, event);
            } else {
                BottomSheet.Builder b = new BottomSheet.Builder(activity).title(url).grid();
                int[] attrs = new int[] {R.attr.tintColor};
                TypedArray ta = getContext().obtainStyledAttributes(attrs);

                int color = ta.getColor(0, Color.WHITE);
                Drawable open = getResources().getDrawable(R.drawable.ic_open_in_new);
                Drawable share = getResources().getDrawable(R.drawable.ic_share);
                Drawable copy = getResources().getDrawable(R.drawable.ic_content_copy);
                final List<Drawable> drawableSet = Arrays.asList(open, share, copy);
                BlendModeUtil.tintDrawablesAsSrcAtop(drawableSet, color);

                ta.recycle();

                b.sheet(R.id.open_link, open, getResources().getString(R.string.open_externally));
                b.sheet(R.id.share_link, share, getResources().getString(R.string.share_link));
                b.sheet(
                        R.id.copy_link,
                        copy,
                        getResources().getString(R.string.submission_link_copy));
                final Activity finalActivity = activity;
                b.listener(
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        switch (which) {
                                            case R.id.open_link:
                                                LinkUtil.openExternally(url);
                                                break;
                                            case R.id.share_link:
                                                Reddit.defaultShareText("", url, finalActivity);
                                                break;
                                            case R.id.copy_link:
                                                LinkUtil.copyUrl(url, finalActivity);
                                                break;
                                        }
                                    }
                                })
                        .show();
            }
        }
    }

    public void setBottomSheet(View v, final Submission submission, final boolean full) {
        handler = new Handler();
        v.setOnTouchListener(
                new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        int x = (int) event.getX();
                        int y = (int) event.getY();
                        x += getScrollX();
                        y += getScrollY();

                        HeaderImageLinkView.this.event = event;

                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            position = event.getY(); // used to see if the user scrolled or not
                        }
                        if (!(event.getAction() == MotionEvent.ACTION_UP
                                || event.getAction() == MotionEvent.ACTION_DOWN)) {
                            if (Math.abs((position - event.getY())) > 25) {
                                handler.removeCallbacksAndMessages(null);
                            }
                            return false;
                        }

                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                clickHandled = false;
                                if (SettingValues.peek) {
                                    handler.postDelayed(
                                            longClicked,
                                            android.view.ViewConfiguration.getTapTimeout() + 50);
                                } else {
                                    handler.postDelayed(
                                            longClicked,
                                            android.view.ViewConfiguration.getLongPressTimeout());
                                }

                                break;
                            case MotionEvent.ACTION_UP:
                                handler.removeCallbacksAndMessages(null);

                                if (!clickHandled) {
                                    // regular click
                                    callOnClick();
                                }
                                break;
                        }
                        return true;
                    }
                });
        longClicked =
                new Runnable() {
                    @Override
                    public void run() {
                        // long click
                        clickHandled = true;

                        handler.removeCallbacksAndMessages(null);
                        if (SettingValues.storeHistory && !full) {
                            if (!submission.isNsfw() || SettingValues.storeNSFWHistory) {
                                HasSeen.addSeen(submission.getFullName());
                                ((View) getParent()).findViewById(R.id.title).setAlpha(0.54f);
                                ((View) getParent()).findViewById(R.id.body).setAlpha(0.54f);
                            }
                        }
                        onLinkLongClick(submission.getUrl(), event, submission);
                    }
                };
    }

    public void setSubmission(
            final Submission submission,
            final boolean full,
            String baseSub,
            ContentType.Type type) {
        this.type = type;
        if (!lastDone.equals(submission.getFullName())) {
            lq = false;
            lastDone = submission.getFullName();
            backdrop.setImageResource(
                    android.R.color
                            .transparent); // reset the image view in case the placeholder is still
            // visible
            thumbImage2.setImageResource(android.R.color.transparent);
            doImageAndText(submission, full, baseSub, false);
        }
    }

    public void setSubmissionNews(
            final Submission submission,
            final boolean full,
            String baseSub,
            ContentType.Type type) {
        this.type = type;
        if (!lastDone.equals(submission.getFullName())) {
            lq = false;
            lastDone = submission.getFullName();
            backdrop.setImageResource(
                    android.R.color
                            .transparent); // reset the image view in case the placeholder is still
            // visible
            thumbImage2.setImageResource(android.R.color.transparent);
            doImageAndText(submission, full, baseSub, true);
        }
    }

    public void setThumbnail(ImageView v) {
        thumbImage2 = v;
    }

    public void setUrl(String url) {}

    public void setWrapArea(View v) {
        wrapArea = v;
        secondTitle = v.findViewById(R.id.contenttitle);
        secondSubTitle = v.findViewById(R.id.contenturl);
    }

    private void init() {
        inflate(getContext(), R.layout.header_image_title_view, this);
        this.title = findViewById(R.id.textimage);
        this.info = findViewById(R.id.subtextimage);
        this.backdrop = findViewById(R.id.leadimage);
    }

    private void handleTypes(Submission submission, String baseSub, boolean full) {
        JsonNode dataNode = submission.getDataNode();
        String url = submission.getUrl();
        String redditPreviewUrl = null;

        // Check for preview data
        if (dataNode.has("preview") && !dataNode.get("preview").isNull()) {
            JsonNode previewNode = dataNode.get("preview").get("images");
            if (previewNode != null && previewNode.size() > 0) {
                JsonNode sourceNode = previewNode.get(0).get("source");
                if (sourceNode != null && sourceNode.has("url")) {
                    redditPreviewUrl = sourceNode.get("url").asText();
                }
            }
        }

        // Validate and use Reddit preview URL if available
        boolean hasValidPreview = false;
        if (redditPreviewUrl != null && !redditPreviewUrl.isEmpty()) {
            url = redditPreviewUrl;
            hasValidPreview = true;
        } else if (dataNode.has("thumbnail") && !dataNode.get("thumbnail").isNull()) {
            String thumbnail = dataNode.get("thumbnail").asText();
            // Check if thumbnail is a valid URL and not a placeholder
            if (!thumbnail.equals("self") && !thumbnail.equals("default") &&
                !thumbnail.equals("nsfw") &&
                !thumbnail.isEmpty()) {
                url = thumbnail;
                hasValidPreview = true;
            }
        }

        // Only show preview if we have a valid image URL
        if (hasValidPreview) {
            if (!full && !SettingValues.isPicsEnabled(baseSub)) {
                thumbImage2.setVisibility(View.VISIBLE);
                ((Reddit) getContext().getApplicationContext())
                        .getImageLoader()
                        .displayImage(url, thumbImage2);
                setVisibility(View.GONE);
            } else {
                backdrop.setVisibility(View.VISIBLE);
                ((Reddit) getContext().getApplicationContext())
                        .getImageLoader()
                        .displayImage(url, backdrop);
                setVisibility(View.VISIBLE);
            }
            if (wrapArea != null) wrapArea.setVisibility(View.GONE);
        } else {
            // No valid preview available
            setVisibility(View.GONE);
            if (thumbImage2 != null) thumbImage2.setVisibility(View.GONE);
            if (backdrop != null) backdrop.setVisibility(View.GONE);
            if (wrapArea != null) wrapArea.setVisibility(View.VISIBLE);
        }
    }

    private void handleRedditGalleryType(Submission submission, String baseSub, boolean full, boolean forceThumb) {
        JsonNode dataNode = submission.getDataNode();

        // If this is a crosspost, we need to load the gallery data from the parent submission
        if (dataNode.has("crosspost_parent_list") && dataNode.get("crosspost_parent_list").size() > 0) {
            dataNode = dataNode.get("crosspost_parent_list").get(0);
        }

        // Check if gallery_data exists AND contains items before proceeding
        if (dataNode.has("gallery_data") &&
            dataNode.get("gallery_data").has("items") &&
            dataNode.get("gallery_data").get("items").size() > 0) {
            handleGalleryData(dataNode, submission, baseSub, full, forceThumb);
        } else {
            // Hide all preview elements when there are no gallery items
            setVisibility(View.GONE);
            if (thumbImage2 != null) thumbImage2.setVisibility(View.GONE);
            if (wrapArea != null) wrapArea.setVisibility(View.GONE);
        }
    }

    private void handleVRedditType(Submission submission, String baseSub, boolean full, boolean forceThumb) {
        JsonNode dataNode = submission.getDataNode();
        String previewUrl = getPreviewUrl(dataNode);

        if (dataNode.has("preview") &&
            dataNode.get("preview").has("images") &&
            dataNode.get("preview").get("images").size() > 0) {
                handlePreviewImage(previewUrl, submission, baseSub, full, forceThumb);
        } else {
            // No valid preview available
            setVisibility(View.GONE);
            if (thumbImage2 != null) thumbImage2.setVisibility(View.GONE);
            if (backdrop != null) backdrop.setVisibility(View.GONE);
            if (wrapArea != null) wrapArea.setVisibility(View.VISIBLE);
        }
    }

    private String getPreviewUrl(JsonNode dataNode) {
        String previewUrl = null;

        // Check crosspost parent first
        if (dataNode.has("crosspost_parent_list") && dataNode.get("crosspost_parent_list").size() > 0) {
            JsonNode parentNode = dataNode.get("crosspost_parent_list").get(0);
            previewUrl = extractPreviewUrl(parentNode);
        }
        // Fallback to current submission preview
        if (previewUrl == null) {
            previewUrl = extractPreviewUrl(dataNode);
        }
        return previewUrl;
    }

    private String extractPreviewUrl(JsonNode node) {
        if (node.has("preview") &&
            node.get("preview").has("images") &&
            node.get("preview").get("images").size() > 0) {

            return node.get("preview")
                    .get("images")
                    .get(0)
                    .get("source")
                    .get("url")
                    .asText();
        }
        return null;
    }

    private void handlePreviewImage(String previewUrl, Submission submission, String baseSub, boolean full, boolean forceThumb) {
        if (!full && !SettingValues.isPicsEnabled(baseSub) || forceThumb) {
            if (!submission.isSelfPost() || full) {
                if (!full) {
                    thumbImage2.setVisibility(View.VISIBLE);
                } else {
                    wrapArea.setVisibility(View.VISIBLE);
                }
                loadedUrl = previewUrl;
                displayImage(previewUrl, thumbImage2, full);
            } else {
                thumbImage2.setVisibility(View.GONE);
            }
            setVisibility(View.GONE);
        } else {
            handleFullPreviewImage(previewUrl, full);
        }
    }

    private void handleFullPreviewImage(String previewUrl, boolean full) {
        loadedUrl = previewUrl;
        displayImage(previewUrl, backdrop, full);
        setVisibility(View.VISIBLE);
        if (!full) {
            thumbImage2.setVisibility(View.GONE);
        } else {
            wrapArea.setVisibility(View.GONE);
        }
    }

    private void displayImage(String url, ImageView target, boolean full) {
        backdrop.setVisibility(View.VISIBLE);
        if (!full) {
            ((Reddit) getContext().getApplicationContext())
                    .getImageLoader()
                    .displayImage(url, target);
        } else {
            ((Reddit) getContext().getApplicationContext())
                    .getImageLoader()
                    .displayImage(url, target, bigOptions);
        }
    }

    private void handleImageType(Submission submission, String baseSub, boolean full, boolean forceThumb, boolean loadLq) {
        String url = "";
        boolean lq = false;

        if (loadLq && submission.getThumbnails() != null && submission.getThumbnails().getVariations().length > 0) {
            url = getLowQualityUrl(submission);
            lq = true;
        } else {
            url = getHighQualityUrl(submission);
        }

        if (!full && !SettingValues.isPicsEnabled(baseSub) || forceThumb) {
            if (!submission.isSelfPost() || full) {
                if (!full) {
                    thumbImage2.setVisibility(View.VISIBLE);
                } else {
                    wrapArea.setVisibility(View.VISIBLE);
                }

                loadedUrl = url;
                displayImage(url, thumbImage2, full);
            } else {
                thumbImage2.setVisibility(View.GONE);
            }
            setVisibility(View.GONE);
        } else {
            loadedUrl = url;
            displayImage(url, backdrop, full);
            setVisibility(View.VISIBLE);
            if (!full) {
                thumbImage2.setVisibility(View.GONE);
            } else {
                wrapArea.setVisibility(View.GONE);
            }
        }
    }

    private String getThumbnailVariationUrl(Submission submission, int index) {
        return CompatUtil.fromHtml(
                submission.getThumbnails().getVariations()[index].getUrl()
        ).toString(); // unescape url characters
    }

    private String getLowQualityUrl(Submission submission) {
        if (ContentType.isImgurImage(submission.getUrl())) {
            String url = submission.getUrl();
            return url.substring(0, url.lastIndexOf("."))
                    + (SettingValues.lqLow ? "m" : (SettingValues.lqMid ? "l" : "h"))
                    + url.substring(url.lastIndexOf("."));
        } else {
            int length = submission.getThumbnails().getVariations().length;
            if (SettingValues.lqLow && length >= 3) {
                return getThumbnailVariationUrl(submission, 2);
            } else if (SettingValues.lqMid && length >= 4) {
                return getThumbnailVariationUrl(submission, 3);
            } else if (length >= 5) {
                return getThumbnailVariationUrl(submission, length - 1);
            } else {
                return CompatUtil.fromHtml(submission.getThumbnails().getSource().getUrl()).toString();
            }
        }
    }

    private String getHighQualityUrl(Submission submission) {
        if (submission.getDataNode().has("preview")
                && submission.getDataNode().get("preview").get("images").get(0).get("source").has("height")) {
            return submission.getDataNode().get("preview").get("images").get(0).get("source").get("url").asText();
        } else if (submission.getThumbnails() != null && submission.getThumbnails().getSource() != null) {
            String sourceUrl = submission.getThumbnails().getSource().getUrl();
            return CompatUtil.fromHtml(
                    sourceUrl.isEmpty() ? submission.getThumbnail() : sourceUrl
            ).toString();
        } else {
            // Fallback in case there is no preview or thumbnails source available.
            return submission.getThumbnail();
        }
    }

    private boolean setBackdropLayoutParams(int height, int width, boolean full, boolean fullImage, ContentType.Type type) {
        if (full) {
            if (!fullImage && height < dpToPx(50) && type != ContentType.Type.SELF) {
                return true;
            } else if (SettingValues.cropImage) {
                setFixedHeightLayoutParams(200);
            } else {
                setAspectRatioLayoutParams(height, width);
            }
        } else if (SettingValues.bigPicCropped) {
            if (!fullImage && height < dpToPx(50)) {
                return true;
            } else {
                setFixedHeightLayoutParams(200);
            }
        } else if (fullImage || height >= dpToPx(50)) {
            setAspectRatioLayoutParams(height, width);
        } else {
            return true;
        }
        return false;
    }

    private void setFixedHeightLayoutParams(int heightDp) {
        backdrop.setLayoutParams(
                new RelativeLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, dpToPx(heightDp)));
    }

    private void setAspectRatioLayoutParams(int height, int width) {
        double h = getHeightFromAspectRatio(height, width);
        if (h != 0) {
            if (h > 3200) {
                backdrop.setLayoutParams(
                        new RelativeLayout.LayoutParams(
                                LayoutParams.MATCH_PARENT, 3200));
            } else {
                backdrop.setLayoutParams(
                        new RelativeLayout.LayoutParams(
                                LayoutParams.MATCH_PARENT, (int) h));
            }
        } else {
            backdrop.setLayoutParams(
                    new RelativeLayout.LayoutParams(
                            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private void handleThumbnailDisplay(Submission submission, boolean full, boolean forceThumb,
            boolean loadLq, String baseSub, boolean news) {
        String url = getSubmissionUrl(submission, loadLq);
        boolean shouldShowThumb = !SettingValues.isPicsEnabled(baseSub) && !full
                || forceThumb;

        if (shouldShowThumb) {
            displayThumbnail(url, full);
        } else {
            displayFullImage(url, full);
        }
    }

    private String getSubmissionUrl(Submission submission, boolean loadLq) {
        if (loadLq && submission.getThumbnails().getVariations().length != 0) {
            if (ContentType.isImgurImage(submission.getUrl())) {
                return getImgurLowQualityUrl(submission.getUrl());
            } else {
                return getLowQualityVariationUrl(submission);
            }
        } else {
            return getHighQualityUrl(submission);
        }
    }

    private String getImgurLowQualityUrl(String url) {
        return url.substring(0, url.lastIndexOf("."))
                + (SettingValues.lqLow ? "m" : (SettingValues.lqMid ? "l" : "h"))
                + url.substring(url.lastIndexOf("."));
    }

    private String getLowQualityVariationUrl(Submission submission) {
        int length = submission.getThumbnails().getVariations().length;
        if (SettingValues.lqLow && length >= 3) {
            return getThumbnailVariationUrl(submission, 2);
        } else if (SettingValues.lqMid && length >= 4) {
            return getThumbnailVariationUrl(submission, 3);
        } else if (length >= 5) {
            return getThumbnailVariationUrl(submission, length - 1);
        } else {
            return CompatUtil.fromHtml(submission.getThumbnails().getSource().getUrl()).toString();
        }
    }

    private void displayThumbnail(String url, boolean full) {
        if (url == null || PLACEHOLDER_URLS.contains(url)) {
            LogUtil.v("Displaying thumbnail - invalid or placeholder URL: " + url + ", hiding view and thumbImage2.");
            setVisibility(View.GONE); // Hides HeaderImageLinkView
            if (thumbImage2 != null) {
                thumbImage2.setVisibility(View.GONE);
            }
            if (full && wrapArea != null) { // if full view, wrapArea might have been made visible
                wrapArea.setVisibility(View.GONE);
            }
            return;
        }

        if (!full) {
            thumbImage2.setVisibility(View.VISIBLE);
        } else {
            wrapArea.setVisibility(View.VISIBLE);
        }
        loadedUrl = url;

        ImageLoadingListener detailedListener = new ImageLoadingListener() {
            @Override
            public void onLoadingStarted(String imageUri, View view) {}

            @Override
            public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                LogUtil.e("UIL (Thumbnail): Loading FAILED for: " + imageUri + ", reason: " + failReason.getType() + ", cause: " + (failReason.getCause() != null ? failReason.getCause().getMessage() : "null"));
                if (HeaderImageLinkView.this != null) {
                    HeaderImageLinkView.this.setVisibility(View.GONE);
                }
            }

            @Override
            public void onLoadingComplete(String imageUri, View view, android.graphics.Bitmap loadedBitmap) {
                if (loadedBitmap != null) {
                    if (loadedBitmap.getWidth() == 0 || loadedBitmap.getHeight() == 0) {
                        LogUtil.w("UIL (Thumbnail): Loaded bitmap has zero width or height for " + imageUri);
                        if (HeaderImageLinkView.this != null) {
                            HeaderImageLinkView.this.setVisibility(View.GONE); // Hide if bitmap is unusable
                        }
                    }
                } else {
                    LogUtil.w("UIL (Thumbnail): Loading COMPLETE for " + imageUri + " but bitmap is NULL.");
                    if (HeaderImageLinkView.this != null) {
                        HeaderImageLinkView.this.setVisibility(View.GONE); // Hide if bitmap is null
                    }
                }
            }

            @Override
            public void onLoadingCancelled(String imageUri, View view) {
                LogUtil.w("UIL (Thumbnail): Loading CANCELLED for " + imageUri);
                if (HeaderImageLinkView.this != null) {
                    HeaderImageLinkView.this.setVisibility(View.GONE);
                }
            }
        };

        ((Reddit) getContext().getApplicationContext())
                .getImageLoader()
                .displayImage(url, thumbImage2, detailedListener); // Use detailedListener
        setVisibility(View.GONE); // This line was already here for thumbnails
    }

    private void displayFullImage(String url, boolean full) {
        if (url == null || PLACEHOLDER_URLS.contains(url)) {
            LogUtil.v("Displaying full image - invalid or placeholder URL for backdrop: " + url + ", hiding view.");
            setVisibility(View.GONE);
            if (thumbImage2 != null) {
                thumbImage2.setVisibility(View.GONE);
            }
            if (wrapArea != null) {
                wrapArea.setVisibility(View.GONE);
            }
            return;
        }

        loadedUrl = url;
        ImageLoadingListener detailedListener = new ImageLoadingListener() {
            @Override
            public void onLoadingStarted(String imageUri, View view) {}

            @Override
            public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                LogUtil.e("UIL (FullImage): Loading FAILED for: " + imageUri + ", reason: " + failReason.getType() + ", cause: " + (failReason.getCause() != null ? failReason.getCause().getMessage() : "null"));
                if (HeaderImageLinkView.this != null) {
                    HeaderImageLinkView.this.setVisibility(View.GONE);
                }
            }

            @Override
            public void onLoadingComplete(String imageUri, View view, android.graphics.Bitmap loadedBitmap) {
                if (loadedBitmap != null) {
                    if (loadedBitmap.getWidth() == 0 || loadedBitmap.getHeight() == 0) {
                        LogUtil.w("UIL (FullImage): Loaded bitmap has zero width or height for " + imageUri);
                        // Don't hide HeaderImageLinkView here by default, let adjustViewBounds try.
                        // If it results in 0 height, it will be invisible anyway.
                        // Only hide if explicitly desired for 0-dim images.
                    }
                     // Ensure backdrop is visible if we successfully loaded an image and HeaderImageLinkView is meant to be visible.
                    if (view instanceof ImageView && HeaderImageLinkView.this.getVisibility() == View.VISIBLE) {
                        ((ImageView) view).setVisibility(View.VISIBLE);
                    }
                } else {
                    LogUtil.w("UIL (FullImage): Loading COMPLETE for " + imageUri + " but bitmap is NULL.");
                    if (HeaderImageLinkView.this != null) {
                        HeaderImageLinkView.this.setVisibility(View.GONE); // Hide if bitmap is null
                    }
                }
            }

            @Override
            public void onLoadingCancelled(String imageUri, View view) {
                LogUtil.w("UIL (FullImage): Loading CANCELLED for " + imageUri);
                if (HeaderImageLinkView.this != null) {
                    HeaderImageLinkView.this.setVisibility(View.GONE);
                }
            }
        };

        // Ensure backdrop ImageView itself is visible before loading, if HeaderImageLinkView is meant to be visible.
        // This is because UIL won't make it visible, and its default state is visible from XML,
        // but good to be explicit if we are about to load an image into it.
        if (backdrop != null && getVisibility() == View.VISIBLE) {
            backdrop.setVisibility(View.VISIBLE);
        }

        if (!full) {
            ((Reddit) getContext().getApplicationContext())
                    .getImageLoader()
                    .displayImage(url, backdrop, null, detailedListener);
        } else {
            ((Reddit) getContext().getApplicationContext())
                    .getImageLoader()
                    .displayImage(url, backdrop, bigOptions, detailedListener);
        }

        setVisibility(View.VISIBLE);

        if (!full) {
            if (thumbImage2 != null) thumbImage2.setVisibility(View.GONE);
        } else {
            if (wrapArea != null) wrapArea.setVisibility(View.GONE);
        }
    }

    private void handleSpecialSubmissionType(Submission submission, boolean full, boolean forceThumb, int drawableResId) {
        setVisibility(View.GONE);
        if (!full || forceThumb) {
            thumbImage2.setVisibility(View.VISIBLE);
        } else {
            wrapArea.setVisibility(View.VISIBLE);
        }

        if (submission.isSelfPost() && full) {
            wrapArea.setVisibility(View.GONE);
        } else {
            thumbImage2.setImageDrawable(
                    ContextCompat.getDrawable(getContext(), drawableResId));
            thumbUsed = true;
        }
        loadedUrl = submission.getUrl();
    }

    private void setupTitleAndBottomSheet(Submission submission, boolean full, boolean forceThumb, ContentType.Type type) {
        if (full) {
            setupFullView(submission, full, forceThumb, type);
        } else {
            setupCompactView(submission, full);
        }
    }

    private void setupFullView(Submission submission, boolean full, boolean forceThumb, ContentType.Type type) {
        if (wrapArea.getVisibility() == View.VISIBLE) {
            title = secondTitle;
            info = secondSubTitle;
            setBottomSheet(wrapArea, submission, full);
        } else {
            setupDefaultTitleAndInfo();
            View targetView = determineBottomSheetTarget(submission, forceThumb, type);
            setBottomSheet(targetView, submission, full);
        }
    }

    private void setupCompactView(Submission submission, boolean full) {
        setupDefaultTitleAndInfo();
        setBottomSheet(thumbImage2, submission, full);
        setBottomSheet(this, submission, full);
    }

    private void setupDefaultTitleAndInfo() {
        title = findViewById(R.id.textimage);
        info = findViewById(R.id.subtextimage);
    }

    private View determineBottomSheetTarget(Submission submission, boolean forceThumb, ContentType.Type type) {
        boolean useThumb = forceThumb
                || (submission.isNsfw()
                        && submission.getThumbnailType() == Submission.ThumbnailType.NSFW
                        || type != ContentType.Type.IMAGE
                        && type != ContentType.Type.SELF
                        && !submission.getDataNode().get("thumbnail").isNull()
                        && (submission.getThumbnailType() != Submission.ThumbnailType.URL));

        return useThumb ? thumbImage2 : this;
    }

    private void setThumbAndWrapVisibility(boolean full, boolean visible) {
        if (!full) {
            thumbImage2.setVisibility(visible ? View.VISIBLE : View.GONE);
        } else {
            wrapArea.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void handleGalleryData(JsonNode dataNode, Submission submission, String baseSub, boolean full, boolean forceThumb) {
        JsonNode galleryData = dataNode.get("gallery_data");
        JsonNode mediaMetadata = dataNode.get("media_metadata");

        if (galleryData.has("items") && galleryData.get("items").size() > 0) {
            boolean allFailed = true;
            for (JsonNode item : galleryData.get("items")) {
                String mediaId = item.get("media_id").asText();
                if (mediaMetadata != null && mediaMetadata.has(mediaId)) {
                    JsonNode mediaInfo = mediaMetadata.get(mediaId);
                    if (!"failed".equals(mediaInfo.get("status").asText())) {
                        allFailed = false;
                        String url = null;

                        // Try to get source URL first
                        if (mediaInfo.has("s") && mediaInfo.get("s").has("u")) {
                            url = mediaInfo.get("s").get("u").asText();
                        }
                        // Fall back to preview array if source not available
                        else if (mediaInfo.has("p") && mediaInfo.get("p").size() > 0) {
                            url = mediaInfo.get("p").get(0).get("u").asText();
                        }

                        if (url != null) {
                            // Clean up URL
                            url = url.replace("preview.redd.it", "i.redd.it")
                                    .replaceAll("\\?.*", "");

                            handlePreviewImage(url, submission, baseSub, full, forceThumb);
                            break;  // Only handle the first image
                        }
                    }
                }
            }

            if (allFailed) {
                // Handle the case where all media failed
                setVisibility(View.GONE);
                if (thumbImage2 != null) thumbImage2.setVisibility(View.GONE);
                if (wrapArea != null) wrapArea.setVisibility(View.GONE);
            }
        } else {
            // Handle the case where gallery_data is missing or empty
            setVisibility(View.GONE);
            if (thumbImage2 != null) thumbImage2.setVisibility(View.GONE);
            if (wrapArea != null) wrapArea.setVisibility(View.GONE);
        }
    }
}
