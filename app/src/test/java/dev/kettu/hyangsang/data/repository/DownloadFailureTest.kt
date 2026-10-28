package dev.kettu.hyangsang.data.repository

import org.jsoup.HttpStatusException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.UnknownHostException

class DownloadFailureTest {
    private fun status(code: Int) = HttpStatusException("HTTP error", code, "https://example.com")

    @Test
    fun `refused downloads are blocked`() {
        assertEquals(DownloadFailure.BLOCKED, DownloadFailure.of(status(403)))
        assertEquals(DownloadFailure.BLOCKED, DownloadFailure.of(status(401)))
    }

    @Test
    fun `missing pages are not found`() {
        assertEquals(DownloadFailure.NOT_FOUND, DownloadFailure.of(status(404)))
        assertEquals(DownloadFailure.NOT_FOUND, DownloadFailure.of(status(410)))
    }

    @Test
    fun `error pages served as articles are not found`() {
        assertEquals(
            DownloadFailure.NOT_FOUND,
            DownloadFailure.of(ErrorPageException("https://example.com/article"))
        )
    }

    @Test
    fun `server and connection errors may pass`() {
        assertEquals(DownloadFailure.OTHER, DownloadFailure.of(status(503)))
        assertEquals(DownloadFailure.OTHER, DownloadFailure.of(UnknownHostException("example.com")))
    }
}
