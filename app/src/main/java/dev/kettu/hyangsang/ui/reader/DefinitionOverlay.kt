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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    word: String,
    stem: String,
    definitions: List<DictionaryWithSenses>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, bottom = 48.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val entry = definitions.firstOrNull()?.entry
        val displayWord = entry?.word ?: stem.ifEmpty { word }
        val origin = entry?.origin

        Row(
            verticalAlignment = Alignment.CenterVertically
        )
        {
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
            if (entry?.vocabularyLevel != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = entry.vocabularyLevel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (entry != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                if (entry.pronunciation != null) {
                    Text(
                        text = "[${entry.pronunciation}]",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                if (entry.partOfSpeech != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entry.partOfSpeech,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        if (definitions.isNotEmpty()) {
            definitions.forEach { fullEntry ->
                fullEntry.entry
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
                            if (sense.definitionEn != null) {
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    text = sense.definitionEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                            senseWithExamples.examples.forEach { example ->
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
        } else {
            Text(
                text = "No definition found for '$stem'.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefinitionOverlayPreview() {
    val sampleDefinitions = listOf(
        DictionaryWithSenses(
            entry = DictionaryEntry(
                id = 1,
                originalId = "123",
                word = "학교",
                origin = "學校",
                partOfSpeech = "Noun",
                vocabularyLevel = "Beginner",
                semanticCategory = "Education",
                lexicalUnit = "Word",
                pronunciation = "학꾜",
                audioUrl = null
            ),
            senses = listOf(
                SenseWithExamples(
                    sense = DictionarySense(
                        senseId = 1,
                        entryId = 1,
                        definitionKo = "일정한 목적, 교과 과정, 제도 등에 따라 학생을 교육하는 기관.",
                        definitionEn = "An institution that educates students according to a specific purpose, curriculum, system, etc.",
                        translationEn = "school"
                    ),
                    examples = listOf(
                        DictionaryExample(
                            exampleId = 1,
                            senseId = 1,
                            example = "학교 운동장.",
                            type = "sentence"
                        ),
                        DictionaryExample(
                            exampleId = 2,
                            senseId = 1,
                            example = "내년에 딸이 여덟 살이 되어 학교에 입학할 것이다.",
                            type = "sentence"
                        )
                    )
                )
            )
        )
    )

    HyangsangTheme {
        DefinitionOverlay(
            word = "학교에",
            stem = "학교",
            definitions = sampleDefinitions
        )
    }
}
