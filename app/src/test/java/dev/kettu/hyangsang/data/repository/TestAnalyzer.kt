package dev.kettu.hyangsang.data.repository

import dev.kettu.hyangsang.analyzer.ContextModel
import dev.kettu.hyangsang.analyzer.DictionaryRow
import dev.kettu.hyangsang.analyzer.Lexicon
import dev.kettu.hyangsang.analyzer.LookupAnalyzer
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses

private val counts by lazy { ContextModel.bundled() }

// Particles and endings the tests' words are written with, which the fake dictionaries leave out
private val grammar = listOf(
    "이", "가", "은", "는", "을", "를", "의", "에", "에서", "에게", "로", "으로", "와", "과", "도", "만",
    "까지", "부터", "나", "이나"
).map { DictionaryRow(it, "Particle", null, "Word") } + listOf(
    "-다", "-ㄴ다", "-는다", "-어", "-아", "-여", "-고", "-며", "-면", "-으면", "-면서", "-러", "-으러",
    "-ㄴ", "-은", "-는", "-ㄹ", "-을", "-던", "-게", "-지", "-어서", "-아서", "-여서", "-ㅂ니다",
    "-습니다", "-어요", "-아요", "-여요", "-었-", "-았-", "-였-", "-겠-", "-시-"
).map { DictionaryRow(it, "Ending", null, "Word") }

/**
 * The app's analyzer, with [entries] and common particles and endings as its lexicon, for
 * tests with a fake dictionary. Entries without a lexical unit are words.
 */
internal fun testAnalyzer(entries: List<DictionaryWithSenses>): Lazy<KoreanAnalyzer> {
    val rows = entries.map {
        DictionaryRow(it.entry.word, it.entry.partOfSpeech, it.entry.vocabularyLevel, it.entry.lexicalUnit ?: "Word")
    }
    return lazyOf(LexiconKoreanAnalyzer(LookupAnalyzer(Lexicon(rows + grammar), counts)))
}
