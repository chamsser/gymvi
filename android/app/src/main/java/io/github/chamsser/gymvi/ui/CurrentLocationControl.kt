package io.github.chamsser.gymvi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R

enum class CurrentLocationControlState {
    IDLE,
    REQUESTING,
    ACTIVE,
    DENIED,
}

@Composable
internal fun CurrentLocationControl(
    state: CurrentLocationControlState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.current_location)
    val stateLabel = when (state) {
        CurrentLocationControlState.IDLE -> label
        CurrentLocationControlState.REQUESTING -> stringResource(
            R.string.current_location_requesting,
        )

        CurrentLocationControlState.ACTIVE -> stringResource(R.string.current_location_active)
        CurrentLocationControlState.DENIED -> stringResource(R.string.current_location_denied)
    }
    val containerColor = MaterialTheme.colorScheme.surface
    val design = LocalNativeDesign.current
    val iconColor = when (state) {
        CurrentLocationControlState.REQUESTING,
        CurrentLocationControlState.ACTIVE,
        -> MaterialTheme.colorScheme.primary

        CurrentLocationControlState.DENIED -> MaterialTheme.colorScheme.onErrorContainer
        CurrentLocationControlState.IDLE -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (state == CurrentLocationControlState.DENIED) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shadowElevation = 2.dp,
            ) {
                Text(
                    text = stateLabel,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(if (design.usesOriginalMapChrome) 52.dp else 48.dp)
                .testTag("current-location-button")
                .semantics {
                    contentDescription = label
                    stateDescription = stateLabel
                },
            shape = if (design.usesOriginalMapChrome || design.style.surface == NativeSurface.FLOATING || design.style.surface == NativeSurface.TONAL) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(design.style.cornerDp.dp),
            color = containerColor,
            contentColor = iconColor,
            shadowElevation = if (design.usesOriginalMapChrome) 5.dp else if (design.style.surface == NativeSurface.FLOATING) 4.dp else 1.dp,
            border = if (!design.usesOriginalMapChrome && design.style.surface != NativeSurface.FLOATING) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        ) {
            if (state == CurrentLocationControlState.REQUESTING) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(15.dp),
                    color = iconColor,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_material_symbol_my_location_24),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
    }
}
