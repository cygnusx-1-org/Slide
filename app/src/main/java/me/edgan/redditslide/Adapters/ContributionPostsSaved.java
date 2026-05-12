package me.edgan.redditslide.Adapters;

import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PhotoLoader;

import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.UserSavedPaginator;

import java.util.ArrayList;

/** Created by ccrama on 9/17/2015. */
public class ContributionPostsSaved extends ContributionPosts {
    private final String category;

    public ContributionPostsSaved(String subreddit, String where, String category) {
        super(subreddit, where);
        this.category = category;
    }

    UserSavedPaginator paginator;

    @Override
    public void loadMore(ContributionAdapter adapter, String subreddit, boolean reset) {
        new LoadData(reset).execute(subreddit);
    }

    public class LoadData extends ContributionPosts.LoadData {

        public LoadData(boolean reset) {
            super(reset);
        }

        @Override
        public void onPostExecute(ArrayList<Contribution> submissions) {
            super.onPostExecute(submissions);
        }

        @Override
        protected ArrayList<Contribution> doInBackground(String... subredditPaginators) {
            ArrayList<Contribution> newData = new ArrayList<>();
            try {
                if (reset || paginator == null) {
                    paginator = new UserSavedPaginator(Authentication.reddit, where, subreddit);
                    paginator.setSorting(SettingValues.getSubmissionSort(subreddit));
                    paginator.setTimePeriod(SettingValues.getSubmissionTimePeriod(subreddit));
                    if (category != null) paginator.setCategory(category);
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
