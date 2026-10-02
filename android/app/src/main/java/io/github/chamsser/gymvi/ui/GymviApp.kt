package io.github.chamsser.gymvi.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chamsser.gymvi.BuildConfig
import io.github.chamsser.gymvi.GymviApplication
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AddressSearchItem
import io.github.chamsser.gymvi.data.AiClientActionType
import io.github.chamsser.gymvi.data.AiPreferences
import io.github.chamsser.gymvi.data.AiPreferencesStore
import io.github.chamsser.gymvi.data.AiRequestOrigin
import io.github.chamsser.gymvi.data.AiTurnCard
import io.github.chamsser.gymvi.data.AiTurnRequestContext
import io.github.chamsser.gymvi.data.DiscoveryFeedPage
import io.github.chamsser.gymvi.data.EvidenceState
import io.github.chamsser.gymvi.data.ExerciseContentItem
import io.github.chamsser.gymvi.data.FacilityBounds
import io.github.chamsser.gymvi.data.FacilityImageCandidate
import io.github.chamsser.gymvi.data.FacilityMapItem
import io.github.chamsser.gymvi.data.FacilityPersonalization
import io.github.chamsser.gymvi.data.FacilityPersonalizationStore
import io.github.chamsser.gymvi.data.MetaVersionApiClient
import io.github.chamsser.gymvi.data.MetaVersionInfo
import io.github.chamsser.gymvi.data.UsageOptionItem
import io.github.chamsser.gymvi.data.FeedUsageOption
import coil3.compose.AsyncImage
import io.github.chamsser.gymvi.data.FacilityOperatingHours
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class AiNavigationTarget {
    FOCUS_MAP,
    SELECT_DESTINATION,
    PREVIEW_ROUTE,
}

private data class PendingAiNavigation(
    val card: AiTurnCard,
    val target: AiNavigationTarget,
    val loadStarted: Boolean = false,
)

internal fun approximateAiCoordinate(value: Double): Double =
    (value * 100.0).roundToInt() / 100.0

@Composable
fun GymviRoute() {
    val context = LocalContext.current
    var locationControlState by remember {
        mutableStateOf(CurrentLocationControlState.IDLE)
    }
    var isFollowingLocation by remember { mutableStateOf(false) }
    var locationRequestKey by rememberSaveable { mutableIntStateOf(0) }
    var pendingSearchBounds by remember { mutableStateOf<FacilityBounds?>(null) }
    var routeOrigin by remember { mutableStateOf<RouteCoordinate?>(null) }
    var mapSearchTarget by remember { mutableStateOf<AddressSearchItem?>(null) }
    var facilitySearchFocus by remember { mutableStateOf<MapSearchFocus?>(null) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            pendingSearchBounds = null
            isFollowingLocation = true
            locationControlState = CurrentLocationControlState.REQUESTING
            locationRequestKey += 1
        } else {
            isFollowingLocation = false
            locationControlState = CurrentLocationControlState.DENIED
        }
    }
    val requestCurrentLocation = {
        pendingSearchBounds = null
        if (context.hasAnyLocationPermission()) {
            isFollowingLocation = true
            locationControlState = CurrentLocationControlState.REQUESTING
            locationRequestKey += 1
        } else {
            isFollowingLocation = true
            locationControlState = CurrentLocationControlState.REQUESTING
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
    // Read before the launch effect requests a location, so the first frame already shows loading.
    val launchedWithLocation = remember(context) { context.hasAnyLocationPermission() }
    LaunchedEffect(context) {
        if (shouldStartLocationTrackingOnLaunch(context.hasAnyLocationPermission(), locationRequestKey)) {
            pendingSearchBounds = null
            isFollowingLocation = true
            locationControlState = CurrentLocationControlState.REQUESTING
            locationRequestKey += 1
        }
    }
    val hasMapCredential = BuildConfig.NAVER_MAPS_CLIENT_ID.isNotBlank()
    val controller = remember(BuildConfig.API_BASE_URL, hasMapCredential) {
        MapScreenController(
            apiBaseUrl = BuildConfig.API_BASE_URL,
            hasMapCredential = hasMapCredential,
        )
    }
    var hasLoadedInitialFacilities by remember(controller) { mutableStateOf(false) }
    val liveUiController = remember(BuildConfig.LIVE_UI_ENDPOINT, BuildConfig.LIVE_UI_TOKEN) {
        LiveUiController(
            endpoint = BuildConfig.LIVE_UI_ENDPOINT,
            token = BuildConfig.LIVE_UI_TOKEN,
        )
    }
    val routeController = remember(BuildConfig.API_BASE_URL) {
        RoutePreviewController(BuildConfig.API_BASE_URL)
    }
    val addressSearchController = remember(BuildConfig.API_BASE_URL) {
        AddressSearchController(BuildConfig.API_BASE_URL)
    }
    val facilityMediaController = remember(BuildConfig.API_BASE_URL) {
        FacilityMediaController(BuildConfig.API_BASE_URL)
    }
    val usageOptionController = remember(BuildConfig.API_BASE_URL) {
        UsageOptionController(BuildConfig.API_BASE_URL)
    }
    val discoveryFeedController = remember(BuildConfig.API_BASE_URL) {
        DiscoveryFeedController(BuildConfig.API_BASE_URL)
    }
    // Production uses the process-scoped controller; only a non-Gymvi host owns a local one.
    val processAiConversationController = remember(context) {
        (context.applicationContext as? GymviApplication)?.aiConversationController
    }
    val aiConversationController = remember(processAiConversationController) {
        processAiConversationController ?: AiConversationController(BuildConfig.API_BASE_URL)
    }
    val ownsAiConversationController = processAiConversationController == null
    val personalizationStore = remember(context) { FacilityPersonalizationStore(context) }
    val aiPreferencesStore = remember(context) { AiPreferencesStore(context) }
    var uiState by remember(controller) { mutableStateOf(controller.currentState) }
    var liveUiState by remember(liveUiController) {
        mutableStateOf(liveUiController.currentState)
    }
    var routePreviewState by remember(routeController) {
        mutableStateOf(routeController.currentState)
    }
    var addressSearchState by remember(addressSearchController) {
        mutableStateOf(addressSearchController.currentState)
    }
    var facilityMediaState by remember(facilityMediaController) {
        mutableStateOf(facilityMediaController.currentState)
    }
    var usageOptionListState by remember(usageOptionController) {
        mutableStateOf(usageOptionController.currentListState)
    }
    var usageOptionEvidenceState by remember(usageOptionController) {
        mutableStateOf(usageOptionController.currentEvidenceState)
    }
    var usageOptionCompareState by remember(usageOptionController) {
        mutableStateOf(usageOptionController.currentCompareState)
    }
    var discoveryFeedState by remember(discoveryFeedController) {
        mutableStateOf(discoveryFeedController.currentState)
    }
    var aiConversationState by remember(aiConversationController) {
        mutableStateOf(aiConversationController.currentState)
    }
    var personalization by remember(personalizationStore) {
        mutableStateOf(personalizationStore.load())
    }
    var aiPreferences by remember(aiPreferencesStore) {
        mutableStateOf(aiPreferencesStore.load())
    }

    DisposableEffect(
        controller,
        liveUiController,
        routeController,
        addressSearchController,
        facilityMediaController,
        usageOptionController,
        discoveryFeedController,
        aiConversationController,
    ) {
        controller.observe { uiState = it }
        liveUiController.observe { liveUiState = it }
        routeController.observe { routePreviewState = it }
        addressSearchController.observe { addressSearchState = it }
        facilityMediaController.observe { facilityMediaState = it }
        usageOptionController.observeList { usageOptionListState = it }
        usageOptionController.observeEvidence { usageOptionEvidenceState = it }
        usageOptionController.observeCompare { usageOptionCompareState = it }
        discoveryFeedController.observe { discoveryFeedState = it }
        aiConversationController.observe { aiConversationState = it }
        liveUiController.start()
        onDispose {
            controller.close()
            liveUiController.close()
            routeController.close()
            addressSearchController.close()
            facilityMediaController.close()
            usageOptionController.close()
            discoveryFeedController.close()
            if (ownsAiConversationController) {
                aiConversationController.close()
            } else {
                // The process-scoped conversation outlives this Activity; only stop observing it.
                aiConversationController.observe(null)
            }
        }
    }

    LaunchedEffect(aiConversationController, aiPreferences) {
        aiConversationController.setPreferences(aiPreferences)
    }

    val preferredFeedCategories = remember(aiPreferences.useAiMemory, aiConversationState.library.memories) {
        if (aiPreferences.useAiMemory) mergeAiMemories(aiConversationState.library.memories.sortedByDescending { it.updatedAt })
            ?.categories.orEmpty() else emptySet()
    }
    LaunchedEffect(
        uiState.completedFacilityRequestId,
        personalization.favoriteFacilityIds,
        personalization.recentFacilityIds,
        preferredFeedCategories,
    ) {
        if (
            uiState.completedFacilityRequestId > 0 &&
            uiState.facilityLoadState == FacilityLoadState.READY
        ) {
            discoveryFeedController.load(
                bounds = controller.currentBounds,
                localHour = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).hour,
                favoriteFacilityIds = personalization.favoriteFacilityIds,
                recentFacilityIds = personalization.recentFacilityIds,
                preferredCategories = preferredFeedCategories,
            )
        }
    }
    // A recent facility saved before names were kept, or recorded before its data loaded, takes
    // its name from the facilities the app has loaded since.
    LaunchedEffect(uiState.facilities, personalization.recentFacilityIds) {
        personalization = personalizationStore.fillRecentNames(personalization) { facilityId ->
            uiState.facilities.firstOrNull { it.facilityId == facilityId }?.name
        }
    }

    val mapContent:
        (@Composable (Modifier, List<FacilityMapItem>, String?, List<RouteCoordinate>, () -> Dp, Boolean, Boolean) -> Unit)? =
        if (hasMapCredential) {
            { modifier, visibleFacilities, visibleSelectedFacilityId, routePath, logoClearance, isInteractive, isFacilitySearchActive ->
                NaverMapHost(
                    facilities = visibleFacilities,
                    selectedFacilityId = visibleSelectedFacilityId,
                    routePath = routePath,
                    searchTarget = mapSearchTarget,
                    facilitySearchFocus = facilitySearchFocus,
                    isFacilitySearchActive = isFacilitySearchActive,
                    accessibilityLabel = stringResource(
                        R.string.map_accessibility_label,
                        visibleFacilities.size,
                    ),
                    onMapLoaded = controller::onMapLoaded,
                    onSearchAreaChanged = { bounds -> pendingSearchBounds = bounds },
                    onLocationTrackingStopped = {
                        isFollowingLocation = false
                        locationControlState = CurrentLocationControlState.IDLE
                    },
                    onLocationChanged = { latitude, longitude ->
                        routeOrigin = RouteCoordinate(latitude, longitude)
                        if (!hasLoadedInitialFacilities) {
                            hasLoadedInitialFacilities = true
                            controller.loadInitialFacilities(latitude, longitude)
                        }
                        if (isFollowingLocation) {
                            locationControlState = CurrentLocationControlState.ACTIVE
                        }
                    },
                    onAuthFailed = controller::onMapAuthFailed,
                    onFacilitySelected = { facilityId ->
                        mapSearchTarget = null
                        controller.selectFacility(facilityId)
                    },
                    bottomLogoClearance = logoClearance,
                    locationRequestKey = locationRequestKey,
                    isInteractive = isInteractive,
                    modifier = modifier,
                )
            }
        } else {
            null
        }

    val savedConversationSummaries = remember(aiConversationState.library.conversations) {
        aiConversationState.library.conversations.sortedByDescending { it.updatedAt }.map {
            io.github.chamsser.gymvi.data.AiSavedConversationSummary(it.id, it.title, it.archived, it.updatedAt)
        }
    }
    CompositionLocalProvider(
        LocalAiStorageStatus provides AiStorageStatus(loaded = aiConversationState.libraryLoaded,
            readOnly = aiConversationState.storageError == "AI_LIBRARY_READ_FAILED",
            error = aiConversationState.storageError),
        LocalAiReferencePreferences provides aiPreferences,
        LocalAiLibraryActions provides AiLibraryActions(
            conversations = savedConversationSummaries,
            currentId = aiConversationState.localConversationId, savingEnabled = aiPreferences.saveConversationHistory,
            onOpen = aiConversationController::openConversation, onRename = aiConversationController::renameConversation,
            onArchive = aiConversationController::archiveConversation, onDelete = aiConversationController::deleteConversation),
        LocalAiPersonalizationActions provides AiPersonalizationActions(
            profile = aiConversationState.library.profile, memories = aiConversationState.library.memories,
            onProfileChanged = aiConversationController::updateProfile, onMemoryChanged = aiConversationController::updateMemory,
            onMemoryDeleted = aiConversationController::deleteMemory),
    ) {
    GymviApp(
        uiState = uiState,
        showFirstRunOnboarding = true,
        liveUiState = liveUiState,
        onRetry = controller::retry,
        onFacilityLoadMore = controller::loadMoreFacilities,
        onFacilitySelected = controller::selectFacility,
        onFacilitySelectionCleared = controller::clearFacilitySelection,
        locationControlState = locationControlState,
        onCurrentLocationRequested = requestCurrentLocation,
        isAwaitingFirstLocation = isWaitingForFirstLocation(launchedWithLocation, locationRequestKey),
        routeOrigin = routeOrigin,
        routePreviewState = routePreviewState,
        addressSearchState = addressSearchState,
        facilityMediaState = facilityMediaState,
        usageOptionListState = usageOptionListState,
        usageOptionEvidenceState = usageOptionEvidenceState,
        usageOptionCompareState = usageOptionCompareState,
        discoveryFeedState = discoveryFeedState,
        onFacilityMediaRequested = facilityMediaController::load,
        onUsageOptionsRequested = usageOptionController::load,
        onUsageOptionsRetry = usageOptionController::retry,
        onUsageOptionEvidenceRequested = usageOptionController::loadEvidence,
        onUsageOptionEvidenceRetry = usageOptionController::retryEvidence,
        onUsageOptionCompareRequested = usageOptionController::compare,
        onUsageOptionCompareRetry = usageOptionController::retryCompare,
        onAddressSearchRequested = addressSearchController::search,
        onAddressSearchCleared = addressSearchController::clear,
        onFacilitySearchSubmitted = { query, sort ->
            val visibleBounds = pendingSearchBounds
            mapSearchTarget = null
            facilitySearchFocus = null
            pendingSearchBounds = null
            controller.clearFacilitySelection()
            controller.searchNearby(
                routeOrigin?.latitude,
                routeOrigin?.longitude,
                query,
                sort,
                movedArea = visibleBounds,
            )
        },
        onFacilityCategorySearchSubmitted = { query, sort ->
            val visibleBounds = pendingSearchBounds
            mapSearchTarget = null
            facilitySearchFocus = null
            pendingSearchBounds = null
            controller.clearFacilitySelection()
            controller.searchCategory(
                query = query,
                sort = sort,
                movedArea = visibleBounds,
                latitude = routeOrigin?.latitude,
                longitude = routeOrigin?.longitude,
            )
        },
        onFacilitySearchFocusChanged = { focus -> facilitySearchFocus = focus },
        selectedMapAddress = mapSearchTarget,
        onMapAddressSelected = { address ->
            mapSearchTarget = address
            facilitySearchFocus = null
            pendingSearchBounds = null
            controller.clearFacilitySelection()
            controller.searchVisibleBounds(
                bounds = address.nearbyFacilityBounds(),
                sort = FacilityResultSort.DISTANCE,
            )
        },
        onMapAddressCleared = { mapSearchTarget = null },
        onRoutePreviewRequested = routeController::preview,
        onRoutePreviewCleared = routeController::clear,
        personalization = personalization,
        aiConversationState = aiConversationState,
        aiPreferences = aiPreferences,
        onAiPromptSubmitted = { message, preferences ->
            val bounds = controller.currentBounds
            aiConversationController.setPreferences(preferences)
            aiConversationController.submit(
                message = message,
                context = AiTurnRequestContext(
                    area = bounds,
                    date = LocalDate.now(ZoneId.of("Asia/Seoul")),
                    favoriteFacilityIds = personalization.favoriteFacilityIds,
                    recentFacilityIds = personalization.recentFacilityIds,
                ),
                origin = if (preferences.useApproximateRegion) {
                    routeOrigin?.let { currentLocation ->
                        AiRequestOrigin(
                            latitude = approximateAiCoordinate(currentLocation.latitude),
                            longitude = approximateAiCoordinate(currentLocation.longitude),
                        )
                    }
                } else {
                    null
                },
            )
        },
        onAiNewConversation = aiConversationController::newConversation,
        onAiClientActionConsumed = aiConversationController::consumeClientAction,
        onAiFacilityRequested = { card ->
            controller.focusFacility(
                facilityId = card.option.facilityId,
                latitude = card.latitude,
                longitude = card.longitude,
            )
        },
        onAiPreferencesChanged = { updatedPreferences ->
            aiPreferences = updatedPreferences
            aiPreferencesStore.save(updatedPreferences)
        },
        onRecentFacilityRecorded = { facilityId ->
            // The name comes from the facilities already loaded; nothing is looked up for it.
            val facilityName = uiState.facilities.firstOrNull { it.facilityId == facilityId }?.name
            personalization = personalizationStore.recordRecent(personalization, facilityId, facilityName)
        },
        onRecentFacilityRemoved = { facilityId ->
            personalization = personalizationStore.removeRecent(personalization, facilityId)
        },
        onRecentFacilitiesCleared = {
            personalization = personalizationStore.clearRecent(personalization)
        },
        onFavoriteToggled = { facilityId ->
            personalization = personalizationStore.toggleFavorite(personalization, facilityId)
        },
        onPhoneRequested = { facility -> openFacilityPhone(context, facility) },
        onShareRequested = { facility -> shareFacility(context, facility) },
        isSearchAreaChanged = pendingSearchBounds != null,
        onSearchThisArea = { query, sort ->
            pendingSearchBounds?.let { bounds ->
                controller.searchVisibleBounds(bounds, query, sort)
            }
            pendingSearchBounds = null
        },
        mapContent = mapContent,
    )
    }
}

@Composable
fun GymviApp(
    uiState: MapUiState = MapUiState(),
    showFirstRunOnboarding: Boolean = false,
    liveUiState: LiveUiRuntimeState = LiveUiRuntimeState.Disabled,
    onRetry: () -> Unit = {},
    onFacilityLoadMore: () -> Unit = {},
    onFacilitySelected: (String) -> Unit = {},
    onFacilitySelectionCleared: () -> Unit = {},
    locationControlState: CurrentLocationControlState = CurrentLocationControlState.IDLE,
    onCurrentLocationRequested: () -> Unit = {},
    isAwaitingFirstLocation: Boolean = true,
    routeOrigin: RouteCoordinate? = null,
    isSearchAreaChanged: Boolean = false,
    onSearchThisArea: (String?, FacilityResultSort) -> Unit = { _, _ -> },
    routePreviewState: RoutePreviewState = RoutePreviewState.Idle,
    addressSearchState: AddressSearchState = AddressSearchState.Idle,
    facilityMediaState: FacilityMediaState = FacilityMediaState.Idle,
    usageOptionListState: UsageOptionListState = UsageOptionListState.Idle,
    usageOptionEvidenceState: UsageOptionEvidenceState = UsageOptionEvidenceState.Idle,
    usageOptionCompareState: UsageOptionCompareState = UsageOptionCompareState.Idle,
    discoveryFeedState: DiscoveryFeedState = DiscoveryFeedState.Idle,
    onFacilityMediaRequested: (String) -> Unit = {},
    onUsageOptionsRequested: (String) -> Unit = {},
    onUsageOptionsRetry: (String) -> Unit = {},
    onUsageOptionEvidenceRequested: (UsageOptionItem) -> Unit = {},
    onUsageOptionEvidenceRetry: (UsageOptionItem) -> Unit = {},
    onUsageOptionCompareRequested: (List<String>) -> Unit = {},
    onUsageOptionCompareRetry: () -> Unit = {},
    onAddressSearchRequested: (String) -> Unit = {},
    onAddressSearchCleared: () -> Unit = {},
    onFacilitySearchSubmitted: (String, FacilityResultSort) -> Unit = { _, _ -> },
    onFacilityCategorySearchSubmitted: (String, FacilityResultSort) -> Unit = { _, _ -> },
    onFacilitySearchFocusChanged: (MapSearchFocus?) -> Unit = {},
    selectedMapAddress: AddressSearchItem? = null,
    onMapAddressSelected: (AddressSearchItem) -> Unit = {},
    onMapAddressCleared: () -> Unit = {},
    onRoutePreviewRequested: (RouteCoordinate, RouteCoordinate) -> Unit = { _, _ -> },
    onRoutePreviewCleared: () -> Unit = {},
    personalization: FacilityPersonalization = FacilityPersonalization(),
    aiConversationState: AiConversationUiState = AiConversationUiState(),
    aiPreferences: AiPreferences = AiPreferences(),
    onAiPromptSubmitted: (String, AiPreferences) -> Unit = { _, _ -> },
    onAiNewConversation: () -> Unit = {},
    onAiClientActionConsumed: (Long) -> Unit = {},
    onAiFacilityRequested: (AiTurnCard) -> Unit = {},
    onAiPreferencesChanged: (AiPreferences) -> Unit = {},
    onRecentFacilityRecorded: (String) -> Unit = {},
    onRecentFacilityRemoved: (String) -> Unit = {},
    onRecentFacilitiesCleared: () -> Unit = {},
    onFavoriteToggled: (String) -> Unit = {},
    onPhoneRequested: (FacilityMapItem) -> Unit = {},
    onShareRequested: (FacilityMapItem) -> Unit = {},
    onRouteRequested: (FacilityMapItem?) -> Unit = {},
    onExitRequested: (() -> Unit)? = null,
    mapContent:
        (@Composable (Modifier, List<FacilityMapItem>, String?, List<RouteCoordinate>, () -> Dp, Boolean, Boolean) -> Unit)? = null,
) {
    val liveSpec = liveUiState.spec
    val nativeDesign = rememberAppNativeDesign()
    var currentAiPreferences by remember(aiPreferences) { mutableStateOf(aiPreferences) }
    var aiInputValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var rootMode by rememberSaveable { mutableStateOf(nativeDesign.homeMode) }
    val metaVersionClient = remember(BuildConfig.API_BASE_URL) { MetaVersionApiClient(BuildConfig.API_BASE_URL) }
    // Asked again each time MY opens; MY keeps the last answer until the next one replaces all of it,
    // so the AI model and the data sources it shows always come from the same answer.
    val metaVersion by produceState(MetaVersionInfo.UNKNOWN, metaVersionClient, rootMode == RootMode.MY) {
        if (rootMode == RootMode.MY) {
            value = withContext(Dispatchers.IO) { metaVersionClient.fetchMetaVersion() }
        }
    }
    val aiPageState = rememberSaveableStateHolder()
    var returnToAiConversation by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var activeCategoryName by rememberSaveable { mutableStateOf<String?>(null) }
    var isRouteOpen by rememberSaveable { mutableStateOf(false) }
    var openUsageOptionListFacilityId by rememberSaveable { mutableStateOf<String?>(null) }
    var openUsageOptionDetail by remember { mutableStateOf<UsageOptionItem?>(null) }
    var openUsageOptionDetailName by remember { mutableStateOf<String?>(null) }
    var openUsageOptionDetailFromFeed by remember { mutableStateOf(false) }
    var openUsageOptionCompareIds by remember { mutableStateOf<List<String>?>(null) }
    var decidedUsageOptionId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAiNavigation by remember { mutableStateOf<PendingAiNavigation?>(null) }
    var routeMapPickTarget by rememberSaveable { mutableStateOf<RouteMapPickTarget?>(null) }
    var routeSheetHeight by remember { mutableStateOf<Dp?>(null) }
    var routeOriginFacilityId by rememberSaveable { mutableStateOf<String?>(null) }
    var routeDestinationId by rememberSaveable { mutableStateOf<String?>(null) }
    var routeOriginSnapshot by remember { mutableStateOf<RouteCoordinate?>(null) }
    var routeOriginAddress by remember { mutableStateOf<AddressSearchItem?>(null) }
    var routeDestinationAddress by remember { mutableStateOf<AddressSearchItem?>(null) }
    var sheetStage by remember { mutableStateOf(FacilitySheetStage.COLLAPSED) }
    var sheetCollapseRequestKey by remember { mutableIntStateOf(0) }
    var sheetExpandRequestKey by remember { mutableIntStateOf(0) }
    var lastExitPromptAtMillis by remember { mutableLongStateOf(0L) }
    var resultSortName by rememberSaveable {
        mutableStateOf(FacilityResultSort.RELEVANCE.name)
    }
    var ownershipFilterName by rememberSaveable {
        mutableStateOf(FacilityOwnershipFilter.ANY.name)
    }
    var searchSubmissionSerial by rememberSaveable { mutableIntStateOf(0) }
    var lastHandledSearchFocusRequestId by rememberSaveable { mutableIntStateOf(0) }
    val activeCategory = FacilityCategory.entries.firstOrNull { category ->
        category.name == activeCategoryName
    }
    val normalizedQuery = searchQuery.trim()
    val matchedSearchFacilities = remember(uiState.facilities, normalizedQuery, activeCategory) {
        filterFacilitiesForSearch(
            facilities = uiState.facilities,
            query = normalizedQuery,
            selectedCategory = activeCategory,
        )
    }
    val resultSort = FacilityResultSort.valueOf(resultSortName)
    // Without a device location, nearness is measured from the area the results came from.
    val searchOrigin = routeOrigin ?: uiState.completedFacilityRequestArea?.center()
    val sortOrigin = facilitySortOrigin(resultSort, searchOrigin)
    val ownershipFilter = FacilityOwnershipFilter.valueOf(ownershipFilterName)
    val searchFacilities = remember(
        matchedSearchFacilities,
        normalizedQuery,
        activeCategory,
        resultSort,
        ownershipFilter,
        sortOrigin,
    ) {
        sortFacilitySearchResults(
            facilities = filterFacilitiesByOwnership(
                facilities = matchedSearchFacilities,
                filter = ownershipFilter,
            ),
            query = normalizedQuery,
            selectedCategory = activeCategory,
            sort = resultSort,
            origin = sortOrigin,
        )
    }
    val visibleUiState = remember(uiState, searchFacilities) {
        uiState.copy(
            facilities = searchFacilities,
            totalFacilityCount = if (searchFacilities.size < uiState.facilities.size) {
                searchFacilities.size
            } else {
                uiState.totalFacilityCount
            },
            selectedFacilityId = uiState.selectedFacilityId
                ?.takeIf { selectedId ->
                    searchFacilities.any { it.facilityId == selectedId }
                },
        )
    }
    val routeDestination = routeDestinationId?.let { destinationId ->
        uiState.facilities.firstOrNull { it.facilityId == destinationId }
    }
    val routeOriginFacility = routeOriginFacilityId?.let { originId ->
        uiState.facilities.firstOrNull { it.facilityId == originId }
    }
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val isKeyboardVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val context = LocalContext.current
    var showOnboarding by rememberSaveable { mutableStateOf(showFirstRunOnboarding && !onboardingCompleted(context)) }
    val activity = remember(context) { context.findActivity() }
    val exitHint = stringResource(R.string.back_again_to_exit)
    val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
    val isInitialNavigationState = !isRouteOpen &&
        openUsageOptionDetail == null &&
        openUsageOptionCompareIds == null &&
        openUsageOptionListFacilityId == null &&
        !isSearchOpen &&
        rootMode == RootMode.MAP &&
        uiState.selectedFacilityId == null &&
        selectedMapAddress == null &&
        normalizedQuery.isEmpty() &&
        activeCategoryName == null &&
        sheetStage == FacilitySheetStage.COLLAPSED

    LaunchedEffect(uiState.selectedFacilityId, sheetStage) {
        val selectedFacilityId = uiState.selectedFacilityId
        if (selectedFacilityId != null && sheetStage != FacilitySheetStage.COLLAPSED) {
            onFacilityMediaRequested(selectedFacilityId)
            onUsageOptionsRequested(selectedFacilityId)
        }
    }

    LaunchedEffect(isRouteOpen, routeOrigin) {
        if (!isRouteOpen) {
            routeOriginSnapshot = null
        } else if (routeOriginSnapshot == null) {
            // Keep the route's origin while map picking temporarily removes its screen.
            routeOriginSnapshot = routeOrigin
        }
    }

    LaunchedEffect(openUsageOptionDetail?.usageOptionId) {
        openUsageOptionDetail?.let(onUsageOptionEvidenceRequested)
    }

    LaunchedEffect(uiState.selectedFacilityId) {
        val selectedId = uiState.selectedFacilityId
        if (!openUsageOptionDetailFromFeed && openUsageOptionDetail?.facilityId != selectedId) {
            openUsageOptionDetail = null
            openUsageOptionDetailName = null
        }
        if (openUsageOptionListFacilityId != selectedId) openUsageOptionListFacilityId = null
    }

    LaunchedEffect(openUsageOptionListFacilityId, usageOptionListState, uiState.facilities) {
        val facilityId = openUsageOptionListFacilityId ?: return@LaunchedEffect
        val hasPage = usageOptionListState.forFacility(facilityId) is UsageOptionListState.Ready
        val hasFacility = uiState.facilities.any { it.facilityId == facilityId }
        if (!hasPage || !hasFacility) openUsageOptionListFacilityId = null
    }

    LaunchedEffect(
        openUsageOptionDetail?.usageOptionId,
        openUsageOptionDetail?.facilityId,
        uiState.facilities,
    ) {
        val option = openUsageOptionDetail ?: return@LaunchedEffect
        if (uiState.facilities.none { it.facilityId == option.facilityId }) {
            openUsageOptionDetail = null
        }
    }

    fun showRouteToDestination(facility: FacilityMapItem) {
        routeOriginFacilityId = null
        routeDestinationId = facility.facilityId
        routeOriginAddress = null
        routeDestinationAddress = null
        routeMapPickTarget = null
        isRouteOpen = true
        onRoutePreviewCleared()
    }

    fun beginAiNavigation(card: AiTurnCard, target: AiNavigationTarget) {
        returnToAiConversation = rootMode == RootMode.AI || returnToAiConversation
        rootMode = RootMode.MAP
        isRouteOpen = false
        isSearchOpen = false
        // A previous category/query must not filter the newly chosen AI destination out.
        searchQuery = ""
        activeCategoryName = null
        resultSortName = FacilityResultSort.RELEVANCE.name
        ownershipFilterName = FacilityOwnershipFilter.ANY.name
        onMapAddressCleared()
        pendingAiNavigation = PendingAiNavigation(card, target)
        onFacilitySearchFocusChanged(
            MapSearchFocus(
                key = "ai:${card.option.usageOptionId}:${target.name}",
                latitude = card.latitude,
                longitude = card.longitude,
            ),
        )
        if (uiState.facilities.any { it.facilityId == card.option.facilityId }) {
            onFacilitySelected(card.option.facilityId)
        } else {
            onAiFacilityRequested(card)
        }
    }

    LaunchedEffect(aiConversationState.pendingClientAction?.id) {
        val event = aiConversationState.pendingClientAction ?: return@LaunchedEffect
        val action = event.actions.firstOrNull { it.type == AiClientActionType.OPEN_USAGE_OPTION_COMPARISON }
            ?: event.actions.firstOrNull { it.type == AiClientActionType.PREVIEW_ROUTE }
            ?: event.actions.firstOrNull { it.type == AiClientActionType.SELECT_USAGE_OPTION }
            ?: event.actions.firstOrNull { it.type == AiClientActionType.FOCUS_MAP }
            ?: event.actions.firstOrNull { it.type == AiClientActionType.OPEN_MANUAL_FILTERS }
        when (action?.type) {
            AiClientActionType.OPEN_USAGE_OPTION_COMPARISON -> {
                val ids = action.optionIds
                if (ids.size in 2..3 && ids.all { id ->
                        event.cards.any { card -> card.option.usageOptionId == id }
                    }
                ) {
                    returnToAiConversation = rootMode == RootMode.AI || returnToAiConversation
                    rootMode = RootMode.MAP
                    isRouteOpen = false
                    isSearchOpen = false
                    openUsageOptionCompareIds = ids
                    onUsageOptionCompareRequested(ids)
                }
            }
            AiClientActionType.PREVIEW_ROUTE,
            AiClientActionType.SELECT_USAGE_OPTION,
            AiClientActionType.FOCUS_MAP,
            -> {
                val card = event.cards.firstOrNull {
                    it.option.usageOptionId == action.optionId &&
                        it.option.facilityId == action.facilityId
                }
                if (card != null) {
                    val target = when (action.type) {
                        AiClientActionType.PREVIEW_ROUTE -> AiNavigationTarget.PREVIEW_ROUTE
                        AiClientActionType.SELECT_USAGE_OPTION -> AiNavigationTarget.SELECT_DESTINATION
                        AiClientActionType.FOCUS_MAP -> AiNavigationTarget.FOCUS_MAP
                        AiClientActionType.OPEN_USAGE_OPTION_COMPARISON -> error("Handled separately")
                        AiClientActionType.OPEN_MANUAL_FILTERS -> error("Handled separately")
                    }
                    beginAiNavigation(card, target)
                }
            }
            AiClientActionType.OPEN_MANUAL_FILTERS -> {
                returnToAiConversation = rootMode == RootMode.AI || returnToAiConversation
                rootMode = RootMode.MAP
                isRouteOpen = false
                isSearchOpen = true
            }
            null -> Unit
        }
        onAiClientActionConsumed(event.id)
    }

    LaunchedEffect(uiState.selectedFacilityId, pendingAiNavigation) {
        val pending = pendingAiNavigation ?: return@LaunchedEffect
        val facility = uiState.selectedFacility
            ?.takeIf { it.facilityId == pending.card.option.facilityId }
            ?: return@LaunchedEffect
        pendingAiNavigation = null
        onRecentFacilityRecorded(facility.facilityId)
        when (pending.target) {
            AiNavigationTarget.FOCUS_MAP -> sheetExpandRequestKey += 1
            AiNavigationTarget.SELECT_DESTINATION -> {
                decidedUsageOptionId = pending.card.option.usageOptionId
                sheetExpandRequestKey += 1
            }
            AiNavigationTarget.PREVIEW_ROUTE -> {
                decidedUsageOptionId = pending.card.option.usageOptionId
                showRouteToDestination(facility)
            }
        }
    }

    LaunchedEffect(uiState.facilityLoadState, pendingAiNavigation) {
        val pending = pendingAiNavigation ?: return@LaunchedEffect
        if (uiState.facilityLoadState == FacilityLoadState.LOADING && !pending.loadStarted) {
            pendingAiNavigation = pending.copy(loadStarted = true)
        } else if (
            pending.loadStarted &&
            uiState.facilityLoadState in setOf(
                FacilityLoadState.NETWORK_ERROR,
                FacilityLoadState.API_ERROR,
                FacilityLoadState.INVALID_RESPONSE,
                FacilityLoadState.EMPTY,
            )
        ) {
            pendingAiNavigation = null
        }
    }

    val clearSearchQuery = {
        searchQuery = ""
        activeCategoryName = null
        resultSortName = FacilityResultSort.RELEVANCE.name
        ownershipFilterName = FacilityOwnershipFilter.ANY.name
        onAddressSearchCleared()
        onMapAddressCleared()
        onFacilitySearchFocusChanged(null)
    }

    LaunchedEffect(isInitialNavigationState) {
        if (!isInitialNavigationState) lastExitPromptAtMillis = 0L
    }
    LaunchedEffect(isSearchOpen, normalizedQuery) {
        if (isSearchOpen) onAddressSearchRequested(normalizedQuery)
    }
    LaunchedEffect(isSearchOpen, isRouteOpen) {
        if (!isSearchOpen && !isRouteOpen) onAddressSearchCleared()
    }
    LaunchedEffect(
        uiState.completedFacilityRequestId,
        uiState.completedFacilityRequestOrigin,
        uiState.selectedFacilityId,
        isSearchOpen,
        normalizedQuery,
        searchFacilities,
    ) {
        val completedRequestId = uiState.completedFacilityRequestId
        if (
            completedRequestId <= lastHandledSearchFocusRequestId ||
            !shouldMoveCameraForFacilityRequest(uiState.completedFacilityRequestOrigin)
        ) return@LaunchedEffect
        val nearest = when (uiState.completedFacilityRequestOrigin) {
            FacilityRequestOrigin.AI_RECOMMENDATION -> uiState.selectedFacility
            FacilityRequestOrigin.SEARCH_BAR -> if (!isSearchOpen && normalizedQuery.isNotEmpty()) {
                nearestFacilityForSearch(searchFacilities, searchOrigin)
            } else {
                null
            }
            else -> null
        } ?: return@LaunchedEffect
        lastHandledSearchFocusRequestId = completedRequestId
        onFacilitySearchFocusChanged(
            MapSearchFocus(
                key = "$completedRequestId:$normalizedQuery:${nearest.facilityId}",
                latitude = nearest.latitude,
                longitude = nearest.longitude,
            ),
        )
    }
    fun returnToChatIfNeeded() {
        if (returnToAiConversation) {
            pendingAiNavigation = null
            returnToAiConversation = false
            rootMode = RootMode.AI
        }
    }

    fun selectRootMode(mode: RootMode) {
        returnToAiConversation = false
        rootMode = mode
    }

    BackHandler {
        when {
            routeMapPickTarget != null && isSearchOpen -> {
                if (normalizedQuery.isNotEmpty()) {
                    clearSearchQuery()
                } else {
                    isSearchOpen = false
                }
            }
            routeMapPickTarget != null -> {
                routeMapPickTarget = null
                onFacilitySelectionCleared()
                onMapAddressCleared()
                sheetCollapseRequestKey += 1
            }
            isRouteOpen -> {
                isRouteOpen = false
                routeMapPickTarget = null
                routeOriginFacilityId = null
                routeDestinationId = null
                routeOriginAddress = null
                routeDestinationAddress = null
                onRoutePreviewCleared()
                returnToChatIfNeeded()
            }
            openUsageOptionDetail != null -> {
                openUsageOptionDetail = null
                openUsageOptionDetailName = null
            }
            openUsageOptionCompareIds != null -> {
                openUsageOptionCompareIds = null
                returnToChatIfNeeded()
            }
            openUsageOptionListFacilityId != null -> openUsageOptionListFacilityId = null
            isSearchOpen -> {
                if (normalizedQuery.isNotEmpty()) {
                    clearSearchQuery()
                } else {
                    isSearchOpen = false
                }
            }
            returnToAiConversation -> returnToChatIfNeeded()
            rootMode != nativeDesign.homeMode -> rootMode = nativeDesign.homeMode
            uiState.selectedFacilityId != null -> {
                onFacilitySelectionCleared()
                sheetCollapseRequestKey += 1
            }

            selectedMapAddress != null -> {
                onMapAddressCleared()
                sheetCollapseRequestKey += 1
            }

            normalizedQuery.isNotEmpty() || activeCategoryName != null -> {
                clearSearchQuery()
                sheetCollapseRequestKey += 1
            }

            sheetStage != FacilitySheetStage.COLLAPSED -> sheetCollapseRequestKey += 1
            else -> {
                val now = SystemClock.elapsedRealtime()
                if (shouldExitAfterBack(lastExitPromptAtMillis, now)) {
                    onExitRequested?.invoke() ?: activity?.finish()
                } else {
                    lastExitPromptAtMillis = now
                    Toast.makeText(context, exitHint, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val systemDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkTheme = if (nativeDesign.isOriginal) systemDarkTheme else when (nativeDesign.theme) {
        NativeTheme.SYSTEM -> systemDarkTheme
        NativeTheme.LIGHT -> false
        NativeTheme.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (!nativeDesign.isOriginal) {
            nativeDesignColors(nativeDesign, isDarkTheme)
        } else if (isDarkTheme) {
            GymviDarkColors
        } else {
            GymviLightColors
        },
        typography = nativeDesignTypography(nativeDesign),
    ) {
     Box(modifier = Modifier.fillMaxSize()) {
      NativeDesignFrame(
        design = nativeDesign,
        selectedMode = rootMode,
        onModeSelected = ::selectRootMode,
        onSearchRequested = { rootMode = RootMode.MAP; isSearchOpen = true },
        showNavigation = !isRouteOpen && !isSearchOpen && openUsageOptionDetail == null &&
            openUsageOptionListFacilityId == null && openUsageOptionCompareIds == null,
      ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .reviewRootSemantics(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val mapPageOffset by animateFloatAsState(
                    targetValue = when (rootMode) {
                        RootMode.AI -> 1f
                        RootMode.MAP -> 0f
                        RootMode.MY -> -1f
                    },
                    animationSpec = tween(durationMillis = nativeDesign.durationMillis),
                    label = "map-page-horizontal-offset",
                )
                val mapRenderingActive by remember {
                    derivedStateOf {
                        rootMode == RootMode.MAP || isRouteOpen || kotlin.math.abs(mapPageOffset) < .999f
                    }
                }
                val mapUiState = if (isRouteOpen && routeMapPickTarget == null) {
                    val routeFacilities = listOfNotNull(routeOriginFacility, routeDestination)
                        .distinctBy(FacilityMapItem::facilityId)
                    uiState.copy(
                        facilities = routeFacilities,
                        selectedFacilityId = routeDestination?.facilityId
                            ?: routeOriginFacility?.facilityId,
                    )
                } else {
                    visibleUiState
                }
                val routePath = remember(routePreviewState) {
                    (routePreviewState as? RoutePreviewState.Ready)
                        ?.route
                        ?.path
                        ?.map { RouteCoordinate(it.latitude, it.longitude) }
                        .orEmpty()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Keep the native SurfaceView stationary. Only AI/MY content animates.
                        .testTag("map-page-layer"),
                ) {
                  CompositionLocalProvider(LocalMapRenderingActive provides mapRenderingActive) {
                    MapModeScreen(
                    uiState = mapUiState,
                    content = liveSpec?.map,
                    searchQuery = searchQuery,
                    onSearchRequested = { isSearchOpen = true },
                    onSearchCleared = {
                        clearSearchQuery()
                        onFacilitySelectionCleared()
                        sheetCollapseRequestKey += 1
                    },
                    onCategorySearch = { category, label ->
                        activeCategoryName = category.name
                        searchQuery = label
                        resultSortName = FacilityResultSort.RELEVANCE.name
                        ownershipFilterName = FacilityOwnershipFilter.ANY.name
                        onMapAddressCleared()
                        onFacilitySelectionCleared()
                        searchSubmissionSerial += 1
                        onFacilityCategorySearchSubmitted(label, FacilityResultSort.RELEVANCE)
                    },
                    isSearchAreaChanged = isSearchAreaChanged,
                    onSearchThisArea = {
                        onSearchThisArea(
                            normalizedQuery.takeIf(String::isNotEmpty),
                            resultSort,
                        )
                    },
                    onRetry = onRetry,
                    onFacilityLoadMore = onFacilityLoadMore,
                    selectedMode = rootMode,
                    onModeSelected = ::selectRootMode,
                    modes = liveSpec?.navigation?.order ?: RootMode.entries,
                    locationControlState = locationControlState,
                    onCurrentLocationRequested = onCurrentLocationRequested,
                    isAwaitingFirstLocation = isAwaitingFirstLocation,
                    onRouteRequested = {
                        val destination = uiState.selectedFacility
                        routeOriginFacilityId = null
                        routeDestinationId = destination?.facilityId
                        routeOriginAddress = null
                        routeDestinationAddress = null
                        routeMapPickTarget = null
                        isRouteOpen = true
                        onRoutePreviewCleared()
                        onRouteRequested(destination)
                    },
                    showFloatingControls = (!isRouteOpen || routeMapPickTarget != null) &&
                        rootMode == RootMode.MAP &&
                        !isKeyboardVisible &&
                        !isSearchOpen &&
                        openUsageOptionListFacilityId == null &&
                        openUsageOptionCompareIds == null &&
                        openUsageOptionDetail == null,
                    showMapChrome = !isRouteOpen || routeMapPickTarget != null,
                    routeMapPickTarget = routeMapPickTarget,
                    onRouteMapPickCancelled = {
                        routeMapPickTarget = null
                        onFacilitySelectionCleared()
                        onMapAddressCleared()
                        sheetCollapseRequestKey += 1
                    },
                    onRouteMapPickFacilityConfirmed = { facility ->
                        when (routeMapPickTarget) {
                            RouteMapPickTarget.ORIGIN -> {
                                routeOriginFacilityId = facility.facilityId
                                routeOriginAddress = null
                            }
                            RouteMapPickTarget.DESTINATION -> {
                                routeDestinationId = facility.facilityId
                                routeDestinationAddress = null
                            }
                            null -> Unit
                        }
                        routeMapPickTarget = null
                        onRoutePreviewCleared()
                    },
                    onRouteMapPickAddressConfirmed = { address ->
                        when (routeMapPickTarget) {
                            RouteMapPickTarget.ORIGIN -> {
                                routeOriginFacilityId = null
                                routeOriginAddress = address
                            }
                            RouteMapPickTarget.DESTINATION -> {
                                routeDestinationId = null
                                routeDestinationAddress = address
                            }
                            null -> Unit
                        }
                        routeMapPickTarget = null
                        onRoutePreviewCleared()
                    },
                    mapLogoClearanceOverride = if (isRouteOpen && routeMapPickTarget == null) {
                        routeSheetHeight?.let { it + RoutePreviewMapSheetGap } ?: RoutePreviewMapLogoClearance
                    } else {
                        null
                    },
                    isMapInteractive = isRouteOpen ||
                        (rootMode == RootMode.MAP && !isSearchOpen &&
                            openUsageOptionListFacilityId == null && openUsageOptionCompareIds == null &&
                            openUsageOptionDetail == null),
                    routePath = if (routeMapPickTarget == null) routePath else emptyList(),
                    isFacilitySearchActive = normalizedQuery.isNotEmpty(),
                    favoriteFacilityIds = personalization.favoriteFacilityIds,
                    recentFacilityIds = personalization.recentFacilityIds,
                    onFavoriteToggled = onFavoriteToggled,
                    currentLocation = routeOrigin,
                    selectedMapAddress = selectedMapAddress,
                    onAddressSelectionCleared = {
                        onMapAddressCleared()
                        sheetCollapseRequestKey += 1
                    },
                    onFacilitySelectionCleared = {
                        onFacilitySelectionCleared()
                        sheetCollapseRequestKey += 1
                    },
                    onStartRouteRequested = { facility ->
                        routeOriginFacilityId = facility.facilityId
                        routeDestinationId = null
                        routeOriginAddress = null
                        routeDestinationAddress = null
                        routeMapPickTarget = null
                        isRouteOpen = true
                        onRoutePreviewCleared()
                    },
                    onDestinationRouteRequested = ::showRouteToDestination,
                    onStartAddressRouteRequested = { address ->
                        routeOriginFacilityId = null
                        routeDestinationId = null
                        routeOriginAddress = address
                        routeDestinationAddress = null
                        routeMapPickTarget = null
                        isRouteOpen = true
                        onRoutePreviewCleared()
                    },
                    onDestinationAddressRouteRequested = { address ->
                        routeOriginFacilityId = null
                        routeDestinationId = null
                        routeOriginAddress = null
                        routeDestinationAddress = address
                        routeMapPickTarget = null
                        isRouteOpen = true
                        onRoutePreviewCleared()
                    },
                    onPhoneRequested = onPhoneRequested,
                    onShareRequested = onShareRequested,
                    facilityMediaState = facilityMediaState,
                    usageOptionListState = usageOptionListState,
                    discoveryFeedState = discoveryFeedState,
                    usageOptionToday = today,
                    onUsageOptionRetry = onUsageOptionsRetry,
                    onUsageOptionSelected = { option ->
                        openUsageOptionDetailFromFeed = false
                        openUsageOptionDetailName = null
                        openUsageOptionDetail = option
                    },
                    onFeedUsageOptionSelected = { item ->
                        openUsageOptionDetailFromFeed = true
                        openUsageOptionDetailName = item.facilityName
                        openUsageOptionDetail = item.option
                    },
                    onUsageOptionCompare = { ids ->
                        openUsageOptionCompareIds = ids
                        onUsageOptionCompareRequested(ids)
                    },
                    onUsageOptionShowAll = { facilityId ->
                        openUsageOptionListFacilityId = facilityId
                    },
                    resultSort = resultSort,
                    onResultSortChanged = { sort ->
                        resultSortName = sort.name
                        if (normalizedQuery.isNotEmpty()) {
                            searchSubmissionSerial += 1
                            if (activeCategory != null) {
                                onFacilityCategorySearchSubmitted(normalizedQuery, sort)
                            } else {
                                onFacilitySearchSubmitted(normalizedQuery, sort)
                            }
                        }
                    },
                    ownershipFilter = ownershipFilter,
                    onOwnershipFilterChanged = { filter -> ownershipFilterName = filter.name },
                    onResultFacilitySelected = { facilityId ->
                        onRecentFacilityRecorded(facilityId)
                        onFacilitySelected(facilityId)
                    },
                    onAskAiRequested = { rootMode = RootMode.AI },
                    searchResultRequestKey = searchSubmissionSerial,
                    sheetCollapseRequestKey = sheetCollapseRequestKey,
                    sheetExpandRequestKey = sheetExpandRequestKey,
                    onSheetStageChanged = { stage -> sheetStage = stage },
                        mapContent = mapContent,
                    )
                  }
                }

                if (isRouteOpen && routeMapPickTarget == null) {
                    RoutePreviewScreen(
                        facilities = uiState.facilities,
                        initialOrigin = routeOriginFacility,
                        initialDestination = routeDestination,
                        initialOriginAddress = routeOriginAddress,
                        initialDestinationAddress = routeDestinationAddress,
                        currentLocation = routeOriginSnapshot ?: routeOrigin,
                        routeState = routePreviewState,
                        locationState = locationControlState,
                        addressSearchState = addressSearchState,
                        onAddressSearchRequested = onAddressSearchRequested,
                        onCurrentLocationRequested = {
                            routeOriginSnapshot = routeOrigin
                            onCurrentLocationRequested()
                        },
                        onOriginSelected = { origin ->
                            routeOriginFacilityId = origin?.facilityId
                            if (origin != null) routeOriginAddress = null
                        },
                        onDestinationSelected = { destination ->
                            routeDestinationId = destination?.facilityId
                            if (destination != null) routeDestinationAddress = null
                        },
                        onOriginAddressSelected = { address ->
                            routeOriginAddress = address
                            if (address != null) routeOriginFacilityId = null
                        },
                        onDestinationAddressSelected = { address ->
                            routeDestinationAddress = address
                            if (address != null) routeDestinationId = null
                        },
                        onMapSelectionRequested = { target ->
                            routeMapPickTarget = target
                            isSearchOpen = false
                            onFacilitySelectionCleared()
                            onMapAddressCleared()
                            onRoutePreviewCleared()
                            sheetCollapseRequestKey += 1
                        },
                        onRouteRequested = onRoutePreviewRequested,
                        onRouteCleared = onRoutePreviewCleared,
                        onSheetHeightChanged = { height -> routeSheetHeight = height },
                        onBack = {
                            isRouteOpen = false
                            routeMapPickTarget = null
                            routeOriginFacilityId = null
                            routeDestinationId = null
                            routeOriginAddress = null
                            routeDestinationAddress = null
                            onRoutePreviewCleared()
                            returnToChatIfNeeded()
                        },
                    )
                } else {
                    AnimatedVisibility(
                        visible = rootMode == RootMode.AI,
                        modifier = Modifier.fillMaxSize(),
                        enter = nativePageEnter(nativeDesign, fromLeft = true),
                        exit = nativePageExit(nativeDesign, toLeft = true),
                        label = "ai-root-page-transition",
                    ) {
                        val aiPageSettled by remember(transition) {
                            derivedStateOf {
                                transition.currentState == androidx.compose.animation.EnterExitState.Visible &&
                                    transition.targetState == androidx.compose.animation.EnterExitState.Visible
                            }
                        }
                        CompositionLocalProvider(LocalAiMapPreviewEnabled provides (rootMode == RootMode.AI && aiPageSettled)) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.background,
                            ) {
                                aiPageState.SaveableStateProvider("ai-conversation") {
                                AiModeScreen(
                                    content = liveSpec?.ai,
                                    inputValue = aiInputValue,
                                    onInputValueChange = { aiInputValue = it },
                                    selectedFacilityName = uiState.selectedFacility?.name,
                                    onClearSelectedFacility = {
                                        pendingAiNavigation = null
                                        onFacilitySelectionCleared()
                                        sheetCollapseRequestKey += 1
                                    },
                                    conversationState = aiConversationState,
                                    onBackToMap = { selectRootMode(RootMode.MAP) },
                                    onPromptSubmitted = { message ->
                                        onAiPromptSubmitted(message, currentAiPreferences)
                                    },
                                    onNewConversation = {
                                        aiInputValue = TextFieldValue()
                                        pendingAiNavigation = null
                                        onAiNewConversation()
                                    },
                                    onShowOnMap = { card ->
                                        beginAiNavigation(card, AiNavigationTarget.FOCUS_MAP)
                                    },
                                    onDecide = { card ->
                                        beginAiNavigation(card, AiNavigationTarget.SELECT_DESTINATION)
                                    },
                                    onPreviewRoute = { card ->
                                        beginAiNavigation(card, AiNavigationTarget.PREVIEW_ROUTE)
                                    },
                                    preferences = currentAiPreferences,
                                    onPreferencesChanged = { updatedPreferences ->
                                        currentAiPreferences = updatedPreferences
                                        onAiPreferencesChanged(updatedPreferences)
                                    },
                                )
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = rootMode == RootMode.MY,
                        modifier = Modifier.fillMaxSize(),
                        enter = nativePageEnter(nativeDesign, fromLeft = false),
                        exit = nativePageExit(nativeDesign, toLeft = false),
                        label = "my-root-page-transition",
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            MyModeScreen(
                                content = liveSpec?.my,
                                selectedMode = RootMode.MY,
                                onModeSelected = ::selectRootMode,
                                modes = liveSpec?.navigation?.order ?: RootMode.entries,
                                preferences = currentAiPreferences,
                                onPreferencesChanged = { updatedPreferences ->
                                    currentAiPreferences = updatedPreferences
                                    onAiPreferencesChanged(updatedPreferences)
                                },
                                favoriteFacilityCount = personalization.favoriteFacilityIds.size,
                                recentFacilities = personalization.recentFacilities,
                                onRecentFacilityRemoved = onRecentFacilityRemoved,
                                onRecentFacilitiesCleared = onRecentFacilitiesCleared,
                                                publicDataSources = metaVersion.publicDataSources,
                            )
                        }
                    }

                    if (isSearchOpen) {
                        FacilitySearchScreen(
                            query = searchQuery,
                            onQueryChanged = { query ->
                                searchQuery = query
                                activeCategoryName = null
                                resultSortName = FacilityResultSort.RELEVANCE.name
                                ownershipFilterName = FacilityOwnershipFilter.ANY.name
                            },
                            facilities = if (normalizedQuery.isBlank()) {
                                personalization.recentFacilityIds.mapNotNull { recentId ->
                                    uiState.facilities.firstOrNull { it.facilityId == recentId }
                                }
                            } else {
                                sortFacilitySearchResults(
                                    facilities = matchedSearchFacilities,
                                    query = normalizedQuery,
                                    selectedCategory = activeCategory,
                                    sort = FacilityResultSort.RELEVANCE,
                                    origin = routeOrigin,
                                )
                            },
                            addressResults = if (normalizedQuery.isBlank()) {
                                emptyList()
                            } else {
                                addressSearchState.resultsFor(normalizedQuery)
                            },
                            isAddressSearchLoading = normalizedQuery.isNotBlank() &&
                                addressSearchState.isLoadingFor(normalizedQuery),
                            showingRecentResults = normalizedQuery.isBlank(),
                            favoriteFacilityIds = personalization.favoriteFacilityIds,
                            searchLabel = liveSpec?.map?.searchLabel
                                ?: stringResource(R.string.map_search_label),
                            onFacilitySelected = { facilityId ->
                                onMapAddressCleared()
                                onRecentFacilityRecorded(facilityId)
                                onFacilitySelected(facilityId)
                                isSearchOpen = false
                            },
                            onAddressSelected = { address ->
                                searchQuery = address.displayLabel
                                activeCategoryName = null
                                resultSortName = FacilityResultSort.RELEVANCE.name
                                ownershipFilterName = FacilityOwnershipFilter.ANY.name
                                onMapAddressSelected(address)
                                isSearchOpen = false
                            },
                            onRecentFacilityRemoved = onRecentFacilityRemoved,
                            onSearchSubmitted = {
                                if (normalizedQuery.isNotEmpty()) {
                                    onFacilitySelectionCleared()
                                    onMapAddressCleared()
                                    searchSubmissionSerial += 1
                                    onFacilitySearchSubmitted(
                                        normalizedQuery,
                                        FacilityResultSort.RELEVANCE,
                                    )
                                    isSearchOpen = false
                                }
                            },
                            onBack = {
                                if (normalizedQuery.isNotEmpty()) {
                                    clearSearchQuery()
                                } else {
                                    isSearchOpen = false
                                }
                            },
                        )
                    }
                    openUsageOptionListFacilityId?.let { facilityId ->
                        val page = (usageOptionListState.forFacility(facilityId)
                            as? UsageOptionListState.Ready)?.page
                        val facility = uiState.facilities.firstOrNull { it.facilityId == facilityId }
                        if (page != null && facility != null) {
                            UsageOptionListScreen(
                                facilityName = facility.name,
                                page = page,
                                today = today,
                                onBack = { openUsageOptionListFacilityId = null },
                                onOptionSelected = { option ->
                                    openUsageOptionDetailFromFeed = false
                                    openUsageOptionDetailName = null
                                    openUsageOptionDetail = option
                                },
                            )
                        }
                    }
                    openUsageOptionCompareIds?.let { ids ->
                        UsageOptionCompareScreen(
                            requestedIds = ids,
                            state = usageOptionCompareState.forIds(ids),
                            onBack = { openUsageOptionCompareIds = null; returnToChatIfNeeded() },
                            onRetry = onUsageOptionCompareRetry,
                            onOptionSelected = { item ->
                                openUsageOptionDetailFromFeed = true
                                openUsageOptionDetailName = item.facilityName
                                openUsageOptionDetail = item.option
                            },
                        )
                    }
                    openUsageOptionDetail?.let { option ->
                        val facility = uiState.facilities.firstOrNull {
                            it.facilityId == option.facilityId
                        }
                        if (facility != null) {
                            UsageOptionDetailScreen(
                                option = option,
                                facilityName = openUsageOptionDetailName ?: facility.name,
                                evidenceState = usageOptionEvidenceState,
                                today = today,
                                onBack = {
                                    openUsageOptionDetail = null
                                    openUsageOptionDetailName = null
                                },
                                onRetryEvidence = onUsageOptionEvidenceRetry,
                                onDecide = { decidedOption ->
                                    uiState.facilities.firstOrNull {
                                        it.facilityId == decidedOption.facilityId
                                    }?.let { destination ->
                                        decidedUsageOptionId = decidedOption.usageOptionId
                                        openUsageOptionDetail = null
                                        openUsageOptionDetailName = null
                                        openUsageOptionDetailFromFeed = false
                                        openUsageOptionListFacilityId = null
                                        openUsageOptionCompareIds = null
                                        showRouteToDestination(destination)
                                    }
                                },
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsBottomHeight(WindowInsets.navigationBars)
                        .background(
                            if (!nativeDesign.isOriginal) {
                                MaterialTheme.colorScheme.surface
                            } else if (isDarkTheme) {
                                SystemNavigationBarDark
                            } else {
                                SystemNavigationBarLight
                            },
                        )
                        .testTag("system-navigation-scrim"),
                )
            }
        }
      }
      if (showOnboarding) {
        GymviOnboarding(
          onRequestLocation = onCurrentLocationRequested,
          onFinish = {
            markOnboardingCompleted(context)
            showOnboarding = false
          },
        )
      }
     }
    }
}

@Composable
private fun MapModeScreen(
    uiState: MapUiState,
    content: LiveUiMapContent?,
    searchQuery: String,
    onSearchRequested: () -> Unit,
    onSearchCleared: () -> Unit,
    onCategorySearch: (FacilityCategory, String) -> Unit,
    isSearchAreaChanged: Boolean,
    onSearchThisArea: () -> Unit,
    onRetry: () -> Unit,
    onFacilityLoadMore: () -> Unit,
    selectedMode: RootMode,
    onModeSelected: (RootMode) -> Unit,
    modes: List<RootMode>,
    locationControlState: CurrentLocationControlState,
    onCurrentLocationRequested: () -> Unit,
    isAwaitingFirstLocation: Boolean,
    onRouteRequested: () -> Unit,
    showFloatingControls: Boolean,
    showMapChrome: Boolean,
    routeMapPickTarget: RouteMapPickTarget?,
    onRouteMapPickCancelled: () -> Unit,
    onRouteMapPickFacilityConfirmed: (FacilityMapItem) -> Unit,
    onRouteMapPickAddressConfirmed: (AddressSearchItem) -> Unit,
    mapLogoClearanceOverride: Dp?,
    isMapInteractive: Boolean,
    routePath: List<RouteCoordinate>,
    isFacilitySearchActive: Boolean,
    favoriteFacilityIds: Set<String>,
    recentFacilityIds: List<String>,
    onFavoriteToggled: (String) -> Unit,
    currentLocation: RouteCoordinate?,
    selectedMapAddress: AddressSearchItem?,
    onFacilitySelectionCleared: () -> Unit,
    onAddressSelectionCleared: () -> Unit,
    onStartRouteRequested: (FacilityMapItem) -> Unit,
    onDestinationRouteRequested: (FacilityMapItem) -> Unit,
    onStartAddressRouteRequested: (AddressSearchItem) -> Unit,
    onDestinationAddressRouteRequested: (AddressSearchItem) -> Unit,
    onPhoneRequested: (FacilityMapItem) -> Unit,
    onShareRequested: (FacilityMapItem) -> Unit,
    facilityMediaState: FacilityMediaState,
    usageOptionListState: UsageOptionListState,
    discoveryFeedState: DiscoveryFeedState,
    usageOptionToday: LocalDate,
    onUsageOptionRetry: (String) -> Unit,
    onUsageOptionSelected: (UsageOptionItem) -> Unit,
    onFeedUsageOptionSelected: (FeedUsageOption) -> Unit,
    onUsageOptionCompare: (List<String>) -> Unit,
    onUsageOptionShowAll: (String) -> Unit,
    resultSort: FacilityResultSort,
    onResultSortChanged: (FacilityResultSort) -> Unit,
    ownershipFilter: FacilityOwnershipFilter,
    onOwnershipFilterChanged: (FacilityOwnershipFilter) -> Unit,
    onResultFacilitySelected: (String) -> Unit,
    onAskAiRequested: () -> Unit,
    searchResultRequestKey: Int,
    sheetCollapseRequestKey: Int,
    sheetExpandRequestKey: Int,
    onSheetStageChanged: (FacilitySheetStage) -> Unit,
    mapContent:
        (@Composable (Modifier, List<FacilityMapItem>, String?, List<RouteCoordinate>, () -> Dp, Boolean, Boolean) -> Unit)?,
) {
    var sheetPresentation by remember {
        mutableStateOf(FacilitySheetPresentation.Initial)
    }
    val sheetStage by remember { derivedStateOf { sheetPresentation.stage } }
    val mapClearance = remember(mapLogoClearanceOverride) {
        { mapLogoClearanceOverride ?: sheetPresentation.mapBottomClearance }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("map-mode-screen")
            .then(
                if (isMapInteractive) {
                    Modifier
                } else {
                    Modifier.clearAndSetSemantics { }
                },
            ),
    ) {
        MapArea(
            uiState = uiState,
            mapContent = mapContent,
            bottomLogoClearance = mapClearance,
            routePath = routePath,
            isFacilitySearchActive = isFacilitySearchActive,
            isInteractive = isMapInteractive &&
                (!showMapChrome || sheetStage != FacilitySheetStage.STAGE_2),
            modifier = Modifier.fillMaxSize(),
        )
        val coveredMapChrome = if (sheetStage == FacilitySheetStage.STAGE_2) {
            Modifier.clearAndSetSemantics { }
        } else {
            Modifier
        }
        if (showMapChrome) {
            MapTopOverlay(
                uiState = uiState,
                content = content,
                searchQuery = searchQuery,
                onSearchRequested = onSearchRequested,
                onSearchCleared = onSearchCleared,
                onCategorySearch = onCategorySearch,
                isSearchAreaChanged = isSearchAreaChanged,
                onSearchThisArea = onSearchThisArea,
                routeMapPickTarget = routeMapPickTarget,
                onRouteMapPickCancelled = onRouteMapPickCancelled,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(vertical = 12.dp)
                    .then(coveredMapChrome),
            )
        }
        if (showFloatingControls) {
            if (routeMapPickTarget == null && LocalNativeDesign.current.isOriginal) {
                RootModeDock(
                    selectedMode = selectedMode,
                    onModeSelected = onModeSelected,
                    modes = modes,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset { IntOffset(0, -sheetPresentation.followedBottomClearance.roundToPx()) }
                        .then(coveredMapChrome),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp)
                    .offset { IntOffset(0, -sheetPresentation.followedBottomClearance.roundToPx()) }
                    .then(coveredMapChrome),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (routeMapPickTarget == null) {
                    RouteControl(
                        destination = uiState.selectedFacility,
                        onRouteRequested = onRouteRequested,
                    )
                }
                CurrentLocationControl(
                    state = locationControlState,
                    onClick = onCurrentLocationRequested,
                )
            }
        }
        if (showMapChrome) {
            AnchoredFacilitySheet(
                uiState = uiState,
                selectedAddress = selectedMapAddress,
                onRetry = onRetry,
                onFacilityLoadMore = onFacilityLoadMore,
                isAwaitingFirstLocation = isAwaitingFirstLocation,
                favoriteFacilityIds = favoriteFacilityIds,
                recentFacilityIds = recentFacilityIds,
                onFavoriteToggled = onFavoriteToggled,
                currentLocation = currentLocation,
                onFacilitySelectionCleared = onFacilitySelectionCleared,
                onAddressSelectionCleared = onAddressSelectionCleared,
                onStartRouteRequested = onStartRouteRequested,
                onDestinationRouteRequested = onDestinationRouteRequested,
                onStartAddressRouteRequested = onStartAddressRouteRequested,
                onDestinationAddressRouteRequested = onDestinationAddressRouteRequested,
                onPhoneRequested = onPhoneRequested,
                onShareRequested = onShareRequested,
                facilityMediaState = facilityMediaState,
                usageOptionListState = usageOptionListState,
                discoveryFeedState = discoveryFeedState,
                usageOptionToday = usageOptionToday,
                onUsageOptionRetry = onUsageOptionRetry,
                onUsageOptionSelected = onUsageOptionSelected,
                onFeedUsageOptionSelected = onFeedUsageOptionSelected,
                onUsageOptionCompare = onUsageOptionCompare,
                onUsageOptionShowAll = onUsageOptionShowAll,
                routeMapPickTarget = routeMapPickTarget,
                onRouteMapPickFacilityConfirmed = onRouteMapPickFacilityConfirmed,
                onRouteMapPickAddressConfirmed = onRouteMapPickAddressConfirmed,
                searchQuery = searchQuery,
                resultSort = resultSort,
                onResultSortChanged = onResultSortChanged,
                ownershipFilter = ownershipFilter,
                onOwnershipFilterChanged = onOwnershipFilterChanged,
                onResultFacilitySelected = onResultFacilitySelected,
                onAskAiRequested = onAskAiRequested,
                searchResultRequestKey = searchResultRequestKey,
                onPresentationChanged = { presentation ->
                    if (presentation != sheetPresentation) {
                        sheetPresentation = presentation
                    }
                    onSheetStageChanged(presentation.stage)
                },
                collapseRequestKey = sheetCollapseRequestKey,
                expandRequestKey = sheetExpandRequestKey,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MapTopOverlay(
    uiState: MapUiState,
    content: LiveUiMapContent?,
    searchQuery: String,
    onSearchRequested: () -> Unit,
    onSearchCleared: () -> Unit,
    onCategorySearch: (FacilityCategory, String) -> Unit,
    isSearchAreaChanged: Boolean,
    onSearchThisArea: () -> Unit,
    routeMapPickTarget: RouteMapPickTarget?,
    onRouteMapPickCancelled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchLabel = content?.searchLabel ?: stringResource(R.string.map_search_label)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        routeMapPickTarget?.let { target ->
            RouteMapPickHeader(
                target = target,
                onCancel = onRouteMapPickCancelled,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (LocalNativeDesign.current.variant != NativeDesignVariant.GPT_C || routeMapPickTarget != null) MapSearchLauncher(
            query = searchQuery,
            label = searchLabel,
            onClick = onSearchRequested,
            onClear = onSearchCleared,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        if (searchQuery.isBlank()) {
            FacilityCategoryBar(onCategorySearch = onCategorySearch)
        }
        if (isSearchAreaChanged) {
            SearchThisAreaButton(onClick = onSearchThisArea)
        }
        MapStatus(uiState = uiState)
    }
}

@Composable
private fun RouteMapPickHeader(
    target: RouteMapPickTarget,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(
        if (target == RouteMapPickTarget.ORIGIN) {
            R.string.route_pick_origin_on_map
        } else {
            R.string.route_pick_destination_on_map
        },
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag("route-map-pick-header"),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FacilityTopIconButton(
                iconRes = R.drawable.ic_material_symbol_arrow_back_24,
                label = stringResource(R.string.route_map_pick_cancel),
                tint = MaterialTheme.colorScheme.onSurface,
                testTag = "route-map-pick-cancel",
                onClick = onCancel,
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.width(44.dp))
        }
    }
}

@Composable
private fun MapArea(
    uiState: MapUiState,
    mapContent:
        (@Composable (Modifier, List<FacilityMapItem>, String?, List<RouteCoordinate>, () -> Dp, Boolean, Boolean) -> Unit)?,
    bottomLogoClearance: () -> Dp,
    routePath: List<RouteCoordinate>,
    isFacilitySearchActive: Boolean,
    isInteractive: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .testTag("map-surface")
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (mapContent == null) {
            MapCredentialPlaceholder(modifier = Modifier.fillMaxSize())
        } else {
            mapContent(
                Modifier.fillMaxSize(),
                uiState.facilities,
                uiState.selectedFacilityId,
                routePath,
                bottomLogoClearance,
                isInteractive,
                isFacilitySearchActive,
            )
        }

        if (uiState.mapAvailability == MapAvailability.AUTH_ERROR) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f)),
            )
        }
    }
}

@Composable
private fun MapCredentialPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .testTag("map-credential-message")
            .padding(horizontal = 32.dp)
            .padding(bottom = 180.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.map_credential_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.map_credential_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun MapStatus(uiState: MapUiState, modifier: Modifier = Modifier) {
    val text = when (uiState.mapAvailability) {
        MapAvailability.MISSING_CREDENTIAL -> null
        MapAvailability.LOADING -> stringResource(R.string.map_loading)
        MapAvailability.READY -> null
        MapAvailability.AUTH_ERROR -> stringResource(
            R.string.map_auth_error,
            uiState.mapAuthErrorCode ?: stringResource(R.string.unknown_value),
        )
    }
    if (text == null) return

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (uiState.mapAvailability == MapAvailability.LOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun FacilityPanel(
    uiState: MapUiState,
    selectedAddress: AddressSearchItem? = null,
    stage: FacilitySheetStage,
    onRetry: () -> Unit,
    onFacilityLoadMore: () -> Unit = {},
    isAwaitingFirstLocation: Boolean = true,
    favoriteFacilityIds: Set<String>,
    recentFacilityIds: List<String> = emptyList(),
    onFavoriteToggled: (String) -> Unit,
    currentLocation: RouteCoordinate? = null,
    onFacilitySelectionCleared: () -> Unit = {},
    onAddressSelectionCleared: () -> Unit = {},
    onStartRouteRequested: (FacilityMapItem) -> Unit = {},
    onDestinationRouteRequested: (FacilityMapItem) -> Unit = {},
    onStartAddressRouteRequested: (AddressSearchItem) -> Unit = {},
    onDestinationAddressRouteRequested: (AddressSearchItem) -> Unit = {},
    onPhoneRequested: (FacilityMapItem) -> Unit = {},
    onShareRequested: (FacilityMapItem) -> Unit = {},
    facilityMediaState: FacilityMediaState = FacilityMediaState.Idle,
    usageOptionListState: UsageOptionListState = UsageOptionListState.Idle,
    discoveryFeedState: DiscoveryFeedState = DiscoveryFeedState.Idle,
    usageOptionToday: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul")),
    onUsageOptionRetry: (String) -> Unit = {},
    onUsageOptionSelected: (UsageOptionItem) -> Unit = {},
    onFeedUsageOptionSelected: (FeedUsageOption) -> Unit = {},
    onUsageOptionCompare: (List<String>) -> Unit = {},
    onUsageOptionShowAll: (String) -> Unit = {},
    routeMapPickTarget: RouteMapPickTarget? = null,
    onRouteMapPickFacilityConfirmed: (FacilityMapItem) -> Unit = {},
    onRouteMapPickAddressConfirmed: (AddressSearchItem) -> Unit = {},
    searchQuery: String = "",
    resultSort: FacilityResultSort = FacilityResultSort.RELEVANCE,
    onResultSortChanged: (FacilityResultSort) -> Unit = {},
    ownershipFilter: FacilityOwnershipFilter = FacilityOwnershipFilter.ANY,
    onOwnershipFilterChanged: (FacilityOwnershipFilter) -> Unit = {},
    onResultFacilitySelected: (String) -> Unit = {},
    onAskAiRequested: () -> Unit = {},
    facilityNameFocus: FacilityNameFocus? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag("facility-panel")
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        if (selectedAddress != null && uiState.selectedFacility == null) {
            AddressDetails(
                address = selectedAddress,
                stage = stage,
                currentLocation = currentLocation,
                onClose = onAddressSelectionCleared,
                onStartRoute = onStartAddressRouteRequested,
                onDestinationRoute = onDestinationAddressRouteRequested,
                routeMapPickTarget = routeMapPickTarget,
                onRouteMapPickConfirmed = onRouteMapPickAddressConfirmed,
            )
            return@Column
        }
        when (uiState.facilityLoadState) {
            FacilityLoadState.IDLE,
            FacilityLoadState.LOADING,
            -> {
                val selected = uiState.selectedFacility
                if (selected == null) {
                    if (uiState.facilityLoadState == FacilityLoadState.IDLE && !isAwaitingFirstLocation) {
                        SearchPromptPanel()
                    } else {
                        LoadingPanel(compact = stage == FacilitySheetStage.COLLAPSED)
                    }
                } else {
                    FacilityDetails(
                        selected,
                        stage = stage,
                        isFavorite = selected.facilityId in favoriteFacilityIds,
                        onFavoriteToggled = onFavoriteToggled,
                        currentLocation = currentLocation,
                        onClose = onFacilitySelectionCleared,
                        onStartRoute = onStartRouteRequested,
                        onDestinationRoute = onDestinationRouteRequested,
                        onPhone = onPhoneRequested,
                        onShare = onShareRequested,
                        mediaState = facilityMediaState.forFacility(selected.facilityId),
                        usageOptionState = usageOptionListState.forFacility(selected.facilityId),
                        usageOptionToday = usageOptionToday,
                        onUsageOptionRetry = onUsageOptionRetry,
                        onUsageOptionSelected = onUsageOptionSelected,
                        onUsageOptionShowAll = onUsageOptionShowAll,
                        routeMapPickTarget = routeMapPickTarget,
                        onRouteMapPickConfirmed = onRouteMapPickFacilityConfirmed,
                        allFacilities = uiState.facilities,
                        onNearbyFacilitySelected = onResultFacilitySelected,
                        onAskAi = onAskAiRequested,
                        exerciseContents = discoveryFeedState.pageOrNull?.contents.orEmpty(),
                        nameFocus = facilityNameFocus,
                    )
                }
            }

            FacilityLoadState.READY -> {
                val selected = uiState.selectedFacility
                if (selected != null) {
                    FacilityDetails(
                        selected,
                        stage = stage,
                        isFavorite = selected.facilityId in favoriteFacilityIds,
                        onFavoriteToggled = onFavoriteToggled,
                        currentLocation = currentLocation,
                        onClose = onFacilitySelectionCleared,
                        onStartRoute = onStartRouteRequested,
                        onDestinationRoute = onDestinationRouteRequested,
                        onPhone = onPhoneRequested,
                        onShare = onShareRequested,
                        mediaState = facilityMediaState.forFacility(selected.facilityId),
                        usageOptionState = usageOptionListState.forFacility(selected.facilityId),
                        usageOptionToday = usageOptionToday,
                        onUsageOptionRetry = onUsageOptionRetry,
                        onUsageOptionSelected = onUsageOptionSelected,
                        onUsageOptionShowAll = onUsageOptionShowAll,
                        routeMapPickTarget = routeMapPickTarget,
                        onRouteMapPickConfirmed = onRouteMapPickFacilityConfirmed,
                        allFacilities = uiState.facilities,
                        onNearbyFacilitySelected = onResultFacilitySelected,
                        onAskAi = onAskAiRequested,
                        exerciseContents = discoveryFeedState.pageOrNull?.contents.orEmpty(),
                        nameFocus = facilityNameFocus,
                    )
                } else if (searchQuery.isNotBlank()) {
                    SearchResultPanel(
                        facilities = uiState.facilities,
                        totalResultCount = uiState.totalFacilityCount,
                        favoriteFacilityIds = favoriteFacilityIds,
                        currentLocation = currentLocation,
                        resultSort = resultSort,
                        onResultSortChanged = onResultSortChanged,
                        ownershipFilter = ownershipFilter,
                        onOwnershipFilterChanged = onOwnershipFilterChanged,
                        onFacilitySelected = onResultFacilitySelected,
                        nextCursor = uiState.nextFacilityCursor,
                        isLoadingMore = uiState.isLoadingMoreFacilities,
                        onLoadMore = onFacilityLoadMore,
                    )
                } else {
                    DefaultFacilityOverview(
                        facilities = uiState.facilities,
                        discoveryFeed = discoveryFeedState.pageOrNull,
                        favoriteFacilityIds = favoriteFacilityIds,
                        recentFacilityIds = recentFacilityIds,
                        currentLocation = currentLocation,
                        onFacilitySelected = onResultFacilitySelected,
                        today = usageOptionToday,
                        onUsageOptionSelected = onFeedUsageOptionSelected,
                        onUsageOptionCompare = onUsageOptionCompare,
                        onAskAi = onAskAiRequested,
                    )
                }
            }

            FacilityLoadState.EMPTY -> if (searchQuery.isBlank()) {
                EmptyPanel(compact = stage == FacilitySheetStage.COLLAPSED)
            } else {
                SearchResultPanel(
                    facilities = emptyList(),
                    totalResultCount = 0,
                    favoriteFacilityIds = favoriteFacilityIds,
                    currentLocation = currentLocation,
                    resultSort = resultSort,
                    onResultSortChanged = onResultSortChanged,
                    ownershipFilter = ownershipFilter,
                    onOwnershipFilterChanged = onOwnershipFilterChanged,
                    onFacilitySelected = onResultFacilitySelected,
                    nextCursor = null,
                    isLoadingMore = false,
                    onLoadMore = onFacilityLoadMore,
                )
            }
            FacilityLoadState.NETWORK_ERROR -> ErrorPanel(
                title = stringResource(R.string.facility_network_error_title),
                body = stringResource(R.string.facility_network_error_body),
                errorCode = uiState.errorCode,
                onRetry = onRetry,
                compact = stage == FacilitySheetStage.COLLAPSED,
            )

            FacilityLoadState.API_ERROR -> ErrorPanel(
                title = stringResource(R.string.facility_api_error_title),
                body = stringResource(R.string.facility_api_error_body),
                errorCode = uiState.errorCode,
                onRetry = onRetry,
                compact = stage == FacilitySheetStage.COLLAPSED,
            )

            FacilityLoadState.INVALID_RESPONSE -> ErrorPanel(
                title = stringResource(R.string.facility_invalid_title),
                body = stringResource(R.string.facility_invalid_body),
                errorCode = uiState.errorCode,
                onRetry = onRetry,
                compact = stage == FacilitySheetStage.COLLAPSED,
            )
        }
    }
}

@Composable
private fun AddressDetails(
    address: AddressSearchItem,
    stage: FacilitySheetStage,
    currentLocation: RouteCoordinate?,
    onClose: () -> Unit,
    onStartRoute: (AddressSearchItem) -> Unit,
    onDestinationRoute: (AddressSearchItem) -> Unit,
    routeMapPickTarget: RouteMapPickTarget?,
    onRouteMapPickConfirmed: (AddressSearchItem) -> Unit,
) {
    if (stage == FacilitySheetStage.COLLAPSED) {
        Text(
            text = address.displayLabel,
            modifier = Modifier.testTag("map-address-name"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = address.displayLabel,
                modifier = Modifier.testTag("map-address-name"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            address.secondaryLabel?.let { secondary ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            currentLocation?.let { location ->
                val distance = straightLineDistanceMeters(
                    location,
                    RouteCoordinate(address.latitude, address.longitude),
                ).roundToInt()
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = formatFacilityDistance(distance),
                    modifier = Modifier.testTag("map-address-distance"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        FacilityTopIconButton(
            iconRes = R.drawable.ic_material_symbol_close_24,
            label = stringResource(R.string.map_address_close),
            tint = MaterialTheme.colorScheme.onSurface,
            testTag = "map-address-close-action",
            onClick = onClose,
        )
    }
    Spacer(modifier = Modifier.height(13.dp))
    if (routeMapPickTarget != null) {
        FacilityActionButton(
            label = stringResource(
                if (routeMapPickTarget == RouteMapPickTarget.ORIGIN) {
                    R.string.route_confirm_origin
                } else {
                    R.string.route_confirm_destination
                },
            ),
            iconRes = if (routeMapPickTarget == RouteMapPickTarget.ORIGIN) {
                R.drawable.ic_material_symbol_route_arrow_24
            } else {
                R.drawable.ic_material_symbol_location_on_24
            },
            testTag = "route-map-pick-address-confirm",
            emphasized = true,
            onClick = { onRouteMapPickConfirmed(address) },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FacilityActionButton(
            label = stringResource(R.string.facility_start),
            iconRes = R.drawable.ic_material_symbol_route_arrow_24,
            testTag = "map-address-route-start",
            onClick = { onStartRoute(address) },
            modifier = Modifier.weight(1f),
        )
        FacilityActionButton(
            label = stringResource(R.string.facility_arrive),
            iconRes = R.drawable.ic_material_symbol_location_on_24,
            testTag = "map-address-route-arrive",
            emphasized = true,
            onClick = { onDestinationRoute(address) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LoadingPanel(compact: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        if (compact) {
            Text(
                text = stringResource(R.string.facility_loading_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Column {
                Text(
                    text = stringResource(R.string.facility_loading_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.facility_loading_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Nothing loads until the user searches or allows location, so this is a prompt, not loading. */
@Composable
private fun SearchPromptPanel() {
    Text(
        text = stringResource(R.string.facility_search_prompt_title),
        modifier = Modifier.testTag("facility-search-prompt"),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EmptyPanel(compact: Boolean) {
    Text(
        text = stringResource(R.string.facility_empty_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    if (compact) return
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.facility_empty_body),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun DefaultFacilityOverview(
    facilities: List<FacilityMapItem>,
    discoveryFeed: DiscoveryFeedPage?,
    favoriteFacilityIds: Set<String>,
    recentFacilityIds: List<String>,
    currentLocation: RouteCoordinate?,
    onFacilitySelected: (String) -> Unit,
    today: LocalDate,
    onUsageOptionSelected: (FeedUsageOption) -> Unit,
    onUsageOptionCompare: (List<String>) -> Unit,
    onAskAi: () -> Unit,
) {
    val facilityById = remember(facilities) { facilities.associateBy(FacilityMapItem::facilityId) }
    val favoriteFacilities = remember(facilities, favoriteFacilityIds, currentLocation) {
        favoriteFacilityIds.mapNotNull(facilityById::get)
            .sortedByDistanceFrom(currentLocation)
            .take(3)
            .toDistanceItems(currentLocation)
    }
    val recentFacilities = remember(
        facilities,
        recentFacilityIds,
        favoriteFacilityIds,
        currentLocation,
    ) {
        recentFacilityIds.mapNotNull(facilityById::get)
            .filterNot { it.facilityId in favoriteFacilityIds }
            .take(3)
            .toDistanceItems(currentLocation)
    }
    val occupiedIds = remember(favoriteFacilities, recentFacilities) {
        (favoriteFacilities + recentFacilities)
            .mapTo(mutableSetOf()) { it.facility.facilityId }
    }
    val nearbyFacilities = remember(facilities, currentLocation, occupiedIds) {
        facilities
            .filterNot { it.facilityId in occupiedIds }
            .sortedByDistanceFrom(currentLocation)
            .distinctBy { facility -> normalizedFacilityName(facility.name) }
            .take(5)
            .toDistanceItems(currentLocation)
    }
    val recommendedFacilities = remember(discoveryFeed, facilityById) {
        discoveryFeed?.facilitySuggestions.orEmpty().mapNotNull { suggestion ->
            facilityById[suggestion.facilityId]?.let { facility ->
                FacilityDistanceItem(facility, suggestion.distanceMeters)
            }
        }
    }
    val feedUsageOptions = remember(discoveryFeed, facilityById) {
        visibleFeedUsageOptions(discoveryFeed?.usageOptions.orEmpty(), facilityById.keys)
    }
    val feedFacilityIds = remember(feedUsageOptions) {
        feedUsageOptions.mapTo(mutableSetOf()) { it.option.facilityId }
    }

    if (discoveryFeed != null) {
        DiscoveryFeedHeader(
            areaLabel = discoveryFeed.areaLabel,
            dayPart = discoveryFeed.dayPart,
            weather = discoveryFeed.weather,
        )
        if (discoveryFeed.proposals.isNotEmpty()) {
            ExerciseProposalSection(discoveryFeed.proposals)
        }
        UsageOptionFeedSection(
            options = feedUsageOptions,
            source = discoveryFeed.usageOptionsSource,
            today = today,
            onOptionSelected = onUsageOptionSelected,
            onCompare = onUsageOptionCompare,
        )
        val visibleRecommendations = withoutFacilityIds(
            recommendedFacilities.ifEmpty { nearbyFacilities },
            feedFacilityIds,
        ) { it.facility.facilityId }.take(4)
        if (visibleRecommendations.isNotEmpty()) {
            NearbyFacilitySection(
                title = stringResource(R.string.discovery_facility_title),
                facilities = visibleRecommendations,
                favoriteFacilityIds = favoriteFacilityIds,
                onFacilitySelected = onFacilitySelected,
                compact = true,
            )
        }
        val personalized = withoutFacilityIds(
            (favoriteFacilities + recentFacilities).distinctBy { it.facility.facilityId },
            feedFacilityIds,
        ) { it.facility.facilityId }
            .filterNot { item ->
                visibleRecommendations.any { it.facility.facilityId == item.facility.facilityId }
            }
            .take(4)
        if (personalized.isNotEmpty()) {
            NearbyFacilitySection(
                title = stringResource(R.string.discovery_personalized_title),
                facilities = personalized,
                favoriteFacilityIds = favoriteFacilityIds,
                onFacilitySelected = onFacilitySelected,
                compact = true,
            )
        }
        if (discoveryFeed.contents.isNotEmpty()) {
            ExerciseContentSection(discoveryFeed.contents)
        }
    } else {
        if (favoriteFacilities.isNotEmpty()) {
            NearbyFacilitySection(
                title = stringResource(R.string.facility_favorites_title),
                facilities = favoriteFacilities,
                favoriteFacilityIds = favoriteFacilityIds,
                onFacilitySelected = onFacilitySelected,
                firstSection = true,
                compact = true,
            )
        }
        if (recentFacilities.isNotEmpty()) {
            NearbyFacilitySection(
                title = stringResource(R.string.facility_recent_title),
                facilities = recentFacilities,
                onFacilitySelected = onFacilitySelected,
                firstSection = favoriteFacilities.isEmpty(),
                compact = true,
            )
        }
        if (nearbyFacilities.isNotEmpty()) {
            NearbyFacilitySection(
                title = stringResource(R.string.facility_current_map_title),
                facilities = nearbyFacilities,
                onFacilitySelected = onFacilitySelected,
                firstSection = favoriteFacilities.isEmpty() && recentFacilities.isEmpty(),
                compact = true,
            )
        }
    }
    FacilityAiAction(
        label = stringResource(R.string.facility_ask_ai_nearby),
        onClick = onAskAi,
    )
    if (discoveryFeed != null) {
        DiscoveryFeedMetadata(
            weatherSourceName = discoveryFeed.weather?.sourceName,
        )
    }
}

@Composable
private fun DiscoveryFeedHeader(
    areaLabel: String,
    dayPart: String,
    weather: io.github.chamsser.gymvi.data.DiscoveryWeatherItem?,
) {
    // A condition the app does not know stays unknown: the chip shows the temperature alone.
    val condition = weather?.let { discoveryWeatherCondition(it.condition) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discovery-feed-header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = areaLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.discovery_feed_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall.merge(KoreanPhraseBreak),
            )
        }
        Surface(
            // A screen reader stops once on the chip and reads the condition with the temperature.
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .testTag("discovery-feed-chip"),
            shape = RoundedCornerShape(14.dp),
            color = condition?.chipColor
                ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
            contentColor = if (condition == null) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                Color.White
            },
        ) {
            if (weather == null) {
                Text(
                    text = discoveryDayPartLabel(dayPart),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("discovery-weather-summary"),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (condition != null) {
                        Icon(
                            painter = painterResource(condition.iconRes),
                            contentDescription = stringResource(condition.labelRes),
                            modifier = Modifier
                                .size(16.dp)
                                .testTag("discovery-weather-icon"),
                            tint = condition.iconTint,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.discovery_weather_summary,
                            weather.temperatureCelsius.roundToInt(),
                        ),
                        style = if (condition == null) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.labelMedium.copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.28f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 2f,
                                ),
                            )
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** The weather conditions the feed chip draws with their own icon and color. */
private enum class DiscoveryWeatherCondition(
    val iconRes: Int,
    val labelRes: Int,
    val chipColor: Color,
    val iconTint: Color,
) {
    CLEAR(R.drawable.ic_weather_sunny_24, R.string.discovery_weather_clear, Color(0xFF3F8FD8), Color(0xFFFFD54F)),
    CLOUDY(R.drawable.ic_weather_cloudy_24, R.string.discovery_weather_cloudy, Color(0xFF6D7F91), Color.White),
    RAIN(R.drawable.ic_weather_rain_24, R.string.discovery_weather_rain, Color(0xFF3979A8), Color(0xFFB9E6FF)),
    SNOW(R.drawable.ic_weather_snow_24, R.string.discovery_weather_snow, Color(0xFF6F91AA), Color.White),
    STORM(R.drawable.ic_weather_storm_24, R.string.discovery_weather_storm, Color(0xFF566187), Color(0xFFFFE082)),
}

private fun discoveryWeatherCondition(code: String): DiscoveryWeatherCondition? =
    DiscoveryWeatherCondition.entries.firstOrNull { it.name == code }

@Composable
private fun discoveryDayPartLabel(dayPart: String): String = stringResource(
    when (dayPart) {
        "MORNING" -> R.string.discovery_daypart_morning
        "DAYTIME" -> R.string.discovery_daypart_daytime
        "EVENING" -> R.string.discovery_daypart_evening
        else -> R.string.discovery_daypart_late_night
    },
)

@Composable
private fun ExerciseProposalSection(proposals: List<io.github.chamsser.gymvi.data.ExerciseProposalItem>) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.discovery_proposal_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    val titles = proposals.map { formatDiscoveryProposalTitle(it.title) }
    val titleStyle = MaterialTheme.typography.labelMedium.merge(
        TextStyle(fontWeight = FontWeight.SemiBold, lineHeight = 16.sp),
    ).merge(KoreanPhraseBreak)
    val cardSize = discoveryProposalCardSize(titles, titleStyle)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discovery-proposal-row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(proposals, key = { _, item -> item.proposalId }) { index, proposal ->
            val category = discoveryFacilityCategory(proposal.category)
            val categoryColor = Color(category.markerColor)
            Surface(
                modifier = Modifier
                    .size(cardSize)
                    .reviewTag("review-discovery-proposal-${proposal.proposalId}-card"),
                shape = RoundedCornerShape(14.dp),
                color = categoryColor.copy(alpha = 0.09f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = DiscoveryProposalHorizontalPadding,
                            vertical = DiscoveryProposalVerticalPadding,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(DiscoveryProposalIconSize),
                        shape = CircleShape,
                        color = categoryColor.copy(alpha = 0.16f),
                    ) {
                        Icon(
                            painter = painterResource(category.iconRes),
                            contentDescription = null,
                            modifier = Modifier.padding(5.dp),
                            tint = categoryColor,
                        )
                    }
                    Spacer(modifier = Modifier.width(DiscoveryProposalIconGap))
                    Text(
                        text = titles[index],
                        modifier = Modifier
                            .weight(1f)
                            .reviewTag("review-discovery-proposal-${proposal.proposalId}-title"),
                        style = titleStyle,
                    )
                }
            }
        }
    }
}

/**
 * Proposal cards stay 188 by 56 until large text needs more room. Then a card widens until its
 * longest word fits on one line and grows tall enough for every line, and all cards share that
 * size so the row stays even.
 */
@Composable
private fun discoveryProposalCardSize(titles: List<String>, style: TextStyle): DpSize {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density, titles, style) {
        with(density) {
            val chrome = 2 * DiscoveryProposalHorizontalPadding.roundToPx() +
                DiscoveryProposalIconSize.roundToPx() + DiscoveryProposalIconGap.roundToPx()
            val width = maxOf(DiscoveryProposalMinWidth, (measurer.widestWord(titles, style) + chrome).toDp())
            val titleWidth = width.roundToPx() - chrome
            val titleHeight = titles.maxOfOrNull { title ->
                measurer.measure(title, style, constraints = Constraints(maxWidth = titleWidth)).size.height
            } ?: 0
            DpSize(
                width = width,
                height = maxOf(
                    DiscoveryProposalMinHeight,
                    (titleHeight + 2 * DiscoveryProposalVerticalPadding.roundToPx()).toDp(),
                ),
            )
        }
    }
}

internal fun formatDiscoveryProposalTitle(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    if (words.size < 2) return title.trim()

    val splitIndex = (1 until words.size).minBy { index ->
        val firstLineLength = words.take(index).sumOf(String::length) + index - 1
        val secondLineLength = words.drop(index).sumOf(String::length) + words.size - index - 1
        abs(firstLineLength - secondLineLength)
    }
    return words.take(splitIndex).joinToString(" ") +
        "\n" +
        words.drop(splitIndex).joinToString(" ")
}

private fun Modifier.reviewRootSemantics(): Modifier = if (BuildConfig.DEBUG) {
    semantics { testTagsAsResourceId = true }
} else {
    this
}

private fun Modifier.reviewTag(tag: String): Modifier = if (BuildConfig.DEBUG) {
    testTag(tag)
} else {
    this
}

@Composable
internal fun ExerciseContentSection(
    contents: List<ExerciseContentItem>,
    compact: Boolean = false,
) {
    if (LocalNativeDesign.current.variant == NativeDesignVariant.COMBINED) {
        NativeExerciseGuide(contents, compact)
        return
    }
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(if (compact) 18.dp else 22.dp))
    Text(
        text = stringResource(R.string.discovery_content_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(if (compact) 6.dp else 8.dp))
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discovery-content-row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(contents, key = { _, item -> item.contentId }) { _, content ->
            Surface(
                onClick = { openExerciseContent(context, content.contentUrl) },
                modifier = Modifier
                    .width(if (compact) 176.dp else 210.dp)
                    .height(if (compact) 192.dp else 232.dp)
                    .testTag("discovery-content-${content.contentId}"),
                shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = content.thumbnailUrl,
                        contentDescription = stringResource(
                            R.string.discovery_content_image_description,
                            content.title,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compact) 88.dp else 112.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(if (compact) 10.dp else 12.dp),
                    ) {
                        Text(
                            text = content.title,
                            style = if (compact) {
                                MaterialTheme.typography.labelLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = content.summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = if (compact) {
                                MaterialTheme.typography.labelMedium
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(
                                R.string.discovery_content_source,
                                content.sourceName,
                            ),
                            modifier = Modifier.testTag(
                                "discovery-content-source-${content.contentId}",
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            style = if (compact) {
                                MaterialTheme.typography.labelSmall
                            } else {
                                MaterialTheme.typography.labelMedium
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private fun discoveryFacilityCategory(category: String): FacilityCategory = when (category) {
    "POOL" -> FacilityCategory.POOL
    "GYM" -> FacilityCategory.GYM
    "FIELD" -> FacilityCategory.FIELD
    "BALL" -> FacilityCategory.BASKETBALL
    else -> FacilityCategory.OTHER
}

private fun exerciseContentsForFacility(
    facility: FacilityMapItem,
    contents: List<ExerciseContentItem>,
): List<ExerciseContentItem> {
    val acceptedCategories = when (FacilityCategory.fromFacility(facility)) {
        FacilityCategory.POOL -> setOf("POOL")
        FacilityCategory.FIELD,
        FacilityCategory.FOOTBALL,
        FacilityCategory.BASEBALL,
        FacilityCategory.TENNIS,
        FacilityCategory.BADMINTON,
        FacilityCategory.BASKETBALL,
        -> setOf("FIELD", "BALL")
        FacilityCategory.GYM,
        FacilityCategory.GOLF,
        FacilityCategory.DANCE,
        FacilityCategory.ICE,
        FacilityCategory.OTHER,
        -> setOf("GYM", "OTHER")
    }
    return contents.filter { it.category in acceptedCategories }.take(3)
}

@Composable
private fun SearchResultPanel(
    facilities: List<FacilityMapItem>,
    totalResultCount: Int,
    favoriteFacilityIds: Set<String>,
    currentLocation: RouteCoordinate?,
    resultSort: FacilityResultSort,
    onResultSortChanged: (FacilityResultSort) -> Unit,
    ownershipFilter: FacilityOwnershipFilter,
    onOwnershipFilterChanged: (FacilityOwnershipFilter) -> Unit,
    onFacilitySelected: (String) -> Unit,
    nextCursor: String?,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    FacilitySearchControls(totalResultCount, facilities.size, resultSort, onResultSortChanged,
        ownershipFilter, onOwnershipFilterChanged)
    Spacer(modifier = Modifier.height(8.dp))
    facilities.forEachIndexed { index, facility ->
        FacilitySearchResultRow(
            facility = facility,
            isFavorite = facility.facilityId in favoriteFacilityIds,
            showRemoveAction = false,
            distanceMeters = currentLocation?.let { facilityDistanceMeters(it, facility) },
            compact = true,
            testTag = "facility-sheet-search-result",
            onClick = { onFacilitySelected(facility.facilityId) },
            onRemove = {},
        )
        if (index != facilities.lastIndex) HorizontalDivider(color = gymviSubtleBorderColor())
    }
    if (nextCursor != null) FacilitySearchLoadMore(isLoadingMore, onLoadMore)
}

@Composable
internal fun FacilitySearchControls(
    totalResultCount: Int,
    visibleResultCount: Int,
    resultSort: FacilityResultSort,
    onResultSortChanged: (FacilityResultSort) -> Unit,
    ownershipFilter: FacilityOwnershipFilter,
    onOwnershipFilterChanged: (FacilityOwnershipFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("facility-search-sheet-controls"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.facility_count,
                if (ownershipFilter == FacilityOwnershipFilter.ANY) {
                    totalResultCount
                } else {
                    visibleResultCount
                },
            ),
            modifier = Modifier.testTag("facility-search-sheet-count"),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OwnershipFilterControl(
                selection = ownershipFilter,
                onSelectionChanged = onOwnershipFilterChanged,
            )
            ResultSortDropdown(
                selection = resultSort,
                onSelectionChanged = onResultSortChanged,
            )
        }
    }
}

@Composable
internal fun FacilitySearchLoadMore(isLoadingMore: Boolean, onLoadMore: () -> Unit) {
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onLoadMore,
            enabled = !isLoadingMore,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .testTag("facility-load-more"),
            shape = RoundedCornerShape(21.dp),
        ) {
            if (isLoadingMore) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = stringResource(
                    if (isLoadingMore) {
                        R.string.facility_loading_more
                    } else {
                        R.string.facility_load_more
                    },
                ),
            )
        }
}

@Composable
private fun OwnershipFilterControl(
    selection: FacilityOwnershipFilter,
    onSelectionChanged: (FacilityOwnershipFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selection) {
        FacilityOwnershipFilter.ANY -> stringResource(R.string.facility_result_filter)
        FacilityOwnershipFilter.PUBLIC -> stringResource(R.string.facility_result_filter_public)
        FacilityOwnershipFilter.PRIVATE -> stringResource(R.string.facility_result_filter_private)
    }
    Box {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides 34.dp,
        ) {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .size(34.dp)
                    .testTag("facility-result-filter"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_material_symbol_tune_24),
                    contentDescription = label,
                    modifier = Modifier.size(19.dp),
                    tint = if (selection == FacilityOwnershipFilter.ANY) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            FacilityOwnershipFilter.entries.forEach { option ->
                val optionLabel = when (option) {
                    FacilityOwnershipFilter.ANY -> stringResource(
                        R.string.facility_result_filter_clear,
                    )

                    FacilityOwnershipFilter.PUBLIC -> stringResource(
                        R.string.facility_result_filter_public,
                    )

                    FacilityOwnershipFilter.PRIVATE -> stringResource(
                        R.string.facility_result_filter_private,
                    )
                }
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    modifier = Modifier.testTag(
                        "facility-result-filter-${option.name.lowercase()}",
                    ),
                    onClick = {
                        expanded = false
                        onSelectionChanged(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultSortDropdown(
    selection: FacilityResultSort,
    onSelectionChanged: (FacilityResultSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selection) {
        FacilityResultSort.RELEVANCE -> stringResource(R.string.facility_result_sort_relevance)
        FacilityResultSort.DISTANCE -> stringResource(R.string.facility_result_sort_distance)
    }
    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .height(34.dp)
                .testTag("facility-result-sort"),
            shape = RoundedCornerShape(17.dp),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_material_symbol_arrow_drop_down_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            FacilityResultSort.entries.forEach { option ->
                val optionLabel = when (option) {
                    FacilityResultSort.RELEVANCE -> stringResource(
                        R.string.facility_result_sort_relevance,
                    )

                    FacilityResultSort.DISTANCE -> stringResource(
                        R.string.facility_result_sort_distance,
                    )
                }
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    modifier = Modifier.testTag(
                        "facility-result-sort-${option.name.lowercase()}",
                    ),
                    onClick = {
                        expanded = false
                        onSelectionChanged(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun FacilityDetails(
    facility: FacilityMapItem,
    stage: FacilitySheetStage,
    isFavorite: Boolean,
    onFavoriteToggled: (String) -> Unit,
    currentLocation: RouteCoordinate?,
    onClose: () -> Unit,
    onStartRoute: (FacilityMapItem) -> Unit,
    onDestinationRoute: (FacilityMapItem) -> Unit,
    onPhone: (FacilityMapItem) -> Unit,
    onShare: (FacilityMapItem) -> Unit,
    mediaState: FacilityMediaState,
    usageOptionState: UsageOptionListState,
    usageOptionToday: LocalDate,
    onUsageOptionRetry: (String) -> Unit,
    onUsageOptionSelected: (UsageOptionItem) -> Unit,
    onUsageOptionShowAll: (String) -> Unit,
    routeMapPickTarget: RouteMapPickTarget?,
    onRouteMapPickConfirmed: (FacilityMapItem) -> Unit,
    allFacilities: List<FacilityMapItem>,
    onNearbyFacilitySelected: (String) -> Unit,
    onAskAi: () -> Unit,
    exerciseContents: List<ExerciseContentItem>,
    nameFocus: FacilityNameFocus?,
) {
    val design = LocalNativeDesign.current
    if (stage == FacilitySheetStage.COLLAPSED) {
        Text(
            text = facility.name,
            modifier = Modifier.testTag("facility-name"),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = facility.facilityTypeName
                ?: stringResource(R.string.facility_type_unknown),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    val nameFocusRequester = remember { FocusRequester() }
    if (nameFocus != null && nameFocus.pendingFacilityId == facility.facilityId) {
        LaunchedEffect(nameFocus, facility.facilityId) {
            nameFocusRequester.requestFocus()
            nameFocus.pendingFacilityId = null
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = facility.name,
                modifier = Modifier
                    .testTag("facility-name")
                    .semantics { heading() }
                    .focusRequester(nameFocusRequester)
                    .focusable(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (design.isOriginal) 20.sp else design.style.titleSp.sp,
                    lineHeight = if (design.isOriginal) 26.sp else (design.style.titleSp + 7).sp,
                ),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(3.dp))
            FacilityMetadata(facility = facility, currentLocation = currentLocation)
            FacilityOperationLine(facility.operationState)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FacilityTopIconButton(
                iconRes = if (isFavorite) {
                    R.drawable.ic_material_symbol_star_24
                } else {
                    R.drawable.ic_material_symbol_star_outline_24
                },
                label = stringResource(
                    if (isFavorite) R.string.favorite_remove else R.string.favorite_add,
                ),
                tint = if (isFavorite) FavoriteStarColor else MaterialTheme.colorScheme.onSurface,
                testTag = "facility-favorite-action",
                onClick = { onFavoriteToggled(facility.facilityId) },
            )
            FacilityTopIconButton(
                iconRes = R.drawable.ic_material_symbol_close_24,
                label = stringResource(R.string.facility_close),
                tint = MaterialTheme.colorScheme.onSurface,
                testTag = "facility-close-action",
                onClick = onClose,
            )
        }
    }
    if (routeMapPickTarget != null) {
        Spacer(modifier = Modifier.height(10.dp))
        FacilityActionButton(
            label = stringResource(
                if (routeMapPickTarget == RouteMapPickTarget.ORIGIN) {
                    R.string.route_confirm_origin
                } else {
                    R.string.route_confirm_destination
                },
            ),
            iconRes = if (routeMapPickTarget == RouteMapPickTarget.ORIGIN) {
                R.drawable.ic_material_symbol_route_arrow_24
            } else {
                R.drawable.ic_material_symbol_location_on_24
            },
            testTag = "route-map-pick-facility-confirm",
            emphasized = true,
            onClick = { onRouteMapPickConfirmed(facility) },
            modifier = Modifier.fillMaxWidth(),
        )
        return
    }
    if (stage != FacilitySheetStage.STAGE_2) {
        FacilityPrimaryActions(
            facility = facility,
            onStartRoute = onStartRoute,
            onDestinationRoute = onDestinationRoute,
            onPhone = onPhone,
            onShare = onShare,
            modifier = Modifier.testTag("facility-primary-actions"),
        )
    }
    FacilityMediaGallery(
        facility = facility,
        mediaState = mediaState,
    )
    FacilityAiAction(
        label = stringResource(R.string.facility_ask_ai),
        onClick = onAskAi,
    )
    UsageOptionSection(
        state = usageOptionState,
        today = usageOptionToday,
        onRetry = onUsageOptionRetry,
        onOptionSelected = onUsageOptionSelected,
        onShowAll = onUsageOptionShowAll,
    )
    val matchingExerciseContents = remember(facility.facilityId, exerciseContents) {
        exerciseContentsForFacility(facility, exerciseContents)
    }
    if (matchingExerciseContents.isNotEmpty()) {
        ExerciseContentSection(
            contents = matchingExerciseContents,
            compact = true,
        )
    }
    FacilityInformationSection(facility = facility)
    val nearbyFacilities = remember(facility.facilityId, allFacilities) {
        nearbyFacilitiesForDetails(
            selectedFacility = facility,
            facilities = allFacilities,
        )
    }
    if (nearbyFacilities.isNotEmpty()) {
        NearbyFacilitySection(
            title = stringResource(R.string.facility_nearby_title),
            facilities = nearbyFacilities,
            onFacilitySelected = onNearbyFacilitySelected,
            compact = true,
        )
    }
}

@Composable
private fun FacilityMediaGallery(
    facility: FacilityMapItem,
    mediaState: FacilityMediaState,
) {
    val context = LocalContext.current
    val readyPage = (mediaState as? FacilityMediaState.Ready)?.page
    val images = readyPage?.images.orEmpty().take(4)
    val isLoading = mediaState is FacilityMediaState.Idle ||
        mediaState is FacilityMediaState.Loading

    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.facility_media_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.facility_media_source_label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    val moreLabel = stringResource(R.string.facility_media_more)
    val moreLabelStyle = MaterialTheme.typography.labelMedium.merge(
        TextStyle(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
    ).merge(KoreanPhraseBreak)
    val tileHeight = facilityMediaTileHeight(moreLabel, moreLabelStyle)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(tileHeight)
            .testTag("facility-media-gallery"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isLoading) {
            items(2) { index ->
                Surface(
                    modifier = Modifier
                        .width(144.dp)
                        .height(tileHeight)
                        .testTag("facility-media-loading-$index"),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {}
            }
        } else {
            itemsIndexed(
                items = images,
                key = { _, image -> image.previewUrl },
            ) { index, image ->
                FacilityMediaImage(
                    facilityName = facility.name,
                    image = image,
                    index = index,
                    height = tileHeight,
                )
            }
        }
        item(key = "more-images") {
            Surface(
                onClick = {
                    openNaverImageSearch(
                        context = context,
                        facility = facility,
                        providerUrl = readyPage?.moreImagesUrl,
                    )
                },
                modifier = Modifier
                    .width(FacilityMediaMoreWidth)
                    .height(tileHeight)
                    .testTag("facility-media-more"),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = FacilityMediaMoreHorizontalPadding,
                        vertical = FacilityMediaMoreVerticalPadding,
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_search_24),
                        contentDescription = null,
                        modifier = Modifier.size(FacilityMediaMoreIconSize),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(FacilityMediaMoreIconGap))
                    Text(
                        text = moreLabel,
                        modifier = Modifier.testTag("facility-media-more-label"),
                        style = moreLabelStyle,
                    )
                    Image(
                        painter = painterResource(
                            if (androidx.compose.foundation.isSystemInDarkTheme()) {
                                com.naver.maps.map.R.drawable.navermap_naver_logo_dark
                            } else {
                                com.naver.maps.map.R.drawable.navermap_naver_logo_light
                            },
                        ),
                        contentDescription = stringResource(R.string.facility_media_more_provider_accessibility),
                        modifier = Modifier.size(width = 45.dp, height = FacilityMediaMoreLogoHeight),
                    )
                }
            }
        }
    }
}

/**
 * Gallery tiles stay 100dp tall until large text makes the end card's label taller; then every
 * tile grows with it, so the label and the NAVER logo below it stay whole.
 */
@Composable
private fun facilityMediaTileHeight(label: String, style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density, label, style) {
        with(density) {
            val labelWidth = FacilityMediaMoreWidth.roundToPx() - 2 * FacilityMediaMoreHorizontalPadding.roundToPx()
            val labelHeight = measurer.measure(label, style, constraints = Constraints(maxWidth = labelWidth)).size.height
            val content = 2 * FacilityMediaMoreVerticalPadding.roundToPx() + FacilityMediaMoreIconSize.roundToPx() +
                FacilityMediaMoreIconGap.roundToPx() + labelHeight + FacilityMediaMoreLogoHeight.roundToPx()
            maxOf(FacilityMediaTileMinHeight, content.toDp())
        }
    }
}

@Composable
private fun FacilityMediaImage(
    facilityName: String,
    image: FacilityImageCandidate,
    index: Int,
    height: Dp,
) {
    Surface(
        modifier = Modifier
            .width(144.dp)
            .height(height),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        AsyncImage(
            model = image.previewUrl,
            contentDescription = stringResource(
                R.string.facility_media_image_description,
                facilityName,
                index + 1,
            ),
            modifier = Modifier
                .fillMaxSize()
                .testTag("facility-media-image-$index"),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun FacilityAiAction(
    label: String,
    onClick: () -> Unit,
) {
    val design = LocalNativeDesign.current
    if (!design.isOriginal) {
        NativeFacilityAiAction(label, onClick)
        return
    }
    Spacer(modifier = Modifier.height(18.dp))
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .testTag("facility-ai-action"),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(1.5.dp, FacilityAiGradient),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithCache {
                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = FacilityAiGradient,
                            blendMode = BlendMode.SrcIn,
                        )
                    }
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_material_symbol_chat_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.merge(KoreanPhraseBreak),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun FacilityInformationSection(facility: FacilityMapItem) {
    if (!LocalNativeDesign.current.isOriginal) {
        NativeFacilityInformationSection(facility)
        return
    }
    val classification = listOfNotNull(
        facility.facilityClassName,
        facility.facilityTypeName,
    ).distinct().joinToString("\n")
    val rows = buildList {
        classification.takeIf(String::isNotBlank)?.let { value ->
            add(FacilityInformationEntry(stringResource(R.string.facility_information_classification), value))
        }
        facility.operatingHours?.let { hours ->
            add(
                FacilityInformationEntry(
                    label = stringResource(R.string.facility_information_hours),
                    value = formatOperatingHours(
                        hours = hours,
                        closedDaysText = hours.closedDays.takeIf(List<String>::isNotEmpty)?.let { days ->
                            stringResource(R.string.facility_information_closed_days, days.joinToString(", "))
                        },
                    ),
                    caption = hours.sourceName,
                ),
            )
        }
        facility.grossFloorAreaSquareMeters?.let { area ->
            add(
                FacilityInformationEntry(
                    stringResource(R.string.facility_information_area),
                    formatFacilityArea(area),
                ),
            )
        }
        facility.phoneNumber?.let { phone ->
            add(FacilityInformationEntry(stringResource(R.string.facility_information_phone), phone))
        }
        facility.description?.trim()?.takeIf(String::isNotEmpty)?.let { description ->
            add(FacilityInformationEntry(stringResource(R.string.facility_information_description), description))
        }
    }
    if (rows.isEmpty()) return

    Spacer(modifier = Modifier.height(20.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("facility-information-section"),
    ) {
        Text(
            text = stringResource(R.string.facility_information_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        rows.forEachIndexed { index, entry ->
            FacilityInformationRow(label = entry.label, value = entry.value, caption = entry.caption)
            if (index != rows.lastIndex) NativeHairline()
        }
    }
}

private data class FacilityInformationEntry(
    val label: String,
    val value: String,
    val caption: String? = null,
)

/** Published operator hours only; this text never states whether the facility is open now. */
internal fun formatOperatingHours(
    hours: FacilityOperatingHours,
    closedDaysText: String?,
): String = buildList {
    hours.schedule.forEach { slot -> add("${slot.days} ${slot.hours}") }
    closedDaysText?.let(::add)
    hours.note?.let(::add)
}.joinToString("\n")

internal fun formatOperatingHoursCheckedDate(hours: FacilityOperatingHours): String =
    hours.checkedAt
        .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
        .format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN))

@Composable
private fun FacilityInformationRow(
    label: String,
    value: String,
    caption: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(70.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                ),
            )
            caption?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    ),
                )
            }
        }
    }
}

internal data class FacilityDistanceItem(
    val facility: FacilityMapItem,
    val distanceMeters: Int?,
)

internal fun nearbyFacilitiesForDetails(
    selectedFacility: FacilityMapItem,
    facilities: List<FacilityMapItem>,
    limit: Int = 3,
): List<FacilityDistanceItem> {
    require(limit > 0)
    val selectedCategory = FacilityCategory.fromFacility(selectedFacility)
    val selectedCoordinate = RouteCoordinate(
        latitude = selectedFacility.latitude,
        longitude = selectedFacility.longitude,
    )
    val selectedName = normalizedFacilityName(selectedFacility.name)
    return facilities.asSequence()
        .filterNot { it.facilityId == selectedFacility.facilityId }
        .map { facility ->
            val distance = facilityDistanceMeters(selectedCoordinate, facility)
            Triple(
                facility,
                distance,
                FacilityCategory.fromFacility(facility) == selectedCategory,
            )
        }
        .filterNot { (facility, distance, _) ->
            distance <= SameVenueComparisonRadiusMeters &&
                normalizedFacilityName(facility.name) == selectedName
        }
        .sortedWith(
            compareByDescending<Triple<FacilityMapItem, Int, Boolean>> { it.third }
                .thenBy { it.second }
                .thenBy { it.first.name },
        )
        .distinctBy { (facility, _, _) -> normalizedFacilityName(facility.name) }
        .take(limit)
        .map { (facility, distance, _) -> FacilityDistanceItem(facility, distance) }
        .toList()
}

@Composable
private fun NearbyFacilitySection(
    title: String,
    facilities: List<FacilityDistanceItem>,
    onFacilitySelected: (String) -> Unit,
    favoriteFacilityIds: Set<String> = emptySet(),
    firstSection: Boolean = false,
    compact: Boolean = false,
) {
    if (!firstSection) Spacer(modifier = Modifier.height(if (compact) 20.dp else 26.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("facility-nearby-section"),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = if (compact) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 6.dp))
        facilities.forEachIndexed { index, item ->
            NearbyFacilityRow(
                item = item,
                isFavorite = item.facility.facilityId in favoriteFacilityIds,
                onClick = { onFacilitySelected(item.facility.facilityId) },
                compact = compact,
            )
            if (index != facilities.lastIndex) {
                NativeHairline()
            }
        }
    }
}

@Composable
private fun NearbyFacilityRow(
    item: FacilityDistanceItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val facility = item.facility
    val category = FacilityCategory.fromFacility(facility)
    val categoryColor = if (LocalNativeDesign.current.variant == NativeDesignVariant.COMBINED) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(category.markerColor)
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("facility-nearby-row-${facility.facilityId}"),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(vertical = if (compact) 7.dp else 9.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(if (compact) 32.dp else 36.dp),
                shape = CircleShape,
                color = if (isFavorite) {
                    FavoriteStarColor.copy(alpha = 0.14f)
                } else {
                    categoryColor.copy(alpha = 0.13f)
                },
            ) {
                Icon(
                    painter = painterResource(
                        if (isFavorite) {
                            R.drawable.ic_material_symbol_star_24
                        } else {
                            category.iconRes
                        },
                    ),
                    contentDescription = if (isFavorite) {
                        stringResource(R.string.favorite_saved)
                    } else {
                        stringResource(category.labelRes)
                    },
                    modifier = Modifier.padding(if (compact) 7.dp else 8.dp),
                    tint = if (isFavorite) FavoriteStarColor else categoryColor,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = facility.name,
                    style = if (compact) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                facility.facilityTypeName?.let { type ->
                    Text(
                        text = type,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = if (compact) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item.distanceMeters?.let { distance ->
                Text(
                    text = formatFacilityDistance(distance),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = if (compact) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelLarge
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun DiscoveryFeedMetadata(
    weatherSourceName: String?,
) {
    if (weatherSourceName == null) return

    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discovery-feed-metadata"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.discovery_weather_attribution,
                weatherSourceName,
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("discovery-weather-attribution"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun formatFacilityArea(areaSquareMeters: Double): String {
    val formatter = java.text.DecimalFormat(
        "#,##0.#",
        java.text.DecimalFormatSymbols(java.util.Locale.KOREA),
    )
    return "${formatter.format(areaSquareMeters)}㎡"
}

internal fun formatFacilityDataDate(asOf: String): String = asOf
    .substringBefore('T')
    .takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
    ?.replace("-", ". ")
    ?: asOf

private fun List<FacilityMapItem>.sortedByDistanceFrom(
    origin: RouteCoordinate?,
): List<FacilityMapItem> = if (origin != null) {
    sortedWith(compareBy<FacilityMapItem> { facilityDistanceMeters(origin, it) }.thenBy { it.name })
} else {
    sortedWith(
        compareByDescending<FacilityMapItem> { it.grossFloorAreaSquareMeters ?: 0.0 }
            .thenBy { it.name },
    )
}

private fun List<FacilityMapItem>.toDistanceItems(
    origin: RouteCoordinate?,
): List<FacilityDistanceItem> = map { facility ->
    FacilityDistanceItem(
        facility = facility,
        distanceMeters = origin?.let { facilityDistanceMeters(it, facility) },
    )
}

internal fun normalizedFacilityName(name: String): String = name
    .lowercase(java.util.Locale.KOREA)
    .filter(Char::isLetterOrDigit)

/**
 * The confirmed operation state under the facility header. Any other state shows no line, so the
 * action buttons follow the address closely instead of after an empty line.
 */
@Composable
private fun FacilityOperationLine(state: EvidenceState) {
    // The address row above already ends in space below its text, so the state stays closer to the
    // facility details than to the action buttons.
    Spacer(modifier = Modifier.height(4.dp))
    val label = facilityOperationLabel(state)?.let { stringResource(it) } ?: return
    Text(
        text = label,
        modifier = Modifier.testTag("facility-operation-state"),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@StringRes
internal fun facilityOperationLabel(state: EvidenceState): Int? = when (state) {
    EvidenceState.OPEN -> R.string.operation_open
    EvidenceState.CLOSED -> R.string.operation_closed
    EvidenceState.UNKNOWN -> null
}

@Composable
private fun FacilityMetadata(
    facility: FacilityMapItem,
    currentLocation: RouteCoordinate?,
) {
    val context = LocalContext.current
    var addressExpanded by rememberSaveable(facility.facilityId) { mutableStateOf(false) }
    val typeLabels = listOfNotNull(
        facility.facilityClassName,
        facility.facilityTypeName,
    ).distinct()
    if (typeLabels.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            typeLabels.forEach { label ->
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val distance = currentLocation?.let { location ->
        facilityDistanceMeters(location, facility)
    }
    val administrativeArea = compactAdministrativeArea(facility)
    val hasAddressDetails = facility.roadAddress != null || facility.lotAddress != null
    if (distance != null || administrativeArea != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("facility-distance-area-row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (distance != null) {
                Text(
                    text = formatFacilityDistance(distance),
                    modifier = Modifier.testTag("facility-distance"),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            administrativeArea?.let { area ->
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides 40.dp,
                ) {
                    Surface(
                        onClick = { if (hasAddressDetails) addressExpanded = !addressExpanded },
                        enabled = hasAddressDetails,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("facility-address-summary"),
                        color = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Row(
                            modifier = Modifier.height(40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = area,
                                // Beside a distance under large text, the area shortens instead of
                                // squeezing the arrow.
                                modifier = Modifier.weight(1f, fill = false),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (hasAddressDetails) {
                                Icon(
                                    painter = painterResource(
                                        R.drawable.ic_material_symbol_arrow_drop_down_24,
                                    ),
                                    contentDescription = stringResource(
                                        if (addressExpanded) {
                                            R.string.address_collapse
                                        } else {
                                            R.string.address_expand
                                        },
                                    ),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .rotate(if (addressExpanded) 180f else 0f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    AnimatedVisibility(
        visible = addressExpanded && hasAddressDetails,
        enter = expandVertically(
            animationSpec = tween(180),
            expandFrom = Alignment.Top,
        ) + fadeIn(animationSpec = tween(140)),
        exit = shrinkVertically(
            animationSpec = tween(160),
            shrinkTowards = Alignment.Top,
        ) + fadeOut(animationSpec = tween(120)),
        label = "facility-address-details-disclosure",
    ) {
        val roadLabel = stringResource(R.string.address_road)
        val lotLabel = stringResource(R.string.address_lot)
        val labelStyle = MaterialTheme.typography.labelMedium.merge(
            TextStyle(fontWeight = FontWeight.SemiBold),
        ).merge(KoreanPhraseBreak)
        val measurer = rememberTextMeasurer()
        val labelWidthPx = with(LocalDensity.current) { AddressLabelWidth.roundToPx() }
        // Both rows move their labels above the addresses together once either label no longer fits.
        val stackLabels = remember(measurer, labelStyle, roadLabel, lotLabel, labelWidthPx) {
            measurer.widestWord(listOf(roadLabel, lotLabel), labelStyle) > labelWidthPx
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag("facility-address-details"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            facility.roadAddress?.let { address ->
                AddressCopyRow(
                    label = roadLabel,
                    address = address,
                    labelStyle = labelStyle,
                    stackLabel = stackLabels,
                    onCopy = { copyAddress(context, address) },
                )
            }
            facility.lotAddress?.let { address ->
                AddressCopyRow(
                    label = lotLabel,
                    address = address,
                    labelStyle = labelStyle,
                    stackLabel = stackLabels,
                    onCopy = { copyAddress(context, address) },
                )
            }
        }
    }
}

/**
 * One address with its copy button. Under large text whose label no longer fits beside the
 * address, the label sits above it instead.
 */
@Composable
private fun AddressCopyRow(
    label: String,
    address: String,
    labelStyle: TextStyle,
    stackLabel: Boolean,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labelText: @Composable (Modifier) -> Unit = { labelModifier ->
            Text(
                text = label,
                modifier = labelModifier,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = labelStyle,
            )
        }
        val addressText: @Composable (Modifier) -> Unit = { addressModifier ->
            Text(
                text = address,
                modifier = addressModifier,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.merge(KoreanPhraseBreak),
            )
        }
        if (stackLabel) {
            Column(modifier = Modifier.weight(1f)) {
                labelText(Modifier)
                addressText(Modifier)
            }
        } else {
            labelText(Modifier.width(AddressLabelWidth))
            addressText(Modifier.weight(1f))
        }
        androidx.compose.runtime.CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides 40.dp,
        ) {
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("facility-address-copy-${label}"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_material_symbol_content_copy_24),
                    contentDescription = stringResource(R.string.address_copy, label),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

internal fun compactAdministrativeArea(facility: FacilityMapItem): String? {
    val sido = facility.sidoName?.trim()?.takeIf(String::isNotEmpty)
    val sigungu = facility.sigunguName?.trim()?.takeIf(String::isNotEmpty)
    if (sido != null || sigungu != null) {
        val shortSido = when (sido) {
            "서울특별시" -> "서울"
            "부산광역시" -> "부산"
            "대구광역시" -> "대구"
            "인천광역시" -> "인천"
            "광주광역시" -> "광주"
            "대전광역시" -> "대전"
            "울산광역시" -> "울산"
            "세종특별자치시" -> "세종"
            "경기도" -> "경기"
            "강원특별자치도", "강원도" -> "강원"
            "충청북도" -> "충북"
            "충청남도" -> "충남"
            "전북특별자치도", "전라북도" -> "전북"
            "전라남도" -> "전남"
            "경상북도" -> "경북"
            "경상남도" -> "경남"
            "제주특별자치도" -> "제주"
            else -> sido
        }
        return listOfNotNull(shortSido, sigungu).distinct().joinToString(" ")
    }
    return (facility.roadAddress ?: facility.lotAddress)
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.take(2)
        ?.joinToString(" ")
        ?.takeIf(String::isNotBlank)
}

private fun copyAddress(context: Context, address: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("Gymvi address", address))
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        Toast.makeText(context, R.string.address_copied, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun FacilityTopIconButton(
    iconRes: Int,
    label: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .testTag(testTag)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
internal fun FacilityPrimaryActions(
    facility: FacilityMapItem,
    onStartRoute: (FacilityMapItem) -> Unit,
    onDestinationRoute: (FacilityMapItem) -> Unit,
    onPhone: (FacilityMapItem) -> Unit,
    onShare: (FacilityMapItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!LocalNativeDesign.current.usesOriginalMapChrome) {
        NativeFacilityActions(facility, onStartRoute, onDestinationRoute, onPhone, onShare, modifier)
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FacilityActionButton(
            label = stringResource(R.string.facility_start),
            iconRes = R.drawable.ic_material_symbol_route_arrow_24,
            testTag = "facility-route-start",
            onClick = { onStartRoute(facility) },
            modifier = Modifier.weight(1f),
        )
        FacilityActionButton(
            label = stringResource(R.string.facility_arrive),
            iconRes = R.drawable.ic_material_symbol_location_on_24,
            testTag = "facility-route-arrive",
            emphasized = true,
            onClick = { onDestinationRoute(facility) },
            modifier = Modifier.weight(1f),
        )
        FacilityActionButton(
            label = stringResource(R.string.facility_call),
            iconRes = R.drawable.ic_material_symbol_call_24,
            testTag = "facility-call",
            enabled = facility.phoneNumber != null,
            onClick = { onPhone(facility) },
            modifier = Modifier.weight(1f),
        )
        FacilityActionButton(
            label = stringResource(R.string.facility_share),
            iconRes = R.drawable.ic_material_symbol_share_24,
            testTag = "facility-share",
            onClick = { onShare(facility) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun FacilityActionButton(
    label: String,
    iconRes: Int,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val design = LocalNativeDesign.current
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant
        emphasized -> MaterialTheme.colorScheme.primary
        else -> if (design.usesOriginalMapChrome) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.outline
        emphasized -> MaterialTheme.colorScheme.onPrimary
        else -> if (design.usesOriginalMapChrome) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(if (design.usesOriginalMapChrome) 42.dp else 48.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(if (design.usesOriginalMapChrome) 21.dp else design.style.cornerDp.dp),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun formatFacilityDistance(distanceMeters: Int): String = if (distanceMeters < 1_000) {
    stringResource(R.string.facility_distance_meters, distanceMeters)
} else {
    stringResource(R.string.facility_distance_kilometers, distanceMeters / 1_000.0)
}

internal fun facilityDistanceMeters(
    origin: RouteCoordinate,
    facility: FacilityMapItem,
): Int {
    val latitudeDelta = Math.toRadians(facility.latitude - origin.latitude)
    val longitudeDelta = Math.toRadians(facility.longitude - origin.longitude)
    val originLatitude = Math.toRadians(origin.latitude)
    val destinationLatitude = Math.toRadians(facility.latitude)
    val haversine = sin(latitudeDelta / 2).let { it * it } +
        cos(originLatitude) * cos(destinationLatitude) *
        sin(longitudeDelta / 2).let { it * it }
    return (6_371_000.0 * 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))).roundToInt()
}

@Composable
private fun ErrorPanel(
    title: String,
    body: String,
    errorCode: String?,
    onRetry: () -> Unit,
    compact: Boolean,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    if (compact) return
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
    if (!errorCode.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = stringResource(R.string.error_code, errorCode),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onRetry,
        modifier = Modifier.testTag("retry-button"),
    ) {
        Text(stringResource(R.string.retry))
    }
}

private fun openFacilityPhone(context: Context, facility: FacilityMapItem) {
    val phone = facility.phoneNumber
    if (phone == null) {
        Toast.makeText(context, R.string.facility_phone_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.facility_phone_unavailable, Toast.LENGTH_SHORT).show()
    }
}

private fun shareFacility(context: Context, facility: FacilityMapItem) {
    val details = listOfNotNull(
        facility.name,
        facility.facilityClassName,
        facility.facilityTypeName,
        facility.roadAddress,
    ).distinct().joinToString(separator = "\n")
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, details)
    }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.facility_share_title),
        ),
    )
}

internal fun openExerciseContent(context: Context, rawUrl: String) {
    val uri = runCatching { Uri.parse(rawUrl) }.getOrNull()
    val host = uri?.host?.lowercase().orEmpty()
    if (
        uri?.scheme != "https" ||
        host !in setOf("www.youtube.com", "youtube.com", "youtu.be")
    ) {
        Toast.makeText(context, R.string.discovery_content_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.discovery_content_unavailable, Toast.LENGTH_SHORT).show()
    }
}

private fun AddressSearchItem.nearbyFacilityBounds(): FacilityBounds = FacilityBounds(
    minLongitude = longitude - 0.008,
    minLatitude = latitude - 0.006,
    maxLongitude = longitude + 0.008,
    maxLatitude = latitude + 0.006,
)

private fun Context.hasAnyLocationPermission(): Boolean =
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

internal fun shouldStartLocationTrackingOnLaunch(
    hasLocationPermission: Boolean,
    locationRequestKey: Int,
): Boolean = hasLocationPermission && locationRequestKey == 0

/** Facilities first load on a location fix, so they are coming only once location is granted or requested. */
internal fun isWaitingForFirstLocation(
    hasLocationPermissionAtLaunch: Boolean,
    locationRequestKey: Int,
): Boolean = hasLocationPermissionAtLaunch || locationRequestKey > 0

internal fun shouldExitAfterBack(
    lastPromptAtMillis: Long,
    nowMillis: Long,
): Boolean = lastPromptAtMillis > 0L &&
    nowMillis >= lastPromptAtMillis &&
    nowMillis - lastPromptAtMillis <= ExitConfirmationWindowMillis

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

private const val ExitConfirmationWindowMillis = 2_000L
private const val SameVenueComparisonRadiusMeters = 150
private val SystemNavigationBarLight = Color(0xFF66727C)
private val SystemNavigationBarDark = Color(0xFF273138)
private val FavoriteStarColor = Color(0xFFF2B705)
private val FacilityMediaTileMinHeight = 100.dp
private val FacilityMediaMoreWidth = 128.dp
private val FacilityMediaMoreHorizontalPadding = 12.dp
private val FacilityMediaMoreVerticalPadding = 9.dp
private val FacilityMediaMoreIconSize = 20.dp
private val FacilityMediaMoreIconGap = 5.dp
private val FacilityMediaMoreLogoHeight = 10.dp
private val DiscoveryProposalMinWidth = 188.dp
private val DiscoveryProposalMinHeight = 56.dp
private val DiscoveryProposalHorizontalPadding = 10.dp
private val DiscoveryProposalVerticalPadding = 7.dp
private val DiscoveryProposalIconSize = 24.dp
private val DiscoveryProposalIconGap = 7.dp
private val AddressLabelWidth = 42.dp
private val FacilityAiGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF24CCF9),
        Color(0xFF46ACF7),
        Color(0xFF8078F1),
    ),
)

private val GymviLightColors = lightColorScheme(
    primary = Color(0xFF006A94),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6E7FF),
    onPrimaryContainer = Color(0xFF003548),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF17212B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17212B),
    surfaceVariant = Color(0xFFE8EDF2),
    onSurfaceVariant = Color(0xFF4B5964),
    outline = Color(0xFF73808B),
    outlineVariant = Color(0xFFD7DEE4),
)

private val GymviDarkColors = darkColorScheme(
    primary = Color(0xFF85CFFF),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF004C6A),
    onPrimaryContainer = Color(0xFFC6E7FF),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E7EC),
    surface = Color(0xFF171C20),
    onSurface = Color(0xFFE1E7EC),
    surfaceVariant = Color(0xFF273138),
    onSurfaceVariant = Color(0xFFBEC8CF),
    outline = Color(0xFF89949C),
    outlineVariant = Color(0xFF39444B),
)

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun GymviAppPreview() {
    GymviApp()
}
