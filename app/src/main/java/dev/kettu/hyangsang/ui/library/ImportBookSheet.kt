package dev.kettu.hyangsang.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.repository.BookImport
import dev.kettu.hyangsang.data.repository.BookImportFailure
import dev.kettu.hyangsang.ui.viewmodel.BookImportState
import java.io.File

/** Asks before adding a book, or says why it can't be added. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBookSheet(
    state: BookImportState,
    coverFile: (BookImport.Ready) -> File?,
    onImport: () -> Unit,
    onOpenExisting: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            when (state) {
                BookImportState.Reading -> Reading()
                is BookImportState.Ready -> Ready(
                    import = state.import,
                    coverFile = coverFile(state.import),
                    onImport = onImport,
                    onOpenExisting = onOpenExisting,
                    onCancel = onDismiss
                )

                is BookImportState.Failed -> Failed(state.reason, onDismiss)
            }
        }
    }
}

@Composable
private fun Reading() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 24.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text(stringResource(R.string.reading_book), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Ready(
    import: BookImport.Ready,
    coverFile: File?,
    onImport: () -> Unit,
    onOpenExisting: (Long) -> Unit,
    onCancel: () -> Unit
) {
    val untitled = stringResource(R.string.untitled_book)
    val title = import.book.title ?: import.fileName?.substringBeforeLast('.') ?: untitled
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.import_book_title), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(
                title = title,
                author = import.book.author,
                coverFile = coverFile,
                modifier = Modifier.width(96.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                import.book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val chapters = import.book.chapters.size
                Text(
                    text = stringResource(
                        R.string.chapters_and_reading_time,
                        pluralStringResource(R.plurals.chapter_count, chapters, chapters),
                        readingTimeText(import.wordCount)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = stringResource(R.string.size_on_device, fileSizeText(import.fileBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                import.fileName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (import.existingId != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = null)
                    Text(stringResource(R.string.book_already_imported), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel) { Text(stringResource(android.R.string.cancel)) }
            if (import.existingId != null) {
                TextButton(onClick = { onOpenExisting(import.existingId) }) {
                    Text(stringResource(R.string.open_existing_book))
                }
            }
            Button(onClick = onImport) {
                Text(stringResource(if (import.existingId != null) R.string.import_copy else R.string.import_book))
            }
        }
    }
}

@Composable
private fun Failed(reason: BookImportFailure, onDismiss: () -> Unit) {
    val (icon, title, body) = when (reason) {
        BookImportFailure.DRM -> Triple(Icons.Outlined.Lock, R.string.import_drm_title, R.string.import_drm_body)
        BookImportFailure.FIXED_LAYOUT ->
            Triple(Icons.AutoMirrored.Outlined.MenuBook, R.string.import_fixed_layout_title, R.string.import_fixed_layout_body)

        BookImportFailure.NO_TEXT ->
            Triple(Icons.Outlined.TextSnippet, R.string.import_no_text_title, R.string.import_no_text_body)

        BookImportFailure.TOO_LARGE ->
            Triple(Icons.Outlined.ErrorOutline, R.string.import_too_large_title, R.string.import_too_large_body)

        BookImportFailure.UNREADABLE ->
            Triple(Icons.Outlined.ErrorOutline, R.string.import_unreadable_title, R.string.import_unreadable_body)
    }
    FailureContent(icon, stringResource(title), stringResource(body), onDismiss)
}

@Composable
private fun FailureContent(icon: ImageVector, title: String, body: String, onDismiss: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        }
    }
}
