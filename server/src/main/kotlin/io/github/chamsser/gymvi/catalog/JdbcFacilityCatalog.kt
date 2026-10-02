package io.github.chamsser.gymvi.catalog

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.sql.ResultSet
import java.time.OffsetDateTime

class JdbcFacilityCatalog(private val jdbc: JdbcTemplate) : FacilityCatalog {
    override fun activeDataset(): DatasetReference? =
        jdbc.query(ACTIVE_DATASET_SQL, DATASET_MAPPER).singleOrNull()

    override fun search(
        bounds: BoundingBox,
        query: String?,
        sort: FacilitySortOrder,
        limit: Int,
        offset: Int,
    ): FacilitySearchResult {
        val dataset = activeDataset() ?: throw DatasetUnavailableException()
        val facilities = when {
            query == null && sort == FacilitySortOrder.RELEVANCE -> jdbc.query(
                SEARCH_ALPHABETICAL_SQL,
                FACILITY_MAPPER,
                dataset.version,
                bounds.minLongitude,
                bounds.minLatitude,
                bounds.maxLongitude,
                bounds.maxLatitude,
                limit,
                offset,
            )

            query == null -> jdbc.query(
                SEARCH_DISTANCE_SQL,
                FACILITY_MAPPER,
                dataset.version,
                bounds.minLongitude,
                bounds.minLatitude,
                bounds.maxLongitude,
                bounds.maxLatitude,
                bounds.centerLongitude,
                bounds.centerLatitude,
                limit,
                offset,
            )

            sort == FacilitySortOrder.RELEVANCE -> jdbc.query(
                SEARCH_RELEVANCE_SQL,
                FACILITY_MAPPER,
                query,
                "$query%",
                "%$query%",
                dataset.version,
                bounds.minLongitude,
                bounds.minLatitude,
                bounds.maxLongitude,
                bounds.maxLatitude,
                limit,
                offset,
            )

            else -> jdbc.query(
                SEARCH_QUERY_DISTANCE_SQL,
                FACILITY_MAPPER,
                query,
                "%$query%",
                dataset.version,
                bounds.minLongitude,
                bounds.minLatitude,
                bounds.maxLongitude,
                bounds.maxLatitude,
                bounds.centerLongitude,
                bounds.centerLatitude,
                limit,
                offset,
            )
        }
        val totalCount = if (query == null) {
            jdbc.queryForObject(
                COUNT_ALL_SQL,
                Int::class.java,
                dataset.version,
                bounds.minLongitude,
                bounds.minLatitude,
                bounds.maxLongitude,
                bounds.maxLatitude,
            ) ?: facilities.size
        } else {
            jdbc.queryForObject(
                COUNT_QUERY_SQL,
                Int::class.java,
                dataset.version,
                bounds.minLongitude,
                bounds.minLatitude,
                bounds.maxLongitude,
                bounds.maxLatitude,
                "%$query%",
                "%$query%",
                "%$query%",
            ) ?: facilities.size
        }
        val nextOffset = offset + facilities.size
        val nextCursor = nextOffset
            .takeIf { facilities.isNotEmpty() && it < totalCount }
            ?.let(::encodeFacilityCursor)
        return FacilitySearchResult(dataset, facilities, totalCount, nextCursor)
    }

    override fun find(facilityId: String): FacilityDetailResult? {
        val dataset = activeDataset() ?: throw DatasetUnavailableException()
        val facility = jdbc.query(FIND_SQL, FACILITY_MAPPER, dataset.version, facilityId).singleOrNull()
            ?: return null
        return FacilityDetailResult(dataset, facility)
    }

    companion object {
        private val DATASET_MAPPER = RowMapper { resultSet: ResultSet, _: Int ->
            DatasetReference(
                version = resultSet.getString("dataset_version"),
                asOf = resultSet.getObject("as_of", OffsetDateTime::class.java).toInstant(),
            )
        }

        private val FACILITY_MAPPER = RowMapper { resultSet: ResultSet, _: Int ->
            FacilityRecord(
                facilityId = resultSet.getString("facility_id"),
                name = resultSet.getString("name"),
                facilityTypeCode = resultSet.getString("facility_type_code"),
                facilityTypeName = resultSet.getString("facility_type_name"),
                facilityClassCode = resultSet.getString("facility_class_code"),
                facilityClassName = resultSet.getString("facility_class_name"),
                roadAddress = resultSet.getString("road_address"),
                lotAddress = resultSet.getString("lot_address"),
                sidoName = resultSet.getString("sido_name"),
                sigunguName = resultSet.getString("sigungu_name"),
                phone = resultSet.getString("phone")?.trim()?.takeIf(::isUsablePhone),
                description = resultSet.getString("description"),
                grossFloorAreaSquareMeters = (
                    resultSet.getObject("gross_floor_area_square_meters") as? Number
                )?.toDouble(),
                longitude = resultSet.getDouble("longitude"),
                latitude = resultSet.getDouble("latitude"),
                operationState = resultSet.getString("operation_state"),
                operationReason = resultSet.getString("operation_reason"),
                sourceRecordId = resultSet.getString("source_record_id"),
                sourceDatasetId = resultSet.getString("source_dataset_id"),
                catalogUrl = resultSet.getString("catalog_url"),
            )
        }

        private const val ACTIVE_DATASET_SQL = """
            SELECT id AS dataset_version, as_of
            FROM dataset_version
            WHERE status = 'ACTIVE'
        """

        private const val FACILITY_COLUMNS = """
            SELECT f.facility_id,
                   f.name,
                   f.facility_type_code,
                   f.facility_type_name,
                   f.facility_class_code,
                   f.facility_class_name,
                   f.road_address,
                   f.lot_address,
                   f.sido_name,
                   f.sigungu_name,
                   f.phone,
                   f.description,
                   f.gross_floor_area_square_meters,
                   ST_X(f.location) AS longitude,
                   ST_Y(f.location) AS latitude,
                   f.operation_state,
                   f.operation_reason,
                   sr.id AS source_record_id,
                   sr.source_dataset_id,
                   sr.catalog_url
            FROM facility f
            JOIN source_record sr ON sr.id = f.source_record_id
        """

        private const val SEARCH_ALPHABETICAL_SQL = FACILITY_COLUMNS + """
            WHERE f.dataset_version_id = ?
              AND f.location && ST_MakeEnvelope(?, ?, ?, ?, 4326)
            ORDER BY f.search_name COLLATE "C" ASC, f.facility_id ASC
            LIMIT ? OFFSET ?
        """

        private const val SEARCH_DISTANCE_SQL = FACILITY_COLUMNS + """
            WHERE f.dataset_version_id = ?
              AND f.location && ST_MakeEnvelope(?, ?, ?, ?, 4326)
            ORDER BY ST_DistanceSphere(
                         f.location,
                         ST_SetSRID(ST_MakePoint(?, ?), 4326)
                     ) ASC,
                     f.search_name COLLATE "C" ASC,
                     f.facility_id ASC
            LIMIT ? OFFSET ?
        """

        private const val SEARCH_RELEVANCE_SQL = FACILITY_COLUMNS + """
            CROSS JOIN (
                SELECT ?::text AS exact_query,
                       ?::text AS prefix_query,
                       ?::text AS contains_query
            ) q
            WHERE f.dataset_version_id = ?
              AND f.location && ST_MakeEnvelope(?, ?, ?, ?, 4326)
              AND (
                    f.search_name LIKE q.contains_query
                 OR lower(coalesce(f.facility_type_name, '')) LIKE q.contains_query
                 OR lower(coalesce(f.description, '')) LIKE q.contains_query
              )
            ORDER BY LEAST(
                         CASE
                             WHEN f.search_name = q.exact_query THEN 0
                             WHEN f.search_name LIKE q.prefix_query THEN 1
                             WHEN f.search_name LIKE q.contains_query THEN 2
                             ELSE 3
                         END,
                         CASE
                             WHEN lower(coalesce(f.facility_type_name, '')) = q.exact_query THEN 0
                             WHEN lower(coalesce(f.facility_type_name, '')) LIKE q.prefix_query THEN 1
                             WHEN lower(coalesce(f.facility_type_name, '')) LIKE q.contains_query THEN 2
                             ELSE 3
                         END
                     ) ASC,
                     CASE
                         WHEN lower(coalesce(f.description, '')) = q.exact_query THEN 0
                         WHEN lower(coalesce(f.description, '')) LIKE q.prefix_query THEN 1
                         WHEN lower(coalesce(f.description, '')) LIKE q.contains_query THEN 2
                         ELSE 3
                     END ASC,
                     f.search_name COLLATE "C" ASC,
                     f.facility_id ASC
            LIMIT ? OFFSET ?
        """

        private const val SEARCH_QUERY_DISTANCE_SQL = FACILITY_COLUMNS + """
            CROSS JOIN (
                SELECT ?::text AS exact_query,
                       ?::text AS contains_query
            ) q
            WHERE f.dataset_version_id = ?
              AND f.location && ST_MakeEnvelope(?, ?, ?, ?, 4326)
              AND (
                    f.search_name LIKE q.contains_query
                 OR lower(coalesce(f.facility_type_name, '')) LIKE q.contains_query
                 OR lower(coalesce(f.description, '')) LIKE q.contains_query
              )
            ORDER BY ST_DistanceSphere(
                         f.location,
                         ST_SetSRID(ST_MakePoint(?, ?), 4326)
                     ) ASC,
                     f.search_name COLLATE "C" ASC,
                     f.facility_id ASC
            LIMIT ? OFFSET ?
        """

        private const val FIND_SQL = FACILITY_COLUMNS + """
            WHERE f.dataset_version_id = ?
              AND f.facility_id = ?
        """

        private const val COUNT_ALL_SQL = """
            SELECT COUNT(*)
            FROM facility f
            WHERE f.dataset_version_id = ?
              AND f.location && ST_MakeEnvelope(?, ?, ?, ?, 4326)
        """

        private const val COUNT_QUERY_SQL = """
            SELECT COUNT(*)
            FROM facility f
            WHERE f.dataset_version_id = ?
              AND f.location && ST_MakeEnvelope(?, ?, ?, ?, 4326)
              AND (
                    f.search_name LIKE ?
                 OR lower(coalesce(f.facility_type_name, '')) LIKE ?
                 OR lower(coalesce(f.description, '')) LIKE ?
              )
        """
    }
}

private fun isUsablePhone(value: String): Boolean =
    value.length in 3..40 && Regex("^[0-9+()\\-\\s]+$").matches(value)

private val BoundingBox.centerLongitude: Double
    get() = (minLongitude + maxLongitude) / 2.0

private val BoundingBox.centerLatitude: Double
    get() = (minLatitude + maxLatitude) / 2.0
