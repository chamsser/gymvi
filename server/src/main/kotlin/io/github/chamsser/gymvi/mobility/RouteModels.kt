package io.github.chamsser.gymvi.mobility

import io.github.chamsser.gymvi.catalog.GymviApiException

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
)

data class RouteResult(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val path: List<RoutePoint>,
    val provider: String,
)

fun interface RouteProvider {
    fun preview(origin: RoutePoint, destination: RoutePoint): RouteResult
}

class RouteValidationException(code: String, message: String) :
    GymviApiException(code, message, false)

class RouteRateLimitedException :
    GymviApiException("RATE_LIMITED", "경로 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", true)

class RouteBudgetExhaustedException :
    GymviApiException("PROVIDER_BUDGET_EXHAUSTED", "오늘 사용할 수 있는 경로 조회 한도에 도달했습니다.", false)

class RouteProviderUnavailableException :
    GymviApiException("PROVIDER_ROUTE_UNAVAILABLE", "경로 서비스를 사용할 수 없습니다.", true)

class RouteProviderFailedException :
    GymviApiException("PROVIDER_ROUTE_FAILED", "경로를 확인하지 못했습니다.", true)
