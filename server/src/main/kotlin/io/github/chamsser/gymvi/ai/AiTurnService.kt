package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.api.ApiWarning
import io.github.chamsser.gymvi.catalog.DatasetReference
import io.github.chamsser.gymvi.catalog.GymviApiException
import io.github.chamsser.gymvi.discovery.ExerciseContentItem
import io.github.chamsser.gymvi.discovery.ExerciseContentService
import io.github.chamsser.gymvi.recommendation.ComparisonData
import io.github.chamsser.gymvi.recommendation.ComparisonQuery
import io.github.chamsser.gymvi.recommendation.PriceCondition
import io.github.chamsser.gymvi.recommendation.RecommendationConditions
import io.github.chamsser.gymvi.recommendation.RecommendationItem
import io.github.chamsser.gymvi.recommendation.RecommendationService
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper

internal class AiTurnService(
    private val recommendationService: RecommendationService,
    private val exerciseContentService: ExerciseContentService,
    private val modelProvider: AiModelProvider,
    private val conversationStore: AiConversationStore,
    private val toolRequestParser: AiToolRequestParser,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(AiTurnService::class.java)

    fun turn(input: AiTurnInput, onReplyDelta: ((String) -> Unit)? = null): AiTurnResult {
        val suppliedQuery = input.context?.recommendationQuery
        val transientOrigin = input.origin
        val state = conversationStore.resolve(input.conversationId, suppliedQuery)
        var emittedReply = ""
        var learnedMemory: AiMemoryFacts? = null
        return synchronized(state) {
            val previousReferences = state.references
            if (previousReferences != null &&
                (previousReferences != input.references || state.locationAvailable != (transientOrigin != null))
            ) {
                // Personalization defaults are applied per search, but the model can copy one into an
                // explicit condition. Clear conditions sharing a revoked reference value and replies
                // based on revoked data. Other explicit conditions and user turns stay.
                state.conditions = state.conditions.withoutRevoked(
                    state.conditions.referenceMatches(previousReferences),
                    input.references,
                )
                state.recentTurns = state.recentTurns.filter { it.role == "user" }
                state.lastCards = emptyList()
                state.lastComparedCards = emptyList()
                state.selectedCard = null
                state.selectedOptionId = null
                state.selectedDataset = null
                state.schedulePolicy = null
            }
            state.references = input.references
            state.locationAvailable = transientOrigin != null
            if (input.conversationId == null && input.continuation != null) {
                state.conditions = try { toolRequestParser.continuation(input.continuation, input.references) }
                    catch (_: AiProviderException) {
                        throw io.github.chamsser.gymvi.catalog.ApiValidationException("VALIDATION_AI_CONTEXT", "저장된 대화 조건을 읽을 수 없습니다.")
                    }
            }
            val turnStartConditions = state.conditions
            val priceMention = AiPriceMention.read(input.message)
            val result = run conversation@{
                conversationStore.applySuppliedContext(state, suppliedQuery)
                AiSafetyPrecheck.classify(input.message)?.let { boundary ->
                    return@conversation safetyBoundaryResult(state, boundary)
                }
                if (AiScopePrecheck.isGeneralKnowledgeRequest(input.message)) {
                    return@conversation outOfScopeResult(state, OUT_OF_SCOPE_REPLY)
                }
                if (AiRequestPrecheck.isForbiddenCapabilityRequest(input.message)) {
                    return@conversation outOfScopeResult(state, CAPABILITY_OUT_OF_SCOPE_REPLY)
                }
                AiRequestPrecheck.clarificationQuestion(
                    message = input.message,
                    currentConditions = state.conditions.withReferences(input.references),
                    previousOptionIds = (state.lastCards + listOfNotNull(state.selectedCard))
                        .map { it.option.usageOptionId }
                        .toSet(),
                )?.let { reply ->
                    return@conversation clarificationResult(input.message, state, reply)
                }
                val previousCards = state.lastCards
                val previousDataset = state.lastDataset
                val previousComparedCards = state.lastComparedCards
                val previousComparedDataset = state.lastComparedDataset
                val baseQuery = state.context.copy(origin = transientOrigin)
                val request = AiModelRequest(
                    message = input.message,
                    recentTurns = state.recentTurns,
                    conditions = state.conditions,
                    lastOptions = state.lastCards.map(AiModelOption::from),
                    originAvailable = transientOrigin != null,
                    references = input.references,
                )
                var searchExecution: AiToolExecution? = null
                var comparisonExecution: AiToolExecution? = null
                var failedExecution: AiToolExecution? = null
                var priceSearchRejected = false
                val modelResult = try {
                    val tools = AiToolExecutor { call ->
                        executeTool(call, baseQuery, state, searchExecution, input.message, priceMention).also { execution ->
                            // A search refused for its price unit changes nothing; the model may retry or ask.
                            val priceRejection = execution.errorCode in PRICE_UNIT_REJECTIONS
                            when (execution.kind) {
                                AiToolKind.SEARCH -> if (priceRejection) priceSearchRejected = true else searchExecution = execution
                                AiToolKind.COMPARE -> comparisonExecution = execution
                            }
                            if (execution.errorCode != null && execution.errorCode != AI_OPTION_ID_REJECTED && !priceRejection) {
                                failedExecution = execution
                            }
                        }
                    }
                    if (onReplyDelta == null) modelProvider.respond(request, tools)
                    else modelProvider.respondStreaming(request, tools) { prefix ->
                        val factCards = (searchExecution?.cards.orEmpty() + previousCards + previousComparedCards)
                        val text = normalizeReplyText(prefix.text)
                        val safe = when (prefix.action) {
                            AiAction.EXERCISE_GUIDE -> safeExerciseGuideText(text, factCards, false) == text
                            // Short introductions depend on the complete action and its facts.
                            // Do not show a prefix that final validation might replace.
                            AiAction.ASK_CLARIFYING_QUESTION, AiAction.SHOW_OPTIONS -> false
                            else -> false
                        }
                        if (safe && text.startsWith(emittedReply) && text.length > emittedReply.length) {
                            onReplyDelta(text.substring(emittedReply.length))
                            emittedReply = text
                        }
                    }
                } catch (exception: AiProviderException) {
                    log.info("ai_provider_fallback reason={}", exception.reasonCode)
                    return@conversation fallback(input.message, baseQuery, state, exception.reasonCode)
                }
                failedExecution?.errorCode?.let { dataReasonCode ->
                    return@conversation manualFallback(
                        userMessage = input.message,
                        state = state,
                        reasonCode = "AI_DATA_LOOKUP_FAILED",
                        dataReasonCode = dataReasonCode,
                    )
                }
                if (state.title == null && !modelResult.conversationalReply) {
                    state.title = modelResult.conversationTitle
                }
                // memory_facts.max_price_won has no unit, so a budget is learned only from this turn's search.
                val statedFacts = modelResult.learnedMemory?.takeUnless { modelResult.conversationalReply }
                    ?.copy(maxPriceWon = null)
                    ?.withoutReferenceCopies(turnStartConditions, input.references)
                val budget = learnedBudget(priceMention, searchExecution, turnStartConditions, input.references)
                learnedMemory = (statedFacts ?: AiMemoryFacts()).copy(maxPriceWon = budget).newlyStated(turnStartConditions)
                searchExecution?.let { execution ->
                    state.conditions = execution.conditions
                    state.lastCards = execution.cards
                    state.lastDataset = execution.dataset
                    state.lastComparedCards = emptyList()
                    state.lastComparedDataset = null
                    state.schedulePolicy = execution.schedulePolicy
                }
                val result = validatedResult(
                    input.message,
                    modelResult,
                    state,
                    previousCards,
                    previousDataset,
                    previousComparedCards,
                    previousComparedDataset,
                    searchExecution,
                    comparisonExecution,
                    priceSearchBlocked = searchExecution == null &&
                        (priceSearchRejected || priceMention == AiPriceMention.Unsupported),
                )
                if (searchExecution == null && result.data.action == AiAction.EXERCISE_GUIDE && !result.data.fallback) {
                    // This is transient chat context, independent of whether the client saves memory.
                    // Reference values the model copied into the metadata were removed above.
                    statedFacts?.let { state.conditions = state.conditions.withExplicitFacts(it) }
                }
                comparisonExecution?.takeIf {
                    result.data.action == AiAction.COMPARE_OPTIONS && it.errorCode == null && it.cards.size in 2..3
                }?.let { execution ->
                    state.lastComparedCards = execution.cards
                    state.lastComparedDataset = execution.dataset
                }
                conversationStore.touch(state)
                result
            }
            // Deterministic/precheck/fallback messages are already complete, so send once.
            // A rejected partial result is replaced by complete, never appended to a new reply.
            if (onReplyDelta != null && result.data.reply.startsWith(emittedReply)) {
                val remaining = result.data.reply.substring(emittedReply.length)
                if (remaining.isNotEmpty()) onReplyDelta(remaining)
            }
            if (result.data.action != AiAction.ASK_CLARIFYING_QUESTION) state.lastAction = result.data.action
            // Precheck, fallback and failure turns learn nothing: accumulated conditions are not memory.
            result.copy(data = result.data.copy(conversationTitle = state.title,
                continuation = state.conditions.continuationJson(objectMapper, input.references), memoryFacts = learnedMemory))
        }
    }

    private fun executeTool(
        call: AiToolCall,
        baseQuery: io.github.chamsser.gymvi.recommendation.RecommendationQuery,
        state: AiConversationState,
        currentSearch: AiToolExecution?,
        userMessage: String,
        priceMention: AiPriceMention,
    ): AiToolExecution = when (call.name) {
        SEARCH_TOOL -> executeSearch(call, baseQuery, state, userMessage, priceMention)
        COMPARE_TOOL -> executeComparison(call, baseQuery, state, currentSearch)
        else -> throw AiProviderException("AI_TOOL_NOT_ALLOWED")
    }

    private fun executeSearch(
        call: AiToolCall,
        baseQuery: io.github.chamsser.gymvi.recommendation.RecommendationQuery,
        state: AiConversationState,
        userMessage: String,
        priceMention: AiPriceMention,
    ): AiToolExecution {
        val schedulePolicy = AiRequestPrecheck.resolveSchedulePolicy(userMessage, state.schedulePolicy)
        val conditions = schedulePolicy?.applyTo(toolRequestParser.search(call.argumentsJson))
            ?: toolRequestParser.search(call.argumentsJson)
        priceUnitRejection(priceMention, conditions.maxPriceWon)?.let { (code, recovery) ->
            return AiToolExecution(
                outputJson = objectMapper.writeValueAsString(
                    mapOf("ordered_options" to emptyList<Any>(), "error_code" to code, "recovery" to recovery),
                ),
                cards = emptyList(),
                conditions = conditions,
                dataset = null,
                errorCode = code,
                kind = AiToolKind.SEARCH,
                schedulePolicy = schedulePolicy,
            )
        }
        return try {
            val result = recommendationService.recommend(
                baseQuery.copy(
                    conditions = conditions.withReferences(state.references ?: AiReferences()),
                    limit = SEARCH_LIMIT,
                    maxPerFacility = 2,
                ),
                requiredWeekdayCoverage = schedulePolicy?.requiredDays.orEmpty(),
                minimumWeekdayCount = schedulePolicy?.minimumDayCount,
            )
            val cards = result.data.recommendations
                .filter { schedulePolicy?.matchesWeekdayLabels(it.option.weekdays) != false }
                .take(SEARCH_LIMIT)
                .mapIndexed { index, item -> item.copy(rank = index + 1) }
            AiToolExecution(
                outputJson = objectMapper.writeValueAsString(
                    mapOf(
                        "ordered_options" to cards.map(AiModelOption::from),
                        "unavailable_reason_codes" to result.data.unavailableReasonCodes,
                        "program_dataset_version" to result.data.programDatasetVersion,
                        "program_as_of" to result.data.programAsOf,
                    ),
                ),
                cards = cards,
                conditions = conditions,
                dataset = result.facilityDataset,
                kind = AiToolKind.SEARCH,
                schedulePolicy = schedulePolicy,
            )
        } catch (exception: GymviApiException) {
            AiToolExecution(
                outputJson = objectMapper.writeValueAsString(
                    mapOf(
                        "ordered_options" to emptyList<Any>(),
                        "error_code" to exception.code,
                    ),
                ),
                cards = emptyList(),
                conditions = conditions,
                dataset = null,
                errorCode = exception.code,
                kind = AiToolKind.SEARCH,
                schedulePolicy = schedulePolicy,
            )
        }
    }

    /**
     * Refuses a search that would silently drop or change a budget unit the user's own words made clear.
     * An unsupported period or package is never searched, with or without the amount. A tolerance of one
     * won keeps 미만 phrasing valid.
     */
    private fun priceUnitRejection(mention: AiPriceMention, condition: PriceCondition?): Pair<String, String>? = when (mention) {
        AiPriceMention.Unsupported -> AI_PRICE_UNIT_UNSUPPORTED to
            "The user's budget covers a period or package the search cannot apply, such as several months, a year " +
            "or a multi-session pass. Do not search with or without it. Keep the current conditions and ask one " +
            "short question for a monthly or per-session amount."
        is AiPriceMention.Clear -> mention.unit?.takeIf { unit ->
            condition?.unit != unit || condition.value !in mention.amountWon - 1..mention.amountWon
        }?.let { unit ->
            AI_PRICE_UNIT_MISMATCH to "The user's message states ${mention.amountWon} won per ${unit.name.lowercase()}. " +
                "Call search_usage_options with max_price_won value ${mention.amountWon} and unit ${unit.name}, or ask the user."
        }
        else -> null
    }

    /**
     * A budget is learned only from this turn's successful search: the message states one amount and no
     * billing unit, the executed explicit condition is exactly that amount without a unit, the chat did not
     * already hold a budget with a unit or the same amount, and it is not an opted-in reference value.
     * Monthly and per-session budgets are not learned until memory can keep their unit.
     */
    private fun learnedBudget(
        mention: AiPriceMention,
        search: AiToolExecution?,
        turnStart: RecommendationConditions,
        references: AiReferences,
    ): Int? {
        val stated = (mention as? AiPriceMention.Clear)?.takeIf { it.unit == null } ?: return null
        val executed = search?.takeIf { it.errorCode == null }?.conditions?.maxPriceWon ?: return null
        if (executed.unit != null || executed.value != stated.amountWon) return null
        val held = turnStart.maxPriceWon
        if (held != null && (held.unit != null || held.value == executed.value)) return null
        if (RecommendationConditions().withReferences(references).maxPriceWon?.value == executed.value) return null
        return executed.value
    }

    private fun executeComparison(
        call: AiToolCall,
        baseQuery: io.github.chamsser.gymvi.recommendation.RecommendationQuery,
        state: AiConversationState,
        currentSearch: AiToolExecution?,
    ): AiToolExecution {
        val requestedIds = toolRequestParser.compare(call.argumentsJson)
        val candidates = (
            (currentSearch?.cards ?: state.lastCards) + state.lastComparedCards + listOfNotNull(state.selectedCard)
            )
            .distinctBy { it.option.usageOptionId }
        val candidateIds = candidates.map { it.option.usageOptionId }.toSet()
        val conditions = currentSearch?.conditions ?: state.conditions
        if (requestedIds.any { it !in candidateIds }) {
            return AiToolExecution(
                outputJson = objectMapper.writeValueAsString(
                    mapOf(
                        "ordered_option_ids" to emptyList<String>(),
                        "error_code" to AI_OPTION_ID_REJECTED,
                        "recovery" to "Ask the user to choose two options from ordered_previous_options.",
                    ),
                ),
                cards = emptyList(),
                conditions = conditions,
                dataset = null,
                errorCode = AI_OPTION_ID_REJECTED,
                kind = AiToolKind.COMPARE,
                schedulePolicy = currentSearch?.schedulePolicy ?: state.schedulePolicy,
            )
        }
        val requestedSet = requestedIds.toSet()
        val orderedCards = candidates.filter { it.option.usageOptionId in requestedSet }
        val orderedIds = orderedCards.map { it.option.usageOptionId }
        return try {
            val result = recommendationService.compare(
                ComparisonQuery(
                    usageOptionIds = orderedIds,
                    origin = baseQuery.origin,
                    date = baseQuery.date,
                    conditions = conditions,
                ),
            )
            AiToolExecution(
                outputJson = objectMapper.writeValueAsString(
                    mapOf(
                        "ordered_option_ids" to orderedIds,
                        "dimensions" to result.data.dimensions,
                        "unavailable_reason_codes" to result.data.unavailableReasonCodes,
                        "program_dataset_version" to result.data.programDatasetVersion,
                        "program_as_of" to result.data.programAsOf,
                    ),
                ),
                cards = orderedCards,
                conditions = conditions,
                dataset = result.facilityDataset,
                kind = AiToolKind.COMPARE,
                comparison = result.data,
                optionIds = orderedIds,
                schedulePolicy = currentSearch?.schedulePolicy ?: state.schedulePolicy,
            )
        } catch (exception: GymviApiException) {
            AiToolExecution(
                outputJson = objectMapper.writeValueAsString(
                    mapOf(
                        "ordered_option_ids" to emptyList<String>(),
                        "error_code" to exception.code,
                    ),
                ),
                cards = emptyList(),
                conditions = conditions,
                dataset = null,
                errorCode = exception.code,
                kind = AiToolKind.COMPARE,
                schedulePolicy = currentSearch?.schedulePolicy ?: state.schedulePolicy,
            )
        }
    }

    private fun validatedResult(
        userMessage: String,
        model: AiModelResult,
        state: AiConversationState,
        previousCards: List<RecommendationItem>,
        previousDataset: DatasetReference?,
        previousComparedCards: List<RecommendationItem>,
        previousComparedDataset: DatasetReference?,
        searchExecution: AiToolExecution?,
        comparisonExecution: AiToolExecution?,
        priceSearchBlocked: Boolean = false,
    ): AiTurnResult {
        val availableCards = searchExecution?.cards ?: state.lastCards
        val availableIds = availableCards.map { it.option.usageOptionId }.toSet()
        val selectionCards = (previousCards + previousComparedCards)
            .distinctBy { it.option.usageOptionId }
        val previousIds = selectionCards.map { it.option.usageOptionId }.toSet()
        val comparisonCards = comparisonExecution?.cards.orEmpty()
        val comparisonIds = comparisonExecution?.optionIds.orEmpty()
        var action = model.action
        // Earlier cards do not answer a budget the search refused, so ask instead of showing them.
        val priceUnitRejected = priceSearchBlocked && action in setOf(AiAction.SHOW_OPTIONS, AiAction.COMPARE_OPTIONS)
        if (priceUnitRejected) action = AiAction.ASK_CLARIFYING_QUESTION
        val showWithoutSearch = action == AiAction.SHOW_OPTIONS &&
            searchExecution == null && availableCards.isEmpty()
        if (showWithoutSearch) action = AiAction.ASK_CLARIFYING_QUESTION
        val compareWithoutTool = action == AiAction.COMPARE_OPTIONS && comparisonExecution == null
        val comparisonIdsInvalid = action == AiAction.COMPARE_OPTIONS && comparisonExecution != null &&
            (model.optionIds.size !in 2..3 || model.optionIds.toSet() != comparisonIds.toSet())
        val comparisonRejected = compareWithoutTool || comparisonIdsInvalid
        if (comparisonRejected) action = AiAction.ASK_CLARIFYING_QUESTION
        val serverOnlyActionRejected = action == AiAction.SAFETY_GUIDANCE
        if (serverOnlyActionRejected) action = AiAction.ASK_CLARIFYING_QUESTION
        val selectionRequested = action in OPTION_ACTIONS
        val selectedId = model.optionIds.singleOrNull()
        val selectionValid = !selectionRequested ||
            (selectedId != null && selectedId in previousIds)
        val selectionDeferred = selectionRequested && !selectionValid &&
            selectedId != null && searchExecution != null && selectedId in availableIds
        val selectionRejected = selectionRequested && !selectionValid && !selectionDeferred
        val allowedIds = when {
            selectionDeferred -> availableIds
            selectionRequested -> previousIds
            model.action == AiAction.COMPARE_OPTIONS -> comparisonIds.toSet()
            model.action == AiAction.SHOW_OPTIONS -> availableIds
            else -> emptySet()
        }
        val rejectedId = model.optionIds.any { it !in allowedIds }
        val optionOrderCorrected = action == AiAction.SHOW_OPTIONS &&
            model.optionIds.isNotEmpty() && model.optionIds != availableCards.map { it.option.usageOptionId }
        val comparisonOrderCorrected = action == AiAction.COMPARE_OPTIONS &&
            model.optionIds != comparisonIds
        val chosen = if (selectionValid && selectionRequested && selectedId != null) {
            selectionCards.firstOrNull { it.option.usageOptionId == selectedId }
        } else {
            null
        }
        if (selectionRejected || selectionDeferred) {
            action = if (searchExecution?.cards?.isNotEmpty() == true) {
                AiAction.SHOW_OPTIONS
            } else {
                AiAction.ASK_CLARIFYING_QUESTION
            }
        }
        if (action in OPTION_ACTIONS) {
            state.selectedOptionId = chosen?.option?.usageOptionId
            state.selectedCard = chosen
            state.selectedDataset = chosen?.option?.usageOptionId?.let { chosenId ->
                if (previousComparedCards.any { it.option.usageOptionId == chosenId }) {
                    previousComparedDataset ?: previousDataset
                } else {
                    previousDataset
                }
            }
        }
        val cards = when (action) {
            AiAction.SHOW_OPTIONS -> availableCards
            AiAction.COMPARE_OPTIONS -> comparisonCards
            AiAction.FOCUS_MAP, AiAction.SELECT_DESTINATION, AiAction.PREVIEW_ROUTE -> listOfNotNull(chosen)
            else -> emptyList()
        }
        val factCards = (availableCards + selectionCards + comparisonCards)
            .distinctBy { it.option.usageOptionId }
        val guideResult = if (action == AiAction.EXERCISE_GUIDE && model.exerciseSport != null) {
            exerciseContentService.find(model.exerciseSport.name, EXERCISE_CONTENT_LIMIT)
        } else {
            null
        }
        val exerciseContents = guideResult?.data?.contents.orEmpty()
        val modelTextAllowed = !selectionRejected && !selectionDeferred && !showWithoutSearch && !comparisonRejected &&
            !serverOnlyActionRejected && !priceUnitRejected
        val conversationalReply = model.conversationalReply && modelTextAllowed &&
            action == AiAction.ASK_CLARIFYING_QUESTION && searchExecution == null && comparisonExecution == null
        val reply = when (action) {
            AiAction.COMPARE_OPTIONS -> comparisonReply(
                comparisonExecution?.comparison,
                cards,
                model.comparisonDimension,
            )
            AiAction.EXERCISE_GUIDE -> safeExerciseGuideText(
                if (modelTextAllowed) model.summary else "",
                factCards,
                exerciseContents.isNotEmpty(),
            )
            else -> composeReply(
                action = action,
                cards = cards,
                modelSummary = if (modelTextAllowed) model.summary else "",
                factCards = factCards,
                capabilityQuestion = isCapabilityQuestion(userMessage),
                conversationalReply = conversationalReply,
                clarificationFallback = when {
                    selectionRejected -> "카드에서 프로그램 하나를 골라 주세요."
                    comparisonRejected -> "비교할 프로그램을 두 개 이상 알려주세요."
                    isCapabilityQuestion(userMessage) -> "운동 방법 안내, 공공 체육시설과 프로그램 찾기, 조건 비교와 경로 확인을 도와드릴 수 있어요."
                    conversationalReply -> "말씀을 조금 더 들려주시겠어요?"
                    else -> "원하는 운동, 요일 또는 시간을 알려주세요."
                },
            )
        }
        val clientActions = clientActions(action, cards)
        val allowedUncertainties = cards.flatMap(RecommendationItem::uncertaintyCodes).toSet()
        val uncertainties = model.uncertainties.filter { it in allowedUncertainties }.distinct()
        rememberTurn(
            state,
            if (action == AiAction.OUT_OF_SCOPE) OUT_OF_SCOPE_MEMORY_MARKER else userMessage,
            reply,
        )
        val warnings = buildList {
            if (rejectedId) {
                add(ApiWarning("AI_OPTION_ID_REJECTED", "서버 결과에 없는 선택지를 제외했습니다."))
            }
            if (selectionRejected) {
                add(ApiWarning("AI_SELECTION_REJECTED", "이전에 표시한 프로그램 하나를 지정해야 합니다."))
            }
            if (selectionDeferred) {
                add(ApiWarning("AI_SELECTION_DEFERRED", "새로 찾은 프로그램은 카드를 확인한 뒤 선택할 수 있습니다."))
            }
            if (comparisonExecution?.errorCode == AI_OPTION_ID_REJECTED) {
                add(ApiWarning(AI_OPTION_ID_REJECTED, "직전 카드에 없는 선택지는 비교에서 제외했습니다."))
            }
            if (optionOrderCorrected) {
                add(ApiWarning("AI_OPTION_ORDER_CORRECTED", "프로그램 순서를 서버 추천 순서로 맞췄습니다."))
            }
            if (comparisonOrderCorrected) {
                add(ApiWarning("AI_OPTION_ORDER_CORRECTED", "비교 순서를 서버 추천 순서로 맞췄습니다."))
            }
            if (showWithoutSearch) {
                add(ApiWarning("AI_SEARCH_REQUIRED", "프로그램을 표시하기 전에 서버 검색이 필요합니다."))
            }
            if (comparisonRejected) {
                add(ApiWarning("AI_COMPARISON_REJECTED", "이전에 표시한 프로그램 두 개 이상을 비교해야 합니다."))
            }
            if (serverOnlyActionRejected) {
                add(ApiWarning("AI_SERVER_ACTION_REJECTED", "서버 전용 안전 응답을 모델이 선택할 수 없습니다."))
            }
            if (priceUnitRejected) {
                add(ApiWarning(AI_PRICE_UNIT_REJECTED, "지원하지 않거나 맞지 않는 가격 단위로는 검색하지 않았습니다."))
            }
            when (model.budgetLevel) {
                AiBudgetLevel.WARNING_70 -> add(
                    ApiWarning("AI_BUDGET_WARNING_70", "AI 사용 예산이 경고 수준에 도달했습니다."),
                )
                AiBudgetLevel.RESTRICTED_90 -> add(
                    ApiWarning("AI_BUDGET_RESTRICTED_90", "AI 사용 예산에 따라 응답 범위를 줄였습니다."),
                )
                else -> Unit
            }
        }
        val fallbackReason = when {
            selectionRejected -> "AI_SELECTION_REJECTED"
            comparisonRejected -> "AI_COMPARISON_REJECTED"
            serverOnlyActionRejected -> "AI_SERVER_ACTION_REJECTED"
            priceUnitRejected -> AI_PRICE_UNIT_REJECTED
            rejectedId -> "AI_OPTION_ID_REJECTED"
            optionOrderCorrected || comparisonOrderCorrected -> "AI_OPTION_ORDER_CORRECTED"
            showWithoutSearch -> "AI_SEARCH_REQUIRED"
            else -> null
        }
        val dataset = when {
            action == AiAction.EXERCISE_GUIDE -> null
            selectionRequested && selectionValid -> state.selectedDataset
            action == AiAction.COMPARE_OPTIONS -> comparisonExecution?.dataset
            searchExecution != null -> searchExecution.dataset
            else -> state.lastDataset
        }
        return AiTurnResult(
            data = AiTurnData(
                conversationId = state.id,
                reply = reply,
                action = action,
                cards = cards,
                exerciseContents = exerciseContents,
                clientActions = clientActions,
                uncertainties = uncertainties,
                fallback = fallbackReason != null,
                fallbackReasonCode = fallbackReason,
            ),
            datasetVersion = guideResult?.datasetVersion ?: dataset?.version,
            asOf = if (guideResult == null) dataset?.asOf else null,
            warnings = warnings,
        )
    }

    private fun fallback(
        message: String,
        baseQuery: io.github.chamsser.gymvi.recommendation.RecommendationQuery,
        state: AiConversationState,
        reasonCode: String,
    ): AiTurnResult {
        if (AiFallbackPlanner.shouldOfferExercise(message, state)) {
            val reply = DEFAULT_EXERCISE_PROPOSAL
            rememberTurn(state, message, reply)
            conversationStore.touch(state)
            return fallbackResult(state, reply, AiAction.EXERCISE_GUIDE, emptyList(), reasonCode)
        }
        val requestedOptionId = AiFallbackPlanner.requestedOptionId(message, state)
        if (requestedOptionId != null) {
            val chosen = state.lastCards.firstOrNull { it.option.usageOptionId == requestedOptionId }
                ?: state.lastComparedCards.firstOrNull { it.option.usageOptionId == requestedOptionId }
                ?: state.selectedCard?.takeIf { it.option.usageOptionId == requestedOptionId }
            if (chosen != null) {
                val chosenWasSelected = state.selectedCard?.option?.usageOptionId == requestedOptionId
                val chosenWasCompared = state.lastComparedCards.any { it.option.usageOptionId == requestedOptionId }
                val dataset = when {
                    chosenWasSelected -> state.selectedDataset
                    chosenWasCompared -> state.lastComparedDataset
                    else -> state.lastDataset
                }
                val action = if (AiFallbackPlanner.isRouteRequest(message)) {
                    AiAction.PREVIEW_ROUTE
                } else {
                    AiAction.SELECT_DESTINATION
                }
                state.selectedOptionId = requestedOptionId
                state.selectedCard = chosen
                state.selectedDataset = dataset
                val reply = composeReply(action, listOf(chosen), "", listOf(chosen))
                rememberTurn(state, message, reply)
                conversationStore.touch(state)
                return fallbackResult(
                    state,
                    reply,
                    action,
                    listOf(chosen),
                    reasonCode,
                    dataset = dataset,
                )
            }
        }

        val schedulePolicy = AiRequestPrecheck.resolveSchedulePolicy(message, state.schedulePolicy)
        val plannedConditions = AiFallbackPlanner.conditions(message, state.conditions)
            ?: return manualFallback(message, state, reasonCode)
        val conditions = schedulePolicy?.applyTo(plannedConditions) ?: plannedConditions
        return try {
            val result = recommendationService.recommend(
                baseQuery.copy(
                    conditions = conditions,
                    limit = SEARCH_LIMIT,
                    maxPerFacility = 2,
                ),
                requiredWeekdayCoverage = schedulePolicy?.requiredDays.orEmpty(),
                minimumWeekdayCount = schedulePolicy?.minimumDayCount,
            )
            state.conditions = conditions
            state.schedulePolicy = schedulePolicy
            state.lastCards = result.data.recommendations
                .filter { schedulePolicy?.matchesWeekdayLabels(it.option.weekdays) != false }
                .take(SEARCH_LIMIT)
                .mapIndexed { index, item -> item.copy(rank = index + 1) }
            state.lastDataset = result.facilityDataset
            state.lastComparedCards = emptyList()
            state.lastComparedDataset = null
            val reply = composeReply(AiAction.SHOW_OPTIONS, state.lastCards, "", state.lastCards)
            rememberTurn(state, message, reply)
            conversationStore.touch(state)
            fallbackResult(
                state = state,
                reply = reply,
                action = AiAction.SHOW_OPTIONS,
                cards = state.lastCards,
                reasonCode = reasonCode,
                dataset = result.facilityDataset,
            )
        } catch (exception: GymviApiException) {
            log.info("ai_rule_fallback_failed code={}", exception.code)
            manualFallback(message, state, reasonCode, exception.code)
        }
    }

    private fun manualFallback(
        userMessage: String,
        state: AiConversationState,
        reasonCode: String,
        dataReasonCode: String? = null,
    ): AiTurnResult {
        val reply = if (dataReasonCode == null) {
            "AI 연결을 사용할 수 없어요. 지도 검색과 필터로 프로그램을 찾아보세요."
        } else {
            "추천 데이터를 불러오지 못했어요. 지도 검색에서 다시 시도해 주세요."
        }
        rememberTurn(state, userMessage, reply)
        conversationStore.touch(state)
        return fallbackResult(
            state = state,
            reply = reply,
            action = AiAction.MANUAL_FILTERS,
            cards = emptyList(),
            reasonCode = reasonCode,
            extraWarning = dataReasonCode?.let { ApiWarning(it, "추천 데이터 조회를 완료하지 못했습니다.") },
        )
    }

    private fun fallbackResult(
        state: AiConversationState,
        reply: String,
        action: AiAction,
        cards: List<RecommendationItem>,
        reasonCode: String,
        dataset: DatasetReference? = null,
        extraWarning: ApiWarning? = null,
    ): AiTurnResult = AiTurnResult(
        data = AiTurnData(
            conversationId = state.id,
            reply = reply,
            action = action,
            cards = cards,
            exerciseContents = emptyList(),
            clientActions = clientActions(action, cards),
            uncertainties = cards.flatMap(RecommendationItem::uncertaintyCodes).distinct(),
            fallback = true,
            fallbackReasonCode = reasonCode,
        ),
        datasetVersion = dataset?.version,
        asOf = dataset?.asOf,
        warnings = buildList {
            add(ApiWarning(reasonCode, "AI 응답 대신 확인 가능한 대체 경로를 사용했습니다."))
            extraWarning?.let(::add)
        },
    )

    private fun composeReply(
        action: AiAction,
        cards: List<RecommendationItem>,
        modelSummary: String,
        factCards: List<RecommendationItem>,
        clarificationFallback: String = "원하는 운동, 요일 또는 시간을 알려주세요.",
        capabilityQuestion: Boolean = false,
        conversationalReply: Boolean = false,
    ): String = when (action) {
        AiAction.SHOW_OPTIONS -> if (cards.isEmpty()) {
            "조건에 맞는 프로그램을 찾지 못했어요. 조건을 바꿔 다시 찾아보세요."
        } else {
            safeOptionIntroduction(modelSummary, factCards, "조건에 맞는 프로그램 ${cards.size}개를 찾았어요.")
        }
        AiAction.COMPARE_OPTIONS -> "프로그램을 비교 화면에서 확인해 보세요."
        AiAction.EXERCISE_GUIDE -> safeExerciseGuideText(modelSummary, factCards, false)
        AiAction.SAFETY_GUIDANCE -> "건강과 안전에 관해 확인 가능한 범위만 안내해요."
        AiAction.FOCUS_MAP -> "선택한 시설을 지도에서 표시할게요."
        AiAction.SELECT_DESTINATION -> "선택한 프로그램을 목적지로 이어갈게요."
        AiAction.PREVIEW_ROUTE -> "선택한 시설의 경로 미리보기를 열게요."
        AiAction.ASK_CLARIFYING_QUESTION -> safeClarifyingQuestion(
            modelSummary,
            clarificationFallback,
            factCards,
            capabilityQuestion,
            conversationalReply,
        )
        // Model text is never shown for out-of-scope turns, so a general-knowledge answer
        // wrapped in an exercise context cannot reach the user.
        AiAction.OUT_OF_SCOPE -> OUT_OF_SCOPE_REPLY
        AiAction.MANUAL_FILTERS -> "지도 검색과 필터로 프로그램을 찾아보세요."
    }

    private fun safeOptionIntroduction(value: String, factCards: List<RecommendationItem>, fallback: String): String {
        val text = safeModelText(value, "", factCards)
        if (text.isEmpty() || GUIDE_UNVERIFIED_FACT_PATTERN.containsMatchIn(text) ||
            OPTION_INTRODUCTION_FACT_PATTERN.containsMatchIn(text) ||
            AiScopePrecheck.containsGeneralKnowledgeAnswer(text) ||
            MEDICAL_CLAIM_TERMS.any(text::contains) || UNSAFE_GUIDE_TERMS.any(text::contains)
        ) return fallback
        return text
    }

    private fun comparisonReply(
        comparison: ComparisonData?,
        cards: List<RecommendationItem>,
        focus: AiComparisonDimension,
    ): String {
        if (comparison == null || cards.size !in 2..3) {
            return "비교 정보를 확인하지 못했어요. 비교할 프로그램을 다시 골라 주세요."
        }
        val intro = "프로그램 ${cards.size}개를 비교했어요."
        if (focus == AiComparisonDimension.NONE) {
            return "$intro 비교 화면에서 항목별 차이를 확인해 보세요."
        }
        if (focus == AiComparisonDimension.OVERALL_RANK) {
            return "$intro 현재 서버 추천 순위에서는 첫 번째 프로그램이 가장 높아요."
        }
        val dimension = comparison.dimensions.firstOrNull { it.dimension == focus.name }
            ?: return "$intro 요청한 항목은 확인된 값이 없어 비교하지 못했어요."
        if (dimension.bestUsageOptionIds.isEmpty()) {
            // Known values without a comparable best, such as prices in different units, stay on the comparison screen.
            if (dimension.values.any { it.state == "KNOWN" }) return "$intro 비교 화면에서 항목별 차이를 확인해 보세요."
            return "$intro 요청한 항목은 확인된 값이 없어 비교하지 못했어요."
        }
        val labelsById = cards.associate { item ->
            item.option.usageOptionId to safeComparisonLabel(item)
        }
        val labels = dimension.bestUsageOptionIds.mapNotNull(labelsById::get)
        if (labels.isEmpty()) return "$intro 비교 화면에서 항목별 차이를 확인해 보세요."
        val label = labels.joinToString(", ")
        val result = when (focus) {
            AiComparisonDimension.PRICE_WON -> "확인된 가격은 $label 쪽이 가장 낮아요."
            AiComparisonDimension.STRAIGHT_LINE_DISTANCE_METERS -> "확인된 직선거리는 $label 쪽이 가장 짧아요."
            AiComparisonDimension.START_TIME -> "확인된 시작 시각은 $label 쪽이 가장 빨라요."
            AiComparisonDimension.NONE,
            AiComparisonDimension.OVERALL_RANK,
            -> "비교 화면에서 항목별 차이를 확인해 보세요."
        }
        return "$intro $result"
    }

    private fun safeComparisonLabel(item: RecommendationItem): String =
        "${item.option.facilityName} ${item.option.programName}"
            .replace(Regex("[\\r\\n\\p{Cc}]+"), " ")
            .trim()
            .take(MAX_COMPARISON_LABEL_LENGTH)

    private fun safeExerciseGuideText(
        value: String,
        factCards: List<RecommendationItem>,
        hasOfficialContent: Boolean,
    ): String {
        val fallback = if (hasOfficialContent) {
            "처음에는 무리하지 않는 범위에서 천천히 시작하고, 통증이나 어지러움이 생기면 중단하세요. " +
                "확인된 공식 운동 콘텐츠를 함께 살펴보세요."
        } else {
            DEFAULT_EXERCISE_PROPOSAL
        }
        val normalized = normalizeReplyText(value)
        val factNames = factCards.flatMap { listOf(it.option.facilityName, it.option.programName) }
        if (normalized.isEmpty() || normalized.length > MAX_GUIDE_LENGTH ||
            normalized.contains("http://", ignoreCase = true) ||
            normalized.contains("https://", ignoreCase = true) ||
            normalized.contains("```") || normalized.any { it == '\u0000' } ||
            GUIDE_FACT_PATTERN.containsMatchIn(normalized) ||
            GUIDE_UNVERIFIED_FACT_PATTERN.containsMatchIn(normalized) ||
            MEDICAL_CLAIM_TERMS.any { normalized.contains(it, ignoreCase = true) } ||
            UNSAFE_GUIDE_TERMS.any { normalized.contains(it, ignoreCase = true) } ||
            AiScopePrecheck.containsGeneralKnowledgeAnswer(normalized) ||
            FACT_STATE_TERMS.any { normalized.contains(it, ignoreCase = true) } ||
            factNames.any { it.length >= 2 && normalized.contains(it, ignoreCase = true) }
        ) return fallback
        return normalized
    }

    private fun outOfScopeResult(
        state: AiConversationState,
        reply: String,
    ): AiTurnResult {
        rememberTurn(state, OUT_OF_SCOPE_MEMORY_MARKER, reply)
        conversationStore.touch(state)
        return AiTurnResult(
            data = AiTurnData(
                conversationId = state.id,
                reply = reply,
                action = AiAction.OUT_OF_SCOPE,
                cards = emptyList(),
                exerciseContents = emptyList(),
                clientActions = emptyList(),
                uncertainties = emptyList(),
                fallback = false,
            ),
        )
    }

    private fun clarificationResult(
        userMessage: String,
        state: AiConversationState,
        reply: String,
    ): AiTurnResult {
        rememberTurn(state, userMessage, reply)
        conversationStore.touch(state)
        return AiTurnResult(
            data = AiTurnData(
                conversationId = state.id,
                reply = reply,
                action = AiAction.ASK_CLARIFYING_QUESTION,
                cards = emptyList(),
                exerciseContents = emptyList(),
                clientActions = emptyList(),
                uncertainties = emptyList(),
                fallback = false,
            ),
        )
    }

    private fun safetyBoundaryResult(
        state: AiConversationState,
        boundary: AiSafetyBoundary,
    ): AiTurnResult {
        rememberTurn(state, SAFETY_MEMORY_MARKER, boundary.reply)
        conversationStore.touch(state)
        return AiTurnResult(
            data = AiTurnData(
                conversationId = state.id,
                reply = boundary.reply,
                action = AiAction.SAFETY_GUIDANCE,
                cards = emptyList(),
                exerciseContents = emptyList(),
                clientActions = emptyList(),
                uncertainties = emptyList(),
                fallback = false,
            ),
        )
    }

    /** Validate facts first; brief conversation does not need exercise vocabulary or a final question. */
    private fun safeClarifyingQuestion(
        value: String,
        fallback: String,
        factCards: List<RecommendationItem>,
        capabilityQuestion: Boolean = false,
        conversationalReply: Boolean = false,
    ): String {
        val text = safeModelText(value, fallback, factCards)
        val factsText = if (capabilityQuestion) text.replace(
            Regex("(?<![가-힣A-Za-z0-9])(?:공공|주변|근처)?\\s*(?:수영장|체육관)(?=[ ,와과을를이가의/]|$)"), "시설",
        ) else text
        if (text == fallback || text.length > MAX_CLARIFYING_QUESTION_LENGTH ||
            text.count { it == '?' } > 1 ||
            AiScopePrecheck.containsGeneralKnowledgeAnswer(text) ||
            GUIDE_UNVERIFIED_FACT_PATTERN.containsMatchIn(factsText) ||
            MEDICAL_CLAIM_TERMS.any(text::contains) || UNSAFE_GUIDE_TERMS.any(text::contains)
        ) return fallback
        if (conversationalReply) {
            val sentences = text.replace(Regex("\\.{2,}|…+"), "").split(Regex("[.!?\\n]+"))
                .map(String::trim).filter(String::isNotEmpty)
            return text.takeIf { it.length <= 240 && sentences.size <= 2 } ?: fallback
        }
        if (capabilityQuestion) return text
        val contextParts = text.split(Regex("[.!?\\n]+"))
            .map(String::trim).filter(String::isNotEmpty).dropLast(1)
        if (contextParts.any { !CLARIFYING_CONTEXT_PATTERN.containsMatchIn(it) }) return fallback
        val declarativeClauses = text.split(',').dropLast(1).filter {
            DECLARATIVE_ENDING.containsMatchIn(it.trim())
        }
        if (declarativeClauses.any { !CLARIFYING_CONTEXT_PATTERN.containsMatchIn(it) }) return fallback
        return text
    }

    private fun isCapabilityQuestion(message: String): Boolean {
        val compact = message.lowercase().replace(Regex("\\s+"), "").trimEnd('?', '!', '.')
        return compact in setOf("뭐할수있어", "무엇을할수있어", "뭘할수있어", "뭐할수있나요", "무엇을도와줄수있어",
            "무슨기능이있어", "어떤기능이있어", "기능알려줘", "너뭐할수있어", "넌뭐할수있어")
    }

    private fun safeModelText(
        value: String,
        fallback: String,
        factCards: List<RecommendationItem>,
    ): String {
        val normalized = normalizeReplyText(value)
        val factNames = factCards.flatMap { listOf(it.option.facilityName, it.option.programName) }
        if (normalized.isEmpty() || normalized.length > 500 ||
            normalized.contains("http://", ignoreCase = true) ||
            normalized.contains("https://", ignoreCase = true) ||
            normalized.contains("```") || normalized.any { it == '\u0000' } ||
            FACT_VALUE_PATTERN.containsMatchIn(normalized) ||
            FACT_STATE_TERMS.any { normalized.contains(it, ignoreCase = true) } ||
            factNames.any { it.length >= 2 && normalized.contains(it, ignoreCase = true) }
        ) return fallback
        return normalized
    }

    private fun normalizeReplyText(value: String): String = value.replace('\r', ' ')
        .replace(DISALLOWED_REPLY_SEPARATOR, ", ")
        .trim()

    private fun clientActions(action: AiAction, cards: List<RecommendationItem>): List<AiClientActionResponse> = when (action) {
        AiAction.FOCUS_MAP -> cards.firstOrNull()?.let {
            listOf(AiClientActionResponse("FOCUS_MAP", it.option.usageOptionId, it.option.facilityId))
        }.orEmpty()
        AiAction.SELECT_DESTINATION -> cards.firstOrNull()?.let {
            listOf(AiClientActionResponse("SELECT_USAGE_OPTION", it.option.usageOptionId, it.option.facilityId))
        }.orEmpty()
        AiAction.PREVIEW_ROUTE -> cards.firstOrNull()?.let {
            listOf(
                AiClientActionResponse("SELECT_USAGE_OPTION", it.option.usageOptionId, it.option.facilityId),
                AiClientActionResponse("PREVIEW_ROUTE", it.option.usageOptionId, it.option.facilityId),
            )
        }.orEmpty()
        AiAction.COMPARE_OPTIONS -> cards.map { it.option.usageOptionId }.takeIf { it.size in 2..3 }?.let { ids ->
            listOf(AiClientActionResponse("OPEN_USAGE_OPTION_COMPARISON", optionIds = ids))
        }.orEmpty()
        AiAction.MANUAL_FILTERS -> listOf(AiClientActionResponse("OPEN_MANUAL_FILTERS"))
        else -> emptyList()
    }

    private fun rememberTurn(state: AiConversationState, userMessage: String, reply: String) {
        state.recentTurns = (
            state.recentTurns +
                AiConversationTurn("user", userMessage) +
                AiConversationTurn("assistant", reply)
            ).takeLast(MAX_RECENT_TURNS)
    }

    private companion object {
        const val SEARCH_TOOL = "search_usage_options"
        const val DEFAULT_EXERCISE_PROPOSAL =
            "가볍게 시작하려면 15분 걷기를 제안해요.\n\n" +
                "처음 3분은 천천히 몸을 풀고, 다음 10분은 대화할 수 있는 편한 속도로 걸어 보세요. " +
                "마지막 2분은 속도를 낮추며 마무리하면 돼요. 통증이나 어지러움이 생기면 멈추세요.\n\n" +
                "실내에서 할 운동을 원하시나요, 밖에서 움직이는 편이 좋으신가요?"
        const val COMPARE_TOOL = "compare_usage_options"
        const val MAX_RECENT_TURNS = 6
        const val EXERCISE_CONTENT_LIMIT = 2
        const val MAX_GUIDE_LENGTH = 1_800
        const val MAX_CLARIFYING_QUESTION_LENGTH = 500
        val CLARIFYING_CONTEXT_PATTERN = Regex(
            "^(안녕|반가|좋아요|알겠|네$)|" +
                "운동|체육|프로그램|경로|지도|수영|걷기|달리기|스트레칭|요가|필라테스|근력|헬스|배드민턴|테니스|골프|자전거|클라이밍",
        )
        val DECLARATIVE_ENDING = Regex("(?:에요|예요|니다|어요|아요|해요|래요|죠|이다|였다|했다)$")
        const val SEARCH_LIMIT = 5
        const val AI_OPTION_ID_REJECTED = "AI_OPTION_ID_REJECTED"
        const val AI_PRICE_UNIT_UNSUPPORTED = "AI_PRICE_UNIT_UNSUPPORTED"
        const val AI_PRICE_UNIT_MISMATCH = "AI_PRICE_UNIT_MISMATCH"
        const val AI_PRICE_UNIT_REJECTED = "AI_PRICE_UNIT_REJECTED"
        val PRICE_UNIT_REJECTIONS = setOf(AI_PRICE_UNIT_UNSUPPORTED, AI_PRICE_UNIT_MISMATCH)
        const val OUT_OF_SCOPE_REPLY = "그 질문에는 답하기 어려워요. 운동 제안과 프로그램 찾기는 도와드릴 수 있어요."
        const val CAPABILITY_OUT_OF_SCOPE_REPLY =
            "파일이나 앱 데이터에는 접근할 수 없어요. 운동 제안과 프로그램 찾기는 도와드릴 수 있어요."
        const val OUT_OF_SCOPE_MEMORY_MARKER = "[범위 밖 요청 차단됨]"
        const val SAFETY_MEMORY_MARKER = "[건강 또는 안전 요청 차단됨]"
        val DISALLOWED_REPLY_SEPARATOR = Regex("[ \\t]*[\\u00B7\\u2027\\u30FB\\u2014\\u2015][ \\t]*")
        const val MAX_COMPARISON_LABEL_LENGTH = 120
        val OPTION_ACTIONS = setOf(
            AiAction.FOCUS_MAP,
            AiAction.SELECT_DESTINATION,
            AiAction.PREVIEW_ROUTE,
        )
        val FACT_VALUE_PATTERN = Regex("(?:\\d[\\d,]*\\s*원|(?:[01]?\\d|2[0-3]):[0-5]\\d|\\d{1,2}\\s*시)")
        val GUIDE_FACT_PATTERN = Regex("(?:\\d[\\d,]*\\s*원|(?:[01]?\\d|2[0-3]):[0-5]\\d)")
        val GUIDE_UNVERIFIED_FACT_PATTERN = Regex(
            "(?:\\d+(?:\\.\\d+)?\\s*(?:m|km|미터|킬로미터)|무료|" +
                "[가-힣A-Za-z0-9]{2,}(?:센터|수영장|체육관|구민회관|시민회관))",
            RegexOption.IGNORE_CASE,
        )
        // Introductions may explain the next choice, but cannot create counts, ranks or
        // factual advantages before the server-owned cards and comparison are validated.
        val OPTION_INTRODUCTION_FACT_PATTERN = Regex("[0-9]|가장|최저|최고|저렴|가까운|신청|운영|영업|무료")
        val FACT_STATE_TERMS = listOf(
            "신청 가능",
            "신청 마감",
            "영업 중",
            "운영 중",
            "잔여",
            "남은 자리",
            "마감됐",
        )
        val MEDICAL_CLAIM_TERMS = listOf(
            "안전합니다",
            "안전해요",
            "효과가 보장",
            "치료됩니다",
            "치료돼요",
            "완치",
            "진단",
            "처방",
        )
        val UNSAFE_GUIDE_TERMS = listOf(
            "통증을 참고",
            "통증을 무시",
            "어지러워도",
            "흉통이 있어도",
            "호흡곤란이 있어도",
            "물 안 마시",
            "탈수",
            "술 마시고",
            "음주 후",
            "부상 중",
        )
    }
}
