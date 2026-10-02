package io.github.chamsser.gymvi.ai

import tools.jackson.databind.ObjectMapper

/**
 * Reads only the leading action and summary fields of the strict result schema.
 * No regex is used to decode JSON escapes. Incomplete escapes stay buffered and
 * only complete sentences are offered to the service's existing content guards.
 */
internal class AiSummaryStreamDecoder(
    private val mapper: ObjectMapper,
    private val onReply: (AiModelReply) -> Unit,
) {
    private val json = StringBuilder()
    private var emittedLength = 0

    fun append(delta: String) {
        json.append(delta)
        if (json.length > 24_000) throw AiProviderException("AI_PROVIDER_RESPONSE_TOO_LARGE")
        val prefix = PREFIX.find(json) ?: return
        val action = AiAction.entries.firstOrNull { it.name == prefix.groupValues[1] } ?: return
        val start = prefix.range.last + 1
        var cursor = start
        var safeEnd = start
        var closed = false
        while (cursor < json.length) {
            val character = json[cursor]
            if (character == '"') { closed = true; break }
            if (character == '\\') {
                if (cursor + 1 >= json.length) break
                val length = if (json[cursor + 1] == 'u') 6 else 2
                if (cursor + length > json.length) break
                cursor += length
            } else {
                cursor += 1
            }
            safeEnd = cursor
        }
        val encoded = "\"" + json.substring(start, safeEnd) + "\""
        val decoded = runCatching { mapper.readTree(encoded).asText() }.getOrElse {
            throw AiProviderException("AI_PROVIDER_SCHEMA_INVALID", it)
        }
        val end = if (closed) decoded.length else sentenceEnd(decoded)
        if (end > emittedLength) {
            emittedLength = end
            onReply(AiModelReply(action, decoded.substring(0, end)))
        }
    }

    private fun sentenceEnd(text: String): Int = text.indices.lastOrNull { index ->
        text[index] in ".!?\n" &&
            (text[index] != '.' || index == 0 || !text[index - 1].isDigit())
    }?.plus(1) ?: 0

    private companion object {
        val PREFIX = Regex("^\\s*\\{\\s*\"action\"\\s*:\\s*\"([A-Z_]+)\"\\s*,\\s*\"summary\"\\s*:\\s*\"")
    }
}
