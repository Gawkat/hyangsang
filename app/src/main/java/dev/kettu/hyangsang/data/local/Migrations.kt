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

// Articles can be imported, without a feed or, for pasted text, a source URL. SQLite can't drop
// NOT NULL from a column, so the table is rebuilt
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `articles_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`feedId` INTEGER, `title` TEXT NOT NULL, `description` TEXT NOT NULL, " +
                    "`content` TEXT, `sourceUrl` TEXT, `addedDate` TEXT NOT NULL, `pubDate` TEXT, " +
                    "`lastReadDate` TEXT, `scrollPosition` INTEGER NOT NULL, `savedDate` TEXT, " +
                    "`parserVersion` INTEGER NOT NULL DEFAULT 0, " +
                    "FOREIGN KEY(`feedId`) REFERENCES `rss_feeds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("INSERT INTO `articles_new` SELECT * FROM `articles`")
        db.execSQL("DROP TABLE `articles`")
        db.execSQL("ALTER TABLE `articles_new` RENAME TO `articles`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_feedId` ON `articles` (`feedId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_pubDate` ON `articles` (`pubDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_addedDate` ON `articles` (`addedDate`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_articles_sourceUrl` ON `articles` (`sourceUrl`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_articles_savedDate` ON `articles` (`savedDate`)")
    }
}

// Tracks how far each article has been read. Articles opened before start from nothing
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `articles` ADD COLUMN `progress` REAL NOT NULL DEFAULT 0")
    }
}

// Counts failed syncs in a row, so failing feeds are retried less often. Feeds failing now start
// with one, since how many times they failed before wasn't kept
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `rss_feeds` ADD COLUMN `failedSyncs` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `rss_feeds` SET `failedSyncs` = 1 WHERE `lastSyncError` IS NOT NULL")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18,
    MIGRATION_18_19, MIGRATION_19_20
)
