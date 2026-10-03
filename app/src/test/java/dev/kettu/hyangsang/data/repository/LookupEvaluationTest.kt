package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.data.defaults.DefaultCategory
import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.local.dao.SenseWithExamples
import dev.kettu.hyangsang.data.local.entity.DictionaryEntry
import dev.kettu.hyangsang.data.local.entity.DictionaryExample
import dev.kettu.hyangsang.data.local.entity.DictionarySense
import dev.kettu.hyangsang.ui.reader.LOOKUP_TRIM_CHARS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Measures how often lookups show the right dictionary entry, using the cases in
 * `lookup-eval.tsv` against the bundled dictionary. Writes a per-case report to
 * `build/reports/lookup-eval.txt`, and fails if accuracy drops below [BASELINE_CORRECT], so
 * ranking changes can't make lookups worse unnoticed. Raise the baseline when they get better.
 */
class LookupEvaluationTest {

    private class Case(
        val category: String,
        val tapped: String,
        val expected: List<String>,
        val gloss: String,
        val sentence: String
    ) {
        val word = tapped.trim { it in LOOKUP_TRIM_CHARS }
        val expectedWords = expected.map { it.substringBefore('#') }.toSet()
    }

    private fun loadCases(): List<Case> {
        val lines = javaClass.classLoader!!.getResource("lookup-eval.tsv")!!.readText().lines()
        return lines
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .drop(1) // Header
            .map { line ->
                val (category, tapped, expected, gloss, sentence) = line.split('\t')
                Case(category, tapped, expected.split('|'), gloss, sentence)
            }
    }

    private fun DictionaryWithSenses.id() = "${entry.word}#${entry.homonymNumber}"

    @Test
    fun `lookups show the expected entries`() = runBlocking {
        val repository = DictionaryRepository(JdbcDictionaryDao(connection))
        val cases = loadCases()

        // Catch typos in the expected entries, which would otherwise just look like misses
        val unknown = cases.flatMap { it.expected }.filter { expected ->
            connection.prepareStatement(
                "SELECT 1 FROM dictionary_entries WHERE word = ? AND homonymNumber = ?"
            ).use {
                it.setString(1, expected.substringBefore('#'))
                it.setInt(2, expected.substringAfter('#').toInt())
                it.executeQuery().use { rows -> !rows.next() }
            }
        }
        assertTrue("Expected entries not in the dictionary: $unknown", unknown.isEmpty())
        val report = StringBuilder()
        var stemsRight = 0
        var entriesRight = 0
        var correct = 0

        for (case in cases) {
            val category = DefaultCategory.entries.firstOrNull {
                it.name == case.category.uppercase().replace(' ', '_')
            }
            val results = repository
                .getDefinitionsForWord(case.word, LookupContext(case.sentence, category))
                .first()

            // What the overlay opens on: the tapped word if it has entries, otherwise the first stem
            val shownStem = case.word.takeIf { !results[it].isNullOrEmpty() } ?: results.keys.firstOrNull()
            val shown = shownStem?.let { results[it]?.firstOrNull()?.id() }
            // Whether the expected entry comes first once its stem is picked
            val bestForExpectedStem = case.expectedWords.firstNotNullOfOrNull { results[it]?.firstOrNull()?.id() }

            val stemRight = shownStem in case.expectedWords
            val entryRight = bestForExpectedStem in case.expected
            val isCorrect = shown in case.expected
            if (stemRight) stemsRight++
            if (entryRight) entriesRight++
            if (isCorrect) correct++

            val mark = when {
                isCorrect -> "ok  "
                entryRight -> "stem"
                else -> "MISS"
            }
            report.appendLine(
                "$mark ${case.tapped} (${case.gloss}, ${case.category}): expected ${case.expected.joinToString("|")}, " +
                    "shown $shown, stems ${results.keys.joinToString(" ")}"
            )
        }

        val summary = "Correct $correct/${cases.size}, right stem shown $stemsRight/${cases.size}, " +
            "expected entry first within its stem $entriesRight/${cases.size}"
        File("build/reports").mkdirs()
        File("build/reports/lookup-eval.txt").writeText("$summary\n\n$report")
        println(summary)

        assertTrue(
            "$summary, below the baseline of $BASELINE_CORRECT. See build/reports/lookup-eval.txt",
            correct >= BASELINE_CORRECT
        )
    }

    /** Reads entries the way Room's [DictionaryWithSenses] relations would. */
    private class JdbcDictionaryDao(private val connection: Connection) : DictionaryDao {
        override fun getEntriesForTerms(words: List<String>): Flow<List<DictionaryWithSenses>> =
            entriesWhere("word", words)

        override fun getEntriesForOrigins(origins: List<String>): Flow<List<DictionaryWithSenses>> =
            entriesWhere("origin", origins)

        private fun entriesWhere(column: String, values: List<String>): Flow<List<DictionaryWithSenses>> {
            if (values.isEmpty()) return flowOf(emptyList())
            val placeholders = values.joinToString(",") { "?" }
            val entries = connection.prepareStatement(
                "SELECT * FROM dictionary_entries WHERE $column IN ($placeholders)"
            ).use { statement ->
                values.forEachIndexed { i, value -> statement.setString(i + 1, value) }
                statement.executeQuery().use { rows ->
                    generateSequence { if (rows.next()) rows else null }.map {
                        DictionaryEntry(
                            id = it.getLong("id"),
                            originalId = it.getString("originalId"),
                            word = it.getString("word"),
                            origin = it.getString("origin"),
                            homonymNumber = it.getInt("homonymNumber"),
                            partOfSpeech = it.getString("partOfSpeech"),
                            vocabularyLevel = it.getString("vocabularyLevel"),
                            semanticCategory = it.getString("semanticCategory"),
                            lexicalUnit = it.getString("lexicalUnit"),
                            pronunciation = it.getString("pronunciation"),
                            audioUrl = it.getString("audioUrl")
                        )
                    }.toList()
                }
            }
            return flowOf(entries.map { DictionaryWithSenses(it, senses(it.id)) })
        }

        private fun senses(entryId: Long): List<SenseWithExamples> =
            connection.prepareStatement("SELECT * FROM dictionary_senses WHERE entryId = ?").use { statement ->
                statement.setLong(1, entryId)
                statement.executeQuery().use { rows ->
                    generateSequence { if (rows.next()) rows else null }.map {
                        DictionarySense(
                            senseId = it.getLong("senseId"),
                            entryId = entryId,
                            definitionKo = it.getString("definitionKo"),
                            definitionEn = it.getString("definitionEn"),
                            translationEn = it.getString("translationEn")
                        )
                    }.toList()
                }
            }.map { SenseWithExamples(it, examples(it.senseId)) }

        private fun examples(senseId: Long): List<DictionaryExample> =
            connection.prepareStatement("SELECT * FROM dictionary_examples WHERE senseId = ?").use { statement ->
                statement.setLong(1, senseId)
                statement.executeQuery().use { rows ->
                    generateSequence { if (rows.next()) rows else null }.map {
                        DictionaryExample(
                            exampleId = it.getLong("exampleId"),
                            senseId = senseId,
                            example = it.getString("example"),
                            type = it.getString("type")
                        )
                    }.toList()
                }
            }

        override suspend fun insertEntry(entry: DictionaryEntry) = Unit
        override suspend fun insertSenses(senses: List<DictionarySense>) = Unit
        override suspend fun insertExamples(examples: List<DictionaryExample>) = Unit
        override fun getEntriesByWord(word: String): Flow<List<DictionaryEntry>> = flowOf(emptyList())
        override fun getSensesForEntry(entryId: Long): Flow<List<DictionarySense>> = flowOf(emptyList())
        override fun getFullEntriesByWord(word: String): Flow<List<DictionaryWithSenses>> = flowOf(emptyList())
    }

    companion object {
        // Correct cases in lookup-eval.tsv as of the last ranking change
        private const val BASELINE_CORRECT = 64

        private val DICTIONARY = File("src/main/assets/dictionary.db")
        private lateinit var copy: File
        private lateinit var connection: Connection

        @BeforeClass
        @JvmStatic
        fun openDictionary() {
            assumeTrue("The bundled dictionary is missing", DICTIONARY.exists())
            // Room adds the indexes on the device, so index a copy the same way. Without them
            // every lookup scans the senses and examples tables once per entry.
            copy = File.createTempFile("dictionary", ".db")
            DICTIONARY.copyTo(copy, overwrite = true)
            connection = DriverManager.getConnection("jdbc:sqlite:${copy.absolutePath}")
            connection.createStatement().use {
                it.execute("CREATE INDEX IF NOT EXISTS index_dictionary_entries_word ON dictionary_entries (word)")
                it.execute("CREATE INDEX IF NOT EXISTS index_dictionary_senses_entryId ON dictionary_senses (entryId)")
                it.execute("CREATE INDEX IF NOT EXISTS index_dictionary_examples_senseId ON dictionary_examples (senseId)")
            }
        }

        @AfterClass
        @JvmStatic
        fun closeDictionary() {
            if (::connection.isInitialized) connection.close()
            if (::copy.isInitialized) copy.delete()
        }
    }
}
