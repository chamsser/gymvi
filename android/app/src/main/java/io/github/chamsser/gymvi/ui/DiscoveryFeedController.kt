package io.github.chamsser.gymvi.ui

import android.os.Handler
import android.os.Looper
import io.github.chamsser.gymvi.data.DiscoveryFeedApiClient
import io.github.chamsser.gymvi.data.DiscoveryFeedFetchResult
import io.github.chamsser.gymvi.data.DiscoveryFeedPage
import io.github.chamsser.gymvi.data.FacilityBounds
import java.io.Closeable
import java.util.concurrent.Executors

sealed interface DiscoveryFeedState {
    data object Idle : DiscoveryFeedState
    data class Loading(val previous: DiscoveryFeedPage? = null) : DiscoveryFeedState
    data class Ready(val page: DiscoveryFeedPage) : DiscoveryFeedState
    data class Error(val code: String, val previous: DiscoveryFeedPage? = null) : DiscoveryFeedState
}

internal val DiscoveryFeedState.pageOrNull: DiscoveryFeedPage?
    get() = when (this) {
        DiscoveryFeedState.Idle -> null
        is DiscoveryFeedState.Loading -> previous
        is DiscoveryFeedState.Ready -> page
        is DiscoveryFeedState.Error -> previous
    }

class DiscoveryFeedController(apiBaseUrl: String) : Closeable {
    private val client = DiscoveryFeedApiClient(apiBaseUrl)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gymvi-discovery-api").apply { isDaemon = true }
    }
    private val lock = Any()
    private var state: DiscoveryFeedState = DiscoveryFeedState.Idle
    private var observer: ((DiscoveryFeedState) -> Unit)? = null
    private var generation = 0
    private var closed = false

    val currentState: DiscoveryFeedState
        get() = synchronized(lock) { state }

    fun observe(observer: ((DiscoveryFeedState) -> Unit)?) {
        val snapshot = synchronized(lock) {
            if (closed) return
            this.observer = observer
            state
        }
        observer?.let { callback -> mainHandler.post { callback(snapshot) } }
    }

    fun load(
        bounds: FacilityBounds,
        localHour: Int,
        favoriteFacilityIds: Set<String>,
        recentFacilityIds: List<String>,
        preferredCategories: Set<String> = emptySet(),
    ) {
        val requestGeneration = synchronized(lock) {
            if (closed) return
            generation += 1
            state = DiscoveryFeedState.Loading(state.pageOrNull)
            notifyObserverLocked()
            generation
        }
        executor.execute {
            val result = client.fetch(bounds, localHour, favoriteFacilityIds, recentFacilityIds, preferredCategories)
            synchronized(lock) {
                if (closed || requestGeneration != generation) return@execute
                val previous = state.pageOrNull
                state = when (result) {
                    is DiscoveryFeedFetchResult.Success -> DiscoveryFeedState.Ready(result.page)
                    is DiscoveryFeedFetchResult.NetworkFailure ->
                        DiscoveryFeedState.Error("NETWORK_UNAVAILABLE", previous)
                    is DiscoveryFeedFetchResult.ApiFailure ->
                        DiscoveryFeedState.Error(result.code, previous)
                    is DiscoveryFeedFetchResult.InvalidResponse ->
                        DiscoveryFeedState.Error("INVALID_DISCOVERY_RESPONSE", previous)
                }
                notifyObserverLocked()
            }
        }
    }

    private fun notifyObserverLocked() {
        val snapshot = state
        mainHandler.post {
            val callback = synchronized(lock) { if (closed) null else observer }
            callback?.invoke(snapshot)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            observer = null
        }
        executor.shutdownNow()
    }
}
