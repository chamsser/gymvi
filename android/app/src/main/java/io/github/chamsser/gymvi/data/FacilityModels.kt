package io.github.chamsser.gymvi.data

import java.time.OffsetDateTime
import kotlin.math.cos

data class FacilityBounds(
    val minLongitude: Double,
    val minLatitude: Double,
    val maxLongitude: Double,
    val maxLatitude: Double,
) {
    init {
        require(listOf(minLongitude, minLatitude, maxLongitude, maxLatitude).all(Double::isFinite))
        require(minLongitude in -180.0..180.0 && maxLongitude in -180.0..180.0)
        require(minLatitude in -90.0..90.0 && maxLatitude in -90.0..90.0)
        require(minLongitude < maxLongitude && minLatitude < maxLatitude)
    }

    companion object {
        const val GEOYEO_CENTER_LONGITUDE = 127.1439
        const val GEOYEO_CENTER_LATITUDE = 37.4931

        val GEOYEO_INITIAL = FacilityBounds(
            minLongitude = 127.134,
            minLatitude = 37.485,
            maxLongitude = 127.154,
            maxLatitude = 37.502,
        )

        fun around(
            latitude: Double,
            longitude: Double,
            radiusKilometers: Double,
        ): FacilityBounds {
            require(latitude.isFinite() && latitude in -89.0..89.0)
            require(longitude.isFinite() && longitude in -179.0..179.0)
            require(radiusKilometers.isFinite() && radiusKilometers in 0.1..100.0)

            val latitudeDelta = radiusKilometers / KILOMETERS_PER_LATITUDE_DEGREE
            val longitudeScale = cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
            val longitudeDelta = radiusKilometers /
                (KILOMETERS_PER_LATITUDE_DEGREE * longitudeScale)
            return FacilityBounds(
                minLongitude = (longitude - longitudeDelta).coerceAtLeast(-180.0),
                minLatitude = (latitude - latitudeDelta).coerceAtLeast(-90.0),
                maxLongitude = (longitude + longitudeDelta).coerceAtMost(180.0),
                maxLatitude = (latitude + latitudeDelta).coerceAtMost(90.0),
            )
        }

        private const val KILOMETERS_PER_LATITUDE_DEGREE = 111.32
    }
}

data class FacilityMapItem(
    val facilityId: String,
    val name: String,
    val facilityTypeName: String?,
    val latitude: Double,
    val longitude: Double,
    val operationState: EvidenceState,
    val operationReasonCode: String,
    val provenanceHref: String,
    val sourceRecordId: String,
    val facilityClassName: String? = null,
    val roadAddress: String? = null,
    val lotAddress: String? = null,
    val sidoName: String? = null,
    val sigunguName: String? = null,
    val phoneNumber: String? = null,
    val description: String? = null,
    val grossFloorAreaSquareMeters: Double? = null,
    val operatingHours: FacilityOperatingHours? = null,
) {
    init {
        require(facilityId.isNotBlank())
        require(name.isNotBlank())
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        require(operationReasonCode.isNotBlank())
        require(provenanceHref.startsWith("/api/v1/facilities/"))
        require(sourceRecordId.isNotBlank())
        require(phoneNumber == null || FACILITY_PHONE_PATTERN.matches(phoneNumber))
        require(
            grossFloorAreaSquareMeters == null ||
                grossFloorAreaSquareMeters.isFinite() &&
                grossFloorAreaSquareMeters > 0 &&
                grossFloorAreaSquareMeters <= 10_000_000
        )
    }
}

data class FacilityOperatingHoursSlot(
    val days: String,
    val hours: String,
)

/**
 * Hours published on an operator page and the time Gymvi checked them. They are not evidence
 * that the facility is open now, so they never change [FacilityMapItem.operationState].
 */
data class FacilityOperatingHours(
    val schedule: List<FacilityOperatingHoursSlot>,
    val closedDays: List<String>,
    val note: String?,
    val sourceName: String,
    val checkedAt: OffsetDateTime,
) {
    init {
        require(schedule.isNotEmpty())
        require(sourceName.isNotBlank())
    }
}

enum class FacilityQuerySort(val apiValue: String) {
    RELEVANCE("relevance"),
    DISTANCE("distance"),
}

internal val FACILITY_PHONE_PATTERN = Regex("^[0-9+()\\-\\s]{3,40}$")

enum class EvidenceState {
    OPEN,
    CLOSED,
    UNKNOWN,
}

data class FacilityPage(
    val facilities: List<FacilityMapItem>,
    val totalCount: Int = facilities.size,
    val nextCursor: String? = null,
    val datasetVersion: String,
    val asOf: String,
) {
    init {
        require(totalCount >= facilities.size)
        require(nextCursor == null || nextCursor.isNotBlank())
        require(datasetVersion.isNotBlank())
        require(asOf.isNotBlank())
    }
}

sealed interface FacilityFetchResult {
    data class Success(val page: FacilityPage) : FacilityFetchResult

    data class NetworkFailure(val reason: String) : FacilityFetchResult

    data class ApiFailure(
        val statusCode: Int,
        val code: String,
        val retryable: Boolean,
    ) : FacilityFetchResult

    data class InvalidResponse(val reason: String) : FacilityFetchResult
}
