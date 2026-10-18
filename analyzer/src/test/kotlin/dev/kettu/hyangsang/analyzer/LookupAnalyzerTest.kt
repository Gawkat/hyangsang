package dev.kettu.hyangsang.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupAnalyzerTest {
    private val analyzer = LookupAnalyzer(Lexicon(DICTIONARY))

    /** The best analysis of [word], as the headwords of its morphemes. */
    private fun best(word: String, previous: String? = null, next: String? = null, analyzer: LookupAnalyzer = this.analyzer) =
        analyzer.analyze(word, previous, next).first().morphemes.joinToString(" + ") { it.headword }

    private fun all(word: String) = analyzer.analyze(word).map { a -> a.morphemes.joinToString(" + ") { it.headword } }

    @Test
    fun `endings are split off`() {
        assertEquals("공부하다 + -면서", best("공부하면서"))
        assertEquals("가다 + -고", best("가고"))
    }

    @Test
    fun `stems contract with 아 and 어`() {
        assertEquals("하다 + -였- + -다", best("했다"))
        assertEquals("통하다 + -여", best("통해"))
        assertEquals("되다 + -어", best("돼"))
        assertEquals("되다 + -어", best("되어"))
        assertEquals("되다 + -었- + -다", best("됐다"))
        assertEquals("오다 + -아요", best("와요"))
        assertEquals("지다 + -어", best("져"))
        assertEquals("가다 + -아", best("가"))
    }

    @Test
    fun `contracted syllables span both morphemes`() {
        val (stem, prefinal, ending) = analyzer.analyze("했다").first().morphemes
        assertEquals(0 to 1, stem.start to stem.end)
        assertEquals(0 to 1, prefinal.start to prefinal.end)
        assertEquals(1 to 2, ending.start to ending.end)
    }

    @Test
    fun `final consonants start endings`() {
        assertEquals("지나다 + -ㄴ", best("지난"))
        assertEquals("가다 + -ㄴ다", best("간다"))
        assertEquals("하다 + -ㅂ니다", best("합니다"))
        assertEquals("하다 + -ㄹ", best("할"))
    }

    @Test
    fun `ㅂ irregular`() {
        assertEquals("춥다 + -어요", best("추워요"))
        assertEquals("돕다 + -아", best("도와"))
        assertEquals("춥다 + -은", best("추운"))
    }

    @Test
    fun `ㄷ irregular`() {
        // 들어 is also a form of 들다, which is regular and so first without context
        assertTrue(all("들어").toString(), "듣다 + -어" in all("들어"))
        assertEquals("듣다 + -으면", best("들으면"))
    }

    @Test
    fun `르 irregular`() {
        assertEquals("부르다 + -어", best("불러"))
        assertEquals("모르다 + -아", best("몰라"))
    }

    @Test
    fun `ㅅ irregular`() {
        assertEquals("짓다 + -으면", best("지으면"))
        // 지어 is also a form of 지다, which is regular and so first
        assertTrue(all("지어").toString(), "짓다 + -어" in all("지어"))
        assertEquals("낫다 + -아", best("나아"))
    }

    @Test
    fun `ㅎ irregular`() {
        assertEquals("하얗다 + -ㄴ", best("하얀"))
        assertEquals("그렇다 + -ㄴ", best("그런"))
        assertEquals("그렇다 + -어", best("그래"))
        assertEquals("놓다 + -아", best("놔"))
    }

    @Test
    fun `ㄹ dropping`() {
        assertEquals("만들다 + -ㄴ", best("만든"))
        assertEquals("만들다 + -ㅂ니다", best("만듭니다"))
        // 사는 is also a form of 사다, which is regular and so first without context
        assertEquals("사다 + -는", best("사는"))
        assertTrue(all("사는").toString(), "살다 + -는" in all("사는"))
    }

    @Test
    fun `context counts choose between readings`() {
        val context = ContextModel(sequenceOf("lemma\t사는\t살다\t10"))
        assertEquals("살다 + -는", best("사는", analyzer = LookupAnalyzer(Lexicon(DICTIONARY), context)))
    }

    @Test
    fun `pre-final endings`() {
        assertEquals("하다 + -시- + -었- + -습니다", best("하셨습니다"))
        assertEquals("하다 + -시- + -어", best("하셔"))
        assertEquals("가다 + -시- + -ㅂ니다", best("가십니다"))
        assertEquals("가다 + -았- + -다", best("갔다"))
        // A pre-final ending needs an ending after it
        assertTrue(all("했").none { it.endsWith("-") })
    }

    @Test
    fun `auxiliary predicates`() {
        assertEquals("높다 + -아 + 지다 + -면서", best("높아지면서"))
    }

    @Test
    fun copula() {
        assertEquals("학교 + 이다 + -에요", best("학교예요"))
        assertEquals("사람 + 이다 + -에요", best("사람이에요"))
        // Dropped after a vowel
        assertEquals("학교 + 이다 + -다", best("학교다"))
    }

    @Test
    fun particles() {
        assertEquals("학교 + 에", best("학교에"))
        assertEquals("사람 + 이", best("사람이"))
        assertEquals("학교 + 에 + 도", best("학교에도"))
        // Contracted particles the dictionary lacks
        assertEquals("학교 + 에선", best("학교에선"))
    }

    @Test
    fun `particles contracted with a noun`() {
        assertEquals("나 + 는", best("난"))
        assertEquals("것 + 이", best("게"))
        assertEquals("것 + 을", best("걸"))
    }

    @Test
    fun `particles after a quote`() {
        val morphemes = analyzer.analyze("없다”는").first().morphemes
        assertEquals("없다 + -다 + 는", morphemes.joinToString(" + ") { it.headword })
        assertEquals(listOf(Tag.ADJECTIVE, Tag.ENDING, Tag.PARTICLE), morphemes.map { it.tag })
    }

    @Test
    fun `counters after a number`() {
        val morphemes = analyzer.analyze("3명이").first().morphemes
        assertEquals(listOf("3", "명", "이"), morphemes.map { it.form })
        assertEquals(listOf(Tag.NUMBER, Tag.BOUND_NOUN, Tag.PARTICLE), morphemes.map { it.tag })
    }

    @Test
    fun `nouns with a predicate suffix`() {
        val analysis = analyzer.analyze("행복스러운").first()
        assertEquals("행복 + -스럽다 + -은", analysis.morphemes.joinToString(" + ") { it.headword })
        assertEquals(listOf("행복", "행복스럽다"), analysis.headwords)
        assertEquals("A", analysis.contentClass)
    }

    @Test
    fun `unknown words are nouns`() {
        val morphemes = analyzer.analyze("한지민이").first().morphemes
        assertEquals(listOf(Tag.UNKNOWN, Tag.PARTICLE), morphemes.map { it.tag })
        assertEquals("한지민", morphemes[0].form)
    }

    @Test
    fun `a bound noun after a word favors a modifier form`() {
        assertEquals("ETM", analyzer.analyze("할", next = "수").first().lastClass)
    }

    @Test
    fun `a bound noun after a word outweighs the counts`() {
        // 산 alone is far more often the noun
        val lexicon = Lexicon(DICTIONARY + word("산", "Noun"))
        val analyzer = LookupAnalyzer(lexicon, ContextModel.bundled())
        assertEquals("산", analyzer.analyze("산", "어제").first().content?.headword)
        assertEquals("사다", analyzer.analyze("산", "어제", "것이다").first().content?.headword)
    }

    @Test
    fun `punctuation is left out`() {
        assertEquals("학교 + 에", best("“학교에,"))
        assertTrue(analyzer.analyze("...").isEmpty())
    }

    @Test
    fun `endings that are several endings keep their parts`() {
        val ending = analyzer.analyze("됐다면").first().morphemes.last()
        assertEquals("었다면", ending.form)
        assertEquals(listOf("었", "다면"), ending.parts)
    }

    @Test
    fun `a stem that is a verb and an adjective is both`() {
        val lexicon = Lexicon(DICTIONARY + listOf(word("크다", "Verb"), word("크다", "Adjective"), word("-게", "Ending")))
        val tags = LookupAnalyzer(lexicon).analyze("크게").map { it.morphemes.first().tag }
        // Verbs win ties, and the counts tell them apart
        assertEquals(listOf(Tag.VERB, Tag.ADJECTIVE), tags.take(2))
        assertEquals(Tag.ADJECTIVE, LookupAnalyzer(lexicon, ContextModel.bundled()).analyze("크게").first().morphemes.first().tag)
    }

    @Test
    fun `number words between numbers are not prefixes`() {
        val lexicon = Lexicon(DICTIONARY + listOf(word("조", "Numeral"), word("조-", "Affix"), word("제-", "Affix")))
        val analyzer = LookupAnalyzer(lexicon)
        assertEquals(Tag.NUMERAL, analyzer.analyze("5조5").first().morphemes[1].tag)
        assertEquals(Tag.PREFIX, analyzer.analyze("제253조").first().morphemes[0].tag)
    }

    @Test
    fun `우 irregular`() {
        val lexicon = Lexicon(DICTIONARY + word("푸다", "Verb"))
        assertEquals("푸다 + -어", best("퍼", analyzer = LookupAnalyzer(lexicon)))
        assertEquals("푸다 + -었- + -다", best("펐다", analyzer = LookupAnalyzer(lexicon)))
    }

    @Test
    fun `러 irregular`() {
        val lexicon = Lexicon(DICTIONARY + listOf(word("이르다", "Verb"), word("-러", "Ending")))
        // Not 이르다 + -러, in order to
        assertEquals("이르다 + -어", best("이르러", analyzer = LookupAnalyzer(lexicon)))
        assertEquals("이르다 + -었- + -다", best("이르렀다", analyzer = LookupAnalyzer(lexicon)))
    }

    @Test
    fun `ㅎ irregular in ㅒ`() {
        assertEquals("하얗다 + -아", best("하얘"))
        assertEquals("하얗다 + -았- + -다", best("하얬다"))
    }

    @Test
    fun `으 dropping before a separate 아`() {
        val lexicon = Lexicon(DICTIONARY + listOf(word("모으다", "Verb"), word("-아서", "Ending")))
        assertEquals("모으다 + -아서", best("모아서", analyzer = LookupAnalyzer(lexicon)))
    }

    @Test
    fun `하 contracted in adjectives`() {
        val lexicon = Lexicon(
            DICTIONARY + listOf(
                word("순탄하다", "Adjective"), word("깨끗하다", "Adjective"), word("간편하다", "Adjective"),
                word("-지", "Ending"), word("-게", "Ending"),
                word("전망", "Noun"), word("전망하다", "Verb"), word("-치", "Affix")
            )
        )
        val analyzer = LookupAnalyzer(lexicon)
        assertEquals("순탄하다 + -지", best("순탄치", analyzer = analyzer))
        assertEquals("간편하다 + -게", best("간편케", analyzer = analyzer))
        // After ㄱ, ㅂ and ㅅ, 하 drops
        assertEquals("깨끗하다 + -지", best("깨끗지", analyzer = analyzer))
        // Verbs don't contract, and nouns often end in -치 (forecast)
        assertEquals("전망 + -치", best("전망치", analyzer = analyzer))
    }

    @Test
    fun `copula contracted with a noun`() {
        val lexicon = Lexicon(DICTIONARY + listOf(word("누구", "Pronoun"), word("-ㄴ가", "Ending"), word("아메리카", "Noun")))
        val analyzer = LookupAnalyzer(lexicon)
        assertEquals("누구 + 이다 + -ㄴ가", best("누군가", analyzer = analyzer))
        // Only with an ending of its own, not a modifier ending followed by a particle
        assertTrue(analyzer.analyze("아메리칸은").first().morphemes.none { it.tag == Tag.COPULA })
    }

    @Test
    fun `colloquial endings`() {
        assertEquals("하다 + -였- + -구요", best("했구요"))
    }

    @Test
    fun `counters are preferred after a number`() {
        val lexicon = Lexicon(
            DICTIONARY + listOf(
                word("대", "Bound Noun"), word("대가", "Noun", "Intermediate"), word("여대", "Noun", "Advanced"),
                word("-여", "Affix", "Intermediate"), word("일", "Noun"), word("일", "Bound Noun"), word("일부", "Noun"),
                word("-부", "Affix", "Advanced"), word("로", "Particle")
            )
        )
        val analyzer = LookupAnalyzer(lexicon)
        // 대가 is a price, 여대 a women's university
        assertEquals("10 + 대 + 가", best("10대가", analyzer = analyzer))
        assertEquals("200 + -여 + 대 + 가", best("200여대가", analyzer = analyzer))
        // Taking effect on the 1st, not 일부, some
        assertEquals("1 + 일 + -부 + 로", best("1일부로", analyzer = analyzer))
    }

    @Test
    fun `number words continue a number`() {
        val lexicon = Lexicon(
            DICTIONARY + listOf(
                word("만", "Numeral"), word("천", "Numeral"), word("원", "Bound Noun"), word("만원", "Noun", "Advanced"),
                word("천만", "Numeral", "Intermediate")
            )
        )
        val analyzer = LookupAnalyzer(lexicon)
        // 만원 is a full house
        assertEquals(listOf("10", "만", "원"), analyzer.analyze("10만원").first().morphemes.map { it.form })
        assertEquals(listOf("2", "천", "만", "원"), analyzer.analyze("2천만원").first().morphemes.map { it.form })
        assertEquals("원", analyzer.analyze("2천만원").first().content?.form)
        assertEquals(listOf(Tag.NUMBER, Tag.NUMERAL), analyzer.analyze("300만").first().morphemes.map { it.tag })
    }

    @Test
    fun `조 before a number is part of it`() {
        val lexicon = Lexicon(
            DICTIONARY + listOf(
                word("조", "Numeral", "Intermediate"), word("조", "Bound Noun", "Intermediate"), word("천", "Numeral"),
                word("억", "Numeral"), word("원", "Bound Noun")
            )
        )
        val analyzer = LookupAnalyzer(lexicon)
        // A trillion, not the counter for articles (제2조)
        assertEquals(Tag.NUMERAL, analyzer.analyze("5조5천억원").first().morphemes[1].tag)
        assertEquals("조", analyzer.analyze("5조5천억원").first().content?.form)
    }

    @Test
    fun `counters keep the tag of their most basic entry`() {
        val lexicon = Lexicon(DICTIONARY + listOf(word("분", "Noun"), word("분", "Bound Noun", "Intermediate"), word("분기", "Noun")))
        // As other nouns do, so lookups treat them the same, such as for the sentence's context words
        val counter = LookupAnalyzer(lexicon).analyze("10분에").first().morphemes[1]
        assertEquals("분" to Tag.NOUN, counter.form to counter.tag)
    }

    @Test
    fun `the copula after a unit`() {
        val lexicon = Lexicon(DICTIONARY + word("다", "Adverb"))
        assertEquals("12.4 + 이다 + -다", best("12.4%다", analyzer = LookupAnalyzer(lexicon)))
    }

    @Test
    fun `제 between article numbers`() {
        val lexicon = Lexicon(
            DICTIONARY + listOf(word("조", "Bound Noun", "Intermediate"), word("조제", "Noun", "Advanced"), word("제-", "Affix"), word("호", "Bound Noun"))
        )
        assertEquals("제- + 2 + 조 + 제- + 1 + 호", best("제2조제1호", analyzer = LookupAnalyzer(lexicon)))
    }

    @Test
    fun names() {
        val lexicon = Lexicon(
            DICTIONARY + listOf(
                word("한지", "Noun", "Advanced"), word("-민", "Affix", "Intermediate"),
                word("김", "Noun"), word("-정", "Affix", "Advanced"),
                word("이란", "Noun", null), word("과", "Particle"),
                word("고양", "Noun", "Advanced"), word("-시", "Affix", "Advanced"),
                word("한국", "Noun", null), word("은행", "Noun")
            )
        )
        val analyzer = LookupAnalyzer(lexicon)
        assertEquals("한지민 + 이", best("한지민이", analyzer = analyzer))
        assertEquals(Tag.UNKNOWN, analyzer.analyze("한지민이").first().morphemes[0].tag)
        assertEquals("김정은", best("김정은", analyzer = analyzer))
        // A word and a particle, ending or suffix (Iran, Goyang City), or a compound
        assertEquals("이란 + 과", best("이란과", analyzer = analyzer))
        assertEquals("고양 + -시", best("고양시", analyzer = analyzer))
        assertEquals("한국 + 은행 + 은", best("한국은행은", analyzer = analyzer))
    }

    @Test
    fun `prefixes rarely come before nouns of one syllable`() {
        val lexicon = Lexicon(DICTIONARY + listOf(word("고-", "Affix", "Intermediate"), word("베", "Noun", "Intermediate")))
        val morphemes = LookupAnalyzer(lexicon).analyze("고베에서").first().morphemes
        assertEquals(listOf("고베", "에서"), morphemes.map { it.form })
    }

    companion object {
        private fun word(word: String, pos: String, level: String? = "Beginner") = DictionaryRow(word, pos, level, "Word")

        val DICTIONARY = listOf(
            word("공부", "Noun"), word("학교", "Noun"), word("사람", "Noun"), word("통", "Noun", "Intermediate"),
            word("행복", "Noun"), word("나", "Pronoun"), word("것", "Bound Noun"), word("명", "Bound Noun"),
            word("수", "Bound Noun"),
            word("공부하다", "Verb"), word("하다", "Verb"), word("되다", "Verb"), word("통하다", "Verb"),
            word("가다", "Verb"), word("오다", "Verb"), word("지다", "Verb"), word("지나다", "Verb"),
            word("듣다", "Verb"), word("들다", "Verb"), word("부르다", "Verb"), word("모르다", "Verb"),
            word("돕다", "Verb"), word("짓다", "Verb"), word("낫다", "Verb", "Intermediate"),
            word("놓다", "Verb"), word("살다", "Verb"), word("사다", "Verb"), word("만들다", "Verb"),
            word("춥다", "Adjective"), word("하얗다", "Adjective"), word("그렇다", "Adjective"),
            word("높다", "Adjective"), word("없다", "Adjective"),
            word("-스럽다", "Affix"),
            word("이", "Particle"), word("가", "Particle"), word("은", "Particle"), word("는", "Particle"),
            word("을", "Particle"), word("를", "Particle"), word("에", "Particle"), word("에서", "Particle"),
            word("도", "Particle"),
            word("-면서", "Ending"), word("-으면", "Ending"), word("-면", "Ending"), word("-고", "Ending"),
            word("-다", "Ending"), word("-어", "Ending"), word("-아", "Ending"), word("-여", "Ending"),
            word("-어요", "Ending"), word("-아요", "Ending"), word("-에요", "Ending"),
            word("-ㄴ", "Ending"), word("-은", "Ending"), word("-는", "Ending"), word("-ㄹ", "Ending"),
            word("-을", "Ending"), word("-ㄴ다", "Ending"), word("-ㅂ니다", "Ending"), word("-습니다", "Ending"),
            word("-다면", "Ending"), word("-었다면", "Ending"),
            word("-었-", "Ending"), word("-았-", "Ending"), word("-였-", "Ending"), word("-시-", "Ending")
        )
    }
}
