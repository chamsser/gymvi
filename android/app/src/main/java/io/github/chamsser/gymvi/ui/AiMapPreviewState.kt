package io.github.chamsser.gymvi.ui

import io.github.chamsser.gymvi.data.AiTurnCard
import java.util.LinkedHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class AiMapPreviewPoint(
    val facilityId: String,
    val latitude: Double,
    val longitude: Double,
    val number: Int,
)

internal data class AiMapPreviewKey(
    val points: List<AiMapPreviewPoint>,
    val width: Int,
    val height: Int,
    val dark: Boolean,
)

internal fun aiMapPreviewPoints(cards: List<AiTurnCard>): List<AiMapPreviewPoint> = cards.asSequence()
    .filter { it.latitude.isFinite() && it.longitude.isFinite() }
    .filter { it.latitude in 33.0..39.5 && it.longitude in 124.0..132.0 }
    .distinctBy { it.option.facilityId }
    .take(5)
    .mapIndexed { index, card ->
        AiMapPreviewPoint(card.option.facilityId, card.latitude, card.longitude, index + 1)
    }
    .toList()

/** Memory-only display cache. Eviction never recycles an image still displayed by Compose. */
internal class AiMapPreviewCache<K, V>(
    private val maxBytes: Int,
    private val maxEntries: Int,
    private val bytesOf: (V) -> Int,
) {
    private val values = LinkedHashMap<K, V>(16, 0.75f, true)
    var byteCount: Int = 0
        private set
    val size: Int get() = values.size

    init { require(maxBytes > 0 && maxEntries > 0) }

    operator fun get(key: K): V? = values[key]

    fun remove(key: K) {
        values.remove(key)?.let { byteCount -= bytesOf(it) }
    }

    fun put(key: K, value: V) {
        val bytes = bytesOf(value)
        require(bytes >= 0)
        if (bytes > maxBytes) return
        remove(key)
        values[key] = value
        byteCount += bytes
        while (byteCount > maxBytes || values.size > maxEntries) {
            val iterator = values.entries.iterator()
            byteCount -= bytesOf(iterator.next().value)
            iterator.remove()
        }
    }
}

/** Counts timeouts per map while it was visible; the oldest keys are forgotten to bound memory. */
internal class AiMapPreviewAttempts<K>(capacity: Int) {
    private val timeouts = AiMapPreviewCache<K, Int>(capacity, capacity) { 1 }

    /** The budget for the next attempt, or null once every budget has timed out. */
    fun budget(key: K, budgetsMs: List<Long>): Long? = budgetsMs.getOrNull(timeouts[key] ?: 0)

    fun timedOut(key: K) = timeouts.put(key, (timeouts[key] ?: 0) + 1)

    fun succeeded(key: K) = timeouts.remove(key)

    fun reset(key: K) = timeouts.remove(key)
}

/** The owner must close its native renderer before returning, including cancellation. */
internal class AiMapPreviewRenderGate {
    private val mutex = Mutex()
    suspend fun <T> render(block: suspend () -> T): T = mutex.withLock { block() }
}
