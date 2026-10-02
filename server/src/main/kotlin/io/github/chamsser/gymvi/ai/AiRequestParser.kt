package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.catalog.ApiValidationException
import io.github.chamsser.gymvi.recommendation.Origin
import io.github.chamsser.gymvi.recommendation.RecommendationRequestParser

object AiRequestParser {
    private val conversationIdPattern = Regex("^[A-Za-z0-9_-]{16,80}$")

    fun turn(body: Map<String, Any?>): AiTurnInput {
        keys(body, setOf("conversation_id", "message", "context", "origin", "references", "continuation"))
        val conversationId = body.string("conversation_id")?.also { value ->
            if (!conversationIdPattern.matches(value)) fail("VALIDATION_AI_CONVERSATION_ID")
        }
        val message = body.string("message")?.trim()
            ?: fail("VALIDATION_AI_MESSAGE")
        if (message.isEmpty() || message.length > MAX_MESSAGE_LENGTH || message.any { it == '\u0000' }) {
            fail("VALIDATION_AI_MESSAGE")
        }
        val context = body.obj("context")?.let(::context)
        val origin = body.obj("origin")?.let(::origin)
        val references = body.obj("references")?.let(AiReferenceParser::parse) ?: AiReferences()
        val continuation = body.string("continuation")?.also { if (it.length > 8000) fail("VALIDATION_AI_CONTEXT") }
        return AiTurnInput(conversationId, message, context, origin, references, continuation)
    }

    private fun context(node: Map<String, Any?>): AiTurnContext {
        keys(node, setOf("area", "date", "preferences"))
        val recommendationBody = node.toMutableMap().apply {
            put("limit", 5)
            put("max_per_facility", 2)
        }
        return AiTurnContext(RecommendationRequestParser.recommendation(recommendationBody))
    }

    private fun origin(node: Map<String, Any?>): Origin {
        keys(node, setOf("latitude", "longitude"))
        val latitude = node.decimal("latitude") ?: fail("VALIDATION_AI_ORIGIN")
        val longitude = node.decimal("longitude") ?: fail("VALIDATION_AI_ORIGIN")
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in 33.0..39.5 || longitude !in 124.0..132.0
        ) {
            fail("VALIDATION_AI_ORIGIN")
        }
        return Origin(latitude, longitude)
    }

    private fun Map<String, Any?>.string(name: String): String? {
        if (!containsKey(name)) return null
        return this[name] as? String ?: fail("VALIDATION_AI_PARAMETER")
    }

    private fun Map<String, Any?>.obj(name: String): Map<String, Any?>? {
        if (!containsKey(name)) return null
        val raw = this[name] as? Map<*, *> ?: fail("VALIDATION_AI_PARAMETER")
        if (raw.keys.any { it !is String }) fail("VALIDATION_AI_PARAMETER")
        @Suppress("UNCHECKED_CAST")
        return raw as Map<String, Any?>
    }

    private fun Map<String, Any?>.decimal(name: String): Double? {
        if (!containsKey(name)) return null
        return (this[name] as? Number)?.toDouble() ?: fail("VALIDATION_AI_PARAMETER")
    }

    private fun keys(node: Map<String, Any?>, allowed: Set<String>) {
        if (node.keys.any { it !in allowed }) fail("VALIDATION_AI_PARAMETER")
    }

    private fun fail(code: String): Nothing =
        throw ApiValidationException(code, "AI 요청 형식이 올바르지 않습니다.")

    private const val MAX_MESSAGE_LENGTH = 1_000
}
