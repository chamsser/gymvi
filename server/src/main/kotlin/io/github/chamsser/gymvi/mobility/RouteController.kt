package io.github.chamsser.gymvi.mobility

import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.ApiSuccess
import io.github.chamsser.gymvi.api.requestId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/routes")
class RouteController(
    private val service: RoutePreviewService,
    private val guard: RouteRequestGuard,
) {
    @PostMapping("/preview")
    fun preview(
        @RequestBody body: RoutePreviewRequest,
        request: HttpServletRequest,
    ): ApiSuccess<RoutePreviewResponse> {
        guard.requirePermit(request.remoteAddr.orEmpty())
        val result = service.preview(body)
        return ApiSuccess(
            data = RoutePreviewResponse(
                distanceMeters = result.distanceMeters,
                durationSeconds = result.durationSeconds,
                path = result.path.map { RouteCoordinateResponse(it.latitude, it.longitude) },
                provider = result.provider,
            ),
            meta = ApiMeta(request.requestId(), null, null),
        )
    }
}
