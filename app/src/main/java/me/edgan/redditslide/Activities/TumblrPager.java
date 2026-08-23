package me.edgan.redditslide.Activities;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.devspark.robototextview.RobotoTypefaces;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.edgan.redditslide.Adapters.ImageGridAdapter;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.Fragments.BlankFragment;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Tumblr.Photo;
import me.edgan.redditslide.Tumblr.PhotoSize;
import me.edgan.redditslide.Tumblr.TumblrUtils;
import me.edgan.redditslide.Views.ExoVideoView;
import me.edgan.redditslide.Views.ImageSource;
import me.edgan.redditslide.Views.SubsamplingScaleImageView;
import me.edgan.redditslide.Views.ToolbarColorizeHelper;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.FileUtil;
import me.edgan.redditslide.util.GifDrawable;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.ImageSaveUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.SubmissionParser;
import org.jspecify.annotations.NullMarked;

/**
 * Created by ccrama on 1/25/2016.
 *
 * <p>This is an extension of Album.java which utilizes a ViewPager for Imgur content instead of a
 * RecyclerView (horizontal vs vertical). It also supports gifs and progress bars which Album.java
 * doesn't.
 */
@NullMarked
public class TumblrPager extends BaseSaveActivity {

    private static int adapterPosition;
    public static final String SUBREDDIT = "subreddit";

    // Add fields to store last save attempt
    @Nullable private String lastContentUrl;
    private int lastIndex = -1;

    @Nullable ViewPager p;

    @Nullable public List<Photo> images;
    public String subreddit = "";

    private static final String TAG = "TumblrPager";

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
        }
        if (id == R.id.vertical) {
            SettingValues.albumSwipe = false;
            SettingValues.prefs.edit().putBoolean(SettingValues.PREF_ALBUM_SWIPE, false).apply();
            Intent i = new Intent(TumblrPager.this, Tumblr.class);
            if (getIntent().hasExtra(MediaView.SUBMISSION_URL)) {
                i.putExtra(
                        MediaView.SUBMISSION_URL,
                        getIntent().getStringExtra(MediaView.SUBMISSION_URL));
            }
            if (getIntent().hasExtra(SUBREDDIT)) {
                i.putExtra(SUBREDDIT, getIntent().getStringExtra(SUBREDDIT));
            }
            i.putExtras(getIntent());
            startActivity(i);
            finish();
        }
        if (id == R.id.grid) {
            requireToolbar().requireViewById(R.id.grid).callOnClick();
        }
        if (id == R.id.external) {
            LinkUtil.openExternally(MiscUtil.orEmpty(getIntent().getStringExtra("url")));
        }

        if (id == R.id.comments) {
            int adapterPosition = getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);
            String submissionPermalink = getIntent().getStringExtra(MediaView.SUBMISSION_URL);
            boolean openCommentsDirect =
                    getIntent().getBooleanExtra(MediaView.EXTRA_OPEN_COMMENTS_DIRECT, false);
            if (openCommentsDirect && submissionPermalink != null) {
                OpenRedditLink.openUrl(this, "https://reddit.com" + submissionPermalink, false);
                finish();
            } else {
                finish();
                SubmissionsView.datachanged(adapterPosition);
            }
        }

        if (id == R.id.download && images != null) {
            int index = 0;
            for (final Photo elem : images) {
                // A photo whose JSON carried no original_size — or that Jackson left null for a null
                // element in the photos array — has no url to save. index still advances so the
                // saved files keep matching album positions: a gap in the numbering, rather than
                // renumbering everything after the skip.
                final String elemUrl = elem == null ? null : elem.getOriginalUrl();
                if (elemUrl != null) {
                    doImageSave(false, elemUrl, index);
                }
                index++;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        overrideSwipeFromAnywhere();
        super.onCreate(savedInstanceState);
        getTheme()
                .applyStyle(
                        new ColorPreferences(this)
                                .getDarkThemeSubreddit(ColorPreferences.FONT_STYLE),
                        true);
        MiscUtil.applyWideColorGamut(this);
        setContentView(R.layout.album_pager);

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mToolbar = (Toolbar) requireViewById(R.id.toolbar);
        mToolbar.setTitle(R.string.type_tumblr);
        ToolbarColorizeHelper.colorizeToolbar(mToolbar, Color.WHITE, this);
        setSupportActionBar(mToolbar);
        java.util.Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        if (getIntent().hasExtra(SUBREDDIT)) {
            this.subreddit = MiscUtil.orEmpty(getIntent().getStringExtra(SUBREDDIT));
        }

        if (getIntent().hasExtra(EXTRA_SUBMISSION_TITLE)) {
            this.submissionTitle = MiscUtil.orEmpty(getIntent().getStringExtra(EXTRA_SUBMISSION_TITLE));
        }

        mToolbar.setPopupTheme(
                new ColorPreferences(this).getDarkThemeSubreddit(ColorPreferences.FONT_STYLE));

        adapterPosition = getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);

        String url = MiscUtil.orEmpty(getIntent().getStringExtra("url"));
        new LoadIntoPager(url, this).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public class LoadIntoPager extends TumblrUtils.GetTumblrPostWithCallback {

        String url;

        public LoadIntoPager(@NonNull String url, @NonNull Activity baseActivity) {
            super(url, baseActivity);
            this.url = url;
        }

        @Override
        public void onError() {
            Intent i = new Intent(TumblrPager.this, Website.class);
            i.putExtra(LinkUtil.EXTRA_URL, url);
            startActivity(i);
            finish();
        }

        @Override
        public boolean doWithData(final @Nullable List<Photo> jsonElements) {
            // A post with no photos has no pages to build; super has already sent this to onError(),
            // which opens the link in the web view and finishes this activity, so there is nothing
            // left to set up here either.
            if (!super.doWithData(jsonElements)) {
                return false;
            }
            requireViewById(R.id.progress).setVisibility(View.GONE);
            images = new ArrayList<>(jsonElements);
            // Captured for the nested callbacks below: they run later, so NullAway cannot prove
            // the fields are still set by then, and a local can.
            final List<Photo> loadedImages = images;

            p = (ViewPager) requireViewById(R.id.images_horizontal);
            final ViewPager loadedPager = p;

            if (getSupportActionBar() != null) {
                java.util.Objects.requireNonNull(getSupportActionBar()).setSubtitle(1 + "/" + loadedImages.size());
            }

            TumblrViewPagerAdapter adapter = new TumblrViewPagerAdapter(getSupportFragmentManager());
            loadedPager.setAdapter(adapter);

            MiscUtil.setupOldSwipeModeBackground(TumblrPager.this, loadedPager);

            int startPage = 0;

            if (SettingValues.oldSwipeMode) {
                startPage = 1;
            }

            loadedPager.setCurrentItem(startPage);

            p.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (loadedImages == null || loadedImages.isEmpty()) {
                                // Don't attempt to load any positions if there are no images
                                return;
                            }

                            // If there is more than one position, load both position 0 and 1.
                            if (adapter.getCount() > 1) {
                                adapter.instantiateItem(loadedPager, 0);
                                adapter.instantiateItem(loadedPager, 1);
                            } else {
                                // Otherwise, only load position 0.
                                adapter.instantiateItem(loadedPager, 0);
                            }
                        }
                    });

            requireViewById(R.id.grid)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    LayoutInflater l = getLayoutInflater();
                                    View body = l.inflate(R.layout.album_grid_dialog, null, false);
                                    GridView gridview = body.requireViewById(R.id.images);
                                    gridview.setAdapter(
                                            new ImageGridAdapter(TumblrPager.this, loadedImages, true));

                                    final AlertDialog.Builder builder =
                                            new AlertDialog.Builder(TumblrPager.this).setView(body);
                                    final Dialog d = builder.create();
                                    gridview.setOnItemClickListener(
                                            new AdapterView.OnItemClickListener() {
                                                @Override public void onItemClick(
                                                        AdapterView<?> parent,
                                                        View v,
                                                        int position,
                                                        long id) {
                                                    loadedPager.setCurrentItem(position + 1);
                                                    d.dismiss();
                                                }
                                            });
                                    DialogUtil.matchDialogToCardBackground(d);
                                    d.show();
                                }
                            });
            loadedPager.addOnPageChangeListener(
                    new ViewPager.SimpleOnPageChangeListener() {
                        @Override
                        public void onPageScrolled(
                                int position, float positionOffset, int positionOffsetPixels) {
                            if (SettingValues.oldSwipeMode) {
                                if (position != 0) {
                                    if (getSupportActionBar() != null) {
                                        java.util.Objects.requireNonNull(getSupportActionBar())
                                                .setSubtitle((position) + "/" + loadedImages.size());
                                    }
                                }
                                if (position == 0 && positionOffset < 0.2) {
                                    finish();
                                }
                            } else {
                                if (getSupportActionBar() != null) {
                                    java.util.Objects.requireNonNull(getSupportActionBar())
                                            .setSubtitle((position + 1) + "/" + loadedImages.size());
                                }
                            }
                        }
                    });
            adapter.notifyDataSetChanged();
            return true;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.album_pager, menu);
        adapterPosition = getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);
        if (adapterPosition < 0) {
            menu.findItem(R.id.comments).setVisible(false);
        }
        return true;
    }

    private class TumblrViewPagerAdapter extends FragmentStatePagerAdapter {

        TumblrViewPagerAdapter(FragmentManager m) {
            super(m, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            if (SettingValues.oldSwipeMode) {
                if (i == 0) {
                    return new BlankFragment();
                }

                i--;
            }

            if (images == null) {
                // The album never loaded. getCount already answers 0 on that path, so this is
                // unreachable in practice; BlankFragment is what the oldSwipeMode branch above
                // already uses for a page with nothing to show.
                return new BlankFragment();
            }
            Photo current = images.get(i);

            // A photo with no original_size — or no photo at all, which Jackson leaves for a null
            // element in the photos array — has no url to classify, and new URI(null) throws NPE
            // rather than the URISyntaxException the catch below expects, so decide before parsing.
            // The image page handles a missing url itself.
            boolean isGif = false;
            final String currentUrl = current == null ? null : current.getOriginalUrl();
            if (currentUrl != null) {
                try {
                    isGif = ContentType.isGif(new URI(currentUrl));
                } catch (URISyntaxException e) {
                    LogUtil.e(e, "TumblrPager.URI failed");
                }
            }

            Fragment f = isGif ? new Gif() : new ImageFullNoSubmission();
            Bundle args = new Bundle();
            args.putInt("page", i);
            f.setArguments(args);

            return f;
        }

        @Override
        public int getCount() {
            if (images == null) {
                return 0;
            }
            if (SettingValues.oldSwipeMode) {
                return images.size() + 1;
            } else {
                return images.size();
            }
        }
    }

    public static class Gif extends Fragment {

        @Nullable private View gif;
        ViewGroup rootView;
        ProgressBar loader;

        @Override
        public void setUserVisibleHint(boolean isVisibleToUser) {
            super.setUserVisibleHint(isVisibleToUser);
            if (this.isVisible()) {
                if (!isVisibleToUser) // If we are becoming invisible, then...
                {
                    if (gif != null) {
                        ((ExoVideoView) gif).pause();
                        gif.setVisibility(View.GONE);
                    }
                }

                if (isVisibleToUser) // If we are becoming visible, then...
                {
                    if (gif != null) {
                        ((ExoVideoView) gif).play();
                        gif.setVisibility(View.VISIBLE);
                    }
                }
            }
        }

        @Override
        public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
            Bundle bundle = requireArguments();
            final int i = bundle.getInt("page", 0);

            rootView = (ViewGroup) inflater.inflate(R.layout.submission_gifcard_album, container, false);
            loader = rootView.requireViewById(R.id.gifprogress);
            final View videoView = rootView.findViewById(R.id.gif); // This is an ExoVideoView

            final List<Photo> albumImages = ((TumblrPager) requireActivity()).images;
            final Photo photo = albumImages == null ? null : albumImages.get(i);
            final String url = photo == null ? null : photo.getOriginalUrl();

            if (url != null && url.toLowerCase(Locale.ENGLISH).endsWith(".gif")) {
                videoView.setVisibility(View.GONE); // Hide ExoVideoView
                View playButton = rootView.findViewById(R.id.playbutton);
                if (playButton != null) {
                    playButton.setVisibility(View.GONE);
                }

                final ImageView imageView = new ImageView(getContext());
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT);
                layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
                imageView.setLayoutParams(layoutParams);

                RelativeLayout imageArea = rootView.requireViewById(R.id.imagearea);
                imageArea.addView(imageView); // Add ImageView to the layout

                loader.setVisibility(View.VISIBLE);

                GifUtils.downloadGif(url, new GifUtils.GifDownloadCallback() {
                    @Override
                    public void onGifDownloaded(File gifFile) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                loader.setVisibility(View.GONE);
                                Movie movie = Movie.decodeFile(gifFile.getAbsolutePath());
                                if (movie != null) {
                                    GifDrawable gifDrawable = new GifDrawable(movie, new Drawable.Callback() {
                                        @Override
                                        public void invalidateDrawable(@NonNull Drawable who) {
                                            imageView.invalidate();
                                        }

                                        @Override
                                        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                                            imageView.postDelayed(what, when - SystemClock.uptimeMillis());
                                        }

                                        @Override
                                        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                                            imageView.removeCallbacks(what);
                                        }
                                    });
                                    imageView.setImageDrawable(gifDrawable);
                                    gifDrawable.start();
                                } else {
                                    // Optionally, show an error or fallback
                                    Log.e(TAG, "Failed to decode GIF: " + url);
                                    if (videoView instanceof ExoVideoView) {
                                    ((ExoVideoView) videoView).setVideoURI(Uri.parse(url), ExoVideoView.VideoType.STANDARD, null); // Fallback to ExoVideoView if Movie decoding fails
                                    ((ExoVideoView) videoView).play();
                                        videoView.setVisibility(View.VISIBLE);
                                        imageView.setVisibility(View.GONE);
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onGifDownloadFailed(Exception e) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                loader.setVisibility(View.GONE);
                                Log.e(TAG, "Failed to download GIF: " + url, e);
                                // Fallback to trying with ExoVideoView or show error
                                if (videoView instanceof ExoVideoView) {
                                   ((ExoVideoView) videoView).setVideoURI(Uri.parse(url), ExoVideoView.VideoType.STANDARD, null);
                                   ((ExoVideoView) videoView).play();
                                    videoView.setVisibility(View.VISIBLE);
                                    imageView.setVisibility(View.GONE);
                                }
                            }
                        });
                    }
                }, requireContext(), null); // Pass null for submissionTitle if not available/needed here

                ImageView rotateRight = rootView.findViewById(R.id.rotate_right);
                ImageView rotateLeft = rootView.findViewById(R.id.rotate_left);
                if (rotateRight != null && rotateLeft != null) {
                    rotateRight.setVisibility(View.VISIBLE);
                    rotateLeft.setVisibility(View.VISIBLE);
                    rotateRight.setOnClickListener(view -> {
                        float next = (imageView.getRotation() + 90f) % 360f;
                        imageView.setRotation(next);
                    });
                    rotateLeft.setOnClickListener(view -> {
                        float next = (imageView.getRotation() - 90f + 360f) % 360f;
                        imageView.setRotation(next);
                    });
                }

                // Direct .gif has no audio track and no quality toggle.
                View muteButton = rootView.findViewById(R.id.mute);
                if (muteButton != null) muteButton.setVisibility(View.GONE);
                View hqButton = rootView.findViewById(R.id.hq);
                if (hqButton != null) hqButton.setVisibility(View.GONE);

            } else { // Not a direct .gif URL, or URL is null, proceed with ExoVideoView
                gif = rootView.findViewById(R.id.gif);
                gif.setVisibility(View.VISIBLE);
                final ExoVideoView v = (ExoVideoView) gif;
                v.clearFocus();

                // The layout ships the play button visible for the vertical list rows, which tap
                // through to MediaView. This page plays inline and autostarts, so the button would
                // just sit on top of the video, as it does in AlbumPager and RedditGallery.
                View playButton = rootView.findViewById(R.id.playbutton);
                if (playButton != null) {
                    playButton.setVisibility(View.GONE);
                }

                ImageView muteButton = rootView.findViewById(R.id.mute);
                if (muteButton != null) {
                    v.attachMuteButton(muteButton);
                }
                ImageView hqButton = rootView.findViewById(R.id.hq);
                if (hqButton != null) {
                    v.attachHqButton(hqButton);
                }

                loadVideo(rootView, requireActivity(), url, ((TumblrPager) requireActivity()).subreddit);

                ImageView rotateRight = rootView.findViewById(R.id.rotate_right);
                ImageView rotateLeft = rootView.findViewById(R.id.rotate_left);
                if (rotateRight != null && rotateLeft != null) {
                    rotateRight.setVisibility(View.VISIBLE);
                    rotateLeft.setVisibility(View.VISIBLE);
                    rotateRight.setOnClickListener(view -> v.rotateRight());
                    rotateLeft.setOnClickListener(view -> v.rotateLeft());
                }
            }

            // Both handlers are wired only when there is a url for them to act on: they capture it,
            // and the bottom sheet and the saver both dereference what they are handed.
            if (url != null) {
                rootView.requireViewById(R.id.more)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        // A click can arrive after the page detaches.
                                        final FragmentActivity activity = getActivity();
                                        if (activity == null) {
                                            return;
                                        }
                                        ((TumblrPager) activity)
                                                .showBottomSheetImage(url, true, i);
                                    }
                                });
                rootView.requireViewById(R.id.save)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        // Call the parent activity's save method
                                        if (getActivity() instanceof TumblrPager) {
                                            ((TumblrPager) getActivity()).doImageSave(true, url, i);
                                        } else {
                                            Log.e(TAG, "Parent activity is not TumblrPager, cannot save.");
                                            // Optionally show a toast or dialog
                                        }
                                    }
                                });
            } else {
                rootView.requireViewById(R.id.more).setVisibility(View.GONE);
                rootView.requireViewById(R.id.save).setVisibility(View.GONE);
            }

            View comments = rootView.findViewById(R.id.comments);
            if (comments != null) {
                if (requireActivity().getIntent().hasExtra(MediaView.SUBMISSION_URL)) {
                    final int adapterPosition =
                            requireActivity()
                                    .getIntent()
                                    .getIntExtra(MediaView.ADAPTER_POSITION, -1);
                    final String submissionPermalink =
                            requireActivity()
                                    .getIntent()
                                    .getStringExtra(MediaView.SUBMISSION_URL);
                    final boolean openCommentsDirect =
                            requireActivity()
                                    .getIntent()
                                    .getBooleanExtra(
                                            MediaView.EXTRA_OPEN_COMMENTS_DIRECT, false);
                    comments.setOnClickListener(v -> {
                        // A detached fragment has no host here; there is nothing to act on.
                        final FragmentActivity activity = getActivity();
                        if (activity == null) {
                            return;
                        }
                        if (openCommentsDirect && submissionPermalink != null) {
                            OpenRedditLink.openUrl(
                                    activity,
                                    "https://reddit.com" + submissionPermalink,
                                    false);
                            activity.finish();
                        } else {
                            activity.finish();
                            SubmissionsView.datachanged(adapterPosition);
                        }
                    });
                } else {
                    comments.setVisibility(View.GONE);
                }
            }

            return rootView;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
        }
    }

    public void showBottomSheetImage(
            final @Nullable String contentUrl, final boolean isGif, final int index) {
        LinkUtil.showImageLinkBottomSheet(
                this, contentUrl, isGif, () -> doImageSave(isGif, contentUrl, index));
    }

    @Override public void doImageSave(boolean isGif, @Nullable String contentUrl, int index) {
        ImageSaveUtils.doImageSave(
                this,
                isGif,
                contentUrl,
                index,
                subreddit,
                submissionTitle,
                this::showFirstDialog
        );
    }

    @Override
    protected void onStoragePermissionGranted() {
        // Retry last save attempt if available
        if (lastContentUrl != null) {
            doImageSave(false, lastContentUrl, lastIndex);
            lastContentUrl = null;
            lastIndex = -1;
        }
    }

    public static class ImageFullNoSubmission extends Fragment {

        private int i = 0;
        private int currentRotation = 0; // Track current rotation in degrees (0, 90, 180, 270)

        public ImageFullNoSubmission() {}

        @Override
        public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
            final ViewGroup rootView =
                    (ViewGroup) inflater.inflate(R.layout.album_image_pager, container, false);

            final List<Photo> albumPhotos = ((TumblrPager) requireActivity()).images;
            final Photo current = albumPhotos == null ? null : albumPhotos.get(i);
            final String url = current == null ? null : current.getOriginalUrl();
            final List<PhotoSize> altSizes = current == null ? null : current.getAltSizes();
            boolean lq = false;
            String lqurl = null;
            if (SettingValues.loadImageLq
                    && (SettingValues.lowResAlways
                            || (!NetworkUtil.isConnectedWifi(requireActivity())
                                    && SettingValues.lowResMobile))
                    && altSizes != null
                    && !altSizes.isEmpty()) {
                lqurl = ImageGridAdapter.sizeUrl(altSizes, altSizes.size() / 2);
            }
            // A null here is a missing alt size, not a missing setting: the chosen entry can be
            // absent from the array or carry no url. Load the original rather than nothing, and
            // leave lq false so the full-quality reload below is not queued for an image that is
            // already at full quality.
            if (lqurl != null) {
                loadImage(rootView, this, lqurl);
                lq = true;
            } else {
                loadImage(rootView, this, url);
            }

            {
                rootView.requireViewById(R.id.more)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        // A click can arrive after the page detaches.
                                        final FragmentActivity activity = getActivity();
                                        if (activity == null) {
                                            return;
                                        }
                                        ((TumblrPager) activity)
                                                .showBottomSheetImage(url, false, i);
                                    }
                                });
                {
                    rootView.requireViewById(R.id.save)
                            .setOnClickListener(
                                    new View.OnClickListener() {

                                        @Override
                                        public void onClick(View v2) {
                                            // A click can arrive after the page detaches.
                                            final FragmentActivity activity = getActivity();
                                            if (activity == null) {
                                                return;
                                            }
                                            ((TumblrPager) activity)
                                                    .doImageSave(false, url, i);
                                        }
                                    });
                    if (!SettingValues.imageDownloadButton) {
                        rootView.requireViewById(R.id.save).setVisibility(View.INVISIBLE);
                    }
                }
            }
            {
                String title = "";
                String description = "";

                if (current != null && current.getCaption() != null) {
                    List<String> text = SubmissionParser.getBlocks(current.getCaption());
                    // A caption can parse to no blocks at all, and indexing one that did threw
                    // inside onCreateView. An empty description leaves the panel hidden below,
                    // which is what a caption with nothing in it should look like.
                    description = text.isEmpty() ? "" : text.get(0).trim();
                }
                if (title.isEmpty() && description.isEmpty()) {
                    rootView.requireViewById(R.id.panel).setVisibility(View.GONE);
                    (rootView.requireViewById(R.id.margin)).setPadding(0, 0, 0, 0);
                } else if (title.isEmpty()) {
                    LinkUtil.setTextWithLinks(description, rootView.requireViewById(R.id.title));
                } else {
                    LinkUtil.setTextWithLinks(title, rootView.requireViewById(R.id.title));
                    LinkUtil.setTextWithLinks(description, rootView.requireViewById(R.id.body));
                }
                {
                    int type = new FontPreferences(requireContext()).getFontTypeComment().getTypeface();
                    Typeface typeface;
                    if (type >= 0) {
                        typeface = RobotoTypefaces.obtainTypeface(requireContext(), type);
                    } else {
                        typeface = Typeface.DEFAULT;
                    }
                    ((SpoilerRobotoTextView) rootView.requireViewById(R.id.body))
                            .setTypeface(typeface);
                }
                {
                    int type = new FontPreferences(requireContext()).getFontTypeTitle().getTypeface();
                    Typeface typeface;
                    if (type >= 0) {
                        typeface = RobotoTypefaces.obtainTypeface(requireContext(), type);
                    } else {
                        typeface = Typeface.DEFAULT;
                    }
                    ((SpoilerRobotoTextView) rootView.requireViewById(R.id.title))
                            .setTypeface(typeface);
                }
                final SlidingUpPanelLayout l = rootView.requireViewById(R.id.sliding_layout);
                rootView.requireViewById(R.id.title)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        l.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
                                    }
                                });
                rootView.requireViewById(R.id.body)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        l.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
                                    }
                                });
            }

            // Set up rotation buttons
            View rotateLeft = rootView.findViewById(R.id.rotate_left);
            View rotateRight = rootView.findViewById(R.id.rotate_right);

            if (rotateLeft != null) {
                rotateLeft.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rotateImageLeft(rootView);
                    }
                });
            }

            if (rotateRight != null) {
                rotateRight.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        rotateImageRight(rootView);
                    }
                });
            }

            if (lq) {
                rootView.findViewById(R.id.hq)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        loadImage(rootView, ImageFullNoSubmission.this, url);
                                        rootView.findViewById(R.id.hq).setVisibility(View.GONE);
                                    }
                                });
            } else {
                rootView.findViewById(R.id.hq).setVisibility(View.GONE);
            }

            if (requireActivity().getIntent().hasExtra(MediaView.SUBMISSION_URL)) {
                final String submissionPermalink =
                        requireActivity().getIntent().getStringExtra(MediaView.SUBMISSION_URL);
                final boolean openCommentsDirect =
                        requireActivity()
                                .getIntent()
                                .getBooleanExtra(MediaView.EXTRA_OPEN_COMMENTS_DIRECT, false);
                rootView.findViewById(R.id.comments)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        // A detached fragment has no host here; there is nothing to act on.
                                        final FragmentActivity activity = getActivity();
                                        if (activity == null) {
                                            return;
                                        }
                                        if (openCommentsDirect && submissionPermalink != null) {
                                            OpenRedditLink.openUrl(
                                                    activity,
                                                    "https://reddit.com" + submissionPermalink,
                                                    false);
                                            activity.finish();
                                        } else {
                                            activity.finish();
                                            SubmissionsView.datachanged(adapterPosition);
                                        }
                                    }
                                });
            } else {
                rootView.findViewById(R.id.comments).setVisibility(View.GONE);
            }

            if (currentRotation != 0) {
                SubsamplingScaleImageView imageView = rootView.findViewById(R.id.image);
                if (imageView != null) {
                    imageView.setOrientation(currentRotation);
                }
            }

            return rootView;
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Bundle bundle = requireArguments();
            i = bundle.getInt("page", 0);
            if (savedInstanceState != null) {
                currentRotation = savedInstanceState.getInt("currentRotation", 0);
            }
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt("currentRotation", currentRotation);
        }

        private void rotateImageLeft(View rootView) {
            currentRotation = (currentRotation - 90 + 360) % 360;
            refreshImageWithRotation(rootView);
        }

        private void rotateImageRight(View rootView) {
            currentRotation = (currentRotation + 90) % 360;
            refreshImageWithRotation(rootView);
        }

        private void refreshImageWithRotation(View rootView) {
            final SubsamplingScaleImageView imageView = rootView.findViewById(R.id.image);
            if (imageView != null) {
                // Set background to black to prevent ghosting
                imageView.setBackgroundColor(android.graphics.Color.BLACK);

                // Recycle the current image to clear any cached state
                imageView.recycle();

                // Apply the rotation and reload the image
                imageView.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded()) return;
                        TumblrPager activity = (TumblrPager) getActivity();
                        if (activity == null || activity.images == null) return;

                        imageView.setOrientation(currentRotation);

                        // Reload the image
                        final Photo current = activity.images.get(i);
                        final String url = current == null ? null : current.getOriginalUrl();
                        final List<PhotoSize> altSizes =
                                current == null ? null : current.getAltSizes();

                        String lqurl = null;
                        if (SettingValues.loadImageLq
                                && (SettingValues.lowResAlways
                                        || (!NetworkUtil.isConnectedWifi(activity)
                                                && SettingValues.lowResMobile))
                                && altSizes != null
                                && !altSizes.isEmpty()) {
                            lqurl = ImageGridAdapter.sizeUrl(altSizes, altSizes.size() / 2);
                        }
                        // Null when the chosen alt size is absent or carries no url; load the
                        // original rather than nothing.
                        if (lqurl != null) {
                            loadImage(rootView, ImageFullNoSubmission.this, lqurl);
                        } else {
                            loadImage(rootView, ImageFullNoSubmission.this, url);
                        }
                    }
                });
            }
        }
    }

    /**
     * Loads one page's video, or stands the page down when the photo has no url to load.
     *
     * <p>Package-private so its no-url case can be tested; the Gif fragment is the only caller.
     *
     * <p>Nothing on this page keeps a null out: it reads its own url null-tolerantly, and the only
     * thing routing a photo with no original_size to the image page instead is a decision made in
     * another class ({@code TumblrViewPagerAdapter.getItem}). That guard is real but remote, and
     * AsyncLoadGif dereferences the url before it does anything else — {@code formatUrl}'s first
     * statement is {@code s.endsWith("v")} — on its worker thread, where the NPE is uncaught and
     * takes the process down rather than reaching {@code onError()}.
     */
    static void loadVideo(
            final View rootView,
            final Activity host,
            final @Nullable String url,
            final @Nullable String subreddit) {
        final ProgressBar loader = rootView.requireViewById(R.id.gifprogress);
        final TextView size = rootView.requireViewById(R.id.size);
        if (url == null) {
            LogUtil.e("TumblrPager: no url for this video page");
            // Both are visible in submission_gifcard_album.xml and only ever hidden by a load
            // completing, so a page that never starts one has to hide them itself.
            loader.setVisibility(View.GONE);
            size.setVisibility(View.GONE);
            return;
        }
        new GifUtils.AsyncLoadGif(
                        host,
                        rootView.findViewById(R.id.gif), // This is the ExoVideoView
                        loader,
                        false, // closeIfNull
                        true, // autostart
                        size,
                        subreddit,
                        null) // Pass null for submissionTitle
                .execute(url);
    }

    /**
     * Loads one page's image.
     *
     * <p>Package-private so its no-url case can be tested; every caller is in this class.
     *
     * <p>A page can have no url to load: a Tumblr photo may carry no original_size at all, and the
     * chosen alt size may carry none of its own. Returning early is not cosmetic — the image loader
     * treats a null or empty uri as a completed load and calls onLoadingComplete with a null bitmap,
     * which ImageSource.bitmap rejects by throwing. That throw comes back out of the listener on the
     * main thread, inside the fragment's onCreateView, so paging onto such a photo took the app down
     * rather than showing an empty page.
     */
    static void loadImage(final View rootView, Fragment f, @Nullable String url) {
        final SubsamplingScaleImageView image = rootView.findViewById(R.id.image);
        image.setMinimumDpi(70);
        image.setMinimumTileDpi(240);
        final TextView size = rootView.requireViewById(R.id.size);
        // A detached page has no Application to reach the image loader through, so nothing will
        // load — the same outcome as having no url, and it has to stop waiting the same way
        // rather than leave the spinner running. The guard sits after the view lookups on
        // purpose: the test for the no-url case drives this with a hostless Fragment.
        final FragmentActivity activity = f.getActivity();
        if (url == null || url.isEmpty() || activity == null) {
            LogUtil.e("TumblrPager: no url for this page");
            // Both of these are visible in album_image_pager.xml and only ever hidden by a load
            // completing, so a page that never loads has to hide them itself.
            size.setVisibility(View.GONE);
            rootView.requireViewById(R.id.progress).setVisibility(View.GONE);
            return;
        }
        ImageView fakeImage = new ImageView(activity);
        fakeImage.setLayoutParams(
                new LinearLayout.LayoutParams(image.getWidth(), image.getHeight()));
        fakeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ((Reddit) activity.getApplication())
                .getImageLoader()
                .displayImage(
                        url,
                        new ImageViewAware(fakeImage),
                        new DisplayImageOptions.Builder()
                                .resetViewBeforeLoading(true)
                                .cacheOnDisk(true)
                                .imageScaleType(ImageScaleType.NONE)
                                .cacheInMemory(false)
                                .build(),
                        new ImageLoadingListener() {

                            @Override
                            public void onLoadingStarted(@Nullable String imageUri, @Nullable View view) {
                                size.setVisibility(View.VISIBLE);
                            }

                            @Override
                            public void onLoadingFailed(
                                    String imageUri, @Nullable View view, FailReason failReason) {
                                Log.v("Slide", "TumblrPager: LOADING FAILED");
                            }

                            @Override
                            public void onLoadingComplete(
                                    @Nullable String imageUri, @Nullable View view, @Nullable Bitmap loadedImage) {
                                size.setVisibility(View.GONE);
                                if (loadedImage == null) {
                                    // A completed load with no bitmap: the loader reports an unusable
                                    // uri that way. ImageSource.bitmap throws on a null, so there is
                                    // nothing to show and nothing to hand it.
                                    (rootView.requireViewById(R.id.progress))
                                            .setVisibility(View.GONE);
                                    return;
                                }
                                image.loader.setImage(ImageSource.bitmap(loadedImage));
                                (rootView.requireViewById(R.id.progress)).setVisibility(View.GONE);
                            }

                            @Override
                            public void onLoadingCancelled(String imageUri, @Nullable View view) {
                                Log.v("Slide", "TumblrPager: LOADING CANCELLED");
                            }
                        },
                        new ImageLoadingProgressListener() {
                            @Override
                            public void onProgressUpdate(
                                    String imageUri, View view, int current, int total) {
                                size.setText(FileUtil.readableFileSize(total));

                                ((ProgressBar) rootView.requireViewById(R.id.progress))
                                        .setProgress(Math.round(100.0f * current / total));
                            }
                        });
    }

    private void showFirstDialog() {
        runOnUiThread(() -> DialogUtil.showFirstDialog(TumblrPager.this));
    }
}
