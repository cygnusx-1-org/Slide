package me.edgan.redditslide.Views;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.SimpleExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerControlView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.NetworkUtil;
import org.jspecify.annotations.NullMarked;

/**
 * ExoVideoView that renders into a TextureView. Each instance owns its own player, TextureView and
 * SurfaceTexture, so several of these can be on screen at once (the video rows of a vertical album,
 * for example) and each renders its own video.
 *
 * <p>Detaching releases the player and reattaching rebuilds an empty one, so a reusable host (a
 * RecyclerView row) has to reload after a detach. Do not infer that from the player's own state: a
 * load is asynchronous, so "no media yet" and "media released" look identical and a host checking
 * only the player reloads on top of a load already in flight. Track the url you asked for instead,
 * clear it on detach, and clear it again from
 * {@link me.edgan.redditslide.util.GifUtils.AsyncLoadGif#onError()} when the load fails.
 */
@NullMarked
@OptIn(markerClass = UnstableApi.class)
public class ExoVideoView extends RelativeLayout {
    private static final String TAG = "ExoVideoView";

    private Context context;
    @Nullable private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;
    @Nullable private PlayerControlView playerUI;
    // The listener currently registered on the player by a load, so the next load can replace it
    // instead of stacking another one. Null whenever no listener is registered, including after the
    // player has been released.
    @Nullable private Player.Listener registeredListener;
    private boolean uiEnabled = true;
    private boolean verticalMode = false;
    private boolean scrubEnabled = false;
    private boolean muteAttached = false;
    private boolean hqAttached = false;
    private float[] speedOptions = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private int currentSpeedIndex = 3; // Normal (1.0x) default
    @Nullable private AudioFocusHelper audioFocusHelper;
    private Handler handler = new Handler(Looper.getMainLooper());
    @Nullable private Runnable hideControlsRunnable;
    private boolean hasAudio = false; // Track whether current video has audio

    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;
    @Nullable private AspectRatioFrameLayout videoFrame;

    // Variables for panning
    private float lastTouchX;
    private float lastTouchY;
    private float positionX = 0f;
    private float positionY = 0f;
    private boolean isDragging = false;
    private boolean wasScaling = false; // Flag to track if scaling happened in the gesture
    private boolean wasDragging = false; // Flag to track if dragging happened in the gesture

    // Variables for horizontal scrub-to-seek gesture
    private boolean isScrubbing = false; // Currently scrubbing in this gesture
    private boolean wasScrubbing = false; // Flag to track if scrubbing happened in the gesture
    private float scrubStartX; // Touch X when the current gesture started
    private float scrubStartY; // Touch Y when the current gesture started
    private long scrubStartPosition = 0; // Playback position when scrubbing started
    private long scrubTargetPosition = 0; // Position the user is currently seeking to

    // Variables for rotation
    private int currentRotation = 0; // Track current rotation in degrees
    private int originalVideoWidth = 0; // Store the original video width
    private int originalVideoHeight = 0; // Store the original video height
    private float rotationScaleFactor = 1.0f; // Scale factor applied for rotation auto-zoom
    private boolean userZoomed = false; // True once the user has pinch-zoomed

    // The TextureView used for video playback.
    @Nullable private TextureView videoTextureView;
    // A SurfaceTexture the TextureView handed back while a player could still be rendering into it;
    // freed in onDetachedFromWindow once stop() has released that player.
    @Nullable private SurfaceTexture pendingSurfaceRelease;

    public interface OnPlaybackStateChangedListener {
        void onPlaybackStateChanged(boolean isPlaying);
    }

    @Nullable private OnPlaybackStateChangedListener playbackStateChangedListener;

    public void setOnPlaybackStateChangedListener(
            @Nullable OnPlaybackStateChangedListener listener) {
        this.playbackStateChangedListener = listener;
    }

    public ExoVideoView(final Context context) {
        this(context, null, true);
    }

    public ExoVideoView(final Context context, final @Nullable AttributeSet attrs) {
        this(context, attrs, true);
    }

    /**
     * @param ui whether this view may build transport controls. Every instance in the app comes from
     *     XML inflation and so passes true; the controls are additionally suppressed for list rows
     *     (see {@link #markVerticalListRow()}) and for the hosts {@link #isVerticalMode()} knows.
     */
    public ExoVideoView(
            final Context context, final @Nullable AttributeSet attrs, final boolean ui) {
        super(context, attrs);
        this.context = context;
        this.uiEnabled = ui;
        setupPlayer();
        // setupUI() is deliberately not called here. A list row only learns that it is one
        // (markVerticalListRow) after it has been inflated, so building the controls now would
        // inflate media3's whole control layout once per row just to throw it away. The first
        // attach builds them, by which point the mode is known.
    }

    /**
     * Declares that this view is a row in a vertical album/gallery list, where tapping opens
     * MediaView instead of playing inline and the transport controls are never shown. Callers that
     * inflate this view into a list row should say so here rather than relying on
     * {@link #isVerticalMode()}, which can only recognise the two activities it knows by name and so
     * misses the album lists embedded in Shadowbox and CommentsScreen.
     *
     * <p>One-way: this discards any controls that already exist and stops them being rebuilt.
     */
    public void markVerticalListRow() {
        verticalMode = true;
        if (playerUI != null) {
            if (handler != null && hideControlsRunnable != null) {
                handler.removeCallbacks(hideControlsRunnable);
                hideControlsRunnable = null;
            }
            playerUI.setPlayer(null);
            removeView(playerUI);
            playerUI = null;
            setOnClickListener(null);
        }
    }

    /**
     * Enables the horizontal swipe-to-seek gesture, which is off by default.
     *
     * <p>MediaView is the only caller, deliberately. This used to be decided by excluding a list of
     * activity names, which meant the gesture was silently on in every host that happened not to be
     * on the list, and those are all hosts where a horizontal drag means something else:
     * MediaFragment (Shadowbox pages between posts), MediaFragmentComment (CommentsScreen pages
     * between posts), and PeekMediaView / ForceTouchLink (transient previews with no seeking).
     * Opting in is safer than opting out here: a host that forgets to opt in loses a gesture, while
     * a host that forgets to opt out steals paging.
     */
    public void setScrubEnabled(final boolean enabled) {
        scrubEnabled = enabled;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // If the player was released (player is null), reinitialize it.
        if (player == null) {
            Log.d(TAG, "Player is null on attach; reinitializing player.");
            setupPlayer();
        }
        if (uiEnabled && playerUI == null && !isVerticalMode()) {
            setupUI();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Pause playback when view is detached
        stop();

        // Now that the player is released, nothing can be rendering into the SurfaceTexture the
        // TextureView handed back on its own (earlier) detach, so it is safe to free.
        releasePendingSurface();

        // Cancel pending hide runnable
        if (handler != null && hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
        }

        super.onDetachedFromWindow();
    }

    private void releasePendingSurface() {
        if (pendingSurfaceRelease != null) {
            Log.d(TAG, "Releasing deferred SurfaceTexture: "
                    + System.identityHashCode(pendingSurfaceRelease));
            pendingSurfaceRelease.release();
            pendingSurfaceRelease = null;
        }
    }

    /**
     * Initializes the player and gives it a fresh TextureView to render into. Runs again on reattach,
     * because detaching releases the player.
     */
    private void setupPlayer() {
        // Create a track selector with bitrate settings.
        trackSelector = new DefaultTrackSelector(context);
        if ((SettingValues.lowResAlways
                || (NetworkUtil.isConnected(context)
                    && !NetworkUtil.isConnectedWifi(context)
                    && SettingValues.lowResMobile))
            && SettingValues.lqVideos) {
            trackSelector.setParameters(
                    trackSelector.buildUponParameters().setForceLowestBitrate(true));
        } else {
            trackSelector.setParameters(
                    trackSelector.buildUponParameters().setForceHighestSupportedBitrate(true));
        }

        // Release any existing player. Detach the controls from it first: once released, it must not
        // be called into, and PlayerControlView.setPlayer() removes its listener from whatever it
        // currently holds.
        if (player != null) {
            if (playerUI != null) {
                playerUI.setPlayer(null);
            }
            player.release();
            player = null;
            registeredListener = null;
        }

        // Drop the frame a previous setup added. Every setupPlayer() call ends in addView(frame),
        // so re-running it on reattach would otherwise stack a second TextureView on top of a dead
        // one.
        if (videoFrame != null) {
            removeView(videoFrame);
            videoFrame = null;
        }

        // Create the player.
        player = new SimpleExoPlayer.Builder(context).setTrackSelector(trackSelector).build();

        // Re-point any existing controls at the new player. setupUI() binds them once, but this
        // method runs again on every reattach, so without this the controls would keep driving the
        // player that stop() released.
        if (playerUI != null) {
            playerUI.setPlayer(player);
        }

        // Create an AspectRatioFrameLayout to size the video correctly.
        AspectRatioFrameLayout frame = new AspectRatioFrameLayout(context);

        this.videoFrame = frame;
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        params.addRule(CENTER_IN_PARENT, TRUE);
        frame.setLayoutParams(params);
        frame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);

        // Initialize scale gesture detector
        scaleGestureDetector = new ScaleGestureDetector(context, new VideoScaleListener());

        // Add a Player.Listener for aspect ratio changes, logging, etc.
        player.addListener(
            new Player.Listener() {
                // Make the video use the correct aspect ratio
                @Override
                public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                    Log.d(TAG, "onVideoSizeChanged: width=" + videoSize.width + ", height=" + videoSize.height + ", unappliedRotationDegrees=" + videoSize.unappliedRotationDegrees);
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        // Store the original video dimensions (accounting for embedded rotation)
                        originalVideoWidth = videoSize.width;
                        originalVideoHeight = videoSize.height;

                        // Calculate the correct aspect ratio
                        float aspectRatio = (float) videoSize.width / videoSize.height;

                        // Apply any needed rotation from video metadata
                        if (videoSize.unappliedRotationDegrees == 90 ||
                            videoSize.unappliedRotationDegrees == 270) {
                            aspectRatio = 1.0f / aspectRatio;
                            // Also swap the stored dimensions for embedded rotation
                            originalVideoWidth = videoSize.height;
                            originalVideoHeight = videoSize.width;
                        }

                        // Set the aspect ratio ONCE and never change it for user rotations
                        Log.d(TAG, "Setting aspect ratio to: " + aspectRatio);
                        frame.setAspectRatio(aspectRatio);

                        // Apply current rotation with proper scaling
                        applyRotation();
                    }
                }

                // Logging
                @Override
                public void onTracksChanged(@NonNull Tracks tracks) {
                    StringBuilder toLog = new StringBuilder();
                    for (int groupIndex = 0; groupIndex < tracks.getGroups().size(); groupIndex++) {
                        Tracks.Group group = tracks.getGroups().get(groupIndex);
                        for (int trackIndex = 0; trackIndex < group.getMediaTrackGroup().length; trackIndex++) {
                            Format format = group.getTrackFormat(trackIndex);
                            boolean isSelected = group.isTrackSelected(trackIndex);

                            toLog.append("Format:\t")
                                    .append(format)
                                    .append(isSelected ? " (selected)" : "")
                                    .append("\n");
                        }
                    }
                }

                @Override
                public void onRenderedFirstFrame() {
                    Log.d(TAG, "onRenderedFirstFrame: Fading in TextureView.");
                    if (videoTextureView != null) {
                        videoTextureView.animate().alpha(1f).setDuration(150).start(); // Short fade-in
                    }
                }
            });

        // --- Add listener for play state changes to manage UI timeout ---
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                // If controls are visible when play state changes,
                // reset the hide timer accordingly.
                final Runnable hideRunnable = hideControlsRunnable;
                if (playerUI != null && playerUI.isVisible() && handler != null && hideRunnable != null) {
                    Log.d(TAG, "PlayWhenReady changed while UI visible. New state=" + playWhenReady);
                    // Cancel any pending hide task
                    Log.d(TAG, "PlayWhenReady Listener: Cancelling any pending hide runnable.");
                    handler.removeCallbacks(hideRunnable);
                    // If starting to play, schedule a new hide task
                    if (playWhenReady) {
                        Log.d(TAG, "PlayWhenReady Listener: Scheduling hide runnable (delay 1000ms).");
                        handler.postDelayed(hideRunnable, 1000);
                    } else {
                        Log.d(TAG, "PlayWhenReady Listener: Not scheduling hide runnable (paused).");
                    }
                }
            }

            @Override
            public void onTracksChanged(@NonNull Tracks tracks) {
                // Check if the video has audio tracks
                boolean foundAudio = false;
                for (Tracks.Group group : tracks.getGroups()) {
                    for (int trackIndex = 0; trackIndex < group.getMediaTrackGroup().length; trackIndex++) {
                        if (group.isTrackSelected(trackIndex)) {
                            Format format = group.getTrackFormat(trackIndex);
                            if (format != null && MimeTypes.isAudio(format.sampleMimeType)) {
                                foundAudio = true;
                                break;
                            }
                        }
                    }
                    if (foundAudio) {
                        break;
                    }
                }
                hasAudio = foundAudio;
                Log.d(TAG, "Audio tracks detected: " + hasAudio);
            }
        });

        // --- Use a TextureView with a cached SurfaceTexture ---
        videoTextureView = new TextureView(context);
        videoTextureView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        videoTextureView.setAlpha(0f); // Make it transparent initially
        videoTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                // Nothing to do: the player was already pointed at this TextureView by
                // setVideoTextureView below, and each view now owns its own texture. Kept for the
                // lifecycle trace, and because the listener is needed for the destroy callback.
                Log.d(TAG, "onSurfaceTextureAvailable: surface=" + surface +
                        " (" + System.identityHashCode(surface) + "), width=" + width + ", height=" + height);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "onSurfaceTextureSizeChanged: surface=" + surface +
                        " (" + System.identityHashCode(surface) + "), width=" + width + ", height=" + height);
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                Log.d(TAG, "onSurfaceTextureDestroyed: surface=" + surface +
                        " (" + System.identityHashCode(surface) + ")");
                // Any texture still pending from an earlier TextureView belongs to a player that
                // has since been released, so it can go now.
                releasePendingSurface();

                if (player == null) {
                    // Nothing can be rendering into it (setupPlayer() releases the player before it
                    // drops the old frame), so let the framework free it right away.
                    return true;
                }

                // A ViewGroup detaches its children before its own onDetachedFromWindow runs, so
                // this fires while the player is still alive and possibly still rendering. Keep the
                // texture and free it in onDetachedFromWindow, just after stop(). Asking the player
                // to drop it here instead (clearVideoTextureView) would block the main thread on the
                // playback thread once per row leaving the screen.
                pendingSurfaceRelease = surface;
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                // Optionally log frame updates.
                // Log.d(TAG, "onSurfaceTextureUpdated: surface=" + surface);
            }
        });

        frame.addView(videoTextureView);
        player.setVideoTextureView(videoTextureView);

        // Index 0: the video has to stay the bottom-most child. Appending would put it above a
        // playerUI that an earlier setupUI() added, hiding the transport controls behind the
        // (opaque) TextureView every time this runs again on reattach.
        addView(frame, 0);

        // Configure player options.
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setVolume(SettingValues.unmuteDefault ? 1f : 0f);
        SettingValues.isMuted = !SettingValues.unmuteDefault;

        // Create audio focus helper.
        AudioManager audioManager = ContextCompat.getSystemService(context, AudioManager.class);
        if (audioManager != null) {
            audioFocusHelper = new AudioFocusHelper(audioManager);
        }
    }

    private void setupUI() {
        // Nothing to build if the host owns the tap gesture. Checked before anything is created:
        // this runs at first attach rather than at construction, so a host that installed its own
        // handler right after inflating would otherwise get controls that nothing can ever show.
        if (hasOnClickListeners()) {
            return;
        }

        playerUI = new PlayerControlView(context);
        playerUI.setPlayer(player);
        playerUI.setVisibility(View.GONE);
        playerUI.setShowTimeoutMs(-1);  // Ensure built-in timeout is disabled

        // Add the player UI with proper positioning constraints
        // The bottom margin lifts the seek bar clear of the @id/gifheader button bar that every host
        // using this view pins to the bottom (activity_media.xml, submission_gifcard_album.xml).
        // Both are dimens, paired in dimens.xml, so the margin cannot drift below the bar.
        RelativeLayout.LayoutParams playerUIParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        playerUIParams.addRule(ALIGN_PARENT_BOTTOM, TRUE);
        playerUIParams.bottomMargin =
                context.getResources().getDimensionPixelSize(R.dimen.video_controls_bottom_margin);
        addView(playerUI, playerUIParams);

        // Define the hide action - it just hides if run.
        hideControlsRunnable = () -> {
            // The decision to run this is made elsewhere.
            if (playerUI != null) {
                // Check visibility just before hiding to avoid hiding if user tapped again quickly
                if (playerUI.isVisible()) {
                    playerUI.hide();
                }
            }
        };

        setOnClickListener((v) -> {
            // Ensure playerUI, player, and handler are not null
            final Runnable hideRunnable = hideControlsRunnable;
            if (playerUI == null || player == null || handler == null || hideRunnable == null) return;

            // Always remove pending runnable when screen is tapped
            handler.removeCallbacks(hideRunnable);

            if (playerUI.isVisible()) {
                // If visible, just hide.
                playerUI.hide();
            } else {
                // If hidden, show and decide whether to schedule auto-hide.
                playerUI.show();
                boolean isPlaying = player.getPlayWhenReady();
                if (isPlaying) {
                    // If playing, schedule the hide runnable.
                    handler.postDelayed(hideRunnable, 2000);
                }
            }
        });
    }

    /**
     * Sets the player's URI and prepares for playback.
     *
     * <p>Rebuilds the player first if a previous load released it. That is not hypothetical: the
     * failure path of {@link me.edgan.redditslide.util.GifUtils.AsyncLoadGif} calls
     * {@link #stop()}, which releases and nulls the player, and then lets its caller retry. Only a
     * detach/attach cycle rebuilds it otherwise — and a RecyclerView rebind is not one, since a
     * scrapped row is detached from its parent without {@code onDetachedFromWindow}. Without this the
     * retried load would be dropped here, registering no listener and so never hiding the row's
     * progress bar: a spinner over media that never arrives.
     */
    public void setVideoURI(
            @Nullable Uri uri, VideoType type, @Nullable Player.Listener listener) {
        Log.d(TAG, "setVideoURI() called with uri: " + (uri != null ? uri.toString() : "null"));
        // Reset rotation and video dimensions when loading new video
        currentRotation = 0;
        originalVideoWidth = 0;
        originalVideoHeight = 0;
        rotationScaleFactor = 1.0f;
        scaleFactor = 1.0f;
        userZoomed = false;

        // Only while attached: a detached view has no use for a player, gets one from
        // onAttachedToWindow when it needs one, and would have nothing to release it again.
        if (player == null && isAttachedToWindow()) {
            Log.d(TAG, "Player was released by an earlier load; rebuilding for this one.");
            setupPlayer();
        }

        // Ensure player and uri are not null before proceeding
        if (player != null && uri != null) {
            DataSource.Factory downloader =
                    new OkHttpDataSource.Factory(Reddit.client)
                            .setDefaultRequestProperties(
                                    GifUtils.AsyncLoadGif.makeHeaderMap(
                                            uri.getHost() == null ? "" : uri.getHost()));
            DataSource.Factory cacheDataSourceFactory =
                    new CacheDataSource.Factory()
                            .setCache(Reddit.getVideoCache())
                            .setUpstreamDataSourceFactory(downloader);

            MediaSource videoSource;
            switch (type) {
                case DASH:
                    Log.d(TAG, "Creating DASH media source");
                    videoSource =
                            new DashMediaSource.Factory(cacheDataSourceFactory)
                                    .createMediaSource(MediaItem.fromUri(uri));
                    break;
                case STANDARD:
                default:
                    Log.d(TAG, "Creating standard media source");
                    videoSource =
                            new ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                                    .createMediaSource(MediaItem.fromUri(uri));
                    break;
            }

            player.setMediaSource(videoSource);
            player.prepare();

            // Replace the listener from a previous load on this player rather than stacking another
            // one: a retry after a playback error reuses the same player, and every stacked listener
            // means another callback for the same event.
            if (registeredListener != null) {
                player.removeListener(registeredListener);
            }
            registeredListener = listener;
            if (listener != null) {
                player.addListener(listener);
            }
        }
        // No else block needed, as we just won't proceed if player or uri is null
    }

    /** Starts video playback. */
    public void play() {
        // Ensure player is not null
        if (player != null) {
            player.play();
            // Gain audio focus only if helper is available and video has audio
            if (audioFocusHelper != null && hasAudio) {
                audioFocusHelper.gainFocus();
            }
            if (playbackStateChangedListener != null) {
                playbackStateChangedListener.onPlaybackStateChanged(true);
            }
        }
    }

    /** Pauses video playback. */
    public void pause() {
        // Ensure player is not null
        if (player != null) {
            player.pause();
            // Lose audio focus only if helper is available and video has audio
            if (audioFocusHelper != null && hasAudio) {
                audioFocusHelper.loseFocus();
            }
            if (playbackStateChangedListener != null) {
                playbackStateChangedListener.onPlaybackStateChanged(false);
            }
        }
    }

    /** Stops video playback and releases the player. */
    public void stop() {
        // Ensure player is not null before stopping/releasing
        if (player != null) {
            player.stop();
            player.release();
            player = null;
            registeredListener = null;
        }
        // Ensure audioFocusHelper is not null before losing focus and only if video had audio
        if (audioFocusHelper != null && hasAudio) {
            audioFocusHelper.loseFocus();
        }
        // Reset audio state when stopping
        hasAudio = false;
        // Cancel pending hide runnable when explicitly stopping
        if (handler != null && hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
        }
    }

    /**
     * Whether this view currently holds a player. Only the tests ask: production code calls the
     * playback methods, every one of which already no-ops without one.
     */
    @VisibleForTesting
    public boolean hasPlayer() {
        return player != null;
    }

    /** Seeks to a specified position (in milliseconds). */
    public void seekTo(long time) {
        Log.d(TAG, "seekTo() called with time: " + time);
        // Ensure player is not null before seeking
        if (player != null) {
            player.seekTo(time);
        }
    }

    /** Returns the current playback position (in milliseconds). */
    public long getCurrentPosition() {
        long pos = player != null ? player.getCurrentPosition() : 0;
        Log.d(TAG, "getCurrentPosition() called, returning: " + pos);
        return pos;
    }

    /** Returns whether the player is currently playing. */
    public boolean isPlaying() {
        boolean playing = player != null &&
                player.getPlaybackState() == Player.STATE_READY &&
                player.getPlayWhenReady();
        Log.d(TAG, "isPlaying() called, returning: " + playing);
        return playing;
    }

    /**
     * Attaches a mute button to this view.
     */
    public void attachMuteButton(final ImageView mute) {
        Log.d(TAG, "attachMuteButton() called");
        // Ensure mute button and player are not null
        if (mute != null && player != null) {
            mute.setVisibility(GONE);
            player.addListener(new Player.Listener() {
                @Override
                public void onTracksChanged(@NonNull Tracks tracks) {
                    Log.d(TAG, "attachMuteButton onTracksChanged");
                    if (muteAttached && !tracks.getGroups().isEmpty()) {
                        return;
                    } else {
                        muteAttached = true;
                    }
                    boolean foundAudio = false;
                    for (Tracks.Group group : tracks.getGroups()) {
                        for (int trackIndex = 0; trackIndex < group.getMediaTrackGroup().length; trackIndex++) {
                            if (group.isTrackSelected(trackIndex)) {
                                Format format = group.getTrackFormat(trackIndex);
                                if (format != null && MimeTypes.isAudio(format.sampleMimeType)) {
                                    foundAudio = true;
                                    break;
                                }
                            }
                        }
                        if (foundAudio) {
                            break;
                        }
                    }
                    if (foundAudio) {
                        mute.setVisibility(VISIBLE);
                        // Ensure player still exists when setting initial state
                        if (player != null) {
                           if (!SettingValues.isMuted) {
                                player.setVolume(1f);
                                mute.setImageResource(R.drawable.ic_volume_on);
                                BlendModeUtil.tintImageViewAsSrcAtop(mute, Color.WHITE);
                                // Gain focus only if helper exists and video has audio
                                if (audioFocusHelper != null && hasAudio) {
                                    audioFocusHelper.gainFocus();
                                }
                            } else {
                                player.setVolume(0f);
                                mute.setImageResource(R.drawable.ic_volume_off);
                                BlendModeUtil.tintImageViewAsSrcAtop(mute, ContextCompat.getColor(getContext(), R.color.md_red_500));
                                // Lose focus only if helper exists and video has audio
                                if (audioFocusHelper != null && hasAudio) {
                                     audioFocusHelper.loseFocus();
                                }
                            }
                        }
                        mute.setOnClickListener((v) -> {
                            // Ensure player still exists when clicked
                            if (player != null) {
                                if (SettingValues.isMuted) {
                                    Log.d(TAG, "Mute button clicked: unmuting");
                                    player.setVolume(1f);
                                    SettingValues.isMuted = false;
                                    SettingValues.prefs.edit().putBoolean(SettingValues.PREF_MUTE, false).apply();
                                    mute.setImageResource(R.drawable.ic_volume_on);
                                    BlendModeUtil.tintImageViewAsSrcAtop(mute, Color.WHITE);
                                    // Gain focus only if helper exists and video has audio
                                    if (audioFocusHelper != null && hasAudio) {
                                        audioFocusHelper.gainFocus();
                                    }
                                } else {
                                    Log.d(TAG, "Mute button clicked: muting");
                                    player.setVolume(0f);
                                    SettingValues.isMuted = true;
                                    SettingValues.prefs.edit().putBoolean(SettingValues.PREF_MUTE, true).apply();
                                    mute.setImageResource(R.drawable.ic_volume_off);
                                    BlendModeUtil.tintImageViewAsSrcAtop(mute, ContextCompat.getColor(getContext(), R.color.md_red_500));
                                    // Lose focus only if helper exists and video has audio
                                    if (audioFocusHelper != null && hasAudio) {
                                        audioFocusHelper.loseFocus();
                                    }
                                }
                            }
                        });
                    }
                }
            });
        }
    }

    /**
     * Attaches an HQ (high quality) button to this view.
     */
    public void attachHqButton(final ImageView hq) {
        Log.d(TAG, "attachHqButton() called");
        // Ensure hq button and player are not null
        if (hq != null && player != null) {
            hq.setVisibility(GONE);
            player.addListener(new Player.Listener() {
                @Override
                public void onTracksChanged(@NonNull Tracks tracks) {
                    Log.d(TAG, "attachHqButton onTracksChanged");
                    // Ensure trackSelector exists
                    if (trackSelector != null) {
                        if (hqAttached || tracks.getGroups().isEmpty() ||
                                trackSelector.getParameters().forceHighestSupportedBitrate) {
                            return;
                        } else {
                            hqAttached = true;
                        }
                        int videoTrackCounter = 0;
                        for (Tracks.Group group : tracks.getGroups()) {
                            for (int trackIndex = 0; trackIndex < group.getMediaTrackGroup().length; trackIndex++) {
                                Format format = group.getTrackFormat(trackIndex);
                                if (format != null && MimeTypes.isVideo(format.sampleMimeType)) {
                                    videoTrackCounter++;
                                    if (videoTrackCounter > 1) {
                                        break;
                                    }
                                }
                            }
                            if (videoTrackCounter > 1) {
                                break;
                            }
                        }
                        if (videoTrackCounter > 1) {
                            hq.setVisibility(VISIBLE);
                            hq.setOnClickListener((v) -> {
                                Log.d(TAG, "HQ button clicked: forcing high bitrate");
                                // Ensure trackSelector still exists when clicked
                                if (trackSelector != null) {
                                    trackSelector.setParameters(
                                            trackSelector.buildUponParameters()
                                                    .setForceLowestBitrate(false)
                                                    .setForceHighestSupportedBitrate(true));
                                    hq.setVisibility(GONE);
                                }
                            });
                        }
                    }
                }
            });
        }
    }

    /**
     * Attaches a speed control button to this view.
     */
    public void attachSpeedButton(final ImageView speed, final Context parentContext) {
        Log.d(TAG, "attachSpeedButton() called");
        if (speed != null && player != null) {
            speed.setVisibility(VISIBLE);
            speed.setImageResource(R.drawable.ic_speed);
            speed.setOnClickListener(v -> {
                // Show a BottomSheetDialog to pick speed
                String[] speedLabels = new String[] {
                        parentContext.getString(R.string.video_speed_0_25x),
                        parentContext.getString(R.string.video_speed_0_5x),
                        parentContext.getString(R.string.video_speed_0_75x),
                        parentContext.getString(R.string.video_speed_1x),
                        parentContext.getString(R.string.video_speed_1_25x),
                        parentContext.getString(R.string.video_speed_1_5x),
                        parentContext.getString(R.string.video_speed_2x)
                };

                android.widget.ListView listView = new android.widget.ListView(parentContext);
                // Custom adapter to show speed label and icon for selected
                android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
                    @Override
                    public int getCount() { return speedLabels.length; }
                    @Override
                    public Object getItem(int position) { return speedLabels[position]; }
                    @Override
                    public long getItemId(int position) { return position; }
                    @Override
                    public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                        android.content.Context ctx = parent.getContext();
                        android.widget.LinearLayout layout = new android.widget.LinearLayout(ctx);
                        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                        layout.setPadding(0, 0, 0, 0);
                        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
                        // Label
                        String labelText;
                        if (speedLabels[position].matches("[0-9.]+x")) {
                            // If the label is like "2x", format to 2.00x
                            try {
                                float val = Float.parseFloat(speedLabels[position].replace("x", ""));
                                labelText = String.format("%.2fx", val);
                            } catch (Exception e) {
                                labelText = speedLabels[position];
                            }
                        } else {
                            labelText = speedLabels[position];
                        }
                        android.widget.TextView label = new android.widget.TextView(ctx);
                        label.setText(labelText);
                        label.setTextColor(android.graphics.Color.WHITE);
                        // Use default text appearance for list items
                        label.setTextAppearance(android.R.style.TextAppearance_Material_Body1);
                        label.setPadding((int)(ctx.getResources().getDisplayMetrics().density*4), (int)(ctx.getResources().getDisplayMetrics().density*8), (int)(ctx.getResources().getDisplayMetrics().density*4), (int)(ctx.getResources().getDisplayMetrics().density*8));
                        android.widget.LinearLayout.LayoutParams labelParams = new android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                        label.setLayoutParams(labelParams);
                        layout.addView(label);
                        // Icon for selected
                        if (position == currentSpeedIndex) {
                            ImageView icon = new ImageView(ctx);
                            icon.setImageResource(R.drawable.ic_speed);
                            icon.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
                            int iconSize = (int)(ctx.getResources().getDisplayMetrics().density*24);
                            android.widget.LinearLayout.LayoutParams iconParams = new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
                            iconParams.setMarginStart((int)(ctx.getResources().getDisplayMetrics().density*8));
                            icon.setLayoutParams(iconParams);
                            layout.addView(icon);
                        }
                        return layout;
                    }
                };
                listView.setAdapter(adapter);
                listView.setChoiceMode(android.widget.ListView.CHOICE_MODE_SINGLE);
                listView.setItemChecked(currentSpeedIndex, true);
                listView.setBackgroundColor(android.graphics.Color.BLACK);
                listView.setDivider(null); // Remove the separator
                listView.setDividerHeight(0); // Ensure no divider is shown
                int horizontalPadding = (int) (parentContext.getResources().getDisplayMetrics().density * 24); // 24dp
                int topPadding = (int) (parentContext.getResources().getDisplayMetrics().density * 12); // 12dp
                listView.setPadding(horizontalPadding, topPadding, horizontalPadding, listView.getPaddingBottom());
                listView.setClipToPadding(false);

                com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(parentContext);
                bottomSheetDialog.setContentView(listView);
                bottomSheetDialog.setTitle(parentContext.getString(R.string.video_speed));

                // Set the background of the bottom sheet itself to black (no rounded corners)
                bottomSheetDialog.setOnShowListener(dialog -> {
                    android.view.View bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                    if (bottomSheet != null) {
                        bottomSheet.setBackgroundColor(android.graphics.Color.BLACK);
                    }
                });

                listView.setOnItemClickListener((parent, view, position, id) -> {
                    setPlaybackSpeed(speedOptions[position]);
                    currentSpeedIndex = position;
                    bottomSheetDialog.dismiss();
                });

                bottomSheetDialog.show();
            });
        }
    }

    /**
     * Sets the playback speed of the player.
     */
    public void setPlaybackSpeed(float speed) {
        if (player != null) {
            player.setPlaybackParameters(new androidx.media3.common.PlaybackParameters(speed));
        }
    }

    /** Enum for video types. */
    public enum VideoType {
        STANDARD,
        DASH
    }

    /** Helper class to manage audio focus. */
    private class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {
        @Nullable private AudioManager manager;
        private boolean wasPlaying;
        @Nullable private AudioFocusRequestCompat request;

        AudioFocusHelper(@Nullable AudioManager manager) {
            // Only proceed if manager is not null
            if (manager != null) {
                this.manager = manager;
                // Initialize request only if it's null and manager is valid
                if (request == null) {
                    AudioAttributesCompat audioAttributes =
                            new AudioAttributesCompat.Builder()
                                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE)
                                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                                    .build();

                    AudioFocusRequestCompat.Builder builder = new AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(audioAttributes)
                            .setOnAudioFocusChangeListener(this);

                    if (SettingValues.pauseOnAudioFocus) {
                        builder.setWillPauseWhenDucked(true);
                    }

                    request = builder.build();
                }
            } else {
                // If manager is null, ensure helper state reflects that
                this.manager = null;
                this.request = null;
            }
        }

        void loseFocus() {
            Log.d(TAG, "AudioFocusHelper: losing focus");
            // Only abandon focus if manager and request are valid
            if (manager != null && request != null) {
                AudioManagerCompat.abandonAudioFocusRequest(manager, request);
            }
        }

        void gainFocus() {
            Log.d(TAG, "AudioFocusHelper: gaining focus");
            // Only request focus if manager and request are valid
            if (manager != null && request != null) {
                AudioManagerCompat.requestAudioFocus(manager, request);
            }
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            Log.d(TAG, "AudioFocusHelper: onAudioFocusChange: " + focusChange);
            // Only proceed if player exists
            if (player != null) {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                    wasPlaying = player.getPlayWhenReady();
                    player.pause();
                } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                    player.setPlayWhenReady(wasPlaying);
                }
            }
        }
    }

    /**
     * Whether the horizontal swipe-to-seek gesture applies to this view right now: the host opted in
     * (the full-screen viewer, never a gallery or list row where a horizontal swipe has to page or
     * scroll), there is a seekbar for it to move, and the view is neither zoomed nor mid-pinch.
     *
     * <p>Its own method, and visible, only so it can be tested: the rest of {@link #handleScrub}
     * needs a loaded video with a known duration before it does anything observable, so the gate
     * cannot be reached through a touch event in a unit test. It stays in guard position at the top
     * of that method — a predicate, not a value handed to something that has already acted on it.
     */
    @VisibleForTesting
    public boolean canScrub(final boolean scalingInProgress) {
        return scrubEnabled
                && playerUI != null
                && scaleFactor <= 1.0f
                && !scalingInProgress
                && player != null;
    }

    /**
     * Handles the horizontal swipe-to-seek (scrub) gesture. Dragging left/right moves the
     * playback position back/forward, mapping a full-width drag to the full video duration.
     *
     * <p>Runs only where {@link #canScrub} allows it, and only when the horizontal movement clearly
     * dominates the vertical movement (so vertical swipe-to-dismiss is preserved).
     *
     * @return true if this event was consumed by the scrub gesture (so it should not be treated as
     *     a tap).
     */
    private boolean handleScrub(MotionEvent event, int action, boolean scalingInProgress) {
        // The end of a gesture is handled whatever canScrub() says by then. A scrub that started
        // while it allowed one has put CLOSEST_SYNC on the player and turned the parent's touch
        // interception off, and both have to be undone even if a second finger or a pinch-zoom has
        // since closed the gate — otherwise every later seek snaps to a keyframe for the life of
        // the player. Returns false either way: only a MOVE ever consumed the event.
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            endScrub();
            return false;
        }

        if (!canScrub(scalingInProgress)) {
            return false;
        }

        // canScrub() has already established this, but it is a separate method so the checker
        // cannot carry the fact across.
        final SimpleExoPlayer scrubPlayer = player;
        if (scrubPlayer == null) {
            return false;
        }

        boolean scrubHandled = false;
        switch (action) {
            case MotionEvent.ACTION_MOVE: {
                if (event.getPointerCount() != 1) break;

                final long duration = scrubPlayer.getDuration();
                if (duration <= 0) break; // Unknown/zero duration (e.g. live) — nothing to scrub.

                final float dx = event.getX() - scrubStartX;
                final float dy = event.getY() - scrubStartY;
                final float touchSlop =
                        android.view.ViewConfiguration.get(context).getScaledTouchSlop();

                // Begin scrubbing once horizontal movement passes the slop and dominates vertical.
                if (!isScrubbing
                        && Math.abs(dx) > touchSlop
                        && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    isScrubbing = true;
                    wasScrubbing = true;
                    scrubStartPosition = scrubPlayer.getCurrentPosition();
                    // Snap to keyframes while dragging so live preview stays responsive.
                    scrubPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC);
                    // Keep the seekbar controls on screen for the whole scrub.
                    showControls();
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }

                if (isScrubbing) {
                    int width = getWidth() > 0
                            ? getWidth()
                            : getResources().getDisplayMetrics().widthPixels;
                    long delta = (long) ((dx / width) * duration);
                    scrubTargetPosition =
                            Math.max(0, Math.min(duration, scrubStartPosition + delta));
                    scrubPlayer.seekTo(scrubTargetPosition);
                    // The seekbar (PlayerControlView) follows the player position automatically,
                    // so seeking is all the visual feedback we need.
                    showControls();
                    scrubHandled = true;
                }
                break;
            }
        }
        return scrubHandled;
    }

    /**
     * Undoes what a scrub in flight set up, and does nothing when there is no scrub in flight.
     *
     * <p>Separate from the gate on purpose: what has to be cleared is decided by whether a scrub
     * started, not by whether one would be allowed to start now.
     */
    private void endScrub() {
        if (!isScrubbing) {
            return;
        }
        isScrubbing = false;
        if (player != null) {
            // Restore exact seeking and land precisely on the chosen position.
            player.setSeekParameters(SeekParameters.DEFAULT);
            player.seekTo(scrubTargetPosition);
        }
        // Let the controls fall back to their normal auto-hide behavior.
        scheduleControlsHide();
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    /** Shows the seekbar controls and keeps them on screen (cancels any pending auto-hide). */
    private void showControls() {
        if (playerUI == null) return;
        if (handler != null && hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
        }
        if (!playerUI.isVisible()) {
            playerUI.show();
        }
    }

    /** Restores the normal auto-hide behavior for the seekbar controls after scrubbing. */
    private void scheduleControlsHide() {
        if (playerUI == null || handler == null || hideControlsRunnable == null) return;
        handler.removeCallbacks(hideControlsRunnable);
        if (player != null && player.getPlayWhenReady()) {
            handler.postDelayed(hideControlsRunnable, 2000);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) return super.onTouchEvent(null);
        if (scaleGestureDetector == null) return super.onTouchEvent(event);

        // Pass event to scale detector FIRST
        scaleGestureDetector.onTouchEvent(event);
        boolean scalingInProgress = scaleGestureDetector.isInProgress();

        final int action = event.getActionMasked();

        // Reset flags on ACTION_DOWN
        if (action == MotionEvent.ACTION_DOWN) {
            lastTouchX = event.getX();
            lastTouchY = event.getY();
            isDragging = false;
            wasScaling = false; // Reset scaling history flag for the new gesture
            wasDragging = false; // Reset dragging history flag for the new gesture
            // Reset scrub state and record the gesture's starting point
            scrubStartX = event.getX();
            scrubStartY = event.getY();
            isScrubbing = false;
            wasScrubbing = false; // Reset scrubbing history flag for the new gesture
        }

        boolean dragHandled = false;
        boolean scrubHandled = handleScrub(event, action, scalingInProgress);
        // Panning logic (only when zoomed and not currently scaling)
        if (scaleFactor > 1.0f && !scalingInProgress) {
            switch (action) {
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getX() - lastTouchX;
                    float dy = event.getY() - lastTouchY;
                    // Start dragging if movement is significant
                    if (!isDragging && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
                        isDragging = true;
                    }
                    if (isDragging) {
                        // Update position with constraints
                        positionX += dx;
                        positionY += dy;
                        if (videoFrame != null) {
                            float maxDeltaX = (videoFrame.getWidth() * (scaleFactor - 1)) / 2;
                            float maxDeltaY = (videoFrame.getHeight() * (scaleFactor - 1)) / 2;
                            positionX = Math.max(-maxDeltaX, Math.min(maxDeltaX, positionX));
                            positionY = Math.max(-maxDeltaY, Math.min(maxDeltaY, positionY));
                            // Apply translation
                            videoFrame.setTranslationX(positionX);
                            videoFrame.setTranslationY(positionY);
                        }
                        dragHandled = true; // Mark that dragging occurred
                        wasDragging = true; // Mark that dragging occurred in this gesture
                    }
                    // Update last touch position regardless for next move calculation
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    break;
                }
                 case MotionEvent.ACTION_POINTER_DOWN:
                 case MotionEvent.ACTION_POINTER_UP: {
                    // Update reference point to maintain smooth panning across pointer changes
                     int index = event.getActionIndex();
                     int newIndex = 0; // Default to the first pointer
                     // If the primary pointer went up, use the next available one
                     if (action == MotionEvent.ACTION_POINTER_UP && index == 0 && event.getPointerCount() > 1) {
                         newIndex = 1;
                     }
                     lastTouchX = event.getX(newIndex);
                     lastTouchY = event.getY(newIndex);
                     break;
                 }
                 // ACTION_UP and ACTION_CANCEL handled below the switch
            }
        } // end if (scaleFactor > 1.0f && !scalingInProgress)


        // Determine if the event should be consumed (preventing click)
        // Consume if:
        // 1. Scaling is currently in progress (mid-gesture)
        // 2. Dragging occurred during this MOVE event
        // 3. The action is UP or CANCEL *and* scaling or dragging happened at any point during this gesture sequence
        boolean consumeEvent = scalingInProgress || dragHandled || scrubHandled ||
                ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && (wasScaling || wasDragging || wasScrubbing));

        // Reset dragging state on UP or CANCEL, regardless of consumption, ready for next gesture
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            isDragging = false;
            // wasScaling and wasDragging are reset on ACTION_DOWN
        }

        if (consumeEvent) {
            return true; // Consume the event, preventing click listener
        } else {
            // Not scaling, not dragging, not an UP/CANCEL after scaling.
            // Pass to superclass to handle potential clicks etc.
            return super.onTouchEvent(event);
        }
    }

    /**
     * Scale gesture listener to handle pinch-to-zoom events
     */
    private class VideoScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            // Ensure detector is not null
            if (detector != null) {
                wasScaling = true; // Mark that scaling has occurred in this gesture sequence
                userZoomed = true;

                scaleFactor *= detector.getScaleFactor();

                // Limit the scale factor to reasonable bounds
                scaleFactor = Math.max(0.5f, Math.min(scaleFactor, 3.0f));

                // Apply the scale to the video frame if it exists
                if (videoFrame != null) {
                    videoFrame.setScaleX(scaleFactor);
                    videoFrame.setScaleY(scaleFactor);
                }
                return true;
            }
            return false; // Indicate scale was not handled
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            // Ensure detector is not null
            if (detector != null) {
                // Snap back to the rotation-aware default scale when the user releases
                // within a small tolerance of it. For unrotated videos rotationScaleFactor
                // is 1.0f, matching the original snap-to-fit behavior.
                if (Math.abs(scaleFactor - rotationScaleFactor) <= 0.05f) {
                    scaleFactor = rotationScaleFactor;
                    userZoomed = false;
                    if (videoFrame != null) {
                        videoFrame.setScaleX(scaleFactor);
                        videoFrame.setScaleY(scaleFactor);
                    }
                    resetPosition(); // resetPosition handles internal null check
                } else if (scaleFactor <= 0.6f) {
                    resetPosition();
                }
            }
        }
    }

    /**
     * Resets any applied zoom to default scale and position
     */
    public void resetZoom() {
        scaleFactor = rotationScaleFactor;
        userZoomed = false;
        resetPosition(); // resetPosition already has a null check for videoFrame
        // Ensure videoFrame exists before resetting scale/mode
        if (videoFrame != null) {
            videoFrame.setScaleX(rotationScaleFactor);
            videoFrame.setScaleY(rotationScaleFactor);
        }
    }

    /**
     * Resets the panning position to center
     */
    private void resetPosition() {
        positionX = 0f;
        positionY = 0f;
        // Ensure videoFrame exists before resetting translation
        if (videoFrame != null) {
            videoFrame.setTranslationX(0f);
            videoFrame.setTranslationY(0f);
        }
    }

    /**
     * Rotates the video 90 degrees clockwise
     */
    public void rotateRight() {
        currentRotation = (currentRotation + 90) % 360;
        resetPosition(); // Reset panning when rotating
        applyRotation();
    }

    /**
     * Rotates the video 90 degrees counter-clockwise
     */
    public void rotateLeft() {
        currentRotation = (currentRotation - 90 + 360) % 360;
        resetPosition(); // Reset panning when rotating
        applyRotation();
    }

    /**
     * Resets video rotation to 0 degrees
     */
    public void resetRotation() {
        currentRotation = 0;
        resetPosition(); // Reset panning when resetting rotation
        applyRotation();
    }

    /**
     * Applies the current rotation to the video frame
     */
    private void applyRotation() {
        if (videoFrame != null && originalVideoWidth > 0 && originalVideoHeight > 0) {
            videoFrame.setRotation(currentRotation);

            // Always use FIT mode to show the full content
            videoFrame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);

            boolean isVerticalVideo = originalVideoHeight > originalVideoWidth;

            if (currentRotation == 90 || currentRotation == 270) {
                if (isVerticalVideo) {
                    // For vertical videos, we need to zoom out so the video's original top/bottom
                    // edges fit the screen's width.
                    // The scale factor is the ratio of the screen's width to its height.
                    float screenWidth = getResources().getDisplayMetrics().widthPixels;
                    float screenHeight = getResources().getDisplayMetrics().heightPixels;
                    rotationScaleFactor = screenWidth / screenHeight;
                    Log.d(TAG, "Applied rotation for vertical video: " + currentRotation + "° with zoom scale: " + rotationScaleFactor);
                } else {
                    // For horizontal videos, zoom in to fit the width of the screen
                    float videoAspect = (float) originalVideoWidth / originalVideoHeight;
                    rotationScaleFactor = videoAspect;
                    Log.d(TAG, "Applied rotation for horizontal video: " + currentRotation + "° with zoom scale: " + rotationScaleFactor);
                }
            } else {
                // For 0/180 degree rotations, reset to normal view
                rotationScaleFactor = 1.0f;
                scaleFactor = 1.0f; // Reset scale to normal for 0/180 degrees
                userZoomed = false;
                resetPosition(); // Reset position when going to normal rotation
                Log.d(TAG, "Applied rotation: " + currentRotation + "° (normal view)");
            }

            // Apply the rotation auto-zoom unless the user has manually pinch-zoomed
            if (!userZoomed) {
                scaleFactor = rotationScaleFactor;
            }

            videoFrame.setScaleX(scaleFactor);
            videoFrame.setScaleY(scaleFactor);
        }
    }

    /**
     * Gets the current rotation in degrees
     */
    public int getCurrentRotation() {
        return currentRotation;
    }

    /**
     * Whether the transport controls should be suppressed for this view.
     *
     * <p>The activity-name check is not dead weight next to {@link #markVerticalListRow()}: the list
     * rows mark themselves, but the Album and RedditGallery activities also host a non-row
     * ExoVideoView — RedditGallery's own full-screen Gif fragment — which has always had its controls
     * suppressed this way. Removing the check would start showing controls there. It matches on the
     * activity's simple name, so it covers only those two: AlbumPager and TumblrPager are separate
     * activities and their Gif pages have always shown the controls.
     */
    private boolean isVerticalMode() {
        if (verticalMode) {
            return true;
        }
        // Get the context's activity
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                String activityName = context.getClass().getSimpleName();
                return activityName.equals("Album") || activityName.equals("RedditGallery");
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return false;
    }
}
