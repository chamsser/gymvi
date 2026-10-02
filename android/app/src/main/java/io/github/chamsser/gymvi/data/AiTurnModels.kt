package io.github.chamsser.gymvi.data

import java.time.LocalDate

enum class AiTurnAction {
    ASK_CLARIFYING_QUESTION,
    SHOW_OPTIONS,
    COMPARE_OPTIONS,
    EXERCISE_GUIDE,
    SAFETY_GUIDANCE,
    FOCUS_MAP,
    SELECT_DESTINATION,
    PREVIEW_ROUTE,
    OUT_OF_SCOPE,
    MANUAL_FILTERS,
}

enum class AiClientActionType {
    FOCUS_MAP,
    SELECT_USAGE_OPTION,
    PREVIEW_ROUTE,
    OPEN_USAGE_OPTION_COMPARISON,
    OPEN_MANUAL_FILTERS,
}

data class AiRequestOrigin(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

data class AiTurnRequestContext(
    val area: FacilityBounds,
    val date: LocalDate,
    val favoriteFacilityIds: Set<String>,
    val recentFacilityIds: List<String>,
)

/** Only coarse, opted-in attributes cross the request boundary. Raw measurements never do. */
data class AiReferenceContext(
    val memory: AiMemoryFacts? = null,
    val ageBand: AiAgeBand? = null,
    val experience: AiExerciseExperience? = null,
    val goal: AiExerciseGoal? = null,
    val continuation: String? = null,
)

data class AiTurnCard(
    val rank: Int,
    val score: Int,
    val matchedConditions: List<String>,
    val unmetPreferredConditions: List<String>,
    val uncertaintyCodes: List<String>,
    val straightLineDistanceMeters: Int?,
    val option: UsageOptionItem,
    val facilityName: String,
    val facilityTypeName: String?,
    val roadAddress: String?,
    val latitude: Double,
    val longitude: Double,
)

data class AiClientAction(
    val type: AiClientActionType,
    val optionId: String? = null,
    val facilityId: String? = null,
    val optionIds: List<String> = emptyList(),
)

data class AiTurnResponse(
    val conversationId: String,
    val reply: String,
    val action: AiTurnAction,
    val cards: List<AiTurnCard>,
    val exerciseContents: List<ExerciseContentItem> = emptyList(),
    val clientActions: List<AiClientAction>,
    val uncertainties: List<String>,
    val fallback: Boolean,
    val fallbackReasonCode: String?,
    val datasetVersion: String?,
    val asOf: String?,
    val conversationTitle: String? = null,
    val continuation: String? = null,
    val memoryFacts: AiMemoryFacts? = null,
)

sealed interface AiTurnFetchResult {
    data class Success(
        val response: AiTurnResponse,
        val responseBody: String? = null,
    ) : AiTurnFetchResult
    data class ApiFailure(
        val statusCode: Int,
        val code: String,
        val retryable: Boolean,
    ) : AiTurnFetchResult
    data class NetworkFailure(val reason: String) : AiTurnFetchResult
    data class InvalidResponse(val reason: String) : AiTurnFetchResult
}
