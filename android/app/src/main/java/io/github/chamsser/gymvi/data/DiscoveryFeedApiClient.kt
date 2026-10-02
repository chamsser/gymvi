package io.github.chamsser.gymvi.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale

class DiscoveryFeedApiClient(private val baseUrl: String) {
    private val usageOptionParser = UsageOptionApiClient(baseUrl)
    init {
        if (baseUrl.isNotBlank()) validateBaseUrl(baseUrl)
    }

    fun fetch(
        bounds: FacilityBounds,
        localHour: Int,
        favoriteFacilityIds: Set<String>,
        recentFacilityIds: List<String>,
        preferredCategories: Set<String> = emptySet(),
    ): DiscoveryFeedFetchResult {
        if (baseUrl.isBlank()) {
            return DiscoveryFeedFetchResult.ApiFailure(0, "API_NOT_CONFIGURED", false)
        }
        // The caller runs this on an executor, so a request that cannot be built must not throw.
        val connection = try {
            buildEndpoint(
                bounds,
                localHour,
                favoriteFacilityIds,
                recentFacilityIds,
                preferredCategories,
            ).openConnection() as HttpURLConnection
        } catch (exception: IllegalArgumentException) {
            return DiscoveryFeedFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } catch (exception: IOException) {
            return DiscoveryFeedFetchResult.NetworkFailure(exception.javaClass.simpleName)
        }
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Gymvi-Android/0.3")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use(::readBoundedUtf8).orEmpty()
            if (status == HttpURLConnection.HTTP_OK) {
                DiscoveryFeedFetchResult.Success(parseSuccess(body))
            } else {
                val error = runCatching { JSONObject(body).getJSONObject("error") }.getOrNull()
                DiscoveryFeedFetchResult.ApiFailure(
                    statusCode = status,
                    code = error?.optString("code")?.takeIf(String::isNotBlank)
                        ?: "HTTP_$status",
                    retryable = error?.optBoolean("retryable", status >= 500) ?: (status >= 500),
                )
            }
        } catch (exception: IOException) {
            DiscoveryFeedFetchResult.NetworkFailure(exception.javaClass.simpleName)
        } catch (exception: JSONException) {
            DiscoveryFeedFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } catch (exception: IllegalArgumentException) {
            DiscoveryFeedFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    internal fun buildEndpoint(
        bounds: FacilityBounds,
        localHour: Int,
        favoriteFacilityIds: Set<String>,
        recentFacilityIds: List<String>,
        preferredCategories: Set<String> = emptySet(),
    ): URL {
        require(localHour in 0..23)
        require(preferredCategories.all { it in MEMORY_CATEGORIES })
        val parameters = buildList {
            add("min_longitude=${bounds.minLongitude.asQueryNumber()}")
            add("min_latitude=${bounds.minLatitude.asQueryNumber()}")
            add("max_longitude=${bounds.maxLongitude.asQueryNumber()}")
            add("max_latitude=${bounds.maxLatitude.asQueryNumber()}")
            add("local_hour=$localHour")
            sentFacilityIds(favoriteFacilityIds).sorted().take(MAX_SENT_FACILITY_IDS).forEach { id ->
                add("favorite_facility_id=${encodeFacilityId(id)}")
            }
            sentFacilityIds(recentFacilityIds).take(MAX_SENT_FACILITY_IDS).forEach { id ->
                add("recent_facility_id=${encodeFacilityId(id)}")
            }
            preferredCategories.sorted().forEach { add("preferred_category=$it") }
        }
        return URL(baseUrl.trimEnd('/') + "/api/v1/discovery-feed?" + parameters.joinToString("&"))
    }

    internal fun parseSuccess(body: String): DiscoveryFeedPage {
        val root = JSONObject(body)
        val data = root.getJSONObject("data")
        val meta = root.getJSONObject("meta")
        val environment = data.getJSONObject("environment")
        val weather = environment.optJSONObject("weather")?.let { item ->
            DiscoveryWeatherItem(
                temperatureCelsius = item.requiredFiniteDouble("temperature_celsius"),
                apparentTemperatureCelsius = item.optionalFiniteDouble(
                    "apparent_temperature_celsius",
                ),
                condition = item.requiredTrimmedString("condition"),
                precipitationMillimeters = item.requiredFiniteDouble(
                    "precipitation_millimeters",
                ).also { value -> require(value >= 0.0) },
                observedAt = item.requiredTrimmedString("observed_at"),
                sourceName = item.requiredTrimmedString("source_name"),
            )
        }
        val proposals = data.getJSONArray("exercise_proposals").mapObjects { item ->
            ExerciseProposalItem(
                proposalId = item.requiredTrimmedString("proposal_id"),
                title = item.requiredTrimmedString("title"),
                summary = item.requiredTrimmedString("summary"),
                category = item.requiredTrimmedString("category"),
                reasonCodes = item.getJSONArray("reason_codes").stringValues(),
            )
        }
        val facilities = data.getJSONArray("facilities").mapObjects { item ->
            DiscoveryFacilityItem(
                facilityId = item.getJSONObject("facility").requiredTrimmedString("facility_id"),
                distanceMeters = item.getInt("distance_meters").also { require(it >= 0) },
                score = item.getInt("score"),
                reasonCodes = item.getJSONArray("reason_codes").stringValues(),
            )
        }
        val contents = data.getJSONArray("exercise_contents").mapObjects { item ->
            ExerciseContentItem(
                contentId = item.requiredTrimmedString("content_id"),
                title = item.requiredTrimmedString("title"),
                summary = item.requiredTrimmedString("summary"),
                category = item.requiredTrimmedString("category"),
                audience = item.requiredTrimmedString("audience"),
                thumbnailUrl = item.requiredHttpsUrl("thumbnail_url", setOf("img.youtube.com")),
                contentUrl = item.requiredHttpsUrl(
                    "content_url",
                    setOf("www.youtube.com", "youtube.com", "youtu.be"),
                ),
                sourceName = item.requiredTrimmedString("source_name"),
                sourceUrl = item.requiredHttpsUrl("source_url", setOf("nfa.kspo.or.kr")),
                channelName = item.exerciseChannelName(),
                viewCount = item.exerciseStatistic("view_count"),
                likeCount = item.exerciseStatistic("like_count"),
                durationSeconds = item.exerciseDuration(),
            )
        }
        require(proposals.distinctBy(ExerciseProposalItem::proposalId).size == proposals.size)
        require(facilities.distinctBy(DiscoveryFacilityItem::facilityId).size == facilities.size)
        require(contents.distinctBy(ExerciseContentItem::contentId).size == contents.size)
        val usagePart = runCatching {
            val options = if (data.has("usage_options")) {
                data.getJSONArray("usage_options").mapObjects { item ->
                    require(item.getInt("rank") >= 1)
                    item.getInt("score")
                    item.getJSONArray("score_components")
                    item.getJSONArray("matched_conditions")
                    item.getJSONArray("unmet_preferred_conditions")
                    item.getJSONArray("uncertainty_codes")
                    item.getJSONObject("facts")
                    val option = item.getJSONObject("option")
                    FeedUsageOption(
                        option = usageOptionParser.parseOption(option),
                        facilityName = option.requiredTrimmedString("facility_name"),
                    )
                }
            } else emptyList()
            require(options.map { it.option.usageOptionId }.distinct().size == options.size)
            val source = data.optJSONObject("usage_options_source")?.let { raw ->
                UsageOptionsSource(
                    programDatasetVersion = raw.requiredTrimmedString("program_dataset_version"),
                    programAsOf = raw.optionalString("program_as_of")?.also(Instant::parse),
                    attribution = raw.optionalString("attribution"),
                )
            }
            options to source
        }.getOrElse { emptyList<FeedUsageOption>() to null }
        return DiscoveryFeedPage(
            areaLabel = data.requiredTrimmedString("area_label"),
            dayPart = environment.requiredTrimmedString("day_part"),
            weather = weather,
            proposals = proposals,
            facilitySuggestions = facilities,
            contents = contents,
            usageOptionsUnavailableReasonCode = data.optionalString(
                "usage_options_unavailable_reason_code",
            ),
            datasetVersion = meta.requiredTrimmedString("dataset_version"),
            asOf = meta.requiredTrimmedString("as_of"),
            usageOptions = usagePart.first,
            usageOptionsSource = usagePart.second,
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
            require(total <= MAX_RESPONSE_BYTES) { "Discovery response exceeded the size limit." }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    /** Trimmed, non-blank, first-seen IDs, cleaned the way AiTurnApiClient cleans them. */
    private fun sentFacilityIds(ids: Collection<String>): List<String> =
        ids.map(String::trim).filter(String::isNotEmpty).distinct()

    private fun encodeFacilityId(id: String): String {
        val normalized = id.trim()
        require(normalized.isNotEmpty() && normalized.length <= MAX_FACILITY_ID_LENGTH)
        return URLEncoder.encode(normalized, StandardCharsets.UTF_8.name())
    }

    private fun validateBaseUrl(value: String) {
        val uri = URI(value)
        require(uri.isAbsolute && uri.rawUserInfo == null)
        require(uri.rawQuery == null && uri.rawFragment == null)
        require(uri.path.isNullOrEmpty() || uri.path == "/")
        val scheme = uri.scheme.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val local = host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost"
        require(host.isNotBlank() && (scheme == "https" || scheme == "http" && (local || isTailnetIpv4(host))))
    }

    private fun JSONObject.requiredTrimmedString(name: String): String =
        getString(name).trim().also { value -> require(value.isNotEmpty()) }

    private fun JSONObject.optionalString(name: String): String? =
        if (isNull(name)) null else getString(name).trim().ifEmpty { null }

    private fun JSONObject.requiredFiniteDouble(name: String): Double =
        getDouble(name).also { value -> require(value.isFinite()) }

    private fun JSONObject.optionalFiniteDouble(name: String): Double? =
        if (isNull(name)) null else requiredFiniteDouble(name)

    private fun JSONObject.requiredHttpsUrl(name: String, allowedHosts: Set<String>): String =
        requiredTrimmedString(name).also { value ->
            val uri = URI(value)
            require(uri.scheme == "https" && uri.host?.lowercase(Locale.ROOT) in allowedHosts)
        }

    private fun JSONArray.stringValues(): List<String> = buildList(length()) {
        for (index in 0 until length()) add(getString(index).trim().also { require(it.isNotEmpty()) })
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        buildList(length()) {
            for (index in 0 until length()) add(transform(getJSONObject(index)))
        }

    private fun Double.asQueryNumber(): String = String.format(Locale.ROOT, "%.8f", this)

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 5_000
        private const val READ_TIMEOUT_MILLIS = 8_000
        private const val MAX_RESPONSE_BYTES = 512 * 1024
        // Same cap as AiTurnApiClient; the server still accepts up to 50 per list.
        private const val MAX_SENT_FACILITY_IDS = 20
        private const val MAX_FACILITY_ID_LENGTH = 160

        private fun isTailnetIpv4(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != 4) return false
            val octets = parts.map { it.toIntOrNull() ?: return false }
            return octets.all { it in 0..255 } && octets[0] == 100 && octets[1] in 64..127
        }
    }
}
