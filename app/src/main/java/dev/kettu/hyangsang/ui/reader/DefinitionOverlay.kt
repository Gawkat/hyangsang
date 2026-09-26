package dev.kettu.hyangsang.ui.reader

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.local.dao.SenseWithExamples
import dev.kettu.hyangsang.data.local.entity.DictionaryEntry
import dev.kettu.hyangsang.data.local.entity.DictionaryExample
import dev.kettu.hyangsang.data.local.entity.DictionarySense
import dev.kettu.hyangsang.ui.theme.HyangsangTheme

/**
 * Dictionary lookup for a tapped word.
 *
 * The first entry for the selected stem is shown in full. Other entries with the same spelling
 * (homographs) are listed as one-line rows that swap into the main position, so a wrong first
 * guess costs one tap instead of a scroll through every entry.
 */
@Composable
fun DefinitionOverlay(
    selectedWord: String,
    lookupResults: Map<String, List<DictionaryWithSenses>>,
    isLoading: Boolean,
    onSearchWeb: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedStem by remember(selectedWord) { mutableStateOf(selectedWord) }

    LaunchedEffect(lookupResults) {
        if (lookupResults[selectedStem].isNullOrEmpty()) {
            lookupResults.keys.firstOrNull { !lookupResults[it].isNullOrEmpty() }
                ?.let { selectedStem = it }
        }
    }

    val entries = lookupResults[selectedStem].orEmpty()
    var selectedEntryIndex by remember(selectedStem, entries.size) { mutableIntStateOf(0) }
    val searchTerm = if (entries.isEmpty()) selectedWord else selectedStem

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
        ) {
            if (lookupResults.size > 1 || (lookupResults.size == 1 && selectedStem != selectedWord)) {
                StemChips(
                    word = selectedWord,
                    stems = lookupResults.keys.toList(),
                    selectedStem = selectedStem,
                    onSelect = { selectedStem = it }
                )
            }

            when {
                isLoading && entries.isEmpty() -> LookupPlaceholder()
                entries.isEmpty() -> Text(
                    text = stringResource(R.string.no_definitions, selectedWord),
                    style = MaterialTheme.typography.bodyLarge
                )

                else -> {
                    val index = selectedEntryIndex.coerceIn(entries.indices)
                    EntryView(entries[index])
                    if (entries.size > 1) {
                        Text(
                            text = stringResource(R.string.other_entries, selectedStem),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        entries.forEachIndexed { i, entry ->
                            if (i != index) {
                                AlternativeEntryRow(entry, onClick = { selectedEntryIndex = i })
                            }
                        }
                    }
                }
            }
        }

        // Pinned below the scrolling content, so the fallback is always one tap away
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = stringResource(
                    if (entries.isEmpty() && !isLoading) R.string.lookup_online_hint else R.string.not_right_meaning
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onSearchWeb(searchTerm) }) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.search_web_dictionary),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StemChips(
    word: String,
    stems: List<String>,
    selectedStem: String,
    onSelect: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        Text(
            text = word,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        stems.forEach { stem ->
            FilterChip(
                selected = selectedStem == stem,
                onClick = { onSelect(stem) },
                label = { Text(stem) }
            )
        }
    }
}

@Composable
fun EntryView(fullEntry: DictionaryWithSenses) {
    val entry = fullEntry.entry
    var showExamples by remember(entry.id) { mutableStateOf(false) }
    val exampleCount = fullEntry.senses.sumOf { sense -> sense.visibleExamples().size }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.word,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (!entry.origin.isNullOrEmpty()) {
                Text(
                    text = entry.origin,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (!entry.vocabularyLevel.isNullOrEmpty() && entry.vocabularyLevel != "None") {
                Text(
                    text = entry.vocabularyLevel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }

        val details = listOfNotNull(
            entry.pronunciation?.takeIf { it.isNotEmpty() }?.let { "[$it]" },
            entry.partOfSpeech?.takeIf { it.isNotEmpty() }
        )
        if (details.isNotEmpty()) {
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        fullEntry.senses.forEachIndexed { index, senseWithExamples ->
            val sense = senseWithExamples.sense
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = "${index + 1}. ${sense.translationEn.orEmpty()}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(text = sense.definitionKo, style = MaterialTheme.typography.bodyMedium)
                if (!sense.definitionEn.isNullOrEmpty()) {
                    Text(
                        text = sense.definitionEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showExamples) {
                    senseWithExamples.visibleExamples().forEach { example ->
                        Text(
                            text = "• ${example.example}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp)
                        )
                    }
                }
            }
        }

        // Examples start collapsed, which keeps the sheet short enough to leave the article visible
        if (exampleCount > 0) {
            TextButton(
                onClick = { showExamples = !showExamples },
                contentPadding = PaddingValues(horizontal = 0.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(
                    if (showExamples) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (showExamples) {
                        stringResource(R.string.hide_examples)
                    } else {
                        pluralStringResource(R.plurals.show_examples, exampleCount, exampleCount)
                    },
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

// Show the first 2 examples per sense, skipping blank ones (a dictionary parsing artefact, e.g. 한국)
private fun SenseWithExamples.visibleExamples() =
    examples.filter { it.example.isNotBlank() }.take(2)

@Composable
private fun AlternativeEntryRow(fullEntry: DictionaryWithSenses, onClick: () -> Unit) {
    val entry = fullEntry.entry
    val gloss = fullEntry.senses.firstOrNull()?.sense?.translationEn.orEmpty()

    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = entry.origin?.takeIf { it.isNotEmpty() } ?: entry.word,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = listOfNotNull(gloss.takeIf { it.isNotEmpty() }, entry.partOfSpeech).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Filled.SwapVert,
                contentDescription = stringResource(R.string.show_this_entry),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Shown while a lookup is running, instead of briefly claiming there are no results
@Composable
private fun LookupPlaceholder() {
    val alpha by rememberInfiniteTransition(label = "lookupPlaceholder").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lookupPlaceholderAlpha"
    )
    val color = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0.35f, 0.25f, 0.8f, 0.6f).forEachIndexed { index, width ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(width)
                    .height(if (index == 0) 32.dp else 16.dp)
                    .drawBehind { drawRoundRect(color, alpha = alpha, cornerRadius = CornerRadius(8.dp.toPx())) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefinitionOverlayPreview() {
    val sampleDefinitions = mapOf(
        Pair(
            "무상", listOf(
                DictionaryWithSenses(
                    entry = DictionaryEntry(
                        id = 1,
                        originalId = "1",
                        word = "무상",
                        origin = "無常",
                        partOfSpeech = "Noun",
                        vocabularyLevel = "Advanced",
                        semanticCategory = "Change",
                        lexicalUnit = "Word",
                        pronunciation = "무상",
                        audioUrl = null
                    ),
                    senses = listOf(
                        SenseWithExamples(
                            sense = DictionarySense(
                                senseId = 1,
                                entryId = 1,
                                definitionKo = "시간이 가면서 모든 것이 변하므로 가치나 의미가 없어 허무함.",
                                definitionEn = "Futility with lack of meaning or value because everything changes as time passes by.",
                                translationEn = "transience"
                            ),
                            examples = listOf(
                                DictionaryExample(
                                    exampleId = 1,
                                    senseId = 1,
                                    example = "세월의 무상.",
                                    type = "sentence"
                                ),
                                DictionaryExample(
                                    exampleId = 2,
                                    senseId = 1,
                                    example = "지금까지의 내 삶을 돌아보니 인생의 무상이 절로 느껴졌다.",
                                    type = "sentence"
                                )
                            )
                        ),
                        SenseWithExamples(
                            sense = DictionarySense(
                                senseId = 2,
                                entryId = 1,
                                definitionKo = "정해져 있지 않고 계속 변함.",
                                definitionEn = "The state of changing continuously without being fixed.",
                                translationEn = "continual change"
                            ),
                            examples = listOf(
                                DictionaryExample(
                                    exampleId = 3,
                                    senseId = 2,
                                    example = "무상으로 다니다.",
                                    type = "sentence"
                                ),
                                DictionaryExample(
                                    exampleId = 4,
                                    senseId = 2,
                                    example = "김 씨는 단골 술집을 무상으로 오가며 외로운 마음을 술로 달랬다.",
                                    type = "sentence"
                                )
                            )
                        )
                    )
                ),
                DictionaryWithSenses(
                    entry = DictionaryEntry(
                        id = 2,
                        originalId = "2",
                        word = "무상",
                        origin = "無償",
                        partOfSpeech = "Noun",
                        vocabularyLevel = "Advanced",
                        semanticCategory = "Money",
                        lexicalUnit = "Word",
                        pronunciation = "무상",
                        audioUrl = null
                    ),
                    senses = listOf(
                        SenseWithExamples(
                            sense = DictionarySense(
                                senseId = 3,
                                entryId = 2,
                                definitionKo = "어떤 일이나 물건에 대한 값을 치르거나 받지 않음.",
                                definitionEn = "The act of not paying or receiving any money on a matter or object.",
                                translationEn = "being free of charge"
                            ),
                            examples = listOf(
                                DictionaryExample(
                                    exampleId = 5,
                                    senseId = 3,
                                    example = "무상 교육.",
                                    type = "sentence"
                                )
                            )
                        )
                    )
                )
            )
        ),
        Pair(
            "무",
            listOf(
                DictionaryWithSenses(
                    entry = DictionaryEntry(
                        id = 3,
                        originalId = "3",
                        word = "무",
                        origin = null,
                        partOfSpeech = "Noun",
                        vocabularyLevel = "Beginner",
                        semanticCategory = "Food",
                        lexicalUnit = "Word",
                        pronunciation = "무ː",
                        audioUrl = null
                    ),
                    senses = listOf(
                        SenseWithExamples(
                            sense = DictionarySense(
                                senseId = 4,
                                entryId = 3,
                                definitionKo = "김치 등을 만드는, 색깔이 희고 팔뚝만 한 크기의 뿌리에 깃 모양의 잎이 있는 채소.",
                                definitionEn = "A white, forearm-sized vegetable that features feather-like leaves on its roots, used for making kimchi, etc.",
                                translationEn = "radish"
                            ),
                            examples = listOf(
                                DictionaryExample(
                                    exampleId = 6,
                                    senseId = 4,
                                    example = "무상 교육.",
                                    type = "sentence"
                                )
                            )
                        )
                    )
                )
            )
        )
    )

    HyangsangTheme {
        DefinitionOverlay(
            selectedWord = "무상",
            lookupResults = sampleDefinitions,
            isLoading = false,
            onSearchWeb = {}
        )
    }
}
