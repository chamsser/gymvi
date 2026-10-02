package io.github.chamsser.gymvi.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale

class AiTurnApiClient(private val baseUrl: String) {
    private val usageOptionParser = UsageOptionApiClient(baseUrl)
    private val streamingConnection = java.util.concurrent.atomic.AtomicReference<HttpURLConnection?>()

    fun cancelStreaming() { runCatching { streamingConnection.getAndSet(null)?.disconnect() } }

    init {
        if (baseUrl.isNotBlank()) validateBaseUrl(baseUrl)
    }

    fun turn(
        conversationId: String?,
        message: String,
        context: AiTurnRequestContext?,
        origin: AiRequestOrigin?,
    ): AiTurnFetchResult {
        if (baseUrl.isBlank()) return AiTurnFetchResult.ApiFailure(0, "API_NOT_CONFIGURED", false)
        val connection = URL(baseUrl.trimEnd('/') + "/api/v1/ai/turns")
            .openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "Gymvi-Android/ai")
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(
                    buildRequestBody(conversationId, message, context, origin)
                        .toByteArray(StandardCharsets.UTF_8),
                )
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use(::readBoundedUtf8).orEmpty()
            if (status == HttpURLConnection.HTTP_OK) {
                AiTurnFetchResult.Success(parseSuccess(body), body)
            } else {
                parseApiFailure(status, body)
            }
        } catch (exception: IOException) {
            AiTurnFetchResult.NetworkFailure(exception.javaClass.simpleName)
        } catch (exception: JSONException) {
            AiTurnFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } catch (exception: IllegalArgumentException) {
            AiTurnFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } catch (exception: java.time.DateTimeException) {
            AiTurnFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    /** Delivers actual server deltas on the caller's worker thread until a terminal event. */
    fun turnStreaming(
        conversationId: String?,
        message: String,
        context: AiTurnRequestContext?,
        origin: AiRequestOrigin?,
        onReplyDelta: (String) -> Unit,
        references: AiReferenceContext = AiReferenceContext(),
    ): AiTurnFetchResult {
        if (baseUrl.isBlank()) return AiTurnFetchResult.ApiFailure(0, "API_NOT_CONFIGURED", false)
        val connection = URL(baseUrl.trimEnd('/') + "/api/v1/ai/turns/stream")
            .openConnection() as HttpURLConnection
        streamingConnection.set(connection)
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = STREAM_READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", "Gymvi-Android/ai")
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(buildRequestBody(conversationId, message, context, origin, references)
                    .toByteArray(StandardCharsets.UTF_8))
            }
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                parseApiFailure(status, connection.errorStream?.use(::readBoundedUtf8).orEmpty())
            } else if (!connection.contentType.orEmpty().substringBefore(';')
                    .trim().equals("text/event-stream", ignoreCase = true)) {
                AiTurnFetchResult.InvalidResponse("UnexpectedAiStreamContentType")
            } else {
                connection.inputStream.use { readEventStream(it, onReplyDelta) }
            }
        } catch (exception: IOException) {
            AiTurnFetchResult.NetworkFailure(exception.javaClass.simpleName)
        } catch (exception: JSONException) {
            AiTurnFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } catch (exception: IllegalArgumentException) {
            AiTurnFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } catch (exception: java.time.DateTimeException) {
            AiTurnFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } finally {
            streamingConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    internal fun readEventStream(input: InputStream, onReplyDelta: (String) -> Unit): AiTurnFetchResult {
        // The bound applies before readLine allocation, including comments and ignored events.
        val bounded = object : FilterInputStream(input) {
            var total = 0
            private fun record(count: Int) {
                if (count > 0) total += count
                require(total <= MAX_RESPONSE_BYTES) { "AI stream exceeded the size limit." }
            }
            override fun read(): Int = `in`.read().also { if (it >= 0) record(1) }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
                `in`.read(bytes, offset, length).also(::record)
        }
        val reader = bounded.bufferedReader(StandardCharsets.UTF_8)
        var event = "message"
        val data = StringBuilder()
        var replyLength = 0
        var firstLine = true
        while (true) {
            val raw = reader.readLine() ?: return AiTurnFetchResult.InvalidResponse("IncompleteAiStream")
            val line = if (firstLine) raw.removePrefix("\uFEFF") else raw
            firstLine = false
            if (line.isEmpty()) {
                if (data.isNotEmpty()) {
                    val body = data.toString().removeSuffix("\n")
                    when (event) {
                        "reply_delta" -> {
                            val delta = JSONObject(body).get("delta") as? String
                                ?: throw IllegalArgumentException("Invalid reply delta")
                            replyLength += delta.length
                            require(replyLength <= MAX_REPLY_LENGTH && '\u0000' !in delta)
                            if (delta.isNotEmpty()) onReplyDelta(delta)
                        }
                        "complete" -> return AiTurnFetchResult.Success(parseSuccess(body), body)
                        "error" -> return parseApiFailure(500, body)
                    }
                }
                event = "message"
                data.setLength(0)
            } else if (!line.startsWith(':')) {
                val name = line.substringBefore(':')
                val value = line.substringAfter(':', "").removePrefix(" ")
                when (name) {
                    "event" -> event = value
                    "data" -> data.append(value).append('\n')
                }
            }
        }
    }

    internal fun buildRequestBody(
        conversationId: String?,
        message: String,
        context: AiTurnRequestContext?,
        origin: AiRequestOrigin?,
        references: AiReferenceContext = AiReferenceContext(),
    ): String {
        val normalizedMessage = message.trim()
        require(normalizedMessage.isNotEmpty() && normalizedMessage.length <= MAX_MESSAGE_LENGTH)
        require(normalizedMessage.none { it == '\u0000' })
        require(conversationId == null || CONVERSATION_ID_PATTERN.matches(conversationId))
        return JSONObject()
            .apply { conversationId?.let { put("conversation_id", it) } }
            .put("message", normalizedMessage)
            .apply {
                put("references", JSONObject().apply {
                    references.memory?.let { put("memory", it.toJson()) }
                    put("age_band", references.ageBand?.name)
                    put("experience", references.experience?.name)
                    put("goal", references.goal?.name)
                })
                references.continuation?.let { put("continuation", it) }
                origin?.let { requestOrigin ->
                    put(
                        "origin",
                        JSONObject()
                            .put("latitude", requestOrigin.latitude)
                            .put("longitude", requestOrigin.longitude),
                    )
                }
                context?.let { requestContext ->
                    val favoriteIds = requestContext.favoriteFacilityIds.map(String::trim)
                        .filter(String::isNotEmpty)
                        .distinct()
                        .sorted()
                        .take(MAX_PREFERENCE_IDS)
                    val recentIds = requestContext.recentFacilityIds.map(String::trim)
                        .filter(String::isNotEmpty)
                        .distinct()
                        .take(MAX_PREFERENCE_IDS)
                    require((favoriteIds + recentIds).all { it.length <= MAX_ID_LENGTH })
                    val contextJson = JSONObject()
                        .put(
                            "area",
                            JSONObject()
                                .put("min_longitude", requestContext.area.minLongitude)
                                .put("min_latitude", requestContext.area.minLatitude)
                                .put("max_longitude", requestContext.area.maxLongitude)
                                .put("max_latitude", requestContext.area.maxLatitude),
                        )
                        .put("date", requestContext.date.toString())
                        .put(
                            "preferences",
                            JSONObject()
                                .put("favorite_facility_ids", JSONArray(favoriteIds))
                                .put("recent_facility_ids", JSONArray(recentIds)),
                        )
                    put("context", contextJson)
                }
            }
            .toString()
    }

    internal fun parseSuccess(body: String): AiTurnResponse {
        val root = JSONObject(body)
        val data = root.getJSONObject("data")
        val meta = root.getJSONObject("meta")
        val conversationId = data.requiredString("conversation_id")
            .also { require(CONVERSATION_ID_PATTERN.matches(it)) }
        val cards = data.getJSONArray("cards").mapObjects(::parseCard)
        require(cards.map { it.option.usageOptionId }.distinct().size == cards.size)
        val cardsByOptionId = cards.associateBy { it.option.usageOptionId }
        val exerciseContents = data.optJSONArray("exercise_contents")?.mapObjects(::parseExerciseContent)
            .orEmpty()
        require(exerciseContents.distinctBy(ExerciseContentItem::contentId).size == exerciseContents.size)
        val clientActions = data.getJSONArray("client_actions").mapObjects { raw ->
            val type = AiClientActionType.valueOf(raw.requiredString("type"))
            val optionId = raw.optionalString("option_id")
            val facilityId = raw.optionalString("facility_id")
            val optionIds = raw.optJSONArray("option_ids")?.strings().orEmpty()
            when (type) {
                AiClientActionType.OPEN_MANUAL_FILTERS -> {
                    require(optionId == null && facilityId == null && optionIds.isEmpty())
                }
                AiClientActionType.OPEN_USAGE_OPTION_COMPARISON -> {
                    require(optionId == null && facilityId == null)
                    require(optionIds.size in 2..3 && optionIds.distinct().size == optionIds.size)
                    require(optionIds.all(cardsByOptionId::containsKey))
                }
                else -> {
                    require(optionId != null && facilityId != null && optionIds.isEmpty())
                    require(cardsByOptionId[optionId]?.option?.facilityId == facilityId)
                }
            }
            AiClientAction(type, optionId, facilityId, optionIds)
        }
        val reply = data.requiredString("reply").also { require(it.length <= MAX_REPLY_LENGTH) }
        val fallback = data.getBoolean("fallback")
        val fallbackReason = data.optionalString("fallback_reason_code")
        require(fallback || fallbackReason == null)
        val datasetVersion = meta.optionalString("dataset_version")
        val asOf = meta.optionalString("as_of")?.also(Instant::parse)
        return AiTurnResponse(
            conversationId = conversationId,
            reply = reply,
            action = AiTurnAction.valueOf(data.requiredString("action")),
            cards = cards,
            exerciseContents = exerciseContents,
            clientActions = clientActions,
            uncertainties = data.getJSONArray("uncertainties").strings().also(::requireDistinct),
            fallback = fallback,
            fallbackReasonCode = fallbackReason,
            datasetVersion = datasetVersion,
            asOf = asOf,
            conversationTitle = data.optionalString("conversation_title")?.also {
                require(it.length <= 80 && it.none(Char::isISOControl))
            },
            continuation = data.optionalString("continuation")?.also { require(it.length <= 8000) },
            memoryFacts = data.optJSONObject("memory_facts")?.let(::aiMemoryFactsFromJson),
        )
    }

    private fun parseCard(raw: JSONObject): AiTurnCard {
        val optionJson = raw.getJSONObject("option")
        val option = usageOptionParser.parseOption(optionJson)
        val facts = raw.getJSONObject("facts")
        val distance = facts.getJSONObject("straight_line_distance_meters").knownNonNegativeInt()
        val latitude = optionJson.getDouble("latitude")
        val longitude = optionJson.getDouble("longitude")
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        return AiTurnCard(
            rank = raw.getInt("rank").also { require(it >= 1) },
            score = raw.getInt("score"),
            matchedConditions = raw.getJSONArray("matched_conditions").strings().also(::requireDistinct),
            unmetPreferredConditions = raw.getJSONArray("unmet_preferred_conditions").strings().also(::requireDistinct),
            uncertaintyCodes = raw.getJSONArray("uncertainty_codes").strings().also(::requireDistinct),
            straightLineDistanceMeters = distance,
            option = option,
            facilityName = optionJson.requiredString("facility_name"),
            facilityTypeName = optionJson.optionalString("facility_type_name"),
            roadAddress = optionJson.optionalString("road_address"),
            latitude = latitude,
            longitude = longitude,
        )
    }

    private fun parseExerciseContent(raw: JSONObject): ExerciseContentItem = ExerciseContentItem(
        contentId = raw.requiredString("content_id"),
        title = raw.requiredString("title"),
        summary = raw.requiredString("summary"),
        category = raw.requiredString("category"),
        audience = raw.requiredString("audience"),
        thumbnailUrl = raw.requiredHttpsUrl("thumbnail_url", setOf("img.youtube.com")),
        contentUrl = raw.requiredHttpsUrl(
            "content_url",
            setOf("www.youtube.com", "youtube.com", "youtu.be"),
        ),
        sourceName = raw.requiredString("source_name"),
        sourceUrl = raw.requiredHttpsUrl("source_url", setOf("nfa.kspo.or.kr")),
        channelName = raw.exerciseChannelName(),
        viewCount = raw.exerciseStatistic("view_count"),
        likeCount = raw.exerciseStatistic("like_count"),
        durationSeconds = raw.exerciseDuration(),
    )

    private fun JSONObject.knownNonNegativeInt(): Int? {
        return when (requiredString("state")) {
            "KNOWN" -> getInt("value").also { require(it >= 0) }
            "UNKNOWN" -> null
            else -> throw IllegalArgumentException("Invalid fact state")
        }
    }

    private fun parseApiFailure(statusCode: Int, body: String): AiTurnFetchResult.ApiFailure {
        val error = runCatching { JSONObject(body).getJSONObject("error") }.getOrNull()
        return AiTurnFetchResult.ApiFailure(
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
            require(total <= MAX_RESPONSE_BYTES) { "AI response exceeded the size limit." }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun JSONObject.requiredString(name: String): String =
        (get(name) as? String)?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Missing or invalid $name")

    private fun JSONObject.optionalString(name: String): String? =
        if (!has(name) || isNull(name)) null else requiredString(name)

    private fun JSONObject.requiredHttpsUrl(name: String, allowedHosts: Set<String>): String =
        requiredString(name).also { value ->
            val uri = try {
                URI(value)
            } catch (exception: URISyntaxException) {
                throw IllegalArgumentException("Invalid $name", exception)
            }
            require(uri.scheme.equals("https", ignoreCase = true))
            require(uri.host?.lowercase(Locale.ROOT) in allowedHosts)
            require(uri.rawUserInfo == null)
        }

    private fun JSONArray.strings(): List<String> = List(length()) { index ->
        (get(index) as? String)?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Invalid array entry")
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        List(length()) { index -> transform(getJSONObject(index)) }

    private fun requireDistinct(values: List<String>) {
        require(values.distinct().size == values.size)
    }

    private fun validateBaseUrl(value: String) {
        val uri = URI(value)
        require(uri.isAbsolute && uri.rawUserInfo == null)
        require(uri.rawQuery == null && uri.rawFragment == null)
        require(uri.path.isNullOrEmpty() || uri.path == "/")
        require(uri.port == -1 || uri.port in 1..65535)
        val scheme = uri.scheme.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val local = host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost"
        require(host.isNotBlank() && (scheme == "https" || scheme == "http" && (local || isTailnetIpv4(host))))
    }

    private fun isTailnetIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        return octets.all { it in 0..255 } && octets[0] == 100 && octets[1] in 64..127
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val STREAM_READ_TIMEOUT_MILLIS = 125_000
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
        const val MAX_MESSAGE_LENGTH = 1_000
        const val MAX_REPLY_LENGTH = 2_000
        const val MAX_PREFERENCE_IDS = 20
        const val MAX_ID_LENGTH = 160
        val CONVERSATION_ID_PATTERN = Regex("^[A-Za-z0-9_-]{16,80}$")
    }
}
