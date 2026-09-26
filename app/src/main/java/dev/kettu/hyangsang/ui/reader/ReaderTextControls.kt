package dev.kettu.hyangsang.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.prefs.ReaderFont
import dev.kettu.hyangsang.data.prefs.ReaderFontWeight
import dev.kettu.hyangsang.data.prefs.ReaderSettings
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Android's serif family falls back to Noto Serif CJK (명조) for Hangul on Android 9+
fun ReaderFont.fontFamily(): FontFamily = when (this) {
    ReaderFont.SANS -> FontFamily.SansSerif
    ReaderFont.SERIF -> FontFamily.Serif
}

fun ReaderFontWeight.fontWeight(): FontWeight = when (this) {
    ReaderFontWeight.REGULAR -> FontWeight.Normal
    ReaderFontWeight.MEDIUM -> FontWeight.Medium
    ReaderFontWeight.BOLD -> FontWeight.Bold
}

/** Body text style for article paragraphs. Line height is relative, so it scales with size. */
@Composable
fun readerBodyStyle(settings: ReaderSettings): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(
        fontSize = settings.textSize.sp,
        lineHeight = settings.lineSpacing.em,
        fontFamily = settings.font.fontFamily(),
        fontWeight = settings.fontWeight.fontWeight(),
        lineBreak = KoreanLineBreak,
        localeList = KoreanLocale
    )

fun formatLineSpacing(spacing: Float): String = String.format(Locale.ROOT, "%.1f×", spacing)

@Composable
fun ControlLabel(text: String, value: String? = null) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TextSizeControl(textSize: Int, onTextSizeChange: (Int) -> Unit) {
    // Local state keeps the thumb under the finger; the saved setting follows asynchronously
    var sliderValue by remember(textSize) { mutableFloatStateOf(textSize.toFloat()) }

    Column {
        ControlLabel(stringResource(R.string.text_size_label), "${sliderValue.roundToInt()} sp")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("가", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    val rounded = it.roundToInt()
                    if (rounded != textSize) onTextSizeChange(rounded)
                },
                valueRange = ReaderSettings.MIN_TEXT_SIZE.toFloat()..ReaderSettings.MAX_TEXT_SIZE.toFloat(),
                steps = ReaderSettings.MAX_TEXT_SIZE - ReaderSettings.MIN_TEXT_SIZE - 1,
                modifier = Modifier.weight(1f)
            )
            Text("가", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FontControl(font: ReaderFont, onFontChange: (ReaderFont) -> Unit) {
    Column {
        ControlLabel(stringResource(R.string.font_label))
        ChoiceRow(
            options = listOf(
                ReaderFont.SANS to stringResource(R.string.font_sans),
                ReaderFont.SERIF to stringResource(R.string.font_serif)
            ),
            selected = font,
            onSelect = onFontChange,
            labelStyle = { MaterialTheme.typography.labelLarge.copy(fontFamily = it.fontFamily()) }
        )
    }
}

// Presets for the reader sheet; the full settings screen uses a slider instead
private val LineSpacingPresets = listOf(1.4f, 1.6f, 1.9f)

@Composable
fun LineSpacingPresetControl(lineSpacing: Float, onLineSpacingChange: (Float) -> Unit) {
    val labels = listOf(
        stringResource(R.string.spacing_tight),
        stringResource(R.string.spacing_normal),
        stringResource(R.string.spacing_loose)
    )
    Column {
        ControlLabel(stringResource(R.string.line_spacing_label), formatLineSpacing(lineSpacing))
        ChoiceRow(
            options = LineSpacingPresets.zip(labels),
            // A custom value from the settings screen matches no preset
            selected = LineSpacingPresets.firstOrNull { abs(it - lineSpacing) < 0.05f },
            onSelect = onLineSpacingChange
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: @Composable (T) -> TextStyle = { MaterialTheme.typography.labelLarge }
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size)
            ) {
                Text(label, style = labelStyle(value))
            }
        }
    }
}
