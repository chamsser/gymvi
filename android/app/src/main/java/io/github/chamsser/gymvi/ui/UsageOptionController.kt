package io.github.chamsser.gymvi.ui

import android.os.Handler
import android.os.Looper
import io.github.chamsser.gymvi.data.ProgramEvidence
import io.github.chamsser.gymvi.data.UsageOptionApiClient
import io.github.chamsser.gymvi.data.UsageOptionFetchResult
import io.github.chamsser.gymvi.data.UsageOptionItem
import io.github.chamsser.gymvi.data.UsageOptionPage
import io.github.chamsser.gymvi.data.UsageOptionComparisonData
import java.io.Closeable
import java.util.LinkedHashMap
import java.util.concurrent.Executors

sealed interface UsageOptionListState {
    data object Idle : UsageOptionListState
    data class Loading(val facilityId: String) : UsageOptionListState
    data class Ready(val page: UsageOptionPage) : UsageOptionListState
    data class Error(val facilityId: String, val code: String, val retryable: Boolean) : UsageOptionListState
}

sealed interface UsageOptionEvidenceState {
    data object Idle : UsageOptionEvidenceState
    data class Loading(val optionId: String) : UsageOptionEvidenceState
    data class Ready(val optionId: String, val evidence: ProgramEvidence) : UsageOptionEvidenceState
    data class Error(val optionId: String, val code: String, val retryable: Boolean) : UsageOptionEvidenceState
}

sealed interface UsageOptionCompareState {
    data object Idle : UsageOptionCompareState
    data class Loading(val ids: List<String>) : UsageOptionCompareState
    data class Ready(val ids: List<String>, val data: UsageOptionComparisonData) : UsageOptionCompareState
    data class Error(val ids: List<String>, val code: String, val retryable: Boolean) : UsageOptionCompareState
}

class UsageOptionController(apiBaseUrl: String) : Closeable {
    private val client = UsageOptionApiClient(apiBaseUrl)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gymvi-usage-option-api").apply { isDaemon = true }
    }
    private val lock = Any()
    private val listCache = object : LinkedHashMap<String, UsageOptionPage>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, UsageOptionPage>?): Boolean =
            size > MAX_CACHED_FACILITIES
    }
    private val evidenceCache = object : LinkedHashMap<String, ProgramEvidence>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ProgramEvidence>?): Boolean =
            size > MAX_CACHED_EVIDENCE
    }
    private var listState: UsageOptionListState = UsageOptionListState.Idle
    private var evidenceState: UsageOptionEvidenceState = UsageOptionEvidenceState.Idle
    private var compareState: UsageOptionCompareState = UsageOptionCompareState.Idle
    private var listObserver: ((UsageOptionListState) -> Unit)? = null
    private var evidenceObserver: ((UsageOptionEvidenceState) -> Unit)? = null
    private var compareObserver: ((UsageOptionCompareState) -> Unit)? = null
    private var listGeneration = 0
    private var evidenceGeneration = 0
    private var compareGeneration = 0
    private var closed = false

    val currentListState: UsageOptionListState get() = synchronized(lock) { listState }
    val currentEvidenceState: UsageOptionEvidenceState get() = synchronized(lock) { evidenceState }
    val currentCompareState: UsageOptionCompareState get() = synchronized(lock) { compareState }

    fun observeList(observer: ((UsageOptionListState) -> Unit)?) {
        val snapshot = synchronized(lock) {
            if (closed) return
            listObserver = observer
            listState
        }
        observer?.let { callback -> mainHandler.post { callback(snapshot) } }
    }

    fun observeEvidence(observer: ((UsageOptionEvidenceState) -> Unit)?) {
        val snapshot = synchronized(lock) {
            if (closed) return
            evidenceObserver = observer
            evidenceState
        }
        observer?.let { callback -> mainHandler.post { callback(snapshot) } }
    }

    fun observeCompare(observer: ((UsageOptionCompareState) -> Unit)?) {
        val snapshot = synchronized(lock) {
            if (closed) return
            compareObserver = observer
            compareState
        }
        observer?.let { callback -> mainHandler.post { callback(snapshot) } }
    }

    fun load(facilityId: String) = loadList(facilityId, force = false)
    fun retry(facilityId: String) = loadList(facilityId, force = true)
    fun loadEvidence(option: UsageOptionItem) = loadEvidence(option, force = false)
    fun retryEvidence(option: UsageOptionItem) = loadEvidence(option, force = true)
    fun compare(ids: List<String>) = loadComparison(ids, force = false)
    fun retryCompare() {
        val ids = synchronized(lock) { (compareState as? UsageOptionCompareState.Error)?.ids } ?: return
        loadComparison(ids, force = true)
    }

    private fun loadComparison(ids: List<String>, force: Boolean) {
        require(ids.size in 2..5 && ids.distinct().size == ids.size)
        val requestGeneration = synchronized(lock) {
            if (closed) return
            if (!force && (compareState as? UsageOptionCompareState.Loading)?.ids == ids) return
            compareGeneration += 1
            compareState = UsageOptionCompareState.Loading(ids.toList())
            notifyCompareLocked()
            compareGeneration
        }
        executor.execute {
            val result = client.compare(ids)
            synchronized(lock) {
                if (closed || requestGeneration != compareGeneration) return@execute
                compareState = result.toCompareState(ids)
                notifyCompareLocked()
            }
        }
    }

    private fun loadList(facilityId: String, force: Boolean) {
        require(facilityId.isNotBlank())
        val requestGeneration = synchronized(lock) {
            if (closed) return
            if (!force) {
                listCache[facilityId]?.let { page ->
                    listGeneration += 1
                    listState = UsageOptionListState.Ready(page)
                    notifyListLocked()
                    return
                }
                if ((listState as? UsageOptionListState.Loading)?.facilityId == facilityId) return
            }
            listGeneration += 1
            listState = UsageOptionListState.Loading(facilityId)
            notifyListLocked()
            listGeneration
        }
        executor.execute {
            val result = client.fetchForFacility(facilityId)
            synchronized(lock) {
                if (closed || requestGeneration != listGeneration) return@execute
                listState = result.toListState(facilityId)
                (listState as? UsageOptionListState.Ready)?.page?.let { listCache[it.facilityId] = it }
                notifyListLocked()
            }
        }
    }

    private fun loadEvidence(option: UsageOptionItem, force: Boolean) {
        val requestGeneration = synchronized(lock) {
            if (closed) return
            if (!force) {
                evidenceCache[option.usageOptionId]?.let { evidence ->
                    evidenceGeneration += 1
                    evidenceState = UsageOptionEvidenceState.Ready(option.usageOptionId, evidence)
                    notifyEvidenceLocked()
                    return
                }
                if ((evidenceState as? UsageOptionEvidenceState.Loading)?.optionId == option.usageOptionId) return
            }
            evidenceGeneration += 1
            evidenceState = UsageOptionEvidenceState.Loading(option.usageOptionId)
            notifyEvidenceLocked()
            evidenceGeneration
        }
        executor.execute {
            val result = client.fetchEvidence(option.evidenceHref)
            synchronized(lock) {
                if (closed || requestGeneration != evidenceGeneration) return@execute
                evidenceState = result.toEvidenceState(option)
                (evidenceState as? UsageOptionEvidenceState.Ready)?.let {
                    evidenceCache[option.usageOptionId] = it.evidence
                }
                notifyEvidenceLocked()
            }
        }
    }

    private fun UsageOptionFetchResult<UsageOptionPage>.toListState(facilityId: String): UsageOptionListState =
        when (this) {
            is UsageOptionFetchResult.Success -> UsageOptionListState.Ready(value)
            is UsageOptionFetchResult.ApiFailure -> UsageOptionListState.Error(facilityId, code, retryable)
            is UsageOptionFetchResult.NetworkFailure -> UsageOptionListState.Error(facilityId, "NETWORK_UNAVAILABLE", true)
            is UsageOptionFetchResult.InvalidResponse -> UsageOptionListState.Error(facilityId, "INVALID_USAGE_OPTION_RESPONSE", false)
        }

    private fun UsageOptionFetchResult<ProgramEvidence>.toEvidenceState(option: UsageOptionItem): UsageOptionEvidenceState =
        when (this) {
            is UsageOptionFetchResult.Success -> {
                if (
                    value.usageOptionId == option.usageOptionId &&
                    value.facilityId == option.facilityId &&
                    value.programName == option.programName
                ) {
                    UsageOptionEvidenceState.Ready(option.usageOptionId, value)
                } else {
                    UsageOptionEvidenceState.Error(option.usageOptionId, "INVALID_EVIDENCE_RESPONSE", false)
                }
            }
            is UsageOptionFetchResult.ApiFailure -> UsageOptionEvidenceState.Error(option.usageOptionId, code, retryable)
            is UsageOptionFetchResult.NetworkFailure -> UsageOptionEvidenceState.Error(option.usageOptionId, "NETWORK_UNAVAILABLE", true)
            is UsageOptionFetchResult.InvalidResponse -> UsageOptionEvidenceState.Error(option.usageOptionId, "INVALID_EVIDENCE_RESPONSE", false)
        }

    private fun UsageOptionFetchResult<UsageOptionComparisonData>.toCompareState(ids: List<String>): UsageOptionCompareState =
        when (this) {
            is UsageOptionFetchResult.Success -> {
                if (value.items.count { it.eligible && it.usageOptionId in ids } < 2) {
                    UsageOptionCompareState.Error(ids, "INSUFFICIENT_ELIGIBLE_OPTIONS", false)
                } else {
                    UsageOptionCompareState.Ready(ids, value)
                }
            }
            is UsageOptionFetchResult.ApiFailure -> UsageOptionCompareState.Error(ids, code, retryable)
            is UsageOptionFetchResult.NetworkFailure -> UsageOptionCompareState.Error(ids, "NETWORK_UNAVAILABLE", true)
            is UsageOptionFetchResult.InvalidResponse -> UsageOptionCompareState.Error(ids, "INVALID_COMPARISON_RESPONSE", false)
        }

    private fun notifyListLocked() {
        val snapshot = listState
        mainHandler.post {
            val callback = synchronized(lock) { if (closed) null else listObserver }
            callback?.invoke(snapshot)
        }
    }

    private fun notifyEvidenceLocked() {
        val snapshot = evidenceState
        mainHandler.post {
            val callback = synchronized(lock) { if (closed) null else evidenceObserver }
            callback?.invoke(snapshot)
        }
    }

    private fun notifyCompareLocked() {
        val snapshot = compareState
        mainHandler.post {
            val callback = synchronized(lock) { if (closed) null else compareObserver }
            callback?.invoke(snapshot)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            listGeneration += 1
            evidenceGeneration += 1
            compareGeneration += 1
            listObserver = null
            evidenceObserver = null
            compareObserver = null
            listCache.clear()
            evidenceCache.clear()
        }
        executor.shutdownNow()
    }

    private companion object {
        const val MAX_CACHED_FACILITIES = 16
        const val MAX_CACHED_EVIDENCE = 48
    }
}

internal fun UsageOptionListState.forFacility(facilityId: String): UsageOptionListState = when (this) {
    UsageOptionListState.Idle -> this
    is UsageOptionListState.Loading -> takeIf { it.facilityId == facilityId } ?: UsageOptionListState.Idle
    is UsageOptionListState.Ready -> takeIf { it.page.facilityId == facilityId } ?: UsageOptionListState.Idle
    is UsageOptionListState.Error -> takeIf { it.facilityId == facilityId } ?: UsageOptionListState.Idle
}

internal fun UsageOptionEvidenceState.forOption(optionId: String): UsageOptionEvidenceState = when (this) {
    UsageOptionEvidenceState.Idle -> this
    is UsageOptionEvidenceState.Loading -> takeIf { it.optionId == optionId } ?: UsageOptionEvidenceState.Idle
    is UsageOptionEvidenceState.Ready -> takeIf { it.optionId == optionId } ?: UsageOptionEvidenceState.Idle
    is UsageOptionEvidenceState.Error -> takeIf { it.optionId == optionId } ?: UsageOptionEvidenceState.Idle
}

internal fun UsageOptionCompareState.forIds(ids: List<String>): UsageOptionCompareState = when (this) {
    UsageOptionCompareState.Idle -> this
    is UsageOptionCompareState.Loading -> takeIf { it.ids == ids } ?: UsageOptionCompareState.Idle
    is UsageOptionCompareState.Ready -> takeIf { it.ids == ids } ?: UsageOptionCompareState.Idle
    is UsageOptionCompareState.Error -> takeIf { it.ids == ids } ?: UsageOptionCompareState.Idle
}
