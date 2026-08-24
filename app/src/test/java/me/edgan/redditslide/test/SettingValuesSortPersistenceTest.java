package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import me.edgan.redditslide.SettingValues;
import me.edgan.redditslide.util.SortingUtil;
import net.dean.jraw.models.CommentSort;
import net.dean.jraw.paginators.Sorting;
import net.dean.jraw.paginators.TimePeriod;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * "Remember this sort for this subreddit", written and read back.
 *
 * <p>Each of these settings is stored under a key built from a string literal and the subreddit
 * name, and that literal is written out again -- separately, by hand -- in every method that reads
 * it. {@code "defaultComment"} appears three times across {@code setDefaultCommentSorting},
 * {@code getCommentSorting} and {@code hasCommentSort}; {@code "defaultSort"} and
 * {@code "defaultTime"} twice each. Nothing composed a write with its read, so the two halves
 * could stop agreeing and every test would still pass: renaming the key in
 * {@code setDefaultCommentSorting} alone -- which makes the setting silently never persist --
 * left the whole suite green.
 *
 * <p>The subreddit name is lowercased on both sides too, so these write under the capitalisation
 * a drawer or a link would supply and read back under another.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, application = Application.class)
public class SettingValuesSortPersistenceTest {

    private static final String SUB_AS_DISPLAYED = "AskReddit";
    private static final String SUB_AS_TYPED = "askreddit";

    private SharedPreferences prefsWas;
    private CommentSort defaultCommentSortingWas;
    private Sorting defaultSortingWas;
    private TimePeriod timePeriodWas;

    @Before
    public void setUp() {
        prefsWas = SettingValues.prefs;
        defaultCommentSortingWas = SettingValues.defaultCommentSorting;
        defaultSortingWas = SortingUtil.defaultSorting;
        timePeriodWas = SortingUtil.timePeriod;

        final Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs =
                context.getSharedPreferences("sort-persistence-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        SettingValues.prefs = prefs;

        SettingValues.defaultCommentSorting = CommentSort.CONFIDENCE;
        SortingUtil.defaultSorting = Sorting.HOT;
        SortingUtil.timePeriod = TimePeriod.DAY;
        // getSubmissionSort and getSubmissionTimePeriod consult these in-memory maps before the
        // preference, so an entry left by another test would answer instead of the stored value.
        SortingUtil.sorting.remove(SUB_AS_TYPED);
        SortingUtil.times.remove(SUB_AS_TYPED);
    }

    @After
    public void tearDown() {
        SettingValues.prefs = prefsWas;
        SettingValues.defaultCommentSorting = defaultCommentSortingWas;
        SortingUtil.defaultSorting = defaultSortingWas;
        SortingUtil.timePeriod = timePeriodWas;
        SortingUtil.sorting.remove(SUB_AS_TYPED);
        SortingUtil.times.remove(SUB_AS_TYPED);
    }

    @Test
    public void aCommentSortSurvivesTheTripThroughPreferences() {
        SettingValues.setDefaultCommentSorting(CommentSort.TOP, SUB_AS_DISPLAYED);

        assertEquals(
                "the sort written for a subreddit has to be the sort read back for it",
                CommentSort.TOP,
                SettingValues.getCommentSorting(SUB_AS_TYPED));
    }

    /** {@code hasCommentSort} probes the same key by hand, so it has to agree with the writer. */
    @Test
    public void hasCommentSortSeesTheSortThatWasJustWritten() {
        assertFalse(
                "nothing written yet", SettingValues.hasCommentSort(SUB_AS_DISPLAYED));

        SettingValues.setDefaultCommentSorting(CommentSort.OLD, SUB_AS_DISPLAYED);

        assertTrue(
                "the writer and the probe have to be looking at the same key",
                SettingValues.hasCommentSort(SUB_AS_TYPED));
    }

    @Test
    public void aSubredditWithNoStoredCommentSortFallsBackToTheDefault() {
        assertEquals(
                CommentSort.CONFIDENCE, SettingValues.getCommentSorting("neverconfigured"));
        assertFalse(SettingValues.hasCommentSort("neverconfigured"));
    }

    @Test
    public void aSubmissionSortSurvivesTheTripThroughPreferences() {
        SettingValues.setSubSorting(Sorting.CONTROVERSIAL, TimePeriod.YEAR, SUB_AS_DISPLAYED);

        assertEquals(
                "the submission sort written has to be the one read back",
                Sorting.CONTROVERSIAL,
                SettingValues.getSubmissionSort(SUB_AS_TYPED));
    }

    /** The same call writes the time period under a second key of its own. */
    @Test
    public void aSubmissionTimePeriodSurvivesTheTripThroughPreferences() {
        SettingValues.setSubSorting(Sorting.TOP, TimePeriod.YEAR, SUB_AS_DISPLAYED);

        assertEquals(
                "the time period written has to be the one read back",
                TimePeriod.YEAR,
                SettingValues.getSubmissionTimePeriod(SUB_AS_TYPED));
    }

    @Test
    public void aSubredditWithNoStoredSubmissionSortFallsBackToTheDefaults() {
        assertEquals(Sorting.HOT, SettingValues.getSubmissionSort("neverconfigured"));
        assertEquals(TimePeriod.DAY, SettingValues.getSubmissionTimePeriod("neverconfigured"));
    }
}
