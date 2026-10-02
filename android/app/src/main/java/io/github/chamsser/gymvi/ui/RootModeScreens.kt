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
    // AI settings live in 내 정보, so this page has no settings state of its own.
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

private suspend fun LazyListState.scrollToConversationEnd(lastIndex: Int) {
    if (lastIndex < 0) return
    scrollToItem(lastIndex)
    // A tall final reply can exceed the viewport; scroll past its top so its end is visible.
    scrollBy(ConversationEndScrollDistance)
}

private const val ConversationEndScrollDistance = 100_000f

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
}

/** The facts of one usage option, with unknown values already resolved to their labels. */
internal data class OptionCardFacts(
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
internal fun optionCardFacts(card: AiTurnCard): OptionCardFacts {
    val option = card.option
    val time = option.operatorTime?.let { "${it.startTime}~${it.endTime}" }
        ?: option.sourceTimeValue
        ?: stringResource(R.string.usage_option_time_unknown)
    val price = formatUsageOptionPrice(option.priceWon)
    return OptionCardFacts(
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

internal fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

