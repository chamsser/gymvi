package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.recommendation.Category
import io.github.chamsser.gymvi.recommendation.RecommendationConditions
import io.github.chamsser.gymvi.recommendation.Strength
import io.github.chamsser.gymvi.recommendation.TimeCondition
import io.github.chamsser.gymvi.recommendation.ValuesCondition
import io.github.chamsser.gymvi.recommendation.Weekday
import java.time.LocalTime

internal object AiFallbackPlanner {
    /**
     * Outage recovery is conservative: stale map filters are not a fresh search request.
     * Keep the previous conversational action for short follow-ups, unless the user
     * explicitly asks to move to a place/search or rejects that transition.
     */
    fun shouldOfferExercise(message: String, state: AiConversationState): Boolean {
        if (rejectsPlaceTransition(message)) return true
        if (EXPLICIT_SEARCH_PATTERN.containsMatchIn(message) || isRouteRequest(message)) return false
        if (EXERCISE_PROPOSAL_PATTERN.containsMatchIn(message)) return true
        if (state.lastAction == AiAction.EXERCISE_GUIDE) return true
        if (requestedOptionId(message, state) != null) return false
        val activeSearch = state.lastAction in setOf(AiAction.SHOW_OPTIONS, AiAction.COMPARE_OPTIONS)
        val suppliesSearchCondition = conditions(message, RecommendationConditions()) != null
        return !activeSearch || !suppliesSearchCondition
    }

    private fun rejectsPlaceTransition(message: String): Boolean = REJECT_PLACE_PATTERN.containsMatchIn(message)
    fun conditions(message: String, existing: RecommendationConditions): RecommendationConditions? {
        var changed = false
        var result = existing
        if (SWIMMING_TERMS.any(message::contains)) {
            result = result.copy(categories = ValuesCondition(setOf(Category.SWIMMING), Strength.REQUIRED))
            changed = true
        }
        parseWeekdays(message)?.let { weekdays ->
            result = result.copy(weekdays = ValuesCondition(weekdays, Strength.REQUIRED))
            changed = true
        }
        parseTime(message)?.let { time ->
            result = result.copy(startTime = TimeCondition(time, time, Strength.REQUIRED))
            changed = true
        }
        return result.takeIf { changed || it != RecommendationConditions() }
    }

    fun requestedOptionId(message: String, state: AiConversationState): String? {
        val visibleCards = state.lastComparedCards.ifEmpty { state.lastCards }
        return when {
            FIRST_OPTION_PATTERN.containsMatchIn(message) && visibleCards.isNotEmpty() ->
                visibleCards.first().option.usageOptionId
            isRouteRequest(message) && state.selectedOptionId != null -> state.selectedOptionId
            else -> null
        }
    }

    fun isRouteRequest(message: String): Boolean = !rejectsPlaceTransition(message) && ROUTE_TERMS.any(message::contains)

    private fun parseWeekdays(message: String): Set<Weekday>? {
        if (message.contains("월수금")) return setOf(Weekday.MON, Weekday.WED, Weekday.FRI)
        val matched = WEEKDAY_TERMS.mapNotNull { (term, value) -> value.takeIf { message.contains(term) } }.toSet()
        return matched.takeIf(Set<Weekday>::isNotEmpty)
    }

    private fun parseTime(message: String): LocalTime? {
        val match = TIME_PATTERN.find(message) ?: return null
        val period = match.groupValues[1]
        var hour = match.groupValues[2].toIntOrNull() ?: return null
        if ((period == "오후" || period == "저녁") && hour in 1..11) hour += 12
        if (period == "오전" && hour == 12) hour = 0
        if (hour !in 0..23) return null
        return LocalTime.of(hour, 0)
    }

    private val SWIMMING_TERMS = listOf("수영", "수영장")
    private val FIRST_OPTION_PATTERN = Regex("(?:첫\\s*번째|(?<!\\d)1\\s*번(?!\\d)|이걸로|이것으로)")
    private val ROUTE_TERMS = listOf("경로", "길찾기", "가는 길")
    private val REJECT_PLACE_PATTERN = Regex("(?:경로|길찾기|장소|시설|지도)\\s*(?:는|가|이|도)?\\s*(?:말[고구]|아니|필요\\s*없|싫)")
    private val EXERCISE_PROPOSAL_PATTERN = Regex("(?:운동|루틴|스트레칭)\\s*(?:을|좀|이나)?\\s*(?:추천|제안|알려)|무슨\\s*운동|어떤\\s*운동")
    private val EXPLICIT_SEARCH_PATTERN = Regex("어디|근처|주변|찾아|찾을|찾고|장소|시설|프로그램|강습|강좌")
    private val WEEKDAY_TERMS = listOf(
        "월요일" to Weekday.MON,
        "화요일" to Weekday.TUE,
        "수요일" to Weekday.WED,
        "목요일" to Weekday.THU,
        "금요일" to Weekday.FRI,
        "토요일" to Weekday.SAT,
        "일요일" to Weekday.SUN,
    )
    private val TIME_PATTERN = Regex("(오전|오후|저녁)?\\s*(\\d{1,2})시")
}
