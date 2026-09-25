package me.edgan.redditslide.Adapters;

import android.content.Context;
import android.os.AsyncTask;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.Constants;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.LastComments;
import me.edgan.redditslide.OfflineSubreddit;
import me.edgan.redditslide.PostLoader;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.Reddit;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.SubmissionCache;
import me.edgan.redditslide.UserSubscriptions;
import me.edgan.redditslide.util.LogUtil;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.NetworkUtil;
import me.edgan.redditslide.util.PhotoLoader;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.MultiReddit;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.MultiRedditPaginator;

/**
 * This class is reponsible for loading subreddit specific submissions {@link loadMore(Context,
 * SubmissionDisplay, boolean, String)} is implemented asynchronously.
 *
 * <p>Created by ccrama on 9/17/2015.
 */
public class MultiredditPosts implements PostLoader {
    public List<Submission> posts;
    public boolean nomore = false;
    public boolean stillShow;
    public boolean offline;
    public boolean loading;
    public String profile;

    /**
     * Rebuild this listing from the on-disk cache instead of fetching it, for the first load only.
     * Set when the app is coming back to a multireddit the user left -- a hibernate resume after
     * the process died -- so the feed they left is the feed they get back. Without it the page
     * refetches, and a listing that has moved on since comes back with a different post at the
     * top under a scroll offset restored for the old one.
     */
    public boolean restoreFromCache;

    /**
     * How many posts the listing held when it was recorded. A blob in the cache directory can be
     * reclaimed by the system at any time and {@code OfflineSubreddit} simply skips the ones that
     * have gone, so a restore can silently come back a fraction of its old length -- at which
     * point the recorded scroll position means nothing and a normal load is the better answer.
     */
    public int restoreExpectedCount;

    /** Fraction of the recorded post count a restore has to reach to be worth showing. */
    private static final double MIN_RESTORE_FRACTION = 0.5;

    /**
     * Listing cursor recorded with the restored posts, so the first "load more" after a restore
     * continues the listing instead of re-fetching page one. Held until a page actually comes
     * back: a failed request leaves it in place for the retry.
     */
    @Nullable public String restoreAfterToken;

    @SuppressWarnings("NullAway.Init") // assigned in onPostExecute
    private MultiRedditPaginator paginator;
    @SuppressWarnings("NullAway.Init") // assigned in loadMore
    Context c;
    @SuppressWarnings("NullAway.Init") // assigned in loadMore
    MultiredditAdapter adapter;

    public MultiredditPosts(String multireddit, String profile) {
        posts = new ArrayList<>();
        LogUtil.e("MJWHITTA: Profile is " + profile + ".");
        LogUtil.e("MJWHITTA: Multireddit is " + multireddit + ".");
        if (profile.isEmpty()) {
            this.multiReddit = UserSubscriptions.getMultiredditByDisplayName(multireddit);
        } else {
            this.multiReddit =
                    UserSubscriptions.getPublicMultiredditByDisplayName(profile, multireddit);
        }
        this.profile = profile;
    }

    @Override
    public void loadMore(Context context, SubmissionDisplay displayer, boolean reset) {
        this.c = context;
        if (multiReddit == null) {
            // The constructor could not resolve the name: UserSubscriptions.multireddits is null
            // after a failed sync, and loadPublicMultireddits stores a null for any profile whose
            // fetch threw. Both LoadData.doInBackground and its onPostExecute dereference this
            // without a guard, so stop here rather than there.
            displayer.updateError();
            return;
        }
        new LoadData(context, displayer, reset).execute(multiReddit);
    }

    public void loadMore(
            Context context,
            SubmissionDisplay displayer,
            boolean reset,
            MultiredditAdapter adapter) {
        this.adapter = adapter;
        this.c = context;
        loadMore(context, displayer, reset);
    }

    @Override
    public List<Submission> getPosts() {
        return posts;
    }

    // Null when the constructor could not resolve the name; loadMore is the single entry
    // point and reports the error rather than proceeding.
    @Nullable public MultiReddit multiReddit;

    /** Display name of the multireddit, or "" when it could not be resolved. */
    public String displayName() {
        return multiReddit == null ? "" : MiscUtil.orEmpty(multiReddit.getDisplayName());
    }

    /** Cache key part for a multireddit; empty when it could not be resolved. */
    private static String multiName(final @Nullable MultiReddit multi) {
        return multi == null ? "" : MiscUtil.orEmpty(multi.getDisplayName()).toLowerCase(Locale.ENGLISH);
    }

    /** The full cache key this listing is written under and read back from. */
    private String cacheKey() {
        return "multi_" + multiName(multiReddit);
    }

    /**
     * The cursor for the page after the last one fetched, or {@code null} if nothing has been
     * fetched yet. Recorded alongside a hibernated feed so it can be handed back to {@link
     * #restoreAfterToken}.
     */
    @Nullable
    public String getAfterToken() {
        if (paginator == null) {
            // Restored from cache and not paged since, so there is no paginator to ask -- but the
            // cursor the restore came with is still the right one.
            return restoreAfterToken;
        }
        final Listing<Submission> listing = paginator.getCurrentListing();
        final String after = listing == null ? null : listing.getAfter();
        return after != null ? after : restoreAfterToken;
    }

    /**
     * The listing as it was last written to disk, rebuilt and ready to display, or {@code null}
     * when there is not enough of it left to be worth restoring.
     *
     * <p>Everything the online path does to a freshly fetched page happens here too, so a restored
     * listing does not come back reading as unseen, with no new-comment counts and no warmed
     * previews.
     *
     * <p>Runs on the loader's background thread: rebuilding a hundred posts means a hundred file
     * reads and as many JSON parses.
     */
    @Nullable
    private List<Submission> rebuildFromCache(Context context) {
        final String key = cacheKey();
        // offline=false: read each blob as a bare submission rather than deserializing every
        // comment tree the user has since opened from this listing.
        final OfflineSubreddit stored = OfflineSubreddit.getSubreddit(key, 0L, false, context);
        if (stored == null) {
            return null;
        }
        final List<Submission> restored = new ArrayList<>();
        for (Submission s : stored.submissions) {
            if (!PostMatch.doesMatch(s, key, false)) {
                restored.add(s);
            }
        }
        if (restored.isEmpty()) {
            return null;
        }
        // A blob in the cache directory can be reclaimed at any point and the read back above
        // simply skips the ones that have gone, without shortening the stored name list.
        // Comparing against the count recorded with the scroll position is the only signal that
        // the listing came back a fraction of what it was.
        if (restoreExpectedCount > 0
                && restored.size() < restoreExpectedCount * MIN_RESTORE_FRACTION) {
            return null;
        }
        if (!(SettingValues.noImages
                && ((!NetworkUtil.isConnectedWifi(context) && SettingValues.lowResMobile)
                        || SettingValues.lowResAlways))) {
            PhotoLoader.loadPhotos(context, restored, key);
        }
        if (SettingValues.storeHistory) {
            HasSeen.setHasSeenSubmission(restored);
            LastComments.setCommentsSince(restored);
        }
        SubmissionCache.cacheSubmissions(restored, context, displayName());
        return restored;
    }

    @Override
    public boolean hasMore() {
        return !nomore;
    }

    boolean usedOffline;

    /** Asynchronous task for loading data */
    private class LoadData extends AsyncTask<MultiReddit, Void, List<Submission>> {
        final boolean reset;
        Context context;
        final SubmissionDisplay displayer;

        public LoadData(Context context, SubmissionDisplay displayer, boolean reset) {
            this.context = context;
            this.displayer = displayer;
            this.reset = reset;
        }

        @Override
        public void onPostExecute(List<Submission> submissions) {
            loading = false;

            if (submissions != null && !submissions.isEmpty()) {
                // new submissions found
                int start = 0;
                if (posts != null) {
                    // Adapter offset of the first newly appended post (old size, before
                    // the addAll below). updateSuccess adds the +1 for the spacer header
                    // and derives the insert count, so this must be the real offset.
                    start = posts.size();
                }

                if (reset || offline || posts == null) {
                    posts = new ArrayList<>(new LinkedHashSet<>(submissions));
                    start = -1;
                } else {
                    posts.addAll(submissions);
                    posts = new ArrayList<>(new LinkedHashSet<>(posts));
                    offline = false;
                }
                if (!usedOffline)
                    OfflineSubreddit.getSubreddit(
                                    "multi_"
                                            + multiName(multiReddit),
                                    false,
                                    context)
                            .overwriteSubmissions(posts)
                            .writeToMemory(c);

                final int finalStart = start;

                // update online
                displayer.updateSuccess(posts, finalStart);

            } else if (submissions != null) {
                // end of submissions
                nomore = true;
            } else if (!OfflineSubreddit.getSubreddit(
                                    "multi_"
                                            + multiName(multiReddit),
                                    false,
                                    context)
                            .submissions
                            .isEmpty()
                    && !nomore
                    && SettingValues.cache) {
                offline = true;
                final OfflineSubreddit cached =
                        OfflineSubreddit.getSubreddit(
                                "multi_" + multiName(multiReddit),
                                true,
                                context);

                List<Submission> finalSubs = new ArrayList<>();
                for (Submission s : cached.submissions) {
                    if (!PostMatch.doesMatch(
                            s,
                            "multi_" + multiName(multiReddit),
                            false)) {
                        finalSubs.add(s);
                    }
                }

                posts = finalSubs;

                if (!cached.submissions.isEmpty()) {
                    stillShow = true;
                } else {
                    displayer.updateOfflineError();
                }
                // update offline
                displayer.updateOffline(submissions, cached.time);
            } else if (!nomore) {
                // error
                displayer.updateError();
            }
        }

        @Override
        protected @Nullable List<Submission> doInBackground(MultiReddit... subredditPaginators) {
            if (restoreFromCache && reset) {
                restoreFromCache = false;
                final List<Submission> restored = rebuildFromCache(context);
                if (restored != null) {
                    // A restore is not an offline fallback: the listing is live, it simply has
                    // not been asked for again. Leaving these set is what would put the offline
                    // banner over a feed the user can still page.
                    offline = false;
                    usedOffline = false;
                    stillShow = true;
                    return restored;
                }
                // Too little of the recorded listing survived in the cache directory to be worth
                // showing. Fall through and fetch it: a short feed at a scroll offset that no
                // longer means anything is worse than a fresh one.
            }
            if (!NetworkUtil.isConnected(context)) {
                offline = true;
                return null;
            } else {
                offline = false;
            }

            stillShow = true;

            if (reset || paginator == null) {
                offline = false;

                if (reset) {
                    // A refresh is a request for the top of the listing, which is the one thing a
                    // resume token must not do.
                    restoreAfterToken = null;
                }
                final ResumableMultiRedditPaginator resumable =
                        new ResumableMultiRedditPaginator(
                                Authentication.reddit, subredditPaginators[0]);
                // Not cleared here: the paginator drops its own copy once a request has actually
                // succeeded, so a retry that rebuilds the paginator re-seeds rather than jumping
                // to the top of the listing.
                resumable.setResumeAfter(restoreAfterToken);
                paginator = resumable;
                paginator.setSorting(
                        SettingValues.getSubmissionSort(
                                "multi_"
                                        + MiscUtil.orEmpty(
                                                        subredditPaginators[0].getDisplayName())
                                                .toLowerCase(Locale.ENGLISH)));
                paginator.setTimePeriod(
                        SettingValues.getSubmissionTimePeriod(
                                "multi_"
                                        + MiscUtil.orEmpty(
                                                        subredditPaginators[0]
                                                                .getDisplayName())
                                                .toLowerCase(Locale.ENGLISH)));
                paginator.setLimit(Constants.DEFAULT_PAGINATOR_LIMIT);
            }

            List<Submission> things = new ArrayList<>();

            try {
                if (paginator != null && paginator.hasNext()) {
                    things.addAll(paginator.next());
                } else {
                    nomore = true;
                }

            } catch (Exception e) {
                LogUtil.e(e, "MultiredditPosts.doInBackground failed");
                if (String.valueOf(e.getMessage()).contains("Forbidden")) {
                    Reddit.authentication.updateToken(context);
                }
            }

            List<Submission> filteredSubmissions = new ArrayList<>();
            String multiName = "multi_" + MiscUtil.orEmpty(paginator.getMultiReddit().getDisplayName()).toLowerCase(Locale.ENGLISH);
            for (Submission s : things) {
                if (!PostMatch.doesMatch(s, multiName, false)) {
                    filteredSubmissions.add(s);
                }
            }

            HasSeen.setHasSeenSubmission(filteredSubmissions);
            SubmissionCache.cacheSubmissions(
                    filteredSubmissions, context, paginator.getMultiReddit().getDisplayName());

            if (!(SettingValues.noImages
                    && ((!NetworkUtil.isConnectedWifi(c) && SettingValues.lowResMobile)
                            || SettingValues.lowResAlways)))
                PhotoLoader.loadPhotos(
                        c,
                        filteredSubmissions,
                        "multi_" + displayName().toLowerCase(Locale.ENGLISH));

            if (SettingValues.storeHistory) LastComments.setCommentsSince(filteredSubmissions);

            return filteredSubmissions;
        }
    }
}
