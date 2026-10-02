package io.github.chamsser.gymvi.data

import android.content.Context

data class AiPreferences(
    val useAiMemory: Boolean = false,
    val useBodyInformation: Boolean = false,
    val useApproximateRegion: Boolean = false,
    val saveConversationHistory: Boolean = true,
)

class AiPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun load(): AiPreferences = AiPreferences(
        useAiMemory = preferences.getBoolean(KEY_USE_AI_MEMORY, false),
        useBodyInformation = preferences.getBoolean(KEY_USE_BODY_INFORMATION, false),
        useApproximateRegion = preferences.getBoolean(KEY_USE_APPROXIMATE_REGION, false),
        saveConversationHistory = preferences.getBoolean(KEY_SAVE_CONVERSATION_HISTORY, true),
    )

    fun save(state: AiPreferences) {
        preferences.edit()
            .putBoolean(KEY_USE_AI_MEMORY, state.useAiMemory)
            .putBoolean(KEY_USE_BODY_INFORMATION, state.useBodyInformation)
            .putBoolean(KEY_USE_APPROXIMATE_REGION, state.useApproximateRegion)
            .putBoolean(KEY_SAVE_CONVERSATION_HISTORY, state.saveConversationHistory)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "gymvi_ai_preferences_v1"
        const val KEY_USE_AI_MEMORY = "use_ai_memory"
        const val KEY_USE_BODY_INFORMATION = "use_body_information"
        const val KEY_USE_APPROXIMATE_REGION = "use_approximate_region"
        const val KEY_SAVE_CONVERSATION_HISTORY = "save_conversation_history"
    }
}
