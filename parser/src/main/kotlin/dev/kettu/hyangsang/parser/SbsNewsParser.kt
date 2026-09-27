package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

class SbsNewsParser : ContentsParser {
    companion object {
        // Captions written into the body as a grey centred line, e.g. "▲ DMZ 인근에서 이동 중인 수리온 헬기"
        private const val CAPTION_MARKER = "▲"
    }

    override fun extractContents(document: Document): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()

        // Byline
        val byline = document.select("a[class=reporter]").firstOrNull()
        if (byline != null) {
            val text = byline.text().trim()
            if (text.isNotEmpty()) {
                blocks.add(ContentBlock.Dateline(text))
            }
        }

        // Lead image. Its alt is the headline, so the caption (if any) comes from the body
        document.select("div.w_article_mainimg img").firstOrNull()
            ?.toImageBlock(caption = null)
            ?.let { blocks.add(it) }

        // Main article: plain text nodes separated by <br>s, with images and captions in between
        val textArea = document.select("div.main_text > div.text_area").firstOrNull()
        if (textArea != null) {
            BodyWalker(blocks).walk(textArea)
        }

        // Footer + copyright
        val footer = document.select("div[class=copyrightsbs]").firstOrNull()
        if (footer != null) {
            val text = footer.text().trim()
            if (text.isNotEmpty()) {
                blocks.add(ContentBlock.Footer(text))
            }
        }

        return blocks
    }

    private class BodyWalker(private val blocks: MutableList<ContentBlock>) {
        private val sb = StringBuilder()
        private val spans = mutableListOf<ContentSpan>()
        private var pendingBreaks = 0

        fun walk(container: Element) {
            walkChildren(container)
            flush()
        }

        private fun walkChildren(element: Element) {
            for (node in element.childNodes()) {
                visit(node)
            }
        }

        private fun visit(node: Node) {
            when (node) {
                is TextNode -> appendText(node.text())
                is Element -> visitElement(node)
            }
        }

        private fun visitElement(element: Element) {
            when {
                element.tagName() == "br" -> pendingBreaks++

                element.tagName() in setOf("script", "style", "iframe", "button") -> Unit

                // Filled in by page JS from the image's alt; only present in pre-rendered HTML
                element.hasClass("img_desc") -> {
                    val caption = element.text().trim()
                    val last = blocks.lastOrNull()
                    if (last is ContentBlock.Image && caption.isNotEmpty()) {
                        blocks[blocks.lastIndex] = last.copy(caption = caption)
                    }
                }

                element.hasClass("w_artcle_conimg") || element.hasClass("article_image") -> {
                    flush()
                    element.select("img").firstOrNull()?.let { img ->
                        val caption = img.attr("alt").trim()
                            .takeIf { img.attr("data-captionyn") == "Y" && it.isNotEmpty() }
                        img.toImageBlock(caption)?.let { blocks.add(it) }
                    }
                }

                isCaptionLine(element) -> {
                    flush()
                    val (text, captionSpans) = element.extractTextAndSpans()
                    val last = blocks.lastOrNull()
                    if (last is ContentBlock.Image && last.caption == null) {
                        blocks[blocks.lastIndex] =
                            last.copy(caption = text.removePrefix(CAPTION_MARKER).trim())
                    } else if (text.isNotBlank()) {
                        blocks.add(ContentBlock.Text(text, captionSpans))
                    }
                }

                element.isBlock -> {
                    flush()
                    walkChildren(element)
                    flush()
                }

                else -> {
                    val type = when (element.tagName()) {
                        "b", "strong" -> SpanType.BOLD
                        "i", "em" -> SpanType.ITALIC
                        else -> null
                    }
                    // Settle any preceding <br>s first, so a flush can't shift the span start
                    if (type != null && element.text().isNotBlank()) resolveBreaks()
                    val start = sb.length
                    walkChildren(element)
                    if (type != null && sb.length > start) {
                        spans.add(ContentSpan(start, sb.length, type))
                    }
                }
            }
        }

        private fun isCaptionLine(element: Element): Boolean =
            element.tagName() == "div" && element.text().trim().startsWith(CAPTION_MARKER) &&
                element.select("span[style*=color]").isNotEmpty()

        private fun appendText(text: String) {
            if (text.isBlank()) {
                if (sb.isNotEmpty() && pendingBreaks == 0) appendSpace()
                return
            }
            resolveBreaks()
            if (text.first().isWhitespace() && sb.isNotEmpty() && sb.last() != '\n') appendSpace()
            sb.append(text.trim())
            if (text.last().isWhitespace()) appendSpace()
        }

        // One <br> is a line break within a paragraph, two or more end the paragraph
        private fun resolveBreaks() {
            when {
                pendingBreaks >= 2 -> flush()
                pendingBreaks == 1 && sb.isNotBlank() -> {
                    trimTrailingSpace()
                    sb.append('\n')
                }
            }
            pendingBreaks = 0
        }

        private fun appendSpace() {
            if (sb.isNotEmpty() && !sb.last().isWhitespace()) sb.append(' ')
        }

        private fun trimTrailingSpace() {
            while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
        }

        private fun flush() {
            pendingBreaks = 0
            val leading = sb.length - sb.trimStart().length
            val text = sb.trim().toString()
            if (text.isNotEmpty()) {
                val adjusted = spans.mapNotNull {
                    val start = (it.start - leading).coerceIn(0, text.length)
                    val end = (it.end - leading).coerceIn(0, text.length)
                    if (end > start) it.copy(start = start, end = end) else null
                }
                blocks.add(ContentBlock.Text(text, adjusted))
            }
            sb.clear()
            spans.clear()
        }
    }
}

// Body images are lazy-loaded: src is a placeholder and the real, protocol-relative URL is in data-src
private fun Element.toImageBlock(caption: String?): ContentBlock.Image? {
    val raw = attr("data-src").ifBlank { attr("src") }.trim()
    val url = when {
        raw.isEmpty() -> return null
        raw.startsWith("//") -> "https:$raw"
        else -> absUrl(if (hasAttr("data-src")) "data-src" else "src").ifEmpty { raw }
    }
    return ContentBlock.Image(
        url = url,
        caption = caption,
        width = attr("v_width").toIntOrNull()?.takeIf { it > 0 },
        height = attr("v_height").toIntOrNull()?.takeIf { it > 0 }
    )
}
