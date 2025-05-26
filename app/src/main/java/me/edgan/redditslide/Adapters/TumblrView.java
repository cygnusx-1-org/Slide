package me.edgan.redditslide.Adapters;

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

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.devspark.robototextview.RobotoTypefaces;

import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Activities.Tumblr;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Tumblr.Photo;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.SubmissionParser;
import me.edgan.redditslide.util.GifDrawable;
import me.edgan.redditslide.util.GifUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.io.File;

import android.graphics.Movie;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

// Import for NavigationUtils
import me.edgan.redditslide.ForceTouch.util.NavigationUtils;

public class TumblrView extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<Photo> users;

    private final Activity main;
    private static final String TAG = "TumblrView";

    public boolean paddingBottom;
    public int height;
    public String subreddit;

    private static final int VIEW_TYPE_IMAGE = 1;
    private static final int VIEW_TYPE_SPACER = 6;
    private static final int VIEW_TYPE_GIF = 2;

    public TumblrView(
            final Activity context, final List<Photo> users, int height, String subreddit) {

        this.height = height;
        main = context;
        this.users = users;
        this.subreddit = subreddit;

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
                                    gridview.setAdapter(new ImageGridAdapterTumblr(context, users));

                                    final AlertDialog.Builder builder =
                                            new AlertDialog.Builder(context).setView(body);
                                    final Dialog d = builder.create();
                                    gridview.setOnItemClickListener(
                                            new AdapterView.OnItemClickListener() {
                                                public void onItemClick(
                                                        AdapterView<?> parent,
                                                        View v,
                                                        int position,
                                                        long id) {
                                                    if (context instanceof Tumblr) {
                                                        ((LinearLayoutManager)
                                                                        ((Tumblr) context)
                                                                                .album.album
                                                                                        .recyclerView
                                                                                        .getLayoutManager())
                                                                .scrollToPositionWithOffset(
                                                                        position + 1,
                                                                        context.findViewById(
                                                                                        R
                                                                                                .id
                                                                                                .toolbar)
                                                                                .getHeight());
                                                    } else {
                                                        ((LinearLayoutManager)
                                                                        ((RecyclerView)
                                                                                        context
                                                                                                .findViewById(
                                                                                                        R
                                                                                                                .id
                                                                                                                .images))
                                                                                .getLayoutManager())
                                                                .scrollToPositionWithOffset(
                                                                        position + 1,
                                                                        context.findViewById(
                                                                                        R.id
                                                                                                .toolbar)
                                                                                .getHeight());
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
            View v =
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.album_image, parent, false);
            return new AlbumViewHolder(v);
        } else if (viewType == VIEW_TYPE_GIF) {
            View v =
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.list_item_tumblr_gif, parent, false);
            return new GifViewHolder(v);
        } else {
            View v =
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.spacer, parent, false);
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
        } else {
            int dataPosition = paddingBottom ? position : position -1;
            if (dataPosition < 0 || dataPosition >= users.size()) {
                return VIEW_TYPE_SPACER;
            }
            Photo photo = users.get(dataPosition);
            try {
                if (ContentType.isGif(new URI(photo.getOriginalSize().getUrl()))) {
                    return VIEW_TYPE_GIF;
                } else {
                    return VIEW_TYPE_IMAGE;
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return VIEW_TYPE_IMAGE;
            }
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        if (holder instanceof AlbumViewHolder) {
            final int position = paddingBottom ? i : i - 1;
            if (position < 0 || position >= users.size()) return;

            AlbumViewHolder albumHolder = (AlbumViewHolder) holder;
            final Photo user = users.get(position);

            ((Reddit) main.getApplicationContext())
                    .getImageLoader()
                    .displayImage(
                            user.getOriginalSize().getUrl(),
                            albumHolder.image,
                            ImageGridAdapter.options);

            if (albumHolder.body != null) {
                albumHolder.body.setVisibility(View.VISIBLE);
            }
            if (albumHolder.text != null) {
                albumHolder.text.setVisibility(View.VISIBLE);
            }
            View imageView = albumHolder.image;

            if (user.getOriginalSize().getWidth() > 0 && user.getOriginalSize().getHeight() > 0) {
                 if (imageView.getWidth() == 0) {
                    albumHolder.image.setLayoutParams(
                        new LinearLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT));
                } else {
                    albumHolder.image.setLayoutParams(
                        new LinearLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                (int) getHeightFromAspectRatio(
                                        user.getOriginalSize().getHeight(),
                                        user.getOriginalSize().getWidth(),
                                        imageView.getWidth())));
                }
            } else {
                 albumHolder.image.setLayoutParams(
                        new LinearLayout.LayoutParams(
                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT));
            }

            if (albumHolder.body != null) {
                int type =
                        new FontPreferences(albumHolder.body.getContext())
                                .getFontTypeComment()
                                .getTypeface();
                Typeface typeface;
                if (type >= 0) {
                    typeface = RobotoTypefaces.obtainTypeface(albumHolder.body.getContext(), type);
                } else {
                    typeface = Typeface.DEFAULT;
                }
                albumHolder.body.setTypeface(typeface);
            }
            if (albumHolder.text != null) {
                int type =
                        new FontPreferences(albumHolder.text.getContext())
                                .getFontTypeTitle()
                                .getTypeface();
                Typeface typeface;
                if (type >= 0) {
                    typeface = RobotoTypefaces.obtainTypeface(albumHolder.text.getContext(), type);
                } else {
                    typeface = Typeface.DEFAULT;
                }
                albumHolder.text.setTypeface(typeface);
            }

            if (user.getCaption() != null) {
                List<String> textBlocks = SubmissionParser.getBlocks(user.getCaption());
                String captionText = textBlocks.get(0).trim();
                if (albumHolder.body != null) {
                    LinkUtil.setTextWithLinks(captionText, albumHolder.body);
                }
                if (albumHolder.text != null) {
                    albumHolder.text.setVisibility(View.GONE);
                }

                boolean bodyIsEmpty = true;
                if (albumHolder.body != null && albumHolder.body.getText() != null) {
                    bodyIsEmpty = albumHolder.body.getText().toString().isEmpty();
                }

                if (bodyIsEmpty) {
                    if (albumHolder.body != null) {
                        albumHolder.body.setVisibility(View.GONE);
                    }
                } else {
                    if (albumHolder.body != null) {
                        albumHolder.body.setVisibility(View.VISIBLE);
                    }
                }
            } else {
                if (albumHolder.text != null) {
                    albumHolder.text.setVisibility(View.GONE);
                }
                if (albumHolder.body != null) {
                    albumHolder.body.setVisibility(View.GONE);
                }
            }

            albumHolder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (SettingValues.image) {
                        Intent myIntent = new Intent(main, MediaView.class);
                        myIntent.putExtra(MediaView.SUBREDDIT, subreddit);
                        myIntent.putExtra(MediaView.EXTRA_URL, user.getOriginalSize().getUrl());
                        if (((Tumblr)main).submissionTitle != null) {
                            myIntent.putExtra(MediaView.EXTRA_SUBMISSION_TITLE, ((Tumblr)main).submissionTitle);
                        }
                        main.startActivity(myIntent);
                    } else {
                        LinkUtil.openExternally(user.getOriginalSize().getUrl());
                    }
                }
            });

        } else if (holder instanceof GifViewHolder) {
            final int position = paddingBottom ? i : i - 1;
            if (position < 0 || position >= users.size()) return;

            final GifViewHolder gifHolder = (GifViewHolder) holder;
            final Photo user = users.get(position);
            final String gifUrl = user.getOriginalSize().getUrl();

            // Tag the itemView with the URL to check in callbacks
            gifHolder.itemView.setTag(gifUrl);

            gifHolder.gifLoader.setVisibility(View.VISIBLE);
            gifHolder.gifDisplay.setVisibility(View.GONE);
            gifHolder.gifDisplay.setImageDrawable(null); // Clear previous drawable
            if (gifHolder.gifCaption != null) gifHolder.gifCaption.setVisibility(View.GONE);

            GifUtils.downloadGif(gifUrl, new GifUtils.GifDownloadCallback() {
                @Override
                public void onGifDownloaded(File gifFile) {
                    // Check if the ViewHolder is still bound to the same URL
                    if (!gifUrl.equals(gifHolder.itemView.getTag()) || main == null || main.isFinishing()) {
                        return;
                    }
                    main.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Double check tag inside UI thread as well, just in case
                            if (!gifUrl.equals(gifHolder.itemView.getTag())) {
                                return;
                            }
                            gifHolder.gifLoader.setVisibility(View.GONE);
                            Movie movie = Movie.decodeFile(gifFile.getAbsolutePath());
                            if (movie != null) {
                                GifDrawable gifDrawable = new GifDrawable(movie, new Drawable.Callback() {
                                    @Override
                                    public void invalidateDrawable(@NonNull Drawable who) {
                                        gifHolder.gifDisplay.invalidate();
                                    }

                                    @Override
                                    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                                        gifHolder.gifDisplay.postDelayed(what, when - SystemClock.uptimeMillis());
                                    }

                                    @Override
                                    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                                        gifHolder.gifDisplay.removeCallbacks(what);
                                    }
                                });
                                gifHolder.gifDisplay.setImageDrawable(gifDrawable);
                                gifHolder.gifDisplay.setVisibility(View.VISIBLE);
                                gifDrawable.start();

                                if (gifHolder.gifCaption != null && user.getCaption() != null) {
                                    List<String> textBlocks = SubmissionParser.getBlocks(user.getCaption());
                                    String captionText = textBlocks.get(0).trim();
                                    if (!captionText.isEmpty()){
                                        LinkUtil.setTextWithLinks(captionText, gifHolder.gifCaption);
                                        gifHolder.gifCaption.setVisibility(View.VISIBLE);
                                    }
                                }

                            } else {
                                Log.e(TAG, "Failed to decode GIF: " + gifUrl);
                                if (gifHolder.gifCaption != null) {
                                     LinkUtil.setTextWithLinks("Failed to load GIF.", gifHolder.gifCaption);
                                     gifHolder.gifCaption.setVisibility(View.VISIBLE);
                                }
                            }
                        }
                    });
                }

                @Override
                public void onGifDownloadFailed(Exception e) {
                    // Check if the ViewHolder is still bound to the same URL
                    if (!gifUrl.equals(gifHolder.itemView.getTag()) || main == null || main.isFinishing()) {
                        return;
                    }
                    main.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Double check tag inside UI thread as well
                            if (!gifUrl.equals(gifHolder.itemView.getTag())) {
                                return;
                            }
                            gifHolder.gifLoader.setVisibility(View.GONE);
                            Log.e(TAG, "Failed to download GIF: " + gifUrl, e);
                             if (gifHolder.gifCaption != null) {
                                 LinkUtil.setTextWithLinks("Failed to download GIF.", gifHolder.gifCaption);
                                 gifHolder.gifCaption.setVisibility(View.VISIBLE);
                             }
                        }
                    });
                }
            }, main, ((Tumblr)main).submissionTitle);

        } else if (holder instanceof SpacerViewHolder) {
            View v = ((SpacerViewHolder) holder).itemView;
            if (i == 0) {
                v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
            } else {
                v.setLayoutParams(
                        new RecyclerView.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                NavigationUtils.getNavBarHeight(main)));
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof GifViewHolder) {
            GifViewHolder gifHolder = (GifViewHolder) holder;
            Drawable drawable = gifHolder.gifDisplay.getDrawable();
            if (drawable instanceof GifDrawable) {
                GifDrawable gifDrawable = (GifDrawable) drawable;
                gifDrawable.setCallback(null);
                gifDrawable.stop();
            }
            gifHolder.gifDisplay.setImageDrawable(null);
            gifHolder.itemView.setTag(null);
        }
    }

    @Override
    public int getItemCount() {
        return users == null ? 0 : users.size() + 1;
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
            text = itemView.findViewById(R.id.text);
            body = itemView.findViewById(R.id.body);
            image = itemView.findViewById(R.id.image);
        }
    }

    public static class GifViewHolder extends RecyclerView.ViewHolder {
        final ImageView gifDisplay;
        final ProgressBar gifLoader;
        final SpoilerRobotoTextView gifCaption;

        public GifViewHolder(View itemView) {
            super(itemView);
            gifDisplay = itemView.findViewById(R.id.gif_display);
            gifLoader = itemView.findViewById(R.id.gif_loader);
            gifCaption = itemView.findViewById(R.id.gif_caption);
        }
    }
}
