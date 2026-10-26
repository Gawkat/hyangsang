package dev.kettu.hyangsang.tools.canary

import dev.kettu.hyangsang.parser.ContentBlock
import dev.kettu.hyangsang.parser.ContentSpan

/*
 * Checks of a parsed article that need no known answer for it, so they work on articles nobody
 * has looked at. Each finds a way parsers break when a site changes its pages: the body is lost,
 * page furniture gets into it, or the text is mangled.
 */

private const val MIN_HANGUL = 50

// The lead is matched a sentence at a time, since some feeds join sentences from all over the
// article, and only by the start of each, since descriptions are cut short or edited, such as
// "어제" for the text's "어제(26일)"
private val sentenceEnd = Regex("""(?<=[.!?。…])\s+""")
private const val MIN_SENTENCE_LETTERS = 15
private const val SENTENCE_PREFIX_LETTERS = 20

private const val MIN_DUPLICATE_LENGTH = 30

private val leftoverPatterns = listOf(
    Regex("""</?[a-zA-Z][a-zA-Z0-9]*[\s/>]"""),
    Regex("""&(nbsp|amp|lt|gt|quot|#\d+);"""),
    Regex("""\bfunction\s*\("""),
    Regex("""\b(window|document)\.[a-zA-Z]""")
)

// Copyright notes, which belong in a footer
private val boilerplatePatterns = listOf(
    Regex("""무단\s*전재"""),
    Regex("""재배포\s*금지"""),
    Regex("""저작권자"""),
    Regex("""[ⓒ©]"""),
    Regex("""Copyright""", RegexOption.IGNORE_CASE)
)

/**
 * Whether a sentence of the [leads], the descriptions the feed and the page give the article, is
 * in the parsed text. They're written from the text, so a parser that lost the body or read the
 * wrong part of the page won't have them. Null when the leads have no sentence long enough.
 */
fun leadFound(blocks: List<ContentBlock>, leads: List<String>): Boolean? {
    val body = blocks.joinToString("") { it.plainText() }.lettersOnly()
    val sentences = leads.flatMap { it.split(sentenceEnd) }
        .map { it.lettersOnly().take(SENTENCE_PREFIX_LETTERS) }
        .filter { it.length >= MIN_SENTENCE_LETTERS }
    if (sentences.isEmpty()) return null
    return sentences.any { it in body }
}

/**
 * Problems with the [blocks] parsed from an article titled [title], as short descriptions. A
 * short body is only a problem when [leadFound] didn't confirm it, as some articles are mostly
 * pictures, and a repeated paragraph only when it's repeated more often than in the [pageText],
 * the text of the whole page before parsing, as some guides repeat steps.
 */
fun contentProblems(
    blocks: List<ContentBlock>,
    title: String?,
    leadFound: Boolean? = null,
    pageText: String? = null
): List<String> {
    val problems = mutableListOf<String>()
    val bodyBlocks = blocks.filter { it is ContentBlock.Text || it is ContentBlock.Heading }

    val hangul = bodyBlocks.sumOf { block -> block.plainText().count { it in '가'..'힣' } }
    if (hangul < MIN_HANGUL && leadFound != true) problems.add("only $hangul Hangul letters in the body")

    for (block in blocks) {
        val text = block.plainText()
        leftoverPatterns.firstOrNull { it.containsMatchIn(text) }?.let { pattern ->
            problems.add("markup or script left in the text: \"${pattern.find(text)!!.value.trim()}\" in ${block.excerpt()}")
        }
    }
    for (block in bodyBlocks) {
        boilerplatePatterns.firstOrNull { it.containsMatchIn(block.plainText()) }?.let {
            problems.add("copyright note in the body: ${block.excerpt()}")
        }
    }

    val page = pageText?.singleSpaced()
    bodyBlocks.filterIsInstance<ContentBlock.Text>()
        .map { it.text.singleSpaced() }
        .filter { it.length >= MIN_DUPLICATE_LENGTH }
        .groupingBy { it }.eachCount()
        .filter { (text, count) ->
            count > 1 && (page == null || count > Regex(Regex.escape(text)).findAll(page).count())
        }
        .keys.forEach { problems.add("repeated paragraph: ${excerpt(it)}") }

    val first = blocks.firstOrNull { it !is ContentBlock.Dateline }
    if (!title.isNullOrBlank() && first != null && first !is ContentBlock.Image &&
        first.plainText().lettersOnly() == title.lettersOnly()
    ) {
        problems.add("the title is repeated at the start of the body")
    }

    for (block in blocks) {
        when (block) {
            is ContentBlock.Text -> if (!block.spans.inBounds(block.text)) {
                problems.add("span out of bounds in ${block.excerpt()}")
            }
            is ContentBlock.Image -> {
                if (!block.url.startsWith("https://") && !block.url.startsWith("http://")) {
                    problems.add("image without an absolute URL: ${block.url.take(80)}")
                }
                if (!block.captionSpans.inBounds(block.caption.orEmpty())) {
                    problems.add("caption span out of bounds in ${block.excerpt()}")
                }
            }
            else -> Unit
        }
    }
    return problems
}

private fun List<ContentSpan>.inBounds(text: String): Boolean =
    all { it.start >= 0 && it.end <= text.length && it.start < it.end }

private fun ContentBlock.plainText(): String = when (this) {
    is ContentBlock.Text -> text
    is ContentBlock.Heading -> text
    is ContentBlock.Dateline -> text
    is ContentBlock.Footer -> text
    is ContentBlock.Image -> caption.orEmpty()
    ContentBlock.Divider, ContentBlock.Break -> ""
}

private fun ContentBlock.excerpt(): String = excerpt(plainText())

private fun excerpt(text: String): String =
    "\"" + text.replace(Regex("""\s+"""), " ").let { if (it.length > 60) it.take(60) + "…" else it } + "\""

private fun String.singleSpaced(): String = replace(Regex("""\s+"""), " ").trim()

// Letters and digits only, so differences in spacing and punctuation don't matter
private fun String.lettersOnly(): String = filter { it.isLetterOrDigit() }
