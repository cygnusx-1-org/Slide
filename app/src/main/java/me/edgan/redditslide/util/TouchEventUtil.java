package me.edgan.redditslide.util;

import android.view.MotionEvent;
import androidx.annotation.NonNull;

import me.edgan.redditslide.Views.SubsamplingScaleImageView;

/**
 * Utility class for handling touch events for SubsamplingScaleImageView.
 */
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
                        view.vTranslateStart.set(view.vTranslate.x, view.vTranslate.y);
                        view.vCenterStart.set((event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2);
                    } else {
                        // Abort all gestures on second touch
                        view.maxTouchCount = 0;
                    }
                    // Cancel long click timer
                    view.handler.removeMessages(SubsamplingScaleImageView.MESSAGE_LONG_CLICK);
                } else if (!view.isQuickScaling) {
                    // Start one-finger pan
                    view.vTranslateStart.set(view.vTranslate.x, view.vTranslate.y);
                    view.vCenterStart.set(event.getX(), event.getY());

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
                            distance(view.vCenterStart.x, vCenterEndX, view.vCenterStart.y, vCenterEndY) > 5
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
                                view.vCenterStart.set(vCenterEndX, vCenterEndY);
                                view.vTranslateStart.set(view.vTranslate);
                            } else if (view.panEnabled) {
                                // Translate to place the source image coordinate that was at the center of the pinch at the start at the center of the pinch now, to give simultaneous pan + zoom.
                                float vLeftStart = view.vCenterStart.x - view.vTranslateStart.x;
                                float vTopStart = view.vCenterStart.y - view.vTranslateStart.y;
                                float vLeftNow = vLeftStart * (view.scale / view.scaleStart);
                                float vTopNow = vTopStart * (view.scale / view.scaleStart);
                                view.vTranslate.x = vCenterEndX - vLeftNow;
                                view.vTranslate.y = vCenterEndY - vTopNow;
                                if ((previousScale * SubsamplingScaleImageViewStateHelper.sHeight(view) < view.getHeight()
                                                && view.scale * SubsamplingScaleImageViewStateHelper.sHeight(view) >= view.getHeight())
                                        || (previousScale * SubsamplingScaleImageViewStateHelper.sWidth(view) < view.getWidth()
                                                && view.scale * SubsamplingScaleImageViewStateHelper.sWidth(view) >= view.getWidth())) {
                                    view.fitToBounds(true);
                                    view.vCenterStart.set(vCenterEndX, vCenterEndY);
                                    view.vTranslateStart.set(view.vTranslate);
                                    view.scaleStart = view.scale;
                                    view.vDistStart = vDistEnd;
                                }
                            } else if (view.sRequestedCenter != null) {
                                // With a center specified from code, zoom around that point.
                                view.vTranslate.x = (view.getWidth() / 2.0f) - (view.scale * view.sRequestedCenter.x);
                                view.vTranslate.y = (view.getHeight() / 2.0f) - (view.scale * view.sRequestedCenter.y);
                            } else {
                                // With no requested center, scale around the image center.
                                view.vTranslate.x = (view.getWidth() / 2.0f) - (view.scale * (SubsamplingScaleImageViewStateHelper.sWidth(view) / 2.0f));
                                view.vTranslate.y = (view.getHeight() / 2.0f) - (view.scale * (SubsamplingScaleImageViewStateHelper.sHeight(view) / 2.0f));
                            }

                            view.fitToBounds(true);
                            view.refreshRequiredTiles(view.eagerLoadingEnabled);
                        }
                    } else if (view.isQuickScaling) {
                        // One finger zoom
                        // Stole Google's Magical Formula™ to make sure it feels the exact same
                        float dist = Math.abs(view.quickScaleVStart.y - event.getY()) * 2 + view.quickScaleThreshold;

                        if (view.quickScaleLastDistance == -1f) {
                            view.quickScaleLastDistance = dist;
                        }

                        boolean isUpwards = event.getY() > view.quickScaleVLastPoint.y;
                        view.quickScaleVLastPoint.set(0, event.getY());

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
                                float vLeftStart = view.vCenterStart.x - view.vTranslateStart.x;
                                float vTopStart = view.vCenterStart.y - view.vTranslateStart.y;
                                float vLeftNow = vLeftStart * (view.scale / view.scaleStart);
                                float vTopNow = vTopStart * (view.scale / view.scaleStart);
                                view.vTranslate.x = view.vCenterStart.x - vLeftNow;
                                view.vTranslate.y = view.vCenterStart.y - vTopNow;

                                if ((previousScale * SubsamplingScaleImageViewStateHelper.sHeight(view) < view.getHeight()
                                                && view.scale * SubsamplingScaleImageViewStateHelper.sHeight(view) >= view.getHeight())
                                        || (previousScale * SubsamplingScaleImageViewStateHelper.sWidth(view) < view.getWidth()
                                                && view.scale * SubsamplingScaleImageViewStateHelper.sWidth(view) >= view.getWidth())) {
                                    view.fitToBounds(true);
                                    view.vCenterStart.set(SubsamplingScaleImageViewStateHelper.sourceToViewCoord(view, view.quickScaleSCenter));
                                    view.vTranslateStart.set(view.vTranslate);
                                    view.scaleStart = view.scale;
                                    dist = 0;
                                }
                            } else if (view.sRequestedCenter != null) {
                                // With a center specified from code, zoom around that point.
                                view.vTranslate.x = (view.getWidth() / 2.0f) - (view.scale * view.sRequestedCenter.x);
                                view.vTranslate.y = (view.getHeight() / 2.0f) - (view.scale * view.sRequestedCenter.y);
                            } else {
                                // With no requested center, scale around the image center.
                                view.vTranslate.x = (view.getWidth() / 2.0f) - (view.scale * (SubsamplingScaleImageViewStateHelper.sWidth(view) / 2.0f));
                                view.vTranslate.y = (view.getHeight() / 2.0f) - (view.scale * (SubsamplingScaleImageViewStateHelper.sHeight(view) / 2.0f));
                            }
                        }

                        view.quickScaleLastDistance = dist;

                        view.fitToBounds(true);
                        view.refreshRequiredTiles(view.eagerLoadingEnabled);

                        consumed = true;
                    } else if (!view.isZooming) {
                        // One finger pan - translate the image. We do this calculation even with pan disabled so click and long click behaviour is preserved.
                        float dx = Math.abs(event.getX() - view.vCenterStart.x);
                        float dy = Math.abs(event.getY() - view.vCenterStart.y);

                        // On the Samsung S6 long click event does not work, because the dx > 5 usually true
                        float offset = view.density * 5;

                        if (dx > offset || dy > offset || view.isPanning) {
                            consumed = true;
                            view.vTranslate.x = view.vTranslateStart.x + (event.getX() - view.vCenterStart.x);
                            view.vTranslate.y = view.vTranslateStart.y + (event.getY() - view.vCenterStart.y);

                            float lastX = view.vTranslate.x;
                            float lastY = view.vTranslate.y;
                            view.fitToBounds(true);
                            boolean atXEdge = lastX != view.vTranslate.x;
                            boolean atYEdge = lastY != view.vTranslate.y;
                            boolean edgeXSwipe = atXEdge && dx > dy && !view.isPanning;
                            boolean edgeYSwipe = atYEdge && dy > dx && !view.isPanning;
                            boolean yPan = lastY == view.vTranslate.y && dy > offset * 3;

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
                                view.vTranslate.x = view.vTranslateStart.x;
                                view.vTranslate.y = view.vTranslateStart.y;
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
                    if (!view.quickScaleMoved) {
                        view.doubleTapZoom(view.quickScaleSCenter, view.vCenterStart);
                    }
                }

                if (view.maxTouchCount > 0 && (view.isZooming || view.isPanning)) {
                    if (view.isZooming && touchCount == 2) {
                        // Convert from zoom to pan with remaining touch
                        view.isPanning = true;
                        view.vTranslateStart.set(view.vTranslate.x, view.vTranslate.y);
                        if (event.getActionIndex() == 1) {
                            view.vCenterStart.set(event.getX(0), event.getY(0));
                        } else {
                            view.vCenterStart.set(event.getX(1), event.getY(1));
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