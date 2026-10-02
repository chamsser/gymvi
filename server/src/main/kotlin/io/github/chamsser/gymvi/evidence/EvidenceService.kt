package io.github.chamsser.gymvi.evidence

import io.github.chamsser.gymvi.catalog.ApiValidationException
import io.github.chamsser.gymvi.catalog.GymviApiException
import io.github.chamsser.gymvi.usage.OperatorProgramTimeCatalog
import io.github.chamsser.gymvi.usage.ProgramPrice
import java.time.LocalDate
import org.springframework.stereotype.Service

@Service
class EvidenceService(
    private val catalog: ProgramEvidenceCatalog,
    private val operatorProgramTimeCatalog: OperatorProgramTimeCatalog = OperatorProgramTimeCatalog.EMPTY,
) {
    fun find(evidenceId: String): EvidenceResponse {
        val (kind, usageOptionId) = parseEvidenceId(evidenceId)
        if (kind != PROGRAM_KIND) {
            throw ApiValidationException(
                "VALIDATION_EVIDENCE_KIND",
                "지원하지 않는 근거 종류입니다.",
            )
        }
        val record = catalog.find(usageOptionId) ?: throw EvidenceNotFoundException()
        return response(evidenceId, record)
    }

    private fun parseEvidenceId(evidenceId: String): Pair<String, String> {
        if (
            evidenceId.isBlank() ||
            evidenceId.length > MAX_EVIDENCE_ID_LENGTH ||
            !EVIDENCE_ID_CHARACTERS.matches(evidenceId)
        ) {
            throw invalidEvidenceId()
        }
        val separator = evidenceId.indexOf(':')
        if (separator <= 0 || separator == evidenceId.lastIndex) {
            throw invalidEvidenceId()
        }
        return evidenceId.substring(0, separator) to evidenceId.substring(separator + 1)
    }

    private fun response(evidenceId: String, record: ProgramEvidenceCatalogRecord): EvidenceResponse {
        val price = ProgramPrice.interpret(
            (record.normalizedValues["price_won"] as? Number)?.toInt(),
            record.normalizedValues["price_type_name"] as? String,
            record.programName,
        )
        val fields = FIELD_MAPPINGS.map { mapping ->
            val sourceValue = record.rawRecord.get(mapping.sourceField)
                ?.takeIf { it.isTextual }
                ?.stringValue()
                ?.takeIf(String::isNotBlank)
            val normalizedValue = record.normalizedValues[mapping.field]
            // A normalized 0 stays as provenance, but without free evidence it is not a known price.
            val unknown = normalizedValue == null ||
                (mapping.field == "weekdays" && (normalizedValue as? Collection<*>)?.isEmpty() == true) ||
                (mapping.field == "price_won" && price.amountWon == null)
            val unknownReasonCode = when {
                !unknown -> null
                sourceValue == null -> "SOURCE_VALUE_BLANK"
                mapping.field == "weekdays" -> "NO_RECOGNIZED_WEEKDAY_TOKENS"
                mapping.field == "price_won" && normalizedValue != null -> price.unknownReasonCode
                else -> "NORMALIZED_VALUE_UNAVAILABLE"
            }
            EvidenceField(
                field = mapping.field,
                sourceField = mapping.sourceField,
                sourceValue = sourceValue,
                normalizedValue = normalizedValue,
                transformation = mapping.transformation,
                state = if (unknown) UNKNOWN_STATE else KNOWN_STATE,
                unknownReasonCode = unknownReasonCode,
            )
        }
        val limitations = buildList {
            addAll(BASE_LIMITATIONS)
            if (price.unit == null) add(PRICE_UNIT_UNKNOWN)
        }
        return EvidenceResponse(
            evidenceId = evidenceId,
            evidenceKind = PROGRAM_KIND.uppercase(),
            subject = EvidenceSubject(record.usageOptionId, record.facilityId, record.programName),
            dataset = record.dataset,
            sourceRecord = record.sourceRecord,
            fields = fields,
            operatorTime = operatorProgramTimeCatalog.find(
                record.usageOptionId,
                record.facilityId,
                record.programName,
                record.normalizedValues["begin_date"].normalizedDate(),
                record.normalizedValues["end_date"].normalizedDate(),
            ),
            states = record.states,
            facilityJoin = record.facilityJoin,
            limitations = limitations,
            license = record.license,
        )
    }

    private fun invalidEvidenceId() =
        ApiValidationException("VALIDATION_EVIDENCE_ID", "근거 ID 형식이 올바르지 않습니다.")

    private data class FieldMapping(
        val field: String,
        val sourceField: String,
        val transformation: String,
    )

    companion object {
        private const val MAX_EVIDENCE_ID_LENGTH = 200
        private const val PROGRAM_KIND = "program"
        private const val KNOWN_STATE = "KNOWN"
        private const val UNKNOWN_STATE = "UNKNOWN"
        private const val PRICE_UNIT_UNKNOWN = "PRICE_UNIT_UNKNOWN"
        private val EVIDENCE_ID_CHARACTERS = Regex("^[A-Za-z0-9:_\\-.]+$")
        private val BASE_LIMITATIONS = listOf(
            "RECRUITMENT_COUNT_IS_NOT_REMAINING_CAPACITY",
            "SOURCE_TIME_NOT_INFERRED_FROM_PROGRAM_NAME",
            "OPERATION_AND_APPLICATION_STATES_NEED_CURRENT_EVIDENCE",
        )
        private val FIELD_MAPPINGS = listOf(
            FieldMapping("program_type_name", "PROGRM_TY_NM", "TRIM_BLANK_TO_NULL"),
            FieldMapping("program_name", "PROGRM_NM", "TRIM"),
            FieldMapping("target_name", "PROGRM_TRGET_NM", "TRIM_BLANK_TO_NULL"),
            FieldMapping("begin_date", "PROGRM_BEGIN_DE", "DATE_YYYYMMDD_TO_ISO"),
            FieldMapping("end_date", "PROGRM_END_DE", "DATE_YYYYMMDD_TO_ISO"),
            FieldMapping("weekdays", "PROGRM_ESTBL_WKDAY_NM", "WEEKDAY_TOKENS"),
            FieldMapping("source_time_value", "PROGRM_ESTBL_TIZN_VALUE", "TRIM_BLANK_TO_NULL"),
            FieldMapping("recruitment_count", "PROGRM_RCRIT_NMPR_CO", "NONNEGATIVE_INTEGER"),
            FieldMapping("price_won", "PROGRM_PRC", "NONNEGATIVE_INTEGER_WON"),
            FieldMapping("price_type_name", "PROGRM_PRC_TY_NM", "TRIM_BLANK_TO_NULL"),
            FieldMapping("homepage_url", "HMPG_URL", "HTTP_URL_OR_NULL"),
        )
    }
}

private fun Any?.normalizedDate(): LocalDate? = when (this) {
    is LocalDate -> this
    is String -> try {
        LocalDate.parse(this)
    } catch (_: Exception) {
        null
    }
    else -> null
}

class EvidenceNotFoundException :
    GymviApiException("DATA_EVIDENCE_NOT_FOUND", "요청한 근거를 찾을 수 없습니다.", false)
