package me.edgan.redditslide;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.List;
import me.edgan.redditslide.Fragments.SubmissionsView;
import me.edgan.redditslide.util.MiscUtil;
import me.edgan.redditslide.util.ScrollAnchor;
import net.dean.jraw.models.Submission;
import org.jspecify.annotations.NullMarked;

/**
 * Everything needed to put a feed back exactly as the user left it: which listing, which row, how
 * far down that row, how far the listing had been paged, and whether the toolbar was hidden.
 *
 * <p>Shared by the two screens that host a feed, {@code MainActivity} and {@code SubredditView},
 * and by the two ways a restore reaches them — a theme restart, which carries it on the relaunch
 * intent, and a hibernate resume, which carries it in the {@link HibernateState} snapshot. Both end
 * up as fragment arguments on the one {@code SubmissionsView} the restore was recorded for.
 */
@NullMarked
public final class FeedRestoreState {

    public static final String EXTRA_SUB = "restoreSub";
    public static final String EXTRA_ANCHOR_ID = "restoreAnchorId";
    public static final String EXTRA_ANCHOR_POSITION = "restoreAnchorPosition";
    public static final String EXTRA_ANCHOR_OFFSET = "restoreAnchorOffset";
    public static final String EXTRA_EXPECTED_COUNT = "restoreExpectedCount";
    public static final String EXTRA_AFTER_TOKEN = "restoreAfterToken";
    public static final String EXTRA_TOOLBAR_HIDDEN = "restoreToolbarHidden";

    /** The listing this restore is for; null once it has been handed over, or when there is none. */
    @Nullable public String subreddit;

    @Nullable public String anchorId;
    @Nullable public String afterToken;
    public int anchorPosition = ScrollAnchor.NO_POSITION;
    public int anchorOffset;
    public int expectedCount;
    public boolean toolbarHidden;

    /** The page of a tabbed host the listing was on; meaningless for a single-feed host. */
    public int page;

    public boolean isPending() {
        return subreddit != null;
    }

    /**
     * Records the state of the feed currently on screen.
     *
     * @param header the auto-hiding app bar. Whether it was hidden is part of what the screen
     *     looked like: coming back with it showing covers the top of the very post the scroll
     *     position was restored for.
     * @return whether anything was written. False means the feed could not describe itself yet,
     *     and the caller must leave {@code out} alone: a bundle that says less than the one
     *     already recorded replaces it, and the position is what gets lost.
     */
    public static boolean capture(
            Bundle out,
            String subreddit,
            int page,
            @Nullable SubmissionsView view,
            @Nullable View header) {
        if (view == null) {
            return false;
        }
        final ScrollAnchor anchor = ScrollAnchor.capture(view.rv);
        // Nothing at all rather than the listing name on its own. An invalid anchor means the list
        // has not laid a row out yet, which is what a capture during startup sees -- and the name
        // without the position is a weaker answer than the one already recorded, which it would
        // replace. A feed genuinely scrolled to the top still anchors, at position 0.
        if (!anchor.isValid()) {
            return false;
        }
        out.putString(HibernateState.STATE_SUBREDDIT, subreddit);
        out.putInt(HibernateState.STATE_PAGE, page);
        if (header != null) {
            out.putBoolean(HibernateState.STATE_TOOLBAR_HIDDEN, header.getTranslationY() != 0f);
        }
        out.putInt(HibernateState.STATE_ANCHOR_POSITION, anchor.position);
        out.putInt(HibernateState.STATE_ANCHOR_OFFSET, anchor.offset);
        out.putString(HibernateState.STATE_AFTER_TOKEN, view.posts.getAfterToken());
        final List<Submission> posts = view.posts.posts;
        if (posts == null) {
            return true;
        }
        out.putInt(HibernateState.STATE_EXPECTED_COUNT, posts.size());
        // Adapter position 0 is the spacer header, so the post at adapter position n is posts[n-1].
        // The fullname is what actually pins the restore: filtering on the way back in, and any
        // cache blob the system reclaimed, both shift the indexes, and an index alone would land
        // somewhere else entirely.
        final int index = anchor.position - 1;
        if (index >= 0 && index < posts.size()) {
            out.putString(
                    HibernateState.STATE_ANCHOR_ID,
                    MiscUtil.orEmpty(posts.get(index).getFullName()));
        }
        return true;
    }

    /** Reads a bundle written by {@link #capture}. */
    public void read(Bundle in) {
        final String sub = in.getString(HibernateState.STATE_SUBREDDIT);
        if (sub == null || sub.isEmpty()) {
            return;
        }
        subreddit = sub;
        anchorId = in.getString(HibernateState.STATE_ANCHOR_ID);
        afterToken = in.getString(HibernateState.STATE_AFTER_TOKEN);
        anchorPosition = in.getInt(HibernateState.STATE_ANCHOR_POSITION, ScrollAnchor.NO_POSITION);
        anchorOffset = in.getInt(HibernateState.STATE_ANCHOR_OFFSET, 0);
        expectedCount = in.getInt(HibernateState.STATE_EXPECTED_COUNT, 0);
        toolbarHidden = in.getBoolean(HibernateState.STATE_TOOLBAR_HIDDEN, false);
        page = in.getInt(HibernateState.STATE_PAGE, 0);
    }

    /**
     * Puts the state on a relaunch intent, for the in-process restart paths (a theme change, a
     * night-mode toggle) that finish an activity and immediately start it again.
     */
    public static void writeToIntent(Intent intent, Bundle state) {
        final String sub = state.getString(HibernateState.STATE_SUBREDDIT);
        if (sub == null) {
            return;
        }
        intent.putExtra(EXTRA_SUB, sub);
        intent.putExtra(EXTRA_ANCHOR_ID, state.getString(HibernateState.STATE_ANCHOR_ID, ""));
        intent.putExtra(EXTRA_AFTER_TOKEN, state.getString(HibernateState.STATE_AFTER_TOKEN, ""));
        intent.putExtra(
                EXTRA_ANCHOR_POSITION,
                state.getInt(HibernateState.STATE_ANCHOR_POSITION, ScrollAnchor.NO_POSITION));
        intent.putExtra(EXTRA_ANCHOR_OFFSET, state.getInt(HibernateState.STATE_ANCHOR_OFFSET, 0));
        intent.putExtra(
                EXTRA_EXPECTED_COUNT, state.getInt(HibernateState.STATE_EXPECTED_COUNT, 0));
        intent.putExtra(
                EXTRA_TOOLBAR_HIDDEN,
                state.getBoolean(HibernateState.STATE_TOOLBAR_HIDDEN, false));
    }

    /**
     * Reads the state back off a relaunch intent and takes it off again. It describes one
     * relaunch; left in place, the task record would replay a scroll position from an unrelated
     * session days later — the system persists an activity's intent, and the restart paths write
     * these onto the activity's own.
     */
    public void readFromIntent(@Nullable Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_SUB)) {
            return;
        }
        subreddit = intent.getStringExtra(EXTRA_SUB);
        anchorId = intent.getStringExtra(EXTRA_ANCHOR_ID);
        afterToken = intent.getStringExtra(EXTRA_AFTER_TOKEN);
        anchorPosition = intent.getIntExtra(EXTRA_ANCHOR_POSITION, ScrollAnchor.NO_POSITION);
        anchorOffset = intent.getIntExtra(EXTRA_ANCHOR_OFFSET, 0);
        expectedCount = intent.getIntExtra(EXTRA_EXPECTED_COUNT, 0);
        toolbarHidden = intent.getBooleanExtra(EXTRA_TOOLBAR_HIDDEN, false);
        intent.removeExtra(EXTRA_SUB);
        intent.removeExtra(EXTRA_ANCHOR_ID);
        intent.removeExtra(EXTRA_AFTER_TOKEN);
        intent.removeExtra(EXTRA_ANCHOR_POSITION);
        intent.removeExtra(EXTRA_ANCHOR_OFFSET);
        intent.removeExtra(EXTRA_EXPECTED_COUNT);
        intent.removeExtra(EXTRA_TOOLBAR_HIDDEN);
    }

    /**
     * Hands the restore to the one page it was recorded for, as fragment arguments, and to no
     * other. One-shot: cleared here so a later adapter rebuild — {@code reloadSubs()}, a
     * subscription change — fetches normally instead of putting the cached listing back on screen.
     *
     * @param name the page's listing as the adapter resolved it, which for a multireddit is the
     *     full {@code api/user/<name>/m/<multi>} path rather than the {@code /m/<multi>} recorded.
     * @param alias an equivalent name for the same listing, or null.
     */
    public boolean applyTo(String name, @Nullable String alias, Bundle args) {
        if (subreddit == null) {
            return false;
        }
        if (!name.equalsIgnoreCase(subreddit) && !name.equalsIgnoreCase(alias)) {
            return false;
        }
        args.putBoolean(SubmissionsView.ARG_RESTORE_FROM_CACHE, true);
        args.putString(SubmissionsView.ARG_RESTORE_ANCHOR_ID, anchorId);
        args.putString(SubmissionsView.ARG_RESTORE_AFTER_TOKEN, afterToken);
        args.putInt(SubmissionsView.ARG_RESTORE_ANCHOR_POSITION, anchorPosition);
        args.putInt(SubmissionsView.ARG_RESTORE_ANCHOR_OFFSET, anchorOffset);
        args.putInt(SubmissionsView.ARG_RESTORE_EXPECTED_COUNT, expectedCount);
        args.putBoolean(SubmissionsView.ARG_RESTORE_TOOLBAR_HIDDEN, toolbarHidden);
        subreddit = null;
        return true;
    }
}
