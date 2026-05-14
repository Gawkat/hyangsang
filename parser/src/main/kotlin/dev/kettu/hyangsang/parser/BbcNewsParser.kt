package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BbcNewsParser : ContentsParser {
    override fun extractContents(document: Document): String {
        // Remove byline, figures, related articles, recommendations
        document.select("figure, section").remove()

        val main = document.select("main")

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
