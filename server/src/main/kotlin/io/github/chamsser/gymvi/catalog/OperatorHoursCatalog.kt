package io.github.chamsser.gymvi.catalog

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.OffsetDateTime

data class OperatingHoursSlotResponse(
    val days: String,
    val hours: String,
)

data class OperatorSourceResponse(
    val name: String,
    val url: String,
)

/**
 * Published hours from an operator page. They describe the operator's stated schedule and
 * never change `facility_operation`, which stays UNKNOWN without current evidence.
 */
data class OperatingHoursResponse(
    val schedule: List<OperatingHoursSlotResponse>,
    val closedDays: List<String>,
    val note: String?,
    val sources: List<OperatorSourceResponse>,
    val checkedAt: OffsetDateTime,
)

@Component
class OperatorHoursCatalog(
    private val byFacilityId: Map<String, OperatingHoursResponse>,
) {
    @Autowired
    constructor() : this(loadResource(RESOURCE))

    fun find(facilityId: String): OperatingHoursResponse? = byFacilityId[facilityId]

    companion object {
        private const val RESOURCE = "curated/operator-hours.json"

        fun loadResource(resource: String): Map<String, OperatingHoursResponse> {
            val stream = OperatorHoursCatalog::class.java.classLoader.getResourceAsStream(resource)
                ?: error("Operator hours resource is missing: $resource")
            return stream.use { parse(ObjectMapper().readTree(it)) }
        }

        fun parse(root: JsonNode): Map<String, OperatingHoursResponse> {
            require(root.path("schema_version").stringValue() == "1.0.0") {
                "Unsupported operator hours schema."
            }
            val result = LinkedHashMap<String, OperatingHoursResponse>()
            root.path("entries").values().forEach { entry ->
                val hours = OperatingHoursResponse(
                    schedule = entry.path("schedule").values().map { slot ->
                        OperatingHoursSlotResponse(slot.text("days"), slot.text("hours"))
                    }.also { require(it.isNotEmpty()) { "Operator hours need a schedule." } },
                    closedDays = entry.path("closed_days").values().map { it.stringValue().trim() },
                    note = entry.path("note").takeIf { it.isString }?.stringValue()?.trim()?.ifEmpty { null },
                    sources = entry.path("sources").values().map { source ->
                        OperatorSourceResponse(source.text("name"), source.text("url")).also {
                            require(it.url.startsWith("https://")) { "Operator source must use HTTPS." }
                        }
                    }.also { require(it.isNotEmpty()) { "Operator hours need a source." } },
                    checkedAt = OffsetDateTime.parse(entry.text("checked_at")),
                )
                val facilityIds = entry.path("facility_ids").values().map { it.stringValue().trim() }
                require(facilityIds.isNotEmpty()) { "Operator hours need facility IDs." }
                facilityIds.forEach { facilityId ->
                    require(result.put(facilityId, hours) == null) {
                        "Duplicate operator hours for $facilityId."
                    }
                }
            }
            return result
        }

        private fun JsonNode.text(name: String): String {
            val node = path(name)
            require(node.isString && node.stringValue().isNotBlank()) { "Operator hours field is missing: $name" }
            return node.stringValue().trim()
        }
    }
}
