package dev.kettu.hyangsang.parser

import junit.framework.TestCase.assertEquals
import org.jsoup.Jsoup
import org.junit.Test

class ArticleParserTest {
    private val articleParser = ArticleParser()

    @Test
    fun `should use BbcNewsParser for BBC URLs`() {
        val url = "https://feeds.bbci.co.uk/korean/rss.xml"
        val doc = Jsoup.parse("<html><body><main><p>BBC Contents</p></main></body></html>")

        val result = articleParser.parse(url, doc)
        assertEquals("BBC Contents", result)
    }

    @Test
    fun `should use YonhapNewsParser for Yonhap URLs`() {
        val url = "https://www.yna.co.kr/rss/entertainment.xml"
        val doc = Jsoup.parse("<html><body><article><p>Yonhap Contents</p></article></body></html>")

        val result = articleParser.parse(url, doc)
        assertEquals("Yonhap Contents", result)
    }

    @Test
    fun `should use GenericContentsParser for unknown URLs`() {
        val url = "https://example.com/article"
        val doc = Jsoup.parse("<html><body><article><p>Hello</p></article></body></html>")

        val result = articleParser.parse(url, doc)
        assertEquals("Hello", result)
    }
}