package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

interface ContentsParser {
    fun extractContents(document: Document): List<ContentBlock>
}

fun Element.parseBlocks(): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    for (element in this.children()) {
        when (element.tagName()) {
            "h1", "h2", "h3", "h4" -> {
                val text = element.text().trim()
                if (text.isNotEmpty()) {
                    blocks.add(ContentBlock.Heading(text, element.tagName().last().digitToInt()))
                }
            }

            "p", "li", "blockquote" -> {
                val (text, spans) = element.extractTextAndSpans()
                if (text.isNotBlank()) {
                    blocks.add(ContentBlock.Text(text, spans))
                }
            }

            "img" -> {
                val url = element.absUrl("src")
                if (url.isNotEmpty() && !element.isTrackingPixel()) {
                    blocks.add(
                        ContentBlock.Image(
                            url = url,
                            caption = element.attr("alt").takeIf { it.isNotBlank() },
                            width = element.dimensionAttr("width"),
                            height = element.dimensionAttr("height")
                        )
                    )
                }
            }

            "figure" -> {
                val img = element.select("img").firstOrNull()
                val figcaption = element.select("figcaption").firstOrNull()

                val (captionText, captionSpans) = figcaption?.extractTextAndSpans()
                    ?: (img?.attr("alt") to emptyList())

                val url = img?.absUrl("src") ?: ""
                if (img != null && url.isNotEmpty() && !img.isTrackingPixel()) {
                    blocks.add(
                        ContentBlock.Image(
                            url = url,
                            caption = captionText?.takeIf { it.isNotBlank() },
                            captionSpans = captionSpans,
                            width = img.dimensionAttr("width"),
                            height = img.dimensionAttr("height")
                        )
                    )
                }
            }

            else -> {
                val nested = element.parseBlocks()
                blocks.addAll(nested)
            }
        }
    }
    return blocks
}

// Only plain pixel values are usable; things like "100%" are ignored
private fun Element.dimensionAttr(name: String): Int? =
    attr(name).trim().removeSuffix("px").toIntOrNull()?.takeIf { it > 0 }

private fun Element.isTrackingPixel(): Boolean =
    attr("width").trim() in setOf("0", "1") || attr("height").trim() in setOf("0", "1")

fun Element.extractTextAndSpans(): Pair<String, List<ContentSpan>> {
    val sb = StringBuilder()
    val spans = mutableListOf<ContentSpan>()

    fun traverse(node: Node) {
        when (node) {
            is TextNode -> {
                sb.append(node.text())
            }

            is Element -> {
                val start = sb.length
                val type = when (node.tagName()) {
                    "b", "strong" -> SpanType.BOLD
                    "i", "em" -> SpanType.ITALIC
                    else -> null
                }
                for (child in node.childNodes()) {
                    traverse(child)
                }
                if (type != null) {
                    val end = sb.length
                    if (end > start) {
                        spans.add(ContentSpan(start, end, type))
                    }
                }
            }
        }
    }

    for (child in this.childNodes()) {
        traverse(child)
    }
    return sb.toString().trim() to spans
}