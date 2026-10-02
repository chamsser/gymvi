package io.github.chamsser.gymvi.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** The published data a [PublicDataSource] describes. */
enum class PublicDataKind { FACILITY, PROGRAM }

/**
 * Where one active dataset came from, as GET /api/v1/meta/version lists it. The server fills the
 * provider and the terms only from facts checked against that exact publish; whatever it left null,
 * or sent in a shape this app does not accept, is null here and reads as unknown.
 */
data class PublicDataSource(
    val kind: PublicDataKind,
    val datasetVersion: String?,
    /** The dataset's reference time as the server sent it, kept only when it parses as an instant. */
    val asOf: String?,
    val provider: String?,
    val licenseName: String?,
    /** The citation the source asks for, exactly as the server sent it. */
    val attribution: String?,
)

/**
 * The listed datasets, facility data first, at most one per kind. A missing or malformed list is
 * empty, and a kind listed twice is left out, since nothing tells which of the two is current.
 */
internal fun parsePublicDataSources(datasets: Any?): List<PublicDataSource> {
    val items = datasets as? JSONArray ?: return emptyList()
    return (0 until items.length())
        .mapNotNull { index -> parsePublicDataSource(items.opt(index)) }
        .groupBy { it.kind }
        .values
        .mapNotNull { it.singleOrNull() }
        .sortedBy { it.kind.ordinal }
}

private fun parsePublicDataSource(item: Any?): PublicDataSource? {
    val source = item as? JSONObject ?: return null
    val kind = PublicDataKind.entries.firstOrNull { it.name == source.opt("dataset_kind") } ?: return null
    val license = source.opt("license") as? JSONObject
    return PublicDataSource(
        kind = kind,
        datasetVersion = (source.opt("dataset_version") as? String)?.takeIf { DATASET_VERSION.matches(it) },
        asOf = (source.opt("as_of") as? String)?.takeIf { runCatching { Instant.parse(it) }.isSuccess },
        provider = source.displayText("provider"),
        licenseName = license?.displayText("name"),
        attribution = license?.displayText("attribution"),
    )
}

private fun JSONObject.displayText(key: String): String? = (opt(key) as? String)?.takeIf(::isDisplayText)

/** One line of 1 to 300 characters with no surrounding space and no control or hidden characters. */
private fun isDisplayText(value: String): Boolean =
    value.isNotEmpty() &&
        value == value.trim() &&
        value.codePointCount(0, value.length) <= MAX_DISPLAY_CODE_POINTS &&
        value.codePoints().noneMatch { Character.getType(it) in HIDDEN_CHARACTER_TYPES }

private val DATASET_VERSION = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,199}$")
private const val MAX_DISPLAY_CODE_POINTS = 300
private val HIDDEN_CHARACTER_TYPES = setOf(
    Character.CONTROL,
    Character.FORMAT,
    Character.LINE_SEPARATOR,
    Character.PARAGRAPH_SEPARATOR,
    Character.SURROGATE,
    Character.PRIVATE_USE,
    Character.UNASSIGNED,
).map { it.toInt() }.toSet()
