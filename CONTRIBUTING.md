# Contributing

Notes for working on Hyangsang: guidelines for contributing, how the project is laid out, how to
test it, and what else needs to change when you change certain things.

## Contribution guidelines

Thank you for considering contributing to Hyangsang!

### Suggesting features

Missing something that should be there?

* Check the [discussions](https://github.com/Gawkat/hyangsang/discussions) to see if someone has
  already suggested it.
* Describe the feature in as much detail as possible.
* Explain why it's useful and how it fits in with the rest of the app.

### Reporting issues

Found a bug?

* Check the [open issues](https://github.com/Gawkat/hyangsang/issues) to make sure it hasn't been
  reported already.
* Make sure the bug hasn't been fixed already. Use the latest version of the app to reproduce it.
* Open a new issue and describe the bug in as much detail as possible. Also include steps to
  reproduce it and details about your environment (e.g. app version, Android version, screen size).

### Submitting pull requests

Want to contribute with a patch?

1. Before writing any major code, please comment on an existing issue or discussion, or open a new
   one to sync with the maintainers.
2. Fork the repository to your own GitHub account and clone it locally.
3. Always base your branch off `main`. Use a clear naming convention:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/short-bug-description
   ```
   Other prefixes, such as `chore/`, `docs/`, `refactor/`, are fine if they fit the change better.
4. Implement your changes. Make sure the project still builds/runs and that you haven't broken
   existing functionality. Run the tests (see [Tests](#tests)) and check
   [When you change...](#when-you-change) for anything else your change needs. CI runs the tests on
   every pull request too.
5. Write clean, concise commit messages. Pull requests are rebase merged, so each commit lands on
   `main` as it is: squash fixups before submitting.
6. Submit your PR against our `main` branch. Provide a brief description of what you changed and
   link it to the relevant issue (e.g., `Closes #12`).

### Licensing

Hyangsang is licensed under the GNU General Public License, version 3 or later (see
[LICENSE](LICENSE)). By submitting a pull request, you agree that your contribution is licensed
under the same terms. Don't include code or content that you can't license that way, such as
articles copied from news sites.

## Project layout

* `app` - the Android app
* `parser` - a plain Kotlin library that turns article pages into content blocks, with a parser per
  news site and a generic fallback, and EPUB files into chapters of content blocks
* `analyzer` - a plain Kotlin library that splits Korean words into dictionary headwords, particles
  and endings, built on the bundled dictionary. Word lookups use it (see
  [docs/lookup-analyzer.md](docs/lookup-analyzer.md)).
* `tools/dictionary-generator` - builds the bundled dictionary from the NIKL dictionary data
* `tools/analyzer-counts` - builds the analyzer's context counts from the UD Korean treebanks

## Tests

```bash
./gradlew :app:testDebugUnitTest :parser:test :analyzer:test
```

CI also runs Android Lint, which checks the `parser` and `analyzer` modules along with the app:

```bash
./gradlew :app:lintDebug
```

The report is in `app/build/reports`. Issues that were there before lint ran in CI are listed in
`app/lint-baseline.xml` and don't fail it. When you fix one, run `./gradlew :app:updateLintBaseline`
to drop it from the baseline. Don't use it to hide new issues.

The instrumented tests, such as the Room migration tests, need a running emulator or device:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Running them uninstalls the app from the device, along with its data.

Some parser tests run against saved copies of real articles in `parser/src/test/resources`, which is
ignored by git since the articles belong to the news sites. Without them those tests are skipped,
and Gradle reports them as skipped. To run them, save an article page from the site under the name
the test reads, such as `bbc_sample.html`. They check details of specific articles, so expect to
adjust the assertions for a different article.

The EPUB parser's tests build small EPUB files in the test, so they need nothing saved. Real books
are worth trying too, since every tool that makes EPUBs lays them out its own way. Put DRM-free
`.epub` files in `parser/src/test/resources/epub` and `EpubSamplesTest` parses each one and prints
its chapters, failing on books it can't read. Without the folder the test is skipped.

`LookupEvaluationTest` measures how often word lookups show the right dictionary entry, and is
skipped without the bundled dictionary. It runs three sets of cases in `app/src/test/resources`:

* `lookup-eval-krdict-dev.tsv` and `lookup-eval-krdict-test.tsv` - example sentences from the
  dictionary, for nouns with more than one common meaning.
* `lookup-eval.tsv` - sentences from real articles, with the category of their feed. The file is
  ignored by git like the saved articles, and its test is skipped without it.

The format of the sets is described in the test.

The analyzer uses its bundled context counts. To evaluate it with other counts, add
`-PlexContext=<counts file>`:

```bash
./gradlew :app:testDebugUnitTest --tests '*LookupEvaluationTest' -PlexContext=<counts file>
```

To collect cases for `lookup-eval.tsv` while reading, turn on Report wrong lookups in the app's
settings. The lookup sheet then gets a button for saving a lookup that shows the wrong entry. Switch
to the right entry first, if the sheet has it, and it's saved as the expected one. Saved lookups are
shared from the settings as tab-separated rows. Their first five columns are the evaluation set's,
so a row with an expected entry can be added to the set as it is. The rest, such as how the analyzer
split the word, help find the cause.
See [Dictionary lookup ranking](#dictionary-lookup-ranking) for how to use them.

## Release builds

Release builds are signed with the key configured in `keystore.properties` at the repository root,
which is ignored by git:

```properties
storeFile=C:/path/outside/the/repository/hyangsang-release.jks
storePassword=...
keyAlias=hyangsang
keyPassword=...
```

Without it, `./gradlew :app:assembleRelease` builds an unsigned APK.

## When you change...

### An article parser

Raise `ArticleParser.VERSION` in
`parser/src/main/kotlin/dev/kettu/hyangsang/parser/ArticleParser.kt` when the change should reach
articles already stored, such as a fix for missing or broken content. Articles parsed with an older
version are parsed again in the background the next time they're opened.

A parser change that only matters for new articles doesn't need a bump. Each bump re-fetches every
article once, as it's opened.

### The EPUB parser

Raise `EpubParser.VERSION` in
`parser/src/main/kotlin/dev/kettu/hyangsang/parser/epub/EpubParser.kt` when the change should reach
books already imported. Each book keeps the EPUB it was imported from, and books parsed with an
older version are parsed again from it the next time they're opened. The reader stays at the same
chapter.

EPUB files are untrusted input. Keep reading the archive through `EpubArchive`, which caps what
it reads against zip bombs, and never write a file under a name taken from the archive.

### The built-in feeds

A feed from a new site needs a `DefaultSource`, with a description in both string files. Follow the
rules in [docs/feed-sources.md](docs/feed-sources.md), and add a citation for each claim there.

When adding feeds, raise `DefaultData.VERSION` and give the new feeds the new version as their
`addedIn`, so existing users are offered them. If it was already raised since the last release, use
that version instead: a version that has shipped must not get more feeds, since users who saw it
wouldn't be offered them. Never lower or reuse it, even when removing feeds.

A feed in a new `DefaultCategory` also needs a place in `FeedTopic`, so the first-run setup can pick
it. `FeedTopicTest` fails until it has one.

### The user database schema

For changes to the entities in `HyangsangDatabase` (articles, feeds, vocabulary):

1. Raise `version` in `HyangsangDatabase`
2. Add a `MIGRATION_x_y` to `Migrations.kt` and to `ALL_MIGRATIONS`
3. Build, and commit the schema Room exports to `app/schemas/`
4. Add a test for the migration to `MigrationTest`

Without a migration, updating the app crashes for existing users, since Room only falls back to a
destructive migration from versions before 13.

### The books database schema

Imported books are in a database of their own, `BooksDatabase`, so they can be left out of cloud
backups (see `data_extraction_rules.xml` and `backup_rules.xml`). Change its entities (books and
their chapters) the same way as the user database's: raise its `version`, add a migration to its
builder, commit the schema Room exports to `app/schemas/`, and test the migration. It has no
destructive fallback, so a missing migration crashes the app for anyone with books.

Keep a chapter's content well under 2 MB, the most a database row can have when it's read back.
The parser splits long chapters at `EpubParser.MAX_CHAPTER_CHARS` for this.

### The bundled dictionary

The dictionary lives in its own database, `DictionaryDatabase`, which is never migrated and only
replaced from the `dictionary.db` asset. Neither the asset nor its source data is committed.

To regenerate it:

1. Download the dictionary's JSON files from
   the [Korean-English Learners' Dictionary site](https://krdict.korean.go.kr/kor/mainAction), from
   사전 전체 내려받기 at the bottom of the page. The files total about 1 GB.
2. Put them in `tools/dictionary-generator/data`, which is ignored by git. Keep them out of
   `app/src/main/assets`, which would package them into the app.
3. Run the generator with the new version. It writes `app/src/main/assets/dictionary.db`:

   ```bash
   ./gradlew :tools:dictionary-generator:run --args="2026091901"
   ```

   A different input folder can be given as a second argument, relative to the repository root.

Installed copies are only replaced when `DICTIONARY_VERSION` changes, so raise it whenever the asset
is regenerated. The version is the NIKL release date followed by a two-digit revision, such as
`2026091901`, and must match the version given to the generator. `DictionaryAssetTest` checks that
they match.

Then run `LookupEvaluationTest`, since a new release can break the lookup evaluation sets (see
[Dictionary lookup ranking](#dictionary-lookup-ranking)), and regenerate the analyzer's context
counts (see [The analyzer's context counts](#the-analyzers-context-counts)).

### The analyzer's context counts

The `analyzer` module ranks analyses with counts from the UD Korean GSD and Kaist treebanks,
`context-counts.tsv` in its resources. Only the counts for words the analyzer can read in more than
one way are kept, so regenerate them when the dictionary or the analyzer's candidates change:

1. Download `ko_gsd-ud-train.conllu` from
   [UD_Korean-GSD](https://github.com/UniversalDependencies/UD_Korean-GSD) and
   `ko_kaist-ud-train.conllu` from
   [UD_Korean-Kaist](https://github.com/UniversalDependencies/UD_Korean-Kaist), about 41 MB in
   total. Use only the training sets: the test sets are for measuring the analyzer.
2. Put them in `tools/analyzer-counts/data`, which is ignored by git.
3. Run the generator, which also needs the bundled dictionary:

   ```bash
   ./gradlew :tools:analyzer-counts:run
   ```

The counts are licensed CC BY-SA 4.0, like the treebanks. If you use a different release, update
`context-counts-attribution.md` next to the counts, and the language data attribution in the app's
strings if the authors change. Measure the result with `LookupEvaluationTest`, as described in
[docs/lookup-analyzer.md](docs/lookup-analyzer.md#how-it-was-measured).

### Dictionary lookup ranking

When a word has several dictionary entries, `HomonymRanking` decides which one a lookup shows first.
Measure ranking changes with `LookupEvaluationTest` (see [Tests](#tests)):

1. Work against the dev set, `lookup-eval-krdict-dev.tsv`, using its per-case report in
   `app/build/reports`.
2. Use the test set, `lookup-eval-krdict-test.tsv`, only to confirm the finished change, so the
   change isn't tuned to the cases it's measured on.
3. Raise the baselines in `LookupEvaluationTest` for the sets the change improves. Each set fails
   below its baseline.

The dictionary sets are generated from the dictionary's example sentences and refer to entries by
homonym number, which can change between dictionary releases. If the test reports entries missing
from the dictionary after it's regenerated, generate the sets again and measure the baselines again:

```bash
./gradlew :tools:dictionary-generator:generateEvalSet
```

### User-facing text

Add every string to both `app/src/main/res/values/strings.xml` and
`app/src/main/res/values-ko-rKR/strings.xml`.
