package dev.kettu.hyangsang.data.defaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTopicTest {
    // Every built-in feed must be reachable from the setup, so each category needs one place
    @Test
    fun everyCategoryHasExactlyOnePlace() {
        val places = FeedTopic.entries.map { it.categories } +
            listOf(FeedTopic.EASIER_READING_CATEGORIES, FeedTopic.GENERAL_CATEGORIES)
        DefaultCategory.entries.forEach { category ->
            assertEquals("$category", 1, places.count { category in it })
        }
    }

    @Test
    fun everyFeedIsPickedWithEverything() {
        val all = FeedTopic.feedsFor(FeedTopic.entries.toSet(), easierReading = true)
        assertEquals(DefaultData.defaultFeeds, all)
    }

    @Test
    fun generalFeedsComeWithAnyTopic() {
        val feeds = FeedTopic.feedsFor(setOf(FeedTopic.SPORTS), easierReading = false)
        assertTrue(feeds.any { it.category == DefaultCategory.NEWS })
        assertTrue(feeds.all { it.category == DefaultCategory.SPORTS || it.category == DefaultCategory.NEWS })
    }

    @Test
    fun easierReadingAloneHasNoGeneralFeeds() {
        val feeds = FeedTopic.feedsFor(emptySet(), easierReading = true)
        assertTrue(feeds.isNotEmpty())
        assertTrue(feeds.all { it.category in FeedTopic.EASIER_READING_CATEGORIES })
    }

    @Test
    fun nothingPicksNothing() {
        assertTrue(FeedTopic.feedsFor(emptySet(), easierReading = false).isEmpty())
    }
}
