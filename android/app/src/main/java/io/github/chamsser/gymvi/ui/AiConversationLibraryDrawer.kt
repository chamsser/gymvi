package io.github.chamsser.gymvi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AiSavedConversationSummary

/**
 * The longest title the library accepts (AiConversationController.renameConversation); longer
 * input is cut here rather than rejected there.
 */
internal const val AiConversationTitleMaxLength = 80

/** A typed or pasted title as the library will keep it: no control characters, within the limit. */
internal fun aiConversationTitleInput(text: String): String =
    text.filterNot(Char::isISOControl).take(AiConversationTitleMaxLength)

/**
 * The saved conversations under the history drawer's 새 대화 row. A tap reopens one, and a long
 * press opens its menu (이름 변경, 보관 or 보관 해제, 삭제) next to the row; screen readers get
 * the same three as accessibility actions. Archived conversations wait in their own list behind
 * the entry at the bottom, where they reopen and come back the same way. The list keeps the order
 * the library gives it. While saving is off every saved conversation stays listed but closed: the
 * note above the list says why, and only the menu still answers until saving is back on.
 */
@Composable
internal fun AiConversationLibrary(
    actions: AiLibraryActions,
    showingArchive: Boolean,
    onShowingArchiveChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = combinedPalette()
    val storage = LocalAiStorageStatus.current
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val active = remember(actions.conversations) { actions.conversations.filterNot { it.archived } }
    val archived = remember(actions.conversations) { actions.conversations.filter { it.archived } }
    val shown = if (showingArchive) archived else active
    Column(modifier = modifier.fillMaxWidth()) {
        AiStorageNotice(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        if (showingArchive) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { onShowingArchiveChange(false) },
                    modifier = Modifier.testTag("ai-library-archive-back"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_arrow_back_24),
                        contentDescription = stringResource(R.string.ai_library_archive_back),
                    )
                }
                Text(
                    text = stringResource(R.string.ai_library_archive_entry),
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                text = stringResource(R.string.ai_history),
                modifier = Modifier.padding(horizontal = 24.dp).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!actions.savingEnabled) {
            // Saving off keeps every saved conversation, archived or not, but closed until it is back on.
            Text(
                text = stringResource(R.string.ai_library_saving_off),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(top = 6.dp)
                    .testTag("ai-library-saving-off"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty() && storage.loaded && !storage.readOnly) {
                Text(
                    text = stringResource(
                        if (showingArchive) R.string.ai_library_archive_empty else R.string.ai_history_empty,
                    ),
                    modifier = Modifier.padding(horizontal = 24.dp).testTag("ai-history-empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(if (showingArchive) "ai-library-archived" else "ai-library-conversations"),
                    // Rows reach 12 points past the titles so their highlight frames the text.
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp),
                ) {
                    items(shown, key = { it.id }) { conversation ->
                        AiConversationRow(
                            conversation = conversation,
                            current = conversation.id == actions.currentId,
                            openable = actions.savingEnabled && storage.canEdit,
                            palette = palette,
                            onOpen = {
                                actions.onOpen(conversation.id)
                                onShowingArchiveChange(false)
                                onClose()
                            },
                            onRename = { renamingId = conversation.id },
                            onArchive = { actions.onArchive(conversation.id, !conversation.archived) },
                            onDelete = { deletingId = conversation.id },
                        )
                    }
                }
            }
        }
        if (!showingArchive && archived.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = gymviSubtleBorderColor())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onShowingArchiveChange(true) }
                    .testTag("ai-library-archive-entry")
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_material_symbol_archive_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.ai_library_archive_entry),
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.ai_library_archive_count, archived.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    renamingId?.let { id -> actions.conversations.firstOrNull { it.id == id } }?.let { conversation ->
        AiConversationRenameDialog(
            current = conversation.title,
            onDismiss = { renamingId = null },
            onRename = { title ->
                actions.onRename(conversation.id, title)
                renamingId = null
            },
        )
    }
    deletingId?.let { id -> actions.conversations.firstOrNull { it.id == id } }?.let { conversation ->
        AiConversationDeleteDialog(
            title = conversation.title.ifBlank { stringResource(R.string.ai_library_untitled) },
            onDismiss = { deletingId = null },
            onDelete = {
                actions.onDelete(conversation.id)
                deletingId = null
            },
        )
    }
}

@Composable
private fun AiConversationRow(
    conversation: AiSavedConversationSummary,
    current: Boolean,
    openable: Boolean,
    palette: CombinedPalette,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember(conversation.id) { mutableStateOf(false) }
    val renameLabel = stringResource(R.string.ai_library_rename)
    val archiveLabel = stringResource(
        if (conversation.archived) R.string.ai_library_unarchive else R.string.ai_library_archive,
    )
    val deleteLabel = stringResource(R.string.ai_library_delete)
    val menuLabel = stringResource(R.string.ai_library_menu)
    val states = listOfNotNull(
        stringResource(R.string.ai_library_current).takeIf { current },
        stringResource(R.string.ai_library_closed).takeUnless { openable },
    ).joinToString(", ")
    val haptics = LocalHapticFeedback.current
    // The pressed row stays marked while its menu is open, as in the chat apps the user pointed to.
    val background = when {
        menuOpen -> lerp(palette.quiet, MaterialTheme.colorScheme.onSurface, .06f)
        current -> palette.quiet
        else -> Color.Transparent
    }
    val press = if (openable) {
        Modifier.combinedClickable(
            onClickLabel = stringResource(R.string.ai_library_open),
            onLongClickLabel = menuLabel,
            onClick = onOpen,
            onLongClick = { menuOpen = true },
        )
    } else {
        // Closed while saving is off: a tap does nothing, and the long press still opens the menu.
        Modifier
            .pointerInput(conversation.id) {
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    },
                )
            }
            .semantics { onLongClick(menuLabel) { menuOpen = true; true } }
    }
    Box {
        Text(
            text = conversation.title.ifBlank { stringResource(R.string.ai_library_untitled) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(background)
                .then(press)
                .semantics {
                    if (current) selected = true
                    if (states.isNotEmpty()) stateDescription = states
                    customActions = listOf(
                        CustomAccessibilityAction(renameLabel) { onRename(); true },
                        CustomAccessibilityAction(archiveLabel) { onArchive(); true },
                        CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                    )
                }
                .testTag("ai-conversation-${conversation.id}")
                .padding(horizontal = 12.dp, vertical = 12.dp),
            color = if (openable) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.testTag("ai-conversation-menu"),
        ) {
            DropdownMenuItem(
                text = { Text(renameLabel) },
                modifier = Modifier.testTag("ai-conversation-rename"),
                onClick = {
                    menuOpen = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(archiveLabel) },
                modifier = Modifier.testTag(
                    if (conversation.archived) "ai-conversation-unarchive" else "ai-conversation-archive",
                ),
                onClick = {
                    menuOpen = false
                    onArchive()
                },
            )
            DropdownMenuItem(
                text = { Text(deleteLabel, color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.testTag("ai-conversation-delete"),
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun AiConversationRenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var value by remember(current) { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val trimmed = value.text.trim()
    val canSave = trimmed.isNotEmpty() && trimmed != current
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ai-conversation-rename-dialog"),
        title = { Text(stringResource(R.string.ai_library_rename_title)) },
        text = {
            val focus = remember { FocusRequester() }
            OutlinedTextField(
                value = value,
                onValueChange = { next ->
                    val text = aiConversationTitleInput(next.text)
                    value = if (text == next.text) next else next.copy(text = text, selection = TextRange(text.length))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .testTag("ai-conversation-rename-field"),
                label = { Text(stringResource(R.string.ai_library_rename_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onRename(trimmed) }),
            )
            LaunchedEffect(focus) { focus.requestFocus() }
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(trimmed) },
                enabled = canSave,
                modifier = Modifier.testTag("ai-conversation-rename-confirm"),
            ) {
                Text(stringResource(R.string.ai_library_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("ai-conversation-rename-cancel")) {
                Text(stringResource(R.string.ai_library_cancel))
            }
        },
    )
}

/** Names the one conversation going away and says what goes with it and what stays. */
@Composable
private fun AiConversationDeleteDialog(title: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("ai-conversation-delete-dialog"),
        title = { Text(stringResource(R.string.ai_library_delete_title)) },
        text = {
            Column {
                Text(
                    text = title,
                    modifier = Modifier.testTag("ai-conversation-delete-target"),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.ai_library_delete_body))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.testTag("ai-conversation-delete-confirm"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.ai_library_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("ai-conversation-delete-cancel")) {
                Text(stringResource(R.string.ai_library_cancel))
            }
        },
    )
}
