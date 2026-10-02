package io.github.chamsser.gymvi.ai

/**
 * Deterministic scope boundary that runs before the model. It only matches high-precision
 * general-knowledge requests (inventors, capitals, translation and similar), so an exercise
 * framing such as "달리다가 궁금한데" cannot carry an unrelated question to the model.
 */
internal object AiScopePrecheck {
    fun isGeneralKnowledgeRequest(message: String): Boolean {
        val compact = message.lowercase().replace(Regex("\\s+"), "")
        return GENERAL_KNOWLEDGE_PATTERNS.any { it.containsMatchIn(compact) }
    }

    /** Model text that reads like a general-knowledge answer rather than exercise help. */
    fun containsGeneralKnowledgeAnswer(text: String): Boolean {
        val compact = text.replace(Regex("\\s+"), "")
        return KNOWLEDGE_ANSWER_TERMS.any(compact::contains)
    }

    private val GENERAL_KNOWLEDGE_PATTERNS = listOf(
        Regex("(발명|발견|창시|작곡|작사|집필)(한|했던|하신|했던)?(사람|자|인물|분)"),
        Regex("(누가|누구).{0,12}(발명|발견|만들었|만든거|썼|지었|세웠)"),
        Regex("(발명|발견|만든|쓴|지은|세운)(사람|분|이)(이|은|는)?(누구|누군)"),
        Regex("(수도|인구|대통령|총리|국왕|노벨상|환율|주가)(는|은|가|이)?.{0,6}(어디|누구|누군|얼마|언제|몇)"),
        Regex("(번역해|번역좀|코드(를|좀)?짜(줘|주|줄))"),
        Regex("방정식(을|좀)?(풀어|풀이|알려)"),
        Regex("숙제(를|좀)?(해줘|해주|풀어|도와)"),
    )

    private val KNOWLEDGE_ANSWER_TERMS = listOf(
        "발명",
        "발견한",
        "알려져있",
        "역사적으로",
        "인물로는",
    )
}
