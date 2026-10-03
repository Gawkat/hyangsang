# Contributing

Notes for working on Hyangsang: how the project is laid out, how to test it, and what else needs
to change when you change certain things.

## Project layout

* `app` - the Android app
* `parser` - a plain Kotlin library that turns article pages into content blocks, with a
  parser per news site and a generic fallback
* `tools/dictionary-generator` - builds the bundled dictionary from the NIKL dictionary data

## Tests

```bash
./gradlew :app:testDebugUnitTest :parser:test
```

The instrumented tests, such as the Room migration tests, need a running emulator or device:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Running them uninstalls the app from the device, along with its data.

Some parser tests run against saved copies of real articles in `parser/src/test/resources`, which
is ignored by git since the articles belong to the news sites. Without them those tests are
skipped, and Gradle reports them as skipped. To run them, save an article page from the site
under the name the test reads, such as `bbc_sample.html`. They check details of specific
articles, so expect to adjust the assertions for a different article.

`LookupEvaluationTest` measures how often word lookups show the right dictionary entry. It needs
the bundled dictionary and a set of cases in `app/src/test/resources/lookup-eval.tsv`. The cases
are sentences from real articles, so the file is ignored by git as well, and the test is skipped
without either one. Its format is described in the test.

## Release builds

Release builds are signed with the key configured in `keystore.properties` at the repository
root, which is ignored by git:

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
articles already stored, such as a fix for missing or broken content. Articles parsed with an
older version are parsed again in the background the next time they're opened.

A parser change that only matters for new articles doesn't need a bump. Each bump re-fetches
every article once, as it's opened.

### The user database schema

For changes to the entities in `HyangsangDatabase` (articles, feeds, vocabulary):

1. Raise `version` in `HyangsangDatabase`
2. Add a `MIGRATION_x_y` to `Migrations.kt` and to `ALL_MIGRATIONS`
3. Build, and commit the schema Room exports to `app/schemas/`
4. Add a test for the migration to `MigrationTest`

Without a migration, updating the app crashes for existing users, since Room only falls back to a
destructive migration from versions before 13.

### The bundled dictionary

The dictionary lives in its own database, `DictionaryDatabase`, which is never migrated and only
replaced from the `dictionary.db` asset. Neither the asset nor its source data is committed.

To regenerate it:

1. Download the dictionary's JSON files from the [Korean-English Learners' Dictionary
   site](https://krdict.korean.go.kr/kor/mainAction), from 사전 전체 내려받기 at the bottom of the
   page. The files total about 1 GB.
2. Put them in `tools/dictionary-generator/data`, which is ignored by git. Keep them out of
   `app/src/main/assets`, which would package them into the app.
3. Run the generator with the new version. It writes `app/src/main/assets/dictionary.db`:

   ```bash
   ./gradlew :tools:dictionary-generator:run --args="2026091901"
   ```

   A different input folder can be given as a second argument, relative to the repository root.

Installed copies are only replaced when `DICTIONARY_VERSION` changes, so raise it whenever the
asset is regenerated. The version is the NIKL release date followed by a two-digit revision, such
as `2026091901`, and must match the version given to the generator. `DictionaryAssetTest` checks
that they match.

### User-facing text

Add every string to both `app/src/main/res/values/strings.xml` and
`app/src/main/res/values-ko-rKR/strings.xml`.
