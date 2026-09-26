package dev.kettu.hyangsang.ui.utils

import android.text.format.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object DateTimeUtils {
    /**
     * Formats an ISO8601 string into a relative time string (e.g., "2 hours ago").
     */
    fun formatRelativeTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val now = System.currentTimeMillis()
            // Anything under a minute old reads "1 min. ago" rather than "0 min. ago"
            val time = minOf(Instant.parse(isoString).toEpochMilli(), now - DateUtils.MINUTE_IN_MILLIS)
            DateUtils.getRelativeTimeSpanString(
                time,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } catch (_: Exception) {
            isoString
        }
    }

    /**
     * Formats an ISO8601 string into a localized date and time, without seconds.
     */
    fun formatDateTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val instant = Instant.parse(isoString)
            val formatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            isoString
        }
    }

    /**
     * The local calendar date of an ISO8601 string, or null if it can't be parsed.
     */
    fun localDate(isoString: String?): LocalDate? {
        if (isoString.isNullOrBlank()) return null
        return try {
            Instant.parse(isoString).atZone(ZoneId.systemDefault()).toLocalDate()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Formats a date for a list section header (e.g. "Sep 24, 2026").
     */
    fun formatDate(date: LocalDate): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(Locale.getDefault())
            .format(date)

    /**
     * Formats an ISO8601 string into a "Month Year" string (e.g., "June 2028").
     */
    fun formatMonthYear(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val instant = Instant.parse(isoString)
            val formatter = DateTimeFormatter
                .ofPattern("MMMM yyyy")
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            isoString
        }
    }
}
