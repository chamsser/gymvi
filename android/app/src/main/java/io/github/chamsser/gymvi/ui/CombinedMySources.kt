package io.github.chamsser.gymvi.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.PublicDataKind
import io.github.chamsser.gymvi.data.PublicDataSource

/**
 * 공공데이터 출처: for the facility data and the program data the server has active, who provides
 * it, its reference date, the published version and the terms of use. Whatever the server did not
 * confirm reads 알 수 없음, and the program data's citation shows under its group exactly as sent.
 */
@Composable
internal fun CombinedMySourcesPage(
    sources: List<PublicDataSource>,
    palette: CombinedPalette,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    CombinedMySubPage(
        title = stringResource(R.string.my_public_data_source),
        palette = palette,
        onBack = onBack,
        modifier = modifier.testTag("my-sources-page"),
    ) {
        CombinedMySourceGroup(
            label = stringResource(R.string.facility_information_title),
            key = "facility",
            source = sources.firstOrNull { it.kind == PublicDataKind.FACILITY },
            palette = palette,
        )
        val program = sources.firstOrNull { it.kind == PublicDataKind.PROGRAM }
        CombinedMySourceGroup(
            label = stringResource(R.string.usage_option_section),
            key = "program",
            source = program,
            palette = palette,
        )
        program?.attribution?.let { attribution ->
            Text(
                text = stringResource(R.string.usage_option_source_attribution, attribution),
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    .testTag("my-source-program-attribution"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = combinedMyDescriptionStyle(),
            )
        }
    }
}

/** One dataset's four facts, each read with its label as one item. */
@Composable
private fun CombinedMySourceGroup(
    label: String,
    key: String,
    source: PublicDataSource?,
    palette: CombinedPalette,
) {
    val unknown = stringResource(R.string.unknown_value)
    val facts = listOf(
        SourceFact("provider", R.string.my_source_provider, source?.provider),
        SourceFact("as-of", R.string.my_source_as_of, formatProgramAsOf(source?.asOf)),
        SourceFact("version", R.string.my_source_version, source?.datasetVersion),
    )
    CombinedMyGroup(
        label = label,
        palette = palette,
        rows = facts.map { fact ->
            val row: @Composable (Modifier) -> Unit = { rowModifier ->
                CombinedMyRow(
                    iconRes = null,
                    title = stringResource(fact.title),
                    value = fact.value ?: unknown,
                    modifier = rowModifier,
                    testTag = "my-source-$key-${fact.key}",
                    alignWithIcons = false,
                )
            }
            row
        },
    )
}

private class SourceFact(val key: String, @StringRes val title: Int, val value: String?)
