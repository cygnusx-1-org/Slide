package me.edgan.redditslide.Adapters;

import android.os.AsyncTask;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import java.util.ArrayList;
import java.util.Map;
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

/** Created by ccrama on 9/17/2015. */
public class ContributionPosts extends GeneralPosts {
    protected final String where;
    protected final String subreddit;
    public boolean loading;
    private UserContributionPaginator paginator;
    protected SwipeRefreshLayout refreshLayout;
    protected ContributionAdapter adapter;
    protected OnLoadCompleteListener loadCompleteListener;

    /** Notified after each page finishes loading so callers can page to the end. */
    public interface OnLoadCompleteListener {
        void onLoadComplete();
    }

    public void setOnLoadCompleteListener(OnLoadCompleteListener listener) {
        this.loadCompleteListener = listener;
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

                // Re-apply filter if active (for ContributionAdapter)
                if (adapter instanceof ContributionAdapter) {
                    ((ContributionAdapter) adapter).onDataUpdated();
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
                adapter.setError(true);
            }
            refreshLayout.setRefreshing(false);

            if (loadCompleteListener != null) {
                loadCompleteListener.onLoadComplete();
            }
        }

        @Override
        protected ArrayList<Contribution> doInBackground(String... subredditPaginators) {
            ArrayList<Contribution> newData = new ArrayList<>();
            try {
                if (reset || paginator == null) {
                    // Reddit only returns post previews/thumbnails when the request asks for
                    // them; otherwise it honors the account's media preference, which is why
                    // thumbnails went missing here (issue #274). Request them the same way the
                    // main feed (SubredditPaginator) does.
                    paginator =
                            new UserProfilePaginator(Authentication.reddit, where, subreddit) {
                                @Override
                                protected Map<String, String> getExtraQueryArgs() {
                                    Map<String, String> args = super.getExtraQueryArgs();
                                    args.put("feature", "link_preview");
                                    args.put("always_show_media", "1");
                                    args.put("sr_detail", "true");
                                    return args;
                                }
                            };

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
