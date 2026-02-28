package me.edgan.redditslide.Activities;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.cocosw.bottomsheet.BottomSheet;
import com.devspark.robototextview.RobotoTypefaces;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageScaleType;
import com.nostra13.universalimageloader.core.imageaware.ImageViewAware;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

import me.edgan.redditslide.Adapters.ImageGridAdapterTumblr;
import me.edgan.redditslide.ContentType;
import me.edgan.redditslide.Fragments.BlankFragment;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SpoilerRobotoTextView;
import me.edgan.redditslide.Tumblr.Photo;
import me.edgan.redditslide.Tumblr.TumblrUtils;
import me.edgan.redditslide.Views.ExoVideoView;
import me.edgan.redditslide.Views.ImageSource;
import me.edgan.redditslide.Views.SubsamplingScaleImageView;
import me.edgan.redditslide.Views.ToolbarColorizeHelper;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.FontPreferences;
import me.edgan.redditslide.util.BlendModeUtil;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.FileUtil;
import me.edgan.redditslide.util.GifDrawable;
import me.edgan.redditslide.util.GifUtils;
import me.edgan.redditslide.util.ImageSaveUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.ShareUtil;
import me.edgan.redditslide.util.SubmissionParser;
import me.edgan.redditslide.util.MiscUtil;

import java.io.File;
import android.graphics.Movie;
import android.net.Uri;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by ccrama on 1/25/2016.
 *
 * <p>This is an extension of Album.java which utilizes a ViewPager for Imgur content instead of a
 * RecyclerView (horizontal vs vertical). It also supports gifs and progress bars which Album.java
 * doesn't.
 */
public class TumblrPager extends BaseSaveActivity {

    private static int adapterPosition;
    public static final String SUBREDDIT = "subreddit";

    // Add fields to store last save attempt
    private String lastContentUrl;
    private int lastIndex = -1;

    ViewPager p;

    public List<Photo> images;
    public String subreddit;

    private static final String TAG = "TumblrPager";

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            onBackPressed();
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
            mToolbar.findViewById(R.id.grid).callOnClick();
        }
        if (id == R.id.external) {
            LinkUtil.openExternally(getIntent().getExtras().getString("url", ""));
        }

        if (id == R.id.comments) {
            int adapterPosition = getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);
            finish();
            SubmissionsView.datachanged(adapterPosition);
        }

        if (id == R.id.download) {
            int index = 0;
            for (final Photo elem : images) {
                doImageSave(false, elem.getOriginalSize().getUrl(), index);
                index++;
            }
        }

        return super.onOptionsItemSelected(item);
    }

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

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbar.setTitle(R.string.type_tumblr);
        ToolbarColorizeHelper.colorizeToolbar(mToolbar, Color.WHITE, this);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (getIntent().hasExtra(SUBREDDIT)) {
            this.subreddit = getIntent().getStringExtra(SUBREDDIT);
        }

        if (getIntent().hasExtra(EXTRA_SUBMISSION_TITLE)) {
            this.submissionTitle = getIntent().getStringExtra(EXTRA_SUBMISSION_TITLE);
        }

        mToolbar.setPopupTheme(
                new ColorPreferences(this).getDarkThemeSubreddit(ColorPreferences.FONT_STYLE));

        adapterPosition = getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);

        String url = getIntent().getExtras().getString("url", "");
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
        public void doWithData(final List<Photo> jsonElements) {
            super.doWithData(jsonElements);
            findViewById(R.id.progress).setVisibility(View.GONE);
            images = new ArrayList<>(jsonElements);

            p = (ViewPager) findViewById(R.id.images_horizontal);

            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle(1 + "/" + images.size());
            }

            TumblrViewPagerAdapter adapter = new TumblrViewPagerAdapter(getSupportFragmentManager());
            p.setAdapter(adapter);

            MiscUtil.setupOldSwipeModeBackground(TumblrPager.this, p);

            int startPage = 0;

            if (SettingValues.oldSwipeMode) {
                startPage = 1;
            }

            p.setCurrentItem(startPage);

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
                                            new ImageGridAdapterTumblr(TumblrPager.this, images));

                                    final AlertDialog.Builder builder =
                                            new AlertDialog.Builder(TumblrPager.this).setView(body);
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
            p.addOnPageChangeListener(
                    new ViewPager.SimpleOnPageChangeListener() {
                        @Override
                        public void onPageScrolled(
                                int position, float positionOffset, int positionOffsetPixels) {
                            if (SettingValues.oldSwipeMode) {
                                if (position != 0) {
                                    if (getSupportActionBar() != null) {
                                        getSupportActionBar()
                                                .setSubtitle((position) + "/" + images.size());
                                    }
                                }
                                if (position == 0 && positionOffset < 0.2) {
                                    finish();
                                }
                            } else {
                                if (getSupportActionBar() != null) {
                                    getSupportActionBar()
                                            .setSubtitle((position + 1) + "/" + images.size());
                                }
                            }
                        }
                    });
            adapter.notifyDataSetChanged();
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

            Photo current = images.get(i);

            try {
                if (ContentType.isGif(new URI(current.getOriginalSize().getUrl()))) {
                    // do gif stuff
                    Fragment f = new Gif();
                    Bundle args = new Bundle();
                    args.putInt("page", i);
                    f.setArguments(args);

                    return f;
                } else {
                    Fragment f = new ImageFullNoSubmission();
                    Bundle args = new Bundle();
                    args.putInt("page", i);
                    f.setArguments(args);

                    return f;
                }
            } catch (URISyntaxException e) {
                Fragment f = new ImageFullNoSubmission();
                Bundle args = new Bundle();
                args.putInt("page", i);
                f.setArguments(args);

                return f;
            }
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

        private int i = 0;
        private View gif;
        ViewGroup rootView;
        ProgressBar loader;

        @Override
        public void setUserVisibleHint(boolean isVisibleToUser) {
            super.setUserVisibleHint(isVisibleToUser);
            if (this.isVisible()) {
                if (!isVisibleToUser) // If we are becoming invisible, then...
                {
                    ((ExoVideoView) gif).pause();
                    gif.setVisibility(View.GONE);
                }

                if (isVisibleToUser) // If we are becoming visible, then...
                {
                    ((ExoVideoView) gif).play();
                    gif.setVisibility(View.VISIBLE);
                }
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            Bundle bundle = this.getArguments();
            final int i = bundle.getInt("page", 0);

            rootView = (ViewGroup) inflater.inflate(R.layout.submission_gifcard_album, container, false);
            loader = rootView.findViewById(R.id.gifprogress);
            final View videoView = rootView.findViewById(R.id.gif); // This is an ExoVideoView

            final String url = ((TumblrPager) getActivity()).images.get(i).getOriginalSize().getUrl();

            if (url != null && url.toLowerCase().endsWith(".gif")) {
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

                RelativeLayout imageArea = rootView.findViewById(R.id.imagearea);
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
                }, getContext(), null); // Pass null for submissionTitle if not available/needed here

            } else { // Not a direct .gif URL, or URL is null, proceed with ExoVideoView
                gif = rootView.findViewById(R.id.gif);
                gif.setVisibility(View.VISIBLE);
                final ExoVideoView v = (ExoVideoView) gif;
                v.clearFocus();

                new GifUtils.AsyncLoadGif(
                        getActivity(),
                        rootView.findViewById(R.id.gif), // This is the ExoVideoView
                        loader,
                        null, // placeholder
                        false, // closeIfNull
                        true, // autostart
                        rootView.findViewById(R.id.size),
                        ((TumblrPager) getActivity()).subreddit,
                        null) // Pass null for submissionTitle
                        .execute(url);
            }

            rootView.findViewById(R.id.more)
                    .setOnClickListener(
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    ((TumblrPager) getActivity())
                                            .showBottomSheetImage(url, true, i);
                                }
                            });
            rootView.findViewById(R.id.save)
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
            View mute = rootView.findViewById(R.id.mute);
            if (mute != null) {
                mute.setVisibility(View.GONE);
            }
            View hq = rootView.findViewById(R.id.hq);
            if (hq != null) {
                hq.setVisibility(View.GONE);
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

    public void showBottomSheetImage(
            final String contentUrl, final boolean isGif, final int index) {

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
        BottomSheet.Builder b = new BottomSheet.Builder(this).title(contentUrl);

        b.sheet(2, external, getString(R.string.open_externally));
        b.sheet(5, share, getString(R.string.submission_link_share));
        if (!isGif) b.sheet(3, image, getString(R.string.share_image));
        b.sheet(4, save, getString(isGif ? R.string.submission_save_mp4 : R.string.submission_save_image));
        b.listener(
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case (2):
                                {
                                    LinkUtil.openExternally(contentUrl);
                                }
                                break;
                            case (3):
                                {
                                    ShareUtil.shareImage(contentUrl, TumblrPager.this);
                                }
                                break;
                            case (5):
                                {
                                    Reddit.defaultShareText("", contentUrl, TumblrPager.this);
                                }
                                break;
                            case (4):
                                {
                                    doImageSave(isGif, contentUrl, index);
                                }
                                break;
                        }
                    }
                });

        b.show();
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

        public ImageFullNoSubmission() {}

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            final ViewGroup rootView =
                    (ViewGroup) inflater.inflate(R.layout.album_image_pager, container, false);

            final Photo current = ((TumblrPager) getActivity()).images.get(i);
            final String url = current.getOriginalSize().getUrl();
            boolean lq = false;
            if (SettingValues.loadImageLq
                    && (SettingValues.lowResAlways
                            || (!NetworkUtil.isConnectedWifi(getActivity())
                                    && SettingValues.lowResMobile))
                    && current.getAltSizes() != null
                    && !current.getAltSizes().isEmpty()) {
                String lqurl = current.getAltSizes().get(current.getAltSizes().size() / 2).getUrl();
                loadImage(rootView, this, lqurl);
                lq = true;
            } else {
                loadImage(rootView, this, url);
            }

            {
                rootView.findViewById(R.id.more)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        ((TumblrPager) getActivity())
                                                .showBottomSheetImage(url, false, i);
                                    }
                                });
                {
                    rootView.findViewById(R.id.save)
                            .setOnClickListener(
                                    new View.OnClickListener() {

                                        @Override
                                        public void onClick(View v2) {
                                            ((TumblrPager) getActivity())
                                                    .doImageSave(false, url, i);
                                        }
                                    });
                    if (!SettingValues.imageDownloadButton) {
                        rootView.findViewById(R.id.save).setVisibility(View.INVISIBLE);
                    }
                }
            }
            {
                String title = "";
                String description = "";

                if (current.getCaption() != null) {
                    List<String> text = SubmissionParser.getBlocks(current.getCaption());
                    description = text.get(0).trim();
                }
                if (title.isEmpty() && description.isEmpty()) {
                    rootView.findViewById(R.id.panel).setVisibility(View.GONE);
                    (rootView.findViewById(R.id.margin)).setPadding(0, 0, 0, 0);
                } else if (title.isEmpty()) {
                    LinkUtil.setTextWithLinks(description, rootView.findViewById(R.id.title));
                } else {
                    LinkUtil.setTextWithLinks(title, rootView.findViewById(R.id.title));
                    LinkUtil.setTextWithLinks(description, rootView.findViewById(R.id.body));
                }
                {
                    int type = new FontPreferences(getContext()).getFontTypeComment().getTypeface();
                    Typeface typeface;
                    if (type >= 0) {
                        typeface = RobotoTypefaces.obtainTypeface(getContext(), type);
                    } else {
                        typeface = Typeface.DEFAULT;
                    }
                    ((SpoilerRobotoTextView) rootView.findViewById(R.id.body))
                            .setTypeface(typeface);
                }
                {
                    int type = new FontPreferences(getContext()).getFontTypeTitle().getTypeface();
                    Typeface typeface;
                    if (type >= 0) {
                        typeface = RobotoTypefaces.obtainTypeface(getContext(), type);
                    } else {
                        typeface = Typeface.DEFAULT;
                    }
                    ((SpoilerRobotoTextView) rootView.findViewById(R.id.title))
                            .setTypeface(typeface);
                }
                final SlidingUpPanelLayout l = rootView.findViewById(R.id.sliding_layout);
                rootView.findViewById(R.id.title)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        l.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
                                    }
                                });
                rootView.findViewById(R.id.body)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        l.setPanelState(SlidingUpPanelLayout.PanelState.EXPANDED);
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

            if (getActivity().getIntent().hasExtra(MediaView.SUBMISSION_URL)) {
                rootView.findViewById(R.id.comments)
                        .setOnClickListener(
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        getActivity().finish();
                                        SubmissionsView.datachanged(adapterPosition);
                                    }
                                });
            } else {
                rootView.findViewById(R.id.comments).setVisibility(View.GONE);
            }
            View mute = rootView.findViewById(R.id.mute);
            if (mute != null) {
                mute.setVisibility(View.GONE);
            }
            View hq = rootView.findViewById(R.id.hq);
            if (hq != null) {
                hq.setVisibility(View.GONE);
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

    private static void loadImage(final View rootView, Fragment f, String url) {
        final SubsamplingScaleImageView image = rootView.findViewById(R.id.image);
        image.setMinimumDpi(70);
        image.setMinimumTileDpi(240);
        ImageView fakeImage = new ImageView(f.getActivity());
        final TextView size = rootView.findViewById(R.id.size);
        fakeImage.setLayoutParams(
                new LinearLayout.LayoutParams(image.getWidth(), image.getHeight()));
        fakeImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ((Reddit) f.getActivity().getApplication())
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
                            public void onLoadingStarted(String imageUri, View view) {
                                size.setVisibility(View.VISIBLE);
                            }

                            @Override
                            public void onLoadingFailed(
                                    String imageUri, View view, FailReason failReason) {
                                Log.v("Slide", "TumblrPager: LOADING FAILED");
                            }

                            @Override
                            public void onLoadingComplete(
                                    String imageUri, View view, Bitmap loadedImage) {
                                size.setVisibility(View.GONE);
                                image.loader.setImage(ImageSource.bitmap(loadedImage));
                                (rootView.findViewById(R.id.progress)).setVisibility(View.GONE);
                            }

                            @Override
                            public void onLoadingCancelled(String imageUri, View view) {
                                Log.v("Slide", "TumblrPager: LOADING CANCELLED");
                            }
                        },
                        new ImageLoadingProgressListener() {
                            @Override
                            public void onProgressUpdate(
                                    String imageUri, View view, int current, int total) {
                                size.setText(FileUtil.readableFileSize(total));

                                ((ProgressBar) rootView.findViewById(R.id.progress))
                                        .setProgress(Math.round(100.0f * current / total));
                            }
                        });
    }

    private void showFirstDialog() {
        runOnUiThread(() -> DialogUtil.showFirstDialog(TumblrPager.this));
    }
}
