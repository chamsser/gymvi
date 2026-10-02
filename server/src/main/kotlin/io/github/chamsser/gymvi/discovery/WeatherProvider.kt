package io.github.chamsser.gymvi.discovery

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.round

data class WeatherSnapshot(
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double?,
    val condition: String,
    val precipitationMillimeters: Double,
    val observedAt: String,
    val sourceName: String,
)

fun interface WeatherProvider {
    fun current(latitude: Double, longitude: Double): WeatherSnapshot?
}

@Configuration
class WeatherConfiguration {
    @Bean
    fun weatherProvider(
        objectMapper: ObjectMapper,
        @Value("\${gymvi.weather.enabled:true}") enabled: Boolean,
        @Value("\${gymvi.weather.endpoint:https://api.open-meteo.com/v1/forecast}") endpoint: String,
        @Value("\${gymvi.weather.daily-provider-call-limit:9000}") dailyCallLimit: Int,
    ): WeatherProvider = if (!enabled) {
        WeatherProvider { _, _ -> null }
    } else {
        OpenMeteoWeatherProvider(
            endpoint = endpoint,
            dailyCallLimit = dailyCallLimit,
            objectMapper = objectMapper,
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(),
        )
    }
}

internal class OpenMeteoWeatherProvider(
    endpoint: String,
    dailyCallLimit: Int,
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient,
) : WeatherProvider {
    private val endpoint = URI.create(endpoint).also { uri ->
        require(
            uri.scheme == "https" &&
                uri.host == "api.open-meteo.com" &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null,
        ) { "Open-Meteo endpoint must use the trusted HTTPS host without a query." }
    }
    private val budget = DailyCallBudget(dailyCallLimit)
    private val cache = ConcurrentHashMap<WeatherCacheKey, CachedWeather>()

    override fun current(latitude: Double, longitude: Double): WeatherSnapshot? {
        if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
            !longitude.isFinite() || longitude !in -180.0..180.0
        ) return null
        val key = WeatherCacheKey(
            latitude = round(latitude * 50.0) / 50.0,
            longitude = round(longitude * 50.0) / 50.0,
        )
        val now = Instant.now()
        cache[key]?.takeIf { it.expiresAt.isAfter(now) }?.let { return it.snapshot }
        if (!budget.tryAcquire()) return null
        val parameters = listOf(
            "latitude" to String.format(Locale.ROOT, "%.4f", key.latitude),
            "longitude" to String.format(Locale.ROOT, "%.4f", key.longitude),
            "current" to "temperature_2m,apparent_temperature,precipitation,weather_code",
            "timezone" to "Asia/Seoul",
            "forecast_days" to "1",
        ).joinToString("&") { (name, value) ->
            "$name=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
        }
        val request = HttpRequest.newBuilder(URI.create("$endpoint?$parameters"))
            .timeout(Duration.ofSeconds(6))
            .header("Accept", "application/json")
            .header("User-Agent", "GymviServer/0.3 (+https://open-meteo.com/)")
            .GET()
            .build()
        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        }.getOrNull() ?: return null
        val body = response.body().use { stream ->
            val bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1)
            if (bytes.size > MAX_RESPONSE_BYTES) return null
            String(bytes, StandardCharsets.UTF_8)
        }
        if (response.statusCode() !in 200..299) return null
        val snapshot = parseOpenMeteoCurrent(body, objectMapper) ?: return null
        if (cache.size >= MAX_CACHE_ENTRIES) {
            cache.entries.removeIf { it.value.expiresAt.isBefore(now) }
            if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
        }
        cache[key] = CachedWeather(snapshot, now.plus(CACHE_TTL))
        return snapshot
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 128 * 1024
        const val MAX_CACHE_ENTRIES = 256
        val CACHE_TTL: Duration = Duration.ofMinutes(15)
    }
}

internal fun parseOpenMeteoCurrent(body: String, objectMapper: ObjectMapper): WeatherSnapshot? =
    runCatching {
        val current = objectMapper.readTree(body).path("current")
        if (!current.isObject) return@runCatching null
        val temperature = current.path("temperature_2m").asText().toDoubleOrNull()
            ?.takeIf(Double::isFinite) ?: return@runCatching null
        val apparent = current.path("apparent_temperature").asText().toDoubleOrNull()
            ?.takeIf(Double::isFinite)
        val precipitation = current.path("precipitation").asText().toDoubleOrNull()
            ?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val code = current.path("weather_code").asText().toIntOrNull()
            ?: return@runCatching null
        val observedAt = current.path("time").asText().trim()
            .takeIf(String::isNotBlank)
            ?.let(::seoulDateTimeToOffset)
            ?: return@runCatching null
        WeatherSnapshot(
            temperatureCelsius = temperature,
            apparentTemperatureCelsius = apparent,
            condition = weatherCondition(code),
            precipitationMillimeters = precipitation,
            observedAt = observedAt,
            sourceName = "Open-Meteo",
        )
    }.getOrNull()

private fun seoulDateTimeToOffset(value: String): String =
    LocalDateTime.parse(value)
        .atZone(ZoneId.of("Asia/Seoul"))
        .toOffsetDateTime()
        .toString()

internal fun weatherCondition(code: Int): String = when (code) {
    0 -> "CLEAR"
    1, 2, 3, 45, 48 -> "CLOUDY"
    in 51..67, in 80..82 -> "RAIN"
    in 71..77, 85, 86 -> "SNOW"
    in 95..99 -> "STORM"
    else -> "UNKNOWN"
}

private data class WeatherCacheKey(val latitude: Double, val longitude: Double)
private data class CachedWeather(val snapshot: WeatherSnapshot, val expiresAt: Instant)

private class DailyCallBudget(private val limit: Int) {
    private val zone = ZoneId.of("Asia/Seoul")
    private var date: LocalDate = LocalDate.now(zone)
    private var used = 0

    init {
        require(limit in 1..10_000)
    }

    @Synchronized
    fun tryAcquire(): Boolean {
        val today = LocalDate.now(zone)
        if (today != date) {
            date = today
            used = 0
        }
        if (used >= limit) return false
        used += 1
        return true
    }
}
