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
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media.AudioAttributesCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.SimpleExoPlayer;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerControlView;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.VideoSize;


import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.NetworkUtil;

/**
 * ExoVideoView that uses a TextureView (with a cached SurfaceTexture) so that when recycled
 * (e.g., when scrolling in the gallery) the video decoder's surface is reused and the video does
 * not go blank.
 */
public class ExoVideoView extends RelativeLayout {
    private static final String TAG = "ExoVideoView";

    public interface OnSingleTapListener {
        boolean onSingleTap(MotionEvent event);
    }
    private OnSingleTapListener singleTapListener;

    public void setOnSingleTapListener(OnSingleTapListener listener) {
        this.singleTapListener = listener;
    }

    private Context context;
    private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private PlayerControlView playerUI;
    private boolean muteAttached = false;
    private boolean hqAttached = false;
    private boolean speedAttached = false;
    private float[] speedOptions = {0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private int currentSpeedIndex = 3; // Normal (1.0x) default
    private AudioFocusHelper audioFocusHelper;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable hideControlsRunnable;

    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;
    private AspectRatioFrameLayout videoFrame;

    // Variables for panning
    private float lastTouchX;
    private float lastTouchY;
    private float positionX = 0f;
    private float positionY = 0f;
    private boolean isDragging = false;
    private boolean wasScaling = false; // Flag to track if scaling happened in the gesture
    private boolean wasDragging = false; // Flag to track if dragging happened in the gesture

    // Static variable to hold the saved SurfaceTexture.
    private static SurfaceTexture sSavedSurfaceTexture;
    // The TextureView used for video playback.
    private TextureView videoTextureView;

    public interface OnPlaybackStateChangedListener {
        void onPlaybackStateChanged(boolean isPlaying);
    }

    private OnPlaybackStateChangedListener playbackStateChangedListener;

    public void setOnPlaybackStateChangedListener(OnPlaybackStateChangedListener listener) {
        this.playbackStateChangedListener = listener;
    }

    public ExoVideoView(final Context context) {
        this(context, null, true);
    }

    public ExoVideoView(final Context context, final boolean ui) {
        this(context, null, ui);
    }

    public ExoVideoView(final Context context, final AttributeSet attrs) {
        this(context, attrs, true);
    }

    public ExoVideoView(final Context context, final AttributeSet attrs, final boolean ui) {
        super(context, attrs);
        this.context = context;
        setupPlayer();
        if (ui && !isVerticalMode()) {
            setupUI();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // If the player was released (player is null), reinitialize it.
        if (player == null) {
            Log.d(TAG, "Player is null on attach; reinitializing player.");
            setupPlayer();
            if (playerUI == null && !isVerticalMode()) {
                setupUI();
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        // Pause playback when view is detached
        stop();

        // Cancel pending hide runnable
        if (handler != null && hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
        }

        super.onDetachedFromWindow();
    }

    /** Initializes the player and sets up a TextureView that reuses its SurfaceTexture. */
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

        // Release any existing player.
        if (player != null) {
            player.release();
            player = null;
        }

        // Create the player.
        player = new SimpleExoPlayer.Builder(context).setTrackSelector(trackSelector).build();

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
                        // Calculate the correct aspect ratio
                        float aspectRatio = (float) videoSize.width / videoSize.height;

                        // Apply any needed rotation
                        if (videoSize.unappliedRotationDegrees == 90 ||
                            videoSize.unappliedRotationDegrees == 270) {
                            aspectRatio = 1.0f / aspectRatio;
                        }

                        // Set the aspect ratio
                        Log.d(TAG, "Setting aspect ratio to: " + aspectRatio);
                        frame.setAspectRatio(aspectRatio);
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
                if (playerUI != null && playerUI.isVisible() && handler != null) {
                    Log.d(TAG, "PlayWhenReady changed while UI visible. New state=" + playWhenReady);
                    // Cancel any pending hide task
                    Log.d(TAG, "PlayWhenReady Listener: Cancelling any pending hide runnable.");
                    handler.removeCallbacks(hideControlsRunnable);
                    // If starting to play, schedule a new hide task
                    if (playWhenReady) {
                        Log.d(TAG, "PlayWhenReady Listener: Scheduling hide runnable (delay 1000ms).");
                        handler.postDelayed(hideControlsRunnable, 1000);
                    } else {
                        Log.d(TAG, "PlayWhenReady Listener: Not scheduling hide runnable (paused).");
                    }
                }
            }
        });

        // --- Use a TextureView with a cached SurfaceTexture ---
        videoTextureView = new TextureView(context);
        videoTextureView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        videoTextureView.setAlpha(0f); // Make it transparent initially
        videoTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                Log.d(TAG, "onSurfaceTextureAvailable: surface=" + surface +
                        " (" + System.identityHashCode(surface) + "), width=" + width + ", height=" + height);
                if (sSavedSurfaceTexture != null) {
                    Log.d(TAG, "Reattaching saved SurfaceTexture: " + System.identityHashCode(sSavedSurfaceTexture));
                    videoTextureView.setSurfaceTexture(sSavedSurfaceTexture);
                } else {
                    sSavedSurfaceTexture = surface;
                    Log.d(TAG, "Saving new SurfaceTexture: " + System.identityHashCode(surface));
                }
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
                // Return false to indicate that we are managing the SurfaceTexture lifecycle.
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

        addView(frame);

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
        playerUI = new PlayerControlView(context);
        playerUI.setPlayer(player);
        playerUI.setVisibility(View.GONE);
        playerUI.setShowTimeoutMs(-1);  // Ensure built-in timeout is disabled

        // Add the player UI with proper positioning constraints
        RelativeLayout.LayoutParams playerUIParams = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        playerUIParams.addRule(ALIGN_PARENT_BOTTOM, TRUE);
        playerUIParams.bottomMargin = (int) (64 * context.getResources().getDisplayMetrics().density);
        addView(playerUI);

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
            if (playerUI == null || player == null || handler == null) return;

            // Always remove pending runnable when screen is tapped
            handler.removeCallbacks(hideControlsRunnable);

            if (playerUI.isVisible()) {
                // If visible, just hide.
                playerUI.hide();
            } else {
                // If hidden, show and decide whether to schedule auto-hide.
                playerUI.show();
                boolean isPlaying = player.getPlayWhenReady();
                if (isPlaying) {
                    // If playing, schedule the hide runnable.
                    handler.postDelayed(hideControlsRunnable, 2000);
                }
            }
        });
    }

    /**
     * Sets the player's URI and prepares for playback.
     */
    public void setVideoURI(Uri uri, VideoType type, Player.Listener listener) {
        Log.d(TAG, "setVideoURI() called with uri: " + (uri != null ? uri.toString() : "null"));
        // Ensure player and uri are not null before proceeding
        if (player != null && uri != null) {
            DataSource.Factory downloader =
                    new OkHttpDataSource.Factory(Reddit.client)
                            .setDefaultRequestProperties(GifUtils.AsyncLoadGif.makeHeaderMap(uri.getHost()));
            DataSource.Factory cacheDataSourceFactory =
                    new CacheDataSource.Factory()
                            .setCache(Reddit.videoCache)
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
            // Gain audio focus if helper is available
            if (audioFocusHelper != null) {
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
            // Lose audio focus if helper is available
            if (audioFocusHelper != null) {
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
        }
        // Ensure audioFocusHelper is not null before losing focus
        if (audioFocusHelper != null) {
            audioFocusHelper.loseFocus();
        }
        // Cancel pending hide runnable when explicitly stopping
        if (handler != null && hideControlsRunnable != null) {
            handler.removeCallbacks(hideControlsRunnable);
        }
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
                                // Gain focus only if helper exists
                                if (audioFocusHelper != null) {
                                    audioFocusHelper.gainFocus();
                                }
                            } else {
                                player.setVolume(0f);
                                mute.setImageResource(R.drawable.ic_volume_off);
                                BlendModeUtil.tintImageViewAsSrcAtop(mute, getResources().getColor(R.color.md_red_500));
                                // Lose focus only if helper exists
                                if (audioFocusHelper != null) {
                                     audioFocusHelper.loseFocus(); // Already checked, but good practice
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
                                    // Gain focus only if helper exists
                                    if (audioFocusHelper != null) {
                                        audioFocusHelper.gainFocus();
                                    }
                                } else {
                                    Log.d(TAG, "Mute button clicked: muting");
                                    player.setVolume(0f);
                                    SettingValues.isMuted = true;
                                    SettingValues.prefs.edit().putBoolean(SettingValues.PREF_MUTE, true).apply();
                                    mute.setImageResource(R.drawable.ic_volume_off);
                                    BlendModeUtil.tintImageViewAsSrcAtop(mute, getResources().getColor(R.color.md_red_500));
                                    // Lose focus only if helper exists
                                    if (audioFocusHelper != null) {
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
        private AudioManager manager;
        private boolean wasPlaying;
        private AudioFocusRequestCompat request;

        AudioFocusHelper(AudioManager manager) {
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event == null) return super.onTouchEvent(null);
        if (scaleGestureDetector == null) return super.onTouchEvent(event);

        // Pass event to scale detector FIRST
        boolean scaleHandledByDetector = scaleGestureDetector.onTouchEvent(event);
        boolean scalingInProgress = scaleGestureDetector.isInProgress();

        final int action = event.getActionMasked();

        // Reset flags on ACTION_DOWN
        if (action == MotionEvent.ACTION_DOWN) {
            lastTouchX = event.getX();
            lastTouchY = event.getY();
            isDragging = false;
            wasScaling = false; // Reset scaling history flag for the new gesture
            wasDragging = false; // Reset dragging history flag for the new gesture
        }

        boolean dragHandled = false;
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

        if (action == MotionEvent.ACTION_UP && !wasScaling && !wasDragging && !scalingInProgress) {
            if (singleTapListener != null) {
                boolean handled = singleTapListener.onSingleTap(event);
                if (handled) {
                    return true;
                }
            }
        }

        // Determine if the event should be consumed (preventing click)
        // Consume if:
        // 1. Scaling is currently in progress (mid-gesture)
        // 2. Dragging occurred during this MOVE event
        // 3. The action is UP or CANCEL *and* scaling or dragging happened at any point during this gesture sequence
        boolean consumeEvent = scalingInProgress || dragHandled ||
                ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && (wasScaling || wasDragging));

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

                scaleFactor *= detector.getScaleFactor();

                // Limit the scale factor to reasonable bounds
                scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 3.0f));

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
                // If scale is back to normal (or very close), reset to FIT mode and reset position
                if (scaleFactor <= 1.05f) {
                    scaleFactor = 1.0f;
                    resetPosition(); // resetPosition handles internal null check
                    if (videoFrame != null) {
                        videoFrame.setScaleX(1.0f);
                        videoFrame.setScaleY(1.0f);
                        videoFrame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                    }
                }
            }
        }
    }

    /**
     * Resets any applied zoom to default scale and position
     */
    public void resetZoom() {
        scaleFactor = 1.0f;
        resetPosition(); // resetPosition already has a null check for videoFrame
        // Ensure videoFrame exists before resetting scale/mode
        if (videoFrame != null) {
            videoFrame.setScaleX(1.0f);
            videoFrame.setScaleY(1.0f);
            videoFrame.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
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

    private boolean isVerticalMode() {
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
