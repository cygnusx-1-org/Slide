package com.sothree.slidinguppanel;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import java.util.ArrayList;
import java.util.List;

import me.edgan.redditslide.R;

/**
 * Drop-in replacement for the abandoned {@code com.sothree:AndroidSlidingUpPanel} library, backed
 * by the Material {@link BottomSheetBehavior} that already ships with the app. Keeps the original
 * class name, package, {@link PanelState} API, listener and {@code umano*} attributes so the
 * existing layouts and call sites need no changes.
 *
 * <p>Only the slice the app used is implemented: a two-child layout (main content + a slide-up
 * panel) with {@code COLLAPSED}/{@code EXPANDED} states, a configurable peek height and a slide
 * listener. The first child is the content; the second child becomes the bottom sheet.
 */
public class SlidingUpPanelLayout extends CoordinatorLayout {

    public enum PanelState {
        EXPANDED,
        COLLAPSED,
        ANCHORED,
        HIDDEN,
        DRAGGING,
        SETTLING
    }

    public interface PanelSlideListener {
        void onPanelSlide(View panel, float slideOffset);

        void onPanelStateChanged(
                View panel, PanelState previousState, PanelState newState);
    }

    /** No-op adapter, matching the original library. */
    public static class SimplePanelSlideListener implements PanelSlideListener {
        @Override
        public void onPanelSlide(View panel, float slideOffset) {}

        @Override
        public void onPanelStateChanged(
                View panel, PanelState previousState, PanelState newState) {}
    }

    private BottomSheetBehavior<View> behavior;
    private View panelView;
    private int peekHeightPx;
    private float shadowElevationPx;
    private PanelState lastState = PanelState.COLLAPSED;
    private final List<PanelSlideListener> listeners = new ArrayList<>();

    public SlidingUpPanelLayout(@NonNull Context context) {
        this(context, null);
    }

    public SlidingUpPanelLayout(@NonNull Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingUpPanelLayout(@NonNull Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // Default peek of 48dp matches every layout that uses this view.
        peekHeightPx =
                (int)
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());
        if (attrs != null) {
            TypedArray ta =
                    context.obtainStyledAttributes(attrs, R.styleable.SlidingUpPanelLayout);
            peekHeightPx =
                    ta.getDimensionPixelSize(
                            R.styleable.SlidingUpPanelLayout_umanoPanelHeight, peekHeightPx);
            shadowElevationPx =
                    ta.getDimension(R.styleable.SlidingUpPanelLayout_umanoShadowHeight, 0f);
            ta.recycle();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (getChildCount() < 2) {
            return;
        }
        panelView = getChildAt(1);
        if (shadowElevationPx > 0) {
            panelView.setElevation(shadowElevationPx);
        }

        behavior = new BottomSheetBehavior<>();
        behavior.setHideable(false);
        behavior.setPeekHeight(peekHeightPx);
        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        behavior.addBottomSheetCallback(
                new BottomSheetBehavior.BottomSheetCallback() {
                    @Override
                    public void onStateChanged(@NonNull View bottomSheet, int newState) {
                        PanelState mapped = fromBehaviorState(newState);
                        if (mapped == PanelState.DRAGGING || mapped == PanelState.SETTLING) {
                            dispatchStateChanged(bottomSheet, mapped);
                            return;
                        }
                        dispatchStateChanged(bottomSheet, mapped);
                    }

                    @Override
                    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                        for (PanelSlideListener l : listeners) {
                            l.onPanelSlide(bottomSheet, slideOffset);
                        }
                    }
                });

        CoordinatorLayout.LayoutParams lp =
                (CoordinatorLayout.LayoutParams) panelView.getLayoutParams();
        lp.setBehavior(behavior);
        panelView.setLayoutParams(lp);
    }

    private void dispatchStateChanged(View bottomSheet, PanelState newState) {
        PanelState previous = lastState;
        lastState = newState;
        for (PanelSlideListener l : listeners) {
            l.onPanelStateChanged(bottomSheet, previous, newState);
        }
    }

    public void setPanelState(PanelState state) {
        if (behavior == null) {
            lastState = state;
            return;
        }
        behavior.setState(toBehaviorState(state));
    }

    public PanelState getPanelState() {
        if (behavior == null) {
            return lastState;
        }
        return fromBehaviorState(behavior.getState());
    }

    public void setPanelHeight(int heightPx) {
        peekHeightPx = heightPx;
        if (behavior != null) {
            behavior.setPeekHeight(heightPx);
        }
    }

    public void addPanelSlideListener(PanelSlideListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void setPanelSlideListener(PanelSlideListener listener) {
        listeners.clear();
        addPanelSlideListener(listener);
    }

    private static int toBehaviorState(PanelState state) {
        switch (state) {
            case EXPANDED:
                return BottomSheetBehavior.STATE_EXPANDED;
            case ANCHORED:
                return BottomSheetBehavior.STATE_HALF_EXPANDED;
            case HIDDEN:
                return BottomSheetBehavior.STATE_HIDDEN;
            case COLLAPSED:
            default:
                return BottomSheetBehavior.STATE_COLLAPSED;
        }
    }

    private static PanelState fromBehaviorState(int state) {
        switch (state) {
            case BottomSheetBehavior.STATE_EXPANDED:
                return PanelState.EXPANDED;
            case BottomSheetBehavior.STATE_HALF_EXPANDED:
                return PanelState.ANCHORED;
            case BottomSheetBehavior.STATE_HIDDEN:
                return PanelState.HIDDEN;
            case BottomSheetBehavior.STATE_DRAGGING:
                return PanelState.DRAGGING;
            case BottomSheetBehavior.STATE_SETTLING:
                return PanelState.SETTLING;
            case BottomSheetBehavior.STATE_COLLAPSED:
            default:
                return PanelState.COLLAPSED;
        }
    }
}
