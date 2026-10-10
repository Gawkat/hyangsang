package dev.kettu.hyangsang.data.defaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedSourcesTest {

    private val sources = FeedSources(
        mapOf("yna.co.kr" to "Yonhap News", "kids.donga.com" to "Kids Dong-A", "rss.donga.com" to "Dong-A Ilbo")
    )

    @Test
    fun `a feed on a built-in host gets that source's name`() {
        assertEquals("Yonhap News", sources.nameOf("https://www.yna.co.kr/rss/sports.xml"))
        assertEquals("Yonhap News", sources.nameOf("http://YNA.co.kr/rss/other.xml"))
    }

    @Test
    fun `sources sharing a domain are told apart by their host`() {
        assertEquals("Kids Dong-A", sources.nameOf("https://kids.donga.com/rss/allArticle.xml"))
        assertEquals("Dong-A Ilbo", sources.nameOf("https://rss.donga.com/politics.xml"))
    }

    @Test
    fun `other feeds are named by their host`() {
        assertEquals("example.com", sources.nameOf("https://www.example.com/feed?cat=1&x=2"))
        assertEquals("blog.example.com", sources.nameOf("https://blog.example.com/rss"))
    }

    @Test
    fun `a URL without a host is used as it is`() {
        assertNull(FeedSources.hostOf("not a url"))
        assertEquals("not a url", sources.nameOf("not a url"))
    }

    @Test
    fun `each built-in host belongs to one source`() {
        val sourcesByHost = DefaultData.defaultFeeds.groupBy({ FeedSources.hostOf(it.url) }, { it.source })
        assertNull(sourcesByHost[null])
        sourcesByHost.forEach { (host, ids) -> assertEquals(host, 1, ids.distinct().size) }
    }
}
