package dev.kettu.hyangsang.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

enum class ThemePalette(val key: String) {
    HYANGSANG("hyangsang"),
    SOLARIZED("solarized"),
    GRUVBOX("gruvbox"),

    // Wallpaper-based colours, Android 12+ only
    DYNAMIC("dynamic");

    companion object {
        val isDynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        fun fromKey(key: String?): ThemePalette =
            entries.firstOrNull { it.key == key && (it != DYNAMIC || isDynamicAvailable) }
                ?: HYANGSANG
    }
}

@Composable
fun HyangsangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    palette: ThemePalette = ThemePalette.HYANGSANG,
    content: @Composable () -> Unit
) {
    val hyangsangColors = if (darkTheme) HyangsangDarkColors else HyangsangLightColors
    val colorScheme: ColorScheme = when (palette) {
        ThemePalette.HYANGSANG -> hyangsangColors
        ThemePalette.SOLARIZED -> if (darkTheme) SolarizedDarkColors else SolarizedLightColors
        ThemePalette.GRUVBOX -> if (darkTheme) GruvboxDarkColors else GruvboxLightColors
        ThemePalette.DYNAMIC -> if (ThemePalette.isDynamicAvailable) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            hyangsangColors
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
