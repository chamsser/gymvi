package io.github.chamsser.gymvi.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AiPreferences
import io.github.chamsser.gymvi.data.AiExerciseExperience

@Composable
internal fun personalizedAiExamples(preferences: AiPreferences, actions: AiPersonalizationActions?, defaults: List<String>): List<String> {
    if (actions == null) return defaults
    val memories = if (preferences.useAiMemory) actions.memories.sortedByDescending { it.updatedAt } else emptyList()
    val category = memories.firstNotNullOfOrNull { it.facts.categories.sorted().firstOrNull() }
    val keys = listOf("SWIMMING", "FITNESS", "YOGA_PILATES", "DANCE", "TENNIS", "BADMINTON", "TABLE_TENNIS",
        "GOLF", "SQUASH", "SKATING", "TEAM_BALL", "MARTIAL_ARTS", "CLIMBING")
    val label = keys.indexOf(category).takeIf { it >= 0 }?.let { stringArrayResource(R.array.ai_suggestion_sports)[it] }
    val profile = actions.profile.takeIf { preferences.useBodyInformation }
    val goal = profile?.goal?.ordinal?.let { stringArrayResource(R.array.ai_suggestion_goals)[it] }
    return listOf(
        label?.let { stringResource(R.string.ai_suggestion_program, it) } ?: defaults[0],
        goal?.let { stringResource(R.string.ai_suggestion_goal, it) } ?: defaults[1],
        if (profile?.experience == AiExerciseExperience.BEGINNER || memories.firstOrNull()?.facts?.beginner == true)
            stringResource(R.string.ai_suggestion_beginner) else defaults[2],
    )
}
