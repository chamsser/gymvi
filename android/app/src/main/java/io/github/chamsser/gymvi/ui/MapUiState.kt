package io.github.chamsser.gymvi.ui

import io.github.chamsser.gymvi.data.FacilityBounds
import io.github.chamsser.gymvi.data.FacilityMapItem
import io.github.chamsser.gymvi.data.FacilityPage

enum class FacilityLoadState {
    IDLE,
    LOADING,
    READY,
    EMPTY,
    NETWORK_ERROR,
    API_ERROR,
    INVALID_RESPONSE,
}

enum class MapAvailability {
    MISSING_CREDENTIAL,
    LOADING,
    READY,
    AUTH_ERROR,
}

enum class FacilityRequestOrigin {
    INITIAL_LOCATION,
    SEARCH_BAR,
    CATEGORY_SHORTCUT,
    VISIBLE_AREA,
    AI_RECOMMENDATION,
}

data class MapUiState(
    val facilityLoadState: FacilityLoadState = FacilityLoadState.IDLE,
    val mapAvailability: MapAvailability = MapAvailability.MISSING_CREDENTIAL,
    val facilities: List<FacilityMapItem> = emptyList(),
    val totalFacilityCount: Int = facilities.size,
    val nextFacilityCursor: String? = null,
    val isLoadingMoreFacilities: Boolean = false,
    val selectedFacilityId: String? = null,
    val datasetVersion: String? = null,
    val asOf: String? = null,
    val errorCode: String? = null,
    val mapAuthErrorCode: String? = null,
    val completedFacilityRequestId: Int = 0,
    val completedFacilityRequestOrigin: FacilityRequestOrigin? = null,
    val completedFacilityRequestArea: FacilityBounds? = null,
) {
    val selectedFacility: FacilityMapItem?
        get() = facilities.firstOrNull { it.facilityId == selectedFacilityId }

    init {
        require(facilities.map(FacilityMapItem::facilityId).distinct().size == facilities.size) {
            "Facility IDs must be unique."
        }
        require(totalFacilityCount >= facilities.size)
        require(selectedFacilityId == null || facilities.any { it.facilityId == selectedFacilityId }) {
            "The selected facility must exist in the visible facility set."
        }
    }
}

sealed interface MapUiEvent {
    data object FacilityLoadStarted : MapUiEvent

    data class FacilityLoadSucceeded(
        val page: FacilityPage,
        val requestId: Int = 0,
        val requestOrigin: FacilityRequestOrigin? = null,
        val requestArea: FacilityBounds? = null,
    ) : MapUiEvent

    data object FacilityLoadMoreStarted : MapUiEvent

    data class FacilityPageAppended(val page: FacilityPage) : MapUiEvent

    data object FacilityLoadMoreFinished : MapUiEvent

    data object FacilityNetworkFailed : MapUiEvent

    data class FacilityApiFailed(val code: String) : MapUiEvent

    data object FacilityResponseInvalid : MapUiEvent

    data object MapLoaded : MapUiEvent

    data class MapAuthFailed(val code: String) : MapUiEvent

    data class FacilitySelected(val facilityId: String) : MapUiEvent

    data object FacilitySelectionCleared : MapUiEvent
}

object MapUiReducer {
    fun reduce(state: MapUiState, event: MapUiEvent): MapUiState = when (event) {
        MapUiEvent.FacilityLoadStarted -> state.copy(
            facilityLoadState = FacilityLoadState.LOADING,
            isLoadingMoreFacilities = false,
            errorCode = null,
        )

        is MapUiEvent.FacilityLoadSucceeded -> {
            val facilities = event.page.facilities.distinctBy(FacilityMapItem::facilityId)
            val selectedId = state.selectedFacilityId
                ?.takeIf { current -> facilities.any { it.facilityId == current } }
                ?: facilities.firstOrNull()
                    ?.takeIf { state.mapAvailability == MapAvailability.MISSING_CREDENTIAL }
                    ?.facilityId
            state.copy(
                facilityLoadState = if (facilities.isEmpty()) {
                    FacilityLoadState.EMPTY
                } else {
                    FacilityLoadState.READY
                },
                facilities = facilities,
                totalFacilityCount = event.page.totalCount.coerceAtLeast(facilities.size),
                nextFacilityCursor = event.page.nextCursor,
                isLoadingMoreFacilities = false,
                selectedFacilityId = selectedId,
                datasetVersion = event.page.datasetVersion,
                asOf = event.page.asOf,
                errorCode = null,
                completedFacilityRequestId = event.requestId,
                completedFacilityRequestOrigin = event.requestOrigin,
                completedFacilityRequestArea = event.requestArea,
            )
        }

        MapUiEvent.FacilityLoadMoreStarted -> state.copy(
            isLoadingMoreFacilities = true,
        )

        is MapUiEvent.FacilityPageAppended -> {
            val merged = (state.facilities + event.page.facilities)
                .distinctBy(FacilityMapItem::facilityId)
            state.copy(
                facilityLoadState = if (merged.isEmpty()) FacilityLoadState.EMPTY else FacilityLoadState.READY,
                facilities = merged,
                totalFacilityCount = event.page.totalCount.coerceAtLeast(merged.size),
                nextFacilityCursor = event.page.nextCursor,
                isLoadingMoreFacilities = false,
                datasetVersion = event.page.datasetVersion,
                asOf = event.page.asOf,
            )
        }

        MapUiEvent.FacilityLoadMoreFinished -> state.copy(
            isLoadingMoreFacilities = false,
        )

        MapUiEvent.FacilityNetworkFailed -> state.copy(
            facilityLoadState = FacilityLoadState.NETWORK_ERROR,
            errorCode = "NETWORK_UNAVAILABLE",
        )

        is MapUiEvent.FacilityApiFailed -> state.copy(
            facilityLoadState = FacilityLoadState.API_ERROR,
            errorCode = event.code,
        )

        MapUiEvent.FacilityResponseInvalid -> state.copy(
            facilityLoadState = FacilityLoadState.INVALID_RESPONSE,
            errorCode = "INVALID_RESPONSE",
        )

        MapUiEvent.MapLoaded -> state.copy(
            mapAvailability = MapAvailability.READY,
            mapAuthErrorCode = null,
        )

        is MapUiEvent.MapAuthFailed -> state.copy(
            mapAvailability = MapAvailability.AUTH_ERROR,
            mapAuthErrorCode = event.code,
        )

        is MapUiEvent.FacilitySelected -> {
            if (state.facilities.any { it.facilityId == event.facilityId }) {
                state.copy(selectedFacilityId = event.facilityId)
            } else {
                state
            }
        }

        MapUiEvent.FacilitySelectionCleared -> state.copy(selectedFacilityId = null)
    }
}
