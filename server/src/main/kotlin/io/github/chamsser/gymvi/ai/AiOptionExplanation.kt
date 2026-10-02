package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.recommendation.RecommendationItem
import io.github.chamsser.gymvi.usage.PriceUnitKind
import io.github.chamsser.gymvi.usage.ProgramPrice
import java.util.Locale

/** Factual prose comes from the same ordered server cards as the UI, never from model claims. */
internal object AiOptionExplanation {
    fun compose(
        cards: List<RecommendationItem>,
        introduction: String,
        message: String = "",
        previousCards: List<RecommendationItem> = emptyList(),
    ): String {
        if (cards.isEmpty()) return "조건에 맞는 프로그램을 찾지 못했어요. 지도에서 지역을 넓히거나 시간 조건을 바꿔 볼까요?"
        val priceFollowUp = CHEAPER.containsMatchIn(message.replace(Regex("\\s+"), ""))
        val intro = if (priceFollowUp) priceFollowUp(cards, previousCards) else introduction
        val details = cards.take(2).mapIndexed { index, card ->
            val conditions = card.matchedConditions.mapNotNull(CONDITION_LABELS::get).distinct().take(3)
            val reason = if (conditions.isEmpty()) "" else " ${conditions.joinToString(", ")} 조건이 맞아요."
            "${index + 1}. ${label(card)}: ${priceLabel(card)}.$reason"
        }
        val unknown = buildList {
            if (cards.any { "START_TIME_UNKNOWN" in it.uncertaintyCodes }) add("시작 시각")
            if (cards.any { "APPLICATION_STATE_UNKNOWN" in it.uncertaintyCodes }) add("신청 가능 여부")
        }
        val caveat = if (unknown.isEmpty()) emptyList() else
            listOf("일부 후보의 ${unknown.joinToString("과 ")}는 확인되지 않았어요. 이용 전 시설에 확인해 주세요.")
        return (listOf(intro) + details + caveat).joinToString("\n")
    }

    private fun priceFollowUp(cards: List<RecommendationItem>, previous: List<RecommendationItem>): String {
        val prices = (cards + previous).map(::price)
        if (prices.any { it.amountWon == null || it.unit == null }) {
            return "가격이나 이용 단위가 확인되지 않은 후보가 있어 더 싼 곳을 판단하기 어려워요. " +
                "시설에 요금을 확인하거나 지역과 시간 조건을 바꿔 찾아볼 수 있어요."
        }
        if (prices.map { it.unit }.distinct().size != 1) {
            return "후보마다 이용 단위가 달라 가격을 바로 비교하기 어려워요. 월 이용료인지 1회 요금인지 정해 볼까요?"
        }
        if (previous.isEmpty()) return "확인된 가격을 카드 순서대로 안내할게요. 가격순으로 다시 정렬한 결과는 아니에요."
        val previousMinimum = previous.minOf { requireNotNull(price(it).amountWon) }
        val cheaper = cards.firstOrNull { requireNotNull(price(it).amountWon) < previousMinimum }
        return if (cheaper != null) {
            "${label(cheaper)}의 표시 가격은 같은 이용 단위의 이전 후보보다 낮아요. 신청 가능 여부는 별도로 확인해 주세요."
        } else {
            "이번 결과에는 이전 후보보다 표시 가격이 낮은 프로그램이 없어요. 지역이나 시간 조건을 바꿔 볼까요?"
        }
    }

    private fun price(card: RecommendationItem) = ProgramPrice.interpret(
        card.option.priceWon, card.option.priceTypeName, card.option.programName,
    )

    private fun priceLabel(card: RecommendationItem): String {
        val price = price(card)
        val amount = price.amountWon ?: return "가격 미확인"
        val won = String.format(Locale.US, "%,d원", amount)
        val unit = price.unit ?: return "$won(이용 단위 미확인)"
        return when (unit.kind) {
            PriceUnitKind.MONTHS -> "${unit.count}개월 $won"
            PriceUnitKind.SESSIONS -> "${unit.count}회 $won"
        }
    }

    private fun label(card: RecommendationItem): String =
        "${safeLabel(card.option.facilityName)} ${safeLabel(card.option.programName)}".trim()

    internal fun safeLabel(raw: String): String = raw
        .replace(Regex("[\\p{C}\\p{Z}]+"), " ")
        .replace(Regex("[\\[\\]`*_<>]"), "")
        .trim().take(80)

    private val CHEAPER = Regex("더(?:싼|저렴)|저렴한|싼곳|싼데|가격(?:이|은)?낮")
    private val CONDITION_LABELS = mapOf(
        "CATEGORY" to "종목", "WEEKDAYS" to "요일", "START_TIME" to "시작 시각",
        "MAX_PRICE_WON" to "예산", "MAX_DISTANCE_METERS" to "직선거리",
        "TARGET_GROUPS" to "이용 대상", "BEGINNER" to "초급", "APPLICATION_AVAILABLE" to "신청 가능",
        "WEEKDAY_COVERAGE" to "이용 요일", "MINIMUM_WEEKDAY_COUNT" to "주간 이용 일수",
    )
}
