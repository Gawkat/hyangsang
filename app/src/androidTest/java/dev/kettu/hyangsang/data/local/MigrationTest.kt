package dev.kettu.hyangsang.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
