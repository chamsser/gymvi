package io.github.chamsser.gymvi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal fun gymviSubtleBorderColor(): Color =
    MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
