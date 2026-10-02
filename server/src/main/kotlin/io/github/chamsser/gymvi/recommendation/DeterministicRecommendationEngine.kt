package io.github.chamsser.gymvi.recommendation

import io.github.chamsser.gymvi.usage.JoinedUsageOptionRecord
import io.github.chamsser.gymvi.usage.OperatorTimeResponse
import io.github.chamsser.gymvi.usage.PriceUnit
import io.github.chamsser.gymvi.usage.PriceUnitKind
import io.github.chamsser.gymvi.usage.ProgramPrice
import java.time.LocalDate

class DeterministicRecommendationEngine {
    fun evaluate(
        record: JoinedUsageOptionRecord,
        conditions: RecommendationConditions,
        preferences: RecommendationPreferences,
        date: LocalDate,
        origin: Origin?,
        operatorTime: OperatorTimeResponse? = null,
        requiredWeekdayCoverage: Set<Weekday> = emptySet(),
        minimumWeekdayCount: Int? = null,
    ): EvaluatedOption {
        val option = record.option
        val facts = ProgramFactDeriver.derive(record, date, origin, operatorTime)
        val exclusion = mutableListOf<String>()
        val matched = mutableListOf<String>()
        val unmet = mutableListOf<String>()
        val uncertain = mutableSetOf<String>()
        if (facts.period.value == Period.ENDED) exclusion += "PERIOD_ENDED"
        if (option.operationState == "CLOSED") exclusion += "FACILITY_CLOSED"
        if (option.joinState !in setOf("EXACT", "REVIEWED")) exclusion += "UNTRUSTED_FACILITY_JOIN"
        if (option.applicationState == "UNKNOWN") uncertain += "APPLICATION_STATE_UNKNOWN"
        if (option.operationState == "UNKNOWN") uncertain += "FACILITY_OPERATION_UNKNOWN"
        if (facts.startTime.state == "UNKNOWN") uncertain += "START_TIME_UNKNOWN"
        if (option.price.amountWon == null) uncertain += "PRICE_UNKNOWN"
        else if (option.price.unit == null) uncertain += "PRICE_UNIT_UNKNOWN"
        if (facts.period.state == "UNKNOWN") uncertain += "PERIOD_UNKNOWN"

        val preferredMet = mutableSetOf<String>()
        fun check(code: String, strength: Strength?, outcome: ConditionOutcome) {
            if (strength == null) return
            when (outcome) {
                ConditionOutcome.MET -> {
                    matched += code
                    if (strength == Strength.PREFERRED) preferredMet += code
                }
                ConditionOutcome.NOT_MET -> if (strength == Strength.REQUIRED) exclusion += "${code}_NOT_MET" else unmet += code
                ConditionOutcome.UNKNOWN -> if (strength == Strength.REQUIRED) exclusion += "${code}_UNKNOWN" else uncertain += "${code}_UNKNOWN"
            }
        }
        fun truth(value: Boolean?) = when (value) {
            true -> ConditionOutcome.MET
            false -> ConditionOutcome.NOT_MET
            null -> ConditionOutcome.UNKNOWN
        }
        conditions.categories?.let { condition ->
            check("CATEGORY", condition.strength, truth(facts.category.value?.let { it in condition.values }))
        }
        conditions.weekdays?.let { condition ->
            check("WEEKDAYS", condition.strength, truth(
                option.weekdays.takeIf(List<String>::isNotEmpty)?.let { days ->
                    days.all { day -> DAY_CODES[day]?.let { it in condition.values } ?: false }
                },
            ))
        }
        if (requiredWeekdayCoverage.isNotEmpty()) {
            check("WEEKDAY_COVERAGE", Strength.REQUIRED, truth(
                option.weekdays.takeIf(List<String>::isNotEmpty)?.let { days ->
                    days.mapNotNull(DAY_CODES::get).toSet().containsAll(requiredWeekdayCoverage)
                },
            ))
        }
        minimumWeekdayCount?.let { minimum ->
            check("MINIMUM_WEEKDAY_COUNT", Strength.REQUIRED, truth(
                option.weekdays.takeIf(List<String>::isNotEmpty)?.let { days ->
                    days.mapNotNull(DAY_CODES::get).toSet().size >= minimum
                },
            ))
        }
        conditions.startTime?.let { condition ->
            check("START_TIME", condition.strength, truth(
                facts.startTime.value?.let { it >= condition.earliest && it <= condition.latest },
            ))
        }
        conditions.maxPriceWon?.let { condition ->
            check("MAX_PRICE_WON", condition.strength, priceOutcome(option.price, condition))
        }
        conditions.maxDistanceMeters?.let { condition ->
            check("MAX_DISTANCE_METERS", condition.strength, truth(
                facts.straightLineDistanceMeters.value?.let { it <= condition.value },
            ))
        }
        conditions.targetGroups?.let { condition ->
            check("TARGET_GROUPS", condition.strength, truth(
                facts.targetGroups.values?.let { groups -> groups.any { it in condition.values } },
            ))
        }
        conditions.beginner?.let { condition ->
            check("BEGINNER", condition.strength, truth(facts.level.values?.let { Level.BEGINNER in it }))
        }
        conditions.applicationAvailable?.let { condition ->
            check("APPLICATION_AVAILABLE", condition.strength, when (option.applicationState) {
                "AVAILABLE" -> ConditionOutcome.MET
                "CLOSED" -> ConditionOutcome.NOT_MET
                else -> ConditionOutcome.UNKNOWN
            })
        }

        if (exclusion.isNotEmpty()) return EvaluatedOption(
            false, exclusion.distinct(), null, emptyList(), matched, unmet, uncertain.sorted(), facts,
        )
        val components = buildList {
            add(ScoreComponent("BASE", Weights.BASE))
            add(ScoreComponent(if (option.joinState == "EXACT") "JOIN_EXACT" else "JOIN_REVIEWED",
                if (option.joinState == "EXACT") Weights.JOIN_EXACT else Weights.JOIN_REVIEWED))
            when (facts.period.value) {
                Period.CURRENT -> add(ScoreComponent("PERIOD_CURRENT", Weights.PERIOD_CURRENT))
                Period.UPCOMING -> add(ScoreComponent("PERIOD_UPCOMING", Weights.PERIOD_UPCOMING))
                else -> Unit
            }
            for ((condition, points) in Weights.PREFERRED) {
                if (condition in preferredMet) add(ScoreComponent("PREFERRED_$condition", points))
            }
            facts.straightLineDistanceMeters.value?.let {
                add(ScoreComponent("DISTANCE_PROXIMITY", (Weights.DISTANCE_PROXIMITY - it / 250).coerceAtLeast(0)))
            }
            if (option.facilityId in preferences.favoriteFacilityIds) add(ScoreComponent("FAVORITE_FACILITY", Weights.FAVORITE_FACILITY))
            if (option.facilityId in preferences.recentFacilityIds) add(ScoreComponent("RECENT_FACILITY", Weights.RECENT_FACILITY))
        }
        return EvaluatedOption(true, emptyList(), components.sumOf(ScoreComponent::points), components,
            matched, unmet, uncertain.sorted(), facts)
    }

    /**
     * A verified free 0 meets every limit. A unit limit is decided only for options billed for exactly that
     * unit; other or unknown units stay unknown because amounts are never converted between units.
     */
    private fun priceOutcome(price: ProgramPrice, condition: PriceCondition): ConditionOutcome {
        val amount = price.amountWon ?: return ConditionOutcome.UNKNOWN
        if (amount == 0) return ConditionOutcome.MET
        val billed = when (condition.unit) {
            null -> null
            PriceConditionUnit.MONTH -> PriceUnit(PriceUnitKind.MONTHS, 1)
            PriceConditionUnit.SESSION -> PriceUnit(PriceUnitKind.SESSIONS, 1)
        }
        if (billed != null && price.unit != billed) return ConditionOutcome.UNKNOWN
        return if (amount <= condition.value) ConditionOutcome.MET else ConditionOutcome.NOT_MET
    }

    companion object {
        private val DAY_CODES = mapOf("월" to Weekday.MON, "화" to Weekday.TUE, "수" to Weekday.WED,
            "목" to Weekday.THU, "금" to Weekday.FRI, "토" to Weekday.SAT, "일" to Weekday.SUN)
    }
}

object Weights {
    const val BASE = 1000
    const val JOIN_EXACT = 80
    const val JOIN_REVIEWED = 60
    const val PERIOD_CURRENT = 30
    const val PERIOD_UPCOMING = 10
    val PREFERRED = linkedMapOf(
        "CATEGORY" to 120,
        "START_TIME" to 100,
        "MAX_DISTANCE_METERS" to 80,
        "APPLICATION_AVAILABLE" to 70,
        "WEEKDAYS" to 60,
        "MAX_PRICE_WON" to 60,
        "TARGET_GROUPS" to 50,
        "BEGINNER" to 50,
    )
    const val DISTANCE_PROXIMITY = 60
    const val FAVORITE_FACILITY = 50
    const val RECENT_FACILITY = 20
}
