package me.edgan.redditslide.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Adapters.ContributionAdapter;
import me.edgan.redditslide.Adapters.ContributionPosts;
import me.edgan.redditslide.Adapters.ContributionPostsSaved;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SavedPostCache;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;

public class ContributionsView extends Fragment {

    private int totalItemCount;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private ContributionAdapter adapter;
    private ContributionPosts posts;
    private String id;
    private String where;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View searchOverlay;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_verticalcontent, container, false);

        recyclerView = v.findViewById(R.id.vertical_content);
        searchOverlay = v.findViewById(R.id.search_loading_overlay);
        final RecyclerView rv = recyclerView;

        final PreCachingLayoutManager mLayoutManager = new PreCachingLayoutManager(getContext());

        rv.setLayoutManager(mLayoutManager);
        rv.setItemViewCacheSize(2);
        v.findViewById(R.id.post_floating_action_button).setVisibility(View.GONE);
        swipeRefreshLayout = v.findViewById(R.id.activity_main_swipe_refresh_layout);
        final SwipeRefreshLayout mSwipeRefreshLayout = swipeRefreshLayout;

        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors(id, getActivity()));

        // If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned.
        // So, we estimate the height of the header in dp
        mSwipeRefreshLayout.setProgressViewOffset(
                false,
                Constants.TAB_HEADER_VIEW_OFFSET - Constants.PTR_OFFSET_TOP,
                Constants.TAB_HEADER_VIEW_OFFSET + Constants.PTR_OFFSET_BOTTOM);

        mSwipeRefreshLayout.post(
                new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(true);
                    }
                });

        if (where.equals("saved") && getActivity() instanceof Profile)
            posts = new ContributionPostsSaved(id, where, ((Profile) getActivity()).category);
        else posts = new ContributionPosts(id, where);

        // noinspection StringEquality
        if (where == "hidden") adapter = new ContributionAdapter(getActivity(), posts, rv, true);
        else adapter = new ContributionAdapter(getActivity(), posts, rv);
        rv.setAdapter(adapter);

        posts.bindAdapter(adapter, mSwipeRefreshLayout);
        // TODO catch errors
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        // Pull-to-refresh means "get fresh": drop the Saved TTL cache (so a later
                        // tab reopen won't serve the pre-refresh list) and force this reload to
                        // hit the network.
                        if (posts instanceof ContributionPostsSaved) {
                            SavedPostCache.invalidate();
                            ((ContributionPostsSaved) posts).bypassCache = true;
                        }
                        posts.loadMore(adapter, id, true);
                        // TODO catch errors
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
                                posts.loadMore(adapter, id, false);
                            }
                        }
                    }
                });
        return v;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = this.getArguments();
        id = bundle.getString("id", "");
        where = bundle.getString("where", "");
    }

    /**
     * Gets the RecyclerView instance for this fragment.
     * Used by Profile activity to access the adapter for search functionality.
     *
     * @return The RecyclerView instance, or null if not yet created
     */
    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    /**
     * Runs a search that first loads the entire (paginated) history so posts deep in
     * the list are found, not just the pages already scrolled into view. The list is
     * blocked behind a spinner overlay until loading finishes, then the filter is
     * applied once over the complete set.
     *
     * @param query The search query string
     * @param searchWhere The tab/section name (e.g., "saved")
     * @param bypassCache On the Saved tab, force a fresh network reload instead of the TTL cache.
     */
    public void startSearch(String query, String searchWhere, boolean bypassCache) {
        if (adapter == null || posts == null) {
            return;
        }

        // Restore any previous filter so the new term searches the full set rather
        // than the previous search's results.
        adapter.clearFilter();

        // Bypass toggle on Saved: reload the whole history from the network (ignoring the
        // cache) with a reset load, then filter over the fresh, complete set.
        boolean forceReload = bypassCache && posts instanceof ContributionPostsSaved;
        if (forceReload) {
            ((ContributionPostsSaved) posts).bypassCache = true;
            posts.nomore = false;
        } else if (posts.nomore) {
            // Everything is already loaded (cache hit or prior full load) -- filter immediately.
            adapter.applyFilter(query, searchWhere);
            return;
        }

        // Block the list and page through the rest of the history before filtering.
        showSearchOverlay(true);
        posts.setOnLoadCompleteListener(
                () -> {
                    if (posts.nomore) {
                        posts.setOnLoadCompleteListener(null);
                        adapter.applyFilter(query, searchWhere);
                        showSearchOverlay(false);
                    } else if (!posts.loading) {
                        posts.loading = true;
                        posts.loadMore(adapter, id, false);
                    }
                });

        if (forceReload) {
            // Force a fresh reset load even if one is already in flight, so the bypass is
            // always honored (the in-flight load can't leave us on stale/cached data).
            posts.loading = true;
            posts.loadMore(adapter, id, true);
        } else if (!posts.loading) {
            posts.loading = true;
            posts.loadMore(adapter, id, false);
        }
    }

    private void showSearchOverlay(boolean show) {
        if (searchOverlay != null) {
            searchOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Clears the active search filter and reloads data.
     */
    public void clearSearchAndReload() {
        // Tear down any in-progress deep-search loading.
        showSearchOverlay(false);
        if (posts != null) {
            posts.setOnLoadCompleteListener(null);
        }
        if (adapter != null) {
            adapter.clearFilter();
        }
        if (posts != null && swipeRefreshLayout != null) {
            posts.loadMore(adapter, id, true);
        }
    }
}
