package io.github.chamsser.gymvi.usage

/** A price unit printed in the source price type text. Units are never converted into each other. */
enum class PriceUnitKind { MONTHS, SESSIONS }

data class PriceUnit(val kind: PriceUnitKind, val count: Int)

/**
 * Server reading of a source program price.
 *
 * The pipeline keeps the source amount unchanged, including 0, because it is part of the source-based program
 * ID and snapshot. This interpretation decides what responses, ranking and comparison may claim about it.
 */
data class ProgramPrice(
    val amountWon: Int?,
    val unit: PriceUnit?,
    val unknownReasonCode: String?,
) {
    companion object {
        const val SOURCE_VALUE_BLANK = "SOURCE_VALUE_BLANK"
        const val ZERO_WITHOUT_FREE_EVIDENCE = "ZERO_WITHOUT_FREE_EVIDENCE"

        /**
         * Exact source program names whose source price is 0 and whose own name states the program is free.
         * A known 0 rests on both fields, PROGRM_PRC and PROGRM_NM. A renamed program or a new free wording
         * stays unknown until it is reviewed against the source; a free mark never implies recruitment or
         * operation state.
         */
        private val FREE_PROGRAM_NAMES = setOf(
            "11월 19일(화) 무료입장 1회차(06:00~08:00)",
            "11월 19일(화) 무료입장 2회차(09:00~11:00)",
            "11월 19일(화) 무료입장 3회차(12:00~14:00)",
            "11월 19일(화) 무료입장 4회차(15:00~17:00)",
            "11월 19일(화) 무료입장 5회차(18:00~20:00)",
            "[무료]달달커플요가(1기)10.5~10.12(현장등록가능)",
            "[무료]달달커플요가(2기)10.19~10.26(현장등록가능)",
            "무료원데이클래스(3/20)",
            "무료원데이클래스(3/25)",
            "시니어 무료 태권도 교실(노인)",
        )
        private const val MAX_MONTHS = 12
        private const val MAX_SESSIONS = 100
        private val MONTHS = Regex("([0-9]+)\\s*개월")
        private val FREQUENCY = Regex("[주월]\\s*[0-9]+\\s*회")
        private val AGE = Regex("[0-9]+\\s*세(?!트)")
        private val SESSIONS = Regex("([0-9]+)\\s*회(?!원)")
        private val DIGIT = Regex("[0-9]")

        /** Discounts, ranges and included extras leave open which amount or count the price is billed for. */
        private val AMBIGUOUS = Regex("할인|감면|할증|포함|%|~|∼")

        fun interpret(sourcePriceWon: Int?, priceTypeName: String?, programName: String): ProgramPrice {
            val unit = parseUnit(priceTypeName)
            return when {
                sourcePriceWon == null -> ProgramPrice(null, unit, SOURCE_VALUE_BLANK)
                // The source does not distinguish free from unrecorded, and operator pages showed paid fees.
                sourcePriceWon == 0 && programName.trim() !in FREE_PROGRAM_NAMES ->
                    ProgramPrice(null, unit, ZERO_WITHOUT_FREE_EVIDENCE)
                else -> ProgramPrice(sourcePriceWon, unit, null)
            }
        }

        /**
         * Reads exactly one positive `N개월` or `N회` token after removing weekly/monthly frequency and age
         * tokens. Codes, multiple prices, leftover numbers, both units at once or ambiguity markers stay unknown.
         */
        fun parseUnit(priceTypeName: String?): PriceUnit? {
            var rest = priceTypeName?.trim()?.takeIf(String::isNotEmpty) ?: return null
            if (AMBIGUOUS.containsMatchIn(rest)) return null
            val months = MONTHS.findAll(rest).map { it.groupValues[1].toIntOrNull() }.toList()
            rest = MONTHS.replace(rest, " ")
            rest = FREQUENCY.replace(rest, " ")
            rest = AGE.replace(rest, " ")
            val sessions = SESSIONS.findAll(rest).map { it.groupValues[1].toIntOrNull() }.toList()
            rest = SESSIONS.replace(rest, " ")
            if (DIGIT.containsMatchIn(rest)) return null
            return when {
                months.isNotEmpty() && sessions.isEmpty() -> months.singleCount(PriceUnitKind.MONTHS, 1..MAX_MONTHS)
                sessions.isNotEmpty() && months.isEmpty() -> sessions.singleCount(PriceUnitKind.SESSIONS, 1..MAX_SESSIONS)
                else -> null
            }
        }

        private fun List<Int?>.singleCount(kind: PriceUnitKind, range: IntRange): PriceUnit? {
            val count = distinct().singleOrNull() ?: return null
            return if (count in range) PriceUnit(kind, count) else null
        }
    }
}
