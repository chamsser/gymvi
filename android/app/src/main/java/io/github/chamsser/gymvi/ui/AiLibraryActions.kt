package io.github.chamsser.gymvi.ui

import androidx.compose.runtime.compositionLocalOf
import io.github.chamsser.gymvi.data.AiLocalProfile
import io.github.chamsser.gymvi.data.AiMemoryEntry
import io.github.chamsser.gymvi.data.AiMemoryFacts
import io.github.chamsser.gymvi.data.AiSavedConversationSummary

/** UI callbacks keep persistence and AI request authority in the controller/application. */
internal data class AiLibraryActions(
    val conversations: List<AiSavedConversationSummary>,
    val currentId: String?,
    val savingEnabled: Boolean,
    val onOpen: (String) -> Unit,
    val onRename: (String, String) -> Unit,
    val onArchive: (String, Boolean) -> Unit,
    val onDelete: (String) -> Unit,
)

internal data class AiPersonalizationActions(
    val profile: AiLocalProfile,
    val memories: List<AiMemoryEntry>,
    val onProfileChanged: (AiLocalProfile) -> Unit,
    val onMemoryChanged: (String, AiMemoryFacts) -> Unit,
    val onMemoryDeleted: (String) -> Unit,
)

internal val LocalAiLibraryActions = compositionLocalOf<AiLibraryActions?> { null }
internal val LocalAiPersonalizationActions = compositionLocalOf<AiPersonalizationActions?> { null }
internal val LocalAiReferencePreferences = compositionLocalOf { io.github.chamsser.gymvi.data.AiPreferences() }

internal data class AiStorageStatus(
    val loaded: Boolean = true,
    val readOnly: Boolean = false,
    val error: String? = null,
) {
    val canEdit: Boolean get() = loaded && !readOnly
}

internal val LocalAiStorageStatus = compositionLocalOf { AiStorageStatus() }
