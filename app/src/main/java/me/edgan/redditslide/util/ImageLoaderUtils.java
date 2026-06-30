package me.edgan.redditslide.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.DisplayMetrics;
import com.nostra13.universalimageloader.cache.disc.DiskCache;
import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiskCache;
import com.nostra13.universalimageloader.cache.disc.impl.ext.LruDiskCache;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.display.SimpleBitmapDisplayer;
import java.io.File;
import java.io.IOException;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.SubsamplingScaleImageView;

/** Created by carlo_000 on 10/19/2015. */
/*Adapted from https://github.com/Kennyc1012/Opengur */

public class ImageLoaderUtils {

    public static ImageLoaderUnescape imageLoader;
    public static DisplayImageOptions options;

    private ImageLoaderUtils() {}

    public static File getCacheDirectory(Context context) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)
                && context.getExternalCacheDir() != null) {
            return context.getExternalCacheDir();
        }
        return context.getCacheDir();
    }

    public static File getCacheDirectoryGif(Context context) {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)
                && context.getExternalCacheDir() != null) {
            return new File(context.getExternalCacheDir() + File.separator + "gifs");
        }
        return new File(context.getCacheDir() + File.separator + "gifs");
    }

    public static void initImageLoader(Context context) {
        long discCacheSize = 1024 * 1024;
        DiskCache discCache;
        File dir = getCacheDirectory(context);
        discCacheSize *= 100;
        int threadPoolSize = Constants.IMAGE_LOADER_THREAD_POOL_SIZE;
        if (discCacheSize > 0) {
            try {
                dir.mkdir();
                discCache = new LruDiskCache(dir, new Md5FileNameGenerator(), discCacheSize);
            } catch (IOException e) {
                discCache = new UnlimitedDiskCache(dir);
            }
        } else {
            discCache = new UnlimitedDiskCache(dir);
        }

        options =
                new DisplayImageOptions.Builder()
                        .cacheOnDisk(true)
                        .bitmapConfig(
                                SettingValues.highColorspaceImages
                                        ? Bitmap.Config.ARGB_8888
                                        : Bitmap.Config.RGB_565)
                        .imageScaleType(
                                SettingValues.highColorspaceImages
                                        ? ImageScaleType.NONE_SAFE
                                        : ImageScaleType.IN_SAMPLE_POWER_OF_2)
                        .cacheInMemory(false)
                        .resetViewBeforeLoading(false)
                        // No fade — images appear instantly instead of animating in.
                        .displayer(new SimpleBitmapDisplayer())
                        .build();

        if (SettingValues.highColorspaceImages) {
            SubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.ARGB_8888);
        }

        // Cap decoded bitmaps to the screen size. The feed's EXACTLY-scaled lead images were
        // otherwise decoded at the (very tall) view height — up to ~15 MB each — which made the
        // in-memory cache hold almost nothing. Full-screen zoom is unaffected: it uses
        // SubsamplingScaleImageView decoding straight from the disk-cache file, not this path.
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        // Give the memory cache a real budget so nearby scroll-back hits memory instead of disk.
        int memoryCacheSize = (int) (Runtime.getRuntime().maxMemory() / 4);

        ImageLoaderConfiguration config =
                new ImageLoaderConfiguration.Builder(context)
                        .threadPoolSize(threadPoolSize)
                        .denyCacheImageMultipleSizesInMemory()
                        .memoryCacheExtraOptions(metrics.widthPixels, metrics.heightPixels)
                        .memoryCacheSize(memoryCacheSize)
                        .diskCache(discCache)
                        .imageDownloader(new OkHttpImageDownloader(context))
                        .defaultDisplayImageOptions(options)
                        .build();

        if (ImageLoader.getInstance().isInited()) {
            ImageLoader.getInstance().destroy();
        }

        imageLoader = ImageLoaderUnescape.getInstance();
        imageLoader.init(config);
    }
}
