package io.github.chamsser.gymvi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.github.chamsser.gymvi.data.FacilityMapItem

@Composable
internal fun RouteControl(
    destination: FacilityMapItem?,
    onRouteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.route_find)
    val stateLabel = if (destination == null) {
        stringResource(R.string.route_enter_points)
    } else {
        stringResource(R.string.route_to_facility, destination.name)
    }
    val iconColor = MaterialTheme.colorScheme.primary
    val design = LocalNativeDesign.current

    Surface(
        onClick = onRouteRequested,
        modifier = modifier
            .size(if (design.usesOriginalMapChrome) 52.dp else 48.dp)
            .testTag("route-button")
            .semantics {
                contentDescription = label
                stateDescription = stateLabel
            },
        shape = if (design.usesOriginalMapChrome || design.style.surface == NativeSurface.FLOATING || design.style.surface == NativeSurface.TONAL) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(design.style.cornerDp.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = iconColor,
        shadowElevation = if (design.usesOriginalMapChrome) 5.dp else if (design.style.surface == NativeSurface.FLOATING) 4.dp else 1.dp,
        border = if (!design.usesOriginalMapChrome && design.style.surface != NativeSurface.FLOATING) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Box(
            modifier = Modifier.padding(13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_material_symbol_directions_24),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
