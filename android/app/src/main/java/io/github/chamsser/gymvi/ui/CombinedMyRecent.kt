package io.github.chamsser.gymvi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.RecentFacility

/**
 * 최근 본 시설 as this device keeps them, newest first: each with its own remove button, and one
 * button above the list that clears them all after asking. The list is the one the search screen
 * and the feed use. A facility whose name was never seen reads 알 수 없음; nothing is looked up here.
 */
@Composable
internal fun CombinedMyRecentPage(
    recentFacilities: List<RecentFacility>,
    palette: CombinedPalette,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    CombinedMySubPage(
        title = stringResource(R.string.facility_recent_title),
        palette = palette,
        onBack = onBack,
        modifier = modifier.testTag("my-recent-page"),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = { confirmingClear = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("my-recent-clear"),
            ) {
                Text(text = stringResource(R.string.my_recent_facilities_clear))
            }
        }
        CombinedMyGroup(
            label = null,
            palette = palette,
            rows = recentFacilities.map { facility ->
                val row: @Composable (Modifier) -> Unit = { rowModifier ->
                    CombinedMyRecentRow(
                        facility = facility,
                        onRemove = { onRemove(facility.facilityId) },
                        modifier = rowModifier,
                    )
                }
                row
            },
        )
    }
    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            modifier = Modifier.testTag("my-recent-clear-dialog"),
            title = { Text(text = stringResource(R.string.my_recent_facilities_clear_title), style = recentDialogTextStyle()) },
            text = { Text(text = stringResource(R.string.my_recent_facilities_clear_body), style = recentDialogTextStyle()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        onClear()
                    },
                    modifier = Modifier.testTag("my-recent-clear-confirm"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = stringResource(R.string.my_recent_facilities_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }, modifier = Modifier.testTag("my-recent-clear-cancel")) {
                    Text(text = stringResource(R.string.my_personalization_cancel))
                }
            },
        )
    }
}

/** The dialog's Korean lines break between words as the 내 정보 descriptions do. */
@Composable
private fun recentDialogTextStyle(): TextStyle = LocalTextStyle.current.merge(CombinedMyDescriptionBreak)

/** One recent facility: its name, or 알 수 없음 in the quieter color, and the button that removes it. */
@Composable
private fun CombinedMyRecentRow(
    facility: RecentFacility,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val name = facility.name ?: stringResource(R.string.unknown_value)
    val removeLabel = stringResource(R.string.facility_recent_remove, name)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            // The name is read as the row; the remove button stays a control of its own.
            .semantics(mergeDescendants = true) {}
            .testTag("my-recent-${facility.facilityId}")
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            color = if (facility.name == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            onClick = onRemove,
            modifier = Modifier
                .size(44.dp)
                .testTag("my-recent-remove-${facility.facilityId}")
                .semantics {
                    contentDescription = removeLabel
                    role = Role.Button
                },
            shape = CircleShape,
            color = Color.Transparent,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_material_symbol_close_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
