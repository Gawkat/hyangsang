package dev.kettu.hyangsang.ui.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.parser.PlainText
import dev.kettu.hyangsang.ui.theme.HyangsangTheme
import kotlinx.coroutines.launch
import java.text.NumberFormat

// Text the user pasted or shared from another app, with a title they can fix before adding it
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTextScreen(
    initialTitle: String,
    initialText: String,
    onAdd: (title: String, text: String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var text by rememberSaveable { mutableStateOf(initialText) }
    val tooLong = text.length > PlainText.MAX_LENGTH
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_text_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_button)
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onAdd(title, text) },
                        enabled = text.isNotBlank() && !tooLong
                    ) {
                        Text(stringResource(R.string.add_text_button))
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.add_text_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.add_text_body_label)) },
                isError = tooLong,
                supportingText = if (tooLong) {
                    {
                        val format = NumberFormat.getIntegerInstance()
                        Text(
                            stringResource(
                                R.string.add_text_too_long,
                                format.format(text.length),
                                format.format(PlainText.MAX_LENGTH)
                            )
                        )
                    }
                } else {
                    null
                },
                minLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
            if (text.isEmpty()) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val pasted = clipboard.getClipEntry()?.clipData
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString()
                                ?: return@launch
                            // Like a share, a short first line becomes the title
                            val (pastedTitle, body) = PlainText.splitTitle(pasted)
                            if (title.isBlank() && pastedTitle != null) {
                                title = pastedTitle
                                text = body
                            } else {
                                text = pasted.trim()
                            }
                        }
                    }
                ) {
                    Icon(
                        Icons.Outlined.ContentPaste,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_text_paste))
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddTextScreenPreview() {
    HyangsangTheme {
        AddTextScreen(
            initialTitle = "주말 일기",
            initialText = "토요일 아침에는 비가 왔지만 오후에는 날씨가 맑아졌다.",
            onAdd = { _, _ -> },
            onBackClick = {}
        )
    }
}
