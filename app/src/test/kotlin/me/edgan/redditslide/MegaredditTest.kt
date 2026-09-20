package me.edgan.redditslide

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a Megareddit keeps out of r/all. This is the whole feature in one pure function, so every
 * rule it has is pinned here rather than inferred from a feed.
 */
class MegaredditTest {

    private fun cuteAnimals(
        positiveTags: List<String> = listOf("cat", "dog"),
        negativeTags: List<String> = emptyList(),
        positive: List<String> = listOf("aww"),
        negative: List<String> = listOf("catalog", "hotdog"),
    ) = Megareddit("CuteAnimals", positiveTags, negativeTags, positive, negative)

    @Test
    fun aTagMatchesAnywhereInTheSubredditName() {
        val mega = cuteAnimals()
        assertTrue(mega.matches("cats"))
        assertTrue(mega.matches("dogs"))
        // The case the tag exists for: a subreddit nobody would think to list by hand.
        assertTrue(mega.matches("CatsThatYell"))
        assertTrue(mega.matches("IllegallySmolCats"))
    }

    @Test
    fun aTagIsCaseInsensitiveInBothDirections() {
        assertTrue(
            Megareddit("M", listOf("CAT"), emptyList(), emptyList(), emptyList())
                .matches("blackcats")
        )
        assertTrue(
            Megareddit("M", listOf("cat"), emptyList(), emptyList(), emptyList())
                .matches("BlackCats")
        )
    }

    @Test
    fun aPositiveSubredditMatchesOnlyItself() {
        val mega = cuteAnimals()
        // r/aww carries no tag, which is what the positive list is for.
        assertTrue(mega.matches("aww"))
        assertTrue(mega.matches("AWW"))
        assertFalse(mega.matches("awwnverts"))
    }

    @Test
    fun anythingElseIsLeftInRAll() {
        val mega = cuteAnimals()
        assertFalse(mega.matches("AskReddit"))
        assertFalse(mega.matches("pics"))
    }

    @Test
    fun aNegativeSubredditBeatsATag() {
        // The two r/all throws up for "cat" and "dog" that nobody wants in CuteAnimals.
        val mega = cuteAnimals()
        assertFalse(mega.matches("catalog"))
        assertFalse(mega.matches("hotdog"))
    }

    @Test
    fun aNegativeSubredditBeatsAPositiveOne() {
        val mega = Megareddit("M", emptyList(), emptyList(), listOf("aww"), listOf("aww"))
        assertFalse(mega.matches("aww"))
    }

    @Test
    fun aNegativeSubredditIsAWholeNameNotAPieceOfOne() {
        // "catalog" must not take r/cats down with it.
        val mega = cuteAnimals()
        assertTrue(mega.matches("cats"))
        assertTrue(mega.matches("hotdogs"))
    }

    @Test
    fun aNegativeTagBeatsAPositiveOne() {
        // What the breakdown on the Subreddits screen is for: "cat" drags in r/Catholicism and
        // r/DragonsDogma2, and one negative tag clears out a whole family of them.
        val mega = cuteAnimals(negativeTags = listOf("cathol", "dogma"))
        assertFalse(mega.matches("Catholicism"))
        assertFalse(mega.matches("CatholicMemes"))
        assertFalse(mega.matches("DragonsDogma2"))
        assertTrue(mega.matches("cats"))
        assertTrue(mega.matches("dogs"))
    }

    @Test
    fun aNamedSubredditBeatsATagEitherWay() {
        // A name is the more specific instruction, so it wins against a tag on either side.
        val kept =
            cuteAnimals(
                negativeTags = listOf("dog"),
                positive = listOf("hotdog"),
                negative = emptyList(),
            )
        assertTrue(kept.matches("hotdog"))
        assertFalse(kept.matches("dogs"))

        val dropped = cuteAnimals(negative = listOf("cats"))
        assertFalse(dropped.matches("cats"))
    }

    @Test
    fun nothingToMatchMatchesNothing() {
        val mega = Megareddit("M", emptyList(), emptyList(), emptyList(), listOf("catalog"))
        assertFalse(mega.matches("cats"))
        assertFalse(mega.matches("aww"))
    }

    @Test
    fun aMissingSubredditNameMatchesNothing() {
        val mega = cuteAnimals()
        assertFalse(mega.matches(null))
        assertFalse(mega.matches(""))
    }

    @Test
    fun valuesAreStoredLowercasedTrimmedAndDeduplicated() {
        val mega =
            Megareddit(
                "M",
                listOf(" Cat ", "cat", "", "DOG"),
                listOf(" CATHOL "),
                listOf("AWW"),
                listOf(" Hotdog"),
            )
        assertTrue(mega.positiveTags == listOf("cat", "dog"))
        assertTrue(mega.negativeTags == listOf("cathol"))
        assertTrue(mega.positiveSubreddits == listOf("aww"))
        assertTrue(mega.negativeSubreddits == listOf("hotdog"))
    }

    @Test
    fun everyListReadsAlphabetically() {
        val mega =
            Megareddit(
                "M",
                listOf("dog", "cat", "bird"),
                listOf("zebra", "aardvark"),
                listOf("pics", "aww"),
                listOf("hotdog", "catalog"),
            )
        assertTrue(mega.positiveTags == listOf("bird", "cat", "dog"))
        assertTrue(mega.negativeTags == listOf("aardvark", "zebra"))
        assertTrue(mega.positiveSubreddits == listOf("aww", "pics"))
        assertTrue(mega.negativeSubreddits == listOf("catalog", "hotdog"))

        // And a list stays sorted when something is added to it.
        assertTrue(mega.withPositive("bees").positiveSubreddits == listOf("aww", "bees", "pics"))
    }

    @Test
    fun addingASubredditToOneListTakesItOffTheOther() {
        val negative = cuteAnimals().withNegative("Cats")
        assertFalse(negative.matches("cats"))
        assertTrue(negative.negativeSubreddits.contains("cats"))

        val positive = negative.withPositive("CATS")
        assertTrue(positive.matches("cats"))
        assertFalse(positive.negativeSubreddits.contains("cats"))
        assertTrue(positive.positiveSubreddits.contains("cats"))
    }

    @Test
    fun theKeyIsTheLowercasedNameUnderTheMegaPrefix() {
        assertTrue(cuteAnimals().key() == "/mega/cuteanimals")
        assertTrue(Megareddits.isKey(cuteAnimals().key()))
        assertFalse(Megareddits.isKey("cats"))
        assertFalse(Megareddits.isKey("/m/tech"))
        assertFalse(Megareddits.isKey(null))
    }
}
