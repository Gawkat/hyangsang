package dev.kettu.hyangsang.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RssFeedParserTest {

    private fun feed(vararg items: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
            <channel>
                <title>Feed</title>
                ${items.joinToString("\n")}
            </channel>
        </rss>
    """.trimIndent()

    @Test
    fun pubDateIsRead() {
        val items = RssFeedParser().parse(
            feed("<item><title>A</title><link>https://example.com/a</link><pubDate>Mon, 28 Sep 2026 04:43:37 +0900</pubDate></item>")
        )

        assertEquals("Mon, 28 Sep 2026 04:43:37 +0900", items.single().pubDate)
    }

    @Test
    fun dcDateIsUsedWithoutPubDate() {
        val items = RssFeedParser().parse(
            feed("<item><title>A</title><link>https://example.com/a</link><dc:date>2026-09-28T00:00:00+09:00</dc:date></item>")
        )

        assertEquals("2026-09-28T00:00:00+09:00", items.single().pubDate)
    }

    @Test
    fun pubDateIsPreferredOverDcDate() {
        val items = RssFeedParser().parse(
            feed("<item><dc:date>2026-09-28T00:00:00+09:00</dc:date><title>A</title><link>https://example.com/a</link><pubDate>Mon, 28 Sep 2026 04:43:37 +0900</pubDate></item>")
        )

        assertEquals("Mon, 28 Sep 2026 04:43:37 +0900", items.single().pubDate)
    }
}
