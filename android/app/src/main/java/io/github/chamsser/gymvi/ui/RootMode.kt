package io.github.chamsser.gymvi.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.testTag as semanticsTestTag
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R

enum class RootMode(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val testTag: String,
) {
    AI(R.string.root_mode_ai, R.drawable.ic_material_symbol_chat_24, "root-mode-ai"),
    MAP(R.string.root_mode_map, R.drawable.ic_material_symbol_map_24, "root-mode-map"),
    MY(R.string.root_mode_my, R.drawable.ic_material_symbol_person_24, "root-mode-my"),
}

@Composable
internal fun RootModeDock(
    selectedMode: RootMode,
    onModeSelected: (RootMode) -> Unit,
    modes: List<RootMode> = RootMode.entries,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("root-mode-dock"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(3.dp),
        ) {
            modes.forEach { mode ->
                val selected = mode == selectedMode
                val label = stringResource(mode.labelRes)
                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                        )
                        .selectable(
                            selected = selected,
                            onClick = { onModeSelected(mode) },
                            role = Role.Tab,
                        )
                        .clearAndSetSemantics {
                            contentDescription = label
                            role = Role.Tab
                            this.selected = selected
                            semanticsTestTag = mode.testTag
                            onClick(label = label) {
                                onModeSelected(mode)
                                true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(mode.iconRes),
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
