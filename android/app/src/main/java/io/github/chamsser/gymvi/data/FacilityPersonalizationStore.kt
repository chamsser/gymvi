package io.github.chamsser.gymvi.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FacilityPersonalization(
    val recentFacilityIds: List<String> = emptyList(),
    val favoriteFacilityIds: Set<String> = emptySet(),
    /**
     * The name last seen for each of [recentFacilityIds], kept so 내 정보 can list them without asking
     * the server again. A facility whose name was never seen has none. Requests carry the IDs alone.
     */
    val recentFacilityNames: Map<String, String> = emptyMap(),
) {
    /** The recent list newest first, each with its kept name or null. */
    val recentFacilities: List<RecentFacility>
        get() = recentFacilityIds.map { id -> RecentFacility(facilityId = id, name = recentFacilityNames[id]) }
}

data class RecentFacility(val facilityId: String, val name: String?)

/**
 * Favorites and recently viewed facilities on this device. The recent IDs stay a JSON array of
 * strings under their first key, so an older version still reads the list it wrote; the names sit
 * under a key of their own that such a version ignores.
 */
class FacilityPersonalizationStore internal constructor(
    context: Context,
    preferencesName: String,
) {
    constructor(context: Context) : this(context, PREFERENCES_NAME)

    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )

    fun load(): FacilityPersonalization {
        val recentFacilityIds = decodeRecentFacilityIds(preferences.getString(KEY_RECENT, null))
        return FacilityPersonalization(
            recentFacilityIds = recentFacilityIds,
            favoriteFacilityIds = preferences.getStringSet(KEY_FAVORITES, emptySet())
                .orEmpty()
                .filter(String::isNotBlank)
                .toSet(),
            recentFacilityNames = decodeRecentFacilityNames(
                preferences.getString(KEY_RECENT_NAMES, null),
                recentFacilityIds,
            ),
        )
    }

    fun recordRecent(
        current: FacilityPersonalization,
        facilityId: String,
        facilityName: String?,
    ): FacilityPersonalization {
        if (facilityId.isBlank()) return current
        val updated = current.withRecent(facilityId, facilityName)
        save(updated)
        return updated
    }

    fun removeRecent(current: FacilityPersonalization, facilityId: String): FacilityPersonalization {
        val updated = current.withoutRecent(facilityId)
        save(updated)
        return updated
    }

    fun clearRecent(current: FacilityPersonalization): FacilityPersonalization {
        val updated = current.withoutRecents()
        save(updated)
        return updated
    }

    /** Keeps names found in what the app already loaded; writes only when one was missing and found. */
    fun fillRecentNames(
        current: FacilityPersonalization,
        nameOf: (String) -> String?,
    ): FacilityPersonalization {
        val updated = current.withRecentNames(nameOf)
        if (updated != current) save(updated)
        return updated
    }

    fun toggleFavorite(current: FacilityPersonalization, facilityId: String): FacilityPersonalization {
        val favorites = current.favoriteFacilityIds.toMutableSet().apply {
            if (!add(facilityId)) remove(facilityId)
        }
        val updated = current.copy(favoriteFacilityIds = favorites)
        save(updated)
        return updated
    }

    private fun save(state: FacilityPersonalization) {
        preferences.edit()
            .putString(KEY_RECENT, encodeRecentFacilityIds(state))
            .putString(KEY_RECENT_NAMES, encodeRecentFacilityNames(state))
            .putStringSet(KEY_FAVORITES, state.favoriteFacilityIds)
            .apply()
    }

    internal companion object {
        private const val PREFERENCES_NAME = "gymvi_facility_personalization_v1"
        private const val KEY_RECENT = "recent_facility_ids"
        private const val KEY_RECENT_NAMES = "recent_facility_names"
        private const val KEY_FAVORITES = "favorite_facility_ids"
        const val MAX_RECENT_FACILITIES = 12
        const val MAX_FACILITY_NAME_LENGTH = 100
    }
}

/** The recent list as every version reads it: a JSON array of ID strings, newest first. */
internal fun decodeRecentFacilityIds(raw: String?): List<String> = runCatching {
    val array = JSONArray(raw ?: "[]")
    buildList {
        for (index in 0 until array.length()) {
            array.optString(index).takeIf(String::isNotBlank)?.let(::add)
        }
    }.distinct().take(FacilityPersonalizationStore.MAX_RECENT_FACILITIES)
}.getOrDefault(emptyList())

/** The kept names, a JSON object from ID to name. Only listed IDs and names [cleanFacilityName] accepts count. */
internal fun decodeRecentFacilityNames(raw: String?, recentFacilityIds: List<String>): Map<String, String> {
    val names = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyMap()
    return recentFacilityIds.mapNotNull { id ->
        cleanFacilityName(names.opt(id) as? String)?.let { name -> id to name }
    }.toMap()
}

internal fun encodeRecentFacilityIds(state: FacilityPersonalization): String =
    JSONArray(state.recentFacilityIds).toString()

internal fun encodeRecentFacilityNames(state: FacilityPersonalization): String = JSONObject().apply {
    state.recentFacilityIds.forEach { id -> state.recentFacilityNames[id]?.let { name -> put(id, name) } }
}.toString()

/**
 * A name fit to keep and show: trimmed, not empty, at most [FacilityPersonalizationStore.MAX_FACILITY_NAME_LENGTH]
 * characters, and free of control, format and separator characters that could break the row or
 * hide part of what it says. Anything else is not kept, so the row reads 알 수 없음.
 */
internal fun cleanFacilityName(name: String?): String? {
    val trimmed = name?.trim()?.takeIf(String::isNotEmpty) ?: return null
    var index = 0
    var length = 0
    while (index < trimmed.length) {
        val codePoint = trimmed.codePointAt(index)
        if (Character.isISOControl(codePoint) || Character.getType(codePoint) in RejectedNameCharacterTypes) {
            return null
        }
        length += 1
        index += Character.charCount(codePoint)
    }
    return trimmed.takeIf { length <= FacilityPersonalizationStore.MAX_FACILITY_NAME_LENGTH }
}

/** Format characters include the direction overrides and zero-width marks; a lone surrogate is broken text. */
private val RejectedNameCharacterTypes = setOf(
    Character.FORMAT.toInt(),
    Character.LINE_SEPARATOR.toInt(),
    Character.PARAGRAPH_SEPARATOR.toInt(),
    Character.SURROGATE.toInt(),
)

/** Puts [facilityId] first and keeps the newest; a valid [facilityName] replaces the kept one. */
internal fun FacilityPersonalization.withRecent(facilityId: String, facilityName: String?): FacilityPersonalization {
    if (facilityId.isBlank()) return this
    val ids = buildList {
        add(facilityId)
        addAll(recentFacilityIds.filterNot { it == facilityId })
    }.take(FacilityPersonalizationStore.MAX_RECENT_FACILITIES)
    val name = cleanFacilityName(facilityName)
    val names = if (name != null) recentFacilityNames + (facilityId to name) else recentFacilityNames
    return copy(recentFacilityIds = ids, recentFacilityNames = names.filterKeys { it in ids })
}

internal fun FacilityPersonalization.withoutRecent(facilityId: String): FacilityPersonalization = copy(
    recentFacilityIds = recentFacilityIds.filterNot { it == facilityId },
    recentFacilityNames = recentFacilityNames - facilityId,
)

internal fun FacilityPersonalization.withoutRecents(): FacilityPersonalization =
    copy(recentFacilityIds = emptyList(), recentFacilityNames = emptyMap())

/** Adds names only for listed facilities that have none; a kept name is never replaced here. */
internal fun FacilityPersonalization.withRecentNames(nameOf: (String) -> String?): FacilityPersonalization {
    val found = recentFacilityIds
        .filterNot { it in recentFacilityNames }
        .mapNotNull { id -> cleanFacilityName(nameOf(id))?.let { name -> id to name } }
    return if (found.isEmpty()) this else copy(recentFacilityNames = recentFacilityNames + found)
}
