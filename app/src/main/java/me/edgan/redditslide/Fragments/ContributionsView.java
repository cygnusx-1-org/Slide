package me.edgan.redditslide.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Adapters.ContributionAdapter;
import me.edgan.redditslide.Adapters.ContributionPosts;
import me.edgan.redditslide.Adapters.ContributionPostsSaved;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.ContributionRestoreState;
import me.edgan.redditslide.HibernateState;
import me.edgan.redditslide.R;
import me.edgan.redditslide.SavedPostCache;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;
import me.edgan.redditslide.util.PhotoLoader;
import me.edgan.redditslide.util.ScrollAnchor;
import net.dean.jraw.models.Contribution;

public class ContributionsView extends Fragment implements ContributionRestoreState.Source {

    private int totalItemCount;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private ContributionAdapter adapter;
    private ContributionPosts posts;
    // Tap-target prefetch: full-names already warmed on a settle-sweep, so repeated micro-stops don't
    // re-warm the same visible rows. See PhotoLoader.warmVisibleTapTargets.
    private final Set<String> warmedTapTargets = new HashSet<>();
    private String id;
    private String where;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View searchProgress;
    private TextView searchProgressText;
    private ToolbarScrollHideHandler toolbarScroll;

    /** True until this tab's one hibernate restore has been applied; see the ARG_RESTORE_* keys. */
    private boolean restoreFromCache;

    @Nullable private String restoreAnchorId;
    private int restoreAnchorPosition = ScrollAnchor.NO_POSITION;
    private int restoreAnchorOffset;
    private boolean restoreToolbarHidden;

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_verticalcontent, container, false);

        recyclerView = v.requireViewById(R.id.vertical_content);
        searchProgress = v.findViewById(R.id.search_progress);
        searchProgressText = v.findViewById(R.id.search_progress_text);
        final View searchCancel = v.findViewById(R.id.search_progress_cancel);
        if (searchCancel != null) {
            searchCancel.setOnClickListener(view -> cancelSearchPaging());
        }
        final RecyclerView rv = recyclerView;

        final PreCachingLayoutManager mLayoutManager = new PreCachingLayoutManager(requireContext());

        rv.setLayoutManager(mLayoutManager);
        rv.setItemViewCacheSize(2);
        v.requireViewById(R.id.post_floating_action_button).setVisibility(View.GONE);
        swipeRefreshLayout = v.findViewById(R.id.activity_main_swipe_refresh_layout);
        final SwipeRefreshLayout mSwipeRefreshLayout = swipeRefreshLayout;

        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors(id, requireActivity()));

        // If we use 'findViewById(R.id.header).getMeasuredHeight()', 0 is always returned.
        // So, we estimate the height of the header in dp
        mSwipeRefreshLayout.setProgressViewOffset(
                false,
                Constants.TAB_HEADER_VIEW_OFFSET - Constants.PTR_OFFSET_TOP,
                Constants.TAB_HEADER_VIEW_OFFSET + Constants.PTR_OFFSET_BOTTOM);

        if (!restoreFromCache) {
            // A restore has its rows already; the spinner would be for a request it never makes.
            mSwipeRefreshLayout.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            mSwipeRefreshLayout.setRefreshing(true);
                        }
                    });
        }

        if (where.equals("saved") && getActivity() instanceof Profile)
            posts =
                    new ContributionPostsSaved(
                            id, where, ((Profile) getActivity()).category);
        else posts = new ContributionPosts(id, where);

        if (where.equals("hidden")) adapter = new ContributionAdapter(requireActivity(), posts, rv, true);
        else adapter = new ContributionAdapter(requireActivity(), posts, rv);
        rv.setAdapter(adapter);

        if (restoreFromCache) {
            final Bundle args = requireArguments();
            posts.restoreFromCache = true;
            posts.restoreCacheKey = args.getString(ContributionRestoreState.ARG_RESTORE_CACHE_KEY);
            posts.restoreExpectedCount =
                    args.getInt(ContributionRestoreState.ARG_RESTORE_EXPECTED_COUNT, 0);
            watchForRestore();
        }

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
        toolbarScroll =
                new ToolbarScrollHideHandler(
                        requireActivity().requireViewById(R.id.toolbar),
                        requireActivity().requireViewById(R.id.header)) {
                    @Override
                    public void onScrollStateChanged(
                            @NonNull RecyclerView recyclerView, int newState) {
                        super.onScrollStateChanged(recyclerView, newState);
                        // On settle, warm the media-viewer image for each visible post. Skipped
                        // mid-scroll so flicked-past rows aren't downloaded.
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            warmVisibleTapTargets();
                            // The tab has come to rest somewhere the user might leave from, so
                            // record where that is; a process killed out of recents gets no
                            // callback of its own.
                            final FragmentActivity settled = getActivity();
                            if (settled != null) {
                                HibernateState.onContentSettled(settled);
                            }
                        }
                    }

                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        final RecyclerView.LayoutManager lm = rv.getLayoutManager();
                        if (lm == null) return;

                        visibleItemCount = lm.getChildCount();
                        totalItemCount = lm.getItemCount();
                        if (lm instanceof PreCachingLayoutManager) {
                            pastVisiblesItems =
                                    ((PreCachingLayoutManager) lm)
                                            .findFirstVisibleItemPosition();
                        } else {
                            int[] firstVisibleItems = null;
                            firstVisibleItems =
                                    ((CatchStaggeredGridLayoutManager) lm)
                                            .findFirstVisibleItemPositions(firstVisibleItems);
                            if (firstVisibleItems != null && firstVisibleItems.length > 0) {
                                pastVisiblesItems = firstVisibleItems[0];
                            }
                        }

                        // This runs from RecyclerView.dispatchLayoutStep3, so it fires on every
                        // layout pass and not only on a real scroll. `error` is what stops a tab
                        // Reddit answers with a failure from re-requesting the same page forever:
                        // the failure leaves `nomore` false and swaps in the error view, whose
                        // layout brings us straight back here. A refresh is how the user retries.
                        if (!posts.loading && !posts.nomore && !posts.error) {
                            if ((visibleItemCount + pastVisiblesItems) + 5 >= totalItemCount) {
                                posts.loading = true;
                                posts.loadMore(adapter, id, false);
                            }
                        }
                    }
                };
        rv.addOnScrollListener(toolbarScroll);

        // Initial-display + refresh sweep: warm the visible rows' tap targets once content is laid out
        // (before any scroll), so a post already on screen — e.g. the album at the top of Saved — is
        // prefetched without needing a scroll. Re-fires when a pull-to-refresh replaces the list; the
        // settle-sweep covers scrolling in between.
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

    // Warm the tap-target image of each currently-visible post. A header spacer sits at adapter
    // position 0, so posts start at index 1 (headerOffset = 1). Shared helper handles range math,
    // dedup, and off-main-thread warming.
    private void warmVisibleTapTargets() {
        PhotoLoader.warmVisibleTapTargets(
                getContext(),
                recyclerView,
                posts != null ? posts.posts : null,
                1,
                warmedTapTargets);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = requireArguments();
        id = bundle.getString("id", "");
        where = bundle.getString("where", "");
        restoreFromCache = bundle.getBoolean(ContributionRestoreState.ARG_RESTORE_FROM_CACHE, false);
        restoreAnchorId = bundle.getString(ContributionRestoreState.ARG_RESTORE_ANCHOR_ID);
        restoreAnchorPosition =
                bundle.getInt(
                        ContributionRestoreState.ARG_RESTORE_ANCHOR_POSITION,
                        ScrollAnchor.NO_POSITION);
        restoreAnchorOffset =
                bundle.getInt(ContributionRestoreState.ARG_RESTORE_ANCHOR_OFFSET, 0);
        restoreToolbarHidden =
                bundle.getBoolean(ContributionRestoreState.ARG_RESTORE_TOOLBAR_HIDDEN, false);
    }

    /**
     * Runs {@link #applyRestoreAnchor} the first time the adapter publishes a list.
     *
     * <p>The loaders notify the adapter directly and tell the fragment nothing, and their one
     * completion callback already belongs to the deep search. Watching the adapter needs no new
     * plumbing through them and cannot collide with it.
     */
    private void watchForRestore() {
        final ContributionAdapter watched = adapter;
        watched.registerAdapterDataObserver(
                new RecyclerView.AdapterDataObserver() {
                    @Override
                    public void onChanged() {
                        watched.unregisterAdapterDataObserver(this);
                        applyRestoreAnchor();
                    }
                });
    }

    /**
     * Lands the restored tab on the row the user left off at, once and only once.
     *
     * <p>The row is found by fullname rather than by the index it had last time, because
     * {@code PostMatch} filtering on the way back in shifts the indexes; the recorded index is only
     * a fallback for a row that is genuinely gone.
     */
    private void applyRestoreAnchor() {
        if (!restoreFromCache) {
            return;
        }
        restoreFromCache = false;
        if (posts == null || !posts.restoredFromCache) {
            // The blob was gone or too short, so the loader fetched instead. These are fresh rows
            // at fresh positions; the recorded offset describes a different list.
            return;
        }
        posts.restoredFromCache = false;
        final List<Contribution> current = posts.posts;
        int position = ScrollAnchor.NO_POSITION;
        if (restoreAnchorId != null && !restoreAnchorId.isEmpty() && current != null) {
            for (int i = 0; i < current.size(); i++) {
                final Contribution at = current.get(i);
                if (at != null && restoreAnchorId.equals(at.getFullName())) {
                    position = i + 1; // adapter position 0 is the spacer header
                    break;
                }
            }
        }
        if (position == ScrollAnchor.NO_POSITION) {
            position = restoreAnchorPosition;
        }
        if (position == ScrollAnchor.NO_POSITION || current == null || position > current.size()) {
            return;
        }
        ScrollAnchor.applyHidden(
                recyclerView,
                position,
                restoreAnchorOffset,
                () ->
                        // The jump pushes one large dy through ToolbarScrollHideHandler. Posted so
                        // the repair lands after that dy rather than being overwritten by it.
                        recyclerView.post(
                                () -> {
                                    if (toolbarScroll != null) {
                                        toolbarScroll.settleAfterJump(restoreToolbarHidden);
                                    }
                                }));
    }

    @Override
    @Nullable
    public List<Contribution> getRestorePosts() {
        return posts == null ? null : posts.posts;
    }

    @Override
    @Nullable
    public String getRestoreCacheKey() {
        return posts == null ? null : posts.cacheKey();
    }

    /**
     * A deep search pages the whole listing in the background, and the cache is deliberately not
     * rewritten while it does. Recording a row count and a scroll position from a list the blob has
     * not caught up with is what makes the resume reject the blob as short and refetch; freezing
     * the snapshot alongside the cache keeps the state from before the search restorable, which is
     * the best answer available while the search is still running.
     */
    @Override
    public boolean isRecordable() {
        return posts == null || !posts.isDeepSearching();
    }

    /**
     * Gets the RecyclerView instance for this fragment.
     * Used by Profile activity to access the adapter for search functionality.
     *
     * @return The RecyclerView instance, or null if not yet created
     */
    @Override
    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    /**
     * Searches the whole (paginated) history, so posts deep in the list are found and not just the
     * pages already scrolled into view.
     *
     * <p>The hits in what is already loaded go on screen at once, and the rest of the history is
     * paged in behind them, each page's hits joining the list as it arrives. A strip at the bottom
     * reports how far the scan has got and offers to stop it. It used to block the whole list
     * behind an opaque overlay until the last page landed, which on an account with a thousand
     * posts was a minute of spinner with the answer sitting in page one the entire time.
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
        final boolean forceReload = bypassCache && posts instanceof ContributionPostsSaved;

        // Filter what is already loaded first, so the hits on the pages already in hand are on
        // screen before the first request goes out. A bypass reload is about to replace the list,
        // and the filter is re-applied to each page of the replacement as it arrives.
        adapter.applyFilter(query, searchWhere);

        if (!forceReload && (posts.nomore || posts.error)) {
            // Nothing left to page, or the listing is not answering. Either way what is on screen
            // is the whole answer. Checked before the bypass is armed below: arming it and then
            // returning would leave the flag set for whatever ordinary reload came next, which
            // would silently skip the cache the user had not asked to skip.
            endSearchPaging();
            return;
        }

        if (forceReload) {
            ((ContributionPostsSaved) posts).bypassCache = true;
            posts.nomore = false;
        }

        // Keep paging in the background, adding each page's hits as they arrive.
        showSearchProgress(true);
        posts.setOnLoadCompleteListener(
                success -> {
                    if (!success) {
                        // The page failed. Asking again changes nothing about why, so the old
                        // unconditional re-fire was an unbounded retry against Reddit with the
                        // blocking overlay never coming down and the search never ending. Stop
                        // here and leave the error the loader put on screen to retry from.
                        endSearchPaging();
                    } else if (posts.nomore) {
                        endSearchPaging();
                        adapter.applyFilter(query, searchWhere);
                    } else if (!posts.loading) {
                        // onDataUpdated has already re-filtered this page into the list; just
                        // refresh the count and ask for the next one.
                        updateSearchProgress();
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

    /** Stops the background paging a search started, leaving the hits found so far on screen. */
    public void cancelSearchPaging() {
        endSearchPaging();
    }

    private void endSearchPaging() {
        if (posts != null) {
            posts.setOnLoadCompleteListener(null);
        }
        showSearchProgress(false);
    }

    /**
     * Shows or hides the strip that reports a search still paging the history.
     *
     * <p>It sits at the bottom of the screen rather than over the list, because the hits found so
     * far are on screen the whole time now and covering them was the bug.
     */
    private void showSearchProgress(boolean show) {
        if (searchProgress == null) {
            return;
        }
        searchProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            updateSearchProgress();
        }
    }

    private void updateSearchProgress() {
        if (searchProgress == null
                || searchProgress.getVisibility() != View.VISIBLE
                || searchProgressText == null
                || adapter == null
                || !isAdded()) {
            return;
        }
        final int hits = adapter.getResultCount();
        searchProgressText.setText(
                getString(
                        R.string.profile_search_progress,
                        hits < 0 ? 0 : hits,
                        posts == null || posts.posts == null ? 0 : posts.posts.size()));
    }

    /**
     * Clears the active search filter and reloads data.
     */
    public void clearSearchAndReload() {
        // Tear down any in-progress deep-search loading.
        endSearchPaging();
        if (adapter != null) {
            adapter.clearFilter();
        }
        if (posts != null && swipeRefreshLayout != null) {
            posts.loadMore(adapter, id, true);
        }
    }
}
