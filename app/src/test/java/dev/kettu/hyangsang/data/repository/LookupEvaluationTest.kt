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
 * Measures how often lookups show the right dictionary entry, using sets of cases against the
 * bundled dictionary. Writes a per-case report for each set to `build/reports`, and fails if a
 * set's accuracy drops below its baseline, so ranking changes can't make lookups worse
 * unnoticed. Raise the baselines when they get better.
 *
 * The sets:
 * - `lookup-eval-krdict-dev.tsv` and `lookup-eval-krdict-test.tsv`: example sentences from the
 *   dictionary itself, made by the dictionary generator's `generateEvalSet` task. Work on ranking
 *   changes against the dev set, and use the test set only to confirm them.
 * - `lookup-eval.tsv`: sentences from real news articles. It isn't committed, so its test is
 *   skipped without it. Only this set has feed categories.
 *
 * The files are tab-separated, with `#` comment lines and a header row, and these columns: the
 * feed category (empty for none), the word as tapped, the expected entries as
 * `word#homonymNumber` with `|` between equally right ones, a gloss of the intended meaning for
 * people reading the file, and the sentence the word was tapped in.
 *
 * Each case's sentence is left out of the dictionary's examples while ranking, since an example
 * sentence would otherwise match its own entry.
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

    private fun loadCases(name: String): List<Case> {
        val text = javaClass.classLoader!!.getResource("$name.tsv")?.readText()
        assumeTrue("$name.tsv not found in src/test/resources, skipping", text != null)
        val lines = text!!.lines()
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
    fun `news lookups show the expected entries`() = evaluate("lookup-eval", BASELINE_NEWS)

    @Test
    fun `dictionary example lookups show the expected entries, dev set`() =
        evaluate("lookup-eval-krdict-dev", BASELINE_KRDICT_DEV)

    @Test
    fun `dictionary example lookups show the expected entries, test set`() =
        evaluate("lookup-eval-krdict-test", BASELINE_KRDICT_TEST)

    private fun evaluate(name: String, baseline: Int) = runBlocking {
        val dao = JdbcDictionaryDao(connection)
        val repository = DictionaryRepository(dao)
        val cases = loadCases(name)

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
            dao.excludedExample = case.sentence
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
        File("build/reports/$name.txt").writeText("$summary\n\n$report")
        println("$name: $summary")

        assertTrue(
            "$summary, below the baseline of $baseline. See build/reports/$name.txt",
            correct >= baseline
        )
    }

    /** Reads entries the way Room's [DictionaryWithSenses] relations would. */
    private class JdbcDictionaryDao(private val connection: Connection) : DictionaryDao {
        /** An example sentence to leave out, compared ignoring differences in whitespace */
        var excludedExample: String? = null
            set(value) {
                field = value?.normalized()
            }

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
                    }.filter { it.example.normalized() != excludedExample }.toList()
                }
            }

        private fun String.normalized() = replace(Regex("\\s+"), " ").trim()

        override suspend fun insertEntry(entry: DictionaryEntry) = Unit
        override suspend fun insertSenses(senses: List<DictionarySense>) = Unit
        override suspend fun insertExamples(examples: List<DictionaryExample>) = Unit
        override fun getEntriesByWord(word: String): Flow<List<DictionaryEntry>> = flowOf(emptyList())
        override fun getSensesForEntry(entryId: Long): Flow<List<DictionarySense>> = flowOf(emptyList())
        override fun getFullEntriesByWord(word: String): Flow<List<DictionaryWithSenses>> = flowOf(emptyList())
    }

    companion object {
        // Correct cases in each set as of the last ranking change
        private const val BASELINE_NEWS = 64
        private const val BASELINE_KRDICT_DEV = 268
        private const val BASELINE_KRDICT_TEST = 274

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
