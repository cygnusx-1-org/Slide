package me.edgan.redditslide.Activities;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.Adapters.RedditGalleryView;
import me.edgan.redditslide.Fragments.BlankFragment;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.ExoVideoView;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Views.ToolbarColorizeHelper;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.ImageSaveUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/**
 * Activity for displaying Reddit gallery content in a vertical scrolling view. Supports downloading
 * images using the Storage Access Framework.
 */
@NullMarked
public class RedditGallery extends BaseSaveActivity implements GalleryParent {

    public static final String SUBREDDIT = "subreddit";
    public static final String GALLERY_URLS = "galleryurls";
    private @Nullable List<GalleryImage> images;
    private int adapterPosition;
    @SuppressWarnings("NullAway.Init")
    public String url;
    public String subreddit = "";
    @SuppressWarnings("NullAway.Init")
    private String submissionTitle;
    public RedditGalleryPagerAdapter gallery;
    @Nullable private static String lastContentUrl; // Track URL for retry after permission

    private static final String TAG = "RedditGallery";

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        } else if (id == R.id.slider) {
            SettingValues.albumSwipe = true;
            SettingValues.prefs.edit().putBoolean(SettingValues.PREF_ALBUM_SWIPE, true).apply();

            Intent i = new Intent(RedditGallery.this, RedditGalleryPager.class);
            i.putExtra(MediaView.ADAPTER_POSITION, adapterPosition);

            if (getIntent().hasExtra(MediaView.SUBMISSION_URL)) {
                i.putExtra(
                        MediaView.SUBMISSION_URL,
                        getIntent().getStringExtra(MediaView.SUBMISSION_URL));
            }
            if (subreddit != null && !subreddit.isEmpty()) {
                i.putExtra(RedditGalleryPager.SUBREDDIT, subreddit);
            }
            if (submissionTitle != null) {
                i.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
            }

            Bundle urlsBundle = new Bundle();
            urlsBundle.putSerializable(RedditGallery.GALLERY_URLS, new ArrayList<>(images));
            i.putExtras(urlsBundle);

            startActivity(i);
            finish();
            return true;
        } else if (id == R.id.grid) {
            requireToolbar().findViewById(R.id.grid).callOnClick();
            return true;
        } else if (id == R.id.comments) {
            String submissionPermalink = getIntent().getStringExtra(MediaView.SUBMISSION_URL);
            boolean openCommentsDirect =
                    getIntent().getBooleanExtra(MediaView.EXTRA_OPEN_COMMENTS_DIRECT, false);
            if (openCommentsDirect && submissionPermalink != null) {
                OpenRedditLink.openUrl(this, "https://reddit.com" + submissionPermalink, false);
            } else {
                SubmissionsView.datachanged(adapterPosition);
            }
            finish();
            return true;
        } else if (id == R.id.external) {
            String url = getIntent().getStringExtra(MediaView.SUBMISSION_URL);
            if (url != null && !url.isEmpty()) {
                LinkUtil.openExternally(url);
            }
            return true;
        } else if (id == R.id.download) {
            if (images != null) {
                int index = 0;
                for (final GalleryImage elem : images) {
                    // An entry whose media_metadata carried no url has nothing to download. Skip
                    // it, but still advance the index so the saved files keep matching positions.
                    final String elemUrl = elem.url;
                    if (elemUrl != null) {
                        if (elem.isAnimated()) {
                            // Handle videos/GIFs using GifUtils
                            GifUtils.cacheSaveGif(
                                    Uri.parse(elemUrl),
                                    this,
                                    subreddit != null ? subreddit : "",
                                    submissionTitle != null ? submissionTitle : "",
                                    true);
                        } else {
                            // Handle static images using existing image download
                            doImageSave(false, elemUrl, index);
                        }
                    }
                    index++;
                }
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.gallery_vertical, menu);

        adapterPosition = getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);
        if (adapterPosition < 0) {
            menu.findItem(R.id.comments).setVisible(false);
        }
        return true;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        overrideSwipeFromAnywhere();
        super.onCreate(savedInstanceState);
        getTheme()
                .applyStyle(
                        new ColorPreferences(this)
                                .getDarkThemeSubreddit(ColorPreferences.FONT_STYLE),
                        true);
        setContentView(R.layout.album);
        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (getIntent().hasExtra(SUBREDDIT)) {
            this.subreddit = MiscUtil.orEmpty(getIntent().getStringExtra(SUBREDDIT));
        }
        if (getIntent().hasExtra(EXTRA_SUBMISSION_TITLE)) {
            this.submissionTitle = MiscUtil.orEmpty(getIntent().getStringExtra(EXTRA_SUBMISSION_TITLE));
        }

        // Extract and verify the gallery URLs
        images = (ArrayList<GalleryImage>) getIntent().getSerializableExtra(RedditGallery.GALLERY_URLS);

        // Debug: Check images array for content
        if (images != null) {
            LogUtil.v("Gallery Images count: " + images.size());
            for (int i = 0; i < images.size(); i++) {
                LogUtil.v("Image " + i + ": " + images.get(i).url + " (animated: " + images.get(i).isAnimated() + ")");
            }
        } else {
            LogUtil.e("Gallery Images is null!");
        }

        final ViewPager pager = (ViewPager) findViewById(R.id.images);
        gallery = new RedditGalleryPagerAdapter(getSupportFragmentManager());
        pager.setAdapter(gallery);
        pager.setCurrentItem(1);

        if (SettingValues.oldSwipeMode) {
            MiscUtil.setupOldSwipeModeBackground(this, pager);

            pager.addOnPageChangeListener(
                new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageScrolled(
                            int position, float positionOffset, int positionOffsetPixels) {
                        if (position == 0 && positionOffsetPixels == 0) {
                            finish();
                        }
                        final RedditGalleryPagerAdapter pagerAdapter =
                                (RedditGalleryPagerAdapter) pager.getAdapter();
                        if (position == 0
                                && pagerAdapter != null
                                && pagerAdapter.blankPage != null) {
                            pagerAdapter.blankPage.doOffset(positionOffset);
                        }
                    }
                }
            );
        }

    }

    private void configureViewPager(final ViewPager pager) {
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {});
    }

    @Override
    protected void onStoragePermissionGranted() {
        if (lastContentUrl != null) {
            doImageSave(false, lastContentUrl, adapterPosition);
            lastContentUrl = null;
        }
    }

    public class RedditGalleryPagerAdapter extends FragmentStatePagerAdapter {
        @SuppressWarnings("NullAway.Init")
        public AlbumFrag gallery;
        @SuppressWarnings("NullAway.Init")
        BlankFragment blankPage;

        RedditGalleryPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            if (SettingValues.oldSwipeMode) {
                if (i == 0) {
                    blankPage = new BlankFragment();
                    return blankPage;
                } else {
                    gallery = new AlbumFrag();
                    return gallery;
                }
            } else {
                gallery = new AlbumFrag();

                return gallery;
            }
        }

        @Override
        public int getCount() {
            if (SettingValues.oldSwipeMode) {
                return 2;
            } else {
                return 1;
            }
        }
    }

    public static class AlbumFrag extends Fragment {
        private int i = 0;
        View rootView;
        @SuppressWarnings("NullAway.Init")
        public RecyclerView recyclerView;
        private void setLastContentUrl(final @Nullable String url) {
            lastContentUrl = url; // Store for potential retry after permission grant
        }

        @Override
        public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
            rootView = inflater.inflate(R.layout.fragment_verticalalbum, container, false);
            final List<GalleryImage> hostImages = ((RedditGallery) getActivity()).images;
            if (hostImages == null) {
                return rootView;
            }

            GalleryImage current = hostImages.get(i);
            final String url = current.getImageUrl();
            this.setLastContentUrl(url);

            final PreCachingLayoutManager mLayoutManager =
                    new PreCachingLayoutManager(getActivity());
            recyclerView = rootView.findViewById(R.id.images);
            recyclerView.setLayoutManager(mLayoutManager);
            final RedditGallery galleryActivity = (RedditGallery) getActivity();
            if (galleryActivity != null) {
                galleryActivity.images =
                        (ArrayList<GalleryImage>)
                                getActivity()
                                        .getIntent()
                                        .getSerializableExtra(RedditGallery.GALLERY_URLS);
                galleryActivity.mToolbar = rootView.findViewById(R.id.toolbar);
                galleryActivity.mToolbar.setTitle(R.string.type_gallery);
                ToolbarColorizeHelper.colorizeToolbar(
                        galleryActivity.mToolbar, Color.WHITE, getActivity());
                galleryActivity.setSupportActionBar(galleryActivity.mToolbar);
                java.util.Objects.requireNonNull(galleryActivity.getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
                galleryActivity.mToolbar.setPopupTheme(
                        new ColorPreferences(getActivity())
                                .getDarkThemeSubreddit(ColorPreferences.FONT_STYLE));
                rootView.post(
                        new Runnable() {
                            @Override
                            public void run() {
                                rootView.findViewById(R.id.progress).setVisibility(View.GONE);

                                // Fix animated content URLs by replacing animated GIFs with their original URL
                                final List<GalleryImage> activityImages = galleryActivity.images;
                                for (int i = 0;
                                        activityImages != null && i < activityImages.size();
                                        i++) {
                                    GalleryImage img = activityImages.get(i);
                                    if (img.isAnimated()) {
                                        String gifUrl = img.url;
                                        activityImages.get(i).url = gifUrl;
                                    }
                                }

                                RedditGalleryView adapter =
                                        new RedditGalleryView(
                                                galleryActivity,
                                                galleryActivity.images == null
                                                        ? new ArrayList<GalleryImage>()
                                                        : galleryActivity.images,
                                                galleryActivity.subreddit,
                                                galleryActivity.submissionTitle);
                                recyclerView.setAdapter(adapter);
                            }
                        });
            }
            return rootView;
        }
    }

    @Override public void doImageSave(boolean isGif, String contentUrl, int index) {
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

    public void showBottomSheetImage(final String contentUrl, final boolean isGif, final int index) {
        // Remember this URL in case we need to request storage permission
        lastContentUrl = contentUrl;

        // Use the same tinted drawables approach
        int[] attrs = new int[] { R.attr.tintColor };
        android.content.res.TypedArray ta = obtainStyledAttributes(attrs);
        int color = ta.getColor(0, android.graphics.Color.WHITE);
        ta.recycle();

        android.graphics.drawable.Drawable external = getResources().getDrawable(R.drawable.ic_open_in_browser);
        android.graphics.drawable.Drawable share = getResources().getDrawable(R.drawable.ic_share);
        android.graphics.drawable.Drawable image = getResources().getDrawable(R.drawable.ic_image);
        android.graphics.drawable.Drawable save = getResources().getDrawable(R.drawable.ic_download);

        // Tint them
        me.edgan.redditslide.util.BlendModeUtil.tintDrawablesAsSrcAtop(
                java.util.Arrays.asList(external, share, image, save), color);

        // Build the bottom sheet
        me.edgan.redditslide.util.BottomSheet.Builder builder = new me.edgan.redditslide.util.BottomSheet.Builder(this)
                .title(contentUrl);

        builder.sheet(2, external, getString(R.string.open_externally));
        builder.sheet(5, share, getString(R.string.submission_link_share));
        if (!isGif) {
            builder.sheet(3, image, getString(R.string.share_image));
        }
        builder.sheet(4, save, getString(R.string.submission_save_image));

        builder.listener((dialog, which) -> {
            switch (which) {
                case 2:
                    // "Open externally"
                    me.edgan.redditslide.util.LinkUtil.openExternally(contentUrl);
                    break;
                case 3:
                    // "Share image"
                    me.edgan.redditslide.util.ShareUtil.shareImage(contentUrl, RedditGallery.this);
                    break;
                case 5:
                    // "Share link"
                    me.edgan.redditslide.Reddit.defaultShareText("", contentUrl, RedditGallery.this);
                    break;
                case 4:
                    // "Save" - same approach as in doImageSave
                    doImageSave(isGif, contentUrl, index);
                    break;
            }
        });

        builder.show();
    }

    /**
     * Loads a gif page's video, or stands the page down when the entry has no url to load.
     *
     * <p>Package-private so its no-url case can be tested; the Gif fragment is the only caller.
     *
     * <p>A gallery entry really can have none: a removed or failed one comes through with
     * media_metadata carrying no "s" node, which leaves every branch of
     * {@link GalleryImage#getImageUrl()} returning null, and nothing routes such an entry away from
     * this page. AsyncLoadGif dereferences the url before it does anything else — {@code formatUrl}'s
     * first statement is {@code s.endsWith("v")} — on its worker thread, where the NPE is uncaught
     * and takes the process down rather than reaching {@code onError()}.
     */
    static void loadVideo(
            final View rootView,
            final Activity host,
            final @Nullable String url,
            final @Nullable String subreddit,
            final @Nullable String submissionTitle) {
        final ProgressBar loader = rootView.findViewById(R.id.gifprogress);
        final TextView size = rootView.findViewById(R.id.size);
        if (url == null) {
            LogUtil.e("RedditGallery: no url for this gallery entry");
            // Both are visible in submission_gifcard_album.xml and only ever hidden by a load
            // completing, so a page that never starts one has to hide them itself.
            loader.setVisibility(View.GONE);
            size.setVisibility(View.GONE);
            return;
        }
        new GifUtils.AsyncLoadGif(
                        host,
                        rootView.findViewById(R.id.gif),
                        loader,
                        false, // closeIfNull
                        true, // autostart
                        size,
                        subreddit,
                        submissionTitle)
                .execute(url);
    }

    // Modify the Gif class to use the interface
    public static class Gif extends Fragment {
        private int position = 0;
        private View gifView;

        // Helper method to get adapter position from activity
        private int getAdapterPositionFromActivity(android.app.Activity activity) {
            if (activity instanceof RedditGallery) {
                return ((RedditGallery) activity).adapterPosition;
            } else if (activity instanceof RedditGalleryPager) {
                return activity.getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);
            }
            return -1;
        }

        // Override this in subclasses to provide appropriate parent
        protected GalleryParent getGalleryParent() {
            return (RedditGallery) getActivity();
        }

        @Override
        public void setUserVisibleHint(boolean isVisibleToUser) {
            super.setUserVisibleHint(isVisibleToUser);
            // If the Fragment is visible or hidden, play or pause the video
            if (this.isVisible() && gifView instanceof me.edgan.redditslide.Views.ExoVideoView) {
                me.edgan.redditslide.Views.ExoVideoView exoVideo = (me.edgan.redditslide.Views.ExoVideoView) gifView;
                if (!isVisibleToUser) {
                    exoVideo.pause();
                    gifView.setVisibility(View.GONE);
                } else {
                    exoVideo.play();
                    gifView.setVisibility(View.VISIBLE);
                }
            }
        }

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Bundle bundle = getArguments();
            if (bundle != null) {
                position = bundle.getInt("page", 0);
            }
        }

        @Override
        public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
            ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.submission_gifcard_album, container, false);

            gifView = rootView.findViewById(R.id.gif);
            gifView.setVisibility(View.VISIBLE);

            final ExoVideoView exoVideoView = (ExoVideoView) gifView;

            gifView.clearFocus();

            // Find and hide the play button in the layout
            ImageView playButton = rootView.findViewById(R.id.playbutton);
            if (playButton != null) {
                playButton.setVisibility(View.GONE);
            }

            // Get the gallery parent interface for data
            GalleryParent galleryParent = getGalleryParent();

            if (galleryParent != null) {
                List<GalleryImage> images = galleryParent.getGalleryImages();

                if (images != null && position < images.size()) {
                    final GalleryImage current = images.get(position);
                    final String url = current.getImageUrl();

                    // Use GifUtils to handle MP4 or GIF
                    loadVideo(
                            rootView,
                            getActivity(),
                            url,
                            galleryParent.getGallerySubreddit(),
                            galleryParent.getGallerySubmissionTitle());

                    // Both handlers are wired only when there is a url for them to act on: they
                    // capture it, and the bottom sheet and the saver both dereference what they are
                    // handed.
                    if (url != null) {
                        // The "more" (overflow) button
                        rootView.findViewById(R.id.more).setOnClickListener(
                                v -> galleryParent.showGalleryBottomSheet(url, true, position)
                        );

                        // The "save" button
                        rootView.findViewById(R.id.save).setOnClickListener(
                                v -> galleryParent.saveGalleryMedia(true, url, position)
                        );

                        // Hide the save button if user preference is off
                        if (!me.edgan.redditslide.SettingValues.imageDownloadButton) {
                            rootView.findViewById(R.id.save).setVisibility(View.INVISIBLE);
                        }
                    } else {
                        rootView.findViewById(R.id.more).setVisibility(View.GONE);
                        rootView.findViewById(R.id.save).setVisibility(View.GONE);
                    }
                    rootView.findViewById(R.id.mute).setVisibility(View.GONE);
                    rootView.findViewById(R.id.hq).setVisibility(View.GONE);

                    ImageView speedButton = rootView.findViewById(R.id.speed);
                    if (speedButton != null) {
                        if (current != null && current.isAnimated()) {
                            speedButton.setVisibility(View.VISIBLE);
                            exoVideoView.attachSpeedButton(speedButton, getActivity());
                        } else {
                            speedButton.setVisibility(View.GONE);
                        }
                    }

                    ImageView rotateButton = rootView.findViewById(R.id.rotate_right);
                    ImageView rotateLeftButton = rootView.findViewById(R.id.rotate_left);
                    if (rotateButton != null && rotateLeftButton != null) {
                        rotateButton.setVisibility(View.VISIBLE);
                        rotateLeftButton.setVisibility(View.VISIBLE);

                        rotateButton.setOnClickListener(view -> exoVideoView.rotateRight());
                        rotateLeftButton.setOnClickListener(view -> exoVideoView.rotateLeft());
                    }

                    // Add comment button logic
                    View comments = rootView.findViewById(R.id.comments);
                    if (comments != null) {
                        if (getActivity().getIntent().hasExtra(MediaView.SUBMISSION_URL)) {
                            final String submissionPermalink =
                                    getActivity()
                                            .getIntent()
                                            .getStringExtra(MediaView.SUBMISSION_URL);
                            final boolean openCommentsDirect =
                                    getActivity()
                                            .getIntent()
                                            .getBooleanExtra(
                                                    MediaView.EXTRA_OPEN_COMMENTS_DIRECT, false);
                            comments.setOnClickListener(v -> {
                                if (openCommentsDirect && submissionPermalink != null) {
                                    OpenRedditLink.openUrl(
                                            getActivity(),
                                            "https://reddit.com" + submissionPermalink,
                                            false);
                                    getActivity().finish();
                                } else {
                                    getActivity().finish();
                                    SubmissionsView.datachanged(
                                            getAdapterPositionFromActivity(getActivity()));
                                }
                            });
                        } else {
                            comments.setVisibility(View.GONE);
                        }
                    }
                }
            }

            return rootView;
        }
    }

    @Override
    public @Nullable List<GalleryImage> getGalleryImages() {
        return images;
    }

    @Override
    public String getGallerySubreddit() {
        return subreddit;
    }

    @Override
    public String getGallerySubmissionTitle() {
        return submissionTitle;
    }

    @Override
    public void showGalleryBottomSheet(String url, boolean isGif, int position) {
        showBottomSheetImage(url, isGif, position);
    }

    @Override
    public void saveGalleryMedia(boolean isGif, String url, int position) {
        doImageSave(isGif, url, position);
    }

    private void showFirstDialog() {
        runOnUiThread(() -> DialogUtil.showFirstDialog(RedditGallery.this));
    }
}
