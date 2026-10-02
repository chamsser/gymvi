package io.github.chamsser.gymvi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The sheet owns this state so collapsing it does not discard the reading position. */
@Composable
internal fun FacilitySearchList(
    uiState: MapUiState,
    state: LazyListState,
    favoriteFacilityIds: Set<String>,
    currentLocation: RouteCoordinate?,
    resultSort: FacilityResultSort,
    onResultSortChanged: (FacilityResultSort) -> Unit,
    ownershipFilter: FacilityOwnershipFilter,
    onOwnershipFilterChanged: (FacilityOwnershipFilter) -> Unit,
    onFacilitySelected: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().testTag("facility-search-list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
    ) {
        item(key = "controls", contentType = "controls") {
            Column {
                FacilitySearchControls(uiState.totalFacilityCount, uiState.facilities.size,
                    resultSort, onResultSortChanged, ownershipFilter, onOwnershipFilterChanged)
                Spacer(Modifier.height(8.dp))
            }
        }
        itemsIndexed(uiState.facilities, key = { _, facility -> "facility:${facility.facilityId}" },
            contentType = { _, _ -> "facility" }) { index, facility ->
            Column {
                FacilitySearchResultRow(
                    facility = facility,
                    isFavorite = facility.facilityId in favoriteFacilityIds,
                    showRemoveAction = false,
                    distanceMeters = currentLocation?.let { facilityDistanceMeters(it, facility) },
                    compact = true,
                    testTag = "facility-sheet-search-result",
                    onClick = { onFacilitySelected(facility.facilityId) },
                    onRemove = {},
                )
                if (index != uiState.facilities.lastIndex) HorizontalDivider(color = gymviSubtleBorderColor())
            }
        }
        if (uiState.nextFacilityCursor != null) item(key = "more", contentType = "more") {
            Column { FacilitySearchLoadMore(uiState.isLoadingMoreFacilities, onLoadMore) }
        }
    }
}
