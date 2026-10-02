package io.github.chamsser.gymvi.catalog

class UnavailableFacilityCatalog : FacilityCatalog {
    override fun activeDataset(): DatasetReference? = null

    override fun search(
        bounds: BoundingBox,
        query: String?,
        sort: FacilitySortOrder,
        limit: Int,
        offset: Int,
    ): FacilitySearchResult =
        throw DatasetUnavailableException()

    override fun find(facilityId: String): FacilityDetailResult =
        throw DatasetUnavailableException()
}
