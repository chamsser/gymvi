package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.catalog.BoundingBox
import io.github.chamsser.gymvi.catalog.DatasetReference
import io.github.chamsser.gymvi.catalog.FacilityCatalog
import io.github.chamsser.gymvi.catalog.FacilitySortOrder
import io.github.chamsser.gymvi.recommendation.Category

/** A bounded, local-catalog alternative, not a program match or a navigable usage option. */
internal class AiFacilityFallback(private val catalog: FacilityCatalog) {
    fun reply(area: BoundingBox, categories: Set<Category>, dataset: DatasetReference?, message: String = ""): String? {
        if (categories.size != 1 || dataset == null) return null
        val category = categories.single()
        val terms = if (category == Category.YOGA_PILATES) {
            val explicit = listOf("요가", "필라테스").filter(message::contains)
            // A combined ranking category does not make Pilates an alternative to requested yoga.
            explicit.ifEmpty { TERMS.getValue(category) }
        } else TERMS[category] ?: return null
        val facilities = try {
            terms.flatMap { term ->
                val result = catalog.search(area, term, FacilitySortOrder.RELEVANCE, 2)
                // Do not mix a concurrent facility publication with this turn's program snapshot.
                if (result.dataset != dataset) return UNAVAILABLE
                result.facilities
            }.filter { it.operationState != "CLOSED" }
                .distinctBy { it.facilityId }
        } catch (_: RuntimeException) {
            return UNAVAILABLE
        }
        if (facilities.isEmpty()) return "조건에 맞는 프로그램을 찾지 못했고, 현재 지도 범위에서 관련 시설도 찾지 못했어요. " +
            "지도에서 지역을 넓히거나 시간 조건을 바꿔 볼까요?"
        // Deduplicate displayed labels only; do not merge source records or manufacture IDs.
        val names = facilities.map { AiOptionExplanation.safeLabel(it.name) }.filter(String::isNotBlank)
            .distinctBy { it.replace(Regex("[\\s\\p{Z}]+"), "").lowercase(java.util.Locale.ROOT) }.take(2)
        if (names.isEmpty()) return UNAVAILABLE
        return "조건에 맞는 프로그램은 찾지 못했지만, 같은 지도 범위에서 찾은 시설은 ${names.joinToString(", ")}입니다. " +
            "프로그램의 시간, 요금, 이용 대상과 신청 가능 여부는 확인된 결과가 아니에요. " +
            "지도 검색에서 시설 이름으로 찾아 이용 조건을 확인해 주세요."
    }

    private companion object {
        const val UNAVAILABLE = "조건에 맞는 프로그램을 찾지 못했어요. 시설 정보는 지금 확인하지 못했으니 잠시 후 지도 검색에서 다시 확인해 주세요."
        val TERMS = mapOf(
            Category.SWIMMING to listOf("수영"),
            Category.FITNESS to listOf("헬스", "체력단련"),
            Category.YOGA_PILATES to listOf("요가", "필라테스"),
            Category.DANCE to listOf("무도", "댄스"),
            Category.TENNIS to listOf("테니스"),
            Category.BADMINTON to listOf("배드민턴"),
            Category.TABLE_TENNIS to listOf("탁구"),
            Category.GOLF to listOf("골프"),
            Category.SQUASH to listOf("스쿼시"),
            Category.SKATING to listOf("빙상"),
            Category.CLIMBING to listOf("클라이밍"),
        )
    }
}
