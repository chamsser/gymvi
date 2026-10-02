package io.github.chamsser.gymvi.mobility

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.LinkedHashMap
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class RoutePreviewService(
    private val provider: RouteProvider,
    @param:Value("\${gymvi.route.daily-provider-call-limit:100}") private val dailyProviderCallLimit: Int,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val lock = Any()
    private val cache = object : LinkedHashMap<RouteCacheKey, CachedRoute>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<RouteCacheKey, CachedRoute>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }
    private var budgetDate: LocalDate = LocalDate.now(clock)
    private var providerCallsToday = 0

    init {
        require(dailyProviderCallLimit > 0)
    }

    fun preview(request: RoutePreviewRequest): RouteResult {
        val origin = request.origin.toRoutePoint()
        val destination = request.destination.toRoutePoint()
        val distance = straightLineDistanceMeters(origin, destination)
        if (distance < MIN_ROUTE_DISTANCE_METERS) {
            throw RouteValidationException("VALIDATION_ROUTE_TOO_SHORT", "출발지와 목적지가 너무 가깝습니다.")
        }
        if (distance > MAX_ROUTE_DISTANCE_METERS) {
            throw RouteValidationException("VALIDATION_ROUTE_TOO_FAR", "한 번에 확인할 수 있는 경로 범위를 벗어났습니다.")
        }

        val cacheKey = RouteCacheKey.from(origin, destination)
        val now = clock.instant()
        synchronized(lock) {
            cache[cacheKey]?.takeIf { Duration.between(it.createdAt, now) <= CACHE_TTL }?.let {
                return it.route
            }
            cache.remove(cacheKey)
            resetBudgetIfNeeded()
            if (providerCallsToday >= dailyProviderCallLimit) throw RouteBudgetExhaustedException()
            providerCallsToday += 1
        }

        val result = provider.preview(origin, destination)
        require(result.path.size >= 2)
        synchronized(lock) {
            cache[cacheKey] = CachedRoute(result, now)
        }
        return result
    }

    private fun resetBudgetIfNeeded() {
        val today = LocalDate.now(clock)
        if (today != budgetDate) {
            budgetDate = today
            providerCallsToday = 0
            cache.clear()
        }
    }

    private fun RouteCoordinateRequest.toRoutePoint(): RoutePoint = RoutePoint(latitude, longitude).also {
        validateRoutePoint(it)
    }

    private data class CachedRoute(val route: RouteResult, val createdAt: Instant)

    private data class RouteCacheKey(
        val originLatitudeE5: Int,
        val originLongitudeE5: Int,
        val destinationLatitudeE5: Int,
        val destinationLongitudeE5: Int,
    ) {
        companion object {
            fun from(origin: RoutePoint, destination: RoutePoint) = RouteCacheKey(
                originLatitudeE5 = (origin.latitude * 100_000).roundToInt(),
                originLongitudeE5 = (origin.longitude * 100_000).roundToInt(),
                destinationLatitudeE5 = (destination.latitude * 100_000).roundToInt(),
                destinationLongitudeE5 = (destination.longitude * 100_000).roundToInt(),
            )
        }
    }

    private companion object {
        const val MAX_CACHE_ENTRIES = 128
        const val MIN_ROUTE_DISTANCE_METERS = 5.0
        const val MAX_ROUTE_DISTANCE_METERS = 100_000.0
        val CACHE_TTL: Duration = Duration.ofMinutes(5)
    }
}

internal fun validateRoutePoint(point: RoutePoint) {
    if (!point.latitude.isFinite() || !point.longitude.isFinite()) {
        throw RouteValidationException("VALIDATION_ROUTE_COORDINATE", "경로 좌표 형식이 올바르지 않습니다.")
    }
    if (point.latitude !in 33.0..39.5 || point.longitude !in 124.0..132.0) {
        throw RouteValidationException("VALIDATION_ROUTE_OUTSIDE_KOREA", "대한민국 안의 경로만 확인할 수 있습니다.")
    }
}

internal fun straightLineDistanceMeters(origin: RoutePoint, destination: RoutePoint): Double {
    val latitudeDelta = Math.toRadians(destination.latitude - origin.latitude)
    val longitudeDelta = Math.toRadians(destination.longitude - origin.longitude)
    val originLatitude = Math.toRadians(origin.latitude)
    val destinationLatitude = Math.toRadians(destination.latitude)
    val haversine = sin(latitudeDelta / 2).let { it * it } +
        cos(originLatitude) * cos(destinationLatitude) *
        sin(longitudeDelta / 2).let { it * it }
    return 6_371_000.0 * 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}
