package me.edgan.redditslide.Views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;
import org.jspecify.annotations.NullMarked;

/**
 * Fallback {@link ImageRegionDecoder} used when the default {@link
 * com.davemorrissey.labs.subscaleview.decoder.SkiaImageRegionDecoder} fails to initialise — i.e.
 * when Android's native {@code BitmapRegionDecoder} rejects the image (CMYK / grayscale JPEGs and
 * some PNGs). Decodes the whole bitmap once via {@link ImageDecoder} (which supports a superset of
 * formats) and serves tile regions from it in memory.
 *
 * <p>Replaces the old, unmaintained RapidDecoder library. Because the full bitmap is held in
 * memory, this is heavier than true region decoding, but it only ever runs as the fallback path
 * for the rare images the native region decoder cannot open.
 */
@NullMarked
public class FallbackImageRegionDecoder implements ImageRegionDecoder {

    private static final String ASSET_PREFIX = "file:///android_asset/";

    @Nullable private Bitmap bitmap;

    @Override
    public Point init(Context context, Uri uri) throws Exception {
        final String uriString = uri.toString();
        final ImageDecoder.Source source;
        if (uriString.startsWith(ASSET_PREFIX)) {
            source =
                    ImageDecoder.createSource(
                            context.getAssets(), uriString.substring(ASSET_PREFIX.length()));
        } else {
            // ContentResolver handles file://, content:// and android.resource:// schemes.
            source = ImageDecoder.createSource(context.getContentResolver(), uri);
        }

        // Force a software bitmap so createBitmap() can read sub-regions (hardware bitmaps can't).
        bitmap =
                ImageDecoder.decodeBitmap(
                        source,
                        (decoder, info, src) ->
                                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));

        return new Point(bitmap.getWidth(), bitmap.getHeight());
    }

    @Override
    public synchronized Bitmap decodeRegion(Rect sRect, int sampleSize) {
        // ImageRegionDecoder declares a non-null result, so a failure has to be thrown rather
        // than returned. SubsamplingScaleImageView's TileLoadTask catches it and reports it
        // through onTileLoadError, which a silent null never reached.
        if (bitmap == null || bitmap.isRecycled()) {
            throw new IllegalStateException("decodeRegion called after recycle()");
        }
        try {
            final int left = Math.max(0, sRect.left);
            final int top = Math.max(0, sRect.top);
            final int width = Math.max(1, Math.min(bitmap.getWidth(), sRect.right) - left);
            final int height = Math.max(1, Math.min(bitmap.getHeight(), sRect.bottom) - top);

            Bitmap region = Bitmap.createBitmap(bitmap, left, top, width, height);
            if (sampleSize > 1) {
                final int scaledWidth = Math.max(1, width / sampleSize);
                final int scaledHeight = Math.max(1, height / sampleSize);
                final Bitmap scaled =
                        Bitmap.createScaledBitmap(region, scaledWidth, scaledHeight, true);
                if (scaled != region) {
                    region.recycle();
                }
                region = scaled;
            }
            return region;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode region", e);
        }
    }

    @Override
    public boolean isReady() {
        return bitmap != null && !bitmap.isRecycled();
    }

    @Override
    public void recycle() {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }
}
