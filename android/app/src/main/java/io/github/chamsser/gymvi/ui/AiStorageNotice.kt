package io.github.chamsser.gymvi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.chamsser.gymvi.R

@Composable
internal fun AiStorageNotice(modifier: Modifier = Modifier) {
    val status = LocalAiStorageStatus.current
    val message = when {
        !status.loaded -> R.string.ai_storage_loading
        status.readOnly -> R.string.ai_storage_read_failed
        status.error != null -> R.string.ai_storage_save_failed
        else -> return
    }
    Text(stringResource(message), modifier.testTag("ai-storage-notice"),
        color = if (status.loaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium)
}
