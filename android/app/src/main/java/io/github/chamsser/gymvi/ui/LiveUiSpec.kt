package io.github.chamsser.gymvi.ui

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

data class LiveUiSpec(
    val revision: String,
    val navigation: LiveUiNavigation,
    val map: LiveUiMapContent,
    val ai: LiveUiAiContent,
    val my: LiveUiMyContent,
)

data class LiveUiNavigation(
    val order: List<RootMode>,
)

data class LiveUiMapContent(
    val searchLabel: String,
    val showContextSummary: Boolean,
)

data class LiveUiAiContent(
    val title: String,
    val headline: String,
    val body: String,
    val examples: List<String>,
    val contentPosition: LiveUiContentPosition,
)

data class LiveUiMyContent(
    val title: String,
)

enum class LiveUiContentPosition {
    CENTERED,
    TOP,
}

internal object LiveUiSpecParser {
    fun parse(body: String): LiveUiSpec {
        require(body.toByteArray(StandardCharsets.UTF_8).size <= MAX_SPEC_BYTES) {
            "Live UI response exceeded the size limit."
        }

        val root = JSONObject(body)
        root.requireExactKeys("schema_version", "revision", "navigation", "map", "ai", "my")
        require(root.strictInt("schema_version") == SCHEMA_VERSION) {
            "Unsupported Live UI schema version."
        }

        val navigation = root.strictObject("navigation").also {
            it.requireExactKeys("order")
        }
        val order = navigation.strictStringArray("order", expectedSize = RootMode.entries.size)
            .map { rawMode ->
                runCatching { RootMode.valueOf(rawMode) }
                    .getOrElse { throw IllegalArgumentException("Unknown root mode.") }
            }
        require(order.toSet() == RootMode.entries.toSet()) {
            "Live UI navigation must contain every root mode exactly once."
        }

        val map = root.strictObject("map").also {
            it.requireExactKeys("search_label", "show_context_summary")
        }
        val ai = root.strictObject("ai").also {
            it.requireExactKeys("title", "headline", "body", "examples", "content_position")
        }
        val my = root.strictObject("my").also {
            it.requireExactKeys("title")
        }

        val revision = root.strictText("revision", maxLength = 32)
        require(REVISION_PATTERN.matches(revision)) {
            "Live UI revision contains unsupported characters."
        }

        val contentPosition = runCatching {
            LiveUiContentPosition.valueOf(ai.strictText("content_position", maxLength = 12))
        }.getOrElse {
            throw IllegalArgumentException("Unknown Live UI content position.")
        }

        return LiveUiSpec(
            revision = revision,
            navigation = LiveUiNavigation(order = order),
            map = LiveUiMapContent(
                searchLabel = map.strictText("search_label", maxLength = 40),
                showContextSummary = map.strictBoolean("show_context_summary"),
            ),
            ai = LiveUiAiContent(
                title = ai.strictText("title", maxLength = 40),
                headline = ai.strictText("headline", maxLength = 80),
                body = ai.strictText("body", maxLength = 180),
                examples = ai.strictStringArray("examples", minSize = 1, maxSize = 4)
                    .map { it.requireSafeText(maxLength = 60) },
                contentPosition = contentPosition,
            ),
            my = LiveUiMyContent(
                title = my.strictText("title", maxLength = 40),
            ),
        )
    }

    private fun JSONObject.strictObject(name: String): JSONObject {
        val value = get(name)
        require(value is JSONObject) { "Live UI field '$name' must be an object." }
        return value
    }

    private fun JSONObject.strictInt(name: String): Int {
        val value = get(name)
        require(value is Number && value.toDouble() == value.toInt().toDouble()) {
            "Live UI field '$name' must be an integer."
        }
        return value.toInt()
    }

    private fun JSONObject.strictBoolean(name: String): Boolean {
        val value = get(name)
        require(value is Boolean) { "Live UI field '$name' must be a boolean." }
        return value
    }

    private fun JSONObject.strictText(name: String, maxLength: Int): String {
        val value = get(name)
        require(value is String) { "Live UI field '$name' must be text." }
        return value.requireSafeText(maxLength)
    }

    private fun JSONObject.strictStringArray(
        name: String,
        expectedSize: Int? = null,
        minSize: Int = 0,
        maxSize: Int = Int.MAX_VALUE,
    ): List<String> {
        val value = get(name)
        require(value is JSONArray) { "Live UI field '$name' must be an array." }
        if (expectedSize != null) {
            require(value.length() == expectedSize) { "Live UI field '$name' has the wrong size." }
        } else {
            require(value.length() in minSize..maxSize) { "Live UI field '$name' has the wrong size." }
        }
        return buildList(value.length()) {
            for (index in 0 until value.length()) {
                val item = value.get(index)
                require(item is String) { "Live UI field '$name' must contain only text." }
                add(item)
            }
        }
    }

    private fun JSONObject.requireExactKeys(vararg expected: String) {
        val actual = buildSet {
            val iterator = keys()
            while (iterator.hasNext()) add(iterator.next())
        }
        require(actual == expected.toSet()) { "Live UI object contains missing or unsupported fields." }
    }

    private fun String.requireSafeText(maxLength: Int): String {
        require(isNotBlank() && length <= maxLength) { "Live UI text has an invalid length." }
        require(this == trim()) { "Live UI text must not have outer whitespace." }
        require(none(Char::isISOControl)) { "Live UI text contains control characters." }
        return this
    }

    private const val SCHEMA_VERSION = 1
    internal const val MAX_SPEC_BYTES = 32 * 1024
    private val REVISION_PATTERN = Regex("[A-Za-z0-9._-]{1,32}")
}
