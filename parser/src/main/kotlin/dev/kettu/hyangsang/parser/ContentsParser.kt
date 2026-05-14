package dev.kettu.hyangsang.parser

import org.jsoup.nodes.Document

interface ContentsParser {
    fun extractContents(document: Document): String
}