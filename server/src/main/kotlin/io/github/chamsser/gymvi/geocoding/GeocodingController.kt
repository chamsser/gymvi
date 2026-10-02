package io.github.chamsser.gymvi.geocoding

import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.ApiSuccess
import io.github.chamsser.gymvi.api.requestId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/addresses")
class GeocodingController(
    private val service: GeocodingService,
    private val guard: GeocodingRequestGuard,
) {
    @GetMapping("/search")
    fun search(
        @RequestParam("query") query: String,
        @RequestParam("limit", defaultValue = "5") limit: Int,
        request: HttpServletRequest,
    ): ApiSuccess<GeocodingSearchResponse> {
        guard.requirePermit(request.remoteAddr.orEmpty())
        val result = service.search(query, limit)
        return ApiSuccess(
            data = GeocodingSearchResponse(result.addresses.map(GeocodedAddress::toResponse)),
            meta = ApiMeta(
                requestId = request.requestId(),
                datasetVersion = "NAVER_GEOCODING",
                asOf = result.asOf,
            ),
        )
    }
}
