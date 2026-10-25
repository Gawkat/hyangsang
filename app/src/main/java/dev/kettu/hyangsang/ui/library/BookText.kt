package dev.kettu.hyangsang.ui.library

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.kettu.hyangsang.R
import dev.kettu.hyangsang.data.local.entity.Book

@Composable
fun fileSizeText(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

@Composable
fun percentText(fraction: Float): String = stringResource(R.string.percent, (fraction * 100).toInt())

@Composable
fun chapterText(index: Int, title: String?): String =
    if (title.isNullOrBlank()) {
        stringResource(R.string.chapter_number, index + 1)
    } else {
        stringResource(R.string.chapter_number_and_title, index + 1, title)
    }

val Book.isStarted: Boolean get() = lastReadDate != null && progress < 1f
val Book.isFinished: Boolean get() = lastReadDate != null && progress >= 1f
