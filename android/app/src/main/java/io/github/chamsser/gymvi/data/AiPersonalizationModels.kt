package io.github.chamsser.gymvi.data

enum class AiAgeBand { YOUTH, ADULT, SENIOR }
enum class AiExerciseExperience { BEGINNER, REGULAR, EXPERIENCED }
enum class AiExerciseGoal { GENERAL_FITNESS, STRENGTH, FLEXIBILITY, ENDURANCE, WEIGHT_MANAGEMENT }

/** Exact measurements are local profile values, never serialized into an AI request. */
data class AiLocalProfile(
    val ageBand: AiAgeBand? = null,
    val heightCm: Int? = null,
    val weightKg: Double? = null,
    val experience: AiExerciseExperience? = null,
    val goal: AiExerciseGoal? = null,
) {
    init {
        require(heightCm == null || heightCm in 50..250)
        require(weightKg == null || weightKg.isFinite() && weightKg in 10.0..350.0)
    }
}

/** Only allowlisted exercise constraints, not a transcript or arbitrary model-authored prose. */
data class AiMemoryFacts(
    val categories: Set<String> = emptySet(),
    val weekdays: Set<String> = emptySet(),
    val earliestStart: String? = null,
    val latestStart: String? = null,
    val maxPriceWon: Int? = null,
    val maxDistanceMeters: Int? = null,
    val beginner: Boolean = false,
) {
    val isEmpty: Boolean get() = categories.isEmpty() && weekdays.isEmpty() && earliestStart == null &&
        maxPriceWon == null && maxDistanceMeters == null && !beginner
}

data class AiMemoryEntry(
    val id: String,
    val title: String,
    val facts: AiMemoryFacts,
    val updatedAt: Long,
)

data class AiSavedConversationSummary(
    val id: String,
    val title: String,
    val archived: Boolean,
    val updatedAt: Long,
)
