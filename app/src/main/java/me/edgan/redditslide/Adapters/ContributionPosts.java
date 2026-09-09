package me.edgan.redditslide.Adapters;

import android.os.AsyncTask;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.ContributionCache;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PhotoLoader;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.Paginator;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.TimePeriod;

/** Created by ccrama on 9/17/2015. */
public class ContributionPosts extends GeneralPosts {
    protected final String where;
    protected final String subreddit;
    public boolean loading;

    /**
     * Set when a page came back as a failure rather than as an end of listing.
     *
     * <p>The bottom-of-list trigger in {@code ContributionsView} fires from
     * {@code RecyclerView.dispatchLayoutStep3}, so it runs on every layout pass, not only on a real
     * scroll. A failed page leaves {@link #nomore} false and swaps in the error view, which lays
     * out and fires it again: a tab Reddit answers with an error (the profile's Gilded tab answers
     * with an HTML page, which JRAW rejects) retried hundreds of times a minute for as long as the
     * profile stayed open. Retrying is the user's to ask for, through a refresh.
     */
    public boolean error;
    @SuppressWarnings("NullAway.Init") // assigned in onPostExecute
    private ResumableUserProfilePaginator paginator;
    @SuppressWarnings("NullAway.Init") // assigned in bindAdapter
    protected SwipeRefreshLayout refreshLayout;
    @SuppressWarnings("NullAway.Init") // assigned in bindAdapter
    protected ContributionAdapter adapter;
    @Nullable protected OnLoadCompleteListener loadCompleteListener;

    /**
     * How much of a hibernated listing has to still be on disk for it to stand in for a fetch. A
     * blob is written whole, so a short one means the write was interrupted; showing what survived
     * would put the user at a scroll offset into a list that is missing its middle.
     */
    private static final double MIN_RESTORE_FRACTION = 0.5;

    /**
     * How many consecutive pages may come back with every row filtered out before the loader gives
     * up and reports the end of the listing. Mirrors {@code SubredditPosts.MAX_EMPTY_PAGES}.
     */
    protected static final int MAX_EMPTY_PAGES = 3;

    /**
     * One thread for every profile listing load in the app.
     *
     * <p>These used to run on {@code AsyncTask.SERIAL_EXECUTOR}, which serialised them against
     * every other {@code AsyncTask.execute()} in the app -- a search paging a whole history held up
     * all of them. What they must keep is the narrower half of that guarantee: two loads on one tab
     * share a paginator and the {@code posts} list, and {@code Paginator.next} is not reentrant, so
     * a pull-to-refresh landing on top of an in-flight page (neither of those two call sites sets
     * {@code loading}) would race a fresh paginator against the one being read. A serial executor
     * of this loader's own keeps that impossible without tying the tab to the rest of the app.
     */
    protected static final Executor LOAD_EXECUTOR = Executors.newSingleThreadExecutor();

    /** True until this tab's one hibernate restore has been attempted; see {@link ContributionCache}. */
    public boolean restoreFromCache;

    /**
     * Whether that attempt actually produced the cached list. The fragment only applies the saved
     * scroll position when it did: a fallback fetch is fresh rows at fresh positions, which the
     * recorded offset does not describe.
     */
    public boolean restoredFromCache;

    /**
     * The cache key recorded with the snapshot, checked against this tab's own before the blob is
     * used. It is a guard, not an override: see {@link #rebuildFromCache}.
     */
    @Nullable public String restoreCacheKey;

    /** The listing cursor read back out of the cached blob, handed to the next paginator built. */
    @Nullable public String restoreAfterToken;

    public int restoreExpectedCount;

    /** Notified after each page finishes loading so callers can page to the end. */
    public interface OnLoadCompleteListener {

        /**
         * @param success whether the page actually arrived. False means the load failed, and
         *     nothing about asking again makes the next attempt any likelier to succeed.
         */
        void onLoadComplete(boolean success);
    }

    public void setOnLoadCompleteListener(@Nullable OnLoadCompleteListener listener) {
        this.loadCompleteListener = listener;
    }

    /**
     * Whether a deep search is paging this listing to its end. The listener is only ever set for
     * that, and only cleared when it finishes, so its presence is the state itself.
     */
    public boolean isDeepSearching() {
        return loadCompleteListener != null;
    }

    public ContributionPosts(String subreddit, String where) {
        this.subreddit = subreddit;
        this.where = where;
    }

    public void bindAdapter(ContributionAdapter a, SwipeRefreshLayout layout) {
        this.adapter = a;
        this.refreshLayout = layout;
        loadMore(a, subreddit, true);
    }

    public void loadMore(ContributionAdapter adapter, String subreddit, boolean reset) {
        new LoadData(reset).executeOnExecutor(LOAD_EXECUTOR, subreddit);
    }

    /** This tab's {@link ContributionCache} key. {@code subreddit} is the profile's username here. */
    public String cacheKey() {
        return ContributionCache.key(subreddit, where, null);
    }

    /**
     * The current page's listing, so {@link #getAfterToken} can read its cursor.
     * {@code ContributionPostsSaved} paginates through a field of its own that shadows this one, so
     * this is the only place the two can be told apart.
     */
    @Nullable
    protected Listing<Contribution> currentListing() {
        return paginator == null ? null : paginator.getCurrentListing();
    }

    /**
     * The cursor for the page after the last one fetched, or {@code null} if nothing has been
     * fetched yet -- or if the listing has ended, which is the same answer. Stored in the cached
     * blob beside the rows it belongs to, and read back into {@link #restoreAfterToken}.
     */
    @Nullable
    public String getAfterToken() {
        final Listing<Contribution> listing = currentListing();
        final String after = listing == null ? null : listing.getAfter();
        // Restored from cache and not paged since, so there is no paginator to ask -- but the
        // cursor the restore came with is still the right one.
        return after != null ? after : restoreAfterToken;
    }

    /**
     * The tab as it was last written to disk, or {@code null} when there is nothing usable there
     * and the caller must fetch instead.
     *
     * <p>Runs on the loader's background thread, and does everything to the restored rows that the
     * online path does to a freshly fetched page. Skipping that is what made the feed's first
     * offline branch render every post as unseen with no previews.
     */
    @Nullable
    protected ArrayList<Contribution> rebuildFromCache() {
        if (!restoreFromCache) {
            return null;
        }
        // One attempt, hit or miss: a later pull-to-refresh must reach the network.
        restoreFromCache = false;
        final String key = cacheKey();
        if (restoreCacheKey != null && !restoreCacheKey.equals(key)) {
            // The snapshot describes a different list than this tab is about to page. The Saved
            // tab's category is a menu selection held only in memory, so a categorised listing
            // comes back uncategorised: loading the recorded blob would put that category's rows
            // on screen under a paginator fetching everything, and the two would interleave at the
            // first scroll to the bottom. Fetch instead.
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
        // The blob's cursor, not one recorded anywhere else: it is written into the same file as
        // the items it belongs to, so the two can never disagree. The snapshot is written on a
        // pause or a settle and the blob after every page, so a copy kept there is a page behind
        // whenever the user scrolled into a new page and left without pausing -- and resuming from
        // a page-behind cursor re-fetches a page the restored list already has, twice over.
        restoreAfterToken = cached.afterToken;
        if (restoreAfterToken == null) {
            // No cursor anywhere means the cached list is the whole listing -- Reddit returned
            // nothing after it. Say so, because the alternative is worse than not paging: the next
            // bottom-hit would build a paginator with nothing to resume from, fetch page one, and
            // append the entire list a second time.
            nomore = true;
        }
        HasSeen.setHasSeenContrib(cached.posts);
        warmPreviews(cached.posts);
        restoredFromCache = true;
        return cached.posts;
    }

    /** Preload thumbnails for the submissions in {@code data}; comments have none. */
    protected void warmPreviews(ArrayList<Contribution> data) {
        ArrayList<Submission> submissions = new ArrayList<>();
        for (Contribution c : data) {
            if (c instanceof Submission) {
                submissions.add((Submission) c);
            }
        }
        if (!(SettingValues.noImages
                && ((!NetworkUtil.isConnectedWifi(adapter.mContext) && SettingValues.lowResMobile)
                        || SettingValues.lowResAlways))) {
            PhotoLoader.loadPhotos(adapter.mContext, submissions);
        }
    }

    public class LoadData extends AsyncTask<String, Void, ArrayList<Contribution>> {
        final boolean reset;

        /**
         * Whether this particular load came out of {@link ContributionCache}. Per-task rather than
         * per-source: two loads overlap (one's onPostExecute can follow the next one's
         * doInBackground), and a shared flag would have each answering for the other. Named apart
         * from {@code ContributionPostsSaved.servedFromCache}, its TTL cache's flag, which this
         * class's subclass would otherwise shadow.
         */
        boolean fromHibernateCache;

        public LoadData(boolean reset) {
            this.reset = reset;
        }

        @Override
        public void onPostExecute(ArrayList<Contribution> submissions) {
            loading = false;

            if (submissions != null && error) {
                // The page that follows a failure has to put the real list back; setError swapped
                // the RecyclerView's adapter out for the error view and nothing else restores it,
                // so a tab that failed once stayed on that view even after a refresh worked.
                error = false;
                adapter.undoSetError();
            }

            if (submissions != null && !submissions.isEmpty()) {
                // new submissions found

                if (reset || posts == null) {
                    posts = submissions;
                } else {
                    posts.addAll(submissions);
                }

                // update online
                if (refreshLayout != null) {
                    refreshLayout.setRefreshing(false);
                }

                // Re-apply filter if active (for ContributionAdapter). A reset replaced the list,
                // so its hits have to be recomputed from scratch; anything else only appended.
                if (adapter instanceof ContributionAdapter) {
                    ((ContributionAdapter) adapter).onDataUpdated(reset ? null : submissions);
                }

                // Always use notifyDataSetChanged() to ensure correct rendering
                // This handles both filtered and unfiltered data correctly
                adapter.notifyDataSetChanged();

            } else if (submissions != null) {
                // end of submissions
                nomore = true;

                // Re-apply filter if active (for ContributionAdapter)
                if (adapter instanceof ContributionAdapter) {
                    ((ContributionAdapter) adapter).onDataUpdated();
                }

                adapter.notifyDataSetChanged();

            } else if (!nomore) {
                // error
                error = true;
                adapter.setError(true);
            }
            refreshLayout.setRefreshing(false);

            if (loadCompleteListener != null) {
                loadCompleteListener.onLoadComplete(submissions != null);
            }

            // Keep this tab's list on disk so a hibernate resume can put it back without asking
            // Reddit for it again. Only worth writing while the feature is on; the write itself is
            // queued off this thread. Nothing to write on the branch that restored it, or on the
            // branch that failed -- a null result leaves the list exactly as the last write found
            // it. Nothing is written mid-search either: a deep search pages the whole history, and
            // rewriting a longer list after every page of it is work nobody asked for. This runs
            // after the callback rather than before it so that the page which ends the search --
            // the callback clears the listener there -- is the one that writes, leaving the cache
            // holding everything the search paged in rather than the prefix from before it.
            // `posts` is always the whole listing: a search narrows what the adapter renders, not
            // this field.
            if (SettingValues.hibernateActive()
                    && submissions != null
                    && !fromHibernateCache
                    && loadCompleteListener == null
                    && posts != null
                    && !posts.isEmpty()) {
                ContributionCache.store(cacheKey(), posts, getAfterToken());
            }
        }

        @Override
        protected @Nullable ArrayList<Contribution> doInBackground(
                String... subredditPaginators) {
            if (reset) {
                final ArrayList<Contribution> restored = rebuildFromCache();
                if (restored != null) {
                    fromHibernateCache = true;
                    return restored;
                }
                // A reset is "start from the top", so a cursor left over from a restore that did
                // not happen must not seed the paginator built below, and a list that had reached
                // its end can page again from the fresh one.
                restoreAfterToken = null;
                nomore = false;
            }
            ArrayList<Contribution> newData = new ArrayList<>();
            try {
                if (reset || paginator == null) {
                    paginator =
                            new ResumableUserProfilePaginator(
                                    Authentication.reddit, where, subreddit);

                    paginator.setSorting(Profile.profSort != null ? Profile.profSort : Sorting.HOT);
                    paginator.setTimePeriod(Profile.profTime != null ? Profile.profTime : TimePeriod.ALL);
                    // JRAW sends no limit at all unless one is set, so every page was Reddit's
                    // default 25. A search pages the whole history, and at 25 a listing that stops
                    // at Reddit's ~1000-item cap took 40 round trips instead of 10.
                    paginator.setLimit(Paginator.RECOMMENDED_MAX_LIMIT);
                    // Picks up where the hibernated session left off. Without it the rebuilt
                    // paginator starts at page one and onPostExecute appends the whole restored
                    // list again, since it does not deduplicate.
                    paginator.setResumeAfter(restoreAfterToken);
                }

                if (!paginator.hasNext()) {
                    nomore = true;
                    return new ArrayList<>();
                }
                final Listing<Contribution> page = paginator.next();
                // The paginator's own cursor is the authority from here. Left in place, the restore
                // token would be what getAfterToken() falls back to at the end of the listing, and
                // the next session would resume from the middle of a list it already has.
                restoreAfterToken = null;
                int emptyPages = 0;
                Listing<Contribution> current = page;
                while (true) {
                    for (Contribution c : current) {
                        if (c instanceof Submission) {
                            Submission s = (Submission) c;
                            if (!PostMatch.doesMatch(s)) {
                                newData.add(s);
                            }
                        } else {
                            newData.add(c);
                        }
                    }
                    // A page Reddit answered with rows, all of which the user's filters removed,
                    // is not the end of the listing -- but an empty result reads as one to
                    // onPostExecute, which would stop paging with most of the history unread.
                    if (!newData.isEmpty()
                            || current.isEmpty()
                            || !paginator.hasNext()
                            || ++emptyPages >= MAX_EMPTY_PAGES) {
                        break;
                    }
                    current = paginator.next();
                }

                HasSeen.setHasSeenContrib(newData);

                // A deep search pages past these rows without ever showing them, and loadPhotos
                // blocks this thread on the first screenful of each page. The rows that do end up
                // on screen are warmed by the visible-row sweeps instead.
                if (!isDeepSearching()) {
                    warmPreviews(newData);
                }

                return newData;
            } catch (Exception e) {
                // Logged rather than swallowed: this catch is what hid the profile's Gilded tab
                // answering with an HTML page (JRAW then rejects the content type) behind a
                // generic error view for as long as the tab has existed.
                LogUtil.e(
                        e,
                        "Could not load /user/"
                                + subreddit
                                + "/"
                                + where
                                + (e instanceof NetworkException
                                        ? " (HTTP "
                                                + ((NetworkException) e).getResponse().getStatusCode()
                                                + ")"
                                        : ""));
                return null;
            }
        }
    }
}
