package me.edgan.redditslide.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import com.nostra13.universalimageloader.core.DisplayImageOptions
import com.nostra13.universalimageloader.core.ImageLoader
import com.nostra13.universalimageloader.core.assist.ImageSize
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener
import com.nostra13.universalimageloader.utils.MemoryCacheUtils
import org.apache.commons.text.StringEscapeUtils

/**
 * Binds an image into a view, drawing it synchronously in this frame when the bitmap is already
 * cached, and falling back to the normal async load when it isn't.
 *
 * This is what stops feed images from popping in after the row is already on screen: on a cache hit
 * the row scrolls in with the image already drawn instead of blank-then-async-swap. Lifted out of
 * [me.edgan.redditslide.SubmissionViews.HeaderImageLinkView] so the lead image and the gallery
 * grid's tiles share one implementation — two copies of this would drift, and the symptom of drift
 * is exactly the pop-in it exists to prevent.
 */
object CachedFirstImageBinder {

    /**
     * Bind [url] into [target].
     *
     * @param decodeSize the size [syncDecodeFromDisk] decodes and caches at. Must match what the
     *   preloader warmed the same url at, or the two land under different UIL `uri_WxH` memory keys
     *   and neither can see the other's bitmap.
     * @param allowSyncDiskDecode whether a memory miss may decode from the disk cache on this
     *   thread. Worth it for a thumbnail or a grid tile; not for a full-width lead image, where a
     *   synchronous decode could jank the scroll.
     * @param minCachedWidthPx how wide a cached bitmap has to be to be worth binding. 0 means "as
     *   wide as the view", which is the right yardstick when the image was fetched to fill it. A
     *   caller that deliberately fetched a smaller image — the low-resolution setting — passes the
     *   width it actually asked for, otherwise the check below throws away the very bitmap the
     *   setting told it to fetch and reloads the same url to get the same pixels back.
     */
    @JvmStatic
    @JvmOverloads
    fun display(
        loader: ImageLoader,
        url: String?,
        target: ImageView?,
        options: DisplayImageOptions,
        listener: ImageLoadingListener?,
        decodeSize: ImageSize,
        allowSyncDiskDecode: Boolean,
        minCachedWidthPx: Int = 0,
    ) {
        if (url != null && target != null) {
            // UIL keys its memory cache by "uri_WxH", so look up by URI across all sizes. Unescape
            // to match ImageLoaderUnescape, which unescapes the URI on every display/load.
            val unescaped = StringEscapeUtils.unescapeHtml4(url)
            var cached = firstCachedBitmap(loader, unescaped)

            // Memory miss, but the preloader already downloaded the file (or it was decoded earlier
            // and then evicted from the memory LRU while scrolling): decode from disk synchronously
            // in this frame instead of swapping it in asynchronously, which reads as a redraw.
            // Bounded to decodeSize and gated on the file already being cached, so it never touches
            // the network.
            if (cached == null && allowSyncDiskDecode) {
                cached = syncDecodeFromDisk(loader, unescaped, decodeSize)
            }

            // Keep the thumbnail and full-image paths from crossing over: only bind a cached bitmap
            // that is actually large enough for this view. A thumbnail-sized bitmap must never be
            // stretched into a full-width lead image (it would look blurry) — this covers the
            // per-subreddit pics override and single-rung posts where the thumb and full share a URL.
            // The small thumbnail view always passes; the big lead image only takes a full-sized one.
            val floor = if (minCachedWidthPx > 0) minCachedWidthPx else target.width
            if (cached != null &&
                !cached.isRecycled &&
                (floor <= 0 || cached.width * 3 >= floor * 2)
            ) {
                // The holder is recycled: cancel any load still in flight for this view from the
                // previous post, or it could complete later and overwrite our bitmap.
                loader.cancelDisplayTask(target)
                target.setImageBitmap(cached)
                listener?.onLoadingComplete(url, target, cached)
                return
            }
        }
        loader.displayImage(url, target, options, listener)
    }

    private fun firstCachedBitmap(loader: ImageLoader, uri: String): Bitmap? {
        for (cached in MemoryCacheUtils.findCachedBitmapsForImageUri(uri, loader.memoryCache)) {
            if (cached != null && !cached.isRecycled) {
                return cached
            }
        }
        return null
    }

    /**
     * Synchronously decode an already-downloaded image from the disk cache, or null if the file
     * isn't cached yet (so the caller falls back to an async load).
     *
     * Decodes the file directly with BitmapFactory so it can never fall through to a main-thread
     * network fetch — `loadImageSync` would, if the disk entry were evicted between the exists()
     * check and the decode. Decoded at, and cached under, the preloader's target size/key so the
     * result reuses the preloader's memory entry instead of adding a second differently sized one,
     * and repeat binds hit memory instead of re-decoding.
     */
    private fun syncDecodeFromDisk(loader: ImageLoader, uri: String, size: ImageSize): Bitmap? {
        try {
            val diskFile = loader.diskCache.get(uri)
            if (diskFile == null || !diskFile.exists()) {
                return null
            }

            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeFile(diskFile.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return null
            }

            val opts = BitmapFactory.Options()
            opts.inPreferredConfig = Bitmap.Config.RGB_565
            // Cap the loop (sample <= 32) so it can't spin even if a target dimension is ever 0.
            var sample = 1
            while (sample < 32 &&
                bounds.outWidth / (sample * 2) >= size.width &&
                bounds.outHeight / (sample * 2) >= size.height
            ) {
                sample *= 2
            }
            opts.inSampleSize = sample

            val bmp = BitmapFactory.decodeFile(diskFile.absolutePath, opts)
            if (bmp != null) {
                loader.memoryCache.put(MemoryCacheUtils.generateKey(uri, size), bmp)
            }
            return bmp
            // Catch Throwable, not Exception: BitmapFactory.decodeFile throws OutOfMemoryError (an
            // Error) on allocation failure, which must degrade to the async fallback, not crash the
            // bind. UIL's own decoder guards against OOM the same way.
        } catch (ignored: Throwable) {
            // See the note above: any failure, OOM included, degrades to the async load.
        }
        return null
    }
}
