package me.edgan.redditslide.util;

import android.content.Context;
import android.graphics.PointF;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import java.util.Objects;
import me.edgan.redditslide.Views.AnimationBuilder;
import me.edgan.redditslide.Views.SubsamplingScaleImageView;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ImageViewGestureListener extends GestureDetector.SimpleOnGestureListener {

    private static final String TAG = ImageViewGestureListener.class.getSimpleName();

    private final SubsamplingScaleImageView view;
    private final Context context;

    public ImageViewGestureListener(SubsamplingScaleImageView view, Context context) {
        this.view = view;
        this.context = context;
    }

    @Override
    public boolean onFling(
            @Nullable MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (view.panEnabled
                && view.readySent && view.vTranslate != null && e1 != null && e2 != null
                && (Math.abs(e1.getX() - e2.getX()) > 50 || Math.abs(e1.getY() - e2.getY()) > 50)
                && (Math.abs(velocityX) > 500 || Math.abs(velocityY) > 500)
                && !view.isZooming) {
            PointF vTranslateEnd = new PointF(view.vTranslate.x + (velocityX * 0.25f), view.vTranslate.y + (velocityY * 0.25f));
            float sCenterXEnd = ((view.getWidth() / 2.0f) - vTranslateEnd.x) / view.scale;
            float sCenterYEnd = ((view.getHeight() / 2.0f) - vTranslateEnd.y) / view.scale;
            new AnimationBuilder(view, new PointF(sCenterXEnd, sCenterYEnd))
                .withEasing(SubsamplingScaleImageView.EASE_OUT_QUAD)
                .withPanLimited(false)
                .withOrigin(SubsamplingScaleImageView.ORIGIN_FLING)
                .start();

            return true;
        }

        return super.onFling(e1, e2, velocityX, velocityY);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        view.performClick(); // Call performClick on the view instance

        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        final PointF vTranslate = view.vTranslate;
        if (view.zoomEnabled && view.readySent && vTranslate != null) {
            // Hacky solution for #15 - after a double tap the GestureDetector gets in a state where the next fling is ignored, so here we replace it with a new one.
            // Use the public method we added to the view.
            view.loader.setGestureDetector(context);

            if (view.quickScaleEnabled) {
                // Store quick scale params. This will become either a double tap zoom or a quick scale depending on whether the user swipes.
                view.vCenterStart = new PointF(e.getX(), e.getY());
                view.vTranslateStart = new PointF(vTranslate.x, vTranslate.y);
                view.scaleStart = view.scale;
                view.isQuickScaling = true;
                view.isZooming = true;
                view.quickScaleLastDistance = -1F;
                // viewToSourceCoord answers null only when vTranslate is null, which the guard
                // at the top of this method rules out.
                final PointF quickScaleSCenter =
                        Objects.requireNonNull(
                                SubsamplingScaleImageViewStateHelper.viewToSourceCoord(
                                        view, view.vCenterStart));
                view.quickScaleSCenter = quickScaleSCenter;
                view.quickScaleVStart = new PointF(e.getX(), e.getY());
                view.quickScaleVLastPoint = new PointF(quickScaleSCenter.x, quickScaleSCenter.y);
                view.quickScaleMoved = false;

                // We need to get events in onTouchEvent after this.
                return false;
            } else {
                // Start double tap zoom animation.
                view.doubleTapZoom(
                        Objects.requireNonNull(
                                SubsamplingScaleImageViewStateHelper.viewToSourceCoord(
                                        view, new PointF(e.getX(), e.getY()))),
                        new PointF(e.getX(), e.getY()));

                return true;
            }
        }

        // Use onDoubleTap here, not onDoubleTapEvent
        return super.onDoubleTap(e);
    }
}