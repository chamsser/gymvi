package io.github.chamsser.gymvi.usage

import io.github.chamsser.gymvi.catalog.FacilityCatalog
import io.github.chamsser.gymvi.catalog.FacilityNotFoundException
import org.springframework.stereotype.Service

@Service
class UsageOptionService(
    private val facilityCatalog: FacilityCatalog,
    private val usageOptionCatalog: UsageOptionCatalog,
    private val operatorProgramTimeCatalog: OperatorProgramTimeCatalog = OperatorProgramTimeCatalog.EMPTY,
) {
    fun findForFacility(facilityId: String): UsageOptionServiceResult {
        if (facilityId.isBlank() || facilityId.length > MAX_FACILITY_ID_LENGTH) {
            throw io.github.chamsser.gymvi.catalog.ApiValidationException(
                "VALIDATION_FACILITY_ID",
                "시설 ID 형식이 올바르지 않습니다.",
            )
        }
        val facility = facilityCatalog.find(facilityId) ?: throw FacilityNotFoundException()
        val result = usageOptionCatalog.findForFacility(facility.dataset.version, facilityId)
        val unavailable = buildList {
            if (result.programDatasetVersion == null) add("PROGRAM_DATASET_NOT_ACTIVE")
        }
        return UsageOptionServiceResult(
            facilityDatasetVersion = facility.dataset.version,
            facilityAsOf = facility.dataset.asOf,
            response = UsageOptionListResponse(
                facilityId = facilityId,
                programDatasetVersion = result.programDatasetVersion,
                programAsOf = result.programDatasetVersion?.let { result.programAsOf },
                attribution = result.programDatasetVersion?.let { result.attribution },
                usageOptions = result.options.map { option ->
                    UsageOptionItemResponse(
                        usageOptionId = option.usageOptionId,
                        facilityId = option.facilityId,
                        programName = option.programName,
                        programTypeName = option.programTypeName,
                        targetName = option.targetName,
                        beginDate = option.beginDate,
                        endDate = option.endDate,
                        weekdays = option.weekdays,
                        sourceTimeValue = option.sourceTimeValue,
                        operatorTime = operatorProgramTimeCatalog.find(option),
                        recruitmentCount = option.recruitmentCount,
                        priceWon = option.price.amountWon,
                        priceTypeName = option.priceTypeName,
                        homepageUrl = option.homepageUrl,
                        facilityOperation = EvidenceStateResponse(
                            option.operationState,
                            option.operationReasonCode,
                        ),
                        programApplication = EvidenceStateResponse(
                            option.applicationState,
                            option.applicationReasonCode,
                        ),
                        joinState = option.joinState,
                        evidenceHref = "/api/v1/evidence/program:${option.usageOptionId}",
                    )
                },
                unavailableReasonCodes = unavailable,
            ),
        )
    }

    companion object {
        private const val MAX_FACILITY_ID_LENGTH = 160
    }
}

data class UsageOptionServiceResult(
    val facilityDatasetVersion: String,
    val facilityAsOf: java.time.Instant,
    val response: UsageOptionListResponse,
)
