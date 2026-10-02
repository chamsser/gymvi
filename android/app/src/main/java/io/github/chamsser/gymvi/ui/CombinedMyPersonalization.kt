package io.github.chamsser.gymvi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AiAgeBand
import io.github.chamsser.gymvi.data.AiExerciseExperience
import io.github.chamsser.gymvi.data.AiExerciseGoal
import io.github.chamsser.gymvi.data.AiLocalProfile
import io.github.chamsser.gymvi.data.AiMemoryEntry
import io.github.chamsser.gymvi.data.AiMemoryFacts
import java.text.NumberFormat
import java.util.Locale

/**
 * 신체 정보, which 내 정보's card opens, laid out like the profile screens of 토스 and 당근: the two
 * measurements as large numbers, the chosen items as rows with their value on the right, and each
 * one edited in a sheet that saves it on its own. Every item is optional.
 */
@Composable
internal fun CombinedMyProfilePage(
    actions: AiPersonalizationActions,
    palette: CombinedPalette,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = actions.profile
    val canEdit = LocalAiStorageStatus.current.canEdit
    var editing by rememberSaveable { mutableStateOf<AiProfileItem?>(null) }
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    CombinedMySubPage(
        title = stringResource(R.string.my_profile_title),
        palette = palette,
        onBack = onBack,
        modifier = modifier.testTag("my-profile-page"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CombinedMyMeasureTile(
                label = stringResource(R.string.my_profile_height),
                number = profile.heightCm?.toString(),
                unit = stringResource(R.string.my_profile_height_unit),
                palette = palette,
                enabled = canEdit,
                testTag = "my-profile-height",
                onClick = { editing = AiProfileItem.HEIGHT },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            CombinedMyMeasureTile(
                label = stringResource(R.string.my_profile_weight),
                number = profile.weightKg?.let(::aiDecimalText),
                unit = stringResource(R.string.my_profile_weight_unit),
                palette = palette,
                enabled = canEdit,
                testTag = "my-profile-weight",
                onClick = { editing = AiProfileItem.WEIGHT },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        CombinedMyGroup(
            label = null,
            palette = palette,
            rows = listOf(
                { rowModifier ->
                    CombinedMyValueRow(
                        title = stringResource(R.string.my_profile_age),
                        value = profile.ageBand?.let { stringResource(it.labelRes) },
                        enabled = canEdit,
                        testTag = "my-profile-age",
                        onClick = { editing = AiProfileItem.AGE },
                        modifier = rowModifier,
                    )
                },
                { rowModifier ->
                    CombinedMyValueRow(
                        title = stringResource(R.string.my_profile_experience),
                        value = profile.experience?.let { stringResource(it.labelRes) },
                        enabled = canEdit,
                        testTag = "my-profile-experience",
                        onClick = { editing = AiProfileItem.EXPERIENCE },
                        modifier = rowModifier,
                    )
                },
                { rowModifier ->
                    CombinedMyValueRow(
                        title = stringResource(R.string.my_profile_goal),
                        value = profile.goal?.let { stringResource(it.labelRes) },
                        enabled = canEdit,
                        testTag = "my-profile-goal",
                        onClick = { editing = AiProfileItem.GOAL },
                        modifier = rowModifier,
                    )
                },
            ),
        )
        CombinedMyNote(text = stringResource(R.string.my_profile_note), testTag = "my-profile-note")
        TextButton(
            onClick = { confirmingClear = true },
            enabled = profile != AiLocalProfile() && canEdit,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 24.dp)
                .heightIn(min = 48.dp)
                .testTag("my-profile-clear"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            Text(text = stringResource(R.string.my_profile_clear))
        }
    }
    val save: (AiLocalProfile) -> Unit = { changed ->
        editing = null
        if (changed != profile) actions.onProfileChanged(changed)
    }
    when (editing) {
        AiProfileItem.HEIGHT -> CombinedMyNumberSheet(
            title = stringResource(R.string.my_profile_height),
            initial = profile.heightCm?.toString().orEmpty(),
            unit = stringResource(R.string.my_profile_height_unit),
            error = stringResource(R.string.my_profile_height_error),
            palette = palette,
            keyboardType = KeyboardType.Number,
            filter = { aiDigitsInput(it, maxDigits = 3) },
            accepts = { aiProfileHeightOrNull(it) != null },
            testTag = "my-profile-height",
            onSave = { text -> save(profile.copy(heightCm = aiProfileHeightOrNull(text))) },
            onDismiss = { editing = null },
        )
        AiProfileItem.WEIGHT -> CombinedMyNumberSheet(
            title = stringResource(R.string.my_profile_weight),
            initial = profile.weightKg?.let(::aiDecimalText).orEmpty(),
            unit = stringResource(R.string.my_profile_weight_unit),
            error = stringResource(R.string.my_profile_weight_error),
            palette = palette,
            keyboardType = KeyboardType.Decimal,
            filter = { aiDecimalInput(it, integerDigits = 3, fractionDigits = 2) },
            accepts = { aiProfileWeightOrNull(it) != null },
            testTag = "my-profile-weight",
            onSave = { text -> save(profile.copy(weightKg = aiProfileWeightOrNull(text))) },
            onDismiss = { editing = null },
        )
        AiProfileItem.AGE -> CombinedMyChoiceSheet(
            title = stringResource(R.string.my_profile_age),
            choices = AiAgeBand.entries,
            selected = profile.ageBand,
            palette = palette,
            labelOf = { stringResource(it.labelRes) },
            testTagOf = { "my-profile-age-${it.name}" },
            onChoose = { save(profile.copy(ageBand = it)) },
            onDismiss = { editing = null },
        )
        AiProfileItem.EXPERIENCE -> CombinedMyChoiceSheet(
            title = stringResource(R.string.my_profile_experience),
            choices = AiExerciseExperience.entries,
            selected = profile.experience,
            palette = palette,
            labelOf = { stringResource(it.labelRes) },
            testTagOf = { "my-profile-experience-${it.name}" },
            onChoose = { save(profile.copy(experience = it)) },
            onDismiss = { editing = null },
        )
        AiProfileItem.GOAL -> CombinedMyChoiceSheet(
            title = stringResource(R.string.my_profile_goal),
            choices = AiExerciseGoal.entries,
            selected = profile.goal,
            palette = palette,
            labelOf = { stringResource(it.labelRes) },
            testTagOf = { "my-profile-goal-${it.name}" },
            onChoose = { save(profile.copy(goal = it)) },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            modifier = Modifier.testTag("my-profile-clear-dialog"),
            title = { Text(text = stringResource(R.string.my_profile_clear_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingClear = false
                        actions.onProfileChanged(AiLocalProfile())
                    },
                    enabled = canEdit,
                    modifier = Modifier.testTag("my-profile-clear-confirm"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = stringResource(R.string.my_profile_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }, modifier = Modifier.testTag("my-profile-clear-cancel")) {
                    Text(text = stringResource(R.string.my_personalization_cancel))
                }
            },
        )
    }
}

/** The 신체 정보 item a sheet is editing. */
private enum class AiProfileItem { HEIGHT, WEIGHT, AGE, EXPERIENCE, GOAL }

/** A measurement as a large number with its unit, or 입력하기 while there is none. */
@Composable
private fun CombinedMyMeasureTile(
    label: String,
    number: String?,
    unit: String,
    palette: CombinedPalette,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CombinedMyOuterCorner))
            .background(palette.groupedRow)
            .clickable(
                enabled = enabled,
                onClickLabel = stringResource(R.string.my_profile_edit_item, label),
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(testTag)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(6.dp))
        // The value line keeps the number's height, so entering one moves nothing below.
        val numberLine = with(LocalDensity.current) { MaterialTheme.typography.headlineMedium.lineHeight.toDp() }
        Box(modifier = Modifier.heightIn(min = numberLine), contentAlignment = Alignment.CenterStart) {
            if (number != null) {
                Row(modifier = Modifier.testTag("$testTag-value")) {
                    Text(
                        text = number,
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = unit,
                        modifier = Modifier.alignByBaseline().padding(start = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.my_profile_enter),
                    modifier = Modifier.testTag("$testTag-value"),
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** A row with its title on the left and its value, or 선택하기 while there is none, on the right. */
@Composable
private fun CombinedMyValueRow(
    title: String,
    value: String?,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                enabled = enabled,
                onClickLabel = stringResource(R.string.my_profile_edit_item, title),
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(testTag)
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value ?: stringResource(R.string.my_profile_choose),
            modifier = Modifier.testTag("$testTag-value"),
            color = if (value == null && enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (value == null) FontWeight.SemiBold else null,
            maxLines = 1,
        )
        CombinedMyChevron()
    }
}

/** One choice of a 신체 정보 item, or none; a tap saves it and closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Any> CombinedMyChoiceSheet(
    title: String,
    choices: List<T>,
    selected: T?,
    palette: CombinedPalette,
    labelOf: @Composable (T) -> String,
    testTagOf: (T) -> String,
    onChoose: (T?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.raised,
        modifier = Modifier.testTag("my-profile-sheet"),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).selectableGroup()) {
            CombinedMySheetTitle(title = title, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp))
            choices.forEach { choice ->
                CombinedMyChoiceOption(
                    label = labelOf(choice),
                    selected = choice == selected,
                    testTag = testTagOf(choice),
                    onClick = { onChoose(choice) },
                )
            }
            CombinedMyChoiceOption(
                label = stringResource(R.string.my_profile_unset),
                selected = selected == null,
                testTag = "my-profile-unset",
                muted = true,
                onClick = { onChoose(null) },
            )
        }
    }
}

@Composable
private fun CombinedMySheetTitle(title: String, modifier: Modifier) {
    Text(
        text = title,
        modifier = modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CombinedMyChoiceOption(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    muted: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = when {
                selected -> MaterialTheme.colorScheme.primary
                muted -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else null,
        )
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_material_symbol_check_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * A measurement typed large before its unit. Emptying the field and saving removes it. Its problem
 * shows once a save was tried, then follows each edit; typing a first digit is never met with an error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CombinedMyNumberSheet(
    title: String,
    initial: String,
    unit: String,
    error: String,
    palette: CombinedPalette,
    keyboardType: KeyboardType,
    filter: (String) -> String,
    accepts: (String) -> Boolean,
    testTag: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }
    var revealError by rememberSaveable { mutableStateOf(false) }
    val text = field.text
    val valid = text.isBlank() || accepts(text)
    val shownError = error.takeIf { revealError && !valid }
    val focus = remember { FocusRequester() }
    val canEdit = LocalAiStorageStatus.current.canEdit
    val submit: () -> Unit = { if (valid) onSave(text) else revealError = true }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.raised,
        modifier = Modifier.testTag("my-profile-sheet"),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
            CombinedMySheetTitle(title = title, modifier = Modifier.padding(bottom = 20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = field,
                    onValueChange = { changed ->
                        val kept = filter(changed.text)
                        field = if (kept == changed.text) changed else TextFieldValue(kept, TextRange(kept.length))
                    },
                    enabled = canEdit,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                        .semantics { contentDescription = title }
                        .testTag("$testTag-input"),
                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
                if (text.isNotEmpty() && canEdit) {
                    IconButton(
                        onClick = { field = TextFieldValue("") },
                        modifier = Modifier.testTag("$testTag-erase"),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_material_symbol_close_24),
                            contentDescription = stringResource(R.string.my_profile_erase),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = unit,
                    modifier = Modifier.padding(start = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(if (shownError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
            )
            Text(
                text = shownError.orEmpty(),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .heightIn(min = 20.dp)
                    .then(if (shownError != null) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier)
                    .testTag("$testTag-error"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = submit,
                enabled = canEdit && text != initial,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("$testTag-save"),
                shape = CircleShape,
                elevation = null,
            ) {
                Text(text = stringResource(R.string.my_personalization_save), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    LaunchedEffect(Unit) { if (canEdit) focus.requestFocus() }
}

/**
 * The exercise conditions AI 메모리 has kept, one per conversation. They stay viewable, editable
 * and deletable while AI 메모리 is off; the note says they are not used then.
 */
@Composable
internal fun CombinedMyMemoryPage(
    actions: AiPersonalizationActions,
    memoryEnabled: Boolean,
    palette: CombinedPalette,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingId?.let { id -> actions.memories.firstOrNull { it.id == id } }
    if (editing != null) {
        key(editing.id) {
            CombinedMyMemoryEditor(
                entry = editing,
                palette = palette,
                onSave = { facts ->
                    actions.onMemoryChanged(editing.id, facts)
                    editingId = null
                },
                onDelete = {
                    actions.onMemoryDeleted(editing.id)
                    editingId = null
                },
                onClose = { editingId = null },
                modifier = modifier,
            )
        }
        return
    }
    // A memory deleted elsewhere while open leaves the list showing, not a stale editor waiting.
    LaunchedEffect(Unit) { editingId = null }
    BackHandler(onBack = onBack)
    CombinedMySubPage(
        title = stringResource(R.string.my_memory_manage),
        palette = palette,
        onBack = onBack,
        modifier = modifier.testTag("my-memory-page"),
    ) {
        if (!memoryEnabled) {
            CombinedMyNote(text = stringResource(R.string.my_memory_off), testTag = "my-memory-off")
        }
        if (actions.memories.isEmpty()) {
            CombinedMyNote(text = stringResource(R.string.my_memory_empty), testTag = "my-memory-empty")
        } else {
            CombinedMyGroup(
                label = null,
                palette = palette,
                rows = actions.memories.map { entry ->
                    val row: @Composable (Modifier) -> Unit = { rowModifier ->
                        CombinedMyRow(
                            iconRes = null,
                            alignWithIcons = false,
                            title = entry.title.ifBlank { stringResource(R.string.ai_library_untitled) },
                            value = aiMemorySummary(entry.facts),
                            valueMaxLines = 2,
                            modifier = rowModifier,
                            testTag = "my-memory-${entry.id}",
                            onClick = { editingId = entry.id },
                            onClickLabel = stringResource(R.string.my_memory_edit),
                        )
                    }
                    row
                },
            )
        }
    }
}

@Composable
private fun CombinedMyMemoryEditor(
    entry: AiMemoryEntry,
    palette: CombinedPalette,
    onSave: (AiMemoryFacts) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(stateSaver = AiMemoryDraftSaver) { mutableStateOf(AiMemoryDraft.from(entry.facts)) }
    var revealErrors by rememberSaveable { mutableStateOf(false) }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val facts = draft.toFactsOrNull()
    val changed = facts != entry.facts
    val title = entry.title.ifBlank { stringResource(R.string.ai_library_untitled) }
    val (earliestProblem, latestProblem) = draft.timeProblems
    val leave: () -> Unit = {
        if (changed) {
            confirmingDiscard = true
        } else {
            onClose()
        }
    }
    BackHandler(onBack = leave)
    CombinedMySubPage(
        title = title,
        palette = palette,
        onBack = leave,
        modifier = modifier.testTag("my-memory-editor"),
        bottomBar = {
            Column {
                if (draft.isBlank) {
                    Text(
                        text = stringResource(R.string.my_memory_nothing_left),
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp).testTag("my-memory-nothing-left"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = combinedMyDescriptionStyle(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { confirmingDelete = true },
                        enabled = LocalAiStorageStatus.current.canEdit,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("my-memory-delete"),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(text = stringResource(R.string.my_memory_delete))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    CombinedMySaveButton(enabled = changed && !draft.isBlank, testTag = "my-memory-save") {
                        if (facts == null) revealErrors = true else onSave(facts)
                    }
                }
            }
        },
    ) {
        CombinedMyChoiceGroup(
            label = stringResource(R.string.my_memory_categories),
            palette = palette,
            choices = AiMemoryCategoryChoices,
            isSelected = { it.code in draft.categories },
            onToggle = { draft = draft.copy(categories = draft.categories.toggled(it.code)) },
            labelOf = { stringResource(it.label) },
            testTagOf = { "my-memory-category-${it.code}" },
        )
        CombinedMyChoiceGroup(
            label = stringResource(R.string.my_memory_weekdays),
            palette = palette,
            choices = AiMemoryWeekdayChoices,
            isSelected = { it.code in draft.weekdays },
            onToggle = { draft = draft.copy(weekdays = draft.weekdays.toggled(it.code)) },
            labelOf = { stringResource(it.label) },
            spokenLabelOf = { stringResource(it.spokenLabel) },
            testTagOf = { "my-memory-weekday-${it.code}" },
        )
        CombinedMyGroup(
            label = stringResource(R.string.my_memory_start_time),
            palette = palette,
            rows = listOf { rowModifier ->
                CombinedMyFields(modifier = rowModifier) {
                    CombinedMyField(
                        value = draft.earliestStart,
                        onValueChange = { draft = draft.copy(earliestStart = aiDigitsInput(it, maxDigits = 4)) },
                        label = stringResource(R.string.my_memory_earliest),
                        error = earliestProblem?.let { aiMemoryTimeMessage(it) },
                        revealError = revealErrors,
                        keyboardType = KeyboardType.Number,
                        visualTransformation = AiMemoryTimeTransformation,
                        testTag = "my-memory-earliest",
                    )
                    CombinedMyField(
                        value = draft.latestStart,
                        onValueChange = { draft = draft.copy(latestStart = aiDigitsInput(it, maxDigits = 4)) },
                        label = stringResource(R.string.my_memory_latest),
                        error = latestProblem?.let { aiMemoryTimeMessage(it) },
                        revealError = revealErrors,
                        keyboardType = KeyboardType.Number,
                        visualTransformation = AiMemoryTimeTransformation,
                        testTag = "my-memory-latest",
                    )
                }
            },
        )
        CombinedMyGroup(
            label = stringResource(R.string.my_memory_limits),
            palette = palette,
            rows = listOf { rowModifier ->
                CombinedMyFields(modifier = rowModifier) {
                    CombinedMyField(
                        value = draft.maxPriceWon,
                        onValueChange = { draft = draft.copy(maxPriceWon = aiDigitsInput(it, maxDigits = 8)) },
                        label = stringResource(R.string.my_memory_price),
                        unit = stringResource(R.string.my_memory_price_unit),
                        error = stringResource(R.string.my_memory_price_error).takeIf { draft.price is AiDraftField.Invalid },
                        revealError = revealErrors,
                        keyboardType = KeyboardType.Number,
                        visualTransformation = AiMemoryWonTransformation,
                        testTag = "my-memory-price",
                    )
                    CombinedMyField(
                        value = draft.maxDistanceKm,
                        onValueChange = {
                            draft = draft.copy(maxDistanceKm = aiDecimalInput(it, integerDigits = 3, fractionDigits = 3))
                        },
                        label = stringResource(R.string.my_memory_distance),
                        unit = stringResource(R.string.my_memory_distance_unit),
                        error = stringResource(R.string.my_memory_distance_error).takeIf { draft.distance is AiDraftField.Invalid },
                        revealError = revealErrors,
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                        testTag = "my-memory-distance",
                    )
                }
            },
        )
        CombinedMyGroup(
            label = null,
            palette = palette,
            rows = listOf { rowModifier ->
                CombinedMySwitchRow(
                    title = stringResource(R.string.my_memory_beginner),
                    checked = draft.beginner,
                    onCheckedChange = { draft = draft.copy(beginner = it) },
                    testTag = "my-memory-beginner",
                    modifier = rowModifier,
                )
            },
        )
    }
    if (confirmingDiscard) {
        CombinedMyDiscardDialog(
            onDiscard = {
                confirmingDiscard = false
                onClose()
            },
            onKeep = { confirmingDiscard = false },
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            modifier = Modifier.testTag("my-memory-delete-dialog"),
            title = { Text(text = stringResource(R.string.my_memory_delete_title)) },
            text = {
                Column {
                    // Names the memory, so the one being deleted is never in doubt.
                    Text(
                        text = title,
                        modifier = Modifier.testTag("my-memory-delete-target"),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.my_memory_delete_body))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("my-memory-delete-confirm"),
                    enabled = LocalAiStorageStatus.current.canEdit,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = stringResource(R.string.my_memory_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }, modifier = Modifier.testTag("my-memory-delete-cancel")) {
                    Text(text = stringResource(R.string.my_personalization_cancel))
                }
            },
        )
    }
}

/** The profile on the card in 내 정보: the chosen items, then the measurements with their units. */
@Composable
internal fun aiProfileSummary(profile: AiLocalProfile): String = listOfNotNull(
    profile.ageBand?.let { stringResource(it.labelRes) },
    profile.experience?.let { stringResource(it.labelRes) },
    profile.goal?.let { stringResource(it.labelRes) },
    profile.heightCm?.let { stringResource(R.string.my_profile_height_value, it) },
    profile.weightKg?.let { stringResource(R.string.my_profile_weight_value, aiDecimalText(it)) },
).joinToString(", ")

/** One memory as a line: categories, weekdays, start times, price, distance and beginner. */
@Composable
internal fun aiMemorySummary(facts: AiMemoryFacts): String {
    val categories = AiMemoryCategoryChoices.filter { it.code in facts.categories }.map { stringResource(it.label) }
    val weekdays = AiMemoryWeekdayChoices.filter { it.code in facts.weekdays }.map { stringResource(it.label) }
    val earliest = facts.earliestStart
    val latest = facts.latestStart
    return listOfNotNull(
        categories.joinToString(", ").ifEmpty { null },
        weekdays.joinToString(", ").ifEmpty { null },
        if (earliest != null && latest != null) {
            stringResource(R.string.my_memory_summary_between, earliest.take(5), latest.take(5))
        } else {
            null
        },
        facts.maxPriceWon?.let {
            stringResource(R.string.my_memory_summary_price, NumberFormat.getIntegerInstance(Locale.KOREA).format(it))
        },
        facts.maxDistanceMeters?.let { stringResource(R.string.my_memory_summary_distance, aiKilometersText(it)) },
        if (facts.beginner) stringResource(R.string.my_memory_summary_beginner) else null,
    ).joinToString(" / ")
}

/**
 * A page that 내 정보 opens over itself: the same grey page and fading header, and an optional
 * bar of actions under the content. The whole page sits above the keyboard, so a focused field
 * scrolls into the space between the header and that bar instead of behind either.
 */
@Composable
internal fun CombinedMySubPage(
    title: String,
    palette: CombinedPalette,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Saving or leaving takes the keyboard along instead of leaving it over 내 정보.
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(keyboard) { onDispose { keyboard?.hide() } }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.groupedPage)
            .imePadding(),
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .then(if (bottomBar == null) Modifier.navigationBarsPadding() else Modifier)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = CombinedHeaderHeight + 4.dp,
                        bottom = if (bottomBar == null) 32.dp else 16.dp,
                    ),
            ) {
                AiStorageNotice(Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                content()
            }
            CombinedMyHeader(
                title = title,
                palette = palette,
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter),
                backTestTag = "my-subpage-back",
                titleTestTag = "my-subpage-title",
            )
        }
        bottomBar?.let { bar ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                bar()
            }
        }
    }
}

/** A sentence under the header or between groups about what the page's data does. */
@Composable
private fun CombinedMyNote(text: String, testTag: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp)
            .testTag(testTag),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = combinedMyDescriptionStyle(),
    )
}

/** A labelled group of chips; tapping a chosen chip again clears it. */
@Composable
private fun <T> CombinedMyChoiceGroup(
    label: String,
    palette: CombinedPalette,
    choices: List<T>,
    isSelected: (T) -> Boolean,
    onToggle: (T) -> Unit,
    labelOf: @Composable (T) -> String,
    testTagOf: (T) -> String,
    spokenLabelOf: (@Composable (T) -> String)? = null,
) {
    CombinedMyGroup(
        label = label,
        palette = palette,
        rows = listOf { rowModifier ->
            FlowRow(
                modifier = rowModifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                choices.forEach { choice ->
                    val selected = isSelected(choice)
                    val spoken = spokenLabelOf?.invoke(choice)
                    FilterChip(
                        selected = selected,
                        enabled = LocalAiStorageStatus.current.canEdit,
                        onClick = { onToggle(choice) },
                        label = { Text(text = labelOf(choice)) },
                        modifier = Modifier
                            .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
                            .testTag(testTagOf(choice)),
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    painter = painterResource(R.drawable.ic_material_symbol_check_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                        shape = CircleShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    )
}

@Composable
private fun CombinedMyFields(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/**
 * A number field whose problem shows once the field has been left or a save was tried, then
 * follows each edit; typing a first digit is never met with an error.
 */
@Composable
private fun CombinedMyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    revealError: Boolean,
    keyboardType: KeyboardType,
    testTag: String,
    unit: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Next,
) {
    var focusedOnce by rememberSaveable { mutableStateOf(false) }
    var leftOnce by rememberSaveable { mutableStateOf(false) }
    val shownError = error?.takeIf { revealError || leftOnce }
    OutlinedTextField(
        value = value,
        enabled = LocalAiStorageStatus.current.canEdit,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.isFocused) focusedOnce = true else if (focusedOnce) leftOnce = true
            }
            .testTag(testTag),
        label = { Text(text = label) },
        suffix = unit?.let { { Text(text = it) } },
        supportingText = shownError?.let { { Text(text = it) } },
        isError = shownError != null,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        singleLine = true,
    )
}

@Composable
private fun CombinedMySwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, enabled = LocalAiStorageStatus.current.canEdit,
                onValueChange = onCheckedChange)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = LocalAiStorageStatus.current.canEdit,
            modifier = Modifier.testTag("$testTag-switch"))
    }
}

@Composable
private fun CombinedMySaveButton(enabled: Boolean, testTag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).testTag(testTag),
        enabled = enabled && LocalAiStorageStatus.current.canEdit,
        shape = CircleShape,
        elevation = null,
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(text = stringResource(R.string.my_personalization_save), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CombinedMyDiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        modifier = Modifier.testTag("my-discard-dialog"),
        title = { Text(text = stringResource(R.string.my_personalization_discard_title)) },
        confirmButton = {
            TextButton(onClick = onDiscard, modifier = Modifier.testTag("my-discard-confirm")) {
                Text(text = stringResource(R.string.my_personalization_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep, modifier = Modifier.testTag("my-discard-keep")) {
                Text(text = stringResource(R.string.my_personalization_discard_keep))
            }
        },
    )
}

@Composable
private fun aiMemoryTimeMessage(problem: AiMemoryTimeProblem): String = stringResource(
    when (problem) {
        AiMemoryTimeProblem.FORMAT -> R.string.my_memory_time_error
        AiMemoryTimeProblem.PAIR -> R.string.my_memory_time_pair_error
        AiMemoryTimeProblem.ORDER -> R.string.my_memory_time_order_error
    },
)

private fun Set<String>.toggled(code: String): Set<String> = if (code in this) this - code else this + code

/**
 * Shows a field's digits through [format] while the field keeps the digits alone, so the cursor
 * moves over separators the person never typed.
 */
private class AiDigitsTransformation(private val format: (String) -> String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val shown = format(text.text)
        return TransformedText(
            AnnotatedString(shown),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    var digits = 0
                    shown.forEachIndexed { index, char ->
                        if (digits == offset) return index
                        if (char in '0'..'9') digits++
                    }
                    return shown.length
                }

                override fun transformedToOriginal(offset: Int): Int = shown.take(offset).count { it in '0'..'9' }
            },
        )
    }
}

private val AiMemoryTimeTransformation = AiDigitsTransformation(::aiMemoryTimeShown)
private val AiMemoryWonTransformation = AiDigitsTransformation(::aiGroupedDigits)

private val AiMemoryDraftSaver = listSaver<AiMemoryDraft, String>(
    save = { draft ->
        listOf(
            draft.categories.joinToString(","),
            draft.weekdays.joinToString(","),
            draft.earliestStart,
            draft.latestStart,
            draft.maxPriceWon,
            draft.maxDistanceKm,
            draft.beginner.toString(),
        )
    },
    restore = { saved ->
        AiMemoryDraft(
            categories = saved[0].codes(),
            weekdays = saved[1].codes(),
            earliestStart = saved[2],
            latestStart = saved[3],
            maxPriceWon = saved[4],
            maxDistanceKm = saved[5],
            beginner = saved[6].toBoolean(),
        )
    },
)

private fun String.codes(): Set<String> = split(',').filter(String::isNotEmpty).toSet()
