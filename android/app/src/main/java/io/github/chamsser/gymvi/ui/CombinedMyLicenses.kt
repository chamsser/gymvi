package io.github.chamsser.gymvi.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.naver.maps.map.app.OpenSourceLicenseActivity
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.OpenSourceLibrary
import io.github.chamsser.gymvi.data.OpenSourceLicenses
import io.github.chamsser.gymvi.data.OpenSourceScreen
import io.github.chamsser.gymvi.data.licenseParagraphs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 오픈소스 라이선스: every library the app ships, by name and version, from the list the build keeps
 * in step with the packaged artifacts (OpenSourceLicenses). A library opens its license and notice
 * texts in full, and the map SDK opens its own notice screen instead. Coming back finds the list
 * where it was.
 */
@Composable
internal fun CombinedMyLicensesPage(
    palette: CombinedPalette,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val libraries by produceState<List<OpenSourceLibrary>?>(null, context) {
        value = withContext(Dispatchers.IO) { OpenSourceLicenses.load(context.assets) }
    }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val title = stringResource(R.string.my_open_source_licenses)
    if (openKey != null) {
        val close = { openKey = null }
        BackHandler(onBack = close)
        CombinedMyLicensePage(
            title = title,
            library = libraries?.firstOrNull { it.key == openKey },
            palette = palette,
            onBack = close,
            modifier = modifier,
        )
        return
    }
    BackHandler(onBack = onBack)
    CombinedMyLazyPage(
        title = title,
        palette = palette,
        onBack = onBack,
        state = listState,
        listTestTag = "my-licenses-list",
        modifier = modifier.testTag("my-licenses-page"),
    ) {
        val items = libraries.orEmpty()
        itemsIndexed(items, key = { _, library -> library.key }) { index, library ->
            CombinedMyRow(
                iconRes = null,
                title = library.name,
                value = library.version,
                modifier = Modifier
                    .padding(top = if (index == 0) 12.dp else 2.dp)
                    .clip(combinedMyTileShape(index, items.size))
                    .background(palette.groupedRow),
                testTag = "my-license-${library.key}",
                onClick = {
                    when (library.screen) {
                        OpenSourceScreen.NAVER_MAP_SDK ->
                            context.startActivity(Intent(context, OpenSourceLicenseActivity::class.java))
                        null -> openKey = library.key
                    }
                },
                alignWithIcons = false,
            )
        }
    }
}

/**
 * One library: its name and version, then each text under its title, a stored text's paragraphs on
 * one continuous tile and a text named only by its address as that address.
 */
@Composable
private fun CombinedMyLicensePage(
    title: String,
    library: OpenSourceLibrary?,
    palette: CombinedPalette,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sections by produceState<List<LicenseSection>?>(null, library, context) {
        value = library?.let {
            withContext(Dispatchers.IO) {
                it.texts.map { text ->
                    val content = OpenSourceLicenses.readText(context.assets, text)
                    LicenseSection(
                        title = text.title,
                        paragraphs = content?.let { stored -> licenseParagraphs(stored).map { lines -> lines.joinToString("\n") } },
                        url = text.url,
                    )
                }
            }
        }
    }
    val unknown = stringResource(R.string.unknown_value)
    CombinedMyLazyPage(
        title = title,
        palette = palette,
        onBack = onBack,
        state = rememberLazyListState(),
        listTestTag = "my-license-texts",
        modifier = modifier.testTag("my-license-page"),
    ) {
        if (library == null) return@CombinedMyLazyPage
        item(key = "library") {
            CombinedMyRow(
                iconRes = null,
                title = library.name,
                value = library.version,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clip(combinedMyTileShape(0, 1))
                    .background(palette.groupedRow),
                testTag = "my-license-library",
                alignWithIcons = false,
            )
        }
        sections.orEmpty().forEachIndexed { index, section ->
            item(key = "title-$index") {
                CombinedMyGroupLabel(section.title, Modifier.padding(top = 24.dp).testTag("my-license-text-$index"))
            }
            // A text that cannot be read is said to be unknown instead of leaving its title alone.
            val paragraphs = section.paragraphs?.takeIf { it.isNotEmpty() } ?: listOf(section.url ?: unknown)
            itemsIndexed(paragraphs, key = { paragraph, _ -> "text-$index-$paragraph" }) { paragraph, text ->
                LicenseParagraph(
                    text = text,
                    first = paragraph == 0,
                    last = paragraph == paragraphs.lastIndex,
                    palette = palette,
                )
            }
        }
    }
}

/** A part of one text's tile: the tile's rounded top on the first paragraph and its bottom on the last. */
@Composable
private fun LicenseParagraph(text: String, first: Boolean, last: Boolean, palette: CombinedPalette) {
    val top = if (first) CombinedMyOuterCorner else 0.dp
    val bottom = if (last) CombinedMyOuterCorner else 0.dp
    // Each paragraph can be selected and copied, such as an address shown without a link.
    SelectionContainer(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.groupedRow, RoundedCornerShape(top, top, bottom, bottom))
            .padding(start = 16.dp, end = 16.dp, top = if (first) 14.dp else 0.dp, bottom = if (last) 14.dp else 12.dp),
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One text as the page shows it: the paragraphs of a stored text, or the address of one that is not. */
private class LicenseSection(val title: String, val paragraphs: List<String>?, val url: String?)

/**
 * A page that 내 정보 opens over itself like CombinedMySubPage, for content too long to lay out at
 * once: the rows scroll under the same fading header, and only those on screen are composed.
 */
@Composable
private fun CombinedMyLazyPage(
    title: String,
    palette: CombinedPalette,
    onBack: () -> Unit,
    state: LazyListState,
    listTestTag: String,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    // Bars an enclosing frame already pads for, such as the bottom tabs, are left out here too.
    var consumed by remember { mutableStateOf(WindowInsets(0, 0, 0, 0)) }
    val bars = WindowInsets.statusBars.union(WindowInsets.navigationBars).exclude(consumed).asPaddingValues()
    val direction = LocalLayoutDirection.current
    Box(modifier = modifier.fillMaxSize().background(palette.groupedPage)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .onConsumedWindowInsetsChanged { consumed = it }
                .testTag(listTestTag),
            state = state,
            contentPadding = PaddingValues(
                start = bars.calculateStartPadding(direction) + 16.dp,
                top = bars.calculateTopPadding() + CombinedHeaderHeight + 4.dp,
                end = bars.calculateEndPadding(direction) + 16.dp,
                bottom = bars.calculateBottomPadding() + 32.dp,
            ),
            content = content,
        )
        CombinedMyHeader(
            title = title,
            palette = palette,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
            backTestTag = "my-subpage-back",
            titleTestTag = "my-subpage-title",
        )
    }
}
