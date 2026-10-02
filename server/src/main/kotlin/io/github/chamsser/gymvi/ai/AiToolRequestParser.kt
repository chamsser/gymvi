package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.recommendation.Category
import io.github.chamsser.gymvi.recommendation.FlagCondition
import io.github.chamsser.gymvi.recommendation.NumberCondition
import io.github.chamsser.gymvi.recommendation.PriceCondition
import io.github.chamsser.gymvi.recommendation.PriceConditionUnit
import io.github.chamsser.gymvi.recommendation.RecommendationConditions
import io.github.chamsser.gymvi.recommendation.Strength
import io.github.chamsser.gymvi.recommendation.TargetGroup
import io.github.chamsser.gymvi.recommendation.TimeCondition
import io.github.chamsser.gymvi.recommendation.ValuesCondition
import io.github.chamsser.gymvi.recommendation.Weekday
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.LocalTime
import java.time.format.DateTimeParseException

internal class AiToolRequestParser(private val objectMapper: ObjectMapper) {
    fun search(argumentsJson: String): RecommendationConditions {
        val root = runCatching { objectMapper.readTree(argumentsJson) }
            .getOrElse { throw AiProviderException("AI_TOOL_ARGUMENTS_INVALID", it) }
        requireObject(root, SEARCH_KEYS)
        return root.conditions()
    }

    /**
     * Reads a server-issued continuation for a new conversation. A field named in reference_matched
     * stays only while the current references still contain its recorded reference part; without
     * reference_values that part is the field itself. Other metadata shapes are rejected.
     */
    fun continuation(json: String, references: AiReferences): RecommendationConditions {
        val root = runCatching { objectMapper.readTree(json) }
            .getOrElse { throw AiProviderException("AI_TOOL_ARGUMENTS_INVALID", it) }
        if (!root.isObject) invalid()
        val body = objectMapper.createObjectNode()
        root.propertyNames().forEach { name -> if (name !in REFERENCE_KEYS) body.set(name, root.get(name)) }
        requireObject(body, SEARCH_KEYS)
        val conditions = body.conditions()
        val matched = root.get("reference_matched")
        val recorded = root.get("reference_values")
        if (matched == null) {
            if (recorded != null) invalid()
            return conditions
        }
        if (!matched.isArray) invalid()
        val fields = (0 until matched.size()).map { index ->
            matched.get(index).takeIf(JsonNode::isTextual)?.asText()?.takeIf { it in REFERENCE_FIELDS } ?: invalid()
        }
        if (fields.distinct().size != fields.size) invalid()
        val recordedParts = if (recorded == null) {
            conditions.only(fields)
        } else {
            if (!recorded.isObject) invalid()
            val names = recorded.propertyNames().asSequence().toSet()
            if (names != fields.toSet() || names.any { recorded.get(it).isNull }) invalid()
            recorded.conditions()
        }
        return conditions.withoutRevoked(recordedParts, references)
    }

    private fun JsonNode.conditions() = RecommendationConditions(
        categories = valuesCondition("categories", Category.entries),
        weekdays = valuesCondition("weekdays", Weekday.entries),
        startTime = startTime(),
        maxPriceWon = priceCondition(),
        maxDistanceMeters = numberCondition("max_distance_meters"),
        targetGroups = valuesCondition("target_groups", TargetGroup.entries),
        beginner = flagCondition("beginner"),
        applicationAvailable = flagCondition("application_available"),
    )

    private fun RecommendationConditions.only(fields: List<String>) = RecommendationConditions(
        categories = categories.takeIf { "categories" in fields },
        weekdays = weekdays.takeIf { "weekdays" in fields },
        startTime = startTime.takeIf { "start_time" in fields },
        maxPriceWon = maxPriceWon.takeIf { "max_price_won" in fields },
        maxDistanceMeters = maxDistanceMeters.takeIf { "max_distance_meters" in fields },
        targetGroups = targetGroups.takeIf { "target_groups" in fields },
        beginner = beginner.takeIf { "beginner" in fields },
    )

    fun compare(argumentsJson: String): List<String> {
        val root = runCatching { objectMapper.readTree(argumentsJson) }
            .getOrElse { throw AiProviderException("AI_TOOL_ARGUMENTS_INVALID", it) }
        requireObject(root, COMPARE_KEYS)
        val optionIds = root.get("option_ids")?.takeIf(JsonNode::isArray) ?: invalid()
        if (optionIds.size() !in 2..3) invalid()
        val parsed = buildList {
            for (index in 0 until optionIds.size()) {
                val optionId = optionIds.get(index).takeIf(JsonNode::isTextual)?.asText()?.trim() ?: invalid()
                if (optionId.isEmpty() || optionId.length > 160) invalid()
                add(optionId)
            }
        }
        if (parsed.distinct().size != parsed.size) invalid()
        return parsed
    }

    private fun <T : Enum<T>> JsonNode.valuesCondition(name: String, values: List<T>): ValuesCondition<T>? {
        val node = get(name) ?: return null
        if (node.isNull) return null
        requireObject(node, setOf("values", "strength"))
        val rawValues = node.get("values")
        if (rawValues == null || !rawValues.isArray || rawValues.isEmpty) invalid()
        val parsed = buildSet {
            for (index in 0 until rawValues.size()) {
                val value = rawValues.get(index)
                val raw = value.takeIf(JsonNode::isTextual)?.asText() ?: invalid()
                add(values.firstOrNull { it.name == raw } ?: invalid())
            }
        }
        if (parsed.size != rawValues.size()) invalid()
        return ValuesCondition(parsed, node.strength())
    }

    private fun JsonNode.startTime(): TimeCondition? {
        val node = get("start_time") ?: return null
        if (node.isNull) return null
        requireObject(node, setOf("earliest", "latest", "strength"))
        val earliest = node.clockTime("earliest")
        val latest = node.clockTime("latest")
        if (earliest > latest) invalid()
        return TimeCondition(earliest, latest, node.strength())
    }

    private fun JsonNode.numberCondition(name: String): NumberCondition? {
        val node = get(name) ?: return null
        if (node.isNull) return null
        requireObject(node, setOf("value", "strength"))
        val value = node.get("value")?.takeIf(JsonNode::isIntegralNumber)?.asLong() ?: invalid()
        val allowedRange = when (name) {
            "max_distance_meters" -> 100L..100_000L
            else -> invalid()
        }
        if (value !in allowedRange) invalid()
        return NumberCondition(value.toInt(), node.strength())
    }

    /**
     * The search tool always sends unit, null when the user named none. A continuation writes unit only when
     * set, so both shapes are read; an unknown unit is rejected rather than read as a plain amount.
     */
    private fun JsonNode.priceCondition(): PriceCondition? {
        val node = get("max_price_won") ?: return null
        if (node.isNull) return null
        if (!node.isObject) invalid()
        val fields = node.propertyNames().asSequence().toSet()
        if (fields != PRICE_KEYS && fields != PRICE_KEYS + "unit") invalid()
        val value = node.get("value")?.takeIf(JsonNode::isIntegralNumber)?.asLong() ?: invalid()
        if (value !in 0L..10_000_000L) invalid()
        val unit = node.get("unit")?.takeUnless(JsonNode::isNull)?.let { raw ->
            val name = raw.takeIf(JsonNode::isTextual)?.asText() ?: invalid()
            PriceConditionUnit.entries.firstOrNull { it.name == name } ?: invalid()
        }
        return PriceCondition(value.toInt(), node.strength(), unit)
    }

    private fun JsonNode.flagCondition(name: String): FlagCondition? {
        val node = get(name) ?: return null
        if (node.isNull) return null
        requireObject(node, setOf("strength"))
        return FlagCondition(node.strength())
    }

    private fun JsonNode.strength(): Strength = when (get("strength")?.asText()) {
        "REQUIRED" -> Strength.REQUIRED
        "PREFERRED" -> Strength.PREFERRED
        else -> invalid()
    }

    private fun JsonNode.clockTime(name: String): LocalTime {
        val raw = get(name)?.takeIf(JsonNode::isTextual)?.asText() ?: invalid()
        if (!CLOCK_PATTERN.matches(raw)) invalid()
        return try {
            LocalTime.parse(raw)
        } catch (_: DateTimeParseException) {
            invalid()
        }
    }

    private fun requireObject(node: JsonNode, allowedKeys: Set<String>) {
        if (!node.isObject) invalid()
        val fields = node.propertyNames().asSequence().toSet()
        if (fields != allowedKeys) invalid()
    }

    private fun invalid(): Nothing = throw AiProviderException("AI_TOOL_ARGUMENTS_INVALID")

    private companion object {
        val CLOCK_PATTERN = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")
        val SEARCH_KEYS = setOf(
            "categories",
            "weekdays",
            "start_time",
            "max_price_won",
            "max_distance_meters",
            "target_groups",
            "beginner",
            "application_available",
        )
        val PRICE_KEYS = setOf("value", "strength")
        val COMPARE_KEYS = setOf("option_ids")
        val REFERENCE_KEYS = setOf("reference_matched", "reference_values")
    }
}
