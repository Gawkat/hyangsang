package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import java.net.URI

class ArticleParser {
    companion object {
        // Raise when a parser change should reach articles already stored. Older ones are
        // parsed again the next time they're opened
        const val VERSION = 2

        /**
         * The page to download for a [link] from a feed, or null when it doesn't lead to an
         * article. Some feeds also list pages without text, or pages that are broken.
         */
        fun articleUrl(link: String): String? = when (hostOf(link)) {
            EasyLawParser.HOST -> EasyLawParser.articleUrl(link)
            else -> link
        }

        private fun hostOf(url: String): String = try {
            URI(url).host.removePrefix("www.")
        } catch (_: Exception) {
            ""
        }
    }

    private val defaultParser = GenericContentsParser()
    private val ndSoftParser = NdSoftNewsParser()

    private val parsers = mapOf(
        "feeds.bbci.co.uk" to BbcNewsParser(),
        "bbc.com" to BbcNewsParser(),
        "yna.co.kr" to YonhapNewsParser(),
        "news.sbs.co.kr" to SbsNewsParser(),
        "hani.co.kr" to HaniNewsParser(),
        EasyLawParser.HOST to EasyLawParser(),
        SlowNewsParser.HOST to SlowNewsParser(),
        DongaNewsParser.HOST to DongaNewsParser()
    )

    fun parse(url: String, document: Document, title: String? = null): List<ContentBlock> {
        val host = hostOf(url)

        // Sites on ND Soft's CMS are recognised by the page rather than listed, as there are many
        val strategy = parsers[host]
            ?: ndSoftParser.takeIf { document.getElementById(NdSoftNewsParser.BODY_ID) != null }
            ?: defaultParser
        return strategy.extractContents(document, title)
    }
}