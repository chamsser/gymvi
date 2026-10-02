package io.github.chamsser.gymvi.evidence

import tools.jackson.databind.JsonNode
import io.github.chamsser.gymvi.usage.OperatorTimeResponse
import java.time.Instant

data class EvidenceDataset(
    val datasetKind: String,
    val datasetVersion: String,
    val sourceDatasetId: String,
    val schemaVersion: String,
    val rawSha256: String,
    val asOf: Instant,
    val activatedAt: Instant,
)

data class EvidenceSourceRecord(
    val sourceRecordId: String,
    val providerRecordId: String,
    val sourceUrl: String,
    val catalogUrl: String,
    val asOf: Instant,
)

data class EvidenceState(
    val state: String,
    val reasonCode: String,
)

data class EvidenceSubject(
    val usageOptionId: String,
    val facilityId: String,
    val programName: String,
)

data class EvidenceField(
    val field: String,
    val sourceField: String,
    val sourceValue: String?,
    val normalizedValue: Any?,
    val transformation: String,
    val state: String,
    val unknownReasonCode: String?,
)

data class FacilityJoinInput(
    val sourceField: String,
    val sourceValue: String?,
)

data class EvidenceFacilityJoin(
    val joinState: String,
    val joinReasonCodes: List<String>,
    val facilityDatasetVersion: String,
    val facilityJoinInputs: List<FacilityJoinInput>,
)

data class EvidenceLicense(
    val name: String?,
    val url: String?,
    val attribution: String?,
    val commercialUseAllowed: Boolean?,
    val modificationAllowed: Boolean?,
    val checkedAt: String?,
)

data class EvidenceStates(
    val facilityOperation: EvidenceState,
    val programApplication: EvidenceState,
)

data class ProgramEvidenceCatalogRecord(
    val usageOptionId: String,
    val facilityId: String,
    val programName: String,
    val dataset: EvidenceDataset,
    val sourceRecord: EvidenceSourceRecord,
    val rawRecord: JsonNode,
    val normalizedValues: Map<String, Any?>,
    val states: EvidenceStates,
    val facilityJoin: EvidenceFacilityJoin,
    val license: EvidenceLicense,
)

data class EvidenceResponse(
    val evidenceId: String,
    val evidenceKind: String,
    val subject: EvidenceSubject,
    val dataset: EvidenceDataset,
    val sourceRecord: EvidenceSourceRecord,
    val fields: List<EvidenceField>,
    val operatorTime: OperatorTimeResponse?,
    val states: EvidenceStates,
    val facilityJoin: EvidenceFacilityJoin,
    val limitations: List<String>,
    val license: EvidenceLicense,
)
