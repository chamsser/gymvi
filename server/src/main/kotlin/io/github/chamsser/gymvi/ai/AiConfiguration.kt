package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.catalog.FacilityCatalog
import io.github.chamsser.gymvi.discovery.ExerciseContentService
import io.github.chamsser.gymvi.recommendation.RecommendationService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

@Configuration(proxyBeanMethods = false)
internal class AiConfiguration {
    @Bean
    fun aiBudgetGuard(
        clock: Clock,
        objectMapper: ObjectMapper,
        @Value("\${gymvi.ai.budget.daily-usd:10}") dailyUsd: String,
        @Value("\${gymvi.ai.budget.total-usd:30}") totalUsd: String,
        @Value("\${gymvi.ai.budget.input-usd-per-million-tokens:0.10}") inputPrice: String,
        @Value("\${gymvi.ai.budget.output-usd-per-million-tokens:0.50}") outputPrice: String,
        @Value("\${gymvi.ai.budget.ledger-path:}") ledgerPath: String,
    ): AiBudgetGuard = AiBudgetGuard.fromDollarLimits(
        dailyLimit = dailyUsd,
        totalLimit = totalUsd,
        clock = clock,
        inputUsdPerMillionTokens = inputPrice,
        outputUsdPerMillionTokens = outputPrice,
        ledger = ledgerPath.trim().takeIf(String::isNotEmpty)?.let { path ->
            FileAiBudgetLedger(Path.of(path), objectMapper)
        },
    )

    @Bean
    fun aiConversationStore(clock: Clock): AiConversationStore = AiConversationStore(clock)

    @Bean
    fun aiConversationSweeper(store: AiConversationStore): AiConversationSweeper =
        AiConversationSweeper(store::removeExpired)

    @Bean
    fun aiToolRequestParser(objectMapper: ObjectMapper): AiToolRequestParser = AiToolRequestParser(objectMapper)

    @Bean
    fun aiModelProvider(
        objectMapper: ObjectMapper,
        budget: AiBudgetGuard,
        @Value("\${gymvi.ai.enabled:false}") enabled: Boolean,
        @Value("\${gymvi.ai.model:gpt-6-luna}") model: String,
        @Value("\${gymvi.ai.openai.api-key:}") apiKey: String,
        @Value("\${gymvi.ai.openai.endpoint:https://api.openai.com/v1/responses}") endpoint: String,
        @Value("\${gymvi.ai.budget.ledger-path:}") ledgerPath: String,
    ): AiModelProvider {
        if (!enabled || apiKey.isBlank()) return UnavailableAiModelProvider()
        if (ledgerPath.isBlank()) return UnavailableAiModelProvider("AI_BUDGET_LEDGER_REQUIRED")
        val transport = JdkOpenAiTransport(
            apiKey = apiKey,
            endpoint = endpoint,
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(),
        )
        return OpenAiResponsesProvider(model, objectMapper, transport, budget)
    }

    @Bean
    fun aiTurnService(
        recommendationService: RecommendationService,
        exerciseContentService: ExerciseContentService,
        modelProvider: AiModelProvider,
        conversationStore: AiConversationStore,
        toolRequestParser: AiToolRequestParser,
        objectMapper: ObjectMapper,
        facilityCatalog: FacilityCatalog,
        clock: Clock,
    ): AiTurnService = AiTurnService(
        recommendationService,
        exerciseContentService,
        modelProvider,
        conversationStore,
        toolRequestParser,
        objectMapper,
        AiFacilityFallback(facilityCatalog),
        clock,
    )
}
