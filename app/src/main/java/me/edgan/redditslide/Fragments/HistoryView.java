package me.edgan.redditslide.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.HashSet;
import java.util.Set;
import me.edgan.redditslide.Adapters.ContributionAdapter;
import me.edgan.redditslide.Adapters.HistoryPosts;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;
import me.edgan.redditslide.util.PhotoLoader;

public class HistoryView extends Fragment {

    private int totalItemCount;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private ContributionAdapter adapter;
    private HistoryPosts posts;
    private RecyclerView rv;
    // Tap-target prefetch: full-names already warmed, so repeated settles don't re-warm the same rows.
    private final Set<String> warmedTapTargets = new HashSet<>();
    private SwipeRefreshLayout swipeRefreshLayout;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_verticalcontent, container, false);

        rv = v.findViewById(R.id.vertical_content);

        final PreCachingLayoutManager mLayoutManager = new PreCachingLayoutManager(getContext());

        rv.setLayoutManager(mLayoutManager);
        rv.setItemViewCacheSize(2);
        v.findViewById(R.id.post_floating_action_button).setVisibility(View.GONE);
        swipeRefreshLayout = v.findViewById(R.id.activity_main_swipe_refresh_layout);
        final SwipeRefreshLayout mSwipeRefreshLayout = swipeRefreshLayout;

        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors("default", getActivity()));

        // If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned.
        // So, we estimate the height of the header in dp
        mSwipeRefreshLayout.setProgressViewOffset(
                false,
                getHeaderViewOffset() - Constants.PTR_OFFSET_TOP,
                getHeaderViewOffset() + Constants.PTR_OFFSET_BOTTOM);

        mSwipeRefreshLayout.post(
                new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(true);
                    }
                });

        posts = createPosts();
        adapter = new ContributionAdapter(getActivity(), posts, rv);
        rv.setAdapter(adapter);

        posts.bindAdapter(adapter, mSwipeRefreshLayout);

        // TODO catch errors
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        posts.loadMore(adapter, true);

                        // TODO catch errors
                    }
                });
        rv.addOnScrollListener(
                new ToolbarScrollHideHandler(
                        getActivity().findViewById(R.id.toolbar),
                        getActivity().findViewById(R.id.header)) {
                    @Override
                    public void onScrollStateChanged(
                            @NonNull RecyclerView recyclerView, int newState) {
                        super.onScrollStateChanged(recyclerView, newState);
                        // On settle, warm the media-viewer image for each visible post; skipped
                        // mid-scroll so flicked-past rows aren't downloaded.
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            warmVisibleTapTargets();
                        }
                    }

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

                        if (!posts.loading) {
                            if ((visibleItemCount + pastVisiblesItems) + 5 >= totalItemCount
                                    && !posts.nomore) {
                                posts.loading = true;
                                posts.loadMore(adapter, false);
                            }
                        }
                    }
                });

        // Initial-display + refresh sweep: warm visible rows once content is laid out (before any
        // scroll), and again when a pull-to-refresh replaces the list.
        PhotoLoader.warmVisibleTapTargetsOnContentChange(
                rv, () -> posts != null ? posts.posts : null, this::sweepReplacedContent);
        return v;
    }

    // Content-change sweep: a refresh replaced the list, so forget the old warmed set (bounding it and
    // letting an evicted-then-refreshed row re-warm) before warming the fresh visible rows.
    private void sweepReplacedContent() {
        warmedTapTargets.clear();
        warmVisibleTapTargets();
    }

    // Warm the tap-target image of each currently-visible post (header spacer at position 0 -> posts
    // start at index 1). Shared helper handles range math, dedup, and off-main-thread warming.
    private void warmVisibleTapTargets() {
        PhotoLoader.warmVisibleTapTargets(
                getContext(), rv, posts != null ? posts.posts : null, 1, warmedTapTargets);
    }

    /** The posts source; ReadLaterView overrides with the read-later store. */
    protected HistoryPosts createPosts() {
        return new HistoryPosts();
    }

    /** Pull-to-refresh offset base; differs when the host has tabs vs a single header. */
    protected int getHeaderViewOffset() {
        return Constants.TAB_HEADER_VIEW_OFFSET;
    }

    public RecyclerView getRecyclerView() {
        return rv;
    }

    /**
     * Clears the active search filter and reloads data.
     */
    public void clearSearchAndReload() {
        if (adapter != null) {
            adapter.clearFilter();
        }
        if (posts != null && swipeRefreshLayout != null) {
            posts.loadMore(adapter, true);
        }
    }
}
