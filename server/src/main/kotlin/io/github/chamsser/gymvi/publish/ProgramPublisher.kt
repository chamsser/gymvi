package io.github.chamsser.gymvi.publish

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Date
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class ProgramPublishFiles(
    val metadata: Path,
    val normalized: Path,
    val qaReport: Path,
    val joins: Path,
    val joinReport: Path,
)

data class ProgramPublishReceipt(
    val datasetVersion: String,
    val facilityDatasetVersion: String,
    val programCount: Int,
    val facilityCount: Int,
    val activeAsOf: OffsetDateTime,
)

/**
 * Publishes one program snapshot joined to the ACTIVE Facility version.
 *
 * Only EXACT/REVIEWED joins whose period has not ended by the join analysis date become
 * usage options. Application and operation states stay UNKNOWN: the source has no
 * current-state evidence.
 */
class ProgramPublisher(
    dataSourceTransactionManager: DataSourceTransactionManager,
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper = ObjectMapper(),
) {
    private val transaction = TransactionTemplate(dataSourceTransactionManager)

    fun publish(files: ProgramPublishFiles): ProgramPublishReceipt {
        listOf(
            files.metadata to "program snapshot metadata",
            files.normalized to "normalized program",
            files.qaReport to "program QA report",
            files.joins to "program join",
            files.joinReport to "program join report",
        ).forEach { (path, label) -> requireReadable(path, label) }

        val metadata = mapper.readTree(files.metadata)
        val qa = mapper.readTree(files.qaReport)
        val joinReport = mapper.readTree(files.joinReport)

        val datasetVersion = metadata.requiredText("/snapshot_id")
        require(qa.requiredText("/snapshot_id") == datasetVersion) {
            "QA report snapshot_id does not match metadata."
        }
        require(qa.requiredText("/status") == "PASS") { "Only a PASS QA report can be published." }
        require(qa.requiredNode("/checks/raw_hash_verified").booleanValue()) {
            "Program QA must verify the raw hash."
        }
        require(qa.requiredNode("/checks/license_evidence_verified").booleanValue()) {
            "Program QA must carry license evidence."
        }
        require(joinReport.requiredText("/program_dataset_version") == datasetVersion) {
            "Join report program_dataset_version does not match metadata."
        }
        val license = metadata.requiredNode("/source/license")
        listOf("/name", "/url", "/attribution").forEach { license.requiredText(it) }

        val facilityDatasetVersion = joinReport.requiredText("/facility_dataset_version")
        val analysisDate = LocalDate.parse(joinReport.requiredText("/as_of_date"))
        val expectedPrograms = qa.requiredNode("/counts/normalized_rows").asInt()
        require(joinReport.requiredNode("/counts/programs").asInt() == expectedPrograms) {
            "Join report program count does not match QA normalized_rows."
        }

        val publishableJoins = HashMap<String, ProgramJoin>()
        var joinRows = 0
        eachJsonLine(files.joins) { row ->
            joinRows += 1
            val state = row.requiredText("/state")
            if (state in PUBLISHABLE_JOIN_STATES) {
                val programId = row.requiredText("/program_id")
                val join = ProgramJoin(
                    facilityId = row.requiredText("/facility_id"),
                    state = state,
                    reasonCodes = row.requiredNode("/reason_codes").values().map { it.stringValue() },
                )
                require(publishableJoins.put(programId, join) == null) { "Duplicate join for $programId." }
            }
        }
        require(joinRows == expectedPrograms) { "Join rows must cover every normalized program." }

        val sourceModifiedDate = LocalDate.parse(metadata.requiredText("/source/source_modified_date"))
        val asOf = sourceModifiedDate.atStartOfDay(SEOUL).toOffsetDateTime()
        val rawSha256 = metadata.requiredText("/response/sha256")
        require(SHA256.matches(rawSha256)) { "Snapshot response SHA-256 is invalid." }
        val catalogUrl = metadata.requiredText("/source/catalog_url")
        val sourceUrl = license.requiredText("/url")

        return transaction.execute {
            jdbc.execute("SELECT pg_advisory_xact_lock($PUBLISH_LOCK_KEY)")
            val activeFacilityVersion = jdbc.query(
                "SELECT id FROM dataset_version WHERE status = 'ACTIVE'",
            ) { resultSet, _ -> resultSet.getString("id") }.singleOrNull()
            require(activeFacilityVersion == facilityDatasetVersion) {
                "Programs were joined to $facilityDatasetVersion, but the ACTIVE Facility version is $activeFacilityVersion."
            }

            jdbc.update(
                """
                INSERT INTO enrichment_dataset_version
                    (id, dataset_kind, source_dataset_id, schema_version, raw_sha256, as_of,
                     status, license_evidence)
                VALUES (?, 'PROGRAM', ?, ?, ?, ?, 'STAGED', ?::jsonb)
                """.trimIndent(),
                datasetVersion,
                metadata.requiredText("/source/source_dataset_id"),
                metadata.requiredText("/collector/normalization_schema_version"),
                rawSha256,
                asOf,
                license.toString(),
            )

            val sourceBatch = ArrayList<Array<Any?>>(BATCH_SIZE)
            val programBatch = ArrayList<Array<Any?>>(BATCH_SIZE)
            val facilityIds = HashSet<String>()
            var published = 0
            fun flush() {
                if (sourceBatch.isEmpty()) return
                jdbc.batchUpdate(SOURCE_RECORD_SQL, sourceBatch)
                jdbc.batchUpdate(FACILITY_PROGRAM_SQL, programBatch)
                sourceBatch.clear()
                programBatch.clear()
            }

            eachJsonLine(files.normalized) { row ->
                require(row.requiredText("/dataset_version") == datasetVersion) {
                    "Every program dataset_version must match metadata."
                }
                val programId = row.requiredText("/program_id")
                val join = publishableJoins[programId] ?: return@eachJsonLine
                val endDate = row.optionalDate("/end_date")
                if (endDate != null && endDate.isBefore(analysisDate)) return@eachJsonLine
                require(row.requiredText("/states/program_application/value") == "UNKNOWN") {
                    "Program application state must stay UNKNOWN without current evidence."
                }

                val sourceRecordId = "$datasetVersion:$programId"
                sourceBatch += arrayOf(
                    sourceRecordId,
                    datasetVersion,
                    programId,
                    sourceUrl,
                    catalogUrl,
                    row.requiredNode("/raw").toString(),
                    asOf,
                )
                programBatch += arrayOf(
                    programId,
                    datasetVersion,
                    facilityDatasetVersion,
                    join.facilityId,
                    sourceRecordId,
                    join.state,
                    join.reasonCodes.toTypedArray(),
                    row.optionalText("/program_type_name"),
                    row.requiredText("/program_name"),
                    row.optionalText("/target_name"),
                    row.optionalDate("/begin_date")?.let(Date::valueOf),
                    endDate?.let(Date::valueOf),
                    row.requiredNode("/weekdays").values().map { it.stringValue() }.toTypedArray(),
                    row.optionalText("/time_value"),
                    row.optionalInt("/recruitment_count"),
                    row.optionalInt("/price_won"),
                    row.optionalText("/price_type_name"),
                    row.optionalText("/homepage_url"),
                    row.requiredText("/states/program_application/reason"),
                )
                facilityIds += join.facilityId
                published += 1
                if (sourceBatch.size >= BATCH_SIZE) flush()
            }
            flush()
            require(published > 0) { "No current EXACT or REVIEWED program can be published." }

            val storedCount = jdbc.queryForObject(
                "SELECT count(*) FROM facility_program WHERE enrichment_dataset_version_id = ?",
                Int::class.java,
                datasetVersion,
            )
            check(storedCount == published) { "Published program count does not match accepted rows." }

            jdbc.update(
                """
                UPDATE enrichment_dataset_version SET status = 'SUPERSEDED'
                WHERE dataset_kind = 'PROGRAM' AND status = 'ACTIVE'
                """.trimIndent(),
            )
            val activated = jdbc.update(
                """
                UPDATE enrichment_dataset_version
                SET status = 'ACTIVE', activated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'STAGED'
                """.trimIndent(),
                datasetVersion,
            )
            check(activated == 1) { "Staged program dataset could not be activated." }
            ProgramPublishReceipt(datasetVersion, facilityDatasetVersion, published, facilityIds.size, asOf)
        }
    }

    private fun eachJsonLine(path: Path, action: (JsonNode) -> Unit) {
        Files.newBufferedReader(path).useLines { lines ->
            lines.filter(String::isNotBlank).forEach { action(mapper.readTree(it)) }
        }
    }

    private fun requireReadable(path: Path, label: String) {
        require(Files.isRegularFile(path) && Files.isReadable(path)) { "$label file is not readable: $path" }
    }

    private data class ProgramJoin(
        val facilityId: String,
        val state: String,
        val reasonCodes: List<String>,
    )

    companion object {
        // Shares the Facility publish lock so the ACTIVE Facility check cannot race a Facility publish.
        private const val PUBLISH_LOCK_KEY = 1187177341
        private const val BATCH_SIZE = 1000
        private val PUBLISHABLE_JOIN_STATES = setOf("EXACT", "REVIEWED")
        private val SHA256 = Regex("^[0-9a-f]{64}$")
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        private val SOURCE_RECORD_SQL = """
            INSERT INTO enrichment_source_record
                (id, enrichment_dataset_version_id, provider_record_id, source_url, catalog_url,
                 raw_record, as_of)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
        """.trimIndent()

        private val FACILITY_PROGRAM_SQL = """
            INSERT INTO facility_program
                (program_id, enrichment_dataset_version_id, facility_dataset_version_id, facility_id,
                 source_record_id, join_state, join_reason_codes, program_type_name, program_name,
                 target_name, begin_date, end_date, weekdays, source_time_value, recruitment_count,
                 price_won, price_type_name, homepage_url, application_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
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

private fun JsonNode.optionalDate(pointer: String): LocalDate? = optionalText(pointer)?.let(LocalDate::parse)

private fun JsonNode.optionalInt(pointer: String): Int? {
    val node = at(pointer)
    if (node.isMissingNode || node.isNull) return null
    require(node.isIntegralNumber && node.canConvertToInt()) { "Optional JSON integer has the wrong type: $pointer" }
    return node.intValue().also { require(it >= 0) { "Optional JSON integer is negative: $pointer" } }
}
