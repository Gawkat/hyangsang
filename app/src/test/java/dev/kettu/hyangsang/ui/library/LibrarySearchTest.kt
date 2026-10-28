package dev.kettu.hyangsang.ui.library

import dev.kettu.hyangsang.data.local.entity.ArticleSummary
import dev.kettu.hyangsang.data.local.entity.ArticleSummaryWithFeed
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookWithChapter
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.ui.utils.SearchTerms
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {

    private fun article(
        title: String = "",
        description: String = "",
        sourceUrl: String? = null,
        feedTitle: String? = null
    ) = ArticleSummaryWithFeed(
        article = ArticleSummary(
            id = 1,
            feedId = if (feedTitle == null) null else 1,
            title = title,
            description = description,
            sourceUrl = sourceUrl,
            addedDate = "2026-10-28T00:00:00Z",
            pubDate = null,
            lastReadDate = null,
            savedDate = "2026-10-28T00:00:00Z"
        ),
        feed = feedTitle?.let { RssFeed(id = 1, title = it, url = "https://example.com/rss", category = "news") }
    )

    private fun book(title: String, author: String?) = BookWithChapter(
        book = Book(
            title = title,
            author = author,
            language = "ko",
            folder = "book",
            coverFile = null,
            sourceFileName = null,
            sourceSha256 = "",
            addedDate = 0,
            chapterCount = 1,
            totalChars = 1,
            wordCount = 1,
            sizeBytes = 1,
            parserVersion = 1
        ),
        chapterTitle = null
    )

    @Test
    fun `articles match on their title, description and source`() {
        val item = article(
            title = "코스피 반등",
            description = "외국인 매수",
            sourceUrl = "https://www.yna.co.kr/view/1",
            feedTitle = "연합뉴스"
        )
        assertTrue(SearchTerms("반등").matches(item))
        assertTrue(SearchTerms("외국인").matches(item))
        assertTrue(SearchTerms("연합뉴스 코스피").matches(item))
        assertFalse(SearchTerms("환율").matches(item))
    }

    @Test
    fun `an imported page matches on its site`() {
        assertTrue(SearchTerms("brunch").matches(article(sourceUrl = "https://brunch.co.kr/@a/1")))
        assertTrue(SearchTerms("코리아").matches(article(feedTitle = "BBC News 코리아")))
    }

    @Test
    fun `books match on their title and author`() {
        val item = book("현진건 단편선", "현진건")
        assertTrue(SearchTerms("단편선").matches(item))
        assertTrue(SearchTerms("현진건").matches(item))
        assertFalse(SearchTerms("이효석").matches(book("감자", null)))
    }
}
