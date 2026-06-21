package me.edgan.redditslide.Views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.annotation.ColorInt;

import com.google.android.material.imageview.ShapeableImageView;

/**
 * Created by Carlos on 9/13/2016.
 *
 * <p>Draws a small colored post-type flag with a diagonal depth gradient in the bottom-right
 * corner of the thumbnail. The gradient style is ported from Continuum's PostTypeIndicatorView.
 */
public class RoundImageTriangleView extends ShapeableImageView {

    public RoundImageTriangleView(Context context) {
        super(context);
    }

    public RoundImageTriangleView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public RoundImageTriangleView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private int color = Color.TRANSPARENT;
    private final Paint flagPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path flagPath = new Path();

    // Cached shader inputs so the gradient is only rebuilt when the color or size changes.
    private int shaderColor = 0;
    private int shaderWidth = 0;
    private int shaderHeight = 0;

    public void setFlagColor(@ColorInt int color) {
        if (this.color != color) {
            this.color = color;
            // Fallback solid color in case the gradient shader isn't built yet (view not laid out).
            flagPaint.setColor(color);
            updateShader();
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader();
    }

    private void updateShader() {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0 || color == Color.TRANSPARENT) {
            return;
        }
        if (color == shaderColor && w == shaderWidth && h == shaderHeight) {
            return;
        }
        // Flag legs are a fifth of the view's width so it scales with the thumbnail size.
        final int s = w / 5;
        // Lighter toward the hypotenuse (inner, top-left), darker into the outer corner.
        flagPaint.setShader(
                new LinearGradient(
                        w - s,
                        h - s,
                        w,
                        h,
                        lighten(color, 0.30f),
                        darken(color, 0.55f),
                        Shader.TileMode.CLAMP));
        shaderColor = color;
        shaderWidth = w;
        shaderHeight = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (color == Color.TRANSPARENT) {
            return;
        }

        final int w = getWidth();
        final int h = getHeight();
        // Right-triangle hugging the bottom-right corner, legs a fifth of the view's width.
        final int s = w / 5;
        if (s <= 0) {
            return;
        }

        flagPath.reset();
        flagPath.moveTo(w - s, h); // along the bottom edge
        flagPath.lineTo(w, h - s); // along the right edge
        flagPath.lineTo(w, h); // outer corner
        flagPath.close();

        canvas.drawPath(flagPath, flagPaint);
    }

    private static int darken(int color, float factor) {
        return Color.rgb(
                (int) (Color.red(color) * factor),
                (int) (Color.green(color) * factor),
                (int) (Color.blue(color) * factor));
    }

    private static int lighten(int color, float factor) {
        return Color.rgb(
                (int) (Color.red(color) + (255 - Color.red(color)) * factor),
                (int) (Color.green(color) + (255 - Color.green(color)) * factor),
                (int) (Color.blue(color) + (255 - Color.blue(color)) * factor));
    }
}
