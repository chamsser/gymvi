package io.github.chamsser.gymvi.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chamsser.gymvi.BuildConfig
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AiPreferences
import io.github.chamsser.gymvi.data.AiTurnCard
import io.github.chamsser.gymvi.data.PublicDataSource
import io.github.chamsser.gymvi.data.RecentFacility
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch

internal enum class AiConversationPhase {
    INITIAL,
    AWAITING_FIRST_RESPONSE,
    ACTIVE,
}

internal fun resolveAiConversationPhase(
    hasSubmittedPrompt: Boolean,
    hasAssistantMessage: Boolean,
): AiConversationPhase = when {
    hasAssistantMessage -> AiConversationPhase.ACTIVE
    hasSubmittedPrompt -> AiConversationPhase.AWAITING_FIRST_RESPONSE
    else -> AiConversationPhase.INITIAL
}

@Composable
internal fun AiModeScreen(
    content: LiveUiAiContent? = null,
    inputValue: TextFieldValue,
    onInputValueChange: (TextFieldValue) -> Unit,
    selectedFacilityName: String? = null,
    onClearSelectedFacility: () -> Unit = {},
    conversationState: AiConversationUiState = AiConversationUiState(),
    onBackToMap: () -> Unit,
    onPromptSubmitted: (String) -> Unit = {},
    onNewConversation: () -> Unit = {},
    onShowOnMap: (AiTurnCard) -> Unit = {},
    onDecide: (AiTurnCard) -> Unit = {},
    onPreviewRoute: (AiTurnCard) -> Unit = {},
    preferences: AiPreferences,
    onPreferencesChanged: (AiPreferences) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The draft is hoisted by the owner so it survives leaving the AI screen, and the open settings
    // page lives above the design branch, so switching designs never discards either.
    val draft = inputValue
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val design = LocalNativeDesign.current
    if (design.variant == NativeDesignVariant.COMBINED) {
        // AI settings live in 내 정보 here, so this page has no settings state of its own.
        CombinedAiModeScreen(
            content = content,
            selectedFacilityName = selectedFacilityName,
            conversationState = conversationState,
            draft = draft,
            onDraftChange = onInputValueChange,
            onPromptSubmitted = onPromptSubmitted,
            onNewConversation = onNewConversation,
            onClearSelectedFacility = onClearSelectedFacility,
            onShowOnMap = onShowOnMap,
            onDecide = onDecide,
            onPreviewRoute = onPreviewRoute,
            modifier = modifier,
        )
        return
    }
    if (!design.isOriginal) {
        CandidateAiModeScreen(
            design = design,
            content = content,
            selectedFacilityName = selectedFacilityName,
            conversationState = conversationState,
            draft = draft,
            onDraftChange = onInputValueChange,
            showSettings = showSettings,
            onShowSettingsChange = { showSettings = it },
            onPromptSubmitted = onPromptSubmitted,
            onNewConversation = onNewConversation,
            onClearSelectedFacility = onClearSelectedFacility,
            onShowOnMap = onShowOnMap,
            onDecide = onDecide,
            onPreviewRoute = onPreviewRoute,
            preferences = preferences,
            onPreferencesChanged = onPreferencesChanged,
            modifier = modifier,
        )
        return
    }
    val conversationPhase = resolveAiConversationPhase(
        hasSubmittedPrompt = conversationState.messages.any { it.role == AiMessageRole.USER },
        hasAssistantMessage = conversationState.messages.any { it.role == AiMessageRole.ASSISTANT },
    )
    val historyDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val examples = content?.examples ?: listOf(
        stringResource(R.string.ai_example_nearby),
        stringResource(R.string.ai_example_new_exercise),
        stringResource(R.string.ai_example_now),
    )
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && historyDrawerState.isOpen) {
        coroutineScope.launch { historyDrawerState.close() }
    }

    if (showSettings) {
        AiSettingsScreen(
            preferences = preferences,
            onPreferencesChanged = onPreferencesChanged,
            onClose = { showSettings = false },
            modifier = modifier,
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = historyDrawerState,
        gesturesEnabled = historyDrawerState.isOpen,
        drawerContent = {
            AiHistoryDrawer(
                onNewConversation = {
                    onInputValueChange(TextFieldValue())
                    onNewConversation()
                    coroutineScope.launch { historyDrawerState.close() }
                },
                onClose = { coroutineScope.launch { historyDrawerState.close() } },
            )
        },
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag("ai-mode-screen")
                .background(aiConversationBackground(conversationPhase))
                .padding(top = 20.dp, bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBackToMap,
                    modifier = Modifier
                        .offset(x = (-16).dp)
                        .testTag("ai-back-button"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_arrow_back_24),
                        contentDescription = stringResource(R.string.ai_back_to_map),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = content?.title ?: stringResource(R.string.ai_screen_title),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai-screen-title"),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
                if (conversationPhase != AiConversationPhase.INITIAL) {
                    IconButton(
                        onClick = {
                            onInputValueChange(TextFieldValue())
                            onNewConversation()
                        },
                        modifier = Modifier.testTag("ai-top-new-conversation"),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_material_symbol_edit_square_24),
                            contentDescription = stringResource(R.string.ai_new_conversation),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                IconButton(
                    onClick = { coroutineScope.launch { historyDrawerState.open() } },
                    modifier = Modifier.testTag("ai-history-button"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_history_24),
                        contentDescription = stringResource(R.string.ai_history),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.testTag("ai-settings-button"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_settings_24),
                        contentDescription = stringResource(R.string.ai_settings),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (conversationState.messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                        .padding(top = if (content?.contentPosition == LiveUiContentPosition.TOP) 32.dp else 0.dp)
                        .offset(
                            y = if (content?.contentPosition == LiveUiContentPosition.TOP) {
                                0.dp
                            } else {
                                (-80).dp
                            },
                        )
                        .testTag("ai-content"),
                    verticalArrangement = if (content?.contentPosition == LiveUiContentPosition.TOP) {
                        Arrangement.Top
                    } else {
                        Arrangement.Center
                    },
                ) {
                    Text(
                        text = content?.headline ?: selectedFacilityName?.let { facilityName ->
                            stringResource(R.string.ai_selected_facility_title, facilityName)
                        } ?: stringResource(R.string.ai_zero_title),
                        modifier = Modifier.testTag("ai-headline"),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 26.sp,
                            lineHeight = 34.sp,
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    examples.forEachIndexed { index, example ->
                        AiExample(
                            text = example,
                            testTag = "ai-example-$index",
                            onClick = {
                                onInputValueChange(
                                    TextFieldValue(
                                        text = example,
                                        selection = TextRange(example.length),
                                    ),
                                )
                            },
                        )
                    }
                }
            } else {
                AiConversationContent(
                    state = conversationState,
                    onShowOnMap = onShowOnMap,
                    onDecide = onDecide,
                    onPreviewRoute = onPreviewRoute,
                    // Only the active phase sits on a solid color; the waiting gradient keeps the mask.
                    fadeBackground = MaterialTheme.colorScheme.background
                        .takeIf { conversationPhase == AiConversationPhase.ACTIVE },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("ai-content"),
                )
            }
            selectedFacilityName?.let { facilityName ->
                AiSelectedFacilityRow(
                    facilityName = facilityName,
                    onClear = onClearSelectedFacility,
                    textStyle = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                )
            }
            AiComposer(
                value = draft,
                onValueChange = onInputValueChange,
                isLoading = conversationState.isLoading,
                onSubmit = { message ->
                    onPromptSubmitted(message)
                    onInputValueChange(TextFieldValue())
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** The facility the next question is about, with a way to stop asking about it. */
@Composable
private fun AiSelectedFacilityRow(
    facilityName: String,
    onClear: () -> Unit,
    textStyle: TextStyle,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ai_selected_facility_label, facilityName),
            modifier = Modifier
                .weight(1f)
                .testTag("ai-selected-facility"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = onClear,
            modifier = Modifier.testTag("ai-selected-facility-clear"),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_material_symbol_close_24),
                contentDescription = stringResource(R.string.native_ai_clear_selected_facility),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The AI history drawer. With a conversation library provided it lists the saved conversations
 * (AiConversationLibraryDrawer.kt); without one it keeps the empty history it always showed.
 */
@Composable
internal fun AiHistoryDrawer(
    onNewConversation: () -> Unit,
    onClose: () -> Unit,
) {
    val library = LocalAiLibraryActions.current
    var showingArchive by rememberSaveable { mutableStateOf(false) }
    ModalDrawerSheet(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .testTag("ai-history-drawer"),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.ai_screen_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("ai-history-close"),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_material_symbol_close_24),
                            contentDescription = stringResource(R.string.ai_history_close),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showingArchive = false
                            onNewConversation()
                        }
                        .testTag("ai-new-conversation")
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_chat_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.ai_new_conversation),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                HorizontalDivider(color = gymviSubtleBorderColor())
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (library == null) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = stringResource(R.string.ai_history),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.ai_history_empty),
                        modifier = Modifier.testTag("ai-history-empty"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                AiConversationLibrary(
                    actions = library,
                    showingArchive = showingArchive,
                    onShowingArchiveChange = { showingArchive = it },
                    onClose = onClose,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AiSettingsScreen(
    preferences: AiPreferences,
    onPreferencesChanged: (AiPreferences) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("ai-settings-screen")
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ai_settings),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("ai-settings-close"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_material_symbol_close_24),
                    contentDescription = stringResource(R.string.ai_settings_close),
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.ai_settings_personalization_section),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        AiSettingToggleRow(
            title = stringResource(R.string.ai_settings_use_memory),
            description = stringResource(R.string.ai_settings_use_memory_description),
            checked = preferences.useAiMemory,
            onCheckedChange = { checked ->
                onPreferencesChanged(preferences.copy(useAiMemory = checked))
            },
            testTag = "ai-setting-memory",
        )
        HorizontalDivider(color = gymviSubtleBorderColor())
        AiSettingToggleRow(
            title = stringResource(R.string.ai_settings_use_body_information),
            description = stringResource(R.string.ai_settings_use_body_information_description),
            checked = preferences.useBodyInformation,
            onCheckedChange = { checked ->
                onPreferencesChanged(preferences.copy(useBodyInformation = checked))
            },
            testTag = "ai-setting-body-information",
        )
        HorizontalDivider(color = gymviSubtleBorderColor())
        AiSettingToggleRow(
            title = stringResource(R.string.ai_settings_use_region),
            description = stringResource(R.string.ai_settings_use_region_description),
            checked = preferences.useApproximateRegion,
            onCheckedChange = { checked ->
                onPreferencesChanged(preferences.copy(useApproximateRegion = checked))
            },
            testTag = "ai-setting-approximate-region",
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.ai_settings_conversation_section),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        AiSettingToggleRow(
            title = stringResource(R.string.ai_settings_save_history),
            description = stringResource(R.string.ai_settings_save_history_description),
            checked = preferences.saveConversationHistory,
            onCheckedChange = { checked ->
                onPreferencesChanged(preferences.copy(saveConversationHistory = checked))
            },
            testTag = "ai-setting-save-history",
        )
    }
}

@Composable
private fun AiSettingToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    tokens: CandidateScreenTokens? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .testTag(testTag)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = tokens?.body ?: MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = tokens?.support ?: MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            modifier = Modifier.testTag("$testTag-switch"),
        )
    }
}

@Composable
private fun aiConversationBackground(phase: AiConversationPhase): Brush = when (phase) {
    AiConversationPhase.INITIAL -> Brush.verticalGradient(
        0.00f to MaterialTheme.colorScheme.background,
        0.56f to MaterialTheme.colorScheme.background,
        0.82f to Color(0xFFF2F6FF),
        1.00f to Color(0xFFDCEAFF),
    )

    AiConversationPhase.AWAITING_FIRST_RESPONSE -> Brush.verticalGradient(
        0.00f to Color(0xFFB9DAFF),
        0.34f to Color(0xFFE7F2FF),
        0.72f to MaterialTheme.colorScheme.background,
        1.00f to MaterialTheme.colorScheme.background,
    )

    AiConversationPhase.ACTIVE -> SolidColor(MaterialTheme.colorScheme.background)
}

@Composable
private fun AiConversationContent(
    state: AiConversationUiState,
    onShowOnMap: (AiTurnCard) -> Unit,
    onDecide: (AiTurnCard) -> Unit,
    onPreviewRoute: (AiTurnCard) -> Unit,
    modifier: Modifier = Modifier,
    candidate: CandidateAiSpec? = null,
    fadeBackground: Color? = null,
) {
    val listState = rememberLazyListState()
    val trailingItems = (if (state.isLoading) 1 else 0) + (if (state.errorCode != null) 1 else 0)
    val lastIndex = state.messages.size + trailingItems - 1
    LaunchedEffect(state.messages.size, state.isLoading, state.errorCode) {
        listState.scrollToConversationEnd(lastIndex)
    }
    // When the keyboard opens the viewport shrinks from the bottom; keep the latest message
    // above the composer instead of leaving it hidden behind the keyboard.
    LaunchedEffect(listState) {
        var previousHeight = 0
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .collect { height ->
                if (previousHeight > 0 && height < previousHeight) {
                    listState.scrollToConversationEnd(listState.layoutInfo.totalItemsCount - 1)
                }
                previousHeight = height
            }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.conversationTopFade(listState, fadeBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 20.dp,
            vertical = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.messages, key = AiConversationMessage::id) { message ->
            if (candidate == null) {
                AiMessageBlock(
                    message = message,
                    onShowOnMap = onShowOnMap,
                    onDecide = onDecide,
                    onPreviewRoute = onPreviewRoute,
                )
            } else {
                val fadeIn = candidate.design.durationMillis
                    .takeIf { it > 0 }
                    ?.let { tween<Float>(it) }
                CandidateMessageBlock(
                    message = message,
                    spec = candidate,
                    onShowOnMap = onShowOnMap,
                    onDecide = onDecide,
                    onPreviewRoute = onPreviewRoute,
                    modifier = Modifier.animateItem(
                        fadeInSpec = fadeIn,
                        placementSpec = null,
                        fadeOutSpec = null,
                    ),
                )
            }
        }
        if (state.isLoading) {
            item(key = "ai-loading") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai-loading"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.ai_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        state.errorCode?.let { errorCode ->
            item(key = "ai-error") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai-error"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = aiErrorText(errorCode),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private suspend fun LazyListState.scrollToConversationEnd(lastIndex: Int) {
    if (lastIndex < 0) return
    scrollToItem(lastIndex)
    // A tall final reply can exceed the viewport; scroll past its top so its end is visible.
    scrollBy(ConversationEndScrollDistance)
}

private const val ConversationEndScrollDistance = 100_000f

private val ConversationTopFadeHeight = 28.dp

/**
 * How strongly the conversation fades under the tool header, from 0 at rest to 1 once the list has
 * scrolled by the fade height. Following the scroll distance avoids a sudden fade on the first pixel.
 */
internal fun conversationTopFadeFraction(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    fadeHeightPx: Float,
): Float = when {
    fadeHeightPx <= 0f -> 0f
    firstVisibleItemIndex > 0 -> 1f
    else -> (firstVisibleItemScrollOffset / fadeHeightPx).coerceIn(0f, 1f)
}

/**
 * Dissolves the top of the conversation under the tool header instead of cutting messages off.
 * On a solid [background] it paints that color fading to transparent over the list, which looks the
 * same as a mask without a list-sized offscreen layer. Without one (the gradient behind the waiting
 * phase) it masks the list itself, and that layer exists only while the fade shows. The scroll
 * position is read only in the layer and draw phases, so scrolling never recomposes. No blur.
 */
private fun Modifier.conversationTopFade(listState: LazyListState, background: Color?): Modifier =
    if (background != null) conversationTopScrim(listState, background) else conversationTopMask(listState)

private fun Modifier.conversationTopScrim(listState: LazyListState, background: Color): Modifier =
    drawWithContent {
        drawContent()
        val fadeHeightPx = ConversationTopFadeHeight.toPx()
        val fraction = conversationTopFadeFraction(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
            fadeHeightPx,
        )
        if (fraction > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to background.copy(alpha = background.alpha * fraction),
                    1f to background.copy(alpha = 0f),
                    startY = 0f,
                    endY = fadeHeightPx,
                ),
                size = Size(size.width, fadeHeightPx),
            )
        }
    }

private fun Modifier.conversationTopMask(listState: LazyListState): Modifier = this
    .graphicsLayer {
        val fraction = conversationTopFadeFraction(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
            ConversationTopFadeHeight.toPx(),
        )
        compositingStrategy = if (fraction > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithContent {
        drawContent()
        val fadeHeightPx = ConversationTopFadeHeight.toPx()
        val fraction = conversationTopFadeFraction(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
            fadeHeightPx,
        )
        if (fraction > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 1f - fraction),
                    1f to Color.Black,
                    startY = 0f,
                    endY = fadeHeightPx,
                ),
                size = Size(size.width, fadeHeightPx),
                blendMode = BlendMode.DstIn,
            )
        }
    }

@Composable
private fun AiMessageBlock(
    message: AiConversationMessage,
    onShowOnMap: (AiTurnCard) -> Unit,
    onDecide: (AiTurnCard) -> Unit,
    onPreviewRoute: (AiTurnCard) -> Unit,
) {
    if (message.role == AiMessageRole.USER) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .testTag("ai-user-message"),
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai-assistant-message"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (message.restartedConversation) {
            Text(
                text = stringResource(R.string.ai_conversation_restarted_notice),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag("ai-restarted-notice"),
            )
        }
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
        )
        message.cards.forEach { card ->
            AiUsageOptionCard(
                card = card,
                onShowOnMap = { onShowOnMap(card) },
                onDecide = { onDecide(card) },
                onPreviewRoute = { onPreviewRoute(card) },
            )
        }
        if (message.exerciseContents.isNotEmpty()) {
            ExerciseContentSection(message.exerciseContents, compact = true)
        }
    }
}

@Composable
private fun AiUsageOptionCard(
    card: AiTurnCard,
    onShowOnMap: () -> Unit,
    onDecide: () -> Unit,
    onPreviewRoute: () -> Unit,
) {
    val option = card.option
    val time = option.operatorTime?.let { "${it.startTime}~${it.endTime}" }
        ?: option.sourceTimeValue
        ?: stringResource(R.string.usage_option_time_unknown)
    val weekdays = formatUsageOptionWeekdays(option.weekdays)
    val price = formatUsageOptionPrice(option.priceWon)
    val application = stringResource(usageOptionApplicationLabel(option.programApplication, card = true))
    val applicationColor = when (option.programApplication.state) {
        "AVAILABLE" -> MaterialTheme.colorScheme.primary
        "CLOSED" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val matched = card.matchedConditions.mapNotNull(::aiConditionLabel).map { stringResource(it) }
    val unmet = card.unmetPreferredConditions.mapNotNull(::aiConditionLabel).map { stringResource(it) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai-option-card-${option.usageOptionId}"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, gymviSubtleBorderColor()),
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = option.programName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = card.facilityName,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                card.straightLineDistanceMeters?.let(::formatAiDistance)?.let { distance ->
                    Text(
                        text = distance,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            // The schedule decides whether an option fits the week, so it leads the facts.
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                weekdays?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = price ?: stringResource(R.string.usage_option_price_unknown),
                    color = if (price == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = application,
                    color = applicationColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (matched.isNotEmpty() || unmet.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .testTag("ai-card-conditions-${option.usageOptionId}"),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (matched.isNotEmpty()) {
                        AiConditionLine(stringResource(R.string.ai_card_matched_conditions), matched.joinToString(", "))
                    }
                    if (unmet.isNotEmpty()) {
                        AiConditionLine(stringResource(R.string.ai_card_unmet_conditions), unmet.joinToString(", "))
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onShowOnMap,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai-card-map-${option.usageOptionId}"),
                ) {
                    Text(stringResource(R.string.ai_show_on_map), maxLines = 1)
                }
                TextButton(
                    onClick = onPreviewRoute,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai-card-route-${option.usageOptionId}"),
                ) {
                    Text(stringResource(R.string.route_preview_title), maxLines = 1)
                }
            }
            FilledTonalButton(
                onClick = onDecide,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("ai-card-decide-${option.usageOptionId}"),
            ) {
                Text(stringResource(R.string.usage_option_decide))
            }
        }
    }
}

@Composable
internal fun AiConditionLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@StringRes
private fun aiConditionLabel(code: String): Int? = when (code) {
    "CATEGORY" -> R.string.ai_condition_category
    "WEEKDAYS" -> R.string.ai_condition_weekdays
    "START_TIME" -> R.string.ai_condition_start_time
    "MAX_PRICE_WON" -> R.string.ai_condition_price
    "MAX_DISTANCE_METERS" -> R.string.ai_condition_distance
    "TARGET_GROUPS" -> R.string.ai_condition_target
    "BEGINNER" -> R.string.ai_condition_beginner
    "APPLICATION_AVAILABLE" -> R.string.ai_condition_application
    else -> null
}

@Composable
internal fun aiErrorText(code: String): String = when (code) {
    "AI_REFERENCES_CHANGED" -> stringResource(R.string.ai_references_changed)
    "NETWORK_UNAVAILABLE" -> stringResource(R.string.ai_network_error)
    "API_NOT_CONFIGURED" -> stringResource(R.string.ai_not_configured)
    else -> stringResource(R.string.ai_response_error)
}

private fun formatAiDistance(distanceMeters: Int): String = if (distanceMeters < 1_000) {
    "${distanceMeters}m"
} else {
    String.format(java.util.Locale.KOREAN, "%.1fkm", distanceMeters / 1_000.0)
}

@Composable
private fun AiExample(
    text: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Text(
        text = "\"$text\"",
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 15.sp,
            lineHeight = 22.sp,
        ),
        textDecoration = TextDecoration.Underline,
    )
}

@Composable
private fun AiComposer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    tokens: CandidateScreenTokens? = null,
) {
    val placeholder = stringResource(R.string.ai_composer_placeholder)
    val tooLong = isAiPromptTooLong(value.text)
    val canSubmit = value.text.isNotBlank() && !isLoading && !tooLong
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .testTag("ai-composer"),
        shape = tokens?.composerShape ?: RoundedCornerShape(30.dp),
        color = tokens?.composerColor ?: MaterialTheme.colorScheme.surface,
        border = tokens?.composerBorder,
        tonalElevation = if (tokens == null) 1.dp else 0.dp,
        shadowElevation = tokens?.composerElevation ?: 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .testTag("ai-composer-input"),
                    textStyle = (tokens?.body ?: MaterialTheme.typography.bodyLarge).copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    minLines = 1,
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.text.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = tokens?.body ?: MaterialTheme.typography.bodyLarge,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (tooLong) {
                    // Sending would be refused, so the draft stays as it is and says why.
                    AiPromptLengthNotice(
                        text = value.text,
                        modifier = Modifier.padding(bottom = 6.dp),
                        style = tokens?.meta ?: MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = if (canSubmit) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                IconButton(
                    onClick = { onSubmit(value.text.trim()) },
                    enabled = canSubmit,
                    modifier = Modifier.testTag("ai-send-button"),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_arrow_upward_24),
                        contentDescription = stringResource(R.string.ai_send),
                        tint = if (canSubmit) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MyModeScreen(
    content: LiveUiMyContent? = null,
    selectedMode: RootMode,
    onModeSelected: (RootMode) -> Unit,
    modes: List<RootMode>,
    modifier: Modifier = Modifier,
    preferences: AiPreferences? = null,
    onPreferencesChanged: (AiPreferences) -> Unit = {},
    favoriteFacilityCount: Int? = null,
    onOpenFavorites: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    publicDataSources: List<PublicDataSource> = emptyList(),
    recentFacilities: List<RecentFacility>? = null,
    onRecentFacilityRemoved: (String) -> Unit = {},
    onRecentFacilitiesCleared: () -> Unit = {},
) {
    val design = LocalNativeDesign.current
    if (design.variant == NativeDesignVariant.COMBINED) {
        CombinedMyModeScreen(
            content = content,
            preferences = preferences,
            onPreferencesChanged = onPreferencesChanged,
            favoriteFacilityCount = favoriteFacilityCount,
            onOpenFavorites = onOpenFavorites,
            onBack = onBack,
            modifier = modifier,
            publicDataSources = publicDataSources,
            recentFacilities = recentFacilities,
            onRecentFacilityRemoved = onRecentFacilityRemoved,
            onRecentFacilitiesCleared = onRecentFacilitiesCleared,
        )
        return
    }
    if (!design.isOriginal) {
        CandidateMyModeScreen(
            design = design,
            content = content,
            preferences = preferences,
            onPreferencesChanged = onPreferencesChanged,
            favoriteFacilityCount = favoriteFacilityCount,
            onOpenFavorites = onOpenFavorites,
            onBack = onBack,
            modifier = modifier,
        )
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("my-mode-screen")
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 56.dp),
    ) {
        Text(
            text = content?.title ?: stringResource(R.string.my_screen_title),
            modifier = Modifier.testTag("my-screen-title"),
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                lineHeight = 28.sp,
            ),
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
                .verticalScroll(rememberScrollState())
                .padding(top = 20.dp),
        ) {
            SettingsSection(
                title = stringResource(R.string.my_profile_section),
                rows = listOf(
                    stringResource(R.string.my_profile_empty),
                    stringResource(R.string.my_accountless),
                ),
            )
            SettingsSection(
                title = stringResource(R.string.my_privacy_section),
                rows = listOf(
                    stringResource(R.string.my_location_state),
                    stringResource(R.string.my_ai_data_state),
                ),
            )
            SettingsSection(
                title = stringResource(R.string.my_service_section),
                rows = listOf(
                    stringResource(R.string.my_public_data_source),
                    stringResource(R.string.my_app_version, BuildConfig.VERSION_NAME),
                ),
            )
        }
        RootModeDock(
            selectedMode = selectedMode,
            onModeSelected = onModeSelected,
            modes = modes,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    rows: List<String>,
) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    rows.forEachIndexed { index, row ->
        Text(
            text = row,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        )
        if (index != rows.lastIndex) {
            HorizontalDivider(color = gymviSubtleBorderColor())
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

/** What a candidate design needs to draw AI answers. */
@Immutable
internal data class CandidateAiSpec(
    val design: NativeDesign,
    val tokens: CandidateScreenTokens,
    val layout: AiCardLayout,
)

/**
 * The AI screen of a comparison candidate. Root navigation (tabs, the AI and map switch, the
 * conversation home header) belongs to the shared design frame, so this screen has no back button
 * or dock. State, callbacks and data are the same as the original screen.
 */
@Composable
private fun CandidateAiModeScreen(
    design: NativeDesign,
    content: LiveUiAiContent?,
    selectedFacilityName: String?,
    conversationState: AiConversationUiState,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    showSettings: Boolean,
    onShowSettingsChange: (Boolean) -> Unit,
    onPromptSubmitted: (String) -> Unit,
    onNewConversation: () -> Unit,
    onClearSelectedFacility: () -> Unit,
    onShowOnMap: (AiTurnCard) -> Unit,
    onDecide: (AiTurnCard) -> Unit,
    onPreviewRoute: (AiTurnCard) -> Unit,
    preferences: AiPreferences,
    onPreferencesChanged: (AiPreferences) -> Unit,
    modifier: Modifier,
) {
    val tokens = candidateScreenTokens(design.style)
    val spec = CandidateAiSpec(design = design, tokens = tokens, layout = aiCardLayout(design))
    val conversationPhase = resolveAiConversationPhase(
        hasSubmittedPrompt = conversationState.messages.any { it.role == AiMessageRole.USER },
        hasAssistantMessage = conversationState.messages.any { it.role == AiMessageRole.ASSISTANT },
    )
    val historyDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val examples = content?.examples ?: listOf(
        stringResource(R.string.ai_example_nearby),
        stringResource(R.string.ai_example_new_exercise),
        stringResource(R.string.ai_example_now),
    )
    BackHandler(enabled = showSettings) { onShowSettingsChange(false) }
    BackHandler(enabled = !showSettings && historyDrawerState.isOpen) {
        coroutineScope.launch { historyDrawerState.close() }
    }

    if (showSettings) {
        AiSettingsScreen(
            preferences = preferences,
            onPreferencesChanged = onPreferencesChanged,
            onClose = { onShowSettingsChange(false) },
            modifier = modifier,
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = historyDrawerState,
        gesturesEnabled = historyDrawerState.isOpen,
        drawerContent = {
            AiHistoryDrawer(
                onNewConversation = {
                    onDraftChange(TextFieldValue())
                    onNewConversation()
                    coroutineScope.launch { historyDrawerState.close() }
                },
                onClose = { coroutineScope.launch { historyDrawerState.close() } },
            )
        },
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(tokens.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag("ai-mode-screen")
                .padding(top = 4.dp, bottom = 12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The conversation home header above already names the app, so C-type designs
                // keep only the conversation tools here.
                if (design.style.navigation == NativeNavigation.CHAT_HOME) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Text(
                        text = content?.title ?: stringResource(R.string.ai_screen_title),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai-screen-title"),
                        style = tokens.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (conversationPhase != AiConversationPhase.INITIAL) {
                    CandidateHeaderIcon(
                        iconRes = R.drawable.ic_material_symbol_edit_square_24,
                        description = stringResource(R.string.ai_new_conversation),
                        testTag = "ai-top-new-conversation",
                        onClick = {
                            onDraftChange(TextFieldValue())
                            onNewConversation()
                        },
                    )
                }
                CandidateHeaderIcon(
                    iconRes = R.drawable.ic_material_symbol_history_24,
                    description = stringResource(R.string.ai_history),
                    testTag = "ai-history-button",
                    onClick = { coroutineScope.launch { historyDrawerState.open() } },
                )
                CandidateHeaderIcon(
                    iconRes = R.drawable.ic_material_symbol_settings_24,
                    description = stringResource(R.string.ai_settings),
                    testTag = "ai-settings-button",
                    onClick = { onShowSettingsChange(true) },
                )
            }
            if (conversationState.messages.isEmpty()) {
                CandidateEmptyConversation(
                    design = design,
                    tokens = tokens,
                    headline = content?.headline ?: selectedFacilityName?.let { facilityName ->
                        stringResource(R.string.ai_selected_facility_title, facilityName)
                    } ?: stringResource(R.string.ai_zero_title),
                    examples = examples,
                    forceTop = content?.contentPosition == LiveUiContentPosition.TOP,
                    onExample = { example ->
                        onDraftChange(TextFieldValue(text = example, selection = TextRange(example.length)))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                AiConversationContent(
                    state = conversationState,
                    onShowOnMap = onShowOnMap,
                    onDecide = onDecide,
                    onPreviewRoute = onPreviewRoute,
                    candidate = spec,
                    fadeBackground = tokens.background,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("ai-content"),
                )
            }
            selectedFacilityName?.let { facilityName ->
                AiSelectedFacilityRow(
                    facilityName = facilityName,
                    onClear = onClearSelectedFacility,
                    textStyle = tokens.meta,
                )
            }
            AiComposer(
                value = draft,
                onValueChange = onDraftChange,
                isLoading = conversationState.isLoading,
                onSubmit = { message ->
                    onPromptSubmitted(message)
                    onDraftChange(TextFieldValue())
                },
                tokens = tokens,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun CandidateHeaderIcon(
    @DrawableRes iconRes: Int,
    description: String,
    testTag: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CandidateEmptyConversation(
    design: NativeDesign,
    tokens: CandidateScreenTokens,
    headline: String,
    examples: List<String>,
    forceTop: Boolean,
    onExample: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A reads top-down like a tab page, B centres the prompt, C keeps suggestions next to the
    // composer the way conversation apps do.
    val arrangement = when {
        forceTop -> Arrangement.Top
        design.style.surface == NativeSurface.FLOATING -> Arrangement.Center
        design.style.surface == NativeSurface.TONAL -> Arrangement.Bottom
        else -> Arrangement.Top
    }
    val listed = design.style.surface == NativeSurface.LINES ||
        design.style.surface == NativeSurface.COMPACT
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("ai-content"),
        verticalArrangement = arrangement,
    ) {
        Text(
            text = headline,
            modifier = Modifier.testTag("ai-headline"),
            style = tokens.headline,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(if (listed) 0.dp else 8.dp)) {
            examples.forEachIndexed { index, example ->
                if (listed) {
                    Text(
                        text = example,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = tokens.rowMinHeight)
                            .clickable { onExample(example) }
                            .testTag("ai-example-$index")
                            .padding(vertical = 14.dp),
                        style = tokens.body,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                } else {
                    Surface(
                        onClick = { onExample(example) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("ai-example-$index"),
                        shape = tokens.buttonShape,
                        color = tokens.chipColor,
                        border = tokens.chipBorder,
                    ) {
                        Text(
                            text = example,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 13.dp),
                            style = tokens.support,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateMessageBlock(
    message: AiConversationMessage,
    spec: CandidateAiSpec,
    onShowOnMap: (AiTurnCard) -> Unit,
    onDecide: (AiTurnCard) -> Unit,
    onPreviewRoute: (AiTurnCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = spec.tokens
    if (message.role == AiMessageRole.USER) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .testTag("ai-user-message"),
                shape = tokens.userBubbleShape,
                color = tokens.userBubbleColor,
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = tokens.userBubbleContentColor,
                    style = tokens.body,
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai-assistant-message"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (message.restartedConversation) {
            Text(
                text = stringResource(R.string.ai_conversation_restarted_notice),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = tokens.meta,
                modifier = Modifier.testTag("ai-restarted-notice"),
            )
        }
        Text(text = message.text, style = tokens.body)
        if (message.cards.isNotEmpty()) {
            when (spec.layout) {
                AiCardLayout.STACK -> message.cards.forEach { card ->
                    CandidateOptionCard(
                        card = card,
                        spec = spec,
                        onShowOnMap = { onShowOnMap(card) },
                        onDecide = { onDecide(card) },
                        onPreviewRoute = { onPreviewRoute(card) },
                    )
                }

                AiCardLayout.HORIZONTAL -> CandidateOptionStrip(
                    messageId = message.id,
                    cards = message.cards,
                    spec = spec,
                    onShowOnMap = onShowOnMap,
                    onDecide = onDecide,
                    onPreviewRoute = onPreviewRoute,
                )

                AiCardLayout.COMPARISON -> CandidateOptionComparison(
                    messageId = message.id,
                    cards = message.cards,
                    spec = spec,
                    onShowOnMap = onShowOnMap,
                    onDecide = onDecide,
                    onPreviewRoute = onPreviewRoute,
                )
            }
        }
        if (message.exerciseContents.isNotEmpty()) {
            ExerciseContentSection(message.exerciseContents, compact = true)
        }
    }
}

/** The facts of one usage option, with unknown values already resolved to their labels. */
internal data class CandidateOptionFacts(
    val id: String,
    val schedule: String,
    val price: String,
    val priceKnown: Boolean,
    val application: String,
    val applicationColor: Color,
    val matched: List<String>,
    val unmet: List<String>,
    val distance: String?,
)

@Composable
internal fun candidateOptionFacts(card: AiTurnCard): CandidateOptionFacts {
    val option = card.option
    val time = option.operatorTime?.let { "${it.startTime}~${it.endTime}" }
        ?: option.sourceTimeValue
        ?: stringResource(R.string.usage_option_time_unknown)
    val price = formatUsageOptionPrice(option.priceWon)
    return CandidateOptionFacts(
        id = option.usageOptionId,
        schedule = listOfNotNull(formatUsageOptionWeekdays(option.weekdays), time).joinToString(" "),
        price = price ?: stringResource(R.string.usage_option_price_unknown),
        priceKnown = price != null,
        application = stringResource(usageOptionApplicationLabel(option.programApplication, card = true)),
        applicationColor = when (option.programApplication.state) {
            "AVAILABLE" -> MaterialTheme.colorScheme.primary
            "CLOSED" -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        matched = card.matchedConditions.mapNotNull(::aiConditionLabel).map { stringResource(it) },
        unmet = card.unmetPreferredConditions.mapNotNull(::aiConditionLabel).map { stringResource(it) },
        distance = card.straightLineDistanceMeters?.let(::formatAiDistance),
    )
}

@Composable
private fun CandidateOptionCard(
    card: AiTurnCard,
    spec: CandidateAiSpec,
    onShowOnMap: () -> Unit,
    onDecide: () -> Unit,
    onPreviewRoute: () -> Unit,
) {
    val tokens = spec.tokens
    val facts = candidateOptionFacts(card)
    val actions = spec.design.style.actions
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai-option-card-${facts.id}"),
        shape = tokens.cardShape,
        color = tokens.cardColor,
        border = tokens.cardBorder,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.option.programName,
                        style = tokens.body.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = listOfNotNull(card.facilityName, facts.distance).joinToString(", "),
                        modifier = Modifier.padding(top = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tokens.support,
                    )
                }
                // Secondary actions become icons when the design keeps only one or two pills.
                if (actions == NativeActions.PAIR || actions == NativeActions.SINGLE) {
                    CandidateIconAction(
                        iconRes = R.drawable.ic_material_symbol_map_24,
                        description = stringResource(R.string.ai_show_on_map),
                        testTag = "ai-card-map-${facts.id}",
                        outlined = false,
                        onClick = onShowOnMap,
                    )
                }
                if (actions == NativeActions.SINGLE) {
                    CandidateIconAction(
                        iconRes = R.drawable.ic_material_symbol_directions_24,
                        description = stringResource(R.string.route_preview_title),
                        testTag = "ai-card-route-${facts.id}",
                        outlined = false,
                        onClick = onPreviewRoute,
                    )
                }
            }
            CandidateOptionFactLines(facts = facts, tokens = tokens, modifier = Modifier.padding(top = 12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (actions) {
                    NativeActions.PAIR -> {
                        CandidatePill(
                            text = stringResource(R.string.route_preview_title),
                            primary = false,
                            tokens = tokens,
                            testTag = "ai-card-route-${facts.id}",
                            onClick = onPreviewRoute,
                            modifier = Modifier.weight(1f),
                        )
                        CandidatePill(
                            text = stringResource(R.string.usage_option_decide),
                            primary = true,
                            tokens = tokens,
                            testTag = "ai-card-decide-${facts.id}",
                            onClick = onDecide,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    NativeActions.DUAL_PRIMARY -> {
                        CandidateIconAction(
                            iconRes = R.drawable.ic_material_symbol_map_24,
                            description = stringResource(R.string.ai_show_on_map),
                            testTag = "ai-card-map-${facts.id}",
                            outlined = true,
                            onClick = onShowOnMap,
                        )
                        CandidatePill(
                            text = stringResource(R.string.route_preview_title),
                            primary = true,
                            tokens = tokens,
                            testTag = "ai-card-route-${facts.id}",
                            onClick = onPreviewRoute,
                            modifier = Modifier.weight(1f),
                        )
                        CandidatePill(
                            text = stringResource(R.string.usage_option_decide),
                            primary = true,
                            tokens = tokens,
                            testTag = "ai-card-decide-${facts.id}",
                            onClick = onDecide,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    NativeActions.SINGLE -> CandidatePill(
                        text = stringResource(R.string.usage_option_decide),
                        primary = true,
                        tokens = tokens,
                        testTag = "ai-card-decide-${facts.id}",
                        onClick = onDecide,
                        modifier = Modifier.weight(1f),
                    )

                    NativeActions.COMPACT -> {
                        CandidatePill(
                            text = stringResource(R.string.ai_show_on_map),
                            primary = false,
                            tokens = tokens,
                            testTag = "ai-card-map-${facts.id}",
                            onClick = onShowOnMap,
                            modifier = Modifier.weight(1f),
                        )
                        CandidatePill(
                            text = stringResource(R.string.route_preview_title),
                            primary = false,
                            tokens = tokens,
                            testTag = "ai-card-route-${facts.id}",
                            onClick = onPreviewRoute,
                            modifier = Modifier.weight(1f),
                        )
                        CandidatePill(
                            text = stringResource(R.string.usage_option_decide),
                            primary = true,
                            tokens = tokens,
                            testTag = "ai-card-decide-${facts.id}",
                            onClick = onDecide,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateOptionFactLines(
    facts: CandidateOptionFacts,
    tokens: CandidateScreenTokens,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // The schedule decides whether an option fits the week, so it leads the facts.
        Text(text = facts.schedule, style = tokens.body.copy(fontWeight = FontWeight.SemiBold))
        // Price and application move to the next line whole on narrow cards instead of splitting.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = facts.price,
                color = if (facts.priceKnown) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = tokens.support,
            )
            Text(text = facts.application, color = facts.applicationColor, style = tokens.support)
        }
        if (facts.matched.isNotEmpty() || facts.unmet.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("ai-card-conditions-${facts.id}"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (facts.matched.isNotEmpty()) {
                    AiConditionLine(stringResource(R.string.ai_card_matched_conditions), facts.matched.joinToString(", "))
                }
                if (facts.unmet.isNotEmpty()) {
                    AiConditionLine(stringResource(R.string.ai_card_unmet_conditions), facts.unmet.joinToString(", "))
                }
            }
        }
    }
}

@Composable
private fun CandidatePill(
    text: String,
    primary: Boolean,
    tokens: CandidateScreenTokens,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag(testTag),
        shape = tokens.buttonShape,
        colors = if (primary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = tokens.neutralButtonColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
        elevation = null,
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
        )
    }
}

@Composable
private fun CandidateIconAction(
    @DrawableRes iconRes: Int,
    description: String,
    testTag: String,
    outlined: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag),
        shape = CircleShape,
        color = if (outlined) MaterialTheme.colorScheme.surface else Color.Transparent,
        border = if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** GPT B: options side by side; choosing one moves its location and route actions below. */
@Composable
private fun CandidateOptionStrip(
    messageId: Long,
    cards: List<AiTurnCard>,
    spec: CandidateAiSpec,
    onShowOnMap: (AiTurnCard) -> Unit,
    onDecide: (AiTurnCard) -> Unit,
    onPreviewRoute: (AiTurnCard) -> Unit,
) {
    val tokens = spec.tokens
    var savedId by rememberSaveable(messageId) { mutableStateOf<String?>(null) }
    val chosenId = resolveChosenOptionId(cards.map { it.option.usageOptionId }, savedId)
    val chosen = cards.first { it.option.usageOptionId == chosenId }
    val chosenFacts = candidateOptionFacts(chosen)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai-option-strip"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards, key = { it.option.usageOptionId }) { card ->
                val facts = candidateOptionFacts(card)
                val selected = card.option.usageOptionId == chosenId
                val borderColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    animationSpec = nativeMotionSpec(spec.design),
                    label = "ai-option-strip-border",
                )
                Surface(
                    selected = selected,
                    onClick = { savedId = card.option.usageOptionId },
                    modifier = Modifier
                        .width(256.dp)
                        .testTag("ai-option-card-${facts.id}"),
                    shape = tokens.cardShape,
                    color = tokens.cardColor,
                    border = BorderStroke(2.dp, borderColor),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = card.facilityTypeName.orEmpty(),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = tokens.meta,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(R.string.native_ai_option_rank, card.rank),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = tokens.meta,
                            )
                        }
                        Text(
                            text = card.facilityName,
                            modifier = Modifier.padding(top = 6.dp),
                            // A 256dp card cannot carry the screen title size without cutting the name.
                            style = tokens.body.copy(
                                fontSize = tokens.body.fontSize * 1.15f,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = card.option.programName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = tokens.support,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        CandidateOptionFactLines(facts = facts, tokens = tokens, modifier = Modifier.padding(top = 10.dp))
                        Text(
                            text = stringResource(
                                if (selected) R.string.native_ai_chosen_program else R.string.native_ai_choose_program,
                            ),
                            modifier = Modifier.padding(top = 10.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = tokens.meta.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai-option-chosen"),
            shape = tokens.cardShape,
            color = tokens.cardColor,
            border = tokens.cardBorder,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.native_ai_chosen_program),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = tokens.meta,
                )
                Text(
                    text = chosen.facilityName,
                    modifier = Modifier.padding(top = 2.dp),
                    style = tokens.body.copy(fontWeight = FontWeight.SemiBold),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 48.dp)
                        .clickable { onShowOnMap(chosen) }
                        .testTag("ai-card-map-${chosenFacts.id}"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_map_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(text = stringResource(R.string.ai_show_on_map), style = tokens.body)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CandidatePill(
                        text = stringResource(R.string.usage_option_decide),
                        primary = false,
                        tokens = tokens,
                        testTag = "ai-card-decide-${chosenFacts.id}",
                        onClick = { onDecide(chosen) },
                        modifier = Modifier.weight(1f),
                    )
                    CandidatePill(
                        text = stringResource(R.string.route_preview_title),
                        primary = true,
                        tokens = tokens,
                        testTag = "ai-card-route-${chosenFacts.id}",
                        onClick = { onPreviewRoute(chosen) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** GPT C: dense numbered rows for comparison, with the chosen option's actions in a tray. */
@Composable
private fun CandidateOptionComparison(
    messageId: Long,
    cards: List<AiTurnCard>,
    spec: CandidateAiSpec,
    onShowOnMap: (AiTurnCard) -> Unit,
    onDecide: (AiTurnCard) -> Unit,
    onPreviewRoute: (AiTurnCard) -> Unit,
) {
    val tokens = spec.tokens
    var savedId by rememberSaveable(messageId) { mutableStateOf<String?>(null) }
    val chosenId = resolveChosenOptionId(cards.map { it.option.usageOptionId }, savedId)
    val chosen = cards.first { it.option.usageOptionId == chosenId }
    val chosenFacts = candidateOptionFacts(chosen)
    Column(modifier = Modifier.testTag("ai-option-comparison")) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        cards.forEachIndexed { index, card ->
            val facts = candidateOptionFacts(card)
            val selected = card.option.usageOptionId == chosenId
            val fill by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                } else {
                    Color.Transparent
                },
                animationSpec = nativeMotionSpec(spec.design),
                label = "ai-option-row-fill",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .background(fill)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { savedId = card.option.usageOptionId },
                    )
                    .testTag("ai-option-card-${facts.id}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Selection shows as a line and a neutral fill rather than a colour change.
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
                )
                Text(
                    text = "${index + 1}",
                    modifier = Modifier.padding(horizontal = 10.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = tokens.meta.copy(fontWeight = FontWeight.SemiBold),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = tokens.rowMinHeight)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    Text(
                        text = card.option.programName,
                        style = tokens.body.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(card.facilityName, facts.distance).joinToString(", "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tokens.meta,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(facts.schedule, facts.price, facts.application).joinToString(", "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = tokens.meta,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .testTag("ai-option-chosen"),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chosen.facilityName,
                    style = tokens.body.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.native_ai_chosen_program),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = tokens.meta,
                )
            }
            CandidatePill(
                text = stringResource(R.string.route_preview_title),
                primary = true,
                tokens = tokens,
                testTag = "ai-card-route-${chosenFacts.id}",
                onClick = { onPreviewRoute(chosen) },
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CandidatePill(
                text = stringResource(R.string.ai_show_on_map),
                primary = false,
                tokens = tokens,
                testTag = "ai-card-map-${chosenFacts.id}",
                onClick = { onShowOnMap(chosen) },
                modifier = Modifier.weight(1f),
            )
            CandidatePill(
                text = stringResource(R.string.usage_option_decide),
                primary = false,
                tokens = tokens,
                testTag = "ai-card-decide-${chosenFacts.id}",
                onClick = { onDecide(chosen) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The MY screen of a comparison candidate. Every row reflects real state: no profile is saved
 * yet, favourites and AI references come from their stores, and location reads the current
 * permission each time the screen resumes.
 */
@Composable
private fun CandidateMyModeScreen(
    design: NativeDesign,
    content: LiveUiMyContent?,
    preferences: AiPreferences?,
    onPreferencesChanged: (AiPreferences) -> Unit,
    favoriteFacilityCount: Int?,
    onOpenFavorites: (() -> Unit)?,
    onBack: (() -> Unit)?,
    modifier: Modifier,
) {
    val tokens = candidateScreenTokens(design.style)
    val context = LocalContext.current
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    LifecycleResumeEffect(context) {
        locationGranted = hasLocationPermission(context)
        onPauseOrDispose { }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.background)
            .testTag("my-mode-screen")
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = if (onBack == null) 20.dp else 4.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { back ->
                IconButton(onClick = back, modifier = Modifier.testTag("my-back-button")) {
                    Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_arrow_back_24),
                        contentDescription = stringResource(R.string.native_my_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                text = content?.title ?: stringResource(R.string.my_screen_title),
                modifier = Modifier.testTag("my-screen-title"),
                style = tokens.title,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CandidateMyGroup(stringResource(R.string.my_profile_section), design, tokens) {
                CandidateMyRow(stringResource(R.string.my_profile_empty), tokens)
                CandidateMyDivider(design)
                CandidateMyRow(stringResource(R.string.my_accountless), tokens)
            }
            favoriteFacilityCount?.let { count ->
                CandidateMyGroup(stringResource(R.string.facility_favorites_title), design, tokens) {
                    CandidateMyRow(
                        text = if (count > 0) {
                            stringResource(R.string.native_my_favorites_count, count)
                        } else {
                            stringResource(R.string.native_my_favorites_empty)
                        },
                        tokens = tokens,
                        testTag = "my-favorites-row",
                        onClick = onOpenFavorites,
                    )
                }
            }
            preferences?.let { current ->
                CandidateMyGroup(stringResource(R.string.ai_settings_personalization_section), design, tokens) {
                    AiSettingToggleRow(
                        title = stringResource(R.string.ai_settings_use_memory),
                        description = stringResource(R.string.ai_settings_use_memory_description),
                        checked = current.useAiMemory,
                        onCheckedChange = { onPreferencesChanged(current.copy(useAiMemory = it)) },
                        testTag = "my-setting-memory",
                        tokens = tokens,
                    )
                    CandidateMyDivider(design)
                    AiSettingToggleRow(
                        title = stringResource(R.string.ai_settings_use_body_information),
                        description = stringResource(R.string.ai_settings_use_body_information_description),
                        checked = current.useBodyInformation,
                        onCheckedChange = { onPreferencesChanged(current.copy(useBodyInformation = it)) },
                        testTag = "my-setting-body-information",
                        tokens = tokens,
                    )
                    CandidateMyDivider(design)
                    AiSettingToggleRow(
                        title = stringResource(R.string.ai_settings_use_region),
                        description = stringResource(R.string.ai_settings_use_region_description),
                        checked = current.useApproximateRegion,
                        onCheckedChange = { onPreferencesChanged(current.copy(useApproximateRegion = it)) },
                        testTag = "my-setting-approximate-region",
                        tokens = tokens,
                    )
                }
                CandidateMyGroup(stringResource(R.string.ai_settings_conversation_section), design, tokens) {
                    AiSettingToggleRow(
                        title = stringResource(R.string.ai_settings_save_history),
                        description = stringResource(R.string.ai_settings_save_history_description),
                        checked = current.saveConversationHistory,
                        onCheckedChange = { onPreferencesChanged(current.copy(saveConversationHistory = it)) },
                        testTag = "my-setting-save-history",
                        tokens = tokens,
                    )
                }
            }
            CandidateMyGroup(stringResource(R.string.my_privacy_section), design, tokens) {
                CandidateMyRow(
                    text = stringResource(
                        if (locationGranted) R.string.native_my_location_granted else R.string.native_my_location_denied,
                    ),
                    tokens = tokens,
                    testTag = "my-location-state",
                )
            }
            CandidateMyGroup(stringResource(R.string.my_service_section), design, tokens) {
                CandidateMyRow(stringResource(R.string.my_public_data_source), tokens)
                CandidateMyDivider(design)
                CandidateMyRow(stringResource(R.string.my_app_version, BuildConfig.VERSION_NAME), tokens)
            }
        }
    }
}

internal fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun CandidateMyGroup(
    title: String,
    design: NativeDesign,
    tokens: CandidateScreenTokens,
    rows: @Composable () -> Unit,
) {
    val boxed = design.style.surface == NativeSurface.FLOATING || design.style.surface == NativeSurface.TONAL
    Column {
        Text(
            text = title,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            color = tokens.sectionLabelColor,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.sp),
            fontWeight = FontWeight.SemiBold,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = if (boxed) tokens.cardShape else RectangleShape,
            color = if (boxed) tokens.groupColor else Color.Transparent,
        ) {
            Column(modifier = Modifier.padding(horizontal = if (boxed) 16.dp else 4.dp)) {
                rows()
            }
        }
    }
}

@Composable
private fun CandidateMyRow(
    text: String,
    tokens: CandidateScreenTokens,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = tokens.rowMinHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, modifier = Modifier.weight(1f), style = tokens.body)
        if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.ic_material_symbol_arrow_back_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(180f),
            )
        }
    }
}

@Composable
private fun CandidateMyDivider(design: NativeDesign) {
    // Tone groups separate rows by space alone.
    if (design.style.surface != NativeSurface.TONAL) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
