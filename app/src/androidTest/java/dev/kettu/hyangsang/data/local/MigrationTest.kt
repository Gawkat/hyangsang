package dev.kettu.hyangsang.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        HyangsangDatabase::class.java
    )

    @Test
    fun migrate13To14_keepsDataAndAddsColumns() {
        helper.createDatabase(TEST_DB, 13).apply {
            execSQL(
                "INSERT INTO rss_feeds (id, title, url, category, isEnabled, lastSynced) " +
                    "VALUES (1, 'Feed', 'https://example.com/rss', 'News', 1, '2026-09-25T00:00:00Z')"
            )
            execSQL(
                "INSERT INTO articles (id, feedId, title, description, content, sourceUrl, addedDate, pubDate, lastReadDate, scrollPosition) " +
                    "VALUES (1, 1, 'Title', 'Description', NULL, 'https://example.com/a', '2026-09-25T00:00:00Z', NULL, NULL, 3)"
            )
            close()
        }

        // Also validates the result against the exported v14 schema
        val db = helper.runMigrationsAndValidate(TEST_DB, 14, true, MIGRATION_13_14)

        db.query("SELECT title, scrollPosition, savedDate FROM articles WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("Title", it.getString(0))
            assertEquals(3, it.getInt(1))
            assertNull(it.getString(2))
        }
        db.query("SELECT isEnabled, lastSynced, lastSyncAttempt, lastSyncError FROM rss_feeds WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
            assertEquals("2026-09-25T00:00:00Z", it.getString(1))
            assertNull(it.getString(2))
            assertNull(it.getString(3))
        }
    }

    @Test
    fun migrate14To15_indexesDictionaryWords() {
        helper.createDatabase(TEST_DB, 14).close()

        // Validation fails if the index Room expects is missing
        val db = helper.runMigrationsAndValidate(TEST_DB, 15, true, MIGRATION_14_15)

        db.query("EXPLAIN QUERY PLAN SELECT * FROM dictionary_entries WHERE word = '값'").use {
            it.moveToFirst()
            assertTrue(it.getString(3).contains("index_dictionary_entries_word"))
        }
    }

    @Test
    fun migrate15To16_dropsDictionaryTables() {
        helper.createDatabase(TEST_DB, 15).apply {
            execSQL(
                "INSERT INTO rss_feeds (id, title, url, category, isEnabled, lastSynced) " +
                    "VALUES (1, 'Feed', 'https://example.com/rss', 'News', 1, '2026-09-25T00:00:00Z')"
            )
            execSQL(
                "INSERT INTO dictionary_entries (id, originalId, word, origin, homonymNumber, partOfSpeech, vocabularyLevel, semanticCategory, lexicalUnit, pronunciation, audioUrl) " +
                    "VALUES (1, '1', '값', NULL, 0, 'Noun', NULL, NULL, 'Word', NULL, NULL)"
            )
            close()
        }

        // Validation fails if the dictionary tables are still there
        val db = helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16)

        db.query("SELECT count(*) FROM sqlite_master WHERE name LIKE 'dictionary_%'").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT title FROM rss_feeds WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("Feed", it.getString(0))
        }
    }

    @Test
    fun migrate16To17_marksStoredArticlesForReparse() {
        helper.createDatabase(TEST_DB, 16).apply {
            execSQL(
                "INSERT INTO rss_feeds (id, title, url, category, isEnabled, lastSynced) " +
                    "VALUES (1, 'Feed', 'https://example.com/rss', 'News', 1, '2026-09-25T00:00:00Z')"
            )
            execSQL(
                "INSERT INTO articles (id, feedId, title, description, content, sourceUrl, addedDate, pubDate, lastReadDate, scrollPosition) " +
                    "VALUES (1, 1, 'Title', 'Description', '[]', 'https://example.com/a', '2026-09-25T00:00:00Z', NULL, NULL, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 17, true, MIGRATION_16_17)

        // Version 0 is older than any ArticleParser.VERSION, so the article is parsed again
        db.query("SELECT content, parserVersion FROM articles WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("[]", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
    }

    @Test
    fun migrate17To18_keepsArticlesAndAllowsImports() {
        helper.createDatabase(TEST_DB, 17).apply {
            execSQL(
                "INSERT INTO rss_feeds (id, title, url, category, isEnabled, lastSynced) " +
                    "VALUES (1, 'Feed', 'https://example.com/rss', 'News', 1, '2026-09-25T00:00:00Z')"
            )
            execSQL(
                "INSERT INTO articles (id, feedId, title, description, content, sourceUrl, addedDate, pubDate, lastReadDate, scrollPosition, savedDate, parserVersion) " +
                    "VALUES (1, 1, 'Title', 'Description', '[]', 'https://example.com/a', '2026-09-25T00:00:00Z', NULL, NULL, 4, '2026-09-26T00:00:00Z', 2)"
            )
            close()
        }

        // Also validates the rebuilt table and its indices against the exported v18 schema
        val db = helper.runMigrationsAndValidate(TEST_DB, 18, true, MIGRATION_17_18)

        db.query("SELECT feedId, sourceUrl, scrollPosition, savedDate, parserVersion FROM articles WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
            assertEquals("https://example.com/a", it.getString(1))
            assertEquals(4, it.getInt(2))
            assertEquals("2026-09-26T00:00:00Z", it.getString(3))
            assertEquals(2, it.getInt(4))
        }
        // Pasted text has neither a feed nor a URL
        db.execSQL(
            "INSERT INTO articles (feedId, title, description, content, sourceUrl, addedDate, scrollPosition) " +
                "VALUES (NULL, 'Pasted', '', '[]', NULL, '2026-10-20T00:00:00Z', 0)"
        )
        db.execSQL(
            "INSERT INTO articles (feedId, title, description, content, sourceUrl, addedDate, scrollPosition) " +
                "VALUES (NULL, 'Pasted 2', '', '[]', NULL, '2026-10-20T00:00:00Z', 0)"
        )
        db.query("SELECT count(*) FROM articles WHERE feedId IS NULL").use {
            it.moveToFirst()
            assertEquals(2, it.getInt(0))
        }
    }

    @Test
    fun migrate18To19_keepsPositionAndAddsProgress() {
        helper.createDatabase(TEST_DB, 18).apply {
            execSQL(
                "INSERT INTO articles (id, feedId, title, description, content, sourceUrl, addedDate, pubDate, lastReadDate, scrollPosition, savedDate, parserVersion) " +
                    "VALUES (1, NULL, 'Title', 'Description', '[]', NULL, '2026-09-25T00:00:00Z', NULL, '2026-09-26T00:00:00Z', 4, NULL, 2)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 19, true, MIGRATION_18_19)

        db.query("SELECT scrollPosition, progress, lastReadDate FROM articles WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(4, it.getInt(0))
            assertEquals(0f, it.getFloat(1))
            assertEquals("2026-09-26T00:00:00Z", it.getString(2))
        }
    }

    @Test
    fun migrate19To20_countsFailingFeedsOnce() {
        helper.createDatabase(TEST_DB, 19).apply {
            execSQL(
                "INSERT INTO rss_feeds (id, title, url, category, isEnabled, lastSynced, lastSyncAttempt, lastSyncError) " +
                    "VALUES (1, 'Working', 'https://example.com/a', 'News', 1, '2026-10-20T00:00:00Z', '2026-10-20T00:00:00Z', NULL)"
            )
            execSQL(
                "INSERT INTO rss_feeds (id, title, url, category, isEnabled, lastSynced, lastSyncAttempt, lastSyncError) " +
                    "VALUES (2, 'Failing', 'https://example.com/b', 'News', 1, '2026-10-01T00:00:00Z', '2026-10-20T00:00:00Z', 'timeout')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 20, true, MIGRATION_19_20)

        db.query("SELECT failedSyncs, lastSyncError FROM rss_feeds ORDER BY id").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
            it.moveToNext()
            assertEquals(1, it.getInt(0))
            assertEquals("timeout", it.getString(1))
        }
    }
}
