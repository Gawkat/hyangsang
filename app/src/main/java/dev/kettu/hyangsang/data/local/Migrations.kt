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

// The dictionary moved to DictionaryDatabase, so it can be replaced without touching user data
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `dictionary_examples`")
        db.execSQL("DROP TABLE IF EXISTS `dictionary_senses`")
        db.execSQL("DROP TABLE IF EXISTS `dictionary_entries`")
    }
}

// Records which parser version produced each article's content
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `articles` ADD COLUMN `parserVersion` INTEGER NOT NULL DEFAULT 0")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
