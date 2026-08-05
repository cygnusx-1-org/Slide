package me.edgan.redditslide.Adapters;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.devspark.robototextview.RobotoTypefaces;

import java.util.List;

import me.edgan.redditslide.Activities.Album;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.Activities.RedditGallery;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.LayoutUtils;

/** Vertical list of a Reddit gallery. Row behaviour lives in {@link VerticalMediaAdapter}. */
public class RedditGalleryView extends VerticalMediaAdapter {

    /**
     * Caption lines shown over a video row. The caption's height is part of the row's one-screenful
     * budget, so an unbounded caption would push the row past a screen.
     */
    private static final int VIDEO_CAPTION_MAX_LINES = 4;

    private final List<GalleryImage> images;

    public RedditGalleryView(
            final Activity context,
            final List<GalleryImage> images,
            String subreddit,
            String submissionTitle) {
        super(context, subreddit, submissionTitle);
        this.images = images;

        // Hook up the "grid" button on the toolbar, if present
        if (context.findViewById(R.id.grid) != null) {
            context.findViewById(R.id.grid).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            LayoutInflater l = context.getLayoutInflater();
                            View body = l.inflate(R.layout.album_grid_dialog, null, false);
                            GridView gridview = body.findViewById(R.id.images);
                            gridview.setAdapter(new ImageGridAdapter(context, true, images));

                            final AlertDialog.Builder builder =
                                    new AlertDialog.Builder(context).setView(body);
                            final Dialog d = builder.create();
                            gridview.setOnItemClickListener(
                                    new AdapterView.OnItemClickListener() {
                                        public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                                            View imagesView = context.findViewById(R.id.images);
                                            if (context instanceof Album) {
                                                // This is the older Album activity
                                                final RecyclerView.LayoutManager albumLm =
                                                        ((Album) context).album.album.recyclerView.getLayoutManager();
                                                if (albumLm != null) {
                                                    ((LinearLayoutManager) albumLm)
                                                        .scrollToPositionWithOffset(
                                                                position + 1,
                                                                LayoutUtils.getToolbarOffset(context));
                                                }
                                            } else if (imagesView instanceof RecyclerView) {
                                                // This case is when R.id.images in the context's layout is a RecyclerView.
                                                // For RedditGallery, R.id.images is a ViewPager, so this branch is not hit.
                                                // Original logic for this case:
                                                final RecyclerView.LayoutManager imagesLm =
                                                        ((RecyclerView) imagesView).getLayoutManager();
                                                if (imagesLm != null) {
                                                    ((LinearLayoutManager) imagesLm)
                                                        .scrollToPositionWithOffset(
                                                                position + 1, // Grid position is 0-indexed for images
                                                                LayoutUtils.getToolbarOffset(context));
                                                }
                                            } else if (imagesView instanceof ViewPager) {
                                                if (context instanceof RedditGallery) { // Specific fix for RedditGallery (vertical album)
                                                    ViewPager mainViewPagerInRedditGallery = (ViewPager) imagesView;
                                                    RedditGallery redditGalleryActivity = (RedditGallery) context;

                                                    // Determine the ViewPager page index for AlbumFrag
                                                    int albumFragPageIndexInViewPager = SettingValues.oldSwipeMode ? 1 : 0;

                                                    // Ensure ViewPager is showing AlbumFrag
                                                    if (mainViewPagerInRedditGallery.getCurrentItem() != albumFragPageIndexInViewPager) {
                                                        mainViewPagerInRedditGallery.setCurrentItem(albumFragPageIndexInViewPager, false);
                                                    }

                                                    // Get AlbumFrag's RecyclerView and scroll it
                                                    RedditGallery.RedditGalleryPagerAdapter pagerAdapter = (RedditGallery.RedditGalleryPagerAdapter) mainViewPagerInRedditGallery.getAdapter();
                                                    if (pagerAdapter != null && pagerAdapter.gallery != null && pagerAdapter.gallery.recyclerView != null) {
                                                        RedditGallery.AlbumFrag albumFrag = pagerAdapter.gallery;
                                                        // Grid 'position' is 0-indexed for images.
                                                        // AlbumFrag's RecyclerView has a spacer at its index 0. Image 0 is at RecyclerView index 1.
                                                        int scrollToRecyclerPosition = position + 1;

                                                        // Offset should be the height of the toolbar AlbumFrag is using.
                                                        // AlbumFrag configures redditGalleryActivity.mToolbar as its action bar.
                                                        int offset = 0;
                                                        if (redditGalleryActivity.mToolbar != null) {
                                                            offset = redditGalleryActivity.mToolbar.getHeight();
                                                        }

                                                        final RecyclerView.LayoutManager fragLm =
                                                                albumFrag.recyclerView.getLayoutManager();
                                                        if (fragLm != null) {
                                                            ((LinearLayoutManager) fragLm)
                                                                .scrollToPositionWithOffset(scrollToRecyclerPosition, offset);
                                                        }
                                                    }
                                                } else {
                                                    // Fallback for other contexts where R.id.images is a ViewPager
                                                    // (e.g., potentially AlbumPager, though it has its own grid handler)
                                                    // Original behavior for generic ViewPager:
                                                    ((ViewPager) imagesView).setCurrentItem(position);
                                                }
                                            }
                                            d.dismiss();
                                        }
                                    });
                            DialogUtil.matchDialogToCardBackground(d);
                            d.show();
                        }
                    });
        }
    }

    @Override
    protected int mediaCount() {
        return images == null ? 0 : images.size();
    }

    @Override
    protected boolean isAnimatedAt(final int index) {
        return images.get(index).isAnimated();
    }

    /**
     * A removed or failed gallery entry can carry no url at all — media_metadata with no "s" node
     * leaves every branch of getImageUrl() returning null.
     */
    @Override
    protected @Nullable String mediaUrlAt(final int index) {
        return images.get(index).getImageUrl();
    }

    @Override
    protected int mediaWidthAt(final int index) {
        return images.get(index).width;
    }

    @Override
    protected int mediaHeightAt(final int index) {
        return images.get(index).height;
    }

    /**
     * Shows the per-image caption above the video and reports its height, since {@code @id/imagearea}
     * sits below it. Bounded to {@link #VIDEO_CAPTION_MAX_LINES}, for the reason given there.
     */
    @Override
    protected int bindAnimatedExtras(
            final AnimatedViewHolder holder, final int index, final int rowWidth) {
        if (holder.caption == null) {
            return 0;
        }
        final String caption = images.get(index).caption;
        if (caption == null || caption.trim().isEmpty()) {
            holder.caption.setVisibility(View.GONE);
            return 0;
        }
        holder.caption.setText(caption);
        holder.caption.setMaxLines(VIDEO_CAPTION_MAX_LINES);
        holder.caption.setEllipsize(TextUtils.TruncateAt.END);
        holder.caption.setVisibility(View.VISIBLE);
        holder.caption.measure(
                View.MeasureSpec.makeMeasureSpec(rowWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return holder.caption.getMeasuredHeight();
    }

    @Override
    protected void bindStaticRow(final RecyclerView.ViewHolder holder2, final int index) {
        final StaticViewHolder holder = (StaticViewHolder) holder2;
        final GalleryImage image = images.get(index);
        final String url = mediaUrlAt(index);
        final boolean playable = url != null;

        // Ensure view is fully opaque. The gallery's still rows have always painted their own black
        // background, unlike the Imgur album's, whose gaps show the list background through.
        holder.itemView.setAlpha(1.0f);
        holder.itemView.setBackgroundColor(android.graphics.Color.BLACK);

        if (playable) {
            ((Reddit) main.getApplicationContext())
                    .getImageLoader()
                    .displayImage(url, holder.image, ImageGridAdapter.options);
        } else {
            // Cancel first: displayImage() is what clears a pending load for this view, so
            // skipping it would let the previous row's task finish and draw into this one.
            ((Reddit) main.getApplicationContext()).getImageLoader().cancelDisplayTask(holder.image);
            holder.image.setImageDrawable(null);
        }

        // Title hidden by default; show the per-image caption when present.
        holder.text.setVisibility(View.GONE);
        if (image.caption != null && !image.caption.trim().isEmpty()) {
            holder.body.setText(image.caption);
            holder.body.setVisibility(View.VISIBLE);
        } else {
            holder.body.setVisibility(View.GONE);
        }

        // Reserve the row's height from the reported dimensions so that loading the bitmap
        // never resizes the row. The 0 reset matters because the ratio survives recycling.
        holder.image.setAspectRatio(
                (image.width > 0 && image.height > 0)
                        ? (double) image.height / (double) image.width
                        : 0);
        holder.image.setLayoutParams(
                new LinearLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT));

        // Font styling
        {
            int commentType = new FontPreferences(holder.body.getContext()).getFontTypeComment().getTypeface();
            Typeface commentTypeface = (commentType >= 0)
                    ? RobotoTypefaces.obtainTypeface(holder.body.getContext(), commentType)
                    : Typeface.DEFAULT;
            holder.body.setTypeface(commentTypeface);
        }
        {
            int titleType = new FontPreferences(holder.body.getContext()).getFontTypeTitle().getTypeface();
            Typeface titleTypeface = (titleType >= 0)
                    ? RobotoTypefaces.obtainTypeface(holder.body.getContext(), titleType)
                    : Typeface.DEFAULT;
            holder.text.setTypeface(titleTypeface);
        }

        // Clicking on the static image. setClickable comes after setOnClickListener, not before:
        // that method turns clickable back on when it is handed a null listener, so setting it
        // first would leave an inert row still clickable.
        holder.itemView.setOnClickListener(playable ? v -> openMedia(url, index) : null);
        holder.itemView.setClickable(playable);
    }

}
