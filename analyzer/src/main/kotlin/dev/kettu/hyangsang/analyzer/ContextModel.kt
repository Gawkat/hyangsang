package dev.kettu.hyangsang.analyzer

import kotlin.math.ln
import kotlin.math.min

/**
 * Counts for choosing between analyses, made from a treebank with gold morphemes:
 * - `prev`: how often each class of content word (N, V, A, X, ADV, DET, or - for none) follows
 *   what the previous word ends in (을 -> V)
 * - `surface`: how often each surface form has each class (위해 -> V)
 * - `lemma`: how often each surface form has each content headword (사는 -> 살다)
 * - `ending`: how often each ending occurs, with 아/어 and 았/었 forms merged
 *
 * The data is tab-separated: the table, the condition (- for `ending`), the value and the count.
 */
class ContextModel(
    lines: Sequence<String>,
    private val previousWeight: Float = PREVIOUS_WEIGHT,
    private val surfaceWeight: Float = SURFACE_WEIGHT,
    private val lemmaWeight: Float = LEMMA_WEIGHT,
    private val endingWeight: Float = ENDING_WEIGHT
) {
    private val tables = HashMap<String, HashMap<String, HashMap<String, Int>>>()
    private val endingTotal: Int

    init {
        for (line in lines) {
            val cols = line.split('\t')
            if (cols.size != 4) continue
            tables.getOrPut(cols[0]) { HashMap() }.getOrPut(cols[1]) { HashMap() }.merge(cols[2], cols[3].toInt(), Int::plus)
        }
        endingTotal = tables["ending"]?.get("-")?.values?.sum() ?: 0
    }

    /**
     * Cost of [analysis] of a word written [surface], after a word ending in [previous], which
     * is null when not known.
     */
    fun cost(previous: String?, surface: String, analysis: Analysis): Float {
        var cost = 0f
        val cls = analysis.contentClass
        previous?.let { tables["prev"]?.get(it) }?.let { cost -= previousWeight * logShare(it, cls, CLASSES.size) }
        tables["surface"]?.get(surface)?.takeIf { it.values.sum() >= MIN_SURFACE_COUNT }?.let {
            cost -= surfaceWeight * logShare(it, cls, CLASSES.size)
        }
        tables["lemma"]?.get(surface)?.takeIf { it.values.sum() >= MIN_SURFACE_COUNT }?.let { counts ->
            val lemma = analysis.content?.headword
            if (lemma != null) cost -= lemmaWeight * logShare(counts, lemma, counts.size + 1)
        }
        tables["ending"]?.get("-")?.let { counts ->
            for (ending in analysis.endings.flatMap { it.parts }) {
                val share = ((counts[normalizeEnding(ending)] ?: 0) + SMOOTHING) / endingTotal
                // Relative to a common ending, so analyses aren't charged for having endings
                cost += endingWeight * min(MAX_ENDING_COST, -ln(share).toFloat() - COMMON_ENDING)
            }
        }
        return cost
    }

    private fun logShare(counts: Map<String, Int>, value: String, values: Int): Float =
        ln(((counts[value] ?: 0) + SMOOTHING) / (counts.values.sum() + SMOOTHING * values)).toFloat()

    companion object {
        val CLASSES = listOf("N", "V", "A", "X", "ADV", "DET", "-")
        const val PREVIOUS_WEIGHT = 0.1f
        const val SURFACE_WEIGHT = 0.5f
        const val LEMMA_WEIGHT = 0.3f
        const val ENDING_WEIGHT = 0.15f
        const val MIN_SURFACE_COUNT = 2
        const val SMOOTHING = 0.5
        const val MAX_ENDING_COST = 6f

        // -ln of the share of a common ending, about 3%
        const val COMMON_ENDING = 3.5f

        /**
         * The counts bundled with the analyzer, `context-counts.tsv`, made from the UD Korean
         * training sets.
         */
        fun bundled(): ContextModel {
            val stream = ContextModel::class.java.getResourceAsStream("context-counts.tsv")
                ?: error("context-counts.tsv is missing from the analyzer's resources")
            return stream.bufferedReader().useLines { ContextModel(it) }
        }

        /** An ending with its 아/어 and 여 forms merged, as treebanks and analyzers differ there. */
        fun normalizeEnding(form: String): String = when {
            form.startsWith("았") || form.startsWith("였") -> "었" + form.drop(1)
            form.startsWith("아") || form.startsWith("여") -> "어" + form.drop(1)
            else -> form
        }
    }
}
