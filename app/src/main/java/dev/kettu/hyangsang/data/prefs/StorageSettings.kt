package dev.kettu.hyangsang.data.prefs

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
