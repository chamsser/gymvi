package io.github.chamsser.gymvi.recommendation

import io.github.chamsser.gymvi.catalog.BoundingBox
import io.github.chamsser.gymvi.usage.OperatorTimeResponse
import io.github.chamsser.gymvi.usage.UsageOptionItemResponse
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class Category { SWIMMING, FITNESS, YOGA_PILATES, DANCE, TENNIS, BADMINTON, TABLE_TENNIS, GOLF, SQUASH, SKATING, TEAM_BALL, MARTIAL_ARTS, CLIMBING }
enum class Weekday { MON, TUE, WED, THU, FRI, SAT, SUN }
enum class TargetGroup { CHILD, YOUTH, ADULT, SENIOR }
enum class Level { BEGINNER, INTERMEDIATE, ADVANCED }
enum class Period { ENDED, UPCOMING, CURRENT }
enum class Strength { REQUIRED, PREFERRED }
enum class ConditionOutcome { MET, NOT_MET, UNKNOWN }

data class ValuesCondition<T>(val values: Set<T>, val strength: Strength)
data class TimeCondition(val earliest: LocalTime, val latest: LocalTime, val strength: Strength)
data class NumberCondition(val value: Int, val strength: Strength)

/** A billing unit a price limit can name: exactly one month or exactly one session. */
enum class PriceConditionUnit { MONTH, SESSION }

/**
 * A price limit. Without [unit] it compares the amount as published, as before. With a unit only options
 * billed for exactly that unit can meet it; 70000 per month, per session and without a unit are different
 * conditions.
 */
data class PriceCondition(val value: Int, val strength: Strength, val unit: PriceConditionUnit? = null)
data class FlagCondition(val strength: Strength)
data class RecommendationConditions(
    val categories: ValuesCondition<Category>? = null,
    val weekdays: ValuesCondition<Weekday>? = null,
    val startTime: TimeCondition? = null,
    val maxPriceWon: PriceCondition? = null,
    val maxDistanceMeters: NumberCondition? = null,
    val targetGroups: ValuesCondition<TargetGroup>? = null,
    val beginner: FlagCondition? = null,
    val applicationAvailable: FlagCondition? = null,
)
data class RecommendationPreferences(
    val favoriteFacilityIds: Set<String> = emptySet(),
    val recentFacilityIds: Set<String> = emptySet(),
)
data class Origin(val latitude: Double, val longitude: Double)
data class RecommendationQuery(
    val area: BoundingBox,
    val origin: Origin? = null,
    val date: LocalDate? = null,
    val conditions: RecommendationConditions = RecommendationConditions(),
    val preferences: RecommendationPreferences = RecommendationPreferences(),
    val limit: Int = 20,
    val maxPerFacility: Int = 3,
)
data class ComparisonQuery(
    val usageOptionIds: List<String>,
    val origin: Origin? = null,
    val date: LocalDate? = null,
    val conditions: RecommendationConditions = RecommendationConditions(),
)

data class FactBasis(val sourceField: String, val sourceValue: String?, val matchedTerms: List<String>)
data class DerivedFact<T>(
    val state: String,
    val value: T? = null,
    val values: List<T>? = null,
    val reasonCode: String? = null,
    val basis: FactBasis? = null,
)
data class ProgramFacts(
    val category: DerivedFact<Category>,
    val level: DerivedFact<Level>,
    val targetGroups: DerivedFact<TargetGroup>,
    val startTime: DerivedFact<LocalTime>,
    val period: DerivedFact<Period>,
    val straightLineDistanceMeters: DerivedFact<Int>,
)
data class ScoreComponent(val code: String, val points: Int)
data class EvaluatedOption(
    val eligible: Boolean,
    val exclusionReasonCodes: List<String>,
    val score: Int?,
    val scoreComponents: List<ScoreComponent>,
    val matchedConditions: List<String>,
    val unmetPreferredConditions: List<String>,
    val uncertaintyCodes: List<String>,
    val facts: ProgramFacts,
)
data class RecommendationOption(
    val usageOptionId: String,
    val facilityId: String,
    val programName: String,
    val programTypeName: String?,
    val targetName: String?,
    val beginDate: LocalDate?,
    val endDate: LocalDate?,
    val weekdays: List<String>,
    val sourceTimeValue: String?,
    val operatorTime: OperatorTimeResponse?,
    val recruitmentCount: Int?,
    val priceWon: Int?,
    val priceTypeName: String?,
    val homepageUrl: String?,
    val facilityOperation: io.github.chamsser.gymvi.usage.EvidenceStateResponse,
    val programApplication: io.github.chamsser.gymvi.usage.EvidenceStateResponse,
    val joinState: String,
    val evidenceHref: String,
    val facilityName: String,
    val facilityTypeName: String?,
    val roadAddress: String?,
    val longitude: Double,
    val latitude: Double,
)
data class RecommendationItem(
    val rank: Int,
    val score: Int,
    val scoreComponents: List<ScoreComponent>,
    val matchedConditions: List<String>,
    val unmetPreferredConditions: List<String>,
    val uncertaintyCodes: List<String>,
    val facts: ProgramFacts,
    val option: RecommendationOption,
)
data class ExcludedOption(val usageOptionId: String, val facilityId: String, val reasonCodes: List<String>)
data class ExclusionCount(val reasonCode: String, val count: Int)
data class RecommendationCounts(val candidates: Int, val eligible: Int, val excluded: Int, val returned: Int)
data class RecommendationData(
    val date: LocalDate,
    val originProvided: Boolean,
    val programDatasetVersion: String?,
    val programAsOf: Instant?,
    val attribution: String?,
    val counts: RecommendationCounts,
    val recommendations: List<RecommendationItem>,
    val excluded: List<ExcludedOption>,
    val excludedTruncated: Boolean,
    val exclusionSummary: List<ExclusionCount>,
    val unavailableReasonCodes: List<String>,
)
data class ComparisonItem(
    val usageOptionId: String,
    val eligible: Boolean,
    val exclusionReasonCodes: List<String>,
    val score: Int?,
    val scoreComponents: List<ScoreComponent>,
    val matchedConditions: List<String>,
    val unmetPreferredConditions: List<String>,
    val uncertaintyCodes: List<String>,
    val facts: ProgramFacts,
    val option: RecommendationOption,
)
data class DimensionValue(val usageOptionId: String, val state: String, val value: Any?)
data class ComparisonDimension(val dimension: String, val values: List<DimensionValue>, val bestUsageOptionIds: List<String>)
data class ComparisonData(
    val date: LocalDate,
    val originProvided: Boolean,
    val programDatasetVersion: String?,
    val programAsOf: Instant?,
    val attribution: String?,
    val items: List<ComparisonItem>,
    val dimensions: List<ComparisonDimension>,
    val unavailableReasonCodes: List<String>,
)
