package me.edgan.redditslide.Adapters;

import android.content.Context;
import android.os.AsyncTask;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.Fragments.SubredditListView;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;

import net.dean.jraw.models.Submission;
import net.dean.jraw.models.Subreddit;
import net.dean.jraw.paginators.Paginator;
import net.dean.jraw.paginators.SubredditPaginator;
import net.dean.jraw.paginators.SubredditSearchPaginator;
import net.dean.jraw.paginators.SubredditStream;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import me.edgan.redditslide.util.LogUtil;

/**
 * This class is reponsible for loading a list of subreddits from an endpoint {@link
 * loadMore(Context, SubmissionDisplay, boolean, String)} is implemented asynchronously.
 *
 * <p>Created by ccrama on 3/21/2016.
 */
public class SubredditNames {
    public List<Subreddit> posts;
    public String where;
    public boolean nomore = false;
    public boolean stillShow;
    public boolean loading;
    public SubredditListView parent;
    private Paginator<Subreddit> paginator;
    private SubredditPaginator trendingPaginator;
    private LinkedHashSet<String> trendingSeen;
    Context c;

    public SubredditNames(String where, Context c, SubredditListView parent) {
        posts = new ArrayList<>();
        this.parent = parent;
        this.where = where;
        this.c = c;
    }

    public void loadMore(Context context, boolean reset) {
        new LoadData(context, reset).execute(where);
    }

    public void loadMore(Context context, boolean reset, String where) {
        this.where = where;
        loadMore(context, reset);
    }

    public List<Subreddit> getPosts() {
        return posts;
    }

    /** Asynchronous task for loading data */
    private class LoadData extends AsyncTask<String, Void, List<Subreddit>> {
        final boolean reset;
        Context context;

        public LoadData(Context context, boolean reset) {
            this.context = context;
            this.reset = reset;
        }

        @Override
        public void onPostExecute(List<Subreddit> submissions) {

            loading = false;
            context = null;

            if (submissions != null && !submissions.isEmpty()) {
                ArrayList<Subreddit> toRemove = new ArrayList<>();
                for (Subreddit s : submissions) {
                    if (PostMatch.contains(
                            s.getDisplayName().toLowerCase(Locale.ENGLISH),
                            SettingValues.subredditFilters,
                            true)) toRemove.add(s);
                }
                submissions.removeAll(toRemove);
                // new submissions found
                int start = 0;
                if (posts != null) {
                    start = posts.size() + 1;
                }

                if (reset || posts == null) {
                    posts = new ArrayList<>(new LinkedHashSet<>(submissions));
                    start = -1;
                } else {
                    posts.addAll(submissions);
                    posts = new ArrayList<>(new LinkedHashSet<>(posts));
                }

                final int finalStart = start;

                // update online
                parent.updateSuccess(posts, finalStart);

            } else if (!nomore) {
                parent.updateError();
            }
        }

        @Override
        protected List<Subreddit> doInBackground(String... subredditPaginators) {

            List<Subreddit> things = new ArrayList<>();
            try {
                if (subredditPaginators[0].equalsIgnoreCase("trending")) {
                    // Reddit no longer maintains /r/trendingsubreddits, so derive a live
                    // "trending" list from /r/all instead: walk the hot feed and collect the
                    // subreddits the posts belong to, deduped in order of first appearance.
                    // Only a batch is resolved per call so the list paginates on scroll and we
                    // avoid resolving (one API call each) hundreds of subreddits up front.
                    stillShow = true;
                    if (reset || trendingPaginator == null) {
                        trendingPaginator = new SubredditPaginator(Authentication.reddit, "all");
                        trendingPaginator.setLimit(Constants.DEFAULT_PAGINATOR_LIMIT);
                        trendingSeen = new LinkedHashSet<>();
                    }

                    try {
                        int added = 0;
                        while (added < Constants.TRENDING_BATCH_SIZE
                                && trendingPaginator.hasNext()) {
                            for (Submission s : trendingPaginator.next()) {
                                String name = s.getSubredditName();
                                if (name == null || name.isEmpty()) continue;
                                // already shown in a previous batch
                                if (!trendingSeen.add(name)) continue;
                                try {
                                    things.add(Authentication.reddit.getSubreddit(name));
                                    added++;
                                } catch (Exception e) {
                                    LogUtil.e(e, "SubredditNames trending: failed /r/" + name);
                                }
                                if (added >= Constants.TRENDING_BATCH_SIZE) break;
                            }
                        }
                        if (!trendingPaginator.hasNext()) {
                            nomore = true;
                        }
                    } catch (Exception e) {
                        LogUtil.e(e, "SubredditNames.doInBackground trending failed");
                        if (e.getMessage() != null && e.getMessage().contains("Forbidden")) {
                            Reddit.authentication.updateToken(context);
                        }
                    }
                } else if (subredditPaginators[0].equalsIgnoreCase("popular")) {
                    stillShow = true;
                    if (reset || paginator == null) {
                        paginator =
                                new SubredditStream(Authentication.reddit, subredditPaginators[0]);
                        paginator.setSorting(SettingValues.getSubmissionSort(where));
                        paginator.setTimePeriod(SettingValues.getSubmissionTimePeriod(where));
                        paginator.setLimit(Constants.DEFAULT_PAGINATOR_LIMIT);
                    }

                    try {
                        if (paginator != null && paginator.hasNext()) {
                            things.addAll(paginator.next());
                        } else {
                            nomore = true;
                        }

                    } catch (Exception e) {
                        LogUtil.e(e, "SubredditNames.doInBackground failed");
                        if (e.getMessage().contains("Forbidden")) {
                            Reddit.authentication.updateToken(context);
                        }
                    }
                } else {
                    stillShow = true;
                    if (reset || paginator == null) {
                        paginator =
                                new SubredditSearchPaginator(
                                        Authentication.reddit, subredditPaginators[0]);
                        paginator.setSorting(SettingValues.getSubmissionSort(where));
                        paginator.setTimePeriod(SettingValues.getSubmissionTimePeriod(where));
                        paginator.setLimit(Constants.DEFAULT_PAGINATOR_LIMIT);
                    }

                    try {
                        if (paginator != null && paginator.hasNext()) {
                            things.addAll(paginator.next());

                        } else {
                            nomore = true;
                        }

                    } catch (Exception e) {
                        LogUtil.e(e, "SubredditNames.doInBackground failed");
                        if (e.getMessage().contains("Forbidden")) {
                            Reddit.authentication.updateToken(context);
                        }
                    }
                }
            } catch (Exception e) {
                return null;
            }
            return things;
        }
    }
}
