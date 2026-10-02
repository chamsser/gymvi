package io.github.chamsser.gymvi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AddressSearchItem
import io.github.chamsser.gymvi.data.FacilityMapItem
import io.github.chamsser.gymvi.data.RoutePreviewData
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }
}

/** A denial leaves no location to wait for, so the origin goes back to an empty field to choose. */
internal fun shouldReleaseCurrentLocationOrigin(
    usesCurrentLocation: Boolean,
    hasSelectedOrigin: Boolean,
    locationState: CurrentLocationControlState,
): Boolean = usesCurrentLocation && !hasSelectedOrigin && locationState == CurrentLocationControlState.DENIED

internal enum class RouteMapPickTarget { ORIGIN, DESTINATION }

private enum class RouteField { ORIGIN, DESTINATION }

@Composable
internal fun RoutePreviewScreen(
    facilities: List<FacilityMapItem>,
    initialOrigin: FacilityMapItem? = null,
    initialDestination: FacilityMapItem?,
    initialOriginAddress: AddressSearchItem? = null,
    initialDestinationAddress: AddressSearchItem? = null,
    currentLocation: RouteCoordinate?,
    routeState: RoutePreviewState,
    locationState: CurrentLocationControlState,
    addressSearchState: AddressSearchState,
    onAddressSearchRequested: (String) -> Unit,
    onCurrentLocationRequested: () -> Unit,
    onOriginSelected: (FacilityMapItem?) -> Unit,
    onDestinationSelected: (FacilityMapItem?) -> Unit,
    onOriginAddressSelected: (AddressSearchItem?) -> Unit = {},
    onDestinationAddressSelected: (AddressSearchItem?) -> Unit = {},
    onMapSelectionRequested: (RouteMapPickTarget) -> Unit = {},
    onRouteRequested: (RouteCoordinate, RouteCoordinate) -> Unit,
    onRouteCleared: () -> Unit = {},
    onBack: () -> Unit,
    onSheetHeightChanged: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val initialOriginKey = initialOrigin?.facilityId ?: initialOriginAddress?.stableId
    val initialDestinationKey = initialDestination?.facilityId ?: initialDestinationAddress?.stableId
    var originQuery by remember(initialOriginKey) {
        mutableStateOf(
            initialOrigin?.name
                ?: initialOriginAddress?.displayLabel
                ?: if (currentLocation == null) "" else "내 위치",
        )
    }
    var destinationQuery by remember(initialDestinationKey) {
        mutableStateOf(
            initialDestination?.name ?: initialDestinationAddress?.displayLabel.orEmpty(),
        )
    }
    var selectedOrigin by remember(initialOriginKey) {
        mutableStateOf(
            initialOrigin?.let { RouteCoordinate(it.latitude, it.longitude) }
                ?: initialOriginAddress?.let { RouteCoordinate(it.latitude, it.longitude) }
                ?: currentLocation,
        )
    }
    var selectedDestinationCoordinate by remember(initialDestinationKey) {
        mutableStateOf(
            initialDestination?.let { RouteCoordinate(it.latitude, it.longitude) }
                ?: initialDestinationAddress?.let { RouteCoordinate(it.latitude, it.longitude) },
        )
    }
    var usesCurrentLocation by remember(initialOriginKey) {
        mutableStateOf(initialOrigin == null && initialOriginAddress == null && currentLocation != null)
    }
    var activeField by remember { mutableStateOf<RouteField?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val useCurrentLocation: () -> Unit = {
        originQuery = "내 위치"
        selectedOrigin = null
        usesCurrentLocation = true
        onOriginSelected(null)
        onOriginAddressSelected(null)
        onCurrentLocationRequested()
    }

    LaunchedEffect(currentLocation, usesCurrentLocation, selectedOrigin) {
        // A route uses a captured origin, not a live GPS stream. Accept the first fix only.
        if (usesCurrentLocation && selectedOrigin == null && currentLocation != null) {
            selectedOrigin = currentLocation
            originQuery = "내 위치"
        }
    }
    // Read in composition: a new request has already moved the state to REQUESTING there,
    // so the earlier denial cannot clear the origin the user just chose.
    val releaseCurrentLocationOrigin = shouldReleaseCurrentLocationOrigin(
        usesCurrentLocation = usesCurrentLocation,
        hasSelectedOrigin = selectedOrigin != null,
        locationState = locationState,
    )
    LaunchedEffect(releaseCurrentLocationOrigin) {
        if (releaseCurrentLocationOrigin) {
            originQuery = ""
            usesCurrentLocation = false
        }
    }
    LaunchedEffect(initialDestinationKey) {
        if (initialDestinationKey != null && selectedOrigin == null && !usesCurrentLocation) {
            useCurrentLocation()
        }
    }
    LaunchedEffect(selectedOrigin, selectedDestinationCoordinate) {
        val origin = selectedOrigin ?: return@LaunchedEffect
        val destination = selectedDestinationCoordinate ?: return@LaunchedEffect
        onRouteRequested(origin, destination)
    }

    val searchResults = remember(facilities, searchQuery, activeField) {
        if (activeField == null) emptyList() else filterFacilitiesForSearch(facilities, searchQuery, null)
    }
    val addressResults = addressSearchState.resultsFor(searchQuery)
    val isAddressSearchLoading = addressSearchState.isLoadingFor(searchQuery)
    LaunchedEffect(activeField, searchQuery) {
        if (activeField != null) onAddressSearchRequested(searchQuery)
    }
    val openSearch: (RouteField) -> Unit = { field ->
        searchQuery = when (field) {
            RouteField.ORIGIN -> originQuery.takeUnless { usesCurrentLocation }.orEmpty()
            RouteField.DESTINATION -> destinationQuery
        }
        activeField = field
    }
    val closeSearch = { activeField = null }

    BackHandler(enabled = activeField != null) { closeSearch() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("route-preview-screen"),
    ) {
        if (activeField == null) {
            RoutePlanner(
                originQuery = originQuery,
                destinationQuery = destinationQuery,
                usesCurrentLocation = usesCurrentLocation,
                locationState = locationState,
                onBack = onBack,
                onOriginSearchRequested = { openSearch(RouteField.ORIGIN) },
                onDestinationSearchRequested = { openSearch(RouteField.DESTINATION) },
                onOriginCleared = {
                    originQuery = ""
                    selectedOrigin = null
                    usesCurrentLocation = false
                    onOriginSelected(null)
                    onOriginAddressSelected(null)
                    onRouteCleared()
                },
                onDestinationCleared = {
                    destinationQuery = ""
                    selectedDestinationCoordinate = null
                    onDestinationSelected(null)
                    onDestinationAddressSelected(null)
                    onRouteCleared()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
            )
            RouteSummary(
                routeState = routeState,
                origin = selectedOrigin,
                destination = selectedDestinationCoordinate,
                originLabel = originQuery,
                destinationLabel = destinationQuery,
                onHeightChanged = onSheetHeightChanged,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            RouteEndpointSearchScreen(
                field = checkNotNull(activeField),
                query = searchQuery,
                facilities = searchResults,
                addressResults = addressResults,
                isAddressSearchLoading = isAddressSearchLoading,
                currentLocation = currentLocation,
                locationState = locationState,
                onQueryChanged = { searchQuery = it },
                onCurrentLocationRequested = {
                    useCurrentLocation()
                    closeSearch()
                },
                onMapSelectionRequested = {
                    val target = if (activeField == RouteField.ORIGIN) {
                        RouteMapPickTarget.ORIGIN
                    } else {
                        RouteMapPickTarget.DESTINATION
                    }
                    closeSearch()
                    onMapSelectionRequested(target)
                },
                onFacilitySelected = { facility ->
                    when (activeField) {
                        RouteField.ORIGIN -> {
                            originQuery = facility.name
                            selectedOrigin = RouteCoordinate(facility.latitude, facility.longitude)
                            usesCurrentLocation = false
                            onOriginSelected(facility)
                            onOriginAddressSelected(null)
                        }

                        RouteField.DESTINATION -> {
                            destinationQuery = facility.name
                            selectedDestinationCoordinate = RouteCoordinate(
                                facility.latitude,
                                facility.longitude,
                            )
                            if (selectedOrigin == null) {
                                useCurrentLocation()
                            }
                            onDestinationSelected(facility)
                            onDestinationAddressSelected(null)
                        }

                        null -> Unit
                    }
                    closeSearch()
                },
                onAddressSelected = { address ->
                    val coordinate = RouteCoordinate(address.latitude, address.longitude)
                    when (activeField) {
                        RouteField.ORIGIN -> {
                            originQuery = address.displayLabel
                            selectedOrigin = coordinate
                            usesCurrentLocation = false
                            onOriginSelected(null)
                            onOriginAddressSelected(address)
                        }

                        RouteField.DESTINATION -> {
                            destinationQuery = address.displayLabel
                            selectedDestinationCoordinate = coordinate
                            if (selectedOrigin == null) {
                                useCurrentLocation()
                            }
                            onDestinationSelected(null)
                            onDestinationAddressSelected(address)
                        }

                        null -> Unit
                    }
                    closeSearch()
                },
                onBack = closeSearch,
            )
        }
    }
}

@Composable
private fun RoutePlanner(
    originQuery: String,
    destinationQuery: String,
    usesCurrentLocation: Boolean,
    locationState: CurrentLocationControlState,
    onBack: () -> Unit,
    onOriginSearchRequested: () -> Unit,
    onDestinationSearchRequested: () -> Unit,
    onOriginCleared: () -> Unit,
    onDestinationCleared: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plannerTitle = stringResource(R.string.route_find)
    val design = LocalNativeDesign.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { paneTitle = plannerTitle }
            .testTag("route-planner"),
        shape = RoundedCornerShape(if (design.usesOriginalMapChrome) 20.dp else design.style.cornerDp.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (design.usesOriginalMapChrome) 6.dp else if (design.style.surface == NativeSurface.FLOATING) 4.dp else 0.dp,
        border = if (!design.usesOriginalMapChrome) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Row(
            modifier = Modifier.padding(start = 2.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RouteIconButton(
                iconRes = R.drawable.ic_material_symbol_arrow_back_24,
                label = stringResource(R.string.route_back),
                testTag = "route-preview-back",
                onClick = onBack,
            )
            Column(modifier = Modifier.weight(1f)) {
                RouteEndpointField(
                    value = originQuery,
                    placeholder = stringResource(R.string.route_origin_placeholder),
                    marker = RouteEndpointMarker.ORIGIN,
                    testTag = "route-origin-input",
                    fieldLabel = stringResource(R.string.route_origin),
                    onClick = onOriginSearchRequested,
                    onClear = onOriginCleared,
                    showClear = originQuery.isNotBlank(),
                    showProgress = locationState == CurrentLocationControlState.REQUESTING && usesCurrentLocation,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 36.dp, end = 8.dp),
                    color = gymviSubtleBorderColor(),
                )
                RouteEndpointField(
                    value = destinationQuery,
                    placeholder = stringResource(R.string.route_destination_placeholder),
                    marker = RouteEndpointMarker.DESTINATION,
                    testTag = "route-destination-input",
                    fieldLabel = stringResource(R.string.route_destination),
                    onClick = onDestinationSearchRequested,
                    onClear = onDestinationCleared,
                    showClear = destinationQuery.isNotBlank(),
                )
            }
        }
    }
}

private enum class RouteEndpointMarker { ORIGIN, DESTINATION }

@Composable
private fun RouteEndpointField(
    value: String,
    placeholder: String,
    marker: RouteEndpointMarker,
    testTag: String,
    fieldLabel: String,
    onClick: () -> Unit,
    onClear: () -> Unit,
    showClear: Boolean,
    showProgress: Boolean = false,
) {
    val displayedText = value.ifBlank { placeholder }
    val connectorColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "$fieldLabel, $displayedText"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        // A short dotted connector ties the two endpoints into one route.
                        val x = size.width / 2f
                        val dash = 3.dp.toPx()
                        val gap = 4.dp.toPx()
                        val start = if (marker == RouteEndpointMarker.ORIGIN) size.height / 2f + 10.dp.toPx() else 0f
                        val end = if (marker == RouteEndpointMarker.ORIGIN) size.height else size.height / 2f - 12.dp.toPx()
                        var y = start
                        while (y < end) {
                            drawLine(
                                color = connectorColor,
                                start = Offset(x, y),
                                end = Offset(x, minOf(y + dash, end)),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                            y += dash + gap
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    showProgress -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    marker == RouteEndpointMarker.ORIGIN -> Box(
                        modifier = Modifier
                            .size(12.dp)
                            .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    else -> Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_location_on_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = displayedText,
                modifier = Modifier.weight(1f),
                color = if (value.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showClear) {
                RouteIconButton(
                    iconRes = R.drawable.ic_material_symbol_close_24,
                    label = stringResource(R.string.search_clear),
                    testTag = "$testTag-clear",
                    onClick = onClear,
                )
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun RouteIconButton(
    iconRes: Int,
    label: String,
    testTag: String,
    onClick: () -> Unit,
    showProgress: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        shape = CircleShape,
        color = Color.Transparent,
    ) {
        if (showProgress) {
            CircularProgressIndicator(modifier = Modifier.padding(14.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(13.dp),
            )
        }
    }
}

@Composable
private fun RouteEndpointSearchScreen(
    field: RouteField,
    query: String,
    facilities: List<FacilityMapItem>,
    addressResults: List<AddressSearchItem>,
    isAddressSearchLoading: Boolean,
    currentLocation: RouteCoordinate?,
    locationState: CurrentLocationControlState,
    onQueryChanged: (String) -> Unit,
    onCurrentLocationRequested: () -> Unit,
    onMapSelectionRequested: () -> Unit,
    onFacilitySelected: (FacilityMapItem) -> Unit,
    onAddressSelected: (AddressSearchItem) -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember(field) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchLabel = when (field) {
        RouteField.ORIGIN -> stringResource(R.string.route_origin_placeholder)
        RouteField.DESTINATION -> stringResource(R.string.route_destination_placeholder)
    }
    var fieldValue by remember(field) {
        mutableStateOf(TextFieldValue(query, selection = TextRange(query.length)))
    }

    LaunchedEffect(field) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    LaunchedEffect(query) {
        if (fieldValue.text != query) {
            fieldValue = TextFieldValue(query, selection = TextRange(query.length))
        }
    }

    val closeSearch = {
        keyboardController?.hide()
        onBack()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("route-endpoint-search-screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 5.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RouteIconButton(
                        iconRes = R.drawable.ic_material_symbol_arrow_back_24,
                        label = stringResource(R.string.route_search_back),
                        testTag = "route-endpoint-search-back",
                        onClick = closeSearch,
                    )
                    BasicTextField(
                        value = fieldValue,
                        onValueChange = { updatedValue ->
                            fieldValue = updatedValue
                            if (updatedValue.text != query) onQueryChanged(updatedValue.text)
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .testTag("route-endpoint-search-input")
                            .semantics { contentDescription = searchLabel },
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { keyboardController?.hide() },
                        ),
                        decorationBox = { innerTextField ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (fieldValue.text.isEmpty()) {
                                    Text(
                                        text = searchLabel,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    if (query.isNotEmpty()) {
                        RouteIconButton(
                            iconRes = R.drawable.ic_material_symbol_close_24,
                            label = stringResource(R.string.search_clear),
                            testTag = "route-endpoint-search-clear",
                            onClick = { onQueryChanged("") },
                        )
                    } else {
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                if (field == RouteField.ORIGIN) {
                    RouteSearchAction(
                        label = stringResource(R.string.current_location),
                        iconRes = R.drawable.ic_material_symbol_my_location_24,
                        testTag = "route-use-current-location",
                        showProgress = locationState == CurrentLocationControlState.REQUESTING,
                        onClick = {
                            keyboardController?.hide()
                            onCurrentLocationRequested()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                RouteSearchAction(
                    label = stringResource(R.string.route_select_on_map),
                    iconRes = R.drawable.ic_material_symbol_map_24,
                    testTag = "route-select-on-map",
                    onClick = {
                        keyboardController?.hide()
                        onMapSelectionRequested()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = gymviSubtleBorderColor())

            Text(
                text = if (query.isBlank()) {
                    stringResource(R.string.route_nearby_facilities)
                } else {
                    stringResource(
                        R.string.route_search_result_count,
                        facilities.size + addressResults.size,
                    )
                },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )

            if (facilities.isEmpty() && addressResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isAddressSearchLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = stringResource(R.string.facility_search_empty_title),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("route-endpoint-search-results"),
                ) {
                    items(
                        addressResults,
                        key = { address -> "address:${address.stableId}" },
                    ) { address ->
                        RouteAddressResultRow(
                            address = address,
                            currentLocation = currentLocation,
                            onClick = {
                                keyboardController?.hide()
                                onAddressSelected(address)
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = gymviSubtleBorderColor(),
                        )
                    }
                    items(facilities, key = FacilityMapItem::facilityId) { facility ->
                        RouteEndpointResultRow(
                            facility = facility,
                            currentLocation = currentLocation,
                            onClick = {
                                keyboardController?.hide()
                                onFacilitySelected(facility)
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = gymviSubtleBorderColor(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteSearchAction(
    label: String,
    iconRes: Int,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .testTag(testTag),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = label
                    role = Role.Button
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun RouteAddressResultRow(
    address: AddressSearchItem,
    currentLocation: RouteCoordinate?,
    onClick: () -> Unit,
) {
    val distanceText = currentLocation?.let { location ->
        formatRouteDistance(
            straightLineDistanceMeters(
                location,
                RouteCoordinate(address.latitude, address.longitude),
            ).roundToInt(),
        )
    }
    val resultDescription = listOfNotNull(
        address.displayLabel,
        address.secondaryLabel,
        distanceText,
    ).joinToString(", ")
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("route-address-result"),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 7.dp)
                .semantics { contentDescription = resultDescription },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_material_symbol_location_on_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = address.displayLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                address.secondaryLabel?.let { secondary ->
                    Text(
                        text = secondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            distanceText?.let { distance ->
                Text(
                    text = distance,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun RouteEndpointResultRow(
    facility: FacilityMapItem,
    currentLocation: RouteCoordinate?,
    onClick: () -> Unit,
) {
    val category = FacilityCategory.fromFacility(facility)
    val detail = facility.roadAddress
        ?: facility.facilityTypeName
        ?: stringResource(R.string.facility_type_unknown)
    val distance = currentLocation?.let { location ->
        straightLineDistanceMeters(
            location,
            RouteCoordinate(facility.latitude, facility.longitude),
        ).roundToInt()
    }
    val distanceText = distance?.let { formatRouteDistance(it) }
    val resultDescription = listOfNotNull(facility.name, detail, distanceText).joinToString(", ")
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("route-endpoint-result"),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 7.dp)
                .semantics { contentDescription = resultDescription },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Icon(
                    painter = painterResource(category.iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = facility.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = detail,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (distanceText != null) {
                Text(
                    text = distanceText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun RouteSummary(
    routeState: RoutePreviewState,
    origin: RouteCoordinate?,
    destination: RouteCoordinate?,
    originLabel: String,
    destinationLabel: String,
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val design = LocalNativeDesign.current
    // Landscape or a large font can make the sheet taller than the screen; it scrolls instead of
    // pushing the external map actions out of reach, and leaves the planner above it visible.
    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * ROUTE_SHEET_MAX_HEIGHT_FRACTION).dp
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .onSizeChanged { size -> onHeightChanged(with(density) { size.height.toDp() }) }
            .testTag("route-preview-sheet"),
        shape = RoundedCornerShape(topStart = if (design.usesOriginalMapChrome) 24.dp else design.style.cornerDp.dp, topEnd = if (design.usesOriginalMapChrome) 24.dp else design.style.cornerDp.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (design.usesOriginalMapChrome) 8.dp else if (design.style.surface == NativeSurface.FLOATING) 4.dp else 0.dp,
        border = if (!design.usesOriginalMapChrome && design.style.surface == NativeSurface.COMPACT) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 16.dp),
        ) {
            when (routeState) {
                RoutePreviewState.Idle -> Text(
                    text = when {
                        origin == null -> stringResource(R.string.route_choose_origin)
                        destination == null -> stringResource(R.string.route_choose_destination)
                        else -> stringResource(R.string.route_loading)
                    },
                    modifier = Modifier.testTag("route-input-prompt"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                RoutePreviewState.Loading -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.route_loading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                is RoutePreviewState.Ready -> if (origin != null && destination != null) {
                    RouteReadySummary(
                        route = routeState.route,
                        origin = origin,
                        destination = destination,
                        originLabel = originLabel,
                        destinationLabel = destinationLabel,
                    )
                } else {
                    Text(
                        text = stringResource(
                            if (origin == null) {
                                R.string.route_choose_origin
                            } else {
                                R.string.route_choose_destination
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                is RoutePreviewState.Error -> {
                    Text(
                        text = routeErrorMessage(routeState.code),
                        modifier = Modifier.testTag("route-error"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The confirmed destination leads, followed by the origin, the three route facts in one row, and
 * one primary hand-off to a navigation app. Durations and distance come from the server route.
 */
@Composable
private fun RouteReadySummary(
    route: RoutePreviewData,
    origin: RouteCoordinate,
    destination: RouteCoordinate,
    originLabel: String,
    destinationLabel: String,
) {
    val destinationName = destinationLabel.ifBlank { "목적지" }
    val originName = originLabel.ifBlank { "출발지" }
    Text(
        text = stringResource(R.string.route_preview_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = destinationName,
        modifier = Modifier
            .testTag("route-destination-title")
            .semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Row(
        // A screen reader stops once on the row and reads 출발 with the origin.
        modifier = Modifier.testTag("route-origin-summary").semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.route_origin),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = originName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteFact(
            iconRes = R.drawable.ic_material_symbol_directions_walk_24,
            label = stringResource(R.string.route_walking),
            value = stringResource(
                R.string.route_estimated_duration,
                formatRouteDuration(estimatedWalkingDurationSeconds(route.distanceMeters)),
            ),
            testTag = "route-walking-duration",
            modifier = Modifier.weight(1f),
        )
        RouteFactDivider()
        RouteFact(
            iconRes = R.drawable.ic_material_symbol_directions_car_24,
            label = stringResource(R.string.route_driving),
            value = formatRouteDuration(route.durationSeconds),
            testTag = "route-driving-duration",
            modifier = Modifier.weight(1f),
        )
        RouteFactDivider()
        RouteFact(
            iconRes = R.drawable.ic_material_symbol_route_arrow_24,
            label = stringResource(R.string.route_distance_label),
            value = formatRouteDistance(route.distanceMeters),
            testTag = "route-distance",
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(modifier = Modifier.height(18.dp))
    Text(
        text = stringResource(R.string.route_open_external_title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    ExternalMapButtons(
        origin = ExternalRouteTarget(origin, originName),
        destination = ExternalRouteTarget(destination, destinationName),
    )
}

@Composable
private fun ExternalMapButtons(
    origin: ExternalRouteTarget,
    destination: ExternalRouteTarget,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val open: (ExternalMapProvider) -> Unit = { provider ->
        openExternalRoute(context, provider, origin, destination)
    }
    Button(
        onClick = { open(ExternalMapProvider.NAVER) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("route-open-naver"),
        shape = RoundedCornerShape(14.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_material_symbol_directions_24),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.route_open_naver_map),
            style = MaterialTheme.typography.titleSmall.merge(KoreanPhraseBreak),
            fontWeight = FontWeight.SemiBold,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    val alternatives = listOf(
        ExternalMapProvider.GOOGLE to stringResource(R.string.route_open_google_map),
        ExternalMapProvider.KAKAO to stringResource(R.string.route_open_kakao_map),
    )
    val labelStyle = MaterialTheme.typography.labelLarge.merge(
        TextStyle(fontWeight = FontWeight.SemiBold),
    ).merge(KoreanPhraseBreak)
    val alternativeButton: @Composable (ExternalMapProvider, String, Modifier) -> Unit = { provider, label, buttonModifier ->
        OutlinedButton(
            onClick = { open(provider) },
            modifier = buttonModifier
                .heightIn(min = 48.dp)
                .testTag("route-open-${provider.name.lowercase()}"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, gymviSubtleBorderColor()),
            contentPadding = PaddingValues(horizontal = ExternalMapButtonHorizontalPadding, vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (externalMapButtonsStack(alternatives.map { it.second }, labelStyle, maxWidth)) {
            Column(verticalArrangement = Arrangement.spacedBy(ExternalMapButtonGap)) {
                alternatives.forEach { (provider, label) ->
                    alternativeButton(provider, label, Modifier.fillMaxWidth())
                }
            }
        } else {
            // The two buttons share the taller one's height when large text wraps either label.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(ExternalMapButtonGap),
            ) {
                alternatives.forEach { (provider, label) ->
                    alternativeButton(provider, label, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/**
 * Whether the Google and Kakao labels would break inside a word, or wrap past two lines, in half
 * of [rowWidth]. Large text does this, and the two buttons then take a full row each.
 */
@Composable
private fun externalMapButtonsStack(labels: List<String>, style: TextStyle, rowWidth: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, density, labels, style, rowWidth) {
        with(density) {
            val textWidth = (((rowWidth - ExternalMapButtonGap) / 2).roundToPx() -
                2 * ExternalMapButtonHorizontalPadding.roundToPx()).coerceAtLeast(0)
            measurer.widestWord(labels, style) > textWidth || labels.any { label ->
                measurer.measure(label, style, constraints = Constraints(maxWidth = textWidth)).lineCount > 2
            }
        }
    }
}

@Composable
private fun RouteFact(
    iconRes: Int,
    label: String,
    value: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    // Large text wraps the label and the value between words instead of cutting them off. The facts
    // share the bottom edge, so a label that wraps does not push its value below the others.
    Column(
        modifier = modifier
            .fillMaxHeight()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.merge(KoreanPhraseBreak),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            modifier = Modifier.testTag(testTag),
            style = MaterialTheme.typography.titleLarge.merge(KoreanPhraseBreak),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RouteFactDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .padding(vertical = 6.dp)
            .background(gymviSubtleBorderColor()),
    )
}

@Composable
private fun routeErrorMessage(code: String): String = when (code) {
    "PROVIDER_BUDGET_EXHAUSTED" -> stringResource(R.string.route_budget_exhausted)
    "VALIDATION_ROUTE_TOO_FAR" -> stringResource(R.string.route_too_far)
    "NETWORK_UNAVAILABLE" -> stringResource(R.string.route_network_error)
    else -> stringResource(R.string.route_unavailable)
}

@Composable
private fun formatRouteDistance(distanceMeters: Int): String = if (distanceMeters < 1_000) {
    stringResource(R.string.route_distance_meters, distanceMeters)
} else {
    stringResource(R.string.route_distance_kilometers, distanceMeters / 1_000.0)
}

@Composable
private fun formatRouteDuration(durationSeconds: Int): String {
    val minutes = (durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
    return if (minutes < 60) {
        stringResource(R.string.route_duration_minutes, minutes)
    } else {
        stringResource(R.string.route_duration_hours_minutes, minutes / 60, minutes % 60)
    }
}

internal fun estimatedWalkingDurationSeconds(distanceMeters: Int): Int {
    require(distanceMeters >= 0)
    return ceil(distanceMeters / ESTIMATED_WALKING_METERS_PER_SECOND)
        .toInt()
        .coerceAtLeast(1)
}

internal fun straightLineDistanceMeters(
    origin: RouteCoordinate,
    destination: RouteCoordinate,
): Double {
    val latitudeDelta = Math.toRadians(destination.latitude - origin.latitude)
    val longitudeDelta = Math.toRadians(destination.longitude - origin.longitude)
    val originLatitude = Math.toRadians(origin.latitude)
    val destinationLatitude = Math.toRadians(destination.latitude)
    val haversine = sin(latitudeDelta / 2).let { it * it } +
        cos(originLatitude) * cos(destinationLatitude) *
        sin(longitudeDelta / 2).let { it * it }
    return EARTH_RADIUS_METERS * 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
}

internal val RoutePreviewMapLogoClearance = 190.dp

/** Space between the measured route sheet and the map content it must not cover. */
internal val RoutePreviewMapSheetGap = 8.dp

private val ExternalMapButtonGap = 8.dp
private val ExternalMapButtonHorizontalPadding = 10.dp

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val ESTIMATED_WALKING_METERS_PER_SECOND = 1.25

private const val ROUTE_SHEET_MAX_HEIGHT_FRACTION = 0.6f
