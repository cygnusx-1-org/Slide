package me.edgan.redditslide.SubmissionViews;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.display.SimpleBitmapDisplayer;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;
import com.nostra13.universalimageloader.utils.MemoryCacheUtils;
import java.io.File;
import java.util.Arrays;
import java.util.List;
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
import me.edgan.redditslide.Views.MaxHeightImageView;
import me.edgan.redditslide.Views.PeekMediaView;
import me.edgan.redditslide.Views.RoundImageTriangleView;
import me.edgan.redditslide.Views.TransparentTagTextView;
import me.edgan.redditslide.util.CompatUtil;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PhotoLoader;
import net.dean.jraw.models.Submission;
import org.apache.commons.text.StringEscapeUtils;

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
                    // Retain the decoded bitmap so already-seen cards reappear instantly on
                    // scroll-back instead of re-decoding from disk every bind.
                    .cacheInMemory(true)
                    // Always RGB_565 for feed cards (half the memory of ARGB_8888) so the memory
                    // cache holds ~2x more images. Feed cards are downscaled previews where the
                    // colour-depth difference is imperceptible; full-screen viewing is unaffected.
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    // No fade — images appear instantly instead of animating in on scroll.
                    .displayer(new SimpleBitmapDisplayer())
                    .build();
    boolean clickHandled;
    Handler handler;
    MotionEvent event;
    Runnable longClicked;
    float position;
    private TextView title;
    private TextView info;
    public MaxHeightImageView backdrop;
    private boolean forceThumb;

    // Cache the resolved gallery preview, keyed by the data node identity, so re-binds of the same
    // card skip re-traversing the gallery JSON while a refreshed submission (new node) recomputes.
    private JsonNode galleryPreviewKey;
    private PhotoLoader.GalleryPreview galleryPreviewCache;

    // Same idea for single-image posts. The chosen URL also depends on the low-quality decision
    // (which varies with network state) and the display width (thumbnail vs card), so those are
    // part of the key too.
    private JsonNode imagePreviewKey;
    private boolean imagePreviewLowQ;
    private int imagePreviewWidth;
    private String imagePreviewUrlCache;

    private static final List<String> PLACEHOLDER_URLS =
            Arrays.asList("self", "default", "image", "nsfw", "spoiler", "");

    // Reused across all loads — instance-free so it's safe as a singleton.
    private static final ImageLoadingListener TRANSPARENCY_LISTENER =
            new SimpleImageLoadingListener() {
                @Override
                public void onLoadingComplete(String imageUri, View view, Bitmap loadedBitmap) {
                    applyTransparencyBackground(view, loadedBitmap);
                }

                @Override
                public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                    if (view != null) view.setBackground(null);
                }

                @Override
                public void onLoadingCancelled(String imageUri, View view) {
                    if (view != null) view.setBackground(null);
                }
            };

    private static void applyTransparencyBackground(View view, Bitmap bitmap) {
        if (view == null) return;
        if (hasMeaningfulTransparency(bitmap)) {
            view.setBackgroundColor(Color.WHITE);
        } else {
            view.setBackground(null);
        }
    }

    // Sample an 8x8 grid; transparent PNGs (logos, icons) trip this, photos don't.
    private static boolean hasMeaningfulTransparency(Bitmap bitmap) {
        if (bitmap == null || !bitmap.hasAlpha()) return false;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width == 0 || height == 0) return false;
        final int samples = 8;
        int transparent = 0;
        int total = 0;
        for (int sy = 0; sy < samples; sy++) {
            for (int sx = 0; sx < samples; sx++) {
                int x = Math.min(width - 1, (int) ((sx + 0.5f) * width / samples));
                int y = Math.min(height - 1, (int) ((sy + 0.5f) * height / samples));
                int alpha = (bitmap.getPixel(x, y) >>> 24) & 0xff;
                if (alpha < 250) transparent++;
                total++;
            }
        }
        return transparent * 20 > total;
    }

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
        backdrop.setAspectRatio(0);
        backdrop.setLayoutParams(new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        boolean fullImage = ContentType.fullImage(type);
        thumbUsed = false;

        setVisibility(View.VISIBLE);
        String url = "";
        boolean forceThumb = false;
        thumbImage2.setImageResource(android.R.color.transparent);
        // View recycling: clear any transparency background from the previous bind.
        backdrop.setBackground(null);
        thumbImage2.setBackground(null);
        // View recycling: reset to the default crop; the letterbox path overrides this below.
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);

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

                displayImageCachedFirst(url, thumbImage2, null);
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
                LinkUtil.showLinkBottomSheet(activity, getContext(), url);
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
        // Universal Image Loader reads the ImageView's maxHeight (the view's layout height is
        // WRAP_CONTENT) to size its decode target. Cap it to the screen-relative value so tall
        // images don't decode at near-full resolution. Previously this was a hardcoded 3200px in
        // the layout, which produced 8-15 MB bitmaps and made the in-memory cache nearly useless.
        this.backdrop.setMaxHeight(MaxHeightImageView.maxHeight);
    }

    private void handleTypes(Submission submission, String baseSub, boolean full) {
        JsonNode dataNode = submission.getDataNode();

        // Prefer a genuine preview image. For crossposts the preview lives on the
        // parent submission, so getPreviewUrl() checks the crosspost parent first. Size it to the
        // display target so a list thumbnail fetches a small preview, not the full-width one.
        String redditPreviewUrl =
                getPreviewUrl(
                        dataNode,
                        feedImageWidth(!full && !SettingValues.isPicsEnabled(baseSub)));

        if (redditPreviewUrl != null && !redditPreviewUrl.isEmpty()) {
            // A real, full-resolution preview is available; show it as a lead image.
            if (!full && !SettingValues.isPicsEnabled(baseSub)) {
                thumbImage2.setVisibility(View.VISIBLE);
                displayImage(redditPreviewUrl, thumbImage2);
                setVisibility(View.GONE);
            } else {
                backdrop.setVisibility(View.VISIBLE);
                displayImage(redditPreviewUrl, backdrop);
                setVisibility(View.VISIBLE);
            }
            if (wrapArea != null) wrapArea.setVisibility(View.GONE);
            return;
        }

        // No real preview available. Fall back to the (small) thumbnail, but show it
        // as a thumbnail rather than stretching it into the big lead image. This
        // matches how the parent post is displayed when it has no preview image.
        String thumbnailUrl = getValidThumbnailUrl(dataNode);
        if (thumbnailUrl == null
                && dataNode.has("crosspost_parent_list")
                && dataNode.get("crosspost_parent_list").size() > 0) {
            thumbnailUrl = getValidThumbnailUrl(dataNode.get("crosspost_parent_list").get(0));
        }

        if (thumbnailUrl != null) {
            loadedUrl = thumbnailUrl;
            setThumbAndWrapVisibility(full, true);
            displayImageCachedFirst(thumbnailUrl, thumbImage2, null);
            setVisibility(View.GONE);
        } else {
            // No image at all.
            setVisibility(View.GONE);
            if (thumbImage2 != null) thumbImage2.setVisibility(View.GONE);
            if (backdrop != null) backdrop.setVisibility(View.GONE);
            if (wrapArea != null) wrapArea.setVisibility(View.VISIBLE);
        }
    }

    // Delegated to PhotoLoader so the feed card and the preloader use identical thumbnail selection.
    private String getValidThumbnailUrl(JsonNode node) {
        return PhotoLoader.getValidThumbnailUrl(node);
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
        String previewUrl =
                getPreviewUrl(
                        dataNode,
                        feedImageWidth(
                                (!full && !SettingValues.isPicsEnabled(baseSub)) || forceThumb));

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

    // Delegated to PhotoLoader so the feed card and the preloader resolve the identical preview URL.
    private String getPreviewUrl(JsonNode dataNode) {
        return PhotoLoader.getPreviewUrl(dataNode);
    }

    private String getPreviewUrl(JsonNode dataNode, int maxWidth) {
        return PhotoLoader.getPreviewUrl(dataNode, maxWidth);
    }

    // The reddit preview width to request: the thumbnail cell for a list thumbnail, the full screen
    // width for a big card. Kept in lock-step with PhotoLoader's preload so the warm and the display
    // resolve the same sized URL (and hit the same memory-cache entry).
    private int feedImageWidth(boolean showThumb) {
        return PhotoLoader.feedImageWidth(getContext(), !showThumb);
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
                displayImage(previewUrl, thumbImage2);
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
        displayImage(previewUrl, backdrop);
        setVisibility(View.VISIBLE);
        if (!full) {
            thumbImage2.setVisibility(View.GONE);
        } else {
            wrapArea.setVisibility(View.GONE);
        }
    }

    private void displayImage(String url, ImageView target) {
        backdrop.setVisibility(View.VISIBLE);
        displayImageCachedFirst(url, target, TRANSPARENCY_LISTENER);
    }

    /**
     * Bind {@code url} into {@code target}, drawing it synchronously in this frame when the bitmap
     * is already in the memory cache (warmed by PhotoLoader as the page was fetched). This is what
     * stops feed images from popping in after the row is already on screen: on a cache hit the row
     * scrolls in with the image already drawn instead of blank-then-async-swap. On a miss it falls
     * back to the normal async load, so a rare miss simply behaves as before.
     */
    private void displayImageCachedFirst(
            String url, ImageView target, ImageLoadingListener listener) {
        final ImageLoader loader =
                ((Reddit) getContext().getApplicationContext()).getImageLoader();
        final boolean isThumb = target instanceof RoundImageTriangleView;
        if (url != null && target != null) {
            // UIL keys its memory cache by "uri_WxH", so look up by URI across all sizes. Unescape
            // to match ImageLoaderUnescape, which unescapes the URI on every display/load.
            final String unescaped = StringEscapeUtils.unescapeHtml4(url);
            Bitmap cached = firstCachedBitmap(loader, unescaped);

            // Memory miss, but the preloader already downloaded the file (or it was decoded earlier
            // and then evicted from the memory LRU while scrolling): decode the small thumbnail from
            // disk synchronously in this frame instead of swapping it in asynchronously, which reads
            // as a redraw. Bounded to the thumbnail size and gated on the file already being cached,
            // so it never touches the network. Skipped for the big lead image, where a full-width
            // synchronous decode could jank the scroll.
            if (cached == null && isThumb) {
                cached = syncDecodeThumbnailFromDisk(loader, unescaped);
            }

            // Keep the thumbnail and full-image paths from crossing over: only bind a cached bitmap
            // that is actually large enough for this view. A thumbnail-sized bitmap must never be
            // stretched into a full-width lead image (it would look blurry) — this covers the
            // per-subreddit pics override and single-rung posts where the thumb and full share a URL.
            // The small thumbnail view always passes; the big lead image only takes a full-sized one.
            if (cached != null
                    && !cached.isRecycled()
                    && (target.getWidth() <= 0 || cached.getWidth() * 3 >= target.getWidth() * 2)) {
                // The holder is recycled: cancel any load still in flight for this view from the
                // previous post, or it could complete later and overwrite our bitmap.
                loader.cancelDisplayTask(target);
                target.setImageBitmap(cached);
                if (listener != null) {
                    listener.onLoadingComplete(url, target, cached);
                }
                return;
            }
        }
        loader.displayImage(url, target, bigOptions, listener);
    }

    private static Bitmap firstCachedBitmap(ImageLoader loader, String uri) {
        for (Bitmap cached :
                MemoryCacheUtils.findCachedBitmapsForImageUri(uri, loader.getMemoryCache())) {
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
        }
        return null;
    }

    // Synchronously decode an already-downloaded thumbnail from the disk cache, or null if the file
    // isn't cached yet (so the caller falls back to an async load). Decodes the file directly with
    // BitmapFactory so it can never fall through to a main-thread network fetch — loadImageSync
    // would, if the disk entry were evicted between the exists() check and the decode. Decoded at,
    // and cached under, the preloader's target size/key so the result reuses the preloader's memory
    // entry instead of adding a second differently sized one, and repeat binds hit memory instead
    // of re-decoding.
    private Bitmap syncDecodeThumbnailFromDisk(ImageLoader loader, String uri) {
        try {
            final File diskFile = loader.getDiskCache().get(uri);
            if (diskFile == null || !diskFile.exists()) {
                return null;
            }
            final ImageSize size = PhotoLoader.feedDecodeSize(getContext());

            final BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(diskFile.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null;
            }

            final BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            // Cap the loop (sample <= 32) so it can't spin even if a target dimension is ever 0.
            int sample = 1;
            while (sample < 32
                    && bounds.outWidth / (sample * 2) >= size.getWidth()
                    && bounds.outHeight / (sample * 2) >= size.getHeight()) {
                sample *= 2;
            }
            opts.inSampleSize = sample;

            final Bitmap bmp = BitmapFactory.decodeFile(diskFile.getAbsolutePath(), opts);
            if (bmp != null) {
                loader.getMemoryCache().put(MemoryCacheUtils.generateKey(uri, size), bmp);
            }
            return bmp;
            // Catch Throwable, not Exception: BitmapFactory.decodeFile throws OutOfMemoryError (an
            // Error) on allocation failure, which must degrade to the async fallback, not crash the
            // bind. UIL's own decoder guards against OOM the same way.
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void handleImageType(Submission submission, String baseSub, boolean full, boolean forceThumb, boolean loadLq) {
        final boolean lowQ =
                loadLq
                        && submission.getThumbnails() != null
                        && submission.getThumbnails().getVariations().length > 0;

        // Record that the feed loaded a low-quality image so a tap can hand MediaView the low-res
        // copy plus an HQ button (SubmissionThumbnailHelper.openImage reads baseView.lq). Reset to
        // false per bind in setSubmission/setSubmissionNews.
        lq = lowQ;

        // The card shows a small thumbnail unless this is the big-image (card / full) view; size the
        // requested preview to match so a thumbnail never downloads the full-width image.
        final boolean showThumb = (!full && !SettingValues.isPicsEnabled(baseSub)) || forceThumb;
        final int maxW = feedImageWidth(showThumb);

        // Cache the resolved preview URL by data-node identity (+ the low-quality decision, which
        // can change with network state, and the display width) so a re-bind of the same card skips
        // re-parsing the JSON — mirrors the gallery path. A refreshed submission (new node) recomputes.
        final JsonNode dataNode = submission.getDataNode();
        final String url;
        if (dataNode != null
                && dataNode == imagePreviewKey
                && lowQ == imagePreviewLowQ
                && maxW == imagePreviewWidth) {
            url = imagePreviewUrlCache;
        } else {
            url = lowQ ? getLowQualityUrl(submission) : getHighQualityUrl(submission, maxW);
            imagePreviewKey = dataNode;
            imagePreviewLowQ = lowQ;
            imagePreviewWidth = maxW;
            imagePreviewUrlCache = url;
        }

        if (showThumb) {
            if (!submission.isSelfPost() || full) {
                if (!full) {
                    thumbImage2.setVisibility(View.VISIBLE);
                } else {
                    wrapArea.setVisibility(View.VISIBLE);
                }

                loadedUrl = url;
                displayImage(url, thumbImage2);
            } else {
                thumbImage2.setVisibility(View.GONE);
            }
            setVisibility(View.GONE);
        } else {
            loadedUrl = url;
            displayImage(url, backdrop);
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

    // Delegated to PhotoLoader so the feed card and the preloader use identical URL selection
    // (preventing first-view pop-in from a preload/display cache-key mismatch).
    private String getLowQualityUrl(Submission submission) {
        return PhotoLoader.getLowQualityUrl(submission);
    }

    private String getHighQualityUrl(Submission submission) {
        return PhotoLoader.getHighQualityUrl(submission);
    }

    private String getHighQualityUrl(Submission submission, int maxWidth) {
        return PhotoLoader.getHighQualityUrl(submission, maxWidth);
    }

    private boolean setBackdropLayoutParams(int height, int width, boolean full, boolean fullImage, ContentType.Type type) {
        if (full) {
            if (!fullImage && height < dpToPx(50) && type != ContentType.Type.SELF) {
                return true;
            } else if (SettingValues.cropImage) {
                backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
                setFixedHeightLayoutParams(200);
            } else {
                backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
                setAspectRatioLayoutParams(height, width);
            }
        } else if (SettingValues.bigPicLetterboxed) {
            if (!fullImage && height < dpToPx(50)) {
                return true;
            } else {
                // Letterbox: keep a fixed height like a link post, but fit the whole preview
                // inside it (zoomed out, with bars) instead of cropping to fill.
                backdrop.setScaleType(ImageView.ScaleType.FIT_CENTER);
                setFixedHeightLayoutParams(200);
            }
        } else if (SettingValues.bigPicCropped) {
            if (!fullImage && height < dpToPx(50)) {
                return true;
            } else {
                backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
                setFixedHeightLayoutParams(200);
            }
        } else if (fullImage || height >= dpToPx(50)) {
            backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
            setAspectRatioLayoutParams(height, width);
        } else {
            return true;
        }
        return false;
    }

    private void setFixedHeightLayoutParams(int heightDp) {
        backdrop.setAspectRatio(0);
        backdrop.setLayoutParams(
                new RelativeLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, dpToPx(heightDp)));
    }

    private void setAspectRatioLayoutParams(int height, int width) {
        // Reserve the slot height from the known aspect ratio so the asynchronously loaded image
        // never resizes the view (which made the feed jump while scrolling up). The actual pixel
        // height is derived from the real measured width in MaxHeightImageView.onMeasure, so this
        // is correct for any column count and even before the view has been measured.
        if (height > 0 && width > 0) {
            backdrop.setAspectRatio((double) height / (double) width);
        } else {
            backdrop.setAspectRatio(0);
        }
        backdrop.setLayoutParams(
                new RelativeLayout.LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void handleThumbnailDisplay(Submission submission, boolean full, boolean forceThumb,
            boolean loadLq, String baseSub, boolean news) {
        boolean shouldShowThumb = !SettingValues.isPicsEnabled(baseSub) && !full
                || forceThumb;
        String url = getSubmissionUrl(submission, loadLq, feedImageWidth(shouldShowThumb));

        if (shouldShowThumb) {
            displayThumbnail(url, full);
        } else {
            displayFullImage(url, full);
        }
    }

    private String getSubmissionUrl(Submission submission, boolean loadLq, int maxWidth) {
        if (loadLq && submission.getThumbnails().getVariations().length != 0) {
            // Loading a low-quality image: record it so a tap hands MediaView the low-res copy plus
            // an HQ button (baseView.lq, read in SubmissionThumbnailHelper.openImage).
            lq = true;
            if (ContentType.isImgurImage(submission.getUrl())) {
                return getImgurLowQualityUrl(submission.getUrl());
            } else {
                return getLowQualityVariationUrl(submission);
            }
        } else {
            return getHighQualityUrl(submission, maxWidth);
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
                if (view != null) view.setBackground(null);
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
                    } else {
                        applyTransparencyBackground(view, loadedBitmap);
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
                if (view != null) view.setBackground(null);
                if (HeaderImageLinkView.this != null) {
                    HeaderImageLinkView.this.setVisibility(View.GONE);
                }
            }
        };

        displayImageCachedFirst(url, thumbImage2, detailedListener); // Use detailedListener
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
                if (view != null) view.setBackground(null);
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
                    } else {
                        applyTransparencyBackground(view, loadedBitmap);
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
                if (view != null) view.setBackground(null);
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

        displayImageCachedFirst(url, backdrop, detailedListener);

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
        // Selection logic is shared with the preloader so the card and PhotoLoader reference the
        // same (sized) gallery image — see PhotoLoader.getGalleryPreview. Cache by data node
        // identity: a re-bind of the same card skips re-parsing, a refreshed submission recomputes.
        final PhotoLoader.GalleryPreview gallery;
        if (dataNode != null && dataNode == galleryPreviewKey) {
            gallery = galleryPreviewCache;
        } else {
            gallery = PhotoLoader.getGalleryPreview(dataNode);
            galleryPreviewKey = dataNode;
            galleryPreviewCache = gallery;
        }

        if (gallery != null) {
            // Reserve the lead-image height from the gallery item's dimensions so the
            // asynchronously loaded image does not resize the view while scrolling.
            if (gallery.width > 0 && gallery.height > 0) {
                setBackdropLayoutParams(
                        gallery.height, gallery.width, full, ContentType.fullImage(type), type);
            }
            handlePreviewImage(gallery.url, submission, baseSub, full, forceThumb);
        } else {
            // No usable gallery media (missing/empty data, or all items failed).
            setVisibility(View.GONE);
            if (thumbImage2 != null) thumbImage2.setVisibility(View.GONE);
            if (wrapArea != null) wrapArea.setVisibility(View.GONE);
        }
    }
}
