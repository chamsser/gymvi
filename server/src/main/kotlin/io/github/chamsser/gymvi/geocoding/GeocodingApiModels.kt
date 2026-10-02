package io.github.chamsser.gymvi.geocoding

data class GeocodingCoordinateResponse(
    val latitude: Double,
    val longitude: Double,
)
data class GeocodedAddressResponse(
    val roadAddress: String?,
    val jibunAddress: String?,
    val coordinate: GeocodingCoordinateResponse,
    val provider: String,
)

data class GeocodingSearchResponse(
    val addresses: List<GeocodedAddressResponse>,
)

internal fun GeocodedAddress.toResponse() = GeocodedAddressResponse(
    roadAddress = roadAddress,
    jibunAddress = jibunAddress,
    coordinate = GeocodingCoordinateResponse(coordinate.latitude, coordinate.longitude),
    provider = provider,
)
