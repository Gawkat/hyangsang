package dev.kettu.hyangsang.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

// Muted colours for covers drawn from the title, light enough text on all of them
private val COVER_COLORS = listOf(
    Color(0xFF2B5F75),
    Color(0xFF5E6B3A),
    Color(0xFF8A5A44),
    Color(0xFF4A4E69),
    Color(0xFF6B4A5E),
    Color(0xFF3F6B5C)
)

/**
 * A book's cover at the 2:3 shape of most covers: its image if it has one, or else one drawn
 * from its title and author.
 */
@Composable
fun BookCover(
    title: String,
    author: String?,
    coverFile: File?,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 14.sp,
    elevation: Dp = 1.dp
) {
    var imageFailed by remember(coverFile) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(4.dp),
        shadowElevation = elevation,
        modifier = modifier.aspectRatio(2f / 3f)
    ) {
        if (coverFile != null && !imageFailed) {
            AsyncImage(
                model = coverFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DrawnCover(title, author, titleSize)
        }
    }
}

@Composable
private fun DrawnCover(title: String, author: String?, titleSize: TextUnit) {
    val color = COVER_COLORS[Math.floorMod(title.hashCode(), COVER_COLORS.size)]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .padding(horizontal = 9.dp, vertical = 10.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = title,
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = titleSize,
                lineHeight = titleSize * 1.3f,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            if (author != null) {
                Text(
                    text = author,
                    color = Color.White.copy(alpha = 0.85f),
                    fontFamily = FontFamily.Serif,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
