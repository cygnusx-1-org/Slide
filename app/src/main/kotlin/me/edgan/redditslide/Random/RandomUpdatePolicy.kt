package me.edgan.redditslide.Random

/**
 * When a cached subreddit list is due for a conditional re-check.
 *
 * Deliberately pure: no `Context`, no clock of its own, no I/O. Everything it needs is passed in, so
 * the cadence can be tested directly rather than inferred from network behaviour.
 *
 * The two timestamps are written by [RandomSubredditList]: a successful check (whether the server
 * answered `304` or `200`) sets both, a failed one sets only the attempt. That makes
 * `lastAttemptUtc <= lastSuccessUtc` the test for "the last check succeeded".
 */
object RandomUpdatePolicy {

    /** Cadence after a check that succeeded. */
    const val SUCCESS_INTERVAL_MS: Long = 24L * 60L * 60L * 1000L

    /** Cadence after a check that failed — retried hourly until one succeeds. */
    const val FAILURE_RETRY_MS: Long = 60L * 60L * 1000L

    /**
     * @param lastSuccessUtc epoch millis of the last check that completed, or 0 if there has never
     *   been one
     * @param lastAttemptUtc epoch millis of the last check attempted including failures, or 0 if
     *   there has never been one
     * @param nowUtc the current epoch millis
     * @param hasDownloadedCopy whether a downloaded list is actually present. Only the success
     *   cadence is skipped when it is not — see below.
     */
    fun isDue(
        lastSuccessUtc: Long,
        lastAttemptUtc: Long,
        nowUtc: Long,
        hasDownloadedCopy: Boolean = true,
    ): Boolean {
        // Never checked.
        if (lastAttemptUtc <= 0L) return true

        // The clock moved backwards (timezone change, NTP correction, a restored backup). Waiting
        // out an interval that can no longer elapse would wedge the refresh, so treat the stored
        // timestamps as unusable and check now.
        if (nowUtc < lastAttemptUtc) return true

        if (lastAttemptUtc <= lastSuccessUtc) {
            // The last check succeeded. If that success left no file behind, it is describing
            // something the system has since evicted, and there is nothing to wait for.
            if (!hasDownloadedCopy) return true
            return nowUtc - lastSuccessUtc >= SUCCESS_INTERVAL_MS
        }

        // The last check failed, so the hourly backoff applies whether or not a copy exists.
        // Missing copy plus failing server is precisely when a caller picks repeatedly and would
        // otherwise re-request several megabytes on every one of those picks.
        return nowUtc - lastAttemptUtc >= FAILURE_RETRY_MS
    }
}
