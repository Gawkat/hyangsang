package dev.kettu.hyangsang.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.prefs.ReaderFontWeight
import dev.kettu.hyangsang.data.prefs.ReaderMargin
import dev.kettu.hyangsang.data.prefs.ReaderSettings
import dev.kettu.hyangsang.ui.reader.ChoiceRow
import dev.kettu.hyangsang.ui.reader.ControlLabel
import dev.kettu.hyangsang.ui.reader.FontControl
import dev.kettu.hyangsang.ui.reader.TextSizeControl
import dev.kettu.hyangsang.ui.reader.formatLineSpacing
import dev.kettu.hyangsang.ui.reader.readerBodyStyle
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextLayoutScreen(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.text_layout_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button)
                        )
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            // Live preview, using the same style and margins as the reader
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.text_layout_sample),
                    style = readerBodyStyle(settings),
                    modifier = Modifier.padding(horizontal = settings.margin.dp.dp, vertical = 16.dp)
                )
            }
            TextSizeControl(settings.textSize) { onSettingsChange(settings.copy(textSize = it)) }
            FontControl(settings.font) { onSettingsChange(settings.copy(font = it)) }
            Column {
                ControlLabel(stringResource(R.string.font_weight_label))
                ChoiceRow(
                    options = listOf(
                        ReaderFontWeight.REGULAR to stringResource(R.string.weight_regular),
                        ReaderFontWeight.MEDIUM to stringResource(R.string.weight_medium),
                        ReaderFontWeight.BOLD to stringResource(R.string.weight_bold)
                    ),
                    selected = settings.fontWeight,
                    onSelect = { onSettingsChange(settings.copy(fontWeight = it)) }
                )
            }
            LineSpacingSlider(settings.lineSpacing) {
                onSettingsChange(settings.copy(lineSpacing = it))
            }
            Column {
                ControlLabel(stringResource(R.string.margins_label))
                ChoiceRow(
                    options = listOf(
                        ReaderMargin.NARROW to stringResource(R.string.margin_narrow),
                        ReaderMargin.NORMAL to stringResource(R.string.margin_normal),
                        ReaderMargin.WIDE to stringResource(R.string.margin_wide)
                    ),
                    selected = settings.margin,
                    onSelect = { onSettingsChange(settings.copy(margin = it)) }
                )
            }
            TextButton(onClick = { onSettingsChange(ReaderSettings()) }) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                Text(
                    text = stringResource(R.string.reset_defaults),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun LineSpacingSlider(lineSpacing: Float, onLineSpacingChange: (Float) -> Unit) {
    var sliderValue by remember(lineSpacing) { mutableFloatStateOf(lineSpacing) }
    val steps = ((ReaderSettings.MAX_LINE_SPACING - ReaderSettings.MIN_LINE_SPACING) * 10).roundToInt() - 1

    Column {
        ControlLabel(stringResource(R.string.line_spacing_label), formatLineSpacing(sliderValue))
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
                // Round to one decimal so presets in the reader sheet can match
                val rounded = (it * 10).roundToInt() / 10f
                if (rounded != lineSpacing) onLineSpacingChange(rounded)
            },
            valueRange = ReaderSettings.MIN_LINE_SPACING..ReaderSettings.MAX_LINE_SPACING,
            steps = steps
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TextLayoutScreenPreview() {
    HyangsangTheme {
        TextLayoutScreen(settings = ReaderSettings(), onSettingsChange = {}, onBackClick = {})
    }
}
