package me.edgan.redditslide.Adapters;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import me.edgan.redditslide.Authentication;
import me.edgan.redditslide.ContributionCache;
import me.edgan.redditslide.HasSeen;
import me.edgan.redditslide.PostMatch;
import me.edgan.redditslide.SavedPostCache;
import me.edgan.redditslide.SavedTagStore;
import me.edgan.redditslide.SavedTags;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.LogUtil;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.Submission;
import net.dean.jraw.paginators.Paginator;

/** Created by ccrama on 9/17/2015. */
public class ContributionPostsSaved extends ContributionPosts {

    /**
     * The user tags being filtered by; empty when only built-ins narrow the list.
     *
     * <p>Reddit's saved-categories feature is gone, so this is no longer something the server can
     * narrow a listing by; the whole saved list is fetched and filtered here instead. See {@link
     * SavedTags}.
     */
    private final Set<String> tags;

    /**
     * The built-in tags being filtered by. Empty, or holding {@link SavedTags.Builtin#ALL}, means
     * they narrow nothing.
     */
    private final Set<SavedTags.Builtin> builtins;

    /**
     * The fullnames carrying any of {@link #tags}, read once per load rather than per row.
     *
     * <p>Volatile because pruning can replace it: that only happens on the loader thread today, but
     * the read side is a filter running over every row and a stale reference there would silently
     * hide items.
     */
    private volatile Set<String> tagged = new HashSet<>();

    /**
     * Every fullname the current walk has seen, or {@code null} when this walk cannot see the whole
     * saved listing -- one resumed mid-listing after a hibernate restore, or one that never ran
     * because the list came out of a cache.
     *
     * <p>Filled from the raw page, ahead of both the tag filter and the user's content filters:
     * pruning deletes tag membership outright, and a post a title filter hides is still saved.
     * Pruning against the rows that reached the screen would drop its tags for good.
     */
    private @Nullable Set<String> walked;

    /** Set true before a reset load to skip the hard-TTL cache and force a fresh network fetch. */
    public boolean bypassCache;

    /** Marks that the last load was served from cache, so we don't re-stamp its TTL. */
    private boolean servedFromCache;

    public ContributionPostsSaved(
            String subreddit,
            String where,
            Collection<String> tags,
            Collection<SavedTags.Builtin> builtins) {
        super(subreddit, where);
        this.tags = new LinkedHashSet<>(tags);
        this.builtins =
                builtins.isEmpty()
                        ? EnumSet.noneOf(SavedTags.Builtin.class)
                        : EnumSet.copyOf(builtins);
    }

    @SuppressWarnings("NullAway.Init") // assigned in onPostExecute
    ResumableUserSavedPaginator paginator;

    /**
     * The Saved tab renders one filter at a time, so the filter is part of what identifies the
     * list. This feeds the hibernate-restore key: without the filter in it, resuming into the tab
     * would restore rows from whatever filter was last active.
     */
    @Override
    public String cacheKey() {
        return ContributionCache.key(subreddit, where, filterKey());
    }

    /**
     * A stable string for the whole selection, so two different filters never share a restore key.
     * Both halves are sorted, because the selection is a set and the order the user ticked things
     * in is not part of what it means.
     */
    private String filterKey() {
        if (isUnfiltered()) {
            return SavedTags.Builtin.ALL.name();
        }
        final StringBuilder key = new StringBuilder();
        for (SavedTags.Builtin builtin : builtins) { // EnumSet iterates in declaration order
            key.append(builtin.name()).append(',');
        }
        // Length-prefixed, because a tag name is free text and may contain the separator: the
        // selections {"a", "b"} and {"a,b"} would otherwise spell the same key and restore each
        // other's rows.
        for (String tag : SavedTags.sort(tags)) {
            key.append('|').append(tag.length()).append(':').append(tag);
        }
        return key.toString();
    }

    /** This class paginates through its own field, which shadows the one in the superclass. */
    @Override
    @Nullable
    protected Listing<Contribution> currentListing() {
        return paginator == null ? null : paginator.getCurrentListing();
    }

    @Override
    public void loadMore(ContributionAdapter adapter, String subreddit, boolean reset) {
        // See ContributionPosts.LOAD_EXECUTOR.
        new LoadData(reset).executeOnExecutor(LOAD_EXECUTOR, subreddit);
    }

    /**
     * See {@link GeneralPosts#isNarrowed()}: a tag filter drops rows before the adapter ever
     * sees them, so an empty result here is an answer rather than a list still loading.
     */
    @Override
    public boolean isNarrowed() {
        return !isUnfiltered();
    }

    /** Whether no filter is active, so the rows loaded are the whole saved list. */
    private boolean isUnfiltered() {
        return tags.isEmpty()
                && (builtins.isEmpty() || builtins.contains(SavedTags.Builtin.ALL));
    }

    /**
     * Whether {@code c} passes the active filter.
     *
     * <p>Custom tags are a union with the built-ins rather than a narrowing of them: a tag is
     * something the user put on an item by hand, so it says "show me this" outright. The built-ins
     * compose among themselves -- see {@link SavedTags#matchesAny}.
     */
    private boolean matchesFilter(Contribution c) {
        if (isUnfiltered()) {
            return true;
        }
        if (!tags.isEmpty()) {
            final String fullname = c.getFullName();
            if (fullname != null && tagged.contains(fullname)) {
                return true;
            }
        }
        return SavedTags.matchesAny(builtins, c);
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
            // Only the unfiltered view may write the cache: with a tag active, `posts` holds
            // just the matching rows, and storing that as the complete saved list would serve a
            // filtered list to every other filter.
            if (submissions != null
                    && submissions.isEmpty()
                    && !servedFromCache
                    && posts != null
                    && isUnfiltered()) {
                // Cache the whole accumulated list (submissions AND saved comments), in order.
                SavedPostCache.store(Authentication.nameOrEmpty(), null, posts, true);
            }
            super.onPostExecute(submissions);
        }

        @Override
        protected @Nullable ArrayList<Contribution> doInBackground(
                String... subredditPaginators) {
            servedFromCache = false;
            boolean bypass = bypassCache;
            // Read the tag's membership once per load. Doing it per row would re-parse the store's
            // map for every item in the listing.
            tagged = tags.isEmpty() ? new HashSet<>() : SavedTagStore.fullnamesInAny(tags);
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
                if (!bypass && SavedPostCache.isFresh(Authentication.nameOrEmpty(), null)) {
                    SavedPostCache.Cached cached =
                            SavedPostCache.load(Authentication.nameOrEmpty(), null);
                    if (cached != null && cached.complete) {
                        servedFromCache = true;
                        nomore = true; // the cache holds the whole saved list
                        // The cached blob is the unfiltered list, so the filter is applied here
                        // rather than being baked in -- which is what lets a tag change take
                        // effect without a refetch.
                        final ArrayList<Contribution> filtered = new ArrayList<>();
                        for (Contribution c : cached.posts) {
                            if (matchesFilter(c)) {
                                filtered.add(c);
                            }
                        }
                        // Refresh seen state the same way the network path does.
                        HasSeen.setHasSeenContrib(filtered);
                        return filtered;
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
                    // No setCategory: Reddit no longer narrows the saved listing by label, so the
                    // whole list is fetched and matchesFilter does the narrowing.
                    // See ContributionPosts: without this JRAW sends no limit and Reddit pages 25
                    // at a time, which a whole-history search pays for four times over.
                    paginator.setLimit(Paginator.RECOMMENDED_MAX_LIMIT);
                    // Picks up where the hibernated session left off; see ContributionPosts.
                    paginator.setResumeAfter(restoreAfterToken);
                    // A walk resuming mid-listing never sees the pages behind its cursor, so it can
                    // never be the complete picture pruning needs.
                    walked = restoreAfterToken == null ? new HashSet<>() : null;
                }

                if (!paginator.hasNext()) {
                    nomore = true;
                    // Nothing left to page: the walk's fullnames are the whole saved listing.
                    pruneToWalk();
                    return new ArrayList<>();
                }
                final Listing<Contribution> page = paginator.next();
                // See ContributionPosts: the paginator's cursor supersedes the restore token.
                restoreAfterToken = null;
                int emptyPages = 0;
                Listing<Contribution> current = page;
                while (true) {
                    for (Contribution c : current) {
                        final Set<String> seen = walked;
                        if (seen != null) {
                            final String fullname = c.getFullName();
                            if (fullname != null) {
                                seen.add(fullname);
                            }
                        }
                        if (!matchesFilter(c)) {
                            continue;
                        }
                        if (c instanceof Submission) {
                            Submission s = (Submission) c;
                            if (!PostMatch.doesMatch(s)) {
                                newData.add(s);
                            }
                        } else {
                            newData.add(c);
                        }
                    }
                    // See ContributionPosts: a page the user's filters emptied is not the end of
                    // the listing, but onPostExecute cannot tell the two apart. A tag filter empties
                    // pages the same way a content filter does, which is why it runs inside here.
                    if (!newData.isEmpty()
                            || current.isEmpty()
                            || !paginator.hasNext()
                            || ++emptyPages >= MAX_EMPTY_PAGES) {
                        break;
                    }
                    current = paginator.next();
                }

                if (!paginator.hasNext()) {
                    pruneToWalk();
                }

                // Reddit can return the same item twice inside one saved-listing response. Drop
                // repeats here, before the seen state and the cached blob are built from them.
                newData = SavedTags.dedupe(newData);

                HasSeen.setHasSeenContrib(newData);

                // See ContributionPosts: nothing a deep search pages past is ever shown.
                if (!isDeepSearching()) {
                    warmPreviews(newData);
                }

                return newData;
            } catch (Exception e) {
                LogUtil.e(e, "Could not load the saved listing");
                return null;
            }
        }
    }

    /**
     * Forget tag membership for anything the walk never found -- unsaved on another client, or
     * dropped by Reddit.
     *
     * <p>Only called once a walk that began at the first page has reached the last one. A partial
     * walk proves nothing: pruning against one would delete the tags of everything it had not
     * reached yet, and unlike the listing itself those cannot be fetched back.
     */
    private void pruneToWalk() {
        final Set<String> live = walked;
        // An empty walk is not evidence that nothing is saved; a listing that returned nothing
        // looks exactly the same, and pruning to it would empty every tag.
        if (live == null || live.isEmpty()) {
            return;
        }
        if (SavedTagStore.pruneTo(live) && !tags.isEmpty()) {
            tagged = SavedTagStore.fullnamesInAny(tags);
        }
    }
}
