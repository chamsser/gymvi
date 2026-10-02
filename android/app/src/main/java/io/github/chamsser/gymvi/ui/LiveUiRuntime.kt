package io.github.chamsser.gymvi.ui

import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONException

enum class LiveUiConnectionStatus {
    DISABLED,
    CONNECTING,
    LIVE,
    STALE,
    ERROR,
}

data class LiveUiRuntimeState(
    val status: LiveUiConnectionStatus,
    val spec: LiveUiSpec? = null,
) {
    companion object {
        val Disabled = LiveUiRuntimeState(LiveUiConnectionStatus.DISABLED)
    }
}

internal class LiveUiController(
    endpoint: String,
    token: String,
) : Closeable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gymvi-live-ui").apply { isDaemon = true }
    }
    private val lock = Any()
    private val client: LiveUiClient?

    private var state: LiveUiRuntimeState
    private var observer: ((LiveUiRuntimeState) -> Unit)? = null
    private var started = false
    private var closed = false
    private var etag: String? = null

    init {
        if (endpoint.isBlank() && token.isBlank()) {
            client = null
            state = LiveUiRuntimeState.Disabled
        } else {
            client = runCatching {
                LiveUiClient(LiveUiEndpointConfig.parse(endpoint, token))
            }.getOrNull()
            state = if (client == null) {
                LiveUiRuntimeState(LiveUiConnectionStatus.ERROR)
            } else {
                LiveUiRuntimeState(LiveUiConnectionStatus.CONNECTING)
            }
        }
    }

    val currentState: LiveUiRuntimeState
        get() = synchronized(lock) { state }

    fun observe(observer: ((LiveUiRuntimeState) -> Unit)?) {
        val snapshot = synchronized(lock) {
            if (closed) return
            this.observer = observer
            state
        }
        observer?.let { callback -> mainHandler.post { callback(snapshot) } }
    }

    fun start() {
        val activeClient = synchronized(lock) {
            if (closed || started || client == null) return
            started = true
            client
        }

        executor.execute {
            while (!Thread.currentThread().isInterrupted) {
                val currentEtag = synchronized(lock) {
                    if (closed) return@execute
                    etag
                }
                val result = activeClient.fetch(currentEtag)
                synchronized(lock) {
                    if (closed) return@execute
                    when (result) {
                        is LiveUiFetchResult.Updated -> {
                            etag = result.etag
                            updateLocked(
                                LiveUiRuntimeState(
                                    status = LiveUiConnectionStatus.LIVE,
                                    spec = result.spec,
                                ),
                            )
                        }

                        LiveUiFetchResult.NotModified -> {
                            updateLocked(state.copy(status = LiveUiConnectionStatus.LIVE))
                        }

                        LiveUiFetchResult.Failed -> {
                            updateLocked(
                                state.copy(
                                    status = if (state.spec == null) {
                                        LiveUiConnectionStatus.ERROR
                                    } else {
                                        LiveUiConnectionStatus.STALE
                                    },
                                ),
                            )
                        }
                    }
                }

                try {
                    Thread.sleep(POLL_INTERVAL_MILLIS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@execute
                }
            }
        }
    }

    private fun updateLocked(nextState: LiveUiRuntimeState) {
        if (nextState == state) return
        state = nextState
        val snapshot = state
        mainHandler.post {
            val callback = synchronized(lock) {
                if (closed) null else observer
            }
            callback?.invoke(snapshot)
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            observer = null
        }
        executor.shutdownNow()
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 800L
    }
}

internal data class LiveUiEndpointConfig(
    val endpoint: URL,
    val token: String,
) {
    companion object {
        fun parse(endpoint: String, token: String): LiveUiEndpointConfig {
            require(TOKEN_PATTERN.matches(token)) { "Live UI token is invalid." }
            val uri = URI(endpoint)
            require(uri.isAbsolute && uri.rawUserInfo == null) { "Live UI endpoint is invalid." }
            require(uri.rawQuery == null && uri.rawFragment == null) { "Live UI endpoint is invalid." }
            require(uri.path == LIVE_UI_PATH) { "Live UI endpoint path is invalid." }
            require(uri.port == -1 || uri.port in 1..65535) { "Live UI endpoint port is invalid." }

            val scheme = uri.scheme.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            val isLocalHost = host == "127.0.0.1" || host == "10.0.2.2" || host == "localhost"
            val isTailnetHost = host.endsWith(".ts.net") && host.length > ".ts.net".length
            val isTailnetIpv4 = isTailnetIpv4(host)
            require(
                (scheme == "https" && (isTailnetHost || isLocalHost)) ||
                    (scheme == "http" && (isTailnetIpv4 || isLocalHost)),
            ) {
                "Live UI must use the tailnet or a local development endpoint."
            }

            return LiveUiEndpointConfig(endpoint = uri.toURL(), token = token)
        }

        private const val LIVE_UI_PATH = "/api/v1/live-ui"
        private val TOKEN_PATTERN = Regex("[A-Za-z0-9_-]{32,128}")

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

private class LiveUiClient(
    private val config: LiveUiEndpointConfig,
) {
    fun fetch(etag: String?): LiveUiFetchResult {
        val connection = config.endpoint.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${config.token}")
            connection.setRequestProperty("User-Agent", "Gymvi-Android/LiveUI")
            etag?.let { connection.setRequestProperty("If-None-Match", it) }

            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> LiveUiFetchResult.NotModified
                HttpURLConnection.HTTP_OK -> {
                    val contentType = connection.contentType.orEmpty().substringBefore(';').trim()
                    if (contentType != "application/json") {
                        LiveUiFetchResult.Failed
                    } else {
                        val body = connection.inputStream.use(::readBoundedUtf8)
                        val spec = LiveUiSpecParser.parse(body)
                        LiveUiFetchResult.Updated(
                            spec = spec,
                            etag = connection.getHeaderField("ETag")?.takeIf(String::isNotBlank),
                        )
                    }
                }

                else -> LiveUiFetchResult.Failed
            }
        } catch (_: IOException) {
            LiveUiFetchResult.Failed
        } catch (_: JSONException) {
            LiveUiFetchResult.Failed
        } catch (_: IllegalArgumentException) {
            LiveUiFetchResult.Failed
        } catch (_: RuntimeException) {
            LiveUiFetchResult.Failed
        } finally {
            connection.disconnect()
        }
    }

    private fun readBoundedUtf8(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            require(total <= LiveUiSpecParser.MAX_SPEC_BYTES) {
                "Live UI response exceeded the size limit."
            }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 2_000
        private const val READ_TIMEOUT_MILLIS = 2_000
    }
}

private sealed interface LiveUiFetchResult {
    data class Updated(
        val spec: LiveUiSpec,
        val etag: String?,
    ) : LiveUiFetchResult

    data object NotModified : LiveUiFetchResult
    data object Failed : LiveUiFetchResult
}
