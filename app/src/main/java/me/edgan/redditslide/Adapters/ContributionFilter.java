package me.edgan.redditslide.Adapters;

import net.dean.jraw.models.Comment;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.Submission;

import java.util.ArrayList;
import java.util.Locale;

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
     * @param where The tab/section name (e.g., "overview", "comments", "submitted")
     * @return A filtered list containing only matching contributions
     */
    public static ArrayList<Contribution> filterContributions(
            ArrayList<Contribution> contributions, String query, String where) {

        if (contributions == null || contributions.isEmpty()) {
            return new ArrayList<>();
        }

        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(contributions);
        }

        // Normalize query: trim, lowercase, split into words
        String normalizedQuery = query.trim().toLowerCase(Locale.getDefault());
        String[] searchTerms = normalizedQuery.split("\\s+");

        ArrayList<Contribution> filtered = new ArrayList<>();

        for (Contribution contribution : contributions) {
            if (matchesQuery(contribution, searchTerms)) {
                filtered.add(contribution);
            }
        }

        return filtered;
    }

    /**
     * Checks if a contribution matches all search terms (AND logic).
     *
     * @param contribution The contribution to check
     * @param searchTerms Array of search terms (already normalized to lowercase)
     * @return true if all search terms are found in the contribution
     */
    private static boolean matchesQuery(Contribution contribution, String[] searchTerms) {
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
                String searchableText = getSearchableText(contribution);
                if (searchableText != null && searchableText.contains(term)) {
                    termMatched = true;
                }
            } else {
                // Normal search: check all fields
                String searchableText = getSearchableText(contribution);
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
    private static String getSubredditName(Contribution contribution) {
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

            // Note: Not searching selftext/body since it's often not visible in card view
            // and would cause confusing results where the match isn't obvious

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
