package me.edgan.redditslide.Activities;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.cocosw.bottomsheet.BottomSheet;

import me.edgan.redditslide.Adapters.ImageGridAdapter;
import me.edgan.redditslide.Fragments.BlankFragment;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.ToolbarColorizeHelper;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.ImageSaveUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.ShareUtil;
import me.edgan.redditslide.util.MiscUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ccrama on 11/7/2020
 * Displays gallery content in a horizontal paging view. This class extends BaseSaveActivity to use
 * the Storage Access Framework for saving images, replacing the old file-based approach.
 */
public class RedditGalleryPager extends BaseSaveActivity implements GalleryParent {

    private static int adapterPosition;
    public static final String SUBREDDIT = "subreddit";
    ViewPager p;
    public List<GalleryImage> images;
    private BottomSheet.Builder bottomSheetBuilder;
    private String lastContentUrl; // Track URL for retry after permission
    private int lastIndex = -1; // Track index for retry after permission

    private static final String TAG = "RedditGalleryPager";

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                onBackPressed();
                return true;

            case R.id.vertical:
                SettingValues.albumSwipe = false;
                SettingValues.prefs
                        .edit()
                        .putBoolean(SettingValues.PREF_ALBUM_SWIPE, false)
                        .apply();

                Intent i = new Intent(RedditGalleryPager.this, RedditGallery.class);
                if (getIntent().hasExtra(MediaView.SUBMISSION_URL)) {
                    i.putExtra(
                            MediaView.SUBMISSION_URL,
                            getIntent().getStringExtra(MediaView.SUBMISSION_URL));
                }
                if (getIntent().hasExtra(SUBREDDIT)) {
                    i.putExtra(SUBREDDIT, getIntent().getStringExtra(SUBREDDIT));
                }
                if (submissionTitle != null) {
                    i.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
                }
                i.putExtras(getIntent());

                Bundle urlsBundle = new Bundle();
                urlsBundle.putSerializable(RedditGallery.GALLERY_URLS, new ArrayList<>(images));
                i.putExtras(urlsBundle);

                startActivity(i);
                finish();
                return true;

            case R.id.grid:
                showGridView();
                return true;

            case R.id.external:
                String url = getIntent().getStringExtra(MediaView.SUBMISSION_URL);
                if (url != null && !url.isEmpty()) {
                    LinkUtil.openExternally(url);
                }
                return true;

            case R.id.comments:
                int adapterPosition = getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);
                finish();
                SubmissionsView.datachanged(adapterPosition);
                return true;

            case R.id.download:
                if (images != null) {
                    int index = 0;
                    Log.d(TAG, "Download - submission title: " + (submissionTitle != null ? submissionTitle : "null"));
                    for (final GalleryImage elem : images) {
                        if (elem.isAnimated()) {
                            // Handle videos/GIFs using GifUtils
                            GifUtils.cacheSaveGif(
                                    Uri.parse(elem.url),
                                    this,
                                    subreddit != null ? subreddit : "",
                                    submissionTitle != null ? submissionTitle : "",
                                    true);
                        } else {
                            // Handle static images using existing image download
                            doImageSave(false, elem.url, index);
                        }
                        index++;
                    }
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        overrideSwipeFromAnywhere();
        super.onCreate(savedInstanceState);
        getTheme()
                .applyStyle(
                        new ColorPreferences(this)
                                .getDarkThemeSubreddit(ColorPreferences.FONT_STYLE),
                        true);
        setContentView(R.layout.album_pager);

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (getIntent().hasExtra(SUBREDDIT)) {
            this.subreddit = getIntent().getStringExtra(SUBREDDIT);
        }

        if (getIntent().hasExtra(EXTRA_SUBMISSION_TITLE)) {
            this.submissionTitle = getIntent().getStringExtra(EXTRA_SUBMISSION_TITLE);
            Log.d(TAG, "Intent has EXTRA_SUBMISSION_TITLE: " + submissionTitle);
        } else {
            Log.d(TAG, "Intent is missing EXTRA_SUBMISSION_TITLE");
            // Try to get from the original extras bundle too
            Bundle extras = getIntent().getExtras();
            if (extras != null && extras.containsKey(EXTRA_SUBMISSION_TITLE)) {
                this.submissionTitle = extras.getString(EXTRA_SUBMISSION_TITLE);
                Log.d(TAG, "Found title in extras bundle: " + submissionTitle);
            }
        }

        setupToolbar();

        adapterPosition = getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);

        findViewById(R.id.progress).setVisibility(View.GONE);
        images =
                (ArrayList<GalleryImage>)
                        getIntent().getSerializableExtra(RedditGallery.GALLERY_URLS);

        p = (ViewPager) findViewById(R.id.images_horizontal);
        p.setOffscreenPageLimit(2);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(1 + "/" + images.size());
        }

        GalleryViewPagerAdapter adapter = new GalleryViewPagerAdapter(getSupportFragmentManager());
        p.setAdapter(adapter);

        MiscUtil.setupOldSwipeModeBackground(this, p);

        p.post(
                new Runnable() {
                    @Override
                    public void run() {
                        if (images == null || images.isEmpty()) {
                            // Don't attempt to load any positions if there are no images
                            return;
                        }

                        // If there is more than one position, load both position 0 and 1.
                        if (adapter.getCount() > 1) {
                            adapter.instantiateItem(p, 0);
                            adapter.instantiateItem(p, 1);
                        } else {
                            // Otherwise, only load position 0.
                            adapter.instantiateItem(p, 0);
                        }
                    }
                });

        findViewById(R.id.grid)
                .setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                LayoutInflater l = getLayoutInflater();
                                View body = l.inflate(R.layout.album_grid_dialog, null, false);
                                GridView gridview = body.findViewById(R.id.images);
                                gridview.setAdapter(
                                        new ImageGridAdapter(
                                                RedditGalleryPager.this, true, images));

                                final AlertDialog.Builder builder =
                                        new AlertDialog.Builder(RedditGalleryPager.this)
                                                .setView(body);
                                final Dialog d = builder.create();
                                gridview.setOnItemClickListener(
                                        new AdapterView.OnItemClickListener() {
                                            public void onItemClick(
                                                    AdapterView<?> parent,
                                                    View v,
                                                    int position,
                                                    long id) {
                                                p.setCurrentItem(position + 1);
                                                d.dismiss();
                                            }
                                        });
                                d.show();
                            }
                        });

        int startPage = 0;

        if (SettingValues.oldSwipeMode) {
            startPage = 1;
        }

        p.setCurrentItem(startPage);

        setupGridAndPagerListeners(adapter);
    }

    private void setupToolbar() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbar.setTitle(R.string.type_gallery);
        ToolbarColorizeHelper.colorizeToolbar(mToolbar, Color.WHITE, this);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mToolbar.setPopupTheme(
                new ColorPreferences(this).getDarkThemeSubreddit(ColorPreferences.FONT_STYLE));
    }

    private void setupGridAndPagerListeners(final GalleryViewPagerAdapter adapter) {
        findViewById(R.id.grid).setOnClickListener(v -> showGridView());

        if (SettingValues.oldSwipeMode) {
            p.addOnPageChangeListener(
                    new ViewPager.SimpleOnPageChangeListener() {
                        @Override
                        public void onPageScrolled(
                                int position, float positionOffset, int positionOffsetPixels) {
                            if (position != 0) {
                                if (getSupportActionBar() != null) {
                                    getSupportActionBar()
                                            .setSubtitle((position) + "/" + images.size());
                                }
                            }
                            if (position == 0 && positionOffset < 0.2) {
                                finish();
                            }
                        }
                    });
        } else {
            p.addOnPageChangeListener(
                    new ViewPager.SimpleOnPageChangeListener() {
                        @Override
                        public void onPageScrolled(
                                int position, float positionOffset, int positionOffsetPixels) {
                            if (getSupportActionBar() != null) {
                                getSupportActionBar()
                                        .setSubtitle((position + 1) + "/" + images.size());
                            }
                        }
                    });
        }
        adapter.notifyDataSetChanged();
    }

    private void showGridView() {
        LayoutInflater l = getLayoutInflater();
        View body = l.inflate(R.layout.album_grid_dialog, null, false);
        GridView gridview = body.findViewById(R.id.images);
        gridview.setAdapter(new ImageGridAdapter(RedditGalleryPager.this, true, images));

        final AlertDialog.Builder builder =
                new AlertDialog.Builder(RedditGalleryPager.this).setView(body);
        final Dialog d = builder.create();

        gridview.setOnItemClickListener(
                (parent, v, position, id) -> {
                    int targetPosition = position;
                    if (SettingValues.oldSwipeMode) {
                        // When oldSwipeMode is on, ViewPager has a blank page at index 0,
                        // so the actual content starts from index 1.
                        // The grid items are 0-indexed based on content, so add 1.
                        targetPosition = position + 1;
                    } else {
                        // Without oldSwipeMode, ViewPager items are 0-indexed directly.
                        targetPosition = position;
                    }
                    p.setCurrentItem(targetPosition);
                    d.dismiss();
                });

        d.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.gallery_pager, menu);
        adapterPosition = getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);
        if (adapterPosition < 0) {
            menu.findItem(R.id.comments).setVisible(false);
        }
        return true;
    }

    public void showBottomSheetImage(final String contentUrl, final boolean isGif, final int index) {
        lastContentUrl = contentUrl; // Store for potential retry after permission grant

        int[] attrs = new int[] {R.attr.tintColor};
        TypedArray ta = obtainStyledAttributes(attrs);

        int color = ta.getColor(0, Color.WHITE);
        Drawable external = getResources().getDrawable(R.drawable.ic_open_in_browser);
        Drawable share = getResources().getDrawable(R.drawable.ic_share);
        Drawable image = getResources().getDrawable(R.drawable.ic_image);
        Drawable save = getResources().getDrawable(R.drawable.ic_download);

        final List<Drawable> drawableSet = Arrays.asList(external, share, image, save);
        BlendModeUtil.tintDrawablesAsSrcAtop(drawableSet, color);

        ta.recycle();
        bottomSheetBuilder = new BottomSheet.Builder(this).title(contentUrl);

        bottomSheetBuilder.sheet(2, external, getString(R.string.open_externally));
        bottomSheetBuilder.sheet(5, share, getString(R.string.submission_link_share));
        if (!isGif) {
            bottomSheetBuilder.sheet(3, image, getString(R.string.share_image));
        }
        bottomSheetBuilder.sheet(4, save, getString(R.string.submission_save_image));

        bottomSheetBuilder.listener(
                (dialog, which) -> {
                    switch (which) {
                        case 2:
                            LinkUtil.openExternally(contentUrl);
                            break;
                        case 3:
                            ShareUtil.shareImage(contentUrl, RedditGalleryPager.this);
                            break;
                        case 5:
                            Reddit.defaultShareText("", contentUrl, RedditGalleryPager.this);
                            break;
                        case 4:
                            doImageSave(isGif, contentUrl, index);
                            break;
                    }
                });

        bottomSheetBuilder.show();
    }

    @Override
    protected void onStoragePermissionGranted() {
        if (lastContentUrl != null) {
            doImageSave(false, lastContentUrl, p.getCurrentItem());
            lastContentUrl = null;
        }
    }

    private class GalleryViewPagerAdapter extends FragmentStatePagerAdapter {
        GalleryViewPagerAdapter(FragmentManager m) {
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

            GalleryImage current = images.get(i);

            Fragment f;
            if (current.isAnimated()) {
                f = new GalleryGifFragment();  // Use adapter class that wraps RedditGallery.Gif
            } else {
                f = new ImageFullNoSubmission();
            }

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

    public static class ImageFullNoSubmission extends Fragment {

        private int i = 0;

        public ImageFullNoSubmission() {}

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            final ViewGroup rootView =
                    (ViewGroup) inflater.inflate(R.layout.album_image_pager, container, false);

            final GalleryImage current = ((RedditGalleryPager) getActivity()).images.get(i);
            final String url = current.url;

            if (SettingValues.loadImageLq
                    && (SettingValues.lowResAlways
                            || (!NetworkUtil.isConnectedWifi(getActivity())
                                    && SettingValues.lowResMobile))) {
                String lqurl =
                        url.substring(0, url.lastIndexOf("."))
                                + (SettingValues.lqLow ? "m" : (SettingValues.lqMid ? "l" : "h"))
                                + url.substring(url.lastIndexOf("."));
                AlbumPager.loadImage(
                        rootView,
                        this,
                        lqurl,
                        ((RedditGalleryPager) getActivity()).images.size() == 1);
            } else {
                AlbumPager.loadImage(
                        rootView,
                        this,
                        url,
                        ((RedditGalleryPager) getActivity()).images.size() == 1);
            }

            View more = rootView.findViewById(R.id.more);
            if (more != null) {
                more.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                ((RedditGalleryPager) getActivity())
                                        .showBottomSheetImage(url, false, i);
                            }
                        });
            }
            View save = rootView.findViewById(R.id.save);
            if (save != null) {
                save.setOnClickListener(
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v2) {
                                ((RedditGalleryPager) getActivity()).doImageSave(false, url, i);
                            }
                        });
                if (!SettingValues.imageDownloadButton) {
                    save.setVisibility(View.INVISIBLE);
                }
            }
            View panel = rootView.findViewById(R.id.panel);
            if (panel != null) {
                panel.setVisibility(View.GONE);
            }
            View margin = rootView.findViewById(R.id.margin);
            if (margin != null) {
                margin.setPadding(0, 0, 0, 0);
            }
            View hq = rootView.findViewById(R.id.hq);
            if (hq != null) {
                hq.setVisibility(View.GONE);
            }
            View mute = rootView.findViewById(R.id.mute);
            if (mute != null) {
                mute.setVisibility(View.GONE);
            }
            View comments = rootView.findViewById(R.id.comments);
            if (getActivity().getIntent().hasExtra(MediaView.SUBMISSION_URL)) {
                if (comments != null) {
                    comments.setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    getActivity().finish();
                                    SubmissionsView.datachanged(adapterPosition);
                                }
                            });
                }
            } else {
                if (comments != null) {
                    comments.setVisibility(View.GONE);
                }
            }
            return rootView;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Bundle bundle = this.getArguments();
            i = bundle.getInt("page", 0);
        }
    }

    public void doImageSave(boolean isGif, String contentUrl, int index) {
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

    private void showFirstDialog() {
        runOnUiThread(() -> DialogUtil.showFirstDialog(RedditGalleryPager.this));
    }

    private void showErrorDialog() {
        runOnUiThread(() -> DialogUtil.showErrorDialog(RedditGalleryPager.this));
    }

    // A wrapper Fragment that uses RedditGallery.Gif internally
    public static class GalleryGifFragment extends RedditGallery.Gif {
        // This method will be called by RedditGallery.Gif to get gallery data
        @Override
        protected GalleryParent getGalleryParent() {
            return (RedditGalleryPager) getActivity();
        }
    }

    // Implement the GalleryParent interface for use by the Gif class
    @Override
    public List<GalleryImage> getGalleryImages() {
        return images;
    }

    @Override
    public String getGallerySubreddit() {
        return subreddit;
    }

    @Override
    public String getGallerySubmissionTitle() {
        Log.d(TAG, "getGallerySubmissionTitle called, returning: " + (submissionTitle != null ? "'" + submissionTitle + "'" : "null"));
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
}
