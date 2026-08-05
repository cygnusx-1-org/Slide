package me.edgan.redditslide.Adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Movie;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.devspark.robototextview.RobotoTypefaces;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.Activities.Tumblr;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Tumblr.Photo;
import me.edgan.redditslide.Tumblr.PhotoSize;
import me.edgan.redditslide.Views.MaxHeightImageView;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.GifDrawable;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MediaRowLoadState;
import me.edgan.redditslide.util.SubmissionParser;

public class TumblrView extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<Photo> users;

    private final Activity main;
    private static final String TAG = "TumblrView";

    /**
     * Whether the list leads with a spacer row clearing an overlaying toolbar. False in Shadowbox,
     * where there is no toolbar over the list and no end-of-list spacer either — BaseAlbumFull pads
     * the RecyclerView by its info panel's measured height instead, which is the only place that
     * height is known.
     */
    public boolean hasToolbarSpacer;
    @Nullable public String subreddit;

    /**
     * Name for saved files and for MediaView's title, supplied by the host rather than read back off
     * it. This adapter also runs inside Shadowbox (TumblrFull), where the host activity is not the
     * Tumblr activity, and casting to it to reach {@code submissionTitle} threw ClassCastException.
     */
    @Nullable private final String submissionTitle;

    // Package-private, not private, so the row-mapping tests can name them rather than assert on
    // bare integers. Same visibility as VerticalMediaAdapter's copies.
    static final int VIEW_TYPE_IMAGE = 1;
    static final int VIEW_TYPE_SPACER = 6;
    static final int VIEW_TYPE_GIF = 2;

    /**
     * @param subreddit where the album came from, for the save paths
     * @param submissionTitle name for saved files; see the field
     */
    public TumblrView(
            final Activity context,
            final List<Photo> users,
            @Nullable String subreddit,
            @Nullable String submissionTitle) {

        main = context;
        this.users = users;
        this.subreddit = subreddit;
        this.submissionTitle = submissionTitle;

        hasToolbarSpacer = main.findViewById(R.id.toolbar) != null;
        if (context.findViewById(R.id.grid) != null)
            context.findViewById(R.id.grid)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    LayoutInflater l = context.getLayoutInflater();
                                    View body = l.inflate(R.layout.album_grid_dialog, null, false);
                                    GridView gridview = body.findViewById(R.id.images);
                                    gridview.setAdapter(new ImageGridAdapter(context, users, true));

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
                                                    final int offset =
                                                            LayoutUtils.getToolbarOffset(context);
                                                    final RecyclerView.LayoutManager lm =
                                                            context instanceof Tumblr
                                                                    ? ((Tumblr) context)
                                                                            .album.album
                                                                            .recyclerView
                                                                            .getLayoutManager()
                                                                    : ((RecyclerView)
                                                                                    context
                                                                                            .findViewById(
                                                                                                    R.id
                                                                                                            .images))
                                                                            .getLayoutManager();
                                                    if (lm != null) {
                                                        ((LinearLayoutManager) lm)
                                                                .scrollToPositionWithOffset(
                                                                        position + 1, offset);
                                                    }
                                                    d.dismiss();
                                                }
                                            });
                                    DialogUtil.matchDialogToCardBackground(d);
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

    @Override
    public int getItemViewType(int position) {
        if (hasToolbarSpacer && position == 0) {
            return VIEW_TYPE_SPACER;
        } else {
            int dataPosition = hasToolbarSpacer ? position - 1 : position;
            if (dataPosition < 0 || dataPosition >= users.size()) {
                return VIEW_TYPE_SPACER;
            }
            Photo photo = users.get(dataPosition);
            // A null photo is possible in its own right: Jackson leaves one in the list for a null
            // element in the photos array.
            final String photoUrl = photo == null ? null : photo.getOriginalUrl();
            if (photoUrl == null) {
                // No size means no url to classify, and new URI(null) throws NPE rather than the
                // URISyntaxException the catch below expects.
                return VIEW_TYPE_IMAGE;
            }
            try {
                if (ContentType.isGif(new URI(photoUrl))) {
                    return VIEW_TYPE_GIF;
                } else {
                    return VIEW_TYPE_IMAGE;
                }
            } catch (URISyntaxException e) {
                LogUtil.e(e, "TumblrView.URI failed");
                return VIEW_TYPE_IMAGE;
            }
        }
    }

    @Override
    // The click listener captures the bound Photo (stable data), not the raw position, so
    // there is no stale-position lookup to convert to getBindingAdapterPosition().
    @SuppressLint("RecyclerView")
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        if (holder instanceof AlbumViewHolder) {
            final int position = hasToolbarSpacer ? i - 1 : i;
            if (position < 0 || position >= users.size()) return;

            AlbumViewHolder albumHolder = (AlbumViewHolder) holder;
            final Photo user = users.get(position);
            // A photo whose JSON carried no original_size has nothing to show and nothing for a tap
            // to open; getOriginalSize() would be null and every use of it a crash. Same for the
            // photo itself, which Jackson leaves null for a null element in the photos array.
            final PhotoSize imageSize =
                    (user != null && user.hasOriginalSize()) ? user.getOriginalSize() : null;
            final String imageUrl = imageSize == null ? null : imageSize.getUrl();
            final boolean playable = imageUrl != null;

            if (imageUrl != null) {
                ((Reddit) main.getApplicationContext())
                        .getImageLoader()
                        .displayImage(imageUrl, albumHolder.image, ImageGridAdapter.options);
            } else {
                ((Reddit) main.getApplicationContext())
                        .getImageLoader()
                        .cancelDisplayTask(albumHolder.image);
                albumHolder.image.setImageDrawable(null);
            }

            // album_image.xml's @id/imagetitle is left alone on purpose: a Tumblr photo carries a
            // caption but no separate title, and the layout hides that view by default.

            // Reserve the row's height from the size Tumblr reported so that loading the bitmap
            // never resizes the row. Tumblr may omit either dimension, hence the null checks; the
            // 0 reset matters because the aspect ratio survives recycling.
            final Integer imageWidth = imageSize == null ? null : imageSize.getWidth();
            final Integer imageHeight = imageSize == null ? null : imageSize.getHeight();
            albumHolder.image.setAspectRatio(
                    (imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0)
                            ? (double) imageHeight / (double) imageWidth
                            : 0);
            albumHolder.image.setLayoutParams(
                    new LinearLayout.LayoutParams(
                            RelativeLayout.LayoutParams.MATCH_PARENT,
                            RelativeLayout.LayoutParams.WRAP_CONTENT));

            {
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

            if (user != null && user.getCaption() != null) {
                List<String> textBlocks = SubmissionParser.getBlocks(user.getCaption());
                String captionText = textBlocks.isEmpty() ? "" : textBlocks.get(0).trim();
                LinkUtil.setTextWithLinks(captionText, albumHolder.body);

                final CharSequence body = albumHolder.body.getText();
                albumHolder.body.setVisibility(
                        (body == null || body.toString().isEmpty()) ? View.GONE : View.VISIBLE);
            } else {
                albumHolder.body.setVisibility(View.GONE);
            }

            albumHolder.itemView.setOnClickListener(!playable ? null : new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (imageUrl == null) {
                        return;
                    }

                    if (SettingValues.image) {
                        Intent myIntent = new Intent(main, MediaView.class);
                        myIntent.putExtra(MediaView.SUBREDDIT, subreddit);
                        myIntent.putExtra(MediaView.EXTRA_URL, imageUrl);
                        if (submissionTitle != null) {
                            myIntent.putExtra(MediaView.EXTRA_SUBMISSION_TITLE, submissionTitle);
                        }
                        main.startActivity(myIntent);
                    } else {
                        LinkUtil.openExternally(imageUrl);
                    }
                }
            });
            // After setOnClickListener, not before: that method turns clickable back on when it is
            // handed a null listener, so setting this first would leave an inert row still clickable.
            albumHolder.itemView.setClickable(playable);

        } else if (holder instanceof GifViewHolder) {
            final int position = hasToolbarSpacer ? i - 1 : i;
            if (position < 0 || position >= users.size()) return;

            final GifViewHolder gifHolder = (GifViewHolder) holder;
            final Photo user = users.get(position);
            if (user == null || !user.hasOriginalSize()) {
                // Defensive only: getItemViewType sends a photo with no url — or no photo at all —
                // to VIEW_TYPE_IMAGE, and hasOriginalSize() is the exact complement of the
                // getOriginalUrl() null it tests there, so no GIF row reaches this. Kept in case
                // that routing changes.
                //
                // Nothing to load. Drop the tag so a download still in flight for the photo this
                // holder showed before cannot recognise it as its own and write a GIF back into a
                // row that has none, and clear the caption that came with it. released() goes with
                // the collapse: leaving a url recorded here would make the next bind of that same
                // url skip the reserve and load that un-collapse the row.
                gifHolder.itemView.setTag(null);
                gifHolder.gifCaption.setVisibility(View.GONE);
                collapseRow(gifHolder);
                gifHolder.loadState.released();
                return;
            }
            final PhotoSize size = user.getOriginalSize();
            final String gifUrl = size == null ? null : size.getUrl();
            if (gifUrl == null) {
                gifHolder.itemView.setTag(null);
                gifHolder.gifCaption.setVisibility(View.GONE);
                collapseRow(gifHolder);
                gifHolder.loadState.released();
                return;
            }

            // Tag the itemView with the URL to check in callbacks
            gifHolder.itemView.setTag(gifUrl);

            // Load only what this row is not already showing; see MediaRowLoadState. Recycling drops
            // the drawable and calls released(), so a reused row loads again. Everything that touches
            // the views belongs inside the guard: reserving or resetting outside it would disturb a
            // row that is already showing a decoded GIF, or one collapsed by a failure.
            if (gifHolder.loadState.shouldLoad(gifUrl)) {
                gifHolder.loadState.loadStarted(gifUrl);
                reserveSlot(
                        gifHolder,
                        size == null ? null : size.getWidth(),
                        size == null ? null : size.getHeight());
                gifHolder.gifLoader.setVisibility(View.VISIBLE);
                gifHolder.gifDisplay.setImageDrawable(null); // Clear previous drawable
                gifHolder.gifCaption.setVisibility(View.GONE);
                startGifLoad(gifHolder, user, gifUrl);
            }

        } else if (holder instanceof SpacerViewHolder) {
            // Leading spacer only, and from resources rather than the toolbar: the height the host
            // measured off it was 0 until the toolbar had laid out, which tucked the first row
            // underneath it. This is the dimen fragment_verticalalbum.xml sets that toolbar to.
            ((SpacerViewHolder) holder)
                    .itemView.setLayoutParams(
                            new RecyclerView.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    main.getResources()
                                            .getDimensionPixelSize(
                                                    R.dimen.standard_toolbar_height)));
        }
    }

    /** Downloads and decodes the GIF for a row whose slot has already been reserved. */
    private void startGifLoad(
            final GifViewHolder gifHolder, final Photo user, final String gifUrl) {
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
                        if (movie == null) {
                            Log.e(TAG, "Failed to decode GIF: " + gifUrl);
                            onGifLoadFailed(
                                    gifHolder, gifUrl, main.getString(R.string.gif_load_failed));
                            return;
                        }
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

                        if (user.getCaption() != null) {
                            List<String> textBlocks = SubmissionParser.getBlocks(user.getCaption());
                            String captionText = textBlocks.isEmpty() ? "" : textBlocks.get(0).trim();
                            if (!captionText.isEmpty()) {
                                LinkUtil.setTextWithLinks(captionText, gifHolder.gifCaption);
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
                        Log.e(TAG, "Failed to download GIF: " + gifUrl, e);
                        // collapseRow, via onGifLoadFailed, owns hiding the spinner here.
                        onGifLoadFailed(
                                gifHolder, gifUrl, main.getString(R.string.gif_download_failed));
                    }
                });
            }
        }, main, submissionTitle);
    }

    /**
     * Shared tail of both failure paths: lets the load be retried once, gives back the height the
     * slot was holding so the row collapses, and shows why in the caption.
     */
    static void onGifLoadFailed(
            final GifViewHolder gifHolder, final String gifUrl, final String message) {
        gifHolder.loadState.loadFailed(gifUrl);
        collapseRow(gifHolder);
        LinkUtil.setTextWithLinks(message, gifHolder.gifCaption);
        gifHolder.gifCaption.setVisibility(View.VISIBLE);
    }

    /**
     * Reserves the row's height from the size Tumblr reported and keeps the view visible while the
     * GIF loads, so decoding it fills a slot that is already the right size instead of growing the
     * row. Tumblr may omit either dimension, hence the null checks; the 0 reset matters because the
     * aspect ratio survives recycling.
     */
    static void reserveSlot(
            final GifViewHolder gifHolder,
            final @Nullable Integer width,
            final @Nullable Integer height) {
        gifHolder.gifDisplay.setAspectRatio(
                (width != null && height != null && width > 0 && height > 0)
                        ? (double) height / (double) width
                        : 0);
        gifHolder.gifDisplay.setVisibility(View.VISIBLE);
    }

    /**
     * Gives back the height {@link #reserveSlot} took for a GIF that is not going to arrive, so the
     * row is its caption rather than a full-size empty box, and stops the spinner that was waiting
     * over it.
     */
    static void collapseRow(final GifViewHolder gifHolder) {
        gifHolder.gifDisplay.setAspectRatio(0);
        gifHolder.gifDisplay.setVisibility(View.GONE);
        gifHolder.gifLoader.setVisibility(View.GONE);
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
            gifHolder.loadState.released();
        }
    }

    @Override
    public int getItemCount() {
        if (users == null) {
            return 0;
        }
        return hasToolbarSpacer ? users.size() + 1 : users.size();
    }

    public static class SpacerViewHolder extends RecyclerView.ViewHolder {
        public SpacerViewHolder(View itemView) {
            super(itemView);
        }
    }

    public static class AlbumViewHolder extends RecyclerView.ViewHolder {
        final SpoilerRobotoTextView body;
        final MaxHeightImageView image;

        public AlbumViewHolder(View itemView) {
            super(itemView);
            body = itemView.findViewById(R.id.imageCaption);
            image = itemView.findViewById(R.id.image);
        }
    }

    public static class GifViewHolder extends RecyclerView.ViewHolder {
        final MediaRowLoadState loadState = new MediaRowLoadState();
        final MaxHeightImageView gifDisplay;
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
