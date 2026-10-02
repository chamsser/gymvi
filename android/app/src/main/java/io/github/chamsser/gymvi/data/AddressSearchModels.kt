package io.github.chamsser.gymvi.data

data class AddressSearchItem(
    val roadAddress: String?,
    val jibunAddress: String?,
    val latitude: Double,
    val longitude: Double,
    val provider: String,
) {
    init {
        require(!roadAddress.isNullOrBlank() || !jibunAddress.isNullOrBlank())
        require(latitude.isFinite() && latitude in 33.0..39.5)
        require(longitude.isFinite() && longitude in 124.0..132.0)
        require(provider.isNotBlank())
    }

    val displayLabel: String
        get() = roadAddress ?: checkNotNull(jibunAddress)

    val secondaryLabel: String?
        get() = jibunAddress?.takeUnless { it == displayLabel }

    val stableId: String
        get() = "$provider:$latitude,$longitude:$displayLabel"
}

data class AddressSearchPage(
    val addresses: List<AddressSearchItem>,
    val asOf: String,
) {
    init {
        require(asOf.isNotBlank())
    }
}

sealed interface AddressSearchFetchResult {
    data class Success(val page: AddressSearchPage) : AddressSearchFetchResult
    data class NetworkFailure(val reason: String) : AddressSearchFetchResult
    data class ApiFailure(
        val statusCode: Int,
        val code: String,
        val retryable: Boolean,
    ) : AddressSearchFetchResult
    data class InvalidResponse(val reason: String) : AddressSearchFetchResult
}

internal fun normalizeAddressSearchQuery(query: String): String =
    query.trim().replace(Regex("\\s+"), " ")

internal fun looksLikeAddressQuery(query: String): Boolean {
    val normalized = normalizeAddressSearchQuery(query)
    if (normalized.length < 2) return false
    if (normalized.any(Char::isDigit)) return true
    return ADDRESS_QUERY_PATTERN.containsMatchIn(normalized)
}

private val ADDRESS_QUERY_PATTERN = Regex(
    "(?:특별시|광역시|특별자치시|특별자치도|[가-힣]+(?:시|군|구)(?:\\s|$)|" +
        "[가-힣0-9·.-]+(?:대로|로|길)(?:\\s|$)|[가-힣]+(?:동|읍|면|리)(?:\\s|$))",
)
