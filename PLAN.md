# 향상 Project Plan: Reader App For Korean Learners

## 1. Architectural Foundation
*   **Language & UI**: Kotlin with Jetpack Compose (Material 3).
*   **Navigation**: Jetpack Navigation Compose.
*   **Local Storage**:
    *   **Room**: For reading progress, user stats, and article metadata.
    *   **DataStore**: For simple user preferences.
    *   **SQLite FTS5**: For high-performance dictionary lookups.
*   **Concurrency**: Kotlin Coroutines & Flow.

## 2. Reader Mode (Core Feature)
*   **Text Processing**: Split text into tokens for interaction.
*   **Morphological Analysis**: Integrate a library like **Open Korean Text (OKT)** or **Komoran** locally to handle conjugation/stemming.
*   **Interactive UI**: A custom layout that renders tokens as clickable elements.
*   **Progress Tracking**: Save scroll position or last visible paragraph index to Room.

## 3. Dictionary Management
*   **Offline First**: Built-in Room database containing the KR-EN/KR-KR dictionary. 
*   **Lookup Overlay**: Modal Bottom Sheet rendering `DictionaryWithSenses` objects.
*   **The "Stemming Pipeline"**:
    1. User taps word in Reader.
    2. Morphological analyzer identifies the stem (Lemma).
    3. Repository queries `DictionaryDao` using the stem.
    4. Overlay displays results with primary definition and collapsible examples.

## 4. Source Index & Discovery
*   **Integrated Sources**: RSS feeds/scrapers for news (e.g., Yonhap) or learning blogs.
*   **Search/Sort**: Filter by difficulty, length, or date.

## 5. Statistics & Gamification (Potentially)
*   **Word Tracking**: Mark unique stems as "encountered" in the database.
*   **Metrics**:
    *   Reading Heatmap.
    *   Vocabulary Level estimation.
    *   Total Immersion Time.

### 6. Sources & Indexing (Refined)
1. [x] **Define Schema**: Add `RssFeed` and update `Article` to include `link` and `isSaved`.
2. [x] **The Fetcher**: Retrofit + XML Parser (Implemented).
3. [ ] **The Extractor**: Integrate **Jsoup** to pull full article bodies from news URLs.
    *   Implement `HtmlSanitizer` utility to strip ads/scripts.
4. [ ] **Reader Integration**:
    *   Update `ReaderViewModel` to handle "Loading" and "Error" states for remote articles.
    *   Implement "Save for Offline" toggle.
5. [ ] **Background Sync**: Implement `WorkManager` to prune old articles (e.g., delete bodies of unread articles older than 7 days) to save space.

## Bugfixes & Improvements
*   Ensure articles are unique.
*   Improve dictionary lookup visuals.
*   Let user choose reader font type.
*   Ensure status bar legibility with all themes.
*   Update default theme to Solarized, add different themes.
*   Show selected page on drawer.
*   Allow users to add own content.
*   Include open source notices (https://developers.google.com/android/guides/opensource).
*   Assign articles a "difficulty score" based on a subset of the text (vocabulary frequency/other metrics).
*   Use Dagger for Dependency Injection.
*   Programmatically update version number and build ID from commit hash.
---

## Roadmap
1.  **Phase 1: Reader UI & Navigation** (Completed) - Interactive text prototype, Navigation Drawer, and Settings UI.
2.  **Phase 2: Persistence & Settings** (Completed) - DataStore integration for theme, font size, and language. Dynamic theme/font size observers in UI.
3.  **Phase 3: Local Database (Room)** (In Progress) - Dictionary schema (Entries, Senses, Examples) is implemented. *Next: Article storage, reading progress, and vocabulary tracking.*
4.  **Phase 4: Definition Overlay Integration** (Completed) - Connect the offline dictionary to the Reader UI. This involves:
    *   **Morphological Analysis**: Integrating OKT or Komoran to convert conjugated words (e.g., "갔어요") into dictionary stems ("가다").
    *   **Rich UI Rendering**: Displaying multiple senses, part-of-speech tags, and example sentences in the BottomSheet.
    *   **Fallback Logic**: Handling cases where no exact stem match is found.
5.  **Phase 5: Search & Performance** - Implement FTS5 for high-speed dictionary lookups and full-text search across articles.
6.  **Phase 6: Sources & Indexing** - Fetching real content.
    1. Define the Schema: Add a FeedSource table (Name, URL, Icon) and link it to Article table via a sourceId.
    2. The Fetcher: Create an RssRepository that handles the network request and XML parsing (Retrofit, Kotlinx.Serialization + XmlUtil?).
    3. The Worker: Use WorkManager to sync the feeds in the background once or twice a day, so the user has fresh content ready when they wake up.
    4. The UI: Create a "Feeds" screen where users can see a list of available feeds.