package io.github.chamsser.gymvi.ui

import android.os.Handler
import android.os.Looper
import io.github.chamsser.gymvi.data.FacilityApiClient
import io.github.chamsser.gymvi.data.FacilityBounds
import io.github.chamsser.gymvi.data.FacilityFetchResult
import io.github.chamsser.gymvi.data.FacilityQuerySort
import java.io.Closeable
import java.util.concurrent.Executors

class MapScreenController(
    apiBaseUrl: String,
    hasMapCredential: Boolean,
) : Closeable {
    private val apiClient = FacilityApiClient(apiBaseUrl)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gymvi-facility-api").apply { isDaemon = true }
    }
    private val lock = Any()

    private var state = MapUiState(
        mapAvailability = if (hasMapCredential) {
            MapAvailability.LOADING
        } else {
            MapAvailability.MISSING_CREDENTIAL
        },
    )
    private var observer: ((MapUiState) -> Unit)? = null
    private var pendingLoad: Runnable? = null
    private var lastBounds = FacilityBounds.GEOYEO_INITIAL
    private var hasRequestedFacilities = false
    private var lastLimit = DEFAULT_FACILITY_LIMIT
    private var lastQuery: String? = null
    private var lastSort = FacilityQuerySort.DISTANCE
    private var lastRequestOrigin = FacilityRequestOrigin.INITIAL_LOCATION
    private var lastSelectFacilityIdAfterLoad: String? = null
    private var requestGeneration = 0
    private var closed = false

    val currentState: MapUiState
        get() = synchronized(lock) { state }

    val currentBounds: FacilityBounds
        get() = synchronized(lock) { lastBounds }

    fun observe(observer: ((MapUiState) -> Unit)?) {
        val snapshot = synchronized(lock) {
            if (closed) return
            this.observer = observer
            state
        }
        observer?.let { callback -> mainHandler.post { callback(snapshot) } }
    }

    fun loadInitialFacilities(latitude: Double, longitude: Double) {
        requestFacilities(
            bounds = FacilityBounds.around(
                latitude = latitude,
                longitude = longitude,
                radiusKilometers = NEARBY_SEARCH_RADIUS_KILOMETERS,
            ),
            debounceMillis = 0,
            limit = SEARCH_FACILITY_LIMIT,
            sort = FacilityQuerySort.DISTANCE,
            requestOrigin = FacilityRequestOrigin.INITIAL_LOCATION,
        )
    }

    fun searchVisibleBounds(
        bounds: FacilityBounds,
        query: String? = null,
        sort: FacilityResultSort = FacilityResultSort.RELEVANCE,
        requestOrigin: FacilityRequestOrigin = FacilityRequestOrigin.VISIBLE_AREA,
    ) {
        requestFacilities(
            bounds = bounds,
            debounceMillis = 0,
            limit = SEARCH_FACILITY_LIMIT,
            query = query,
            sort = sort.toQuerySort(),
            requestOrigin = requestOrigin,
        )
    }

    fun searchNearby(
        latitude: Double?,
        longitude: Double?,
        query: String,
        sort: FacilityResultSort = FacilityResultSort.RELEVANCE,
        requestOrigin: FacilityRequestOrigin = FacilityRequestOrigin.SEARCH_BAR,
        movedArea: FacilityBounds? = null,
    ) {
        val bounds = synchronized(lock) {
            nearbySearchBounds(latitude, longitude, fallbackArea = movedArea ?: searchedAreaLocked())
        }
        requestFacilities(
            bounds = bounds,
            debounceMillis = 0,
            limit = SEARCH_FACILITY_LIMIT,
            query = query,
            sort = sort.toQuerySort(),
            requestOrigin = requestOrigin,
        )
    }

    fun searchCategory(
        query: String,
        sort: FacilityResultSort,
        movedArea: FacilityBounds?,
        latitude: Double?,
        longitude: Double?,
    ) {
        val bounds = synchronized(lock) {
            categorySearchBounds(movedArea, searchedAreaLocked(), latitude, longitude)
        }
        requestFacilities(
            bounds = bounds,
            debounceMillis = 0,
            limit = SEARCH_FACILITY_LIMIT,
            query = query,
            sort = sort.toQuerySort(),
            requestOrigin = FacilityRequestOrigin.CATEGORY_SHORTCUT,
        )
    }

    private fun searchedAreaLocked(): FacilityBounds? = lastBounds.takeIf { hasRequestedFacilities }

    fun focusFacility(
        facilityId: String,
        latitude: Double,
        longitude: Double,
    ) {
        require(facilityId.isNotBlank())
        requestFacilities(
            bounds = FacilityBounds.around(latitude, longitude, AI_FOCUS_RADIUS_KILOMETERS),
            debounceMillis = 0,
            limit = SEARCH_FACILITY_LIMIT,
            sort = FacilityQuerySort.DISTANCE,
            requestOrigin = FacilityRequestOrigin.AI_RECOMMENDATION,
            selectFacilityIdAfterLoad = facilityId,
        )
    }

    fun retry() {
        val request = synchronized(lock) {
            RetryRequest(
                lastBounds,
                lastLimit,
                lastQuery,
                lastSort,
                lastRequestOrigin,
                lastSelectFacilityIdAfterLoad,
            )
        }
        requestFacilities(
            bounds = request.bounds,
            debounceMillis = 0,
            limit = request.limit,
            query = request.query,
            sort = request.sort,
            requestOrigin = request.origin,
            selectFacilityIdAfterLoad = request.selectFacilityIdAfterLoad,
        )
    }

    fun loadMoreFacilities() {
        val request: PageRequest
        val generation: Int
        synchronized(lock) {
            if (closed || state.isLoadingMoreFacilities) return
            val cursor = state.nextFacilityCursor ?: return
            request = PageRequest(lastBounds, lastLimit, lastQuery, lastSort, cursor)
            generation = requestGeneration
            reduceLocked(MapUiEvent.FacilityLoadMoreStarted)
        }
        executor.execute {
            val result = apiClient.fetch(
                bounds = request.bounds,
                limit = request.limit,
                query = request.query,
                sort = request.sort,
                cursor = request.cursor,
            )
            synchronized(lock) {
                if (closed || generation != requestGeneration) return@execute
                reduceLocked(
                    when (result) {
                        is FacilityFetchResult.Success -> MapUiEvent.FacilityPageAppended(result.page)
                        else -> MapUiEvent.FacilityLoadMoreFinished
                    },
                )
            }
        }
    }

    fun onMapLoaded() {
        dispatch(MapUiEvent.MapLoaded)
    }

    fun onMapAuthFailed(code: String) {
        dispatch(MapUiEvent.MapAuthFailed(code.ifBlank { "UNKNOWN" }))
    }

    fun selectFacility(facilityId: String) {
        dispatch(MapUiEvent.FacilitySelected(facilityId))
    }

    fun clearFacilitySelection() {
        dispatch(MapUiEvent.FacilitySelectionCleared)
    }

    private fun requestFacilities(
        bounds: FacilityBounds,
        debounceMillis: Long,
        limit: Int = DEFAULT_FACILITY_LIMIT,
        query: String? = null,
        sort: FacilityQuerySort = FacilityQuerySort.RELEVANCE,
        requestOrigin: FacilityRequestOrigin,
        selectFacilityIdAfterLoad: String? = null,
    ) {
        val generation: Int
        val runnable: Runnable
        val normalizedQuery = query?.trim()?.takeIf(String::isNotEmpty)
        synchronized(lock) {
            if (closed) return
            lastBounds = bounds
            hasRequestedFacilities = true
            lastLimit = limit
            lastQuery = normalizedQuery
            lastSort = sort
            lastRequestOrigin = requestOrigin
            lastSelectFacilityIdAfterLoad = selectFacilityIdAfterLoad
            requestGeneration += 1
            generation = requestGeneration
            pendingLoad?.let(mainHandler::removeCallbacks)
            reduceLocked(MapUiEvent.FacilityLoadStarted)
            runnable = Runnable {
                synchronized(lock) {
                    if (closed || generation != requestGeneration) return@Runnable
                    pendingLoad = null
                }
                executor.execute {
                    val result = apiClient.fetch(bounds, limit, normalizedQuery, sort)
                    synchronized(lock) {
                        if (closed || generation != requestGeneration) return@execute
                        reduceLocked(result.toEvent(generation, requestOrigin, bounds))
                        if (
                            selectFacilityIdAfterLoad != null &&
                            state.facilities.any { it.facilityId == selectFacilityIdAfterLoad }
                        ) {
                            reduceLocked(MapUiEvent.FacilitySelected(selectFacilityIdAfterLoad))
                        }
                    }
                }
            }
            pendingLoad = runnable
        }

        if (debounceMillis == 0L) {
            mainHandler.post(runnable)
        } else {
            mainHandler.postDelayed(runnable, debounceMillis)
        }
    }

    private fun FacilityFetchResult.toEvent(
        requestId: Int,
        requestOrigin: FacilityRequestOrigin,
        requestArea: FacilityBounds,
    ): MapUiEvent = when (this) {
        is FacilityFetchResult.Success -> MapUiEvent.FacilityLoadSucceeded(
            page = page,
            requestId = requestId,
            requestOrigin = requestOrigin,
            requestArea = requestArea,
        )
        is FacilityFetchResult.NetworkFailure -> MapUiEvent.FacilityNetworkFailed
        is FacilityFetchResult.ApiFailure -> MapUiEvent.FacilityApiFailed(code)
        is FacilityFetchResult.InvalidResponse -> MapUiEvent.FacilityResponseInvalid
    }

    private fun dispatch(event: MapUiEvent) {
        synchronized(lock) {
            if (closed) return
            reduceLocked(event)
        }
    }

    private fun reduceLocked(event: MapUiEvent) {
        state = MapUiReducer.reduce(state, event)
        val snapshot = state
        mainHandler.post {
            val callback = synchronized(lock) {
                if (closed) null else observer
            }
            callback?.invoke(snapshot)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            requestGeneration += 1
            pendingLoad?.let(mainHandler::removeCallbacks)
            pendingLoad = null
            observer = null
        }
        executor.shutdownNow()
    }

    companion object {
        internal const val NEARBY_SEARCH_RADIUS_KILOMETERS = 8.0
        internal const val AI_FOCUS_RADIUS_KILOMETERS = 1.0
        private const val DEFAULT_FACILITY_LIMIT = 100
        private const val SEARCH_FACILITY_LIMIT = FacilityApiClient.MAX_PAGE_SIZE
    }

    private data class RetryRequest(
        val bounds: FacilityBounds,
        val limit: Int,
        val query: String?,
        val sort: FacilityQuerySort,
        val origin: FacilityRequestOrigin,
        val selectFacilityIdAfterLoad: String?,
    )

    private data class PageRequest(
        val bounds: FacilityBounds,
        val limit: Int,
        val query: String?,
        val sort: FacilityQuerySort,
        val cursor: String,
    )
}

private fun FacilityResultSort.toQuerySort(): FacilityQuerySort = when (this) {
    FacilityResultSort.RELEVANCE -> FacilityQuerySort.RELEVANCE
    FacilityResultSort.DISTANCE -> FacilityQuerySort.DISTANCE
}

/**
 * Centers on the device location. Without one the user's area stands in for it, and the
 * initial map center is used only before any area exists, never after the user chose one.
 */
internal fun nearbySearchBounds(
    latitude: Double?,
    longitude: Double?,
    fallbackArea: FacilityBounds?,
): FacilityBounds {
    val center = when {
        latitude != null && longitude != null -> RouteCoordinate(latitude, longitude)
        fallbackArea != null -> fallbackArea.center()
        else -> RouteCoordinate(
            latitude = FacilityBounds.GEOYEO_CENTER_LATITUDE,
            longitude = FacilityBounds.GEOYEO_CENTER_LONGITUDE,
        )
    }
    return FacilityBounds.around(
        latitude = center.latitude,
        longitude = center.longitude,
        radiusKilometers = MapScreenController.NEARBY_SEARCH_RADIUS_KILOMETERS,
    )
}

/** A category stays in the area the user moved to or last searched, with or without a location. */
internal fun categorySearchBounds(
    movedArea: FacilityBounds?,
    searchedArea: FacilityBounds?,
    latitude: Double?,
    longitude: Double?,
): FacilityBounds = movedArea ?: searchedArea ?: nearbySearchBounds(latitude, longitude, fallbackArea = null)

internal fun FacilityBounds.center(): RouteCoordinate = RouteCoordinate(
    latitude = (minLatitude + maxLatitude) / 2.0,
    longitude = (minLongitude + maxLongitude) / 2.0,
)
