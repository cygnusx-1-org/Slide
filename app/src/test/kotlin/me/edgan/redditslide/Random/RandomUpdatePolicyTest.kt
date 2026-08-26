package me.edgan.redditslide.Random

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The refresh cadence for the hosted subreddit lists. It is a pure state machine over two stored
 * timestamps and a clock, so it is pinned here rather than inferred from network behaviour.
 *
 * A successful check writes both timestamps, a failed one writes only the attempt. That is the
 * contract the policy reads, and every case below is expressed in those terms.
 */
class RandomUpdatePolicyTest {

    private fun due(lastSuccess: Long, lastAttempt: Long) =
        RandomUpdatePolicy.isDue(lastSuccess, lastAttempt, NOW)

    @Test
    fun neverCheckedIsDue() {
        assertTrue(due(0L, 0L))
    }

    @Test
    fun succeededTwentyThreeHoursAgoIsNotDue() {
        val then = NOW - 23 * HOUR
        assertFalse(due(then, then))
    }

    @Test
    fun succeededTwentyFiveHoursAgoIsDue() {
        val then = NOW - 25 * HOUR
        assertTrue(due(then, then))
    }

    @Test
    fun succeededExactlyTwentyFourHoursAgoIsDue() {
        val then = NOW - 24 * HOUR
        assertTrue(due(then, then))
    }

    @Test
    fun failedThirtyMinutesAgoIsNotDue() {
        assertFalse(due(NOW - 10 * HOUR, NOW - HOUR / 2))
    }

    @Test
    fun failedTwoHoursAgoIsDue() {
        assertTrue(due(NOW - 10 * HOUR, NOW - 2 * HOUR))
    }

    @Test
    fun failedExactlyOneHourAgoIsDue() {
        assertTrue(due(NOW - 10 * HOUR, NOW - HOUR))
    }

    @Test
    fun failingRepeatedlyKeepsTheHourlyCadence() {
        // The last of several failures, 90 minutes back: still hourly, never a 24 hour wait
        // inherited from the older success.
        assertTrue(due(NOW - 40 * HOUR, NOW - HOUR - HOUR / 2))
    }

    @Test
    fun failedThenSucceededReturnsToTheDailyCadence() {
        // The success is newer than the failure, so both timestamps carry the success time.
        val succeeded = NOW - 2 * HOUR
        assertFalse(due(succeeded, succeeded))
    }

    @Test
    fun succeededInTheFutureIsDue() {
        // The clock moved backwards. Waiting out an interval that can no longer elapse would wedge
        // the refresh permanently, so the stored timestamps are discarded.
        val ahead = NOW + 5 * HOUR
        assertTrue(due(ahead, ahead))
    }

    @Test
    fun aFailedAttemptGovernsRatherThanAnOlderSuccess() {
        // Succeeded 25 hours ago, retry failed 2 hours ago: due, on the hourly cadence.
        assertTrue(due(NOW - 25 * HOUR, NOW - 2 * HOUR))
        // Succeeded 25 hours ago, retry failed 10 minutes ago: the failure holds it off, even
        // though the daily interval has long since elapsed.
        assertFalse(due(NOW - 25 * HOUR, NOW - HOUR / 6))
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val HOUR = 60L * 60L * 1000L
    }
}
