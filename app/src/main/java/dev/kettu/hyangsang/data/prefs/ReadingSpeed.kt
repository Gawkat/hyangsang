package dev.kettu.hyangsang.data.prefs

// How fast the user reads, for the read times of articles, books and chapters. Words are counted
// between spaces, so each is an 어절, and the fluent speed is about a native reader's. Each step
// changes read times by about the same ratio
enum class ReadingSpeed(val wordsPerMinute: Int) {
    BEGINNER(35),
    INTERMEDIATE(70),
    ADVANCED(120),
    FLUENT(200);

    companion object {
        val DEFAULT = INTERMEDIATE
    }
}
