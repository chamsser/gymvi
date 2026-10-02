package io.github.chamsser.gymvi.data

data class DiscoveryFeedPage(
    val areaLabel: String,
    val dayPart: String,
    val weather: DiscoveryWeatherItem?,
    val proposals: List<ExerciseProposalItem>,
    val facilitySuggestions: List<DiscoveryFacilityItem>,
    val contents: List<ExerciseContentItem>,
    val usageOptionsUnavailableReasonCode: String?,
    val datasetVersion: String,
    val asOf: String,
    val usageOptions: List<FeedUsageOption> = emptyList(),
    val usageOptionsSource: UsageOptionsSource? = null,
) {
    init {
        require(areaLabel.isNotBlank())
        require(datasetVersion.isNotBlank())
        require(asOf.isNotBlank())
    }
}

data class FeedUsageOption(val option: UsageOptionItem, val facilityName: String)

data class UsageOptionsSource(
    val programDatasetVersion: String,
    val programAsOf: String?,
    val attribution: String?,
)

data class DiscoveryWeatherItem(
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double?,
    val condition: String,
    val precipitationMillimeters: Double,
    val observedAt: String,
    val sourceName: String,
)

data class ExerciseProposalItem(
    val proposalId: String,
    val title: String,
    val summary: String,
    val category: String,
    val reasonCodes: List<String>,
)

data class DiscoveryFacilityItem(
    val facilityId: String,
    val distanceMeters: Int,
    val score: Int,
    val reasonCodes: List<String>,
)

data class ExerciseContentItem(
    val contentId: String,
    val title: String,
    val summary: String,
    val category: String,
    val audience: String,
    val thumbnailUrl: String,
    val contentUrl: String,
    val sourceName: String,
    val sourceUrl: String,
    val channelName: String? = null,
    val viewCount: Long? = null,
    val likeCount: Long? = null,
    val durationSeconds: Int? = null,
)

sealed interface DiscoveryFeedFetchResult {
    data class Success(val page: DiscoveryFeedPage) : DiscoveryFeedFetchResult
    data class NetworkFailure(val reason: String) : DiscoveryFeedFetchResult
    data class ApiFailure(val statusCode: Int, val code: String, val retryable: Boolean) :
        DiscoveryFeedFetchResult
    data class InvalidResponse(val reason: String) : DiscoveryFeedFetchResult
}
