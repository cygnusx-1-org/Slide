package me.edgan.redditslide.Adapters;

import android.os.AsyncTask;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.ContributionCache;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionViews.LocalSaved;
import me.edgan.redditslide.util.LogUtil;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Thing;
import net.dean.jraw.paginators.FullnamesPaginator;

/**
 * Feed source for the Local Saved viewer. On a reset/refresh it runs {@link LocalSaved#reconcile()}
 * (detect saves Reddit has dropped) and then loads the promoted fullnames via {@code /api/info}
 * ({@link FullnamesPaginator}). Items removed by a moderator render as whatever Reddit now returns.
 */
public class LocalSavedPosts extends GeneralPosts {
    @SuppressWarnings("NullAway.Init") // bound by bindAdapter before any load
    private SwipeRefreshLayout refreshLayout;

    @SuppressWarnings("NullAway.Init") // assigned in bindAdapter
    private ContributionAdapter adapter;
    public boolean loading;

    /**
     * How much of a hibernated listing has to still be on disk for it to stand in for a fetch. See
     * {@code ContributionPosts} for why a short blob is rejected rather than shown.
     */
    private static final double MIN_RESTORE_FRACTION = 0.5;

    /** True until this tab's one hibernate restore has been attempted. */
    public boolean restoreFromCache;

    /** Whether that attempt produced the cached list, which is when the anchor still describes it. */
    public boolean restoredFromCache;

    @Nullable public String restoreCacheKey;
    public int restoreExpectedCount;

    /** This tab's {@link ContributionCache} key. */
    public String cacheKey() {
        return ContributionCache.key(Authentication.nameOrEmpty(), "localsaved", null);
    }

    /**
     * The tab as it was last written to disk, or {@code null} when there is nothing usable there.
     * Runs on the loader's background thread.
     *
     * <p>A restore deliberately skips {@link LocalSaved#reconcile()} along with the fetch: that is
     * the whole point of coming back without a refresh. Any pull-to-refresh reconciles again.
     */
    @Nullable
    private ArrayList<Contribution> rebuildFromCache() {
        if (!restoreFromCache) {
            return null;
        }
        // One attempt, hit or miss: a later pull-to-refresh must reach the network.
        restoreFromCache = false;
        final String key = restoreCacheKey != null ? restoreCacheKey : cacheKey();
        final ContributionCache.Cached cached = ContributionCache.load(key);
        if (cached == null) {
            return null;
        }
        if (restoreExpectedCount > 0
                && cached.posts.size() < restoreExpectedCount * MIN_RESTORE_FRACTION) {
            return null;
        }
        restoredFromCache = true;
        return cached.posts;
    }

    public void bindAdapter(ContributionAdapter a, SwipeRefreshLayout layout) {
        this.adapter = a;
        this.refreshLayout = layout;
        loadMore(true);
    }

    public void loadMore(boolean reset) {
        // Run on the thread pool, not the shared serial executor: reconcile() can walk the entire
        // /saved listing, and that must not block unrelated AsyncTask.execute() work app-wide.
        new LoadData(reset).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public class LoadData extends AsyncTask<Void, Void, ArrayList<Contribution>> {
        final boolean reset;

        /** Whether this particular load came out of {@link ContributionCache}. */
        boolean fromHibernateCache;

        public LoadData(boolean reset) {
            this.reset = reset;
        }

        @Override
        protected @Nullable ArrayList<Contribution> doInBackground(Void... params) {
            if (reset) {
                final ArrayList<Contribution> restored = rebuildFromCache();
                if (restored != null) {
                    fromHibernateCache = true;
                    return restored;
                }
            }
            try {
                if (reset) {
                    LocalSaved.reconcile();
                }

                ArrayList<String> ids = LocalSaved.getPromoted();
                ArrayList<Contribution> newData = new ArrayList<>();

                if (!ids.isEmpty() && Authentication.reddit != null) {
                    FullnamesPaginator paginator =
                            new FullnamesPaginator(
                                    Authentication.reddit, ids.toArray(new String[0]));
                    paginator.setLimit(100);
                    while (paginator.hasNext()) {
                        for (Thing t : paginator.next()) {
                            if (t instanceof Contribution) {
                                newData.add((Contribution) t);
                            }
                        }
                    }
                }

                return newData;
            } catch (Exception e) {
                LogUtil.e(e, "LocalSavedPosts.doInBackground failed");
                return null;
            }
        }

        @Override
        protected void onPostExecute(ArrayList<Contribution> data) {
            loading = false;
            nomore = true;

            ArrayList<Contribution> filtered = new ArrayList<>();
            if (data != null) {
                for (Contribution c : data) {
                    if (c instanceof Submission) {
                        if (!PostMatch.doesMatch((Submission) c)) {
                            filtered.add(c);
                        }
                    } else {
                        filtered.add(c);
                    }
                }
            }

            posts = filtered;

            if (refreshLayout != null) {
                refreshLayout.setRefreshing(false);
            }
            // Re-apply any active search filter to the freshly loaded data (no-op if none),
            // matching ContributionPosts so a search + pull-to-refresh doesn't desync filter state.
            adapter.onDataUpdated();
            adapter.notifyDataSetChanged();

            // Keep the list on disk so a hibernate resume can put it back without re-hydrating
            // every id over /api/info. The write is queued off this thread.
            if (SettingValues.hibernateActive() && !fromHibernateCache && !posts.isEmpty()) {
                ContributionCache.store(cacheKey(), posts, null);
            }
        }
    }
}
