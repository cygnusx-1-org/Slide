package me.edgan.redditslide.util;

import androidx.annotation.Nullable;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.imageaware.ImageAware;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ImageLoaderUnescape extends ImageLoader {

    @Nullable private static volatile ImageLoaderUnescape instance;

    public static ImageLoaderUnescape getInstance() {
        // Read the volatile field into a local so the result is provably non-null on return; the
        // double-check itself is unchanged.
        ImageLoaderUnescape result = instance;
        if (result == null) {
            synchronized (ImageLoader.class) {
                result = instance;
                if (result == null) {
                    result = new ImageLoaderUnescape();
                    instance = result;
                }
            }
        }
        return result;
    }

    @Override
    public void displayImage(
            String uri,
            ImageAware imageAware,
            DisplayImageOptions options,
            ImageSize targetSize,
            ImageLoadingListener listener,
            ImageLoadingProgressListener progressListener) {
        String newUri = StringEscapeUtils.unescapeHtml4(uri);
        super.displayImage(newUri, imageAware, options, targetSize, listener, progressListener);
    }
}
