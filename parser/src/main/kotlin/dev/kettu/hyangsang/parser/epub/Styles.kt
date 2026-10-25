package dev.kettu.hyangsang.parser.epub

import org.jsoup.nodes.Element

// The only properties the parser reads
private val PROPERTIES = setOf("font-size", "font-style", "font-weight", "vertical-align")

private val COMBINATOR = Regex("[\\s>+~]+")
private val CLASS = Regex("\\.([\\w-]+)")

private val SIZE_KEYWORDS = mapOf(
    "xx-small" to 0.6f,
    "x-small" to 0.75f,
    "small" to 0.89f,
    "smaller" to 0.83f,
    "medium" to 1f,
    "larger" to 1.2f,
    "large" to 1.2f,
    "x-large" to 1.5f,
    "xx-large" to 2f
)

/**
 * The little of a book's CSS the parser uses, since some books mark italics, bold and footnotes
 * only with styles, such as raised numbers and notes in smaller type. A rule applies to the elements with all the
 * classes in the last part of its selector, whatever comes before that part.
 */
internal class Styles private constructor(private val rules: Map<String, List<Rule>>) {
    private class Rule(val classes: Set<String>, val order: Int, val declarations: Map<String, String>)

    /** Whether [element] is set above the line, as footnote markers are */
    fun isSuperscript(element: Element): Boolean =
        element.localName() == "sup" || value(element, "vertical-align") == "super"

    /** Whether [element]'s own styles set it in italics */
    fun isItalic(element: Element): Boolean = value(element, "font-style").let { it == "italic" || it == "oblique" }

    /** Whether [element]'s own styles set it in bold */
    fun isBold(element: Element): Boolean = value(element, "font-weight").let { weight ->
        weight == "bold" || weight == "bolder" || (weight?.toIntOrNull() ?: 0) >= 600
    }

    /** The value [element]'s own style or classes give [property], with the last rule winning */
    fun value(element: Element, property: String): String? {
        declarations(element.attr("style"))[property]?.let { return it }
        val classes = element.classNames()
        return classes.asSequence()
            .flatMap { rules[it].orEmpty() }
            .filter { property in it.declarations && classes.containsAll(it.classes) }
            .maxByOrNull { it.order }
            ?.declarations?.get(property)
    }

    companion object {
        val EMPTY = Styles(emptyMap())

        fun parse(css: String): Styles {
            val rules = HashMap<String, MutableList<Rule>>()
            var order = 0
            // Rules inside @media blocks are read as if they always applied
            for (chunk in withoutComments(css).split('}')) {
                val open = chunk.lastIndexOf('{')
                if (open < 0) continue
                val declarations = declarations(chunk.substring(open + 1))
                if (declarations.isEmpty()) continue
                val selectors = chunk.substring(0, open).substringAfterLast('{').substringAfterLast(';')
                for (selector in selectors.split(',')) {
                    val last = selector.trim().split(COMBINATOR).last()
                    // Pseudo-classes and pseudo-elements, which don't style the element as it is
                    if (':' in last) continue
                    val classes = CLASS.findAll(last).map { it.groupValues[1] }.toSet()
                    if (classes.isEmpty()) continue
                    val rule = Rule(classes, order++, declarations)
                    classes.forEach { rules.getOrPut(it) { mutableListOf() }.add(rule) }
                }
            }
            return Styles(rules)
        }

        // Without a regex, which could take quadratic time on a sheet of unclosed comments
        private fun withoutComments(css: String): String = buildString {
            var i = 0
            while (i < css.length) {
                val start = css.indexOf("/*", i)
                if (start < 0) {
                    append(css, i, css.length)
                    break
                }
                append(css, i, start)
                val end = css.indexOf("*/", start + 2)
                if (end < 0) break
                i = end + 2
            }
        }

        private fun declarations(block: String): Map<String, String> =
            block.split(';').mapNotNull { declaration ->
                val name = declaration.substringBefore(':', "").trim().lowercase()
                val value = declaration.substringAfter(':', "").substringBefore("!important").trim().lowercase()
                (name to value).takeIf { name in PROPERTIES && value.isNotEmpty() }
            }.toMap()
    }
}

/**
 * A font size relative to the text around it, such as 0.75 for 0.75em or 75%, or null for sizes
 * in absolute units, which can't be compared without knowing the size of that text
 */
internal fun relativeSize(value: String): Float? = when {
    value in SIZE_KEYWORDS -> SIZE_KEYWORDS[value]
    value.endsWith("em") -> value.removeSuffix("em").removeSuffix("r").toFloatOrNull()
    value.endsWith("%") -> value.removeSuffix("%").toFloatOrNull()?.div(100)
    else -> null
}
