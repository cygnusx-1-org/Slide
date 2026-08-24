package me.edgan.redditslide.util;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import net.dean.jraw.paginators.Sorting;
import org.jspecify.annotations.NullMarked;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The per-subreddit sort that Slide remembers when you change "Hot" to "New" on one subreddit.
 *
 * <p>Every caller of {@link SortingUtil#setSorting} hands it the subreddit name as it happened to
 * arrive — from the drawer, from a link, from Reddit's own JSON — so the same subreddit reaches
 * this map as "AskReddit", "askreddit" and "ASKREDDIT" in one session. The saved sort has to
 * survive that: a sort saved under one capitalisation must come back under any other, or the
 * setting silently reverts to the default the next time the subreddit is opened by a different
 * route.
 *
 * <p>"frontpage" is the same question one level up — it is not a real subreddit, so it has to be
 * recognised case-insensitively and answered from {@code frontpageSorting} rather than falling
 * through to {@code defaultSorting}.
 */
@NullMarked
public class SortingUtilTest {

    private Map<String, Sorting> savedSorting;
    private Sorting savedDefault;
    private Sorting savedFrontpage;

    @Before
    public void captureStatics() {
        // SortingUtil.sorting is a process-wide static and Robolectric hands the same JVM to every
        // later test class, so whatever this test puts in it has to come back out in tearDown.
        savedSorting = new HashMap<>(SortingUtil.sorting);
        savedDefault = SortingUtil.defaultSorting;
        savedFrontpage = SortingUtil.frontpageSorting;
    }

    @After
    public void restoreStatics() {
        SortingUtil.sorting.clear();
        SortingUtil.sorting.putAll(savedSorting);
        SortingUtil.defaultSorting = savedDefault;
        SortingUtil.frontpageSorting = savedFrontpage;
    }

    @Test
    public void sortSavedForMixedCaseSubredditIsFoundByLowercaseName() {
        SortingUtil.setSorting("AskReddit", Sorting.NEW);

        assertEquals(
                "a sort saved under the display capitalisation must be found by the lowercase name",
                Sorting.NEW,
                SortingUtil.getSorting("askreddit", Sorting.HOT));
    }

    @Test
    public void sortSavedForLowercaseSubredditIsFoundByMixedCaseName() {
        SortingUtil.setSorting("pics", Sorting.TOP);

        assertEquals(
                "a sort saved by one caller must be found by a caller using another capitalisation",
                Sorting.TOP,
                SortingUtil.getSorting("PiCs", Sorting.HOT));
    }

    @Test
    public void unknownSubredditFallsBackToTheSuppliedDefault() {
        SortingUtil.sorting.remove("neverconfigured");

        assertEquals(
                "a subreddit with no saved sort answers with the default it was given",
                Sorting.CONTROVERSIAL,
                SortingUtil.getSorting("neverconfigured", Sorting.CONTROVERSIAL));
    }

    @Test
    public void frontpageIsAnsweredFromFrontpageSortingWhateverItsCase() {
        SortingUtil.frontpageSorting = Sorting.RISING;
        SortingUtil.defaultSorting = Sorting.CONTROVERSIAL;

        assertEquals(
                "Frontpage must resolve to the frontpage sort, not the subreddit default",
                SortingUtil.getSortingId(Sorting.RISING),
                SortingUtil.getSortingId("Frontpage"));
    }

    @Test
    public void savedSubredditSortWinsOverTheGlobalDefault() {
        SortingUtil.defaultSorting = Sorting.HOT;
        SortingUtil.setSorting("AskReddit", Sorting.CONTROVERSIAL);

        assertEquals(
                "the sort saved for a subreddit outranks defaultSorting",
                SortingUtil.getSortingId(Sorting.CONTROVERSIAL),
                SortingUtil.getSortingId("askreddit"));
    }
}
