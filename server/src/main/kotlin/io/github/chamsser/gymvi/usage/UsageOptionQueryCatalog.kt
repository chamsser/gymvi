package io.github.chamsser.gymvi.usage

import io.github.chamsser.gymvi.catalog.BoundingBox
import io.github.chamsser.gymvi.catalog.DatasetReference
import io.github.chamsser.gymvi.catalog.DatasetUnavailableException
import java.time.Instant
import java.time.LocalDate

data class JoinedUsageOptionRecord(
    val option: UsageOptionRecord,
    val facilityName: String,
    val facilityTypeName: String?,
    val roadAddress: String?,
    val longitude: Double,
    val latitude: Double,
)

data class UsageOptionQueryResult(
    val facilityDataset: DatasetReference,
    val programDatasetVersion: String?,
    val programAsOf: Instant?,
    val attribution: String?,
    val records: List<JoinedUsageOptionRecord>,
)

interface UsageOptionQueryCatalog {
    fun findInArea(bounds: BoundingBox, candidateCap: Int, date: LocalDate): UsageOptionQueryResult
    fun findByIds(ids: List<String>): UsageOptionQueryResult
}

class UnavailableUsageOptionQueryCatalog : UsageOptionQueryCatalog {
    override fun findInArea(bounds: BoundingBox, candidateCap: Int, date: LocalDate): UsageOptionQueryResult =
        throw DatasetUnavailableException()

    override fun findByIds(ids: List<String>): UsageOptionQueryResult = throw DatasetUnavailableException()
}
