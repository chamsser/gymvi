package io.github.chamsser.gymvi.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getScrollViewportLength
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag as semanticsTestTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AddressSearchItem
import io.github.chamsser.gymvi.data.FacilityMapItem
import io.github.chamsser.gymvi.data.UsageOptionItem
import io.github.chamsser.gymvi.data.FeedUsageOption
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class FacilitySheetPresentation(
    val stage: FacilitySheetStage,
    val followedBottomClearance: Dp,
    val mapBottomClearance: Dp = followedBottomClearance,
) {
    companion object {
        val Initial = FacilitySheetPresentation(
            stage = FacilitySheetStage.COLLAPSED,
            followedBottomClearance = 104.dp,
        )
    }
}

@Composable
internal fun AnchoredFacilitySheet(
    uiState: MapUiState,
    selectedAddress: AddressSearchItem? = null,
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
    searchResultRequestKey: Int = 0,
    onPresentationChanged: (FacilitySheetPresentation) -> Unit,
    collapseRequestKey: Int = 0,
    expandRequestKey: Int = 0,
    modifier: Modifier = Modifier,
) {
    val design = LocalNativeDesign.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val statusBarPx = if (LocalNativeStatusBarConsumed.current) 0f else WindowInsets.statusBars.getTop(density).toFloat()
        val navigationBarPx = if (!design.isOriginal && design.style.navigation == NativeNavigation.BOTTOM_TABS) 0f else WindowInsets.navigationBars.getBottom(density).toFloat()
        val expandedTopGapPx = statusBarPx
        val expandedHeightPx = (constraints.maxHeight - expandedTopGapPx)
            .coerceAtLeast(with(density) { 360.dp.toPx() })
            .coerceAtMost(constraints.maxHeight.toFloat())
        val handleHeightPx = with(density) { FacilitySheetHandleHeight.toPx() }
        val screenReaderPageOverlapPx = with(density) { FacilitySheetScreenReaderPageOverlap.toPx() }
        val collapsedHeightPx = (handleHeightPx + navigationBarPx)
            .coerceAtMost(expandedHeightPx)
        val stage1Fraction = when (design.variant) {
            NativeDesignVariant.GPT_A -> .38f
            NativeDesignVariant.GPT_C -> .52f
            NativeDesignVariant.CLAUDE_B, NativeDesignVariant.GPT_B -> .46f
            NativeDesignVariant.CLAUDE_A, NativeDesignVariant.CLAUDE_C -> .44f
            else -> .40f
        }
        val stage1HeightPx = (constraints.maxHeight * stage1Fraction)
            .coerceIn(collapsedHeightPx, expandedHeightPx)
        val anchors = FacilitySheetAnchors(
            stage2OffsetPx = 0f,
            stage1OffsetPx = expandedHeightPx - stage1HeightPx,
            collapsedOffsetPx = expandedHeightPx - collapsedHeightPx,
        )
        val shouldStartAtStage1 = uiState.selectedFacilityId != null ||
            selectedAddress != null ||
            uiState.facilityLoadState.isActionableError
        val motion = remember(scope) {
            FacilitySheetMotion(
                initialStage = if (shouldStartAtStage1) {
                    FacilitySheetStage.STAGE_1
                } else {
                    FacilitySheetStage.COLLAPSED
                },
                scope = scope,
            )
        }
        val scrollState = rememberScrollState()
        val searchListState = rememberLazyListState()
        val usesSearchList = selectedAddress == null && uiState.selectedFacility == null &&
            searchQuery.isNotBlank() &&
            (uiState.facilityLoadState == FacilityLoadState.READY || uiState.facilityLoadState == FacilityLoadState.EMPTY)
        var previousSelectedFacilityId by remember {
            mutableStateOf(uiState.selectedFacilityId)
        }
        var previousSelectedAddressId by remember {
            mutableStateOf(selectedAddress?.stableId)
        }
        var previousLoadState by remember {
            mutableStateOf(uiState.facilityLoadState)
        }
        var revealedSearchResultRequestKey by remember { mutableStateOf(0) }
        val facilityNameFocus = remember { FacilityNameFocus() }

        SideEffect {
            motion.design = design
            motion.bindAnchors(anchors)
        }
        DisposableEffect(motion) {
            onDispose(motion::dispose)
        }
        LaunchedEffect(
            uiState.selectedFacilityId,
            selectedAddress?.stableId,
            uiState.facilityLoadState,
        ) {
            val selectedFacilityId = uiState.selectedFacilityId
            val selectedFacilityChanged =
                selectedFacilityId != null &&
                selectedFacilityId != previousSelectedFacilityId
            val selectedAddressChanged = selectedAddress != null &&
                selectedAddress.stableId != previousSelectedAddressId
            val actionableErrorAppeared = uiState.facilityLoadState.isActionableError &&
                uiState.facilityLoadState != previousLoadState
            if (selectedFacilityId != previousSelectedFacilityId) {
                facilityNameFocus.pendingFacilityId = selectedFacilityId
            }
            if (selectedFacilityChanged || selectedAddressChanged || actionableErrorAppeared) {
                scrollState.scrollTo(0)
                motion.animateTo(FacilitySheetStage.STAGE_1, anchors)
            }
            previousSelectedFacilityId = selectedFacilityId
            previousSelectedAddressId = selectedAddress?.stableId
            previousLoadState = uiState.facilityLoadState
        }
        LaunchedEffect(
            searchResultRequestKey,
            searchQuery,
            uiState.facilityLoadState,
            uiState.facilities,
        ) {
            if (
                searchResultRequestKey > revealedSearchResultRequestKey &&
                searchQuery.isNotBlank() &&
                uiState.facilityLoadState == FacilityLoadState.READY &&
                uiState.facilities.isNotEmpty()
            ) {
                scrollState.scrollTo(0)
                motion.animateTo(FacilitySheetStage.STAGE_1, anchors)
                revealedSearchResultRequestKey = searchResultRequestKey
                searchListState.scrollToItem(0)
            }
        }
        // Collapse and expand requests are edges, not levels: a sheet created after a request
        // (for example on returning from the route screen) starts from the current selection,
        // so only keys that change while it exists count and an old request never replays.
        var handledCollapseRequestKey by remember { mutableIntStateOf(collapseRequestKey) }
        LaunchedEffect(collapseRequestKey) {
            if (collapseRequestKey > 0 && collapseRequestKey != handledCollapseRequestKey) {
                handledCollapseRequestKey = collapseRequestKey
                if (motion.stage != FacilitySheetStage.COLLAPSED) {
                    motion.animateTo(FacilitySheetStage.COLLAPSED, anchors)
                }
            }
        }
        // Opens the sheet on request even when the facility is already selected, which the
        // selection effect above cannot see.
        var handledExpandRequestKey by remember { mutableIntStateOf(expandRequestKey) }
        LaunchedEffect(expandRequestKey) {
            if (expandRequestKey > 0 && expandRequestKey != handledExpandRequestKey) {
                handledExpandRequestKey = expandRequestKey
                scrollState.scrollTo(0)
                motion.animateTo(FacilitySheetStage.STAGE_1, anchors)
            }
        }

        val contentStage = remember(motion, anchors) {
            derivedStateOf {
                facilitySheetContentStage(motion.stage, motion.offsetForLayout(anchors), anchors.collapsedOffsetPx)
            }
        }.value
        val currentOnPresentationChanged by rememberUpdatedState(onPresentationChanged)
        // Offset is a layout/frame value. Reading it in composition rebuilt the sheet content
        // for every drag tick, even though only its position and the map viewport changed.
        LaunchedEffect(motion, anchors, density, expandedHeightPx, stage1HeightPx) {
            snapshotFlow {
                val clearance = with(density) {
                    (facilitySheetMapClearancePx(expandedHeightPx, motion.offsetForLayout(anchors), stage1HeightPx) + 12.dp.toPx()).toDp()
                }
                FacilitySheetPresentation(motion.stage, clearance)
            }.collect { currentOnPresentationChanged(it) }
        }

        val draggableState = rememberDraggableState { delta ->
            motion.dragBy(delta, anchors)
        }
        // Screen readers, focus moves and mouse wheels also scroll the content, with the same
        // source as a drag. Only a finger on the content hands its scroll to the sheet, so reading
        // through the content never moves the sheet away from the stage it announces.
        val contentTouch = remember { SheetContentTouch() }
        val nestedScrollConnection = remember(anchors, scrollState, searchListState, usesSearchList, motion) {
            object : NestedScrollConnection {
                private fun atTop() = if (usesSearchList) !searchListState.canScrollBackward else scrollState.value == 0
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (
                        source == NestedScrollSource.UserInput &&
                        contentTouch.pressed &&
                        available.y < 0f &&
                        motion.offsetForLayout(anchors) > anchors.stage2OffsetPx
                    ) {
                        return Offset(
                            x = 0f,
                            y = motion.dragBy(available.y, anchors),
                        )
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (
                        source == NestedScrollSource.UserInput &&
                        contentTouch.pressed &&
                        available.y > 0f &&
                        atTop() &&
                        motion.offsetForLayout(anchors) < anchors.collapsedOffsetPx
                    ) {
                        return Offset(
                            x = 0f,
                            y = motion.dragBy(available.y, anchors),
                        )
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    // A list drag can reach an anchor before release. Commit that stage even
                    // when the sheet has no pixels left to consume, then let the list fling.
                    val offset = motion.offsetForLayout(anchors)
                    if (offset <= anchors.stage2OffsetPx && motion.stage != FacilitySheetStage.STAGE_2) {
                        motion.animateTo(FacilitySheetStage.STAGE_2, anchors)
                        return Velocity.Zero
                    }
                    if (offset >= anchors.collapsedOffsetPx && motion.stage != FacilitySheetStage.COLLAPSED) {
                        motion.animateTo(FacilitySheetStage.COLLAPSED, anchors)
                        return Velocity(x = 0f, y = available.y)
                    }
                    val movingUp = available.y < 0f &&
                        motion.offsetForLayout(anchors) > anchors.stage2OffsetPx
                    val movingDown = available.y > 0f &&
                        atTop() &&
                        motion.offsetForLayout(anchors) < anchors.collapsedOffsetPx
                    if (movingUp || movingDown || offset != anchors.offsetFor(motion.stage)) {
                        motion.settle(available.y, anchors)
                        return Velocity(x = 0f, y = available.y)
                    }
                    return Velocity.Zero
                }
            }
        }
        val handleDescription = when (motion.stage) {
            FacilitySheetStage.COLLAPSED -> stringResource(
                R.string.facility_sheet_collapsed_state,
            )

            FacilitySheetStage.STAGE_1 -> stringResource(R.string.facility_sheet_stage_1_state)
            FacilitySheetStage.STAGE_2 -> stringResource(R.string.facility_sheet_stage_2_state)
        }
        val handleAction = when (motion.stage) {
            FacilitySheetStage.COLLAPSED,
            FacilitySheetStage.STAGE_1,
            -> stringResource(R.string.facility_sheet_expand)

            FacilitySheetStage.STAGE_2 -> stringResource(R.string.facility_sheet_reduce)
        }
        val handleLabel = stringResource(R.string.facility_sheet_label)
        val expandOrReduce = {
            motion.animateTo(motion.stage.nextForTap(), anchors)
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = if (design.variant == NativeDesignVariant.CLAUDE_B) 10.dp else 0.dp)
                .fillMaxWidth()
                .height(with(density) { expandedHeightPx.toDp() })
                .offset {
                    IntOffset(
                        x = 0,
                        y = motion.offsetForLayout(anchors).roundToInt(),
                    )
                }
                .testTag("facility-sheet-${motion.stage.testTagSuffix}"),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = if (design.usesOriginalMapChrome) 28.dp else design.style.cornerDp.dp,
                topEnd = if (design.usesOriginalMapChrome) 28.dp else design.style.cornerDp.dp,
            ),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = if (design.usesOriginalMapChrome) 12.dp else if (design.style.surface == NativeSurface.FLOATING) 6.dp else 0.dp,
            border = if (!design.usesOriginalMapChrome && design.style.surface in listOf(NativeSurface.LINES, NativeSurface.COMPACT)) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FacilitySheetHandleHeight)
                        .draggable(
                            state = draggableState,
                            orientation = Orientation.Vertical,
                            onDragStarted = { motion.stopAnimation() },
                            onDragStopped = { velocity -> motion.settle(velocity, anchors) },
                        )
                        .clickable(onClick = expandOrReduce)
                        .clearAndSetSemantics {
                            contentDescription = handleLabel
                            stateDescription = handleDescription
                            role = Role.Button
                            semanticsTestTag = "facility-sheet-handle"
                            onClick(label = handleAction) {
                                expandOrReduce()
                                true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(if (!design.usesOriginalMapChrome && design.style.surface == NativeSurface.COMPACT) 24.dp else 36.dp)
                            .height(4.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                if (contentStage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // The sheet slides below the screen edge instead of shrinking, so the
                            // partial stages hide its lower part. The scrollable content gets only
                            // the visible part: screen reader page scrolls and focus moves use the
                            // viewport size, and an off-screen viewport skips what nobody saw.
                            .layout { measurable, constraints ->
                                val visibleHeight = (expandedHeightPx - motion.offsetForLayout(anchors) - handleHeightPx)
                                    .roundToInt()
                                    .coerceIn(0, constraints.maxHeight)
                                val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = visibleHeight))
                                val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
                                layout(placeable.width, height) { placeable.place(0, 0) }
                            }
                            .trackSheetContentTouch(contentTouch)
                            .nestedScroll(nestedScrollConnection)
                            .then(if (usesSearchList) Modifier.testTag("facility-panel") else Modifier.screenReaderPages(scrollState, screenReaderPageOverlapPx).verticalScroll(scrollState)),
                    ) {
                        if (usesSearchList) FacilitySearchList(
                            uiState = uiState,
                            state = searchListState,
                            favoriteFacilityIds = favoriteFacilityIds,
                            currentLocation = currentLocation,
                            resultSort = resultSort,
                            onResultSortChanged = onResultSortChanged,
                            ownershipFilter = ownershipFilter,
                            onOwnershipFilterChanged = onOwnershipFilterChanged,
                            onFacilitySelected = onResultFacilitySelected,
                            onLoadMore = onFacilityLoadMore,
                        ) else FacilityPanel(
                            uiState = uiState,
                            selectedAddress = selectedAddress,
                            stage = contentStage,
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
                            facilityNameFocus = facilityNameFocus,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val selectedFacility = uiState.selectedFacility
                    if (
                        contentStage == FacilitySheetStage.STAGE_2 &&
                        selectedFacility != null &&
                        routeMapPickTarget == null
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("facility-stage2-action-bar"),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = if (design.usesOriginalMapChrome) 5.dp else 0.dp,
                        ) {
                            Column {
                                HorizontalDivider(
                                    color = gymviSubtleBorderColor(),
                                )
                                FacilityPrimaryActions(
                                    facility = selectedFacility,
                                    onStartRoute = onStartRouteRequested,
                                    onDestinationRoute = onDestinationRouteRequested,
                                    onPhone = onPhoneRequested,
                                    onShare = onShareRequested,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 6.dp,
                                    ),
                                )
                            }
                        }
                    }
                    if (
                        contentStage == FacilitySheetStage.STAGE_2 &&
                        uiState.selectedFacilityId == null &&
                        selectedAddress == null &&
                        routeMapPickTarget == null
                    ) {
                        // This is not Back: keep the search, result order and AI return target.
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FilledTonalButton(
                                onClick = { scope.launch { motion.animateTo(FacilitySheetStage.COLLAPSED, anchors) } },
                                modifier = Modifier.testTag("facility-return-to-map"),
                            ) {
                                Text(stringResource(R.string.facility_return_to_map))
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun facilitySheetContentStage(
    settledStage: FacilitySheetStage,
    displayedOffsetPx: Float,
    collapsedOffsetPx: Float,
): FacilitySheetStage? = when {
    settledStage != FacilitySheetStage.COLLAPSED -> settledStage
    displayedOffsetPx < collapsedOffsetPx - 1f -> FacilitySheetStage.STAGE_1
    else -> null
}

private class FacilitySheetMotion(
    initialStage: FacilitySheetStage,
    private val scope: CoroutineScope,
) {
    var design: NativeDesign = NativeDesign()
    var stage by mutableStateOf(initialStage)
        private set

    private var anchors: FacilitySheetAnchors? = null
    private var offsetPx by mutableFloatStateOf(Float.NaN)
    private var animationJob: Job? = null

    fun bindAnchors(newAnchors: FacilitySheetAnchors) {
        if (anchors == newAnchors) return
        animationJob?.cancel()
        anchors = newAnchors
        offsetPx = newAnchors.offsetFor(stage)
    }

    fun offsetForLayout(currentAnchors: FacilitySheetAnchors): Float =
        if (anchors == currentAnchors && !offsetPx.isNaN()) {
            currentAnchors.coerce(offsetPx)
        } else {
            currentAnchors.offsetFor(stage)
        }

    fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    fun dragBy(deltaPx: Float, currentAnchors: FacilitySheetAnchors): Float {
        stopAnimation()
        val previous = offsetForLayout(currentAnchors)
        val updated = currentAnchors.coerce(previous + deltaPx)
        anchors = currentAnchors
        offsetPx = updated
        return updated - previous
    }

    fun settle(
        velocityPxPerSecond: Float,
        currentAnchors: FacilitySheetAnchors,
    ) {
        animateTo(
            targetStage = settleFacilitySheetStage(
                currentStage = stage,
                offsetPx = offsetForLayout(currentAnchors),
                velocityPxPerSecond = velocityPxPerSecond,
                anchors = currentAnchors,
            ),
            currentAnchors = currentAnchors,
            initialVelocityPxPerSecond = velocityPxPerSecond,
        )
    }

    fun animateTo(
        targetStage: FacilitySheetStage,
        currentAnchors: FacilitySheetAnchors,
        initialVelocityPxPerSecond: Float = 0f,
    ) {
        stopAnimation()
        anchors = currentAnchors
        val initialOffset = offsetForLayout(currentAnchors)
        val targetOffset = currentAnchors.offsetFor(targetStage)
        stage = targetStage
        animationJob = scope.launch {
            animate(
                initialValue = initialOffset,
                targetValue = targetOffset,
                initialVelocity = initialVelocityPxPerSecond,
                animationSpec = if (design.reduceMotion) tween(0) else if (design.usesOriginalMapChrome || design.variant == NativeDesignVariant.CLAUDE_B) spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ) else tween(design.durationMillis),
            ) { value, _ ->
                offsetPx = currentAnchors.coerce(value)
            }
            offsetPx = targetOffset
        }
    }

    fun dispose() {
        stopAnimation()
    }
}

private val FacilitySheetHandleHeight = 44.dp

/** How much of the previous page a screen reader page leaves on screen: one touch target. */
private val FacilitySheetScreenReaderPageOverlap = 48.dp

/**
 * A screen reader scrolls the content one page at a time to reach what is below it. A page of the
 * whole viewport took the item it was reading off screen, and TalkBack then lost its place and
 * jumped to the map. Leaving the end of the previous page on screen lets it go on to the next item.
 */
private fun Modifier.screenReaderPages(scrollState: ScrollState, overlapPx: Float): Modifier = semantics {
    getScrollViewportLength { maxOf(scrollState.viewportSize - overlapPx, scrollState.viewportSize / 2f) }
}

/** Whether a finger, or a pressed mouse button, is on the sheet content right now. */
private class SheetContentTouch {
    var pressed = false
}

private fun Modifier.trackSheetContentTouch(touch: SheetContentTouch): Modifier = pointerInput(touch) {
    // The initial pass sees the press before the content's own scrolling consumes it.
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        touch.pressed = true
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        } finally {
            touch.pressed = false
        }
    }
}

/**
 * The newly chosen facility whose name takes the focus when its details appear. Choosing a
 * facility replaces the row that held the screen reader's focus, which then landed on whatever
 * the screen reader picked next, such as the end of the image gallery. Moving the input focus to
 * the name moves the screen reader there too. Only a new choice does this, not every expansion
 * of the sheet, so the handle keeps the focus while the sheet changes stage.
 */
internal class FacilityNameFocus {
    var pendingFacilityId by mutableStateOf<String?>(null)
}

private val FacilityLoadState.isActionableError: Boolean
    get() = when (this) {
        FacilityLoadState.NETWORK_ERROR,
        FacilityLoadState.API_ERROR,
        FacilityLoadState.INVALID_RESPONSE,
        -> true

        else -> false
    }

private val FacilitySheetStage.testTagSuffix: String
    get() = when (this) {
        FacilitySheetStage.COLLAPSED -> "collapsed"
        FacilitySheetStage.STAGE_1 -> "stage-1"
        FacilitySheetStage.STAGE_2 -> "stage-2"
    }
