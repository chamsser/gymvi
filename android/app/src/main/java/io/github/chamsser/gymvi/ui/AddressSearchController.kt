package io.github.chamsser.gymvi.ui

import android.os.Handler
import android.os.Looper
import io.github.chamsser.gymvi.data.AddressApiClient
import io.github.chamsser.gymvi.data.AddressSearchFetchResult
import io.github.chamsser.gymvi.data.AddressSearchItem
import io.github.chamsser.gymvi.data.looksLikeAddressQuery
import io.github.chamsser.gymvi.data.normalizeAddressSearchQuery
import java.io.Closeable
import java.util.LinkedHashMap
import java.util.concurrent.Executors

sealed interface AddressSearchState {
    data object Idle : AddressSearchState
    data class Loading(val query: String) : AddressSearchState
    data class Ready(val query: String, val addresses: List<AddressSearchItem>) : AddressSearchState
    data class Error(val query: String, val code: String, val retryable: Boolean) : AddressSearchState
}

class AddressSearchController(apiBaseUrl: String) : Closeable {
    private val client = AddressApiClient(apiBaseUrl)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gymvi-address-api").apply { isDaemon = true }
    }
    private val lock = Any()
    private var state: AddressSearchState = AddressSearchState.Idle
    private var observer: ((AddressSearchState) -> Unit)? = null
    private var pendingSearch: Runnable? = null
    private var generation = 0
    private var closed = false
    private val cachedAddresses = object : LinkedHashMap<String, List<AddressSearchItem>>(
        MAX_CACHED_QUERIES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<AddressSearchItem>>?,
        ): Boolean = size > MAX_CACHED_QUERIES
    }

    val currentState: AddressSearchState
        get() = synchronized(lock) { state }

    fun observe(observer: ((AddressSearchState) -> Unit)?) {
        val snapshot = synchronized(lock) {
            if (closed) return
            this.observer = observer
            state
        }
        observer?.let { callback -> mainHandler.post { callback(snapshot) } }
    }

    fun search(rawQuery: String) {
        val query = normalizeAddressSearchQuery(rawQuery)
        if (!looksLikeAddressQuery(query)) {
            clear()
            return
        }
        val requestGeneration: Int
        val runnable: Runnable
        synchronized(lock) {
            if (closed) return
            if ((state as? AddressSearchState.Ready)?.query == query) return
            if ((state as? AddressSearchState.Loading)?.query == query) return
            generation += 1
            requestGeneration = generation
            pendingSearch?.let(mainHandler::removeCallbacks)
            cachedAddresses[query]?.let { addresses ->
                pendingSearch = null
                state = AddressSearchState.Ready(query, addresses)
                notifyObserverLocked()
                return
            }
            state = AddressSearchState.Loading(query)
            notifyObserverLocked()
            runnable = Runnable {
                synchronized(lock) {
                    if (closed || requestGeneration != generation) return@Runnable
                    pendingSearch = null
                }
                executor.execute {
                    val result = client.search(query)
                    synchronized(lock) {
                        if (closed || requestGeneration != generation) return@execute
                        if (result is AddressSearchFetchResult.Success) {
                            cachedAddresses[query] = result.page.addresses
                        }
                        state = result.toState(query)
                        notifyObserverLocked()
                    }
                }
            }
            pendingSearch = runnable
        }
        mainHandler.postDelayed(runnable, SEARCH_DEBOUNCE_MILLIS)
    }

    fun clear() {
        synchronized(lock) {
            if (closed) return
            if (state == AddressSearchState.Idle && pendingSearch == null) return
            generation += 1
            pendingSearch?.let(mainHandler::removeCallbacks)
            pendingSearch = null
            state = AddressSearchState.Idle
            notifyObserverLocked()
        }
    }

    private fun AddressSearchFetchResult.toState(query: String): AddressSearchState = when (this) {
        is AddressSearchFetchResult.Success -> AddressSearchState.Ready(query, page.addresses)
        is AddressSearchFetchResult.NetworkFailure -> AddressSearchState.Error(query, "NETWORK_UNAVAILABLE", true)
        is AddressSearchFetchResult.ApiFailure -> AddressSearchState.Error(query, code, retryable)
        is AddressSearchFetchResult.InvalidResponse -> AddressSearchState.Error(query, "INVALID_ADDRESS_RESPONSE", false)
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
            pendingSearch?.let(mainHandler::removeCallbacks)
            pendingSearch = null
            observer = null
        }
        executor.shutdownNow()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 800L
        const val MAX_CACHED_QUERIES = 32
    }
}

internal fun AddressSearchState.resultsFor(query: String): List<AddressSearchItem> {
    val normalizedQuery = normalizeAddressSearchQuery(query)
    return (this as? AddressSearchState.Ready)
        ?.takeIf { it.query == normalizedQuery }
        ?.addresses
        .orEmpty()
}

internal fun AddressSearchState.isLoadingFor(query: String): Boolean {
    val normalizedQuery = normalizeAddressSearchQuery(query)
    return this is AddressSearchState.Loading && this.query == normalizedQuery
}
