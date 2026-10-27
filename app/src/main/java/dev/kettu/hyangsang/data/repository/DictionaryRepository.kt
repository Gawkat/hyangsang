package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.analyzer.Tag
import dev.kettu.hyangsang.data.local.dao.DictionaryDao
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * @param analyzer built on first use, which takes a while, so it's only used off the main thread
 */
class DictionaryRepository(
    private val dictionaryDao: DictionaryDao,
    private val analyzer: Lazy<KoreanAnalyzer>
) {
    /**
     * @param terms search terms, each with the tag of the token it came from, or null when
     *   unknown (such as the whole word when it spans several tokens)
     * @param mainTerm the term the word is read as, shown first: the word itself, unless the
     *   analyzer reads it as a form of another word (통해 as 통하다) or splits it (배가 as 배 +
     *   가)
     * @param compounds noun stems whose parts were added as terms
     * @param splitParts terms only worth showing when they're part of a compound's split into
     *   words: single syllables, and affixes as the dictionary writes them (-부, 외-)
     * @param affixFallbacks tokens that look like affixes, mapped to their affix form; the token
     *   itself is only shown if the dictionary doesn't have the affix
     * @param counters terms right after a number, like 명 in 3명 or 일 in 26일
     * @param numberWordCounters the counters after a number word, like 바퀴 in 두 바퀴
     * @param leadingNoun the noun the word starts with, like 중 in 중에서, if any
     * @param hanjaTerms the hanja runs and their parts, looked up by the entries' origin
     * @param titledHanja single hanja followed by a title, like 李 in 李대통령, which makes them
     *   family names
     * @param standaloneHanja single hanja with no Hangul attached but a particle (金, 北이), so
     *   not affixes
     * @param weekdayHanja standalone hanja in parentheses, as weekdays are written in dates
     *   (3일(金))
     */
    private class SearchTerms(
        val terms: Map<String, Tag?>,
        val mainTerm: String,
        val compounds: List<String>,
        val splitParts: Set<String>,
        val affixFallbacks: Map<String, String>,
        val counters: Set<String>,
        val numberWordCounters: Set<String> = emptySet(),
        val leadingNoun: String?,
        val hanjaTerms: List<String> = emptyList(),
        val titledHanja: Set<String> = emptySet(),
        val standaloneHanja: Set<String> = emptySet(),
        val weekdayHanja: Set<String> = emptySet()
    )

    /** The words around a tapped word in its sentence, when known. */
    private class Neighbors(val previous: String? = null, val next: String? = null)

    /**
     * News writes some words in hanja (北, 韓美), which are looked up by the entries' origin, and
     * the Hangul between them as words of their own.
     *
     * @param neighbors the words around [word] in its sentence. The next word tells whether hanja
     *   at the end of [word] are a family name before a title (韓 총리).
     */
    private fun getSearchTerms(word: String, neighbors: Neighbors = Neighbors()): SearchTerms {
        val nextWord = neighbors.next
        val hanjaRuns = Hanja.RUN.findAll(word).map { it.value }.toList()
        if (hanjaRuns.isEmpty()) return getHangulSearchTerms(word, neighbors)

        // Longest first, so a run's parts follow it (韓美日, 韓美, 美日, 韓, 美, 日)
        val hanjaTerms = LinkedHashSet<String>()
        for (run in hanjaRuns) {
            for (length in run.length downTo 1) hanjaTerms += run.windowed(length)
        }

        val terms = LinkedHashMap<String, Tag?>()
        terms[word] = null
        val parts = mutableListOf<SearchTerms>()
        var leadingNoun: String? = null
        val titledHanja = mutableSetOf<String>()
        val standaloneHanja = mutableSetOf<String>()
        val weekdayHanja = mutableSetOf<String>()
        // Every segment but the first follows a hanja run, hanjaRuns[index - 1]
        val segments = word.split(Hanja.RUN)
        segments.forEachIndexed { index, segment ->
            val text = segment.trim { !it.isLetterOrDigit() }
            val run = hanjaRuns.getOrNull(index - 1)
            // A title can also be the next word, but not after punctuation (韓, 美)
            val following = if (index == segments.lastIndex && segment.isEmpty()) nextWord else segment
            if (run != null && run.length == 1 && following != null && startsWithTitle(following)) {
                titledHanja += run
            }
            val previous = segments.getOrNull(index - 1)
            if (run != null && run.length == 1 && previous?.lastOrNull()?.isLetterOrDigit() != true &&
                (segment.firstOrNull()?.isLetterOrDigit() != true || text in PARTICLES)
            ) {
                standaloneHanja += run
                if (previous?.lastOrNull() == '(' && segment.firstOrNull() == ')') weekdayHanja += run
            }
            when {
                text.isEmpty() -> Unit
                index > 0 && text in PARTICLES -> terms.putIfAbsent(text, Tag.PARTICLE)
                else -> {
                    val part = getHangulSearchTerms(text)
                    if (index == 0) leadingNoun = part.leadingNoun
                    parts += part
                }
            }
        }
        parts.forEach { part -> part.terms.forEach { (term, pos) -> terms.putIfAbsent(term, pos) } }

        return SearchTerms(
            terms = terms,
            mainTerm = word,
            compounds = parts.flatMap { it.compounds },
            splitParts = parts.flatMap { it.splitParts }.toSet(),
            affixFallbacks = parts.fold(emptyMap()) { fallbacks, part -> fallbacks + part.affixFallbacks },
            counters = parts.flatMap { it.counters }.toSet(),
            leadingNoun = leadingNoun,
            hanjaTerms = hanjaTerms.toList(),
            titledHanja = titledHanja,
            standaloneHanja = standaloneHanja,
            weekdayHanja = weekdayHanja
        )
    }

    // Whether [text] starts with a title as a word of its own (대통령은), not as part of a
    // compound (대표팀) or after punctuation
    private fun startsWithTitle(text: String): Boolean {
        val hangul = text.split(Hanja.RUN).first()
        if (hangul.firstOrNull()?.isLetter() != true) return false
        return tokenize(hangul).firstOrNull()?.text in Hanja.TITLES
    }

    /**
     * Entries for each hanja term, keyed by the term as written. Hanja the dictionary has
     * together (韓美 -> 한미) hide their parts. Within a term, entries come in this order:
     * - A family name, if followed by a title (李대통령)
     * - The full word for a hanja that news uses as an abbreviation (美 for 미국), since the
     *   entry for the hanja alone has its literal meaning (미, beauty)
     * - Entries with the term as their origin. A standalone hanja puts affixes last, and puts
     *   time senses (금, Friday) first in parentheses and last elsewhere.
     * - A family name otherwise, as in 韓에, which is usually Korea but can be someone named Han
     */
    private fun hanjaResults(
        searchTerms: SearchTerms,
        byOrigin: Map<String?, List<DictionaryWithSenses>>,
        ranking: HomonymRanking
    ): List<Pair<String, List<DictionaryWithSenses>>> {
        fun entries(term: String) = byOrigin[Hanja.normalize(term)].orEmpty()
        val found = searchTerms.hanjaTerms.filter { it.length > 1 && entries(it).isNotEmpty() }
        return searchTerms.hanjaTerms
            .filter { term -> found.none { it != term && term in it } }
            .map { term ->
                val normalized = Hanja.normalize(term)
                val surname = listOfNotNull(Hanja.surnameEntry(normalized))
                val titled = term in searchTerms.titledHanja
                term to (if (titled) surname else emptyList()) +
                    byOrigin[Hanja.NEWS_ABBREVIATIONS[normalized]].orEmpty() +
                    entries(term).sortedWith(ranking.comparator).let { ranked ->
                        if (term !in searchTerms.standaloneHanja) return@let ranked
                        val weekday = term in searchTerms.weekdayHanja
                        ranked.sortedWith(
                            compareBy<DictionaryWithSenses> { it.entry.partOfSpeech == "Affix" }
                                .thenBy { (it.entry.semanticCategory == TIME_CATEGORY) != weekday }
                        )
                    } +
                    (if (titled) emptyList() else surname)
            }
            .filter { (_, entries) -> entries.isNotEmpty() }
    }

    private fun getHangulSearchTerms(word: String, neighbors: Neighbors = Neighbors()): SearchTerms {
        // The words around it, for the analyzer's context
        val context = listOfNotNull(neighbors.previous, word, neighbors.next).takeIf { it.size > 1 }?.joinToString(" ")
        val (endings, tokens) = analyzer.value.tokenize(word, context).partition { it.tag == Tag.ENDING }
        val terms = LinkedHashMap<String, Tag?>()
        val compounds = mutableListOf<String>()
        val splitParts = mutableSetOf<String>()
        val affixFallbacks = mutableMapOf<String, String>()
        val counters = mutableSetOf<String>()
        val numberWordCounters = mutableSetOf<String>()
        val single = tokens.singleOrNull()
        terms[word] = single?.tag

        tokens.forEachIndexed { index, token ->
            val stem = token.stem ?: token.text
            terms.putIfAbsent(stem, token.tag)

            // The noun a predicate is made from (공부 in 공부하다), as a word of its own
            if (token.tag.isPredicate) {
                LIGHT_VERBS.firstOrNull { stem.length > it.length + 1 && stem.endsWith(it) }
                    ?.let { terms.putIfAbsent(stem.dropLast(it.length), Tag.NOUN) }
            }

            // A word after a number is almost always a counter (3명, 134개), possibly after a unit
            // (16t급)
            val previous = tokens.subList(0, index).lastOrNull { it.tag != Tag.FOREIGN }
            val isCounter = previous?.tag == Tag.NUMBER && token.tag.isContent
            if (isCounter) {
                terms.putIfAbsent(token.text, Tag.NOUN)
                counters += token.text
            }

            // The dictionary writes affixes as -군 and 초-
            val affix = when (token.tag) {
                Tag.SUFFIX, Tag.PREDICATE_SUFFIX -> "-$stem"
                Tag.PREFIX -> "$stem-"
                else -> null
            }
            if (affix != null && affix !in terms) {
                terms[affix] = token.tag
                if (stem != word) affixFallbacks[stem] = affix
            }
        }

        // A noun after a number written as a word (두 바퀴, 몇 명) is a counter too
        if (neighbors.previous in NUMBER_DETERMINERS) {
            tokens.firstOrNull()?.takeIf { it.tag.isNominal }?.let {
                counters += it.text
                numberWordCounters += it.text
            }
        }

        // Handle compound nouns by generating all sub-strings. This lets the dictionary decide
        // which parts are "real" words. Nouns next to each other are one compound, as the
        // analyzer splits compounds the dictionary doesn't have (대표팀 -> 대표 + 팀), but not
        // counters or nouns apart in the text (88세)씨).
        val nounRuns = mutableListOf<String>()
        var run = ""
        for (token in tokens) {
            val joins = token.tag in COMPOUND_TAGS && token.text !in counters
            if (joins && (run + token.text) in word) {
                run += token.text
            } else {
                if (run.isNotEmpty()) nounRuns += run
                run = if (joins) token.text else ""
            }
        }
        if (run.isNotEmpty()) nounRuns += run
        for (compound in nounRuns.filter { it.length >= 2 }) {
            compounds += compound
            for (i in compound.indices) {
                for (j in i + 1..compound.length) {
                    val sub = compound.substring(i, j)
                    if (sub.length < compound.length && sub !in terms) {
                        terms[sub] = Tag.NOUN
                        if (sub.length == 1) splitParts += sub
                    }
                }
            }
            // The start and end of a compound can also be affixes (외교부 -> 외-, -부)
            for (i in 1 until compound.length) {
                for (affix in listOf(compound.substring(0, i) + "-", "-" + compound.substring(i))) {
                    if (affix !in terms) {
                        terms[affix] = null
                        splitParts += affix
                    }
                }
            }
        }

        // Endings as the dictionary writes them (-면서, -겠-), except the most basic ones
        for (ending in endings) ending.stem?.takeIf { it !in BASIC_ENDINGS }?.let { terms.putIfAbsent(it, Tag.ENDING) }

        // The word is read as the content word it starts with, unless it's that word. Without
        // one, as after a number or a prefix, the terms are ranked by the rest of the order.
        val content = tokens.firstOrNull()?.takeIf { it.tag.isContent }
        val mainTerm = when {
            content == null || single?.text == word && single.stem == null -> word
            else -> content.stem ?: content.text
        }

        // Filter out pure numbers (e.g. "2009") to keep the UI chips clean
        return SearchTerms(
            terms = terms.filterKeys { term -> term.any { !it.isDigit() } || term == word },
            mainTerm = mainTerm,
            compounds = compounds,
            splitParts = splitParts,
            affixFallbacks = affixFallbacks,
            counters = counters,
            numberWordCounters = numberWordCounters,
            leadingNoun = tokens.firstOrNull()?.takeIf { it.tag.isNominal }?.text
        )
    }

    /** The word after [word] in [sentence], or null if [word] ends in punctuation or isn't found. */
    private fun nextWord(sentence: String, word: String): String? {
        fun String.core() = trim { !it.isLetterOrDigit() }
        val words = sentence.split(Regex("\\s+"))
        val index = words.indexOfFirst { it.core() == word.core() }
        if (index < 0 || words[index].lastOrNull()?.isLetterOrDigit() != true) return null
        return words.getOrNull(index + 1)
    }

    /** The word before [word] in [sentence], or null if punctuation is between them or it isn't found. */
    private fun previousWord(sentence: String, word: String): String? {
        fun String.core() = trim { !it.isLetterOrDigit() }
        val words = sentence.split(Regex("\\s+"))
        val index = words.indexOfFirst { it.core() == word.core() }
        if (index < 1 || words[index].firstOrNull()?.isLetterOrDigit() != true) return null
        return words[index - 1].takeIf { it.lastOrNull()?.isLetterOrDigit() == true }
    }

    /** What comes before [word] in [sentence], for telling whether a bound noun can follow. */
    private fun precedingWord(sentence: String, word: String): PrecedingWord {
        if (sentence.isBlank()) return PrecedingWord.UNKNOWN
        fun String.core() = trim { !it.isLetterOrDigit() }
        val words = sentence.split(Regex("\\s+"))
        val index = words.indexOfFirst { it.core() == word.core() }
        if (index < 0) return PrecedingWord.UNKNOWN
        if (index == 0) return PrecedingWord.BREAK
        // Tapped words can start with punctuation too, as in "(9월"
        if (!words[index].first().isLetterOrDigit()) return PrecedingWord.BREAK

        val previous = words[index - 1]
        if (!previous.last().isLetterOrDigit()) return PrecedingWord.BREAK
        val last = tokenize(previous).lastOrNull() ?: return PrecedingWord.UNKNOWN
        return when {
            // Suffixes end nouns too (1960년대 -> 대/Suffix)
            last.tag.isNominal || last.tag in setOf(Tag.NUMBER, Tag.DETERMINER, Tag.SUFFIX, Tag.HANJA) ->
                PrecedingWord.MODIFIER
            last.tag == Tag.PARTICLE -> PrecedingWord.BREAK
            // Verbs modify nouns in forms ending in ㄴ or ㄹ (맛볼, 위치한, 하는)
            last.tag.isPredicate || last.tag == Tag.ENDING ->
                if (previous.last().finalConsonant() in listOf(FINAL_N, FINAL_L)) PrecedingWord.MODIFIER
                else PrecedingWord.BREAK
            else -> PrecedingWord.UNKNOWN
        }
    }

    // Index of a Hangul syllable's final consonant (0 for none), or -1 for other characters
    private fun Char.finalConsonant(): Int = if (this in '가'..'힣') (this - '가') % 28 else -1

    /**
     * The split parts worth showing: those in the fewest-word split of a compound into dictionary
     * [words], like 값 in 농산물값, 팀 in 대표팀 or the suffix -군 in 후보군. Nearly every
     * syllable of a Sino-Korean compound is a word of its own, so the rest (정 and 부 in 정부)
     * are noise.
     */
    private fun usefulSplitParts(compounds: List<String>, words: Set<String>): Set<String> =
        compounds.flatMap { compound -> splitIntoWords(compound, words).orEmpty().flatten() }
            .toSet()

    /**
     * Splits [compound] into the fewest [words], or null if it can't be split fully (such as
     * loanwords, where partial splits give meaningless syllables). Each part is given as the
     * dictionary forms it matched: itself, and at the start or end of the compound also as a
     * prefix or suffix. Ties go to the split with the longer first word, since compounds more
     * often end in a short suffix (외교+부, 서울+시).
     */
    private fun splitIntoWords(compound: String, words: Set<String>): List<List<String>>? {
        // splits[i] is the best split of compound.substring(i)
        val splits = arrayOfNulls<List<List<String>>>(compound.length + 1)
        splits[compound.length] = emptyList()
        for (i in compound.length - 1 downTo 0) {
            for (j in compound.length downTo i + 1) {
                val rest = splits[j] ?: continue
                val part = compound.substring(i, j)
                val forms = buildList {
                    if (part in words) add(part)
                    if (i == 0 && j < compound.length && "$part-" in words) add("$part-")
                    if (i > 0 && j == compound.length && "-$part" in words) add("-$part")
                }
                if (forms.isEmpty()) continue
                val current = splits[i]
                if (current == null || rest.size + 1 < current.size) splits[i] =
                    listOf(forms) + rest
            }
        }
        return splits[0]
    }

    /**
     * How the analyzer splits [word], as `text/TAG`, with the stem where it differs. Call off the
     * main thread.
     */
    fun describeTokens(word: String): String = tokenize(word).joinToString(" ") { token ->
        val stem = token.stem?.takeIf { it.isNotEmpty() && it != token.text }?.let { "($it)" }.orEmpty()
        "${token.text}/${token.tag}$stem"
    }

    private fun tokenize(text: String) = analyzer.value.tokenize(text)

    // Words in the sentence other than the looked up word itself, for matching against entries
    private fun contextWords(sentence: String, searchTerms: Set<String>): Set<String> =
        if (sentence.isBlank()) emptySet()
        else analyzer.value.contextWords(sentence).filter { it !in searchTerms }.toSet()

    private class Lookup(
        val searchTerms: SearchTerms,
        val contextWords: Set<String>,
        val precedingWord: PrecedingWord
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDefinitionsForWord(
        word: String,
        context: LookupContext = LookupContext()
    ): Flow<Map<String, List<DictionaryWithSenses>>> {
        val categoryPrefixes = HomonymRanking.categoryPrefixesFor(context.feedCategory)
        // Analyzing takes a while, and the first call builds the analyzer, so it must not run on
        // the main thread.
        return flow {
            val searchTerms = getSearchTerms(
                word,
                Neighbors(previousWord(context.sentence, word), nextWord(context.sentence, word))
            )
            emit(
                Lookup(
                    searchTerms = searchTerms,
                    contextWords = contextWords(context.sentence, searchTerms.terms.keys - searchTerms.splitParts),
                    precedingWord = precedingWord(context.sentence, word)
                )
            )
        }
            .flowOn(Dispatchers.Default)
            .flatMapLatest { lookup ->
                val terms = lookup.searchTerms.terms
                val hanjaTerms = lookup.searchTerms.hanjaTerms
                val originEntries = if (hanjaTerms.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    val origins = hanjaTerms.map { Hanja.normalize(it) }
                    dictionaryDao.getEntriesForOrigins(
                        origins + origins.mapNotNull { Hanja.NEWS_ABBREVIATIONS[it] }
                    )
                }
                combine(
                    dictionaryDao.getEntriesForTerms(terms.keys.toList()),
                    originEntries
                ) { entries, hanjaEntries ->
                    val byWord = entries.groupBy { it.entry.word }
                    val hanja = hanjaResults(
                        lookup.searchTerms,
                        hanjaEntries.groupBy { it.entry.origin },
                        HomonymRanking(
                            tokenPos = null,
                            contextWords = lookup.contextWords,
                            categoryPrefixes = categoryPrefixes
                        )
                    )
                    val keptParts = usefulSplitParts(lookup.searchTerms.compounds, byWord.keys)
                    val mainTerm = lookup.searchTerms.mainTerm
                    byWord
                        .filterKeys { it !in lookup.searchTerms.splitParts || it in keptParts }
                        .filterKeys { term ->
                            val affix = lookup.searchTerms.affixFallbacks[term]
                            affix == null || affix !in byWord
                        }
                        .mapValues { (term, homonyms) ->
                            val ranking = HomonymRanking(
                                tokenPos = terms[term],
                                contextWords = lookup.contextWords,
                                categoryPrefixes = categoryPrefixes,
                                isCounter = term in lookup.searchTerms.counters,
                                afterNumberWord = term in lookup.searchTerms.numberWordCounters,
                                preceding = if (term == lookup.searchTerms.leadingNoun) {
                                    lookup.precedingWord
                                } else {
                                    PrecedingWord.UNKNOWN
                                }
                            )
                            homonyms.sortedWith(ranking.comparator)
                        }
                        .toList()
                        .plus(hanja)
                        // Sort:
                        // 1. What the word is read as first (the word itself, or its stem),
                        //    then hanja, which are likely why the word was tapped
                        // 2. Particles and endings last, in the order they're in the word. Many
                        //    have noun homonyms (과, 이) or affix homonyms (-고), so the entries'
                        //    POS alone would put them first.
                        // 3. Counters first, as the word after a number is what's being counted
                        // 4. POS priority of the entry shown first (Nouns > Verbs > Adverbs >
                        //    Grammatical markers)
                        // 5. Longest sub-strings next
                        .sortedWith(
                            compareByDescending<Pair<String, List<DictionaryWithSenses>>> {
                                it.first == mainTerm
                            }.thenByDescending {
                                it.first in hanjaTerms
                            }.thenBy {
                                terms[it.first] in GRAMMAR_TAGS
                            }.thenByDescending {
                                it.first in lookup.searchTerms.counters
                            }.thenBy {
                                // Grammar keeps its order in the word (-었- before -다)
                                if (terms[it.first] in GRAMMAR_TAGS) 0 else getPosPriority(it.second.first())
                            }.thenByDescending {
                                if (terms[it.first] in GRAMMAR_TAGS) 0 else it.first.length
                            }.thenBy {
                                if (terms[it.first] in GRAMMAR_TAGS) terms.keys.indexOf(it.first) else 0
                            }
                        )
                        .toMap()
                }
            }
            .flowOn(Dispatchers.Default)
    }

    // Ranks by the top homonym, which the overlay shows first, since the others' POS can differ
    // (도 the particle also has noun homonyms)
    private fun getPosPriority(entry: DictionaryWithSenses): Int = when (entry.entry.partOfSpeech) {
        // Nominals / Core semantic units
        "Noun", "Pronoun", "Numeral", "Bound Noun" -> 1
        // Predicates
        "Verb", "Adjective", "Auxiliary Verb", "Auxiliary Adjective" -> 2
        // Modifiers
        "Adverb", "Determiner", "Interjection" -> 3
        // Functional / Grammatical markers. Entries without a POS (품사 없음) are mostly
        // grammar patterns (-ㄴ 것 같다), multi-word phrases and conjugated stems (쳐다봐-).
        "Affix", "Particle", "Ending", "품사 없음" -> 4
        null -> 10 // Idioms and proverbs
        else -> 5
    }

    private companion object {
        // What predicates are made from nouns with (공부하다, 발표되다, 퇴출시키다)
        val LIGHT_VERBS = listOf("하다", "되다", "시키다")

        // Determiners that count, before a counter (두 바퀴, 몇 명)
        val NUMBER_DETERMINERS = setOf(
            "한", "두", "세", "네", "서", "석", "너", "넉", "다섯", "여섯", "일곱", "여덟", "아홉", "열",
            "스무", "몇", "여러", "몇몇", "수십", "수백", "수천", "수만", "십여"
        )

        // Endings too common to be worth a chip: the plain statement, the past tense and the
        // modifier forms
        val BASIC_ENDINGS = setOf("-다", "-였-", "-았-", "-었-", "-ㄴ", "-ㄹ")

        // Tags of the terms shown after the words they attach to
        val GRAMMAR_TAGS = setOf(Tag.PARTICLE, Tag.ENDING)

        // Tags of the parts of a compound noun
        val COMPOUND_TAGS = setOf(Tag.NOUN, Tag.UNKNOWN, Tag.SUFFIX)

        val PARTICLES = setOf(
            "이", "가", "을", "를", "은", "는", "의", "에", "에서", "에게", "로", "으로", "와", "과",
            "도", "만", "까지", "부터"
        )

        // The semantic category of weekdays (금, Friday), among other time words
        const val TIME_CATEGORY = "개념 > 시간"

        // Hangul final consonant indexes
        const val FINAL_N = 4
        const val FINAL_L = 8
    }
}
