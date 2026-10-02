package io.github.chamsser.gymvi.mobility

data class RouteCoordinateRequest(
    val latitude: Double,
    val longitude: Double,
)

data class RoutePreviewRequest(
    val origin: RouteCoordinateRequest,
    val destination: RouteCoordinateRequest,
)

data class RouteCoordinateResponse(
    val latitude: Double,
    val longitude: Double,
)

data class RoutePreviewResponse(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val path: List<RouteCoordinateResponse>,
    val provider: String,
)
