package io.github.chamsser.gymvi.media

import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.ApiSuccess
import io.github.chamsser.gymvi.api.requestId
import io.github.chamsser.gymvi.operations.ClientRateLimiter
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.net.http.HttpClient
import java.time.Duration

data class FacilityImageResponse(
    val previewUrl: String,
    val width: Int?,
    val height: Int?,
    val source: String,
    val matchState: String,
)

data class FacilityMediaResponse(
    val facilityId: String,
    val images: List<FacilityImageResponse>,
    val moreImagesUrl: String,
)

private fun FacilityImageCandidate.toResponse() = FacilityImageResponse(
    previewUrl = previewUrl,
    width = width,
    height = height,
    source = source,
    matchState = matchState,
)

@Configuration
class FacilityMediaConfiguration {
    @Bean
    fun facilityImageProvider(
        objectMapper: ObjectMapper,
        @Value("\${gymvi.media.naver.client-id:}") clientId: String,
        @Value("\${gymvi.media.naver.client-secret:}") clientSecret: String,
        @Value("\${gymvi.media.naver.endpoint:https://naverapihub.apigw.ntruss.com/search/v1/image}")
        endpoint: String,
    ): FacilityImageProvider = if (clientId.isBlank() || clientSecret.isBlank()) {
        FacilityImageProvider { _, _ -> throw FacilityMediaProviderUnavailableException() }
    } else {
        NaverImageSearchProvider(
            clientId = clientId,
            clientSecret = clientSecret,
            endpoint = endpoint,
            objectMapper = objectMapper,
            httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build(),
        )
    }
}

@Component
class FacilityMediaRequestGuard(
    @Value("\${gymvi.media.requests-per-minute:12}") requestsPerMinute: Int,
    @Value("\${gymvi.media.burst-capacity:4}") burstCapacity: Int,
    @Value("\${gymvi.rate-limit.max-tracked-clients:4096}") maxTrackedClients: Int,
) {
    private val limiter = ClientRateLimiter(requestsPerMinute, burstCapacity, maxTrackedClients)

    fun requirePermit(clientAddress: String) {
        if (!limiter.tryAcquire(clientAddress)) throw FacilityMediaRateLimitedException()
    }
}

@RestController
@RequestMapping("/api/v1/facilities")
class FacilityMediaController(
    private val service: FacilityMediaService,
    private val guard: FacilityMediaRequestGuard,
) {
    @GetMapping("/{facility_id}/media")
    fun find(
        @PathVariable("facility_id") facilityId: String,
        @RequestParam("limit", defaultValue = "$MAX_FACILITY_IMAGES") limit: Int,
        request: HttpServletRequest,
    ): ApiSuccess<FacilityMediaResponse> {
        guard.requirePermit(request.remoteAddr.orEmpty())
        val result = service.find(facilityId, limit)
        return ApiSuccess(
            data = FacilityMediaResponse(
                facilityId = result.facilityId,
                images = result.images.map(FacilityImageCandidate::toResponse),
                moreImagesUrl = result.moreImagesUrl,
            ),
            meta = ApiMeta(
                requestId = request.requestId(),
                datasetVersion = result.dataset.version,
                asOf = result.retrievedAt,
            ),
        )
    }
}
