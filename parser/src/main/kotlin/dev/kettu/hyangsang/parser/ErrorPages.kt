package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import java.net.URI

/**
 * Signs that a page is a site's error page for an article that doesn't exist, served with a
 * success status instead of a 404, that hold across sites. Site parsers check their own sites'
 * error pages in [ContentsParser.isErrorPage].
 */
internal object ErrorPages {
    private val goBackRegex = Regex("""history\.(back\s*\(|go\s*\(\s*-)""")

    /** [url] is the page that was asked for, and the document's location where it ended up. */
    fun looksLikeErrorPage(url: String, document: Document): Boolean =
        redirectedHome(url, document) || pointsToParent(document) || onlyGoesBack(document)

    // A missing article that redirects to the home page
    private fun redirectedHome(url: String, document: Document): Boolean {
        val requested = uriOf(url)?.trimmedPath() ?: return false
        val landed = uriOf(document.location())?.trimmedPath() ?: return false
        return requested.isNotEmpty() && landed.isEmpty()
    }

    // The page gives the home page or a parent of its own path as its address, such as Yonhap's
    // "/view/" for a missing "/view/AKR…". A real article gives its own address
    private fun pointsToParent(document: Document): Boolean {
        val location = uriOf(document.location()) ?: return false
        val path = location.trimmedPath()
        if (path.isEmpty()) return false
        return listOfNotNull(
            document.selectFirst("link[rel=canonical]")?.absUrl("href"),
            document.selectFirst("meta[property=og:url]")?.absUrl("content")
        ).mapNotNull(::uriOf).any { address ->
            address.hostName() == location.hostName() && address.rawQuery == null &&
                    path.startsWith(address.trimmedPath() + "/")
        }
    }

    // A page without text that only sends the browser back, usually after an alert saying the
    // link is wrong, like on ND Soft's sites
    private fun onlyGoesBack(document: Document): Boolean =
        document.body().text().isBlank() &&
                document.select("script").any { goBackRegex.containsMatchIn(it.data()) }

    private fun uriOf(url: String): URI? = try {
        URI(url).takeIf { it.host != null }
    } catch (_: Exception) {
        null
    }

    private fun URI.trimmedPath(): String = path.orEmpty().trimEnd('/')

    private fun URI.hostName(): String = host.removePrefix("www.")
}
