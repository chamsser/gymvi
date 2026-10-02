package io.github.chamsser.gymvi.geocoding

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.LinkedHashMap
import java.util.Locale

data class GeocodingSearchResult(
    val addresses: List<GeocodedAddress>,
    val asOf: Instant,
)

@Service
class GeocodingService(
    private val provider: GeocodingProvider,
    @param:Value("\${gymvi.geocoding.daily-provider-call-limit:50000}")
    private val dailyProviderCallLimit: Int,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Any()
    private val cache = object : LinkedHashMap<GeocodingCacheKey, CachedGeocoding>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<GeocodingCacheKey, CachedGeocoding>?,
        ): Boolean = size > MAX_CACHE_ENTRIES
    }
    private var budgetDate: LocalDate = LocalDate.now(clock)
    private var providerCallsToday = 0

    init {
        require(dailyProviderCallLimit > 0)
    }

    fun search(rawQuery: String, limit: Int): GeocodingSearchResult {
        val query = normalizeGeocodingQuery(rawQuery)
        if (query.length !in MIN_QUERY_LENGTH..MAX_QUERY_LENGTH) {
            throw GeocodingValidationException(
                "VALIDATION_GEOCODING_QUERY",
                "주소 검색어 형식이 올바르지 않습니다.",
            )
        }
        if (limit !in 1..MAX_RESULT_LIMIT) {
            throw GeocodingValidationException(
                "VALIDATION_GEOCODING_LIMIT",
                "주소 검색 결과 수 형식이 올바르지 않습니다.",
            )
        }

        val key = GeocodingCacheKey(query.lowercase(Locale.KOREAN), limit)
        synchronized(lock) {
            val now = clock.instant()
            cache[key]?.takeIf { Duration.between(it.asOf, now) <= CACHE_TTL }?.let { cached ->
                return GeocodingSearchResult(cached.addresses, cached.asOf)
            }
            cache.remove(key)
            resetBudgetIfNeeded()
            if (providerCallsToday >= dailyProviderCallLimit) throw GeocodingBudgetExhaustedException()
            providerCallsToday += 1

            val addresses = provider.search(query, limit)
            val cached = CachedGeocoding(addresses, now)
            cache[key] = cached
            return GeocodingSearchResult(cached.addresses, cached.asOf)
        }
    }

    private fun resetBudgetIfNeeded() {
        val today = LocalDate.now(clock)
        if (today != budgetDate) {
            budgetDate = today
            providerCallsToday = 0
        }
    }

    private data class GeocodingCacheKey(val query: String, val limit: Int)
    private data class CachedGeocoding(val addresses: List<GeocodedAddress>, val asOf: Instant)

    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MAX_QUERY_LENGTH = 120
        const val MAX_RESULT_LIMIT = 10
        const val MAX_CACHE_ENTRIES = 512
        val CACHE_TTL: Duration = Duration.ofHours(24)
    }
}

internal fun normalizeGeocodingQuery(query: String): String =
    query.trim().replace(Regex("\\s+"), " ")
