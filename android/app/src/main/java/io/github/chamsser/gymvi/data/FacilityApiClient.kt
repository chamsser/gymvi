package io.github.chamsser.gymvi.data

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.util.Locale

class FacilityApiClient(private val baseUrl: String) {
    init {
        if (baseUrl.isNotBlank()) {
            val uri = URI(baseUrl)
            require(uri.isAbsolute && uri.rawUserInfo == null) { "Gymvi API URL is invalid." }
            require(uri.rawQuery == null && uri.rawFragment == null) { "Gymvi API URL is invalid." }
            require(uri.path.isNullOrEmpty() || uri.path == "/") { "Gymvi API URL must be an origin." }
            require(uri.port == -1 || uri.port in 1..65535) { "Gymvi API port is invalid." }

            val scheme = uri.scheme.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            require(host.isNotBlank()) { "Gymvi API URL host is invalid." }
            val isLocalHost = host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost"
            require(scheme == "https" || (scheme == "http" && (isLocalHost || isTailnetIpv4(host)))) {
                "A non-local Gymvi API must use HTTPS or a Tailscale IPv4 address."
            }
        }
    }

    fun fetch(
        bounds: FacilityBounds,
        limit: Int = 100,
        query: String? = null,
        sort: FacilityQuerySort = FacilityQuerySort.RELEVANCE,
        cursor: String? = null,
    ): FacilityFetchResult {
        if (baseUrl.isBlank()) {
            return FacilityFetchResult.ApiFailure(0, "API_NOT_CONFIGURED", false)
        }
        require(limit in 1..MAX_PAGE_SIZE)

        val endpoint = buildEndpoint(bounds, limit, query, sort, cursor)
        val connection = endpoint.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Gymvi-Android/0.1")

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use(::readBoundedUtf8).orEmpty()
            if (status == HttpURLConnection.HTTP_OK) {
                parseSuccess(body)
            } else {
                parseApiFailure(status, body)
            }
        } catch (exception: IOException) {
            FacilityFetchResult.NetworkFailure(exception.javaClass.simpleName)
        } catch (exception: JSONException) {
            FacilityFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } catch (exception: IllegalArgumentException) {
            FacilityFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    internal fun buildEndpoint(
        bounds: FacilityBounds,
        limit: Int,
        query: String? = null,
        sort: FacilityQuerySort = FacilityQuerySort.RELEVANCE,
        cursor: String? = null,
    ): URL {
        require(limit in 1..MAX_PAGE_SIZE)
        val normalizedQuery = query?.trim()?.takeIf(String::isNotEmpty)
        require(normalizedQuery == null || normalizedQuery.length <= MAX_QUERY_LENGTH)
        val queryParameter = normalizedQuery?.let {
            "&query=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}"
        }.orEmpty()
        val normalizedCursor = cursor?.trim()?.takeIf(String::isNotEmpty)
        require(normalizedCursor == null || normalizedCursor.length <= MAX_CURSOR_LENGTH)
        val cursorParameter = normalizedCursor?.let {
            "&cursor=${URLEncoder.encode(it, StandardCharsets.UTF_8.name())}"
        }.orEmpty()
        return URL(
            baseUrl.trimEnd('/') + "/api/v1/facilities" +
                "?min_longitude=${bounds.minLongitude.asQueryNumber()}" +
                "&min_latitude=${bounds.minLatitude.asQueryNumber()}" +
                "&max_longitude=${bounds.maxLongitude.asQueryNumber()}" +
                "&max_latitude=${bounds.maxLatitude.asQueryNumber()}" +
                "&limit=$limit" +
                queryParameter +
                "&sort=${sort.apiValue}" +
                cursorParameter,
        )
    }

    internal fun parseSuccess(body: String): FacilityFetchResult.Success {
        val root = JSONObject(body)
        val meta = root.getJSONObject("meta")
        val data = root.getJSONObject("data")
        val facilitiesJson = data.getJSONArray("facilities")
        val facilities = buildList(facilitiesJson.length()) {
            for (index in 0 until facilitiesJson.length()) {
                val facility = facilitiesJson.getJSONObject(index)
                val type = facility.getJSONObject("facility_type")
                val facilityClass = facility.optJSONObject("facility_class")
                val coordinate = facility.getJSONObject("coordinate")
                val operation = facility.getJSONObject("facility_operation")
                val provenance = facility.getJSONObject("provenance")
                val administrativeArea = facility.optJSONObject("administrative_area")
                add(
                    FacilityMapItem(
                        facilityId = facility.getString("facility_id"),
                        name = facility.getString("name"),
                        facilityTypeName = type.optionalString("name"),
                        latitude = coordinate.getDouble("latitude"),
                        longitude = coordinate.getDouble("longitude"),
                        operationState = EvidenceState.valueOf(operation.getString("state")),
                        operationReasonCode = operation.getString("reason_code"),
                        provenanceHref = provenance.getString("href"),
                        sourceRecordId = provenance.getString("source_record_id"),
                        facilityClassName = facilityClass?.optionalString("name"),
                        roadAddress = facility.optionalString("road_address"),
                        lotAddress = facility.optionalString("lot_address"),
                        sidoName = administrativeArea?.optionalString("sido"),
                        sigunguName = administrativeArea?.optionalString("sigungu"),
                        phoneNumber = facility.optionalString("phone")
                            ?.takeIf(FACILITY_PHONE_PATTERN::matches),
                        description = facility.optionalString("description"),
                        grossFloorAreaSquareMeters = facility.optionalPositiveDouble(
                            "gross_floor_area_square_meters",
                        ),
                        operatingHours = facility.optionalOperatingHours("operating_hours"),
                    ),
                )
            }
        }
        require(facilities.map { it.facilityId }.distinct().size == facilities.size) {
            "Facility response contains duplicate IDs."
        }
        return FacilityFetchResult.Success(
            FacilityPage(
                facilities = facilities,
                totalCount = data.optInt("total_count", facilities.size)
                    .coerceAtLeast(facilities.size),
                nextCursor = data.optionalString("next_cursor"),
                datasetVersion = meta.getString("dataset_version"),
                asOf = meta.getString("as_of"),
            ),
        )
    }

    private fun parseApiFailure(statusCode: Int, body: String): FacilityFetchResult.ApiFailure {
        val error = runCatching { JSONObject(body).getJSONObject("error") }.getOrNull()
        return FacilityFetchResult.ApiFailure(
            statusCode = statusCode,
            code = error?.optString("code")?.takeIf(String::isNotBlank) ?: "HTTP_$statusCode",
            retryable = error?.optBoolean("retryable", statusCode >= 500) ?: (statusCode >= 500),
        )
    }

    private fun readBoundedUtf8(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            require(total <= MAX_RESPONSE_BYTES) { "Facility response exceeded the size limit." }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun JSONObject.optionalString(name: String): String? =
        if (isNull(name)) null else getString(name).trim().ifEmpty { null }

    /** Malformed or unsourced hours are dropped instead of failing the facility list. */
    private fun JSONObject.optionalOperatingHours(name: String): FacilityOperatingHours? {
        if (isNull(name)) return null
        return runCatching {
            val hours = getJSONObject(name)
            val schedule = hours.getJSONArray("schedule")
            val closedDays = hours.getJSONArray("closed_days")
            val source = hours.getJSONArray("sources").getJSONObject(0)
            require(source.getString("url").startsWith("https://"))
            FacilityOperatingHours(
                schedule = List(schedule.length()) { index ->
                    val slot = schedule.getJSONObject(index)
                    FacilityOperatingHoursSlot(
                        days = requireNotNull(slot.optionalString("days")),
                        hours = requireNotNull(slot.optionalString("hours")),
                    )
                },
                closedDays = List(closedDays.length()) { index -> closedDays.getString(index).trim() }
                    .filter(String::isNotEmpty),
                note = hours.optionalString("note"),
                sourceName = requireNotNull(source.optionalString("name")),
                checkedAt = OffsetDateTime.parse(hours.getString("checked_at")),
            )
        }.getOrNull()
    }

    private fun JSONObject.optionalPositiveDouble(name: String): Double? {
        if (isNull(name)) return null
        return getDouble(name).takeIf { value ->
            value.isFinite() && value > 0 && value <= 10_000_000
        }
    }

    private fun Double.asQueryNumber(): String = String.format(Locale.ROOT, "%.8f", this)

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 5_000
        private const val READ_TIMEOUT_MILLIS = 8_000
        internal const val MAX_PAGE_SIZE = 1_000
        private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        private const val MAX_QUERY_LENGTH = 80
        private const val MAX_CURSOR_LENGTH = 32

        private fun isTailnetIpv4(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != 4) return false
            val octets = parts.map { it.toIntOrNull() ?: return false }
            return octets.all { it in 0..255 } &&
                octets[0] == 100 &&
                octets[1] in 64..127
        }
    }
}
