package io.github.chamsser.gymvi.catalog

data class CoordinateResponse(
    val latitude: Double,
    val longitude: Double,
)

data class FacilityTypeResponse(
    val code: String?,
    val name: String?,
)

data class FacilityClassResponse(
    val code: String?,
    val name: String?,
)

data class AdministrativeAreaResponse(
    val sido: String?,
    val sigungu: String?,
)

data class StateResponse(
    val state: String,
    val reasonCode: String,
    val evidenceId: String? = null,
)

data class ProvenanceLinkResponse(
    val href: String,
    val sourceRecordId: String,
)

data class FacilitySummaryResponse(
    val facilityId: String,
    val name: String,
    val facilityType: FacilityTypeResponse,
    val facilityClass: FacilityClassResponse,
    val roadAddress: String?,
    val lotAddress: String?,
    val administrativeArea: AdministrativeAreaResponse,
    val phone: String?,
    val description: String?,
    val grossFloorAreaSquareMeters: Double?,
    val coordinate: CoordinateResponse,
    val facilityOperation: StateResponse,
    val provenance: ProvenanceLinkResponse,
    val operatingHours: OperatingHoursResponse? = null,
)

data class FacilityListResponse(
    val facilities: List<FacilitySummaryResponse>,
    val totalCount: Int,
    val nextCursor: String? = null,
)

data class FacilitySourceResponse(
    val sourceDatasetId: String,
    val sourceRecordId: String,
    val catalogUrl: String,
)

data class FacilityDetailResponse(
    val facilityId: String,
    val name: String,
    val facilityType: FacilityTypeResponse,
    val facilityClass: FacilityClassResponse,
    val roadAddress: String?,
    val lotAddress: String?,
    val administrativeArea: AdministrativeAreaResponse,
    val phone: String?,
    val description: String?,
    val grossFloorAreaSquareMeters: Double?,
    val coordinate: CoordinateResponse,
    val facilityOperation: StateResponse,
    val source: FacilitySourceResponse,
    val operatingHours: OperatingHoursResponse? = null,
)

fun FacilityRecord.toSummary(operatingHours: OperatingHoursResponse? = null): FacilitySummaryResponse =
    FacilitySummaryResponse(
        facilityId = facilityId,
        name = name,
        facilityType = FacilityTypeResponse(facilityTypeCode, facilityTypeName),
        facilityClass = FacilityClassResponse(facilityClassCode, facilityClassName),
        roadAddress = roadAddress,
        lotAddress = lotAddress,
        administrativeArea = AdministrativeAreaResponse(sidoName, sigunguName),
        phone = phone,
        description = description,
        grossFloorAreaSquareMeters = grossFloorAreaSquareMeters,
        coordinate = CoordinateResponse(latitude, longitude),
        facilityOperation = StateResponse(operationState, operationReason),
        provenance = ProvenanceLinkResponse(
            href = "/api/v1/facilities/$facilityId",
            sourceRecordId = sourceRecordId,
        ),
        operatingHours = operatingHours,
    )

fun FacilityRecord.toDetail(operatingHours: OperatingHoursResponse? = null): FacilityDetailResponse =
    FacilityDetailResponse(
        facilityId = facilityId,
        name = name,
        facilityType = FacilityTypeResponse(facilityTypeCode, facilityTypeName),
        facilityClass = FacilityClassResponse(facilityClassCode, facilityClassName),
        roadAddress = roadAddress,
        lotAddress = lotAddress,
        administrativeArea = AdministrativeAreaResponse(sidoName, sigunguName),
        phone = phone,
        description = description,
        grossFloorAreaSquareMeters = grossFloorAreaSquareMeters,
        coordinate = CoordinateResponse(latitude, longitude),
        facilityOperation = StateResponse(operationState, operationReason),
        source = FacilitySourceResponse(sourceDatasetId, sourceRecordId, catalogUrl),
        operatingHours = operatingHours,
    )
