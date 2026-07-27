package me.edgan.redditslide.Adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.ImageView;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.ImgurAlbum.Image;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Tumblr.Photo;
import me.edgan.redditslide.Tumblr.PhotoSize;

/** Created by carlo_000 on 3/20/2016. */
public class ImageGridAdapter extends android.widget.BaseAdapter {
    private Context mContext;
    private List<String> jsons;
    public static final DisplayImageOptions options =
            new DisplayImageOptions.Builder()
                    .cacheOnDisk(true)
                    .resetViewBeforeLoading(true)
                    .bitmapConfig(
                            SettingValues.highColorspaceImages
                                    ? Bitmap.Config.ARGB_8888
                                    : Bitmap.Config.RGB_565)
                    .imageScaleType(ImageScaleType.EXACTLY)
                    .cacheInMemory(false)
                    .displayer(new FadeInBitmapDisplayer(250))
                    .build();

    public ImageGridAdapter(Context c, List<Image> imgurAlbum) {
        mContext = c;
        jsons = new ArrayList<>();
        for (Image i : imgurAlbum) {
            addUrl(i == null ? null : i.getThumbnailUrl());
        }
    }

    public ImageGridAdapter(Context c, boolean gallery, List<GalleryImage> redditGallery) {
        mContext = c;
        jsons = new ArrayList<>();
        for (GalleryImage i : redditGallery) {
            addUrl(i == null ? null : i.url);
        }
    }

    public ImageGridAdapter(Context c, List<Photo> tumblrAlbum, boolean tumblr) {
        mContext = c;
        jsons = new ArrayList<>();
        for (Photo i : tumblrAlbum) {
            addUrl(i == null ? null : thumbnailUrl(i));
        }
    }

    /**
     * Adds one cell's url, keeping a null rather than skipping it.
     *
     * <p>One cell per album entry, never skipped: callers use the grid position as an album index —
     * the vertical lists scroll to it and the pagers page to it — so dropping an entry would shift
     * every later thumbnail onto the wrong item. An entry with nothing to show contributes a null,
     * which {@code displayImage} tolerates (it clears the view and reports the load complete).
     *
     * <p>All three constructors go through here, and all three null-check the entry itself as well:
     * a deserialized list holds a null for a null element in the JSON array, which Jackson leaves in
     * place (Photo and Image are both read with an ObjectMapper).
     */
    private void addUrl(final String url) {
        jsons.add(url);
    }

    /**
     * A grid-sized url for a Tumblr photo, or null when it has none.
     *
     * <p>The last alt_sizes entry, because Tumblr returns that array largest first: a real response
     * gives widths 900, 640, 540, 500, 400, 250, 100, 75, and its first entry's url is byte-identical
     * to original_size's. So the last is the smallest — the right one for a thumbnail — and the first
     * is the full-size original. The ordering is pinned by {@code tumblrPhotoAltSizes.json} and
     * ImageGridAdapterTest rather than assumed; TumblrPager's {@code ImageFullNoSubmission} picks the
     * middle entry on the same understanding, in both its initial load and its reload.
     *
     * <p>This consolidated two adapters that disagreed. The deleted ImageGridAdapterTumblr, used by
     * the vertical Tumblr album, took the last entry; this class's own constructor, used by the pager
     * grid and the force-touch peek, took the first — so those two were loading a full-size original
     * into every cell of a five-column grid. Taking the last everywhere is a deliberate change to
     * them, not preserved behaviour.
     */
    private static String thumbnailUrl(final Photo photo) {
        final List<PhotoSize> altSizes = photo.getAltSizes();
        if (altSizes != null) {
            // Backwards from the smallest: an entry that is null or carries no url costs one step up
            // the size ladder, not a fall all the way back to original_size — which is the 900px
            // file this method exists to keep out of a five-column grid.
            for (int i = altSizes.size() - 1; i >= 0; i--) {
                final String altUrl = sizeUrl(altSizes, i);
                if (altUrl != null) {
                    return altUrl;
                }
            }
        }
        return photo.getOriginalUrl();
    }

    /**
     * The url of {@code sizes.get(index)}, or null when there is none — an index off the end, a null
     * entry, or an entry carrying no url. alt_sizes holds a null for a null element in the JSON
     * array exactly as photos does, so indexing it and dereferencing in one breath is an NPE.
     *
     * <p>Shared with {@link me.edgan.redditslide.Activities.TumblrPager}, which picks a mid-sized
     * entry rather than the smallest but has the same nulls to survive.
     */
    public static String sizeUrl(final List<PhotoSize> sizes, final int index) {
        if (sizes == null || index < 0 || index >= sizes.size()) {
            return null;
        }
        final PhotoSize size = sizes.get(index);
        return size == null ? null : size.getUrl();
    }

    public int getCount() {
        return jsons.size();
    }

    public String getItem(int position) {
        return jsons.get(position);
    }

    public long getItemId(int position) {
        return 0;
    }

    // create a new ImageView for each item referenced by the Adapter
    public View getView(int position, View convertView, ViewGroup parent) {
        ImageView imageView;
        GridView grid = (GridView) parent;
        int size = grid.getColumnWidth();
        if (convertView == null) {
            // if it's not recycled, initialize some attributes
            imageView = new ImageView(mContext);
        } else {
            imageView = (ImageView) convertView;
        }
        imageView.setLayoutParams(new GridView.LayoutParams(size, size));
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        ((Reddit) mContext.getApplicationContext())
                .getImageLoader()
                .displayImage(getItem(position), imageView, options);
        return imageView;
    }
}
