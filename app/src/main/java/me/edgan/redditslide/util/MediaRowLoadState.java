package me.edgan.redditslide.util;

/**
 * Tracks which media url a reusable row has asked to load, for the vertical album and gallery
 * adapters. A row's video is loaded asynchronously into an ExoVideoView that releases its player on
 * detach, and the row is bound more often than it is loaded, so neither the player's state nor the
 * bind alone says whether a load is needed:
 *
 * <ul>
 *   <li>the attach that immediately follows a bind must not start a second load, even though the
 *       first has not finished and the player still holds nothing;
 *   <li>a rebind for an unrelated reason (the list's width changed) must not re-download anything;
 *   <li>an attach after a detach must load again, because the player was released;
 *   <li>a failed load should be retried, but only once, since some failures are really hand-offs.
 * </ul>
 *
 * <p>Main thread only. Every caller is a RecyclerView callback (bind, attach, detach) or
 * {@link GifUtils.AsyncLoadGif#onError()}, which is documented to run there, so the fields need no
 * synchronization. Urls must be non-null; a row with no media should not be asking to load one.
 */
public class MediaRowLoadState {

    private String loadedUrl;
    private String retriedUrl;

    /**
     * Whether a load has to be started for {@code url}. False once one has been, so a download still
     * running counts the same as media already on screen — this answers "does this row need a load
     * started", not "is this row idle". A caller that wants the second question (to decide whether to
     * show a progress indicator, say) has to track the in-flight task itself; that is deliberate,
     * since it is the only way to tell the two apart without asking the player, which cannot say.
     */
    public boolean shouldLoad(final String url) {
        return !url.equals(loadedUrl);
    }

    /** Whether the row holds nothing, so an attach has to start a load. */
    public boolean isEmpty() {
        return loadedUrl == null;
    }

    /** Records that a load for {@code url} has been started. */
    public void loadStarted(final String url) {
        if (!url.equals(retriedUrl)) {
            // Moved on to different media, so any earlier retry budget was for something else.
            retriedUrl = null;
        }
        loadedUrl = url;
    }

    /** The player was released (the row detached), so nothing is loaded any more. */
    public void released() {
        loadedUrl = null;
    }

    /**
     * Allows one retry of a failed load.
     *
     * <p>Ignores a url the row has since moved on from: a row outlives any one item, so a load that
     * fails late can belong to media this row no longer shows. Idempotent, as
     * {@link GifUtils.AsyncLoadGif#onError()} requires.
     */
    public void loadFailed(final String url) {
        if (url.equals(loadedUrl) && !url.equals(retriedUrl)) {
            retriedUrl = url;
            loadedUrl = null;
        }
    }
}
