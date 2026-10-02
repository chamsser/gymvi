package io.github.chamsser.gymvi.catalog

import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.ApiSuccess
import io.github.chamsser.gymvi.api.requestId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/facilities")
class FacilityController(
    private val service: FacilityService,
    private val operatorHours: OperatorHoursCatalog,
) {
    @GetMapping
    fun search(
        @RequestParam("min_longitude", required = false) minLongitude: Double?,
        @RequestParam("min_latitude", required = false) minLatitude: Double?,
        @RequestParam("max_longitude", required = false) maxLongitude: Double?,
        @RequestParam("max_latitude", required = false) maxLatitude: Double?,
        @RequestParam("limit", defaultValue = "${FacilityService.DEFAULT_LIMIT}") limit: Int,
        @RequestParam("query", required = false) query: String?,
        @RequestParam("sort", defaultValue = "relevance") sort: String,
        @RequestParam("cursor", required = false) cursor: String?,
        request: HttpServletRequest,
    ): ApiSuccess<FacilityListResponse> {
        val result = service.search(
            minLongitude,
            minLatitude,
            maxLongitude,
            maxLatitude,
            limit,
            query,
            sort,
            cursor,
        )
        return ApiSuccess(
            data = FacilityListResponse(
                facilities = result.facilities.map { it.toSummary(operatorHours.find(it.facilityId)) },
                totalCount = result.totalCount,
                nextCursor = result.nextCursor,
            ),
            meta = ApiMeta(request.requestId(), result.dataset.version, result.dataset.asOf),
        )
    }

    @GetMapping("/{facility_id}")
    fun find(
        @PathVariable("facility_id") facilityId: String,
        request: HttpServletRequest,
    ): ApiSuccess<FacilityDetailResponse> {
        val result = service.find(facilityId)
        return ApiSuccess(
            data = result.facility.toDetail(operatorHours.find(result.facility.facilityId)),
            meta = ApiMeta(request.requestId(), result.dataset.version, result.dataset.asOf),
        )
    }
}
