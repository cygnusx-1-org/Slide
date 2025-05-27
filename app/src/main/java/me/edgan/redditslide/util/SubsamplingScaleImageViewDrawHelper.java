package me.edgan.redditslide.util;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.edgan.redditslide.Views.SubsamplingScaleImageView;

/**
 * Helper class containing drawing logic extracted from SubsamplingScaleImageView.
 */
public class SubsamplingScaleImageViewDrawHelper {

    private static final String TAG = SubsamplingScaleImageViewDrawHelper.class.getSimpleName();

    public static void drawContent(SubsamplingScaleImageView view, Canvas canvas) {
        view.createPaints();

        // If image or view dimensions are not known yet, abort.
        if (view.sWidth == 0 || view.sHeight == 0 || view.getWidth() == 0 || view.getHeight() == 0) {
            return;
        }

        // When using tiles, on first render with no tile map ready, initialise it and kick off async base image loading.
        if (view.tileMap == null && view.decoder != null) {
            view.loader.initialiseBaseLayer(view.getMaxBitmapDimensions(canvas));
        }

        // If image has been loaded or supplied as a bitmap, onDraw may be the first time the view
        // has dimensions and therefore the first opportunity to set scale and translate. If this call
        // returns false there is nothing to be drawn so return immediately.
        if (!view.checkReady()) {
            return;
        }

        // Set scale and translate before draw.
        view.preDraw();

        // If animating scale, calculate current scale and center with easing equations
        if (view.anim != null && view.anim.vFocusStart != null) {
            // Store current values so we can send an event if they change
            float scaleBefore = view.scale;
            if (view.vTranslateBefore == null) {
                view.vTranslateBefore = new PointF(0, 0);
            }
            view.vTranslateBefore.set(view.vTranslate);

            long scaleElapsed = System.currentTimeMillis() - view.anim.time;
            boolean finished = scaleElapsed > view.anim.duration;
            scaleElapsed = Math.min(scaleElapsed, view.anim.duration);
            view.scale = view.ease(view.anim.easing, scaleElapsed, view.anim.scaleStart, view.anim.scaleEnd - view.anim.scaleStart, view.anim.duration);

            // Apply required animation to the focal point
            float vFocusNowX = view.ease(view.anim.easing, scaleElapsed, view.anim.vFocusStart.x, view.anim.vFocusEnd.x - view.anim.vFocusStart.x, view.anim.duration);
            float vFocusNowY = view.ease(view.anim.easing, scaleElapsed, view.anim.vFocusStart.y, view.anim.vFocusEnd.y - view.anim.vFocusStart.y, view.anim.duration);

            // Find out where the focal point is at this scale and adjust its position to follow the animation path
            view.vTranslate.x -= SubsamplingScaleImageViewStateHelper.sourceToViewX(view, view.anim.sCenterEnd.x) - vFocusNowX;
            view.vTranslate.y -= SubsamplingScaleImageViewStateHelper.sourceToViewY(view, view.anim.sCenterEnd.y) - vFocusNowY;

            // For translate anims, showing the image non-centered is never allowed, for scaling anims it is during the animation.
            view.fitToBounds(finished || (view.anim.scaleStart == view.anim.scaleEnd));
            view.sendStateChanged(scaleBefore, view.vTranslateBefore, view.anim.origin);
            view.refreshRequiredTiles(finished);

            if (finished) {
                if (view.anim.listener != null) {
                    try {
                        view.anim.listener.onComplete();
                    } catch (Exception e) {
                        Log.w(TAG, "Error thrown by animation listener", e);
                    }
                }
                view.anim = null;
            }

            view.invalidate();
        }

        if (view.tileMap != null && view.isBaseLayerReady()) {
            // Optimum sample size for current scale
            int sampleSize = Math.min(view.fullImageSampleSize, view.calculateInSampleSize(view.scale));

            // First check for missing tiles - if there are any we need the base layer underneath to
            // avoid gaps
            boolean hasMissingTiles = false;

            for (Map.Entry<Integer, List<SubsamplingScaleImageView.Tile>> tileMapEntry : view.tileMap.entrySet()) {
                if (tileMapEntry.getKey() == sampleSize) {
                    for (SubsamplingScaleImageView.Tile tile : tileMapEntry.getValue()) {
                        if (tile.visible && (tile.loading || tile.bitmap == null)) {
                            hasMissingTiles = true;
                            break;
                        }
                    }
                }
            }

            // Render all loaded tiles. LinkedHashMap used for bottom up rendering - lower res tiles
            // underneath.
            for (Map.Entry<Integer, List<SubsamplingScaleImageView.Tile>> tileMapEntry : view.tileMap.entrySet()) {
                if (tileMapEntry.getKey() == sampleSize || hasMissingTiles) {
                    for (SubsamplingScaleImageView.Tile tile : tileMapEntry.getValue()) {
                        SubsamplingScaleImageViewStateHelper.sourceToViewRect(view, tile.sRect, tile.vRect);
                        if (!tile.loading && tile.bitmap != null) {
                            if (view.tileBgPaint != null) {
                                canvas.drawRect(tile.vRect, view.tileBgPaint);
                            }

                            if (view.matrix == null) {
                                view.matrix = new Matrix();
                            }

                            view.matrix.reset();
                            setMatrixArray(
                                view.srcArray,
                                0,
                                0,
                                tile.bitmap.getWidth(),
                                0,
                                tile.bitmap.getWidth(),
                                tile.bitmap.getHeight(),
                                0,
                                tile.bitmap.getHeight());

                            if (SubsamplingScaleImageViewStateHelper.getRequiredRotation(view) == SubsamplingScaleImageView.ORIENTATION_0) {
                                setMatrixArray(
                                    view.dstArray,
                                    tile.vRect.left,
                                    tile.vRect.top,
                                    tile.vRect.right,
                                    tile.vRect.top,
                                    tile.vRect.right,
                                    tile.vRect.bottom,
                                    tile.vRect.left,
                                    tile.vRect.bottom);
                            } else if (SubsamplingScaleImageViewStateHelper.getRequiredRotation(view) == SubsamplingScaleImageView.ORIENTATION_90) {
                                setMatrixArray(
                                    view.dstArray,
                                    tile.vRect.right,
                                    tile.vRect.top,
                                    tile.vRect.right,
                                    tile.vRect.bottom,
                                    tile.vRect.left,
                                    tile.vRect.bottom,
                                    tile.vRect.left,
                                    tile.vRect.top);
                            } else if (SubsamplingScaleImageViewStateHelper.getRequiredRotation(view) == SubsamplingScaleImageView.ORIENTATION_180) {
                                setMatrixArray(
                                    view.dstArray,
                                    tile.vRect.right,
                                    tile.vRect.bottom,
                                    tile.vRect.left,
                                    tile.vRect.bottom,
                                    tile.vRect.left,
                                    tile.vRect.top,
                                    tile.vRect.right,
                                    tile.vRect.top);
                            } else if (SubsamplingScaleImageViewStateHelper.getRequiredRotation(view) == SubsamplingScaleImageView.ORIENTATION_270) {
                                setMatrixArray(
                                    view.dstArray,
                                    tile.vRect.left,
                                    tile.vRect.bottom,
                                    tile.vRect.left,
                                    tile.vRect.top,
                                    tile.vRect.right,
                                    tile.vRect.top,
                                    tile.vRect.right,
                                    tile.vRect.bottom);
                            }

                            view.matrix.setPolyToPoly(view.srcArray, 0, view.dstArray, 0, 4);
                            canvas.drawBitmap(tile.bitmap, view.matrix, view.bitmapPaint);

                            if (view.debug) {
                                canvas.drawRect(tile.vRect, view.debugLinePaint);
                            }
                        } else if (tile.loading && view.debug) {
                            canvas.drawText(
                                    "LOADING",
                                    tile.vRect.left + px(view, 5),
                                    tile.vRect.top + px(view, 35),
                                    view.debugTextPaint);
                        }

                        if (tile.visible && view.debug) {
                            canvas.drawText(
                                "ISS "
                                    + tile.sampleSize
                                    + " RECT "
                                    + tile.sRect.top
                                    + ","
                                    + tile.sRect.left
                                    + ","
                                    + tile.sRect.bottom
                                    + ","
                                    + tile.sRect.right,
                                tile.vRect.left + px(view, 5),
                                tile.vRect.top + px(view, 15),
                                view.debugTextPaint);
                        }
                    }
                }
            }
        } else if (view.bitmap != null && !view.bitmap.isRecycled()) {

            float xScale = view.scale, yScale = view.scale;

            if (view.bitmapIsPreview) {
                xScale = view.scale * ((float) view.sWidth / view.bitmap.getWidth());
                yScale = view.scale * ((float) view.sHeight / view.bitmap.getHeight());
            }

            if (view.matrix == null) {
                view.matrix = new Matrix();
            }

            view.matrix.reset();
            view.matrix.postScale(xScale, yScale);
            view.matrix.postRotate(SubsamplingScaleImageViewStateHelper.getRequiredRotation(view));
            view.matrix.postTranslate(view.vTranslate.x, view.vTranslate.y);

            if (SubsamplingScaleImageViewStateHelper.getRequiredRotation(view) == SubsamplingScaleImageView.ORIENTATION_180) {
                view.matrix.postTranslate(view.scale * view.sWidth, view.scale * view.sHeight);
            } else if (SubsamplingScaleImageViewStateHelper.getRequiredRotation(view) == SubsamplingScaleImageView.ORIENTATION_90) {
                view.matrix.postTranslate(view.scale * view.sHeight, 0);
            } else if (SubsamplingScaleImageViewStateHelper.getRequiredRotation(view) == SubsamplingScaleImageView.ORIENTATION_270) {
                view.matrix.postTranslate(0, view.scale * view.sWidth);
            }

            if (view.tileBgPaint != null) {
                if (view.sRect == null) {
                    view.sRect = new RectF();
                }
                view.sRect.set(
                    0f,
                    0f,
                    view.bitmapIsPreview ? view.bitmap.getWidth() : view.sWidth,
                    view.bitmapIsPreview ? view.bitmap.getHeight() : view.sHeight);
                view.matrix.mapRect(view.sRect);
                canvas.drawRect(view.sRect, view.tileBgPaint);
            }
            canvas.drawBitmap(view.bitmap, view.matrix, view.bitmapPaint);
        }

        if (view.debug) {
            canvas.drawText(
                "Scale: "
                    + String.format(Locale.ENGLISH, "%.2f", view.scale)
                    + " ("
                    + String.format(Locale.ENGLISH, "%.2f", SubsamplingScaleImageViewStateHelper.minScale(view))
                    + " - "
                    + String.format(Locale.ENGLISH, "%.2f", view.maxScale)
                    + ")",
                px(view, 5),
                px(view, 15),
                view.debugTextPaint);
            canvas.drawText(
                "Translate: "
                    + String.format(Locale.ENGLISH, "%.2f", view.vTranslate.x)
                    + ":"
                    + String.format(Locale.ENGLISH, "%.2f", view.vTranslate.y),
                px(view, 5),
                px(view, 30),
                view.debugTextPaint);
            PointF center = view.getCenter();
            // noinspection ConstantConditions
            canvas.drawText(
                "Source center: "
                    + String.format(Locale.ENGLISH, "%.2f", center.x)
                    + ":"
                    + String.format(Locale.ENGLISH, "%.2f", center.y),
                px(view, 5),
                px(view, 45),
                view.debugTextPaint);
            if (view.anim != null) {
                PointF vCenterStart = SubsamplingScaleImageViewStateHelper.sourceToViewCoord(view, view.anim.sCenterStart);
                PointF vCenterEndRequested = SubsamplingScaleImageViewStateHelper.sourceToViewCoord(view, view.anim.sCenterEndRequested);
                PointF vCenterEnd = SubsamplingScaleImageViewStateHelper.sourceToViewCoord(view, view.anim.sCenterEnd);
                // noinspection ConstantConditions
                canvas.drawCircle(vCenterStart.x, vCenterStart.y, px(view, 10), view.debugLinePaint);
                view.debugLinePaint.setColor(Color.RED);
                // noinspection ConstantConditions
                canvas.drawCircle(
                        vCenterEndRequested.x, vCenterEndRequested.y, px(view, 20), view.debugLinePaint);
                view.debugLinePaint.setColor(Color.BLUE);
                // noinspection ConstantConditions
                canvas.drawCircle(vCenterEnd.x, vCenterEnd.y, px(view, 25), view.debugLinePaint);
                view.debugLinePaint.setColor(Color.CYAN);
                canvas.drawCircle(view.getWidth() / 2.0f, view.getHeight() / 2.0f, px(view, 30), view.debugLinePaint);
            }
            if (view.vCenterStart != null) {
                view.debugLinePaint.setColor(Color.RED);
                canvas.drawCircle(view.vCenterStart.x, view.vCenterStart.y, px(view, 20), view.debugLinePaint);
            }
            if (view.quickScaleSCenter != null) {
                view.debugLinePaint.setColor(Color.BLUE);
                canvas.drawCircle(SubsamplingScaleImageViewStateHelper.sourceToViewX(view, view.quickScaleSCenter.x), SubsamplingScaleImageViewStateHelper.sourceToViewY(view, view.quickScaleSCenter.y), px(view, 35), view.debugLinePaint);
            }
            if (view.quickScaleVStart != null && view.isQuickScaling) {
                view.debugLinePaint.setColor(Color.CYAN);
                canvas.drawCircle(view.quickScaleVStart.x, view.quickScaleVStart.y, px(view, 30), view.debugLinePaint);
            }
            view.debugLinePaint.setColor(Color.MAGENTA);
        }
    }
/** For debug overlays. Scale pixel value according to screen density. */
public static int px(SubsamplingScaleImageView view, int px) {
    return (int) (view.density * px);
}

/** Helper method for setting the values of a tile matrix array. */
static void setMatrixArray(
        float[] array,
        float f0,
        float f1,
        float f2,
        float f3,
        float f4,
        float f5,
        float f6,
        float f7) {
    array[0] = f0;
    array[1] = f1;
    array[2] = f2;
    array[3] = f3;
    array[4] = f4;
    array[5] = f5;
    array[6] = f6;
    array[7] = f7;
}
    // Other helper methods will be added here.

    /**
     * Adjusts hypothetical future scale and translate values to keep scale within the allowed range
     * and the image on screen. Minimum scale is set so one dimension fills the view and the image
     * is centered on the other dimension. Used to calculate what the target of an animation should
     * be.
     *
     * @param view The SubsamplingScaleImageView instance.
     * @param center Whether the image should be centered in the dimension it's too small to fill.
     *     While animating this can be false to avoid changes in direction as bounds are reached.
     * @param sat The scale we want and the translation we're aiming for. The values are adjusted to
     *     be valid.
     */
    public static void fitToBounds(SubsamplingScaleImageView view, boolean center, SubsamplingScaleImageView.ScaleAndTranslate sat) {
        if (view.panLimit == SubsamplingScaleImageView.PAN_LIMIT_OUTSIDE && view.isReady()) {
            center = false;
        }

        PointF vTranslate = sat.vTranslate;
        // Use view's limitedScale method - ensure it's accessible
        float scale = SubsamplingScaleImageViewStateHelper.limitedScale(view, sat.scale);
        float scaleWidth = scale * SubsamplingScaleImageViewStateHelper.sWidth(view);
        float scaleHeight = scale * SubsamplingScaleImageViewStateHelper.sHeight(view);

        if (view.panLimit == SubsamplingScaleImageView.PAN_LIMIT_CENTER && view.isReady()) {
            vTranslate.x = Math.max(vTranslate.x, view.getWidth() / 2.0f - scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, view.getHeight() / 2.0f - scaleHeight);
        } else if (center) {
            vTranslate.x = Math.max(vTranslate.x, view.getWidth() - scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, view.getHeight() - scaleHeight);
        } else {
            vTranslate.x = Math.max(vTranslate.x, -scaleWidth);
            vTranslate.y = Math.max(vTranslate.y, -scaleHeight);
        }

        // Asymmetric padding adjustments
        float xPaddingRatio = view.getPaddingLeft() > 0 || view.getPaddingRight() > 0 ? view.getPaddingLeft() / (float) (view.getPaddingLeft() + view.getPaddingRight()) : 0.5f;
        float yPaddingRatio = view.getPaddingTop() > 0 || view.getPaddingBottom() > 0 ? view.getPaddingTop() / (float) (view.getPaddingTop() + view.getPaddingBottom()) : 0.5f;

        float maxTx;
        float maxTy;

        if (view.panLimit == SubsamplingScaleImageView.PAN_LIMIT_CENTER && view.isReady()) {
            maxTx = Math.max(0, view.getWidth() / 2);
            maxTy = Math.max(0, view.getHeight() / 2);
        } else if (center) {
            maxTx = Math.max(0, (view.getWidth() - scaleWidth) * xPaddingRatio);
            maxTy = Math.max(0, (view.getHeight() - scaleHeight) * yPaddingRatio);
        } else {
            maxTx = Math.max(0, view.getWidth());
            maxTy = Math.max(0, view.getHeight());
        }

        vTranslate.x = Math.min(vTranslate.x, maxTx);
        vTranslate.y = Math.min(vTranslate.y, maxTy);

        sat.scale = scale;
    }
}