# 향상 Project Plan: Korean Immersion & Learning App

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
    3. Repository queries `OfflineDictionaryDao` using the stem.
    4. Overlay displays results with primary definition and collapsible examples.

## 4. Source Index & Discovery
*   **Integrated Sources**: RSS feeds/scrapers for news (e.g., Yonhap) or learning blogs.
*   **Search/Sort**: Filter by difficulty, length, or date.

## 5. Statistics & Gamification
*   **Word Tracking**: Mark unique stems as "encountered" in the database.
*   **Metrics**:
    *   Reading Heatmap.
    *   Vocabulary Level estimation.
    *   Total Immersion Time.

## 6. Advanced Features (Future)
*   **Local LLM Integration**: llama.cpp with JNI for contextual grammar/nuance explanations (not a chatbot).

---

## Bugfixes & Improvements
*   Let user choose reader font type.
*   Ensure status bar legibility with all themes.
*   Update default theme to Solarized, add different themes.
*   Show selected page on drawer.
*   Allow users to add own content.

---

## Roadmap
1.  **Phase 1: Reader UI & Navigation** (Completed) - Interactive text prototype, Navigation Drawer, and Settings UI.
2.  **Phase 2: Persistence & Settings** (Completed) - DataStore integration for theme, font size, and language. Dynamic theme/font size observers in UI.
3.  **Phase 3: Local Database (Room)** (In Progress) - Dictionary schema (Entries, Senses, Examples) is implemented. *Next: Article storage, reading progress, and vocabulary tracking.*
4.  **Phase 4: Definition Overlay Integration** (Next) - Connect the offline dictionary to the Reader UI. This involves:
    *   **Morphological Analysis**: Integrating OKT or Komoran to convert conjugated words (e.g., "갔어요") into dictionary stems ("가다").
    *   **Rich UI Rendering**: Displaying multiple senses, part-of-speech tags, and example sentences in the BottomSheet.
    *   **Fallback Logic**: Handling cases where no exact stem match is found.
5.  **Phase 5: Search & Performance** - Implement FTS5 for high-speed dictionary lookups and full-text search across articles.
6.  **Phase 6: Sources & Indexing** - Fetching real content.
