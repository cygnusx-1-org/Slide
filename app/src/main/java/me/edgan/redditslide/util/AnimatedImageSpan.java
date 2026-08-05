package me.edgan.redditslide.util;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.style.DynamicDrawableSpan;
import android.util.Log;
import android.view.View;
import androidx.annotation.Nullable;
import me.edgan.redditslide.SettingValues;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class AnimatedImageSpan extends DynamicDrawableSpan {
    private final GifDrawable drawable;
    private final View view;
    private final Handler handler;
    private boolean isAttached = false;
    private boolean isAnimationEnabled = false;
    private final Runnable invalidateRunnable;
    private static final int FRAME_DELAY = 16; // Approximately 60fps

    public AnimatedImageSpan(GifDrawable drawable, View view) {
        super(ALIGN_BASELINE); // Keep ALIGN_BASELINE but we'll handle positioning manually
        this.drawable = drawable;
        this.view = view;
        this.handler = new Handler(Looper.getMainLooper());
        this.isAnimationEnabled =
                SettingValues.commentEmoteAnimation; // Initialize based on setting

        invalidateRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        if (isAttached && isAnimationEnabled) {
                            view.postInvalidate();
                            handler.postDelayed(this, FRAME_DELAY);
                        }
                    }
                };
    }

    private static final float SCALE_FACTOR = 0.5f;
    private static final int EMOTE_SPACING = 30; // extra horizontal spacing in pixels

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                     float x, int top, int y, int bottom, Paint paint) {

        Drawable drawable = getDrawable();
        Rect bounds = drawable.getBounds();

        int intrinsicWidth = bounds.width();
        int intrinsicHeight = bounds.height();

        int transY = y - intrinsicHeight + 18;

        canvas.save();
        canvas.scale(SCALE_FACTOR, SCALE_FACTOR);
        canvas.translate(x / SCALE_FACTOR, transY / SCALE_FACTOR);

        drawable.draw(canvas);
        canvas.restore();
    }

    @Override
    public Drawable getDrawable() {
        drawable.invalidateSelf();
        return drawable;
    }

    @Override
    public int getSize(
            Paint paint,
            CharSequence text,
            int start,
            int end,
            @Nullable Paint.FontMetricsInt fm) {
        Drawable d = getDrawable();
        Rect rect = d.getBounds();
        int scaledHeight = (int) (rect.height() * SCALE_FACTOR);
        int scaledWidth  = (int) (rect.width()  * SCALE_FACTOR);

        if (fm != null) {
            // Ensure the line is tall enough for the image.
            int fontHeight = fm.descent - fm.ascent;

            // If the image is taller than the font height, push the ascent up:
            if (scaledHeight > fontHeight) {
                // Increase descent so the line can fit the image
                fm.descent += (scaledHeight - fontHeight);
            }
            // (You could also shift fm.ascent upward, but the simplest is to push descent down.)
            fm.bottom = fm.descent;
        }

        // Reserve enough horizontal space for the image plus spacing
        return scaledWidth + EMOTE_SPACING;
    }

    public void start() {
        if (SettingValues.commentEmoteAnimation) {
            isAnimationEnabled = true;
            if (isAttached) {
                handler.removeCallbacks(invalidateRunnable); // Remove any existing callbacks
                drawable.start();
                handler.post(invalidateRunnable);
            }
        }
    }

    public void stop() {
        isAnimationEnabled = false;
        handler.removeCallbacks(invalidateRunnable);
        drawable.stop();
        drawable.seekToFirstFrame();
        if (isAttached) {
            view.postInvalidate();
        }
    }

    public GifDrawable getGifDrawable() {
        return drawable;
    }

    public void onAttached() {
        Log.d("EmoteDebug", "Span attached to view");
        isAttached = true;
        if (isAnimationEnabled && SettingValues.commentEmoteAnimation) {
            handler.removeCallbacks(invalidateRunnable); // Remove any existing callbacks
            drawable.start();
            handler.post(invalidateRunnable);
        }
    }

    public void onDetached() {
        Log.d("EmoteDebug", "Span detached from view");
        isAttached = false;
        handler.removeCallbacks(invalidateRunnable);
        drawable.stop();
        drawable.seekToFirstFrame();
    }
}
