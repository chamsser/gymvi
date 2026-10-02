package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.catalog.ApiValidationException
import io.github.chamsser.gymvi.catalog.DatasetReference
import io.github.chamsser.gymvi.recommendation.RecommendationConditions
import io.github.chamsser.gymvi.recommendation.RecommendationItem
import io.github.chamsser.gymvi.recommendation.RecommendationQuery
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal data class AiConversationState(
    val id: String,
    var context: RecommendationQuery,
    var conditions: RecommendationConditions = RecommendationConditions(),
    var lastCards: List<RecommendationItem> = emptyList(),
    var lastDataset: DatasetReference? = null,
    var lastComparedCards: List<RecommendationItem> = emptyList(),
    var lastComparedDataset: DatasetReference? = null,
    var selectedOptionId: String? = null,
    var selectedCard: RecommendationItem? = null,
    var selectedDataset: DatasetReference? = null,
    var recentTurns: List<AiConversationTurn> = emptyList(),
    var schedulePolicy: AiSchedulePolicy? = null,
    var lastAction: AiAction? = null,
    var references: AiReferences? = null,
    var locationAvailable: Boolean? = null,
    var title: String? = null,
    var updatedAt: Instant,
)

internal class AiConversationStore(
    private val clock: Clock,
    private val ttl: Duration = DEFAULT_TTL,
    private val maxConversations: Int = 2_000,
) {
    private val states = linkedMapOf<String, AiConversationState>()

    init {
        require(!ttl.isNegative && !ttl.isZero)
        require(maxConversations > 0)
    }

    @Synchronized
    fun resolve(conversationId: String?, suppliedContext: RecommendationQuery?): AiConversationState {
        val now = clock.instant()
        removeExpired(now)
        val existing = conversationId?.let(states::get)
        if (existing != null) {
            // A new question restarts the retention, so a sweep while its turn runs keeps the conversation.
            existing.updatedAt = now
            return existing
        }
        if (conversationId != null) {
            throw ApiValidationException(
                "VALIDATION_AI_CONVERSATION_ID",
                "대화 ID가 만료됐거나 이 서버에서 발급되지 않았습니다.",
            )
        }
        val context = suppliedContext
            ?: throw ApiValidationException(
                "VALIDATION_AI_CONTEXT",
                "새 대화를 시작하려면 현재 지도 범위가 필요합니다.",
            )
        if (states.size >= maxConversations) {
            states.minByOrNull { it.value.updatedAt }?.key?.let(states::remove)
        }
        val id = conversationId ?: UUID.randomUUID().toString()
        return AiConversationState(
            id = id,
            context = context.withoutOrigin(),
            updatedAt = now,
        ).also { states[id] = it }
    }

    fun applySuppliedContext(state: AiConversationState, suppliedContext: RecommendationQuery?) {
        if (suppliedContext == null) return
        synchronized(state) {
            val nextContext = suppliedContext.withoutOrigin()
            if (state.context != nextContext) {
                state.lastCards = emptyList()
                state.lastDataset = null
                state.lastComparedCards = emptyList()
                state.lastComparedDataset = null
                state.selectedOptionId = null
                state.selectedCard = null
                state.selectedDataset = null
                state.schedulePolicy = null
            }
            state.context = nextContext
        }
    }

    @Synchronized
    fun touch(state: AiConversationState) {
        state.updatedAt = clock.instant()
        states[state.id] = state
    }

    /**
     * Drops every conversation idle for longer than the TTL and returns how many it dropped. A
     * request checks this first, and [AiConversationSweeper] calls it on a schedule so a
     * conversation nobody comes back to is dropped too.
     */
    @Synchronized
    fun removeExpired(): Int = removeExpired(clock.instant())

    @Synchronized
    internal fun size(): Int = states.size

    private fun removeExpired(now: Instant): Int {
        val before = states.size
        states.entries.removeIf { (_, state) -> state.updatedAt.plus(ttl).isBefore(now) }
        return before - states.size
    }

    private fun RecommendationQuery.withoutOrigin(): RecommendationQuery = copy(
        origin = null,
        conditions = RecommendationConditions(),
    )

    companion object {
        /** How long a conversation outlives its last turn; MY의 개인정보 이용 안내 states it as about 30 minutes. */
        val DEFAULT_TTL: Duration = Duration.ofMinutes(30)
    }
}
