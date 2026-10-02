package io.github.chamsser.gymvi.recommendation

import io.github.chamsser.gymvi.catalog.ApiValidationException
import io.github.chamsser.gymvi.catalog.BoundingBox
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

object RecommendationRequestParser {
    fun recommendation(body: Map<String, Any?>): RecommendationQuery {
        keys(body, setOf("area", "origin", "date", "conditions", "preferences", "limit", "max_per_facility"))
        val area = body.obj("area", required = true)!!
        keys(area, setOf("min_longitude", "min_latitude", "max_longitude", "max_latitude"))
        val bounds = BoundingBox.fromNullable(
            area.decimal("min_longitude"), area.decimal("min_latitude"),
            area.decimal("max_longitude"), area.decimal("max_latitude"),
        )
        val preferences = body.obj("preferences")
        val favorite = preferences?.ids("favorite_facility_ids").orEmpty()
        val recent = preferences?.ids("recent_facility_ids").orEmpty()
        if (preferences != null) keys(preferences, setOf("favorite_facility_ids", "recent_facility_ids"))
        if (favorite.size > 20 || recent.size > 20) fail("VALIDATION_PREFERENCES")
        return RecommendationQuery(bounds, body.origin(), body.date(), body.conditions(),
            RecommendationPreferences(favorite.toSet(), recent.toSet()),
            body.int("limit") ?: 20, body.int("max_per_facility") ?: 3)
    }

    fun comparison(body: Map<String, Any?>): ComparisonQuery {
        keys(body, setOf("usage_option_ids", "origin", "date", "conditions"))
        return ComparisonQuery(body.ids("usage_option_ids", required = true), body.origin(), body.date(), body.conditions())
    }

    private fun Map<String, Any?>.origin(): Origin? {
        val node = obj("origin") ?: return null
        keys(node, setOf("latitude", "longitude"))
        return Origin(node.decimal("latitude") ?: fail("VALIDATION_ORIGIN_OUTSIDE_KOREA"),
            node.decimal("longitude") ?: fail("VALIDATION_ORIGIN_OUTSIDE_KOREA"))
    }

    private fun Map<String, Any?>.date(): LocalDate? = string("date")?.let {
        try { LocalDate.parse(it) } catch (_: DateTimeParseException) { fail("VALIDATION_DATE") }
    }

    private fun Map<String, Any?>.conditions(): RecommendationConditions {
        val node = obj("conditions") ?: return RecommendationConditions()
        keys(node, setOf("categories", "weekdays", "start_time", "max_price_won", "max_distance_meters",
            "target_groups", "beginner", "application_available"))
        return RecommendationConditions(
            categories = node.valuesCondition("categories", Category.entries),
            weekdays = node.valuesCondition("weekdays", Weekday.entries),
            startTime = node.obj("start_time")?.let { item ->
                keys(item, setOf("earliest", "latest", "strength"))
                val earliest = item.time("earliest")
                val latest = item.time("latest")
                if (earliest > latest) fail("VALIDATION_START_TIME")
                TimeCondition(earliest, latest, item.strength())
            },
            maxPriceWon = node.priceCondition(),
            maxDistanceMeters = node.numberCondition("max_distance_meters"),
            targetGroups = node.valuesCondition("target_groups", TargetGroup.entries),
            beginner = node.flagCondition("beginner"),
            applicationAvailable = node.flagCondition("application_available"),
        )
    }

    private fun <T : Enum<T>> Map<String, Any?>.valuesCondition(name: String, allowed: List<T>): ValuesCondition<T>? =
        obj(name)?.let { node ->
            keys(node, setOf("values", "strength"))
            val raw = node["values"] as? List<*> ?: fail("VALIDATION_${name.uppercase()}")
            if (raw.isEmpty()) fail("VALIDATION_${name.uppercase()}")
            val values = raw.map { value ->
                allowed.firstOrNull { it.name == value } ?: fail("VALIDATION_${name.uppercase()}")
            }.toSet()
            ValuesCondition(values, node.strength())
        }

    private fun Map<String, Any?>.numberCondition(name: String): NumberCondition? = obj(name)?.let { node ->
        keys(node, setOf("value", "strength"))
        NumberCondition(node.int("value") ?: fail("VALIDATION_${name.uppercase()}"), node.strength())
    }

    private fun Map<String, Any?>.priceCondition(): PriceCondition? = obj("max_price_won")?.let { node ->
        keys(node, setOf("value", "strength", "unit"))
        val unit = node["unit"]?.let { raw ->
            PriceConditionUnit.entries.firstOrNull { it.name == raw } ?: fail("VALIDATION_MAX_PRICE_WON")
        }
        PriceCondition(node.int("value") ?: fail("VALIDATION_MAX_PRICE_WON"), node.strength(), unit)
    }

    private fun Map<String, Any?>.flagCondition(name: String): FlagCondition? = obj(name)?.let { node ->
        keys(node, setOf("strength"))
        FlagCondition(node.strength())
    }

    private fun Map<String, Any?>.strength(): Strength = when (string("strength")) {
        "REQUIRED" -> Strength.REQUIRED
        "PREFERRED" -> Strength.PREFERRED
        else -> fail("VALIDATION_STRENGTH")
    }

    private fun Map<String, Any?>.time(name: String): LocalTime {
        val value = string(name) ?: fail("VALIDATION_START_TIME")
        if (!Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$").matches(value)) fail("VALIDATION_START_TIME")
        return LocalTime.parse(value)
    }

    private fun Map<String, Any?>.ids(name: String, required: Boolean = false): List<String> {
        if (!containsKey(name) && !required) return emptyList()
        val raw = this[name] as? List<*> ?: fail("VALIDATION_${name.uppercase()}")
        return raw.map { it as? String ?: fail("VALIDATION_${name.uppercase()}") }
    }

    private fun Map<String, Any?>.obj(name: String, required: Boolean = false): Map<String, Any?>? {
        if (!containsKey(name) && !required) return null
        val raw = this[name] as? Map<*, *> ?: fail("VALIDATION_${name.uppercase()}")
        if (raw.keys.any { it !is String }) fail("VALIDATION_PARAMETER")
        @Suppress("UNCHECKED_CAST")
        return raw as Map<String, Any?>
    }

    private fun Map<String, Any?>.string(name: String): String? {
        if (!containsKey(name)) return null
        return this[name] as? String ?: fail("VALIDATION_${name.uppercase()}")
    }

    private fun Map<String, Any?>.int(name: String): Int? {
        if (!containsKey(name)) return null
        val value = this[name] as? Number ?: fail("VALIDATION_${name.uppercase()}")
        val long = value.toLong()
        if (value.toDouble() != long.toDouble() || long !in Int.MIN_VALUE..Int.MAX_VALUE) fail("VALIDATION_${name.uppercase()}")
        return long.toInt()
    }

    private fun Map<String, Any?>.decimal(name: String): Double? {
        if (!containsKey(name)) return null
        return (this[name] as? Number)?.toDouble() ?: fail("VALIDATION_${name.uppercase()}")
    }

    private fun keys(node: Map<String, Any?>, allowed: Set<String>) {
        if (node.keys.any { it !in allowed }) fail("VALIDATION_PARAMETER")
    }

    private fun fail(code: String): Nothing = throw ApiValidationException(code, "요청 조건 형식이 올바르지 않습니다.")
}
