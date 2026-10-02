package io.github.chamsser.gymvi.data

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

data class RoutePointDto(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

data class RoutePreviewData(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val path: List<RoutePointDto>,
    val provider: String,
) {
    init {
        require(distanceMeters >= 0)
        require(durationSeconds >= 0)
        require(path.size >= 2)
        require(provider.isNotBlank())
    }
}

sealed interface RouteFetchResult {
    data class Success(val route: RoutePreviewData) : RouteFetchResult
    data class NetworkFailure(val reason: String) : RouteFetchResult
    data class ApiFailure(val statusCode: Int, val code: String, val retryable: Boolean) : RouteFetchResult
    data class InvalidResponse(val reason: String) : RouteFetchResult
}

class RouteApiClient(private val baseUrl: String) {
    init {
        if (baseUrl.isNotBlank()) validateBaseUrl(baseUrl)
    }

    fun preview(origin: RoutePointDto, destination: RoutePointDto): RouteFetchResult {
        if (baseUrl.isBlank()) return RouteFetchResult.ApiFailure(0, "API_NOT_CONFIGURED", false)
        val connection = URL(baseUrl.trimEnd('/') + "/api/v1/routes/preview")
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", "Gymvi-Android/0.2")
            val body = JSONObject()
                .put("origin", origin.toJson())
                .put("destination", destination.toJson())
                .toString()
                .toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.use(::readBoundedUtf8).orEmpty()
            if (status == HttpURLConnection.HTTP_OK) {
                parseSuccess(responseBody)
            } else {
                parseApiFailure(status, responseBody)
            }
        } catch (exception: IOException) {
            RouteFetchResult.NetworkFailure(exception.javaClass.simpleName)
        } catch (exception: JSONException) {
            RouteFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } catch (exception: IllegalArgumentException) {
            RouteFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseSuccess(body: String): RouteFetchResult.Success {
        val data = JSONObject(body).getJSONObject("data")
        val rawPath = data.getJSONArray("path")
        require(rawPath.length() in 2..MAX_PATH_POINTS)
        val path = buildList(rawPath.length()) {
            for (index in 0 until rawPath.length()) {
                val point = rawPath.getJSONObject(index)
                add(RoutePointDto(point.getDouble("latitude"), point.getDouble("longitude")))
            }
        }
        return RouteFetchResult.Success(
            RoutePreviewData(
                distanceMeters = data.getInt("distance_meters"),
                durationSeconds = data.getInt("duration_seconds"),
                path = path,
                provider = data.getString("provider"),
            ),
        )
    }

    private fun parseApiFailure(statusCode: Int, body: String): RouteFetchResult.ApiFailure {
        val error = runCatching { JSONObject(body).getJSONObject("error") }.getOrNull()
        return RouteFetchResult.ApiFailure(
            statusCode = statusCode,
            code = error?.optString("code")?.takeIf(String::isNotBlank) ?: "HTTP_$statusCode",
            retryable = error?.optBoolean("retryable", statusCode >= 500) ?: (statusCode >= 500),
        )
    }

    private fun RoutePointDto.toJson(): JSONObject = JSONObject()
        .put("latitude", latitude)
        .put("longitude", longitude)

    private fun readBoundedUtf8(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            require(total <= MAX_RESPONSE_BYTES) { "Route response exceeded the size limit." }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 10_000
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_PATH_POINTS = 20_000

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
