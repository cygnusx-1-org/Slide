package me.edgan.redditslide.Random

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.atomic.AtomicBoolean
import me.edgan.redditslide.util.InfoMode
import me.edgan.redditslide.util.LookupResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checking random candidates without `/api/info`, which Reddit blocks for some client ids: one
 * about page at a time, by the batch's own rules, stopping at the first that passes.
 */
class RandomSubredditsAboutTest {

    private fun json(raw: String): JsonNode = ObjectMapper().readTree(raw)

    private fun about(name: String, subscribers: String = "500") =
        LookupResponse(
            200,
            json("""{"kind":"t5","data":{"display_name":"$name","subscribers":$subscribers}}"""),
        )

    private val banned =
        LookupResponse(404, json("""{"reason":"banned","message":"Not Found","error":404}"""))
    private val quarantined = LookupResponse(403, json("""{"reason":"quarantined"}"""))

    private val asked = ArrayList<String>()
    private val blocked = AtomicBoolean()
    private var infoCalls = 0

    private fun survivors(
        mode: InfoMode,
        info: LookupResponse,
        names: List<String>,
        answers: Map<String, LookupResponse>,
    ): List<String> =
        RandomSubreddits.survivors(
            names,
            mode,
            blocked,
            {
                infoCalls++
                info
            },
            { name ->
                asked.add(name)
                answers[name] ?: about(name)
            },
        )

    @Test
    fun aLiveAboutPagePasses() {
        assertEquals(listOf("pics"), RandomSubreddits.live(RandomSubreddits.aboutListing(about("pics").json)))
    }

    @Test
    fun anAboutPageFailsByTheBatchsRules() {
        assertTrue(RandomSubreddits.live(RandomSubreddits.aboutListing(about("x", "null").json)).isEmpty())
        assertTrue(RandomSubreddits.live(RandomSubreddits.aboutListing(banned.json)).isEmpty())
        assertTrue(RandomSubreddits.live(RandomSubreddits.aboutListing(json("""["junk"]"""))).isEmpty())
        assertTrue(RandomSubreddits.live(RandomSubreddits.aboutListing(null)).isEmpty())
    }

    @Test
    fun skipsBannedPrivateAndQuarantinedAndStopsAtTheFirstThatPasses() {
        val found =
            survivors(
                InfoMode.NEVER,
                LookupResponse(200, null),
                listOf("gone", "quiet", "dead", "live", "later"),
                mapOf("gone" to banned, "quiet" to quarantined, "dead" to about("dead", "null")),
            )

        assertEquals(listOf("live"), found)
        assertEquals(listOf("gone", "quiet", "dead", "live"), asked)
        assertEquals(0, infoCalls)
    }

    @Test
    fun aRateLimitOrServerErrorStops() {
        for (code in listOf(429, 500, 0)) {
            asked.clear()
            val found =
                survivors(
                    InfoMode.NEVER,
                    LookupResponse(200, null),
                    listOf("a", "b"),
                    mapOf("a" to LookupResponse(code, null)),
                )
            assertTrue(found.isEmpty())
            assertEquals(listOf("a"), asked)
        }
    }

    @Test
    fun checksAtMostTen() {
        val names = (0 until 30).map { "dead$it" }
        val found =
            survivors(
                InfoMode.NEVER,
                LookupResponse(200, null),
                names,
                names.associateWith { banned },
            )

        assertTrue(found.isEmpty())
        assertEquals(10, asked.size)
    }

    @Test
    fun firstFallsBackOnA403AndRemembersIt() {
        val found = survivors(InfoMode.FIRST, LookupResponse(403, null), listOf("a"), emptyMap())

        assertEquals(listOf("a"), found)
        assertTrue(blocked.get())

        survivors(InfoMode.FIRST, LookupResponse(403, null), listOf("b"), emptyMap())
        assertEquals(1, infoCalls)
    }

    @Test
    fun firstReturnsNothingOnAnyOtherFailure() {
        val found = survivors(InfoMode.FIRST, LookupResponse(503, null), listOf("a"), emptyMap())

        assertTrue(found.isEmpty())
        assertTrue(asked.isEmpty())
        assertFalse(blocked.get())
    }

    @Test
    fun onlyNeverFallsBack() {
        val found = survivors(InfoMode.ONLY, LookupResponse(403, null), listOf("a"), emptyMap())

        assertTrue(found.isEmpty())
        assertTrue(asked.isEmpty())
        assertFalse(blocked.get())
    }

    @Test
    fun aWorkingBatchIsUsedAsBefore() {
        val batch =
            LookupResponse(
                200,
                json(
                    """{"kind":"Listing","data":{"children":[""" +
                        """{"kind":"t5","data":{"display_name":"a","subscribers":5}}]}}"""
                ),
            )

        assertEquals(listOf("a"), survivors(InfoMode.FIRST, batch, listOf("a", "b"), emptyMap()))
        assertTrue(asked.isEmpty())
    }
}
