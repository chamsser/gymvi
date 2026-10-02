package io.github.chamsser.gymvi.ui

import androidx.annotation.StringRes
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AiAgeBand
import io.github.chamsser.gymvi.data.AiExerciseExperience
import io.github.chamsser.gymvi.data.AiExerciseGoal
import io.github.chamsser.gymvi.data.AiLocalProfile
import io.github.chamsser.gymvi.data.AiMemoryFacts
import java.math.BigDecimal
import java.math.RoundingMode

/*
 * What the profile and memory editors accept, kept apart from the screens so it runs on the JVM.
 * A draft turns into a profile or memory only when every field passes AiLocalProfile's checks and
 * the library store's memory checks, so saving never hands the store a value it would reject.
 */

/** AiLocalProfile's accepted height and weight. */
internal val AiProfileHeightRange = 50..250
internal val AiProfileWeightRange = 10.0..350.0

/** The memory store's accepted price ceiling in won and travel distance in metres. */
internal val AiMemoryPriceRange = 0..10_000_000
internal val AiMemoryDistanceRange = 100..100_000

/** One allowlisted memory code, the label its chip shows and the name a screen reader says. */
internal data class AiMemoryChoice(
    val code: String,
    @get:StringRes val label: Int,
    @get:StringRes val spokenLabel: Int = label,
)

/** The store's exercise categories, in the order the editor offers them. */
internal val AiMemoryCategoryChoices = listOf(
    AiMemoryChoice("SWIMMING", R.string.my_memory_category_swimming),
    AiMemoryChoice("FITNESS", R.string.my_memory_category_fitness),
    AiMemoryChoice("YOGA_PILATES", R.string.my_memory_category_yoga_pilates),
    AiMemoryChoice("DANCE", R.string.my_memory_category_dance),
    AiMemoryChoice("TENNIS", R.string.my_memory_category_tennis),
    AiMemoryChoice("BADMINTON", R.string.my_memory_category_badminton),
    AiMemoryChoice("TABLE_TENNIS", R.string.my_memory_category_table_tennis),
    AiMemoryChoice("GOLF", R.string.my_memory_category_golf),
    AiMemoryChoice("SQUASH", R.string.my_memory_category_squash),
    AiMemoryChoice("SKATING", R.string.my_memory_category_skating),
    AiMemoryChoice("TEAM_BALL", R.string.my_memory_category_team_ball),
    AiMemoryChoice("MARTIAL_ARTS", R.string.my_memory_category_martial_arts),
    AiMemoryChoice("CLIMBING", R.string.my_memory_category_climbing),
)

/** The store's weekdays from Monday. Chips show one syllable; screen readers say the whole day. */
internal val AiMemoryWeekdayChoices = listOf(
    AiMemoryChoice("MON", R.string.my_memory_weekday_mon, R.string.my_memory_weekday_mon_full),
    AiMemoryChoice("TUE", R.string.my_memory_weekday_tue, R.string.my_memory_weekday_tue_full),
    AiMemoryChoice("WED", R.string.my_memory_weekday_wed, R.string.my_memory_weekday_wed_full),
    AiMemoryChoice("THU", R.string.my_memory_weekday_thu, R.string.my_memory_weekday_thu_full),
    AiMemoryChoice("FRI", R.string.my_memory_weekday_fri, R.string.my_memory_weekday_fri_full),
    AiMemoryChoice("SAT", R.string.my_memory_weekday_sat, R.string.my_memory_weekday_sat_full),
    AiMemoryChoice("SUN", R.string.my_memory_weekday_sun, R.string.my_memory_weekday_sun_full),
)

@get:StringRes
internal val AiAgeBand.labelRes: Int
    get() = when (this) {
        AiAgeBand.YOUTH -> R.string.my_profile_age_youth
        AiAgeBand.ADULT -> R.string.my_profile_age_adult
        AiAgeBand.SENIOR -> R.string.my_profile_age_senior
    }

@get:StringRes
internal val AiExerciseExperience.labelRes: Int
    get() = when (this) {
        AiExerciseExperience.BEGINNER -> R.string.my_profile_experience_beginner
        AiExerciseExperience.REGULAR -> R.string.my_profile_experience_regular
        AiExerciseExperience.EXPERIENCED -> R.string.my_profile_experience_experienced
    }

@get:StringRes
internal val AiExerciseGoal.labelRes: Int
    get() = when (this) {
        AiExerciseGoal.GENERAL_FITNESS -> R.string.my_profile_goal_general_fitness
        AiExerciseGoal.STRENGTH -> R.string.my_profile_goal_strength
        AiExerciseGoal.FLEXIBILITY -> R.string.my_profile_goal_flexibility
        AiExerciseGoal.ENDURANCE -> R.string.my_profile_goal_endurance
        AiExerciseGoal.WEIGHT_MANAGEMENT -> R.string.my_profile_goal_weight_management
    }

/** A typed field: left empty, a value the data accepts, or something to correct before saving. */
internal sealed interface AiDraftField<out T> {
    data object Blank : AiDraftField<Nothing>
    data object Invalid : AiDraftField<Nothing>
    data class Valid<out T>(val value: T) : AiDraftField<T>
}

internal fun <T : Any> aiDraftField(text: String, parse: (String) -> T?): AiDraftField<T> = when {
    text.isBlank() -> AiDraftField.Blank
    else -> parse(text)?.let { AiDraftField.Valid(it) } ?: AiDraftField.Invalid
}

internal fun <T> AiDraftField<T>.valueOrNull(): T? = (this as? AiDraftField.Valid<T>)?.value

/** Keeps up to [maxDigits] ASCII digits, dropping anything else a keyboard or a paste brings. */
internal fun aiDigitsInput(text: String, maxDigits: Int): String = text.filter { it in '0'..'9' }.take(maxDigits)

/** Keeps a decimal number with at most [integerDigits] before the point and [fractionDigits] after it. */
internal fun aiDecimalInput(text: String, integerDigits: Int, fractionDigits: Int): String {
    val kept = StringBuilder()
    var point = false
    var integers = 0
    var fractions = 0
    for (char in text) {
        when {
            char in '0'..'9' && !point && integers < integerDigits -> {
                kept.append(char)
                integers++
            }
            char in '0'..'9' && point && fractions < fractionDigits -> {
                kept.append(char)
                fractions++
            }
            char == '.' && !point && fractionDigits > 0 -> {
                kept.append(char)
                point = true
            }
        }
    }
    return kept.toString()
}

internal fun aiProfileHeightOrNull(text: String): Int? = text.toIntOrNull()?.takeIf { it in AiProfileHeightRange }

internal fun aiProfileWeightOrNull(text: String): Double? =
    text.toBigDecimalOrNull()?.toDouble()?.takeIf { it.isFinite() && it in AiProfileWeightRange }

/** A number without trailing zeros: 70.0 as 70 and 68.50 as 68.5. */
internal fun aiDecimalText(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

internal fun aiMemoryPriceOrNull(text: String): Int? = text.toIntOrNull()?.takeIf { it in AiMemoryPriceRange }

/** Typed kilometres as the store's whole metres, or null outside what the store accepts. */
internal fun aiMemoryDistanceOrNull(kilometers: String): Int? {
    val meters = kilometers.toBigDecimalOrNull()?.movePointRight(3)?.setScale(0, RoundingMode.HALF_UP) ?: return null
    val accepted = meters >= BigDecimal(AiMemoryDistanceRange.first) && meters <= BigDecimal(AiMemoryDistanceRange.last)
    return if (accepted) meters.toInt() else null
}

internal fun aiKilometersText(meters: Int): String =
    BigDecimal(meters).movePointLeft(3).stripTrailingZeros().toPlainString()

/** Digits with thousands separators, as 50,000. */
internal fun aiGroupedDigits(digits: String): String = digits.reversed().chunked(3).joinToString(",").reversed()

/** Four typed digits as the store's HH:mm, or null while they are not a time of day. */
internal fun aiMemoryTimeOrNull(digits: String): String? {
    if (digits.length != 4 || digits.any { it !in '0'..'9' }) return null
    val hours = digits.substring(0, 2)
    val minutes = digits.substring(2)
    return if (hours.toInt() <= 23 && minutes.toInt() <= 59) "$hours:$minutes" else null
}

/** A stored HH:mm as the digits its field edits. */
internal fun aiMemoryTimeDigits(stored: String?): String = stored.orEmpty().filter { it in '0'..'9' }.take(4)

/** How the time field shows its digits while they are typed: 18, 180 as 18:0, 1800 as 18:00. */
internal fun aiMemoryTimeShown(digits: String): String =
    if (digits.length <= 2) digits else "${digits.substring(0, 2)}:${digits.substring(2)}"

internal enum class AiMemoryTimeProblem { FORMAT, PAIR, ORDER }

/**
 * What is wrong with the earliest and the latest start, each null when that field is fine. The
 * store keeps both times or neither, and the earliest may equal but not pass the latest.
 */
internal fun aiMemoryTimeProblems(
    earliest: AiDraftField<String>,
    latest: AiDraftField<String>,
): Pair<AiMemoryTimeProblem?, AiMemoryTimeProblem?> {
    if (earliest is AiDraftField.Invalid || latest is AiDraftField.Invalid) {
        return (if (earliest is AiDraftField.Invalid) AiMemoryTimeProblem.FORMAT else null) to
            (if (latest is AiDraftField.Invalid) AiMemoryTimeProblem.FORMAT else null)
    }
    return when {
        // Zero-padded HH:mm compares in time order.
        earliest is AiDraftField.Valid && latest is AiDraftField.Valid ->
            (if (earliest.value > latest.value) AiMemoryTimeProblem.ORDER else null) to null
        earliest is AiDraftField.Valid -> null to AiMemoryTimeProblem.PAIR
        latest is AiDraftField.Valid -> AiMemoryTimeProblem.PAIR to null
        else -> null to null
    }
}

/** The profile editor's fields as typed. */
internal data class AiProfileDraft(
    val ageBand: AiAgeBand? = null,
    val heightCm: String = "",
    val weightKg: String = "",
    val experience: AiExerciseExperience? = null,
    val goal: AiExerciseGoal? = null,
) {
    val height: AiDraftField<Int> get() = aiDraftField(heightCm, ::aiProfileHeightOrNull)
    val weight: AiDraftField<Double> get() = aiDraftField(weightKg, ::aiProfileWeightOrNull)
    val isBlank: Boolean
        get() = ageBand == null && heightCm.isBlank() && weightKg.isBlank() && experience == null && goal == null

    /** The profile to save, or null while the height or weight still needs correcting. */
    fun toProfileOrNull(): AiLocalProfile? {
        val height = height
        val weight = weight
        if (height is AiDraftField.Invalid || weight is AiDraftField.Invalid) return null
        return AiLocalProfile(ageBand, height.valueOrNull(), weight.valueOrNull(), experience, goal)
    }

    companion object {
        fun from(profile: AiLocalProfile) = AiProfileDraft(
            ageBand = profile.ageBand,
            heightCm = profile.heightCm?.toString().orEmpty(),
            weightKg = profile.weightKg?.let(::aiDecimalText).orEmpty(),
            experience = profile.experience,
            goal = profile.goal,
        )
    }
}

/** The memory editor's fields as typed: times as digits, the price in won and the distance in km. */
internal data class AiMemoryDraft(
    val categories: Set<String> = emptySet(),
    val weekdays: Set<String> = emptySet(),
    val earliestStart: String = "",
    val latestStart: String = "",
    val maxPriceWon: String = "",
    val maxDistanceKm: String = "",
    val beginner: Boolean = false,
) {
    val earliest: AiDraftField<String> get() = aiDraftField(earliestStart, ::aiMemoryTimeOrNull)
    val latest: AiDraftField<String> get() = aiDraftField(latestStart, ::aiMemoryTimeOrNull)
    val price: AiDraftField<Int> get() = aiDraftField(maxPriceWon, ::aiMemoryPriceOrNull)
    val distance: AiDraftField<Int> get() = aiDraftField(maxDistanceKm, ::aiMemoryDistanceOrNull)
    val timeProblems: Pair<AiMemoryTimeProblem?, AiMemoryTimeProblem?> get() = aiMemoryTimeProblems(earliest, latest)

    /** True when saving would keep a memory with nothing in it; deleting it is the way out. */
    val isBlank: Boolean
        get() = categories.isEmpty() && weekdays.isEmpty() && earliestStart.isBlank() && latestStart.isBlank() &&
            maxPriceWon.isBlank() && maxDistanceKm.isBlank() && !beginner

    /** The facts to save, or null while a field still needs correcting. */
    fun toFactsOrNull(): AiMemoryFacts? {
        val (earliestProblem, latestProblem) = timeProblems
        val price = price
        val distance = distance
        if (earliestProblem != null || latestProblem != null) return null
        if (price is AiDraftField.Invalid || distance is AiDraftField.Invalid) return null
        return AiMemoryFacts(
            categories = categories,
            weekdays = weekdays,
            earliestStart = earliest.valueOrNull(),
            latestStart = latest.valueOrNull(),
            maxPriceWon = price.valueOrNull(),
            maxDistanceMeters = distance.valueOrNull(),
            beginner = beginner,
        )
    }

    companion object {
        fun from(facts: AiMemoryFacts) = AiMemoryDraft(
            categories = facts.categories,
            weekdays = facts.weekdays,
            earliestStart = aiMemoryTimeDigits(facts.earliestStart),
            latestStart = aiMemoryTimeDigits(facts.latestStart),
            maxPriceWon = facts.maxPriceWon?.toString().orEmpty(),
            maxDistanceKm = facts.maxDistanceMeters?.let(::aiKilometersText).orEmpty(),
            beginner = facts.beginner,
        )
    }
}
