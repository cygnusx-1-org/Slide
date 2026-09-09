package me.edgan.redditslide.util;

import android.graphics.Bitmap;
import com.nostra13.universalimageloader.cache.disc.DiskCache;
import com.nostra13.universalimageloader.utils.IoUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A disk cache that refuses to hand back Reddit's "probably deleted" graphic.
 *
 * <p>{@link OkHttpImageDownloader} stops that graphic being cached in the first place, but entries
 * written before that check existed are still on disk, and the cache is read from far more places
 * than the downloader: the image loader's own task, the synchronous bind in
 * {@link me.edgan.redditslide.util.CachedFirstImageBinder}, the full-screen viewer, the force-touch
 * peek, and the service that saves an image to the user's storage. Filtering here covers all of
 * them at once, and does it better than a downloader check could: dropping the entry inside
 * {@code get} makes the loader treat it as a cache miss and refetch in the same pass, instead of
 * failing the load and waiting for a later one.
 */
@NullMarked
public class PlaceholderFilteringDiskCache implements DiskCache {

    private final DiskCache delegate;

    public PlaceholderFilteringDiskCache(DiskCache delegate) {
        this.delegate = delegate;
    }

    @Override
    public File getDirectory() {
        return delegate.getDirectory();
    }

    @Override
    public @Nullable File get(String imageUri) {
        final File file = delegate.get(imageUri);
        if (file != null && RedditPlaceholderImage.isPlaceholder(file)) {
            delegate.remove(imageUri);
            return null;
        }
        return file;
    }

    @Override
    public boolean save(String imageUri, InputStream imageStream, IoUtils.CopyListener listener)
            throws IOException {
        return delegate.save(imageUri, imageStream, listener);
    }

    @Override
    public boolean save(String imageUri, Bitmap bitmap) throws IOException {
        return delegate.save(imageUri, bitmap);
    }

    @Override
    public boolean remove(String imageUri) {
        return delegate.remove(imageUri);
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
