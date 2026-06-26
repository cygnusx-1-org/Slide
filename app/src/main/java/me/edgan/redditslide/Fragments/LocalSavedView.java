package me.edgan.redditslide.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import me.edgan.redditslide.Adapters.ContributionAdapter;
import me.edgan.redditslide.Adapters.LocalSavedPosts;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;

public class LocalSavedView extends Fragment {

    private int totalItemCount;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private ContributionAdapter adapter;
    private LocalSavedPosts posts;
    private RecyclerView recyclerView;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_verticalcontent, container, false);

        final RecyclerView rv = v.findViewById(R.id.vertical_content);
        recyclerView = rv;

        final PreCachingLayoutManager mLayoutManager = new PreCachingLayoutManager(getContext());

        rv.setLayoutManager(mLayoutManager);
        v.findViewById(R.id.post_floating_action_button).setVisibility(View.GONE);
        final SwipeRefreshLayout mSwipeRefreshLayout =
                v.findViewById(R.id.activity_main_swipe_refresh_layout);

        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors("default", getActivity()));

        // If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned.
        // So, we estimate the height of the header in dp. The standalone activity (PostLocalSaved)
        // has only a toolbar, while the profile tab sits under the taller profile+tabs header --
        // use the matching offset so the refresh spinner lands below the header in both.
        final int headerOffset =
                (getArguments() != null && getArguments().getBoolean("single"))
                        ? Constants.SINGLE_HEADER_VIEW_OFFSET
                        : Constants.TAB_HEADER_VIEW_OFFSET;
        mSwipeRefreshLayout.setProgressViewOffset(
                false,
                headerOffset - Constants.PTR_OFFSET_TOP,
                headerOffset + Constants.PTR_OFFSET_BOTTOM);

        mSwipeRefreshLayout.post(
                new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(true);
                    }
                });

        posts = new LocalSavedPosts();
        adapter = new ContributionAdapter(getActivity(), posts, rv);
        rv.setAdapter(adapter);

        posts.bindAdapter(adapter, mSwipeRefreshLayout);

        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        posts.loadMore(true);
                    }
                });
        rv.addOnScrollListener(
                new ToolbarScrollHideHandler(
                        getActivity().findViewById(R.id.toolbar),
                        getActivity().findViewById(R.id.header)) {
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
                                posts.loadMore(false);
                            }
                        }
                    }
                });
        return v;
    }

    /** Used by {@link me.edgan.redditslide.Activities.Profile} to apply/clear in-tab search. */
    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    /** Clears the active search filter and reloads. */
    public void clearSearchAndReload() {
        if (adapter != null) {
            adapter.clearFilter();
        }
        if (posts != null) {
            posts.loadMore(true);
        }
    }
}
