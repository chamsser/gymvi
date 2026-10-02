package io.github.chamsser.gymvi.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * One locally saved conversation turn. User turns keep their text; assistant turns keep the
 * validated server response body so cards are rebuilt through the same parser on restore.
 */
data class AiStoredMessage(
    val role: String,
    val text: String? = null,
    val responseBody: String? = null,
    val restartedConversation: Boolean = false,
)

data class AiConversationSnapshot(
    val conversationId: String?,
    val messages: List<AiStoredMessage>,
)

/**
 * Keeps the current AI conversation in app-private storage only while the user allows
 * `대화 기록 저장`. The file is excluded from backup and removed with app data.
 */
class AiConversationSnapshotStore(
    private val file: File,
    private val maxFileBytes: Long = MAX_FILE_BYTES,
) {
    fun load(): AiConversationSnapshot? {
        if (!file.isFile || file.length() > maxFileBytes) return null
        return try {
            val root = JSONObject(file.readText(StandardCharsets.UTF_8))
            if (root.getInt("version") != VERSION) return null
            val messages = root.getJSONArray("messages").let { array ->
                List(array.length()) { index ->
                    val raw = array.getJSONObject(index)
                    AiStoredMessage(
                        role = raw.getString("role"),
                        text = raw.optString("text").takeIf { raw.has("text") },
                        responseBody = raw.optString("response_body").takeIf { raw.has("response_body") },
                        restartedConversation = raw.optBoolean("restarted_conversation", false),
                    )
                }
            }
            AiConversationSnapshot(
                conversationId = root.optString("conversation_id").takeIf { root.has("conversation_id") },
                messages = messages,
            )
        } catch (exception: JSONException) {
            clear()
            null
        } catch (exception: IOException) {
            null
        }
    }

    fun save(snapshot: AiConversationSnapshot) {
        if (snapshot.messages.isEmpty()) {
            clear()
            return
        }
        val root = JSONObject()
            .put("version", VERSION)
            .put("messages", JSONArray().apply {
                snapshot.messages.forEach { message ->
                    put(
                        JSONObject()
                            .put("role", message.role)
                            .apply {
                                message.text?.let { put("text", it) }
                                message.responseBody?.let { put("response_body", it) }
                                if (message.restartedConversation) put("restarted_conversation", true)
                            },
                    )
                }
            })
        snapshot.conversationId?.let { root.put("conversation_id", it) }
        val bytes = root.toString().toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > maxFileBytes) {
            // Never leave an older, now inconsistent conversation behind an oversized one.
            clear()
            return
        }
        val temporary = File(file.parentFile, file.name + ".tmp")
        try {
            file.parentFile?.mkdirs()
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(file)) {
                file.delete()
                temporary.renameTo(file)
            }
        } catch (exception: IOException) {
            temporary.delete()
        }
    }

    fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    private companion object {
        const val VERSION = 1
        const val MAX_FILE_BYTES = 2L * 1024 * 1024
    }
}
