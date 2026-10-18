package dev.kettu.hyangsang.analyzer

/** A dictionary entry as the bundled dictionary has it. */
class DictionaryRow(val word: String, val partOfSpeech: String?, val vocabularyLevel: String?, val lexicalUnit: String?)

/**
 * What the analyzer can recognize: the dictionary's headwords, particles and endings, plus a few
 * forms the dictionary doesn't have as entries. Each word has a cost, lower for more common
 * words, from its vocabulary level.
 */
class Lexicon(rows: Iterable<DictionaryRow>) {
    internal class Ending(val form: String, val parts: List<String>, val prefinal: Boolean)

    /** Nouns, pronouns, numerals and bound nouns, with their tag and cost. */
    internal val nominals = HashMap<String, Pair<Tag, Float>>()

    /** Verb and adjective stems (공부하 for 공부하다), with each tag they have and its cost. */
    internal val predicates = HashMap<String, MutableMap<Tag, Float>>()
    internal val adverbs = HashMap<String, Float>()
    internal val determiners = HashMap<String, Float>()
    internal val interjections = HashMap<String, Float>()
    internal val prefixes = HashMap<String, Float>()
    internal val suffixes = HashMap<String, Float>()

    /** Stems of suffixes that make predicates from nouns (-하다, -스럽다), and 이다 */
    internal val predicateSuffixes = HashMap<String, Float>()
    internal val particles = HashSet<String>()
    internal val endings = HashMap<String, Ending>()
    internal var maxEnding = 0
    internal var maxParticle = 0

    init {
        val endingForms = HashSet<String>()
        val prefinals = HashSet<String>()
        for (row in rows) {
            val word = row.word
            val cost = levelCost(row.vocabularyLevel)
            val bare = word.trim('-')
            if (bare.isEmpty() || !bare.all { Hangul.isSyllable(it) || Hangul.isJamo(it) }) continue
            val isGrammar = row.lexicalUnit == "문법‧표현"
            when {
                row.partOfSpeech == "Ending" || isGrammar && word.startsWith("-") -> {
                    if (word.endsWith("-") && word.length > 1) {
                        // Grammar patterns that end in a pre-final ending (-어야겠-) too
                        if (bare in PREFINALS || isGrammar) prefinals += bare
                    } else if (!bare.endsWith("지다") || !isGrammar) {
                        endingForms += bare
                    }
                }
                row.partOfSpeech == "Particle" || isGrammar && !word.contains('-') -> particles += bare
                bare.any(Hangul::isJamo) -> Unit
                row.partOfSpeech == "Affix" -> when {
                    word.startsWith("-") && word.endsWith("-") -> Unit
                    word.startsWith("-") && bare.endsWith("다") -> predicateSuffixes.merge(bare.dropLast(1), cost, ::minOf)
                    word.startsWith("-") -> suffixes.merge(bare, cost, ::minOf)
                    word.endsWith("-") -> prefixes.merge(bare, cost, ::minOf)
                }
                row.lexicalUnit != "Word" -> Unit
                else -> when (row.partOfSpeech) {
                    "Noun" -> addNominal(word, Tag.NOUN, cost)
                    "Pronoun" -> addNominal(word, Tag.PRONOUN, cost)
                    "Numeral" -> addNominal(word, Tag.NUMERAL, cost)
                    "Bound Noun" -> addNominal(word, Tag.BOUND_NOUN, cost)
                    "Verb", "Adjective", "Auxiliary Verb", "Auxiliary Adjective" -> if (word.endsWith("다") && word.length > 1) {
                        val tag = when (row.partOfSpeech) {
                            "Verb" -> Tag.VERB
                            "Adjective" -> Tag.ADJECTIVE
                            "Auxiliary Verb" -> Tag.AUX_VERB
                            else -> Tag.AUX_ADJECTIVE
                        }
                        predicates.getOrPut(word.dropLast(1)) { HashMap() }.merge(tag, cost, ::minOf)
                    }
                    "Adverb" -> adverbs.merge(word, cost, ::minOf)
                    "Determiner" -> determiners.merge(word, cost, ::minOf)
                    "Interjection" -> interjections.merge(word, cost, ::minOf)
                }
            }
        }
        particles += EXTRA_PARTICLES
        endingForms += EXTRA_ENDINGS
        prefinals += PREFINALS
        // 이다 is the copula, analyzed on its own rather than as a verb or a particle
        predicates.remove("이")
        particles.removeAll(setOf("이다", "다", "라"))

        // Endings the dictionary has as one entry that are several endings (-었던), are also
        // given as their parts, for comparing with analyses that split them
        val basic = endingForms.filter { it.length <= 2 } + prefinals
        for (form in endingForms) endings[form] = Ending(form, split(form, basic) ?: listOf(form), prefinal = false)
        for (form in prefinals) endings[form] = Ending(form, listOf(form), prefinal = true)
        maxEnding = endings.keys.maxOf { it.length }
        maxParticle = particles.maxOf { it.length }
    }

    private fun addNominal(word: String, tag: Tag, cost: Float) {
        val current = nominals[word]
        if (current == null || cost < current.second) nominals[word] = tag to cost
    }

    private fun split(form: String, parts: List<String>): List<String>? {
        if (form.length <= 2) return null
        for (part in parts.sortedByDescending { it.length }) {
            if (part != form && form.startsWith(part)) {
                val rest = form.drop(part.length)
                if (rest in parts) return listOf(part, rest)
            }
        }
        return null
    }

    companion object {
        fun levelCost(level: String?): Float = when (level) {
            "Beginner" -> 0f
            "Intermediate" -> 0.2f
            "Advanced" -> 0.5f
            else -> 0.7f
        }

        val PREFINALS = setOf("시", "으시", "었", "았", "였", "었었", "았었", "였었", "겠")

        // Contracted particles the dictionary has no entries for
        val EXTRA_PARTICLES = setOf("에선", "에겐", "한텐", "론", "으론", "보단", "께선", "에서도", "이란", "에게선")

        val EXTRA_ENDINGS = setOf("ㄴ다는", "는다는", "다는", "라는", "으라는", "냐는", "느냐는", "자는", "려는", "으려는")
    }
}
