package io.github.chamsser.gymvi.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalTime
import java.util.UUID

data class AiSavedConversation(
    val id: String,
    val title: String,
    val titleIsManual: Boolean = false,
    val titleIsGenerated: Boolean = false,
    val archived: Boolean = false,
    val updatedAt: Long,
    val snapshot: AiConversationSnapshot,
    val continuation: String? = null,
    val referenceKey: String? = null,
)

data class AiLibraryData(
    val conversations: List<AiSavedConversation> = emptyList(),
    val currentId: String? = null,
    val profile: AiLocalProfile = AiLocalProfile(),
    val memories: List<AiMemoryEntry> = emptyList(),
    val migratedLegacyId: String? = null,
)

/** App-private, no-backup data. Read failures never remove or replace the user's file. */
class AiLibraryStore(private val file: File, private val legacyFile: File? = null,
    private val maxBytes: Long = 32L * 1024 * 1024) {
    fun load(): AiLibraryData {
        if (!file.exists()) return migrateLegacy()
        require(file.length() <= maxBytes) { "AI_LIBRARY_TOO_LARGE" }
        return decode(JSONObject(file.readText(Charsets.UTF_8)))
    }

    fun save(data: AiLibraryData) {
        val bytes = encode(data).toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= maxBytes) { "AI_LIBRARY_TOO_LARGE" }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temporary).use { it.write(bytes); it.fd.sync() }
        // Both files are on the same filesystem. Never delete the old file as a fallback.
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING)
        if (data.migratedLegacyId != null && data.conversations.none { it.id == data.migratedLegacyId }) {
            // Explicit deletion of the imported conversation also removes its rollback copy.
            legacyFile?.let { Files.deleteIfExists(it.toPath()) }
        }
    }

    private fun migrateLegacy(): AiLibraryData {
        val legacy = legacyFile?.takeIf(File::isFile) ?: return AiLibraryData()
        require(legacy.length() <= maxBytes) { "AI_LIBRARY_TOO_LARGE" }
        val raw = JSONObject(legacy.readText(Charsets.UTF_8))
        require(raw.getInt("version") == 1)
        val snapshot = readSnapshot(raw)
        if (snapshot.messages.isEmpty()) return AiLibraryData()
        val id = UUID.randomUUID().toString()
        val title = snapshot.messages.firstOrNull { it.role == "USER" }?.text.orEmpty()
            .trim().take(40).ifBlank { "이전 대화" }
        return AiLibraryData(listOf(AiSavedConversation(id, title, updatedAt = legacy.lastModified(),
            snapshot = snapshot)), currentId = id, migratedLegacyId = id).also(::save)
        // The old file remains available for rollback; v2 existence prevents duplicate import.
    }

    private fun encode(data: AiLibraryData): JSONObject = JSONObject()
        .put("version", 2).put("current_id", data.currentId)
        .put("migrated_legacy_id", data.migratedLegacyId)
        .put("conversations", JSONArray().apply { data.conversations.forEach { item ->
            put(JSONObject().put("id", item.id).put("title", item.title)
                .put("manual", item.titleIsManual).put("generated", item.titleIsGenerated)
                .put("archived", item.archived).put("updated_at", item.updatedAt)
                .put("snapshot", writeSnapshot(item.snapshot)).put("continuation", item.continuation)
                .put("reference_key", item.referenceKey))
        } })
        .put("profile", JSONObject().put("age_band", data.profile.ageBand?.name)
            .put("height_cm", data.profile.heightCm).put("weight_kg", data.profile.weightKg)
            .put("experience", data.profile.experience?.name).put("goal", data.profile.goal?.name))
        .put("memories", JSONArray().apply { data.memories.forEach { item ->
            put(JSONObject().put("id", item.id).put("title", item.title)
                .put("updated_at", item.updatedAt).put("facts", item.facts.toJson()))
        } })

    private fun decode(raw: JSONObject): AiLibraryData {
        require(raw.getInt("version") == 2)
        val conversations = raw.getJSONArray("conversations").objects { item ->
            AiSavedConversation(id = item.getString("id").also { UUID.fromString(it) },
                title = item.getString("title").also { require(it.isNotBlank() && it.length <= 80) },
                titleIsManual = item.getBoolean("manual"), titleIsGenerated = item.getBoolean("generated"),
                archived = item.getBoolean("archived"), updatedAt = item.getLong("updated_at"),
                snapshot = readSnapshot(item.getJSONObject("snapshot")),
                continuation = item.textOrNull("continuation"), referenceKey = item.textOrNull("reference_key"))
        }
        require(conversations.distinctBy { it.id }.size == conversations.size)
        val profile = raw.getJSONObject("profile")
        val memories = raw.getJSONArray("memories").objects { item ->
            AiMemoryEntry(item.getString("id"), item.getString("title"),
                aiMemoryFactsFromJson(item.getJSONObject("facts")), item.getLong("updated_at"))
        }
        require(memories.size <= 100 && memories.distinctBy { it.id }.size == memories.size)
        return AiLibraryData(conversations, raw.textOrNull("current_id")?.takeIf { id -> conversations.any { it.id == id } },
            AiLocalProfile(profile.textOrNull("age_band")?.let(AiAgeBand::valueOf),
                if (profile.has("height_cm")) profile.getInt("height_cm") else null,
                if (profile.has("weight_kg")) profile.getDouble("weight_kg") else null,
                profile.textOrNull("experience")?.let(AiExerciseExperience::valueOf),
                profile.textOrNull("goal")?.let(AiExerciseGoal::valueOf)), memories, raw.textOrNull("migrated_legacy_id"))
    }
}

internal fun AiMemoryFacts.toJson(): JSONObject = JSONObject()
    .put("categories", JSONArray(categories.sorted())).put("weekdays", JSONArray(weekdays.sorted()))
    .put("earliest_start", earliestStart).put("latest_start", latestStart)
    .put("max_price_won", maxPriceWon).put("max_distance_meters", maxDistanceMeters).put("beginner", beginner)

internal fun aiMemoryFactsFromJson(raw: JSONObject): AiMemoryFacts {
    val categories = raw.getJSONArray("categories").strings().toSet()
    val weekdays = raw.getJSONArray("weekdays").strings().toSet()
    require(categories.all { it in MEMORY_CATEGORIES } && weekdays.all { it in MEMORY_WEEKDAYS })
    fun time(key: String) = raw.textOrNull(key)?.also {
        require(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(it))
    }
    val earliest = time("earliest_start")
    val latest = time("latest_start")
    require((earliest == null) == (latest == null))
    require(earliest == null || LocalTime.parse(earliest) <= LocalTime.parse(latest))
    val price = if (raw.has("max_price_won") && !raw.isNull("max_price_won")) raw.getInt("max_price_won") else null
    val distance = if (raw.has("max_distance_meters") && !raw.isNull("max_distance_meters")) raw.getInt("max_distance_meters") else null
    require(price == null || price in 0..10_000_000)
    require(distance == null || distance in 100..100_000)
    return AiMemoryFacts(categories, weekdays, earliest, latest, price, distance, raw.optBoolean("beginner", false))
}

internal val MEMORY_CATEGORIES = setOf("SWIMMING", "FITNESS", "YOGA_PILATES", "DANCE", "TENNIS", "BADMINTON",
    "TABLE_TENNIS", "GOLF", "SQUASH", "SKATING", "TEAM_BALL", "MARTIAL_ARTS", "CLIMBING")
internal val MEMORY_WEEKDAYS = setOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
internal fun JSONObject.textOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
private fun <T> JSONArray.objects(read: (JSONObject) -> T): List<T> = List(length()) { read(getJSONObject(it)) }
private fun readSnapshot(raw: JSONObject) = AiConversationSnapshot(raw.textOrNull("conversation_id"),
    raw.getJSONArray("messages").objects { item ->
        AiStoredMessage(item.getString("role"), item.textOrNull("text"), item.textOrNull("response_body"),
            item.optBoolean("restarted_conversation", false))
    })
private fun writeSnapshot(snapshot: AiConversationSnapshot) = JSONObject().put("conversation_id", snapshot.conversationId)
    .put("messages", JSONArray().apply { snapshot.messages.forEach { item ->
        put(JSONObject().put("role", item.role).put("text", item.text).put("response_body", item.responseBody)
            .put("restarted_conversation", item.restartedConversation))
    } })
