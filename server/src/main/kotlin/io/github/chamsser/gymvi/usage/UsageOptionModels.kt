package io.github.chamsser.gymvi.usage

import java.time.Instant
import java.time.LocalDate

data class UsageOptionRecord(
    val usageOptionId: String,
    val facilityId: String,
    val programName: String,
    val programTypeName: String?,
    val targetName: String?,
    val beginDate: LocalDate?,
    val endDate: LocalDate?,
    val weekdays: List<String>,
    val sourceTimeValue: String?,
    val recruitmentCount: Int?,
    /** Source amount as published. Responses and ranking use [price] instead. */
    val sourcePriceWon: Int?,
    val priceTypeName: String?,
    val homepageUrl: String?,
    val operationState: String,
    val operationReasonCode: String,
    val applicationState: String,
    val applicationReasonCode: String,
    val joinState: String,
    val sourceRecordId: String,
) {
    val price: ProgramPrice = ProgramPrice.interpret(sourcePriceWon, priceTypeName, programName)
}

data class UsageOptionCatalogResult(
    val programDatasetVersion: String?,
    val programAsOf: Instant?,
    val attribution: String? = null,
    val options: List<UsageOptionRecord>,
)

data class EvidenceStateResponse(
    val state: String,
    val reasonCode: String,
)

data class UsageOptionItemResponse(
    val usageOptionId: String,
    val facilityId: String,
    val programName: String,
    val programTypeName: String?,
    val targetName: String?,
    val beginDate: LocalDate?,
    val endDate: LocalDate?,
    val weekdays: List<String>,
    val sourceTimeValue: String?,
    val operatorTime: OperatorTimeResponse?,
    val recruitmentCount: Int?,
    val priceWon: Int?,
    val priceTypeName: String?,
    val homepageUrl: String?,
    val facilityOperation: EvidenceStateResponse,
    val programApplication: EvidenceStateResponse,
    val joinState: String,
    val evidenceHref: String,
)

data class UsageOptionListResponse(
    val facilityId: String,
    val programDatasetVersion: String?,
    val programAsOf: Instant?,
    val attribution: String?,
    val usageOptions: List<UsageOptionItemResponse>,
    val unavailableReasonCodes: List<String>,
)
