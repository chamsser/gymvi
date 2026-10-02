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

class AddressApiClient(private val baseUrl: String) {
    init {
        if (baseUrl.isNotBlank()) validateBaseUrl(baseUrl)
    }

    fun search(query: String, limit: Int = 5): AddressSearchFetchResult {
        require(limit in 1..10)
        val normalizedQuery = normalizeAddressSearchQuery(query)
        require(normalizedQuery.length in 2..120)
        if (baseUrl.isBlank()) {
            return AddressSearchFetchResult.ApiFailure(0, "API_NOT_CONFIGURED", false)
        }
        val encodedQuery = URLEncoder.encode(normalizedQuery, StandardCharsets.UTF_8.name())
        val connection = URL(
            baseUrl.trimEnd('/') + "/api/v1/addresses/search" +
                "?query=$encodedQuery&limit=$limit",
        ).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Gymvi-Android/0.2")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.use(::readBoundedUtf8).orEmpty()
            if (status == HttpURLConnection.HTTP_OK) {
                parseSuccess(responseBody)
            } else {
                parseApiFailure(status, responseBody)
            }
        } catch (exception: IOException) {
            AddressSearchFetchResult.NetworkFailure(exception.javaClass.simpleName)
        } catch (exception: JSONException) {
            AddressSearchFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } catch (exception: IllegalArgumentException) {
            AddressSearchFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseSuccess(body: String): AddressSearchFetchResult.Success {
        val root = JSONObject(body)
        val data = root.getJSONObject("data")
        val rawAddresses = data.getJSONArray("addresses")
        require(rawAddresses.length() <= 10)
        val addresses = buildList(rawAddresses.length()) {
            for (index in 0 until rawAddresses.length()) {
                val raw = rawAddresses.getJSONObject(index)
                val coordinate = raw.getJSONObject("coordinate")
                add(
                    AddressSearchItem(
                        roadAddress = raw.optString("road_address").takeIf(String::isNotBlank),
                        jibunAddress = raw.optString("jibun_address").takeIf(String::isNotBlank),
                        latitude = coordinate.getDouble("latitude"),
                        longitude = coordinate.getDouble("longitude"),
                        provider = raw.getString("provider"),
                    ),
                )
            }
        }
        require(addresses.distinctBy(AddressSearchItem::stableId).size == addresses.size)
        val asOf = root.getJSONObject("meta").getString("as_of")
        return AddressSearchFetchResult.Success(AddressSearchPage(addresses, asOf))
    }

    private fun parseApiFailure(statusCode: Int, body: String): AddressSearchFetchResult.ApiFailure {
        val error = runCatching { JSONObject(body).getJSONObject("error") }.getOrNull()
        return AddressSearchFetchResult.ApiFailure(
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
            require(total <= MAX_RESPONSE_BYTES) { "Address response exceeded the size limit." }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 8_000
        const val MAX_RESPONSE_BYTES = 512 * 1024

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
