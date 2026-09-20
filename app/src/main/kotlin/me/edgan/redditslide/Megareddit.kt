package me.edgan.redditslide

import java.util.Locale

/**
 * A named filter over the r/all listing. It is not a multireddit: nothing is fetched per subreddit,
 * so there is no cap on how many subreddits it can match.
 *
 * Four lists decide whether a post from r/all is kept, most specific first, so that a name always
 * beats a tag and, at the same specificity, a negative always beats a positive:
 * 1. [negativeSubreddits] -- an exact subreddit name, always dropped.
 * 2. [positiveSubreddits] -- an exact subreddit name, always kept, even against a negative tag.
 * 3. [negativeTags] -- part of a subreddit name, dropped; "cathol" clears out r/Catholicism.
 * 4. [positiveTags] -- part of a subreddit name, kept; "cat" keeps r/cats and r/CatsThatYell.
 *
 * Anything none of them speaks for is left in r/all. Every tag and name is held lowercased, so
 * matching is case-insensitive.
 *
 * Immutable; [Megareddits] stores these per account.
 */
class Megareddit(
    val name: String,
    positiveTags: Collection<String>,
    negativeTags: Collection<String>,
    positiveSubreddits: Collection<String>,
    negativeSubreddits: Collection<String>,
) {

    val positiveTags: List<String> = normalize(positiveTags)
    val negativeTags: List<String> = normalize(negativeTags)
    val positiveSubreddits: List<String> = normalize(positiveSubreddits)
    val negativeSubreddits: List<String> = normalize(negativeSubreddits)

    /** The tab and feed key for this Megareddit, see [Megareddits.keyFor]. */
    fun key(): String = Megareddits.keyFor(name)

    /** Whether a post from [subreddit] belongs in this Megareddit's feed. */
    fun matches(subreddit: String?): Boolean {
        if (subreddit.isNullOrEmpty()) {
            return false
        }
        val sub = subreddit.lowercase(Locale.ENGLISH)
        if (sub in negativeSubreddits) {
            return false
        }
        if (sub in positiveSubreddits) {
            return true
        }
        if (negativeTags.any { sub.contains(it) }) {
            return false
        }
        return positiveTags.any { sub.contains(it) }
    }

    /**
     * Whether [subreddit] is named on either subreddit list, and so has already been ruled in or
     * out by hand rather than left to the tags.
     */
    fun isListed(subreddit: String): Boolean {
        val sub = subreddit.lowercase(Locale.ENGLISH)
        return sub in positiveSubreddits || sub in negativeSubreddits
    }

    /** A copy with [subreddit] added to the positive list and taken off the negative one. */
    fun withPositive(subreddit: String): Megareddit {
        val sub = subreddit.lowercase(Locale.ENGLISH)
        return Megareddit(
            name,
            positiveTags,
            negativeTags,
            positiveSubreddits + sub,
            negativeSubreddits - sub,
        )
    }

    /** A copy with [subreddit] added to the negative list and taken off the positive one. */
    fun withNegative(subreddit: String): Megareddit {
        val sub = subreddit.lowercase(Locale.ENGLISH)
        return Megareddit(
            name,
            positiveTags,
            negativeTags,
            positiveSubreddits - sub,
            negativeSubreddits + sub,
        )
    }

    private companion object {
        /**
         * Lowercased, trimmed, deduplicated and sorted. The order is the stored order, so a list
         * reads alphabetically wherever it is shown and never in the order it happened to be typed.
         */
        fun normalize(values: Collection<String>): List<String> =
            values
                .map { it.trim().lowercase(Locale.ENGLISH) }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
    }
}
