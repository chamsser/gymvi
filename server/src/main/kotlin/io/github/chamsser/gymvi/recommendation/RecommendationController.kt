package io.github.chamsser.gymvi.recommendation

import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.ApiSuccess
import io.github.chamsser.gymvi.api.requestId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class RecommendationController(private val service: RecommendationService) {
    @PostMapping("/api/v1/recommendations")
    fun recommend(@RequestBody body: Map<String, Any?>, request: HttpServletRequest): ApiSuccess<RecommendationData> {
        val result = service.recommend(RecommendationRequestParser.recommendation(body))
        return ApiSuccess(result.data, ApiMeta(request.requestId(), result.facilityDataset.version, result.facilityDataset.asOf))
    }

    @PostMapping("/api/v1/usage-options/compare")
    fun compare(@RequestBody body: Map<String, Any?>, request: HttpServletRequest): ApiSuccess<ComparisonData> {
        val result = service.compare(RecommendationRequestParser.comparison(body))
        return ApiSuccess(result.data, ApiMeta(request.requestId(), result.facilityDataset.version, result.facilityDataset.asOf))
    }
}
