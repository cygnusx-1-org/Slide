package me.edgan.redditslide.Views;

import me.edgan.redditslide.util.SubsamplingScaleImageViewStateHelper;

import android.graphics.PointF;
import android.util.Log;
import androidx.annotation.NonNull;

import me.edgan.redditslide.util.SubsamplingScaleImageViewDrawHelper;

import java.util.Arrays;
import java.util.List;

/**
 * Builder class used to set additional options for a scale animation. Create an instance using
 * {@link SubsamplingScaleImageView#animateScale(float)}, then set your options and call {@link #start()}.
 */
public final class AnimationBuilder {

    private static final String TAG = AnimationBuilder.class.getSimpleName();

    private final SubsamplingScaleImageView view;
    private final float targetScale;
    private final PointF targetSCenter;
    private final PointF vFocus;
    private long duration = 500;
    private int easing = SubsamplingScaleImageView.EASE_IN_OUT_QUAD;
    private int origin = SubsamplingScaleImageView.ORIGIN_ANIM;
    private boolean interruptible = true;
    private boolean panLimited = true;
    private SubsamplingScaleImageView.OnAnimationEventListener listener;

    // Reference static constants via class
    private static final List<Integer> VALID_EASING_STYLES =  Arrays.asList(SubsamplingScaleImageView.EASE_IN_OUT_QUAD, SubsamplingScaleImageView.EASE_OUT_QUAD);


    public AnimationBuilder(SubsamplingScaleImageView view, PointF sCenter) {
        this.view = view;
        this.targetScale = view.scale;
        this.targetSCenter = sCenter;
        this.vFocus = null;
    }

    AnimationBuilder(SubsamplingScaleImageView view, float scale) {
        this.view = view;
        this.targetScale = scale;
        this.targetSCenter = view.getCenter();
        this.vFocus = null;
    }

    AnimationBuilder(SubsamplingScaleImageView view, float scale, PointF sCenter) {
        this.view = view;
        this.targetScale = scale;
        this.targetSCenter = sCenter;
        this.vFocus = null;
    }

    AnimationBuilder(SubsamplingScaleImageView view, float scale, PointF sCenter, PointF vFocus) {
        this.view = view;
        this.targetScale = scale;
        this.targetSCenter = sCenter;
        this.vFocus = vFocus;
    }

    /**
     * Desired duration of the anim in milliseconds. Default is 500.
     *
     * @param duration duration in milliseconds.
     * @return this builder for method chaining.
     */
    @NonNull
    public AnimationBuilder withDuration(long duration) {
        this.duration = duration;

        return this;
    }

    /**
     * Whether the animation can be interrupted with a touch. Default is true.
     *
     * @param interruptible interruptible flag.
     * @return this builder for method chaining.
     */
    @NonNull
    public AnimationBuilder withInterruptible(boolean interruptible) {
        this.interruptible = interruptible;

        return this;
    }

    /**
     * Set the easing style. See static fields. {@link SubsamplingScaleImageView#EASE_IN_OUT_QUAD} is recommended, and
     * the default.
     *
     * @param easing easing style.
     * @return this builder for method chaining.
     */
    @NonNull
    public AnimationBuilder withEasing(int easing) {
        if (!VALID_EASING_STYLES.contains(easing)) {
            throw new IllegalArgumentException("Unknown easing type: " + easing);
        }

        this.easing = easing;

        return this;
    }

    /**
     * Add an animation event listener.
     *
     * @param listener The listener.
     * @return this builder for method chaining.
     */
    @NonNull
    public AnimationBuilder withOnAnimationEventListener(SubsamplingScaleImageView.OnAnimationEventListener listener) {
        this.listener = listener;

        return this;
    }

    /**
     * Only for internal use. When set to true, the animation proceeds towards the actual end
     * point - the nearest point to the center allowed by pan limits. When false, animation is
     * in the direction of the requested end point and is stopped when the limit for each axis
     * is reached. The latter behaviour is used for flings but nothing else.
     */
    @NonNull
    public AnimationBuilder withPanLimited(boolean panLimited) {
        this.panLimited = panLimited;

        return this;
    }

    /** Only for internal use. Indicates what caused the animation. */
    @NonNull
    public AnimationBuilder withOrigin(int origin) {
        this.origin = origin;

        return this;
    }

    /** Starts the animation. */
    public void start() {
        if (view.anim != null && view.anim.listener != null) {
            try {
                view.anim.listener.onInterruptedByNewAnim();
            } catch (Exception e) {
                Log.w(TAG, "Error thrown by animation listener", e);
            }
        }

        int vxCenter = view.getPaddingLeft() + (view.getWidth() - view.getPaddingRight() - view.getPaddingLeft()) / 2;
        int vyCenter = view.getPaddingTop() + (view.getHeight() - view.getPaddingBottom() - view.getPaddingTop()) / 2;
        float targetScale = SubsamplingScaleImageViewStateHelper.limitedScale(view, this.targetScale);
        PointF targetSCenter = panLimited ? SubsamplingScaleImageViewStateHelper.limitedSCenter(view, this.targetSCenter.x, this.targetSCenter.y, targetScale, new PointF()): this.targetSCenter;
        view.anim = new SubsamplingScaleImageView.Anim();
        view.anim.scaleStart = view.scale;
        view.anim.scaleEnd = targetScale;
        view.anim.time = System.currentTimeMillis();
        view.anim.sCenterEndRequested = targetSCenter;
        view.anim.sCenterStart = view.getCenter();
        view.anim.sCenterEnd = targetSCenter;
        view.anim.vFocusStart = SubsamplingScaleImageViewStateHelper.sourceToViewCoord(view, targetSCenter);
        view.anim.vFocusEnd = new PointF(vxCenter, vyCenter);
        view.anim.duration = duration;
        view.anim.interruptible = interruptible;
        view.anim.easing = easing;
        view.anim.origin = origin;
        view.anim.time = System.currentTimeMillis();
        view.anim.listener = listener;

        if (vFocus != null) {
            // Calculate where translation will be at the end of the anim
            float vTranslateXEnd = vFocus.x - (targetScale * view.anim.sCenterStart.x);
            float vTranslateYEnd = vFocus.y - (targetScale * view.anim.sCenterStart.y);
            SubsamplingScaleImageView.ScaleAndTranslate satEnd = new SubsamplingScaleImageView.ScaleAndTranslate(targetScale, new PointF(vTranslateXEnd, vTranslateYEnd));
            // Fit the end translation into bounds
            SubsamplingScaleImageViewDrawHelper.fitToBounds(view, true, satEnd);
            // Adjust the position of the focus point at end so image will be in bounds
            view.anim.vFocusEnd = new PointF(vFocus.x + (satEnd.vTranslate.x - vTranslateXEnd), vFocus.y + (satEnd.vTranslate.y - vTranslateYEnd));
        }

        view.invalidate();
    }
}