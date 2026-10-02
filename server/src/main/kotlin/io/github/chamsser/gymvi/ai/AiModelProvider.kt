package io.github.chamsser.gymvi.ai

internal class UnavailableAiModelProvider(
    private val reasonCode: String = "AI_PROVIDER_UNAVAILABLE",
) : AiModelProvider {
    override fun respond(request: AiModelRequest, tools: AiToolExecutor): AiModelResult =
        throw AiProviderException(reasonCode)
}
