package io.github.chamsser.gymvi.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.UsageOptionItem
import io.github.chamsser.gymvi.data.UsageOptionPage
import io.github.chamsser.gymvi.data.FeedUsageOption
import io.github.chamsser.gymvi.data.UsageOptionsSource
import java.net.URI
import java.time.LocalDate

@Composable
internal fun UsageOptionCard(
    option: UsageOptionItem,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    facilityName: String? = null,
) {
    val period = when (usageOptionPeriod(option, today)) {
        UsageOptionPeriod.CURRENT -> stringResource(R.string.usage_option_current)
        UsageOptionPeriod.UPCOMING -> stringResource(R.string.usage_option_upcoming)
        else -> stringResource(R.string.usage_option_period_unknown)
    }
    val values = buildList {
        add(period)
        formatUsageOptionWeekdays(option.weekdays)?.let(::add)
        add(formatUsageOptionTime(option) ?: stringResource(R.string.usage_option_time_unknown))
        add(formatUsageOptionPrice(option.priceWon) ?: stringResource(R.string.usage_option_price_unknown))
        add(option.targetName ?: stringResource(R.string.usage_option_target_unknown))
        add(stringResource(usageOptionApplicationLabel(option.programApplication, card = true)))
    }
    val accessibilityDescription = (listOfNotNull(option.programName, facilityName) + values).joinToString(", ")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityDescription
            }
            .clickable(
                onClickLabel = stringResource(R.string.usage_option_details),
                role = Role.Button,
                onClick = onClick,
            )
            .testTag("usage-option-card"),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, gymviSubtleBorderColor()),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(
                text = option.programName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            facilityName?.let { name ->
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                values.forEach { value ->
                    Text(
                        text = value,
                        modifier = Modifier.alignByBaseline(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun UsageOptionFeedSection(
    options: List<FeedUsageOption>,
    source: UsageOptionsSource?,
    today: LocalDate,
    onOptionSelected: (FeedUsageOption) -> Unit,
    onCompare: (List<String>) -> Unit,
) {
    if (options.isEmpty()) return
    Spacer(modifier = Modifier.height(20.dp))
    Column(modifier = Modifier.fillMaxWidth().testTag("usage-option-feed-section")) {
        Text(
            text = stringResource(R.string.usage_option_feed_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        options.forEach { item ->
            UsageOptionCard(
                option = item.option,
                today = today,
                onClick = { onOptionSelected(item) },
                modifier = Modifier.padding(bottom = 8.dp),
                facilityName = item.facilityName,
            )
        }
        if (options.size >= 2) {
            TextButton(
                onClick = { onCompare(options.map { it.option.usageOptionId }) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .testTag("usage-option-compare-button"),
            ) { Text(stringResource(R.string.usage_option_compare_button)) }
        }
        UsageOptionSourceDisclosure(source?.attribution, source?.programAsOf, showUnknown = true)
    }
}

@Composable
internal fun UsageOptionSection(
    state: UsageOptionListState,
    today: LocalDate,
    onRetry: (String) -> Unit,
    onOptionSelected: (UsageOptionItem) -> Unit,
    onShowAll: (String) -> Unit,
) {
    val ready = state as? UsageOptionListState.Ready
    val visible = ready?.page?.let { visibleUsageOptions(it.options, today) }.orEmpty()
    if (state is UsageOptionListState.Idle ||
        state is UsageOptionListState.Error &&
        (!state.retryable || state.code == "PROGRAM_DATASET_NOT_ACTIVE") ||
        ready != null && (visible.isEmpty() || "PROGRAM_DATASET_NOT_ACTIVE" in ready.page.unavailableReasonCodes)
    ) return

    Spacer(modifier = Modifier.height(20.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("usage-option-section"),
    ) {
        Text(
            text = stringResource(R.string.usage_option_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        when (state) {
            is UsageOptionListState.Loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.facility_loading_more),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is UsageOptionListState.Error -> {
                Text(
                    text = stringResource(R.string.usage_option_load_error),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(
                    onClick = { onRetry(state.facilityId) },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.retry)) }
            }
            is UsageOptionListState.Ready -> {
                previewUsageOptions(state.page.options, today).forEach { option ->
                    UsageOptionCard(
                        option = option,
                        today = today,
                        onClick = { onOptionSelected(option) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (visible.size > 3) {
                    TextButton(
                        onClick = { onShowAll(state.page.facilityId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("usage-option-show-all"),
                    ) {
                        Text(stringResource(R.string.usage_option_show_all, visible.size))
                    }
                }
                UsageOptionSourceDisclosure(state.page.attribution, state.page.programAsOf)
            }
            UsageOptionListState.Idle -> Unit
        }
    }
}

@Composable
internal fun UsageOptionListScreen(
    facilityName: String,
    page: UsageOptionPage,
    today: LocalDate,
    onBack: () -> Unit,
    onOptionSelected: (UsageOptionItem) -> Unit,
) {
    val visible = visibleUsageOptions(page.options, today)
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("usage-option-list"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            UsageOptionScreenHeader(
                title = stringResource(R.string.usage_option_section),
                subtitle = facilityName,
                onBack = onBack,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 20.dp,
                ),
            ) {
                listOf(
                    UsageOptionPeriod.CURRENT to R.string.usage_option_current,
                    UsageOptionPeriod.UPCOMING to R.string.usage_option_upcoming,
                    UsageOptionPeriod.UNKNOWN to R.string.usage_option_period_unknown,
                ).forEach { (period, titleRes) ->
                    val group = visible.filter { usageOptionPeriod(it, today) == period }
                    if (group.isNotEmpty()) {
                        item(key = "header-$period") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 20.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(titleRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = group.size.toString(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        items(group, key = UsageOptionItem::usageOptionId) { option ->
                            UsageOptionCard(
                                option = option,
                                today = today,
                                onClick = { onOptionSelected(option) },
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                }
                item(key = "source") {
                    UsageOptionSourceDisclosure(page.attribution, page.programAsOf)
                }
            }
        }
    }
}

@Composable
internal fun UsageOptionDetailScreen(
    option: UsageOptionItem,
    facilityName: String,
    evidenceState: UsageOptionEvidenceState,
    today: LocalDate,
    onBack: () -> Unit,
    onRetryEvidence: (UsageOptionItem) -> Unit,
    onDecide: (UsageOptionItem) -> Unit,
) {
    val context = LocalContext.current
    var rawExpanded by rememberSaveable(option.usageOptionId) { mutableStateOf(false) }
    val unknown = stringResource(R.string.unknown_value)
    val rows = buildList {
        add(UsageOptionDetailEntry(stringResource(R.string.usage_option_period_label),
            formatUsageOptionDateRange(option.beginDate, option.endDate, today) ?: unknown))
        add(UsageOptionDetailEntry(stringResource(R.string.usage_option_weekdays_label),
            formatUsageOptionWeekdays(option.weekdays) ?: unknown))
        val operator = option.operatorTime?.takeIf { option.sourceTimeValue == null }
        add(UsageOptionDetailEntry(
            stringResource(R.string.usage_option_time_label),
            formatUsageOptionTime(option) ?: unknown,
            operator?.let { time ->
                listOfNotNull(
                    formatOperatorTimeDays(time.days),
                    time.sources.first().name,
                )
            }.orEmpty(),
        ))
        add(UsageOptionDetailEntry(stringResource(R.string.usage_option_target_label), option.targetName ?: unknown))
        add(UsageOptionDetailEntry(stringResource(R.string.usage_option_price_label),
            formatUsageOptionPrice(option.priceWon) ?: unknown))
        add(UsageOptionDetailEntry(stringResource(R.string.usage_option_price_type_label), option.priceTypeName ?: unknown))
        add(UsageOptionDetailEntry(stringResource(R.string.usage_option_recruitment_label),
            formatUsageOptionRecruitment(option.recruitmentCount) ?: unknown))
        add(UsageOptionDetailEntry(stringResource(R.string.usage_option_application_label),
            stringResource(usageOptionApplicationLabel(option.programApplication, card = false))))
        usageOptionOperationLabel(option.facilityOperation)?.let { value ->
            add(UsageOptionDetailEntry(stringResource(R.string.usage_option_operation_label), stringResource(value)))
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("usage-option-detail"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            UsageOptionScreenHeader(option.programName, facilityName, onBack)
            LazyColumn(
                modifier = Modifier.weight(1f).testTag("usage-option-detail-list"),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = 24.dp,
                ),
            ) {
                items(rows) { row -> UsageOptionDetailRow(row.label, row.value, row.secondaryLines) }
                item {
                    Row(
                        // The label is read with 알 수 없음; the button keeps its own stop.
                        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.usage_option_homepage_label),
                            modifier = Modifier.width(82.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (option.homepageUrl == null) {
                            Text(unknown, style = MaterialTheme.typography.bodyMedium)
                        } else {
                            TextButton(
                                onClick = { openUsageOptionHomepage(context, option.homepageUrl) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(0.dp),
                            ) { Text(stringResource(R.string.usage_option_homepage_open)) }
                        }
                    }
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 22.dp)
                            .testTag("usage-option-source"),
                    ) {
                        Text(
                            text = stringResource(R.string.usage_option_source_title),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        when (val source = evidenceState.forOption(option.usageOptionId)) {
                            UsageOptionEvidenceState.Idle,
                            is UsageOptionEvidenceState.Loading -> Text(
                                text = stringResource(R.string.facility_loading_more),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            is UsageOptionEvidenceState.Error -> {
                                Text(
                                    text = stringResource(R.string.usage_option_source_error),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (source.retryable) {
                                    TextButton(
                                        onClick = { onRetryEvidence(option) },
                                        modifier = Modifier.heightIn(min = 48.dp),
                                    ) { Text(stringResource(R.string.retry)) }
                                }
                            }
                            is UsageOptionEvidenceState.Ready -> {
                                // The section title already says 정보 출처, so the citation stands alone.
                                Text(
                                    text = source.evidence.licenseAttribution,
                                    modifier = Modifier.testTag("usage-option-source-citation"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                formatProgramAsOf(source.evidence.datasetAsOf)?.let { date ->
                                    Text(
                                        text = stringResource(R.string.usage_option_evidence_date, date),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                TextButton(
                                    onClick = { rawExpanded = !rawExpanded },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(0.dp),
                                ) {
                                    Text(stringResource(
                                        if (rawExpanded) R.string.usage_option_raw_hide
                                        else R.string.usage_option_raw_show,
                                    ))
                                }
                                if (rawExpanded) {
                                    usageOptionRawRows(source.evidence.fields).forEach { (label, value) ->
                                        UsageOptionDetailRow(
                                            stringResource(label),
                                            value ?: stringResource(R.string.usage_option_raw_empty),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 5.dp,
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = gymviSubtleBorderColor())
                    FacilityActionButton(
                        label = stringResource(R.string.usage_option_decide),
                        iconRes = R.drawable.ic_material_symbol_location_on_24,
                        testTag = "usage-option-decide",
                        emphasized = true,
                        onClick = { onDecide(option) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun UsageOptionScreenHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_material_symbol_arrow_back_24),
                contentDescription = stringResource(R.string.usage_option_back),
            )
        }
        Column(modifier = Modifier.padding(start = 4.dp, top = 5.dp, bottom = 12.dp)) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    HorizontalDivider(color = gymviSubtleBorderColor())
}

private data class UsageOptionDetailEntry(
    val label: String,
    val value: String,
    val secondaryLines: List<String> = emptyList(),
)

@Composable
private fun UsageOptionDetailRow(label: String, value: String, secondaryLines: List<String> = emptyList()) {
    Row(
        // A screen reader stops once on the row and reads the label with its value.
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 10.dp),
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
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
            secondaryLines.forEach { line ->
                Text(
                    text = line,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
internal fun UsageOptionSourceCaption(attribution: String?, asOf: String?, showUnknown: Boolean = false) {
    if (!showUnknown && attribution == null && asOf == null) return
    val unknown = stringResource(R.string.unknown_value)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        (attribution ?: unknown.takeIf { showUnknown })?.let { source ->
            Text(
                text = stringResource(R.string.usage_option_source_attribution, source),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * One source line that is its own toggle: it names the provider and opens in place to the full
 * citation and the dataset date, so the provider is never listed twice.
 */
@Composable
internal fun UsageOptionSourceDisclosure(
    attribution: String?,
    asOf: String?,
    showUnknown: Boolean = false,
) {
    if (!showUnknown && attribution == null && asOf == null) return
    val unknown = stringResource(R.string.unknown_value)
    val provider = attribution ?: unknown
    val programProvider = stringResource(R.string.usage_option_program_provider)
    // The API sends the full citation; the collapsed line shows only the provider name.
    val shortProvider = if (provider.startsWith(programProvider)) programProvider else provider
    val expandedDescription = stringResource(R.string.usage_option_source_expanded)
    val collapsedDescription = stringResource(R.string.usage_option_source_collapsed)
    val reduceMotion = LocalNativeDesign.current.reduceMotion
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .heightIn(min = 48.dp)
            .clickable(
                onClickLabel = stringResource(R.string.usage_option_source_more),
                role = Role.Button,
            ) { expanded = !expanded }
            .semantics {
                stateDescription = if (expanded) expandedDescription else collapsedDescription
            }
            .animateContentSize(if (reduceMotion) snap() else spring())
            .testTag("usage-option-source-details"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(
                text = stringResource(
                    R.string.usage_option_source_attribution,
                    if (expanded) provider else shortProvider,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
            if (expanded) {
                val date = formatProgramAsOf(asOf)
                Text(
                    text = if (date == null) {
                        stringResource(R.string.usage_option_source_as_of_unknown)
                    } else {
                        stringResource(R.string.usage_option_evidence_date, date)
                    },
                    modifier = Modifier.padding(top = 2.dp).testTag("usage-option-source-expanded"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_material_symbol_expand_more_24),
            contentDescription = null,
            modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openUsageOptionHomepage(context: Context, rawUrl: String) {
    val safe = runCatching { URI(rawUrl) }.getOrNull()?.let { uri ->
        uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    } == true
    if (safe) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)))
            return
        } catch (_: ActivityNotFoundException) {
            // The same visible failure applies to an unsupported browser.
        } catch (_: SecurityException) {
            // The same visible failure applies when the system rejects the intent.
        }
    }
    Toast.makeText(context, R.string.usage_option_homepage_error, Toast.LENGTH_SHORT).show()
}
