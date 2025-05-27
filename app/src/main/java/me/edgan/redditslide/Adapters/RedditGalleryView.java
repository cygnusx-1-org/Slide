package me.edgan.redditslide.Adapters;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnScrollListener;
import androidx.viewpager.widget.ViewPager;

import com.devspark.robototextview.RobotoTypefaces;

import java.util.List;

import me.edgan.redditslide.Activities.Album;
import me.edgan.redditslide.Activities.GalleryImage;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Activities.RedditGallery;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.LinkUtil;

/**
 * RecyclerView adapter for the vertical Reddit gallery,
 * now extended to handle GIF/MP4 items as well as static images.
 */
public class RedditGalleryView extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_IMAGE = 1;
    private static final int VIEW_TYPE_SPACER = 6;
    private static final int VIEW_TYPE_ANIMATED = 2;

    private final List<GalleryImage> images;
    private final Activity main;

    public boolean paddingBottom;
    public int height;
    public String subreddit;
    private final String submissionTitle;

    private RecyclerView recyclerView;

    public RedditGalleryView(
            final Activity context,
            final List<GalleryImage> images,
            int height,
            String subreddit,
            String submissionTitle) {
        this.height = height;
        main = context;
        this.images = images;
        this.subreddit = subreddit;
        this.submissionTitle = submissionTitle;

        // If there's no toolbar, we might handle top or bottom spacing differently
        paddingBottom = (main.findViewById(R.id.toolbar) == null);

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
                                                ((LinearLayoutManager)((Album) context).album.album.recyclerView.getLayoutManager())
                                                        .scrollToPositionWithOffset(
                                                                position + 1,
                                                                context.findViewById(R.id.toolbar).getHeight());
                                            } else if (imagesView instanceof RecyclerView) {
                                                // This case is when R.id.images in the context's layout is a RecyclerView.
                                                // For RedditGallery, R.id.images is a ViewPager, so this branch is not hit.
                                                // Original logic for this case:
                                                ((LinearLayoutManager)((RecyclerView) imagesView).getLayoutManager())
                                                        .scrollToPositionWithOffset(
                                                                position + 1, // Grid position is 0-indexed for images
                                                                context.findViewById(R.id.toolbar).getHeight());
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

                                                        ((LinearLayoutManager) albumFrag.recyclerView.getLayoutManager())
                                                            .scrollToPositionWithOffset(scrollToRecyclerPosition, offset);
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
                            d.show();
                        }
                    });
        }
    }

    @Override
    public int getItemViewType(int position) {
        // If first or last item is meant to be a "spacer" row, handle that
        if (!paddingBottom && position == 0) {
            return VIEW_TYPE_SPACER;  // top spacer
        } else if (paddingBottom && position == getItemCount() - 1) {
            return VIEW_TYPE_SPACER;  // bottom spacer
        }

        // Real index in images list
        int actualIndex = paddingBottom ? position : position - 1;
        GalleryImage image = images.get(actualIndex);
        if (image.isAnimated()) {
            return VIEW_TYPE_ANIMATED; // MP4 or GIF
        } else {
            return VIEW_TYPE_IMAGE; // normal static image
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SPACER) {
            // Spacer row for top or bottom
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.spacer, parent, false);
            return new SpacerViewHolder(v);
        } else if (viewType == VIEW_TYPE_ANIMATED) {
            // *** HERE is where we load the layout with ExoVideoView, e.g. submission_gifcard_album ***
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.submission_gifcard_album, parent, false);
            return new AnimatedViewHolder(v);
        } else { // VIEW_TYPE_IMAGE
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.album_image, parent, false);
            return new AlbumViewHolder(v);
        }
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;

        // Add scroll listener to manage view transitions
        recyclerView.addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }
        });
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder2, int position) {
        if (holder2 instanceof SpacerViewHolder) {
            // Adjust spacer height to match the toolbar height or user-specified padding
            holder2.itemView.findViewById(R.id.height)
                    .setLayoutParams(new LinearLayout.LayoutParams(
                            holder2.itemView.getWidth(),
                            paddingBottom
                                    ? height
                                    : main.findViewById(R.id.toolbar).getHeight()));
            return;
        }

        // Actual index in "images" list
        final int actualIndex = paddingBottom ? position : position - 1;
        final GalleryImage image = images.get(actualIndex);

        // Add a solid background to prevent bleed-through
        holder2.itemView.setBackgroundColor(android.graphics.Color.BLACK);

        // 1) Animated items
        if (holder2 instanceof AnimatedViewHolder) {
            AnimatedViewHolder holder = (AnimatedViewHolder) holder2;
            final String url = image.getImageUrl();

            // Ensure view is fully opaque to prevent bleed-through
            holder.rootView.setAlpha(1.0f);

            holder.muteButton.setVisibility(View.GONE);
            holder.hqButton.setVisibility(View.GONE);

            // Show play button initially
            if (holder.playButton != null) {
                holder.playButton.setVisibility(View.VISIBLE);
                holder.playButton.setAlpha(0.8f); // Slightly transparent
            }

            // Store the position directly in the holder itself
            holder.position = actualIndex;

            // Use GifUtils to load MP4 or GIF with ExoPlayer and add player listener
            new GifUtils.AsyncLoadGif(
                    main,
                    holder.exoVideoView,
                    holder.loader,
                    null, // placeholder
                    false, // closeIfNull
                    false, // autostart
                    holder.rootView.findViewById(R.id.size),
                    subreddit,
                    submissionTitle
            ).execute(url);

            // Show play button
            if (holder.playButton != null) {
                holder.playButton.setVisibility(View.VISIBLE);
                holder.playButton.setAlpha(0.8f);

                // Make the play button open MediaView instead of playing inline
                holder.playButton.setOnClickListener(v -> {
                    if (SettingValues.image) {
                        Intent intent = new Intent(main, MediaView.class);
                        intent.putExtra(MediaView.EXTRA_URL, url);
                        intent.putExtra(MediaView.SUBREDDIT, subreddit);
                        if (submissionTitle != null) {
                            intent.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
                        }
                        intent.putExtra("index", actualIndex);
                        main.startActivity(intent);
                    } else {
                        LinkUtil.openExternally(url);
                    }
                });
            }

            // Update the ExoVideoView click listener to do the same thing
            holder.exoVideoView.setOnClickListener(v -> {
                if (SettingValues.image) {
                    Intent intent = new Intent(main, MediaView.class);
                    intent.putExtra(MediaView.EXTRA_URL, url);
                    intent.putExtra(MediaView.SUBREDDIT, subreddit);
                    if (submissionTitle != null) {
                        intent.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
                    }
                    intent.putExtra("index", actualIndex);
                    main.startActivity(intent);
                } else {
                    LinkUtil.openExternally(url);
                }
            });

            // Remove any playback state listener since we're not playing inline
            holder.exoVideoView.setOnPlaybackStateChangedListener(null);

            // Overflow menu
            holder.moreButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // If we're in the vertical gallery, call showBottomSheetImage on the activity
                            if (main instanceof RedditGallery) {
                                ((RedditGallery) main).showBottomSheetImage(url, /* isGif= */true, actualIndex);
                            }
                        }
                    }
            );

            // Save button
            holder.saveButton.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (main instanceof RedditGallery) {
                                ((RedditGallery) main).doImageSave(/* isGif= */true, url, actualIndex);
                            }
                        }
                    }
            );

            holder.saveButton.setVisibility(View.GONE);
            holder.moreButton.setVisibility(View.GONE);
            View commentsButton = holder.rootView.findViewById(R.id.comments);
            if (commentsButton != null) {
                commentsButton.setVisibility(View.GONE);
            }

            // Hide the "save" button if user preference is off
            if (!SettingValues.imageDownloadButton) {
                holder.saveButton.setVisibility(View.INVISIBLE);
            }
        } else if (holder2 instanceof AlbumViewHolder) {
            final AlbumViewHolder holder = (AlbumViewHolder) holder2;

            // Ensure view is fully opaque
            holder.itemView.setAlpha(1.0f);

            // Load static image
            ((Reddit) main.getApplicationContext()).getImageLoader()
                    .displayImage(image.getImageUrl(), holder.image, ImageGridAdapter.options);

            // Title + caption hidden by default
            holder.body.setVisibility(View.GONE);
            holder.text.setVisibility(View.GONE);

            // Adjust image layout params to maintain aspect ratio
            if (holder.image.getWidth() == 0) {
                holder.image.setLayoutParams(
                        new LinearLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT));
            } else {
                holder.image.setLayoutParams(
                        new LinearLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                (int) getHeightFromAspectRatio(
                                        image.height, image.width, holder.image.getWidth())));
            }

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

            // Clicking on the static image
            holder.itemView.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (SettingValues.image) {
                                // Open MediaView for a closer look
                                Intent myIntent = new Intent(main, MediaView.class);
                                myIntent.putExtra(MediaView.EXTRA_URL, image.getImageUrl());
                                myIntent.putExtra(MediaView.SUBREDDIT, subreddit);
                                if (submissionTitle != null) {
                                    myIntent.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
                                }
                                myIntent.putExtra("index", actualIndex);
                                main.startActivity(myIntent);
                            } else {
                                // Or open in browser if user has that setting
                                LinkUtil.openExternally(image.getImageUrl());
                            }
                        }
                    });
        }
    }

    @Override
    public int getItemCount() {
        // +1 for spacer if paddingBottom is used
        return (images == null) ? 0 : (images.size() + 1);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof AnimatedViewHolder) {
            AnimatedViewHolder animatedHolder = (AnimatedViewHolder) holder;
            // Just stop the player but don't release it to keep the thumbnail
            if (animatedHolder.exoVideoView != null) {
                animatedHolder.exoVideoView.pause();
            }
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof AnimatedViewHolder) {
            AnimatedViewHolder animatedHolder = (AnimatedViewHolder) holder;
            int position = animatedHolder.position;

            if (position >= 0 && position < images.size()) {
                final GalleryImage image = images.get(position);
                final String url = image.getImageUrl();

                // Reload the video preview if needed
                if (!animatedHolder.exoVideoView.isPlaying()) {
                    new GifUtils.AsyncLoadGif(
                            main,
                            animatedHolder.exoVideoView,
                            animatedHolder.loader,
                            null, // placeholder
                            true, // closeIfNull
                            false, // autostart
                            animatedHolder.rootView.findViewById(R.id.size),
                            subreddit,
                            submissionTitle
                    ).execute(url);
                }
            }
        }
    }

    /**
     * Utility to calculate image height from aspect ratio
     */
    public double getHeightFromAspectRatio(int imageHeight, int imageWidth, int viewWidth) {
        double ratio = (double) imageHeight / (double) imageWidth;
        return (viewWidth * ratio);
    }

    /**
     * ViewHolder for top/bottom spacer
     */
    public static class SpacerViewHolder extends RecyclerView.ViewHolder {
        public SpacerViewHolder(View itemView) {
            super(itemView);
        }
    }

    /**
     * ViewHolder for static images
     */
    public static class AlbumViewHolder extends RecyclerView.ViewHolder {
        final SpoilerRobotoTextView text;
        final SpoilerRobotoTextView body;
        final ImageView image;

        public AlbumViewHolder(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.imagetitle);
            body = itemView.findViewById(R.id.imageCaption);
            image = itemView.findViewById(R.id.image);
        }
    }

    /**
     * ViewHolder for animated items (MP4/GIF)
     */
    public static class AnimatedViewHolder extends RecyclerView.ViewHolder {
        final View rootView;
        final ProgressBar loader;
        final me.edgan.redditslide.Views.ExoVideoView exoVideoView;
        final View moreButton;
        final View saveButton;
        final View muteButton;
        final View hqButton;
        final ImageView playButton;
        int position = -1;

        public AnimatedViewHolder(View itemView) {
            super(itemView);
            this.rootView = itemView;
            this.loader = itemView.findViewById(R.id.gifprogress);
            this.exoVideoView = itemView.findViewById(R.id.gif);
            this.moreButton = itemView.findViewById(R.id.more);
            this.saveButton = itemView.findViewById(R.id.save);
            this.muteButton = itemView.findViewById(R.id.mute);
            this.hqButton = itemView.findViewById(R.id.hq);
            this.playButton = itemView.findViewById(R.id.playbutton);

            // Add solid background to prevent transparency issues
            itemView.setBackgroundColor(android.graphics.Color.BLACK);
        }
    }
}
