package me.edgan.redditslide.Adapters;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import me.edgan.redditslide.Activities.MainActivity;
import me.edgan.redditslide.Activities.SubredditView;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.BuildConfig;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.LastComments;
import me.edgan.redditslide.OfflineSubreddit;
import me.edgan.redditslide.PostLoader;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.R;
import me.edgan.redditslide.Random.RandomSubreddits;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionCache;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PhotoLoader;
import me.edgan.redditslide.util.TimeUtils;
import net.dean.jraw.http.NetworkException;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.DomainPaginator;
import net.dean.jraw.paginators.Paginator;
import net.dean.jraw.paginators.SubredditPaginator;

/**
 * This class is reponsible for loading subreddit specific submissions {@link loadMore(Context,
 * SubmissionDisplay, boolean, String)} is implemented asynchronously.
 *
 * <p>Created by ccrama on 9/17/2015.
 */
public class SubredditPosts implements PostLoader {
    public List<Submission> posts;
    public String subreddit;
    @SuppressWarnings("NullAway.Init") // assigned in onPostExecute
    public String subredditRandom;
    public boolean nomore = false;
    public boolean offline;
    public boolean forced;
    public boolean loading;
    public boolean error;
    @SuppressWarnings("NullAway.Init") // assigned in getNextFiltered/onPostExecute
    private Paginator paginator;
    @SuppressWarnings("NullAway.Init") // set in doInBackground
    public OfflineSubreddit cached;
    Context c;
    boolean force18;

    public SubredditPosts(String subreddit, Context c) {
        posts = new ArrayList<>();
        this.subreddit = subreddit;
        this.c = c;
    }

    public SubredditPosts(String subreddit, Context c, boolean force18) {
        posts = new ArrayList<>();
        this.subreddit = subreddit;
        this.c = c;
        this.force18 = force18;
    }

    @Override
    public void loadMore(
            final Context context, final SubmissionDisplay display, final boolean reset) {
        new LoadData(context, display, reset).execute(subreddit);
    }

    public void loadMore(
            Context context, SubmissionDisplay display, boolean reset, String subreddit) {
        this.subreddit = subreddit;
        loadMore(context, display, reset);
    }

    @SuppressWarnings("NullAway.Init") // assigned in doMainActivityOffline/onPostExecute
    public ArrayList<String> all;

    @Override
    public List<Submission> getPosts() {
        return posts;
    }

    @Override
    public boolean hasMore() {
        return !nomore;
    }

    boolean authedOnce = false;
    boolean usedOffline;
    public long currentid;
    @SuppressWarnings("NullAway.Init") // LoadData's constructor assigns this
    public SubmissionDisplay displayer;

    /** Asynchronous task for loading data */
    private class LoadData extends AsyncTask<String, Void, List<Submission>> {
        final boolean reset;
        Context context;

        public LoadData(Context context, SubmissionDisplay display, boolean reset) {
            this.context = context;
            displayer = display;
            this.reset = reset;
        }

        public int start;

        @Override
        public void onPreExecute() {
            if (reset) {
                posts.clear();
                displayer.onAdapterUpdated();
            }
        }

        @Override
        public void onPostExecute(final List<Submission> submissions) {
            boolean success = true;
            loading = false;

            if (error != null) {
                if (error instanceof NetworkException) {
                    NetworkException e = (NetworkException) error;
                    if (e.getResponse().getStatusCode() == 403 && !authedOnce) {
                        if (Reddit.authentication != null && Authentication.didOnline) {
                            Reddit.authentication.updateToken(context);
                        } else if (NetworkUtil.isConnected(context)
                                && Reddit.authentication == null) {
                            Reddit.authentication = new Authentication(context);
                        }
                        authedOnce = true;
                        loadMore(context, displayer, reset, subreddit);
                        return;
                    } else {
                        Toast.makeText(
                                        context,
                                        "A server error occurred, "
                                                + e.getResponse().getStatusCode()
                                                + (e.getResponse().getStatusMessage().isEmpty()
                                                        ? ""
                                                        : ": "
                                                                + e.getResponse()
                                                                        .getStatusMessage()),
                                        Toast.LENGTH_SHORT)
                                .show();
                    }
                }
                success = false;
            } else if (submissions != null && !submissions.isEmpty()) {
                if (displayer instanceof SubmissionsView
                        && ((SubmissionsView) displayer).adapter.isError) {
                    ((SubmissionsView) displayer).adapter.undoSetError();
                }

                // update online
                displayer.updateSuccess(posts, start);
                currentid = 0;
                OfflineSubreddit.currentid = currentid;

                if (RandomSubreddits.isRandom(subreddit)) {
                    // The name is resolved before the fetch now, so this only confirms it. Keep
                    // the resolved pick when the submission carries no subreddit name, rather
                    // than blanking it and losing the pick for the retry path.
                    final String fromSubmission =
                            MiscUtil.orEmpty(submissions.get(0).getSubredditName());
                    if (!fromSubmission.isEmpty()) {
                        subredditRandom = fromSubmission;
                    }
                }

                MainActivity.randomoverride = subredditRandom;

                handOffResolvedRandom();
            } else if (submissions != null) {
                // end of submissions
                nomore = true;
                displayer.updateSuccess(posts, posts.size() + 1);

                // A random pick can resolve and still return nothing to show — an NSFW subreddit
                // with NSFW hidden has every post filtered out by PostMatch. The name still has
                // to reach SubredditView, or the over-18 interstitial that would offer to load it
                // anyway never runs and the screen stays blank with no way forward.
                handOffResolvedRandom();
            } else if (MainActivity.isRestart) {
                posts = new ArrayList<>();
                cached = OfflineSubreddit.getSubreddit(subreddit, 0L, true, c);
                for (Submission s : cached.submissions) {
                    if (!PostMatch.doesMatch(s, subreddit, force18)) {
                        posts.add(s);
                    }
                }
                offline = false;
                usedOffline = false;
                // posts was just replaced wholesale from cache, so force a full redraw
                // (-1) rather than a targeted insert against a now-mismatched offset.
                displayer.updateSuccess(posts, -1);
            } else {
                if (!all.isEmpty() && !nomore && SettingValues.cache) {
                    if (context instanceof MainActivity) {
                        doMainActivityOffline(context, displayer);
                    }
                } else if (!nomore) {
                    // error
                    LogUtil.v("Setting error");
                    success = false;
                }
            }

            SubredditPosts.this.error = !success;
        }

        /**
         * Turns "random", "myrandom" or "randnsfw" into a real subreddit name before anything is
         * fetched.
         *
         * <p>This used to be a side effect of fetching: Reddit resolved /r/random itself and the
         * name was read back off the first submission. Those endpoints are gone, so a name is
         * chosen up front instead and everything downstream of {@code subredditRandom} carries on
         * unchanged.
         *
         * @param fresh true to draw a new subreddit — a first load or a refresh, both of which
         *     start a new listing and so should land somewhere new. False keeps the current pick,
         *     which is what the 500-retry below needs: it rebuilds the paginator for the listing
         *     already on screen and must not move it to a different subreddit.
         */
        private String resolveRandom(String sub, boolean fresh) {
            if (!RandomSubreddits.isRandom(sub)) {
                return sub;
            }
            if (!fresh) {
                final String current = currentRandomPick(sub);
                if (!current.isEmpty()) {
                    subredditRandom = current;
                    return current;
                }
            }
            final String picked = RandomSubreddits.pick(context, sub);
            if (picked != null && !picked.isEmpty()) {
                subredditRandom = picked;
                MainActivity.randomoverride = picked;
                return picked;
            }
            // Nothing could be resolved: no cached list and no network on a first use, or the
            // batch check failed. Reuse whatever was already landed on before falling back to the
            // special name, which Reddit answers with a 404.
            final String current = currentRandomPick(sub);
            return current.isEmpty() ? sub : current;
        }

        /**
         * The subreddit this random listing is already on, or empty if there is not one yet.
         *
         * <p>Three carriers, widest last, because a reload can arrive on a brand new loader: this
         * loader's own pick; the last pick {@link RandomSubreddits} made for this special name,
         * which survives the adapter being rebuilt; and finally {@code randomoverride}, which
         * {@code SubmissionAdapter} clears on construction and so cannot be relied on alone.
         */
        private String currentRandomPick(String sub) {
            if (subredditRandom != null && !subredditRandom.isEmpty()) {
                return subredditRandom;
            }
            final String last = MiscUtil.orEmpty(RandomSubreddits.lastPick(sub));
            if (!last.isEmpty()) {
                return last;
            }
            return MiscUtil.orEmpty(MainActivity.randomoverride);
        }

        /**
         * Hands the subreddit a random name resolved to over to the hosting {@link SubredditView},
         * so its title, sidebar and over-18 interstitial act on the real subreddit rather than on
         * "random".
         *
         * <p>Called from both the loaded and the empty branch of {@link #onPostExecute}: resolving
         * a name and having posts to show are independent. A pick that lands on an NSFW subreddit
         * while NSFW content is hidden returns posts that PostMatch then filters away entirely, and
         * that is exactly the case where the interstitial needs to run.
         */
        private void handOffResolvedRandom() {
            if (!(context instanceof SubredditView) || !RandomSubreddits.isRandom(subreddit)) {
                return;
            }
            if (subredditRandom == null || subredditRandom.isEmpty()) {
                return;
            }
            final SubredditView view = (SubredditView) context;
            if (subredditRandom.equalsIgnoreCase(view.subreddit)) {
                // Already handed off. executeAsyncSubreddit re-raises the over-18 interstitial
                // every time it runs against an NSFW subreddit, so repeating it here would prompt
                // again for the very subreddit the user just accepted — and accepting rebuilds the
                // loader, which lands back here, with no way through to the posts.
                return;
            }
            view.subreddit = subredditRandom;
            view.executeAsyncSubreddit(subredditRandom);
        }

        @Override
        protected @Nullable List<Submission> doInBackground(String... subredditPaginators) {
            if (BuildConfig.DEBUG) LogUtil.v("Loading data");
            if ((!NetworkUtil.isConnected(context) && !Authentication.didOnline)
                    || MainActivity.isRestart) {
                Log.v(LogUtil.getTag(), "Using offline data");
                offline = true;
                usedOffline = true;
                all = OfflineSubreddit.getAll(subreddit);
                return null;
            } else {
                offline = false;
                usedOffline = false;
            }

            if (reset || paginator == null) {
                offline = false;
                nomore = false;
                // force18 means the user just accepted the over-18 interstitial for the
                // subreddit this listing resolved to, on a loader rebuilt by doAdapter(true).
                // That is a reload of what they agreed to see, not a request for somewhere new.
                String sub =
                        resolveRandom(
                                subredditPaginators[0].toLowerCase(Locale.ENGLISH), !force18);
                if (sub.equals("frontpage")) {
                    paginator = new SubredditPaginator(Authentication.reddit);
                } else if (!sub.contains(".")) {
                    paginator = new SubredditPaginator(Authentication.reddit, sub);
                } else {
                    paginator = new DomainPaginator(Authentication.reddit, sub);
                }
                paginator.setSorting(SettingValues.getSubmissionSort(subreddit));
                paginator.setTimePeriod(SettingValues.getSubmissionTimePeriod(subreddit));
                paginator.setLimit(Paginator.RECOMMENDED_MAX_LIMIT);
            }

            List<Submission> filteredSubmissions = getNextFiltered();

            if (!(SettingValues.noImages
                    && ((!NetworkUtil.isConnectedWifi(c) && SettingValues.lowResMobile)
                            || SettingValues.lowResAlways))) {
                PhotoLoader.loadPhotos(
                        c,
                        filteredSubmissions,
                        subreddit == null ? null : subreddit.toLowerCase(Locale.ENGLISH));
            }
            if (SettingValues.storeHistory) {
                HasSeen.setHasSeenSubmission(filteredSubmissions);
                LastComments.setCommentsSince(filteredSubmissions);
            }
            SubmissionCache.cacheSubmissions(filteredSubmissions, context, subreddit);

            if (reset || offline || posts == null) {
                posts = new ArrayList<>(new LinkedHashSet<>(filteredSubmissions));
                start = -1;
            } else {
                int oldSize = posts.size();
                posts.addAll(filteredSubmissions);
                posts = new ArrayList<>(new LinkedHashSet<>(posts));
                offline = false;
                // Adapter offset of the first newly appended post. The trailing
                // notifyDataSetChanged backstop has been removed, so this must be
                // the real insert offset (not the new total).
                start = oldSize;
            }

            if (!usedOffline) {
                OfflineSubreddit.getSubNoLoad(subreddit.toLowerCase(Locale.ENGLISH))
                        .overwriteSubmissions(posts)
                        .writeToMemory(context);
            }

            return filteredSubmissions;
        }

        public ArrayList<Submission> getNextFiltered() {
            ArrayList<Submission> filteredSubmissions = new ArrayList<>();
            ArrayList<Submission> adding = new ArrayList<>();

            try {
                if (paginator != null && paginator.hasNext()) {
                    if (force18 && paginator instanceof SubredditPaginator) {
                        ((SubredditPaginator) paginator).setObeyOver18(false);
                    }
                    adding.addAll(paginator.next());
                } else {
                    nomore = true;
                }

                for (Submission s : adding) {
                    if (!PostMatch.doesMatch(
                            s,
                            paginator instanceof SubredditPaginator
                                    ? ((SubredditPaginator) paginator).getSubreddit()
                                    : ((DomainPaginator) paginator).getDomain(),
                            force18)) {
                        filteredSubmissions.add(s);
                    }
                }
                if (paginator != null && paginator.hasNext() && filteredSubmissions.isEmpty()) {
                    filteredSubmissions.addAll(getNextFiltered());
                }
            } catch (Exception e) {
                if (e instanceof NetworkException
                        && ((NetworkException) e).getResponse().getStatusCode() == 500
                        && retryCount < 2) {
                    retryCount++;
                    int newLimit = Paginator.RECOMMENDED_MAX_LIMIT;
                    for (int r = 0; r < retryCount; r++) {
                        newLimit /= 2;
                    }
                    String sub = resolveRandom(subreddit.toLowerCase(Locale.ENGLISH), false);
                    if (sub.equals("frontpage")) {
                        paginator = new SubredditPaginator(Authentication.reddit);
                    } else if (!sub.contains(".")) {
                        paginator = new SubredditPaginator(Authentication.reddit, sub);
                    } else {
                        paginator = new DomainPaginator(Authentication.reddit, sub);
                    }
                    paginator.setSorting(SettingValues.getSubmissionSort(subreddit));
                    paginator.setTimePeriod(SettingValues.getSubmissionTimePeriod(subreddit));
                    paginator.setLimit(newLimit);
                    if (force18 && paginator instanceof SubredditPaginator) {
                        ((SubredditPaginator) paginator).setObeyOver18(false);
                    }
                    return getNextFiltered();
                }
                LogUtil.e(e, "SubredditPosts.getNextFiltered failed");
                error = e;
                if (e.getMessage() != null && e.getMessage().contains("Forbidden")) {
                    Reddit.authentication.updateToken(context);
                }
            }
            return filteredSubmissions;
        }

        int retryCount = 0;
        @Nullable Exception error;
    }

    public void doMainActivityOffline(final Context c, final SubmissionDisplay displayer) {
        LogUtil.v(subreddit);
        if (all == null) {
            all = OfflineSubreddit.getAll(subreddit);
        }
        Collections.rotate(all, -1); // Move 0, or "submission only", to the end
        offline = true;

        final String[] titles = new String[all.size()];
        final String[] base = new String[all.size()];
        int i = 0;
        for (String s : all) {
            String[] split = s.split(",");
            titles[i] =
                    (Long.parseLong(split[1]) == 0
                            ? c.getString(R.string.settings_backup_submission_only)
                            : TimeUtils.getTimeAgo(Long.parseLong(split[1]), c)
                                    + c.getString(R.string.settings_backup_comments));
            base[i] = s;
            i++;
        }
        final ActionBar actionBar = ((MainActivity) c).getSupportActionBar();
        if (actionBar == null) {
            return;
        }

        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setListNavigationCallbacks(
                        new OfflineSubAdapter(c, android.R.layout.simple_list_item_1, titles),
                        new ActionBar.OnNavigationListener() {

                            @Override
                            public boolean onNavigationItemSelected(int itemPosition, long itemId) {
                                final String[] s2 = base[itemPosition].split(",");
                                OfflineSubreddit.currentid = Long.valueOf(s2[1]);
                                currentid = OfflineSubreddit.currentid;

                                new AsyncTask<Void, Void, Void>() {
                                    @SuppressWarnings("NullAway.Init") // set in doInBackground
                                    OfflineSubreddit cached;

                                    @Override
                                    protected Void doInBackground(Void... params) {
                                        cached =
                                                OfflineSubreddit.getSubreddit(
                                                        subreddit, Long.valueOf(s2[1]), true, c);
                                        List<Submission> finalSubs = new ArrayList<>();
                                        for (Submission s : cached.submissions) {
                                            if (!PostMatch.doesMatch(s, subreddit, force18)) {
                                                finalSubs.add(s);
                                            }
                                        }

                                        posts = finalSubs;

                                        return null;
                                    }

                                    @Override
                                    protected void onPostExecute(Void aVoid) {
                                        if (cached.submissions.isEmpty()) {
                                            displayer.updateOfflineError();
                                        }
                                        // update offline
                                        displayer.updateOffline(posts, Long.parseLong(s2[1]));
                                    }
                                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                return true;
                            }
                        });
    }
}
