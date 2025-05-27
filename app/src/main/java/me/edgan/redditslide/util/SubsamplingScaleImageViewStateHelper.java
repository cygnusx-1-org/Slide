package me.edgan.redditslide.util;

import android.graphics.PointF;
import android.graphics.Rect;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// Needed for getState logic if moved here (kept in main class for now)

import me.edgan.redditslide.Views.SubsamplingScaleImageView;
import me.edgan.redditslide.Views.SubsamplingScaleImageView.ScaleAndTranslate; // Need access to inner class

// Import necessary classes used by the moved methods
import static me.edgan.redditslide.Views.SubsamplingScaleImageView.*; // For constants like ORIENTATION_USE_EXIF, SCALE_TYPE_*, PAN_LIMIT_*

/**
 * Helper class containing static methods extracted from SubsamplingScaleImageView
 * for managing state, coordinate transformations, and calculating derived properties.
 */
public class SubsamplingScaleImageViewStateHelper {

    /** Convert screen to source x coordinate. */
    public static float viewToSourceX(SubsamplingScaleImageView view, float vx) {
        if (view.vTranslate == null) {
            return Float.NaN;
        }
        return (vx - view.vTranslate.x) / view.scale;
    }

    /** Convert screen to source y coordinate. */
    public static float viewToSourceY(SubsamplingScaleImageView view, float vy) {
        if (view.vTranslate == null) {
            return Float.NaN;
        }
        return (vy - view.vTranslate.y) / view.scale;
    }

    /**
     * Converts a rectangle within the view to the corresponding rectangle from the source file,
     * taking into account the current scale, translation, orientation and clipped region. This can
     * be used to decode a bitmap from the source file.
     *
     * <p>This method will only work when the image has fully initialised, after {@link SubsamplingScaleImageView#isReady()}
     * returns true. It is not guaranteed to work with preloaded bitmaps.
     *
     * <p>The result is written to the fRect argument. Re-use a single instance for efficiency.
     *
     * @param vRect rectangle representing the view area to interpret.
     * @param fRect rectangle instance to which the result will be written. Re-use for efficiency.
     */
    public static void viewToFileRect(SubsamplingScaleImageView view, Rect vRect, Rect fRect) {
        if (view.vTranslate == null || !view.isReady()) { // Use isReady() getter
            return;
        }
        fRect.set((int) viewToSourceX(view, vRect.left), (int) viewToSourceY(view, vRect.top), (int) viewToSourceX(view, vRect.right), (int) viewToSourceY(view, vRect.bottom));
        fileSRect(view, fRect, fRect); // Call the helper version
        fRect.set(Math.max(0, fRect.left), Math.max(0, fRect.top), Math.min(view.getSWidth(), fRect.right), Math.min(view.getSHeight(), fRect.bottom)); // Use getters for sWidth/sHeight
        if (view.sRegion != null) { // Assume sRegion is accessible
            fRect.offset(view.sRegion.left, view.sRegion.top);
        }
    }

    /**
     * Find the area of the source file that is currently visible on screen, taking into account the
     * current scale, translation, orientation and clipped region. This is a convenience method; see
     * {@link #viewToFileRect(SubsamplingScaleImageView, Rect, Rect)}.
     *
     * @param fRect rectangle instance to which the result will be written. Re-use for efficiency.
     */
    public static void visibleFileRect(SubsamplingScaleImageView view, Rect fRect) {
        if (view.vTranslate == null || !view.isReady()) { // Use isReady() getter
            return;
        }
        fRect.set(0, 0, view.getWidth(), view.getHeight());
        viewToFileRect(view, fRect, fRect);
    }


    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vxy view X/Y coordinate.
     * @return a coordinate representing the corresponding source coordinate.
     */
    @Nullable
    public static PointF viewToSourceCoord(SubsamplingScaleImageView view, PointF vxy) {
        return viewToSourceCoord(view, vxy.x, vxy.y, new PointF());
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vx view X coordinate.
     * @param vy view Y coordinate.
     * @return a coordinate representing the corresponding source coordinate.
     */
    @Nullable
    public static PointF viewToSourceCoord(SubsamplingScaleImageView view, float vx, float vy) {
        return viewToSourceCoord(view, vx, vy, new PointF());
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vxy view coordinates to convert.
     * @param sTarget target object for result. The same instance is also returned.
     * @return source coordinates. This is the same instance passed to the sTarget param.
     */
    @Nullable
    public static PointF viewToSourceCoord(SubsamplingScaleImageView view, PointF vxy, @NonNull PointF sTarget) {
        return viewToSourceCoord(view, vxy.x, vxy.y, sTarget);
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vx view X coordinate.
     * @param vy view Y coordinate.
     * @param sTarget target object for result. The same instance is also returned.
     * @return source coordinates. This is the same instance passed to the sTarget param.
     */
    @Nullable
    public static PointF viewToSourceCoord(SubsamplingScaleImageView view, float vx, float vy, @NonNull PointF sTarget) {
        if (view.vTranslate == null) {
            return null;
        }
        sTarget.set(viewToSourceX(view, vx), viewToSourceY(view, vy));
        return sTarget;
    }

    /** Convert source to view x coordinate. */
    public static float sourceToViewX(SubsamplingScaleImageView view, float sx) {
        if (view.vTranslate == null) {
            return Float.NaN;
        }
        return (sx * view.scale) + view.vTranslate.x;
    }

    /** Convert source to view y coordinate. */
    public static float sourceToViewY(SubsamplingScaleImageView view, float sy) {
        if (view.vTranslate == null) {
            return Float.NaN;
        }
        return (sy * view.scale) + view.vTranslate.y;
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sxy source coordinates to convert.
     * @return view coordinates.
     */
    @Nullable
    public static PointF sourceToViewCoord(SubsamplingScaleImageView view, PointF sxy) {
        return sourceToViewCoord(view, sxy.x, sxy.y, new PointF());
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sx source X coordinate.
     * @param sy source Y coordinate.
     * @return view coordinates.
     */
    @Nullable
    public static PointF sourceToViewCoord(SubsamplingScaleImageView view, float sx, float sy) {
        return sourceToViewCoord(view, sx, sy, new PointF());
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sxy source coordinates to convert.
     * @param vTarget target object for result. The same instance is also returned.
     * @return view coordinates. This is the same instance passed to the vTarget param.
     */
    @SuppressWarnings("UnusedReturnValue")
    @Nullable
    public static PointF sourceToViewCoord(SubsamplingScaleImageView view, PointF sxy, @NonNull PointF vTarget) {
        return sourceToViewCoord(view, sxy.x, sxy.y, vTarget);
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sx source X coordinate.
     * @param sy source Y coordinate.
     * @param vTarget target object for result. The same instance is also returned.
     * @return view coordinates. This is the same instance passed to the vTarget param.
     */
    @Nullable
    public static PointF sourceToViewCoord(SubsamplingScaleImageView view, float sx, float sy, @NonNull PointF vTarget) {
        if (view.vTranslate == null) {
            return null;
        }
        vTarget.set(sourceToViewX(view, sx), sourceToViewY(view, sy));
        return vTarget;
    }

    /** Convert source rect to screen rect, integer values. */
    public static void sourceToViewRect(SubsamplingScaleImageView view, @NonNull Rect sRect, @NonNull Rect vTarget) {
        vTarget.set((int) sourceToViewX(view, sRect.left), (int) sourceToViewY(view, sRect.top), (int) sourceToViewX(view, sRect.right), (int) sourceToViewY(view, sRect.bottom));
    }

    /**
     * Get the translation required to place a given source coordinate at the center of the screen,
     * with the center adjusted for asymmetric padding. Accepts the desired scale as an argument, so
     * this is independent of current translate and scale. The result is fitted to bounds, putting
     * the image point as near to the screen center as permitted.
     */
    @NonNull
    public static PointF vTranslateForSCenter(SubsamplingScaleImageView view, float sCenterX, float sCenterY, float scale) {
        int vxCenter = view.getPaddingLeft() + (view.getWidth() - view.getPaddingRight() - view.getPaddingLeft()) / 2;
        int vyCenter = view.getPaddingTop() + (view.getHeight() - view.getPaddingBottom() - view.getPaddingTop()) / 2;

        // satTemp is volatile in the original class, used for temporary calculations.
        // Recreating it here for the calculation should be fine.
        ScaleAndTranslate satTemp = new ScaleAndTranslate(0, new PointF(0, 0));

        satTemp.scale = scale;
        satTemp.vTranslate.set(vxCenter - (sCenterX * scale), vyCenter - (sCenterY * scale));
        // Call the existing helper in the util package
        SubsamplingScaleImageViewDrawHelper.fitToBounds(view, true, satTemp);

        return satTemp.vTranslate;
    }

    /**
     * Given a requested source center and scale, calculate what the actual center will have to be
     * to keep the image in pan limits, keeping the requested center as near to the middle of the
     * screen as allowed.
     */
    @NonNull
    public static PointF limitedSCenter(SubsamplingScaleImageView view, float sCenterX, float sCenterY, float scale, @NonNull PointF sTarget) {
        PointF vTranslate = vTranslateForSCenter(view, sCenterX, sCenterY, scale); // Call helper version
        int vxCenter = view.getPaddingLeft() + (view.getWidth() - view.getPaddingRight() - view.getPaddingLeft()) / 2;
        int vyCenter = view.getPaddingTop() + (view.getHeight() - view.getPaddingBottom() - view.getPaddingTop()) / 2;
        float sx = (vxCenter - vTranslate.x) / scale;
        float sy = (vyCenter - vTranslate.y) / scale;
        sTarget.set(sx, sy);
        return sTarget;
    }

    /** Returns the minimum allowed scale. */
    public static float minScale(SubsamplingScaleImageView view) {
        int vPadding = view.getPaddingBottom() + view.getPaddingTop();
        int hPadding = view.getPaddingLeft() + view.getPaddingRight();
        // Access fields via getters if possible, or assume direct access for now
        int minimumScaleType = view.getMinimumScaleType();
        float minScaleField = view.getMinScaleField();

        if (minimumScaleType == SCALE_TYPE_CENTER_CROP || minimumScaleType == SCALE_TYPE_START) {
            // Use helper methods for sWidth/sHeight
            return Math.max((view.getWidth() - hPadding) / (float) sWidth(view), (view.getHeight() - vPadding) / (float) sHeight(view));
        } else if (minimumScaleType == SCALE_TYPE_CUSTOM && minScaleField > 0) {
            return minScaleField;
        } else { // Default: SCALE_TYPE_CENTER_INSIDE
            // Use helper methods for sWidth/sHeight
            return Math.min((view.getWidth() - hPadding) / (float) sWidth(view), (view.getHeight() - vPadding) / (float) sHeight(view));
        }
    }

     /** Adjust a requested scale to be within the allowed limits. */
    public static float limitedScale(SubsamplingScaleImageView view, float targetScale) {
        targetScale = Math.max(minScale(view), targetScale);
        targetScale = Math.min(view.getMaxScale(), targetScale);
        return targetScale;
    }

    /** Get source width taking rotation into account. */
    @SuppressWarnings("SuspiciousNameCombination")
    public static int sWidth(SubsamplingScaleImageView view) {
        int rotation = getRequiredRotation(view);
        if (rotation == 90 || rotation == 270) {
            return view.getSHeight();
        } else {
            return view.getSWidth();
        }
    }

    /** Get source height taking rotation into account. */
    @SuppressWarnings("SuspiciousNameCombination")
    public static int sHeight(SubsamplingScaleImageView view) {
        int rotation = getRequiredRotation(view);
        if (rotation == 90 || rotation == 270) {
            return view.getSWidth();
        } else {
            return view.getSHeight();
        }
    }

    /**
     * Converts source rectangle from tile, which treats the image file as if it were in the correct
     * orientation already, to the rectangle of the image that needs to be loaded.
     */
    @SuppressWarnings("SuspiciousNameCombination")
    public static void fileSRect(SubsamplingScaleImageView view, Rect sRect, Rect target) {
        int requiredRotation = getRequiredRotation(view);
        int sWidth = view.getSWidth();
        int sHeight = view.getSHeight();

        if (requiredRotation == 0) {
            target.set(sRect);
        } else if (requiredRotation == 90) {
            target.set(sRect.top, sHeight - sRect.right, sRect.bottom, sHeight - sRect.left);
        } else if (requiredRotation == 180) {
            target.set(
                    sWidth - sRect.right,
                    sHeight - sRect.bottom,
                    sWidth - sRect.left,
                    sHeight - sRect.top);
        } else { // 270
            target.set(sWidth - sRect.bottom, sRect.left, sWidth - sRect.top, sRect.right);
        }
    }

    /**
     * Determines the rotation to be applied to tiles, based on EXIF orientation or chosen setting.
     */
    public static int getRequiredRotation(SubsamplingScaleImageView view) {
        // Access fields via getters
        int orientation = view.getOrientation();
        if (orientation == ORIENTATION_USE_EXIF) {
            return view.sOrientation; // Assume sOrientation is accessible
        } else {
            return orientation;
        }
    }

    /**
     * Returns the source point at the center of the view.
     *
     * @return the source coordinates current at the center of the view.
     */
    @Nullable
    public static PointF getCenter(SubsamplingScaleImageView view) {
        int mX = view.getWidth() / 2;
        int mY = view.getHeight() / 2;
        return viewToSourceCoord(view, mX, mY); // Call helper version
    }
}