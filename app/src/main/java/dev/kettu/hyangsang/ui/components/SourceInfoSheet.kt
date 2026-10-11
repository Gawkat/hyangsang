package dev.kettu.hyangsang.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.defaults.DefaultSource
import dev.kettu.hyangsang.ui.settings.openInBrowser

/** What a built-in source is, the sections the app has feeds for, and a link to its site. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceInfoSheet(source: DefaultSource, onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Sources with a single feed have no sections to list
    val sections = source.feeds
        .filter { it.isSection }
        .map { feed -> stringResource(feed.section ?: feed.category.label) }
        .distinct()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(stringResource(source.label), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(source.about), style = MaterialTheme.typography.bodyLarge)
            if (sections.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.source_sections, sections.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = { openInBrowser(context, source.website) }) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Text(
                    stringResource(R.string.visit_website),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing)
                )
            }
        }
    }
}
