package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kettu.hyangsang.data.local.entity.WordEntry
import dev.kettu.hyangsang.ui.viewmodel.DictionaryViewModel
import dev.kettu.hyangsang.ui.viewmodel.VocabularyViewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    title: String,
    content: String,
    onMenuClick: () -> Unit,
    dictionaryViewModel: DictionaryViewModel,
    vocabularyViewModel: VocabularyViewModel,
    modifier: Modifier = Modifier,
    fontSize: String = "Medium (Default)"
) {
    var selectedWord by remember { mutableStateOf<String?>(null) }
    val lookupResult by dictionaryViewModel.lookupResult.collectAsState()
    val offlineResult by dictionaryViewModel.offlineResult.collectAsState()
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    val baseFontSize = when (fontSize) {
        "Small" -> 16.sp
        "Medium (Default)" -> 20.sp
        "Large" -> 24.sp
        else -> 20.sp
    }

    // Basic tokenization: split by spaces and keep them to preserve layout
    val tokens = remember(content) { content.split(Regex("(?<=\\s)|(?=\\s)")) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                FlowRow {
                    tokens.forEach { token ->
                        if (token.isBlank()) {
                            Text(text = token, fontSize = baseFontSize)
                        } else {
                            ClickableWord(
                                word = token,
                                isSelected = selectedWord == token,
                                fontSize = baseFontSize,
                                onClick = {
                                    val wordToLookup = token.trim { it in "!.?,\"'" }
                                    selectedWord = token
                                    dictionaryViewModel.lookupWord(wordToLookup)
                                    showBottomSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                    selectedWord = null
                    dictionaryViewModel.clearLookup()
                },
                sheetState = sheetState
            ) {
                DefinitionOverlay(
                    word = selectedWord ?: "",
                    entry = lookupResult,
                    offlineEntries = offlineResult,
                    onMarkKnown = { vocabularyViewModel.markWordAsKnown(it) },
                    onMarkLearning = { vocabularyViewModel.markWordAsLearning(it) }
                )
            }
        }
    }
}

@Composable
fun DefinitionOverlay(
    word: String,
    entry: WordEntry?,
    offlineEntries: List<dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses>,
    onMarkKnown: (String) -> Unit,
    onMarkLearning: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val displayWord = offlineEntries.firstOrNull()?.entry?.word ?: entry?.word ?: word
        val origin = offlineEntries.firstOrNull()?.entry?.origin

        Row {
            Text(
                text = displayWord,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (origin != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "($origin)",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            AssistChip(
                onClick = { onMarkLearning(displayWord) },
                label = { Text("Learning") },
                leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.secondary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(
                onClick = { onMarkKnown(displayWord) },
                label = { Text("Known") },
                leadingIcon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        if (offlineEntries.isNotEmpty()) {
            offlineEntries.forEach { fullEntry ->
                val e = fullEntry.entry
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Row {
                        if (e.pronunciation != null) {
                            Text(
                                text = "[${e.pronunciation}]",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = e.partOfSpeech ?: "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    
                    fullEntry.senses.forEachIndexed { index, senseWithExamples ->
                        val sense = senseWithExamples.sense
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "${index + 1}. ${sense.definitionKo}",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (sense.definitionEn != null) {
                                Text(
                                    text = sense.definitionEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            senseWithExamples.examples.forEach { example ->
                                Text(
                                    text = "• ${example.example}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }
        } else if (entry != null) {
            // Fallback to legacy WordEntry
            if (entry.pronunciation != null) {
                Text(
                    text = "[${entry.pronunciation}]",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = entry.pos ?: "Dictionary Entry",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                text = entry.definition,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            Text(
                text = "No definition found for '$word'.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "In the next phase, we will integrate a morphological analyzer to show the stem (e.g., if you clicked '했어요', we show '하다') and fetch real definitions from the offline database.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ClickableWord(
    word: String,
    isSelected: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit
) {
    Text(
        text = word,
        fontSize = fontSize,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 1.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
    )
}

@Preview(showBackground = true)
@Composable
fun ReaderScreenPreview() {
    // Previews cannot easily provide ViewModels without a lot of boilerplate,
    // so in a real app we'd usually use a stateless version of the screen for previews.
}
