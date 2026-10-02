package io.github.chamsser.gymvi.ui

import android.os.Handler
import android.os.Looper
import io.github.chamsser.gymvi.data.RouteApiClient
import io.github.chamsser.gymvi.data.RouteFetchResult
import io.github.chamsser.gymvi.data.RoutePointDto
import io.github.chamsser.gymvi.data.RoutePreviewData
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

sealed interface RoutePreviewState {
    data object Idle : RoutePreviewState
    data object Loading : RoutePreviewState
    data class Ready(val route: RoutePreviewData) : RoutePreviewState
    data class Error(val code: String, val retryable: Boolean) : RoutePreviewState
}

class RoutePreviewController internal constructor(
    private val fetchRoute: (RouteCoordinate, RouteCoordinate) -> RouteFetchResult,
    private val executor: ExecutorService,
    private val dispatch: (() -> Unit) -> Unit,
) : Closeable {
    constructor(apiBaseUrl: String) : this(
        fetchRoute = RouteApiClient(apiBaseUrl).let { client ->
            { origin, destination ->
                client.preview(
                    RoutePointDto(origin.latitude, origin.longitude),
                    RoutePointDto(destination.latitude, destination.longitude),
                )
            }
        },
        executor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "gymvi-route-api").apply { isDaemon = true }
        },
        dispatch = Handler(Looper.getMainLooper()).let { handler ->
            { action -> handler.post { action() }; Unit }
        },
    )

    private data class Request(val origin: RouteCoordinate, val destination: RouteCoordinate)
    private val lock = Any()
    private var state: RoutePreviewState = RoutePreviewState.Idle
    private var request: Request? = null
    private var requestState: RoutePreviewState = RoutePreviewState.Idle
    private var observer: ((RoutePreviewState) -> Unit)? = null
    private var generation = 0
    private var closed = false

    val currentState: RoutePreviewState
        get() = synchronized(lock) { state }

    fun observe(observer: ((RoutePreviewState) -> Unit)?) {
        val snapshot = synchronized(lock) {
            if (closed) return
            this.observer = observer
            state
        }
        observer?.let { callback -> dispatch { callback(snapshot) } }
    }

    fun preview(origin: RouteCoordinate, destination: RouteCoordinate) {
        val requestGeneration = synchronized(lock) {
            if (closed) return
            val nextRequest = Request(origin, destination)
            if (request == nextRequest) {
                // Returning from map picking or reopening the preview must not retry a route.
                if (state != requestState) {
                    state = requestState
                    notifyObserverLocked()
                }
                return
            }
            request = nextRequest
            generation += 1
            state = RoutePreviewState.Loading
            requestState = state
            notifyObserverLocked()
            generation
        }
        executor.execute {
            val result = fetchRoute(origin, destination)
            synchronized(lock) {
                if (closed || requestGeneration != generation) return@execute
                requestState = result.toState()
                if (state != RoutePreviewState.Idle) {
                    state = requestState
                    notifyObserverLocked()
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            if (closed) return
            state = RoutePreviewState.Idle
            notifyObserverLocked()
        }
    }

    private fun notifyObserverLocked() {
        val snapshot = state
        dispatch {
            val callback = synchronized(lock) { if (closed) null else observer }
            callback?.invoke(snapshot)
        }
    }

    private fun RouteFetchResult.toState(): RoutePreviewState = when (this) {
        is RouteFetchResult.Success -> RoutePreviewState.Ready(route)
        is RouteFetchResult.NetworkFailure -> RoutePreviewState.Error("NETWORK_UNAVAILABLE", true)
        is RouteFetchResult.ApiFailure -> RoutePreviewState.Error(code, retryable)
        is RouteFetchResult.InvalidResponse -> RoutePreviewState.Error("INVALID_ROUTE_RESPONSE", false)
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
