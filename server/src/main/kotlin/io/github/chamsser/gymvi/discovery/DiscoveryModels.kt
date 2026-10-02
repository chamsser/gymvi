package io.github.chamsser.gymvi.discovery

import io.github.chamsser.gymvi.catalog.FacilitySummaryResponse
import io.github.chamsser.gymvi.recommendation.RecommendationItem
import java.time.Instant

data class DiscoveryEnvironmentResponse(
    val localHour: Int,
    val dayPart: String,
    val weather: DiscoveryWeatherResponse? = null,
)

data class DiscoveryWeatherResponse(
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double?,
    val condition: String,
    val precipitationMillimeters: Double,
    val observedAt: String,
    val sourceName: String,
)

data class ExerciseProposalResponse(
    val proposalId: String,
    val title: String,
    val summary: String,
    val category: String,
    val reasonCodes: List<String>,
)

data class DiscoveryFacilityResponse(
    val facility: FacilitySummaryResponse,
    val distanceMeters: Int,
    val score: Int,
    val reasonCodes: List<String>,
)

data class ExerciseContentResponse(
    val contentId: String,
    val title: String,
    val summary: String,
    val category: String,
    val audience: String,
    val thumbnailUrl: String,
    val contentUrl: String,
    val sourceName: String,
    val sourceUrl: String,
)

data class DiscoveryFeedResponse(
    val areaLabel: String,
    val environment: DiscoveryEnvironmentResponse,
    val exerciseProposals: List<ExerciseProposalResponse>,
    val usageOptions: List<RecommendationItem>,
    val usageOptionsSource: UsageOptionsSource?,
    val usageOptionsUnavailableReasonCode: String?,
    val facilities: List<DiscoveryFacilityResponse>,
    val exerciseContents: List<ExerciseContentResponse>,
)

data class UsageOptionsSource(
    val programDatasetVersion: String,
    val programAsOf: Instant?,
    val attribution: String?,
)
