# Lookup analyzer

Kiwi analyzes Korean far better than Open Korean Text (OKT) but needs about 490 MB of memory on a
device (see [korean-analyzer.md](korean-analyzer.md)). In October 2026 a time-boxed prototype
tested whether a narrow analyzer, built for what a lookup needs and driven by the bundled
dictionary, could get close to Kiwi's accuracy at a small cost. Its code is now the `analyzer`
module. The app's lookups still use OKT.

**Decision: replace OKT with this analyzer, in stages (see [Plan](#plan)).** It's much more
accurate than OKT, close to Kiwi on most measures, uses about 14 MB of memory instead of OKT's
~50 MB, loads in about a second instead of ten, and recovers endings such as -면서. Its main weak
spot is proper names. End-to-end, lookups only get slightly better so far, because the lookup code
around the analyzer is tuned to OKT. There will be no fallback to OKT and no choice between
analyzers in the app.

## How it works

Given one word (eojeol) and the words around it, it returns ranked analyses: 공부하면서 →
공부하다 + -면서, 했다 → 하다 + -였- + -다, 추워요 → 춥다 + -어요.

* **Lexicon** (`Lexicon`): the dictionary's headwords, its ~500 endings and ~160 particles, and
  its grammar patterns that are endings (-ㄴ다는, -어야겠-). Each entry costs less the more basic
  its vocabulary level. About 20 contracted particles and endings were added by hand.
* **Candidates** (`LookupAnalyzer`): particles and endings are stripped from the right, and
  conjugation is undone: contraction with 아/어 (해, 와, 져, 됐), ㄹ dropping, the ㄷ, ㅂ, ㅅ, 르
  and ㅎ irregulars, 으 dropping, and pre-final endings (-시-, -었-, -겠-). Only stems the
  dictionary has are kept. A noun can be followed by particles, the copula, a suffix that makes a
  predicate (-하다, -스럽다) or another noun. Words the dictionary doesn't have are nouns, at a
  cost.
* **Ranking:** fewer morphemes and more basic words cost less. Counts from the UD Korean treebanks
  (`ContextModel`) add how often each surface form is each class of word (위해 is usually a verb)
  and each headword (사는 is usually 살다), how common each ending is, and what class follows what
  the previous word ends in. A bound noun after the word (볼 수) favors a modifier form.

## Results

Measured with the harness from the Kiwi comparison, extended with the prototype ("Lex"). Kiwi is
0.24.0 with sentence context.

UD Korean treebanks (test sets), per word with a content word:

| | OKT | Kiwi | Lex |
|---|---|---|---|
| GSD lemma | 84.5% | 97.7% | 95.3% |
| GSD part of speech | 77.1% | 96.9% | 93.6% |
| GSD ending recall / precision | 0% | 94.5% / 94.6% | 86.4% / 90.5% |
| Kaist lemma | 81.6% | 93.4% | 93.4% |
| Kaist part of speech | 75.4% | 95.2% | 93.1% |
| Kaist ending recall / precision | 0% | 88.6% / 91.1% | 81.9% / 86.6% |

The counts used for each treebank come from the other one, so the analyzer isn't measured on what
its counts came from. Without the counts, lemma accuracy is 94.8% (GSD) and 92.9% (Kaist).

Headword found in 1,500 dictionary example sentences per part of speech:

| | OKT | Kiwi | Lex |
|---|---|---|---|
| Verbs | 29.3% | 83.9% | 99.1% |
| Adjectives | 56.9% | 84.7% | 98.5% |
| Adverbs | 74.4% | 85.7% | 98.3% |
| Nouns | 78.1% | 88.5% | 99.3% |

This favors Lex by design, as its vocabulary is the dictionary's: it can only find headwords the
dictionary has, which is also all a lookup can show.

`LookupEvaluationTest` (expected entry shown first; in parentheses, the right headword shown):

| Set | OKT | Kiwi | Lex |
|---|---|---|---|
| Dictionary, dev | 268 (433) | 285 | 275 (451) / 464 |
| Dictionary, test | 274 (412) | 290 | 277 (436) / 455 |
| Real articles | 64 (88) | 67 | 64 (88) / 88 |

Lex shows the right headword more often than OKT. Whether it then shows the right homonym first is
up to `HomonymRanking`, which was tuned with OKT's tokens. All the differences on the article set
are homonyms of the right headword (수, 원, 이상).

The prototype's counts above were made from the treebanks' test sets. The counts in the `analyzer`
module are made from their training sets instead (see [Plan](#plan)):

| Set | No counts | Test-set counts | Training-set counts |
|---|---|---|---|
| Dictionary, dev | 275 (449) | 275 (451) | 274 (451) |
| Dictionary, test | 275 (433) | 277 (436) | 273 (433) |
| Real articles | 62 (88) | 64 (88) | 63 (88) |

The training sets have about nine times as much text, most of it Kaist's news, fiction and academic
text. Counts from GSD's training set alone got 275 (451) on the dev set. The differences are a few
cases each, and the counts' weights were picked with the test-set counts, before
`HomonymRanking` is re-tuned for the analyzer.

On an x86_64 emulator, in the same process, Lex measured first:

| | OKT | Lex |
|---|---|---|
| Load time | 10–11 s | 0.9–1.6 s (reading the dictionary 0.2–0.4 s, lexicon 0.4–0.6 s, counts 0.4–0.6 s) |
| Memory added (Java heap) | ~50 MB | ~14 MB |
| Tapped word, median / p95 | 1.3 / 7–8 ms | 0.38 / 1.3 ms |
| All a lookup analyzes, median / p95 / max | 39–43 / 105–108 / 530 ms | 3.7–3.9 / 44–47 / 640–890 ms |

What a lookup analyzes includes the whole sentence, for the nouns used in ranking. The slowest
sentences are long lists of agency names (한국해외인프라도시개발지원공사, ...), slow for both.
On a desktop JVM, Lex analyzes a sentence in 0.20 ms (median), Kiwi in 0.47 ms and OKT in 1.33 ms.

## Where it goes wrong

On 268 saved articles, Lex's main headword isn't among Kiwi's for 9% of words. In a sample of 60 of
those read by hand, Kiwi was right in about 27, Lex in about 6, and the rest were ties or
differences in how far words are split (Lex keeps the dictionary's 자극적 where Kiwi has 자극 +
-적). In the sample and in the treebank misses:

* **Proper names** (about half of Kiwi's wins): names are split into dictionary words, as in
  한지민 → 한지 + 민, 신의주 → 신의 + 주, 고베 → 고- + 베. Kiwi has a large list of names. The
  lookup splits nouns itself, so this mostly shows unrelated entries rather than hiding the right
  one.
* **Homographs that need context:** a noun or a conjugated form (볼 cheek / 보다, 담은, 낼, 가게,
  투자가), and verbs with the same forms (사는 살다 / 사다). The treebank counts fix common
  cases; the rest needs a real language model.
* **After numbers:** 12.4%다, 200여대가 (read as 여대), 1일부로 (일부).
* **Missing rules:** 우 and 러 irregulars (퍼, 이르러), ㅎ forms in ㅒ (하얘), contractions
  such as 순탄치 (순탄하지) and 누군가, colloquial spellings (했구요, 알앗는데).
* **Conventions, not errors:** the dictionary has 다른 and 이런 as determiners and 없이 as an
  adverb, which the treebanks analyze as adjectives.

## Plan

OKT is replaced in stages, each its own pull request:

1. **The analyzer as a module (done):** the `analyzer` module, with unit tests for each
   conjugation and contraction rule. The app has a `KoreanAnalyzer` interface with OKT
   (`OktAnalyzer`) as the default, and an adapter for the new analyzer (`LexiconKoreanAnalyzer`)
   that lookups don't use yet. `LookupEvaluationTest` can evaluate it (see below).
2. **Counts (done):** made from the GSD and Kaist training sets (UD 2.18) by
   `tools/analyzer-counts`, and committed as `context-counts.tsv` in the `analyzer` module's
   resources, with `context-counts-attribution.md` next to it and an attribution in the app's
   settings. Only words the analyzer can read in more than one way, and that were seen at least
   twice, are kept. That leaves out 28% of the counts and changes none of the evaluation results.
   The UD Korean treebanks are licensed CC BY-SA 4.0, which Creative Commons lists as compatible
   with GPLv3 (one-way), so the derived counts ship under CC BY-SA 4.0 with attribution.
3. **Switch lookups to it:** use it through `KoreanAnalyzer` in `DictionaryRepository`, and remove
   the OKT patches it makes unnecessary (misread predicates, 돼다 and 되어다, 늘다 after quotes,
   noun + 하다 splits). Give `HomonymRanking` its finer parts of speech (bound noun, determiner) and
   re-tune it, then raise the lookup baselines. Load the lexicon once, from the dictionary database
   (~0.3 s) or a compact asset, and share it with the lookup. Context nouns only need the
   sentence's nouns, which would also cap the slow sentences.
4. **Coverage, then remove OKT:** a list of names, or a rule for surname + given name; the missing
   irregulars; numbers and units. Then remove OKT and the Scala library it depends on, its 1.7 MB
   jar and 5.2 MB of Scala before shrinking.

Showing endings such as -면서 in the lookup sheet comes after the switch. Tuning the analyzer
belongs in the `analyzer` module, not in the app's lookup code.

## How it was measured

The comparison harness, which runs OKT, Kiwi and the analyzer on the treebanks, the dictionary
examples and saved articles, and measures them on a device, is kept unpublished, like the Kiwi
comparison's. `LookupEvaluationTest` evaluates the analyzer instead of OKT with:

```bash
./gradlew :app:testDebugUnitTest --tests '*LookupEvaluationTest' -PlookupAnalyzer=lexicon -PlexContext=<counts file>
```

`-PlexContext` is optional. Without counts, the analyzer gets 275 (449), 275 (433) and 62 (88) on
the three sets. The committed counts are
`analyzer/src/main/resources/dev/kettu/hyangsang/analyzer/context-counts.tsv`; CONTRIBUTING.md
describes how to regenerate them.

Some caveats:

* The rules and costs were adjusted while reading misses on the treebank test sets and the
  dictionary dev set, so those numbers are somewhat optimistic. The weights of the counts were
  picked from four settings on the treebanks. The dictionary test set was run twice: 276 halfway
  and 277 at the end, after fixes for speed and compound nouns.
* The dictionary sets and the krdict measure use counts made from both treebanks, which don't
  contain those sentences.
* Memory is the Java heap after garbage collection; PSS moved too much between runs to compare
  (the analyzer added 12–22 MB, OKT 52–105 MB).
