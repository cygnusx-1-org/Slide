package me.edgan.redditslide.util;

import android.graphics.PointF;
import android.view.MotionEvent;

import androidx.annotation.NonNull;

import java.util.Objects;

import me.edgan.redditslide.Views.SubsamplingScaleImageView;
import org.jspecify.annotations.NullMarked;

/**
 * Utility class for handling touch events for SubsamplingScaleImageView.
 */
@NullMarked
public class TouchEventUtil {

    // Moved from SubsamplingScaleImageView
    /** Pythagoras distance between two points. */
    private static float distance(float x0, float x1, float y0, float y1) {
        float x = x0 - x1;
        float y = y0 - y1;
        return (float) Math.sqrt(x * x + y * y);
    }

    // Copied and modified from SubsamplingScaleImageView
    @SuppressWarnings("deprecation")
    public static boolean handleTouchEventInternal(@NonNull SubsamplingScaleImageView view, @NonNull MotionEvent event) {
        int touchCount = event.getPointerCount();

        // Preconditions established by SubsamplingScaleImageView.onTouchEvent, the only caller: it
        // returns early unless vTranslate is set, and creates vTranslateStart and vCenterStart just
        // above the call. fitToBounds mutates vTranslate in place, so these locals stay current.
        final PointF vTranslate = Objects.requireNonNull(view.vTranslate);
        final PointF vTranslateStart = Objects.requireNonNull(view.vTranslateStart);
        final PointF vCenterStart = Objects.requireNonNull(view.vCenterStart);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_1_DOWN:
            case MotionEvent.ACTION_POINTER_2_DOWN:
                view.anim = null;
                view.requestDisallowInterceptTouchEvent(true);
                view.maxTouchCount = Math.max(view.maxTouchCount, touchCount);

                if (touchCount >= 2) {
                    if (view.zoomEnabled) {
                        // Start pinch to zoom. Calculate distance between touch points and center point of the pinch.
                        float distance = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                        view.scaleStart = view.scale;
                        view.vDistStart = distance;
                        vTranslateStart.set(vTranslate.x, vTranslate.y);
                        vCenterStart.set((event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2);
                    } else {
                        // Abort all gestures on second touch
                        view.maxTouchCount = 0;
                    }
                    // Cancel long click timer
                    view.handler.removeMessages(SubsamplingScaleImageView.MESSAGE_LONG_CLICK);
                } else if (!view.isQuickScaling) {
                    // Start one-finger pan
                    vTranslateStart.set(vTranslate.x, vTranslate.y);
                    vCenterStart.set(event.getX(), event.getY());

                    // Start long click timer
                    view.handler.sendEmptyMessageDelayed(SubsamplingScaleImageView.MESSAGE_LONG_CLICK, 600);
                }

                return true;
            case MotionEvent.ACTION_MOVE:
                boolean consumed = false;

                if (view.maxTouchCount > 0) {
                    if (touchCount >= 2) {
                        // Calculate new distance between touch points, to scale and pan relative to start values.
                        float vDistEnd = distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1));
                        float vCenterEndX = (event.getX(0) + event.getX(1)) / 2;
                        float vCenterEndY = (event.getY(0) + event.getY(1)) / 2;

                        if (view.zoomEnabled && (
                            distance(vCenterStart.x, vCenterEndX, vCenterStart.y, vCenterEndY) > 5
                            || Math.abs(vDistEnd - view.vDistStart) > 5
                            || view.isPanning)) {
                            view.isZooming = true;
                            view.isPanning = true;
                            consumed = true;

                            double previousScale = view.scale;
                            view.scale = Math.min(view.maxScale, (vDistEnd / view.vDistStart) * view.scaleStart);

                            if (view.scale <= SubsamplingScaleImageViewStateHelper.minScale(view)) {
                                // Minimum scale reached so don't pan. Adjust start settings so any expand will zoom in.
                                view.vDistStart = vDistEnd;
                                view.scaleStart = SubsamplingScaleImageViewStateHelper.minScale(view);
                                vCenterStart.set(vCenterEndX, vCenterEndY);
                                vTranslateStart.set(vTranslate);
                            } else if (view.panEnabled) {
                                // Translate to place the source image coordinate that was at the center of the pinch at the start at the center of the pinch now, to give simultaneous pan + zoom.
                                float vLeftStart = vCenterStart.x - vTranslateStart.x;
                                float vTopStart = vCenterStart.y - vTranslateStart.y;
                                float vLeftNow = vLeftStart * (view.scale / view.scaleStart);
                                float vTopNow = vTopStart * (view.scale / view.scaleStart);
                                vTranslate.x = vCenterEndX - vLeftNow;
                                vTranslate.y = vCenterEndY - vTopNow;
                                if ((previousScale * SubsamplingScaleImageViewStateHelper.sHeight(view) < view.getHeight()
                                                && view.scale * SubsamplingScaleImageViewStateHelper.sHeight(view) >= view.getHeight())
                                        || (previousScale * SubsamplingScaleImageViewStateHelper.sWidth(view) < view.getWidth()
                                                && view.scale * SubsamplingScaleImageViewStateHelper.sWidth(view) >= view.getWidth())) {
                                    view.fitToBounds(true);
                                    vCenterStart.set(vCenterEndX, vCenterEndY);
                                    vTranslateStart.set(vTranslate);
                                    view.scaleStart = view.scale;
                                    view.vDistStart = vDistEnd;
                                }
                            } else if (view.sRequestedCenter != null) {
                                // With a center specified from code, zoom around that point.
                                vTranslate.x = (view.getWidth() / 2.0f) - (view.scale * view.sRequestedCenter.x);
                                vTranslate.y = (view.getHeight() / 2.0f) - (view.scale * view.sRequestedCenter.y);
                            } else {
                                // With no requested center, scale around the image center.
                                vTranslate.x = (view.getWidth() / 2.0f) - (view.scale * (SubsamplingScaleImageViewStateHelper.sWidth(view) / 2.0f));
                                vTranslate.y = (view.getHeight() / 2.0f) - (view.scale * (SubsamplingScaleImageViewStateHelper.sHeight(view) / 2.0f));
                            }

                            view.fitToBounds(true);
                            view.refreshRequiredTiles(view.eagerLoadingEnabled);
                        }
                    } else if (view.isQuickScaling) {
                        // One finger zoom
                        // Stole Google's Magical Formula™ to make sure it feels the exact same
                        // Set together with isQuickScaling by ImageViewGestureListener.
                        final PointF quickScaleVStart = Objects.requireNonNull(view.quickScaleVStart);
                        final PointF quickScaleVLastPoint =
                                Objects.requireNonNull(view.quickScaleVLastPoint);
                        final PointF quickScaleSCenter = Objects.requireNonNull(view.quickScaleSCenter);
                        float dist = Math.abs(quickScaleVStart.y - event.getY()) * 2 + view.quickScaleThreshold;

                        if (view.quickScaleLastDistance == -1f) {
                            view.quickScaleLastDistance = dist;
                        }

                        boolean isUpwards = event.getY() > quickScaleVLastPoint.y;
                        quickScaleVLastPoint.set(0, event.getY());

                        float spanDiff = Math.abs(1 - (dist / view.quickScaleLastDistance)) * 0.5f;

                        if (spanDiff > 0.03f || view.quickScaleMoved) {
                            view.quickScaleMoved = true;

                            float multiplier = 1;

                            if (view.quickScaleLastDistance > 0) {
                                multiplier = isUpwards ? (1 + spanDiff) : (1 - spanDiff);
                            }

                            double previousScale = view.scale;
                            view.scale = Math.max(SubsamplingScaleImageViewStateHelper.minScale(view), Math.min(view.maxScale, view.scale * multiplier));

                            if (view.panEnabled) {
                                float vLeftStart = vCenterStart.x - vTranslateStart.x;
                                float vTopStart = vCenterStart.y - vTranslateStart.y;
                                float vLeftNow = vLeftStart * (view.scale / view.scaleStart);
                                float vTopNow = vTopStart * (view.scale / view.scaleStart);
                                vTranslate.x = vCenterStart.x - vLeftNow;
                                vTranslate.y = vCenterStart.y - vTopNow;

                                if ((previousScale * SubsamplingScaleImageViewStateHelper.sHeight(view) < view.getHeight()
                                                && view.scale * SubsamplingScaleImageViewStateHelper.sHeight(view) >= view.getHeight())
                                        || (previousScale * SubsamplingScaleImageViewStateHelper.sWidth(view) < view.getWidth()
                                                && view.scale * SubsamplingScaleImageViewStateHelper.sWidth(view) >= view.getWidth())) {
                                    view.fitToBounds(true);
                                    PointF quickScaleVCenter =
                                            SubsamplingScaleImageViewStateHelper.sourceToViewCoord(
                                                    view, quickScaleSCenter);
                                    if (quickScaleVCenter != null) {
                                        vCenterStart.set(quickScaleVCenter);
                                    }
                                    vTranslateStart.set(vTranslate);
                                    view.scaleStart = view.scale;
                                    dist = 0;
                                }
                            } else if (view.sRequestedCenter != null) {
                                // With a center specified from code, zoom around that point.
                                vTranslate.x = (view.getWidth() / 2.0f) - (view.scale * view.sRequestedCenter.x);
                                vTranslate.y = (view.getHeight() / 2.0f) - (view.scale * view.sRequestedCenter.y);
                            } else {
                                // With no requested center, scale around the image center.
                                vTranslate.x = (view.getWidth() / 2.0f) - (view.scale * (SubsamplingScaleImageViewStateHelper.sWidth(view) / 2.0f));
                                vTranslate.y = (view.getHeight() / 2.0f) - (view.scale * (SubsamplingScaleImageViewStateHelper.sHeight(view) / 2.0f));
                            }
                        }

                        view.quickScaleLastDistance = dist;

                        view.fitToBounds(true);
                        view.refreshRequiredTiles(view.eagerLoadingEnabled);

                        consumed = true;
                    } else if (!view.isZooming) {
                        // One finger pan - translate the image. We do this calculation even with pan disabled so click and long click behaviour is preserved.
                        float dx = Math.abs(event.getX() - vCenterStart.x);
                        float dy = Math.abs(event.getY() - vCenterStart.y);

                        // On the Samsung S6 long click event does not work, because the dx > 5 usually true
                        float offset = view.density * 5;

                        if (dx > offset || dy > offset || view.isPanning) {
                            consumed = true;
                            vTranslate.x = vTranslateStart.x + (event.getX() - vCenterStart.x);
                            vTranslate.y = vTranslateStart.y + (event.getY() - vCenterStart.y);

                            float lastX = vTranslate.x;
                            float lastY = vTranslate.y;
                            view.fitToBounds(true);
                            boolean atXEdge = lastX != vTranslate.x;
                            boolean atYEdge = lastY != vTranslate.y;
                            boolean edgeXSwipe = atXEdge && dx > dy && !view.isPanning;
                            boolean edgeYSwipe = atYEdge && dy > dx && !view.isPanning;
                            boolean yPan = lastY == vTranslate.y && dy > offset * 3;

                            if (!edgeXSwipe && !edgeYSwipe && (!atXEdge || !atYEdge || yPan || view.isPanning)) {
                                view.isPanning = true;
                            } else if (dx > offset || dy > offset) {
                                // Haven't panned the image, and we're at the left or right edge.
                                // Switch to page swipe.
                                view.maxTouchCount = 0;
                                view.handler.removeMessages(SubsamplingScaleImageView.MESSAGE_LONG_CLICK);
                                view.requestDisallowInterceptTouchEvent(false);
                            }

                            if (!view.panEnabled) {
                                vTranslate.x = vTranslateStart.x;
                                vTranslate.y = vTranslateStart.y;
                                view.requestDisallowInterceptTouchEvent(false);
                            }

                            view.refreshRequiredTiles(view.eagerLoadingEnabled);
                        }
                    }
                }

                if (consumed) {
                    view.handler.removeMessages(SubsamplingScaleImageView.MESSAGE_LONG_CLICK);
                    view.invalidate();
                    return true;
                }

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_POINTER_2_UP:
                view.handler.removeMessages(SubsamplingScaleImageView.MESSAGE_LONG_CLICK);

                if (view.isQuickScaling) {
                    view.isQuickScaling = false;
                    // Set together with isQuickScaling by ImageViewGestureListener.
                    if (!view.quickScaleMoved && view.quickScaleSCenter != null) {
                        view.doubleTapZoom(view.quickScaleSCenter, vCenterStart);
                    }
                }

                if (view.maxTouchCount > 0 && (view.isZooming || view.isPanning)) {
                    if (view.isZooming && touchCount == 2) {
                        // Convert from zoom to pan with remaining touch
                        view.isPanning = true;
                        vTranslateStart.set(vTranslate.x, vTranslate.y);
                        if (event.getActionIndex() == 1) {
                            vCenterStart.set(event.getX(0), event.getY(0));
                        } else {
                            vCenterStart.set(event.getX(1), event.getY(1));
                        }
                    }
                    if (touchCount < 3) {
                        // End zooming when only one touch point
                        view.isZooming = false;
                    }
                    if (touchCount < 2) {
                        // End panning when no touch points
                        view.isPanning = false;
                        view.maxTouchCount = 0;
                    }
                    // Trigger load of tiles now required
                    view.refreshRequiredTiles(true);
                    return true;
                }

                if (touchCount == 1) {
                    view.isZooming = false;
                    view.isPanning = false;
                    view.maxTouchCount = 0;
                }

                return true;
        }

        return false;
    }
}