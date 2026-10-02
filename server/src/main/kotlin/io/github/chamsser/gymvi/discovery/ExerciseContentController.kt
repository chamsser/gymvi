package io.github.chamsser.gymvi.discovery

import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.ApiSuccess
import io.github.chamsser.gymvi.api.requestId
import io.github.chamsser.gymvi.catalog.ApiValidationException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/exercise-contents")
class ExerciseContentController(private val service: ExerciseContentService) {
    @GetMapping
    fun contents(
        @RequestParam(required = false) sport: String?,
        @RequestParam(required = false) limit: String?,
        request: HttpServletRequest,
    ): ApiSuccess<ExerciseContentsData> {
        val parsedLimit = limit?.toIntOrNull() ?: if (limit == null) 10 else
            throw ApiValidationException("VALIDATION_LIMIT", "결과 제한은 1부터 20까지입니다.")
        val result = service.find(sport, parsedLimit)
        return ApiSuccess(result.data, ApiMeta(request.requestId(), result.datasetVersion, null))
    }
}
