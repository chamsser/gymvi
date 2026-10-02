package io.github.chamsser.gymvi.usage

import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.ApiSuccess
import io.github.chamsser.gymvi.api.requestId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/facilities/{facility_id}/usage-options")
class UsageOptionController(private val service: UsageOptionService) {
    @GetMapping
    fun find(
        @PathVariable("facility_id") facilityId: String,
        request: HttpServletRequest,
    ): ApiSuccess<UsageOptionListResponse> {
        val result = service.findForFacility(facilityId)
        return ApiSuccess(
            data = result.response,
            meta = ApiMeta(
                request.requestId(),
                result.facilityDatasetVersion,
                result.facilityAsOf,
            ),
        )
    }
}

