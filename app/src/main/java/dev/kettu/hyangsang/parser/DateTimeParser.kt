package dev.kettu.hyangsang.parser

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// Dates without a zone or offset are assumed to be in Korean time, as the feeds are Korean
private val FEED_ZONE = ZoneId.of("Asia/Seoul")

private val zonedFormatters = listOf(
    DateTimeFormatter.RFC_1123_DATE_TIME, // Standard RSS: "Tue, 05 Oct 2021 14:48:00 GMT"
    DateTimeFormatter.ISO_DATE_TIME,       // Standard ISO: "2021-10-05T14:48:00Z"
    // Fallbacks for non-standard variations
    // RFC 1123 with a colon in the offset: "Mon, 28 Sep 2026 04:00:00 +09:00"
    DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss XXX", Locale.ENGLISH),
    // Numeric month: "Sun, 27 09 2026 23:00:00 +0900"
    DateTimeFormatter.ofPattern("EEE, d MM yyyy HH:mm:ss Z", Locale.ENGLISH)
)

private val localFormatters = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH), // "2026-09-27 19:00:00"
    DateTimeFormatter.ISO_LOCAL_DATE_TIME                                 // "2026-09-27T19:00:00"
)

private val whitespace = Regex("\\s+")

// "Sun,27 Sep 2026 ..." is missing the space after the day of the week
private val dayOfWeekWithoutSpace = Regex("^(\\p{Alpha}{3}),(?=\\S)")

// Abbreviations like KST are ambiguous to java.time, so only the known one is mapped
private val kstSuffix = Regex(" KST$")

fun parseToIso8601(dateTime: String?): String? {
    if (dateTime.isNullOrBlank()) return null

    val normalized = dateTime.trim()
        .replace(whitespace, " ")
        .replace(dayOfWeekWithoutSpace, "$1, ")
        .replace(kstSuffix, " +0900")

    // Parse and convert to UTC Instant for a uniform ISO-8601 storage format
    for (formatter in zonedFormatters) {
        try {
            return ZonedDateTime.parse(normalized, formatter).toInstant().toString()
        } catch (_: Exception) {
            continue // Try the next formatter
        }
    }

    for (formatter in localFormatters) {
        try {
            return LocalDateTime.parse(normalized, formatter).atZone(FEED_ZONE).toInstant().toString()
        } catch (_: Exception) {
            continue
        }
    }

    return null
}
