package dev.kettu.hyangsang.ui.setup

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.Constants
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.defaults.DefaultData
import dev.kettu.hyangsang.data.defaults.DefaultFeed
import dev.kettu.hyangsang.data.defaults.FeedTopic
import dev.kettu.hyangsang.data.repository.LookupContext
import dev.kettu.hyangsang.ui.components.HyangsangLogo
import dev.kettu.hyangsang.ui.feeds.BuiltInFeedsList
import dev.kettu.hyangsang.ui.reader.ClickableText
import dev.kettu.hyangsang.ui.reader.DefinitionOverlay
import dev.kettu.hyangsang.ui.reader.WordSelection
import dev.kettu.hyangsang.ui.reader.rangeIn
import dev.kettu.hyangsang.ui.settings.openInBrowser
import dev.kettu.hyangsang.ui.viewmodel.DictionaryViewModel

private enum class SetupStep { WELCOME, TOPICS, SOURCES, DONE }

// A sentence to try lookups on. 비 has several entries, and 내리겠습니다 and 챙기세요 show that
// conjugated words are found by their dictionary form
private const val SAMPLE_TEXT = "오늘 서울은 하루 종일 비가 내리겠습니다. 외출할 때 우산을 꼭 챙기세요."
private const val SAMPLE_TEXT_ID = "setup_sample"

/**
 * The first-run setup: a welcome that shows word lookups, then topics that pick built-in
 * feeds, the feeds to check over, and where to change things later. [onAddFeeds] is called
 * once, with the picked feeds or, if the user skips the setup, every built-in feed.
 * [onFinish] leaves the setup.
 */
@Composable
fun SetupScreen(
    dictionaryViewModel: DictionaryViewModel,
    onAddFeeds: (List<DefaultFeed>) -> Unit,
    onFinish: () -> Unit
) {
    var step by rememberSaveable { mutableStateOf(SetupStep.WELCOME) }
    var topics by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var easierReading by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var pickedFromTopics by rememberSaveable { mutableStateOf(false) }

    // The feeds are added when leaving the list of them, so there's nothing to go back to
    BackHandler(enabled = step != SetupStep.WELCOME) {
        when (step) {
            SetupStep.TOPICS -> step = SetupStep.WELCOME
            SetupStep.SOURCES -> step = SetupStep.TOPICS
            else -> onFinish()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(targetState = step, label = "setup step") { current ->
            when (current) {
                SetupStep.WELCOME -> WelcomeStep(
                    dictionaryViewModel = dictionaryViewModel,
                    onSkip = {
                        onAddFeeds(DefaultData.defaultFeeds)
                        onFinish()
                    },
                    onNext = { step = SetupStep.TOPICS }
                )

                SetupStep.TOPICS -> TopicsStep(
                    topics = topics.mapNotNull { name -> FeedTopic.entries.firstOrNull { it.name == name } }.toSet(),
                    onTopicsChange = { new -> topics = new.map { it.name } },
                    easierReading = easierReading,
                    onEasierReadingChange = { easierReading = it },
                    onBack = { step = SetupStep.WELCOME },
                    onSkip = {
                        picked = emptyList()
                        pickedFromTopics = false
                        step = SetupStep.SOURCES
                    },
                    onNext = { selected ->
                        picked = FeedTopic.feedsFor(selected, easierReading).map { it.url }
                        pickedFromTopics = true
                        step = SetupStep.SOURCES
                    }
                )

                SetupStep.SOURCES -> SourcesStep(
                    picked = picked,
                    onPickedChange = { picked = it },
                    pickedFromTopics = pickedFromTopics,
                    onBack = { step = SetupStep.TOPICS },
                    onAdd = {
                        onAddFeeds(DefaultData.defaultFeeds.filter { it.url in picked })
                        step = SetupStep.DONE
                    }
                )

                SetupStep.DONE -> DoneStep(onFinish = onFinish)
            }
        }
    }
}

@Composable
private fun WelcomeStep(
    dictionaryViewModel: DictionaryViewModel,
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    val context = LocalContext.current
    val lookupResult by dictionaryViewModel.lookupResult.collectAsState()
    val isLookingUp by dictionaryViewModel.isLookingUp.collectAsState()
    var selection by remember { mutableStateOf<WordSelection?>(null) }
    var lookupWord by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose { dictionaryViewModel.clearLookup() }
    }

    StepLayout(
        step = SetupStep.WELCOME,
        title = stringResource(R.string.setup_welcome_title),
        description = stringResource(R.string.setup_welcome_description),
        header = { HyangsangLogo(modifier = Modifier.size(56.dp)) },
        bottomBar = {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.setup_skip_all)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onNext) { Text(stringResource(R.string.setup_get_started)) }
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.setup_try_lookup),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                )
                ClickableText(
                    text = SAMPLE_TEXT,
                    textId = SAMPLE_TEXT_ID,
                    selectedRange = selection.rangeIn(SAMPLE_TEXT_ID),
                    onWordClick = { wordSelection, word ->
                        selection = wordSelection
                        lookupWord = word
                        dictionaryViewModel.lookupWord(word, LookupContext(wordSelection.sentence))
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.8f
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
                if (selection != null) {
                    HorizontalDivider()
                    DefinitionOverlay(
                        selectedWord = lookupWord,
                        lookupResults = lookupResult,
                        isLoading = isLookingUp,
                        onSearchWeb = { term ->
                            openInBrowser(context, Constants.WEB_DICTIONARY_SEARCH_URL + Uri.encode(term))
                        },
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .padding(top = 16.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.setup_welcome_next),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun TopicsStep(
    topics: Set<FeedTopic>,
    onTopicsChange: (Set<FeedTopic>) -> Unit,
    easierReading: Boolean,
    onEasierReadingChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: (Set<FeedTopic>) -> Unit
) {
    val feedCount = remember(topics, easierReading) { FeedTopic.feedsFor(topics, easierReading).size }

    StepLayout(
        step = SetupStep.TOPICS,
        title = stringResource(R.string.setup_topics_title),
        description = stringResource(R.string.setup_topics_description),
        bottomBar = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back_button)) }
            Spacer(Modifier.weight(1f))
            // What the choices add up to, before the list of them
            if (feedCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.setup_feed_count, feedCount, feedCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.setup_skip)) }
            Button(
                onClick = { onNext(topics) },
                enabled = feedCount > 0,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(stringResource(R.string.setup_next))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            // The level comes first: for a learner it matters more than the topics
            SectionLabel(stringResource(R.string.setup_reading_level))
            ListItem(
                headlineContent = { Text(stringResource(R.string.setup_easier_reading)) },
                supportingContent = { Text(stringResource(R.string.setup_easier_reading_description)) },
                leadingContent = { Checkbox(checked = easierReading, onCheckedChange = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .toggleable(
                        value = easierReading,
                        role = Role.Checkbox,
                        onValueChange = onEasierReadingChange
                    )
                    .padding(horizontal = 8.dp)
            )
            SectionLabel(stringResource(R.string.setup_topics), modifier = Modifier.padding(top = 16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                FeedTopic.entries.forEach { topic ->
                    val selected = topic in topics
                    FilterChip(
                        selected = selected,
                        onClick = { onTopicsChange(if (selected) topics - topic else topics + topic) },
                        label = { Text(stringResource(topic.label)) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SourcesStep(
    picked: List<String>,
    onPickedChange: (List<String>) -> Unit,
    pickedFromTopics: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit
) {
    StepLayout(
        step = SetupStep.SOURCES,
        title = stringResource(R.string.setup_sources_title),
        description = stringResource(
            if (pickedFromTopics) R.string.setup_sources_from_topics else R.string.setup_sources_description
        ),
        bottomBar = {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back_button)) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onAdd, enabled = picked.isNotEmpty()) {
                Text(
                    if (picked.isEmpty()) {
                        stringResource(R.string.add_feeds)
                    } else {
                        pluralStringResource(R.plurals.add_feeds_count, picked.size, picked.size)
                    }
                )
            }
        }
    ) {
        BuiltInFeedsList(
            feeds = DefaultData.defaultFeeds,
            addedUrls = emptySet(),
            newUrls = emptySet(),
            picked = picked,
            onPickedChange = onPickedChange,
            initiallyExpanded = false
        )
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    StepLayout(
        step = SetupStep.DONE,
        title = stringResource(R.string.setup_done_title),
        description = stringResource(R.string.setup_done_description),
        bottomBar = {
            Spacer(Modifier.weight(1f))
            Button(onClick = onFinish) { Text(stringResource(R.string.setup_start_reading)) }
        }
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            HintRow(Icons.Outlined.Menu, R.string.setup_hint_drawer_title, R.string.setup_hint_drawer)
            HintRow(Icons.Outlined.RssFeed, R.string.manage_feeds, R.string.setup_hint_manage_feeds)
            HintRow(Icons.Outlined.Add, R.string.setup_hint_own_feeds_title, R.string.setup_hint_own_feeds)
            HintRow(Icons.Outlined.Bookmarks, R.string.saved_nav, R.string.setup_hint_saved)
        }
    }
}

@Composable
private fun HintRow(icon: ImageVector, @StringRes title: Int, @StringRes description: Int) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

// A step's heading above its [content], which fills the rest of the screen above the [bottomBar]
@Composable
private fun StepLayout(
    step: SetupStep,
    title: String,
    description: String,
    bottomBar: @Composable RowScope.() -> Unit,
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
        ) {
            header?.invoke()
            Text(
                text = stringResource(R.string.setup_step, step.ordinal + 1, SetupStep.entries.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(modifier = Modifier.weight(1f)) { content() }
        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            content = bottomBar
        )
    }
}
