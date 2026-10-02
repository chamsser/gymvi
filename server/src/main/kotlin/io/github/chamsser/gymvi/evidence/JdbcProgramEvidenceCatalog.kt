package io.github.chamsser.gymvi.evidence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime

class JdbcProgramEvidenceCatalog(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : ProgramEvidenceCatalog {
    override fun find(usageOptionId: String): ProgramEvidenceCatalogRecord? =
        jdbc.query(SQL, recordMapper, usageOptionId).singleOrNull()

    private val recordMapper = RowMapper { resultSet: ResultSet, _: Int ->
        val rawRecord = objectMapper.readTree(resultSet.getString("raw_record_json"))
        ProgramEvidenceCatalogRecord(
            usageOptionId = resultSet.getString("program_id"),
            facilityId = resultSet.getString("facility_id"),
            programName = resultSet.getString("program_name"),
            dataset = EvidenceDataset(
                datasetKind = "PROGRAM",
                datasetVersion = resultSet.getString("dataset_version"),
                sourceDatasetId = resultSet.getString("source_dataset_id"),
                schemaVersion = resultSet.getString("schema_version"),
                rawSha256 = resultSet.getString("raw_sha256").trim(),
                asOf = resultSet.instant("program_as_of") ?: error("Active program dataset has no as_of."),
                activatedAt = resultSet.instant("activated_at")
                    ?: error("Active program dataset has no activated_at."),
            ),
            sourceRecord = EvidenceSourceRecord(
                sourceRecordId = resultSet.getString("source_record_id"),
                providerRecordId = resultSet.getString("provider_record_id"),
                sourceUrl = resultSet.getString("source_url"),
                catalogUrl = resultSet.getString("catalog_url"),
                asOf = resultSet.instant("source_as_of") ?: error("Program source record has no as_of."),
            ),
            rawRecord = rawRecord,
            normalizedValues = linkedMapOf(
                "program_type_name" to resultSet.getString("program_type_name"),
                "program_name" to resultSet.getString("program_name"),
                "target_name" to resultSet.getString("target_name"),
                "begin_date" to resultSet.getDate("begin_date")?.toLocalDate()?.toString(),
                "end_date" to resultSet.getDate("end_date")?.toLocalDate()?.toString(),
                "weekdays" to resultSet.stringArray("weekdays"),
                "source_time_value" to resultSet.getString("source_time_value"),
                "recruitment_count" to (resultSet.getObject("recruitment_count") as? Number)?.toInt(),
                "price_won" to (resultSet.getObject("price_won") as? Number)?.toInt(),
                "price_type_name" to resultSet.getString("price_type_name"),
                "homepage_url" to resultSet.getString("homepage_url"),
            ),
            states = EvidenceStates(
                facilityOperation = EvidenceState(
                    resultSet.getString("operation_state"),
                    resultSet.getString("operation_reason"),
                ),
                programApplication = EvidenceState(
                    resultSet.getString("application_state"),
                    resultSet.getString("application_reason"),
                ),
            ),
            facilityJoin = EvidenceFacilityJoin(
                joinState = resultSet.getString("join_state"),
                joinReasonCodes = resultSet.stringArray("join_reason_codes"),
                facilityDatasetVersion = resultSet.getString("facility_dataset_version"),
                facilityJoinInputs = JOIN_INPUT_FIELDS.map { sourceField ->
                    FacilityJoinInput(sourceField, rawRecord.sourceValue(sourceField))
                },
            ),
            license = parseLicense(resultSet.getString("license_json")),
        )
    }

    private fun parseLicense(json: String?): EvidenceLicense {
        if (json == null) return EvidenceLicense(null, null, null, null, null, null)
        val node = objectMapper.readTree(json)
        return EvidenceLicense(
            name = node.optionalText("name"),
            url = node.optionalText("url"),
            attribution = node.optionalText("attribution"),
            commercialUseAllowed = node.optionalBoolean("commercial_use_allowed"),
            modificationAllowed = node.optionalBoolean("modification_allowed"),
            checkedAt = node.optionalText("checked_at"),
        )
    }

    companion object {
        private val JOIN_INPUT_FIELDS = listOf(
            "FCLTY_NM",
            "FCLTY_ADDR",
            "FCLTY_TEL_NO",
            "FCLTY_LA",
            "FCLTY_LO",
        )
        private const val SQL = """
            SELECT ev.id AS dataset_version,
                   ev.source_dataset_id,
                   ev.schema_version,
                   ev.raw_sha256,
                   ev.as_of AS program_as_of,
                   ev.activated_at,
                   ev.license_evidence::text AS license_json,
                   fp.program_id,
                   fp.facility_id,
                   fp.program_name,
                   fp.program_type_name,
                   fp.target_name,
                   fp.begin_date,
                   fp.end_date,
                   fp.weekdays,
                   fp.source_time_value,
                   fp.recruitment_count,
                   fp.price_won,
                   fp.price_type_name,
                   fp.homepage_url,
                   fp.operation_state,
                   fp.operation_reason,
                   fp.application_state,
                   fp.application_reason,
                   fp.join_state,
                   fp.join_reason_codes,
                   fp.facility_dataset_version_id AS facility_dataset_version,
                   program_source.id AS source_record_id,
                   program_source.provider_record_id,
                   program_source.source_url,
                   program_source.catalog_url,
                   program_source.as_of AS source_as_of,
                   program_source.raw_record::text AS raw_record_json
            FROM facility_program fp
            JOIN enrichment_dataset_version ev
              ON ev.id = fp.enrichment_dataset_version_id
             AND ev.dataset_kind = 'PROGRAM'
             AND ev.status = 'ACTIVE'
            JOIN enrichment_source_record program_source
              ON program_source.id = fp.source_record_id
             AND program_source.enrichment_dataset_version_id = ev.id
            JOIN dataset_version facility_version
              ON facility_version.id = fp.facility_dataset_version_id
             AND facility_version.status = 'ACTIVE'
            JOIN facility f
              ON f.dataset_version_id = facility_version.id
             AND f.facility_id = fp.facility_id
            WHERE fp.program_id = ?
              AND fp.join_state IN ('EXACT', 'REVIEWED')
        """
    }
}

private fun ResultSet.instant(column: String): Instant? =
    getObject(column, OffsetDateTime::class.java)?.toInstant()

private fun ResultSet.stringArray(column: String): List<String> =
    getArray(column)?.let { sqlArray ->
        (sqlArray.array as? Array<*>)?.mapNotNull { it?.toString() }
    }.orEmpty()

private fun JsonNode.sourceValue(field: String): String? =
    get(field)?.takeIf { it.isTextual }?.stringValue()?.takeIf(String::isNotBlank)

private fun JsonNode.optionalText(field: String): String? =
    get(field)?.takeIf { it.isTextual }?.stringValue()?.takeIf(String::isNotBlank)

private fun JsonNode.optionalBoolean(field: String): Boolean? =
    get(field)?.takeIf { it.isBoolean }?.booleanValue()
