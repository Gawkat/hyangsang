package dev.kettu.hyangsang.data.prefs

// How often enabled feeds are refreshed in the background. Null hours turns it off
enum class FeedSyncInterval(val hours: Int?) {
    OFF(null),
    SIX_HOURS(6),
    TWELVE_HOURS(12),
    DAILY(24);

    companion object {
        val DEFAULT = TWELVE_HOURS
    }
}

// How long the downloaded text of an unsaved article is kept after it was last opened. Null days
// keeps it for good
enum class ContentRetention(val days: Int?) {
    ONE_WEEK(7),
    TWO_WEEKS(14),
    ONE_MONTH(30),
    FOREVER(null);

    companion object {
        val DEFAULT = TWO_WEEKS
    }
}

// How long unsaved articles are kept after they were published and added. Null days keeps them
// for good. Well beyond how long items stay in a feed, so a removed article isn't added again
enum class ArticleRetention(val days: Int?) {
    ONE_MONTH(30),
    THREE_MONTHS(90),
    ONE_YEAR(365),
    FOREVER(null);

    companion object {
        val DEFAULT = THREE_MONTHS
    }
}
