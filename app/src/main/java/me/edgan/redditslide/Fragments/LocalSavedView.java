package me.edgan.redditslide.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.edgan.redditslide.Adapters.ContributionAdapter;
import me.edgan.redditslide.Adapters.LocalSavedPosts;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.ContributionRestoreState;
import me.edgan.redditslide.HibernateState;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Views.CatchStaggeredGridLayoutManager;
import me.edgan.redditslide.Views.PreCachingLayoutManager;
import me.edgan.redditslide.Visuals.Palette;
import me.edgan.redditslide.handler.ToolbarScrollHideHandler;
import me.edgan.redditslide.util.PhotoLoader;
import me.edgan.redditslide.util.ScrollAnchor;
import net.dean.jraw.models.Contribution;

public class LocalSavedView extends Fragment implements ContributionRestoreState.Source {

    private int totalItemCount;
    private int visibleItemCount;
    private int pastVisiblesItems;
    private ContributionAdapter adapter;
    private LocalSavedPosts posts;
    private RecyclerView recyclerView;
    // Tap-target prefetch: full-names already warmed, so repeated settles don't re-warm the same rows.
    private final Set<String> warmedTapTargets = new HashSet<>();
    private ToolbarScrollHideHandler toolbarScroll;

    /** True until this tab's one hibernate restore has been applied; see the ARG_RESTORE_* keys. */
    private boolean restoreFromCache;

    @Nullable private String restoreAnchorId;
    private int restoreAnchorPosition = ScrollAnchor.NO_POSITION;
    private int restoreAnchorOffset;
    private boolean restoreToolbarHidden;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // PostLocalSaved hosts this with only its "single" flag, and so does Profile outside a
        // restore, so read defensively rather than requiring them.
        final Bundle bundle = getArguments();
        if (bundle == null) {
            return;
        }
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

    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_verticalcontent, container, false);

        final RecyclerView rv = v.requireViewById(R.id.vertical_content);
        recyclerView = rv;

        final PreCachingLayoutManager mLayoutManager = new PreCachingLayoutManager(requireContext());

        rv.setLayoutManager(mLayoutManager);
        v.requireViewById(R.id.post_floating_action_button).setVisibility(View.GONE);
        final SwipeRefreshLayout mSwipeRefreshLayout =
                v.requireViewById(R.id.activity_main_swipe_refresh_layout);

        mSwipeRefreshLayout.setColorSchemeColors(Palette.getColors("default", requireActivity()));

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

        posts = new LocalSavedPosts();
        adapter = new ContributionAdapter(requireActivity(), posts, rv);
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

        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        posts.loadMore(true);
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
                        // On settle, warm the media-viewer image for each visible post; skipped
                        // mid-scroll so flicked-past rows aren't downloaded.
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            warmVisibleTapTargets();
                            // The tab has come to rest somewhere the user might leave from, so
                            // record where that is.
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

                        if (!posts.loading) {
                            if ((visibleItemCount + pastVisiblesItems) + 5 >= totalItemCount
                                    && !posts.nomore) {
                                posts.loading = true;
                                posts.loadMore(false);
                            }
                        }
                    }
                };
        rv.addOnScrollListener(toolbarScroll);

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
                getContext(),
                recyclerView,
                posts != null ? posts.posts : null,
                1,
                warmedTapTargets);
    }

    /** Used by {@link me.edgan.redditslide.Activities.Profile} to apply/clear in-tab search. */
    @Override
    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    /** See {@code ContributionsView.watchForRestore} for why the adapter is what gets watched. */
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

    /** Lands the restored tab on the row the user left off at, once and only once. */
    private void applyRestoreAnchor() {
        if (!restoreFromCache) {
            return;
        }
        restoreFromCache = false;
        if (posts == null || !posts.restoredFromCache) {
            // The blob was gone or too short, so the loader fetched instead: fresh rows at fresh
            // positions, which the recorded offset does not describe.
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
                        // Posted so the repair lands after the large dy the jump pushes through
                        // ToolbarScrollHideHandler rather than being overwritten by it.
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
