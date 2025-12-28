package me.edgan.redditslide.Adapters;

import android.os.AsyncTask;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import me.edgan.redditslide.Activities.Profile;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PhotoLoader;

import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.TimePeriod;
import net.dean.jraw.paginators.UserContributionPaginator;
import net.dean.jraw.paginators.UserProfilePaginator;

import java.util.ArrayList;

/** Created by ccrama on 9/17/2015. */
public class ContributionPosts extends GeneralPosts {
    protected final String where;
    protected final String subreddit;
    public boolean loading;
    private UserContributionPaginator paginator;
    protected SwipeRefreshLayout refreshLayout;
    protected ContributionAdapter adapter;

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
        new LoadData(reset).execute(subreddit);
    }

    public class LoadData extends AsyncTask<String, Void, ArrayList<Contribution>> {
        final boolean reset;

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

                if (reset || posts == null) {
                    posts = submissions;
                    start = -1;
                } else {
                    posts.addAll(submissions);
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

            } else if (submissions != null) {
                // end of submissions
                nomore = true;
                adapter.notifyDataSetChanged();

            } else if (!nomore) {
                // error
                adapter.setError(true);
            }
            refreshLayout.setRefreshing(false);
        }

        @Override
        protected ArrayList<Contribution> doInBackground(String... subredditPaginators) {
            ArrayList<Contribution> newData = new ArrayList<>();
            try {
                if (reset || paginator == null) {
                    paginator =
                            new UserProfilePaginator(Authentication.reddit, where, subreddit);

                    paginator.setSorting(Profile.profSort != null ? Profile.profSort : Sorting.HOT);
                    paginator.setTimePeriod(Profile.profTime != null ? Profile.profTime : TimePeriod.ALL);
                }

                if (!paginator.hasNext()) {
                    nomore = true;
                    return new ArrayList<>();
                }
                for (Contribution c : paginator.next()) {
                    if (c instanceof Submission) {
                        Submission s = (Submission) c;
                        if (!PostMatch.doesMatch(s)) {
                            newData.add(s);
                        }
                    } else {
                        newData.add(c);
                    }
                }

                HasSeen.setHasSeenContrib(newData);

                // Preload thumbnails for submissions (not comments)
                ArrayList<Submission> submissions = new ArrayList<>();
                for (Contribution c : newData) {
                    if (c instanceof Submission) {
                        submissions.add((Submission) c);
                    }
                }
                if (!(SettingValues.noImages
                        && ((!NetworkUtil.isConnectedWifi(adapter.mContext)
                                        && SettingValues.lowResMobile)
                                || SettingValues.lowResAlways))) {
                    PhotoLoader.loadPhotos(adapter.mContext, submissions);
                }

                return newData;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
