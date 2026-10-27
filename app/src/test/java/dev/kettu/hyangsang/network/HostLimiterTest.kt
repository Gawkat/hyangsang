package dev.kettu.hyangsang.network

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class HostLimiterTest {
    // Runs the URLs at once and returns the most requests that ran together for each host
    private fun peakPerHost(limiter: HostLimiter, urls: List<String>): Map<String, Int> = runBlocking {
        val running = ConcurrentHashMap<String, AtomicInteger>()
        val peak = ConcurrentHashMap<String, Int>()
        urls.map { url ->
            async {
                limiter.withPermit(url) {
                    val host = java.net.URI(url).host
                    val now = running.computeIfAbsent(host) { AtomicInteger() }.incrementAndGet()
                    peak.merge(host, now, ::maxOf)
                    delay(20)
                    running.getValue(host).decrementAndGet()
                }
            }
        }.awaitAll()
        peak
    }

    @Test
    fun `limits requests to one host`() {
        val urls = (1..12).map { "https://rss.donga.com/feed$it.xml" }
        assertEquals(mapOf("rss.donga.com" to 2), peakPerHost(HostLimiter(2), urls))
    }

    @Test
    fun `hosts are limited separately`() {
        val urls = (1..4).map { "https://rss.donga.com/feed$it.xml" } +
            (1..4).map { "https://kids.donga.com/feed$it.xml" }
        assertEquals(
            mapOf("rss.donga.com" to 2, "kids.donga.com" to 2),
            peakPerHost(HostLimiter(2), urls)
        )
    }

    @Test
    fun `hosts differing in case share a limit`() {
        val urls = listOf("https://Example.com/a", "https://example.com/b", "https://EXAMPLE.com/c")
        val limiter = HostLimiter(1)
        val running = AtomicInteger()
        val peak = AtomicInteger()
        runBlocking {
            urls.map { url ->
                async {
                    limiter.withPermit(url) {
                        peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                        delay(20)
                        running.decrementAndGet()
                    }
                }
            }.awaitAll()
        }
        assertEquals(1, peak.get())
    }
}
