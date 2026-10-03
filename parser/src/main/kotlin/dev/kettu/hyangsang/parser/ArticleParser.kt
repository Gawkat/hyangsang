package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import java.net.URI

class ArticleParser {
    companion object {
        // Raise when a parser change should reach articles already stored. Older ones are
        // parsed again the next time they're opened
        const val VERSION = 2
    }

    private val defaultParser = GenericContentsParser()

    private val parsers = mapOf(
        "feeds.bbci.co.uk" to BbcNewsParser(),
        "bbc.com" to BbcNewsParser(),
        "yna.co.kr" to YonhapNewsParser(),
        "news.sbs.co.kr" to SbsNewsParser()
    )

    fun parse(url: String, document: Document, title: String? = null): List<ContentBlock> {
        val host = try {
            URI(url).host.removePrefix("www.")
        } catch (_: Exception) {
            ""
        }

        val strategy = parsers[host] ?: defaultParser
        return strategy.extractContents(document, title)
    }
}