package io.github.chamsser.gymvi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.UsageOptionComparisonDimension
import io.github.chamsser.gymvi.data.UsageOptionComparisonItem
import io.github.chamsser.gymvi.data.UsageOptionItem

@Composable
internal fun UsageOptionCompareScreen(
    requestedIds: List<String>,
    state: UsageOptionCompareState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOptionSelected: (UsageOptionComparisonItem) -> Unit,
) {
    val ids = when (state) {
        is UsageOptionCompareState.Loading -> state.ids
        is UsageOptionCompareState.Ready -> state.ids
        is UsageOptionCompareState.Error -> state.ids
        UsageOptionCompareState.Idle -> requestedIds
    }
    Surface(
        modifier = Modifier.fillMaxSize().testTag("usage-option-compare"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            UsageOptionScreenHeader(
                title = stringResource(R.string.usage_option_compare_title),
                subtitle = stringResource(R.string.usage_option_compare_subtitle, ids.size),
                onBack = onBack,
            )
            when (state) {
                UsageOptionCompareState.Idle,
                is UsageOptionCompareState.Loading -> Text(
                    text = stringResource(R.string.facility_loading_more),
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is UsageOptionCompareState.Error -> ComparisonError(state.retryable, onRetry)
                is UsageOptionCompareState.Ready -> {
                    val items = eligibleComparisonItems(state.data.items, state.ids)
                    if (items.size < 2) {
                        ComparisonError(retryable = false, onRetry = onRetry)
                    } else {
                        UsageOptionComparisonCard(
                            items = items,
                            dimensions = state.data.dimensions,
                            attribution = state.data.attribution,
                            programAsOf = state.data.programAsOf,
                            onOptionSelected = onOptionSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonError(retryable: Boolean, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text(
            text = stringResource(R.string.usage_option_compare_error),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (retryable) {
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
internal fun UsageOptionComparisonCard(
    items: List<UsageOptionComparisonItem>,
    dimensions: List<UsageOptionComparisonDimension>,
    attribution: String?,
    programAsOf: String?,
    onOptionSelected: (UsageOptionComparisonItem) -> Unit,
) {
    val itemIds = items.map(UsageOptionComparisonItem::usageOptionId)
    var selectedIds by rememberSaveable(itemIds) { mutableStateOf(itemIds.take(2)) }
    val selected = selectedIds.mapNotNull { id -> items.firstOrNull { it.usageOptionId == id } }
        .takeIf { it.size == 2 } ?: items.take(2)
    val dimensionByName = dimensions.associateBy(UsageOptionComparisonDimension::dimension)
    val detailsLabel = stringResource(R.string.usage_option_details)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            selected.forEachIndexed { slot, item ->
                ComparisonSlotSelector(
                    item = item,
                    candidates = items,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onChoose = { chosen ->
                        val ids = selected.map(UsageOptionComparisonItem::usageOptionId)
                        selectedIds = if (chosen.usageOptionId == ids[1 - slot]) {
                            ids.reversed()
                        } else {
                            ids.toMutableList().also { it[slot] = chosen.usageOptionId }
                        }
                    },
                )
            }
        }
        comparisonDimensionOrder.forEach { name ->
            val dimension = dimensionByName[name] ?: return@forEach
            val labelRes = comparisonDimensionLabel(name) ?: return@forEach
            ComparisonRow(
                label = stringResource(labelRes),
                dimension = dimension,
                items = selected,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            selected.forEach { item ->
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { onOptionSelected(item) },
                        modifier = Modifier.heightIn(min = 48.dp)
                            .semantics { contentDescription = item.option.programName + ", " + detailsLabel }
                            .testTag("usage-option-compare-details"),
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) {
                        Text(detailsLabel)
                    }
                }
            }
        }
        UsageOptionSourceDisclosure(attribution, programAsOf, showUnknown = true)
    }
}

@Composable
private fun ComparisonSlotSelector(
    item: UsageOptionComparisonItem,
    candidates: List<UsageOptionComparisonItem>,
    modifier: Modifier,
    onChoose: (UsageOptionComparisonItem) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val changeLabel = stringResource(R.string.usage_option_compare_change)
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxSize()
                .clickable(onClickLabel = changeLabel, role = Role.Button) { menuOpen = true }
                .testTag("usage-option-compare-header"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, gymviSubtleBorderColor()),
        ) {
            Row(modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 14.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.facilityName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = item.option.programName,
                        modifier = Modifier.padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_material_symbol_arrow_drop_down_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            candidates.forEach { candidate ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = candidate.option.programName,
                                fontWeight = if (candidate.usageOptionId == item.usageOptionId) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                            Text(
                                text = candidate.facilityName,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                    onClick = {
                        menuOpen = false
                        onChoose(candidate)
                    },
                    modifier = Modifier.testTag("usage-option-compare-choice"),
                )
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    dimension: UsageOptionComparisonDimension,
    items: List<UsageOptionComparisonItem>,
) {
    val markers = comparisonPriceMarkers(dimension, items.mapTo(mutableSetOf()) { it.usageOptionId })
    val markerText = stringResource(R.string.usage_option_compare_lowest)
    val emphasized = dimension.dimension == "PRICE_WON"
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { item ->
                val entry = dimension.values.firstOrNull { it.usageOptionId == item.usageOptionId }
                val presentation = comparisonValuePresentation(dimension.dimension, entry)
                val translated = mutableListOf<String>()
                for (resourceId in presentation.resourceIds) translated.add(stringResource(resourceId))
                val value = presentation.text ?: translated.joinToString(", ")
                val marked = item.usageOptionId in markers && presentation.known
                val description = listOfNotNull(label, item.option.programName, value, markerText.takeIf { marked })
                    .joinToString(", ")
                Column(
                    modifier = Modifier.weight(1f)
                        .clearAndSetSemantics { contentDescription = description },
                ) {
                    Text(
                        text = value,
                        color = if (presentation.known) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = if (emphasized) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        fontWeight = if (emphasized && presentation.known) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (marked) {
                        Text(
                            text = markerText,
                            modifier = Modifier.padding(top = 2.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}
