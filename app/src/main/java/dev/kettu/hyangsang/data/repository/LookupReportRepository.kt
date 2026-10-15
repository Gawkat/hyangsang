package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.defaults.DefaultCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A lookup the user marked as wrong. The first five fields match the columns of the lookup
 * evaluation sets (see LookupEvaluationTest), so a labelled report can be copied into one.
 *
 * @param expected the entry the user switched to before reporting, as `word#homonymNumber`, or
 *   null if they reported the entry the lookup showed
 * @param gloss the first translation of [expected]
 * @param shown the entry the lookup showed first, as `word#homonymNumber`
 * @param stems the terms the lookup searched for, as shown in the sheet's chips
 * @param tokens how Open Korean Text split the tapped word
 */
data class LookupReport(
    val category: DefaultCategory?,
    val tapped: String,
    val expected: String?,
    val gloss: String?,
    val sentence: String,
    val shown: String?,
    val stems: List<String>,
    val tokens: String,
    val note: String,
    val articleUrl: String
) {
    fun toTsvRow(): String = listOf(
        category?.evaluationLabel().orEmpty(),
        tapped,
        expected.orEmpty(),
        gloss.orEmpty(),
        sentence,
        shown.orEmpty(),
        stems.joinToString(" "),
        tokens,
        note,
        articleUrl
    ).joinToString("\t") { it.replace(WHITESPACE, " ").trim() }

    companion object {
        val TSV_HEADER = listOf(
            "category", "tapped", "expected", "gloss", "sentence", "shown", "stems", "tokens", "note", "article"
        ).joinToString("\t")

        private val WHITESPACE = Regex("\\s+")

        // How the evaluation sets write categories: NORTH_KOREA -> North Korea
        private fun DefaultCategory.evaluationLabel() =
            name.split('_').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
    }
}

/** Lookup reports, kept as tab-separated rows in [file] until the user shares or deletes them. */
class LookupReportRepository(private val file: File) {
    private val mutex = Mutex()
    private val count = MutableStateFlow(0)

    /** The number of saved reports, read from the file when first collected. */
    val reportCount: SharedFlow<Int> = count.onSubscription {
        withContext(Dispatchers.IO) { mutex.withLock { count.value = readRows().size } }
    }

    suspend fun add(report: LookupReport) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.appendText(report.toTsvRow() + "\n")
            count.value = readRows().size
        }
    }

    /** All reports with a header row, or null if there are none. */
    suspend fun export(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val rows = readRows()
            if (rows.isEmpty()) null else (listOf(LookupReport.TSV_HEADER) + rows).joinToString("\n")
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.delete()
            count.value = 0
        }
    }

    private fun readRows(): List<String> =
        if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()
}
