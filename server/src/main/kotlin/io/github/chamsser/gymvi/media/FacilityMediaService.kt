package io.github.chamsser.gymvi.media

import io.github.chamsser.gymvi.catalog.FacilityService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.LinkedHashMap

@Service
class FacilityMediaService(
    private val facilityService: FacilityService,
    private val provider: FacilityImageProvider,
    @param:Value("\${gymvi.media.daily-provider-call-limit:20000}")
    private val dailyProviderCallLimit: Int,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Any()
    private val cache = object : LinkedHashMap<FacilityMediaCacheKey, FacilityMediaResult>(
        MAX_CACHE_ENTRIES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<FacilityMediaCacheKey, FacilityMediaResult>?,
        ): Boolean = size > MAX_CACHE_ENTRIES
    }
    private var budgetDate: LocalDate = LocalDate.now(clock)
    private var providerCallsToday = 0

    init {
        require(dailyProviderCallLimit in 1..MAX_DAILY_PROVIDER_CALL_LIMIT)
    }

    fun find(facilityId: String, limit: Int = MAX_FACILITY_IMAGES): FacilityMediaResult {
        if (limit !in 1..MAX_FACILITY_IMAGES) {
            throw FacilityMediaValidationException(
                "VALIDATION_MEDIA_LIMIT",
                "관련 이미지는 한 번에 1장 이상 ${MAX_FACILITY_IMAGES}장 이하로 요청해야 합니다.",
            )
        }
        val detail = facilityService.find(facilityId)
        val query = buildFacilityImageQuery(detail.facility)
        val key = FacilityMediaCacheKey(detail.dataset.version, facilityId)
        val now = clock.instant()
        synchronized(lock) {
            resetBudgetIfNeeded()
            cache[key]?.takeIf { cached ->
                Duration.between(cached.retrievedAt, now) <= CACHE_TTL
            }?.let { cached -> return cached.withLimit(limit) }
            cache.remove(key)
            if (providerCallsToday >= dailyProviderCallLimit) {
                throw FacilityMediaBudgetExhaustedException()
            }
            providerCallsToday += 1
        }

        val images = provider.search(query, MAX_FACILITY_IMAGES).take(MAX_FACILITY_IMAGES)
        val result = FacilityMediaResult(
            dataset = detail.dataset,
            facilityId = facilityId,
            images = images,
            moreImagesUrl = buildNaverImageSearchPageUrl(query),
            retrievedAt = now,
        )
        synchronized(lock) {
            cache[key] = result
        }
        return result.withLimit(limit)
    }

    private fun resetBudgetIfNeeded() {
        val today = LocalDate.now(clock)
        if (today != budgetDate) {
            budgetDate = today
            providerCallsToday = 0
            cache.clear()
        }
    }

    private data class FacilityMediaCacheKey(
        val datasetVersion: String,
        val facilityId: String,
    )

    private companion object {
        const val MAX_CACHE_ENTRIES = 2_048
        const val MAX_DAILY_PROVIDER_CALL_LIMIT = 25_000
        val CACHE_TTL: Duration = Duration.ofHours(24)
    }
}

private fun FacilityMediaResult.withLimit(limit: Int): FacilityMediaResult =
    if (images.size <= limit) this else copy(images = images.take(limit))
