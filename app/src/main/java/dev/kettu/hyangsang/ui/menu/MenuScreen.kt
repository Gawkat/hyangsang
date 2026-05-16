package dev.kettu.hyangsang.ui.menu

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.kettu.hyangsang.BuildConfig
import dev.kettu.hyangsang.Constants
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import dev.kettu.hyangsang.ui.utils.DateTimeUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    onSettingsClick: () -> Unit,
    onOssLicensesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDictionaryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_title)) }
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
            // Settings
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_nav)) },
                leadingContent = {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_nav)
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onSettingsClick() }
            )

            Text(
                text = stringResource(R.string.about_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            // OSS Licenses
            ListItem(
                headlineContent = { Text(stringResource(R.string.oss_licenses_nav)) },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.Article,
                        contentDescription = stringResource(R.string.oss_licenses_nav)
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { onOssLicensesClick() }
            )

            // Dictionary attribution
            ListItem(
                headlineContent = { Text(stringResource(R.string.dictionary_attribution_label)) },
                leadingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = stringResource(R.string.dictionary_attribution_label)
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable { showDictionaryDialog = true }
            )

            Text(
                text = stringResource(R.string.support_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            // Report issue
            ListItem(
                headlineContent = { Text(stringResource(R.string.report_issue_nav)) },
                leadingContent = {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = stringResource(R.string.report_issue_nav)
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    openInBrowser(
                        context,
                        Constants.GITHUB_REPORT_ISSUE_URL
                    )
                }
            )

            Spacer(modifier = Modifier.weight(1f))
            AppInfo()
            Spacer(modifier = Modifier.height(8.dp))
        }
    }


    @Suppress("AssignedValueIsNeverRead")
    if (showDictionaryDialog) {
        AlertDialog(
            onDismissRequest = { showDictionaryDialog = false },
            title = { Text(stringResource(R.string.dictionary_attribution_label)) },
            text = { Text(stringResource(R.string.dictionary_attribution_text)) },
            confirmButton = {
                TextButton(onClick = {
                    openInBrowser(
                        context,
                        Constants.DICTIONARY_COPYRIGHT_URL
                    )
                }) {
                    Text(stringResource(R.string.view_license_information))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDictionaryDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }
}

fun openInBrowser(context: Context, url: String) {
    context.startActivity(
        Intent(
            Intent.ACTION_VIEW,
            url.toUri()
        )
    )
}

@Composable
fun AppInfo() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = stringResource(R.string.version_label, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = stringResource(
                R.string.released_label,
                DateTimeUtils.formatMonthYear(BuildConfig.BUILD_TIME)
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MenuScreenPreview() {
    HyangsangTheme {
        MenuScreen(
            onSettingsClick = {},
            onOssLicensesClick = {}
        )
    }
}