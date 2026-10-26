package dev.kettu.hyangsang.data.defaults

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ParserCanaryFeedsTest {
    // Tests run in the app module's folder
    private val feeds = File("../tools/parser-canary/feeds.tsv").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }

    // So a new source's parser is checked too
    @Test
    fun everySourceIsChecked() {
        val sources = feeds.map { it[0] }.toSet()
        DefaultSource.entries.forEach { assertTrue("$it", it.name in sources) }
    }

    // So the canary doesn't keep reading a feed that was removed
    @Test
    fun everyFeedIsBuiltIn() {
        feeds.forEach { (source, url) ->
            assertTrue(url, DefaultData.defaultFeeds.any { it.url == url && it.source.name == source })
        }
    }
}
