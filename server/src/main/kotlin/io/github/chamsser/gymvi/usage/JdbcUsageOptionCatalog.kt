package io.github.chamsser.gymvi.usage

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.OffsetDateTime

class JdbcUsageOptionCatalog(private val jdbc: JdbcTemplate) : UsageOptionCatalog {
    override fun findForFacility(
        facilityDatasetVersion: String,
        facilityId: String,
    ): UsageOptionCatalogResult {
        val programDataset = jdbc.query(PROGRAM_DATASET_SQL) { resultSet, _ ->
            Triple(
                resultSet.getString("id"),
                resultSet.getObject("as_of", OffsetDateTime::class.java).toInstant(),
                resultSet.getString("attribution"),
            )
        }.singleOrNull()
        val options = programDataset?.let { dataset ->
            jdbc.query(
                OPTIONS_SQL,
                OPTION_MAPPER,
                dataset.first,
                facilityDatasetVersion,
                facilityId,
            )
        }.orEmpty()
        return UsageOptionCatalogResult(
            programDatasetVersion = programDataset?.first,
            programAsOf = programDataset?.second,
            attribution = programDataset?.third,
            options = options,
        )
    }

    companion object {
        private const val PROGRAM_DATASET_SQL = """
            SELECT id, as_of, license_evidence ->> 'attribution' AS attribution
            FROM enrichment_dataset_version
            WHERE dataset_kind = 'PROGRAM' AND status = 'ACTIVE'
        """
        private const val OPTIONS_SQL = """
            SELECT program_id,
                   facility_id,
                   program_name,
                   program_type_name,
                   target_name,
                   begin_date,
                   end_date,
                   weekdays,
                   source_time_value,
                   recruitment_count,
                   price_won,
                   price_type_name,
                   homepage_url,
                   operation_state,
                   operation_reason,
                   application_state,
                   application_reason,
                   join_state,
                   source_record_id
            FROM facility_program
            WHERE enrichment_dataset_version_id = ?
              AND facility_dataset_version_id = ?
              AND facility_id = ?
              AND join_state IN ('EXACT', 'REVIEWED')
            ORDER BY lower(program_name) COLLATE "C" ASC,
                     begin_date ASC NULLS LAST,
                     program_id ASC
        """
        private val OPTION_MAPPER = RowMapper { resultSet: ResultSet, _: Int ->
            UsageOptionRecord(
                usageOptionId = resultSet.getString("program_id"),
                facilityId = resultSet.getString("facility_id"),
                programName = resultSet.getString("program_name"),
                programTypeName = resultSet.getString("program_type_name"),
                targetName = resultSet.getString("target_name"),
                beginDate = resultSet.getDate("begin_date")?.toLocalDate(),
                endDate = resultSet.getDate("end_date")?.toLocalDate(),
                weekdays = resultSet.getArray("weekdays")?.let { sqlArray ->
                    (sqlArray.array as? Array<*>)?.mapNotNull { it?.toString() }
                }.orEmpty(),
                sourceTimeValue = resultSet.getString("source_time_value"),
                recruitmentCount = (resultSet.getObject("recruitment_count") as? Number)?.toInt(),
                sourcePriceWon = (resultSet.getObject("price_won") as? Number)?.toInt(),
                priceTypeName = resultSet.getString("price_type_name"),
                homepageUrl = resultSet.getString("homepage_url"),
                operationState = resultSet.getString("operation_state"),
                operationReasonCode = resultSet.getString("operation_reason"),
                applicationState = resultSet.getString("application_state"),
                applicationReasonCode = resultSet.getString("application_reason"),
                joinState = resultSet.getString("join_state"),
                sourceRecordId = resultSet.getString("source_record_id"),
            )
        }
    }
}
