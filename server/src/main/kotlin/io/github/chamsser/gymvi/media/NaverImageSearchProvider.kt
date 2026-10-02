package io.github.chamsser.gymvi.media

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

internal class NaverImageSearchProvider(
    private val clientId: String,
    private val clientSecret: String,
    endpoint: String,
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient,
) : FacilityImageProvider {
    private val endpoint = URI.create(endpoint).also { uri ->
        require(
            uri.scheme == "https" &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null,
        ) {
            "NAVER image-search endpoint must be HTTPS without credentials, a query, or a fragment."
        }
    }

    override fun search(query: String, limit: Int): List<FacilityImageCandidate> {
        require(query.isNotBlank())
        require(limit in 1..MAX_FACILITY_IMAGES)
        val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val requestUri = URI.create(
            "$endpoint?query=$encodedQuery&display=$limit&start=1&sort=sim&filter=large",
        )
        val request = HttpRequest.newBuilder(requestUri)
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/json")
            .header("X-NCP-APIGW-API-KEY-ID", clientId)
            .header("X-NCP-APIGW-API-KEY", clientSecret)
            .GET()
            .build()
        val response = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        }.getOrElse { throw FacilityMediaProviderFailedException() }
        val body = response.body().use { stream ->
            val bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1)
            if (bytes.size > MAX_RESPONSE_BYTES) throw FacilityMediaProviderFailedException()
            String(bytes, StandardCharsets.UTF_8)
        }
        if (response.statusCode() !in 200..299) throw FacilityMediaProviderFailedException()
        return parseNaverImageSearchResponse(body, objectMapper, limit)
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 1024 * 1024
    }
}

internal fun parseNaverImageSearchResponse(
    body: String,
    objectMapper: ObjectMapper,
    limit: Int,
): List<FacilityImageCandidate> = runCatching {
    require(limit in 1..MAX_FACILITY_IMAGES)
    val items = objectMapper.readTree(body).path("items")
    if (!items.isArray || items.size() > 100) throw FacilityMediaProviderFailedException()
    buildList<FacilityImageCandidate> {
        for (index in 0 until items.size()) {
            if (size == limit) break
            val raw = items.path(index)
            val previewUrl = raw.path("thumbnail").asText().trim()
            if (!isTrustedNaverThumbnail(previewUrl)) continue
            if (any { existing -> existing.previewUrl == previewUrl }) continue
            add(
                FacilityImageCandidate(
                    previewUrl = previewUrl,
                    width = raw.optionalPositiveInt("sizewidth").asImageDimensionOrNull(),
                    height = raw.optionalPositiveInt("sizeheight").asImageDimensionOrNull(),
                ),
            )
        }
    }
}.getOrElse { exception ->
    if (exception is FacilityMediaProviderFailedException) throw exception
    throw FacilityMediaProviderFailedException()
}

private fun JsonNode.optionalPositiveInt(fieldName: String): Int? =
    path(fieldName).asText().trim().toIntOrNull()
