package io.github.chamsser.gymvi.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R

/**
 * What leaves the device and where it goes, in the sections a Korean privacy policy is expected to
 * have: the information and its purpose, outside services and transfers abroad, retention, and the
 * user's choices. Each sentence follows the request that sends the data, and PrivacyNoticeCopyTest
 * holds the facility ID counts to those requests.
 */
@Composable
internal fun CombinedMyPrivacyPage(
    palette: CombinedPalette,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    CombinedMySubPage(
        title = stringResource(R.string.my_privacy_notice),
        palette = palette,
        onBack = onBack,
        modifier = modifier.testTag("my-privacy-page"),
    ) {
        Text(
            text = stringResource(R.string.my_privacy_summary),
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .testTag("my-privacy-summary"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = combinedMyDescriptionStyle(),
        )
        CombinedMyPrivacySections.forEach { section ->
            CombinedMyGroup(
                label = stringResource(section.title),
                palette = palette,
                rows = section.facts.map { fact ->
                    val row: @Composable (Modifier) -> Unit = { rowModifier -> CombinedMyPrivacyFact(fact, rowModifier) }
                    row
                },
            )
        }
        Text(
            text = stringResource(R.string.my_privacy_effective),
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .testTag("my-privacy-effective"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** One fact: what it is about, then what happens to it. */
@Composable
private fun CombinedMyPrivacyFact(fact: PrivacyFact, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag("my-privacy-${fact.key}")
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = stringResource(fact.title),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(fact.body),
            modifier = Modifier.testTag("my-privacy-${fact.key}-body"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = combinedMyDescriptionStyle(),
        )
    }
}

private data class PrivacyFact(val key: String, @StringRes val title: Int, @StringRes val body: Int)

private data class PrivacySection(@StringRes val title: Int, val facts: List<PrivacyFact>)

private val CombinedMyPrivacySections = listOf(
    PrivacySection(
        R.string.my_privacy_section_items,
        listOf(
            PrivacyFact("device", R.string.my_privacy_device_title, R.string.my_privacy_notice_device),
            PrivacyFact("ai", R.string.my_privacy_ai_title, R.string.my_privacy_notice_ai),
            PrivacyFact("references", R.string.my_privacy_references_title, R.string.my_privacy_notice_references),
            PrivacyFact("location", R.string.my_privacy_location_title, R.string.my_privacy_notice_location),
            PrivacyFact("facility-ids", R.string.my_privacy_facility_ids_title, R.string.my_privacy_notice_facility_ids),
            PrivacyFact("map", R.string.my_privacy_map_title, R.string.my_privacy_notice_map),
        ),
    ),
    PrivacySection(
        R.string.my_privacy_section_services,
        listOf(
            PrivacyFact("openai", R.string.my_privacy_openai_title, R.string.my_privacy_notice_openai),
            PrivacyFact("naver", R.string.my_privacy_naver_title, R.string.my_privacy_notice_naver),
            PrivacyFact("weather", R.string.my_privacy_weather_title, R.string.my_privacy_notice_weather),
            PrivacyFact("external-map", R.string.my_privacy_external_map_title, R.string.my_privacy_notice_external_map),
        ),
    ),
    PrivacySection(
        R.string.my_privacy_section_retention,
        listOf(
            PrivacyFact("server", R.string.my_privacy_server_title, R.string.my_privacy_notice_server),
            PrivacyFact("erase", R.string.my_privacy_erase_title, R.string.my_privacy_notice_erase),
        ),
    ),
    PrivacySection(
        R.string.my_privacy_section_rights,
        listOf(
            PrivacyFact("controls", R.string.my_privacy_controls_title, R.string.my_privacy_notice_controls),
            PrivacyFact("permission", R.string.my_privacy_permission_title, R.string.my_privacy_notice_permission),
        ),
    ),
)
