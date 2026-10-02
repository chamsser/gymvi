package io.github.chamsser.gymvi.geocoding

import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

internal class NaverGeocodingProvider(
    private val clientId: String,
    private val clientSecret: String,
    endpoint: String,
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient,
) : GeocodingProvider {
    private val endpoint = URI.create(endpoint).also { uri ->
        require(uri.scheme == "https" && uri.rawUserInfo == null && uri.rawQuery == null) {
            "NAVER Geocoding endpoint must be an HTTPS endpoint without credentials or a query."
        }
    }

    override fun search(query: String, limit: Int): List<GeocodedAddress> {
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val requestUri = URI.create(
            "$endpoint?query=$encodedQuery&count=$limit&language=kor",
        )
        val request = HttpRequest.newBuilder(requestUri)
            .timeout(Duration.ofSeconds(7))
            .header("Accept", "application/json")
            .header("x-ncp-apigw-api-key-id", clientId)
            .header("x-ncp-apigw-api-key", clientSecret)
            .GET()
            .build()
        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        }.getOrElse { throw GeocodingProviderFailedException() }

        val body = response.body().use { stream ->
            val bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1)
            if (bytes.size > MAX_RESPONSE_BYTES) throw GeocodingProviderFailedException()
            String(bytes, StandardCharsets.UTF_8)
        }
        if (response.statusCode() !in 200..299) throw GeocodingProviderFailedException()

        return runCatching {
            val root = objectMapper.readTree(body)
            if (root.path("status").asText() != "OK") throw GeocodingProviderFailedException()
            val rawAddresses = root.path("addresses")
            if (!rawAddresses.isArray || rawAddresses.size() > MAX_PROVIDER_RESULTS) {
                throw GeocodingProviderFailedException()
            }
            buildList {
                for (index in 0 until minOf(rawAddresses.size(), limit)) {
                    val raw = rawAddresses.path(index)
                    val roadAddress = raw.path("roadAddress").asText().trim().takeIf(String::isNotBlank)
                    val jibunAddress = raw.path("jibunAddress").asText().trim().takeIf(String::isNotBlank)
                    if (roadAddress == null && jibunAddress == null) continue
                    if ((roadAddress?.length ?: 0) > MAX_ADDRESS_LENGTH ||
                        (jibunAddress?.length ?: 0) > MAX_ADDRESS_LENGTH
                    ) {
                        continue
                    }
                    val longitude = raw.path("x").asText().toDoubleOrNull() ?: continue
                    val latitude = raw.path("y").asText().toDoubleOrNull() ?: continue
                    val coordinate = runCatching { GeocodingPoint(latitude, longitude) }.getOrNull() ?: continue
                    add(
                        GeocodedAddress(
                            roadAddress = roadAddress,
                            jibunAddress = jibunAddress,
                            coordinate = coordinate,
                            provider = PROVIDER,
                        ),
                    )
                }
            }.distinctBy { address ->
                Triple(address.roadAddress, address.coordinate.latitude, address.coordinate.longitude)
            }
        }.getOrElse { exception ->
            if (exception is GeocodingProviderFailedException) throw exception
            throw GeocodingProviderFailedException()
        }
    }

    private companion object {
        const val PROVIDER = "NAVER_GEOCODING"
        const val MAX_RESPONSE_BYTES = 512 * 1024
        const val MAX_PROVIDER_RESULTS = 100
        const val MAX_ADDRESS_LENGTH = 300
    }
}
