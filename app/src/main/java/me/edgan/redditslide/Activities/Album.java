package me.edgan.redditslide.Activities;

import static me.edgan.redditslide.Notifications.ImageDownloadNotificationService.EXTRA_SUBMISSION_TITLE;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;
import java.util.ArrayList;
import java.util.List;
import me.edgan.redditslide.Adapters.AlbumView;
import me.edgan.redditslide.Fragments.BlankFragment;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.ImgurAlbum.AlbumUtils;
import me.edgan.redditslide.ImgurAlbum.Image;
import me.edgan.redditslide.OpenRedditLink;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Views.ToolbarColorizeHelper;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.util.DialogUtil;
import me.edgan.redditslide.util.ImageSaveUtils;
import me.edgan.redditslide.util.LinkUtil;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;
import org.jspecify.annotations.NullMarked;

/**
 * Created by ccrama on 3/5/2015.
 *
 * <p>This class is responsible for accessing the Imgur api to get the album json data from a URL or
 * Imgur hash. It extends FullScreenActivity and supports swipe from anywhere.
 */
@NullMarked
public class Album extends BaseSaveActivity {
    public static final String EXTRA_URL = "url";
    public static final String SUBREDDIT = "subreddit";
    @Nullable private List<Image> images;
    private int adapterPosition;

    private static final String TAG = "Album";

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
        }

        if (id == R.id.slider) {
            SettingValues.albumSwipe = true;
            SettingValues.prefs.edit().putBoolean(SettingValues.PREF_ALBUM_SWIPE, true).apply();
            Intent i = new Intent(Album.this, AlbumPager.class);
            int adapterPosition = getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);
            i.putExtra(MediaView.ADAPTER_POSITION, adapterPosition);

            if (getIntent().hasExtra(MediaView.SUBMISSION_URL)) {
                i.putExtra(MediaView.SUBMISSION_URL, getIntent().getStringExtra(MediaView.SUBMISSION_URL));
            }

            if (submissionTitle != null) {
                i.putExtra(EXTRA_SUBMISSION_TITLE, submissionTitle);
            }

            i.putExtra("url", url);
            startActivity(i);
            finish();
        }
        if (id == R.id.grid) {
            requireToolbar().requireViewById(R.id.grid).callOnClick();
        }
        if (id == R.id.comments) {
            String submissionPermalink = getIntent().getStringExtra(MediaView.SUBMISSION_URL);
            boolean openCommentsDirect =
                    getIntent().getBooleanExtra(MediaView.EXTRA_OPEN_COMMENTS_DIRECT, false);
            if (openCommentsDirect && submissionPermalink != null) {
                OpenRedditLink.openUrl(this, "https://reddit.com" + submissionPermalink, false);
            } else {
                SubmissionsView.datachanged(adapterPosition);
            }
            finish();
        }
        if (id == R.id.external) {
            LinkUtil.openExternally(url);
        }
        if (id == R.id.download && images != null) {
            int index = 0;
            for (final Image elem : images) {
                doImageSave(false, elem.getImageUrl(), index);
                index++;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override public void doImageSave(boolean isGif, String contentUrl, int index) {
        ImageSaveUtils.doImageSave(this, isGif, contentUrl, index, subreddit, submissionTitle, this::showFirstDialog);
    }

    @Nullable public String url;
    public String subreddit = "";
    @SuppressWarnings("NullAway.Init") // assigned in onCreate
    public String submissionTitle;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.album_vertical, menu);
        adapterPosition = getIntent().getIntExtra(MediaView.ADAPTER_POSITION, -1);

        if (adapterPosition < 0) {
            menu.findItem(R.id.comments).setVisible(false);
        }

        return true;
    }

    public AlbumPagerAdapter album;

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        overrideSwipeFromAnywhere();
        super.onCreate(savedInstanceState);
        getTheme().applyStyle(new ColorPreferences(this).getDarkThemeSubreddit(ColorPreferences.FONT_STYLE), true);
        setContentView(R.layout.album);

        // Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (getIntent().hasExtra(SUBREDDIT)) {
            this.subreddit = MiscUtil.orEmpty(getIntent().getStringExtra(SUBREDDIT));
        }

        if (getIntent().hasExtra(EXTRA_SUBMISSION_TITLE)) {
            this.submissionTitle = MiscUtil.orEmpty(getIntent().getStringExtra(EXTRA_SUBMISSION_TITLE));
        }

        final ViewPager pager = (ViewPager) requireViewById(R.id.images);

        album = new AlbumPagerAdapter(getSupportFragmentManager());
        pager.setAdapter(album);

        pager.setCurrentItem(1);

        if (SettingValues.oldSwipeMode) {
            MiscUtil.setupOldSwipeModeBackground(this, pager);

            pager.addOnPageChangeListener(
                    new ViewPager.SimpleOnPageChangeListener() {
                        @Override
                        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                            if (position == 0 && positionOffsetPixels == 0) {
                                finish();
                            }

                            final AlbumPagerAdapter pagerAdapter =
                                    (AlbumPagerAdapter) pager.getAdapter();
                            if (position == 0
                                    && pagerAdapter != null
                                    && pagerAdapter.blankPage != null) {
                                pagerAdapter.blankPage.doOffset(positionOffset);
                            }
                        }
                    });
        } else {
            pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {});
        }
    }

    public static class AlbumPagerAdapter extends FragmentStatePagerAdapter {
        @SuppressWarnings("NullAway.Init") // assigned in getItem
        public BlankFragment blankPage;
        @SuppressWarnings("NullAway.Init") // assigned in getItem/onCreate
        public AlbumFrag album;

        public AlbumPagerAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            if (SettingValues.oldSwipeMode) {
                if (i == 0) {
                    blankPage = new BlankFragment();
                    return blankPage;
                }
            }

            album = new AlbumFrag();

            return album;
        }

        @Override
        public int getCount() {
            int count = 1;

            if (SettingValues.oldSwipeMode) {
                count = 2;
            }

            return count;
        }
    }

    public static class AlbumFrag extends Fragment {
        View rootView;
        public RecyclerView recyclerView;

        @Override
        public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
            rootView = inflater.inflate(R.layout.fragment_verticalalbum, container, false);

            final PreCachingLayoutManager mLayoutManager = new PreCachingLayoutManager(requireActivity());
            recyclerView = rootView.requireViewById(R.id.images);
            recyclerView.setLayoutManager(mLayoutManager);
            ((Album) requireActivity()).url =
                    MiscUtil.orEmpty(requireActivity().getIntent().getStringExtra(EXTRA_URL));

            new LoadIntoRecycler(((Album) requireActivity()).url, requireActivity()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            ((Album) requireActivity()).mToolbar = rootView.requireViewById(R.id.toolbar);
            ((Album) requireActivity()).requireToolbar().setTitle(R.string.type_album);
            ToolbarColorizeHelper.colorizeToolbar(((Album) requireActivity()).requireToolbar(), Color.WHITE, (requireActivity()));
            ((Album) requireActivity()).setSupportActionBar(((Album) requireActivity()).requireToolbar());
            java.util.Objects.requireNonNull(((Album) requireActivity()).getSupportActionBar())
                    .setDisplayHomeAsUpEnabled(true);

            ((Album) requireActivity()).requireToolbar().setPopupTheme(new ColorPreferences(requireActivity()).getDarkThemeSubreddit(ColorPreferences.FONT_STYLE));
            return rootView;
        }

        public class LoadIntoRecycler extends AlbumUtils.GetAlbumWithCallback {

            String url;

            public LoadIntoRecycler(@NonNull String url, @NonNull Activity baseActivity) {
                super(url, baseActivity);
                this.url = url;
            }

            @Override
            public void onError() {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                // A detached fragment has no host here; there is nothing to act on.
                                final FragmentActivity activity = getActivity();
                                if (activity == null) {
                                    return;
                                }
                                try {
                                    DialogUtil.showWithCardBackground(new AlertDialog.Builder(activity)
                                        .setTitle(R.string.error_album_not_found)
                                        .setMessage(R.string.error_album_not_found_text)
                                        .setNegativeButton(R.string.btn_no, (dialog, which) -> activity.finish())
                                        .setCancelable(false)
                                        .setPositiveButton(
                                            R.string.btn_yes,
                                            (dialog, which) -> {
                                                Intent i = new Intent(getActivity(), Website.class);
                                                i.putExtra(LinkUtil.EXTRA_URL, url);
                                                startActivity(i);
                                                activity.finish();
                                            })
                                        );
                                } catch (Exception e) {
                                    LogUtil.e(e, "Failed to show album-not-found dialog");
                                }
                            }
                        }
                    );
                }
            }

            @Override
            public boolean doWithData(final @Nullable List<Image> jsonElements) {
                // Nothing usable came back, so there is no album to build; super has already told
                // onError(), which offers the link in a web view instead.
                if (!super.doWithData(jsonElements)) {
                    return false;
                }
                if (getActivity() != null) {
                    getActivity().requireViewById(R.id.progress).setVisibility(View.GONE);
                    Album albumActivity = (Album) getActivity();
                    albumActivity.images = new ArrayList<>(jsonElements);
                    AlbumView adapter = new AlbumView(
                        baseActivity,
                        albumActivity.images,
                        albumActivity.subreddit,
                        albumActivity.submissionTitle
                    );
                    recyclerView.setAdapter(adapter);
                }
                return true;
            }
        }
    }

    private void showFirstDialog() {
        runOnUiThread(() -> DialogUtil.showFirstDialog(Album.this));
    }
}
