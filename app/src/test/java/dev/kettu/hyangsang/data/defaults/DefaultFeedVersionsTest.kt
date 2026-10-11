package dev.kettu.hyangsang.data.defaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultFeedVersionsTest {

    @Test
    fun `every built-in feed has a version up to the current one`() {
        DefaultData.defaultFeeds.forEach { feed ->
            assertTrue(
                "${feed.url} has version ${feed.addedIn}; raise DefaultData.VERSION",
                feed.addedIn in DefaultData.BASELINE_VERSION..DefaultData.VERSION
            )
        }
    }

    @Test
    fun `built-in feed URLs are unique`() {
        val urls = DefaultData.defaultFeeds.map { it.url }
        assertEquals(urls.size, urls.toSet().size)
    }

    @Test
    fun `nothing is new after seeing the current version`() {
        assertTrue(DefaultData.feedsAddedSince(DefaultData.VERSION, emptySet()).isEmpty())
    }

    @Test
    fun `feeds added after the seen version are new, across several versions`() {
        val new = DefaultData.feedsAddedSince(DefaultData.BASELINE_VERSION, emptySet())
        assertEquals(DefaultData.defaultFeeds.filter { it.addedIn > DefaultData.BASELINE_VERSION }, new)
        assertTrue(new.isNotEmpty())
    }

    @Test
    fun `feeds the user already has are not new`() {
        val new = DefaultData.feedsAddedSince(DefaultData.BASELINE_VERSION, emptySet())
        val present = new.take(2).map { it.url }.toSet()
        assertEquals(
            new.drop(2),
            DefaultData.feedsAddedSince(DefaultData.BASELINE_VERSION, present)
        )
    }
}
