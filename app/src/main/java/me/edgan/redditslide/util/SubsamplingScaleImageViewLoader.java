package me.edgan.redditslide.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PointF;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;

import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;

import java.util.List;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;

import java.util.Arrays;
import java.util.List;

import me.edgan.redditslide.Views.ImageSource;
import me.edgan.redditslide.Views.RapidImageRegionDecoder;
import me.edgan.redditslide.Views.SubsamplingScaleImageView;

/**
 * Helper class for SubsamplingScaleImageView responsible for
 * image loading, initialization, and related callbacks.
 */
public class SubsamplingScaleImageViewLoader {

    private final SubsamplingScaleImageView view;
    public ImageSource savedImageSource;

    public SubsamplingScaleImageViewLoader(@NonNull SubsamplingScaleImageView view) {
        this.view = view;
    }

    // Methods will be moved here...

    /**
     * Helper method for load tasks. Examines the EXIF info on the image file to determine the
     * orientation. This will only work for external files, not assets, resources or other URIs.
     */
    @AnyThread
    public int getExifOrientation(Context context, String sourceUri) {
        // Need access to constants from the view
        final List<Integer> VALID_ORIENTATIONS = Arrays.asList(
                SubsamplingScaleImageView.ORIENTATION_0,
                SubsamplingScaleImageView.ORIENTATION_90,
                SubsamplingScaleImageView.ORIENTATION_180,
                SubsamplingScaleImageView.ORIENTATION_270,
                SubsamplingScaleImageView.ORIENTATION_USE_EXIF
        );

        int exifOrientation = SubsamplingScaleImageView.ORIENTATION_0;
        if (sourceUri.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Cursor cursor = null;
            try {
                String[] columns = {MediaStore.Images.Media.ORIENTATION};
                cursor = context.getContentResolver().query(Uri.parse(sourceUri), columns, null, null, null);

                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int orientation = cursor.getInt(0);

                        if (VALID_ORIENTATIONS.contains(orientation) && orientation != SubsamplingScaleImageView.ORIENTATION_USE_EXIF) {
                            exifOrientation = orientation;
                        } else {
                            Log.w(SubsamplingScaleImageView.TAG, "Unsupported orientation: " + orientation);
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(SubsamplingScaleImageView.TAG, "Could not get orientation of image from media store");
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } else if (sourceUri.startsWith(ImageSource.FILE_SCHEME) && !sourceUri.startsWith(ImageSource.ASSET_SCHEME)) {
            try {
                ExifInterface exifInterface = new ExifInterface(sourceUri.substring(ImageSource.FILE_SCHEME.length() - 1));
                int orientationAttr = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (orientationAttr == ExifInterface.ORIENTATION_NORMAL || orientationAttr == ExifInterface.ORIENTATION_UNDEFINED) {
                    exifOrientation = SubsamplingScaleImageView.ORIENTATION_0;
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_90) {
                    exifOrientation = SubsamplingScaleImageView.ORIENTATION_90;
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_180) {
                    exifOrientation = SubsamplingScaleImageView.ORIENTATION_180;
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_270) {
                    exifOrientation = SubsamplingScaleImageView.ORIENTATION_270;
                } else {
                    Log.w(SubsamplingScaleImageView.TAG, "Unsupported EXIF orientation: " + orientationAttr);
                }
            } catch (Exception e) {
                Log.w(SubsamplingScaleImageView.TAG, "Could not get EXIF orientation of image");
            }
        }
        return exifOrientation;
    }


    /** Called by worker task when decoder is ready and image size and EXIF orientation is known. */
    public synchronized void onTilesInited(ImageRegionDecoder decoder, int sWidth, int sHeight, int sOrientation) {
        view.debug("onTilesInited sWidth=%d, sHeight=%d, sOrientation=%d", sWidth, sHeight, sOrientation);

        // If actual dimensions don't match the declared size, reset everything.
        if (view.sWidth > 0 && view.sHeight > 0 && (view.sWidth != sWidth || view.sHeight != sHeight)) {
            this.reset(false); // Call loader's own reset method

            if (view.bitmap != null) {
                if (!view.bitmapIsCached) {
                    view.bitmap.recycle();
                }

                view.bitmap = null;

                if (view.onImageEventListener != null && view.bitmapIsCached) {
                    view.onImageEventListener.onPreviewReleased();
                }

                view.bitmapIsPreview = false;
                view.bitmapIsCached = false;
            }
        }

        view.decoder = decoder;
        view.sWidth = sWidth;
        view.sHeight = sHeight;
        view.sOrientation = sOrientation;
        view.checkReady();

        if (!view.checkImageLoaded() && view.maxTileWidth > 0 && view.maxTileWidth != SubsamplingScaleImageView.TILE_SIZE_AUTO && view.maxTileHeight > 0 && view.maxTileHeight != SubsamplingScaleImageView.TILE_SIZE_AUTO && view.getWidth() > 0 && view.getHeight() > 0) {
            // Need to make initialiseBaseLayer accessible or move it too. Let's assume it will be moved later.
            // For now, we might need to make it public/package-private or call via loader if moved.
            // Temporarily making it public for this step.
             this.initialiseBaseLayer(new Point(view.maxTileWidth, view.maxTileHeight)); // Call loader's own method
        }

        view.invalidate();
        view.requestLayout();
        view.animate().setInterpolator(new FastOutSlowInInterpolator()).alpha(1);
    }

    /** Called by worker task when a tile has loaded. Redraws the view. */
    public synchronized void onTileLoaded() {
        view.debug("onTileLoaded");
        view.checkReady();
        view.checkImageLoaded();

        if (view.isBaseLayerReady() && view.bitmap != null) {
            if (!view.bitmapIsCached) {
                view.bitmap.recycle();
            }

            view.bitmap = null;

            if (view.onImageEventListener != null && view.bitmapIsCached) {
                view.onImageEventListener.onPreviewReleased();
            }

            view.bitmapIsPreview = false;
            view.bitmapIsCached = false;
        }

        view.invalidate();
        // The original method didn't have the alpha animation here, only in onTilesInited/onImageLoaded.
        // Keeping it consistent with the original onTileLoaded.
    }

    /** Called by worker task when preview image is loaded. */
    public synchronized void onPreviewLoaded(Bitmap previewBitmap) {
        view.debug("onPreviewLoaded");

        if (view.bitmap != null || view.imageLoadedSent) {
            previewBitmap.recycle();
            return;
        }

        if (view.pRegion != null) {
            view.bitmap =
                    Bitmap.createBitmap(
                            previewBitmap,
                            view.pRegion.left,
                            view.pRegion.top,
                            view.pRegion.width(),
                            view.pRegion.height());
        } else {
            view.bitmap = previewBitmap;
        }

        view.bitmapIsPreview = true;

        if (view.checkReady()) {
            view.invalidate();
            view.requestLayout();
        }
    }

    /** Called by worker task when full size image bitmap is ready (tiling is disabled). */
    public synchronized void onImageLoaded(Bitmap bitmap, int sOrientation, boolean bitmapIsCached) {
        view.debug("onImageLoaded");

        // If actual dimensions don't match the declared size, reset everything.
        if (view.sWidth > 0 && view.sHeight > 0 && (view.sWidth != bitmap.getWidth() || view.sHeight != bitmap.getHeight())) {
            this.reset(false); // Call loader's own reset method
        }

        if (view.bitmap != null && !view.bitmapIsCached) {
            view.bitmap.recycle();
        }

        if (view.bitmap != null && view.bitmapIsCached && view.onImageEventListener != null) {
            view.onImageEventListener.onPreviewReleased();
        }

        view.bitmapIsPreview = false;
        view.bitmapIsCached = bitmapIsCached;
        view.bitmap = bitmap;
        view.sWidth = bitmap.getWidth();
        view.sHeight = bitmap.getHeight();
        view.sOrientation = sOrientation;
        boolean ready = view.checkReady();
        boolean imageLoaded = view.checkImageLoaded();

        if (ready || imageLoaded) {
            view.invalidate();
            view.requestLayout();
        }

        view.animate().setInterpolator(new FastOutSlowInInterpolator()).alpha(1);
    }

    /** Reset all state before setting/changing image or setting new rotation. */
    public void reset(boolean newImage) {
        view.debug("reset newImage=" + newImage);
        view.scale = 0f;
        view.scaleStart = 0f;
        view.vTranslate = null;
        view.vTranslateStart = null;
        view.vTranslateBefore = null;
        view.pendingScale = 0f;
        view.sPendingCenter = null;
        view.sRequestedCenter = null;
        view.isZooming = false;
        view.isPanning = false;
        view.isQuickScaling = false;
        view.maxTouchCount = 0;
        view.fullImageSampleSize = 0;
        view.vCenterStart = null;
        view.vDistStart = 0;
        view.quickScaleLastDistance = 0f;
        view.quickScaleMoved = false;
        view.quickScaleSCenter = null;
        view.quickScaleVLastPoint = null;
        view.quickScaleVStart = null;
        view.anim = null;
        view.satTemp = null;
        view.matrix = null;
        view.sRect = null;

        if (newImage) {
            view.uri = null;
            view.decoderLock.writeLock().lock();
            try {
                if (view.decoder != null) {
                    view.decoder.recycle();
                    view.decoder = null;
                }
            } finally {
                view.decoderLock.writeLock().unlock();
            }

            if (view.bitmap != null && !view.bitmapIsCached) {
                view.bitmap.recycle();
            }

            if (view.bitmap != null && view.bitmapIsCached && view.onImageEventListener != null) {
                view.onImageEventListener.onPreviewReleased();
            }

            view.sWidth = 0;
            view.sHeight = 0;
            view.sOrientation = 0;
            view.sRegion = null;
            view.pRegion = null;
            view.readySent = false;
            view.imageLoadedSent = false;
            view.bitmap = null;
            view.bitmapIsPreview = false;
            view.bitmapIsCached = false;
        }

        if (view.tileMap != null) {
            for (java.util.Map.Entry<Integer, java.util.List<SubsamplingScaleImageView.Tile>> tileMapEntry : view.tileMap.entrySet()) {
                for (SubsamplingScaleImageView.Tile tile : tileMapEntry.getValue()) {
                    tile.visible = false;
                    if (tile.bitmap != null) {
                        tile.bitmap.recycle();
                        tile.bitmap = null;
                    }
                }
            }
            view.tileMap = null;
        }

        this.setGestureDetector(view.getContext());
    }

    // --- setImage and doLoader methods ---

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI.
     *
     * @param imageSource Image source.
     */
    public final void setImage(@NonNull ImageSource imageSource) {
        setImage(imageSource, null, null);
    }

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI, starting with a given
     * orientation setting, scale and center. This is the best method to use when you want scale and
     * center to be restored after screen orientation change; it avoids any redundant loading of
     * tiles in the wrong orientation.
     *
     * @param imageSource Image source.
     * @param state State to be restored. Nullable.
     */
    public final void setImage(@NonNull ImageSource imageSource, ImageViewState state) {
        setImage(imageSource, null, state);
    }

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI, providing a preview
     * image to be displayed until the full size image is loaded.
     *
     * <p>You must declare the dimensions of the full size image by calling {@link
     * ImageSource#dimensions(int, int)} on the imageSource object. The preview source will be
     * ignored if you don't provide dimensions, and if you provide a bitmap for the full size image.
     *
     * @param imageSource Image source. Dimensions must be declared.
     * @param previewSource Optional source for a preview image to be displayed and allow
     *     interaction while the full size image loads.
     */
    public final void setImage(@NonNull ImageSource imageSource, ImageSource previewSource) {
        setImage(imageSource, previewSource, null);
    }

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI, providing a preview
     * image to be displayed until the full size image is loaded, starting with a given orientation
     * setting, scale and center. This is the best method to use when you want scale and center to
     * be restored after screen orientation change; it avoids any redundant loading of tiles in the
     * wrong orientation.
     *
     * <p>You must declare the dimensions of the full size image by calling {@link
     * ImageSource#dimensions(int, int)} on the imageSource object. The preview source will be
     * ignored if you don't provide dimensions, and if you provide a bitmap for the full size image.
     *
     * @param imageSource Image source. Dimensions must be declared.
     * @param previewSource Optional source for a preview image to be displayed and allow
     *     interaction while the full size image loads.
     * @param state State to be restored. Nullable.
     */
    public final void setImage(
            @NonNull ImageSource imageSource, ImageSource previewSource, ImageViewState state) {
        // noinspection ConstantConditions
        view.setAlpha(0);

        if (imageSource == null) {
            throw new NullPointerException("imageSource must not be null");
        }

        reset(true);

        if (state != null) {
            view.restoreState(state);
        }

        if (previewSource != null) {
            if (imageSource.getBitmap() != null) {
                throw new IllegalArgumentException("Preview image cannot be used when a bitmap is provided for the main image");
            }

            if (imageSource.getSWidth() <= 0 || imageSource.getSHeight() <= 0) {
                throw new IllegalArgumentException("Preview image cannot be used unless dimensions are provided for the main image");
            }

            view.sWidth = imageSource.getSWidth();
            view.sHeight = imageSource.getSHeight();
            view.pRegion = previewSource.getSRegion(); // pRegion is public

            if (previewSource.getBitmap() != null) {
                view.bitmapIsCached = previewSource.isCached();
                onPreviewLoaded(previewSource.getBitmap());
            } else {
                Uri uri = previewSource.getUri();
                if (uri == null && previewSource.getResource() != null) {
                    uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + view.getContext().getPackageName() + "/" + previewSource.getResource());
                }
                SubsamplingScaleImageView.BitmapLoadTask task = new SubsamplingScaleImageView.BitmapLoadTask(view, view.getContext(), view.bitmapDecoderFactory, uri, true); // bitmapDecoderFactory is public
                view.execute(task);
            }
        }

        if (imageSource.getBitmap() != null && imageSource.getSRegion() != null) {
            onImageLoaded(
                    Bitmap.createBitmap(
                            imageSource.getBitmap(),
                            imageSource.getSRegion().left,
                            imageSource.getSRegion().top,
                            imageSource.getSRegion().width(),
                            imageSource.getSRegion().height()),
                    SubsamplingScaleImageView.ORIENTATION_0,
                    false);
        } else if (imageSource.getBitmap() != null) {
            onImageLoaded(imageSource.getBitmap(), SubsamplingScaleImageView.ORIENTATION_0, imageSource.isCached());
        } else {
            view.sRegion = imageSource.getSRegion();
            view.uri = imageSource.getUri(); // uri is public

            if (view.uri == null && imageSource.getResource() != null) {
                view.uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + view.getContext().getPackageName() + "/" + imageSource.getResource());
            }

            doLoader(imageSource, false);
        }
    }

    public void doLoader(boolean rapid) {
        doLoader(this.savedImageSource, rapid);
    }

    public void doLoader(ImageSource imageSource, boolean rapid) {
        if (imageSource.getTile() || view.sRegion != null) {

            // Load the bitmap using tile decoding.
            if (rapid) {
                view.setRegionDecoderClass(RapidImageRegionDecoder.class); // Assuming this method remains public or becomes public
                TilesInitTask task = new TilesInitTask(view, view.getContext(), view.regionDecoderFactory, view.uri); // regionDecoderFactory and uri are public
                view.execute(task);
            } else {
                this.savedImageSource = imageSource;
                TilesInitTask task = new TilesInitTask(view, view.getContext(), view.regionDecoderFactory, view.uri); // regionDecoderFactory and uri are public
                view.execute(task);
            }
        } else {
            // Load the bitmap as a single image.
            SubsamplingScaleImageView.BitmapLoadTask task = new SubsamplingScaleImageView.BitmapLoadTask(view, view.getContext(), view.bitmapDecoderFactory, view.uri, false); // bitmapDecoderFactory and uri are public
            view.execute(task);
        }
    }

    public void setGestureDetector(final Context context) {
        // Use the new external listener class directly.
        // The methods onSingleTapConfirmed and onDoubleTap will be moved next.
        view.detector = new GestureDetector(context, new ImageViewGestureListener(view, context));

        view.singleDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                view.performClick();
                return true;
            }
        });
    }

    /**
     * Called on first draw when the view has dimensions. Calculates the initial sample size and
     * starts async loading of the base layer image - the whole source subsampled as necessary.
     */
    public synchronized void initialiseBaseLayer(@NonNull Point maxTileDimensions) {
        view.debug("initialiseBaseLayer maxTileDimensions=%dx%d", maxTileDimensions.x, maxTileDimensions.y);

        view.satTemp = new SubsamplingScaleImageView.ScaleAndTranslate(0f, new PointF(0, 0)); // satTemp is public
        SubsamplingScaleImageViewDrawHelper.fitToBounds(view, true, view.satTemp);

        // Load double resolution - next level will be split into four tiles and at the center all
        // four are required, so don't bother with tiling until the next level 16 tiles are needed.
        view.fullImageSampleSize = view.calculateInSampleSize(view.satTemp.scale); // fullImageSampleSize and calculateInSampleSize are public

        if (view.fullImageSampleSize > 1) {
            view.fullImageSampleSize /= 2;
        }

        if (view.fullImageSampleSize == 1 && view.sRegion == null && SubsamplingScaleImageViewStateHelper.sWidth(view) < maxTileDimensions.x
                && SubsamplingScaleImageViewStateHelper.sHeight(view) < maxTileDimensions.y) { // sRegion is public
            // Whole image is required at native resolution, and is smaller than the canvas max
            // bitmap size. Use BitmapDecoder for better image support.
            view.decoder.recycle(); // decoder is public
            view.decoder = null;
            SubsamplingScaleImageView.BitmapLoadTask task = new SubsamplingScaleImageView.BitmapLoadTask(view, view.getContext(), view.bitmapDecoderFactory, view.uri, false); // bitmapDecoderFactory and uri are public
            view.execute(task);
        } else {
            view.initialiseTileMap(maxTileDimensions); // initialiseTileMap is public

            List<SubsamplingScaleImageView.Tile> baseGrid = view.tileMap.get(view.fullImageSampleSize); // tileMap and fullImageSampleSize are public

            for (SubsamplingScaleImageView.Tile baseTile : baseGrid) {
                SubsamplingScaleImageView.TileLoadTask task = new SubsamplingScaleImageView.TileLoadTask(view, view.decoder, baseTile); // TileLoadTask needs to be public
                view.execute(task);
            }

            view.refreshRequiredTiles(true); // refreshRequiredTiles is public
        }
    }
}