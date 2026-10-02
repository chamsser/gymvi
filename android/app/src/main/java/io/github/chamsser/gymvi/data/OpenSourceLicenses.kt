package io.github.chamsser.gymvi.data

import android.content.res.AssetManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** A license or notice text that libraries ship under: stored with the app, or named by its address. */
data class OpenSourceText(val title: String, val file: String?, val url: String?)

/** A screen of its own that shows a library's notices instead of the texts here. */
enum class OpenSourceScreen(val key: String) { NAVER_MAP_SDK("naver-map-sdk") }

/**
 * One library the app ships: the artifacts it packages under one name and version, and either the
 * texts to show for it in order or the screen that shows them.
 */
data class OpenSourceLibrary(
    val name: String,
    val version: String,
    val artifacts: List<String>,
    val texts: List<OpenSourceText>,
    val screen: OpenSourceScreen?,
) {
    /** Tells the entry apart from the others: an artifact belongs to one entry only. */
    val key: String get() = artifacts.first()
}

/**
 * The static list under assets/open_source_licenses. The build's verifyOpenSourceLicenses task keeps
 * it equal to what each variant packages, and a debug build adds its debug-only entries.
 */
object OpenSourceLicenses {
    private const val DIRECTORY = "open_source_licenses"
    private const val MAIN_INDEX = "libraries.json"
    private const val DEBUG_INDEX = "libraries-debug.json"

    /** Every library sorted by name, or none when the list cannot be read. */
    fun load(assets: AssetManager): List<OpenSourceLibrary> =
        try {
            val main = JSONObject(assets.readText("$DIRECTORY/$MAIN_INDEX"))
            val debug = if (assets.list(DIRECTORY)?.contains(DEBUG_INDEX) == true) {
                JSONObject(assets.readText("$DIRECTORY/$DEBUG_INDEX"))
            } else {
                null
            }
            parseOpenSourceLibraries(main, debug)
        } catch (_: IOException) {
            emptyList()
        } catch (_: JSONException) {
            emptyList()
        }

    /** A stored text's content, or null for a text named only by its address or one that cannot be read. */
    fun readText(assets: AssetManager, text: OpenSourceText): String? {
        val file = text.file ?: return null
        return try {
            assets.readText("$DIRECTORY/$file")
        } catch (_: IOException) {
            null
        }
    }

    private fun AssetManager.readText(path: String): String = open(path).use { it.readBytes().decodeToString() }
}

/**
 * The libraries of [main] and [debug] sorted by name. Texts are defined once in [main]; an entry
 * naming a text or screen that does not exist, missing its name, version or artifacts, or starting
 * with an artifact an earlier entry starts with, is left out.
 */
internal fun parseOpenSourceLibraries(main: JSONObject, debug: JSONObject?): List<OpenSourceLibrary> {
    val texts = main.optJSONObject("texts")?.let { entries ->
        entries.keys().asSequence().mapNotNull { id ->
            val entry = entries.optJSONObject(id) ?: return@mapNotNull null
            val title = entry.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val file = entry.opt("file") as? String
            val url = entry.opt("url") as? String
            if ((file == null) == (url == null)) null else id to OpenSourceText(title, file, url)
        }.toMap()
    }.orEmpty()
    return (parseLibraries(main.optJSONArray("libraries"), texts) + parseLibraries(debug?.optJSONArray("libraries"), texts))
        .distinctBy(OpenSourceLibrary::key)
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, OpenSourceLibrary::name).thenBy(OpenSourceLibrary::version))
}

private fun parseLibraries(items: JSONArray?, texts: Map<String, OpenSourceText>): List<OpenSourceLibrary> {
    if (items == null) return emptyList()
    return (0 until items.length()).mapNotNull { index ->
        val entry = items.optJSONObject(index) ?: return@mapNotNull null
        val name = entry.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val version = entry.optString("version").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val artifacts = entry.optJSONArray("artifacts")?.strings().orEmpty()
        val textIds = entry.optJSONArray("texts")?.strings()
        val screenKey = entry.opt("opens") as? String
        val screen = OpenSourceScreen.entries.firstOrNull { it.key == screenKey }
        val libraryTexts = textIds?.map { id -> texts[id] ?: return@mapNotNull null }.orEmpty()
        when {
            artifacts.isEmpty() -> null
            screenKey != null && (screen == null || textIds != null) -> null
            screen == null && libraryTexts.isEmpty() -> null
            else -> OpenSourceLibrary(name, version, artifacts, libraryTexts, screen)
        }
    }
}

private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { opt(it) as? String }

/**
 * A license text as paragraphs of lines for a narrow screen. License files are wrapped for a fixed
 * width, so a line that only carries on the sentence above joins it. List items, rules, addresses,
 * copyright lines and lines the author ended early, such as titles, keep a line of their own, and
 * every word stays as it is.
 */
internal fun licenseParagraphs(text: String): List<List<String>> {
    val paragraphs = mutableListOf<List<String>>()
    var lines = mutableListOf<String>()
    var previous: String? = null
    for (raw in text.lines()) {
        val line = raw.trim()
        if (line.isEmpty()) {
            if (lines.isNotEmpty()) paragraphs += lines
            lines = mutableListOf()
            previous = null
            continue
        }
        if (previous != null && continuesLine(previous, line)) {
            lines[lines.lastIndex] = lines.last() + " " + line
        } else {
            lines += line
        }
        previous = line
    }
    if (lines.isNotEmpty()) paragraphs += lines
    return paragraphs
}

private fun continuesLine(previous: String, line: String): Boolean =
    previous.length >= MIN_WRAPPED_LINE_LENGTH &&
        !previous.endsWith(':') &&
        !RULE_LINE.matches(previous) &&
        !RULE_LINE.matches(line) &&
        !LIST_ITEM.containsMatchIn(line) &&
        OWN_LINE_STARTS.none { line.startsWith(it) }

/** Shorter lines were ended on purpose: the files wrap near 70 to 80 characters. */
private const val MIN_WRAPPED_LINE_LENGTH = 40
private val RULE_LINE = Regex("[-=_*~#.]{3,}")
private val LIST_ITEM =
    Regex("^([-*•+]|\\(?[0-9]{1,3}[.)]|\\(?[a-zA-Z][.)]|\\([ivxlcdm]{1,6}\\)|[0-9]+(\\.[0-9]+)+\\.?)(\\s|$)")
private val OWN_LINE_STARTS = listOf("http://", "https://", "Copyright", "(c)", "(C)", "©")
