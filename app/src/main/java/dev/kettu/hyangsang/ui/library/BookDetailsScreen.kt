package dev.kettu.hyangsang.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.Book
import dev.kettu.hyangsang.ui.utils.DateTimeUtils
import kotlinx.coroutines.delay
import java.io.File
import java.time.Instant
import java.time.ZoneId

// How long typing has to pause before an edited title or author is saved
private const val SAVE_DELAY_MILLIS = 500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    book: Book,
    coverFile: File?,
    onTitleAndAuthorChange: (title: String, author: String) -> Unit,
    onReadClick: () -> Unit,
    onDelete: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by rememberSaveable(book.id) { mutableStateOf(book.title) }
    var author by rememberSaveable(book.id) { mutableStateOf(book.author.orEmpty()) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(title, author) {
        if (title == book.title && author == book.author.orEmpty()) return@LaunchedEffect
        delay(SAVE_DELAY_MILLIS)
        onTitleAndAuthorChange(title, author)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.book_details)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
                BookCover(
                    title = title.ifBlank { book.title },
                    author = author.ifBlank { null },
                    coverFile = coverFile,
                    modifier = Modifier.width(112.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (book.lastReadDate != null) {
                        Text(
                            text = stringResource(
                                R.string.chapter_of_chapters_and_percent,
                                book.chapterIndex + 1,
                                book.chapterCount,
                                percentText(book.progress)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onReadClick) {
                        Icon(
                            Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(
                                if (book.lastReadDate != null) R.string.continue_reading else R.string.start_reading
                            ),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.book_title_label)) },
                isError = title.isBlank(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = author,
                onValueChange = { author = it },
                label = { Text(stringResource(R.string.book_author_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Column {
                InfoRow(
                    stringResource(R.string.book_info_chapters),
                    book.chapterCount.toString()
                )
                InfoRow(
                    stringResource(R.string.book_info_length),
                    stringResource(
                        R.string.reading_time_and_words,
                        readingTimeText(book.wordCount),
                        pluralStringResource(R.plurals.word_count, book.wordCount, book.wordCount)
                    )
                )
                InfoRow(stringResource(R.string.book_info_added), formatDay(book.addedDate))
                InfoRow(
                    stringResource(R.string.book_info_last_read),
                    book.lastReadDate?.let { formatDay(it) } ?: stringResource(R.string.book_not_read_yet)
                )
                InfoRow(stringResource(R.string.book_info_size), fileSizeText(book.sizeBytes))
                book.sourceFileName?.let { InfoRow(stringResource(R.string.book_info_source), it) }
                Text(
                    text = stringResource(R.string.book_backup_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            OutlinedButton(onClick = { confirmingDelete = true }) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.delete_book),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(stringResource(R.string.delete_book_title, book.title)) },
            text = { Text(stringResource(R.string.delete_book_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
    HorizontalDivider()
}

private fun formatDay(millis: Long): String =
    DateTimeUtils.formatDate(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
