package me.edgan.redditslide.Adapters;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.ContributionCache;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SavedPostCache;
import me.edgan.redditslide.SettingValues;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.Submission;

/** Created by ccrama on 9/17/2015. */
public class ContributionPostsSaved extends ContributionPosts {
    private final @Nullable String category;

    /** Set true before a reset load to skip the hard-TTL cache and force a fresh network fetch. */
    public boolean bypassCache;

    /** Marks that the last load was served from cache, so we don't re-stamp its TTL. */
    private boolean servedFromCache;

    public ContributionPostsSaved(String subreddit, String where, @Nullable String category) {
        super(subreddit, where);
        this.category = category;
    }

    @SuppressWarnings("NullAway.Init") // assigned in onPostExecute
    ResumableUserSavedPaginator paginator;

    /** The Saved tab caches per category, so the category is part of what identifies the list. */
    @Override
    public String cacheKey() {
        return ContributionCache.key(subreddit, where, category);
    }

    /** This class paginates through its own field, which shadows the one in the superclass. */
    @Override
    @Nullable
    protected Listing<Contribution> currentListing() {
        return paginator == null ? null : paginator.getCurrentListing();
    }

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
                SavedPostCache.store(Authentication.nameOrEmpty(), category, posts, true);
            }
            super.onPostExecute(submissions);
        }

        @Override
        protected @Nullable ArrayList<Contribution> doInBackground(
                String... subredditPaginators) {
            servedFromCache = false;
            boolean bypass = bypassCache;
            if (reset) {
                bypassCache = false; // one-shot: consume the bypass request
                nomore = false; // a fresh reset can page again even after a prior "no more"
                // Ahead of the TTL cache below: a hibernate restore carries the scroll anchor and
                // the listing cursor that go with this exact list, which the TTL cache does not.
                final ArrayList<Contribution> restored = rebuildFromCache();
                if (restored != null) {
                    fromHibernateCache = true;
                    return restored;
                }
                restoreAfterToken = null;
                if (!bypass && SavedPostCache.isFresh(Authentication.nameOrEmpty(), category)) {
                    SavedPostCache.Cached cached =
                            SavedPostCache.load(Authentication.nameOrEmpty(), category);
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
                    paginator =
                            new ResumableUserSavedPaginator(
                                    Authentication.reddit, where, subreddit);
                    paginator.setSorting(SettingValues.getSubmissionSort(subreddit));
                    paginator.setTimePeriod(SettingValues.getSubmissionTimePeriod(subreddit));
                    if (category != null) paginator.setCategory(category);
                    // Picks up where the hibernated session left off; see ContributionPosts.
                    paginator.setResumeAfter(restoreAfterToken);
                }

                if (!paginator.hasNext()) {
                    nomore = true;
                    return new ArrayList<>();
                }
                final Listing<Contribution> page = paginator.next();
                // See ContributionPosts: the paginator's cursor supersedes the restore token.
                restoreAfterToken = null;
                for (Contribution c : page) {
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

                warmPreviews(newData);

                return newData;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
