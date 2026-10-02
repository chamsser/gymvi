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
import java.util.Locale

data class FacilityImageCandidate(
    val previewUrl: String,
    val width: Int?,
    val height: Int?,
) {
    init {
        require(isTrustedNaverImageUrl(previewUrl))
        require(width == null || width in 1..MAX_IMAGE_DIMENSION)
        require(height == null || height in 1..MAX_IMAGE_DIMENSION)
    }
}

data class FacilityMediaPage(
    val facilityId: String,
    val images: List<FacilityImageCandidate>,
    val moreImagesUrl: String,
    val datasetVersion: String,
    val asOf: String,
) {
    init {
        require(facilityId.isNotBlank())
        require(images.size <= MAX_FACILITY_IMAGES)
        require(isTrustedNaverImageSearchUrl(moreImagesUrl))
        require(datasetVersion.isNotBlank())
        require(asOf.isNotBlank())
    }
}

sealed interface FacilityMediaFetchResult {
    data class Success(val page: FacilityMediaPage) : FacilityMediaFetchResult
    data class NetworkFailure(val reason: String) : FacilityMediaFetchResult
    data class ApiFailure(
        val statusCode: Int,
        val code: String,
        val retryable: Boolean,
    ) : FacilityMediaFetchResult
    data class InvalidResponse(val reason: String) : FacilityMediaFetchResult
}

class FacilityMediaApiClient(private val baseUrl: String) {
    init {
        if (baseUrl.isNotBlank()) validateBaseUrl(baseUrl)
    }

    fun fetch(facilityId: String, limit: Int = MAX_FACILITY_IMAGES): FacilityMediaFetchResult {
        if (baseUrl.isBlank()) {
            return FacilityMediaFetchResult.ApiFailure(0, "API_NOT_CONFIGURED", false)
        }
        require(facilityId.isNotBlank())
        require(limit in 1..MAX_FACILITY_IMAGES)
        val connection = buildEndpoint(facilityId, limit).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Gymvi-Android/0.2")

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use(::readBoundedUtf8).orEmpty()
            if (status == HttpURLConnection.HTTP_OK) {
                parseSuccess(body, facilityId)
            } else {
                parseApiFailure(status, body)
            }
        } catch (exception: IOException) {
            FacilityMediaFetchResult.NetworkFailure(exception.javaClass.simpleName)
        } catch (exception: JSONException) {
            FacilityMediaFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } catch (exception: IllegalArgumentException) {
            FacilityMediaFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    internal fun buildEndpoint(facilityId: String, limit: Int = MAX_FACILITY_IMAGES): URL {
        require(facilityId.isNotBlank())
        require(limit in 1..MAX_FACILITY_IMAGES)
        val encodedId = URLEncoder.encode(facilityId, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return URL(
            baseUrl.trimEnd('/') + "/api/v1/facilities/$encodedId/media?limit=$limit",
        )
    }

    internal fun parseSuccess(body: String, expectedFacilityId: String): FacilityMediaFetchResult.Success {
        val root = JSONObject(body)
        val meta = root.getJSONObject("meta")
        val data = root.getJSONObject("data")
        val facilityId = data.getString("facility_id")
        require(facilityId == expectedFacilityId)
        val rawImages = data.getJSONArray("images")
        require(rawImages.length() <= MAX_FACILITY_IMAGES)
        val images = buildList(rawImages.length()) {
            for (index in 0 until rawImages.length()) {
                val raw = rawImages.getJSONObject(index)
                require(raw.getString("source") == "NAVER_IMAGE_SEARCH")
                require(raw.getString("match_state") == "SEARCH_CANDIDATE")
                add(
                    FacilityImageCandidate(
                        previewUrl = raw.getString("preview_url"),
                        width = raw.optionalImageDimension("width"),
                        height = raw.optionalImageDimension("height"),
                    ),
                )
            }
        }
        require(images.map(FacilityImageCandidate::previewUrl).distinct().size == images.size)
        return FacilityMediaFetchResult.Success(
            FacilityMediaPage(
                facilityId = facilityId,
                images = images,
                moreImagesUrl = data.getString("more_images_url"),
                datasetVersion = meta.getString("dataset_version"),
                asOf = meta.getString("as_of"),
            ),
        )
    }

    private fun parseApiFailure(statusCode: Int, body: String): FacilityMediaFetchResult.ApiFailure {
        val error = runCatching { JSONObject(body).getJSONObject("error") }.getOrNull()
        return FacilityMediaFetchResult.ApiFailure(
            statusCode = statusCode,
            code = error?.optString("code")?.takeIf(String::isNotBlank) ?: "HTTP_$statusCode",
            retryable = error?.optBoolean("retryable", statusCode >= 500) ?: (statusCode >= 500),
        )
    }

    private fun JSONObject.optionalImageDimension(name: String): Int? {
        if (isNull(name)) return null
        return getInt(name).takeIf { it in 1..MAX_IMAGE_DIMENSION }
    }

    private fun readBoundedUtf8(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            require(total <= MAX_RESPONSE_BYTES) { "Facility media response exceeded the size limit." }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 10_000
        const val MAX_RESPONSE_BYTES = 1024 * 1024

        fun validateBaseUrl(baseUrl: String) {
            val uri = URI(baseUrl)
            require(uri.isAbsolute && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
            require(uri.path.isNullOrEmpty() || uri.path == "/")
            require(uri.port == -1 || uri.port in 1..65535)
            val scheme = uri.scheme.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            val isLocalHost = host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost"
            require(host.isNotBlank() && (scheme == "https" || (scheme == "http" && (isLocalHost || isTailnetIpv4(host)))))
        }

        fun isTailnetIpv4(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != 4) return false
            val octets = parts.map { it.toIntOrNull() ?: return false }
            return octets.all { it in 0..255 } && octets[0] == 100 && octets[1] in 64..127
        }
    }
}

internal fun isTrustedNaverImageUrl(rawUrl: String): Boolean {
    val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
    val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
    return uri.scheme.equals("https", ignoreCase = true) &&
        uri.rawUserInfo == null && uri.rawFragment == null &&
        (host == "pstatic.net" || host.endsWith(".pstatic.net"))
}

internal fun isTrustedNaverImageSearchUrl(rawUrl: String): Boolean {
    val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        uri.rawUserInfo == null && uri.rawFragment == null &&
        uri.host.equals("m.search.naver.com", ignoreCase = true) &&
        uri.path == "/search.naver" &&
        uri.rawQuery?.contains("where=m_image") == true
}

internal const val MAX_FACILITY_IMAGES = 4
private const val MAX_IMAGE_DIMENSION = 100_000
