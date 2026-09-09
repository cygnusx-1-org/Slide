package me.edgan.redditslide.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.edgan.redditslide.Adapters.ContributionFilter;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;
import org.junit.Test;

/**
 * {@link ContributionFilter}: what the profile search actually matches.
 *
 * <p>Two things it used to get wrong are pinned here. A submission's own text was excluded from the
 * searchable fields, so a term that appeared only in the body made the post unfindable; and every
 * query was split on whitespace and AND-ed, so there was no way to ask for a phrase — {@code too
 * good} matched any post carrying both words anywhere.
 */
public class ContributionFilterTest {

    private static Submission post(String title, String selftext, String subreddit)
            throws Exception {
        final ObjectNode data;
        try (InputStream input =
                ContributionFilterTest.class
                        .getClassLoader()
                        .getResourceAsStream("submissions/galleryPost.json")) {
            assertNotNull(input);
            data = (ObjectNode) new ObjectMapper().readTree(input);
        }
        data.put("name", "t3_" + Math.abs(title.hashCode()));
        data.put("is_gallery", false);
        data.put("title", title);
        data.put("selftext", selftext);
        data.put("subreddit", subreddit);
        return new Submission(data);
    }

    private static ArrayList<Contribution> listing(Submission... posts) {
        return new ArrayList<Contribution>(Arrays.asList(posts));
    }

    private static List<String> titlesOf(List<Contribution> filtered) {
        final List<String> titles = new ArrayList<>();
        for (Contribution c : filtered) {
            titles.add(((Submission) c).getTitle());
        }
        return titles;
    }

    // ---------------------------------------------------------------------
    // parseTerms
    // ---------------------------------------------------------------------

    @Test
    public void bareWordsAreSeparateTerms() {
        assertEquals(
                Arrays.asList("too", "good"), Arrays.asList(ContributionFilter.parseTerms("too good")));
    }

    @Test
    public void aQuotedRunIsOneTerm() {
        assertEquals(
                Arrays.asList("too good"),
                Arrays.asList(ContributionFilter.parseTerms("\"too good\"")));
    }

    @Test
    public void quotedAndBareTermsMix() {
        assertEquals(
                Arrays.asList("an offer", "sound"),
                Arrays.asList(ContributionFilter.parseTerms("\"an offer\" sound")));
    }

    @Test
    public void termsAreLowercased() {
        assertEquals(Arrays.asList("offer"), Arrays.asList(ContributionFilter.parseTerms("OFFER")));
    }

    /** An unbalanced quote must not leave the quote character glued to the word. */
    @Test
    public void anUnbalancedQuoteIsStripped() {
        assertEquals(
                Arrays.asList("too", "good"),
                Arrays.asList(ContributionFilter.parseTerms("\"too good")));
    }

    // ---------------------------------------------------------------------
    // filterContributions
    // ---------------------------------------------------------------------

    @Test
    public void aTermInTheBodyMatches() throws Exception {
        final ArrayList<Contribution> all =
                listing(
                        post("Nothing to see", "an offer too good to be true", "pics"),
                        post("Unrelated", "no such words here", "pics"));

        assertEquals(
                Arrays.asList("Nothing to see"),
                titlesOf(ContributionFilter.filterContributions(all, "offer", null)));
    }

    @Test
    public void aTermInTheTitleStillMatches() throws Exception {
        final ArrayList<Contribution> all =
                listing(post("An offer too good", "", "pics"), post("Unrelated", "", "pics"));

        assertEquals(
                Arrays.asList("An offer too good"),
                titlesOf(ContributionFilter.filterContributions(all, "offer", null)));
    }

    /** Bare words are AND-ed and may be anywhere, which is the pre-existing behaviour. */
    @Test
    public void bareWordsMatchAnywhereAndInAnyOrder() throws Exception {
        final ArrayList<Contribution> all =
                listing(post("Good news, and too soon", "", "pics"));

        assertEquals(1, ContributionFilter.filterContributions(all, "too good", null).size());
    }

    /** The quoted form is the one that means the phrase. */
    @Test
    public void aQuotedPhraseMatchesOnlyTheRun() throws Exception {
        final ArrayList<Contribution> all =
                listing(
                        post("Good news, and too soon", "", "pics"),
                        post("An offer too good to be true", "", "pics"));

        assertEquals(
                Arrays.asList("An offer too good to be true"),
                titlesOf(ContributionFilter.filterContributions(all, "\"too good\"", null)));
    }

    @Test
    public void allBareTermsMustBePresent() throws Exception {
        final ArrayList<Contribution> all = listing(post("An offer", "", "pics"));

        assertTrue(ContributionFilter.filterContributions(all, "offer missing", null).isEmpty());
    }

    @Test
    public void aSubredditTermMatchesTheSubreddit() throws Exception {
        final ArrayList<Contribution> all =
                listing(post("One", "", "pics"), post("Two", "", "videos"));

        assertEquals(
                Arrays.asList("Two"),
                titlesOf(ContributionFilter.filterContributions(all, "/r/videos", null)));
    }

    @Test
    public void anEmptyQueryKeepsEverything() throws Exception {
        final ArrayList<Contribution> all =
                listing(post("One", "", "pics"), post("Two", "", "videos"));

        assertEquals(2, ContributionFilter.filterContributions(all, "   ", null).size());
    }
}
