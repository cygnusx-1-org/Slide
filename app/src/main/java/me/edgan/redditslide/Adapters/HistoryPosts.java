package me.edgan.redditslide.Adapters;

import android.os.AsyncTask;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.lusfold.androidkeyvaluestore.KVStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.ContributionCache;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.LogUtil;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Thing;
import net.dean.jraw.paginators.FullnamesPaginator;

/** Created by ccrama on 9/17/2015. */
public class HistoryPosts extends GeneralPosts {
    @SuppressWarnings("NullAway.Init") // assigned in bindAdapter
    private SwipeRefreshLayout refreshLayout;
    @SuppressWarnings("NullAway.Init") // assigned in bindAdapter
    private ContributionAdapter adapter;
    public boolean loading;
    String prefix = "";

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

    /**
     * The fullname of the last restored row, and so the point the id list has to be resumed after.
     * History has no {@code after} cursor: {@link FullnamesPaginator} walks a fixed array of ids
     * built from local storage, and where it had got to is expressible only as a position in that
     * array.
     */
    @Nullable private String resumeAfterFullname;

    public HistoryPosts() {}

    public HistoryPosts(String prefix) {
        this.prefix = prefix;
    }

    public void bindAdapter(ContributionAdapter a, SwipeRefreshLayout layout) {
        this.adapter = a;
        this.refreshLayout = layout;
        loadMore(a, true);
    }

    public void loadMore(ContributionAdapter adapter, boolean reset) {
        new LoadData(reset).execute();
    }

    /**
     * This tab's {@link ContributionCache} key, or {@code null} when the list is not one a resume
     * can land on. Only the profile's History tab is: {@code ReadLaterView} builds this with a
     * prefix and lives in {@code PostReadLater}, which no snapshot names, so a blob written for it
     * could only ever sit in the cache taking a slot from a tab that will be restored.
     */
    @Nullable
    public String cacheKey() {
        return prefix.isEmpty()
                ? ContributionCache.key(Authentication.nameOrEmpty(), "history", null)
                : null;
    }

    /**
     * The tab as it was last written to disk, or {@code null} when there is nothing usable there
     * and the caller must fetch instead. Runs on the loader's background thread.
     */
    @Nullable
    private ArrayList<Contribution> rebuildFromCache() {
        if (!restoreFromCache) {
            return null;
        }
        // One attempt, hit or miss: a later pull-to-refresh must reach the network.
        restoreFromCache = false;
        final String key = restoreCacheKey != null ? restoreCacheKey : cacheKey();
        if (key == null) {
            return null;
        }
        final ContributionCache.Cached cached = ContributionCache.load(key);
        if (cached == null) {
            return null;
        }
        if (restoreExpectedCount > 0
                && cached.posts.size() < restoreExpectedCount * MIN_RESTORE_FRACTION) {
            return null;
        }
        final Contribution last = cached.posts.get(cached.posts.size() - 1);
        resumeAfterFullname = last == null ? null : last.getFullName();
        restoredFromCache = true;
        return cached.posts;
    }

    @SuppressWarnings("NullAway.Init") // assigned in onPostExecute
    FullnamesPaginator paginator;

    public class LoadData extends AsyncTask<String, Void, ArrayList<Contribution>> {
        final boolean reset;

        /** Whether this particular load came out of {@link ContributionCache}. */
        boolean fromHibernateCache;

        public LoadData(boolean reset) {
            this.reset = reset;
        }

        @Override
        public void onPostExecute(ArrayList<Contribution> submissions) {
            loading = false;

            if (submissions != null && !submissions.isEmpty()) {
                // new submissions found

                int start = 0;
                if (posts != null) {
                    start = posts.size() + 1;
                }

                ArrayList<Contribution> filteredSubmissions = new ArrayList<>();
                for (Contribution c : submissions) {
                    if (c instanceof Submission) {
                        if (!PostMatch.doesMatch((Submission) c)) {
                            filteredSubmissions.add(c);
                        }
                    } else {
                        filteredSubmissions.add(c);
                    }
                }

                if (reset || posts == null) {
                    posts = filteredSubmissions;
                    start = -1;
                } else {
                    posts.addAll(filteredSubmissions);
                }

                final int finalStart = start;
                // update online
                if (refreshLayout != null) {
                    refreshLayout.setRefreshing(false);
                }

                if (finalStart != -1) {
                    adapter.notifyItemRangeInserted(finalStart + 1, posts.size());
                } else {
                    adapter.notifyDataSetChanged();
                }

            } else {
                // end of submissions
                nomore = true;
                adapter.notifyDataSetChanged();
            }
            refreshLayout.setRefreshing(false);

            // Keep the list on disk so a hibernate resume can put it back without re-hydrating
            // every id over /api/info. The write is queued off this thread. Skipped when the load
            // failed -- a null result leaves the list as the last write found it -- and when this
            // list is not one a resume can land on (see cacheKey).
            final String key = cacheKey();
            if (SettingValues.hibernateActive()
                    && submissions != null
                    && key != null
                    && !fromHibernateCache
                    && posts != null
                    && !posts.isEmpty()) {
                ContributionCache.store(key, posts, null);
            }
        }

        @Override
        protected @Nullable ArrayList<Contribution> doInBackground(String... subredditPaginators) {
            if (reset) {
                final ArrayList<Contribution> restored = rebuildFromCache();
                if (restored != null) {
                    fromHibernateCache = true;
                    return restored;
                }
                // A reset is "start from the top", so a resume point left over from a restore that
                // did not happen must not shorten the id list below.
                resumeAfterFullname = null;
            }
            ArrayList<Contribution> newData = new ArrayList<>();
            try {
                if (reset || paginator == null) {
                    ArrayList<String> ids = new ArrayList<>();
                    HashMap<Long, String> idsSorted = new HashMap<>();
                    Map<String, String> values;
                    if (prefix.isEmpty()) {
                        values = KVStore.getInstance().getByContains("");
                    } else {
                        values = KVStore.getInstance().getByPrefix(prefix);
                    }

                    for (Map.Entry<String, String> entry : values.entrySet()) {
                        Object done;
                        if (entry.getValue().equals("true") || entry.getValue().equals("false")) {
                            done = Boolean.valueOf(entry.getValue());
                        } else {
                            done = Long.valueOf(entry.getValue());
                        }
                        if (prefix.isEmpty()) {
                            if (!entry.getKey().contains("readLater")) {
                                if (entry.getKey().length() == 6 && done instanceof Boolean) {
                                    ids.add("t3_" + entry.getKey());
                                } else if (done instanceof Long) {
                                    if (entry.getKey().contains("_")) {
                                        idsSorted.put((Long) done, entry.getKey());
                                    } else {
                                        idsSorted.put((Long) done, "t3_" + entry.getKey());
                                    }
                                }
                            }
                        } else {
                            String key = entry.getKey();
                            if (!key.contains("_")) {
                                key = "t3_" + key;
                            }
                            idsSorted.put((Long) done, key.replace(prefix, ""));
                        }
                    }

                    if (!idsSorted.isEmpty()) {
                        TreeMap<Long, String> result2 = new TreeMap<>(Collections.reverseOrder());
                        result2.putAll(idsSorted);
                        ids.addAll(0, result2.values());
                    }

                    if (resumeAfterFullname != null) {
                        final int at = ids.indexOf(resumeAfterFullname);
                        resumeAfterFullname = null;
                        if (at >= 0) {
                            if (at + 1 >= ids.size()) {
                                // The restored list already reaches the end of the ids.
                                nomore = true;
                                return new ArrayList<>();
                            }
                            ids = new ArrayList<>(ids.subList(at + 1, ids.size()));
                        }
                        // Not in the list at all means the local ordering moved under us -- a post
                        // viewed in the meantime rewrites it. Load from the top rather than guess
                        // an offset into a list that is no longer the one that was recorded.
                    }

                    paginator =
                            new FullnamesPaginator(
                                    Authentication.reddit, ids.toArray(new String[ids.size() - 1]));
                }

                if (!paginator.hasNext()) {
                    nomore = true;
                    return new ArrayList<>();
                }

                for (Thing c : paginator.next()) {
                    if (c instanceof Contribution) {
                        newData.add((Contribution) c);
                    }
                }

                return newData;
            } catch (Exception e) {
                LogUtil.e(e, "HistoryPosts.doInBackground failed");
                return null;
            }
        }
    }
}
