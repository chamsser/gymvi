package io.github.chamsser.gymvi.ui

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak

/**
 * Phrase breaking keeps Korean words whole instead of splitting a syllable run across lines. It
 * follows the text locale, so the Korean copy declares it even on a device set to English.
 */
internal val KoreanPhraseBreak = TextStyle(
    localeList = LocaleList("ko-KR"),
    lineBreak = LineBreak(
        strategy = LineBreak.Strategy.HighQuality,
        strictness = LineBreak.Strictness.Strict,
        wordBreak = LineBreak.WordBreak.Phrase,
    ),
)

/**
 * The width in pixels of the widest single word in [texts], so a box under large text can grow, or
 * move its label above the value, before any word has to break inside itself.
 */
internal fun TextMeasurer.widestWord(texts: List<String>, style: TextStyle): Int = texts
    .flatMap { it.split(Regex("\\s+")) }
    .filter(String::isNotEmpty)
    .maxOfOrNull { word -> measure(word, style, softWrap = false, maxLines = 1).size.width }
    ?: 0
