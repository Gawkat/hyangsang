package dev.kettu.hyangsang.data.defaults

import android.content.Context
import java.net.URI

/**
 * Names the site each feed comes from. Feeds on the same host as a built-in feed get its source
 * name, so a Yonhap feed the user added joins the built-in ones. Other feeds are named by their
 * host, without "www.".
 */
class FeedSources(private val sourcesByHost: Map<String, String>) {

    fun nameOf(url: String): String {
        val host = hostOf(url) ?: return url
        return sourcesByHost[host] ?: host
    }

    companion object {
        fun from(context: Context) = FeedSources(
            DefaultData.defaultFeeds
                .mapNotNull { feed -> hostOf(feed.url)?.let { it to context.getString(feed.source) } }
                .toMap()
        )

        fun hostOf(url: String): String? = runCatching { URI(url).host }.getOrNull()
            ?.lowercase()
            ?.removePrefix("www.")
            ?.ifEmpty { null }
    }
}
