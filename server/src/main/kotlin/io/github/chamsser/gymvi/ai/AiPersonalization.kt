package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.catalog.ApiValidationException
import io.github.chamsser.gymvi.recommendation.*
import java.time.LocalTime

data class AiReferences(
    val memory: AiMemoryFacts? = null,
    val ageBand: String? = null,
    val experience: String? = null,
    val goal: String? = null,
)

data class AiMemoryFacts(
    val categories: Set<String> = emptySet(),
    val weekdays: Set<String> = emptySet(),
    val earliestStart: String? = null,
    val latestStart: String? = null,
    val maxPriceWon: Int? = null,
    val maxDistanceMeters: Int? = null,
    val beginner: Boolean = false,
)

internal object AiReferenceParser {
    fun parse(raw: Map<String, Any?>): AiReferences {
        requireKeys(raw, setOf("memory", "age_band", "experience", "goal"))
        val memory = raw["memory"]?.let {
            @Suppress("UNCHECKED_CAST")
            facts(it as? Map<String, Any?> ?: invalid())
        }
        return AiReferences(memory, raw.enum("age_band", setOf("YOUTH", "ADULT", "SENIOR")),
            raw.enum("experience", setOf("BEGINNER", "REGULAR", "EXPERIENCED")),
            raw.enum("goal", setOf("GENERAL_FITNESS", "STRENGTH", "FLEXIBILITY", "ENDURANCE", "WEIGHT_MANAGEMENT")))
    }

    private fun facts(raw: Map<String, Any?>): AiMemoryFacts {
        requireKeys(raw, setOf("categories", "weekdays", "earliest_start", "latest_start", "max_price_won", "max_distance_meters", "beginner"))
        fun values(key: String, allowed: Set<String>): Set<String> {
            val values = raw[key] as? List<*> ?: invalid()
            if (values.size > allowed.size || values.any { it !is String || it !in allowed }) invalid()
            return values.filterIsInstance<String>().toSet()
        }
        fun time(key: String): String? = raw[key]?.let {
            val value = it as? String ?: invalid()
            if (!Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(value)) invalid()
            value
        }
        fun number(key: String, range: IntRange): Int? = raw[key]?.let {
            val value = it as? Number ?: invalid()
            if (value.toDouble() != value.toInt().toDouble() || value.toInt() !in range) invalid()
            value.toInt()
        }
        val earliest = time("earliest_start")
        val latest = time("latest_start")
        if ((earliest == null) != (latest == null) || earliest != null && earliest > latest!!) invalid()
        return AiMemoryFacts(values("categories", Category.entries.map { it.name }.toSet()),
            values("weekdays", Weekday.entries.map { it.name }.toSet()), earliest, latest,
            number("max_price_won", 0..10_000_000), number("max_distance_meters", 100..100_000),
            raw["beginner"] as? Boolean ?: invalid())
    }

    private fun Map<String, Any?>.enum(key: String, allowed: Set<String>): String? = this[key]?.let {
        it as? String ?: invalid()
    }?.also { if (it !in allowed) invalid() }
    private fun requireKeys(raw: Map<String, Any?>, allowed: Set<String>) { if (raw.keys.any { it !in allowed }) invalid() }
    private fun invalid(): Nothing = throw ApiValidationException("VALIDATION_AI_REFERENCE", "AI 참고 설정 형식이 올바르지 않습니다.")
}

/** Explicit conversational conditions win. Opted-in references are only soft defaults. */
internal fun RecommendationConditions.withReferences(ref: AiReferences): RecommendationConditions {
    val memory = ref.memory
    return copy(
        categories = categories ?: memory?.categories?.takeIf { it.isNotEmpty() }?.let {
            ValuesCondition(it.map(Category::valueOf).toSet(), Strength.PREFERRED) },
        weekdays = weekdays ?: memory?.weekdays?.takeIf { it.isNotEmpty() }?.let {
            ValuesCondition(it.map(Weekday::valueOf).toSet(), Strength.PREFERRED) },
        startTime = startTime ?: memory?.earliestStart?.let {
            TimeCondition(LocalTime.parse(it), LocalTime.parse(memory.latestStart), Strength.PREFERRED) },
        // Saved budgets and MY input carry no unit, so they stay amount-only defaults.
        maxPriceWon = maxPriceWon ?: memory?.maxPriceWon?.let { PriceCondition(it, Strength.PREFERRED) },
        maxDistanceMeters = maxDistanceMeters ?: memory?.maxDistanceMeters?.let { NumberCondition(it, Strength.PREFERRED) },
        targetGroups = targetGroups ?: ref.ageBand?.let { ValuesCondition(setOf(TargetGroup.valueOf(it)), Strength.PREFERRED) },
        beginner = beginner ?: if (memory?.beginner == true || ref.experience == "BEGINNER") FlagCondition(Strength.PREFERRED) else null,
    )
}

/** Condition fields an opted-in reference can supply, named as in the search tool and continuation. */
internal val REFERENCE_FIELDS = listOf("categories", "weekdays", "start_time", "max_price_won", "max_distance_meters",
    "target_groups", "beginner")

/**
 * The reference part each explicit condition shares: common set values, or an equal time, amount or
 * flag. A shared value cannot be told apart from one the model copied from that reference.
 */
internal fun RecommendationConditions.referenceMatches(references: AiReferences): RecommendationConditions {
    val defaults = RecommendationConditions().withReferences(references)
    fun <T> shared(own: ValuesCondition<T>?, reference: ValuesCondition<T>?) =
        reference?.values?.intersect(own?.values.orEmpty())?.takeIf { it.isNotEmpty() }?.let { reference.copy(values = it) }
    fun same(own: NumberCondition?, reference: NumberCondition?) = reference?.takeIf { it.value == own?.value }
    // A unit is part of the condition: a unitless reference never matches a monthly or per-session limit.
    fun samePrice(own: PriceCondition?, reference: PriceCondition?) =
        reference?.takeIf { it.value == own?.value && it.unit == own.unit }
    return RecommendationConditions(
        categories = shared(categories, defaults.categories),
        weekdays = shared(weekdays, defaults.weekdays),
        startTime = defaults.startTime?.takeIf { it.earliest == startTime?.earliest && it.latest == startTime?.latest },
        maxPriceWon = samePrice(maxPriceWon, defaults.maxPriceWon),
        maxDistanceMeters = same(maxDistanceMeters, defaults.maxDistanceMeters),
        targetGroups = shared(targetGroups, defaults.targetGroups),
        beginner = defaults.beginner?.takeIf { beginner != null },
    )
}

/**
 * Clears, as a whole, each condition whose recorded reference part the current references no longer
 * contain. Conditions without a recorded part were never matched to a reference and stay.
 */
internal fun RecommendationConditions.withoutRevoked(recorded: RecommendationConditions, references: AiReferences): RecommendationConditions {
    val defaults = RecommendationConditions().withReferences(references)
    fun <T> stillContains(now: ValuesCondition<T>?, was: ValuesCondition<T>?) = was == null || now?.values?.containsAll(was.values) == true
    fun stillEqual(now: NumberCondition?, was: NumberCondition?) = was == null || now?.value == was.value
    fun stillEqualPrice(now: PriceCondition?, was: PriceCondition?) = was == null || now?.value == was.value && now.unit == was.unit
    val time = recorded.startTime
    return copy(
        categories = categories.takeIf { stillContains(defaults.categories, recorded.categories) },
        weekdays = weekdays.takeIf { stillContains(defaults.weekdays, recorded.weekdays) },
        startTime = startTime.takeIf {
            time == null || defaults.startTime?.earliest == time.earliest && defaults.startTime?.latest == time.latest
        },
        maxPriceWon = maxPriceWon.takeIf { stillEqualPrice(defaults.maxPriceWon, recorded.maxPriceWon) },
        maxDistanceMeters = maxDistanceMeters.takeIf { stillEqual(defaults.maxDistanceMeters, recorded.maxDistanceMeters) },
        targetGroups = targetGroups.takeIf { stillContains(defaults.targetGroups, recorded.targetGroups) },
        beginner = beginner.takeIf { recorded.beginner == null || defaults.beginner != null },
    )
}

/**
 * Facts the model extracted from this message without opted-in reference values the chat did not
 * already hold. An exact copy cannot be told apart from a stated value, so both are dropped.
 */
internal fun AiMemoryFacts.withoutReferenceCopies(conditions: RecommendationConditions, references: AiReferences): AiMemoryFacts {
    val defaults = RecommendationConditions().withReferences(references)
    val time = startClock()
    val keepTime = time == null || time == conditions.startTime.clock() || time != defaults.startTime.clock()
    fun kept(value: Int?, held: Int?, referenced: Int?) = value?.takeIf { it == held || it != referenced }
    return AiMemoryFacts(
        categories = categories - (defaults.categories.names() - conditions.categories.names()),
        weekdays = weekdays - (defaults.weekdays.names() - conditions.weekdays.names()),
        earliestStart = earliestStart.takeIf { keepTime },
        latestStart = latestStart.takeIf { keepTime },
        maxPriceWon = kept(maxPriceWon, conditions.maxPriceWon?.value, defaults.maxPriceWon?.value),
        maxDistanceMeters = kept(maxDistanceMeters, conditions.maxDistanceMeters?.value, defaults.maxDistanceMeters?.value),
        beginner = beginner && (conditions.beginner != null || defaults.beginner == null),
    )
}

/** Only what this message added to the chat, so earlier turns, including unsaved ones, are not learned again. */
internal fun AiMemoryFacts.newlyStated(conditions: RecommendationConditions): AiMemoryFacts? {
    val newTime = startClock() != conditions.startTime.clock()
    return AiMemoryFacts(
        categories = categories - conditions.categories.names(),
        weekdays = weekdays - conditions.weekdays.names(),
        earliestStart = earliestStart.takeIf { newTime },
        latestStart = latestStart.takeIf { newTime },
        maxPriceWon = maxPriceWon?.takeIf { it != conditions.maxPriceWon?.value },
        maxDistanceMeters = maxDistanceMeters?.takeIf { it != conditions.maxDistanceMeters?.value },
        beginner = beginner && conditions.beginner == null,
    ).takeUnless { it == AiMemoryFacts() }
}

private fun AiMemoryFacts.startClock() = earliestStart?.let { "$it-$latestStart" }
private fun TimeCondition?.clock() = this?.let { "${it.earliest}-${it.latest}" }
private fun <T : Enum<T>> ValuesCondition<T>?.names() = this?.values.orEmpty().map { it.name }.toSet()

/** Explicit facts extracted from this turn also let a saved exercise-only chat resume. */
internal fun RecommendationConditions.withExplicitFacts(facts: AiMemoryFacts): RecommendationConditions {
    val incoming = RecommendationConditions().withReferences(AiReferences(memory = facts))
    // A repeated value keeps the strength this chat already gave it.
    fun <T> changed(current: ValuesCondition<T>?, next: ValuesCondition<T>?) = next?.takeIf { it.values != current?.values } ?: current
    fun changedNumber(current: NumberCondition?, next: NumberCondition?) = next?.takeIf { it.value != current?.value } ?: current
    // A budget is never merged here: an amount without a known unit is not a price condition, and an
    // existing price condition is kept as it is. A budget first said in an exercise-only chat stays in the
    // recent turns until a search sends it with its unit.
    return copy(categories = changed(categories, incoming.categories), weekdays = changed(weekdays, incoming.weekdays),
        startTime = incoming.startTime?.takeIf { it.earliest != startTime?.earliest || it.latest != startTime?.latest } ?: startTime,
        maxDistanceMeters = changedNumber(maxDistanceMeters, incoming.maxDistanceMeters), beginner = beginner ?: incoming.beginner)
}

/**
 * Explicit conditions for a later server conversation. reference_matched names fields that share a
 * value with the opted-in references, and reference_values keeps that shared part, so a restart can
 * drop the whole field once the current references no longer contain it.
 */
internal fun RecommendationConditions.continuationJson(mapper: tools.jackson.databind.ObjectMapper, references: AiReferences): String {
    val node = mapper.createObjectNode()
    jsonFields().forEach { (key, value) -> if (value == null) node.putNull(key) else node.set(key, mapper.valueToTree(value)) }
    val matched = referenceMatches(references).jsonFields().filterValues { it != null }
    if (matched.isNotEmpty()) {
        node.set("reference_matched", mapper.valueToTree(matched.keys.toList()))
        node.set("reference_values", mapper.valueToTree(matched))
    }
    return node.toString()
}

private fun RecommendationConditions.jsonFields(): Map<String, Map<String, Any>?> {
    fun <T : Enum<T>> values(condition: ValuesCondition<T>?) = condition?.let {
        mapOf("values" to it.values.map { value -> value.name }, "strength" to it.strength.name) }
    fun number(condition: NumberCondition?) = condition?.let { mapOf("value" to it.value, "strength" to it.strength.name) }
    // unit is written only when set, so unitless conditions keep the earlier continuation shape.
    fun price(condition: PriceCondition?) = condition?.let {
        linkedMapOf<String, Any>("value" to it.value, "strength" to it.strength.name).apply { it.unit?.let { unit -> put("unit", unit.name) } }
    }
    fun flag(condition: FlagCondition?) = condition?.let { mapOf("strength" to it.strength.name) }
    return linkedMapOf("categories" to values(categories), "weekdays" to values(weekdays),
        "start_time" to startTime?.let { mapOf("earliest" to it.earliest.toString(), "latest" to it.latest.toString(), "strength" to it.strength.name) },
        "max_price_won" to price(maxPriceWon), "max_distance_meters" to number(maxDistanceMeters),
        "target_groups" to values(targetGroups), "beginner" to flag(beginner), "application_available" to flag(applicationAvailable))
}
