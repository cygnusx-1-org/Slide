package me.edgan.redditslide.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import me.edgan.redditslide.util.MediaRowLoadState;
import org.junit.Before;
import org.junit.Test;

/**
 * Pins the load bookkeeping for a reusable media row. Each rule here stands in for a bug the vertical
 * album/gallery rows actually had: loading twice because an attach followed a bind, a late failure
 * clearing state for media the row had moved on from, and a failure retrying forever.
 */
public class MediaRowLoadStateTest {

    private static final String A = "https://example.org/a.mp4";
    private static final String B = "https://example.org/b.mp4";

    private MediaRowLoadState state;

    @Before
    public void setUp() {
        state = new MediaRowLoadState();
    }

    @Test
    public void aFreshRowNeedsLoadingAndIsEmpty() {
        assertTrue(state.shouldLoad(A));
        assertTrue(state.isEmpty());
    }

    @Test
    public void inFlightLoadIsNotStartedAgain() {
        state.loadStarted(A);

        // The attach that follows a bind must not start a second load, even though the first has not
        // finished and the player therefore still holds nothing.
        assertFalse(state.shouldLoad(A));
        assertFalse(state.isEmpty());
    }

    @Test
    public void rebindForTheSameMediaDoesNotReload() {
        state.loadStarted(A);

        // A rebind for an unrelated reason — the list's width changed — must not re-download.
        assertFalse(state.shouldLoad(A));
    }

    @Test
    public void rebindForDifferentMediaReloads() {
        state.loadStarted(A);

        assertTrue(state.shouldLoad(B));
    }

    @Test
    public void attachAfterReleaseLoadsAgain() {
        state.loadStarted(A);
        state.released();

        assertTrue(state.isEmpty());
        assertTrue(state.shouldLoad(A));
    }

    @Test
    public void failureAllowsExactlyOneRetry() {
        state.loadStarted(A);
        state.loadFailed(A);
        assertTrue("the first failure should let the row try again", state.shouldLoad(A));

        state.loadStarted(A);
        state.loadFailed(A);
        assertFalse("a second failure must not retry forever", state.shouldLoad(A));
    }

    @Test
    public void repeatedFailureCallbacksForOneLoadAreIdempotent() {
        state.loadStarted(A);

        // GifUtils can report one failure more than once, so onError implementations must be safe to
        // call repeatedly; two calls must not spend the retry budget twice.
        state.loadFailed(A);
        state.loadFailed(A);

        assertTrue(state.shouldLoad(A));
    }

    @Test
    public void failureForMediaTheRowHasMovedOnFromIsIgnored() {
        state.loadStarted(A);
        state.loadStarted(B);

        // A row outlives any one item, so a load that fails late can belong to media this row no
        // longer shows. It must not clear the state for what is showing now.
        state.loadFailed(A);

        assertFalse(state.shouldLoad(B));
        assertFalse(state.isEmpty());
    }

    @Test
    public void releaseLeavesNoUrlLoaded() {
        state.loadStarted(A);
        state.released();

        // Emptied, not merely switched: no url should look loaded afterwards.
        assertTrue(state.shouldLoad(A));
        assertTrue(state.shouldLoad(B));
    }

    @Test
    public void failureArrivingAfterAReleaseIsIgnored() {
        state.loadStarted(A);

        // A detach can land between a load starting and its failure callback. The release already
        // emptied the row, so the callback has nothing left to clear.
        state.released();
        state.loadFailed(A);

        assertTrue(state.isEmpty());
        assertTrue(state.shouldLoad(A));
    }

    @Test
    public void differentMediaGetsAFreshRetryBudget() {
        state.loadStarted(A);
        state.loadFailed(A);
        state.loadStarted(A);
        state.loadFailed(A);
        assertFalse("A's budget is spent", state.shouldLoad(A));

        // Reusing the row for other media forgets A's budget, so returning to A later retries again.
        state.loadStarted(B);
        state.loadStarted(A);
        state.loadFailed(A);

        assertTrue(state.shouldLoad(A));
    }
}
