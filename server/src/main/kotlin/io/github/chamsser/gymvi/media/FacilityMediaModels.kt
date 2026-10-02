package io.github.chamsser.gymvi.media

import io.github.chamsser.gymvi.catalog.DatasetReference
import io.github.chamsser.gymvi.catalog.FacilityRecord
import io.github.chamsser.gymvi.catalog.GymviApiException
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant

data class FacilityImageCandidate(
    val previewUrl: String,
    val width: Int?,
    val height: Int?,
    val source: String = SOURCE_NAVER_IMAGE_SEARCH,
    val matchState: String = MATCH_STATE_SEARCH_CANDIDATE,
) {
    init {
        require(isTrustedNaverThumbnail(previewUrl))
        require(width == null || width in 1..MAX_IMAGE_DIMENSION)
        require(height == null || height in 1..MAX_IMAGE_DIMENSION)
        require(source == SOURCE_NAVER_IMAGE_SEARCH)
        require(matchState == MATCH_STATE_SEARCH_CANDIDATE)
    }
}

data class FacilityMediaResult(
    val dataset: DatasetReference,
    val facilityId: String,
    val images: List<FacilityImageCandidate>,
    val moreImagesUrl: String,
    val retrievedAt: Instant,
)

fun interface FacilityImageProvider {
    fun search(query: String, limit: Int): List<FacilityImageCandidate>
}

class FacilityMediaValidationException(code: String, message: String) :
    GymviApiException(code, message, false)

class FacilityMediaBudgetExhaustedException :
    GymviApiException(
        "PROVIDER_MEDIA_BUDGET_EXHAUSTED",
        "오늘 사용할 수 있는 관련 이미지 검색 한도에 도달했습니다.",
        false,
    )

class FacilityMediaProviderUnavailableException :
    GymviApiException(
        "PROVIDER_MEDIA_UNAVAILABLE",
        "관련 이미지 검색을 사용할 수 없습니다.",
        true,
    )

class FacilityMediaProviderFailedException :
    GymviApiException(
        "PROVIDER_MEDIA_FAILED",
        "관련 이미지를 검색하지 못했습니다.",
        true,
    )

class FacilityMediaRateLimitedException :
    GymviApiException(
        "RATE_LIMITED",
        "관련 이미지 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
        true,
    )

internal fun buildFacilityImageQuery(facility: FacilityRecord): String {
    val fields = listOfNotNull(
        facility.name,
        facility.roadAddress ?: facility.lotAddress,
        facility.facilityTypeName,
    ).map { value -> value.trim().replace(Regex("\\s+"), " ") }
        .filter(String::isNotBlank)
        .distinctBy { value -> value.lowercase() }
    return fields.joinToString(" ").take(MAX_IMAGE_QUERY_LENGTH)
}

internal fun buildNaverImageSearchPageUrl(query: String): String {
    val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
    return "https://m.search.naver.com/search.naver?where=m_image&query=$encoded"
}

internal fun isTrustedNaverThumbnail(rawUrl: String): Boolean {
    val uri = runCatching { URI.create(rawUrl) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    return uri.scheme.equals("https", ignoreCase = true) &&
        uri.rawUserInfo == null &&
        uri.rawFragment == null &&
        (host == "pstatic.net" || host.endsWith(".pstatic.net"))
}

internal fun Int?.asImageDimensionOrNull(): Int? = this?.takeIf { it in 1..MAX_IMAGE_DIMENSION }

internal const val MAX_FACILITY_IMAGES = 4
internal const val SOURCE_NAVER_IMAGE_SEARCH = "NAVER_IMAGE_SEARCH"
internal const val MATCH_STATE_SEARCH_CANDIDATE = "SEARCH_CANDIDATE"
private const val MAX_IMAGE_DIMENSION = 100_000
private const val MAX_IMAGE_QUERY_LENGTH = 240
