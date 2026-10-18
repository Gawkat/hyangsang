# Context counts: attribution and license

`context-counts.tsv` contains counts derived from the training sets of two treebanks of the
[Universal Dependencies](https://universaldependencies.org/) project, release 2.18:

* **UD Korean GSD** (`ko_gsd-ud-train.conllu`),
  <https://github.com/UniversalDependencies/UD_Korean-GSD>, by Ryan McDonald, Joakim Nivre, Daniel
  Zeman, Jinho Choi, Jayeol Chun, Na-Rae Han and Jena Hwang, converted from the Google Universal
  Dependency Treebank v2.0 (original contributors Adam LaMontagne, Milan Souček, Timo Järvinen and
  Alessandra Radici).
* **UD Korean Kaist** (`ko_kaist-ud-train.conllu`),
  <https://github.com/UniversalDependencies/UD_Korean-Kaist>, by Jinho Choi, Jayeol Chun, Na-Rae
  Han and Jena Hwang, generated from the constituency trees of the KAIST Tree-Tagging Corpus
  (original contributor Key-Sun Choi).

Citations:

* Jayeol Chun, Na-Rae Han, Jena D. Hwang and Jinho D. Choi. Building Universal Dependency
  Treebanks in Korean. In Proceedings of the 11th International Conference on Language Resources
  and Evaluation (LREC 2018), Miyazaki, Japan, 2018.
* Ryan McDonald, Joakim Nivre, Yvonne Quirmbach-Brundage, Yoav Goldberg, Dipanjan Das, Kuzman
  Ganchev, Keith Hall, Slav Petrov, Hao Zhang, Oscar Täckström, Claudia Bedini, Núria Bertomeu
  Castelló and Jungmee Lee. Universal Dependency Annotation for Multilingual Parsing. In
  Proceedings of ACL 2013.

Both treebanks are licensed under the Creative Commons Attribution-ShareAlike 4.0 International
license (CC BY-SA 4.0), <https://creativecommons.org/licenses/by-sa/4.0/>.

## Changes

The counts were computed from the treebanks' gold morphemes by `tools/analyzer-counts`: how often
each class of word follows what the previous word ends in, how often each word form has each class
and each headword, and how often each ending occurs. Word forms seen once, and those the analyzer
can only read one way with the bundled dictionary, were left out.

The counts are shared under the same license, CC BY-SA 4.0. Creative Commons lists the GNU General
Public License version 3 as compatible with CC BY-SA 4.0
(<https://creativecommons.org/share-your-work/licensing-considerations/compatible-licenses/>), so
they are included in Hyangsang, which is licensed GPLv3 or later. The treebanks and the counts are
provided as is, without warranties of any kind.
