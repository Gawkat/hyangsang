package dev.kettu.hyangsang.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.ui.theme.HyangsangTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.menu_title)) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
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

            Spacer(modifier = Modifier.weight(1f))
            AppInfo()
        }
    }
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
            text = stringResource(R.string.version_label, "0.1.0"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = stringResource(R.string.released_label, "April 2026"),
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
            onSettingsClick = {}
        )
    }
}