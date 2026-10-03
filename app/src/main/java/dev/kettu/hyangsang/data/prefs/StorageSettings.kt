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
