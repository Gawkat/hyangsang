package dev.kettu.hyangsang.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.kettu.hyangsang.appContainer
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.data.local.entity.BookWithChapter
import dev.kettu.hyangsang.data.local.entity.ChapterInfo
import dev.kettu.hyangsang.data.repository.BookImport
import dev.kettu.hyangsang.data.repository.BookImportFailure
import dev.kettu.hyangsang.data.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.Collator

enum class BookSort {
    RECENTLY_READ,
    TITLE,
    RECENTLY_ADDED
}

/** Where an import is, from reading the file to the user's answer on the confirmation sheet. */
sealed interface BookImportState {
    data object Reading : BookImportState
    data class Ready(val import: BookImport.Ready) : BookImportState
    data class Failed(val reason: BookImportFailure) : BookImportState
}

class BooksViewModel(private val bookRepository: BookRepository) : ViewModel() {
    private val _sort = MutableStateFlow(BookSort.RECENTLY_READ)
    val sort: StateFlow<BookSort> = _sort.asStateFlow()

    val books: StateFlow<List<BookWithChapter>> = combine(bookRepository.getBooks(), _sort) { books, sort ->
        sorted(books, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importState = MutableStateFlow<BookImportState?>(null)
    val importState: StateFlow<BookImportState?> = _importState.asStateFlow()
    private var importJob: Job? = null

    fun setSort(sort: BookSort) {
        _sort.value = sort
    }

    fun getBook(id: Long): Flow<Book?> = bookRepository.getBook(id)

    fun getChapterInfos(bookId: Long): Flow<List<ChapterInfo>> = bookRepository.getChapterInfos(bookId)

    fun setRead(book: Book, read: Boolean) {
        viewModelScope.launch { bookRepository.setRead(book, read) }
    }

    fun restorePosition(book: Book) {
        viewModelScope.launch { bookRepository.restorePosition(book) }
    }

    fun coverFile(book: Book): File? =
        book.coverFile?.let { File(bookRepository.imageDir(book.folder), it) }

    fun coverFile(import: BookImport.Ready): File? =
        import.book.cover?.let { File(bookRepository.imageDir(import.folder), it) }

    /**
     * Reads a book from [open] and shows it for the user to confirm, replacing any import shown.
     * Both functions are called off the main thread.
     */
    fun startImport(open: () -> InputStream?, fileName: () -> String?) {
        cancelImport()
        _importState.value = BookImportState.Reading
        importJob = viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { fileName() }
            _importState.value = when (val result = bookRepository.prepare(open, name)) {
                is BookImport.Ready -> BookImportState.Ready(result)
                is BookImport.Failed -> BookImportState.Failed(result.reason)
            }
        }
    }

    /** Adds the book shown for confirmation, and passes its id to [onImported]. */
    fun confirmImport(fallbackTitle: String, onImported: (Long) -> Unit) {
        val ready = (_importState.value as? BookImportState.Ready)?.import ?: return
        _importState.value = null
        viewModelScope.launch {
            val id = bookRepository.commit(ready, fallbackTitle)
            if (id == null) {
                _importState.value = BookImportState.Failed(BookImportFailure.UNREADABLE)
            } else {
                onImported(id)
            }
        }
    }

    /** Closes the import sheet, removing the files of a book that wasn't imported. */
    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        val ready = (_importState.value as? BookImportState.Ready)?.import
        _importState.value = null
        if (ready != null) viewModelScope.launch { bookRepository.discard(ready) }
    }

    fun updateTitleAndAuthor(id: Long, title: String, author: String) {
        if (title.isBlank()) return
        viewModelScope.launch { bookRepository.updateTitleAndAuthor(id, title.trim(), author.trim()) }
    }

    fun delete(book: Book) {
        viewModelScope.launch { bookRepository.delete(book) }
    }

    private fun sorted(books: List<BookWithChapter>, sort: BookSort): List<BookWithChapter> = when (sort) {
        // Books never opened come after the ones being read, newest first
        BookSort.RECENTLY_READ -> books.sortedWith(
            compareByDescending<BookWithChapter> { it.book.lastReadDate ?: Long.MIN_VALUE }
                .thenByDescending { it.book.addedDate }
        )

        BookSort.TITLE -> {
            val collator = Collator.getInstance()
            books.sortedWith { a, b -> collator.compare(a.book.title, b.book.title) }
        }

        BookSort.RECENTLY_ADDED -> books.sortedByDescending { it.book.addedDate }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { BooksViewModel(appContainer.bookRepository) }
        }
    }
}
