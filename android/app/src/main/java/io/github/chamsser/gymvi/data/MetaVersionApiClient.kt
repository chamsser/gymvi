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

/**
 * One answer from GET /api/v1/meta/version. The model and the data sources always come from the same
 * response, so MY never shows one answer's model next to another answer's sources.
 */
data class MetaVersionInfo(
    /** Null whenever the server does not confirm a model; never a built-in name. */
    val configuredAiModel: String?,
    /** Only what the server listed; empty for a server from before the list. */
    val publicDataSources: List<PublicDataSource>,
) {
    companion object {
        /** No answer to show: no server set up, a failed request or an unreadable body. */
        val UNKNOWN = MetaVersionInfo(configuredAiModel = null, publicDataSources = emptyList())
    }
}

/**
 * Reads what the server reports about its own setup from GET /api/v1/meta/version: the AI model it
 * is set up to call and where its active public data came from. That is not the model behind any one
 * reply, so MY shows it as information only.
 */
class MetaVersionApiClient(private val baseUrl: String) {
    init {
        if (baseUrl.isNotBlank()) validateBaseUrl(baseUrl)
    }

    /** The server's answer, or [MetaVersionInfo.UNKNOWN] when there is none to read. */
    fun fetchMetaVersion(): MetaVersionInfo {
        if (baseUrl.isBlank()) return MetaVersionInfo.UNKNOWN
        val connection = URL(baseUrl.trimEnd('/') + "/api/v1/meta/version").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Gymvi-Android/0.3")
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                parseMetaVersion(connection.inputStream.use(::readBoundedUtf8))
            } else {
                MetaVersionInfo.UNKNOWN
            }
        } catch (exception: IOException) {
            MetaVersionInfo.UNKNOWN
        } catch (exception: IllegalArgumentException) {
            MetaVersionInfo.UNKNOWN
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseMetaVersion(body: String): MetaVersionInfo {
        val data = try {
            JSONObject(body).getJSONObject("data")
        } catch (exception: JSONException) {
            null
        } ?: return MetaVersionInfo.UNKNOWN
        return MetaVersionInfo(
            configuredAiModel = configuredAiModel(data.optJSONObject("ai_model")),
            publicDataSources = parsePublicDataSources(data.opt("datasets")),
        )
    }

    /** The model name, only when the server reports it configured. A server from before the field has none. */
    private fun configuredAiModel(aiModel: JSONObject?): String? {
        if (aiModel == null || aiModel.opt("status") != "CONFIGURED") return null
        val model = aiModel.opt("configured_model") as? String ?: return null
        // A model id is short printable ASCII; anything else is not shown as a name.
        return model.takeIf { it.length in 1..MAX_MODEL_NAME_LENGTH && it.all { char -> char in '!'..'~' } }
    }

    private fun readBoundedUtf8(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            require(total <= MAX_RESPONSE_BYTES) { "Version response exceeded the size limit." }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 8_000
        const val MAX_RESPONSE_BYTES = 64 * 1024
        const val MAX_MODEL_NAME_LENGTH = 64

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
