package io.github.chamsser.gymvi.mobility

import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Locale

internal class NaverDirectionsProvider(
    private val clientId: String,
    private val clientSecret: String,
    endpoint: String,
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient,
) : RouteProvider {
    private val endpoint = URI.create(endpoint).also { uri ->
        require(uri.scheme == "https" && uri.rawUserInfo == null && uri.rawQuery == null) {
            "NAVER Directions endpoint must be an HTTPS endpoint without credentials or a query."
        }
    }

    override fun preview(origin: RoutePoint, destination: RoutePoint): RouteResult {
        val requestUri = URI.create(
            endpoint.toString() +
                "?start=${origin.asNaverCoordinate()}" +
                "&goal=${destination.asNaverCoordinate()}" +
                "&option=traoptimal",
        )
        val request = HttpRequest.newBuilder(requestUri)
            .timeout(Duration.ofSeconds(9))
            .header("Accept", "application/json")
            .header("x-ncp-apigw-api-key-id", clientId)
            .header("x-ncp-apigw-api-key", clientSecret)
            .GET()
            .build()
        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        }.getOrElse { throw RouteProviderFailedException() }

        val body = response.body().use { stream ->
            val bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1)
            if (bytes.size > MAX_RESPONSE_BYTES) throw RouteProviderFailedException()
            String(bytes, StandardCharsets.UTF_8)
        }
        if (response.statusCode() !in 200..299) throw RouteProviderFailedException()

        return runCatching {
            val root = objectMapper.readTree(body)
            if (root.path("code").asInt(-1) != 0) throw RouteProviderFailedException()
            val route = root.path("route").path("traoptimal").path(0)
            val summary = route.path("summary")
            val rawPath = route.path("path")
            if (!rawPath.isArray || rawPath.size() !in 2..MAX_PATH_POINTS) {
                throw RouteProviderFailedException()
            }
            val path = buildList(rawPath.size()) {
                for (index in 0 until rawPath.size()) {
                    val coordinate = rawPath.path(index)
                    if (!coordinate.isArray || coordinate.size() < 2) {
                        throw RouteProviderFailedException()
                    }
                    add(
                        RoutePoint(
                            longitude = coordinate.path(0).asDouble(Double.NaN),
                            latitude = coordinate.path(1).asDouble(Double.NaN),
                        ).also(::validateRoutePoint),
                    )
                }
            }
            val distanceMeters = summary.path("distance").asInt(-1)
            val durationMillis = summary.path("duration").asLong(-1L)
            if (distanceMeters < 0 || durationMillis < 0) throw RouteProviderFailedException()
            RouteResult(
                distanceMeters = distanceMeters,
                durationSeconds = (durationMillis / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                path = path,
                provider = "NAVER_DIRECTIONS_5",
            )
        }.getOrElse { exception ->
            if (exception is RouteProviderFailedException) throw exception
            throw RouteProviderFailedException()
        }
    }

    private fun RoutePoint.asNaverCoordinate(): String = String.format(
        Locale.ROOT,
        "%.7f,%.7f",
        longitude,
        latitude,
    )

    private companion object {
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_PATH_POINTS = 20_000
    }
}
