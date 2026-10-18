package dev.kettu.hyangsang.tools.counts

import dev.kettu.hyangsang.analyzer.ContextModel
import dev.kettu.hyangsang.analyzer.DictionaryRow
import dev.kettu.hyangsang.analyzer.Lexicon
import dev.kettu.hyangsang.analyzer.LookupAnalyzer
import java.io.File
import java.sql.DriverManager

/*
 * Builds the analyzer's context counts (see ContextModel) from the gold morphemes of the UD
 * Korean GSD and Kaist treebanks' training sets, *-ud-train.conllu in the input folder.
 *
 * The counts for a surface form only change how the analyzer ranks a word if it has analyses of
 * different classes or headwords, so the others are left out, using the bundled dictionary's
 * analyses. That keeps the file small but ties it to the dictionary: regenerate it when the
 * dictionary or the analyzer's candidates change.
 *
 * Args: [input folder] [output file], relative to the repository root
 */

private val DICTIONARY = File("app/src/main/assets/dictionary.db")

fun main(args: Array<String>) {
    val input = File(args.getOrElse(0) { "tools/analyzer-counts/data" })
    val output = File(args.getOrElse(1) { "analyzer/src/main/resources/dev/kettu/hyangsang/analyzer/context-counts.tsv" })
    val files = input.listFiles { f -> f.name.endsWith("-ud-train.conllu") }.orEmpty().sorted()
    require(files.isNotEmpty()) { "No *-ud-train.conllu files in $input" }
    require(DICTIONARY.exists()) { "The bundled dictionary is missing: $DICTIONARY" }

    val counts = Counts()
    for (file in files) {
        val sentences = readSentences(file)
        println("${file.name}: ${sentences.size} sentences")
        sentences.forEach(counts::add)
    }

    val analyzer = LookupAnalyzer(Lexicon(dictionaryRows()))
    val lines = counts.lines { surface, table ->
        val analyses = analyzer.analyze(surface, limit = Int.MAX_VALUE)
        when (table) {
            "surface" -> analyses.map { it.contentClass }.distinct().size > 1
            else -> analyses.map { it.content?.headword }.distinct().size > 1
        }
    }
    output.parentFile.mkdirs()
    output.writeText(lines.joinToString("\n", postfix = "\n"))
    println("Wrote ${lines.size} counts to $output")
}

private fun dictionaryRows(): List<DictionaryRow> =
    DriverManager.getConnection("jdbc:sqlite:${DICTIONARY.absolutePath}").use { conn ->
        conn.createStatement().executeQuery(
            "SELECT word, partOfSpeech, vocabularyLevel, lexicalUnit FROM dictionary_entries"
        ).use { rows ->
            generateSequence {
                if (rows.next()) DictionaryRow(rows.getString(1), rows.getString(2), rows.getString(3), rows.getString(4)) else null
            }.toList()
        }
    }

class Morph(val form: String, val tag: String)

class Word(val surface: String, val morphs: List<Morph>)

/** The sentences of a CoNLL-U file, as words with their gold morphemes. */
fun readSentences(file: File): List<List<Word>> {
    val result = mutableListOf<List<Word>>()
    var words = mutableListOf<Word>()
    file.forEachLine { line ->
        when {
            line.isBlank() -> {
                if (words.isNotEmpty()) result += words
                words = mutableListOf()
            }
            line.startsWith("#") -> Unit
            else -> {
                val cols = line.split("\t")
                // Multiword tokens and empty nodes
                if (cols[0].contains('-') || cols[0].contains('.')) return@forEachLine
                val forms = cols[2].split("+")
                val tags = cols[4].split("+")
                // A literal + in a form makes the split ambiguous, so such words get no morphemes
                val morphs = if (forms.size == tags.size) forms.zip(tags) { f, t -> Morph(f, t) } else emptyList()
                words += Word(cols[1], morphs)
            }
        }
    }
    if (words.isNotEmpty()) result += words
    return result
}

class Counts {
    private val byPrevious = HashMap<Pair<String, String>, Int>()
    private val bySurface = HashMap<String, HashMap<String, Int>>()
    private val byLemma = HashMap<String, HashMap<String, Int>>()
    private val endings = HashMap<String, Int>()

    fun add(sentence: List<Word>) {
        // The analyzer has no class for the start of a sentence, so the first word isn't counted
        var previous: String? = null
        for (word in sentence) {
            if (word.morphs.isEmpty()) {
                previous = "-"
                continue
            }
            val content = goldContent(word)
            val cls = content?.cls ?: "-"
            // As the analyzer trims the tapped word
            val surface = word.surface.trim { !it.isLetterOrDigit() }
            previous?.let { byPrevious.merge(it to cls, 1, Int::plus) }
            if (surface.isNotEmpty()) {
                bySurface.getOrPut(surface) { HashMap() }.merge(cls, 1, Int::plus)
                content?.let { byLemma.getOrPut(surface) { HashMap() }.merge(it.lemma, 1, Int::plus) }
            }
            word.morphs.filter { goldClass(it.tag) == "E" }.forEach {
                endings.merge(ContextModel.normalizeEnding(it.form), 1, Int::plus)
            }
            previous = goldLastClass(word)
        }
    }

    /**
     * The counts as ContextModel reads them, sorted. Surface forms seen fewer times than
     * ContextModel uses are left out, and so are those [keep] rejects for a table.
     */
    fun lines(keep: (surface: String, table: String) -> Boolean): List<String> {
        fun table(name: String, counts: Map<String, Map<String, Int>>) = counts
            .filter { (surface, values) -> values.values.sum() >= ContextModel.MIN_SURFACE_COUNT && keep(surface, name) }
            .flatMap { (surface, values) -> values.map { (value, n) -> "$name\t$surface\t$value\t$n" } }
            .sorted()
        return byPrevious.map { (k, n) -> "prev\t${k.first}\t${k.second}\t$n" }.sorted() +
            table("surface", bySurface) +
            table("lemma", byLemma) +
            endings.map { (k, n) -> "ending\t-\t$k\t$n" }.sorted()
    }
}

/** The gold content word of a word: its headword and class, as Analysis.contentClass has them. */
class Gold(val lemma: String, val cls: String)

fun goldContent(word: Word): Gold? {
    val morphs = word.morphs
    val index = morphs.indexOfFirst { goldClass(it.tag) in CONTENT_CLASSES || goldClass(it.tag) == "XR" }
    if (index < 0) return null
    val m = morphs[index]
    val next = morphs.getOrNull(index + 1)
    val cls = goldClass(m.tag)
    val nextCls = next?.let { goldClass(it.tag) }
    return when {
        (cls == "N" || cls == "XR") && (nextCls == "XSV" || nextCls == "XSA") ->
            Gold(m.form + next!!.form + "다", if (nextCls == "XSV") "V" else "A")
        cls == "XR" -> null
        cls == "V" || cls == "A" || cls == "X" -> Gold(m.form + "다", cls)
        else -> Gold(m.form, cls)
    }
}

/** What a gold word ends in, as Analysis.lastClass has it. */
fun goldLastClass(word: Word): String {
    val tag = word.morphs.lastOrNull { !it.tag.startsWith("S") && !it.tag.startsWith("s") }?.tag ?: return "-"
    return when {
        tag == "JKO" || tag == "jco" -> "JKO"
        tag == "JKS" || tag == "JKC" || tag == "jcs" || tag == "jcc" -> "JKS"
        tag == "JKG" || tag == "jcm" -> "JKG"
        tag == "JX" || tag.startsWith("jx") -> "JX"
        tag.startsWith("JK") || tag == "JC" || tag.startsWith("jc") -> "JKB"
        tag == "ETM" || tag == "etm" -> "ETM"
        tag == "ETN" || tag == "etn" -> "ETN"
        tag == "EF" || tag == "ef" -> "EF"
        tag.startsWith("E") || tag.startsWith("e") -> "EC"
        tag == "MAG" || tag == "MAJ" || tag == "IC" || tag.startsWith("ma") || tag == "ii" -> "ADV"
        tag == "MM" || tag.startsWith("mm") -> "DET"
        tag.startsWith("V") || tag.startsWith("p") || tag == "XSV" || tag == "XSA" || tag == "xsv" || tag.startsWith("xsm") -> "EC"
        else -> "N"
    }
}

private val CONTENT_CLASSES = setOf("N", "V", "A", "X", "ADV", "DET")

/** Coarse class of a gold tag, from either the Sejong (GSD) or KAIST (lowercase) tag set. */
fun goldClass(tag: String): String = when {
    // Sejong
    tag.matches(Regex("NN[GPB]|NP|NR")) -> "N"
    tag == "VV" -> "V"
    tag == "VA" -> "A"
    tag == "VX" -> "X"
    tag == "VCN" -> "A"
    tag == "VCP" -> "COP"
    tag == "MAG" || tag == "MAJ" -> "ADV"
    tag == "MM" -> "DET"
    tag.matches(Regex("E[CFP]|ETM|ETN")) -> "E"
    tag == "XSV" -> "XSV"
    tag == "XSA" -> "XSA"
    tag == "XR" -> "XR"
    // KAIST
    tag.matches(Regex("n[cqbpn].*")) -> "N"
    tag.startsWith("pv") -> "V"
    tag.startsWith("pa") -> "A"
    tag == "px" -> "X"
    tag.startsWith("ma") -> "ADV"
    tag.startsWith("mm") -> "DET"
    tag.matches(Regex("e[pcft].*")) -> "E"
    tag == "jp" -> "COP"
    tag == "xsv" -> "XSV"
    tag.startsWith("xsm") -> "XSA"
    else -> tag
}
