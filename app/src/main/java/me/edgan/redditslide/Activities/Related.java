package me.edgan.redditslide.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import me.edgan.redditslide.Adapters.ContributionAdapter;
import me.edgan.redditslide.Adapters.SubredditSearchPosts;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Visuals.ColorPreferences;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;
import me.edgan.redditslide.util.MiscUtil;

public class Related extends BaseActivityAnim {

    public static final String EXTRA_URL = "url";

    private int totalItemCount;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private ContributionAdapter adapter;

    private SubredditSearchPosts posts;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    String url;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        overrideSwipeFromAnywhere();
        super.onCreate(savedInstanceState);

        applyColorTheme("");
        setContentView(R.layout.activity_search);

        MiscUtil.setupOldSwipeModeBackground(this, getWindow().getDecorView());

        Intent intent = getIntent();
        if (intent.hasExtra(Intent.EXTRA_TEXT)
                && !intent.getExtras().getString(Intent.EXTRA_TEXT, "").isEmpty()) {
            url = intent.getStringExtra(Intent.EXTRA_TEXT);
        }
        if (intent.hasExtra(EXTRA_URL)) {
            url = intent.getStringExtra(EXTRA_URL);
        }
        if (url == null || url.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("URL is empty")
                    .setMessage("Try again with a different link!")
                    .setCancelable(false)
                    .setPositiveButton(R.string.btn_ok, (dialogInterface, i) -> finish())
                    .show();
        }

        setupAppBar(R.id.toolbar, "Related links", true, true);

        assert mToolbar != null; // it won't be, trust me
        mToolbar.setPopupTheme(new ColorPreferences(this).getFontStyle().getBaseId());

        final RecyclerView rv = ((RecyclerView) findViewById(R.id.vertical_content));
        final PreCachingLayoutManager mLayoutManager = new PreCachingLayoutManager(this);
        rv.setLayoutManager(mLayoutManager);

        rv.addOnScrollListener(
                new ToolbarScrollHideHandler(mToolbar, findViewById(R.id.header)) {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);

                        visibleItemCount = rv.getLayoutManager().getChildCount();
                        totalItemCount = rv.getLayoutManager().getItemCount();
                        if (rv.getLayoutManager() instanceof PreCachingLayoutManager) {
                            pastVisiblesItems =
                                    ((PreCachingLayoutManager) rv.getLayoutManager())
                                            .findFirstVisibleItemPosition();
                        } else {
                            int[] firstVisibleItems = null;
                            firstVisibleItems =
                                    ((CatchStaggeredGridLayoutManager) rv.getLayoutManager())
                                            .findFirstVisibleItemPositions(firstVisibleItems);
                            if (firstVisibleItems != null && firstVisibleItems.length > 0) {
                                pastVisiblesItems = firstVisibleItems[0];
                            }
                        }

                        if (!posts.loading
                                && (visibleItemCount + pastVisiblesItems) + 5 >= totalItemCount) {
                            posts.loading = true;
                            posts.loadMore(adapter, "", "url:" + url, false);
                        }
                    }
                });
        final SwipeRefreshLayout mSwipeRefreshLayout =
                (SwipeRefreshLayout) findViewById(R.id.activity_main_swipe_refresh_layout);

        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors("", this));

        // If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned.
        // So, we estimate the height of the header in dp.
        mSwipeRefreshLayout.setProgressViewOffset(
                false,
                Constants.SINGLE_HEADER_VIEW_OFFSET - Constants.PTR_OFFSET_TOP,
                Constants.SINGLE_HEADER_VIEW_OFFSET + Constants.PTR_OFFSET_BOTTOM);

        mSwipeRefreshLayout.post(
                new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(true);
                    }
                });

        posts = new SubredditSearchPosts("", "url:" + url, this, false);
        adapter = new ContributionAdapter(this, posts, rv);
        rv.setAdapter(adapter);

        posts.bindAdapter(adapter, mSwipeRefreshLayout);
        // TODO catch errors
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        posts.loadMore(adapter, "", "url:" + url, true);
                        // TODO catch errors
                    }
                });
    }
}
