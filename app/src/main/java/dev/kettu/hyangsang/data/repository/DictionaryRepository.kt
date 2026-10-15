package dev.kettu.hyangsang.data.repository

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
import org.openkoreantext.processor.KoreanTokenJava
import org.openkoreantext.processor.OpenKoreanTextProcessorJava

class DictionaryRepository(
    private val dictionaryDao: DictionaryDao
) {
    /**
     * @param terms search terms, each with the Open Korean Text POS of the token it came from, or
     *   null when unknown (such as the whole word when it spans several tokens)
     * @param compounds noun stems whose parts were added as terms
     * @param splitParts terms only worth showing when they're part of a compound's split into
     *   words: single syllables, and affixes as the dictionary writes them (-부, 외-)
     * @param affixFallbacks tokens that look like affixes, mapped to their affix form; the token
     *   itself is only shown if the dictionary doesn't have the affix
     * @param counters terms right after a number, like 명 in 3명 or 일 in 26일
     * @param leadingNoun the noun the word starts with, like 중 in 중에서, if any
     * @param hanjaTerms the hanja runs and their parts, looked up by the entries' origin
     * @param titledHanja single hanja followed by a title, like 李 in 李대통령, which makes them
     *   family names
     * @param standaloneHanja single hanja with no Hangul attached but a particle (金, 北이), so
     *   not affixes
     * @param weekdayHanja standalone hanja in parentheses, as weekdays are written in dates
     *   (3일(金))
     * @param predicateReadings nouns that may be a conjugated verb or adjective Open Korean Text
     *   misread as a noun (통해, 지난)
     */
    private class SearchTerms(
        val terms: Map<String, String?>,
        val compounds: List<String>,
        val splitParts: Set<String>,
        val affixFallbacks: Map<String, String>,
        val counters: Set<String>,
        val leadingNoun: String?,
        val hanjaTerms: List<String> = emptyList(),
        val titledHanja: Set<String> = emptySet(),
        val standaloneHanja: Set<String> = emptySet(),
        val weekdayHanja: Set<String> = emptySet(),
        val predicateReadings: List<PredicateReading> = emptyList()
    )

    /**
     * A noun token that may be a misread conjugated predicate, like 통해 (통하다), 지난 (지나다)
     * or 크게 (크다). Open Korean Text has many such forms in its noun list. Which reading wins
     * is decided once the dictionary has been searched, by [resolvePredicateReadings].
     *
     * @param noun the token as Open Korean Text read it
     * @param stems the predicates it could be a form of, added as search terms for it
     * @param contractedNoun for forms ending in ㄴ or ㄹ, the noun they'd be with a contracted
     *   particle (땐 for 때는, 걸 for 거를), searched for to rule out the predicate
     * @param hidesContractedNoun whether [contractedNoun] was only added to be searched for, so
     *   it isn't shown. Names often end like a contraction (마이클, 서산).
     * @param beforeBoundNoun whether a bound noun follows (낼 수), which only a predicate can
     *   modify
     * @param afterCaseParticle whether the token is a 해 form and the whole word, and follows an
     *   object or adverbial particle (을 위해, 에 대해), which needs a predicate
     */
    private class PredicateReading(
        val noun: String,
        val stems: List<String>,
        val contractedNoun: String?,
        val hidesContractedNoun: Boolean,
        val beforeBoundNoun: Boolean,
        val afterCaseParticle: Boolean
    )

    /** The words around a tapped word in its sentence, when known. */
    private class Neighbors(val previous: String? = null, val next: String? = null)

    /**
     * News writes some words in hanja (北, 韓美), which Open Korean Text reads as foreign and
     * misreads the particles after (北이 -> 이/Noun). So hanja are looked up by the entries'
     * origin, and the Hangul between them as words of their own.
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

        val terms = LinkedHashMap<String, String?>()
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
                index > 0 && text in PARTICLES -> terms.putIfAbsent(text, "Josa")
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
            compounds = parts.flatMap { it.compounds },
            splitParts = parts.flatMap { it.splitParts }.toSet(),
            affixFallbacks = parts.fold(emptyMap()) { fallbacks, part -> fallbacks + part.affixFallbacks },
            counters = parts.flatMap { it.counters }.toSet(),
            leadingNoun = leadingNoun,
            hanjaTerms = hanjaTerms.toList(),
            titledHanja = titledHanja,
            standaloneHanja = standaloneHanja,
            weekdayHanja = weekdayHanja,
            predicateReadings = parts.flatMap { it.predicateReadings }
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

    // Open Korean Text stems 됐다 and 돼 as 돼다, and 되어 and 되었다 as 되어다, instead of 되다
    private fun stemOf(token: KoreanTokenJava): String =
        (if (!token.stem.isNullOrEmpty()) token.stem else token.text).let {
            when {
                it.endsWith("돼다") -> it.dropLast(2) + "되다"
                it.endsWith("되어다") -> it.dropLast(3) + "되다"
                else -> it
            }
        }

    private fun getHangulSearchTerms(word: String, neighbors: Neighbors = Neighbors()): SearchTerms {
        val javaTokens = tokenize(word)
        val terms = LinkedHashMap<String, String?>()
        val compounds = mutableListOf<String>()
        val splitParts = mutableSetOf<String>()
        val affixFallbacks = mutableMapOf<String, String>()
        val counters = mutableSetOf<String>()
        terms[word] = javaTokens.singleOrNull()?.pos?.toString()

        // A word can't start with a particle, so a leading particle token is Open Korean Text
        // misreading a noun and its particle (나흘 만에 -> 만에/Josa, meaning 만 + 에)
        val misreadNoun = javaTokens.firstOrNull()
            ?.takeIf { it.pos.toString() == "Josa" && it.text.length > 1 && it.text.drop(1) in PARTICLES }
            ?.text?.take(1)
        misreadNoun?.let { terms.putIfAbsent(it, null) }

        javaTokens.forEachIndexed { index, token ->
            val pos = token.pos.toString()
            // Open Korean Text reads 는 after a quote or a Latin letter as a form of 늘다
            // (없다”는, LG는), where it's always the particle
            if (index > 0 && token.text == "는" && pos == "Verb") {
                terms.putIfAbsent(token.text, "Josa")
                return@forEachIndexed
            }
            val stem = stemOf(token)
            terms.putIfAbsent(stem, pos)

            // Open Korean Text splits verbs made of a noun and 하다, 되다 or 시키다
            // (공부하러 -> 공부 + 하러/Verb), so the dictionary's joined verb is looked up too
            val next = javaTokens.getOrNull(index + 1)
            if (pos == "Noun" && next?.pos?.toString() == "Verb" && stemOf(next) in LIGHT_VERBS) {
                terms.putIfAbsent(token.text + stemOf(next), "Verb")
            }

            // A word after a number is almost always a counter (3명, 134개), possibly after a unit
            // (16t급). Its own text is used since Open Korean Text can misread it (60대 -> 대다).
            val previous = javaTokens.subList(0, index).lastOrNull { it.pos.toString() != "Alpha" }
            val isCounter = previous?.pos?.toString() == "Number" && pos in listOf("Noun", "Verb", "Adjective")
            if (isCounter) {
                terms.putIfAbsent(token.text, "Noun")
                counters += token.text
            }

            // Open Korean Text splits off common suffixes (후보군 -> 후보 + 군/Suffix) and some
            // prefixes, as one-syllable nouns (초고속 -> 초 + 고속). The dictionary writes these
            // as -군 and 초-.
            val nextPos = javaTokens.getOrNull(index + 1)?.pos?.toString()
            val affix = when (pos) {
                "Suffix" -> "-$stem"
                "Noun" if stem.length == 1 && nextPos == "Noun" && !isCounter -> "$stem-"
                else -> null
            }
            if (affix != null && affix !in terms) {
                terms[affix] = pos
                if (stem != word) affixFallbacks[stem] = affix
            }

            // Regex to catch numbers followed by Hangul (e.g., "2009년", "12일")
            // This ensures "년" or "일" are added as separate search terms
            val numericSuffixMatch = Regex("^\\d+([ㄱ-ㅎㅏ-ㅣ가-힣]+)$").find(token.text)
            numericSuffixMatch?.let {
                terms.putIfAbsent(it.groupValues[1], null)
                counters += it.groupValues[1]
            }

            // Handle compound nouns by generating all sub-strings. This lets the dictionary
            // decide which parts are "real" words.
            if (pos == "Noun" && stem.length >= 2) {
                compounds += stem
                for (i in stem.indices) {
                    for (j in i + 1..stem.length) {
                        val sub = stem.substring(i, j)
                        if (sub.length < stem.length && sub !in terms) {
                            terms[sub] = "Noun"
                            if (sub.length == 1) splitParts += sub
                        }
                    }
                }
                // The start and end of a compound can also be affixes (외교부 -> 외-, -부)
                for (i in 1 until stem.length) {
                    for (affix in listOf(stem.substring(0, i) + "-", "-" + stem.substring(i))) {
                        if (affix !in terms) {
                            terms[affix] = null
                            splitParts += affix
                        }
                    }
                }
            }
        }

        // Open Korean Text misreads some noun + 하다 verbs altogether (연계한 -> 연/Modifier +
        // 계/Noun + 한/Josa), so the noun is also split off at the verb's ending. Not from what
        // it reads as one noun, which can contain the ending (지난해), or when it read the verb
        // right (중요한 -> 중요하다).
        val isOneNoun = javaTokens.singleOrNull()?.pos?.toString() == "Noun"
        LIGHT_VERB_FORM.find(word)?.takeIf { !isOneNoun }?.let { match ->
            val (noun, ending) = match.destructured
            val verb = noun + when (ending.first()) {
                '시' -> "시키다"
                '되', '된', '될', '됨', '됩', '돼', '됐' -> "되다"
                else -> "하다"
            }
            if (verb !in terms) {
                terms[verb] = "Verb"
                terms.putIfAbsent(noun, "Noun")
            }
        }

        // A noun followed only by particles and endings may be a misread predicate
        val predicateReading = javaTokens.firstOrNull()
            ?.takeIf { first ->
                first.pos.toString() == "Noun" && first.text.all { it in '가'..'힣' } &&
                    javaTokens.drop(1).all { it.pos.toString() in GRAMMATICAL_POS }
            }
            ?.let { predicateReading(it.text, isWholeWord = it.text == word, neighbors, terms.keys) }
            ?.also { reading ->
                for (stem in reading.stems) terms[stem] = if (reading.noun.last() == '게') "Adjective" else null
                reading.contractedNoun?.let { terms.putIfAbsent(it, "Noun") }
            }

        // Filter out pure numbers (e.g. "2009") to keep the UI chips clean
        return SearchTerms(
            terms = terms.filterKeys { term -> term.any { !it.isDigit() } || term == word },
            compounds = compounds,
            splitParts = splitParts,
            affixFallbacks = affixFallbacks,
            counters = counters,
            leadingNoun = misreadNoun ?: javaTokens.firstOrNull()?.takeIf { it.pos.toString() == "Noun" }?.text,
            predicateReadings = listOfNotNull(predicateReading)
        )
    }

    /**
     * How [noun] would read as a conjugated predicate, or null if it can't be one. Covers the
     * endings Open Korean Text most often misses: modifier ㄴ and ㄹ (지난, 낼), 해 (통해), 게
     * (크게), and the bare stem before a particle-like ending (앞서, 알리고).
     */
    private fun predicateReading(
        noun: String,
        isWholeWord: Boolean,
        neighbors: Neighbors,
        otherTerms: Set<String>
    ): PredicateReading? {
        val last = noun.last()
        val head = noun.dropLast(1)
        val final = last.finalConsonant()
        val isModifier = final == FINAL_N || final == FINAL_L
        val withoutFinal = head + (last - final)
        val stems = buildList {
            if (isModifier) add(withoutFinal + "다")
            if (last == '해') add(head + "하다")
            if (last == '게' && head.isNotEmpty()) add(head + "다")
            // Not after a particle's syllable, as Open Korean Text also reads some nouns and
            // their particle as one noun (달이)
            if (last.toString() !in PARTICLES) add(noun + "다")
        }.distinct().filter { it !in otherTerms }
        if (stems.isEmpty()) return null

        val nextToken = neighbors.next?.let { tokenize(it).firstOrNull() }
        val previousToken = neighbors.previous?.let { tokenize(it).lastOrNull() }
        val contractedNoun = withoutFinal.takeIf { isModifier && it.isNotEmpty() }
        return PredicateReading(
            noun = noun,
            stems = stems,
            contractedNoun = contractedNoun,
            hidesContractedNoun = contractedNoun != null && contractedNoun !in otherTerms,
            beforeBoundNoun = isModifier && isWholeWord && nextToken?.text in BOUND_NOUNS,
            afterCaseParticle = last == '해' && isWholeWord && previousToken?.pos?.toString() == "Josa" &&
                previousToken.text in CASE_PARTICLES
        )
    }

    private class ResolvedReadings(
        val promoted: Set<String> = emptySet(),
        val demoted: Set<String> = emptySet(),
        val hidden: Set<String> = emptySet()
    )

    /**
     * Decides how each of [searchTerms]'s nouns that may be a misread predicate reads, given the
     * dictionary [entries] found for the terms. The predicate wins, and is shown first, when:
     * - A bound noun follows the form ending in ㄴ or ㄹ (낼 수)
     * - The noun isn't in the dictionary (통해, 지난), unless it can be a noun with a contracted
     *   particle (땐 for 때는)
     * - The dictionary only has it as rare nouns, without a vocabulary level (대한, 大韓), with
     *   the same exception
     * - It's a 해 form of a noun and follows an object or adverbial particle (에 대해, 을 위해),
     *   which needs a predicate
     * Otherwise the noun was read right (산, 피해), and the predicates aren't shown.
     */
    private fun resolvePredicateReadings(
        searchTerms: SearchTerms,
        entries: Map<String, List<DictionaryWithSenses>>
    ): ResolvedReadings {
        val promoted = mutableSetOf<String>()
        val demoted = mutableSetOf<String>()
        val hidden = mutableSetOf<String>()
        for (reading in searchTerms.predicateReadings) {
            val stems = reading.stems.filter { stem ->
                entries[stem].orEmpty().any { it.entry.partOfSpeech in PREDICATE_POS }
            }
            val own = entries[reading.noun].orEmpty()
            val isContraction = reading.contractedNoun
                ?.let { noun -> entries[noun].orEmpty().any { it.entry.partOfSpeech in NOMINAL_POS } } == true
            val isPredicate = stems.isNotEmpty() && (
                reading.beforeBoundNoun ||
                    !isContraction && (
                        own.isEmpty() ||
                            own.all { it.entry.partOfSpeech == "Noun" && it.entry.vocabularyLevel in UNLEVELED }
                        ) ||
                    reading.afterCaseParticle && own.all { it.entry.partOfSpeech == "Noun" }
                )
            if (isPredicate) {
                promoted += stems
                demoted += reading.noun
                hidden += reading.stems - stems.toSet()
            } else {
                hidden += reading.stems
            }
            if (reading.hidesContractedNoun) hidden += listOfNotNull(reading.contractedNoun)
        }
        return ResolvedReadings(promoted, demoted, hidden)
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
        return when (last.pos.toString()) {
            // Suffixes end nouns too (1960년대 -> 대/Suffix)
            "Noun", "ProperNoun", "Number", "Determiner", "Modifier", "Suffix" -> PrecedingWord.MODIFIER
            "Josa" -> PrecedingWord.BREAK
            // What follows a number, like 대 in 1960년대 or 에 in 5조원에
            "Foreign" -> if (last.text in PARTICLES) PrecedingWord.BREAK else PrecedingWord.MODIFIER
            // Verbs modify nouns in forms ending in ㄴ or ㄹ (맛볼, 위치한, 하는)
            "Verb", "Adjective", "Eomi" ->
                if (last.text.last().finalConsonant() in listOf(FINAL_N, FINAL_L)) PrecedingWord.MODIFIER
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

    /** How Open Korean Text splits [word], as `text/Pos`, with the stem where it differs. */
    fun describeTokens(word: String): String = tokenize(word).joinToString(" ") { token ->
        val stem = token.stem?.takeIf { it.isNotEmpty() && it != token.text }?.let { "($it)" }.orEmpty()
        "${token.text}/${token.pos}$stem"
    }

    private fun tokenize(text: String) =OpenKoreanTextProcessorJava.tokensToJavaKoreanTokenList(
        OpenKoreanTextProcessorJava.tokenize(OpenKoreanTextProcessorJava.normalize(text))
    ).filter { it.pos.toString() !in listOf("Space", "Punctuation") }

    // Nouns in the sentence other than the looked up word itself, for matching against entries
    private fun contextNouns(sentence: String, searchTerms: Set<String>): Set<String> =
        if (sentence.isBlank()) emptySet()
        else tokenize(sentence)
            .filter { it.pos.toString() in listOf("Noun", "ProperNoun") && it.text.length >= 2 }
            .map { it.text }
            .filter { it !in searchTerms }
            .toSet()

    private class Lookup(
        val searchTerms: SearchTerms,
        val contextNouns: Set<String>,
        val precedingWord: PrecedingWord
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDefinitionsForWord(
        word: String,
        context: LookupContext = LookupContext()
    ): Flow<Map<String, List<DictionaryWithSenses>>> {
        val categoryPrefixes = HomonymRanking.categoryPrefixesFor(context.feedCategory)
        // Tokenizing with Open Korean Text is CPU-heavy (and the first call loads its
        // dictionaries), so it must not run on the main thread.
        return flow {
            val searchTerms = getSearchTerms(
                word,
                Neighbors(previousWord(context.sentence, word), nextWord(context.sentence, word))
            )
            emit(
                Lookup(
                    searchTerms = searchTerms,
                    contextNouns = contextNouns(context.sentence, searchTerms.terms.keys),
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
                            contextNouns = lookup.contextNouns,
                            categoryPrefixes = categoryPrefixes
                        )
                    )
                    val readings = resolvePredicateReadings(lookup.searchTerms, byWord)
                    // A misread predicate isn't a compound, so its syllables aren't words
                    val keptParts = usefulSplitParts(lookup.searchTerms.compounds - readings.demoted, byWord.keys)
                    byWord
                        .filterKeys { it !in readings.hidden }
                        .filterKeys { it !in lookup.searchTerms.splitParts || it in keptParts }
                        .filterKeys { term ->
                            val affix = lookup.searchTerms.affixFallbacks[term]
                            affix == null || affix !in byWord
                        }
                        .mapValues { (term, homonyms) ->
                            val ranking = HomonymRanking(
                                tokenPos = terms[term],
                                contextNouns = lookup.contextNouns,
                                categoryPrefixes = categoryPrefixes,
                                isCounter = term in lookup.searchTerms.counters,
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
                        // 1. Exact match first, then hanja, which are likely why the word was
                        //    tapped. A predicate Open Korean Text misread as a noun takes the
                        //    noun's place.
                        // 2. Particles and endings last. Many have noun homonyms (과, 이), so
                        //    the entries' POS alone would put them first.
                        // 3. Counters first, as the word after a number is what's being counted
                        // 4. POS priority of the entry shown first (Nouns > Verbs > Adverbs >
                        //    Grammatical markers)
                        // 5. Longest sub-strings next
                        .sortedWith(
                            compareByDescending<Pair<String, List<DictionaryWithSenses>>> {
                                it.first in readings.promoted ||
                                    it.first == word && it.first !in readings.demoted
                            }.thenByDescending {
                                it.first in hanjaTerms
                            }.thenBy {
                                terms[it.first] in GRAMMATICAL_POS
                            }.thenByDescending {
                                it.first in lookup.searchTerms.counters
                            }.thenBy {
                                getPosPriority(it.second.first())
                            }.thenByDescending {
                                it.first.length
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
        // Open Korean Text POS of particles and endings. It also tags particles after numbers as
        // Foreign (5조원에 -> 에/Foreign).
        val GRAMMATICAL_POS = setOf("Josa", "Eomi", "PreEomi", "Foreign")

        val PARTICLES = setOf(
            "이", "가", "을", "를", "은", "는", "의", "에", "에서", "에게", "로", "으로", "와", "과",
            "도", "만", "까지", "부터"
        )

        // Particles that need a predicate after them, rather than a noun (을 위해, 에 대해)
        val CASE_PARTICLES = setOf("을", "를", "에", "에게")

        // Bound nouns that follow a predicate's ㄴ or ㄹ form (낼 수, 온 지)
        val BOUND_NOUNS = setOf("수", "것", "거", "줄", "데", "뿐", "듯", "바", "리", "적", "채", "터", "때", "지", "만큼")

        // What 하다 verbs and the like are made from nouns with
        val LIGHT_VERBS = setOf("하다", "되다", "시키다")

        // A noun of two or more syllables followed by a form of 하다, 되다 or 시키다
        val LIGHT_VERB_FORM = Regex("^([가-힣]{2,}?)(하|한|할|함|합|해|했|시키|시킨|시킬|시켜|시켰|되|된|될|됨|됩|돼|됐)")

        val PREDICATE_POS = setOf("Verb", "Adjective", "Auxiliary Verb", "Auxiliary Adjective")
        val NOMINAL_POS = setOf("Noun", "Bound Noun", "Pronoun")

        // Entries without a vocabulary level, which are rarer words
        val UNLEVELED = setOf(null, "None")

        // The semantic category of weekdays (금, Friday), among other time words
        const val TIME_CATEGORY = "개념 > 시간"

        // Hangul final consonant indexes
        const val FINAL_N = 4
        const val FINAL_L = 8
    }
}
