package io.github.chamsser.gymvi.ai

internal enum class AiSafetyBoundary(val reply: String) {
    SELF_HARM_CRISIS(
        "많이 힘드신 것 같아요. 지금 스스로를 다치게 할 위험이 있다면 119에 바로 연락하고, " +
            "가까운 사람에게 곁에 있어 달라고 부탁해 주세요. 자살예방상담전화 109에서도 24시간 이야기할 수 있어요.",
    ),
    DANGER_SIGN(
        "흉통, 호흡곤란, 심한 어지러움, 갑작스러운 저림이나 마비가 있다면 바로 운동을 멈추고 쉬어 주세요. " +
            "증상이 계속되거나 심하면 119에 연락하거나 의료기관 진료를 받아 주세요.",
    ),
    MEDICAL_DECISION(
        "진단이나 처방, 수술 후 운동 재개 시점, 재활 강도는 Gymvi가 판단할 수 없어요. " +
            "의료 전문가와 상담해 주세요. 원하시면 조건에 맞는 시설과 프로그램은 찾아드릴게요.",
    ),
    PERSONALIZED_HEALTH_LOAD(
        "말씀하신 건강 상태에는 개인별 운동 강도나 횟수를 제시하지 않습니다. " +
            "의료 전문가와 상담한 범위 안에서 운동해 주세요. 원하시면 시설과 프로그램은 찾아드릴게요.",
    ),
    HEALTH_EXERCISE_CLEARANCE(
        "통증이나 부상이 있을 때 운동 가능 여부와 강도는 Gymvi가 판단할 수 없어요. " +
            "의료 전문가와 상담해 주세요. 원하시면 상담에서 정한 조건으로 시설과 프로그램을 찾아드릴게요.",
    ),
    DANGEROUS_EXERCISE(
        "이 방식은 부상이나 건강 위험이 커서 안내하지 않아요. 다른 운동이나 시설 찾기는 도와드릴게요.",
    ),
    FACILITY_SAFETY_UNAVAILABLE(
        "지금은 이 시설의 확인된 안전점검 기록을 조회할 수 없어요. " +
            "안전하다거나 위험하다고 판단하지 않을게요. 필요하면 시설에 직접 문의해 주세요.",
    ),
    FACILITY_SAFETY_SEARCH_UNAVAILABLE(
        "지금은 확인된 안전점검 기록을 조회할 수 없어 안전 여부로 시설을 선별할 수 없어요. " +
            "운동 종류, 시간, 가격, 거리 조건으로 프로그램을 찾는 것은 도와드릴게요.",
    ),
}

internal object AiSafetyPrecheck {
    fun classify(message: String): AiSafetyBoundary? {
        val normalized = message.lowercase().replace(Regex("\\s+"), " ").trim()
        val compact = normalized.replace(" ", "")
        if (SELF_HARM_INTENT_PATTERN.containsMatchIn(compact)) return AiSafetyBoundary.SELF_HARM_CRISIS
        if (DANGER_TERMS.any(normalized::contains) ||
            DIZZINESS_TERMS.any(normalized::contains) && DIZZINESS_CONTEXT_TERMS.any(normalized::contains)
        ) return AiSafetyBoundary.DANGER_SIGN
        val medicalRequest = compact
            .replace("운동처방", "")
            .replace("체력진단", "")
            .replace("체력측정", "")
        if (MEDICAL_DECISION_TERMS.any(medicalRequest::contains) ||
            MEDICAL_DECISION_PATTERNS.any { it.containsMatchIn(medicalRequest) } ||
            MEDICATION_DECISION_PATTERN.containsMatchIn(normalized)
        ) return AiSafetyBoundary.MEDICAL_DECISION
        if (DANGEROUS_EXERCISE_TERMS.any(normalized::contains)) return AiSafetyBoundary.DANGEROUS_EXERCISE
        if (HEALTH_CONDITION_TERMS.any(normalized::contains) && HEALTH_LOAD_TERMS.any(normalized::contains)) {
            return AiSafetyBoundary.PERSONALIZED_HEALTH_LOAD
        }
        val healthConcern = hasHealthConcern(normalized)
        if (healthConcern && HEALTH_CLEARANCE_TERMS.any(normalized::contains)) {
            return AiSafetyBoundary.HEALTH_EXERCISE_CLEARANCE
        }
        if (SAFETY_NEGATIONS.none(normalized::contains) &&
            asksAboutFacilitySafety(normalized) &&
            !healthConcern
        ) {
            return if (GENERAL_SAFETY_SEARCH_TERMS.any(normalized::contains)) {
                AiSafetyBoundary.FACILITY_SAFETY_SEARCH_UNAVAILABLE
            } else {
                AiSafetyBoundary.FACILITY_SAFETY_UNAVAILABLE
            }
        }
        return null
    }

    private fun hasHealthConcern(message: String): Boolean =
        GENERAL_HEALTH_TERMS.any(message::contains) ||
            BODY_PART_TERMS.any(message::contains) && INJURY_TERMS.any(message::contains)

    private fun asksAboutFacilitySafety(message: String): Boolean =
        EXPLICIT_SAFETY_RECORD_TERMS.any(message::contains) ||
            GENERAL_SAFETY_SEARCH_TERMS.any(message::contains) ||
            FACILITY_SAFETY_QUESTION_PATTERN.containsMatchIn(message)

    // Explicit intent, not figurative fatigue ("피곤해 죽겠다") or a negated wish.
    private val SELF_HARM_INTENT_PATTERN = Regex(
        "(?:죽고|죽어버리고)싶(?:어|다|네|은|습니다)|죽으려고|목숨을끊|살고싶지않아|" +
            "(?:자살|자해)(?:하고싶(?:어|다|은)|하려고|할생각|할거|할래|하겠|했|하는방법|하는법|방법|생각이나)|" +
            "극단적인선택(?:을)?(?:하고싶(?:어|다|은)|하려고|할생각|할거|하겠)",
    )
    private val DANGER_TERMS = listOf(
        "흉통",
        "가슴 통증",
        "호흡곤란",
        "숨을 못 쉬",
        "숨이 안 쉬",
        "실신",
        "기절",
        "심한 어지러움",
        "갑작스러운 저림",
        "갑자기 저려",
        "마비",
    )
    private val DIZZINESS_TERMS = listOf("어지러워", "어지럽", "어지러움")
    private val DIZZINESS_CONTEXT_TERMS = listOf("지금", "갑자기", "계속", "운동하다가", "달리다가", "수영하다가", "심한")
    private val MEDICAL_DECISION_TERMS = listOf(
        "무슨병",
        "병명",
        "약을먹",
        "약먹",
        "수술후언제",
        "재활강도",
    )
    private val MEDICAL_DECISION_PATTERNS = listOf(
        Regex("(?:진단|처방)(?:해|해줘|해주|정해|알려)"),
    )
    private val MEDICATION_DECISION_PATTERN = Regex(
        "(?:^|\\s)(?:약(?:을|은|이)?(?=\\s|$)(?!\\s*\\d)|복용량(?:을|은|이)?(?=\\s|$))" +
            "\\s*.{0,8}(?:정해|추천|처방)",
    )
    private val DANGEROUS_EXERCISE_TERMS = listOf(
        "탈수 감량",
        "물 안 마시",
        "수분 안 마시",
        "땀복",
        "부상 중 강행",
        "다쳤지만 운동",
        "다쳤는데 운동",
        "음주 후 수영",
        "술 마시고 수영",
        "술먹고 수영",
    )
    private val HEALTH_CONDITION_TERMS = listOf(
        "임신",
        "수술 후",
        "고혈압",
        "당뇨",
        "심장질환",
        "심장 질환",
    )
    private val HEALTH_LOAD_TERMS = listOf("강도", "횟수", "몇 회", "얼마나", "루틴")
    private val HEALTH_CLEARANCE_TERMS = listOf(
        "운동해도",
        "수영해도",
        "달려도",
        "뛰어도",
        "해도 안전",
        "해도 돼",
        "해도 되",
    )
    private val EXPLICIT_SAFETY_RECORD_TERMS = listOf("안전점검", "점검 기록", "안전 등급")
    private val FACILITY_SAFETY_QUESTION_PATTERN = Regex(
        "(?:시설|센터|체육관|수영장|운동장|여기|이곳).{0,12}(?:안전해|안전한가|안전한지|위험해|위험한가|위험한지)",
    )
    private val GENERAL_SAFETY_SEARCH_TERMS = listOf(
        "안전한 시설",
        "안전한 센터",
        "안전한 체육관",
        "안전한 수영장",
        "안전한 운동장",
        "안전한 곳",
        "안전 점검 잘 된",
        "안전점검 잘 된",
        "안전 등급이 좋은",
        "안전점검 등급이 좋은",
        "안전점검 기록이 없는 시설",
        "안전점검 기록 없는 시설",
    )
    private val SAFETY_NEGATIONS = listOf("안전 말고", "안전은 빼고", "안전 얘기 말고")
    private val GENERAL_HEALTH_TERMS = listOf(
        "아프",
        "아픈",
        "아파",
        "아픔",
        "통증",
        "부상",
        "다쳤",
        "질환",
        "임신",
        "수술",
        "고혈압",
        "당뇨",
        "심장",
    )
    private val BODY_PART_TERMS = listOf("무릎", "허리")
    private val INJURY_TERMS = listOf("아프", "아픈", "아파", "아픔", "통증", "부상", "다쳤", "수술")
}
