package io.github.chamsser.gymvi.usage

import io.github.chamsser.gymvi.catalog.OperatorSourceResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class OperatorTimeResponse(
    val startTime: String,
    val endTime: String,
    val days: String?,
    val operatorProgramName: String,
    val validFrom: LocalDate,
    val validTo: LocalDate,
    val sources: List<OperatorSourceResponse>,
    val checkedAt: OffsetDateTime,
)

data class OperatorProgramTimeCatalogEntry(
    val usageOptionId: String,
    val facilityId: String,
    val programName: String,
    val response: OperatorTimeResponse,
)

@Component
class OperatorProgramTimeCatalog(
    private val byUsageOptionId: Map<String, OperatorProgramTimeCatalogEntry>,
) {
    @Autowired
    constructor() : this(loadResource(RESOURCE))

    fun find(
        usageOptionId: String,
        facilityId: String,
        programName: String,
        beginDate: LocalDate?,
        endDate: LocalDate?,
    ): OperatorTimeResponse? {
        val entry = byUsageOptionId[usageOptionId] ?: return null
        if (entry.facilityId != facilityId || entry.programName != programName) return null
        if (beginDate == null || endDate == null) return null
        if (beginDate < entry.response.validFrom || beginDate > entry.response.validTo) return null
        if (endDate < entry.response.validFrom || endDate > entry.response.validTo) return null
        return entry.response
    }

    fun find(option: UsageOptionRecord): OperatorTimeResponse? = find(
        option.usageOptionId,
        option.facilityId,
        option.programName,
        option.beginDate,
        option.endDate,
    )

    companion object {
        private const val RESOURCE = "curated/operator-program-times.json"
        private val TIME_PATTERN = Regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$")

        val EMPTY = OperatorProgramTimeCatalog(emptyMap())

        fun loadResource(resource: String): Map<String, OperatorProgramTimeCatalogEntry> {
            val stream = OperatorProgramTimeCatalog::class.java.classLoader.getResourceAsStream(resource)
                ?: error("Operator program times resource is missing: $resource")
            return stream.use { parse(ObjectMapper().readTree(it)) }
        }

        fun parse(root: JsonNode): Map<String, OperatorProgramTimeCatalogEntry> {
            require(root.path("schema_version").stringValue() == "1.0.0") {
                "Unsupported operator program times schema."
            }
            val entries = root.path("entries")
            require(entries.isArray) { "Operator program times entries must be an array." }

            val result = LinkedHashMap<String, OperatorProgramTimeCatalogEntry>()
            entries.values().forEachIndexed { index, entry ->
                val label = "operator program times entries[$index]"
                val usageOptionId = entry.text("usage_option_id", label)
                val facilityId = entry.text("facility_id", label)
                val programName = entry.text("program_name", label)
                val operatorProgramName = entry.text("operator_program_name", label)
                val validFrom = entry.isoDate("valid_from", label)
                val validTo = entry.isoDate("valid_to", label)
                require(validFrom <= validTo) { "$label valid_from must not be after valid_to." }

                val startTime = entry.text("start_time", label)
                val endTime = entry.text("end_time", label)
                val start = entry.clockTime("start_time", startTime, label)
                val end = entry.clockTime("end_time", endTime, label)
                require(start < end) { "$label start_time must be before end_time." }

                val daysNode = entry.path("days")
                val days = when {
                    daysNode.isMissingNode || daysNode.isNull -> null
                    daysNode.isString && daysNode.stringValue().isNotBlank() -> daysNode.stringValue()
                    else -> throw IllegalArgumentException("$label days must be absent, null or a non-blank string.")
                }

                val sourcesNode = entry.path("sources")
                require(sourcesNode.isArray) { "$label sources must be an array." }
                val sources = sourcesNode.values().mapIndexed { sourceIndex, source ->
                    val sourceLabel = "$label sources[$sourceIndex]"
                    val name = source.text("name", sourceLabel)
                    val url = source.text("url", sourceLabel)
                    val uri = try {
                        URI(url)
                    } catch (exception: Exception) {
                        throw IllegalArgumentException("$sourceLabel url must be an HTTPS URL.", exception)
                    }
                    require(url.startsWith("https://") && uri.scheme == "https" && !uri.host.isNullOrBlank()) {
                        "$sourceLabel url must be an HTTPS URL."
                    }
                    OperatorSourceResponse(name, url)
                }.also { require(it.isNotEmpty()) { "$label needs at least one source." } }

                val checkedAtValue = entry.text("checked_at", label)
                val checkedAt = try {
                    OffsetDateTime.parse(checkedAtValue)
                } catch (exception: DateTimeParseException) {
                    throw IllegalArgumentException("$label checked_at must be an offset date-time.", exception)
                }
                val catalogEntry = OperatorProgramTimeCatalogEntry(
                    usageOptionId,
                    facilityId,
                    programName,
                    OperatorTimeResponse(
                        startTime,
                        endTime,
                        days,
                        operatorProgramName,
                        validFrom,
                        validTo,
                        sources,
                        checkedAt,
                    ),
                )
                require(!result.containsKey(usageOptionId)) {
                    "Duplicate operator program time for usage option $usageOptionId."
                }
                result[usageOptionId] = catalogEntry
            }
            return result
        }

        private fun JsonNode.text(name: String, label: String): String {
            val node = path(name)
            require(node.isString && node.stringValue().isNotBlank()) {
                "$label $name must be a non-blank string."
            }
            return node.stringValue()
        }

        private fun JsonNode.isoDate(name: String, label: String): LocalDate {
            val value = text(name, label)
            return try {
                LocalDate.parse(value)
            } catch (exception: DateTimeParseException) {
                throw IllegalArgumentException("$label $name must be an ISO date.", exception)
            }
        }

        private fun JsonNode.clockTime(name: String, value: String, label: String): LocalTime {
            require(TIME_PATTERN.matches(value)) {
                "$label $name must match HH:MM in 24-hour time."
            }
            return LocalTime.parse(value)
        }
    }
}
