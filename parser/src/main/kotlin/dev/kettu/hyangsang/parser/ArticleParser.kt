package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import java.net.URI

class ArticleParser {
    private val defaultParser = GenericContentsParser()

    private val parsers = mapOf(
        "feeds.bbci.co.uk" to BbcNewsParser(),
        "bbc.com" to BbcNewsParser(),
        "yna.co.kr" to YonhapNewsParser()
    )

    fun parse(url: String, document: Document): List<ContentBlock> {
        val host = try {
            URI(url).host.removePrefix("www.")
        } catch (_: Exception) {
            ""
        }

        val strategy = parsers[host] ?: defaultParser
        return strategy.extractContents(document)
    }
}