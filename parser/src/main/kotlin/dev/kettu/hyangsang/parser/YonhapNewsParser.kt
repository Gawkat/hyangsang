package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class YonhapNewsParser : ContentsParser {
    override fun extractContents(document: Document): String {
        document.select("aside, figure, script, meta").remove()

        val main = document.select("div[class=story-news article]")

        val contentsBuilder = StringBuilder()
        main.traverse { node, _ ->
            if (node is Element && node.tagName() == "p") {
                contentsBuilder.append(node.text())
                contentsBuilder.append("\n\n")
            }
        }

        return contentsBuilder.trim().toString()
    }
}