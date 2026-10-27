package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.local.entity.RssFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class FeedBackoffTest {
    private val now = Instant.parse("2026-10-27T12:00:00Z")

    private fun feed(failedSyncs: Int, lastAttemptAgo: kotlin.time.Duration) = RssFeed(
        title = "Feed",
        url = "https://example.com/rss",
        category = "News",
        lastSyncAttempt = (now - lastAttemptAgo).toString(),
        lastSyncError = if (failedSyncs > 0) "timeout" else null,
        failedSyncs = failedSyncs
    )

    @Test
    fun `each failure in a row doubles the wait`() {
        assertEquals(5.minutes, retryDelay(0))
        assertEquals(5.minutes, retryDelay(1))
        assertEquals(10.minutes, retryDelay(2))
        assertEquals(20.minutes, retryDelay(3))
        assertEquals(80.minutes, retryDelay(5))
    }

    @Test
    fun `the wait stops growing at a day`() {
        assertEquals(1.days, retryDelay(10))
        assertEquals(1.days, retryDelay(1000))
    }

    @Test
    fun `working feeds are due after the rate limit`() {
        assertFalse(isDue(feed(0, 4.minutes), now, forceRefresh = false))
        assertTrue(isDue(feed(0, 5.minutes), now, forceRefresh = false))
    }

    @Test
    fun `a forced refresh fetches working feeds right away`() {
        assertTrue(isDue(feed(0, 1.minutes), now, forceRefresh = true))
    }

    @Test
    fun `failing feeds wait out their backoff even when forced`() {
        assertFalse(isDue(feed(4, 30.minutes), now, forceRefresh = true))
        assertTrue(isDue(feed(4, 40.minutes), now, forceRefresh = true))
        assertFalse(isDue(feed(20, 23.hours), now, forceRefresh = false))
        assertTrue(isDue(feed(20, 1.days), now, forceRefresh = false))
    }

    @Test
    fun `feeds never attempted are due`() {
        val neverFetched = RssFeed(title = "Feed", url = "https://example.com/rss", category = "News")
        assertTrue(isDue(neverFetched, now, forceRefresh = false))
    }
}
