package io.github.chamsser.gymvi.discovery

import io.github.chamsser.gymvi.catalog.ApiValidationException
import io.github.chamsser.gymvi.catalog.BoundingBox
import io.github.chamsser.gymvi.catalog.DatasetReference
import io.github.chamsser.gymvi.catalog.DatasetUnavailableException
import io.github.chamsser.gymvi.catalog.FacilityCatalog
import io.github.chamsser.gymvi.catalog.FacilityRecord
import io.github.chamsser.gymvi.catalog.FacilitySortOrder
import io.github.chamsser.gymvi.catalog.toSummary
import io.github.chamsser.gymvi.recommendation.Category
import io.github.chamsser.gymvi.recommendation.Origin
import io.github.chamsser.gymvi.recommendation.RecommendationConditions
import io.github.chamsser.gymvi.recommendation.RecommendationItem
import io.github.chamsser.gymvi.recommendation.RecommendationPreferences
import io.github.chamsser.gymvi.recommendation.RecommendationQuery
import io.github.chamsser.gymvi.recommendation.RecommendationService
import io.github.chamsser.gymvi.recommendation.Strength
import io.github.chamsser.gymvi.recommendation.ValuesCondition
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class DiscoveryService(
    private val catalog: FacilityCatalog,
    private val weatherProvider: WeatherProvider,
    private val recommendationService: RecommendationService,
    private val exerciseContentService: ExerciseContentService,
    private val clock: Clock,
) {
    fun feed(
        minLongitude: Double?,
        minLatitude: Double?,
        maxLongitude: Double?,
        maxLatitude: Double?,
        localHour: Int,
        favoriteFacilityIds: List<String>,
        recentFacilityIds: List<String>,
        preferredCategories: List<String> = emptyList(),
    ): DiscoveryFeedResult {
        if (localHour !in 0..23) {
            throw ApiValidationException("VALIDATION_LOCAL_HOUR", "현지 시각은 0시부터 23시 사이여야 합니다.")
        }
        val bounds = BoundingBox.fromNullable(minLongitude, minLatitude, maxLongitude, maxLatitude)
        val favorites = normalizeIds(favoriteFacilityIds, "즐겨찾기")
        val recent = normalizeIds(recentFacilityIds, "최근 시설")
        if (preferredCategories.size > Category.entries.size || preferredCategories.any { value -> Category.entries.none { it.name == value } }) {
            throw ApiValidationException("VALIDATION_PERSONALIZATION", "참고 종목 형식이 올바르지 않습니다.")
        }
        val preferred = preferredCategories.map(Category::valueOf).toSet()
        val search = catalog.search(
            bounds = bounds,
            query = null,
            sort = FacilitySortOrder.DISTANCE,
            limit = SEARCH_LIMIT,
        )
        val ranked = rankFacilities(
            facilities = search.facilities,
            centerLatitude = (bounds.minLatitude + bounds.maxLatitude) / 2.0,
            centerLongitude = (bounds.minLongitude + bounds.maxLongitude) / 2.0,
            favorites = favorites,
            recent = recent,
            preferred = preferred,
        )
        val centerLatitude = (bounds.minLatitude + bounds.maxLatitude) / 2.0
        val centerLongitude = (bounds.minLongitude + bounds.maxLongitude) / 2.0
        val weather = weatherProvider.current(centerLatitude, centerLongitude)
        val categories = search.facilities.map(::classify).toSet()
        val proposals = buildExerciseProposals(categories, localHour, weather, preferred)
        val usage = usageOptions(bounds, proposals, favorites, recent, preferred)
        return DiscoveryFeedResult(
            dataset = search.dataset,
            feed = DiscoveryFeedResponse(
                areaLabel = areaLabel(search.facilities),
                environment = DiscoveryEnvironmentResponse(
                    localHour = localHour,
                    dayPart = dayPart(localHour),
                    weather = weather?.let { snapshot ->
                        DiscoveryWeatherResponse(
                            temperatureCelsius = snapshot.temperatureCelsius,
                            apparentTemperatureCelsius = snapshot.apparentTemperatureCelsius,
                            condition = snapshot.condition,
                            precipitationMillimeters = snapshot.precipitationMillimeters,
                            observedAt = snapshot.observedAt,
                            sourceName = snapshot.sourceName,
                        )
                    },
                ),
                exerciseProposals = proposals,
                usageOptions = usage.items,
                usageOptionsSource = usage.source,
                usageOptionsUnavailableReasonCode = usage.unavailableReasonCode,
                facilities = ranked.map { candidate ->
                    DiscoveryFacilityResponse(
                        facility = candidate.facility.toSummary(),
                        distanceMeters = candidate.distanceMeters,
                        score = candidate.score,
                        reasonCodes = candidate.reasonCodes,
                    )
                },
                exerciseContents = contentFor(categories),
            ),
        )
    }

    private fun usageOptions(
        bounds: BoundingBox,
        proposals: List<ExerciseProposalResponse>,
        favorites: Set<String>,
        recent: Set<String>,
        preferred: Set<Category> = emptySet(),
    ): FeedUsageOptions {
        return try {
            val query = recommendationQuery(bounds, proposals, favorites, recent, preferred)
                ?: return FeedUsageOptions(emptyList(), null, null)
            val data = recommendationService.recommend(query).data
            if (PROGRAM_DATASET_NOT_ACTIVE in data.unavailableReasonCodes) {
                FeedUsageOptions(emptyList(), null, PROGRAM_DATASET_NOT_ACTIVE)
            } else {
                val source = data.programDatasetVersion?.let {
                    UsageOptionsSource(it, data.programAsOf, data.attribution)
                }
                if (data.recommendations.isNotEmpty() && data.attribution.isNullOrBlank()) {
                    FeedUsageOptions(emptyList(), source, "USAGE_OPTIONS_UNAVAILABLE")
                } else {
                    FeedUsageOptions(data.recommendations, source, null)
                }
            }
        } catch (error: ApiValidationException) {
            logger.warn("Usage options unavailable: {}", error.javaClass.name)
            val reason = when (error.code) {
                "VALIDATION_AREA_TOO_LARGE" -> "AREA_TOO_LARGE"
                "VALIDATION_AREA_TOO_DENSE" -> "AREA_TOO_DENSE"
                else -> "USAGE_OPTIONS_UNAVAILABLE"
            }
            FeedUsageOptions(emptyList(), null, reason)
        } catch (error: DatasetUnavailableException) {
            logger.warn("Usage options unavailable: {}", error.javaClass.name)
            FeedUsageOptions(emptyList(), null, "USAGE_OPTIONS_UNAVAILABLE")
        } catch (error: Exception) {
            logger.warn("Usage options unavailable: {}", error.javaClass.name)
            FeedUsageOptions(emptyList(), null, "USAGE_OPTIONS_UNAVAILABLE")
        }
    }

    internal fun recommendationQuery(
        bounds: BoundingBox,
        proposals: List<ExerciseProposalResponse>,
        favorites: Set<String>,
        recent: Set<String>,
        preferred: Set<Category> = emptySet(),
    ): RecommendationQuery? {
        val sportCategories = proposals.flatMap { proposal ->
            PROPOSAL_SPORTS.getValue(ExerciseCategory.valueOf(proposal.category))
        }.toSet()
        if (sportCategories.isEmpty() && preferred.isEmpty()) return null
        return RecommendationQuery(
            area = bounds,
            origin = Origin((bounds.minLatitude + bounds.maxLatitude) / 2.0,
                (bounds.minLongitude + bounds.maxLongitude) / 2.0),
            date = LocalDate.now(clock.withZone(SEOUL)),
            conditions = RecommendationConditions(categories = if (preferred.isNotEmpty()) ValuesCondition(preferred, Strength.PREFERRED)
                else ValuesCondition(sportCategories, Strength.REQUIRED)),
            preferences = RecommendationPreferences(favorites.take(20).toSet(), recent.take(20).toSet()),
            limit = 5,
            maxPerFacility = 2,
        )
    }

    private fun normalizeIds(values: List<String>, label: String): Set<String> {
        if (values.size > MAX_PERSONALIZATION_IDS) {
            throw ApiValidationException("VALIDATION_PERSONALIZATION", "$label 항목이 너무 많습니다.")
        }
        return values.map { it.trim() }
            .onEach { value ->
                if (value.isEmpty() || value.length > MAX_FACILITY_ID_LENGTH) {
                    throw ApiValidationException("VALIDATION_PERSONALIZATION", "$label ID 형식이 올바르지 않습니다.")
                }
            }
            .toSet()
    }

    private fun rankFacilities(
        facilities: List<FacilityRecord>,
        centerLatitude: Double,
        centerLongitude: Double,
        favorites: Set<String>,
        recent: Set<String>,
        preferred: Set<Category> = emptySet(),
    ): List<RankedFacility> {
        val ranked = facilities.map { facility ->
            val preferredSport = PROPOSAL_SPORTS.getValue(classify(facility)).any(preferred::contains)
            val distance = distanceMeters(
                centerLatitude,
                centerLongitude,
                facility.latitude,
                facility.longitude,
            )
            val reasons = buildList {
                if (facility.facilityId in favorites) add("FAVORITE")
                if (facility.facilityId in recent) add("RECENTLY_VIEWED")
                if (preferredSport) add("PREFERRED_SPORT")
                add("NEAR_MAP_CENTER")
                add("PUBLIC_FACILITY_DATA")
            }
            val score = (1_000 - distance / 25).coerceAtLeast(0) +
                (if (facility.facilityId in favorites) 240 else 0) +
                (if (facility.facilityId in recent) 100 else 0) + (if (preferredSport) 180 else 0)
            RankedFacility(facility, distance, score, reasons)
        }.sortedWith(
            compareByDescending<RankedFacility>(RankedFacility::score)
                .thenBy(RankedFacility::distanceMeters)
                .thenBy { normalizeName(it.facility.name) }
                .thenBy { it.facility.facilityId },
        )

        val picked = mutableListOf<RankedFacility>()
        val seenNames = mutableSetOf<String>()
        val categoryCounts = mutableMapOf<ExerciseCategory, Int>()
        for (candidate in ranked) {
            val name = normalizeName(candidate.facility.name)
            if (!seenNames.add(name)) continue
            val category = classify(candidate.facility)
            if ((categoryCounts[category] ?: 0) >= MAX_PER_CATEGORY && ranked.size > FEED_LIMIT) continue
            picked += candidate
            categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
            if (picked.size == FEED_LIMIT) break
        }
        if (picked.size < FEED_LIMIT) {
            for (candidate in ranked) {
                if (candidate in picked) continue
                if (!seenNames.add(normalizeName(candidate.facility.name))) continue
                picked += candidate
                if (picked.size == FEED_LIMIT) break
            }
        }
        return picked
    }

    private fun buildExerciseProposals(
        categories: Set<ExerciseCategory>,
        localHour: Int,
        weather: WeatherSnapshot?,
        preferred: Set<Category> = emptySet(),
    ): List<ExerciseProposalResponse> {
        val timeReason = when (dayPart(localHour)) {
            "MORNING" -> "아침에는 무리하지 않고 몸을 깨우는 강도로 시작해 보세요."
            "DAYTIME" -> "낮 시간에 가까운 시설을 골라 운동을 시작해 보세요."
            "EVENING" -> "저녁에는 이동 부담이 작은 운동부터 살펴보세요."
            else -> "늦은 시간에는 시설 운영시간을 먼저 확인해 주세요."
        }
        val standardOrder = listOf(
            ExerciseCategory.POOL,
            ExerciseCategory.GYM,
            ExerciseCategory.FIELD,
            ExerciseCategory.BALL,
            ExerciseCategory.OTHER,
        )
        val ordered = if (weather?.condition in setOf("RAIN", "SNOW", "STORM")) {
            listOf(
                ExerciseCategory.POOL,
                ExerciseCategory.GYM,
                ExerciseCategory.OTHER,
                ExerciseCategory.FIELD,
                ExerciseCategory.BALL,
            )
        } else {
            standardOrder
        }.filter(categories::contains).ifEmpty { listOf(ExerciseCategory.OTHER) }
        val weatherReason = weather?.let { "WEATHER_${it.condition}" }

        return ordered.sortedByDescending { PROPOSAL_SPORTS.getValue(it).any(preferred::contains) }
            .take(PROPOSAL_LIMIT).map { category ->
            when (category) {
                ExerciseCategory.POOL -> ExerciseProposalResponse(
                    proposalId = "nearby-swimming",
                    title = "가까운 수영시설에서 전신 운동",
                    summary = "$timeReason 운영시간과 프로그램은 시설 상세에서 별도로 확인할 수 있어요.",
                    category = category.apiValue,
                    reasonCodes = listOfNotNull("NEARBY_POOL", "LOCAL_TIME_CONTEXT", weatherReason),
                )

                ExerciseCategory.GYM -> ExerciseProposalResponse(
                    proposalId = "nearby-strength",
                    title = "가까운 체육시설에서 근력 운동",
                    summary = "$timeReason 이용 가능한 종목과 운영 여부는 시설 상세에서 확인해 주세요.",
                    category = category.apiValue,
                    reasonCodes = listOfNotNull("NEARBY_GYM", "LOCAL_TIME_CONTEXT", weatherReason),
                )

                ExerciseCategory.FIELD -> ExerciseProposalResponse(
                    proposalId = "nearby-running",
                    title = "가까운 운동장에서 가볍게 달리기",
                    summary = "$timeReason 날씨 정보가 없을 때에는 현장 환경을 직접 확인해 주세요.",
                    category = category.apiValue,
                    reasonCodes = listOfNotNull("NEARBY_FIELD", "LOCAL_TIME_CONTEXT", weatherReason),
                )

                ExerciseCategory.BALL -> ExerciseProposalResponse(
                    proposalId = "nearby-ball-sports",
                    title = "가까운 구기 시설 찾아보기",
                    summary = "$timeReason 예약과 현재 이용 가능 여부는 아직 알 수 없어요.",
                    category = category.apiValue,
                    reasonCodes = listOfNotNull(
                        "NEARBY_BALL_FACILITY",
                        "LOCAL_TIME_CONTEXT",
                        weatherReason,
                    ),
                )

                ExerciseCategory.OTHER -> ExerciseProposalResponse(
                    proposalId = "nearby-light-exercise",
                    title = "가까운 곳에서 가볍게 움직이기",
                    summary = "$timeReason 지도에 확인된 시설을 거리순으로 살펴볼 수 있어요.",
                    category = category.apiValue,
                    reasonCodes = listOfNotNull(
                        "NEARBY_FACILITY",
                        "LOCAL_TIME_CONTEXT",
                        weatherReason,
                    ),
                )
            }
        }
    }

    private fun contentFor(categories: Set<ExerciseCategory>): List<ExerciseContentResponse> {
        val selected = mutableListOf<ExerciseContentResponse>()
        if (ExerciseCategory.POOL in categories) selected += exerciseContentService.forFeed("nfa-prescription-9")
        if (categories.any { it in setOf(ExerciseCategory.FIELD, ExerciseCategory.BALL) }) {
            selected += exerciseContentService.forFeed("nfa-prescription-2")
        }
        selected += exerciseContentService.forFeed("nfa-prescription-137")
        return selected.distinctBy(ExerciseContentResponse::contentId).take(CONTENT_LIMIT)
    }

    private fun classify(facility: FacilityRecord): ExerciseCategory {
        val text = listOfNotNull(facility.name, facility.facilityTypeName, facility.facilityClassName)
            .joinToString(" ")
            .lowercase(Locale.KOREAN)
        return when {
            "수영" in text -> ExerciseCategory.POOL
            listOf("축구", "풋살", "농구", "야구", "테니스", "배드민턴").any(text::contains) ->
                ExerciseCategory.BALL
            listOf("운동장", "육상", "트랙").any(text::contains) -> ExerciseCategory.FIELD
            listOf("체육관", "체육센터", "헬스", "피트니스", "체력단련").any(text::contains) ->
                ExerciseCategory.GYM
            else -> ExerciseCategory.OTHER
        }
    }

    private fun areaLabel(facilities: List<FacilityRecord>): String =
        facilities.asSequence().mapNotNull(FacilityRecord::sigunguName).firstOrNull()
            ?: facilities.asSequence().mapNotNull(FacilityRecord::sidoName).firstOrNull()
            ?: "현재 지도 영역"

    private fun dayPart(hour: Int): String = when (hour) {
        in 5..10 -> "MORNING"
        in 11..16 -> "DAYTIME"
        in 17..21 -> "EVENING"
        else -> "LATE_NIGHT"
    }

    private fun normalizeName(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.KOREAN)
            .replace(Regex("[^0-9a-z가-힣]"), "")

    private fun distanceMeters(
        fromLatitude: Double,
        fromLongitude: Double,
        toLatitude: Double,
        toLongitude: Double,
    ): Int {
        val latitudeDelta = Math.toRadians(toLatitude - fromLatitude)
        val longitudeDelta = Math.toRadians(toLongitude - fromLongitude)
        val originLatitude = Math.toRadians(fromLatitude)
        val destinationLatitude = Math.toRadians(toLatitude)
        val haversine = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(originLatitude) * cos(destinationLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return (EARTH_RADIUS_METERS * 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))).roundToInt()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(DiscoveryService::class.java)
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private val PROPOSAL_SPORTS = mapOf(
            ExerciseCategory.POOL to setOf(Category.SWIMMING),
            ExerciseCategory.GYM to setOf(Category.FITNESS),
            ExerciseCategory.BALL to setOf(Category.TENNIS, Category.BADMINTON, Category.TABLE_TENNIS,
                Category.SQUASH, Category.TEAM_BALL),
            ExerciseCategory.FIELD to emptySet(),
            ExerciseCategory.OTHER to emptySet(),
        )
        private const val SEARCH_LIMIT = 80
        private const val FEED_LIMIT = 8
        private const val MAX_PER_CATEGORY = 3
        private const val PROPOSAL_LIMIT = 3
        private const val CONTENT_LIMIT = 3
        private const val MAX_PERSONALIZATION_IDS = 50
        private const val MAX_FACILITY_ID_LENGTH = 160
        private const val EARTH_RADIUS_METERS = 6_371_000.0
        private const val PROGRAM_DATASET_NOT_ACTIVE = "PROGRAM_DATASET_NOT_ACTIVE"
    }
}

private data class FeedUsageOptions(
    val items: List<RecommendationItem>,
    val source: UsageOptionsSource?,
    val unavailableReasonCode: String?,
)

data class DiscoveryFeedResult(
    val dataset: DatasetReference,
    val feed: DiscoveryFeedResponse,
)

private data class RankedFacility(
    val facility: FacilityRecord,
    val distanceMeters: Int,
    val score: Int,
    val reasonCodes: List<String>,
)

private enum class ExerciseCategory(val apiValue: String) {
    POOL("POOL"),
    GYM("GYM"),
    FIELD("FIELD"),
    BALL("BALL"),
    OTHER("OTHER"),
}
