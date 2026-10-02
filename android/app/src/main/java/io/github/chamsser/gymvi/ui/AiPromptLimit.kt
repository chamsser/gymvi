package io.github.chamsser.gymvi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import io.github.chamsser.gymvi.R
import java.util.Locale

/** The longest question a turn accepts once trimmed; the AI turn API enforces the same limit. */
internal const val AI_PROMPT_MAX_LENGTH = 1_000

/** Whether sending [text] would be refused, so a composer keeps the draft instead of clearing it. */
internal fun isAiPromptTooLong(text: String): Boolean = text.trim().length > AI_PROMPT_MAX_LENGTH

/** Says why send is off while the draft is over the limit; the draft itself stays untouched. */
@Composable
internal fun AiPromptLengthNotice(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Text(
        text = stringResource(
            R.string.ai_prompt_too_long,
            String.format(Locale.KOREAN, "%,d", AI_PROMPT_MAX_LENGTH),
            String.format(Locale.KOREAN, "%,d", text.trim().length),
        ),
        modifier = modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("ai-prompt-too-long"),
        color = MaterialTheme.colorScheme.error,
        style = style,
    )
}
