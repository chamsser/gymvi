package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.api.ApiWarning
import io.github.chamsser.gymvi.catalog.DatasetReference
import io.github.chamsser.gymvi.discovery.ExerciseContentItem
import io.github.chamsser.gymvi.discovery.ExerciseSport
import io.github.chamsser.gymvi.recommendation.ComparisonData
import io.github.chamsser.gymvi.recommendation.RecommendationConditions
import io.github.chamsser.gymvi.recommendation.RecommendationItem
import io.github.chamsser.gymvi.recommendation.RecommendationQuery
import io.github.chamsser.gymvi.recommendation.Origin
import java.time.Instant
import java.time.LocalDate

enum class AiAction {
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

internal enum class AiComparisonDimension {
    NONE,
    OVERALL_RANK,
    PRICE_WON,
    STRAIGHT_LINE_DISTANCE_METERS,
    START_TIME,
}

internal enum class AiToolKind {
    SEARCH,
    COMPARE,
}

data class AiTurnInput(
    val conversationId: String?,
    val message: String,
    val context: AiTurnContext?,
    val origin: Origin? = context?.recommendationQuery?.origin,
    val references: AiReferences = AiReferences(),
    val continuation: String? = null,
)

data class AiTurnContext(
    val recommendationQuery: RecommendationQuery,
)

data class AiClientActionResponse(
    val type: String,
    val optionId: String? = null,
    val facilityId: String? = null,
    val optionIds: List<String> = emptyList(),
)

data class AiTurnData(
    val conversationId: String,
    val reply: String,
    val action: AiAction,
    val cards: List<RecommendationItem>,
    val exerciseContents: List<ExerciseContentItem>,
    val clientActions: List<AiClientActionResponse>,
    val uncertainties: List<String>,
    val fallback: Boolean,
    val fallbackReasonCode: String? = null,
    val conversationTitle: String? = null,
    val continuation: String? = null,
    val memoryFacts: AiMemoryFacts? = null,
)

internal data class AiTurnResult(
    val data: AiTurnData,
    val datasetVersion: String? = null,
    val asOf: Instant? = null,
    val warnings: List<ApiWarning> = emptyList(),
)

internal data class AiConversationTurn(
    val role: String,
    val text: String,
)

internal data class AiModelOption(
    val optionId: String,
    val facilityId: String,
    val facilityName: String,
    val programName: String,
    val weekdays: List<String>,
    val startTime: String?,
    val endTime: String?,
    val priceWon: Int?,
    val distanceMeters: Int?,
    val applicationState: String,
    val facilityOperationState: String,
    val uncertaintyCodes: List<String>,
) {
    companion object {
        fun from(item: RecommendationItem): AiModelOption = AiModelOption(
            optionId = item.option.usageOptionId,
            facilityId = item.option.facilityId,
            facilityName = item.option.facilityName,
            programName = item.option.programName,
            weekdays = item.option.weekdays,
            startTime = item.option.operatorTime?.startTime ?: item.option.sourceTimeValue,
            endTime = item.option.operatorTime?.endTime,
            priceWon = item.option.priceWon,
            distanceMeters = item.facts.straightLineDistanceMeters.value,
            applicationState = item.option.programApplication.state,
            facilityOperationState = item.option.facilityOperation.state,
            uncertaintyCodes = item.uncertaintyCodes,
        )
    }
}

internal data class AiModelRequest(
    val message: String,
    val recentTurns: List<AiConversationTurn>,
    val conditions: RecommendationConditions,
    val lastOptions: List<AiModelOption>,
    val originAvailable: Boolean,
    val references: AiReferences = AiReferences(),
    val searchDate: LocalDate? = null,
)

internal data class AiModelResult(
    val action: AiAction,
    val summary: String,
    val optionIds: List<String>,
    val uncertainties: List<String>,
    val budgetLevel: AiBudgetLevel = AiBudgetLevel.NORMAL,
    val comparisonDimension: AiComparisonDimension = AiComparisonDimension.NONE,
    val exerciseSport: ExerciseSport? = null,
    val conversationTitle: String? = null,
    val learnedMemory: AiMemoryFacts? = null,
    val conversationalReply: Boolean = false,
)

internal data class AiToolCall(
    val name: String,
    val argumentsJson: String,
)

internal data class AiToolExecution(
    val outputJson: String,
    val cards: List<RecommendationItem>,
    val conditions: RecommendationConditions,
    val dataset: DatasetReference?,
    val errorCode: String? = null,
    val kind: AiToolKind = AiToolKind.SEARCH,
    val comparison: ComparisonData? = null,
    val optionIds: List<String> = emptyList(),
    val schedulePolicy: AiSchedulePolicy? = null,
    val programDataAvailable: Boolean = true,
)

internal fun interface AiToolExecutor {
    fun execute(call: AiToolCall): AiToolExecution
}

/** The model a provider was built to call, without the key, endpoint or ledger it uses to call it. */
internal data class ConfiguredAiModel(val provider: String, val model: String)

internal fun interface AiModelProvider {
    fun respond(request: AiModelRequest, tools: AiToolExecutor): AiModelResult

    fun respondStreaming(
        request: AiModelRequest,
        tools: AiToolExecutor,
        onReply: (AiModelReply) -> Unit,
    ): AiModelResult = respond(request, tools)

    /** Null unless this provider calls a real model; GET /api/v1/meta/version reports it as server setup. */
    val configuredModel: ConfiguredAiModel? get() = null
}

/** A complete sentence prefix, never tool arguments or a partial JSON string. */
internal data class AiModelReply(val action: AiAction, val text: String)

internal class AiClientDisconnectedException(cause: Throwable) : RuntimeException(cause)

internal class AiProviderException(
    val reasonCode: String,
    cause: Throwable? = null,
) : RuntimeException(reasonCode, cause)
