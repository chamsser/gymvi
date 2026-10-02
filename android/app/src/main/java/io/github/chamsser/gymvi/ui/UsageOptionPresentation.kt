package io.github.chamsser.gymvi.ui

import androidx.annotation.StringRes
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.ProgramEvidenceField
import io.github.chamsser.gymvi.data.UsageOptionItem
import io.github.chamsser.gymvi.data.UsageOptionState
import io.github.chamsser.gymvi.data.FeedUsageOption
import io.github.chamsser.gymvi.data.OperatorTime
import io.github.chamsser.gymvi.data.UsageOptionComparisonDimension
import io.github.chamsser.gymvi.data.UsageOptionComparisonItem
import io.github.chamsser.gymvi.data.UsageOptionComparisonValue
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class UsageOptionPeriod {
    CURRENT,
    UPCOMING,
    UNKNOWN,
    ENDED,
}

internal fun usageOptionPeriod(option: UsageOptionItem, today: LocalDate): UsageOptionPeriod = when {
    option.endDate != null && option.endDate.isBefore(today) -> UsageOptionPeriod.ENDED
    option.beginDate != null && option.beginDate.isAfter(today) -> UsageOptionPeriod.UPCOMING
    option.beginDate != null && option.endDate != null -> UsageOptionPeriod.CURRENT
    else -> UsageOptionPeriod.UNKNOWN
}

internal fun visibleUsageOptions(options: List<UsageOptionItem>, today: LocalDate): List<UsageOptionItem> =
    listOf(UsageOptionPeriod.CURRENT, UsageOptionPeriod.UPCOMING, UsageOptionPeriod.UNKNOWN)
        .flatMap { period -> options.filter { usageOptionPeriod(it, today) == period } }

internal fun previewUsageOptions(options: List<UsageOptionItem>, today: LocalDate): List<UsageOptionItem> =
    visibleUsageOptions(options, today).take(3)

internal fun visibleFeedUsageOptions(
    options: List<FeedUsageOption>,
    knownFacilityIds: Set<String>,
): List<FeedUsageOption> = options.filter { it.option.facilityId in knownFacilityIds }

internal fun <T> withoutFacilityIds(
    items: List<T>,
    excludedIds: Set<String>,
    facilityId: (T) -> String,
): List<T> = items.filterNot { facilityId(it) in excludedIds }

internal fun formatUsageOptionTime(option: UsageOptionItem): String? =
    option.sourceTimeValue ?: option.operatorTime?.let { "${it.startTime}~${it.endTime}" }

internal fun formatOperatorTimeCheckedDate(time: OperatorTime): String =
    time.checkedAt.atZoneSameInstant(ZoneId.of("Asia/Seoul"))
        .format(DateTimeFormatter.ofPattern("yyyy. MM. dd", Locale.KOREAN))

internal fun formatUsageOptionWeekdays(weekdays: List<String>): String? =
    weekdays.takeIf(List<String>::isNotEmpty)?.joinToString(", ")

internal fun formatOperatorTimeDays(days: String?): String? {
    val value = days?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return if (value.all { it in "월화수목금토일" }) {
        value.map(Char::toString).joinToString(", ")
    } else {
        value
    }
}

internal fun formatUsageOptionDate(date: LocalDate, today: LocalDate): String =
    if (date.year == today.year) {
        String.format(Locale.KOREAN, "%d월 %d일", date.monthValue, date.dayOfMonth)
    } else {
        String.format(Locale.KOREAN, "%d년 %d월 %d일", date.year, date.monthValue, date.dayOfMonth)
    }

internal fun formatUsageOptionDateRange(
    beginDate: LocalDate?,
    endDate: LocalDate?,
    today: LocalDate,
): String? = when {
    beginDate != null && endDate != null ->
        "${formatUsageOptionDate(beginDate, today)} ~ ${formatUsageOptionDate(endDate, today)}"
    beginDate != null -> "${formatUsageOptionDate(beginDate, today)} ~"
    endDate != null -> "~ ${formatUsageOptionDate(endDate, today)}"
    else -> null
}

internal fun formatUsageOptionPrice(priceWon: Int?): String? =
    priceWon?.let { String.format(Locale.KOREAN, "%,d원", it) }

internal fun formatUsageOptionRecruitment(count: Int?): String? = count?.let { "${it}명" }

@StringRes
internal fun usageOptionApplicationLabel(state: UsageOptionState, card: Boolean): Int = when (state.state) {
    "AVAILABLE" -> R.string.usage_option_application_available
    "CLOSED" -> R.string.usage_option_application_closed
    else -> if (card) R.string.usage_option_application_unknown else R.string.unknown_value
}

@StringRes
internal fun usageOptionOperationLabel(state: UsageOptionState): Int? = when (state.state) {
    "OPEN" -> R.string.operation_open
    "CLOSED" -> R.string.operation_closed
    else -> null
}

@StringRes
internal fun usageOptionRawFieldLabel(field: String): Int? = when (field) {
    "program_type_name" -> R.string.usage_option_raw_type
    "program_name" -> R.string.usage_option_raw_name
    "target_name" -> R.string.usage_option_target_label
    "begin_date" -> R.string.usage_option_raw_begin
    "end_date" -> R.string.usage_option_raw_end
    "weekdays" -> R.string.usage_option_weekdays_label
    "source_time_value" -> R.string.usage_option_time_label
    "recruitment_count" -> R.string.usage_option_recruitment_label
    "price_won" -> R.string.usage_option_price_label
    "price_type_name" -> R.string.usage_option_price_type_label
    "homepage_url" -> R.string.usage_option_homepage_label
    else -> null
}

internal fun usageOptionRawRows(fields: List<ProgramEvidenceField>): List<Pair<Int, String?>> =
    fields.mapNotNull { field ->
        usageOptionRawFieldLabel(field.field)?.let { label -> label to field.sourceValue }
    }

internal fun formatProgramAsOf(asOf: String?): String? = asOf?.let { raw ->
    runCatching {
        Instant.parse(raw)
            .atZone(ZoneId.of("Asia/Seoul"))
            .format(DateTimeFormatter.ofPattern("yyyy. MM. dd", Locale.KOREAN))
    }.getOrNull()
}

internal val comparisonDimensionOrder = listOf(
    "PRICE_WON", "START_TIME", "WEEKDAYS", "PERIOD", "PROGRAM_APPLICATION", "TARGET_GROUPS", "LEVEL",
)

@StringRes
internal fun comparisonDimensionLabel(dimension: String): Int? = when (dimension) {
    "PRICE_WON" -> R.string.usage_option_price_label
    "START_TIME" -> R.string.usage_option_compare_start_time
    "WEEKDAYS" -> R.string.usage_option_weekdays_label
    "PERIOD" -> R.string.usage_option_period_label
    "PROGRAM_APPLICATION" -> R.string.usage_option_application_label
    "TARGET_GROUPS" -> R.string.usage_option_target_label
    "LEVEL" -> R.string.usage_option_compare_level
    else -> null
}

internal data class ComparisonValuePresentation(
    val text: String? = null,
    val resourceIds: List<Int> = emptyList(),
    val known: Boolean = false,
)

internal fun comparisonValuePresentation(
    dimension: String,
    entry: UsageOptionComparisonValue?,
): ComparisonValuePresentation {
    val unknown = ComparisonValuePresentation(resourceIds = listOf(R.string.unknown_value))
    if (entry?.state != "KNOWN" || entry.value == null) return unknown
    val value = entry.value
    return when (dimension) {
        "PRICE_WON" -> {
            val amount = (value as? Number)?.toString()?.toLongOrNull()?.takeIf { it >= 0 }
            amount?.let { ComparisonValuePresentation(String.format(Locale.KOREAN, "%,d원", it), known = true) }
                ?: unknown
        }
        "START_TIME" -> {
            val raw = value as? String
            val valid = raw?.matches(Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?$")) == true
            if (valid) {
                ComparisonValuePresentation(
                    LocalTime.parse(raw).format(DateTimeFormatter.ofPattern("HH:mm")),
                    known = true,
                )
            } else unknown
        }
        "WEEKDAYS" -> (value as? List<*>)?.takeIf { it.isNotEmpty() && it.all { day -> day is String && day.isNotBlank() } }
            ?.let { ComparisonValuePresentation(it.joinToString(", "), known = true) } ?: unknown
        "PERIOD" -> when (value) {
            "CURRENT" -> ComparisonValuePresentation(resourceIds = listOf(R.string.usage_option_current), known = true)
            "UPCOMING" -> ComparisonValuePresentation(resourceIds = listOf(R.string.usage_option_upcoming), known = true)
            "ENDED" -> ComparisonValuePresentation(resourceIds = listOf(R.string.usage_option_compare_ended), known = true)
            else -> unknown
        }
        "PROGRAM_APPLICATION" -> when (value) {
            "AVAILABLE" -> ComparisonValuePresentation(resourceIds = listOf(R.string.usage_option_application_available), known = true)
            "CLOSED" -> ComparisonValuePresentation(resourceIds = listOf(R.string.usage_option_application_closed), known = true)
            else -> unknown
        }
        "TARGET_GROUPS", "LEVEL" -> {
            val codes = value as? List<*>
            val labels = codes?.map { code ->
                if (dimension == "TARGET_GROUPS") comparisonTargetLabel(code)
                else comparisonLevelLabel(code)
            }
            labels?.takeIf { it.isNotEmpty() && it.none { id -> id == null } }
                ?.let { ComparisonValuePresentation(resourceIds = it.filterNotNull(), known = true) } ?: unknown
        }
        else -> unknown
    }
}

@StringRes
private fun comparisonTargetLabel(value: Any?): Int? = when (value) {
    "CHILD" -> R.string.usage_option_compare_child
    "YOUTH" -> R.string.usage_option_compare_youth
    "ADULT" -> R.string.usage_option_compare_adult
    "SENIOR" -> R.string.usage_option_compare_senior
    else -> null
}

@StringRes
private fun comparisonLevelLabel(value: Any?): Int? = when (value) {
    "BEGINNER" -> R.string.usage_option_compare_beginner
    "INTERMEDIATE" -> R.string.usage_option_compare_intermediate
    "ADVANCED" -> R.string.usage_option_compare_advanced
    else -> null
}

internal fun comparisonPriceMarkers(
    dimension: UsageOptionComparisonDimension,
    visibleIds: Set<String> = dimension.values.mapTo(mutableSetOf()) { it.usageOptionId },
): Set<String> {
    if (dimension.dimension != "PRICE_WON") return emptySet()
    val known = dimension.values.filter {
        it.usageOptionId in visibleIds && comparisonValuePresentation("PRICE_WON", it).known
    }
    val knownIds = known.mapTo(mutableSetOf()) { it.usageOptionId }
    val best = dimension.bestUsageOptionIds.toSet().intersect(knownIds)
    return if (best.isNotEmpty() && best.size < known.size) best
    else emptySet()
}

internal fun eligibleComparisonItems(
    items: List<UsageOptionComparisonItem>,
    ids: List<String>,
): List<UsageOptionComparisonItem> = ids.mapNotNull { id ->
    items.firstOrNull { it.usageOptionId == id && it.eligible }
}
