package io.github.chamsser.gymvi.publish

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class FacilityPublishFiles(
    val metadata: Path,
    val normalized: Path,
    val qaReport: Path,
)

data class FacilityPublishReceipt(
    val datasetVersion: String,
    val facilityCount: Int,
    val activeAsOf: Instant,
)

class FacilityPublisher(
    dataSourceTransactionManager: DataSourceTransactionManager,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper = ObjectMapper(),
) {
    private val transaction = TransactionTemplate(dataSourceTransactionManager)

    fun publish(files: FacilityPublishFiles): FacilityPublishReceipt {
        requireReadable(files.metadata, "snapshot metadata")
        requireReadable(files.normalized, "normalized Facility")
        requireReadable(files.qaReport, "QA report")

        val metadata = mapper.readTree(files.metadata)
        val qa = mapper.readTree(files.qaReport)
        val rows = Files.newBufferedReader(files.normalized).useLines { lines ->
            lines.filter(String::isNotBlank).map(mapper::readTree).toList()
        }

        val datasetVersion = metadata.requiredText("/snapshot_id")
        val qaDatasetVersion = qa.requiredText("/snapshot_id")
        val requestScope = metadata.optionalText("/request/scope") ?: "sigungu"
        require(datasetVersion == qaDatasetVersion) { "QA report snapshot_id does not match metadata." }
        require(qa.requiredText("/status") == "PASS") { "Only a PASS QA report can be published." }
        require(rows.isNotEmpty()) { "At least one normalized Facility is required." }
        require(rows.all { it.requiredText("/dataset_version") == datasetVersion }) {
            "Every Facility dataset_version must match metadata."
        }
        if (requestScope == "nationwide") {
            require(metadata.requiredNode("/response/missing_pages").isEmpty) {
                "A nationwide snapshot cannot have missing pages."
            }
            val sourceRows = metadata.requiredNode("/response/row_count").asInt()
            val qaInputRows = qa.requiredNode("/counts/input_rows").asInt()
            val qaNormalizedRows = qa.requiredNode("/counts/normalized_rows").asInt()
            val qaRejectedRows = qa.requiredNode("/counts/rejected_rows").asInt()
            require(sourceRows == qaInputRows) {
                "Nationwide source row_count must match QA input_rows."
            }
            require(qaNormalizedRows == rows.size) {
                "Nationwide QA normalized_rows must match normalized input."
            }
            require(qaInputRows == qaNormalizedRows + qaRejectedRows) {
                "Nationwide QA must account for every source row."
            }
        } else {
            val requestedSigunguCode = metadata.requiredText("/request/parameters/cpbCd")
            require(rows.all { it.requiredText("/administrative_area/sigungu_code") == requestedSigunguCode }) {
                "Every Facility must belong to the sigungu requested by the snapshot."
            }
        }

        val asOf = Instant.parse(metadata.requiredText("/request/collected_at"))
        val sourceDatasetId = metadata.requiredText("/source/source_dataset_id")
        val rawSha256 = metadata.requiredText("/response/sha256")
        require(SHA256.matches(rawSha256)) { "Snapshot response SHA-256 is invalid." }

        transaction.executeWithoutResult {
            jdbc.execute("SELECT pg_advisory_xact_lock($PUBLISH_LOCK_KEY)")
            jdbc.update(
                """
                INSERT INTO dataset_version
                    (id, source_dataset_id, schema_version, raw_sha256, as_of, status)
                VALUES (?, ?, ?, ?, ?, 'STAGED')
                """.trimIndent(),
                datasetVersion,
                sourceDatasetId,
                metadata.requiredText("/collector/normalization_schema_version"),
                rawSha256,
                asOf.jdbcTimestamp(),
            )

            rows.forEach { publishFacility(it, metadata, datasetVersion, asOf) }

            val publishedCount = jdbc.queryForObject(
                "SELECT count(*) FROM facility WHERE dataset_version_id = ?",
                Int::class.java,
                datasetVersion,
            )
            check(publishedCount == rows.size) { "Published Facility count does not match normalized input." }

            jdbc.update("UPDATE dataset_version SET status = 'SUPERSEDED' WHERE status = 'ACTIVE'")
            val activated = jdbc.update(
                """
                UPDATE dataset_version
                SET status = 'ACTIVE', activated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'STAGED'
                """.trimIndent(),
                datasetVersion,
            )
            check(activated == 1) { "Staged dataset could not be activated." }
        }

        return FacilityPublishReceipt(datasetVersion, rows.size, asOf)
    }

    private fun publishFacility(
        row: JsonNode,
        metadata: JsonNode,
        datasetVersion: String,
        fallbackAsOf: Instant,
    ) {
        val facilityId = row.requiredText("/facility_id")
        val sourceRecordId = row.requiredText("/source_record_id")
        val asOf = Instant.parse(row.optionalText("/source/as_of") ?: fallbackAsOf.toString())
        val sourceDatasetId = row.requiredText("/source/source_dataset_id")

        jdbc.update(
            """
            INSERT INTO source_record
                (id, dataset_version_id, source_dataset_id, provider_namespace,
                 provider_record_id, source_url, catalog_url, raw_record, as_of)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
            """.trimIndent(),
            sourceRecordId,
            datasetVersion,
            sourceDatasetId,
            row.requiredText("/source/provider_namespace"),
            row.requiredText("/source/provider_record_id"),
            metadata.requiredText("/source/acquisition_url"),
            metadata.requiredText("/source/catalog_url"),
            row.requiredNode("/raw").toString(),
            asOf.jdbcTimestamp(),
        )

        jdbc.update(
            """
            INSERT INTO facility
                (facility_id, dataset_version_id, source_record_id, name, search_name,
                 facility_class_code, facility_class_name, facility_type_code, facility_type_name,
                 road_address, lot_address, phone, description, gross_floor_area_square_meters,
                 sido_code, sigungu_code, sido_name, sigungu_name, location,
                 operation_state, operation_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?)
            """.trimIndent(),
            facilityId,
            datasetVersion,
            sourceRecordId,
            row.requiredText("/name/value"),
            row.requiredText("/search_name"),
            row.optionalText("/facility_class/code"),
            row.optionalText("/facility_class/name"),
            row.optionalText("/facility_type/industry_code"),
            row.optionalText("/facility_type/industry_name"),
            row.optionalText("/address/road/value"),
            row.optionalText("/address/lot/value"),
            row.optionalText("/contact/phone/value"),
            row.optionalText("/description/value"),
            row.optionalDouble("/gross_floor_area_square_meters/value"),
            row.optionalText("/administrative_area/sido_code"),
            row.optionalText("/administrative_area/sigungu_code"),
            row.optionalText("/administrative_area/sido_name"),
            row.optionalText("/administrative_area/sigungu_name"),
            row.requiredDouble("/location/longitude"),
            row.requiredDouble("/location/latitude"),
            row.requiredText("/states/facility_operation/value"),
            row.requiredText("/states/facility_operation/reason"),
        )

        publishEvidence(row, datasetVersion, facilityId, sourceRecordId, "name", "/name")
        if (row.optionalText("/address/road/value") != null) {
            publishEvidence(row, datasetVersion, facilityId, sourceRecordId, "road_address", "/address/road")
        }
        if (row.optionalText("/address/lot/value") != null) {
            publishEvidence(row, datasetVersion, facilityId, sourceRecordId, "lot_address", "/address/lot")
        }
        if (row.optionalText("/contact/phone/value") != null) {
            publishEvidence(row, datasetVersion, facilityId, sourceRecordId, "phone", "/contact/phone")
        }
        if (row.optionalDouble("/gross_floor_area_square_meters/value") != null) {
            publishEvidence(
                row,
                datasetVersion,
                facilityId,
                sourceRecordId,
                "gross_floor_area_square_meters",
                "/gross_floor_area_square_meters",
            )
        }
    }

    private fun publishEvidence(
        row: JsonNode,
        datasetVersion: String,
        facilityId: String,
        sourceRecordId: String,
        fieldName: String,
        pointer: String,
    ) {
        val evidence = row.requiredNode(pointer)
        jdbc.update(
            """
            INSERT INTO evidence_value
                (id, dataset_version_id, facility_id, source_record_id, field_name,
                 source_field, value_json, observed_at, confidence)
            VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """.trimIndent(),
            "$sourceRecordId:$fieldName",
            datasetVersion,
            facilityId,
            sourceRecordId,
            fieldName,
            evidence.requiredText("/source_field"),
            evidence.requiredNode("/value").toString(),
            Instant.parse(evidence.requiredText("/as_of")).jdbcTimestamp(),
            evidence.requiredText("/confidence"),
        )
    }

    private fun requireReadable(path: Path, label: String) {
        require(Files.isRegularFile(path) && Files.isReadable(path)) { "$label file is not readable: $path" }
    }

    companion object {
        private const val PUBLISH_LOCK_KEY = 1187177341
        private val SHA256 = Regex("^[0-9a-f]{64}$")
    }
}

private fun JsonNode.requiredNode(pointer: String): JsonNode {
    val node = at(pointer)
    require(!node.isMissingNode && !node.isNull) { "Required JSON value is missing: $pointer" }
    return node
}

private fun JsonNode.requiredText(pointer: String): String {
    val node = requiredNode(pointer)
    require(node.isString) { "Required JSON text has the wrong type: $pointer" }
    val value = node.stringValue().trim()
    require(value.isNotEmpty()) { "Required JSON text is empty: $pointer" }
    return value
}

private fun JsonNode.optionalText(pointer: String): String? {
    val node = at(pointer)
    if (node.isMissingNode || node.isNull) return null
    require(node.isString) { "Optional JSON text has the wrong type: $pointer" }
    return node.stringValue().trim().ifEmpty { null }
}

private fun JsonNode.requiredDouble(pointer: String): Double {
    val node = requiredNode(pointer)
    require(node.isNumber) { "Required JSON number has the wrong type: $pointer" }
    return node.asDouble().also { require(it.isFinite()) { "JSON number is not finite: $pointer" } }
}

private fun JsonNode.optionalDouble(pointer: String): Double? {
    val node = at(pointer)
    if (node.isMissingNode || node.isNull) return null
    require(node.isNumber) { "Optional JSON number has the wrong type: $pointer" }
    return node.asDouble().also {
        require(it.isFinite() && it > 0 && it <= 10_000_000) {
            "Optional JSON number is outside the supported range: $pointer"
        }
    }
}

private fun Instant.jdbcTimestamp(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
