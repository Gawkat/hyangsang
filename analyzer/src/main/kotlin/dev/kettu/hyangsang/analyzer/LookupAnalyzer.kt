package dev.kettu.hyangsang.analyzer

enum class Tag(val isContent: Boolean = false, val isPredicate: Boolean = false) {
    NOUN(true), PRONOUN(true), NUMERAL(true), BOUND_NOUN(true),

    /** A word the dictionary doesn't have, read as a noun, such as a name */
    UNKNOWN(true),
    VERB(true, true), ADJECTIVE(true, true), AUX_VERB(true, true), AUX_ADJECTIVE(true, true),

    /** 이다 */
    COPULA(isPredicate = true),

    /** A suffix that makes a predicate from a noun (-스럽다), when the dictionary doesn't have the predicate */
    PREDICATE_SUFFIX(isPredicate = true),
    ADVERB(true), DETERMINER(true), INTERJECTION(true),
    PREFIX, SUFFIX, PARTICLE, ENDING,
    NUMBER, FOREIGN, HANJA;

    val isNominal get() = this in setOf(NOUN, PRONOUN, NUMERAL, BOUND_NOUN, UNKNOWN)
}

/**
 * @param form the morpheme as the dictionary writes it, without the 다 of predicates or the
 *   hyphens of affixes and endings
 * @param start start of the morpheme in the analyzed text. Morphemes contracted into one syllable
 *   (했 -> 하 + 였) both span it.
 * @param parts for endings the dictionary has as one entry but that are several endings (-었던),
 *   those endings
 */
data class Morpheme(val form: String, val tag: Tag, val start: Int, val end: Int, val parts: List<String> = listOf(form)) {
    /** The dictionary headword: 공부하다, -면서, 는, 외-, -군 */
    val headword: String
        get() = when (tag) {
            Tag.VERB, Tag.ADJECTIVE, Tag.AUX_VERB, Tag.AUX_ADJECTIVE, Tag.COPULA -> form + "다"
            Tag.PREDICATE_SUFFIX -> "-" + form + "다"
            Tag.ENDING -> if (form in Lexicon.PREFINALS) "-$form-" else "-$form"
            Tag.PREFIX -> "$form-"
            Tag.SUFFIX -> "-$form"
            else -> form
        }

    override fun toString() = "$form/$tag"
}

class Analysis(val morphemes: List<Morpheme>, val cost: Float) {
    /** The first content word: the noun, predicate, adverb or the like the word is built on. */
    val content: Morpheme? get() = morphemes.firstOrNull { it.tag.isContent }

    /**
     * The headwords a lookup would show for the word: its content words, and the predicate a
     * noun and suffix make (공부 + 하 -> 공부하다), even if the dictionary doesn't have it.
     */
    val headwords: List<String>
        get() {
            val result = LinkedHashSet<String>()
            morphemes.forEachIndexed { i, m ->
                if (m.tag.isContent) result += m.headword
                val previous = morphemes.getOrNull(i - 1)
                if (previous != null && previous.tag.isNominal &&
                    (m.tag == Tag.PREDICATE_SUFFIX || m.tag.isPredicate && m.form in LIGHT_VERBS)
                ) {
                    result += previous.form + m.form + "다"
                }
                if (previous != null && previous.tag.isNominal && (m.tag.isNominal || m.tag == Tag.SUFFIX)) {
                    result += previous.form + m.form
                }
            }
            return result.toList()
        }

    val endings: List<Morpheme> get() = morphemes.filter { it.tag == Tag.ENDING }

    /** Class of the content word, as [ContextModel] has them: N, V, A, X, ADV, DET or - for none. */
    val contentClass: String
        get() {
            val i = morphemes.indexOfFirst { it.tag.isContent }
            if (i < 0) return "-"
            val m = morphemes[i]
            val next = morphemes.getOrNull(i + 1)
            return when {
                m.tag.isNominal && next?.tag == Tag.PREDICATE_SUFFIX -> if (next.form in ADJECTIVE_SUFFIXES) "A" else "V"
                m.tag.isNominal && next != null && next.tag.isPredicate && next.form in LIGHT_VERBS -> if (next.tag == Tag.ADJECTIVE) "A" else "V"
                m.tag.isNominal -> "N"
                m.tag == Tag.VERB -> "V"
                m.tag == Tag.ADJECTIVE -> "A"
                m.tag == Tag.AUX_VERB || m.tag == Tag.AUX_ADJECTIVE -> "X"
                m.tag == Tag.ADVERB -> "ADV"
                m.tag == Tag.DETERMINER -> "DET"
                else -> "-"
            }
        }

    /**
     * What the word ends in, as [ContextModel] has it: a class of particle (JKO for 을, JKS, JKG,
     * JX, JKB) or ending (ETM for modifier endings, ETN, EF, EC), or N, ADV or DET.
     */
    val lastClass: String
        get() {
            val m = morphemes.lastOrNull() ?: return "-"
            return when (m.tag) {
                Tag.PARTICLE -> when (m.form) {
                    "을", "를" -> "JKO"
                    "이", "가", "께서" -> "JKS"
                    "의" -> "JKG"
                    in TOPIC_PARTICLES -> "JX"
                    else -> "JKB"
                }
                Tag.ENDING -> {
                    val last = m.parts.last()
                    when {
                        last in setOf("ㄴ", "은", "는", "ㄹ", "을", "던") || m.form.length > 1 && (m.form.endsWith("는") || m.form.endsWith("던")) -> "ETM"
                        last in setOf("기", "음", "ㅁ") -> "ETN"
                        last.endsWith("다") || last.last() in "요까죠냐오라자" -> "EF"
                        else -> "EC"
                    }
                }
                Tag.ADVERB, Tag.INTERJECTION -> "ADV"
                Tag.DETERMINER -> "DET"
                else -> "N"
            }
        }

    override fun toString() = morphemes.joinToString(" + ") + " (%.2f)".format(cost)

    private companion object {
        val LIGHT_VERBS = setOf("하", "되", "시키", "당하", "받", "드리")
        val ADJECTIVE_SUFFIXES = setOf("스럽", "롭", "답", "적")
        val TOPIC_PARTICLES = setOf("은", "는", "도", "만", "까지", "부터", "조차", "마저", "요", "야", "이야", "라도", "이라도", "나", "이나", "밖에", "뿐")
    }
}

/**
 * Analyzes single words (eojeols) for dictionary lookups: which headwords, particles and endings
 * a word is made of. Candidates are generated by stripping particles and endings from the right
 * and undoing conjugation, keeping only stems the dictionary has, and ranked by a simple cost:
 * fewer morphemes, common words, regular conjugation.
 */
class LookupAnalyzer(private val lexicon: Lexicon, private val context: ContextModel? = null) {
    /**
     * Candidate analyses of [word], best first. Punctuation is left out.
     *
     * @param previous the word before [word] in its sentence, if known, for [context]
     * @param next the word after [word], if known. A bound noun after it (볼 수) means it's a
     *   predicate's modifier form.
     */
    fun analyze(word: String, previous: String? = null, next: String? = null, limit: Int = 5): List<Analysis> {
        val candidates = analyzeAlone(word, CONTEXT_CANDIDATES)
        if (candidates.size < 2) return candidates
        val previousClass = previous?.let { analyzeAlone(it, 1).firstOrNull()?.lastClass }
        val surface = word.trim { !it.isLetterOrDigit() }
        val beforeBoundNoun = next != null && word.lastOrNull()?.isLetterOrDigit() == true &&
            next.trim { !it.isLetterOrDigit() }.let { n -> BOUND_NOUNS.any { n.startsWith(it) } }
        return candidates
            .map { a ->
                var cost = a.cost
                if (context != null) cost += context.cost(previousClass, surface, a)
                if (beforeBoundNoun && a.lastClass == "ETM") cost -= BOUND_NOUN_BONUS
                Analysis(a.morphemes, cost)
            }
            .sortedBy { it.cost }
            .take(limit)
    }

    // Recent analyses, as a lookup analyzes the words around the tapped one too
    private val cache = object : LinkedHashMap<String, List<Analysis>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Analysis>>) = size > CACHE_SIZE
    }

    fun clearCache() = synchronized(cache) { cache.clear() }

    private fun analyzeAlone(word: String, limit: Int): List<Analysis> {
        val cached = synchronized(cache) { cache[word] }
        val analyses = cached ?: analyzeUncached(word).also { synchronized(cache) { cache[word] = it } }
        return analyses.take(limit)
    }

    private fun analyzeUncached(word: String, limit: Int = CONTEXT_CANDIDATES): List<Analysis> {
        val runs = RUNS.findAll(word).toList()
        if (runs.isEmpty()) return emptyList()
        // Each run is analyzed on its own, and the best analyses of all runs joined. Only the
        // last Hangul run gets alternatives, which are what a lookup needs.
        var prefix = emptyList<Morpheme>()
        var prefixCost = 0f
        var alternatives = listOf(Analysis(emptyList(), 0f))
        runs.forEachIndexed { index, run ->
            val text = run.value
            val analyses = when {
                Hangul.isSyllable(text[0]) -> {
                    // After a number, letters, hanja or a closing quote, a Hangul run can start
                    // with what follows a noun (3명이, LG는, 없다”는)
                    val before = word.getOrNull(run.range.first - 1)
                    val afterNominal = before != null && (before.isLetterOrDigit() || before in CLOSING || before in UNITS)
                    val beforeNumber = word.getOrNull(run.range.last + 1)?.isDigit() == true
                    // 제 before a number is the prefix (제253조), but not between numbers (5조5천)
                    if (beforeNumber && !afterNominal && index < runs.lastIndex && text in lexicon.prefixes) {
                        listOf(Analysis(listOf(Morpheme(text, Tag.PREFIX, run.range.first, run.range.last + 1)), 0f))
                    } else {
                        Search(text, run.range.first, afterNominal, afterQuote = before in CLOSING).run()
                    }
                }
                text[0].isDigit() -> listOf(Analysis(listOf(Morpheme(text, Tag.NUMBER, run.range.first, run.range.last + 1)), 0f))
                text[0] in 'A'..'z' -> listOf(Analysis(listOf(Morpheme(text, Tag.FOREIGN, run.range.first, run.range.last + 1)), 0f))
                else -> listOf(Analysis(listOf(Morpheme(text, Tag.HANJA, run.range.first, run.range.last + 1)), 0f))
            }
            if (analyses.isEmpty()) return@forEachIndexed
            if (index < runs.lastIndex) {
                prefix = prefix + analyses.first().morphemes
                prefixCost += analyses.first().cost
            } else {
                alternatives = analyses
            }
        }
        return alternatives.take(limit).map { Analysis(prefix + it.morphemes, prefixCost + it.cost) }
    }

    /** The text still to analyze, starting at [start] in the word. Its first syllable can be the
     * part of a contracted syllable that's left after the stem (했 -> 었), so the text can differ
     * from the word's, but is always as long. */
    private class Rest(val text: String, val start: Int) {
        val isEmpty get() = text.isEmpty()
        fun drop(n: Int) = Rest(text.substring(n), start + n)
        fun replaceFirst(c: Char) = Rest(c + text.substring(1), start)
    }

    private inner class Search(
        private val word: String,
        private val offset: Int,
        private val afterNominal: Boolean,
        private val afterQuote: Boolean = false
    ) {
        private val results = ArrayList<Analysis>()
        private val stack = ArrayList<Morpheme>()
        private var best = Float.MAX_VALUE
        private var steps = 0

        fun run(): List<Analysis> {
            val rest = Rest(word, offset)
            // Particles after a quote are common in news (없다”는, 했다"고)
            if (afterNominal) {
                nominalTail(rest, null, if (afterQuote) 0f else AFTER_NOMINAL, allowCompound = false)
                // Suffixes after a number (200여대 -> 여 + 대)
                for (k in 1 until rest.text.length) {
                    val w = rest.text.substring(0, k)
                    lexicon.suffixes[w]?.let { c ->
                        with(Morpheme(w, Tag.SUFFIX, rest.start, rest.start + k)) {
                            nominalTail(rest.drop(k), null, AFTER_NOMINAL + GRAMMATICAL + SUFFIX + c, allowCompound = true)
                        }
                    }
                }
            }
            content(rest, 0f)
            return results.sortedBy { it.cost }
                .distinctBy { a -> a.morphemes.joinToString { "${it.form}/${it.tag}" } }
                .let { list -> list.filter { it.cost <= list.first().cost + MARGIN } }
        }

        private fun emit(cost: Float) {
            if (cost < best) best = cost
            results += Analysis(stack.toList(), cost)
        }

        private fun pruned(cost: Float) = cost > best + MARGIN || ++steps > MAX_STEPS

        private inline fun with(m: Morpheme, block: () -> Unit) {
            stack += m
            block()
            stack.removeAt(stack.lastIndex)
        }

        /** A content word at the start of [rest], with whatever follows it. */
        private fun content(rest: Rest, cost: Float) {
            if (pruned(cost)) return
            val text = rest.text
            // Longest first, so good analyses are found early and the rest pruned
            for (j in text.length downTo 1) {
                val w = text.substring(0, j)
                val after = rest.drop(j)
                lexicon.nominals[w]?.let { (tag, c) ->
                    val tagCost = if (tag == Tag.NUMERAL) NUMERAL else 0f
                    with(Morpheme(w, tag, rest.start, rest.start + j)) { nominalTail(after, w, cost + CONTENT + c + tagCost) }
                }
                // A noun with a contracted particle: 난 (나는), 걸 (거를), 땐 (때는)
                val last = w.last()
                val final = Hangul.final(last)
                if (final == Hangul.N || final == Hangul.L) {
                    val noun = w.dropLast(1) + Hangul.withFinal(last, 0)
                    lexicon.nominals[noun]?.let { (tag, c) ->
                        with(Morpheme(noun, tag, rest.start, rest.start + j)) {
                            with(Morpheme(if (final == Hangul.N) "는" else "를", Tag.PARTICLE, rest.start + j - 1, rest.start + j)) {
                                if (after.isEmpty) emit(cost + CONTENT + c + GRAMMATICAL + CONTRACTED_PARTICLE)
                            }
                        }
                    }
                }
                CONTRACTED_NOUNS[w]?.let { (noun, particle) ->
                    with(Morpheme(noun, lexicon.nominals[noun]?.first ?: Tag.NOUN, rest.start, rest.start + j)) {
                        with(Morpheme(particle, Tag.PARTICLE, rest.start + j - 1, rest.start + j)) {
                            if (after.isEmpty) emit(cost + CONTENT + GRAMMATICAL + CONTRACTED_PARTICLE)
                        }
                    }
                }
                lexicon.adverbs[w]?.let { c ->
                    with(Morpheme(w, Tag.ADVERB, rest.start, rest.start + j)) {
                        if (after.isEmpty) emit(cost + CONTENT + c) else particles(after, w, cost + CONTENT + c + ADVERB_PARTICLE, 0)
                    }
                }
                if (after.isEmpty) {
                    lexicon.determiners[w]?.let { c -> with(Morpheme(w, Tag.DETERMINER, rest.start, rest.start + j)) { emit(cost + CONTENT + c) } }
                    lexicon.interjections[w]?.let { c -> with(Morpheme(w, Tag.INTERJECTION, rest.start, rest.start + j)) { emit(cost + CONTENT + c + INTERJECTION) } }
                }
                if (!after.isEmpty) lexicon.prefixes[w]?.let { c ->
                    with(Morpheme(w, Tag.PREFIX, rest.start, rest.start + j)) { nouns(after, cost + GRAMMATICAL + c + PREFIX) }
                }
            }
            predicate(rest, cost, PredicateKind.ANY)
            unknown(rest, cost)
        }

        /** A noun at the start of [rest], after a prefix. */
        private fun nouns(rest: Rest, cost: Float) {
            for (j in rest.text.length downTo 1) {
                val w = rest.text.substring(0, j)
                lexicon.nominals[w]?.let { (tag, c) ->
                    with(Morpheme(w, tag, rest.start, rest.start + j)) { nominalTail(rest.drop(j), w, cost + CONTENT + c) }
                }
            }
        }

        /** A word the dictionary doesn't have, such as a name, followed by particles or the like. */
        private fun unknown(rest: Rest, cost: Float) {
            for (j in rest.text.length downTo 1) {
                val nextCost = cost + UNKNOWN_BASE + UNKNOWN_PER_SYLLABLE * j
                if (pruned(nextCost)) continue
                val w = rest.text.substring(0, j)
                with(Morpheme(w, Tag.UNKNOWN, rest.start, rest.start + j)) {
                    nominalTail(rest.drop(j), w, nextCost, allowCompound = false)
                }
            }
        }

        /** What can follow a noun [noun]: particles, the copula, a predicate suffix or another noun. */
        private fun nominalTail(rest: Rest, noun: String?, cost: Float, allowCompound: Boolean = true, afterSuffix: Boolean = false) {
            if (pruned(cost)) return
            if (rest.isEmpty) {
                if (noun != null) emit(cost)
                return
            }
            particles(rest, noun, cost, 0)
            // After a number, the copula is rarer than a counter (16일)
            val copulaCost = if (noun == null) cost + COPULA_AFTER_NUMBER else cost
            predicate(rest, copulaCost, PredicateKind.COPULA)
            // Copula dropped after a vowel (학교다, 학교라는)
            if (noun == null || !Hangul.endsInConsonant(noun)) {
                with(Morpheme("이", Tag.COPULA, rest.start, rest.start)) {
                    endings(rest, noun ?: "이", copulaCost + GRAMMATICAL + ELIDED_COPULA, 0)
                }
            }
            if (noun != null) predicate(rest, cost + DERIVED, PredicateKind.SUFFIX)
            if (!allowCompound || noun == null) return
            for (k in rest.text.length downTo 1) {
                val w = rest.text.substring(0, k)
                lexicon.nominals[w]?.let { (tag, c) ->
                    with(Morpheme(w, tag, rest.start, rest.start + k)) { nominalTail(rest.drop(k), w, cost + CONTENT + COMPOUND + c) }
                }
                lexicon.suffixes[w]?.let { c ->
                    // Suffixes rarely follow suffixes (국방 + 장관, not 국방 + 장 + 관)
                    val suffixCost = GRAMMATICAL + SUFFIX + c + if (afterSuffix) SUFFIX_AFTER_SUFFIX else 0f
                    with(Morpheme(w, Tag.SUFFIX, rest.start, rest.start + k)) { nominalTail(rest.drop(k), noun + w, cost + suffixCost, afterSuffix = true) }
                }
            }
        }

        /** Particles after [previous], which is null after a number or the like. */
        private fun particles(rest: Rest, previous: String?, cost: Float, depth: Int) {
            if (pruned(cost) || depth >= 3) return
            for (k in 1..minOf(rest.text.length, lexicon.maxParticle)) {
                val p = rest.text.substring(0, k)
                if (p !in lexicon.particles) continue
                val nextCost = cost + GRAMMATICAL + (previous?.let { particleMismatch(p, it) } ?: 0f) +
                    // Particles rare in news, which make many noun misreadings (쥐고 as 쥐 + 고)
                    (if (p in RARE_PARTICLES && !(afterQuote && depth == 0 && rest.start == offset)) RARE_PARTICLE else 0f)
                with(Morpheme(p, Tag.PARTICLE, rest.start, rest.start + k)) {
                    val after = rest.drop(k)
                    if (after.isEmpty) emit(nextCost) else particles(after, p, nextCost, depth + 1)
                }
            }
        }

        /**
         * A predicate stem at the start of [rest], conjugated, with its endings. The stem's last
         * syllable can be contracted with the first ending (했, 와, 져), or changed by an
         * irregular conjugation (들어 for 듣다, 추워 for 춥다).
         */
        private fun predicate(rest: Rest, cost: Float, kind: PredicateKind) {
            if (pruned(cost)) return
            val text = rest.text
            for (i in 1..text.length) {
                val left = text.substring(0, i)
                val right = rest.drop(i)
                for ((stem, endingRest, ruleCost, surface) in splits(left, right, rest.start + i - 1)) {
                    if (endingRest.isEmpty) continue
                    val base = when (kind) {
                        PredicateKind.ANY -> CONTENT + PREDICATE_PRIOR
                        PredicateKind.AUX -> GRAMMATICAL + AUXILIARY
                        else -> GRAMMATICAL
                    }
                    for ((tag, entryCost) in stemTags(stem, kind)) {
                        with(Morpheme(stem, tag, rest.start, rest.start + i)) {
                            endings(endingRest, surface, cost + base + entryCost + ruleCost, 0)
                        }
                    }
                }
            }
        }

        /**
         * The tags [stem] can have as a predicate of [kind], with their costs. A stem that's both
         * a verb and an adjective (크다) is both, for the context to choose from.
         */
        private fun stemTags(stem: String, kind: PredicateKind): List<Pair<Tag, Float>> = when (kind) {
            PredicateKind.COPULA -> if (stem == "이") listOf(Tag.COPULA to 0f) else emptyList()
            PredicateKind.SUFFIX -> listOfNotNull(
                lexicon.predicateSuffixes[stem]?.let { c ->
                    // Suffixes that are verbs of their own too (하다, 되다) are shown as those
                    lexicon.predicates[stem]?.minByOrNull { it.value }?.let { it.key to minOf(c, it.value) }
                        ?: (Tag.PREDICATE_SUFFIX to c)
                }
            )
            PredicateKind.AUX -> listOfNotNull(
                lexicon.predicates[stem]?.let { tags ->
                    tags.entries.filter { it.key == Tag.AUX_VERB || it.key == Tag.AUX_ADJECTIVE }.minByOrNull { it.value }?.toPair()
                        ?: if (stem in AUXILIARIES) tags.minBy { it.value }.let { Tag.AUX_VERB to it.value } else null
                }
            )
            PredicateKind.ANY -> lexicon.predicates[stem]?.let { tags ->
                // Auxiliaries on their own are their main verb (있다), and verbs win ties with
                // adjectives, which are more often looked up as verbs
                val main = tags.entries.filter { it.key == Tag.VERB || it.key == Tag.ADJECTIVE }
                    .map { if (it.key == Tag.ADJECTIVE) it.key to it.value + ADJECTIVE_TIE else it.toPair() }
                main.ifEmpty { listOf(tags.minBy { it.value }.toPair()) }
            }.orEmpty()
        }

        /**
         * Ways [left] can end in a predicate stem with [right] starting its endings: the stem,
         * the endings' text, the cost of the rule used and the stem as the endings see it (하야
         * for 하얗 in 하얀). [last] is the position of left's last syllable in the word.
         */
        private fun splits(left: String, right: Rest, last: Int): List<Split> {
            val result = ArrayList<Split>(4)
            val l = left.last()
            if (!Hangul.isSyllable(l)) return result
            val head = left.dropLast(1)
            val ini = Hangul.initial(l)
            val med = Hangul.medial(l)
            val fin = Hangul.final(l)
            val next = right.text.firstOrNull()?.takeIf { Hangul.isSyllable(it) }
            val nextIni = next?.let { Hangul.initial(it) }
            val nextMed = next?.let { Hangul.medial(it) }

            // Regular: 먹 + 고
            result += Split(left, right, 0f)
            if (next != null) {
                // ㄹ dropped before ㄴ, ㅅ, ㅂ and 오: 사는 (살다), 아세요 (알다)
                if (fin == 0 && (nextIni in setOf(Hangul.I_N, Hangul.I_S, Hangul.I_B) || next == '오')) {
                    result += Split(head + Hangul.withFinal(l, Hangul.L), right, IRREGULAR, left)
                }
                // ㄷ irregular: 들어 (듣다). Most stems in ㄷ are regular (굳은), so only these.
                val dStem = head + Hangul.withFinal(l, Hangul.D)
                if (fin == Hangul.L && nextIni == Hangul.I_NG && D_IRREGULAR.any { dStem.endsWith(it) }) {
                    result += Split(dStem, right, IRREGULAR)
                }
                // ㅂ irregular: 추워 (춥다), 추운, 도와 (돕다)
                if (fin == 0 && nextIni == Hangul.I_NG && nextMed in setOf(Hangul.WEO, Hangul.WA, Hangul.U)) {
                    val vowel = when (nextMed) {
                        Hangul.WEO -> Hangul.EO
                        Hangul.WA -> Hangul.A
                        else -> Hangul.EU
                    }
                    result += Split(head + Hangul.withFinal(l, Hangul.B), right.replaceFirst(Hangul.withMedial(next, vowel)), IRREGULAR)
                }
                // ㅅ irregular: 지어 (짓다), 나아 (낫다). Most stems in ㅅ are regular (웃어).
                val sStem = head + Hangul.withFinal(l, Hangul.S)
                if (fin == 0 && nextIni == Hangul.I_NG && nextMed in setOf(Hangul.A, Hangul.EO, Hangul.EU) && S_IRREGULAR.any { sStem.endsWith(it) }) {
                    result += Split(sStem, right, IRREGULAR)
                }
                // 르 irregular: 불러 (부르다), 몰라 (모르다)
                if (fin == Hangul.L && nextIni == Hangul.I_R && nextMed in setOf(Hangul.A, Hangul.EO)) {
                    result += Split(head + Hangul.withFinal(l, 0) + '르', right.replaceFirst(Hangul.withInitial(next, Hangul.I_NG)), 0.05f)
                }
            }

            // A final consonant that starts the ending: 간다 (가 + ㄴ다), 합니다, 할
            Hangul.JAMO_FINALS[fin]?.let { jamo ->
                val endingRest = Rest(jamo + right.text, last)
                val surface = head + Hangul.withFinal(l, 0)
                result += Split(surface, endingRest, 0f)
                // ㄹ stems: 산 (살다), 만들 (만들다 + ㄹ)
                result += Split(head + Hangul.withFinal(l, Hangul.L), endingRest, IRREGULAR, surface)
                // ㅎ irregular: 하얀 (하얗다), 그런 (그렇다)
                result += Split(head + Hangul.withFinal(l, Hangul.H), endingRest, IRREGULAR, surface)
            }

            // Contraction with 아/어: 해 (하 + 여), 와 (오 + 아), 져 (지 + 어), 했 (하 + 였)
            if (fin == 0 || fin == Hangul.SS) {
                val withFinal = { c: Char -> if (fin == Hangul.SS) Hangul.withFinal(c, Hangul.SS) else c }
                fun add(stemSyllable: Char, ending: Char, ruleCost: Float = 0f) {
                    result += Split(head + stemSyllable, Rest(withFinal(ending) + right.text, last), ruleCost)
                }
                when (med) {
                    Hangul.A -> {
                        add(Hangul.compose(ini, Hangul.A), '아', 0.1f)
                        add(Hangul.compose(ini, Hangul.EU), '아')
                    }
                    Hangul.EO -> {
                        add(Hangul.compose(ini, Hangul.EO), '어', 0.1f)
                        add(Hangul.compose(ini, Hangul.EU), '어')
                    }
                    Hangul.AE -> {
                        if (ini == Hangul.I_H) add('하', '여')
                        add(Hangul.compose(ini, Hangul.AE), '어', 0.1f)
                        // ㅎ irregular: 그래 (그렇다), 파래 (파랗다)
                        add(Hangul.compose(ini, Hangul.EO, Hangul.H), '어', IRREGULAR)
                        add(Hangul.compose(ini, Hangul.A, Hangul.H), '아', IRREGULAR)
                    }
                    Hangul.E -> add(Hangul.compose(ini, Hangul.E), '어', 0.1f)
                    Hangul.YEO -> {
                        add(Hangul.compose(ini, Hangul.I), '어')
                        add(Hangul.compose(ini, Hangul.YEO), '어', 0.1f)
                    }
                    // 예요 (이 + 에요)
                    Hangul.YE -> if (ini == Hangul.I_NG) add('이', '에')
                    Hangul.WA -> {
                        add(Hangul.compose(ini, Hangul.O), '아')
                        // 놔 (놓다)
                        add(Hangul.compose(ini, Hangul.O, Hangul.H), '아', IRREGULAR)
                    }
                    Hangul.WEO -> add(Hangul.compose(ini, Hangul.U), '어')
                    Hangul.WAE -> add(Hangul.compose(ini, Hangul.OE), '어')
                }
            }
            return result
        }

        /**
         * Endings after [previous], the stem or ending before them. Pre-final endings (시, 었, 겠)
         * come in that order, as given by [stage], and need an ending after them.
         */
        private fun endings(rest: Rest, previous: String, cost: Float, stage: Int) {
            if (pruned(cost) || rest.isEmpty) return
            val text = rest.text
            for (k in 1..minOf(text.length, lexicon.maxEnding)) {
                val f = text.substring(0, k)
                val ending = lexicon.endings[f] ?: continue
                val order = if (ending.prefinal) PREFINAL_ORDER[f] ?: 3 else null
                if (order != null && order <= stage) continue
                val mismatch = endingMismatch(f, previous)
                if (mismatch >= HARD) continue
                val nextCost = cost + GRAMMATICAL + mismatch
                val after = rest.drop(k)
                with(Morpheme(f, Tag.ENDING, rest.start, rest.start + k, ending.parts)) {
                    if (order != null) {
                        endings(after, f, nextCost, order)
                    } else if (after.isEmpty) {
                        emit(nextCost)
                    } else {
                        particles(after, null, nextCost + PARTICLE_AFTER_ENDING, 0)
                        // An auxiliary predicate: 먹어 보다, 하고 싶다, 높아지다
                        if (f in AUX_CONNECTIVES) predicate(after, nextCost, PredicateKind.AUX)
                    }
                }
            }
            // 시 contracted with what follows: 하셔 (시 + 어), 하셨 (시 + 었), 하십니다 (시 + ㅂ니다)
            if (stage < 1) {
                val isEu = text[0] == '으' && text.length > 1
                val si = if (isEu) text[1] else text[0]
                val siRest = if (isEu) rest.drop(1) else rest
                val replaced = if (Hangul.isSyllable(si) && Hangul.initial(si) == Hangul.I_S) {
                    val fin = Hangul.final(si)
                    when (Hangul.medial(si)) {
                        Hangul.YEO -> when (fin) {
                            0 -> siRest.replaceFirst('어')
                            Hangul.SS -> siRest.replaceFirst('었')
                            else -> null
                        }
                        Hangul.I -> Hangul.JAMO_FINALS[fin]?.let { siRest.replaceFirst(it) }
                        else -> null
                    }
                } else {
                    null
                }
                val form = if (isEu) "으시" else "시"
                val mismatch = endingMismatch(form, previous)
                if (replaced != null && mismatch < HARD) {
                    with(Morpheme(form, Tag.ENDING, rest.start, siRest.start + 1)) {
                        endings(replaced, "시", cost + GRAMMATICAL + mismatch, 1)
                    }
                }
            }
        }

        /** How badly ending [f] fits after [previous]: 0 if it does, [HARD] if it can't follow it. */
        private fun endingMismatch(f: String, previous: String): Float {
            val consonant = Hangul.endsInConsonant(previous) && !Hangul.endsInL(previous)
            val first = f[0]
            val endings = lexicon.endings
            return when {
                Hangul.isJamo(first) -> if (consonant) HARD else 0f
                first == '으' -> if (consonant) 0f else HARD
                first in "은을음읍" && (Hangul.JAMO_FINALS.values.any { j -> (j + f.drop(1)) in endings || first == '읍' }) ->
                    if (consonant) 0f else HARD
                f.startsWith("는다") || f.startsWith("는대") || f.startsWith("는단") -> if (consonant) 0f else HARD
                // Soft, as some endings have the same form for both (-나 and -으나, 먹나)
                ("으$f") in endings -> if (consonant) SOFT else 0f
                first == '아' || first == '았' -> if (Hangul.isBright(previous) && !previous.endsWith("하")) 0f else SOFT
                first == '어' || first == '었' -> if (!Hangul.isBright(previous) || previous == "이") 0f else SOFT
                first == '여' || first == '였' -> if (previous.endsWith("하")) 0f else SOFT
                else -> 0f
            }
        }

        /** How badly particle [p] fits after [previous] (이 after a consonant, 가 after a vowel). */
        private fun particleMismatch(p: String, previous: String): Float {
            if (!previous.last().let(Hangul::isSyllable)) return 0f
            val consonant = Hangul.endsInConsonant(previous)
            val needsConsonant = p in CONSONANT_PARTICLES || p.startsWith("으") ||
                p.length > 1 && p.startsWith("이") && p.drop(1) in lexicon.particles
            val needsVowel = p in VOWEL_PARTICLES || ("이$p") in lexicon.particles || ("으$p") in lexicon.particles
            return when {
                needsConsonant && !consonant -> SOFT
                // 로 after ㄹ (서울로)
                needsVowel && consonant && !(Hangul.endsInL(previous) && p.startsWith("로")) -> SOFT
                else -> 0f
            }
        }
    }

    private enum class PredicateKind { ANY, COPULA, SUFFIX, AUX }

    private data class Split(val stem: String, val rest: Rest, val cost: Float, val surface: String = stem)

    private companion object {
        const val CONTENT = 1f
        const val GRAMMATICAL = 0.3f
        const val PREDICATE_PRIOR = 0.05f

        // Numerals written in Hangul are rare in news, which writes numbers in digits
        const val NUMERAL = 0.3f
        const val AUXILIARY = 0.3f
        const val ADJECTIVE_TIE = 0.01f
        const val IRREGULAR = 0.25f
        const val CONTRACTED_PARTICLE = 0.4f
        const val ADVERB_PARTICLE = 0.3f
        const val INTERJECTION = 0.3f
        const val ELIDED_COPULA = 0.5f
        const val COPULA_AFTER_NUMBER = 0.3f
        const val AFTER_NOMINAL = 0.5f
        const val RARE_PARTICLE = 0.6f
        const val DERIVED = 0.3f
        const val COMPOUND = 0.4f
        const val SUFFIX_AFTER_SUFFIX = 0.6f
        const val SUFFIX = 0.2f
        const val PREFIX = 0.8f
        const val BOUND_NOUN_BONUS = 2.5f
        // More per syllable than a particle, so particles are split off unknown words
        const val UNKNOWN_BASE = 2.0f
        const val UNKNOWN_PER_SYLLABLE = 0.5f
        const val PARTICLE_AFTER_ENDING = 0.2f
        const val SOFT = 1.5f
        const val HARD = 100f
        const val MARGIN = 3f
        const val MAX_STEPS = 20_000
        const val CONTEXT_CANDIDATES = 10
        const val CACHE_SIZE = 256

        // Hangul, numbers, Latin letters and hanja, each analyzed on its own
        val RUNS = Regex("[가-힣]+|[0-9]+(?:[.,][0-9]+)*|[A-Za-z]+|[\\u4E00-\\u9FFF\\uF900-\\uFAFF]+")

        // Units written as symbols, which a Hangul run can follow like a noun (12.4%다)
        val UNITS = setOf('%', '℃', '°', '㎞', '㎡', '㎏', '$', '€', '¥', '￦')

        val CLOSING = setOf('”', '’', '"', '\'', ')', ']', '》', '〉', '」', '』')

        val PREFINAL_ORDER = mapOf("시" to 1, "으시" to 1, "었" to 2, "았" to 2, "였" to 2, "었었" to 2, "았었" to 2, "였었" to 2, "겠" to 3)

        val D_IRREGULAR = setOf("듣", "걷", "묻", "싣", "닫", "붇", "컫", "긷", "눋")
        val S_IRREGULAR = setOf("짓", "낫", "잇", "붓", "젓", "긋", "잣")

        val AUX_CONNECTIVES = setOf("어", "아", "여", "고", "게", "지")

        // Predicates that also follow 아/어 or 고 as auxiliaries, though the dictionary only has
        // them as verbs
        val AUXILIARIES = setOf("지", "보", "주", "드리", "버리", "놓", "두", "내", "가", "오", "있", "계시", "대", "싶", "말", "않", "못하", "되", "하")

        // Nouns contracted with a particle irregularly
        val CONTRACTED_NOUNS = mapOf(
            "누가" to ("누구" to "가"), "내가" to ("나" to "가"), "제가" to ("저" to "가"), "네가" to ("너" to "가"),
            "게" to ("것" to "이"), "건" to ("것" to "은"), "걸" to ("것" to "을"),
            "이게" to ("이것" to "이"), "그게" to ("그것" to "이"), "저게" to ("저것" to "이"),
            "이건" to ("이것" to "은"), "그건" to ("그것" to "은"), "이걸" to ("이것" to "을"), "그걸" to ("그것" to "을"),
            "뭘" to ("뭐" to "를"), "뭔" to ("뭐" to "는")
        )

        // 하고 and 하며 after a noun are more often a form of the noun's 하다 verb
        val RARE_PARTICLES = setOf(
            "고", "서", "면", "아", "야", "여", "며", "시여", "이여", "그려", "그래", "마는", "깨나", "게", "게로", "게서",
            "다가", "더러", "보고", "하고", "하며", "하고는", "커녕", "따라", "치고", "라", "니", "든", "란", "랑", "요"
        )

        // Bound nouns that follow a predicate's modifier form (볼 수, 할 것)
        val BOUND_NOUNS = listOf("수", "것", "거", "줄", "데", "뿐", "듯", "바", "리", "적", "채", "터", "지", "만큼", "때문")

        val CONSONANT_PARTICLES = setOf("이", "을", "은", "과", "아")
        val VOWEL_PARTICLES = setOf("가", "를", "는", "와", "야")
    }
}
