package io.github.chamsser.gymvi.usage

interface UsageOptionCatalog {
    fun findForFacility(
        facilityDatasetVersion: String,
        facilityId: String,
    ): UsageOptionCatalogResult
}

class UnavailableUsageOptionCatalog : UsageOptionCatalog {
    override fun findForFacility(
        facilityDatasetVersion: String,
        facilityId: String,
    ): UsageOptionCatalogResult = UsageOptionCatalogResult(
        programDatasetVersion = null,
        programAsOf = null,
        attribution = null,
        options = emptyList(),
    )
}
