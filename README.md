# 향상 - Reader App For Korean Learners

## Features
* Integrated RSS feeds
* Built-in dictionary

## About
* Offline version of [National Institute of Korean Language's
  Korean-English Learners' Dictionary](https://krdict.korean.go.kr/eng/mainAction) for fast word lookups
* [open-korean-text](https://github.com/open-korean-text/open-korean-text) for lemmatization

## Building

The bundled dictionary isn't part of the repository, so it has to be generated before the first
build. Without it, the app builds, but fails when it opens the dictionary.

1. Clone the repository and open it in Android Studio, or use the command line with a JDK 17 or
   newer in `JAVA_HOME`. The JDK bundled with Android Studio works.
2. Generate the dictionary from the NIKL data, as described in [CONTRIBUTING.md](CONTRIBUTING.md#the-bundled-dictionary).
3. Build and install the app on a connected device or emulator:

   ```bash
   ./gradlew :app:installDebug
   ```

For signed release builds and running the tests, see [CONTRIBUTING.md](CONTRIBUTING.md).

## License
Hyangsang is free software: you can redistribute it and/or modify it under the terms of the GNU
General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. See [LICENSE](LICENSE) for the full text.

The dictionary bundled with the app is not covered by this license. It is built from the National
Institute of Korean Language's Korean-English Learners' Dictionary, which is provided under the
Creative Commons Attribution-ShareAlike license (see the [copyright
terms](https://krdict.korean.go.kr/eng/kboardPolicy/copyRightTermsInfo)), and the generated
dictionary database is distributed under the same license.
