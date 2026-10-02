package io.github.chamsser.gymvi.data

import org.json.JSONArray
import org.json.JSONObject

/** Remove revoked derived values before even sending a saved continuation to the server. */
internal fun continuationForReferences(value: String?, references: AiReferenceContext): String? {
    if (value == null) return null
    return runCatching {
        require(value.length <= 8_000)
        val raw = JSONObject(value)
        // Old public versions have no continuation. Preserve the unmarked development format.
        if (!raw.has("reference_matched")) return@runCatching value
        val marked = raw.getJSONArray("reference_matched")
        val provenance = raw.optJSONObject("reference_values")
        val kept = JSONArray()
        val keptValues = JSONObject()
        repeat(marked.length()) { index ->
            val field = marked.getString(index)
            require(field in CONTINUATION_REFERENCE_FIELDS)
            val source = provenance?.optJSONObject(field) ?: raw.optJSONObject(field)
            if (source != null && references.stillProvides(field, source)) {
                kept.put(field)
                if (provenance != null) keptValues.put(field, source)
            } else {
                raw.put(field, JSONObject.NULL)
            }
        }
        raw.remove("reference_matched")
        raw.remove("reference_values")
        if (kept.length() > 0) {
            raw.put("reference_matched", kept)
            if (provenance != null) raw.put("reference_values", keptValues)
        }
        raw.toString()
    }.getOrNull()
}

private val CONTINUATION_REFERENCE_FIELDS = setOf("categories", "weekdays", "start_time", "max_price_won",
    "max_distance_meters", "target_groups", "beginner")

private fun AiReferenceContext.stillProvides(field: String, previous: JSONObject): Boolean {
    fun retains(values: Set<String>): Boolean {
        val old = previous.getJSONArray("values")
        return old.length() > 0 && (0 until old.length()).all { old.getString(it) in values }
    }
    return when (field) {
        "categories" -> retains(memory?.categories.orEmpty())
        "weekdays" -> retains(memory?.weekdays.orEmpty())
        "target_groups" -> retains(setOfNotNull(ageBand?.name))
        "start_time" -> memory?.earliestStart == previous.getString("earliest") &&
            memory.latestStart == previous.getString("latest")
        // Saved budgets have no unit, so they never provide a monthly or per-session limit.
        "max_price_won" -> previous.isNull("unit") && memory?.maxPriceWon == previous.getInt("value")
        "max_distance_meters" -> memory?.maxDistanceMeters == previous.getInt("value")
        "beginner" -> memory?.beginner == true || experience == AiExerciseExperience.BEGINNER
        else -> false
    }
}
