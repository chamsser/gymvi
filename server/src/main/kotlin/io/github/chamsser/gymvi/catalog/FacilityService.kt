package io.github.chamsser.gymvi.catalog

import org.springframework.stereotype.Service

@Service
class FacilityService(private val catalog: FacilityCatalog) {
    fun search(
        minLongitude: Double?,
        minLatitude: Double?,
        maxLongitude: Double?,
        maxLatitude: Double?,
        limit: Int,
        query: String? = null,
        sort: String = FacilitySortOrder.RELEVANCE.apiValue,
        cursor: String? = null,
    ): FacilitySearchResult {
        if (limit !in 1..MAX_LIMIT) {
            throw ApiValidationException(
                "VALIDATION_LIMIT",
                "결과 제한은 1 이상 $MAX_LIMIT 이하여야 합니다.",
            )
        }
        val bounds = BoundingBox.fromNullable(minLongitude, minLatitude, maxLongitude, maxLatitude)
        return catalog.search(
            bounds = bounds,
            query = normalizeFacilityQuery(query),
            sort = FacilitySortOrder.fromApiValue(sort),
            limit = limit,
            offset = decodeFacilityCursor(cursor),
        )
    }

    fun find(facilityId: String): FacilityDetailResult {
        if (facilityId.isBlank() || facilityId.length > MAX_FACILITY_ID_LENGTH) {
            throw ApiValidationException("VALIDATION_FACILITY_ID", "시설 ID 형식이 올바르지 않습니다.")
        }
        return catalog.find(facilityId) ?: throw FacilityNotFoundException()
    }

    companion object {
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 1_000
        private const val MAX_FACILITY_ID_LENGTH = 160
    }
}
