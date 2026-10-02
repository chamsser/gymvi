package io.github.chamsser.gymvi.recommendation

import io.github.chamsser.gymvi.catalog.ApiValidationException
import io.github.chamsser.gymvi.catalog.DatasetReference
import io.github.chamsser.gymvi.catalog.GymviApiException
import io.github.chamsser.gymvi.usage.EvidenceStateResponse
import io.github.chamsser.gymvi.usage.JoinedUsageOptionRecord
import io.github.chamsser.gymvi.usage.OperatorProgramTimeCatalog
import io.github.chamsser.gymvi.usage.OperatorTimeResponse
import io.github.chamsser.gymvi.usage.ProgramPrice
import io.github.chamsser.gymvi.usage.UsageOptionQueryCatalog
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class DataUsageOptionNotFoundException : GymviApiException(
    "DATA_USAGE_OPTION_NOT_FOUND", "요청한 이용 선택지를 찾을 수 없습니다.", false,
)

data class RecommendationResult<T>(val data: T, val facilityDataset: DatasetReference)

class RecommendationService(
    private val catalog: UsageOptionQueryCatalog,
    private val clock: Clock,
    private val engine: DeterministicRecommendationEngine = DeterministicRecommendationEngine(),
    private val operatorProgramTimeCatalog: OperatorProgramTimeCatalog = OperatorProgramTimeCatalog.EMPTY,
) {
    fun recommend(
        query: RecommendationQuery,
        requiredWeekdayCoverage: Set<Weekday> = emptySet(),
        minimumWeekdayCount: Int? = null,
    ): RecommendationResult<RecommendationData> {
        validateCommon(query.origin, query.conditions)
        if (minimumWeekdayCount != null && minimumWeekdayCount !in 1..7) {
            throw ApiValidationException("VALIDATION_MINIMUM_WEEKDAY_COUNT", "최소 운영 요일 수는 1부터 7까지입니다.")
        }
        if (query.area.maxLongitude - query.area.minLongitude > 0.5 ||
            query.area.maxLatitude - query.area.minLatitude > 0.5
        ) throw ApiValidationException("VALIDATION_AREA_TOO_LARGE", "추천 지역 범위가 너무 넓습니다.")
        if (query.limit !in 1..50) throw ApiValidationException("VALIDATION_LIMIT", "결과 제한은 1부터 50까지입니다.")
        if (query.maxPerFacility !in 1..10) throw ApiValidationException("VALIDATION_MAX_PER_FACILITY", "시설별 제한은 1부터 10까지입니다.")
        validateIds(query.preferences.favoriteFacilityIds.toList(), "VALIDATION_PREFERENCES")
        validateIds(query.preferences.recentFacilityIds.toList(), "VALIDATION_PREFERENCES")
        if (query.preferences.favoriteFacilityIds.size > 20 || query.preferences.recentFacilityIds.size > 20) {
            throw ApiValidationException("VALIDATION_PREFERENCES", "선호 시설은 각 목록에 최대 20개까지 지정할 수 있습니다.")
        }
        val date = query.date ?: LocalDate.now(clock.withZone(SEOUL))
        val result = catalog.findInArea(query.area, CANDIDATE_CAP, date)
        if (result.records.size > CANDIDATE_CAP) throw ApiValidationException("VALIDATION_AREA_TOO_DENSE", "추천 지역의 이용 선택지가 너무 많습니다.")
        val operatorTimes = result.records.associate { record ->
            record.option.usageOptionId to operatorProgramTimeCatalog.find(record.option)
        }
        val evaluations = result.records.map { record ->
            record to engine.evaluate(
                record,
                query.conditions,
                query.preferences,
                date,
                query.origin,
                operatorTimes[record.option.usageOptionId],
                requiredWeekdayCoverage,
                minimumWeekdayCount,
            )
        }
        val eligible = evaluations.filter { it.second.eligible }.sortedWith(EVALUATION_ORDER)
        val perFacility = mutableMapOf<String, Int>()
        val selected = eligible.filter { (record, _) ->
            val count = perFacility.getOrDefault(record.option.facilityId, 0)
            if (count >= query.maxPerFacility) false else {
                perFacility[record.option.facilityId] = count + 1
                true
            }
        }.take(query.limit)
        val excluded = evaluations.filterNot { it.second.eligible }.map { (record, evaluated) ->
            ExcludedOption(record.option.usageOptionId, record.option.facilityId, evaluated.exclusionReasonCodes)
        }.sortedBy(ExcludedOption::usageOptionId)
        val summary = excluded.flatMap(ExcludedOption::reasonCodes).groupingBy { it }.eachCount()
            .map { (code, count) -> ExclusionCount(code, count) }
            .sortedWith(compareByDescending<ExclusionCount> { it.count }.thenBy { it.reasonCode })
        return RecommendationResult(
            RecommendationData(
                date, query.origin != null, result.programDatasetVersion, result.programAsOf, result.attribution,
                RecommendationCounts(evaluations.size, eligible.size, excluded.size, selected.size),
                selected.mapIndexed { index, (record, evaluated) -> RecommendationItem(
                    index + 1, evaluated.score!!, evaluated.scoreComponents, evaluated.matchedConditions,
                    evaluated.unmetPreferredConditions, evaluated.uncertaintyCodes, evaluated.facts,
                    record.toResponse(operatorTimes[record.option.usageOptionId]),
                ) },
                excluded.take(50), excluded.size > 50, summary,
                if (result.programDatasetVersion == null) listOf("PROGRAM_DATASET_NOT_ACTIVE") else emptyList(),
            ),
            result.facilityDataset,
        )
    }

    fun compare(query: ComparisonQuery): RecommendationResult<ComparisonData> {
        validateCommon(query.origin, query.conditions)
        if (query.usageOptionIds.size !in 2..5 || query.usageOptionIds.distinct().size != query.usageOptionIds.size) {
            throw ApiValidationException("VALIDATION_USAGE_OPTION_IDS", "서로 다른 이용 선택지 ID를 2개부터 5개까지 지정해야 합니다.")
        }
        validateIds(query.usageOptionIds, "VALIDATION_USAGE_OPTION_IDS")
        val date = query.date ?: LocalDate.now(clock.withZone(SEOUL))
        val result = catalog.findByIds(query.usageOptionIds)
        if (result.programDatasetVersion != null && result.records.map { it.option.usageOptionId }.toSet() != query.usageOptionIds.toSet()) {
            throw DataUsageOptionNotFoundException()
        }
        val byId = result.records.associateBy { it.option.usageOptionId }
        val items = query.usageOptionIds.mapNotNull(byId::get).map { record ->
            val operatorTime = operatorProgramTimeCatalog.find(record.option)
            val evaluation = engine.evaluate(
                record,
                query.conditions,
                RecommendationPreferences(),
                date,
                query.origin,
                operatorTime,
            )
            ComparisonItem(record.option.usageOptionId, evaluation.eligible, evaluation.exclusionReasonCodes,
                evaluation.score, evaluation.scoreComponents, evaluation.matchedConditions,
                evaluation.unmetPreferredConditions, evaluation.uncertaintyCodes, evaluation.facts,
                record.toResponse(operatorTime))
        }
        return RecommendationResult(
            ComparisonData(date, query.origin != null, result.programDatasetVersion, result.programAsOf, result.attribution,
                items, comparisonDimensions(items, query.origin != null),
                if (result.programDatasetVersion == null) listOf("PROGRAM_DATASET_NOT_ACTIVE") else emptyList()),
            result.facilityDataset,
        )
    }

    private fun comparisonDimensions(items: List<ComparisonItem>, withOrigin: Boolean): List<ComparisonDimension> {
        fun dimension(name: String, value: (ComparisonItem) -> Any?, best: Boolean = false): ComparisonDimension {
            val values = items.map { item ->
                val current = value(item)
                DimensionValue(item.usageOptionId, if (current == null) "UNKNOWN" else "KNOWN", current)
            }
            val minimum = if (best) values.mapNotNull { it.value as? Comparable<Any> }.minOrNull() else null
            return ComparisonDimension(name, values, if (minimum == null) emptyList() else values.filter { it.value == minimum }.map(DimensionValue::usageOptionId))
        }
        return buildList {
            add(dimension("PRICE_WON", { it.option.priceWon }).copy(bestUsageOptionIds = lowestComparablePrice(items)))
            if (withOrigin) add(dimension("STRAIGHT_LINE_DISTANCE_METERS", { it.facts.straightLineDistanceMeters.value }, best = true))
            add(dimension("START_TIME", { it.facts.startTime.value }, best = true))
            add(dimension("WEEKDAYS", { it.option.weekdays.takeIf(List<String>::isNotEmpty) }))
            add(dimension("PERIOD", { it.facts.period.value }))
            add(dimension("PROGRAM_APPLICATION", { it.option.programApplication.state.takeUnless { state -> state == "UNKNOWN" } }))
            add(dimension("TARGET_GROUPS", { it.facts.targetGroups.values }))
            add(dimension("LEVEL", { it.facts.level.values }))
        }
    }

    /**
     * A verified free 0 is the lowest price because amounts are never negative. Otherwise the lowest positive
     * price needs every compared amount known and one shared parsed unit; units are never mixed or converted.
     */
    private fun lowestComparablePrice(items: List<ComparisonItem>): List<String> {
        val free = items.filter { it.option.priceWon == 0 }
        if (free.isNotEmpty()) return free.map(ComparisonItem::usageOptionId)
        if (items.any { it.option.priceWon == null }) return emptyList()
        if (items.map { ProgramPrice.parseUnit(it.option.priceTypeName) }.distinct().singleOrNull() == null) return emptyList()
        val minimum = items.minOf { it.option.priceWon!! }
        return items.filter { it.option.priceWon == minimum }.map(ComparisonItem::usageOptionId)
    }

    private fun validateCommon(origin: Origin?, conditions: RecommendationConditions) {
        if (origin != null && (!origin.latitude.isFinite() || !origin.longitude.isFinite() ||
                origin.latitude !in 33.0..39.5 || origin.longitude !in 124.0..132.0)) {
            throw ApiValidationException("VALIDATION_ORIGIN_OUTSIDE_KOREA", "출발 좌표가 대한민국 범위를 벗어났습니다.")
        }
        if (conditions.maxDistanceMeters != null && origin == null) {
            throw ApiValidationException("VALIDATION_ORIGIN_REQUIRED", "거리 조건에는 출발 좌표가 필요합니다.")
        }
        if (conditions.maxDistanceMeters != null && conditions.maxDistanceMeters.value !in 100..100_000) {
            throw ApiValidationException("VALIDATION_MAX_DISTANCE_METERS", "거리 상한은 100부터 100000미터까지입니다.")
        }
        if (conditions.maxPriceWon != null && conditions.maxPriceWon.value !in 0..10_000_000) {
            throw ApiValidationException("VALIDATION_MAX_PRICE_WON", "가격 상한은 0부터 10000000원까지입니다.")
        }
        if (conditions.startTime != null && conditions.startTime.earliest > conditions.startTime.latest) {
            throw ApiValidationException("VALIDATION_START_TIME", "시작 시각 범위가 올바르지 않습니다.")
        }
        if (conditions.categories?.values?.isEmpty() == true || conditions.weekdays?.values?.isEmpty() == true ||
            conditions.targetGroups?.values?.isEmpty() == true) {
            throw ApiValidationException("VALIDATION_CONDITIONS", "조건 값 목록은 비어 있을 수 없습니다.")
        }
    }

    private fun validateIds(ids: List<String>, code: String) {
        if (ids.any { it.isBlank() || it.length > 160 }) throw ApiValidationException(code, "ID 형식이 올바르지 않습니다.")
    }

    private fun JoinedUsageOptionRecord.toResponse(operatorTime: OperatorTimeResponse?): RecommendationOption = option.let {
        RecommendationOption(it.usageOptionId, it.facilityId, it.programName, it.programTypeName,
            it.targetName, it.beginDate, it.endDate, it.weekdays, it.sourceTimeValue, operatorTime, it.recruitmentCount,
            it.price.amountWon, it.priceTypeName, it.homepageUrl,
            EvidenceStateResponse(it.operationState, it.operationReasonCode),
            EvidenceStateResponse(it.applicationState, it.applicationReasonCode), it.joinState,
            "/api/v1/evidence/program:${it.usageOptionId}", facilityName, facilityTypeName,
            roadAddress, longitude, latitude)
    }

    private companion object {
        const val CANDIDATE_CAP = 5_000
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        // Price is not a tie-break: monthly, per-session and package amounts are not one cost scale.
        val EVALUATION_ORDER = compareByDescending<Pair<JoinedUsageOptionRecord, EvaluatedOption>> { it.second.score }
            .thenBy { it.second.facts.straightLineDistanceMeters.value ?: Int.MAX_VALUE }
            .thenBy { it.first.option.usageOptionId }
    }
}
