package dev.kettu.hyangsang.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.pluralStringResource
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.prefs.ReadingSpeed
import kotlin.math.roundToInt

// The user's reading speed, provided for the whole app so every read time uses it
val LocalReadingSpeed = compositionLocalOf { ReadingSpeed.DEFAULT }

/** Minutes to read [wordCount] words at [speed], at least one. */
fun readingMinutes(wordCount: Int, speed: ReadingSpeed): Int = maxOf(1, wordCount / speed.wordsPerMinute)

/** "~4 h read" for a book, or "~40 min read" for an article or a short book. */
@Composable
fun readingTimeText(wordCount: Int): String {
    val minutes = readingMinutes(wordCount, LocalReadingSpeed.current)
    if (minutes < 60) return pluralStringResource(R.plurals.reading_time, minutes, minutes)
    val hours = (minutes / 60f).roundToInt()
    return pluralStringResource(R.plurals.reading_time_hours, hours, hours)
}
