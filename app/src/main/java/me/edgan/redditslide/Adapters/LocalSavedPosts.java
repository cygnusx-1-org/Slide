package me.edgan.redditslide.Adapters;

import android.os.AsyncTask;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.PostMatch;
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
    private SwipeRefreshLayout refreshLayout;
    private ContributionAdapter adapter;
    public boolean loading;

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

        public LoadData(boolean reset) {
            this.reset = reset;
        }

        @Override
        protected ArrayList<Contribution> doInBackground(Void... params) {
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
        }
    }
}
