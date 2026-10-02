package io.github.chamsser.gymvi.ui

import android.os.Handler
import android.os.Looper
import io.github.chamsser.gymvi.data.FacilityMediaApiClient
import io.github.chamsser.gymvi.data.FacilityMediaFetchResult
import io.github.chamsser.gymvi.data.FacilityMediaPage
import java.io.Closeable
import java.util.LinkedHashMap
import java.util.concurrent.Executors

sealed interface FacilityMediaState {
    data object Idle : FacilityMediaState
    data class Loading(val facilityId: String) : FacilityMediaState
    data class Ready(val page: FacilityMediaPage) : FacilityMediaState
    data class Error(
        val facilityId: String,
        val code: String,
        val retryable: Boolean,
    ) : FacilityMediaState
}

class FacilityMediaController(apiBaseUrl: String) : Closeable {
    private val client = FacilityMediaApiClient(apiBaseUrl)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gymvi-facility-media-api").apply { isDaemon = true }
    }
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, FacilityMediaPage>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, FacilityMediaPage>?,
        ): Boolean = size > MAX_CACHED_FACILITIES
    }
    private var state: FacilityMediaState = FacilityMediaState.Idle
    private var observer: ((FacilityMediaState) -> Unit)? = null
    private var generation = 0
    private var closed = false

    val currentState: FacilityMediaState
        get() = synchronized(lock) { state }

    fun observe(observer: ((FacilityMediaState) -> Unit)?) {
        val snapshot = synchronized(lock) {
            if (closed) return
            this.observer = observer
            state
        }
        observer?.let { callback -> mainHandler.post { callback(snapshot) } }
    }

    fun load(facilityId: String) {
        require(facilityId.isNotBlank())
        val requestGeneration = synchronized(lock) {
            if (closed) return
            cache[facilityId]?.let { cached ->
                state = FacilityMediaState.Ready(cached)
                notifyObserverLocked()
                return
            }
            if ((state as? FacilityMediaState.Loading)?.facilityId == facilityId) return
            generation += 1
            state = FacilityMediaState.Loading(facilityId)
            notifyObserverLocked()
            generation
        }
        executor.execute {
            val result = client.fetch(facilityId)
            synchronized(lock) {
                if (closed || requestGeneration != generation) return@execute
                state = result.toState(facilityId)
                (state as? FacilityMediaState.Ready)?.page?.let { page ->
                    cache[page.facilityId] = page
                }
                notifyObserverLocked()
            }
        }
    }

    private fun FacilityMediaFetchResult.toState(facilityId: String): FacilityMediaState = when (this) {
        is FacilityMediaFetchResult.Success -> FacilityMediaState.Ready(page)
        is FacilityMediaFetchResult.NetworkFailure -> FacilityMediaState.Error(
            facilityId,
            "NETWORK_UNAVAILABLE",
            true,
        )
        is FacilityMediaFetchResult.ApiFailure -> FacilityMediaState.Error(
            facilityId,
            code,
            retryable,
        )
        is FacilityMediaFetchResult.InvalidResponse -> FacilityMediaState.Error(
            facilityId,
            "INVALID_MEDIA_RESPONSE",
            false,
        )
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
            cache.clear()
        }
        executor.shutdownNow()
    }

    private companion object {
        const val MAX_CACHED_FACILITIES = 16
    }
}

internal fun FacilityMediaState.forFacility(facilityId: String): FacilityMediaState = when (this) {
    FacilityMediaState.Idle -> this
    is FacilityMediaState.Loading -> takeIf { it.facilityId == facilityId } ?: FacilityMediaState.Idle
    is FacilityMediaState.Ready -> takeIf { it.page.facilityId == facilityId } ?: FacilityMediaState.Idle
    is FacilityMediaState.Error -> takeIf { it.facilityId == facilityId } ?: FacilityMediaState.Idle
}
