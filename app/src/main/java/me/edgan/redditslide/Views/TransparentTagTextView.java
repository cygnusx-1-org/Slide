package me.edgan.redditslide.Views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import me.edgan.redditslide.R;
import org.jspecify.annotations.NullMarked;

/** Created by carlos on 3/14/16. */
@NullMarked
public class TransparentTagTextView extends AppCompatTextView {
    @Nullable Bitmap mMaskBitmap;
    @Nullable Canvas mMaskCanvas;

    // Assigned by init(context), which every constructor calls.
    @SuppressWarnings("NullAway.Init")
    Paint mPaint;

    @Nullable Drawable backdrop;
    @Nullable Drawable mBackground;
    @Nullable Bitmap mBackgroundBitmap;
    @Nullable Canvas mBackgroundCanvas;
    boolean mSetBoundsOnSizeAvailable = false;

    public TransparentTagTextView(Context context) {
        super(context);

        init(context);
    }

    public TransparentTagTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void init(Context context) {
        mSetBoundsOnSizeAvailable = true;
        mPaint = new Paint();
        super.setTextColor(Color.BLACK);
        super.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        setBackgroundDrawable(context.getResources().getDrawable(R.drawable.flairback));
    }

    @Override
    public void setBackgroundDrawable(@Nullable Drawable bg) {
        if (bg != null) {
            mBackground = bg;
            int w = bg.getIntrinsicWidth();
            int h = bg.getIntrinsicHeight();

            // Drawable has no dimensions, retrieve View's dimensions
            if (w == -1 || h == -1) {
                w = getWidth();
                h = getHeight();
            }

            // Layout has not run
            if (w == 0 || h == 0) {
                mSetBoundsOnSizeAvailable = true;
                return;
            }

            mBackground.setBounds(0, 0, w, h);
        }
        invalidate();
    }

    @Override
    public void setBackgroundColor(int color) {
        setBackgroundDrawable(new ColorDrawable(color));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && h > 0) {
            mBackgroundBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mBackgroundCanvas = new Canvas(mBackgroundBitmap);
            mMaskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            mMaskCanvas = new Canvas(mMaskBitmap);

            if (mSetBoundsOnSizeAvailable && mBackground != null) {
                mBackground.setBounds(0, 0, w, h);
                mSetBoundsOnSizeAvailable = false;
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.setBackgroundDrawable(backdrop);

        // Everything below needs the off-screen buffers onSizeChanged allocates, so a draw that
        // arrives before the first layout has nothing to composite.
        final Drawable background = mBackground;
        final Canvas backgroundCanvas = mBackgroundCanvas;
        final Bitmap maskBitmap = mMaskBitmap;
        final Bitmap backgroundBitmap = mBackgroundBitmap;
        if (background == null || backgroundCanvas == null) {
            return;
        }

        // Draw background
        background.draw(backgroundCanvas);

        // Draw mask
        if (mMaskCanvas != null && maskBitmap != null && backgroundBitmap != null) {
            mMaskCanvas.drawColor(Color.BLACK, BlendMode.CLEAR);
            super.onDraw(mMaskCanvas);
            backgroundCanvas.drawBitmap(maskBitmap, 0.f, 0.f, mPaint);
            canvas.drawBitmap(backgroundBitmap, 0.f, 0.f, null);
        }
    }
}
