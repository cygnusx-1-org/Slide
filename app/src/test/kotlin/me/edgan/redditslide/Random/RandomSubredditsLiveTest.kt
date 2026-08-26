package me.edgan.redditslide.Random

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What survives a batch check against `/api/info`.
 *
 * The hosted list is an archive, so a name in it is only a candidate — a sixth of the archived set
 * was already banned when it was last measured. These pin the two ways a candidate fails: a null
 * subscriber count, and a name that does not come back in the response at all.
 */
class RandomSubredditsLiveTest {

    private fun listing(vararg children: String): JsonNode =
        ObjectMapper()
            .readTree(
                """{"kind":"Listing","data":{"children":[${children.joinToString(",")}]}}"""
            )

    private fun subreddit(name: String, subscribers: String) =
        """{"kind":"t5","data":{"display_name":"$name","subscribers":$subscribers}}"""

    @Test
    fun keepsSubredditsWithASubscriberCount() {
        val live =
            RandomSubreddits.live(
                listing(subreddit("pics", "31000000"), subreddit("gifs", "12"))
            )
        assertEquals(setOf("pics", "gifs"), live.toSet())
    }

    @Test
    fun dropsSubredditsWithANullSubscriberCount() {
        // Banned, private and deleted subreddits all come back this way.
        val live =
            RandomSubreddits.live(listing(subreddit("alive", "500"), subreddit("banned", "null")))
        assertEquals(listOf("alive"), live)
    }

    @Test
    fun namesAbsentFromTheResponseAreDroppedLikeANullCount() {
        // Three names were requested; only one came back. The other two are gone entirely, which
        // has to be treated exactly like a null subscriber count rather than assumed usable.
        val requested = setOf("stillhere", "vanished", "alsovanished")
        val live = RandomSubreddits.live(listing(subreddit("stillhere", "9000")))

        assertEquals(listOf("stillhere"), live)
        assertTrue(requested.containsAll(live))
    }

    @Test
    fun anEmptyBatchResultYieldsNothing() {
        assertTrue(RandomSubreddits.live(listing()).isEmpty())
    }

    @Test
    fun aMissingChildrenArrayYieldsNothing() {
        assertTrue(RandomSubreddits.live(ObjectMapper().readTree("""{"error":404}""")).isEmpty())
    }

    @Test
    fun aNullResponseYieldsNothing() {
        assertTrue(RandomSubreddits.live(null).isEmpty())
    }

    @Test
    fun dropsEntriesWithNoDisplayName() {
        val listing =
            listing("""{"kind":"t5","data":{"subscribers":42}}""", subreddit("real", "7"))
        assertEquals(listOf("real"), RandomSubreddits.live(listing))
    }
}
