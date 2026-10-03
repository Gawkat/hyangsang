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
}
