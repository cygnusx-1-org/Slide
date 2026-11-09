package me.edgan.redditslide.util;

import static me.edgan.redditslide.util.MoreCommentsFixtures.comment;
import static me.edgan.redditslide.util.MoreCommentsFixtures.ids;
import static me.edgan.redditslide.util.MoreCommentsFixtures.listing;
import static me.edgan.redditslide.util.MoreCommentsFixtures.more;
import static me.edgan.redditslide.util.MoreCommentsFixtures.ok;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class MoreCommentsLoaderTest {
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final MoreCommentsSession session = new MoreCommentsSession(false, clock::get);
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());

    /** Answers each id with a childless comment, and records what was asked. */
    private FocusedListingFetcher simple() {
        return (id, limit) -> {
            requests.add(id + (limit == null ? "" : "@" + limit));
            return ok(listing(comment(id)));
        };
    }

    private static List<String> idsOf(MoreCommentsResult result) {
        List<String> out = new ArrayList<>();
        for (JsonNode c : ((MoreCommentsResult.Loaded) result).getComments()) {
            out.add(c.path("id").asText());
        }
        return out;
    }

    private static final FocusedListing BLOCKED = new FocusedListing(403, null);

    @Test
    public void parentListingKeepsRequestedRepliesAndReportsDeferred() {
        List<String> requested = Arrays.asList("a", "b", "c", "d", "e");
        FocusedListingFetcher fetcher =
                (id, limit) -> {
                    requests.add(id + "@" + limit);
                    return ok(
                            listing(
                                    comment(
                                            "parent",
                                            comment("onscreen"),
                                            comment("a", comment("nested")),
                                            comment("b"),
                                            more(7, "d", "e", "unrequested"),
                                            more(2, "c"))));
                };

        MoreCommentsResult result =
                new MoreCommentsLoader(session, fetcher).loadReplies("parent", requested);

        assertEquals(Collections.singletonList("parent@500"), requests);
        assertEquals(Arrays.asList("a", "b"), idsOf(result));
        assertEquals(Arrays.asList("c", "d", "e"), result.getRemainingIds());
        assertEquals(Integer.valueOf(9), ((MoreCommentsResult.Loaded) result).getRemainingCount());
    }

    @Test
    public void omittedIdsAreGoneSoAnEmptyListingRemovesThePlaceholder() {
        FocusedListingFetcher fetcher = (id, limit) -> ok(listing(comment("parent", comment("x"))));

        MoreCommentsResult result =
                new MoreCommentsLoader(session, fetcher).loadReplies("parent", ids("g", 5));

        assertTrue(idsOf(result).isEmpty());
        assertTrue(result.getRemainingIds().isEmpty());
        assertNull(((MoreCommentsResult.Loaded) result).getRemainingCount());
    }

    @Test
    public void secondLoadOfAParentSkipsTheListing() {
        MoreCommentsLoader loader = new MoreCommentsLoader(session, simple());
        loader.loadReplies("parent", ids("a", 5));
        requests.clear();

        loader.loadReplies("parent", ids("b", 5));

        assertEquals(5, requests.size());
        assertFalse(requests.contains("parent@500"));
    }

    @Test
    public void aThreadReloadAllowsTheParentToBeListedAgain() {
        MoreCommentsLoader loader = new MoreCommentsLoader(session, simple());
        loader.loadReplies("parent", ids("a", 5));
        session.onThreadReloaded();
        requests.clear();

        loader.loadReplies("parent", ids("a", 5));

        assertTrue(requests.contains("parent@500"));
    }

    @Test
    public void fewerThanFiveDistinctIdsSkipTheListing() {
        MoreCommentsResult result =
                new MoreCommentsLoader(session, simple())
                        .loadReplies("parent", Arrays.asList("a", "a", "b", "b", "c", "d"));

        assertFalse(requests.contains("parent@500"));
        assertEquals(Arrays.asList("a", "b", "c", "d"), idsOf(result));
    }

    private void assertFallsBackToPerId(JsonNode parentListing) {
        FocusedListingFetcher fetcher =
                (id, limit) -> {
                    requests.add(id);
                    return limit != null ? ok(parentListing) : ok(listing(comment(id)));
                };

        MoreCommentsResult result =
                new MoreCommentsLoader(session, fetcher).loadReplies("parent", ids("a", 5));

        assertEquals(6, requests.size());
        assertEquals(ids("a", 5), idsOf(result));
    }

    @Test
    public void deletedParentFallsBackToPerId() {
        assertFallsBackToPerId(listing());
    }

    @Test
    public void listingThatDefersEverythingFallsBackToPerId() {
        assertFallsBackToPerId(listing(comment("parent", more(5, "a0", "a1", "a2", "a3", "a4"))));
    }

    @Test
    public void perIdFallbackSkipsIdsTheListingShowedAreGone() {
        FocusedListingFetcher fetcher =
                (id, limit) -> {
                    requests.add(id);
                    return limit != null
                            ? ok(listing(comment("parent", more(2, "a3", "a4"))))
                            : ok(listing(comment(id)));
                };

        MoreCommentsResult result =
                new MoreCommentsLoader(session, fetcher).loadReplies("parent", ids("a", 5));

        assertEquals(Arrays.asList("parent", "a3", "a4"), requests);
        assertEquals(Arrays.asList("a3", "a4"), idsOf(result));
    }

    @Test
    public void wrongParentFallsBackToPerId() {
        assertFallsBackToPerId(listing(comment("someoneelse")));
    }

    @Test
    public void unparseableListingFallsBackToPerId() {
        assertFallsBackToPerId(com.fasterxml.jackson.databind.node.TextNode.valueOf("nope"));
    }

    @Test
    public void failedListingMakesNoFurtherRequestsAndIsRetriedAsAListing() {
        MoreCommentsLoader loader =
                new MoreCommentsLoader(
                        session,
                        (id, limit) -> {
                            requests.add(id + "@" + limit);
                            return BLOCKED;
                        });

        MoreCommentsResult first = loader.loadReplies("parent", ids("a", 5));
        assertTrue(first instanceof MoreCommentsResult.Failed);
        assertEquals(ids("a", 5), first.getRemainingIds());
        assertEquals(Collections.singletonList("parent@500"), requests);

        // The retry the placeholder's tap makes has to reach the network, and as a listing.
        loader.loadReplies("parent", ids("a", 5));
        assertEquals(Arrays.asList("parent@500", "parent@500"), requests);
    }

    @Test
    public void thrownFailureAndHtmlBlockPageAreNormalFailures() {
        MoreCommentsResult thrown =
                new MoreCommentsLoader(
                                session,
                                (id, limit) -> {
                                    throw new IOException("offline");
                                })
                        .loadTopLevel(ids("a", 3));
        assertEquals("offline", ((MoreCommentsResult.Failed) thrown).getCause().getMessage());

        MoreCommentsResult blocked =
                new MoreCommentsLoader(session, (id, limit) -> BLOCKED).loadTopLevel(ids("a", 3));
        assertTrue(blocked instanceof MoreCommentsResult.Failed);
    }

    @Test
    public void topLevelLoadReturnsOnePageInOrderWithAtMostFourInFlight() {
        List<String> all = ids("t", 60);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        Random random = new Random(7);
        FocusedListingFetcher fetcher =
                (id, limit) -> {
                    peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                    // Out-of-order completion.
                    Thread.sleep(random.nextInt(8));
                    inFlight.decrementAndGet();
                    requests.add(id);
                    return ok(listing(comment(id)));
                };

        MoreCommentsResult result = new MoreCommentsLoader(session, fetcher).loadTopLevel(all);

        assertEquals(all.subList(0, 25), idsOf(result));
        assertEquals(all.subList(25, 60), result.getRemainingIds());
        assertEquals(25, requests.size());
        assertTrue("peak " + peak.get(), peak.get() <= 4);
        assertTrue("peak " + peak.get(), peak.get() > 1);
    }

    @Test
    public void failureKeepsEarlierResultsAndResumesFromTheFailedId() {
        List<String> all = ids("t", 30);
        FocusedListingFetcher fetcher =
                (id, limit) -> {
                    requests.add(id);
                    return id.equals("t10") ? BLOCKED : ok(listing(comment(id)));
                };

        MoreCommentsResult result = new MoreCommentsLoader(session, fetcher).loadTopLevel(all);

        assertEquals(all.subList(0, 10), idsOf(result));
        assertEquals(all.subList(10, 30), result.getRemainingIds());
    }

    @Test
    public void noRequestStartsAfterTheInFlightOnesHaveFailed() {
        List<String> all = ids("t", 30);
        // t10 and the requests started alongside it wait for each other, so every worker is held
        // in one of them and none can claim another id before its own failure stops it. A count
        // of what was sent after a lone failure can't be pinned: a fetch that returns at once
        // lets the other workers claim ids before the failing one has recorded its failure.
        List<String> inFlight =
                all.subList(10, 10 + MoreCommentsLoaderKt.MORE_COMMENTS_MAX_IN_FLIGHT);
        CyclicBarrier together = new CyclicBarrier(inFlight.size());
        FocusedListingFetcher fetcher =
                (id, limit) -> {
                    requests.add(id);
                    if (!inFlight.contains(id)) {
                        return ok(listing(comment(id)));
                    }
                    // A timeout rather than a hang if fewer workers than this ever run at once.
                    together.await(10, TimeUnit.SECONDS);
                    return BLOCKED;
                };

        MoreCommentsResult result = new MoreCommentsLoader(session, fetcher).loadTopLevel(all);

        assertEquals(all.subList(0, 10), idsOf(result));
        assertEquals(all.subList(10, 30), result.getRemainingIds());
        assertEquals(all.subList(0, 10 + inFlight.size()), sorted(requests));
    }

    /** {@link #requests} in id order, since concurrent requests are recorded in any order. */
    private static List<String> sorted(List<String> sent) {
        List<String> out = new ArrayList<>(sent);
        out.sort(Comparator.comparingInt(id -> Integer.parseInt(id.substring(1))));
        return out;
    }

    @Test
    public void failureOnTheFirstIdLoadsNothing() {
        MoreCommentsResult result =
                new MoreCommentsLoader(
                                session,
                                (id, limit) -> id.equals("t0") ? BLOCKED : ok(listing(comment(id))))
                        .loadTopLevel(ids("t", 5));

        assertTrue(result instanceof MoreCommentsResult.Failed);
        assertEquals(ids("t", 5), result.getRemainingIds());
    }

    @Test
    public void emptyPagesAdvanceToTheNextOne() {
        List<String> all = ids("t", 30);
        FocusedListingFetcher fetcher =
                (id, limit) -> {
                    requests.add(id);
                    return all.indexOf(id) < 25 ? ok(listing()) : ok(listing(comment(id)));
                };

        MoreCommentsResult result = new MoreCommentsLoader(session, fetcher).loadTopLevel(all);

        assertEquals(all.subList(25, 30), idsOf(result));
        assertTrue(result.getRemainingIds().isEmpty());
    }

    @Test
    public void emptyPagesStopAtTheCap() {
        List<String> all = ids("t", 25 * 9);
        FocusedListingFetcher fetcher =
                (id, limit) -> {
                    requests.add(id);
                    return ok(listing());
                };

        MoreCommentsResult result = new MoreCommentsLoader(session, fetcher).loadTopLevel(all);

        assertEquals(25 * 8, requests.size());
        assertTrue(idsOf(result).isEmpty());
        assertEquals(all.subList(25 * 8, 25 * 9), result.getRemainingIds());
    }

    @Test
    public void descendantsAreNotDuplicatedWithinAPageNorFetchedInALaterOne() {
        List<String> all = new ArrayList<>(ids("t", 26));
        // t1 is under t0, in the same page; t25 is under t0 and would be in the next one.
        FocusedListingFetcher fetcher =
                (id, limit) -> {
                    requests.add(id);
                    return id.equals("t0")
                            ? ok(listing(comment("t0", comment("t1"), comment("t25"))))
                            : ok(listing(comment(id)));
                };

        MoreCommentsResult result = new MoreCommentsLoader(session, fetcher).loadTopLevel(all);

        assertFalse(idsOf(result).contains("t1"));
        assertTrue(idsOf(result).contains("t0"));
        assertFalse(result.getRemainingIds().contains("t25"));
        assertTrue(result.getRemainingIds().isEmpty());
    }

    @Test
    public void mismatchedOrMalformedCommentDoesNotBlockTheRest() {
        FocusedListingFetcher fetcher =
                (id, limit) -> {
                    switch (id) {
                        case "t1":
                            return ok(listing(comment("somethingelse")));
                        case "t2":
                            return ok(listing(more(3, "x")));
                        case "t3":
                            return ok(null);
                        default:
                            return ok(listing(comment(id)));
                    }
                };

        MoreCommentsResult result =
                new MoreCommentsLoader(session, fetcher).loadTopLevel(ids("t", 5));

        assertEquals(Arrays.asList("t0", "t4"), idsOf(result));
        assertTrue(result.getRemainingIds().isEmpty());
    }

    @Test
    public void lowBudgetStopsSpendingKeepsResultsAndPausesUntilReset() {
        List<String> all = ids("t", 25);
        AtomicInteger sent = new AtomicInteger();
        FocusedListingFetcher fetcher =
                (id, limit) -> {
                    // Decimal, as Reddit sends it: an integer parse would miss this entirely.
                    String left = sent.incrementAndGet() >= 8 ? "20.0" : "500.0";
                    return new FocusedListing(200, listing(comment(id)), left, "120");
                };
        MoreCommentsLoader loader = new MoreCommentsLoader(session, fetcher);

        MoreCommentsResult result = loader.loadTopLevel(all);

        assertTrue(idsOf(result).size() >= 8);
        assertTrue(idsOf(result).size() < 25);
        assertTrue(session.isPaused());

        int sentBefore = sent.get();
        MoreCommentsResult paused = loader.loadTopLevel(all);
        assertTrue(((MoreCommentsResult.Failed) paused).getCause()
                instanceof RateLimitPausedException);
        assertEquals(sentBefore, sent.get());

        clock.addAndGet(119_999L);
        assertTrue(session.isPaused());
        clock.addAndGet(1L);
        assertFalse(session.isPaused());
        assertFalse(loader.loadTopLevel(all) instanceof MoreCommentsResult.Failed);
    }

    @Test
    public void aPausedLoadSaysHowLongIsLeft() {
        session.onResponse(200, "3.0", "120");
        clock.addAndGet(30_000L);

        MoreCommentsResult result =
                new MoreCommentsLoader(session, simple()).loadTopLevel(ids("t", 3));

        Exception cause = ((MoreCommentsResult.Failed) result).getCause();
        assertEquals(90_000L, ((RateLimitPausedException) cause).getWaitMs());
        assertTrue(requests.isEmpty());
    }

    @Test
    public void aRateLimitedResponseIsReportedAsAPause() {
        FocusedListingFetcher fetcher = (id, limit) -> new FocusedListing(429, null, null, "45");

        MoreCommentsResult result = new MoreCommentsLoader(session, fetcher).loadTopLevel(ids("t", 1));

        Exception cause = ((MoreCommentsResult.Failed) result).getCause();
        assertEquals(45_000L, ((RateLimitPausedException) cause).getWaitMs());
    }

    @Test
    public void pausedParentListingSendsNothingAndStaysUnlisted() {
        session.onResponse(429, null, null);
        MoreCommentsLoader loader = new MoreCommentsLoader(session, simple());

        assertTrue(loader.loadReplies("parent", ids("a", 5)) instanceof MoreCommentsResult.Failed);
        assertTrue(requests.isEmpty());

        clock.addAndGet(10_000L);
        loader.loadReplies("parent", ids("a", 5));
        assertTrue(requests.contains("parent@500"));
    }

    @Test
    public void healthyBudgetDoesNotPause() {
        session.onResponse(200, "21.0", "300");
        assertFalse(session.isPaused());
    }

    @Test
    public void missingHeadersDoNotPauseASuccess() {
        session.onResponse(200, null, null);
        session.onResponse(403, null, null);
        assertFalse(session.isPaused());
    }

    @Test
    public void shortResetStillPausesForTheMinimum() {
        session.onResponse(200, "3.0", "2");
        clock.addAndGet(9_999L);
        assertTrue(session.isPaused());
        clock.addAndGet(1L);
        assertFalse(session.isPaused());
    }

    @Test
    public void rateLimitedResponseWithoutHeadersPausesForTheMinimum() {
        FocusedListingFetcher fetcher = (id, limit) -> new FocusedListing(429, null);

        new MoreCommentsLoader(session, fetcher).loadTopLevel(ids("t", 1));

        assertTrue(session.isPaused());
        clock.addAndGet(10_000L);
        assertFalse(session.isPaused());
    }
}
