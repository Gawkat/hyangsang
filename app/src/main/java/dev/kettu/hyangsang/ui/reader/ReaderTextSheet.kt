package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.prefs.ReaderSettings

/**
 * Quick text settings, opened from the reader's Aa button. There is no scrim, so the article
 * stays readable behind the sheet and every change can be judged as it is made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTextSheet(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onMoreSettingsClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Transparent
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.text_layout_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onMoreSettingsClick) {
                    Text(stringResource(R.string.more_settings))
                }
            }
            TextSizeControl(settings.textSize) { onSettingsChange(settings.copy(textSize = it)) }
            FontControl(settings.font) { onSettingsChange(settings.copy(font = it)) }
            LineSpacingPresetControl(settings.lineSpacing) {
                onSettingsChange(settings.copy(lineSpacing = it))
            }
            Column {
                ControlLabel(stringResource(R.string.theme_setting))
                ChoiceRow(
                    options = listOf(
                        "Light" to stringResource(R.string.theme_light),
                        "Dark" to stringResource(R.string.theme_dark),
                        "System default" to stringResource(R.string.theme_auto)
                    ),
                    selected = currentTheme,
                    onSelect = onThemeChange
                )
            }
        }
    }
}
