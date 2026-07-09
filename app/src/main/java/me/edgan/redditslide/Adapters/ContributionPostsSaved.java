package me.edgan.redditslide.Adapters;

import java.util.ArrayList;
import java.util.Map;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SavedPostCache;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PhotoLoader;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.UserSavedPaginator;

/** Created by ccrama on 9/17/2015. */
public class ContributionPostsSaved extends ContributionPosts {
    private final String category;

    /** Set true before a reset load to skip the hard-TTL cache and force a fresh network fetch. */
    public boolean bypassCache;

    /** Marks that the last load was served from cache, so we don't re-stamp its TTL. */
    private boolean servedFromCache;

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
            // An empty page means we've paged to the end: the accumulated posts are the complete
            // saved list, so cache it. Do this before super runs -- super fires the deep-search
            // load-complete callback that applies the search filter, and we want to snapshot the
            // unfiltered list. Skip when we merely served the list from cache (don't re-stamp TTL).
            if (submissions != null
                    && submissions.isEmpty()
                    && !servedFromCache
                    && posts != null) {
                // Cache the whole accumulated list (submissions AND saved comments), in order.
                SavedPostCache.store(Authentication.name, category, posts, true);
            }
            super.onPostExecute(submissions);
        }

        @Override
        protected ArrayList<Contribution> doInBackground(String... subredditPaginators) {
            servedFromCache = false;
            boolean bypass = bypassCache;
            if (reset) {
                bypassCache = false; // one-shot: consume the bypass request
                nomore = false; // a fresh reset can page again even after a prior "no more"
                if (!bypass && SavedPostCache.isFresh(Authentication.name, category)) {
                    SavedPostCache.Cached cached =
                            SavedPostCache.load(Authentication.name, category);
                    if (cached != null && cached.complete) {
                        servedFromCache = true;
                        nomore = true; // the cache holds the whole saved list
                        // Refresh seen state the same way the network path does.
                        HasSeen.setHasSeenContrib(cached.posts);
                        return new ArrayList<Contribution>(cached.posts);
                    }
                }
            }

            ArrayList<Contribution> newData = new ArrayList<>();
            try {
                if (reset || paginator == null) {
                    // Request post previews/thumbnails the same way the main feed does so they
                    // show up here regardless of the account's Reddit media preference (#274).
                    paginator =
                            new UserSavedPaginator(Authentication.reddit, where, subreddit) {
                                @Override
                                protected Map<String, String> getExtraQueryArgs() {
                                    Map<String, String> args = super.getExtraQueryArgs();
                                    args.put("feature", "link_preview");
                                    args.put("always_show_media", "1");
                                    args.put("sr_detail", "true");
                                    return args;
                                }
                            };
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
