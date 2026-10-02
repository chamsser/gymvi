package io.github.chamsser.gymvi.ui

import io.github.chamsser.gymvi.data.FacilityBounds
import io.github.chamsser.gymvi.data.FacilityMapItem
import java.text.Collator
import java.text.CollationKey
import java.text.Normalizer
import java.util.Locale

enum class FacilityResultSort {
    RELEVANCE,
    DISTANCE,
}

internal fun facilitySortOrigin(sort: FacilityResultSort, origin: RouteCoordinate?): RouteCoordinate? =
    origin.takeIf { sort == FacilityResultSort.DISTANCE }

internal enum class FacilityOwnershipFilter {
    ANY,
    PUBLIC,
    PRIVATE,
}

data class MapSearchFocus(
    val key: String,
    val latitude: Double,
    val longitude: Double,
)

internal fun shouldMoveCameraForFacilityRequest(origin: FacilityRequestOrigin?): Boolean =
    origin == FacilityRequestOrigin.SEARCH_BAR || origin == FacilityRequestOrigin.AI_RECOMMENDATION

internal fun filterFacilitiesByOwnership(
    facilities: List<FacilityMapItem>,
    filter: FacilityOwnershipFilter,
): List<FacilityMapItem> = when (filter) {
    FacilityOwnershipFilter.ANY -> facilities
    FacilityOwnershipFilter.PUBLIC -> facilities.filter { facility ->
        facility.facilityClassName?.contains("공공", ignoreCase = true) == true
    }

    FacilityOwnershipFilter.PRIVATE -> facilities.filter { facility ->
        facility.facilityClassName?.contains("민간", ignoreCase = true) == true
    }
}

internal fun sortFacilitySearchResults(
    facilities: List<FacilityMapItem>,
    query: String,
    selectedCategory: FacilityCategory?,
    sort: FacilityResultSort,
    origin: RouteCoordinate?,
): List<FacilityMapItem> {
    @Suppress("UNUSED_VARIABLE")
    val categoryHandledByQuery = selectedCategory
    val effectiveOrigin = origin ?: DEFAULT_SEARCH_ORIGIN
    // Locale keys, normalization and distance are per row, not repeated for every comparison.
    val collator = Collator.getInstance(Locale.KOREAN)
    val normalizedQuery = normalizeSearchValue(query)
    val rows = facilities.map { facility ->
        FacilitySortEntry(
            facility = facility,
            nameKey = collator.getCollationKey(facility.name),
            distance = if (sort == FacilityResultSort.DISTANCE) facilityDistanceMeters(effectiveOrigin, facility) else 0,
            relevance = if (sort == FacilityResultSort.RELEVANCE) facilitySearchRelevance(facility, normalizedQuery)
                else FacilityRelevanceScore(0, 0),
        )
    }
    val primary = if (sort == FacilityResultSort.DISTANCE) compareBy<FacilitySortEntry> { it.distance }
        else compareBy { it.relevance }
    return rows.sortedWith(primary.thenBy { it.nameKey }.thenBy { it.facility.facilityId })
        .map { it.facility }
}

private data class FacilitySortEntry(
    val facility: FacilityMapItem,
    val nameKey: CollationKey,
    val distance: Int,
    val relevance: FacilityRelevanceScore,
)

internal fun nearestFacilityForSearch(
    facilities: List<FacilityMapItem>,
    origin: RouteCoordinate?,
): FacilityMapItem? {
    val effectiveOrigin = origin ?: DEFAULT_SEARCH_ORIGIN
    return facilities.minWithOrNull(
        compareBy<FacilityMapItem> { facilityDistanceMeters(effectiveOrigin, it) }
            .thenBy(FacilityMapItem::facilityId),
    )
}

private fun facilitySearchRelevance(
    facility: FacilityMapItem,
    normalizedQuery: String,
): FacilityRelevanceScore {
    if (normalizedQuery.isEmpty()) return FacilityRelevanceScore(0, 0)
    val nameMatch = matchQuality(facility.name, normalizedQuery)
    val industryMatch = matchQuality(facility.facilityTypeName, normalizedQuery)
    return FacilityRelevanceScore(
        nameOrIndustry = minOf(nameMatch, industryMatch),
        description = matchQuality(facility.description, normalizedQuery),
    )
}

private fun matchQuality(value: String?, normalizedQuery: String): Int {
    val normalizedValue = value?.let(::normalizeSearchValue).orEmpty()
    return when {
        normalizedValue == normalizedQuery -> 0
        normalizedValue.startsWith(normalizedQuery) -> 1
        normalizedValue.contains(normalizedQuery) -> 2
        else -> 3
    }
}

private fun normalizeSearchValue(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        .lowercase(Locale.KOREAN)
        .replace(SEARCH_WHITESPACE, " ")

private val SEARCH_WHITESPACE = Regex("\\s+")

private data class FacilityRelevanceScore(
    val nameOrIndustry: Int,
    val description: Int,
) : Comparable<FacilityRelevanceScore> {
    override fun compareTo(other: FacilityRelevanceScore): Int =
        compareValues(nameOrIndustry, other.nameOrIndustry).takeIf { it != 0 }
            ?: compareValues(description, other.description)
}

private val DEFAULT_SEARCH_ORIGIN = RouteCoordinate(
    latitude = FacilityBounds.GEOYEO_CENTER_LATITUDE,
    longitude = FacilityBounds.GEOYEO_CENTER_LONGITUDE,
)
