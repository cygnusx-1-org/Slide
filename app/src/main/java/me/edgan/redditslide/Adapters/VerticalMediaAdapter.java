package me.edgan.redditslide.Adapters;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;
import java.util.concurrent.Executor;
import me.edgan.redditslide.Activities.MediaView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Views.ExoVideoView;
import me.edgan.redditslide.Views.MaxHeightImageView;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.LayoutUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.MediaRowLoadState;

/**
 * Shared machinery for the vertical album/gallery lists — the single-column RecyclerViews behind
 * Album, RedditGallery and their embedded Shadowbox/CommentsScreen counterparts.
 *
 * <p>The three lists inflate the same three row layouts ({@code album_image},
 * {@code submission_gifcard_album}, {@code spacer}) and need the same behaviour from all of it: a
 * video row sized to its aspect ratio rather than the viewport, the button bar hidden, a play button
 * that opens {@link MediaView} instead of playing inline, one load per row tracked through
 * {@link MediaRowLoadState}, and a re-measure when the list's width changes. Only the model differs,
 * so subclasses supply accessors over their own item type and keep their own static-image binding.
 *
 * <p>This exists because the two adapters previously carried verbatim copies of all of it, and every
 * fix had to be made twice — which is how several of them ended up applied to one copy only.
 */
abstract class VerticalMediaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    protected static final int VIEW_TYPE_IMAGE = 1;
    protected static final int VIEW_TYPE_ANIMATED = 2;
    protected static final int VIEW_TYPE_SPACER = 6;

    protected final Activity main;
    @Nullable protected final String subreddit;
    @Nullable protected final String submissionTitle;

    /**
     * Whether the list leads with a spacer row that keeps the first item clear of an overlaying
     * toolbar. False for the shadowbox and comment-thread hosts, which have no toolbar over the list
     * — and no end-of-list spacer either: there the album card's own fragment pads the RecyclerView
     * by the height of the info panel that overlays its bottom, because only it knows that height.
     *
     * <p>Final, and decided in the constructor: it shifts every position this adapter maps, so a
     * caller flipping it later would leave the rows already bound answering to the old mapping. The
     * hosts used to pass in a toolbar height for this, which is what made it settable.
     */
    private final boolean hasToolbarSpacer;

    @Nullable private RecyclerView recyclerView;
    @Nullable private View.OnLayoutChangeListener widthChangeListener;
    private int lastRowWidth;

    protected VerticalMediaAdapter(
            final Activity main,
            final @Nullable String subreddit,
            final @Nullable String submissionTitle) {
        this.main = main;
        this.subreddit = subreddit;
        this.submissionTitle = submissionTitle;
        this.hasToolbarSpacer = main.findViewById(R.id.toolbar) != null;
    }

    /** See the field; the tests are the only readers outside this class. */
    public boolean hasToolbarSpacer() {
        return hasToolbarSpacer;
    }

    // ---------------------------------------------------------------- model seam

    /**
     * Number of media entries, excluding the leading spacer row where there is one. Every other
     * accessor below is called only for an index this method admits, so an implementation that
     * returns 0 for a null list (both of them do) does not have to repeat the check: with no entries
     * the list is a lone spacer, or empty in a host that has no spacer.
     */
    protected abstract int mediaCount();

    /** Whether the entry at {@code index} is a video or GIF rather than a still. */
    protected abstract boolean isAnimatedAt(int index);

    /**
     * Url for the entry at {@code index}, or null when it has none. A null makes the row inert: no
     * load is started, the play button is hidden and no tap opens anything.
     */
    protected abstract @Nullable String mediaUrlAt(int index);

    /** Intrinsic width of the entry at {@code index}, or 0 when unknown. */
    protected abstract int mediaWidthAt(int index);

    /** Intrinsic height of the entry at {@code index}, or 0 when unknown. */
    protected abstract int mediaHeightAt(int index);

    /** Binds the still-image row; the subclasses' captions and titles differ enough to keep apart. */
    protected abstract void bindStaticRow(RecyclerView.ViewHolder holder, int index);

    /**
     * Binds anything a subclass stacks above the video, returning its height so the row can allow for
     * it. {@code @id/imagearea} sits below {@code @id/galleryCaption}, so a caption that is not
     * accounted for here squashes the video instead of extending the row.
     */
    protected int bindAnimatedExtras(
            final AnimatedViewHolder holder, final int index, final int rowWidth) {
        return 0;
    }

    // ---------------------------------------------------------------- adapter plumbing

    /** Index into the media list for a RecyclerView position, allowing for the leading spacer row. */
    protected final int indexFor(final int position) {
        return hasToolbarSpacer ? position - 1 : position;
    }

    @Override
    public final int getItemCount() {
        return hasToolbarSpacer ? mediaCount() + 1 : mediaCount();
    }

    @Override
    public int getItemViewType(final int position) {
        if (hasToolbarSpacer && position == 0) {
            return VIEW_TYPE_SPACER;
        }
        final int index = indexFor(position);
        if (index < 0 || index >= mediaCount()) {
            return VIEW_TYPE_SPACER;
        }
        return isAnimatedAt(index) ? VIEW_TYPE_ANIMATED : VIEW_TYPE_IMAGE;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent, final int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_ANIMATED) {
            return new AnimatedViewHolder(
                    inflater.inflate(R.layout.submission_gifcard_album, parent, false));
        }
        if (viewType == VIEW_TYPE_IMAGE) {
            return new StaticViewHolder(inflater.inflate(R.layout.album_image, parent, false));
        }
        return new SpacerViewHolder(inflater.inflate(R.layout.spacer, parent, false));
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull final RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;

        // Video rows get an explicit pixel height, which goes stale when the list is resized.
        widthChangeListener = LayoutUtils.rebindOnWidthChange(this::rebindIfRowWidthChanged);
        recyclerView.addOnLayoutChangeListener(widthChangeListener);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull final RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        if (widthChangeListener != null) {
            recyclerView.removeOnLayoutChangeListener(widthChangeListener);
            widthChangeListener = null;
        }
        this.recyclerView = null;
    }

    /**
     * Re-measures the video rows, but only when the width they were sized for really did change. The
     * list's first layout also reports a width change (from zero), and rebinding there would reload
     * every visible video for nothing.
     */
    private void rebindIfRowWidthChanged() {
        if (recyclerView != null
                && lastRowWidth > 0
                && LayoutUtils.getAlbumRowWidth(recyclerView, main) != lastRowWidth) {
            notifyDataSetChanged();
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof SpacerViewHolder) {
            bindSpacer(holder);
            return;
        }

        final int index = indexFor(position);
        if (index < 0 || index >= mediaCount()) {
            return;
        }

        // The row background is deliberately not set here. A video row is opaque black (set in
        // AnimatedViewHolder, so the reserved area around the video is not transparent), but a still
        // row must stay transparent so the gaps around its card show the list's own background.
        if (holder instanceof AnimatedViewHolder) {
            bindAnimatedRow((AnimatedViewHolder) holder, index);
        } else {
            bindStaticRow(holder, index);
        }
    }

    /**
     * Sizes the leading spacer that keeps the first row clear of the overlaying toolbar.
     *
     * <p>Both dimensions come from resources rather than measurement: {@code itemView.getWidth()} is
     * 0 on a first bind and the toolbar's height is 0 until it has laid out, either of which used to
     * leave the first row tucked underneath it. This is the dimen fragment_verticalalbum.xml gives
     * that toolbar, so it needs nothing measured.
     */
    private void bindSpacer(final RecyclerView.ViewHolder holder) {
        // Returns itemView itself: @id/height is on spacer.xml's root, so this is never null.
        final View spacer = holder.itemView.requireViewById(R.id.height);
        spacer.setLayoutParams(
                new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        main.getResources()
                                .getDimensionPixelSize(R.dimen.standard_toolbar_height)));
    }

    private void bindAnimatedRow(final AnimatedViewHolder holder, final int index) {
        final String url = mediaUrlAt(index);
        final boolean playable = url != null;

        // Reset state a recycled row may have left behind.
        holder.rootView.setAlpha(1.0f);
        holder.exoVideoView.setVisibility(View.VISIBLE);
        holder.exoVideoView.setOnPlaybackStateChangedListener(null);

        // Size the row to the video instead of letting the shared pager layout's match_parent height
        // make it one screenful tall.
        lastRowWidth = LayoutUtils.getAlbumRowWidth(recyclerView, main);
        final int extraHeight = bindAnimatedExtras(holder, index, lastRowWidth);
        LayoutUtils.applyAlbumRowSize(
                holder.itemView,
                recyclerView,
                lastRowWidth,
                mediaWidthAt(index),
                mediaHeightAt(index),
                extraHeight);

        // The button bar has no enabled buttons in a list row, but its @id/size TextView still gets
        // the file size text, which would land on top of the video.
        if (holder.gifHeader != null) {
            holder.gifHeader.setVisibility(View.GONE);
        }

        holder.mediaIndex = index;

        // The play button and the video area both open MediaView; neither does anything without a
        // url, so a tap cannot launch MediaView with nothing to show.
        if (holder.playButton != null) {
            holder.playButton.setVisibility(playable ? View.VISIBLE : View.GONE);
            holder.playButton.setAlpha(0.8f);
            holder.playButton.setOnClickListener(
                    url == null ? null : v -> openMedia(url, index));
        }
        // setClickable after setOnClickListener, not before: that method turns clickable back on
        // when it is handed a null listener, so setting it first would leave an unplayable row
        // swallowing taps and announcing itself as actionable.
        holder.exoVideoView.setOnClickListener(
                url == null ? null : v -> openMedia(url, index));
        holder.exoVideoView.setClickable(playable);

        // Load only what this row is not already showing; see MediaRowLoadState for why the player's
        // own state cannot answer that.
        final boolean loading = url != null && holder.loadState.shouldLoad(url);

        // Only a row with nothing coming hides the progress bar. @id/gifprogress is visible in the
        // layout and nothing but reaching STATE_READY hides it, so a row inheriting one from the item
        // it used to show — whose load was cancelled or failed — would keep it animating over media
        // that never arrives. The load-in-flight test is separate from `loading` on purpose:
        // shouldLoad() is false both for media already on screen and for a download still running,
        // and a rebind in the middle of one (a width change) must leave its bar alone.
        if (!loading && holder.load == null) {
            holder.loader.setVisibility(View.GONE);
        }

        if (loading && url != null) {
            startLoad(holder, url);
        }
    }

    /**
     * Opens the full-screen viewer, or the browser when in-app media is switched off. The index is
     * what MediaView pages and names saved files by; pass -1 where the caller has none to give.
     */
    protected final void openMedia(final @Nullable String url, final int index) {
        if (url == null) {
            return;
        }

        if (!SettingValues.image) {
            LinkUtil.openExternally(url);
            return;
        }
        final Intent intent = new Intent(main, MediaView.class);
        intent.putExtra(MediaView.EXTRA_URL, url);
        intent.putExtra(MediaView.SUBREDDIT, subreddit);
        if (submissionTitle != null) {
            intent.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
        }
        if (index >= 0) {
            intent.putExtra("index", index);
        }
        main.startActivity(intent);
    }

    /**
     * Starts the load for a video row.
     *
     * <p>{@code closeIfNull} is false: it makes AsyncLoadGif finish the hosting activity when the
     * media does not resolve, which in a list row would close the whole album because one row failed.
     */
    private void startLoad(final AnimatedViewHolder holder, final String url) {
        // Drop whatever the row asked for before. The task holds this holder's ExoVideoView and
        // writes into it from onPostExecute, so a load left running for the item this row used to
        // show would play that item's video here once it finished — and it can easily finish after
        // the load below, since the row is rebound long before the network is done. MediaRowLoadState
        // cannot catch this: it tracks one url per row, not tasks belonging to a previous item.
        holder.cancelLoad();
        holder.loadState.loadStarted(url);
        holder.loader.setVisibility(View.VISIBLE);
        holder.load =
                new GifUtils.AsyncLoadGif(
                        main,
                        holder.exoVideoView,
                        holder.loader,
                        false, // closeIfNull
                        false, // autostart
                        holder.rootView.requireViewById(R.id.size),
                        subreddit,
                        submissionTitle) {
                    @Override
                    public void onError() {
                        holder.loadState.loadFailed(url);
                    }

                    @Override
                    protected void onPostExecute(final @Nullable Uri uri) {
                        super.onPostExecute(uri);
                        // Done either way; the row no longer has anything to cancel.
                        if (holder.load == this) {
                            holder.load = null;
                        }
                    }
                };
        // THREAD_POOL_EXECUTOR, not execute(): the default is a single process-wide serial queue,
        // and every one of these blocks on a network round trip before the next starts — the file
        // size lookup at the top of doInBackground alone is a synchronous HEAD request. A list with
        // several video rows would load them one after another, behind every other AsyncTask in the
        // app. GifUtils.downloadGif, which the Tumblr rows use, already picks the pool.
        execute(holder.load, AsyncTask.THREAD_POOL_EXECUTOR, url);
    }

    /**
     * Hands the row's load to an executor.
     *
     * <p>A seam, and the executor is a parameter rather than picked here, only so the test can see
     * which one the caller chose: overriding a method that picked its own would hide the choice
     * behind the override and let it revert unnoticed. Production behaviour is the one call below.
     */
    @VisibleForTesting
    void execute(
            final GifUtils.AsyncLoadGif load, final Executor executor, final String url) {
        load.executeOnExecutor(executor, url);
    }

    @Override
    public void onViewRecycled(@NonNull final RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder instanceof AnimatedViewHolder) {
            final AnimatedViewHolder animated = (AnimatedViewHolder) holder;
            // Nothing this row asked for is wanted any more: the holder is about to be handed to a
            // different item.
            animated.cancelLoad();
            // Stop the player but do not release it here; detaching does that.
            if (animated.exoVideoView != null) {
                animated.exoVideoView.pause();
            }
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull final RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (!(holder instanceof AnimatedViewHolder)) {
            return;
        }
        final AnimatedViewHolder animated = (AnimatedViewHolder) holder;
        final int index = animated.mediaIndex;
        if (index < 0 || index >= mediaCount()) {
            return;
        }
        // Reload only when detaching released the player and cleared the load state. These rows never
        // autostart, so an isPlaying() check here would always be false and every attach would re-run
        // the load onBindViewHolder had already started.
        final String url = mediaUrlAt(index);
        if (url != null && animated.loadState.isEmpty()) {
            startLoad(animated, url);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull final RecyclerView.ViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (holder instanceof AnimatedViewHolder) {
            final AnimatedViewHolder animated = (AnimatedViewHolder) holder;
            // The player this load was going to feed has just been released, so let it go too.
            animated.cancelLoad();
            // Detaching releases the player (ExoVideoView.onDetachedFromWindow), so whatever was
            // loaded is gone and the next attach has to load it again.
            animated.loadState.released();
        }
    }

    // ---------------------------------------------------------------- holders

    static class SpacerViewHolder extends RecyclerView.ViewHolder {
        SpacerViewHolder(final View itemView) {
            super(itemView);
        }
    }

    /** Row for a still image, from {@code album_image.xml}. */
    static class StaticViewHolder extends RecyclerView.ViewHolder {
        final SpoilerRobotoTextView text;
        final SpoilerRobotoTextView body;
        final MaxHeightImageView image;

        StaticViewHolder(final View itemView) {
            super(itemView);
            text = itemView.requireViewById(R.id.imagetitle);
            body = itemView.requireViewById(R.id.imageCaption);
            image = itemView.requireViewById(R.id.image);
        }
    }

    /** Row for a video or GIF, from {@code submission_gifcard_album.xml}. */
    static class AnimatedViewHolder extends RecyclerView.ViewHolder {
        final View rootView;

        /**
         * Not null, and not guarded at its use sites: {@code submission_gifcard_album.xml} always
         * declares {@code @id/gifprogress}, and this holder is only ever built over that layout.
         * Guarding it in one place and dereferencing it in another said two different things about
         * the same field.
         */
        @NonNull final ProgressBar loader;

        final ExoVideoView exoVideoView;
        final View gifHeader;
        final ImageView playButton;
        final SpoilerRobotoTextView caption;
        final MediaRowLoadState loadState = new MediaRowLoadState();

        /** Index into the media list, not an adapter position; -1 before the first bind. */
        int mediaIndex = -1;

        /**
         * The load in flight for this row, so it can be dropped when the row moves on. Null once the
         * load has finished or been cancelled — there is nothing left to drop in either case.
         */
        @Nullable GifUtils.AsyncLoadGif load;

        /**
         * Abandons the load in flight, if any. Cancelling before the task reaches onPostExecute is
         * what keeps it from writing into this row; a task that has already got there is harmless,
         * since it wrote what the row had asked for at the time.
         *
         * <p>Without interrupting: false lets the download it is inside finish and be cached, and
         * AsyncTask skips onPostExecute for a cancelled task either way, which is the part that
         * matters here.
         */
        void cancelLoad() {
            if (load != null) {
                load.cancel(false);
                load = null;
            }
        }

        AnimatedViewHolder(final View itemView) {
            super(itemView);
            this.rootView = itemView;
            this.loader = itemView.requireViewById(R.id.gifprogress);
            this.exoVideoView = itemView.findViewById(R.id.gif);
            // A list row: tapping opens MediaView, so the transport controls are never used.
            this.exoVideoView.markVerticalListRow();
            this.gifHeader = itemView.findViewById(R.id.gifheader);
            this.playButton = itemView.findViewById(R.id.playbutton);
            this.caption = itemView.requireViewById(R.id.galleryCaption);

            // Solid background so the reserved area around the video is not transparent.
            itemView.setBackgroundColor(Color.BLACK);
        }
    }
}
