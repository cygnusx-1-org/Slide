package me.edgan.redditslide.Adapters;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
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

import com.devspark.robototextview.RobotoTypefaces;

import me.edgan.redditslide.Activities.Album;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.ImgurAlbum.Image;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.ExoVideoView;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.SubmissionParser;

import java.util.List;

public class AlbumView extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_IMAGE = 1;
    private static final int VIEW_TYPE_SPACER = 6;
    private static final int VIEW_TYPE_ANIMATED = 2;

    private final List<Image> users;

    private final Activity main;

    public boolean paddingBottom;
    public int height;
    public String subreddit;
    private final String submissionTitle;

    public AlbumView(
            final Activity context,
            final List<Image> users,
            int height,
            String subreddit,
            String SubmissionTitle) {

        this.height = height;
        main = context;
        this.users = users;
        this.subreddit = subreddit;
        this.submissionTitle = SubmissionTitle;

        paddingBottom = main.findViewById(R.id.toolbar) == null;
        if (context.findViewById(R.id.grid) != null)
            context.findViewById(R.id.grid)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    LayoutInflater l = context.getLayoutInflater();
                                    View body = l.inflate(R.layout.album_grid_dialog, null, false);
                                    GridView gridview = body.findViewById(R.id.images);
                                    gridview.setAdapter(new ImageGridAdapter(context, users));

                                    final AlertDialog.Builder builder =
                                            new AlertDialog.Builder(context).setView(body);
                                    final Dialog d = builder.create();
                                    gridview.setOnItemClickListener(
                                            new AdapterView.OnItemClickListener() {
                                                public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                                                    if (context instanceof Album) {
                                                        ((LinearLayoutManager) ((Album) context).album.album.recyclerView.getLayoutManager())
                                                                .scrollToPositionWithOffset(position + 1, context.findViewById(R.id.toolbar).getHeight());

                                                    } else {
                                                        ((LinearLayoutManager) ((RecyclerView) context.findViewById(R.id.images)).getLayoutManager())
                                                                .scrollToPositionWithOffset(position + 1, context.findViewById(R.id.toolbar).getHeight());
                                                    }
                                                    d.dismiss();
                                                }
                                            });
                                    d.show();
                                }
                            });
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_IMAGE) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.album_image, parent, false);
            return new AlbumViewHolder(v);
        } else if (viewType == VIEW_TYPE_ANIMATED) {
            // *** HERE is where we load the layout with ExoVideoView, e.g. submission_gifcard_album ***
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.submission_gifcard_album, parent, false);
            return new AnimatedViewHolder(v);
        } else {
            View v =
                    LayoutInflater.from(parent.getContext()).inflate(R.layout.spacer, parent, false);
            return new SpacerViewHolder(v);
        }
    }

    public double getHeightFromAspectRatio(int imageHeight, int imageWidth, int viewWidth) {
        double ratio = (double) imageHeight / (double) imageWidth;
        return (viewWidth * ratio);
    }

    @Override
    public int getItemViewType(int position) {
        if (!paddingBottom && position == 0) {
            return VIEW_TYPE_SPACER;
        } else if (paddingBottom && position == getItemCount() - 1) {
            return VIEW_TYPE_SPACER;
        }
        // Real index in images list
        int actualIndex = paddingBottom ? position : position - 1;
        Image image = users.get(actualIndex);
        if (image.isAnimated()) {
            return VIEW_TYPE_ANIMATED; // MP4 or GIF
        } else {
            return 1;
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder2, int i) {
        if (holder2 instanceof AlbumViewHolder) {
            final int position = paddingBottom ? i : i - 1;

            AlbumViewHolder holder = (AlbumViewHolder) holder2;

            final Image user = users.get(position);
            ((Reddit) main.getApplicationContext()).getImageLoader().displayImage(user.getImageUrl(), holder.image, ImageGridAdapter.options);
            holder.body.setVisibility(View.VISIBLE);
            holder.text.setVisibility(View.VISIBLE);
            View imageView = holder.image;
            if (imageView.getWidth() == 0) {
                holder.image.setLayoutParams(
                        new LinearLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT));
            } else {
                holder.image.setLayoutParams(new LinearLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                (int)
                                        getHeightFromAspectRatio(
                                                user.getHeight(),
                                                user.getWidth(),
                                                imageView.getWidth())));
            }
            {
                int type = new FontPreferences(holder.body.getContext()).getFontTypeComment().getTypeface();
                Typeface typeface;
                if (type >= 0) {
                    typeface = RobotoTypefaces.obtainTypeface(holder.body.getContext(), type);
                } else {
                    typeface = Typeface.DEFAULT;
                }
                holder.body.setTypeface(typeface);
            }
            {
                int type = new FontPreferences(holder.body.getContext()).getFontTypeTitle().getTypeface();
                Typeface typeface;
                if (type >= 0) {
                    typeface = RobotoTypefaces.obtainTypeface(holder.body.getContext(), type);
                } else {
                    typeface = Typeface.DEFAULT;
                }
                holder.text.setTypeface(typeface);
            }
            {
                if (user.getTitle() != null) {
                    List<String> text = SubmissionParser.getBlocks(user.getTitle());
                    LinkUtil.setTextWithLinks(text.get(0), holder.text);
                    if (holder.text.getText().toString().isEmpty()) {
                        holder.text.setVisibility(View.GONE);
                    }

                } else {
                    holder.text.setVisibility(View.GONE);
                }
            }
            {
                if (user.getDescription() != null) {
                    List<String> text = SubmissionParser.getBlocks(user.getDescription());
                    LinkUtil.setTextWithLinks(text.get(0), holder.body);
                    if (holder.body.getText().toString().isEmpty()) {
                        holder.body.setVisibility(View.GONE);
                    }
                } else {
                    holder.body.setVisibility(View.GONE);
                }
            }

            View.OnClickListener onGifImageClickListener =
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (SettingValues.image && !user.isAnimated()
                                    || SettingValues.gif && user.isAnimated()) {
                                Intent myIntent = new Intent(main, MediaView.class);
                                myIntent.putExtra(MediaView.EXTRA_URL, user.getImageUrl());
                                myIntent.putExtra(MediaView.SUBREDDIT, subreddit);
                                if (submissionTitle != null) {
                                    myIntent.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
                                }
                                main.startActivity(myIntent);
                            } else {
                                LinkUtil.openExternally(user.getImageUrl());
                            }
                        }
                    };

            holder.itemView.setOnClickListener(onGifImageClickListener);
        } else if (holder2 instanceof SpacerViewHolder) {
            holder2.itemView
                    .findViewById(R.id.height)
                    .setLayoutParams(new LinearLayout.LayoutParams(holder2.itemView.getWidth(), paddingBottom ? height : main.findViewById(R.id.toolbar).getHeight()));
        } else if (holder2 instanceof AnimatedViewHolder) {
            AnimatedViewHolder holder = (AnimatedViewHolder) holder2;
            final int position = paddingBottom ? i : i - 1;
            final Image user = users.get(position);
            final String url = user.getImageUrl();

            // Reset view state to prevent flickering
            holder.rootView.setAlpha(1.0f);
            holder.exoVideoView.setVisibility(View.VISIBLE);

            holder.saveButton.setVisibility(View.GONE);
            holder.moreButton.setVisibility(View.GONE);
            View commentsButton = holder.rootView.findViewById(R.id.comments);
            if (commentsButton != null) {
                commentsButton.setVisibility(View.GONE);
            }
            holder.muteButton.setVisibility(View.GONE);
            holder.hqButton.setVisibility(View.GONE);

            // Show play button
            if (holder.playButton != null) {
                holder.playButton.setVisibility(View.VISIBLE);
                holder.playButton.setAlpha(0.8f);

                // Make the play button open MediaView
                holder.playButton.setOnClickListener(v -> {
                    if (SettingValues.image) {
                        Intent intent = new Intent(main, MediaView.class);
                        intent.putExtra(MediaView.EXTRA_URL, url);
                        intent.putExtra(MediaView.SUBREDDIT, subreddit);
                        if (submissionTitle != null) {
                            intent.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
                        }
                        intent.putExtra("index", position);
                        main.startActivity(intent);
                    } else {
                        LinkUtil.openExternally(url);
                    }
                });
            }

            // Store the position directly in the holder itself
            holder.position = position;

            new GifUtils.AsyncLoadGif(
                    main,
                    holder.exoVideoView,
                    holder.loader,
                    null, // placeholder
                    true, // closeIfNull
                    false,  // autostart
                    holder.rootView.findViewById(R.id.size),
                    subreddit,
                    submissionTitle
            ).execute(url);

            // If user taps the main video area -> open MediaView or open externally, up to you
            holder.exoVideoView.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (SettingValues.image) {
                                Intent intent = new Intent(main, MediaView.class);
                                intent.putExtra(MediaView.EXTRA_URL, url);
                                intent.putExtra(MediaView.SUBREDDIT, subreddit);
                                if (submissionTitle != null) {
                                    intent.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
                                }
                                intent.putExtra("index", position);
                                main.startActivity(intent);
                            } else {
                                LinkUtil.openExternally(url);
                            }
                        }
                    }
            );
        }
    }

    @Override
    public int getItemCount() {
        return users == null ? 0 : users.size() + 1;
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

            if (position >= 0 && position < users.size()) {
                final Image user = users.get(position);
                final String url = user.getImageUrl();

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

    public static class SpacerViewHolder extends RecyclerView.ViewHolder {
        public SpacerViewHolder(View itemView) {
            super(itemView);
        }
    }

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
        final ExoVideoView exoVideoView;
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
            itemView.setBackgroundColor(Color.BLACK);
        }
    }
}
