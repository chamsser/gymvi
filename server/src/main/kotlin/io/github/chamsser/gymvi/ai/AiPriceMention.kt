package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.recommendation.PriceConditionUnit
import java.math.BigDecimal

/**
 * Deterministic reading of a budget in the user's own message, shared by the search boundary and budget
 * learning. It covers one amount in digits followed by 원, or by 만/천 and a limit word such as 이하, and
 * the unit words written right next to it. Wording outside that scope is left to the model and is not
 * claimed to be enforced.
 */
internal sealed interface AiPriceMention {
    /** No amount in the covered forms. */
    data object None : AiPriceMention

    /** One amount and a clear unit; [unit] is null only when the message names no billing unit at all. */
    data class Clear(val amountWon: Int, val unit: PriceConditionUnit?) : AiPriceMention

    /** An amount for a period or package a price condition cannot express, such as 3개월 or 10회. */
    data object Unsupported : AiPriceMention

    /** Several amounts, a range, a lower bound, an exclusion or an unclear link between amount and unit. */
    data object Ambiguous : AiPriceMention

    companion object {
        fun read(message: String): AiPriceMention {
            val text = DIGIT_GROUPING.replace(WHITESPACE.replace(message.lowercase(), ""), "")
            val amounts = AMOUNT.findAll(text).toList()
            if (amounts.isEmpty()) return None
            val match = amounts.singleOrNull() ?: return Ambiguous
            val amount = amountWon(match) ?: return Ambiguous
            val before = text.substring(0, match.range.first)
            val after = text.substring(match.range.last + 1)
            if (RANGE_BEFORE.containsMatchIn(before) || LOWER_BOUND_BEFORE.containsMatchIn(before) ||
                NOT_A_LIMIT_AFTER.containsMatchIn(after)
            ) return Ambiguous
            val month = MONTH_BEFORE.containsMatchIn(before) || MONTH_AFTER.containsMatchIn(after)
            val session = SESSION_BEFORE.containsMatchIn(before) || SESSION_AFTER.containsMatchIn(after)
            val other = OTHER_BEFORE.containsMatchIn(before) || OTHER_AFTER.containsMatchIn(after)
            return when {
                listOf(month, session, other).count { it } > 1 -> Ambiguous
                other -> Unsupported
                month -> Clear(amount, PriceConditionUnit.MONTH)
                session -> Clear(amount, PriceConditionUnit.SESSION)
                OTHER_ANYWHERE.containsMatchIn(text) -> Unsupported
                UNIT_ANYWHERE.containsMatchIn(text) || FREQUENCY_ANYWHERE.containsMatchIn(text) -> Ambiguous
                else -> Clear(amount, null)
            }
        }

        private fun amountWon(match: MatchResult): Int? {
            val (tenThousands, extraThousands, thousands, plain) = match.destructured
            val value = when {
                tenThousands.isNotEmpty() -> BigDecimal(tenThousands).multiply(BigDecimal(10_000))
                    .add(BigDecimal(extraThousands.ifEmpty { "0" }).multiply(BigDecimal(1_000)))
                thousands.isNotEmpty() -> BigDecimal(thousands).multiply(BigDecimal(1_000))
                else -> BigDecimal(plain)
            }
            if (value.stripTrailingZeros().scale() > 0 || value > BigDecimal(MAX_WON)) return null
            return value.toInt()
        }

        private const val MAX_WON = 10_000_000
        private val WHITESPACE = Regex("\\s+")
        private val DIGIT_GROUPING = Regex("(?<=[0-9]),(?=[0-9]{3}(?![0-9]))")
        private const val LIMIT_WORD = "이하|이내|까지|미만|안쪽|아래|밑|정도|내외|짜리|선에서"
        private val AMOUNT = Regex(
            "(?<![0-9.만천])(?:([0-9]+(?:\\.[0-9]+)?)만(?:([0-9])천)?|([0-9]+)천|([0-9]+)(?=원))" +
                "(?:원|(?=$LIMIT_WORD))",
        )

        /** Words that may sit between a unit and the amount after it, as in 3개월 합계, 월 회비 or 한 달에 최대. */
        private const val JOINER = "(?:에|당|마다|기준으로|기준|단위로|단위|씩|치|간|동안|합계|합쳐서|총액|총|최대|대략|약|" +
            "회비|요금|이용료|수강료|강습비|비용|가격|금액|은|는|이|가)*"
        private val RANGE_BEFORE = Regex("[~∼〜-]$")
        private val LOWER_BOUND_BEFORE = Regex("(?:최소|적어도)$")
        private val NOT_A_LIMIT_AFTER = Regex(
            "^(?:[~∼〜-]|대|(?:짜리|정도)?(?:은|는|이|가|도|을|를)?(?:이상|초과|넘(?!지않|지말)|부터|보다(?:더)?비|말고|빼고|제외))",
        )

        // 월 is not a unit after a digit (6월), inside 개월, before a weekday (월요일, 월수금) or in 월N회.
        private val MONTH_BEFORE = Regex("(?:(?<![0-9개])월|매월|매달|한달|(?<![0-9])1달|(?<![0-9])1개월)$JOINER$")
        private val MONTH_AFTER = Regex("^(?:/|씩|에)?(?:월(?!요일|[화수목금토일]|[0-9])|매월|매달|한달|1달|1개월)")

        // 1회 after 주 or 월 is a frequency (주1회), not a per-session price.
        private val SESSION_BEFORE = Regex("(?:회당|매회|회차당|(?<![0-9주월])1회(?:당|권|분)?|(?<![0-9주월])1번에|한번에)$JOINER$")
        private val SESSION_AFTER = Regex("^(?:/회|씩?(?:회당|매회)|에?(?:1회|1번|한번)(?![0-9]))")

        private const val OTHER_UNIT = "(?<![0-9])(?:[2-9]|[1-9][0-9])개월|(?:두|세|네|다섯|여섯)달|(?<![0-9])[2-9]달|분기|반년|" +
            "(?<![0-9])[0-9]+년|일년|연간|주당|일주일|하루|(?<![0-9월])1일|일일|" +
            "(?<![0-9주월])(?:[2-9]|[1-9][0-9])(?:회|번)(?:권|분|짜리)?|회권"
        private val OTHER_BEFORE = Regex("(?:$OTHER_UNIT|(?<![0-9가-힣])연)$JOINER$")
        private val OTHER_AFTER = Regex("^(?:/(?:일|주|년|분기)|씩?(?:$OTHER_UNIT))")
        private val OTHER_ANYWHERE = Regex(
            "(?<![0-9])(?:[2-9]|[1-9][0-9])개월|(?:두|세|네|다섯|여섯)달|(?<![0-9])[2-9]달|분기|반년|" +
                "(?<![0-9])[0-9]+년|일년|연간|연회비|회권|(?<![0-9주월])(?:[2-9]|[1-9][0-9])회(?:권|분|짜리)",
        )
        private val UNIT_ANYWHERE = Regex(
            "회당|매회|한달|(?<![0-9])1달|(?<![0-9])1개월|매달|매월|월간|(?<![0-9개])월(?:에|당|마다|기준|씩|단위|회비)|/월|/회|" +
                "(?<![0-9주월])1회",
        )
        private val FREQUENCY_ANYWHERE = Regex("(?:[주월]|주에|매주|일주일에)[0-9]+(?:회|번)")
    }
}
