package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.data.local.dao.DictionaryWithSenses
import dev.kettu.hyangsang.data.local.dao.SenseWithExamples
import dev.kettu.hyangsang.data.local.entity.DictionaryEntry
import dev.kettu.hyangsang.data.local.entity.DictionaryExample
import dev.kettu.hyangsang.data.local.entity.DictionarySense
import dev.kettu.hyangsang.ui.theme.HyangsangTheme

@Composable
fun DefinitionOverlay(
    selectedWord: String,
    lookupResults: Map<String, List<DictionaryWithSenses>>
) {
    var selectedStem by remember { mutableStateOf(selectedWord) }

    LaunchedEffect(lookupResults) {
        val currentDefinitions = lookupResults[selectedStem] ?: emptyList()
        if (currentDefinitions.isEmpty()) {
            val bestStem =
                lookupResults.keys.firstOrNull { lookupResults[it]?.isNotEmpty() == true }
            if (bestStem != null) {
                selectedStem = bestStem
            }
        }
    }

    val currentDefinitions = lookupResults[selectedStem] ?: emptyList()

    // Segment chips for compound words
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (lookupResults.size > 1) {
            Row {
                lookupResults.keys.forEach { stem ->
                    FilterChip(
                        selected = selectedStem == stem,
                        onClick = { selectedStem = stem },
                        label = { Text(stem) }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
        }

        // Definitions
        if (currentDefinitions.isNotEmpty()) {
            currentDefinitions.forEachIndexed { index, fullEntry ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 16.dp))

                EntryView(fullEntry, index + 1, total = currentDefinitions.size)
            }
        } else {
            // No results, no definitions
            Text(
                text = "No definitions found for '$selectedWord'.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun EntryView(fullEntry: DictionaryWithSenses, entryNumber: Int, total: Int) {
    val entry = fullEntry.entry

    Column {
        // Entry header
        Row(verticalAlignment = Alignment.CenterVertically)
        {
            Text(
                text = entry.word,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            // Show entry number if homographs exist
            if (total > 1) {
                Text(
                    text = entryNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Top)
                )
            }
            if (!entry.origin.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "(${entry.origin})",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            if (!entry.vocabularyLevel.isNullOrEmpty() && entry.vocabularyLevel != "None") {
                Spacer(Modifier.weight(1f))
                Text(
                    text = entry.vocabularyLevel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp)
        ) {
            if (!entry.pronunciation.isNullOrEmpty()) {
                Text(
                    text = "[${entry.pronunciation}]",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (!entry.partOfSpeech.isNullOrEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.partOfSpeech,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Senses
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            fullEntry.senses.forEachIndexed { index, senseWithExamples ->
                val sense = senseWithExamples.sense
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "${index + 1}. ${sense.translationEn}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = sense.definitionKo,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                    if (!sense.definitionEn.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = sense.definitionEn,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    // Show 2 first examples to not overfill overlay
                    // TODO: Allow user to expand/open examples?
                    senseWithExamples.examples.take(2).forEach { example ->
                        // Check for edge case where examples exist but are "empty" (e.g. 한국) (Might be caused by incorrect dictionary parsing)
                        if (example.example.isEmpty() || example.example == " ") {
                            return@forEach
                        }

                        Text(
                            text = "• ${example.example}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(
                                start = 12.dp,
                                top = 4.dp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
            lookupResults = sampleDefinitions
        )
    }
}
