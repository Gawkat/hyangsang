package dev.kettu.hyangsang.network

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Limits how many requests run at once to each host. Sites with many feeds, such as 동아일보 with
 * twelve, would otherwise get them all in one burst, which their bot protection can take for an
 * attack and answer by blocking the device's address.
 */
class HostLimiter(private val permitsPerHost: Int) {
    private val semaphores = ConcurrentHashMap<String, Semaphore>()

    suspend fun <T> withPermit(url: String, block: suspend () -> T): T =
        semaphores.computeIfAbsent(hostOf(url)) { Semaphore(permitsPerHost) }.withPermit { block() }

    // Falls back to the whole URL, so a URL without a host is at least limited on its own
    private fun hostOf(url: String): String =
        runCatching { URI(url).host?.lowercase() }.getOrNull() ?: url
}
