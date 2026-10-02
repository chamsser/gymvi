package io.github.chamsser.gymvi.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.naver.maps.map.app.LegalNoticeActivity
import io.github.chamsser.gymvi.BuildConfig
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AiLocalProfile
import io.github.chamsser.gymvi.data.AiPreferences
import io.github.chamsser.gymvi.data.PublicDataSource
import io.github.chamsser.gymvi.data.RecentFacility

/**
 * 내 정보, laid out like a settings screen: white rows
 * grouped on a grey page, an outline icon leading each row, the current value under its title and
 * blue section labels. Every row shows real state, and the AI settings live here
 * instead of behind a button on the AI page. With personalization actions provided, the account
 * card opens the profile and a row under AI 메모리 opens the saved memories
 * (CombinedMyPersonalization.kt). A row under 개인정보 및 권한 opens what the app sends where
 * (CombinedMyPrivacy.kt), 최근 본 시설 under 즐겨찾기 opens that list (CombinedMyRecent.kt),
 * 공공데이터 출처 opens where the server's active data came from (CombinedMySources.kt), and 오픈소스
 * 라이선스 opens what the app ships under which license (CombinedMyLicenses.kt).
 */
@Composable
internal fun CombinedMyModeScreen(
    content: LiveUiMyContent?,
    preferences: AiPreferences?,
    onPreferencesChanged: (AiPreferences) -> Unit,
    favoriteFacilityCount: Int?,
    onOpenFavorites: (() -> Unit)?,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    publicDataSources: List<PublicDataSource> = emptyList(),
    recentFacilities: List<RecentFacility>? = null,
    onRecentFacilityRemoved: (String) -> Unit = {},
    onRecentFacilitiesCleared: () -> Unit = {},
) {
    val palette = combinedPalette()
    val context = LocalContext.current
    // Read again on every resume, so coming back from the system settings shows the new choice.
    var locationAccess by remember { mutableStateOf(currentLocationAccess(context)) }
    LifecycleResumeEffect(context) {
        locationAccess = currentLocationAccess(context)
        onPauseOrDispose { }
    }
    val personalization = LocalAiPersonalizationActions.current
    var page by rememberSaveable { mutableStateOf(CombinedMyPage.MAIN) }
    // Kept out here so coming back from a page it opens finds the list where it was.
    val scroll = rememberScrollState()
    if (personalization != null && page == CombinedMyPage.PROFILE) {
        CombinedMyProfilePage(
            actions = personalization,
            palette = palette,
            onBack = { page = CombinedMyPage.MAIN },
            modifier = modifier,
        )
        return
    }
    if (personalization != null && page == CombinedMyPage.MEMORY) {
        CombinedMyMemoryPage(
            actions = personalization,
            memoryEnabled = preferences?.useAiMemory == true,
            palette = palette,
            onBack = { page = CombinedMyPage.MAIN },
            modifier = modifier,
        )
        return
    }
    if (page == CombinedMyPage.PRIVACY) {
        CombinedMyPrivacyPage(
            palette = palette,
            onBack = { page = CombinedMyPage.MAIN },
            modifier = modifier,
        )
        return
    }
    if (page == CombinedMyPage.RECENT) {
        if (!recentFacilities.isNullOrEmpty()) {
            CombinedMyRecentPage(
                recentFacilities = recentFacilities,
                palette = palette,
                onRemove = onRecentFacilityRemoved,
                onClear = onRecentFacilitiesCleared,
                onBack = { page = CombinedMyPage.MAIN },
                modifier = modifier,
            )
            return
        }
        // Removing the last one or clearing the list leaves nothing there, so the list below shows.
        LaunchedEffect(Unit) { page = CombinedMyPage.MAIN }
    }
    if (page == CombinedMyPage.SOURCES) {
        CombinedMySourcesPage(
            sources = publicDataSources,
            palette = palette,
            onBack = { page = CombinedMyPage.MAIN },
            modifier = modifier,
        )
        return
    }
    if (page == CombinedMyPage.LICENSES) {
        CombinedMyLicensesPage(
            palette = palette,
            onBack = { page = CombinedMyPage.MAIN },
            modifier = modifier,
        )
        return
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.groupedPage)
            .testTag("my-mode-screen"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = CombinedHeaderHeight + 4.dp, bottom = 32.dp),
        ) {
            AiStorageNotice(Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            CombinedMyAccount(
                palette = palette,
                profile = personalization?.profile,
                onOpenProfile = personalization?.let { { page = CombinedMyPage.PROFILE } },
            )
            val favoritesRow: (@Composable (Modifier) -> Unit)? = favoriteFacilityCount?.let { count ->
                { rowModifier ->
                    CombinedMyRow(
                        iconRes = R.drawable.ic_material_symbol_star_outline_24,
                        title = stringResource(R.string.facility_favorites_title),
                        value = if (count > 0) {
                            stringResource(R.string.native_my_favorites_count, count)
                        } else {
                            stringResource(R.string.native_my_favorites_empty)
                        },
                        modifier = rowModifier,
                        testTag = "my-favorites-row",
                        onClick = onOpenFavorites,
                    )
                }
            }
            val recentRow: (@Composable (Modifier) -> Unit)? = recentFacilities?.let { recent ->
                { rowModifier ->
                    // An empty list has nothing to open.
                    CombinedMyRow(
                        iconRes = R.drawable.ic_material_symbol_history_24,
                        title = stringResource(R.string.facility_recent_title),
                        value = if (recent.isNotEmpty()) {
                            stringResource(R.string.my_recent_facilities_count, recent.size)
                        } else {
                            stringResource(R.string.my_recent_facilities_none)
                        },
                        modifier = rowModifier,
                        testTag = "my-recent-row",
                        onClick = { page = CombinedMyPage.RECENT }.takeIf { recent.isNotEmpty() },
                    )
                }
            }
            listOfNotNull(favoritesRow, recentRow).takeIf { it.isNotEmpty() }?.let { rows ->
                CombinedMyGroup(label = null, palette = palette, rows = rows)
            }
            preferences?.let { current ->
                // Four independent settings: each row saves its own value and says what it controls.
                val memoryRow: @Composable (Modifier) -> Unit = { rowModifier ->
                    CombinedMyToggleRow(
                        iconRes = R.drawable.ic_material_symbol_history_24,
                        title = stringResource(R.string.ai_settings_use_memory),
                        description = stringResource(R.string.my_setting_memory_description),
                        checked = current.useAiMemory,
                        onCheckedChange = { onPreferencesChanged(current.copy(useAiMemory = it)) },
                        testTag = "my-setting-memory",
                        modifier = rowModifier,
                    )
                }
                val memoryManageRow: (@Composable (Modifier) -> Unit)? = personalization?.let { actions ->
                    { rowModifier ->
                        // Saved memories stay viewable and deletable while AI 메모리 is off.
                        CombinedMyRow(
                            iconRes = null,
                            title = stringResource(R.string.my_memory_manage),
                            value = if (actions.memories.isEmpty()) {
                                stringResource(R.string.my_memory_none)
                            } else {
                                stringResource(R.string.my_memory_count, actions.memories.size)
                            },
                            modifier = rowModifier,
                            testTag = "my-memory-manage",
                            onClick = { page = CombinedMyPage.MEMORY },
                        )
                    }
                }
                val bodyInformationRow: @Composable (Modifier) -> Unit = { rowModifier ->
                    CombinedMyToggleRow(
                        iconRes = R.drawable.ic_material_symbol_person_24,
                        title = stringResource(R.string.ai_settings_use_body_information),
                        description = stringResource(R.string.my_setting_body_description),
                        checked = current.useBodyInformation,
                        onCheckedChange = { onPreferencesChanged(current.copy(useBodyInformation = it)) },
                        testTag = "my-setting-body-information",
                        modifier = rowModifier,
                    )
                }
                val regionRow: @Composable (Modifier) -> Unit = { rowModifier ->
                    CombinedMyToggleRow(
                        iconRes = R.drawable.ic_material_symbol_location_on_24,
                        title = stringResource(R.string.ai_settings_use_region),
                        description = stringResource(R.string.my_setting_region_description),
                        checked = current.useApproximateRegion,
                        onCheckedChange = { onPreferencesChanged(current.copy(useApproximateRegion = it)) },
                        testTag = "my-setting-approximate-region",
                        modifier = rowModifier,
                    )
                }
                val historyRow: @Composable (Modifier) -> Unit = { rowModifier ->
                    CombinedMyToggleRow(
                        iconRes = R.drawable.ic_material_symbol_chat_24,
                        title = stringResource(R.string.ai_settings_save_history),
                        // Off stops saving from then on; what is already saved stays, closed until saving is back on.
                        description = stringResource(R.string.my_setting_history_description),
                        checked = current.saveConversationHistory,
                        onCheckedChange = { onPreferencesChanged(current.copy(saveConversationHistory = it)) },
                        testTag = "my-setting-save-history",
                        modifier = rowModifier,
                    )
                }
                CombinedMyGroup(
                    label = stringResource(R.string.ai_settings_personalization_section),
                    palette = palette,
                    rows = listOfNotNull(memoryRow, memoryManageRow, bodyInformationRow, regionRow),
                )
                CombinedMyGroup(
                    label = stringResource(R.string.my_history_section),
                    palette = palette,
                    rows = listOf(historyRow),
                )
            }
            CombinedMyGroup(
                label = stringResource(R.string.my_privacy_section),
                palette = palette,
                rows = listOf(
                    { rowModifier ->
                        CombinedMyRow(
                            iconRes = R.drawable.ic_material_symbol_my_location_24,
                            title = stringResource(R.string.native_my_location_title),
                            value = stringResource(locationAccess.labelRes()),
                            modifier = rowModifier,
                            testTag = "my-location-state",
                            onClick = { openAppDetailsSettings(context) },
                        )
                    },
                    { rowModifier ->
                        CombinedMyRow(
                            iconRes = R.drawable.ic_material_symbol_privacy_tip_24,
                            title = stringResource(R.string.my_privacy_notice),
                            modifier = rowModifier,
                            testTag = "my-privacy-notice",
                            onClick = { page = CombinedMyPage.PRIVACY },
                        )
                    },
                ),
            )
            CombinedMyGroup(
                label = stringResource(R.string.my_service_section),
                palette = palette,
                rows = listOf(
                    { rowModifier ->
                        // Opens even with nothing confirmed, where each fact then reads 알 수 없음.
                        CombinedMyRow(
                            iconRes = R.drawable.ic_material_symbol_description_24,
                            title = stringResource(R.string.my_public_data_source),
                            modifier = rowModifier,
                            testTag = "my-public-data-source",
                            onClick = { page = CombinedMyPage.SOURCES },
                        )
                    },
                    { rowModifier ->
                        CombinedMyRow(
                            iconRes = R.drawable.ic_material_symbol_chat_24,
                            title = stringResource(R.string.my_ai_provider),
                            value = stringResource(R.string.my_ai_provider_name),
                            modifier = rowModifier,
                            testTag = "my-ai-provider",
                        )
                    },
                    { rowModifier ->
                        CombinedMyRow(
                            iconRes = R.drawable.ic_material_symbol_description_24,
                            title = stringResource(R.string.my_open_source_licenses),
                            modifier = rowModifier,
                            testTag = "my-open-source-licenses",
                            onClick = { page = CombinedMyPage.LICENSES },
                        )
                    },
                    { rowModifier ->
                        // The map SDK's own notice page, the same one the AI map preview opens.
                        CombinedMyRow(
                            iconRes = R.drawable.ic_material_symbol_map_24,
                            title = stringResource(R.string.my_map_legal_notice),
                            modifier = rowModifier,
                            testTag = "my-map-legal-notice",
                            onClick = { context.startActivity(Intent(context, LegalNoticeActivity::class.java)) },
                        )
                    },
                    { rowModifier ->
                        CombinedMyRow(
                            iconRes = R.drawable.ic_material_symbol_info_24,
                            title = stringResource(R.string.native_my_app_version),
                            value = BuildConfig.VERSION_NAME,
                            modifier = rowModifier,
                            testTag = "my-app-version",
                        )
                    },
                ),
            )
        }
        CombinedMyHeader(
            title = content?.title ?: stringResource(R.string.my_screen_title),
            palette = palette,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * The pages of 내 정보: the settings list, and the profile, memories, privacy notice, recent list,
 * data sources and open source licenses it opens.
 */
private enum class CombinedMyPage { MAIN, PROFILE, MEMORY, PRIVACY, RECENT, SOURCES, LICENSES }

@Composable
internal fun CombinedMyHeader(
    title: String,
    palette: CombinedPalette,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    backTestTag: String = "my-back-button",
    titleTestTag: String = "my-screen-title",
) {
    Box(
        modifier = modifier
            // The header floats over a page that also starts at the top of the screen, and a
            // screen reader read the whole page before reaching the title. It reads the header first.
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            }
            .fillMaxWidth()
            .combinedHeaderFade(palette.groupedPage)
            .statusBarsPadding()
            .height(CombinedHeaderHeight)
            .padding(horizontal = 12.dp),
    ) {
        onBack?.let { back ->
            CombinedRoundButton(
                iconRes = R.drawable.ic_material_symbol_arrow_back_24,
                description = stringResource(R.string.native_my_back),
                testTag = backTestTag,
                palette = palette,
                onClick = back,
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        Text(
            text = title,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 60.dp)
                .semantics { heading() }
                .testTag(titleTestTag),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 신체 정보 on top of 내 정보: the saved items, when there are any, show under the title, and with
 * [onOpenProfile] the card opens them for editing.
 */
@Composable
private fun CombinedMyAccount(
    palette: CombinedPalette,
    profile: AiLocalProfile?,
    onOpenProfile: (() -> Unit)?,
) {
    val summary = profile?.let { aiProfileSummary(it) }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CombinedMyOuterCorner))
            .background(palette.groupedRow)
            .then(
                if (onOpenProfile != null) {
                    Modifier.clickable(
                        onClickLabel = stringResource(R.string.my_profile_edit),
                        role = Role.Button,
                        onClick = onOpenProfile,
                    )
                } else {
                    Modifier.semantics(mergeDescendants = true) {}
                },
            )
            .testTag("my-account")
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(palette.groupedPage, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_material_symbol_person_24),
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.my_profile_title),
                style = MaterialTheme.typography.bodyLarge.merge(KoreanPhraseBreak),
                fontWeight = FontWeight.SemiBold,
            )
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    modifier = Modifier.padding(top = 2.dp).testTag("my-profile-summary"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = combinedMyDescriptionStyle(),
                )
            }
        }
        if (onOpenProfile != null) CombinedMyChevron()
    }
}

internal val CombinedMyOuterCorner = 24.dp
internal val CombinedMyInnerCorner = 4.dp

/** The refined descriptions break at Korean phrases. */
internal val CombinedMyDescriptionBreak = KoreanPhraseBreak

/**
 * Rows of one group as separate white tiles two points apart: the group's outer corners are
 * round and the corners between rows nearly square, as in the reference settings screens.
 */
@Composable
internal fun CombinedMyGroup(
    label: String?,
    palette: CombinedPalette,
    rows: List<@Composable (Modifier) -> Unit>,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = if (label != null) 24.dp else 12.dp)) {
        label?.let { CombinedMyGroupLabel(it) }
        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(modifier = Modifier.height(2.dp))
            row(Modifier.clip(combinedMyTileShape(index, rows.size)).background(palette.groupedRow))
        }
    }
}

/** A group's blue label, read as a heading. */
@Composable
internal fun CombinedMyGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 16.dp, bottom = 8.dp).semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

/** Tile [index] of a group of [count]: round at the group's outer corners, nearly square between rows. */
internal fun combinedMyTileShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) CombinedMyOuterCorner else CombinedMyInnerCorner
    val bottom = if (index == count - 1) CombinedMyOuterCorner else CombinedMyInnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * A row with its value under the title. Without an icon it either starts at the tile's edge or,
 * with [alignWithIcons], lines its title up under the titles of the icon rows around it.
 */
@Composable
internal fun CombinedMyRow(
    @DrawableRes iconRes: Int?,
    title: String,
    modifier: Modifier,
    value: String? = null,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    alignWithIcons: Boolean = iconRes == null,
    valueMaxLines: Int = Int.MAX_VALUE,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            // An information row is read as one item: its title together with its value.
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick)
                } else {
                    Modifier.semantics(mergeDescendants = true) {}
                },
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            iconRes != null -> CombinedMyIcon(iconRes)
            alignWithIcons -> Spacer(modifier = Modifier.width(CombinedMyIconSpace))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            value?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = combinedMyDescriptionStyle(),
                    maxLines = valueMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) CombinedMyChevron()
    }
}

/** The arrow closing a row or card that opens another page. */
@Composable
internal fun CombinedMyChevron() {
    Icon(
        painter = painterResource(R.drawable.ic_material_symbol_arrow_back_24),
        contentDescription = null,
        modifier = Modifier
            .padding(start = 8.dp)
            .size(20.dp)
            .rotate(180f),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Secondary text under a title, broken at Korean phrases. */
@Composable
internal fun combinedMyDescriptionStyle(): TextStyle = MaterialTheme.typography.bodyMedium.merge(CombinedMyDescriptionBreak)

@Composable
internal fun CombinedMyToggleRow(
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    modifier: Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(testTag)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CombinedMyIcon(iconRes)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                modifier = Modifier.padding(top = 2.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = combinedMyDescriptionStyle(),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.testTag("$testTag-switch"))
    }
}

/** The width an icon takes at the start of a row, including the space before the title. */
private val CombinedMyIconSpace = 38.dp

@Composable
private fun CombinedMyIcon(@DrawableRes iconRes: Int) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier
            .padding(end = 16.dp)
            .size(22.dp),
        tint = MaterialTheme.colorScheme.onSurface,
    )
}

/** What the system lets the app read now. From Android 12 a person can allow only an approximate location. */
internal enum class LocationAccess { PRECISE, APPROXIMATE, NONE }

internal fun locationAccess(fineGranted: Boolean, coarseGranted: Boolean): LocationAccess = when {
    fineGranted -> LocationAccess.PRECISE
    coarseGranted -> LocationAccess.APPROXIMATE
    else -> LocationAccess.NONE
}

@StringRes
internal fun LocationAccess.labelRes(): Int = when (this) {
    LocationAccess.PRECISE -> R.string.native_my_location_precise
    LocationAccess.APPROXIMATE -> R.string.native_my_location_approximate
    LocationAccess.NONE -> R.string.native_my_location_not_allowed
}

private fun currentLocationAccess(context: Context): LocationAccess = locationAccess(
    fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED,
    coarseGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED,
)

/** This app's page in the system settings, where its location permission is changed. */
private fun openAppDetailsSettings(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    } catch (_: ActivityNotFoundException) {
        // Nothing opens, and the row still shows the current permission.
    }
}