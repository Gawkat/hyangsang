package dev.kettu.hyangsang.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kettu.hyangsang.R

// The icon of the Manage feeds rows, with a dot when there are new built-in feeds to review
@Composable
fun ManageFeedsIcon(hasNewFeeds: Boolean) {
    val description = stringResource(R.string.new_built_in_feeds_available)
    BadgedBox(
        badge = {
            if (hasNewFeeds) {
                Badge(modifier = Modifier.semantics { contentDescription = description })
            }
        }
    ) {
        Icon(Icons.Outlined.RssFeed, contentDescription = null)
    }
}
