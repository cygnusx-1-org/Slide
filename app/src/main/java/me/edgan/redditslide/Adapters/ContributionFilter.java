package me.edgan.redditslide.Adapters;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;

/**
 * Utility class for filtering user contributions (posts and comments) based on search queries.
 * Created for Profile view search functionality.
 */
public class ContributionFilter {

    /**
     * Filters a list of contributions based on a search query.
     *
     * @param contributions The list of contributions to filter
     * @param query The search query string
     * @param where The tab/section name; every tab matches the same way, so it is not read
     * @return A filtered list containing only matching contributions
     */
    public static ArrayList<Contribution> filterContributions(
            ArrayList<Contribution> contributions, String query, @Nullable String where) {

        if (contributions == null || contributions.isEmpty()) {
            return new ArrayList<>();
        }

        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(contributions);
        }

        String[] searchTerms = parseTerms(query);
        if (searchTerms.length == 0) {
            return new ArrayList<>(contributions);
        }

        ArrayList<Contribution> filtered = new ArrayList<>();

        for (Contribution contribution : contributions) {
            if (matchesQuery(contribution, searchTerms)) {
                filtered.add(contribution);
            }
        }

        return filtered;
    }

    /** Matches a "quoted phrase" or a single bare word. */
    private static final Pattern TERM = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    /**
     * Splits a query into the terms that must all be present.
     *
     * <p>Bare words are separate terms, so {@code too good} still matches a post carrying both
     * words anywhere. A run wrapped in double quotes is one term instead, so {@code "too good"}
     * matches the phrase and nothing else -- there was no way to ask for that before.
     *
     * @param query The raw search query
     * @return The lowercased terms, in query order
     */
    public static String[] parseTerms(String query) {
        final List<String> terms = new ArrayList<>();
        final Matcher matcher = TERM.matcher(query.trim().toLowerCase(Locale.getDefault()));
        while (matcher.find()) {
            final String quoted = matcher.group(1);
            final String bare = matcher.group(2);
            // An unbalanced quote falls through to the bare branch and would otherwise keep the
            // quote character as part of the word, which matches nothing.
            final String term =
                    quoted != null ? quoted.trim() : (bare == null ? null : bare.replace("\"", ""));
            if (term != null && !term.isEmpty()) {
                terms.add(term);
            }
        }
        return terms.toArray(new String[0]);
    }

    /**
     * Checks if a contribution matches all search terms (AND logic).
     *
     * @param contribution The contribution to check
     * @param searchTerms Array of search terms (already normalized to lowercase)
     * @return true if all search terms are found in the contribution
     */
    private static boolean matchesQuery(Contribution contribution, String[] searchTerms) {
        // Built once per contribution, not once per term: it concatenates and lowercases the
        // title, the body and the rest, so rebuilding it for every word of the query re-walked
        // the whole post text as many times as the query had words.
        final String searchableText = getSearchableText(contribution);

        // AND logic: all terms must be present
        for (String term : searchTerms) {
            boolean termMatched = false;

            // Check if this is a subreddit-specific search (starts with /r/)
            if (term.startsWith("/r/") && term.length() > 3) {
                String subredditName = term.substring(3); // Remove "/r/" prefix

                // Match against subreddit field
                String actualSubreddit = getSubredditName(contribution);
                if (actualSubreddit != null && actualSubreddit.toLowerCase(Locale.getDefault()).equals(subredditName)) {
                    termMatched = true;
                }

                // Also check if the literal "/r/..." appears in titles or comment bodies
                if (searchableText != null && searchableText.contains(term)) {
                    termMatched = true;
                }
            } else {
                // Normal search: check all fields
                if (searchableText != null && searchableText.contains(term)) {
                    termMatched = true;
                }
            }

            if (!termMatched) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the subreddit name from a contribution.
     *
     * @param contribution The contribution
     * @return The subreddit name, or null if not available
     */
    private static @Nullable String getSubredditName(Contribution contribution) {
        if (contribution instanceof Submission) {
            return ((Submission) contribution).getSubredditName();
        } else if (contribution instanceof Comment) {
            return ((Comment) contribution).getSubredditName();
        }
        return null;
    }

    /**
     * Extracts all searchable text from a contribution.
     *
     * @param contribution The contribution (Submission or Comment)
     * @return Lowercase string containing all searchable fields
     */
    private static String getSearchableText(Contribution contribution) {
        StringBuilder text = new StringBuilder();

        if (contribution instanceof Submission) {
            Submission submission = (Submission) contribution;

            // Search in title
            if (submission.getTitle() != null) {
                text.append(submission.getTitle()).append(" ");
            }

            // Search in the post's own text. It is not always visible in card view, but a term
            // the user typed is far more often in the body than nowhere at all, and leaving it
            // out made a self post unfindable by anything but its title.
            if (submission.getSelftext() != null) {
                text.append(submission.getSelftext()).append(" ");
            }

            // Search in subreddit name
            if (submission.getSubredditName() != null) {
                text.append(submission.getSubredditName()).append(" ");
            }

            // Search in author (useful for finding posts by specific users)
            if (submission.getAuthor() != null) {
                text.append(submission.getAuthor()).append(" ");
            }

        } else if (contribution instanceof Comment) {
            Comment comment = (Comment) contribution;

            // Search in comment body
            if (comment.getBody() != null) {
                text.append(comment.getBody()).append(" ");
            }

            // Search in submission title (the post the comment is on)
            if (comment.getSubmissionTitle() != null) {
                text.append(comment.getSubmissionTitle()).append(" ");
            }

            // Search in subreddit (useful for finding comments in specific subs)
            if (comment.getSubredditName() != null) {
                text.append(comment.getSubredditName()).append(" ");
            }

            // Search in author
            if (comment.getAuthor() != null) {
                text.append(comment.getAuthor()).append(" ");
            }
        }

        return text.toString().toLowerCase(Locale.getDefault());
    }
}
