package io.github.chamsser.gymvi.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.ExerciseContentItem
import io.github.chamsser.gymvi.data.FacilityMapItem

/** Separates list rows with a finer line than the Material default. */
@Composable
internal fun NativeHairline(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = gymviSubtleBorderColor(),
    )
}

/** Asks the AI about the selected facility with an outlined button. */
@Composable
internal fun NativeFacilityAiAction(label: String, onClick: () -> Unit) {
    val outlined = true
    val cornerDp = 12
    Spacer(Modifier.height(12.dp))
    Surface(
        onClick = onClick,
        // At least 48dp, and taller when large text wraps the label onto more lines.
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("facility-ai-action"),
        shape = RoundedCornerShape(cornerDp.dp),
        color = if (outlined) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        border = if (outlined) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_material_symbol_chat_24), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelLarge.merge(KoreanPhraseBreak))
        }
    }
}

internal enum class FacilityInformationKind(val tag: String) {
    CLASSIFICATION("classification"),
    HOURS("hours"),
    AREA("area"),
    PHONE("phone"),
    DESCRIPTION("description"),
}

private data class FacilityInformationItem(
    val kind: FacilityInformationKind,
    val label: String,
    val value: String,
    val caption: String? = null,
)

/**
 * Rows kept open, at most three in their usual order: what people check before
 * going (hours and phone) first, then the next known rows. The rest stay in the same block behind the toggle.
 */
internal fun combinedFacilityInformationKeyKinds(kinds: List<FacilityInformationKind>): List<FacilityInformationKind> {
    val beforeGoing = kinds.filter { it == FacilityInformationKind.HOURS || it == FacilityInformationKind.PHONE }
    val open = (beforeGoing + kinds).distinct().take(CombinedInformationRowsOpen).toSet()
    return kinds.filter { it in open }
}

private const val CombinedInformationRowsOpen = 3

/** Every candidate's facility information; the original layout stays in GymviApp. */
@Composable
internal fun NativeFacilityInformationSection(facility: FacilityMapItem) {
    val items = facilityInformationItems(facility)
    if (items.isEmpty()) return
    val design = LocalNativeDesign.current
    Spacer(Modifier.height(20.dp))
    Column(Modifier.fillMaxWidth().testTag("facility-information-section")) {
        Text(
            text = stringResource(R.string.facility_information_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        CombinedFacilityInformation(facility.facilityId, items, design.reduceMotion)
    }
}

@Composable
private fun facilityInformationItems(facility: FacilityMapItem): List<FacilityInformationItem> {
    val classification = listOfNotNull(facility.facilityClassName, facility.facilityTypeName).distinct().joinToString("\n")
    return buildList {
        classification.takeIf(String::isNotBlank)?.let { value ->
            add(FacilityInformationItem(FacilityInformationKind.CLASSIFICATION, stringResource(R.string.facility_information_classification), value))
        }
        facility.operatingHours?.let { hours ->
            add(
                FacilityInformationItem(
                    kind = FacilityInformationKind.HOURS,
                    label = stringResource(R.string.facility_information_hours),
                    value = formatOperatingHours(
                        hours = hours,
                        closedDaysText = hours.closedDays.takeIf(List<String>::isNotEmpty)?.let { days ->
                            stringResource(R.string.facility_information_closed_days, days.joinToString(", "))
                        },
                    ),
                    // Routine surfaces name the source only; the checked time stays in the data.
                    caption = hours.sourceName,
                ),
            )
        }
        facility.grossFloorAreaSquareMeters?.let { area ->
            add(FacilityInformationItem(FacilityInformationKind.AREA, stringResource(R.string.facility_information_area), formatFacilityArea(area)))
        }
        facility.phoneNumber?.let { phone ->
            add(FacilityInformationItem(FacilityInformationKind.PHONE, stringResource(R.string.facility_information_phone), phone))
        }
        facility.description?.trim()?.takeIf(String::isNotEmpty)?.let { description ->
            add(FacilityInformationItem(FacilityInformationKind.DESCRIPTION, stringResource(R.string.facility_information_description), description))
        }
    }
}

/** One tonal block: key rows first, the rest appended below them, and the toggle inside it. */
@Composable
private fun CombinedFacilityInformation(facilityId: String, items: List<FacilityInformationItem>, reduceMotion: Boolean) {
    val keyKinds = combinedFacilityInformationKeyKinds(items.map { it.kind })
    val keyItems = items.filter { it.kind in keyKinds }
    val moreItems = items.filterNot { it.kind in keyKinds }
    var expanded by rememberSaveable(facilityId) { mutableStateOf(false) }
    val shown = if (expanded) keyItems + moreItems else keyItems
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(if (reduceMotion) snap() else spring())
            .testTag("facility-information-block"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        val stackLabels = facilityInformationLabelsStack(items, roomy = true)
        Column {
            shown.forEachIndexed { index, item ->
                if (index > 0) NativeHairline(Modifier.padding(horizontal = 16.dp))
                FacilityInformationLine(item, stackLabels, horizontalPadding = 16.dp, verticalPadding = 12.dp, roomy = true)
            }
            if (moreItems.isNotEmpty()) {
                NativeHairline(Modifier.padding(horizontal = 16.dp))
                FacilityInformationToggle(
                    label = if (expanded) {
                        stringResource(R.string.native_facility_information_less)
                    } else {
                        stringResource(R.string.native_facility_information_more, moreItems.size)
                    },
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                )
            }
        }
    }
}

@Composable
private fun FacilityInformationToggle(label: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 16.dp)
            .testTag("facility-information-toggle"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            painter = painterResource(R.drawable.ic_material_symbol_expand_more_24),
            contentDescription = null,
            modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun facilityInformationLabelWidth(roomy: Boolean): Dp = if (roomy) 76.dp else 70.dp

@Composable
private fun facilityInformationLabelStyle(): TextStyle =
    MaterialTheme.typography.labelMedium.merge(TextStyle(fontWeight = FontWeight.SemiBold)).merge(KoreanPhraseBreak)

/**
 * Whether some label has a word wider than the label column, which large text does to 전화번호 and
 * 운영시간. Every label of the block then sits above its value, so the rows stay alike.
 */
@Composable
private fun facilityInformationLabelsStack(items: List<FacilityInformationItem>, roomy: Boolean): Boolean {
    val measurer = rememberTextMeasurer()
    val style = facilityInformationLabelStyle()
    val labelWidthPx = with(LocalDensity.current) { facilityInformationLabelWidth(roomy).roundToPx() }
    val labels = items.map { it.label }
    return remember(measurer, style, labels, labelWidthPx) { measurer.widestWord(labels, style) > labelWidthPx }
}

@Composable
private fun FacilityInformationLine(
    item: FacilityInformationItem,
    stackLabel: Boolean,
    horizontalPadding: Dp = 0.dp,
    verticalPadding: Dp = 8.dp,
    roomy: Boolean = false,
) {
    val label: @Composable (Modifier) -> Unit = { labelModifier ->
        Text(
            text = item.label,
            modifier = labelModifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = facilityInformationLabelStyle(),
        )
    }
    val value: @Composable (Modifier) -> Unit = { valueModifier ->
        val valueStyle = if (roomy) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall
        Column(valueModifier) {
            Text(
                text = item.value,
                color = MaterialTheme.colorScheme.onSurface,
                // Short values wrap only under large text, where a split word reads worst. The free
                // description keeps the breaking it has at every text size.
                style = if (item.kind == FacilityInformationKind.DESCRIPTION) valueStyle else valueStyle.merge(KoreanPhraseBreak),
            )
            item.caption?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall.merge(KoreanPhraseBreak))
            }
        }
    }
    val lineModifier = Modifier
        .fillMaxWidth()
        // A screen reader stops once on the row and reads the label with its value.
        .semantics(mergeDescendants = true) {}
        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
        .testTag("facility-information-${item.kind.tag}")
    if (stackLabel) {
        Column(lineModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            label(Modifier)
            value(Modifier.fillMaxWidth())
        }
    } else {
        Row(lineModifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            label(Modifier.width(facilityInformationLabelWidth(roomy)))
            value(Modifier.weight(1f))
        }
    }
}

private const val ExerciseGuideRowsShown = 3

/**
 * The combined exercise guide: one large video card leads to the real video, and the rest are
 * short rows. Inside an AI answer there is no large card, so the answer stays the main content.
 */
@Composable
internal fun NativeExerciseGuide(contents: List<ExerciseContentItem>, compact: Boolean) {
    if (contents.isEmpty()) return
    val context = LocalContext.current
    val featured = if (compact) null else contents.first()
    val rows = if (compact) contents else contents.drop(1)
    var showAll by rememberSaveable(contents.joinToString { it.contentId }) { mutableStateOf(false) }
    val visibleRows = if (showAll) rows else rows.take(ExerciseGuideRowsShown)
    Spacer(Modifier.height(if (compact) 18.dp else 22.dp))
    Text(
        text = stringResource(R.string.discovery_content_title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
    Column(
        modifier = Modifier.fillMaxWidth().testTag("discovery-content-list"),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
    ) {
        featured?.let { content ->
            FeaturedExerciseGuide(content) { openExerciseContent(context, content.contentUrl) }
        }
        visibleRows.forEach { content ->
            ExerciseGuideRow(content) { openExerciseContent(context, content.contentUrl) }
        }
        val hidden = rows.size - visibleRows.size
        if (hidden > 0) {
            TextButton(
                onClick = { showAll = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("discovery-content-more"),
            ) {
                Text(stringResource(R.string.native_exercise_guide_more, hidden), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun FeaturedExerciseGuide(content: ExerciseContentItem, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("discovery-content-${content.contentId}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column {
            ExerciseGuideThumbnail(
                content = content,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                shape = RectangleShape,
                playSize = 48.dp,
            )
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleSmall.merge(KoreanPhraseBreak),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (content.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = content.summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.merge(KoreanPhraseBreak),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ExerciseGuideSource(content, MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ExerciseGuideRow(content: ExerciseContentItem, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .testTag("discovery-content-${content.contentId}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseGuideThumbnail(
            content = content,
            modifier = Modifier.width(112.dp).aspectRatio(16f / 9f),
            shape = RoundedCornerShape(10.dp),
            playSize = 28.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = content.title,
                style = MaterialTheme.typography.labelLarge.merge(KoreanPhraseBreak),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            ExerciseGuideSource(content, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExerciseGuideSource(content: ExerciseContentItem, color: Color) {
    Text(
        text = stringResource(R.string.discovery_content_source, content.sourceName),
        modifier = Modifier.testTag("discovery-content-source-${content.contentId}"),
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ExerciseGuideThumbnail(content: ExerciseContentItem, modifier: Modifier, shape: Shape, playSize: Dp) {
    Box(
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = content.thumbnailUrl,
            contentDescription = stringResource(R.string.discovery_content_image_description, content.title),
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier.size(playSize).background(Color.Black.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_material_symbol_play_arrow_24),
                contentDescription = null,
                modifier = Modifier.size(playSize * 0.6f),
                tint = Color.White,
            )
        }
    }
}
