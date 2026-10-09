package dev.kettu.hyangsang.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kettu.hyangsang.data.local.dao.ArticleDao
import dev.kettu.hyangsang.data.local.entity.Article
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.data.repository.escapeLike
import dev.kettu.hyangsang.parser.ContentBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleDaoTest {
    private lateinit var db: HyangsangDatabase
    private lateinit var dao: ArticleDao
    private var newsFeed = 0L
    private var sportsFeed = 0L

    @Before
    fun createDb(): Unit = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            HyangsangDatabase::class.java
        ).build()
        dao = db.articleDao()
        newsFeed = db.rssFeedDao().insertFeed(RssFeed(title = "News", url = "n", category = "뉴스"))
        sportsFeed =
            db.rssFeedDao().insertFeed(RssFeed(title = "Sports", url = "s", category = "스포츠"))

        dao.insertArticles(
            listOf(
                article(1, newsFeed, "경제 성장률 발표", pubDate = "2026-10-01T00:00:00Z"),
                article(2, newsFeed, "100% 환불", pubDate = "2026-10-02T00:00:00Z", read = true),
                article(3, sportsFeed, "야구 결승전", pubDate = "2026-10-03T00:00:00Z"),
                article(4, sportsFeed, "축구", description = "경제 효과", pubDate = null)
            )
        )
    }

    @After
    fun closeDb() = db.close()

    private fun article(
        id: Long,
        feedId: Long,
        title: String,
        description: String = "",
        pubDate: String?,
        read: Boolean = false
    ) = Article(
        id = id,
        feedId = feedId,
        title = title,
        description = description,
        sourceUrl = "https://example.com/$id",
        addedDate = "2026-09-30T00:00:00Z",
        pubDate = pubDate,
        lastReadDate = if (read) "2026-10-03T00:00:00Z" else null
    )

    private fun ids(
        category: String? = null,
        feedId: Long? = null,
        query: String = "",
        unreadOnly: Boolean = false
    ) = runBlocking {
        dao.getArticleSummaries(category, feedId, query, unreadOnly).first().map { it.article.id }
    }

    @Test
    fun listsAllArticlesNewestFirst() {
        // Without a publication date, the date it was added sorts it
        assertEquals(listOf(3L, 2L, 1L, 4L), ids())
    }

    @Test
    fun filtersByCategoryAndFeed() {
        assertEquals(listOf(3L, 4L), ids(category = "스포츠"))
        assertEquals(listOf(2L, 1L), ids(feedId = newsFeed))
    }

    @Test
    fun searchesTitlesAndDescriptions() {
        assertEquals(listOf(1L, 4L), ids(query = escapeLike("경제")))
    }

    @Test
    fun searchMatchesWildcardsLiterally() {
        assertEquals(listOf(2L), ids(query = escapeLike("0%")))
        assertEquals(emptyList<Long>(), ids(query = escapeLike("_")))
    }

    @Test
    fun filtersUnread() {
        assertEquals(listOf(3L, 1L, 4L), ids(unreadOnly = true))
    }

    @Test
    fun countsUnreadPerFeed(): Unit = runBlocking {
        assertEquals(mapOf(newsFeed to 1, sportsFeed to 2), dao.getUnreadCounts().first())
    }

    @Test
    fun leavesOutFeedsThatAreOff(): Unit = runBlocking {
        db.rssFeedDao().setEnabled(sportsFeed, false)
        dao.updateSavedDate(3, "2026-10-03T01:00:00Z")

        assertEquals(listOf(2L, 1L), ids())
        assertEquals(mapOf(newsFeed to 1), dao.getUnreadCounts().first())
        // Saved articles still show on the saved screen
        assertEquals(listOf(3L), dao.getSavedArticleSummaries().first().map { it.article.id })
    }

    @Test
    fun clearsContentOfUnsavedArticlesNotOpenedSinceCutoff(): Unit = runBlocking {
        val content = listOf(ContentBlock.Text("본문"))
        (1L..4L).forEach { dao.replaceContent(it, content, maxPosition = 0, parserVersion = 5) }
        dao.updateProgress(1, position = 0, timestamp = "2026-10-02T00:00:00Z") // opened recently
        dao.updateProgress(2, position = 0, timestamp = "2026-09-01T00:00:00Z") // opened long ago
        dao.updateSavedDate(3, "2026-09-01T00:00:00Z") // saved
        // 4 was never opened and was added on 2026-09-30

        val cleared = dao.clearContentNotOpenedSince("2026-10-01T00:00:00Z")

        assertEquals(2, cleared)
        assertEquals(content, dao.getArticleById(1)?.content)
        assertNull(dao.getArticleById(2)?.content)
        assertEquals(0, dao.getArticleById(2)?.parserVersion)
        assertEquals(content, dao.getArticleById(3)?.content)
        assertNull(dao.getArticleById(4)?.content)
    }

    @Test
    fun deletesUnsavedArticlesPublishedAndAddedBeforeCutoff(): Unit = runBlocking {
        val longAgo = "2026-01-01T00:00:00Z"
        dao.insertArticles(
            listOf(
                article(5, newsFeed, "old", pubDate = longAgo).copy(addedDate = longAgo),
                article(6, newsFeed, "old, saved", pubDate = longAgo).copy(addedDate = longAgo),
                article(7, newsFeed, "old, no date", pubDate = null).copy(addedDate = longAgo),
                // An old publication date on an item that was only just added
                article(8, newsFeed, "old date, added recently", pubDate = longAgo)
            )
        )
        dao.updateSavedDate(6, longAgo)

        val deleted = dao.deleteUnsavedOlderThan("2026-07-01T00:00:00Z")

        assertEquals(2, deleted)
        assertEquals(setOf(1L, 2L, 3L, 4L, 6L, 8L), ids().toSet())
    }

    @Test
    fun includesFeedWithSummary(): Unit = runBlocking {
        dao.updateSavedDate(3, "2026-10-03T01:00:00Z")
        val saved = dao.getSavedArticleSummaries().first().single()
        assertEquals(3L, saved.article.id)
        assertEquals("Sports", saved.feed.title)
    }
}
