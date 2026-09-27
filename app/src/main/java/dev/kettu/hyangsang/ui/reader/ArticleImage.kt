package dev.kettu.hyangsang.ui.reader

import android.util.LruCache
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import dev.kettu.hyangsang.parser.ContentSpan

// Most news photos are 16:9, so this is the least jarring guess when the size is unknown
private const val DEFAULT_ASPECT_RATIO = 16f / 9f
private const val CROSSFADE_MILLIS = 250

// Remembers the real aspect ratio of loaded images, so revisited images don't resize again
private val aspectRatioCache = LruCache<String, Float>(256)

@Composable
fun ArticleImage(
    url: String,
    caption: String?,
    textId: String,
    selectedRange: TextRange?,
    onWordClick: (WordSelection, String) -> Unit,
    modifier: Modifier = Modifier,
    captionSpans: List<ContentSpan> = emptyList(),
    fontFamily: FontFamily = FontFamily.Default,
    width: Int? = null,
    height: Int? = null
) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(CROSSFADE_MILLIS)
            .build()
    }
    var aspectRatio by remember(url) {
        mutableFloatStateOf(
            aspectRatioCache.get(url)
                ?: aspectRatioOf(width?.toFloat(), height?.toFloat())
                ?: DEFAULT_ASPECT_RATIO
        )
    }
    var loadState by remember(url) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                // Must come before aspectRatio so a wrong guess eases into the real size
                .animateContentSize()
                .aspectRatio(aspectRatio)
        ) {
            // Qualified to avoid resolving to the ColumnScope overload of the outer Column
            androidx.compose.animation.AnimatedVisibility(
                visible = loadState !is AsyncImagePainter.State.Success,
                enter = EnterTransition.None,
                exit = fadeOut(tween(CROSSFADE_MILLIS))
            ) {
                ImagePlaceholder(isError = loadState is AsyncImagePainter.State.Error)
            }
            AsyncImage(
                model = request,
                contentDescription = caption,
                modifier = Modifier.fillMaxSize(),
                // Matches FillWidth once the ratio is known, and covers the box while resizing
                contentScale = ContentScale.Crop,
                onState = { state ->
                    loadState = state
                    if (state is AsyncImagePainter.State.Success) {
                        val size = state.painter.intrinsicSize
                        aspectRatioOf(size.width, size.height)?.let {
                            aspectRatio = it
                            aspectRatioCache.put(url, it)
                        }
                    }
                }
            )
        }
        if (!caption.isNullOrBlank()) {
            ClickableText(
                text = caption,
                textId = textId,
                spans = captionSpans,
                selectedRange = selectedRange,
                onWordClick = onWordClick,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = fontFamily
                ),
                modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
            )
        }
    }
}

@Composable
private fun ImagePlaceholder(isError: Boolean) {
    val color = MaterialTheme.colorScheme.surfaceContainerHighest
    if (isError) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp)
            )
        }
    } else {
        // Slow, low-contrast pulse; alpha is only read in the draw phase, so no recomposition
        val alpha by rememberInfiniteTransition(label = "imagePlaceholder").animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "imagePlaceholderAlpha"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(color = color, alpha = alpha) }
        )
    }
}

private fun aspectRatioOf(width: Float?, height: Float?): Float? {
    if (width == null || height == null) return null
    return (width / height).takeIf { it.isFinite() && it > 0f }
}
