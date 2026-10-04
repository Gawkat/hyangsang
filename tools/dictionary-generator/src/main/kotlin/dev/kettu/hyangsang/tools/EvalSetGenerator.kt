package dev.kettu.hyangsang.tools

import java.io.File
import java.sql.DriverManager
import kotlin.random.Random

/*
 * Builds the dictionary lookup evaluation sets, lookup-eval-krdict-dev.tsv and
 * lookup-eval-krdict-test.tsv, from the example sentences in the bundled dictionary. Each case is
 * an example sentence of a noun entry whose word has other common noun homonyms, so the lookup has
 * to pick the entry the example belongs to. LookupEvaluationTest leaves the case's sentence out
 * of the dictionary's examples while ranking, since it would otherwise match its own entry.
 *
 * The sets are committed, so rerun this only to change how cases are picked: the cases change
 * with the dictionary and the seed, and the test baselines have to be measured again.
 */

private val DICTIONARY = File("app/src/main/assets/dictionary.db")
private val OUTPUT_DIR = File("app/src/test/resources")

private const val SEED = 20261004
private const val EXAMPLES_PER_ENTRY = 2
private const val MIN_EOJEOL = 4

// Levels of the homonyms a learner is likely to meet. Most entries are Advanced, including many
// rare ones, so a word needs two homonyms at these levels to be a useful case.
private val COMMON_LEVELS = setOf("Beginner", "Intermediate")
private val NOUN_POS = setOf("Noun", "Bound Noun")

// Particles and copula forms that can follow the noun in the tapped 어절. Longer ones first, so
// the regex prefers them.
private val ENDINGS = listOf(
    "에서는", "에게는", "으로는", "이었다", "이에요", "입니다", "에서", "에게", "한테", "으로",
    "까지", "부터", "보다", "처럼", "이나", "이랑", "에는", "에도", "로는", "와는", "과는", "이다",
    "였다", "예요", "이야", "이", "가", "은", "는", "을", "를", "의", "에", "로", "와", "과", "도",
    "만", "나", "랑", "께", "야", "다"
)

// What the reader trims from a tapped word, see LOOKUP_TRIM_CHARS
private const val TRIM_CHARS = "!.?,"

private class Example(val word: String, val homonymNumber: Int, val gloss: String, val sentence: String)

fun main() {
    check(DICTIONARY.exists()) { "No dictionary at ${DICTIONARY.absolutePath}, generate it first" }

    val examples = DriverManager.getConnection("jdbc:sqlite:${DICTIONARY.absolutePath}").use { conn ->
        // Words with at least two common noun homonyms
        val words = conn.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT word FROM dictionary_entries
                WHERE partOfSpeech IN ('Noun', 'Bound Noun') AND vocabularyLevel IN ('Beginner', 'Intermediate')
                GROUP BY word HAVING COUNT(DISTINCT homonymNumber) >= 2
                """
            ).use { rows -> generateSequence { if (rows.next()) rows.getString(1) else null }.toSet() }
        }

        conn.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT e.word, e.homonymNumber, e.partOfSpeech, e.vocabularyLevel, s.translationEn,
                    s.definitionEn, x.example
                FROM dictionary_entries e
                JOIN dictionary_senses s ON s.entryId = e.id
                JOIN dictionary_examples x ON x.senseId = s.senseId
                WHERE x.type = '문장'
                ORDER BY e.word, e.homonymNumber, s.senseId, x.exampleId
                """
            ).use { rows ->
                generateSequence { if (rows.next()) rows else null }
                    .filter {
                        it.getString("word") in words && it.getString("partOfSpeech") in NOUN_POS &&
                            it.getString("vocabularyLevel") in COMMON_LEVELS
                    }
                    .map {
                        Example(
                            word = it.getString("word"),
                            homonymNumber = it.getInt("homonymNumber"),
                            gloss = (it.getString("translationEn") ?: it.getString("definitionEn") ?: "").clean(),
                            sentence = it.getString("example").clean()
                        )
                    }
                    .toList()
            }
        }
    }

    val random = Random(SEED)
    val cases = examples
        .mapNotNull { example -> tappedForm(example)?.let { example to it } }
        .groupBy { (example, _) -> example.word to example.homonymNumber }
        .values
        .flatMap { it.shuffled(random).take(EXAMPLES_PER_ENTRY) }
        .groupBy { (example, _) -> example.word }

    // Split by word, so tuning on the dev set can't fit the words in the test set
    val (devWords, testWords) = cases.keys.sorted().shuffled(random).let { it.chunked((it.size + 1) / 2) }
    write("dev", devWords.flatMap { cases.getValue(it) })
    write("test", testWords.flatMap { cases.getValue(it) })
}

/**
 * The 어절 the word is tapped in: the only one that is the word, optionally followed by a particle.
 * Null if no 어절 or several match, or the sentence is too short to give context.
 */
private fun tappedForm(example: Example): String? {
    val eojeol = example.sentence.split(' ')
    if (eojeol.size < MIN_EOJEOL) return null
    val pattern = Regex(Regex.escape(example.word) + "(?:" + ENDINGS.joinToString("|") + ")?")
    return eojeol.singleOrNull { pattern.matches(it.trim { c -> c in TRIM_CHARS }) }
}

private fun String.clean() = replace(Regex("\\s+"), " ").trim()

private fun write(name: String, cases: List<Pair<Example, String>>) {
    val file = File(OUTPUT_DIR, "lookup-eval-krdict-$name.tsv")
    file.bufferedWriter().use { out ->
        out.write(
            """
            # Dictionary lookup evaluation cases ($name set), generated by the dictionary generator's
            # generateEvalSet task from the example sentences of the National Institute of Korean
            # Language's Korean-English Learners' Dictionary (https://krdict.korean.go.kr), licensed
            # under CC BY-SA 2.0 KR. This file is distributed under the same license.
            # Columns are described in LookupEvaluationTest. The category is left empty, since the
            # sentences don't come from a feed.

            """.trimIndent()
        )
        out.write("category\ttapped\texpected\tgloss\tsentence\n")
        for ((example, tapped) in cases.sortedWith(compareBy({ it.first.word }, { it.first.homonymNumber }))) {
            out.write("\t$tapped\t${example.word}#${example.homonymNumber}\t${example.gloss}\t${example.sentence}\n")
        }
    }
    println("Wrote ${cases.size} cases to ${file.path}")
}
