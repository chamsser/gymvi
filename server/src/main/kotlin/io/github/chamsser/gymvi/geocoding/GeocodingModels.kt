package io.github.chamsser.gymvi.geocoding

import io.github.chamsser.gymvi.catalog.GymviApiException

data class GeocodingPoint(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in 33.0..39.5)
        require(longitude.isFinite() && longitude in 124.0..132.0)
    }
}

data class GeocodedAddress(
    val roadAddress: String?,
    val jibunAddress: String?,
    val coordinate: GeocodingPoint,
    val provider: String,
) {
    init {
        require(!roadAddress.isNullOrBlank() || !jibunAddress.isNullOrBlank())
        require(roadAddress == null || roadAddress.length <= 300)
        require(jibunAddress == null || jibunAddress.length <= 300)
        require(provider.isNotBlank())
    }
}

fun interface GeocodingProvider {
    fun search(query: String, limit: Int): List<GeocodedAddress>
}

class GeocodingValidationException(code: String, message: String) :
    GymviApiException(code, message, false)

class GeocodingBudgetExhaustedException :
    GymviApiException(
        "PROVIDER_GEOCODING_BUDGET_EXHAUSTED",
        "오늘 사용할 수 있는 주소 검색 한도에 도달했습니다.",
        false,
    )

class GeocodingProviderUnavailableException :
    GymviApiException("PROVIDER_GEOCODING_UNAVAILABLE", "주소 검색을 사용할 수 없습니다.", true)

class GeocodingProviderFailedException :
    GymviApiException("PROVIDER_GEOCODING_FAILED", "주소를 검색하지 못했습니다.", true)

class GeocodingRateLimitedException :
    GymviApiException("RATE_LIMITED", "주소 검색 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", true)
