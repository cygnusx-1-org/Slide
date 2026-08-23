package me.edgan.redditslide.util;

import androidx.annotation.Nullable;

import java.util.Random;

/**
 * Picks the {@code limit} sent with an interactive comment fetch.
 *
 * <p>Reddit builds and caches a comment tree per {@code (link, sort, limit)}, so {@code limit} does
 * two jobs: it caps how many comments come back, and it selects which cached tree is read. That
 * cache can miss an invalidation - a reply lands, the tree for one particular slot is never rebuilt,
 * and the parent comes back carrying an empty {@code replies} field rather than a "more" node. There
 * is then nothing for the UI to draw as a "load more" row, so the comment is simply missing, and a
 * refresh re-reads the same slot and never helps.
 *
 * <p>Omitting {@code limit} is the same as sending 200, and that slot is shared by every client that
 * omits it, which makes it the most contended one. So a limit is always sent, it is never 200, and
 * it rotates within a band sized to the thread. Sizing the band above the comment count also stops a
 * large thread's newest comments - which sort to the bottom under confidence, exactly where a
 * 200-comment cut falls - from starting out behind a "load more" row.
 *
 * <p>Rotating does not make a stale slot impossible. It makes reading the same stale slot twice
 * unlikely, and makes a refresh a reliable escape from one.
 */
public final class CommentLimit {
    /** Reddit clamps {@code limit} here; 1000, 2500 and 99999 all return the same 499 comments. */
    public static final int MAX = 500;

    /** The value Reddit uses when {@code limit} is omitted. Never send it. */
    private static final int OMITTED = 200;

    /** Bands are this wide, and start on a multiple of this above the thread's comment count. */
    private static final int STEP = 50;

    private static final Random RANDOM = new Random();

    private CommentLimit() {}

    /**
     * A starting point for {@link #forCommentCount}'s rotation, so re-opening a submission does not
     * go straight back to the slot the previous visit read. Always non-negative.
     */
    public static int newRotation() {
        return RANDOM.nextInt(STEP);
    }

    /**
     * The limit to send for a thread of {@code commentCount} comments, picking slot {@code rotation}
     * out of that thread's band. A null count - a permalink opened before any submission has been
     * loaded - falls back to the top band rather than omitting the limit.
     */
    public static int forCommentCount(@Nullable Integer commentCount, int rotation) {
        // Clamped before the band is computed, both because the count arrives from a submission that
        // may be stale and because an unclamped multiply would overflow on a nonsense value.
        final int count =
                commentCount == null ? MAX : Math.max(0, Math.min(MAX, commentCount));

        // The smallest 50-step strictly above the comment count, so every value in the band returns
        // the whole thread and only the cache slot differs.
        final int base = Math.min(MAX, (count / STEP + 1) * STEP);

        if (base >= MAX) {
            // At the ceiling there is no room above, so rotate downward from 500 instead. A thread
            // at or over the clamp is truncated at 499 whatever we ask for, so the whole step is
            // fair game; below the clamp the floor is one above the count, so rotating never pushes
            // a comment that previously fit behind a "load more" row. At exactly 499 comments those
            // two floors meet and the band is the single value 500 - that thread size gets no
            // rotation.
            final int floor = count >= MAX ? MAX - STEP + 1 : count + 1;
            return MAX - Math.floorMod(rotation, MAX - floor + 1);
        }

        // 200 is dropped from its band rather than mapped onto a neighbour. Bumping it after the
        // fact would make two consecutive rotations send the same limit, so one refresh would
        // silently re-read the very slot it was trying to escape.
        final int low = base == OMITTED ? OMITTED + 1 : base;
        final int high = base + STEP - 1;

        return low + Math.floorMod(rotation, high - low + 1);
    }
}
