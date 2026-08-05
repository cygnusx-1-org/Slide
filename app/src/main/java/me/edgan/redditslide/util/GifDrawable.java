package me.edgan.redditslide.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class GifDrawable extends Drawable {
    private final Movie movie;
    private long startTime = 0;
    private int alpha = 255; // Default alpha
    @Nullable private ColorFilter colorFilter;
    private final Paint paint;

    public GifDrawable(Movie movie, @Nullable Drawable.Callback callback) {
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
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
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
        startTime = 0;
    }

    public void seekToFirstFrame() {
        if (movie != null) {
            movie.setTime(0);
            invalidateSelf();
        }
    }
}
