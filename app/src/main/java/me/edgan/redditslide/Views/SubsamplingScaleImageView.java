package me.edgan.redditslide.Views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.davemorrissey.labs.subscaleview.R.styleable;
import com.davemorrissey.labs.subscaleview.decoder.CompatDecoderFactory;
import com.davemorrissey.labs.subscaleview.decoder.DecoderFactory;
import com.davemorrissey.labs.subscaleview.decoder.ImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageDecoder;
import com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder;

import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.SubsamplingScaleImageViewDrawHelper;
import me.edgan.redditslide.util.TouchEventUtil;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import me.edgan.redditslide.util.SubsamplingScaleImageViewStateHelper;
import me.edgan.redditslide.util.TileManager;
import me.edgan.redditslide.util.SubsamplingScaleImageViewLoader;

/**
 * Displays an image subsampled as necessary to avoid loading too much image data into memory. After
 * zooming in, a set of image tiles subsampled at higher resolution are loaded and displayed over
 * the base layer. During pan and zoom, tiles off screen or higher/lower resolution than required
 * are discarded from memory.
 *
 * <p>Tiles are no larger than the max supported bitmap size, so with large images tiling may be
 * used even when zoomed out.
 *
 * <p>v prefixes - coordinates, translations and distances measured in screen (view) pixels <br>
 * s prefixes - coordinates, translations and distances measured in rotated and cropped source image
 * pixels (scaled) <br>
 * f prefixes - coordinates, translations and distances measured in original unrotated, uncropped
 * source file pixels
 *
 * <p><a href="https://github.com/davemorrissey/subsampling-scale-image-view">View project on
 * GitHub</a>
 */
@SuppressWarnings("unused")
public class SubsamplingScaleImageView extends View {

    public static final String TAG = SubsamplingScaleImageView.class.getSimpleName();

    /** Attempt to use EXIF information on the image to rotate it. Works for external files only. */
    public static final int ORIENTATION_USE_EXIF = -1;

    /** Display the image file in its native orientation. */
    public static final int ORIENTATION_0 = 0;

    /** Rotate the image 90 degrees clockwise. */
    public static final int ORIENTATION_90 = 90;

    /** Rotate the image 180 degrees. */
    public static final int ORIENTATION_180 = 180;

    /** Rotate the image 270 degrees clockwise. */
    public static final int ORIENTATION_270 = 270;

    private static final List<Integer> VALID_ORIENTATIONS = Arrays.asList(
        ORIENTATION_0,
        ORIENTATION_90,
        ORIENTATION_180,
        ORIENTATION_270,
        ORIENTATION_USE_EXIF
    );

    /**
     * During zoom animation, keep the point of the image that was tapped in the same place, and
     * scale the image around it.
     */
    public static final int ZOOM_FOCUS_FIXED = 1;

    /**
     * During zoom animation, move the point of the image that was tapped to the center of the
     * screen.
     */
    public static final int ZOOM_FOCUS_CENTER = 2;

    /** Zoom in to and center the tapped point immediately without animating. */
    public static final int ZOOM_FOCUS_CENTER_IMMEDIATE = 3;

    private static final List<Integer> VALID_ZOOM_STYLES = Arrays.asList(ZOOM_FOCUS_FIXED, ZOOM_FOCUS_CENTER, ZOOM_FOCUS_CENTER_IMMEDIATE);

    /** Quadratic ease out. Not recommended for scale animation, but good for panning. */
    public static final int EASE_OUT_QUAD = 1;

    /** Quadratic ease in and out. */
    public static final int EASE_IN_OUT_QUAD = 2;

    private static final List<Integer> VALID_EASING_STYLES = Arrays.asList(EASE_IN_OUT_QUAD, EASE_OUT_QUAD);

    /**
     * Don't allow the image to be panned off screen. As much of the image as possible is always
     * displayed, centered in the view when it is smaller. This is the best option for galleries.
     */
    public static final int PAN_LIMIT_INSIDE = 1;

    /**
     * Allows the image to be panned until it is just off screen, but no further. The edge of the
     * image will stop when it is flush with the screen edge.
     */
    public static final int PAN_LIMIT_OUTSIDE = 2;

    /**
     * Allows the image to be panned until a corner reaches the center of the screen but no further.
     * Useful when you want to pan any spot on the image to the exact center of the screen.
     */
    public static final int PAN_LIMIT_CENTER = 3;

    private static final List<Integer> VALID_PAN_LIMITS = Arrays.asList(PAN_LIMIT_INSIDE, PAN_LIMIT_OUTSIDE, PAN_LIMIT_CENTER);

    /**
     * Scale the image so that both dimensions of the image will be equal to or less than the
     * corresponding dimension of the view. The image is then centered in the view. This is the
     * default behaviour and best for galleries.
     */
    public static final int SCALE_TYPE_CENTER_INSIDE = 1;

    /**
     * Scale the image uniformly so that both dimensions of the image will be equal to or larger
     * than the corresponding dimension of the view. The image is then centered in the view.
     */
    public static final int SCALE_TYPE_CENTER_CROP = 2;

    /**
     * Scale the image so that both dimensions of the image will be equal to or less than the
     * maxScale and equal to or larger than minScale. The image is then centered in the view.
     */
    public static final int SCALE_TYPE_CUSTOM = 3;

    /**
     * Scale the image so that both dimensions of the image will be equal to or larger than the
     * corresponding dimension of the view. The top left is shown.
     */
    public static final int SCALE_TYPE_START = 4;

    private static final List<Integer> VALID_SCALE_TYPES = Arrays.asList(
        SCALE_TYPE_CENTER_CROP,
        SCALE_TYPE_CENTER_INSIDE,
        SCALE_TYPE_CUSTOM,
        SCALE_TYPE_START
    );

    /** State change originated from animation. */
    public static final int ORIGIN_ANIM = 1;

    /** State change originated from touch gesture. */
    public static final int ORIGIN_TOUCH = 2;

    /** State change originated from a fling momentum anim. */
    public static final int ORIGIN_FLING = 3;

    /** State change originated from a double tap zoom anim. */
    public static final int ORIGIN_DOUBLE_TAP_ZOOM = 4;

    // Bitmap (preview or full image)
    public Bitmap bitmap;

    // Whether the bitmap is a preview image
    public boolean bitmapIsPreview;

    // Specifies if a cache handler is also referencing the bitmap. Do not recycle if so.
    public boolean bitmapIsCached;

    // Uri of full size image
    public Uri uri;

    // Sample size used to display the whole image when fully zoomed out
    public int fullImageSampleSize;

    // Map of zoom level to tile grid
    public Map<Integer, List<Tile>> tileMap;

    // Overlay tile boundaries and other info
    public boolean debug;

    // Image orientation setting
    private int orientation = ORIENTATION_0;

    // Max scale allowed (prevent infinite zoom)
    public float maxScale = 2F;

    // Min scale allowed (prevent infinite zoom)
    private float minScale; // Defer initialization

    // Density to reach before loading higher resolution tiles
    private int minimumTileDpi = -1;

    // Pan limiting style
    public int panLimit = PAN_LIMIT_INSIDE;

    // Minimum scale type
    private int minimumScaleType = SCALE_TYPE_CENTER_INSIDE;

    // overrides for the dimensions of the generated tiles
    public static final int TILE_SIZE_AUTO = Integer.MAX_VALUE;
    public int maxTileWidth = TILE_SIZE_AUTO;
    public int maxTileHeight = TILE_SIZE_AUTO;

    // An executor service for loading of images
    private Executor executor = AsyncTask.THREAD_POOL_EXECUTOR;

    // Whether tiles should be loaded while gestures and animations are still in progress
    public boolean eagerLoadingEnabled = true;

    // Gesture detection settings
    public boolean panEnabled = true;
    public boolean zoomEnabled = true;
    public boolean quickScaleEnabled = true;

    // Double tap zoom behaviour
    private float doubleTapZoomScale = 1F;
    private int doubleTapZoomStyle = ZOOM_FOCUS_FIXED;
    private int doubleTapZoomDuration = 500;

    // Current scale and scale at start of zoom
    public float scale;
    public float scaleStart;

    // Screen coordinate of top-left corner of source image
    public PointF vTranslate;
    public PointF vTranslateStart;
    public PointF vTranslateBefore;

    // Source coordinate to center on, used when new position is set externally before view is ready
    public Float pendingScale;
    public PointF sPendingCenter;
    public PointF sRequestedCenter;

    // Source image dimensions and orientation - dimensions relate to the unrotated image
    public int sWidth;
    public int sHeight;
    public int sOrientation;
    public Rect sRegion;
    public Rect pRegion;

    // Is two-finger zooming in progress
    public boolean isZooming;
    // Is one-finger panning in progress
    public boolean isPanning;
    // Is quick-scale gesture in progress
    public boolean isQuickScaling;
    // Max touches used in current gesture
    public int maxTouchCount;

    // Fling detector
    public GestureDetector detector;
    public GestureDetector singleDetector;

    // Tile and image decoding
    public ImageRegionDecoder decoder;
    public final ReadWriteLock decoderLock = new ReentrantReadWriteLock(true);
    public DecoderFactory<? extends ImageDecoder> bitmapDecoderFactory =
            new CompatDecoderFactory<ImageDecoder>(SkiaImageDecoder.class, SettingValues.highColorspaceImages ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
    public DecoderFactory<? extends ImageRegionDecoder> regionDecoderFactory =
            new CompatDecoderFactory<ImageRegionDecoder>(SkiaImageRegionDecoder.class, SettingValues.highColorspaceImages ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);

    // Debug values
    public PointF vCenterStart;
    public float vDistStart;

    // Current quickscale state
    public final float quickScaleThreshold;
    public float quickScaleLastDistance;
    public boolean quickScaleMoved;
    public PointF quickScaleVLastPoint;
    public PointF quickScaleSCenter;
    public PointF quickScaleVStart;

    // Scale and center animation tracking
    public Anim anim;

    // Whether a ready notification has been sent to subclasses
    public boolean readySent;
    // Whether a base layer loaded notification has been sent to subclasses
    public boolean imageLoadedSent;

    // Event listener
    public OnImageEventListener onImageEventListener;

    // Scale and center listener
    private OnStateChangedListener onStateChangedListener;

    // Long click listener
    private OnLongClickListener onLongClickListener;

    // Long click handler
    public final Handler handler;
    public static final int MESSAGE_LONG_CLICK = 1;

    // Paint objects created once and reused for efficiency
    public Paint bitmapPaint;
    public Paint debugPaint;
    public Paint debugTextPaint;
    public Paint debugLinePaint;
    public Paint tileBgPaint;

    // Volatile fields used to reduce object creation
    public ScaleAndTranslate satTemp;
    public Matrix matrix;
    public RectF sRect;
    public float[] srcArray = new float[8];
    public float[] dstArray = new float[8];

    // The logical density of the display
    public final float density;

    // A global preference for bitmap format, available to decoder classes that respect it
    private static Bitmap.Config preferredBitmapConfig;

    // Loader helper instance
    public SubsamplingScaleImageViewLoader loader;

    public SubsamplingScaleImageView(Context context, AttributeSet attr) {
        super(context, attr);
        this.loader = new SubsamplingScaleImageViewLoader(this); // Initialize loader
        density = getResources().getDisplayMetrics().density;
        // Initialize minScale here now that 'this' is available
        this.minScale = SubsamplingScaleImageViewStateHelper.minScale(this);
        setMinimumDpi(160);
        setDoubleTapZoomDpi(160);
        setMinimumTileDpi(320);
        loader.setGestureDetector(context);
        this.handler =
            new Handler(
                new Handler.Callback() {
                    public boolean handleMessage(Message message) {
                        if (message.what == MESSAGE_LONG_CLICK && onLongClickListener != null) {
                            maxTouchCount = 0;
                            SubsamplingScaleImageView.super.setOnLongClickListener(onLongClickListener);
                            performLongClick();
                            SubsamplingScaleImageView.super.setOnLongClickListener(null);
                        }
                        return true;
                    }
                    });
        // Handle XML attributes
        if (attr != null) {
            TypedArray typedAttr = getContext().obtainStyledAttributes(attr, styleable.SubsamplingScaleImageView);
            if (typedAttr.hasValue(styleable.SubsamplingScaleImageView_assetName)) {
                String assetName = typedAttr.getString(styleable.SubsamplingScaleImageView_assetName);

                if (assetName != null && assetName.length() > 0) {
                    loader.setImage(ImageSource.asset(assetName).tilingEnabled());
                }
            }

            if (typedAttr.hasValue(styleable.SubsamplingScaleImageView_src)) {
                int resId = typedAttr.getResourceId(styleable.SubsamplingScaleImageView_src, 0);

                if (resId > 0) {
                    loader.setImage(ImageSource.resource(resId).tilingEnabled());
                }
            }

            if (typedAttr.hasValue(styleable.SubsamplingScaleImageView_panEnabled)) {
                setPanEnabled(typedAttr.getBoolean(styleable.SubsamplingScaleImageView_panEnabled, true));
            }

            if (typedAttr.hasValue(styleable.SubsamplingScaleImageView_zoomEnabled)) {
                setZoomEnabled(typedAttr.getBoolean(styleable.SubsamplingScaleImageView_zoomEnabled, true));
            }

            if (typedAttr.hasValue(styleable.SubsamplingScaleImageView_quickScaleEnabled)) {
                setQuickScaleEnabled(typedAttr.getBoolean(styleable.SubsamplingScaleImageView_quickScaleEnabled, true));
            }

            if (typedAttr.hasValue(styleable.SubsamplingScaleImageView_tileBackgroundColor)) {
                setTileBackgroundColor(typedAttr.getColor(styleable.SubsamplingScaleImageView_tileBackgroundColor, Color.argb(0, 0, 0, 0)));
            }

            typedAttr.recycle();
        }

        quickScaleThreshold = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, context.getResources().getDisplayMetrics());
    }

    public SubsamplingScaleImageView(Context context) {
        this(context, null);
    }

    /**
     * Get the current preferred configuration for decoding bitmaps. {@link ImageDecoder} and {@link
     * ImageRegionDecoder} instances can read this and use it when decoding images.
     *
     * @return the preferred bitmap configuration, or null if none has been set.
     */
    public static Bitmap.Config getPreferredBitmapConfig() {
        return preferredBitmapConfig;
    }

    /**
     * Set a global preferred bitmap config shared by all view instances and applied to new
     * instances initialised after the call is made. This is a hint only; the bundled {@link
     * ImageDecoder} and {@link ImageRegionDecoder} classes all respect this (except when they were
     * constructed with an instance-specific config) but custom decoder classes will not.
     *
     * @param preferredBitmapConfig the bitmap configuration to be used by future instances of the
     *     view. Pass null to restore the default.
     */
    public static void setPreferredBitmapConfig(Bitmap.Config preferredBitmapConfig) {
        SubsamplingScaleImageView.preferredBitmapConfig = preferredBitmapConfig;
    }

    /**
     * Sets the image orientation. It's best to call this before setting the image file or asset,
     * because it may waste loading of tiles. However, this can be freely called at any time.
     *
     * @param orientation orientation to be set. See ORIENTATION_ static fields for valid values.
     */
    public final void setOrientation(int orientation) {
        if (!VALID_ORIENTATIONS.contains(orientation)) {
            throw new IllegalArgumentException("Invalid orientation: " + orientation);
        }

        this.orientation = orientation;
        loader.reset(false);
        invalidate();
        requestLayout();
    }

    /**
     * On resize, preserve center and scale. Various behaviours are possible, override this method
     * to use another.
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        debug("onSizeChanged %dx%d -> %dx%d", oldw, oldh, w, h);
        PointF sCenter = SubsamplingScaleImageViewStateHelper.getCenter(this);

        if (readySent && sCenter != null) {
            this.anim = null;
            this.pendingScale = scale;
            this.sPendingCenter = sCenter;
        }
    }

    /**
     * Measures the width and height of the view, preserving the aspect ratio of the image displayed
     * if wrap_content is used. The image will scale within this box, not resizing the view as it is
     * zoomed.
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
        boolean resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
        boolean resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
        int width = parentWidth;
        int height = parentHeight;

        if (sWidth > 0 && sHeight > 0) {
            if (resizeWidth && resizeHeight) {
                width = SubsamplingScaleImageViewStateHelper.sWidth(this);
                height = SubsamplingScaleImageViewStateHelper.sHeight(this);
            } else if (resizeHeight) {
                height = (int) ((((double) SubsamplingScaleImageViewStateHelper.sHeight(this) / (double) SubsamplingScaleImageViewStateHelper.sWidth(this)) * width));
            } else if (resizeWidth) {
                width = (int) ((((double) SubsamplingScaleImageViewStateHelper.sWidth(this) / (double) SubsamplingScaleImageViewStateHelper.sHeight(this)) * height));
            }
        }

        width = Math.max(width, getSuggestedMinimumWidth());
        height = Math.max(height, getSuggestedMinimumHeight());
        setMeasuredDimension(width, height);
    }

    /** Handle touch events. One finger pans, and two finger pinch and zoom plus panning. */
    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        // During non-interruptible anims, ignore all touch events
        if (anim != null && !anim.interruptible) {
            requestDisallowInterceptTouchEvent(true);
            return true;
        } else {
            if (anim != null && anim.listener != null) {
                try {
                    anim.listener.onInterruptedByUser();
                } catch (Exception e) {
                    Log.w(TAG, "Error thrown by animation listener", e);
                }
            }
            anim = null;
        }

        // Abort if not ready
        if (vTranslate == null) {
            if (singleDetector != null) {
                singleDetector.onTouchEvent(event);
            }
            return true;
        }

        // Detect flings, taps and double taps
        if (!isQuickScaling && (detector == null || detector.onTouchEvent(event))) {
            isZooming = false;
            isPanning = false;
            maxTouchCount = 0;
            return true;
        }

        if (vTranslateStart == null) {
            vTranslateStart = new PointF(0, 0);
        }

        if (vTranslateBefore == null) {
            vTranslateBefore = new PointF(0, 0);
        }

        if (vCenterStart == null) {
            vCenterStart = new PointF(0, 0);
        }

        // Store current values so we can send an event if they change
        float scaleBefore = scale;
        vTranslateBefore.set(vTranslate);

        boolean handled = TouchEventUtil.handleTouchEventInternal(this, event);
        sendStateChanged(scaleBefore, vTranslateBefore, ORIGIN_TOUCH);
        return handled || super.onTouchEvent(event);
    }

    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    /**
     * Double tap zoom handler triggered from gesture detector or on touch, depending on whether
     * quick scale is enabled.
     */
    public void doubleTapZoom(PointF sCenter, PointF vFocus) {
        if (!panEnabled) {
            if (sRequestedCenter != null) {
                // With a center specified from code, zoom around that point.
                sCenter.x = sRequestedCenter.x;
                sCenter.y = sRequestedCenter.y;
            } else {
                // With no requested center, scale around the image center.
                sCenter.x = SubsamplingScaleImageViewStateHelper.sWidth(this) / 2.0f;
                sCenter.y = SubsamplingScaleImageViewStateHelper.sHeight(this) / 2.0f;
            }
        }

        float doubleTapZoomScale = Math.min(maxScale, this.doubleTapZoomScale);
        boolean zoomIn = (scale <= doubleTapZoomScale * 0.9) || scale == SubsamplingScaleImageViewStateHelper.minScale(this);
        float targetScale = zoomIn ? doubleTapZoomScale : SubsamplingScaleImageViewStateHelper.minScale(this);

        if (doubleTapZoomStyle == ZOOM_FOCUS_CENTER_IMMEDIATE) {
            setScaleAndCenter(targetScale, sCenter);
        } else if (doubleTapZoomStyle == ZOOM_FOCUS_CENTER || !zoomIn || !panEnabled) {
            new AnimationBuilder(this, targetScale, sCenter)
                    .withInterruptible(false)
                    .withDuration(doubleTapZoomDuration)
                    .withOrigin(ORIGIN_DOUBLE_TAP_ZOOM)
                    .start();
        } else if (doubleTapZoomStyle == ZOOM_FOCUS_FIXED) {
            new AnimationBuilder(this, targetScale, sCenter, vFocus)
                    .withInterruptible(false)
                    .withDuration(doubleTapZoomDuration)
                    .withOrigin(ORIGIN_DOUBLE_TAP_ZOOM)
                    .start();
        }

        invalidate();
    }

    /**
     * Draw method should not be called until the view has dimensions so the first calls are used as
     * triggers to calculate the scaling and tiling required. Once the view is setup, tiles are
     * displayed as they are loaded.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Delegate drawing to the helper class
        me.edgan.redditslide.util.SubsamplingScaleImageViewDrawHelper.drawContent(this, canvas);
    }

    /** Checks whether the base layer of tiles or full size bitmap is ready. */
    public boolean isBaseLayerReady() {
        if (bitmap != null && !bitmapIsPreview) {
            return true;
        } else if (tileMap != null) {
            boolean baseLayerReady = true;
            for (Map.Entry<Integer, List<Tile>> tileMapEntry : tileMap.entrySet()) {
                if (tileMapEntry.getKey() == fullImageSampleSize) {
                    for (Tile tile : tileMapEntry.getValue()) {
                        if (tile.loading || tile.bitmap == null) {
                            baseLayerReady = false;
                            break;
                        }
                    }
                }
            }
            return baseLayerReady;
        }
        return false;
    }

    /**
     * Check whether view and image dimensions are known and either a preview, full size image or
     * base layer tiles are loaded. First time, send ready event to listener. The next draw will
     * display an image.
     */
    public boolean checkReady() {
        boolean ready = getWidth() > 0 && getHeight() > 0 && sWidth > 0 && sHeight > 0 && (bitmap != null || isBaseLayerReady());

        if (!readySent && ready) {
            preDraw();
            readySent = true;
            onReady();

            if (onImageEventListener != null) {
                onImageEventListener.onReady();
            }
        }

        return ready;
    }

    /**
     * Check whether either the full size bitmap or base layer tiles are loaded. First time, send
     * image loaded event to listener.
     */
    public boolean checkImageLoaded() {
        boolean imageLoaded = isBaseLayerReady();

        if (!imageLoadedSent && imageLoaded) {
            preDraw();
            imageLoadedSent = true;
            onImageLoaded();

            if (onImageEventListener != null) {
                onImageEventListener.onImageLoaded();
            }
        }

        return imageLoaded;
    }

    /** Creates Paint objects once when first needed. */
    public void createPaints() {
        if (bitmapPaint == null) {
            bitmapPaint = new Paint();
            bitmapPaint.setAntiAlias(true);
            bitmapPaint.setFilterBitmap(true);
            bitmapPaint.setDither(true);
        }

        if ((debugTextPaint == null || debugLinePaint == null) && debug) {
            debugTextPaint = new Paint();
            debugTextPaint.setTextSize(SubsamplingScaleImageViewDrawHelper.px(this, 12));
            debugTextPaint.setColor(Color.MAGENTA);
            debugTextPaint.setStyle(Style.FILL);
            debugLinePaint = new Paint();
            debugLinePaint.setColor(Color.MAGENTA);
            debugLinePaint.setStyle(Style.STROKE);
            debugLinePaint.setStrokeWidth(SubsamplingScaleImageViewDrawHelper.px(this, 1));
        }
    }

    /**
     * Loads the optimum tiles for display at the current scale and translate, so the screen can be
     * filled with tiles that are at least as high resolution as the screen. Frees up bitmaps that
     * are now off the screen.
     *
     * @param load Whether to load the new tiles needed. Use false while scrolling/panning for
     *     performance.
     */
    public void refreshRequiredTiles(boolean load) {
        TileManager.refreshRequiredTiles(this, load);
    }

    /** Determine whether tile is visible. */
    private boolean tileVisible(Tile tile) {
        return TileManager.tileVisible(this, tile);
    }

    /** Sets scale and translate ready for the next draw. */
    public void preDraw() {
        if (getWidth() == 0 || getHeight() == 0 || sWidth <= 0 || sHeight <= 0) {
            return;
        }

        // If waiting to translate to new center position, set translate now
        if (sPendingCenter != null && pendingScale != null) {
            scale = pendingScale;
            if (vTranslate == null) {
                vTranslate = new PointF();
            }
            vTranslate.x = (getWidth() / 2.0f) - (scale * sPendingCenter.x);
            vTranslate.y = (getHeight() / 2.0f) - (scale * sPendingCenter.y);
            sPendingCenter = null;
            pendingScale = null;
            fitToBounds(true);
            refreshRequiredTiles(true);
        }

        // On first display of base image set up position, and in other cases make sure scale is
        // correct.
        fitToBounds(false);
    }

    /** Calculates sample size to fit the source image in given bounds. */
    public int calculateInSampleSize(float scale) {
        if (minimumTileDpi > 0) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
            scale = (minimumTileDpi / averageDpi) * scale;
        }

        int reqWidth = (int) (SubsamplingScaleImageViewStateHelper.sWidth(this) * scale);
        int reqHeight = (int) (SubsamplingScaleImageViewStateHelper.sHeight(this) * scale);

        // Raw height and width of image
        int inSampleSize = 1;
        if (reqWidth == 0 || reqHeight == 0) {
            return 32;
        }

        if (SubsamplingScaleImageViewStateHelper.sHeight(this) > reqHeight || SubsamplingScaleImageViewStateHelper.sWidth(this) > reqWidth) {

            // Calculate ratios of height and width to requested height and width
            final int heightRatio = Math.round((float) SubsamplingScaleImageViewStateHelper.sHeight(this) / (float) reqHeight);
            final int widthRatio = Math.round((float) SubsamplingScaleImageViewStateHelper.sWidth(this) / (float) reqWidth);
            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = Math.min(heightRatio, widthRatio);
        }

        // We want the actual sample size that will be used, so round down to nearest power of 2.
        int power = 1;
        while (power * 2 < inSampleSize) {
            power = power * 2;
        }

        return power;
    }

    /**
     * Adjusts hypothetical future scale and translate values to keep scale within the allowed range
     * and the image on screen. Minimum scale is set so one dimension fills the view and the image
     * is centered on the other dimension. Used to calculate what the target of an animation should
     * be.
     *
     * @param center Whether the image should be centered in the dimension it's too small to fill.
     *     While animating this can be false to avoid changes in direction as bounds are reached.
     * @param sat The scale we want and the translation we're aiming for. The values are adjusted to
     *     be valid.
     */
    /**
     * Adjusts current scale and translate values to keep scale within the allowed range and the
     * image on screen. Minimum scale is set so one dimension fills the view and the image is
     * centered on the other dimension.
     *
     * @param center Whether the image should be centered in the dimension it's too small to fill.
     *     While animating this can be false to avoid changes in direction as bounds are reached.
     */
    public void fitToBounds(boolean center) {
        boolean init = false;

        if (vTranslate == null) {
            init = true;
            vTranslate = new PointF(0, 0);
        }

        if (satTemp == null) {
            satTemp = new ScaleAndTranslate(0, new PointF(0, 0));
        }

        satTemp.scale = scale;
        satTemp.vTranslate.set(vTranslate);
        SubsamplingScaleImageViewDrawHelper.fitToBounds(this, center, satTemp);
        scale = satTemp.scale;
        vTranslate.set(satTemp.vTranslate);

        if (init && minimumScaleType != SCALE_TYPE_START) {
            vTranslate.set(SubsamplingScaleImageViewStateHelper.vTranslateForSCenter(
                this, SubsamplingScaleImageViewStateHelper.sWidth(this) / 2.0f, SubsamplingScaleImageViewStateHelper.sHeight(this) / 2.0f, scale));
        }
    }

    /**
     * Once source image and view dimensions are known, creates a map of sample size to tile grid.
     */
    public void initialiseTileMap(Point maxTileDimensions) {
        TileManager.initialiseTileMap(this, maxTileDimensions);
    }

    /** Async task used to get image details without blocking the UI thread. */

    /** Async task used to load images without blocking the UI thread. */
    public static class TileLoadTask extends AsyncTask<Void, Void, Bitmap> {
        private final WeakReference<SubsamplingScaleImageView> viewRef;
        private final WeakReference<ImageRegionDecoder> decoderRef;
        private final WeakReference<Tile> tileRef;
        private Exception exception;

        public TileLoadTask(SubsamplingScaleImageView view, ImageRegionDecoder decoder, Tile tile) {
            this.viewRef = new WeakReference<>(view);
            this.decoderRef = new WeakReference<>(decoder);
            this.tileRef = new WeakReference<>(tile);
            tile.loading = true;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            try {
                SubsamplingScaleImageView view = viewRef.get();
                ImageRegionDecoder decoder = decoderRef.get();
                Tile tile = tileRef.get();
                if (decoder != null && tile != null && view != null && decoder.isReady() && tile.visible) {
                    view.debug("TileLoadTask.doInBackground, tile.sRect=%s, tile.sampleSize=%d", tile.sRect, tile.sampleSize);
                    view.decoderLock.readLock().lock();
                    try {
                        if (decoder.isReady()) {
                            // Update tile's file sRect according to rotation
                            SubsamplingScaleImageViewStateHelper.fileSRect(view, tile.sRect, tile.fileSRect);
                            if (view.sRegion != null) {
                                tile.fileSRect.offset(view.sRegion.left, view.sRegion.top);
                            }
                            return decoder.decodeRegion(tile.fileSRect, tile.sampleSize);
                        } else {
                            tile.loading = false;
                        }
                    } finally {
                        view.decoderLock.readLock().unlock();
                    }
                } else if (tile != null) {
                    tile.loading = false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to decode tile", e);
                this.exception = e;
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Failed to decode tile - OutOfMemoryError", e);
                this.exception = new RuntimeException(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            final SubsamplingScaleImageView subsamplingScaleImageView = viewRef.get();
            final Tile tile = tileRef.get();

            if (subsamplingScaleImageView != null && tile != null) {
                if (bitmap != null) {
                    tile.bitmap = bitmap;
                    tile.loading = false;
                    subsamplingScaleImageView.loader.onTileLoaded();
                } else if (exception != null
                        && subsamplingScaleImageView.onImageEventListener != null) {
                    subsamplingScaleImageView.onImageEventListener.onTileLoadError(exception);
                }
            }
        }
    }

    /** Async task used to load bitmap without blocking the UI thread. */
    public static class BitmapLoadTask extends AsyncTask<Void, Void, Integer> {
        private final WeakReference<SubsamplingScaleImageView> viewRef;
        private final WeakReference<Context> contextRef;
        private final WeakReference<DecoderFactory<? extends ImageDecoder>> decoderFactoryRef;
        private final Uri source;
        private final boolean preview;
        private Bitmap bitmap;
        private Exception exception;

        public BitmapLoadTask(SubsamplingScaleImageView view, Context context, DecoderFactory<? extends ImageDecoder> decoderFactory, Uri source, boolean preview) {
            this.viewRef = new WeakReference<>(view);
            this.contextRef = new WeakReference<>(context);
            this.decoderFactoryRef = new WeakReference<DecoderFactory<? extends ImageDecoder>>(decoderFactory);
            this.source = source;
            this.preview = preview;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            try {
                String sourceUri = source.toString();
                Context context = contextRef.get();
                DecoderFactory<? extends ImageDecoder> decoderFactory = decoderFactoryRef.get();
                SubsamplingScaleImageView view = viewRef.get();

                if (context != null && decoderFactory != null && view != null) {
                    view.debug("BitmapLoadTask.doInBackground");
                    try {
                        bitmap = decoderFactory.make().decode(context, source);
                    } catch (OutOfMemoryError e) {
                        System.gc();
                        bitmap = decoderFactory.make().decode(context, source);
                    }

                    return view.loader.getExifOrientation(context, sourceUri);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load bitmap", e);
                this.exception = e;
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "Failed to load bitmap - OutOfMemoryError", e);
                this.exception = new RuntimeException(e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Integer orientation) {
            SubsamplingScaleImageView subsamplingScaleImageView = viewRef.get();

            if (subsamplingScaleImageView != null) {
                if (bitmap != null && orientation != null) {
                    if (preview) {
                        subsamplingScaleImageView.loader.onPreviewLoaded(bitmap);
                    } else {
                        subsamplingScaleImageView.loader.onImageLoaded(bitmap, orientation, false);
                    }
                } else if (exception != null
                        && subsamplingScaleImageView.onImageEventListener != null) {
                    if (preview) {
                        subsamplingScaleImageView.onImageEventListener.onPreviewLoadError(
                                exception);
                    } else {
                        subsamplingScaleImageView.onImageEventListener.onImageLoadError(exception);
                    }
                }
            }
        }
    }

    public void execute(AsyncTask<Void, Void, ?> asyncTask) {
        asyncTask.executeOnExecutor(executor);
    }
    public static class Tile {
        public Rect sRect;
        public int sampleSize;
        public Bitmap bitmap;
        public boolean loading;
        public boolean visible;

        // Volatile fields instantiated once then updated before use to reduce GC.
        public Rect vRect;
        public Rect fileSRect;
    }

    public static class Anim {
        public float scaleStart; // Scale at start of anim
        public float scaleEnd; // Scale at end of anim (target)
        public PointF sCenterStart; // Source center point at start
        public PointF sCenterEnd; // Source center point at end, adjusted for pan limits
        public PointF sCenterEndRequested; // Source center point that was requested, without adjustment
        public PointF vFocusStart; // View point that was double tapped
        public PointF vFocusEnd; // Where the view focal point should be moved to during the anim
        public long duration = 500; // How long the anim takes
        public boolean interruptible = true; // Whether the anim can be interrupted by a touch
        public int easing = EASE_IN_OUT_QUAD; // Easing style
        public int origin = ORIGIN_ANIM; // Animation origin (API, double tap or fling)
        public long time = System.currentTimeMillis(); // Start time
        public OnAnimationEventListener listener; // Event listener
    }

    public static class ScaleAndTranslate {
        // Make constructor public so helper class can access it
        public ScaleAndTranslate(float scale, PointF vTranslate) {
            this.scale = scale;
            this.vTranslate = vTranslate;
        }

        public float scale;
        public final PointF vTranslate;
    }

    /** Set scale, center and orientation from saved state. */
    public void restoreState(ImageViewState state) {
        if (state != null && VALID_ORIENTATIONS.contains(state.getOrientation())) {
            this.orientation = state.getOrientation();
            this.pendingScale = state.getScale();
            this.sPendingCenter = state.getCenter();
            invalidate();
        }
    }

    /**
     * By default the View automatically calculates the optimal tile size. Set this to override
     * this, and force an upper limit to the dimensions of the generated tiles. Passing {@link
     * #TILE_SIZE_AUTO} will re-enable the default behaviour.
     *
     * @param maxPixels
     */
    public void setMaxTileSize(int maxPixels) {
        this.maxTileWidth = maxPixels;
        this.maxTileHeight = maxPixels;
    }

    /**
     * By default the View automatically calculates the optimal tile size. Set this to override
     * this, and force an upper limit to the dimensions of the generated tiles. Passing {@link
     * #TILE_SIZE_AUTO} will re-enable the default behaviour.
     *
     * @param maxPixelsX Maximum tile width.
     * @param maxPixelsY Maximum tile height.
     */
    public void setMaxTileSize(int maxPixelsX, int maxPixelsY) {
        this.maxTileWidth = maxPixelsX;
        this.maxTileHeight = maxPixelsY;
    }

    /**
     * Use canvas max bitmap width and height instead of the default 2048, to avoid redundant
     * tiling.
     */
    @NonNull
    public Point getMaxBitmapDimensions(Canvas canvas) {
        return new Point(Math.min(canvas.getMaximumBitmapWidth(), maxTileWidth), Math.min(canvas.getMaximumBitmapHeight(), maxTileHeight));
    }

    /** Pythagoras distance between two points. */
    private float distance(float x0, float x1, float y0, float y1) {
        float x = x0 - x1;
        float y = y0 - y1;

        return (float) Math.sqrt(x * x + y * y);
    }

    /**
     * Releases all resources the view is using and resets the state, nulling any fields that use
     * significant memory. After you have called this method, the view can be re-used by setting a
     * new image. Settings are remembered but state (scale and center) is forgotten. You can restore
     * these yourself if required.
     */
    public void recycle() {
        loader.reset(true);
        bitmapPaint = null;
        debugTextPaint = null;
        debugLinePaint = null;
        tileBgPaint = null;
    }

    /**
     * Apply a selected type of easing.
     *
     * @param type Easing type, from static fields
     * @param time Elapsed time
     * @param from Start value
     * @param change Target value
     * @param duration Anm duration
     * @return Current value
     */
    public float ease(int type, long time, float from, float change, long duration) {
        switch (type) {
            case EASE_IN_OUT_QUAD:
                return easeInOutQuad(time, from, change, duration);
            case EASE_OUT_QUAD:
                return easeOutQuad(time, from, change, duration);
            default:
                throw new IllegalStateException("Unexpected easing type: " + type);
        }
    }

    /**
     * Quadratic easing for fling. With thanks to Robert Penner - http://gizma.com/easing/
     *
     * @param time Elapsed time
     * @param from Start value
     * @param change Target value
     * @param duration Anm duration
     * @return Current value
     */
    public float easeOutQuad(long time, float from, float change, long duration) {
        float progress = (float) time / (float) duration;

        return -change * progress * (progress - 2) + from;
    }

    /**
     * Quadratic easing for scale and center animations. With thanks to Robert Penner -
     * http://gizma.com/easing/
     *
     * @param time Elapsed time
     * @param from Start value
     * @param change Target value
     * @param duration Anm duration
     * @return Current value
     */
    public float easeInOutQuad(long time, float from, float change, long duration) {
        float timeF = time / (duration / 2f);

        if (timeF < 1) {
            return (change / 2f * timeF * timeF) + from;
        } else {
            timeF--;

            return (-change / 2f) * (timeF * (timeF - 2) - 1) + from;
        }
    }

    /** Debug logger */
    @AnyThread
    public void debug(String message, Object... args) {
        if (debug) {
            Log.d(TAG, String.format(message, args));
        }
    }


    /**
     * Swap the default region decoder implementation for one of your own. You must do this before
     * setting the image file or asset, and you cannot use a custom decoder when using layout XML to
     * set an asset name. Your class must have a public default constructor.
     *
     * @param regionDecoderClass The {@link ImageRegionDecoder} implementation to use.
     */
    public final void setRegionDecoderClass(@NonNull Class<? extends ImageRegionDecoder> regionDecoderClass) {
        // noinspection ConstantConditions
        if (regionDecoderClass == null) {
            throw new IllegalArgumentException("Decoder class cannot be set to null");
        }

        this.regionDecoderFactory = new CompatDecoderFactory<>(regionDecoderClass);
    }

    /**
     * Swap the default region decoder implementation for one of your own. You must do this before
     * setting the image file or asset, and you cannot use a custom decoder when using layout XML to
     * set an asset name.
     *
     * @param regionDecoderFactory The {@link DecoderFactory} implementation that produces {@link
     *     ImageRegionDecoder} instances.
     */
    public final void setRegionDecoderFactory(@NonNull DecoderFactory<? extends ImageRegionDecoder> regionDecoderFactory) {
        // noinspection ConstantConditions
        if (regionDecoderFactory == null) {
            throw new IllegalArgumentException("Decoder factory cannot be set to null");
        }

        this.regionDecoderFactory = regionDecoderFactory;
    }

    /**
     * Swap the default bitmap decoder implementation for one of your own. You must do this before
     * setting the image file or asset, and you cannot use a custom decoder when using layout XML to
     * set an asset name. Your class must have a public default constructor.
     *
     * @param bitmapDecoderClass The {@link ImageDecoder} implementation to use.
     */
    public final void setBitmapDecoderClass(@NonNull Class<? extends ImageDecoder> bitmapDecoderClass) {
        // noinspection ConstantConditions
        if (bitmapDecoderClass == null) {
            throw new IllegalArgumentException("Decoder class cannot be set to null");
        }

        this.bitmapDecoderFactory = new CompatDecoderFactory<>(bitmapDecoderClass);
    }

    /**
     * Swap the default bitmap decoder implementation for one of your own. You must do this before
     * setting the image file or asset, and you cannot use a custom decoder when using layout XML to
     * set an asset name.
     *
     * @param bitmapDecoderFactory The {@link DecoderFactory} implementation that produces {@link
     *     ImageDecoder} instances.
     */
    public final void setBitmapDecoderFactory(@NonNull DecoderFactory<? extends ImageDecoder> bitmapDecoderFactory) {
        // noinspection ConstantConditions
        if (bitmapDecoderFactory == null) {
            throw new IllegalArgumentException("Decoder factory cannot be set to null");
        }

        this.bitmapDecoderFactory = bitmapDecoderFactory;
    }

    /**
     * Calculate how much further the image can be panned in each direction. The results are set on
     * the supplied {@link RectF} and expressed as screen pixels. For example, if the image cannot
     * be panned any further towards the left, the value of {@link RectF#left} will be set to 0.
     *
     * @param vTarget target object for results. Re-use for efficiency.
     */
    public final void getPanRemaining(RectF vTarget) {
        if (!isReady()) {
            return;
        }

        float scaleWidth = scale * SubsamplingScaleImageViewStateHelper.sWidth(this);
        float scaleHeight = scale * SubsamplingScaleImageViewStateHelper.sHeight(this);

        if (panLimit == PAN_LIMIT_CENTER) {
            vTarget.top = Math.max(0, -(vTranslate.y - (getHeight() / 2.0f)));
            vTarget.left = Math.max(0, -(vTranslate.x - (getWidth() / 2.0f)));
            vTarget.bottom = Math.max(0, vTranslate.y - ((getHeight() / 2.0f) - scaleHeight));
            vTarget.right = Math.max(0, vTranslate.x - ((getWidth() / 2.0f) - scaleWidth));
        } else if (panLimit == PAN_LIMIT_OUTSIDE) {
            vTarget.top = Math.max(0, -(vTranslate.y - getHeight()));
            vTarget.left = Math.max(0, -(vTranslate.x - getWidth()));
            vTarget.bottom = Math.max(0, vTranslate.y + scaleHeight);
            vTarget.right = Math.max(0, vTranslate.x + scaleWidth);
        } else {
            vTarget.top = Math.max(0, -vTranslate.y);
            vTarget.left = Math.max(0, -vTranslate.x);
            vTarget.bottom = Math.max(0, (scaleHeight + vTranslate.y) - getHeight());
            vTarget.right = Math.max(0, (scaleWidth + vTranslate.x) - getWidth());
        }
    }

    /**
     * Set the pan limiting style. See static fields. Normally {@link #PAN_LIMIT_INSIDE} is best,
     * for image galleries.
     *
     * @param panLimit a pan limit constant. See static fields.
     */
    public final void setPanLimit(int panLimit) {
        if (!VALID_PAN_LIMITS.contains(panLimit)) {
            throw new IllegalArgumentException("Invalid pan limit: " + panLimit);
        }

        this.panLimit = panLimit;

        if (isReady()) {
            fitToBounds(true);
            invalidate();
        }
    }

    /**
     * Set the minimum scale type. See static fields. Normally {@link #SCALE_TYPE_CENTER_INSIDE} is
     * best, for image galleries.
     *
     * @param scaleType a scale type constant. See static fields.
     */
    public final void setMinimumScaleType(int scaleType) {
        if (!VALID_SCALE_TYPES.contains(scaleType)) {
            throw new IllegalArgumentException("Invalid scale type: " + scaleType);
        }

        this.minimumScaleType = scaleType;

        if (isReady()) {
            fitToBounds(true);
            invalidate();
        }
    }

    /**
     * Set the maximum scale allowed. A value of 1 means 1:1 pixels at maximum scale. You may wish
     * to set this according to screen density - on a retina screen, 1:1 may still be too small.
     * Consider using {@link #setMinimumDpi(int)}, which is density aware.
     *
     * @param maxScale maximum scale expressed as a source/view pixels ratio.
     */
    public final void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }

    /**
     * Set the minimum scale allowed. A value of 1 means 1:1 pixels at minimum scale. You may wish
     * to set this according to screen density. Consider using {@link #setMaximumDpi(int)}, which is
     * density aware.
     *
     * @param minScale minimum scale expressed as a source/view pixels ratio.
     */
    public final void setMinScale(float minScale) {
        this.minScale = minScale;
    }

    /**
     * This is a screen density aware alternative to {@link #setMaxScale(float)}; it allows you to
     * express the maximum allowed scale in terms of the minimum pixel density. This avoids the
     * problem of 1:1 scale still being too small on a high density screen. A sensible starting
     * point is 160 - the default used by this view.
     *
     * @param dpi Source image pixel density at maximum zoom.
     */
    public final void setMinimumDpi(int dpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
        setMaxScale(averageDpi / dpi);
    }

    /**
     * This is a screen density aware alternative to {@link #setMinScale(float)}; it allows you to
     * express the minimum allowed scale in terms of the maximum pixel density.
     *
     * @param dpi Source image pixel density at minimum zoom.
     */
    public final void setMaximumDpi(int dpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
        setMinScale(averageDpi / dpi);
    }

    /**
     * Returns the maximum allowed scale.
     *
     * @return the maximum scale as a source/view pixels ratio.
     */
    public float getMaxScale() {
        return maxScale;
    }

    /**
     * Returns the minimum allowed scale.
     *
     * @return the minimum scale as a source/view pixels ratio.
     */
    public final float getMinScale() {
        return SubsamplingScaleImageViewStateHelper.minScale(this);
    }

    /**
     * By default, image tiles are at least as high resolution as the screen. For a retina screen
     * this may not be necessary, and may increase the likelihood of an OutOfMemoryError. This
     * method sets a DPI at which higher resolution tiles should be loaded. Using a lower number
     * will on average use less memory but result in a lower quality image. 160-240dpi will usually
     * be enough. This should be called before setting the image source, because it affects which
     * tiles get loaded. When using an untiled source image this method has no effect.
     *
     * @param minimumTileDpi Tile loading threshold.
     */
    public void setMinimumTileDpi(int minimumTileDpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
        this.minimumTileDpi = (int) Math.min(averageDpi, minimumTileDpi);

        if (isReady()) {
            loader.reset(false);
            invalidate();
        }
    }

    /**
     * Returns the source point at the center of the view.
     *
     * @return the source coordinates current at the center of the view.
     */
    @Nullable
    public final PointF getCenter() {
        return SubsamplingScaleImageViewStateHelper.getCenter(this);
    }

    /**
     * Returns the current scale value.
     *
     * @return the current scale as a source/view pixels ratio.
     */
    public final float getScale() {
        return scale;
    }

    /**
     * Externally change the scale and translation of the source image. This may be used with
     * getCenter() and getScale() to restore the scale and zoom after a screen rotate.
     *
     * @param scale New scale to set.
     * @param sCenter New source image coordinate to center on the screen, subject to boundaries.
     */
    public final void setScaleAndCenter(float scale, @Nullable PointF sCenter) {
        this.anim = null;
        this.pendingScale = scale;
        this.sPendingCenter = sCenter;
        this.sRequestedCenter = sCenter;
        invalidate();
    }

    /**
     * Fully zoom out and return the image to the middle of the screen. This might be useful if you
     * have a view pager and want images to be reset when the user has moved to another page.
     */
    public final void resetScaleAndCenter() {
        this.anim = null;
        this.pendingScale = SubsamplingScaleImageViewStateHelper.limitedScale(this, 0);

        if (isReady()) {
            this.sPendingCenter = new PointF(SubsamplingScaleImageViewStateHelper.sWidth(this) / 2.0f, SubsamplingScaleImageViewStateHelper.sHeight(this) / 2.0f);
        } else {
            this.sPendingCenter = new PointF(0, 0);
        }

        invalidate();
    }

    /**
     * Call to find whether the view is initialised, has dimensions, and will display an image on
     * the next draw. If a preview has been provided, it may be the preview that will be displayed
     * and the full size image may still be loading. If no preview was provided, this is called once
     * the base layer tiles of the full size image are loaded.
     *
     * @return true if the view is ready to display an image and accept touch gestures.
     */
    public final boolean isReady() {
        return readySent;
    }

    /**
     * Called once when the view is initialised, has dimensions, and will display an image on the
     * next draw. This is triggered at the same time as {@link OnImageEventListener#onReady()} but
     * allows a subclass to receive this event without using a listener.
     */
    @SuppressWarnings("EmptyMethod")
    protected void onReady() {}

    /**
     * Call to find whether the main image (base layer tiles where relevant) have been loaded.
     * Before this event the view is blank unless a preview was provided.
     *
     * @return true if the main image (not the preview) has been loaded and is ready to display.
     */
    public final boolean isImageLoaded() {
        return imageLoadedSent;
    }

    /** Called once when the full size image or its base layer tiles have been loaded. */
    @SuppressWarnings("EmptyMethod")
    protected void onImageLoaded() {}

    /**
     * Get source width, ignoring orientation. If {@link #getOrientation()} returns 90 or 270, you
     * can use {@link #getSHeight()} for the apparent width.
     *
     * @return the source image width in pixels.
     */
    public final int getSWidth() {
        return sWidth;
    }

    /**
     * Get source height, ignoring orientation. If {@link #getOrientation()} returns 90 or 270, you
     * can use {@link #getSWidth()} for the apparent height.
     *
     * @return the source image height in pixels.
     */
    public final int getSHeight() {
        return sHeight;
    }

    // Getter for the private minScale field, needed by helper
    public float getMinScaleField() {
        return minScale;
    }

    // Getter for minimumScaleType, needed by helper
    public int getMinimumScaleType() {
        return minimumScaleType;
    }

    /**
     * Returns the orientation setting. This can return {@link #ORIENTATION_USE_EXIF}, in which case
     * it doesn't tell you the applied orientation of the image. For that, use {@link
     * #getAppliedOrientation()}.
     *
     * @return the orientation setting. See static fields.
     */
    public final int getOrientation() {
        return orientation;
    }

    /**
     * Returns the actual orientation of the image relative to the source file. This will be based
     * on the source file's EXIF orientation if you're using ORIENTATION_USE_EXIF. Values are 0, 90,
     * 180, 270.
     *
     * @return the orientation applied after EXIF information has been extracted. See static fields.
     */
    public final int getAppliedOrientation() {
        return SubsamplingScaleImageViewStateHelper.getRequiredRotation(this);
    }

    /**
     * Get the current state of the view (scale, center, orientation) for restoration after rotate.
     * Will return null if the view is not ready.
     *
     * @return an {@link ImageViewState} instance representing the current position of the image.
     *     null if the view isn't ready.
     */
    @Nullable
    public final ImageViewState getState() {
        if (vTranslate != null && sWidth > 0 && sHeight > 0) {
            // noinspection ConstantConditions
            return new ImageViewState(getScale(), SubsamplingScaleImageViewStateHelper.getCenter(this), getOrientation());
        }

        return null;
    }

    /**
     * Returns true if zoom gesture detection is enabled.
     *
     * @return true if zoom gesture detection is enabled.
     */
    public final boolean isZoomEnabled() {
        return zoomEnabled;
    }

    /**
     * Enable or disable zoom gesture detection. Disabling zoom locks the the current scale.
     *
     * @param zoomEnabled true to enable zoom gestures, false to disable.
     */
    public final void setZoomEnabled(boolean zoomEnabled) {
        this.zoomEnabled = zoomEnabled;
    }

    /**
     * Returns true if double tap &amp; swipe to zoom is enabled.
     *
     * @return true if double tap &amp; swipe to zoom is enabled.
     */
    public final boolean isQuickScaleEnabled() {
        return quickScaleEnabled;
    }

    /**
     * Enable or disable double tap &amp; swipe to zoom.
     *
     * @param quickScaleEnabled true to enable quick scale, false to disable.
     */
    public final void setQuickScaleEnabled(boolean quickScaleEnabled) {
        this.quickScaleEnabled = quickScaleEnabled;
    }

    /**
     * Returns true if pan gesture detection is enabled.
     *
     * @return true if pan gesture detection is enabled.
     */
    public final boolean isPanEnabled() {
        return panEnabled;
    }

    /**
     * Enable or disable pan gesture detection. Disabling pan causes the image to be centered. Pan
     * can still be changed from code.
     *
     * @param panEnabled true to enable panning, false to disable.
     */
    public final void setPanEnabled(boolean panEnabled) {
        this.panEnabled = panEnabled;

        if (!panEnabled && vTranslate != null) {
            vTranslate.x = (getWidth() / 2.0f) - (scale * (SubsamplingScaleImageViewStateHelper.sWidth(this) / 2.0f));
            vTranslate.y = (getHeight() / 2.0f) - (scale * (SubsamplingScaleImageViewStateHelper.sHeight(this) / 2.0f));

            if (isReady()) {
                refreshRequiredTiles(true);
                invalidate();
            }
        }
    }

    /**
     * Set a solid color to render behind tiles, useful for displaying transparent PNGs.
     *
     * @param tileBgColor Background color for tiles.
     */
    public final void setTileBackgroundColor(int tileBgColor) {
        if (Color.alpha(tileBgColor) == 0) {
            tileBgPaint = null;
        } else {
            tileBgPaint = new Paint();
            tileBgPaint.setStyle(Style.FILL);
            tileBgPaint.setColor(tileBgColor);
        }

        invalidate();
    }

    /**
     * Set the scale the image will zoom in to when double tapped. This also the scale point where a
     * double tap is interpreted as a zoom out gesture - if the scale is greater than 90% of this
     * value, a double tap zooms out. Avoid using values greater than the max zoom.
     *
     * @param doubleTapZoomScale New value for double tap gesture zoom scale.
     */
    public final void setDoubleTapZoomScale(float doubleTapZoomScale) {
        this.doubleTapZoomScale = doubleTapZoomScale;
    }

    /**
     * A density aware alternative to {@link #setDoubleTapZoomScale(float)}; this allows you to
     * express the scale the image will zoom in to when double tapped in terms of the image pixel
     * density. Values lower than the max scale will be ignored. A sensible starting point is 160 -
     * the default used by this view.
     *
     * @param dpi New value for double tap gesture zoom scale.
     */
    public final void setDoubleTapZoomDpi(int dpi) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        float averageDpi = (metrics.xdpi + metrics.ydpi) / 2;
        setDoubleTapZoomScale(averageDpi / dpi);
    }

    /**
     * Set the type of zoom animation to be used for double taps. See static fields.
     *
     * @param doubleTapZoomStyle New value for zoom style.
     */
    public final void setDoubleTapZoomStyle(int doubleTapZoomStyle) {
        if (!VALID_ZOOM_STYLES.contains(doubleTapZoomStyle)) {
            throw new IllegalArgumentException("Invalid zoom style: " + doubleTapZoomStyle);
        }

        this.doubleTapZoomStyle = doubleTapZoomStyle;
    }

    /**
     * Set the duration of the double tap zoom animation.
     *
     * @param durationMs Duration in milliseconds.
     */
    public final void setDoubleTapZoomDuration(int durationMs) {
        this.doubleTapZoomDuration = Math.max(0, durationMs);
    }

    /**
     * Provide an {@link Executor} to be used for loading images. By default, {@link
     * AsyncTask#THREAD_POOL_EXECUTOR} is used to minimise contention with other background work the
     * app is doing. You can also choose to use {@link AsyncTask#SERIAL_EXECUTOR} if you want to
     * limit concurrent background tasks. Alternatively you can supply an {@link Executor} of your
     * own to avoid any contention. It is strongly recommended to use a single executor instance for
     * the life of your application, not one per view instance.
     *
     * <p><b>Warning:</b> If you are using a custom implementation of {@link ImageRegionDecoder},
     * and you supply an executor with more than one thread, you must make sure your implementation
     * supports multi-threaded bitmap decoding or has appropriate internal synchronization. From SDK
     * 21, Android's {@link android.graphics.BitmapRegionDecoder} uses an internal lock so it is
     * thread safe but there is no advantage to using multiple threads.
     *
     * @param executor an {@link Executor} for image loading.
     */
    public void setExecutor(@NonNull Executor executor) {
        // noinspection ConstantConditions
        if (executor == null) {
            throw new NullPointerException("Executor must not be null");
        }

        this.executor = executor;
    }

    /**
     * Enable or disable eager loading of tiles that appear on screen during gestures or animations,
     * while the gesture or animation is still in progress. By default this is enabled to improve
     * responsiveness, but it can result in tiles being loaded and discarded more rapidly than
     * necessary and reduce the animation frame rate on old/cheap devices. Disable this on older
     * devices if you see poor performance. Tiles will then be loaded only when gestures and
     * animations are completed.
     *
     * @param eagerLoadingEnabled true to enable loading during gestures, false to delay loading
     *     until gestures end
     */
    public void setEagerLoadingEnabled(boolean eagerLoadingEnabled) {
        this.eagerLoadingEnabled = eagerLoadingEnabled;
    }

    /**
     * Enables visual debugging, showing tile boundaries and sizes.
     *
     * @param debug true to enable debugging, false to disable.
     */
    public final void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Check if an image has been set. The image may not have been loaded and displayed yet.
     *
     * @return If an image is currently set.
     */
    public boolean hasImage() {
        return uri != null || bitmap != null;
    }

    /** {@inheritDoc} */
    @Override
    public void setOnLongClickListener(OnLongClickListener onLongClickListener) {
        this.onLongClickListener = onLongClickListener;
    }

    /**
     * Add a listener allowing notification of load and error events. Extend {@link
     * DefaultOnImageEventListener} to simplify implementation.
     *
     * @param onImageEventListener an {@link OnImageEventListener} instance.
     */
    public void setOnImageEventListener(OnImageEventListener onImageEventListener) {
        this.onImageEventListener = onImageEventListener;
    }

    /**
     * Add a listener for pan and zoom events. Extend {@link DefaultOnStateChangedListener} to
     * simplify implementation.
     *
     * @param onStateChangedListener an {@link OnStateChangedListener} instance.
     */
    public void setOnStateChangedListener(OnStateChangedListener onStateChangedListener) {
        this.onStateChangedListener = onStateChangedListener;
    }

    public void sendStateChanged(float oldScale, PointF oldVTranslate, int origin) {
        if (onStateChangedListener != null && scale != oldScale) {
            onStateChangedListener.onScaleChanged(scale, origin);
        }

        if (onStateChangedListener != null && !vTranslate.equals(oldVTranslate)) {
            onStateChangedListener.onCenterChanged(SubsamplingScaleImageViewStateHelper.getCenter(this), origin);
        }
    }

    /**
     * Creates a panning animation builder, that when started will animate the image to place the
     * given coordinates of the image in the center of the screen. If doing this would move the
     * image beyond the edges of the screen, the image is instead animated to move the center point
     * as near to the center of the screen as is allowed - it's guaranteed to be on screen.
     *
     * @param sCenter Target center point
     * @return {@link AnimationBuilder} instance. Call {@link
     *     SubsamplingScaleImageView.AnimationBuilder#start()} to start the anim.
     */
    @Nullable
    public AnimationBuilder animateCenter(PointF sCenter) {
        if (!isReady()) {
            return null;
        }

        return new AnimationBuilder(this, sCenter);
    }

    /**
     * Creates a scale animation builder, that when started will animate a zoom in or out. If this
     * would move the image beyond the panning limits, the image is automatically panned during the
     * animation.
     *
     * @param scale Target scale.
     * @return {@link AnimationBuilder} instance. Call {@link
     *     SubsamplingScaleImageView.AnimationBuilder#start()} to start the anim.
     */
    @Nullable
    public AnimationBuilder animateScale(float scale) {
        if (!isReady()) {
            return null;
        }

        return new AnimationBuilder(this, scale);
    }

    /**
     * Creates a scale animation builder, that when started will animate a zoom in or out. If this
     * would move the image beyond the panning limits, the image is automatically panned during the
     * animation.
     *
     * @param scale Target scale.
     * @param sCenter Target source center.
     * @return {@link AnimationBuilder} instance. Call {@link
     *     SubsamplingScaleImageView.AnimationBuilder#start()} to start the anim.
     */
    @Nullable
    public AnimationBuilder animateScaleAndCenter(float scale, PointF sCenter) {
        if (!isReady()) {
            return null;
        }

        return new AnimationBuilder(this, scale, sCenter);
    }

    /**
     * An event listener for animations, allows events to be triggered when an animation completes,
     * is aborted by another animation starting, or is aborted by a touch event. Note that none of
     * these events are triggered if the activity is paused, the image is swapped, or in other cases
     * where the view's internal state gets wiped or draw events stop.
     */
    @SuppressWarnings("EmptyMethod")
    public interface OnAnimationEventListener {
        /** The animation has completed, having reached its endpoint. */
        void onComplete();

        /**
         * The animation has been aborted before reaching its endpoint because the user touched the
         * screen.
         */
        void onInterruptedByUser();

        /**
         * The animation has been aborted before reaching its endpoint because a new animation has
         * been started.
         */
        void onInterruptedByNewAnim();
    }

    /**
     * Default implementation of {@link OnAnimationEventListener} for extension. This does nothing
     * in any method.
     */
    public static class DefaultOnAnimationEventListener implements OnAnimationEventListener {
        @Override
        public void onComplete() {}

        @Override
        public void onInterruptedByUser() {}

        @Override
        public void onInterruptedByNewAnim() {}
    }

    /**
     * An event listener, allowing subclasses and activities to be notified of significant events.
     */
    @SuppressWarnings("EmptyMethod")
    public interface OnImageEventListener {

        /**
         * Called when the dimensions of the image and view are known, and either a preview image,
         * the full size image, or base layer tiles are loaded. This indicates the scale and
         * translate are known and the next draw will display an image. This event can be used to
         * hide a loading graphic, or inform a subclass that it is safe to draw overlays.
         */
        void onReady();

        /**
         * Called when the full size image is ready. When using tiling, this means the lowest
         * resolution base layer of tiles are loaded, and when tiling is disabled, the image bitmap
         * is loaded. This event could be used as a trigger to enable gestures if you wanted
         * interaction disabled while only a preview is displayed, otherwise for most cases {@link
         * #onReady()} is the best event to listen to.
         */
        void onImageLoaded();

        /**
         * Called when a preview image could not be loaded. This method cannot be relied upon;
         * certain encoding types of supported image formats can result in corrupt or blank images
         * being loaded and displayed with no detectable error. The view will continue to load the
         * full size image.
         *
         * @param e The exception thrown. This error is logged by the view.
         */
        void onPreviewLoadError(Exception e);

        /**
         * Indicates an error initiliasing the decoder when using a tiling, or when loading the full
         * size bitmap when tiling is disabled. This method cannot be relied upon; certain encoding
         * types of supported image formats can result in corrupt or blank images being loaded and
         * displayed with no detectable error.
         *
         * @param e The exception thrown. This error is also logged by the view.
         */
        void onImageLoadError(Exception e);

        /**
         * Called when an image tile could not be loaded. This method cannot be relied upon; certain
         * encoding types of supported image formats can result in corrupt or blank images being
         * loaded and displayed with no detectable error. Most cases where an unsupported file is
         * used will result in an error caught by {@link #onImageLoadError(Exception)}.
         *
         * @param e The exception thrown. This error is logged by the view.
         */
        void onTileLoadError(Exception e);

        /**
         * Called when a bitmap set using ImageSource.cachedBitmap is no longer being used by the
         * View. This is useful if you wish to manage the bitmap after the preview is shown
         */
        void onPreviewReleased();
    }

    /**
     * Default implementation of {@link OnImageEventListener} for extension. This does nothing in
     * any method.
     */
    public static class DefaultOnImageEventListener implements OnImageEventListener {
        @Override
        public void onReady() {}

        @Override
        public void onImageLoaded() {}

        @Override
        public void onPreviewLoadError(Exception e) {}

        @Override
        public void onImageLoadError(Exception e) {}

        @Override
        public void onTileLoadError(Exception e) {}

        @Override
        public void onPreviewReleased() {}
    }

    /**
     * An event listener, allowing activities to be notified of pan and zoom events. Initialisation
     * and calls made by your code do not trigger events; touch events and animations do. Methods in
     * this listener will be called on the UI thread and may be called very frequently - your
     * implementation should return quickly.
     */
    @SuppressWarnings("EmptyMethod")
    public interface OnStateChangedListener {

        /**
         * The scale has changed. Use with {@link #getMaxScale()} and {@link #getMinScale()} to
         * determine whether the image is fully zoomed in or out.
         *
         * @param newScale The new scale.
         * @param origin Where the event originated from - one of {@link #ORIGIN_ANIM}, {@link
         *     #ORIGIN_TOUCH}.
         */
        void onScaleChanged(float newScale, int origin);

        /**
         * The source center has been changed. This can be a result of panning or zooming.
         *
         * @param newCenter The new source center point.
         * @param origin Where the event originated from - one of {@link #ORIGIN_ANIM}, {@link
         *     #ORIGIN_TOUCH}.
         */
        void onCenterChanged(PointF newCenter, int origin);
    }

    /**
     * Default implementation of {@link OnStateChangedListener}. This does nothing in any method.
     */
    public static class DefaultOnStateChangedListener implements OnStateChangedListener {

        @Override
        public void onCenterChanged(PointF newCenter, int origin) {}

        @Override
        public void onScaleChanged(float newScale, int origin) {}
    }
}
