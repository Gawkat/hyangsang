package dev.kettu.hyangsang.ui.components

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.ui.theme.GruvboxLightColors
import dev.kettu.hyangsang.ui.theme.HyangsangLightColors
import dev.kettu.hyangsang.ui.theme.SolarizedLightColors

// Same artwork as the launcher and splash drawables, cropped to the mark
private const val HIEUT = "M54,22.5a3,3 0,0 1,3 3v2a3,3 0,0 1,-6 0v-2a3,3 0,0 1,3 -3z" +
    "M42.75,33h22.5a2.75,2.75 0,0 1,0 5.5h-22.5a2.75,2.75 0,0 1,0 -5.5z"
private const val SUN = "M54,41a9.5,9.5 0,1 1,0 19a9.5,9.5 0,1 1,0 -19z"
private const val BOOK = "M52.5,65.5C47,62 40,61 33,62V78.5C40,77.5 47,78.5 52.5,82Z" +
    "M55.5,65.5C61,62 68,61 75,62V78.5C68,77.5 61,78.5 55.5,82Z"

/** The Hyangsang mark: 히읗 whose ㅇ is a sun rising over an open book. */
@Composable
fun HyangsangLogo(
    modifier: Modifier = Modifier,
    ink: Color = MaterialTheme.colorScheme.primary,
    accent: Color = logoAccent()
) {
    val logo = remember(ink, accent) {
        ImageVector.Builder(
            name = "HyangsangLogo",
            defaultWidth = 60.dp,
            defaultHeight = 60.dp,
            viewportWidth = 60f,
            viewportHeight = 60f
        ).apply {
            // Centre the 42 x 59.5 mark in a square viewport
            group(translationX = -24f, translationY = -22.25f) {
                addPath(pathData = addPathNodes(HIEUT), fill = SolidColor(ink))
                addPath(pathData = addPathNodes(SUN), fill = SolidColor(accent))
                addPath(pathData = addPathNodes(BOOK), fill = SolidColor(ink))
            }
        }.build()
    }
    Image(imageVector = logo, contentDescription = null, modifier = modifier)
}

/**
 * The light palettes' tertiary colours are too dark for the sun to read as one, so they use
 * a brighter gold from the same palette. Dark palettes and wallpaper colours use tertiary.
 */
@Composable
private fun logoAccent(): Color = when (val scheme = MaterialTheme.colorScheme) {
    HyangsangLightColors -> Color(0xFFC29A2A)
    SolarizedLightColors -> Color(0xFFB58900)
    GruvboxLightColors -> Color(0xFFD79921)
    else -> scheme.tertiary
}
