package io.github.chamsser.gymvi.catalog

interface FacilityCatalog {
    fun activeDataset(): DatasetReference?

    fun search(
        bounds: BoundingBox,
        query: String?,
        sort: FacilitySortOrder,
        limit: Int,
        offset: Int = 0,
    ): FacilitySearchResult

    fun find(facilityId: String): FacilityDetailResult?
}
