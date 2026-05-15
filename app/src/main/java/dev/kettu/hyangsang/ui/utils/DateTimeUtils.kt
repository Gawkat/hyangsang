package dev.kettu.hyangsang.ui.utils

import android.text.format.DateUtils
import java.time.Instant
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
            val instant = Instant.parse(isoString)
            DateUtils.getRelativeTimeSpanString(
                instant.toEpochMilli(),
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } catch (_: Exception) {
            isoString
        }
    }

    /**
     * Formats an ISO8601 string into a full localized date and time.
     */
    fun formatFullDate(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val instant = Instant.parse(isoString)
            val formatter = DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (_: Exception) {
            isoString
        }
    }
}