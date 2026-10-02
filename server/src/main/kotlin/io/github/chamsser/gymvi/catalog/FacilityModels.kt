package io.github.chamsser.gymvi.catalog

import java.time.Instant
import java.text.Normalizer
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

data class DatasetReference(
    val version: String,
    val asOf: Instant,
)

data class BoundingBox(
    val minLongitude: Double,
    val minLatitude: Double,
    val maxLongitude: Double,
    val maxLatitude: Double,
) {
    init {
        if (!listOf(minLongitude, minLatitude, maxLongitude, maxLatitude).all(Double::isFinite)) {
            throw ApiValidationException("VALIDATION_BBOX_RANGE", "지도 범위 좌표는 유한한 숫자여야 합니다.")
        }
        if (minLongitude !in -180.0..180.0 || maxLongitude !in -180.0..180.0 ||
            minLatitude !in -90.0..90.0 || maxLatitude !in -90.0..90.0
        ) {
            throw ApiValidationException("VALIDATION_BBOX_RANGE", "지도 범위 좌표가 WGS84 범위를 벗어났습니다.")
        }
        if (minLongitude >= maxLongitude || minLatitude >= maxLatitude) {
            throw ApiValidationException("VALIDATION_BBOX_ORDER", "지도 범위의 최소 좌표는 최대 좌표보다 작아야 합니다.")
        }
        if (
            maxLongitude - minLongitude > MAX_LONGITUDE_SPAN_DEGREES ||
            maxLatitude - minLatitude > MAX_LATITUDE_SPAN_DEGREES
        ) {
            throw ApiValidationException(
                "VALIDATION_BBOX_TOO_LARGE",
                "한 요청의 지도 범위는 대한민국 지도 범위를 넘을 수 없습니다.",
            )
        }
    }

    companion object {
        const val MAX_LONGITUDE_SPAN_DEGREES = 20.0
        const val MAX_LATITUDE_SPAN_DEGREES = 20.0

        fun fromNullable(
            minLongitude: Double?,
            minLatitude: Double?,
            maxLongitude: Double?,
            maxLatitude: Double?,
        ): BoundingBox {
            if (minLongitude == null || minLatitude == null || maxLongitude == null || maxLatitude == null) {
                throw ApiValidationException("VALIDATION_BBOX_REQUIRED", "네 개의 지도 범위 좌표가 모두 필요합니다.")
            }
            return BoundingBox(minLongitude, minLatitude, maxLongitude, maxLatitude)
        }
    }
}

data class FacilityRecord(
    val facilityId: String,
    val name: String,
    val facilityTypeCode: String?,
    val facilityTypeName: String?,
    val facilityClassCode: String? = null,
    val facilityClassName: String? = null,
    val roadAddress: String?,
    val lotAddress: String? = null,
    val sidoName: String? = null,
    val sigunguName: String? = null,
    val phone: String? = null,
    val description: String? = null,
    val grossFloorAreaSquareMeters: Double? = null,
    val longitude: Double,
    val latitude: Double,
    val operationState: String,
    val operationReason: String,
    val sourceRecordId: String,
    val sourceDatasetId: String,
    val catalogUrl: String,
)

enum class FacilitySortOrder(val apiValue: String) {
    RELEVANCE("relevance"),
    DISTANCE("distance"),
    ;

    companion object {
        fun fromApiValue(value: String): FacilitySortOrder = entries.firstOrNull {
            it.apiValue == value.trim().lowercase(Locale.ROOT)
        } ?: throw ApiValidationException(
            "VALIDATION_SORT",
            "정렬은 relevance 또는 distance여야 합니다.",
        )
    }
}

internal fun normalizeFacilityQuery(rawQuery: String?): String? {
    val trimmed = rawQuery?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (trimmed.length > MAX_FACILITY_QUERY_LENGTH) {
        throw ApiValidationException(
            "VALIDATION_QUERY",
            "검색어는 $MAX_FACILITY_QUERY_LENGTH 자 이하여야 합니다.",
        )
    }
    return Normalizer.normalize(trimmed, Normalizer.Form.NFKC)
        .lowercase(Locale.KOREAN)
        .replace(Regex("\\s+"), " ")
}

private const val MAX_FACILITY_QUERY_LENGTH = 80
private const val MAX_FACILITY_CURSOR_LENGTH = 32
private const val MAX_FACILITY_CURSOR_OFFSET = 200_000

internal fun encodeFacilityCursor(offset: Int): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(
        "v1:$offset".toByteArray(StandardCharsets.UTF_8),
    )

internal fun decodeFacilityCursor(rawCursor: String?): Int {
    val cursor = rawCursor?.trim().orEmpty()
    if (cursor.isEmpty()) return 0
    if (cursor.length > MAX_FACILITY_CURSOR_LENGTH) {
        throw ApiValidationException("VALIDATION_CURSOR", "다음 결과 위치가 올바르지 않습니다.")
    }
    val decoded = runCatching {
        String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
    }.getOrNull()
    val offset = decoded
        ?.takeIf { it.startsWith("v1:") }
        ?.removePrefix("v1:")
        ?.toIntOrNull()
        ?.takeIf { it in 0..MAX_FACILITY_CURSOR_OFFSET }
    return offset ?: throw ApiValidationException(
        "VALIDATION_CURSOR",
        "다음 결과 위치가 올바르지 않습니다.",
    )
}

data class FacilitySearchResult(
    val dataset: DatasetReference,
    val facilities: List<FacilityRecord>,
    val totalCount: Int = facilities.size,
    val nextCursor: String? = null,
)

data class FacilityDetailResult(
    val dataset: DatasetReference,
    val facility: FacilityRecord,
)

open class GymviApiException(
    val code: String,
    override val message: String,
    val retryable: Boolean,
) : RuntimeException(message)

class ApiValidationException(code: String, message: String) :
    GymviApiException(code, message, false)

class DatasetUnavailableException :
    GymviApiException("DATASET_UNAVAILABLE", "활성 시설 데이터셋을 조회할 수 없습니다.", true)

class FacilityNotFoundException :
    GymviApiException("DATA_FACILITY_NOT_FOUND", "요청한 시설을 찾을 수 없습니다.", false)
