package io.github.chamsser.gymvi.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Colors of the combined design's own AI and 내 정보 pages. The AI page is a faint grey so its
 * white buttons and composer stand on it; 내 정보 groups white rows on a deeper grey, as the
 * settings screens the user chose do. Shadows vanish on a dark page, so raised parts are lighter
 * there instead.
 */
@Immutable
internal data class CombinedPalette(
    val page: Color,
    val raised: Color,
    val quiet: Color,
    val groupedPage: Color,
    val groupedRow: Color,
    val shadow: Boolean,
)

@Composable
internal fun combinedPalette(): CombinedPalette {
    val colors = MaterialTheme.colorScheme
    return if (colors.background.luminance() < .5f) {
        CombinedPalette(
            page = colors.background,
            raised = colors.surfaceVariant,
            quiet = lerp(colors.surfaceVariant, colors.onSurface, .06f),
            groupedPage = colors.background,
            groupedRow = lerp(colors.surface, colors.surfaceVariant, .5f),
            shadow = false,
        )
    } else {
        CombinedPalette(
            page = lerp(colors.surface, colors.surfaceVariant, .5f),
            raised = colors.surface,
            quiet = lerp(colors.surfaceVariant, colors.onSurface, .035f),
            groupedPage = colors.surfaceVariant,
            groupedRow = colors.surface,
            shadow = true,
        )
    }
}

internal val CombinedHeaderHeight = 64.dp

/**
 * A see-through header backdrop: dense enough behind the header's buttons and title, clearing a
 * little below them so content scrolling underneath stays faintly visible.
 */
internal fun Modifier.combinedHeaderFade(color: Color, fade: Dp = 20.dp): Modifier = drawBehind {
    val fadeEnd = size.height + fade.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            0f to color.copy(alpha = .94f),
            size.height * .6f / fadeEnd to color.copy(alpha = .86f),
            size.height / fadeEnd to color.copy(alpha = .6f),
            1f to color.copy(alpha = 0f),
            startY = 0f,
            endY = fadeEnd,
        ),
        size = Size(size.width, fadeEnd),
    )
}

/** A header button: a raised circle, so it stays legible over whatever scrolls beneath. */
@Composable
internal fun CombinedRoundButton(
    @DrawableRes iconRes: Int,
    description: String,
    testTag: String,
    palette: CombinedPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .testTag(testTag),
        shape = CircleShape,
        color = palette.raised,
        shadowElevation = if (palette.shadow) 2.dp else 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
