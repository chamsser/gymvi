package io.github.chamsser.gymvi.data

import java.time.LocalDate
import java.time.OffsetDateTime

data class OperatorTimeSource(val name: String, val url: String)

data class OperatorTime(
    val startTime: String,
    val endTime: String,
    val days: String?,
    val operatorProgramName: String,
    val validFrom: LocalDate,
    val validTo: LocalDate,
    val sources: List<OperatorTimeSource>,
    val checkedAt: OffsetDateTime,
)

data class UsageOptionState(
    val state: String,
    val reasonCode: String,
)

data class UsageOptionItem(
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
    val priceWon: Int?,
    val priceTypeName: String?,
    val homepageUrl: String?,
    val facilityOperation: UsageOptionState,
    val programApplication: UsageOptionState,
    val joinState: String,
    val evidenceHref: String,
    val operatorTime: OperatorTime? = null,
)

data class UsageOptionComparisonItem(
    val usageOptionId: String,
    val eligible: Boolean,
    val option: UsageOptionItem,
    val facilityName: String,
)

data class UsageOptionComparisonValue(
    val usageOptionId: String,
    val state: String,
    val value: Any?,
)

data class UsageOptionComparisonDimension(
    val dimension: String,
    val values: List<UsageOptionComparisonValue>,
    val bestUsageOptionIds: List<String>,
)

data class UsageOptionComparisonData(
    val items: List<UsageOptionComparisonItem>,
    val dimensions: List<UsageOptionComparisonDimension>,
    val programAsOf: String?,
    val attribution: String?,
)

data class UsageOptionPage(
    val facilityId: String,
    val programDatasetVersion: String?,
    val programAsOf: String?,
    val attribution: String?,
    val options: List<UsageOptionItem>,
    val unavailableReasonCodes: List<String>,
)

data class ProgramEvidenceField(
    val field: String,
    val sourceValue: String?,
)

data class ProgramEvidence(
    val usageOptionId: String,
    val facilityId: String,
    val programName: String,
    val licenseAttribution: String,
    val licenseName: String,
    val datasetAsOf: String,
    val datasetVersion: String,
    val fields: List<ProgramEvidenceField>,
    val limitations: List<String>,
    val facilityOperation: UsageOptionState,
    val programApplication: UsageOptionState,
)

sealed interface UsageOptionFetchResult<out T> {
    data class Success<T>(val value: T) : UsageOptionFetchResult<T>
    data class ApiFailure(
        val statusCode: Int,
        val code: String,
        val retryable: Boolean,
    ) : UsageOptionFetchResult<Nothing>
    data class NetworkFailure(val reason: String) : UsageOptionFetchResult<Nothing>
    data class InvalidResponse(val reason: String) : UsageOptionFetchResult<Nothing>
}
