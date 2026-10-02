package io.github.chamsser.gymvi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AddressSearchItem
import io.github.chamsser.gymvi.data.FacilityMapItem

@Composable
internal fun MapSearchLauncher(
    query: String,
    label: String,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayedText = query.ifBlank { label }
    val openLabel = if (query.isBlank()) {
        stringResource(R.string.search_open)
    } else {
        stringResource(R.string.search_edit)
    }
    val design = LocalNativeDesign.current
    Surface(
        modifier = modifier.height(SearchBarHeight),
        shape = SearchBarShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
        border = null,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The icon and the text open the same search, so a screen reader stops on them once.
            // Compose names a role's class only for a node without accessibility children, so the
            // bar clears its children to be read as a button rather than as text.
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .testTag("map-search")
                    .clearAndSetSemantics {
                        text = AnnotatedString(displayedText)
                        role = Role.Button
                        onClick(label = openLabel) {
                            onClick()
                            true
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchEdgeAction(
                    label = null,
                    testTag = "map-search-icon",
                    onClick = onClick,
                ) {
                    Icon(
                        painter = painterResource(
                            if (query.isBlank()) {
                                R.drawable.ic_material_symbol_search_24
                            } else {
                                R.drawable.ic_material_symbol_arrow_back_24
                            },
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SearchGlyphSize),
                    )
                }
                Surface(
                    onClick = onClick,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clearAndSetSemantics { },
                    shape = SearchTextActionShape,
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 4.dp, end = if (query.isBlank()) 20.dp else 4.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = displayedText,
                            color = if (query.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (query.isNotBlank()) {
                SearchEdgeAction(
                    label = stringResource(R.string.search_clear),
                    testTag = "map-search-clear",
                    onClick = onClear,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_close_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun FacilitySearchScreen(
    query: String,
    onQueryChanged: (String) -> Unit,
    facilities: List<FacilityMapItem>,
    addressResults: List<AddressSearchItem>,
    isAddressSearchLoading: Boolean,
    showingRecentResults: Boolean,
    favoriteFacilityIds: Set<String>,
    searchLabel: String,
    onFacilitySelected: (String) -> Unit,
    onAddressSelected: (AddressSearchItem) -> Unit,
    onRecentFacilityRemoved: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val closeSearch = {
        if (query.isBlank()) keyboardController?.hide()
        onBack()
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("facility-search-screen"),
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
                    .height(SearchBarHeight),
                shape = SearchBarShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 5.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchBackButton(onClick = closeSearch)
                    SearchInput(
                        query = query,
                        onQueryChanged = onQueryChanged,
                        searchLabel = searchLabel,
                        focusRequester = focusRequester,
                        onSearch = {
                            keyboardController?.hide()
                            if (query.isNotBlank()) {
                                onSearchSubmitted()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (query.isNotEmpty()) {
                        SearchClearButton(onClick = { onQueryChanged("") })
                    } else {
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }
            }

            if (facilities.isEmpty() && addressResults.isEmpty()) {
                if (showingRecentResults) {
                    SearchSectionHeading(text = stringResource(R.string.facility_recent_title))
                }
                if (isAddressSearchLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                } else {
                    FacilitySearchEmptyState(
                        query = query,
                        showingRecentResults = showingRecentResults,
                    )
                }
            } else {
                val heading = if (showingRecentResults) {
                    stringResource(R.string.facility_recent_title)
                } else {
                    stringResource(
                        R.string.facility_search_result_count,
                        facilities.size + addressResults.size,
                    )
                }
                SearchSectionHeading(text = heading)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(
                        items = addressResults,
                        key = { address -> "address:${address.stableId}" },
                    ) { address ->
                        AddressSearchResultRow(
                            address = address,
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
                    items(
                        items = facilities,
                        key = FacilityMapItem::facilityId,
                    ) { facility ->
                        FacilitySearchResultRow(
                            facility = facility,
                            isFavorite = facility.facilityId in favoriteFacilityIds,
                            showRemoveAction = showingRecentResults,
                            onClick = {
                                keyboardController?.hide()
                                onFacilitySelected(facility.facilityId)
                            },
                            onRemove = { onRecentFacilityRemoved(facility.facilityId) },
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
private fun SearchInput(
    query: String,
    onQueryChanged: (String) -> Unit,
    searchLabel: String,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = query,
                selection = TextRange(query.length),
            ),
        )
    }
    LaunchedEffect(query) {
        if (fieldValue.text != query) {
            fieldValue = TextFieldValue(
                text = query,
                selection = TextRange(query.length),
            )
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { updatedValue ->
            fieldValue = updatedValue
            if (updatedValue.text != query) {
                onQueryChanged(updatedValue.text)
            }
        },
        modifier = modifier
            .fillMaxHeight()
            .focusRequester(focusRequester)
            .testTag("facility-search-input")
            .semantics { contentDescription = searchLabel },
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 4.dp, end = 4.dp),
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
}

@Composable
private fun FacilitySearchEmptyState(
    query: String,
    showingRecentResults: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (showingRecentResults) {
                    stringResource(R.string.facility_search_recent_empty_title)
                } else if (query.isBlank()) {
                    stringResource(R.string.facility_search_prompt_title)
                } else {
                    stringResource(R.string.facility_search_empty_title)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (!showingRecentResults && query.isBlank()) {
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.facility_search_prompt_body),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AddressSearchResultRow(
    address: AddressSearchItem,
    onClick: () -> Unit,
) {
    val resultDescription = listOfNotNull(address.displayLabel, address.secondaryLabel).joinToString(", ")
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("address-search-result"),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 12.dp, top = 7.dp, bottom = 7.dp)
                .semantics { contentDescription = resultDescription },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(34.dp),
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
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
        }
    }
}

@Composable
internal fun FacilitySearchResultRow(
    facility: FacilityMapItem,
    isFavorite: Boolean,
    showRemoveAction: Boolean,
    distanceMeters: Int? = null,
    compact: Boolean = false,
    testTag: String = "facility-search-result",
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 50.dp else 56.dp)
                .padding(
                    start = if (compact) 0.dp else 16.dp,
                    end = if (compact) 0.dp else 8.dp,
                    top = if (compact) 5.dp else 7.dp,
                    bottom = if (compact) 5.dp else 7.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FacilityResultIcon(facility = facility, isFavorite = isFavorite)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = facility.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val metadata = listOfNotNull(
                    facility.facilityTypeName
                        ?: stringResource(R.string.facility_type_unknown),
                    distanceMeters?.let { formatFacilityDistance(it) },
                ).joinToString(" · ")
                Text(
                    text = metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showRemoveAction) {
                val removeLabel = stringResource(
                    R.string.facility_recent_remove,
                    facility.name,
                )
                Surface(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("facility-search-recent-remove-${facility.facilityId}")
                        .semantics {
                            contentDescription = removeLabel
                            role = Role.Button
                        },
                    shape = CircleShape,
                    color = Color.Transparent,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_material_symbol_close_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FacilityResultIcon(
    facility: FacilityMapItem,
    isFavorite: Boolean,
) {
    val category = FacilityCategory.fromFacility(facility)
    Surface(
        modifier = Modifier
            .size(34.dp)
            .testTag(if (isFavorite) "facility-result-favorite-icon" else "facility-result-category-icon"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Icon(
            painter = painterResource(
                if (isFavorite) R.drawable.ic_material_symbol_star_24 else category.iconRes,
            ),
            contentDescription = null,
            tint = if (isFavorite) Color(0xFFF2B705) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp),
        )
    }
}

@Composable
private fun SearchSectionHeading(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 9.dp)
            .semantics { heading() },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SearchBackButton(onClick: () -> Unit) {
    SearchEdgeAction(
        label = stringResource(R.string.search_back),
        testTag = "facility-search-back",
        onClick = onClick,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_material_symbol_arrow_back_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SearchClearButton(onClick: () -> Unit) {
    SearchEdgeAction(
        label = stringResource(R.string.search_clear),
        testTag = "facility-search-clear",
        onClick = onClick,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_material_symbol_close_24),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SearchEdgeAction(
    label: String?,
    testTag: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxHeight()
            .width(SearchLeadingSlotWidth)
            .testTag(testTag)
            .then(
                if (label == null) {
                    Modifier.clearAndSetSemantics { }
                } else {
                    Modifier.semantics {
                        contentDescription = label
                        role = Role.Button
                    }
                },
            ),
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

private val SearchBarHeight = 48.dp
private val SearchLeadingSlotWidth = 48.dp
private val SearchGlyphSize = 22.dp
private val SearchBarShape = RoundedCornerShape(24.dp)
private val SearchTextActionShape = RoundedCornerShape(
    topEnd = 24.dp,
    bottomEnd = 24.dp,
)
