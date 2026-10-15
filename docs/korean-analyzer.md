# Korean analyzer: Open Korean Text vs Kiwi

Word lookups use [Open Korean Text](https://github.com/open-korean-text/open-korean-text) (OKT) to
find the dictionary forms of a tapped word. OKT is no longer developed and often misses
conjugations, so in October 2026 it was compared with [Kiwi](https://github.com/bab2min/Kiwi)
0.24.0, an actively developed analyzer with an Android library.

**Decision: keep OKT and patch its errors as they're found.** Kiwi is clearly more accurate and
much faster, but its model needs about 490 MB of memory on a device, plus a ~97 MB model on
storage. That's too much for a reading app. Look again if Kiwi gets a much smaller model.

## How it was measured

* **Dictionary examples:** 1,500 example sentences per part of speech from the bundled dictionary.
  Does the analysis of the sentence contain the entry's headword?
* **UD Korean treebanks:** the GSD and Kaist test sets (about 40,000 words), whose words are split
  into morphemes by hand. Lemma, part of speech and endings per word.
* **Real articles:** 268 saved articles from the built-in feeds, for speed and for reading the
  words the two analyze differently.
* **`LookupEvaluationTest`:** the lookup itself, with Kiwi's output turned into tokens like OKT's
  so `DictionaryRepository` could use either. Kiwi wasn't tuned for.
* **On an emulator** (x86_64): load time, memory from `dumpsys meminfo`, and the time to analyze
  what a lookup does.

The comparison code is kept on an unpublished branch.

## Results

Headword found in dictionary example sentences:

| Part of speech | OKT | Kiwi |
|---|---|---|
| Verbs | 29% | 84% |
| Adjectives | 57% | 85% |
| Adverbs | 74% | 86% |
| Nouns | 78% | 89% |

Most of OKT's verb misses are verbs made from a noun (공부하다), which OKT splits into the noun and
하다. Counting the noun as found, verbs are 73% for OKT and 89% for Kiwi.

On the treebanks (GSD / Kaist), Kiwi found the right lemma for 98% / 93% of words (OKT 85% / 82%)
and the right part of speech for 97% / 95% (OKT 77% / 75%). Kiwi also finds the endings, such as
-면서 in 높아지면서, for 94% / 89%. OKT doesn't output endings for conjugated words at all.

In `LookupEvaluationTest`, Kiwi showed the expected entry more often in every set: 285 vs 268 on
the dictionary dev set, 290 vs 274 on the test set and 67 vs 64 on real articles.

On the emulator:

| | OKT | Kiwi |
|---|---|---|
| Load time | 8–10 s | 3–4 s |
| Memory | ~50 MB | ~490 MB |
| Analysis per lookup, median | 35 ms | 0.8 ms |
| Size | | 11 MB library (arm64), 97 MB model |

Leaving out Kiwi's typo and multi-word dictionaries didn't reduce its memory on the device. Older
Kiwi models are about half the size but don't load in current versions.

## OKT errors to patch

Reading words in the articles where the two analyzers disagreed, Kiwi was right in 48 of 60 and
OKT in 3. OKT's common errors, worth fixing in `DictionaryRepository`:

* Very common conjugated words read as nouns: 통해, 지난, 크게, 다른, 대한, 앞서
* 는 after a closing quote mark (없다”는) read as a form of 늘다
* 되어 and 되었다 stemmed as 되어다, like 돼다, which is already fixed
* Verbs made from a noun split into the noun and 하다 (공부하러, 퇴출시키고), so the verb's own entry
  isn't shown
