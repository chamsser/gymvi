package io.github.chamsser.gymvi.discovery

import io.github.chamsser.gymvi.api.ApiMeta
import io.github.chamsser.gymvi.api.ApiSuccess
import io.github.chamsser.gymvi.api.requestId
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/discovery-feed")
class DiscoveryController(private val service: DiscoveryService) {
    @GetMapping
    fun feed(
        @RequestParam("min_longitude", required = false) minLongitude: Double?,
        @RequestParam("min_latitude", required = false) minLatitude: Double?,
        @RequestParam("max_longitude", required = false) maxLongitude: Double?,
        @RequestParam("max_latitude", required = false) maxLatitude: Double?,
        @RequestParam("local_hour") localHour: Int,
        @RequestParam("favorite_facility_id", required = false) favoriteFacilityIds: List<String>?,
        @RequestParam("recent_facility_id", required = false) recentFacilityIds: List<String>?,
        request: HttpServletRequest,
        @RequestParam("preferred_category", required = false) preferredCategories: List<String>? = null,
    ): ApiSuccess<DiscoveryFeedResponse> {
        val result = service.feed(
            minLongitude = minLongitude,
            minLatitude = minLatitude,
            maxLongitude = maxLongitude,
            maxLatitude = maxLatitude,
            localHour = localHour,
            favoriteFacilityIds = favoriteFacilityIds.orEmpty(),
            recentFacilityIds = recentFacilityIds.orEmpty(),
            preferredCategories = preferredCategories.orEmpty(),
        )
        return ApiSuccess(
            data = result.feed,
            meta = ApiMeta(request.requestId(), result.dataset.version, result.dataset.asOf),
        )
    }
}
