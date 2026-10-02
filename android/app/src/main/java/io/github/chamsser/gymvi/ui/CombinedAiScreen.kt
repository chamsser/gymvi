package io.github.chamsser.gymvi.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AiTurnCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * The AI page. The conversation runs edge to edge behind a see-through header
 * and a floating composer: only their buttons block it, and it fades out under both. The list
 * follows new text only while the latest reply is in view and otherwise offers 맨 아래로.
 * AI settings live in 내 정보, and root navigation belongs to the design frame's bottom tabs.
 */
@Composable
internal fun CombinedAiModeScreen(
    content: LiveUiAiContent?,
    selectedFacilityName: String?,
    conversationState: AiConversationUiState,
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onPromptSubmitted: (String) -> Unit,
    onNewConversation: () -> Unit,
    onClearSelectedFacility: () -> Unit,
    onShowOnMap: (AiTurnCard) -> Unit,
    onDecide: (AiTurnCard) -> Unit,
    onPreviewRoute: (AiTurnCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = combinedPalette()
    val design = LocalNativeDesign.current
    val density = LocalDensity.current
    val overlays = remember(density) { CombinedAiOverlayPadding(density) }
    val listState = rememberLazyListState()
    var followLatest by rememberSaveable { mutableStateOf(true) }
    val historyDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var displayedConversationId by rememberSaveable { mutableStateOf(conversationState.localConversationId) }
    LaunchedEffect(conversationState.localConversationId) {
        if (displayedConversationId != conversationState.localConversationId) {
            displayedConversationId = conversationState.localConversationId
            followLatest = true
            onDraftChange(TextFieldValue())
            if (conversationState.messages.isNotEmpty()) listState.scrollToLatest()
        }
    }
    val nearby = stringResource(R.string.ai_example_nearby)
    val newExercise = stringResource(R.string.ai_example_new_exercise)
    val goNow = stringResource(R.string.ai_example_now)
    val personalized = personalizedAiExamples(LocalAiReferencePreferences.current,
        LocalAiPersonalizationActions.current, listOf(nearby, newExercise, goNow))
    // Keep known default icons even when Live UI repeats the same examples. Only custom
    // suggestions use a neutral chat icon instead of inferring their intent from arbitrary text.
    val examples = remember(content?.examples, personalized, nearby, newExercise, goNow) {
        combinedAiExamples(content?.examples?.takeUnless { it == listOf(nearby, newExercise, goNow) }, listOf(
            CombinedAiExample(personalized[0], R.drawable.ic_material_symbol_location_on_24),
            CombinedAiExample(personalized[1], R.drawable.ic_material_symbol_fitness_center_24),
            CombinedAiExample(personalized[2], R.drawable.ic_material_symbol_directions_walk_24),
        ))
    }
    BackHandler(enabled = historyDrawerState.isOpen) {
        coroutineScope.launch { historyDrawerState.close() }
    }
    val startNewConversation = {
        onDraftChange(TextFieldValue())
        onNewConversation()
        followLatest = true
    }

    // Dragging hands the position to the reader; reaching the end again resumes following.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> followLatest = false
                is DragInteraction.Stop, is DragInteraction.Cancel ->
                    if (!listState.canScrollForward) followLatest = true
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }.collect { canScrollForward ->
            if (!canScrollForward) followLatest = true
        }
    }
    // Streaming text and new messages push the end out of view after the list has measured
    // them; while following, this brings it back a frame later. It scrolls rather than jumps, so
    // a new message keeps its fade. The keyboard and the composer are handled while measuring,
    // in combinedAiListAnchor.
    LaunchedEffect(listState) {
        snapshotFlow { followLatest && listState.canScrollForward }.collect { behind ->
            if (behind) listState.scrollToLatest()
        }
    }

    ModalNavigationDrawer(
        drawerState = historyDrawerState,
        gesturesEnabled = historyDrawerState.isOpen,
        drawerContent = {
            AiHistoryDrawer(
                onNewConversation = {
                    startNewConversation()
                    coroutineScope.launch { historyDrawerState.close() }
                },
                onClose = { coroutineScope.launch { historyDrawerState.close() } },
            )
        },
    ) {
        // The keyboard shortens the whole page, so the list gets its new height as a constraint
        // in the same frame instead of reading the composer's size a frame later.
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(palette.page)
                .imePadding()
                .testTag("ai-mode-screen"),
        ) {
            // The header and the composer are measured first, so the padding the content reads
            // below is already theirs; zIndex keeps them drawn above it.
            CombinedAiHeader(
                title = conversationState.title ?: content?.title ?: stringResource(R.string.ai_screen_title),
                palette = palette,
                showNewConversation = conversationState.messages.isNotEmpty(),
                onOpenHistory = { coroutineScope.launch { historyDrawerState.open() } },
                onNewConversation = startNewConversation,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(1f)
                    .onSizeChanged { overlays.topPx = it.height },
            )
            CombinedAiBottomBar(
                draft = draft,
                onDraftChange = onDraftChange,
                isLoading = conversationState.isLoading || !conversationState.libraryLoaded,
                selectedFacilityName = selectedFacilityName,
                onClearSelectedFacility = onClearSelectedFacility,
                onSubmit = { message ->
                    followLatest = true
                    onPromptSubmitted(message)
                    onDraftChange(TextFieldValue())
                },
                palette = palette,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1f)
                    .onSizeChanged { overlays.bottomPx = it.height },
            )
            if (conversationState.messages.isEmpty()) {
                // Read once per new chat, so the greeting stays put while it is on screen.
                val openedAtHour = remember { LocalTime.now().hour }
                val defaultHeadline = stringResource(R.string.ai_zero_title)
                CombinedAiEmptyConversation(
                    headline = content?.headline?.takeUnless { it == defaultHeadline }
                        ?: selectedFacilityName?.let { facilityName ->
                        stringResource(R.string.ai_selected_facility_title, facilityName)
                    } ?: stringResource(combinedAiGreeting(openedAtHour)),
                    examples = examples,
                    forceTop = content?.contentPosition == LiveUiContentPosition.TOP,
                    contentPadding = overlays,
                    onExample = { example ->
                        onDraftChange(TextFieldValue(text = example, selection = TextRange(example.length)))
                    },
                )
            } else {
                CombinedAiConversation(
                    state = conversationState,
                    listState = listState,
                    following = { followLatest },
                    contentPadding = overlays,
                    palette = palette,
                    fadeInMillis = design.durationMillis,
                    reduceMotion = design.reduceMotion,
                    onShowOnMap = onShowOnMap,
                    onDecide = onDecide,
                    onPreviewRoute = onPreviewRoute,
                )
            }
            CombinedAiScrollToLatest(
                visible = conversationState.messages.isNotEmpty() && !followLatest && listState.canScrollForward,
                reduceMotion = design.reduceMotion,
                palette = palette,
                onClick = { followLatest = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, -(overlays.bottomPx + 12.dp.roundToPx())) },
            )
            if (conversationState.storageError != null) {
                Text(stringResource(R.string.ai_library_storage_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .offset { IntOffset(0, -(overlays.bottomPx + 4.dp.roundToPx())) }
                        .padding(horizontal = 24.dp).testTag("ai-library-storage-error"),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Content padding that follows the measured header and composer. The list reads it only while
 * measuring, so a growing draft or a selected facility relayouts without recomposing.
 */
@Stable
private class CombinedAiOverlayPadding(private val density: Density) : PaddingValues {
    var topPx by mutableIntStateOf(0)
    var bottomPx by mutableIntStateOf(0)

    override fun calculateTopPadding(): Dp = with(density) { topPx.toDp() } + 4.dp
    override fun calculateBottomPadding(): Dp = with(density) { bottomPx.toDp() } + 16.dp
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 20.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 20.dp
}

private suspend fun LazyListState.scrollToLatest() {
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    try {
        if (layoutInfo.visibleItemsInfo.lastOrNull()?.index != lastIndex) scrollToItem(lastIndex)
        // A tall final reply can exceed the viewport; scrolling past it lands exactly on its end.
        scrollBy(100_000f)
    } catch (interrupted: CancellationException) {
        // A drag took the list over; the reader's position wins and following stays usable.
        currentCoroutineContext().ensureActive()
    }
}

/** The list's height and bottom padding at its last measure, and whether it was following. */
private class CombinedAiListAnchor {
    var height = -1
    var bottomPadding = -1
    var following = false
}

// Past any reply's height: the list stops at its end and keeps that end above the composer.
private const val CombinedAiPastTheEnd = 1 shl 24

/**
 * Keeps the end, or the reader's place, above the composer in the same frame the keyboard or a
 * taller composer takes room from the list. The position is set before the list measures, so it
 * never draws a frame with the latest reply underneath and then jumps.
 */
private fun Modifier.combinedAiListAnchor(
    listState: LazyListState,
    anchor: CombinedAiListAnchor,
    contentPadding: PaddingValues,
    itemCount: Int,
    following: () -> Boolean,
): Modifier = layout { measurable, constraints ->
    val height = constraints.maxHeight
    val bottomPadding = contentPadding.calculateBottomPadding().roundToPx()
    val follow = following()
    Snapshot.withoutReadObservation {
        // A drag or fling owns the position, and a request would cancel it.
        if (!listState.isScrollInProgress && itemCount > 0) {
            val lostRoom = anchor.height >= 0 && (height < anchor.height || bottomPadding > anchor.bottomPadding)
            if (follow) {
                // More room keeps the end in place by itself; less room has to scroll to it.
                if (lostRoom || !anchor.following) {
                    listState.requestScrollToItem(itemCount - 1, CombinedAiPastTheEnd)
                }
            } else if (anchor.height >= 0) {
                // What sat just above the composer moves with it, up or down.
                val lifted = anchor.height - height + bottomPadding - anchor.bottomPadding
                if (lifted != 0) {
                    listState.requestScrollToItem(
                        listState.firstVisibleItemIndex,
                        listState.firstVisibleItemScrollOffset + lifted,
                    )
                }
            }
        }
    }
    anchor.height = height
    anchor.bottomPadding = bottomPadding
    anchor.following = follow
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** Only the vertical part of [padding]; rows that scroll sideways pad their own ends. */
@Stable
private class CombinedAiVerticalPadding(private val padding: PaddingValues) : PaddingValues {
    override fun calculateTopPadding(): Dp = padding.calculateTopPadding()
    override fun calculateBottomPadding(): Dp = padding.calculateBottomPadding()
    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp
    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
}

private val CombinedAiComposerFade = 28.dp

@Composable
private fun CombinedAiHeader(
    title: String,
    palette: CombinedPalette,
    showNewConversation: Boolean,
    onOpenHistory: () -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A fade, not a bar: touches outside the buttons reach the list underneath.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedHeaderFade(palette.page)
            .statusBarsPadding()
            .height(CombinedHeaderHeight)
            .padding(horizontal = 12.dp),
    ) {
        CombinedRoundButton(
            iconRes = R.drawable.ic_material_symbol_history_24,
            description = stringResource(R.string.ai_history),
            testTag = "ai-history-button",
            palette = palette,
            onClick = onOpenHistory,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 60.dp)
                .semantics { heading() }
                .testTag("ai-screen-title"),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showNewConversation) {
            CombinedRoundButton(
                iconRes = R.drawable.ic_material_symbol_edit_square_24,
                description = stringResource(R.string.ai_new_conversation),
                testTag = "ai-top-new-conversation",
                palette = palette,
                onClick = onNewConversation,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

/** A new chat's suggestion and the icon that tells what kind of question it is. */
@Immutable
internal data class CombinedAiExample(val text: String, @param:DrawableRes val iconRes: Int)

internal fun combinedAiExamples(custom: List<String>?, defaults: List<CombinedAiExample>): List<CombinedAiExample> =
    custom?.map { text -> defaults.firstOrNull { it.text == text }
        ?: CombinedAiExample(text, R.drawable.ic_material_symbol_chat_24) } ?: defaults

/** The new chat's greeting for the hour it opened in; late at night it simply asks about today. */
@StringRes
internal fun combinedAiGreeting(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.ai_greeting_morning
    in 12..17 -> R.string.ai_greeting_afternoon
    in 18..21 -> R.string.ai_greeting_evening
    else -> R.string.ai_zero_title
}

// A little above the middle of the free room, where a page's opening sits; when the keyboard
// takes room the greeting and suggestions rise with it instead of hiding under the composer.
private val CombinedAiGreetingAlignment = BiasAlignment.Vertical(-.2f)

@Composable
private fun CombinedAiEmptyConversation(
    headline: String,
    examples: List<CombinedAiExample>,
    forceTop: Boolean,
    contentPadding: PaddingValues,
    onExample: (String) -> Unit,
) {
    val verticalPadding = remember(contentPadding) { CombinedAiVerticalPadding(contentPadding) }
    // The scroll container hands its height down as the column's minimum, so the alignment
    // places the greeting while it fits. When it no longer does, scrolling from the end keeps the
    // suggestions next to the composer in the same frame the keyboard takes room; the greeting
    // gives way first.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai-content")
            .verticalScroll(rememberScrollState(), reverseScrolling = !forceTop)
            .padding(verticalPadding),
        verticalArrangement = if (forceTop) Arrangement.Top else Arrangement.aligned(CombinedAiGreetingAlignment),
    ) {
        Text(
            text = headline,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .testTag("ai-headline"),
            // Balanced lines that break between phrases, never inside a word. Phrase breaking
            // follows the text locale, so the Korean greeting declares it even on an English device.
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                localeList = LocaleList("ko-KR"),
                lineBreak = LineBreak.Heading,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(28.dp))
        val fillLabel = stringResource(R.string.ai_example_fill_action)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            examples.forEachIndexed { index, example ->
                CombinedAiExampleRow(
                    example = example,
                    tag = "ai-example-$index",
                    fillLabel = fillLabel,
                    onClick = { onExample(example.text) },
                )
            }
        }
    }
}

/**
 * One suggestion under the greeting. The whole row answers a tap by filling the composer; its
 * tile starts where the greeting starts, and the tile's icon only illustrates the question.
 */
@Composable
private fun CombinedAiExampleRow(
    example: CombinedAiExample,
    tag: String,
    fillLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = fillLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The accent's tinted container sets the suggestions apart from the plain page.
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
                .testTag("$tag-icon"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(example.iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = example.text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CombinedAiConversation(
    state: AiConversationUiState,
    listState: LazyListState,
    following: () -> Boolean,
    contentPadding: PaddingValues,
    palette: CombinedPalette,
    fadeInMillis: Int,
    reduceMotion: Boolean,
    onShowOnMap: (AiTurnCard) -> Unit,
    onDecide: (AiTurnCard) -> Unit,
    onPreviewRoute: (AiTurnCard) -> Unit,
) {
    // Until the first words of a reply arrive, the waiting line stands in for it.
    val waitingForReply = state.isLoading && state.messages.lastOrNull()?.role != AiMessageRole.ASSISTANT
    val itemCount = state.messages.size + (if (waitingForReply) 1 else 0) + (if (state.errorCode != null) 1 else 0)
    val anchor = remember { CombinedAiListAnchor() }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .combinedAiListAnchor(listState, anchor, contentPadding, itemCount, following)
            .testTag("ai-content"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(state.messages, key = AiConversationMessage::id) { message ->
            CombinedAiMessage(
                message = message,
                palette = palette,
                reduceMotion = reduceMotion,
                onShowOnMap = onShowOnMap,
                onDecide = onDecide,
                onPreviewRoute = onPreviewRoute,
                modifier = Modifier.animateItem(
                    fadeInSpec = fadeInMillis.takeIf { it > 0 }?.let { tween(it) },
                    placementSpec = null,
                    fadeOutSpec = null,
                ),
            )
        }
        if (waitingForReply) {
            item(key = "ai-loading") { CombinedAiThinking(reduceMotion) }
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

@Composable
private fun CombinedAiMessage(
    message: AiConversationMessage,
    palette: CombinedPalette,
    reduceMotion: Boolean,
    onShowOnMap: (AiTurnCard) -> Unit,
    onDecide: (AiTurnCard) -> Unit,
    onPreviewRoute: (AiTurnCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message.role == AiMessageRole.USER) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 48.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            CombinedAiUserMessage(text = message.text, palette = palette)
        }
        return
    }

    // Places, guides and answer actions belong to a finished, validated reply only.
    val complete = !message.isStreaming
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai-assistant-message"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (message.restartedConversation) {
            Text(
                text = stringResource(R.string.ai_conversation_restarted_notice),
                modifier = Modifier.testTag("ai-restarted-notice"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (message.text.isNotBlank()) {
            CombinedAiAnswerText(text = message.text, streaming = message.isStreaming, reduceMotion = reduceMotion)
        } else if (message.isStreaming) {
            CombinedAiThinking(reduceMotion)
        }
        if (complete && message.cards.isNotEmpty()) {
            CombinedAiPlaces(
                messageId = message.id,
                cards = message.cards,
                palette = palette,
                onShowOnMap = onShowOnMap,
                onDecide = onDecide,
                onPreviewRoute = onPreviewRoute,
            )
        }
        if (complete && message.exerciseContents.isNotEmpty()) {
            CombinedExerciseVideos(message.exerciseContents)
        }
        if (complete && message.text.isNotBlank()) {
            CombinedAiAnswerActions(message)
        }
    }
}

/** The reply as it streams in, with a pulsing mark after its last word until it is complete. */
@Composable
private fun CombinedAiAnswerText(text: String, streaming: Boolean, reduceMotion: Boolean) {
    val style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp)
    val color = MaterialTheme.colorScheme.onSurface
    if (!streaming) {
        Text(text = text, style = style, color = color)
        return
    }
    val streamingDescription = stringResource(R.string.native_ai_streaming)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val pulse = if (reduceMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "ai-streaming").animateFloat(
            initialValue = 1f,
            targetValue = .3f,
            animationSpec = infiniteRepeatable(tween(560), RepeatMode.Reverse),
            label = "ai-streaming-mark",
        )
    }
    Text(
        text = text,
        modifier = Modifier
            .testTag("ai-streaming-text")
            .semantics { stateDescription = streamingDescription }
            .drawWithContent {
                drawContent()
                // Read in the draw phase only, so the pulse never recomposes the reply.
                val result = layout ?: return@drawWithContent
                val lastLine = result.lineCount - 1
                val radius = 4.dp.toPx()
                val x = result.getLineRight(lastLine) + 6.dp.toPx() + radius
                val y = (result.getLineTop(lastLine) + result.getLineBottom(lastLine)) / 2f
                drawCircle(color = color.copy(alpha = pulse?.value ?: 1f), radius = radius, center = Offset(x, y))
            },
        style = style,
        color = color,
        onTextLayout = { layout = it },
    )
}

@Composable
private fun CombinedAiThinking(reduceMotion: Boolean) {
    val pulse = if (reduceMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "ai-thinking").animateFloat(
            initialValue = 1f,
            targetValue = .45f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "ai-thinking-alpha",
        )
    }
    Text(
        text = stringResource(R.string.ai_loading),
        modifier = Modifier
            .graphicsLayer { alpha = pulse?.value ?: 1f }
            .testTag("ai-loading"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
    )
}

// The preview's markers use this blue, so a number reads as the same place on both.
private val CombinedAiMarkerColor = Color(0xFF2563EB)

/** A facility answer: one map of the places, then a numbered item per usage option. */
@Composable
private fun CombinedAiPlaces(
    messageId: Long,
    cards: List<AiTurnCard>,
    palette: CombinedPalette,
    onShowOnMap: (AiTurnCard) -> Unit,
    onDecide: (AiTurnCard) -> Unit,
    onPreviewRoute: (AiTurnCard) -> Unit,
) {
    // Programs at one facility share its marker number.
    val numbers = remember(cards) { aiMapPreviewPoints(cards).associate { it.facilityId to it.number } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai-places-$messageId"),
    ) {
        AiMapPreview(cards = cards, onOpenMap = onShowOnMap)
        cards.forEachIndexed { index, card ->
            if (index > 0) NativeHairline(Modifier.padding(start = 36.dp))
            CombinedAiPlace(
                card = card,
                number = numbers[card.option.facilityId],
                palette = palette,
                onShowOnMap = { onShowOnMap(card) },
                onDecide = { onDecide(card) },
                onPreviewRoute = { onPreviewRoute(card) },
            )
        }
    }
}

@Composable
private fun CombinedAiPlace(
    card: AiTurnCard,
    number: Int?,
    palette: CombinedPalette,
    onShowOnMap: () -> Unit,
    onDecide: () -> Unit,
    onPreviewRoute: () -> Unit,
) {
    val facts = optionCardFacts(card)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai-option-card-${facts.id}")
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CombinedAiPlaceNumber(number)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = card.option.programName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(card.facilityName, facts.distance).joinToString(", "),
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            // The schedule decides whether an option fits the week, so it leads the facts.
            Text(
                text = facts.schedule,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = facts.price,
                    color = if (facts.priceKnown) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(text = facts.application, color = facts.applicationColor, style = MaterialTheme.typography.bodyMedium)
            }
            if (facts.matched.isNotEmpty() || facts.unmet.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .padding(top = 6.dp)
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
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CombinedAiPlaceAction(
                    text = stringResource(R.string.native_ai_place_map),
                    iconRes = R.drawable.ic_material_symbol_map_24,
                    primary = false,
                    palette = palette,
                    testTag = "ai-card-map-${facts.id}",
                    onClick = onShowOnMap,
                )
                CombinedAiPlaceAction(
                    text = stringResource(R.string.native_ai_place_route),
                    iconRes = R.drawable.ic_material_symbol_directions_24,
                    primary = false,
                    palette = palette,
                    testTag = "ai-card-route-${facts.id}",
                    onClick = onPreviewRoute,
                )
                CombinedAiPlaceAction(
                    text = stringResource(R.string.usage_option_decide),
                    iconRes = null,
                    primary = true,
                    palette = palette,
                    testTag = "ai-card-decide-${facts.id}",
                    onClick = onDecide,
                )
            }
        }
    }
}

@Composable
private fun CombinedAiPlaceNumber(number: Int?) {
    val description = number?.let { stringResource(R.string.native_ai_place_number, it) }
    Box(
        modifier = Modifier
            .padding(top = 1.dp)
            .size(24.dp)
            .then(
                if (number != null) {
                    Modifier.background(CombinedAiMarkerColor, CircleShape)
                } else {
                    // Beyond the five mapped facilities an item has no marker to point at.
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                },
            )
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (number != null) {
            Text(
                text = number.toString(),
                modifier = Modifier.clearAndSetSemantics { },
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun CombinedAiPlaceAction(
    text: String,
    @DrawableRes iconRes: Int?,
    primary: Boolean,
    palette: CombinedPalette,
    testTag: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.testTag(testTag),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MaterialTheme.colorScheme.primary else palette.quiet,
            contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
        elevation = null,
        contentPadding = PaddingValues(start = if (iconRes != null) 12.dp else 16.dp, end = 16.dp),
    ) {
        if (iconRes != null) {
            Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text = text, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun CombinedAiAnswerActions(message: AiConversationMessage) {
    val context = LocalContext.current
    // Icons line up with the reply's first letter; their touch targets extend to the left.
    Row(modifier = Modifier.offset(x = (-12).dp)) {
        CombinedAiAnswerAction(
            iconRes = R.drawable.ic_material_symbol_content_copy_24,
            description = stringResource(R.string.native_ai_copy_answer),
            testTag = "ai-answer-copy-${message.id}",
            onClick = { copyAiAnswer(context, message.text) },
        )
        CombinedAiAnswerAction(
            iconRes = R.drawable.ic_material_symbol_share_24,
            description = stringResource(R.string.native_ai_share_answer),
            testTag = "ai-answer-share-${message.id}",
            onClick = { shareAiAnswer(context, message.text) },
        )
    }
}

@Composable
private fun CombinedAiAnswerAction(
    @DrawableRes iconRes: Int,
    description: String,
    testTag: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(testTag)) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun copyAiAnswer(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.ai_screen_title), text))
    // Android 13 and later confirm a copy themselves.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.native_ai_answer_copied, Toast.LENGTH_SHORT).show()
    }
}

private fun shareAiAnswer(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        // Nothing on the device can receive text; the copy action still works.
    }
}

@Composable
private fun CombinedAiScrollToLatest(
    visible: Boolean,
    reduceMotion: Boolean,
    palette: CombinedPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(150)),
        exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(150)),
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .testTag("ai-scroll-bottom"),
            shape = CircleShape,
            color = palette.raised,
            shadowElevation = if (palette.shadow) 3.dp else 0.dp,
            border = if (palette.shadow) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(R.drawable.ic_material_symbol_arrow_upward_24),
                    contentDescription = stringResource(R.string.native_ai_scroll_to_latest),
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(180f),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CombinedAiBottomBar(
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    isLoading: Boolean,
    selectedFacilityName: String?,
    onClearSelectedFacility: () -> Unit,
    onSubmit: (String) -> Unit,
    palette: CombinedPalette,
    modifier: Modifier = Modifier,
) {
    // The page above already ends at the keyboard, so the bar keeps one height while it moves.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                // The conversation dissolves toward the composer but never behind a solid bar:
                // only the capsule and the facility chip are opaque, and their sides stay faintly
                // see-through all the way down.
                val fadeTop = -CombinedAiComposerFade.toPx()
                val fadeEnd = 40.dp.toPx()
                val fade = Brush.verticalGradient(
                    0f to palette.page.copy(alpha = 0f),
                    -fadeTop / (fadeEnd - fadeTop) to palette.page.copy(alpha = .45f),
                    1f to palette.page.copy(alpha = .72f),
                    startY = fadeTop,
                    endY = fadeEnd,
                )
                val fadeSize = Size(size.width, size.height - fadeTop)
                onDrawBehind { drawRect(brush = fade, topLeft = Offset(0f, fadeTop), size = fadeSize) }
            }
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
    ) {
        selectedFacilityName?.let { facilityName ->
            CombinedAiSelectedFacility(facilityName, onClearSelectedFacility, palette)
        }
        CombinedAiComposer(
            value = draft,
            onValueChange = onDraftChange,
            isLoading = isLoading,
            onSubmit = onSubmit,
            palette = palette,
        )
    }
}

/** The facility the next question is about, with a way to stop asking about it. */
@Composable
private fun CombinedAiSelectedFacility(
    facilityName: String,
    onClear: () -> Unit,
    palette: CombinedPalette,
) {
    Row(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .then(if (palette.shadow) Modifier.shadow(1.dp, CircleShape) else Modifier)
            .background(palette.raised, CircleShape)
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_material_symbol_location_on_24),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.ai_selected_facility_label, facilityName),
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag("ai-selected-facility"),
            style = MaterialTheme.typography.labelLarge,
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

/** An open composer: a floating capsule with a soft shadow instead of a boxed bar. */
@Composable
private fun CombinedAiComposer(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isLoading: Boolean,
    onSubmit: (String) -> Unit,
    palette: CombinedPalette,
) {
    val tooLong = isAiPromptTooLong(value.text)
    val canSubmit = value.text.isNotBlank() && !isLoading && !tooLong
    val shape = RoundedCornerShape(28.dp)
    val placeholder = stringResource(R.string.ai_composer_placeholder)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (palette.shadow) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = .5f),
                        spotColor = Color.Black.copy(alpha = .4f),
                    )
                } else {
                    Modifier
                },
            )
            .background(palette.raised, shape)
            .testTag("ai-composer")
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("ai-composer-input"),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 1,
                maxLines = 5,
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (tooLong) {
                // Sending would be refused, so the draft stays as it is and says why.
                AiPromptLengthNotice(text = value.text, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = if (canSubmit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            IconButton(
                onClick = { onSubmit(value.text.trim()) },
                enabled = canSubmit,
                modifier = Modifier.testTag("ai-send-button"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_material_symbol_arrow_upward_24),
                    contentDescription = stringResource(R.string.ai_send),
                    modifier = Modifier.size(22.dp),
                    tint = if (canSubmit) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
