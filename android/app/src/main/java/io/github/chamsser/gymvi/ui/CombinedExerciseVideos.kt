package io.github.chamsser.gymvi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.ExerciseContentItem

/** Media stays readable inside an answer, with a glimpse of the next item when there are several. */
@Composable
internal fun CombinedExerciseVideos(
    contents: List<ExerciseContentItem>,
    modifier: Modifier = Modifier,
    onOpen: ((ExerciseContentItem) -> Unit)? = null,
    preview: Boolean = false,
) {
    if (contents.isEmpty()) return
    val context = LocalContext.current
    val open: (ExerciseContentItem) -> Unit = onOpen ?: { openExerciseContent(context, it.contentUrl) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.discovery_content_title), style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        BoxWithConstraints(Modifier.fillMaxWidth().testTag("ai-video-list")) {
            if (contents.size == 1) {
                CombinedExerciseVideo(contents.first(), Modifier.fillMaxWidth(), preview) { open(contents.first()) }
            } else {
                val cardWidth = (maxWidth * .88f).coerceAtMost(440.dp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(contents, key = { it.contentId }) { content ->
                        CombinedExerciseVideo(content, Modifier.width(cardWidth), preview) { open(content) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CombinedExerciseVideo(content: ExerciseContentItem, modifier: Modifier, preview: Boolean, onOpen: () -> Unit) {
    val openLabel = stringResource(R.string.ai_video_open)
    Column(
        modifier.clip(RoundedCornerShape(16.dp))
            .then(if (preview) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpen))
            .testTag("discovery-content-${content.contentId}"),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .testTag("ai-video-thumbnail-${content.contentId}"),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(model = content.thumbnailUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.size(48.dp).background(Color.Black.copy(alpha = .62f), CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_material_symbol_play_arrow_24), contentDescription = null,
                    modifier = Modifier.size(30.dp), tint = Color.White)
            }
            exerciseVideoDuration(content.durationSeconds)?.let { duration ->
                Text(duration, color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color.Black.copy(alpha = .76f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp).testTag("ai-video-duration-${content.contentId}"))
            }
            if (preview) Text(stringResource(R.string.ai_video_sample_badge), color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(Color.Black.copy(alpha = .76f), RoundedCornerShape(4.dp)).padding(6.dp))
        }
        Column(Modifier.padding(top = 10.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(content.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val channel = content.channelName?.takeIf(String::isNotBlank) ?: content.sourceName
            if (channel.isNotBlank()) Text(channel, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("discovery-content-source-${content.contentId}"))
            val views = exerciseVideoCount(content.viewCount)
            val likes = exerciseVideoCount(content.likeCount)
            if (views != null || likes != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.testTag("ai-video-statistics-${content.contentId}")) {
                    if (views != null) Text(stringResource(R.string.ai_video_views, views),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (likes != null) Text(stringResource(R.string.ai_video_likes, likes),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
