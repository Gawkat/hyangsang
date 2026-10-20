package dev.kettu.hyangsang.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.RssFeed
import java.net.URI

// Where an article comes from: its feed, or for an imported one the site it was shared from
@Composable
fun sourceLabel(feed: RssFeed?, sourceUrl: String?): String =
    feed?.title ?: sourceUrl?.let(::siteOf) ?: stringResource(R.string.pasted_text)

// An imported link isn't downloaded yet when it's first listed, so it has no title until then
fun displayTitle(title: String, sourceUrl: String?): String = title.ifBlank { sourceUrl.orEmpty() }

private fun siteOf(url: String): String = try {
    URI(url).host?.removePrefix("www.") ?: url
} catch (_: Exception) {
    url
}
