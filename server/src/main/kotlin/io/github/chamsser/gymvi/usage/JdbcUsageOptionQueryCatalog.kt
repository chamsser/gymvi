package io.github.chamsser.gymvi.usage

import io.github.chamsser.gymvi.catalog.BoundingBox
import io.github.chamsser.gymvi.catalog.DatasetReference
import io.github.chamsser.gymvi.catalog.DatasetUnavailableException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.LocalDate
import java.time.OffsetDateTime

class JdbcUsageOptionQueryCatalog(private val jdbc: JdbcTemplate) : UsageOptionQueryCatalog {
    override fun findInArea(bounds: BoundingBox, candidateCap: Int, date: LocalDate): UsageOptionQueryResult =
        query { facility, program ->
            jdbc.query(
                "$FIELDS $JOINS WHERE $FILTER AND fp.join_state IN ('EXACT','REVIEWED') " +
                    "AND f.location && ST_MakeEnvelope(?, ?, ?, ?, 4326) " +
                    "AND (fp.end_date IS NULL OR fp.end_date >= ?) ORDER BY fp.program_id LIMIT ?",
                RECORD_MAPPER,
                program,
                facility,
                bounds.minLongitude,
                bounds.minLatitude,
                bounds.maxLongitude,
                bounds.maxLatitude,
                date,
                candidateCap + 1,
            )
        }

    override fun findByIds(ids: List<String>): UsageOptionQueryResult = query { facility, program ->
        val placeholders = ids.joinToString(",") { "?" }
        jdbc.query(
            "$FIELDS $JOINS WHERE $FILTER AND fp.program_id IN ($placeholders) ORDER BY fp.program_id",
            RECORD_MAPPER,
            *(listOf(program, facility) + ids).toTypedArray(),
        )
    }

    private fun query(fetch: (String, String) -> List<JoinedUsageOptionRecord>): UsageOptionQueryResult {
        val facility = jdbc.query(
            "SELECT id, as_of FROM dataset_version WHERE status = 'ACTIVE'",
        ) { rs, _ -> DatasetReference(rs.getString("id"), rs.getObject("as_of", OffsetDateTime::class.java).toInstant()) }
            .singleOrNull() ?: throw DatasetUnavailableException()
        val program = jdbc.query(
            "SELECT id, as_of, license_evidence ->> 'attribution' AS attribution " +
                "FROM enrichment_dataset_version WHERE dataset_kind = 'PROGRAM' AND status = 'ACTIVE'",
        ) { rs, _ -> Triple(
            rs.getString("id"),
            rs.getObject("as_of", OffsetDateTime::class.java).toInstant(),
            rs.getString("attribution"),
        ) }.singleOrNull()
        return UsageOptionQueryResult(
            facility,
            program?.first,
            program?.second,
            program?.third,
            if (program == null) emptyList() else fetch(facility.version, program.first),
        )
    }

    private companion object {
        const val FIELDS = """
            SELECT fp.program_id, fp.facility_id, fp.program_name, fp.program_type_name,
                   fp.target_name, fp.begin_date, fp.end_date, fp.weekdays, fp.source_time_value,
                   fp.recruitment_count, fp.price_won, fp.price_type_name, fp.homepage_url,
                   f.operation_state, f.operation_reason, fp.application_state, fp.application_reason,
                   fp.join_state, fp.source_record_id, f.name AS facility_name,
                   f.facility_type_name, f.road_address,
                   ST_X(f.location) AS longitude, ST_Y(f.location) AS latitude
        """
        const val JOINS = """
            FROM facility_program fp JOIN facility f
              ON (f.dataset_version_id, f.facility_id) = (fp.facility_dataset_version_id, fp.facility_id)
        """
        const val FILTER = "fp.enrichment_dataset_version_id = ? AND f.dataset_version_id = ?"

        val RECORD_MAPPER = RowMapper { rs: ResultSet, _: Int ->
            JoinedUsageOptionRecord(
                option = UsageOptionRecord(
                    usageOptionId = rs.getString("program_id"),
                    facilityId = rs.getString("facility_id"),
                    programName = rs.getString("program_name"),
                    programTypeName = rs.getString("program_type_name"),
                    targetName = rs.getString("target_name"),
                    beginDate = rs.getDate("begin_date")?.toLocalDate(),
                    endDate = rs.getDate("end_date")?.toLocalDate(),
                    weekdays = rs.getArray("weekdays")?.let { (it.array as? Array<*>)?.mapNotNull(Any?::toString) }.orEmpty(),
                    sourceTimeValue = rs.getString("source_time_value"),
                    recruitmentCount = (rs.getObject("recruitment_count") as? Number)?.toInt(),
                    sourcePriceWon = (rs.getObject("price_won") as? Number)?.toInt(),
                    priceTypeName = rs.getString("price_type_name"),
                    homepageUrl = rs.getString("homepage_url"),
                    operationState = rs.getString("operation_state"),
                    operationReasonCode = rs.getString("operation_reason"),
                    applicationState = rs.getString("application_state"),
                    applicationReasonCode = rs.getString("application_reason"),
                    joinState = rs.getString("join_state"),
                    sourceRecordId = rs.getString("source_record_id"),
                ),
                facilityName = rs.getString("facility_name"),
                facilityTypeName = rs.getString("facility_type_name"),
                roadAddress = rs.getString("road_address"),
                longitude = rs.getDouble("longitude"),
                latitude = rs.getDouble("latitude"),
            )
        }
    }
}
