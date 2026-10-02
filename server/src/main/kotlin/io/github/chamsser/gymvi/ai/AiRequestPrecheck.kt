package io.github.chamsser.gymvi.ai

import io.github.chamsser.gymvi.recommendation.RecommendationConditions
import io.github.chamsser.gymvi.recommendation.Weekday

internal data class AiSchedulePolicy(
    val requiredDays: Set<Weekday> = emptySet(),
    val minimumDayCount: Int? = null,
) {
    init {
        require(requiredDays.isNotEmpty() || minimumDayCount != null)
        require(requiredDays.isEmpty() || minimumDayCount == null)
    }

    fun applyTo(conditions: RecommendationConditions): RecommendationConditions = conditions.copy(weekdays = null)

    fun matchesWeekdayLabels(labels: List<String>): Boolean {
        val actual = labels.flatMap(::parseWeekdayLabel).toSet()
        if (actual.isEmpty()) return false
        return if (requiredDays.isNotEmpty()) {
            actual.containsAll(requiredDays)
        } else {
            actual.size >= requireNotNull(minimumDayCount)
        }
    }

    private fun parseWeekdayLabel(raw: String): List<Weekday> {
        val label = raw.trim()
        DAY_LABELS[label]?.let { return listOf(it) }
        if (label.isNotEmpty() && label.all(DAY_CHARACTERS::contains)) {
            return label.mapNotNull { DAY_LABELS[it.toString()] }
        }
        return emptyList()
    }

    private companion object {
        const val DAY_CHARACTERS = "월화수목금토일"
        val DAY_LABELS = mapOf(
            "월" to Weekday.MON,
            "월요일" to Weekday.MON,
            "화" to Weekday.TUE,
            "화요일" to Weekday.TUE,
            "수" to Weekday.WED,
            "수요일" to Weekday.WED,
            "목" to Weekday.THU,
            "목요일" to Weekday.THU,
            "금" to Weekday.FRI,
            "금요일" to Weekday.FRI,
            "토" to Weekday.SAT,
            "토요일" to Weekday.SAT,
            "일" to Weekday.SUN,
            "일요일" to Weekday.SUN,
        )
    }
}

/** High-precision request checks that must hold even when the model chooses the wrong action. */
internal object AiRequestPrecheck {
    fun isForbiddenCapabilityRequest(message: String): Boolean {
        val normalized = message.lowercase().replace(Regex("\\s+"), " ").trim()
        return FORBIDDEN_OWNED_OBJECT_PATTERN.containsMatchIn(normalized) ||
            FORBIDDEN_PERSONAL_SCHEDULE_PATTERN.containsMatchIn(normalized)
    }

    fun clarificationQuestion(
        message: String,
        currentConditions: RecommendationConditions,
        previousOptionIds: Set<String>,
    ): String? {
        val compact = compact(message)
        if (isComparisonIntent(compact)) {
            val mentionedIds = OPTION_ID_PATTERN.findAll(message).map { it.value }.toSet()
            if (mentionedIds.any { it !in previousOptionIds }) {
                return "비교할 프로그램은 직전 카드에서 두 개 골라 주세요."
            }
            if (SPORT_TERM_GROUPS.count { terms -> terms.any(compact::contains) } >= 2) {
                return "운동 목표나 선호 중 무엇을 기준으로 비교할까요?"
            }
        }
        val categoryKnown = currentConditions.categories?.values?.isNotEmpty() == true ||
            SPORT_TERM_GROUPS.any { terms -> terms.any(compact::contains) }
        if (!categoryKnown && !allowsAnyCategory(compact) && isSearchIntent(compact) && hasOrphanConstraint(compact)) {
            return "원하는 운동 종목을 알려주세요."
        }
        return null
    }

    fun resolveSchedulePolicy(message: String, current: AiSchedulePolicy?): AiSchedulePolicy? {
        val compact = compact(message)
        return when {
            EVERY_DAY_NEGATION_PATTERN.containsMatchIn(compact) -> null
            compact.contains("매일") &&
                (compact.contains("평일") || WEEKDAY_RANGE_PATTERN.containsMatchIn(compact)) -> {
                AiSchedulePolicy(requiredDays = WEEKDAYS)
            }
            FIVE_DAYS_PATTERN.containsMatchIn(compact) -> AiSchedulePolicy(minimumDayCount = 5)
            compact.contains("매일") -> AiSchedulePolicy(requiredDays = Weekday.entries.toSet())
            WEEKDAY_CHANGE_PATTERN.containsMatchIn(compact) ||
                GENERIC_WEEKDAY_CHANGE_PATTERN.containsMatchIn(compact) -> null
            else -> current
        }
    }

    private fun allowsAnyCategory(compact: String): Boolean =
        OPEN_CATEGORY_TERMS.any(compact::contains) ||
            (compact.contains("실내") && compact.contains("운동"))

    private fun isSearchIntent(compact: String): Boolean = SEARCH_TERMS.any(compact::contains)

    private fun isComparisonIntent(compact: String): Boolean =
        COMPARISON_TERMS.any(compact::contains) || COMPARISON_QUESTION_PATTERN.containsMatchIn(compact)

    private fun hasOrphanConstraint(compact: String): Boolean =
        CONSTRAINT_TERMS.any(compact::contains) || TIME_PATTERN.containsMatchIn(compact) ||
            PRICE_PATTERN.containsMatchIn(compact) || DISTANCE_PATTERN.containsMatchIn(compact)

    private fun compact(value: String): String = value.lowercase().replace(Regex("\\s+"), "")

    private val WEEKDAYS = setOf(Weekday.MON, Weekday.TUE, Weekday.WED, Weekday.THU, Weekday.FRI)
    private val FORBIDDEN_OWNED_OBJECT_PATTERN = Regex(
        "(?:^|\\s)(?:내|제|이|첨부한|업로드한)\\s*(?:파일|문서|사진|연락처|메일|문자).{0,12}" +
            "(?:읽|열|봐|보여|확인|분석|보내|전송|추가|등록|삭제)",
    )
    private val FORBIDDEN_PERSONAL_SCHEDULE_PATTERN = Regex(
        "(?:^|\\s)(?:(?:내|제)\\s*(?:일정|캘린더|달력)|(?:캘린더|달력)).{0,12}" +
            "(?:읽|열|봐|보여|확인|분석|보내|전송|추가|등록|삭제)",
    )
    private val OPTION_ID_PATTERN = Regex(
        "(?i)(?:option[-_:][a-z0-9._:-]+|[a-z][a-z0-9_-]{1,40}:[a-z0-9._:-]+)",
    )
    private val SPORT_TERM_GROUPS = listOf(
        listOf("수영", "아쿠아"),
        listOf("헬스", "피트니스", "웨이트"),
        listOf("요가"),
        listOf("필라테스"),
        listOf("댄스", "에어로빅", "줌바", "발레", "방송댄스", "라인댄스"),
        listOf("테니스"),
        listOf("배드민턴"),
        listOf("탁구"),
        listOf("골프"),
        listOf("스쿼시"),
        listOf("스케이트", "빙상"),
        listOf("농구"),
        listOf("배구"),
        listOf("축구", "풋살"),
        listOf("태권도", "유도", "검도", "복싱", "주짓수"),
        listOf("클라이밍"),
    )
    private val SEARCH_TERMS = listOf("찾", "추천", "보여", "골라", "어디", "할만한곳", "할만한데")
    private val COMPARISON_TERMS = listOf("비교", "대결", "vs")
    private val COMPARISON_QUESTION_PATTERN = Regex("(?:중|뭐가|어느|어떤).{0,8}(?:낫|나을|좋|맞)")
    private val OPEN_CATEGORY_TERMS = listOf(
        "실내운동",
        "아무운동이나",
        "아무종목이나",
        "종목상관없이",
        "운동종류상관없이",
        "운동은상관없",
    )
    private val CONSTRAINT_TERMS = listOf(
        "아침",
        "점심",
        "오후",
        "저녁",
        "밤",
        "가격",
        "비용",
        "예산",
        "무료",
        "가까",
        "거리",
        "근처",
        "주변",
        "신청",
        "모집",
        "시설",
        "센터",
        "할만한곳",
    )
    private val TIME_PATTERN = Regex("(?:오전|오후|저녁)?\\d{1,2}시")
    private val PRICE_PATTERN = Regex("\\d+(?:천|만)?원")
    private val DISTANCE_PATTERN = Regex("\\d+(?:km|킬로|m|미터|분안)")
    private val EVERY_DAY_NEGATION_PATTERN = Regex("매일.{0,10}(?:못|어렵|힘들|말고|제외|않|아니)")
    private val WEEKDAY_RANGE_PATTERN = Regex("(?:월(?:요일)?(?:부터|에서|~)?금(?:요일)?(?:까지)?|월화수목금)")
    private val FIVE_DAYS_PATTERN = Regex("주(?:에)?5일")
    private val WEEKDAY_CHANGE_PATTERN = Regex(
        "(?:월요일|화요일|수요일|목요일|금요일|토요일|일요일|월수금|화목|평일|주말)",
    )
    private val GENERIC_WEEKDAY_CHANGE_PATTERN = Regex("요일.{0,8}(?:바꿔|변경|빼|상관없|제외)")
}
