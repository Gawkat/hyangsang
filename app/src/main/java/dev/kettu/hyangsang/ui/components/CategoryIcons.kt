package dev.kettu.hyangsang.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.Factory
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.kettu.hyangsang.data.defaults.DefaultCategory

/**
 * Icons for categories by name. Built-in categories are matched by their name in every app
 * language; categories the user made get a generic label icon.
 */
@Composable
fun rememberCategoryIcons(): (String) -> ImageVector {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val icons = DefaultCategory.byLabel(context).mapValues { it.value.icon() }
        val lookup: (String) -> ImageVector = { category ->
            icons[category] ?: Icons.AutoMirrored.Outlined.Label
        }
        lookup
    }
}

private fun DefaultCategory.icon(): ImageVector = when (this) {
    DefaultCategory.NEWS -> Icons.Outlined.Newspaper
    DefaultCategory.POLITICS -> Icons.Outlined.AccountBalance
    DefaultCategory.NORTH_KOREA -> Icons.Outlined.Flag
    DefaultCategory.ECONOMY -> Icons.AutoMirrored.Outlined.TrendingUp
    DefaultCategory.MARKET -> Icons.Outlined.CandlestickChart
    DefaultCategory.INDUSTRY -> Icons.Outlined.Factory
    DefaultCategory.SOCIETY -> Icons.Outlined.Groups
    DefaultCategory.LOCAL -> Icons.Outlined.Place
    DefaultCategory.INTERNATIONAL -> Icons.Outlined.Public
    DefaultCategory.CULTURE -> Icons.Outlined.TheaterComedy
    DefaultCategory.HEALTH -> Icons.Outlined.HealthAndSafety
    DefaultCategory.SCIENCE -> Icons.Outlined.Science
    DefaultCategory.ENTERTAINMENT -> Icons.Outlined.Movie
    DefaultCategory.SPORTS -> Icons.Outlined.SportsSoccer
    DefaultCategory.OPINION -> Icons.Outlined.Forum
    DefaultCategory.PEOPLE -> Icons.Outlined.Person
}
