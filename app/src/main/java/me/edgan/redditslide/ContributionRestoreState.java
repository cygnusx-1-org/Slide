package me.edgan.redditslide;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.ScrollAnchor;

import net.dean.jraw.models.Contribution;

import org.jspecify.annotations.NullMarked;

/**
 * Where the user was inside the "You" screen: which tab, which row of it, how far down that row,
 * how far the tab had been paged, and whether the header was hidden.
 *
 * <p>{@link FeedRestoreState}'s counterpart for {@code Profile}. A profile tab is a list like a feed
 * is, but it is not a feed — its rows are {@link Contribution}s, so submissions and comments
 * interleaved, and its listing is per-account rather than per-subreddit. The cache is
 * {@link ContributionCache} and the restore is keyed by tab index rather than by listing name.
 */
@NullMarked
public final class ContributionRestoreState {

    /** Fragment-argument keys, read by every list fragment {@code Profile} hosts. */
    public static final String ARG_RESTORE_FROM_CACHE = "restoreFromCache";

    public static final String ARG_RESTORE_CACHE_KEY = "restoreCacheKey";
    public static final String ARG_RESTORE_ANCHOR_ID = "restoreAnchorId";
    public static final String ARG_RESTORE_ANCHOR_POSITION = "restoreAnchorPosition";
    public static final String ARG_RESTORE_ANCHOR_OFFSET = "restoreAnchorOffset";
    public static final String ARG_RESTORE_EXPECTED_COUNT = "restoreExpectedCount";
    public static final String ARG_RESTORE_TOOLBAR_HIDDEN = "restoreToolbarHidden";

    /**
     * What a profile tab has to be able to say about itself to be restorable. Implemented by the
     * three fragments {@code Profile} hosts — {@code ContributionsView}, {@code HistoryView} and
     * {@code LocalSavedView} — which have three different loaders behind one adapter.
     */
    public interface Source {

        /** The list, or null before {@code onCreateView} has built it. */
        @Nullable
        RecyclerView getRecyclerView();

        /** The rows currently displayed, or null before the first load has landed. */
        @Nullable
        List<Contribution> getRestorePosts();

        /** This tab's {@link ContributionCache} key, or null when the tab is not cacheable. */
        @Nullable
        String getRestoreCacheKey();

        /**
         * Whether the tab is in a state worth recording. False while it is mid-operation and its
         * cache is deliberately standing still, so that the snapshot stands still with it.
         */
        default boolean isRecordable() {
            return true;
        }
    }

    /** The tab this restore is for; -1 once it has been handed over, or when there is none. */
    public int page = -1;

    @Nullable public String cacheKey;
    @Nullable public String anchorId;
    public int anchorPosition = ScrollAnchor.NO_POSITION;
    public int anchorOffset;
    public int expectedCount;
    public boolean toolbarHidden;

    /**
     * Records the state of the tab currently on screen.
     *
     * @param header the profile header, which the toolbar handler hides on a scroll. Whether it was
     *     hidden is part of the position: bringing it back visible pushes every row down by its
     *     height.
     * @return whether anything was written. False means the tab could not describe itself yet, and
     *     the caller must leave {@code out} alone — a bundle naming only the tab would replace one
     *     that also held the position, which is the half that is hard to get back.
     */
    public static boolean capture(
            Bundle out, int page, @Nullable Source source, @Nullable View header) {
        if (source == null || !source.isRecordable()) {
            return false;
        }
        final ScrollAnchor anchor = ScrollAnchor.capture(source.getRecyclerView());
        if (!anchor.isValid()) {
            return false;
        }
        out.putInt(HibernateState.STATE_PAGE, page);
        if (header != null) {
            out.putBoolean(HibernateState.STATE_TOOLBAR_HIDDEN, header.getTranslationY() != 0f);
        }
        out.putInt(HibernateState.STATE_ANCHOR_POSITION, anchor.position);
        out.putInt(HibernateState.STATE_ANCHOR_OFFSET, anchor.offset);
        out.putString(HibernateState.STATE_CONTRIB_KEY, source.getRestoreCacheKey());
        final List<Contribution> posts = source.getRestorePosts();
        if (posts == null) {
            return true;
        }
        out.putInt(HibernateState.STATE_EXPECTED_COUNT, posts.size());
        // Adapter position 0 is the spacer header, so the row at adapter position n is posts[n-1].
        // The fullname is what pins the restore; the index is only a fallback for a row that has
        // since gone.
        final int index = anchor.position - 1;
        if (index >= 0 && index < posts.size()) {
            final Contribution at = posts.get(index);
            if (at != null) {
                out.putString(HibernateState.STATE_ANCHOR_ID, MiscUtil.orEmpty(at.getFullName()));
            }
        }
        return true;
    }

    /** Reads a bundle written by {@link #capture}. */
    public void read(Bundle in) {
        final int recorded = in.getInt(HibernateState.STATE_PAGE, -1);
        if (recorded < 0) {
            return;
        }
        page = recorded;
        cacheKey = in.getString(HibernateState.STATE_CONTRIB_KEY);
        anchorId = in.getString(HibernateState.STATE_ANCHOR_ID);
        anchorPosition = in.getInt(HibernateState.STATE_ANCHOR_POSITION, ScrollAnchor.NO_POSITION);
        anchorOffset = in.getInt(HibernateState.STATE_ANCHOR_OFFSET, 0);
        expectedCount = in.getInt(HibernateState.STATE_EXPECTED_COUNT, 0);
        toolbarHidden = in.getBoolean(HibernateState.STATE_TOOLBAR_HIDDEN, false);
    }

    /**
     * Hands the restore to the one tab it was recorded for, as fragment arguments, and to no other.
     * One-shot: the pager builds the neighbouring tab as well
     * ({@code pager.setOffscreenPageLimit(1)}), and only the tab the user was actually looking at
     * should come back from cache and scrolled.
     */
    public boolean applyTo(int forPage, Bundle args) {
        if (page < 0 || forPage != page) {
            return false;
        }
        args.putBoolean(ARG_RESTORE_FROM_CACHE, true);
        args.putString(ARG_RESTORE_CACHE_KEY, cacheKey);
        args.putString(ARG_RESTORE_ANCHOR_ID, anchorId);
        args.putInt(ARG_RESTORE_ANCHOR_POSITION, anchorPosition);
        args.putInt(ARG_RESTORE_ANCHOR_OFFSET, anchorOffset);
        args.putInt(ARG_RESTORE_EXPECTED_COUNT, expectedCount);
        args.putBoolean(ARG_RESTORE_TOOLBAR_HIDDEN, toolbarHidden);
        page = -1;
        return true;
    }
}
