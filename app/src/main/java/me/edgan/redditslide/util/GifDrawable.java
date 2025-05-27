package me.edgan.redditslide.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

public class GifDrawable extends Drawable {
    private final Movie movie;
    private long startTime = 0;
    private int alpha = 255; // Default alpha
    private ColorFilter colorFilter;
    private final Paint paint;
    private boolean isPlaying = false;
    private int currentFrame = 0;

    public GifDrawable(Movie movie, Drawable.Callback callback) {
        this.movie = movie;
        setCallback(callback);
        this.paint = new Paint();
    }

    @Override
    public void draw(Canvas canvas) {
        if (movie == null || movie.duration() == 0) return;

        long now = SystemClock.uptimeMillis();

        if (startTime == 0) { // first time
            startTime = now;
        }

        int relTime = (int) ((now - startTime) % movie.duration());
        movie.setTime(relTime);

        // Apply alpha and color filter to Paint
        paint.setAlpha(alpha);
        paint.setColorFilter(colorFilter);

        // Draw the movie with the paint
        movie.draw(canvas, getBounds().left, getBounds().top, paint);

        // Schedule a redraw to animate the GIF
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getIntrinsicWidth() {
        return movie != null ? movie.width() : 0;
    }

    @Override
    public int getIntrinsicHeight() {
        return movie != null ? movie.height() : 0;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /** Starts the GIF animation. */
    public void start() {
        startTime = 0; // Reset to start
        invalidateSelf();
    }

    /** Stops the GIF animation. */
    public void stop() {
        isPlaying = false;
        startTime = 0;
        currentFrame = 0;
    }

    public void seekToFirstFrame() {
        if (movie != null) {
            movie.setTime(0);
            invalidateSelf();
        }
    }
}
