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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.Locale

class UsageOptionApiClient(private val baseUrl: String) {
    init {
        if (baseUrl.isNotBlank()) validateBaseUrl(baseUrl)
    }

    fun fetchForFacility(facilityId: String): UsageOptionFetchResult<UsageOptionPage> {
        if (baseUrl.isBlank()) return UsageOptionFetchResult.ApiFailure(0, "API_NOT_CONFIGURED", false)
        require(facilityId.isNotBlank())
        return request(buildFacilityEndpoint(facilityId), { body -> parseList(body, facilityId) })
    }

    fun fetchEvidence(evidenceHref: String): UsageOptionFetchResult<ProgramEvidence> {
        if (!isValidProgramEvidenceHref(evidenceHref)) {
            return UsageOptionFetchResult.InvalidResponse("Invalid program evidence href")
        }
        if (baseUrl.isBlank()) return UsageOptionFetchResult.ApiFailure(0, "API_NOT_CONFIGURED", false)
        return request(URL(baseUrl.trimEnd('/') + evidenceHref), ::parseEvidence)
    }

    fun compare(ids: List<String>): UsageOptionFetchResult<UsageOptionComparisonData> {
        require(ids.size in 2..5 && ids.distinct().size == ids.size)
        require(ids.all { it.isNotBlank() && it.length <= 160 })
        if (baseUrl.isBlank()) return UsageOptionFetchResult.ApiFailure(0, "API_NOT_CONFIGURED", false)
        return request(
            URL(baseUrl.trimEnd('/') + "/api/v1/usage-options/compare"),
            ::parseComparison,
            requestBody = buildComparisonBody(ids),
        )
    }

    internal fun buildComparisonBody(ids: List<String>): String =
        JSONObject().put("usage_option_ids", JSONArray(ids)).toString()

    internal fun buildFacilityEndpoint(facilityId: String): URL {
        require(facilityId.isNotBlank())
        val encodedId = URLEncoder.encode(facilityId, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return URL(baseUrl.trimEnd('/') + "/api/v1/facilities/$encodedId/usage-options")
    }

    internal fun parseList(body: String, expectedFacilityId: String): UsageOptionFetchResult<UsageOptionPage> =
        parseSafely {
            val root = JSONObject(body)
            validateMeta(root)
            val data = root.getJSONObject("data")
            val facilityId = data.requiredString("facility_id")
            require(facilityId == expectedFacilityId)
            val rawOptions = data.getJSONArray("usage_options")
            val options = List(rawOptions.length()) { index ->
                parseOption(rawOptions.getJSONObject(index)).also { require(it.facilityId == facilityId) }
            }
            require(options.map(UsageOptionItem::usageOptionId).distinct().size == options.size)
            UsageOptionPage(
                facilityId = facilityId,
                programDatasetVersion = data.requiredNullableString("program_dataset_version"),
                programAsOf = data.optionalString("program_as_of")?.also(Instant::parse),
                attribution = data.optionalString("attribution"),
                options = options,
                unavailableReasonCodes = data.getJSONArray("unavailable_reason_codes").strings(),
            )
        }

    internal fun parseOption(raw: JSONObject): UsageOptionItem = UsageOptionItem(
        usageOptionId = raw.requiredString("usage_option_id"),
        facilityId = raw.requiredString("facility_id"),
        programName = raw.requiredString("program_name"),
        programTypeName = raw.requiredNullableString("program_type_name"),
        targetName = raw.requiredNullableString("target_name"),
        beginDate = raw.requiredNullableString("begin_date")?.let(LocalDate::parse),
        endDate = raw.requiredNullableString("end_date")?.let(LocalDate::parse),
        weekdays = raw.getJSONArray("weekdays").strings(),
        sourceTimeValue = raw.requiredNullableString("source_time_value"),
        recruitmentCount = raw.requiredNullableInt("recruitment_count"),
        priceWon = raw.requiredNullableInt("price_won"),
        priceTypeName = raw.requiredNullableString("price_type_name"),
        homepageUrl = raw.requiredNullableString("homepage_url"),
        facilityOperation = raw.getJSONObject("facility_operation")
            .usageState(setOf("OPEN", "CLOSED", "UNKNOWN")),
        programApplication = raw.getJSONObject("program_application")
            .usageState(setOf("AVAILABLE", "CLOSED", "UNKNOWN")),
        joinState = raw.requiredString("join_state"),
        evidenceHref = raw.requiredString("evidence_href"),
        operatorTime = if (!raw.has("operator_time") || raw.isNull("operator_time")) null
            else parseOperatorTime(raw.getJSONObject("operator_time")),
    )

    private fun parseOperatorTime(raw: JSONObject): OperatorTime {
        val start = raw.requiredString("start_time")
        val end = raw.requiredString("end_time")
        require(TIME_PATTERN.matches(start) && TIME_PATTERN.matches(end))
        val sources = raw.getJSONArray("sources")
        require(sources.length() > 0)
        return OperatorTime(
            startTime = start,
            endTime = end,
            days = raw.requiredNullableString("days"),
            operatorProgramName = raw.requiredString("operator_program_name"),
            validFrom = LocalDate.parse(raw.requiredString("valid_from")),
            validTo = LocalDate.parse(raw.requiredString("valid_to")),
            sources = List(sources.length()) { index ->
                val source = sources.getJSONObject(index)
                OperatorTimeSource(source.requiredString("name"), source.requiredString("url"))
            },
            checkedAt = OffsetDateTime.parse(raw.requiredString("checked_at")),
        )
    }

    internal fun parseComparison(body: String): UsageOptionFetchResult<UsageOptionComparisonData> =
        parseSafely {
            val root = JSONObject(body)
            validateMeta(root)
            val data = root.getJSONObject("data")
            val rawItems = data.getJSONArray("items")
            val items = List(rawItems.length()) { index ->
                val item = rawItems.getJSONObject(index)
                val option = item.getJSONObject("option")
                UsageOptionComparisonItem(
                    usageOptionId = item.requiredString("usage_option_id")
                        .also { require(it == option.requiredString("usage_option_id")) },
                    eligible = item.getBoolean("eligible"),
                    option = parseOption(option),
                    facilityName = option.requiredString("facility_name"),
                )
            }
            require(items.map(UsageOptionComparisonItem::usageOptionId).distinct().size == items.size)
            val rawDimensions = data.getJSONArray("dimensions")
            val dimensions = (0 until rawDimensions.length()).mapNotNull { index ->
                val raw = rawDimensions.getJSONObject(index)
                val name = raw.requiredString("dimension")
                if (name !in COMPARISON_DIMENSIONS) return@mapNotNull null
                val values = raw.getJSONArray("values")
                UsageOptionComparisonDimension(
                    dimension = name,
                    values = List(values.length()) { valueIndex ->
                        val entry = values.getJSONObject(valueIndex)
                        require(entry.has("value"))
                        UsageOptionComparisonValue(
                            usageOptionId = entry.requiredString("usage_option_id"),
                            state = entry.requiredString("state").also {
                                require(it in setOf("KNOWN", "UNKNOWN"))
                            },
                            value = when (val value = entry.get("value")) {
                                JSONObject.NULL -> null
                                is JSONArray -> value.strings()
                                else -> value
                            },
                        )
                    },
                    bestUsageOptionIds = raw.getJSONArray("best_usage_option_ids").strings(),
                )
            }
            UsageOptionComparisonData(
                items = items,
                dimensions = dimensions,
                programAsOf = data.optionalString("program_as_of")?.also(Instant::parse),
                attribution = data.optionalString("attribution"),
            )
        }

    internal fun parseEvidence(body: String): UsageOptionFetchResult<ProgramEvidence> = parseSafely {
        val root = JSONObject(body)
        validateMeta(root)
        val data = root.getJSONObject("data")
        require(data.requiredString("evidence_kind") == "PROGRAM")
        data.requiredString("evidence_id")
        val subject = data.getJSONObject("subject")
        val dataset = data.getJSONObject("dataset")
        require(dataset.requiredString("dataset_kind") == "PROGRAM")
        dataset.requiredString("source_dataset_id")
        dataset.requiredString("schema_version")
        dataset.requiredString("raw_sha256")
        Instant.parse(dataset.requiredString("activated_at"))
        val sourceRecord = data.getJSONObject("source_record")
        sourceRecord.requiredString("source_record_id")
        sourceRecord.requiredString("provider_record_id")
        sourceRecord.requiredString("source_url")
        sourceRecord.requiredString("catalog_url")
        Instant.parse(sourceRecord.requiredString("as_of"))
        val rawFields = data.getJSONArray("fields")
        val fields = List(rawFields.length()) { index ->
            val raw = rawFields.getJSONObject(index)
            raw.requiredString("source_field")
            require(raw.has("normalized_value"))
            raw.requiredString("transformation")
            require(raw.requiredString("state") in setOf("KNOWN", "UNKNOWN"))
            raw.requiredNullableString("unknown_reason_code")
            ProgramEvidenceField(
                field = raw.requiredString("field"),
                sourceValue = raw.requiredNullableRawString("source_value"),
            )
        }
        val states = data.getJSONObject("states")
        val join = data.getJSONObject("facility_join")
        join.requiredString("join_state")
        join.getJSONArray("join_reason_codes").strings()
        join.requiredNullableString("facility_dataset_version")
        val joinInputs = join.getJSONArray("facility_join_inputs")
        for (index in 0 until joinInputs.length()) {
            val input = joinInputs.getJSONObject(index)
            input.requiredString("source_field")
            input.requiredNullableRawString("source_value")
        }
        val license = data.getJSONObject("license")
        license.requiredString("url")
        license.getBoolean("commercial_use_allowed")
        license.getBoolean("modification_allowed")
        Instant.parse(license.requiredString("checked_at"))
        ProgramEvidence(
            usageOptionId = subject.requiredString("usage_option_id"),
            facilityId = subject.requiredString("facility_id"),
            programName = subject.requiredString("program_name"),
            licenseAttribution = license.requiredString("attribution"),
            licenseName = license.requiredString("name"),
            datasetAsOf = dataset.requiredString("as_of").also(Instant::parse),
            datasetVersion = dataset.requiredString("dataset_version"),
            fields = fields,
            limitations = data.getJSONArray("limitations").strings(),
            facilityOperation = states.getJSONObject("facility_operation")
                .usageState(setOf("OPEN", "CLOSED", "UNKNOWN")),
            programApplication = states.getJSONObject("program_application")
                .usageState(setOf("AVAILABLE", "CLOSED", "UNKNOWN")),
        )
    }

    internal fun parseApiFailure(statusCode: Int, body: String): UsageOptionFetchResult.ApiFailure {
        val error = runCatching { JSONObject(body).getJSONObject("error") }.getOrNull()
        return UsageOptionFetchResult.ApiFailure(
            statusCode = statusCode,
            code = error?.optString("code")?.takeIf(String::isNotBlank) ?: "HTTP_$statusCode",
            retryable = error?.optBoolean("retryable", statusCode >= 500) ?: (statusCode >= 500),
        )
    }

    private fun <T> request(
        endpoint: URL,
        parse: (String) -> UsageOptionFetchResult<T>,
        requestBody: String? = null,
    ): UsageOptionFetchResult<T> {
        val connection = endpoint.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = if (requestBody == null) "GET" else "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Gymvi-Android/0.2")
            if (requestBody != null) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { output ->
                    output.write(requestBody.toByteArray(StandardCharsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use(::readBoundedUtf8).orEmpty()
            if (status == HttpURLConnection.HTTP_OK) parse(body) else parseApiFailure(status, body)
        } catch (exception: IOException) {
            UsageOptionFetchResult.NetworkFailure(exception.javaClass.simpleName)
        } catch (exception: IllegalArgumentException) {
            UsageOptionFetchResult.InvalidResponse(exception.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private fun <T> parseSafely(parse: () -> T): UsageOptionFetchResult<T> = try {
        UsageOptionFetchResult.Success(parse())
    } catch (exception: JSONException) {
        UsageOptionFetchResult.InvalidResponse(exception.javaClass.simpleName)
    } catch (exception: IllegalArgumentException) {
        UsageOptionFetchResult.InvalidResponse(exception.javaClass.simpleName)
    } catch (exception: java.time.DateTimeException) {
        UsageOptionFetchResult.InvalidResponse(exception.javaClass.simpleName)
    }

    private fun validateMeta(root: JSONObject) {
        val meta = root.getJSONObject("meta")
        meta.requiredString("dataset_version")
        Instant.parse(meta.requiredString("as_of"))
    }

    private fun JSONObject.requiredString(name: String): String =
        (get(name) as? String)?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Missing or invalid $name")

    private fun JSONObject.requiredNullableString(name: String): String? {
        require(has(name)) { "Missing $name" }
        if (isNull(name)) return null
        return requiredString(name)
    }

    private fun JSONObject.requiredNullableRawString(name: String): String? {
        require(has(name)) { "Missing $name" }
        if (isNull(name)) return null
        return get(name) as? String ?: throw IllegalArgumentException("Invalid $name")
    }

    private fun JSONObject.optionalString(name: String): String? =
        if (!has(name) || isNull(name)) null else requiredString(name)

    private fun JSONObject.requiredNullableInt(name: String): Int? {
        require(has(name)) { "Missing $name" }
        if (isNull(name)) return null
        val value = get(name) as? Number ?: throw IllegalArgumentException("Invalid $name")
        return value.toString().toIntOrNull()?.takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Invalid $name")
    }

    private fun JSONObject.usageState(allowed: Set<String>): UsageOptionState {
        val state = requiredString("state")
        require(state in allowed)
        return UsageOptionState(state, requiredString("reason_code"))
    }

    private fun JSONArray.strings(): List<String> = List(length()) { index ->
        (get(index) as? String)?.trim()?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Invalid array entry")
    }

    private fun readBoundedUtf8(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            total += read
            require(total <= MAX_RESPONSE_BYTES) { "Usage option response exceeded the size limit" }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val READ_TIMEOUT_MILLIS = 10_000
        const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
        val TIME_PATTERN = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")
        val COMPARISON_DIMENSIONS = setOf(
            "PRICE_WON", "START_TIME", "WEEKDAYS", "PERIOD", "PROGRAM_APPLICATION", "TARGET_GROUPS", "LEVEL",
        )

        fun validateBaseUrl(baseUrl: String) {
            val uri = URI(baseUrl)
            require(uri.isAbsolute && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null)
            require(uri.path.isNullOrEmpty() || uri.path == "/")
            require(uri.port == -1 || uri.port in 1..65535)
            val scheme = uri.scheme.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            val localHost = host == "10.0.2.2" || host == "127.0.0.1" || host == "localhost"
            require(host.isNotBlank() && (scheme == "https" || (scheme == "http" && (localHost || isTailnetIpv4(host)))))
        }

        fun isTailnetIpv4(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != 4) return false
            val octets = parts.map { it.toIntOrNull() ?: return false }
            return octets.all { it in 0..255 } && octets[0] == 100 && octets[1] in 64..127
        }
    }
}

internal fun isValidProgramEvidenceHref(href: String): Boolean =
    href.length <= 220 &&
        href.startsWith("/api/v1/evidence/program:") &&
        !href.contains("..") &&
        PROGRAM_EVIDENCE_HREF_PATTERN.matches(href)

private val PROGRAM_EVIDENCE_HREF_PATTERN = Regex("/api/v1/evidence/program:[A-Za-z0-9:_./-]+")
