package io.github.chamsser.gymvi.meta

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime

/**
 * One ACTIVE published dataset as the version response reads it. The license columns are the
 * text the publisher stored; the source registry decides whether any of it may be shown.
 */
data class ActiveDatasetRow(
    val datasetKind: String,
    val datasetVersion: String,
    val sourceDatasetId: String,
    val rawSha256: String,
    val asOf: Instant,
    val licenseUrl: String? = null,
    val licenseAttribution: String? = null,
)

interface ActiveDatasetCatalog {
    /** All rows come from one read, so FACILITY and PROGRAM always describe the same moment. */
    fun activeDatasets(): List<ActiveDatasetRow>
}

class UnavailableActiveDatasetCatalog : ActiveDatasetCatalog {
    override fun activeDatasets(): List<ActiveDatasetRow> = emptyList()
}

class JdbcActiveDatasetCatalog(private val jdbc: JdbcTemplate) : ActiveDatasetCatalog {
    override fun activeDatasets(): List<ActiveDatasetRow> = jdbc.query(SQL, ROW_MAPPER)

    private companion object {
        // A single statement reads one snapshot. A PROGRAM version is listed only while its rows
        // still join the ACTIVE facility version, the same rule the program endpoints serve by.
        const val SQL = """
            SELECT 'FACILITY' AS dataset_kind,
                   id AS dataset_version,
                   source_dataset_id,
                   raw_sha256,
                   as_of,
                   NULL::text AS license_url,
                   NULL::text AS license_attribution
            FROM dataset_version
            WHERE status = 'ACTIVE'
            UNION ALL
            SELECT 'PROGRAM',
                   ev.id,
                   ev.source_dataset_id,
                   ev.raw_sha256,
                   ev.as_of,
                   CASE WHEN jsonb_typeof(ev.license_evidence -> 'url') = 'string'
                        THEN ev.license_evidence ->> 'url' END,
                   CASE WHEN jsonb_typeof(ev.license_evidence -> 'attribution') = 'string'
                        THEN ev.license_evidence ->> 'attribution' END
            FROM enrichment_dataset_version ev
            WHERE ev.dataset_kind = 'PROGRAM'
              AND ev.status = 'ACTIVE'
              AND EXISTS (
                  SELECT 1
                  FROM facility_program fp
                  JOIN dataset_version facility_version
                    ON facility_version.id = fp.facility_dataset_version_id
                   AND facility_version.status = 'ACTIVE'
                  WHERE fp.enrichment_dataset_version_id = ev.id
                    AND fp.join_state IN ('EXACT', 'REVIEWED')
              )
            ORDER BY dataset_kind
        """

        val ROW_MAPPER = RowMapper { resultSet: ResultSet, _: Int ->
            ActiveDatasetRow(
                datasetKind = resultSet.getString("dataset_kind"),
                datasetVersion = resultSet.getString("dataset_version"),
                sourceDatasetId = resultSet.getString("source_dataset_id"),
                rawSha256 = resultSet.getString("raw_sha256").trim(),
                asOf = resultSet.getObject("as_of", OffsetDateTime::class.java).toInstant(),
                licenseUrl = resultSet.getString("license_url"),
                licenseAttribution = resultSet.getString("license_attribution"),
            )
        }
    }
}
