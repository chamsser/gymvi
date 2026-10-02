package io.github.chamsser.gymvi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CombinedAiUserMessage(text: String, palette: CombinedPalette, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var menuOpen by remember(text) { mutableStateOf(false) }
    var selecting by remember(text) { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.ai_user_message_menu)
    val shape = RoundedCornerShape(22.dp)
    Box(modifier) {
        Text(
            text = text,
            modifier = Modifier.clip(shape)
                .background(if (menuOpen) lerp(palette.quiet, MaterialTheme.colorScheme.onSurface, .06f) else palette.quiet)
                .combinedClickable(onClickLabel = menuLabel, onLongClickLabel = menuLabel,
                    onClick = { menuOpen = true }, onLongClick = { menuOpen = true })
                .testTag("ai-user-message").padding(horizontal = 18.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.ai_user_message_copy)) },
                modifier = Modifier.testTag("ai-user-message-copy"), onClick = {
                    copyUserMessage(context, text)
                    menuOpen = false
                })
            DropdownMenuItem(text = { Text(stringResource(R.string.ai_user_message_select)) },
                modifier = Modifier.testTag("ai-user-message-select"), onClick = {
                    menuOpen = false
                    selecting = true
                })
        }
    }
    if (selecting) {
        var selection by remember(text) { mutableStateOf(TextFieldValue(text, TextRange(0, text.length))) }
        val focus = remember { FocusRequester() }
        ModalBottomSheet(
            onDismissRequest = { selecting = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.raised,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.ai_user_message_select),
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(enabled = !selection.selection.collapsed,
                        modifier = Modifier.testTag("ai-user-message-copy-selection"), onClick = {
                            copyUserMessage(context, text.substring(selection.selection.min, selection.selection.max))
                            selecting = false
                        }) { Text(stringResource(R.string.ai_user_message_copy)) }
                }
                // Read-only keeps native selection handles and the system copy toolbar without opening an IME.
                BasicTextField(
                    value = selection,
                    onValueChange = { changed ->
                        selection = TextFieldValue(text, TextRange(
                            changed.selection.start.coerceIn(0, text.length),
                            changed.selection.end.coerceIn(0, text.length),
                        ))
                    },
                    readOnly = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()).padding(vertical = 16.dp)
                        .focusRequester(focus).testTag("ai-user-message-selection"),
                )
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}

private fun copyUserMessage(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("", text))
    // Android 13 and later already confirm clipboard writes at the system level.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.ai_message_copied, Toast.LENGTH_SHORT).show()
    }
}
