package dev.kettu.hyangsang.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Dates are taken from real feeds
class DateTimeParserTest {

    @Test
    fun `standard RFC 1123 dates are parsed`() {
        assertEquals("2026-09-27T19:43:47Z", parseToIso8601("Mon, 28 Sep 2026 04:43:47 +0900"))
        assertEquals("2026-09-27T20:12:54Z", parseToIso8601("Sun, 27 Sep 2026 20:12:54 +0000"))
    }

    @Test
    fun `ISO dates with an offset are parsed`() {
        assertEquals("2026-09-27T15:00:00Z", parseToIso8601("2026-09-28T00:00:00+09:00"))
    }

    @Test
    fun `dates without a zone are read as Korean time`() {
        assertEquals("2026-09-27T10:00:00Z", parseToIso8601("2026-09-27 19:00:00"))
        assertEquals("2026-09-27T10:00:00Z", parseToIso8601("2026-09-27T19:00:00"))
    }

    @Test
    fun `RFC 1123 dates with a colon in the offset are parsed`() {
        assertEquals("2026-09-27T19:00:00Z", parseToIso8601("Mon, 28 Sep 2026 04:00:00 +09:00"))
    }

    @Test
    fun `missing space after the day of the week is tolerated`() {
        assertEquals("2026-09-27T14:48:46Z", parseToIso8601("Sun,27 Sep 2026 23:48:46 +0900"))
    }

    @Test
    fun `repeated spaces are tolerated`() {
        assertEquals("2026-09-27T19:32:00Z", parseToIso8601("27 Sep  2026 19:32:00 GMT"))
    }

    @Test
    fun `numeric months are parsed`() {
        assertEquals("2026-09-27T14:56:13Z", parseToIso8601("Sun, 27 09 2026 23:56:13 +0900"))
    }

    @Test
    fun `KST is read as Korean time`() {
        assertEquals("2026-09-25T05:18:05Z", parseToIso8601("Fri, 25 Sep 2026 14:18:05 KST"))
    }

    @Test
    fun `unparseable dates return null`() {
        assertNull(parseToIso8601(null))
        assertNull(parseToIso8601(" "))
        assertNull(parseToIso8601("어제"))
    }
}
