package dev.kettu.hyangsang.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Adds saved articles and feed sync status
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `articles` ADD COLUMN `savedDate` TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_savedDate` ON `articles` (`savedDate`)")
        db.execSQL("ALTER TABLE `rss_feeds` ADD COLUMN `lastSyncAttempt` TEXT")
        db.execSQL("ALTER TABLE `rss_feeds` ADD COLUMN `lastSyncError` TEXT")
    }
}

// Indexes dictionary words for lookups
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dictionary_entries_word` ON `dictionary_entries` (`word`)")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_13_14, MIGRATION_14_15)
