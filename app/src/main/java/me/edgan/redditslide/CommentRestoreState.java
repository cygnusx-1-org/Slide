package me.edgan.redditslide;

import android.os.Bundle;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import me.edgan.redditslide.Fragments.CommentPage;
import me.edgan.redditslide.util.ScrollAnchor;
import org.jspecify.annotations.NullMarked;

/**
 * Where the user was inside a post's comments, so reopening the app puts them back there.
 *
 * <p>Unlike a feed, nothing caches a comment tree on an ordinary read, so the comments themselves
 * are refetched and only the <em>place</em> is restored. That is why the anchor is a comment
 * fullname rather than a row index — the tree that comes back has new replies in it, at different
 * indexes — with the recorded index kept only as a fallback for a comment that has since been
 * deleted.
 *
 * <p>Shared by {@code CommentsScreen} (the swipe-between-posts pager) and
 * {@code CommentsScreenSingle}.
 */
@NullMarked
public final class CommentRestoreState {

    /** Fullname of the post whose comments this describes; null when there is nothing pending. */
    @Nullable public String submission;

    @Nullable public String anchorId;
    public int anchorPosition = ScrollAnchor.NO_POSITION;
    public int anchorOffset;
    @Nullable public ArrayList<String> collapsed;
    @Nullable public ArrayList<String> collapsedRoots;
    public boolean toolbarHidden;

    /**
     * Records the state of the comment page currently on screen.
     *
     * @return whether anything was written. False means the page could not describe itself yet,
     *     and the caller must leave {@code out} alone.
     */
    public static boolean capture(
            Bundle out, @Nullable String submissionFullname, @Nullable CommentPage page) {
        if (submissionFullname == null || submissionFullname.isEmpty()) {
            return false;
        }
        if (page == null || page.adapter == null) {
            return false;
        }
        final ScrollAnchor anchor = ScrollAnchor.capture(page.rv);
        // All of it or none of it. An invalid anchor means the thread has not laid a row out yet,
        // which is what a capture taken while the screen is still building sees -- and the post's
        // name on its own is a weaker answer than the one already recorded, which it would
        // replace, leaving a resume that opens the right thread at the wrong place.
        if (!anchor.isValid()) {
            return false;
        }
        out.putString(HibernateState.STATE_SUBMISSION, submissionFullname);
        // The auto-hiding toolbar is part of the position: bringing it back visible pushes every
        // comment down by its height, which is exactly "close, but not where I left it".
        out.putBoolean(HibernateState.STATE_COMMENT_TOOLBAR_HIDDEN, page.isToolbarHidden());
        out.putStringArrayList(HibernateState.STATE_HIDDEN, page.adapter.collapsedNames());
        out.putStringArrayList(
                HibernateState.STATE_HIDDEN_PERSONS,
                new ArrayList<>(page.adapter.hiddenPersons));
        out.putInt(HibernateState.STATE_COMMENT_ANCHOR_POSITION, anchor.position);
        out.putInt(HibernateState.STATE_COMMENT_ANCHOR_OFFSET, anchor.offset);
        final String name = page.adapter.fullnameAt(anchor.position);
        if (name != null) {
            out.putString(HibernateState.STATE_COMMENT_ANCHOR_ID, name);
        }
        return true;
    }

    /** Reads a bundle written by {@link #capture}. */
    public void read(Bundle in) {
        final String name = in.getString(HibernateState.STATE_SUBMISSION);
        if (name == null || name.isEmpty()) {
            return;
        }
        submission = name;
        anchorId = in.getString(HibernateState.STATE_COMMENT_ANCHOR_ID);
        anchorPosition = in.getInt(HibernateState.STATE_COMMENT_ANCHOR_POSITION, ScrollAnchor.NO_POSITION);
        anchorOffset = in.getInt(HibernateState.STATE_COMMENT_ANCHOR_OFFSET, 0);
        collapsed = in.getStringArrayList(HibernateState.STATE_HIDDEN);
        collapsedRoots = in.getStringArrayList(HibernateState.STATE_HIDDEN_PERSONS);
        toolbarHidden = in.getBoolean(HibernateState.STATE_COMMENT_TOOLBAR_HIDDEN, false);
    }

    /**
     * Hands the restore to the page for {@code submissionFullname}, and to no other. One-shot: the
     * pager builds a page per post either side of the current one, and only the one the user was
     * actually reading should come back scrolled.
     */
    public boolean applyTo(@Nullable String submissionFullname, Bundle args) {
        if (submission == null || submissionFullname == null) {
            return false;
        }
        if (!submission.equalsIgnoreCase(submissionFullname)) {
            return false;
        }
        args.putString(CommentPage.ARG_RESTORE_ANCHOR_ID, anchorId);
        args.putInt(CommentPage.ARG_RESTORE_ANCHOR_POSITION, anchorPosition);
        args.putInt(CommentPage.ARG_RESTORE_ANCHOR_OFFSET, anchorOffset);
        args.putStringArrayList(CommentPage.ARG_RESTORE_COLLAPSED, collapsed);
        args.putStringArrayList(CommentPage.ARG_RESTORE_COLLAPSED_ROOTS, collapsedRoots);
        args.putBoolean(CommentPage.ARG_RESTORE_TOOLBAR_HIDDEN, toolbarHidden);
        submission = null;
        return true;
    }
}
