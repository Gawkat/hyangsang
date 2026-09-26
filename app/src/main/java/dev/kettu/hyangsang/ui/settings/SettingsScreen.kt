package dev.kettu.hyangsang.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.kettu.hyangsang.BuildConfig
import dev.kettu.hyangsang.Constants
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.RssFeed
import dev.kettu.hyangsang.data.prefs.ReaderFont
import dev.kettu.hyangsang.data.prefs.ReaderSettings
import dev.kettu.hyangsang.ui.reader.formatLineSpacing
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.theme.ThemePalette
import dev.kettu.hyangsang.ui.utils.DateTimeUtils

private enum class SettingsDialog { THEME, PALETTE, DICTIONARY_ATTRIBUTION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    currentPalette: ThemePalette,
    onPaletteChange: (ThemePalette) -> Unit,
    readerSettings: ReaderSettings,
    onTextLayoutClick: () -> Unit,
    showUnreadCounts: Boolean,
    onShowUnreadCountsChange: (Boolean) -> Unit,
    feeds: List<RssFeed>,
    onManageFeedsClick: () -> Unit,
    onOssLicensesClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var openDialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    val themeOptions = listOf(
        "Light" to stringResource(R.string.theme_light),
        "Dark" to stringResource(R.string.theme_dark),
        "System default" to stringResource(R.string.theme_system)
    )
    val paletteOptions = buildList {
        add(ThemePalette.HYANGSANG to stringResource(R.string.palette_hyangsang))
        add(ThemePalette.SOLARIZED to stringResource(R.string.palette_solarized))
        add(ThemePalette.GRUVBOX to stringResource(R.string.palette_gruvbox))
        if (ThemePalette.isDynamicAvailable) {
            add(ThemePalette.DYNAMIC to stringResource(R.string.palette_dynamic))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(stringResource(R.string.appearance_section))
            SettingsItem(
                icon = Icons.Outlined.Contrast,
                title = stringResource(R.string.theme_setting),
                summary = themeOptions.labelFor(currentTheme),
                onClick = { openDialog = SettingsDialog.THEME }
            )
            SettingsItem(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.palette_setting),
                summary = paletteOptions.labelFor(currentPalette),
                onClick = { openDialog = SettingsDialog.PALETTE }
            )

            SectionHeader(stringResource(R.string.reader_preferences_section))
            ListItem(
                headlineContent = { Text(stringResource(R.string.text_layout_title)) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.text_layout_summary,
                            readerSettings.textSize,
                            stringResource(
                                if (readerSettings.font == ReaderFont.SERIF) R.string.font_serif else R.string.font_sans
                            ),
                            formatLineSpacing(readerSettings.lineSpacing)
                        )
                    )
                },
                leadingContent = { Icon(Icons.Outlined.FormatSize, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onTextLayoutClick)
            )

            SectionHeader(stringResource(R.string.feeds_title))
            ListItem(
                headlineContent = { Text(stringResource(R.string.manage_feeds)) },
                supportingContent = { FeedsSummary(feeds) },
                leadingContent = { Icon(Icons.Outlined.RssFeed, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onManageFeedsClick)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.unread_counts_setting)) },
                supportingContent = { Text(stringResource(R.string.unread_counts_summary)) },
                leadingContent = { Icon(Icons.Outlined.Numbers, contentDescription = null) },
                trailingContent = { Switch(checked = showUnreadCounts, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = showUnreadCounts,
                    onValueChange = onShowUnreadCountsChange,
                    role = Role.Switch
                )
            )

            SectionHeader(stringResource(R.string.about_label))
            SettingsItem(
                icon = Icons.AutoMirrored.Outlined.Article,
                title = stringResource(R.string.oss_licenses_nav),
                onClick = onOssLicensesClick
            )
            SettingsItem(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = stringResource(R.string.dictionary_attribution_label),
                onClick = { openDialog = SettingsDialog.DICTIONARY_ATTRIBUTION }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.report_issue_nav)) },
                supportingContent = { Text(stringResource(R.string.report_issue_summary)) },
                leadingContent = { Icon(Icons.Outlined.BugReport, contentDescription = null) },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    openInBrowser(context, Constants.GITHUB_REPORT_ISSUE_URL)
                }
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.version_label, BuildConfig.VERSION_NAME))
                },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.released_label,
                            DateTimeUtils.formatMonthYear(BuildConfig.BUILD_TIME)
                        )
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) }
            )
        }
    }

    val dismiss = { openDialog = null }
    when (openDialog) {
        SettingsDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.theme_setting),
            options = themeOptions,
            selected = currentTheme,
            onSelect = { onThemeChange(it); dismiss() },
            onDismiss = dismiss
        )

        SettingsDialog.PALETTE -> ChoiceDialog(
            title = stringResource(R.string.palette_setting),
            options = paletteOptions,
            selected = currentPalette,
            onSelect = { onPaletteChange(it); dismiss() },
            onDismiss = dismiss
        )

        SettingsDialog.DICTIONARY_ATTRIBUTION -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.dictionary_attribution_label)) },
            text = { Text(stringResource(R.string.dictionary_attribution_text)) },
            confirmButton = {
                TextButton(onClick = {
                    openInBrowser(context, Constants.DICTIONARY_COPYRIGHT_URL)
                }) {
                    Text(stringResource(R.string.view_license_information))
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )

        null -> Unit
    }
}

fun openInBrowser(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
}

private fun <T> List<Pair<T, String>>.labelFor(value: T): String =
    firstOrNull { it.first == value }?.second ?: ""

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    summary: String? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

// "7 of 8 on · 1 couldn't update", with the failure part in the error colour
@Composable
private fun FeedsSummary(feeds: List<RssFeed>) {
    val enabled = feeds.count { it.isEnabled }
    val failed = feeds.count { it.isEnabled && it.lastSyncError != null }
    val enabledText = stringResource(R.string.feeds_enabled_summary, enabled, feeds.size)
    val failedText = pluralStringResource(R.plurals.feeds_failed_summary, failed, failed)
    val errorColor = MaterialTheme.colorScheme.error

    Text(
        buildAnnotatedString {
            append(enabledText)
            if (failed > 0) {
                append(" · ")
                withStyle(SpanStyle(color = errorColor)) { append(failedText) }
            }
        }
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = value == selected,
                                onClick = { onSelect(value) },
                                role = Role.RadioButton
                            )
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    HyangsangTheme {
        SettingsScreen(
            currentTheme = "System default",
            onThemeChange = {},
            currentPalette = ThemePalette.HYANGSANG,
            onPaletteChange = {},
            readerSettings = ReaderSettings(),
            onTextLayoutClick = {},
            showUnreadCounts = false,
            onShowUnreadCountsChange = {},
            feeds = emptyList(),
            onManageFeedsClick = {},
            onOssLicensesClick = {},
            onBackClick = {}
        )
    }
}
