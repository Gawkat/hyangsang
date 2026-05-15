package dev.kettu.hyangsang.parser

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun parseToIso8601(dateTime: String?): String? {
    if (dateTime.isNullOrBlank()) return null

    val formatters = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME, // Standard RSS: "Tue, 05 Oct 2021 14:48:00 GMT"
        DateTimeFormatter.ISO_DATE_TIME,       // Standard ISO: "2021-10-05T14:48:00Z"
        // Fallback for non-standard variations
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    )

    for (formatter in formatters) {
        try {
            // Parse and convert to UTC Instant for a uniform ISO-8601 storage format
            return ZonedDateTime.parse(dateTime, formatter).toInstant().toString()
        } catch (e: Exception) {
            continue // Try the next formatter
        }
    }

    return null
}