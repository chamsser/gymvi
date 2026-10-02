package io.github.chamsser.gymvi.ui

import android.os.Handler
import android.os.Looper
import io.github.chamsser.gymvi.data.AiClientAction
import io.github.chamsser.gymvi.data.AiConversationSnapshot
import io.github.chamsser.gymvi.data.AiConversationSnapshotStore
import io.github.chamsser.gymvi.data.AiRequestOrigin
import io.github.chamsser.gymvi.data.AiStoredMessage
import io.github.chamsser.gymvi.data.AiTurnApiClient
import io.github.chamsser.gymvi.data.AiTurnCard
import io.github.chamsser.gymvi.data.AiTurnFetchResult
import io.github.chamsser.gymvi.data.AiTurnRequestContext
import io.github.chamsser.gymvi.data.AiTurnResponse
import io.github.chamsser.gymvi.data.ExerciseContentItem
import java.io.Closeable
import java.util.concurrent.Executors
import io.github.chamsser.gymvi.data.*
import java.util.UUID
import java.security.MessageDigest

enum class AiMessageRole { USER, ASSISTANT }

data class AiConversationMessage(
    val id: Long,
    val role: AiMessageRole,
    val text: String,
    val cards: List<AiTurnCard> = emptyList(),
    val exerciseContents: List<ExerciseContentItem> = emptyList(),
    val fallback: Boolean = false,
    val datasetVersion: String? = null,
    val asOf: String? = null,
    val responseBody: String? = null,
    /** The server conversation had expired, so this reply saw only the latest question. */
    val restartedConversation: Boolean = false,
    val isStreaming: Boolean = false,
)

data class AiClientActionEvent(
    val id: Long,
    val actions: List<AiClientAction>,
    val cards: List<AiTurnCard>,
)

data class AiConversationUiState(
    val messages: List<AiConversationMessage> = emptyList(),
    val conversationId: String? = null,
    val isLoading: Boolean = false,
    val errorCode: String? = null,
    val pendingClientAction: AiClientActionEvent? = null,
    val localConversationId: String? = null,
    val title: String? = null,
    val library: AiLibraryData = AiLibraryData(),
    val libraryLoaded: Boolean = true,
    val storageError: String? = null,
)

/**
 * Holds the current AI conversation for the process lifetime so rotation and leaving the AI
 * screen keep the same `conversation_id`. A saved library is loaded after process restart;
 * opening a saved conversation explicitly restores its turns and structured continuation.
 */
class AiConversationController(
    apiBaseUrl: String,
    private val snapshotStore: AiConversationSnapshotStore? = null,
    private val libraryStore: AiLibraryStore? = null,
    private val transport: AiConversationTransport? = null,
) : Closeable {
    private val client = AiTurnApiClient(apiBaseUrl)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gymvi-ai-api").apply { isDaemon = true }
    }

    // Local saves and deletes keep their order but never wait behind a slow AI request.
    private val storageExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "gymvi-ai-storage").apply { isDaemon = true }
    }
    private val lock = Any()
    private var state = AiConversationUiState(libraryLoaded = libraryStore == null)
    private var observer: ((AiConversationUiState) -> Unit)? = null
    private var generation = 0
    private var nextEventId = 1L
    private var conversationOrigin: AiRequestOrigin? = null
    private var persistHistory: Boolean? = null
    private var closed = false
    private var preferences = AiPreferences()
    private var continuation: String? = null
    private var referenceKey: String? = null
    private var notificationPending = false
    private var libraryWritable = libraryStore == null
    private val unsavedMessageIds = mutableSetOf<Long>()
    private var resumeSavedContext = false
    private var memoryRevision = 0

    init {
        libraryStore?.let { store -> storageExecutor.execute {
            val loaded = runCatching { store.load() }
            synchronized(lock) {
                if (!closed) {
                    libraryWritable = loaded.isSuccess
                    state = state.copy(library = loaded.getOrDefault(AiLibraryData()),
                        libraryLoaded = true, storageError = if (loaded.isFailure) "AI_LIBRARY_READ_FAILED" else null)
                    // Opening is explicit. A new process starts with a new chat and the entire library available.
                    notifyObserverLocked()
                }
            }
        } }
    }

    val currentState: AiConversationUiState
        get() = synchronized(lock) { state }

    fun observe(observer: ((AiConversationUiState) -> Unit)?) {
        val snapshot = synchronized(lock) {
            if (closed) return
            this.observer = observer
            state
        }
        observer?.let { callback -> mainHandler.post { callback(snapshot) } }
    }

    fun submit(
        message: String,
        context: AiTurnRequestContext,
        origin: AiRequestOrigin?,
    ) {
        val normalizedMessage = message.trim()
        if (normalizedMessage.isEmpty() || normalizedMessage.length > AI_PROMPT_MAX_LENGTH) return
        val requestConversationId: String?
        val requestContext: AiTurnRequestContext?
        val requestOrigin: AiRequestOrigin?
        val permittedOrigin: AiRequestOrigin?
        val requestGeneration: Int
        val replyMessageId: Long
        val references: AiReferenceContext
        val requestContinuation: String?
        val saveThisTurn: Boolean
        val learnThisTurn: Boolean
        val requestMemoryRevision: Int
        synchronized(lock) {
            if (closed || state.isLoading || !state.libraryLoaded) return
            saveThisTurn = persistHistory == true
            learnThisTurn = preferences.useAiMemory
            requestMemoryRevision = memoryRevision
            if (saveThisTurn && resumeSavedContext) {
                // An unsaved segment must not supply a later saved title or server context.
                // The visible transcript stays, while the server resumes the last saved conditions.
                val saved = state.library.conversations.find { it.id == state.localConversationId }
                continuation = saved?.continuation
                state = state.copy(conversationId = null, title = saved?.title)
                conversationOrigin = null
                resumeSavedContext = false
            }
            permittedOrigin = origin.takeIf { preferences.useApproximateRegion }
            references = referencesLocked()
            val newReferenceKey = referenceKey(references, permittedOrigin != null)
            if (referenceKey != newReferenceKey) {
                conversationOrigin = null
            }
            referenceKey = newReferenceKey
            requestContinuation = continuationForReferences(continuation, references)
            generation += 1
            requestGeneration = generation
            requestConversationId = state.conversationId
            requestContext = aiContextForTurn(requestConversationId, context)
            requestOrigin = aiOriginForTurn(requestConversationId, permittedOrigin, conversationOrigin)
            if (requestConversationId == null) conversationOrigin = requestOrigin
            state = state.copy(
                messages = (state.messages + AiConversationMessage(
                    id = nextEventId++,
                    role = AiMessageRole.USER,
                    text = normalizedMessage,
                )),
                conversationId = requestConversationId,
                isLoading = true,
                errorCode = null,
                pendingClientAction = null,
                localConversationId = state.localConversationId ?: UUID.randomUUID().toString(),
                title = state.title ?: if (saveThisTurn) normalizedMessage.take(40) else "새 대화",
            )
            replyMessageId = nextEventId++
            if (!saveThisTurn) {
                unsavedMessageIds += state.messages.last().id
                resumeSavedContext = true
            }
            persistLocked()
            notifyObserverLocked()
        }
        executor.execute {
            synchronized(lock) { if (closed || requestGeneration != generation) return@execute }
            val onDelta: (String) -> Unit = { delta ->
                synchronized(lock) {
                    if (!closed && requestGeneration == generation) {
                        state = state.withReplyDelta(replyMessageId, delta)
                        notifyObserverLocked()
                    }
                }
            }
            fun turn(id: String?, turnContext: AiTurnRequestContext?, turnOrigin: AiRequestOrigin?) =
                transport?.turn(id, normalizedMessage, turnContext, turnOrigin,
                    references.copy(continuation = requestContinuation), onDelta)
                    ?: client.turnStreaming(id, normalizedMessage, turnContext, turnOrigin, onDelta,
                        references.copy(continuation = requestContinuation))
            var result = turn(requestConversationId, requestContext, requestOrigin)
            var restarted = false
            if (shouldRestartExpiredConversation(requestConversationId, result)) {
                // The server keeps conversation state for a limited time. Keep the visible turns
                // and continue in a fresh server conversation with explicit conditions only.
                // Older snapshots without a continuation still disclose their context loss.
                synchronized(lock) {
                    if (closed || requestGeneration != generation) return@execute
                    conversationOrigin = permittedOrigin
                    state = state.withoutPartialReply(replyMessageId)
                }
                result = turn(null, context, permittedOrigin)
                restarted = requestContinuation == null
            }
            synchronized(lock) {
                if (closed || requestGeneration != generation) return@execute
                // Partial text is never evidence for cards or navigation. Only a complete,
                // parsed response is persisted, and an interrupted partial is removed.
                state = state.withoutPartialReply(replyMessageId)
                state = when (result) {
                    is AiTurnFetchResult.Success -> {
                        val response = result.response
                        state.copy(
                            messages = (state.messages + AiConversationMessage(
                                id = replyMessageId,
                                role = AiMessageRole.ASSISTANT,
                                text = response.reply,
                                cards = response.cards,
                                exerciseContents = response.exerciseContents,
                                fallback = response.fallback,
                                datasetVersion = response.datasetVersion,
                                asOf = response.asOf,
                                responseBody = result.responseBody,
                                restartedConversation = restarted,
                            )),
                            conversationId = response.conversationId,
                            isLoading = false,
                            errorCode = null,
                            pendingClientAction = response.clientActions.takeIf(List<AiClientAction>::isNotEmpty)
                                ?.let { actions -> AiClientActionEvent(nextEventId++, actions, response.cards) },
                        )
                    }
                    is AiTurnFetchResult.ApiFailure -> state.copy(
                        isLoading = false,
                        errorCode = result.code,
                    )
                    is AiTurnFetchResult.NetworkFailure -> state.copy(
                        isLoading = false,
                        errorCode = "NETWORK_UNAVAILABLE",
                    )
                    is AiTurnFetchResult.InvalidResponse -> state.copy(
                        isLoading = false,
                        errorCode = "INVALID_AI_RESPONSE",
                    )
                }
                if (result is AiTurnFetchResult.Success) {
                    val saveReply = saveThisTurn && persistHistory == true
                    if (!saveReply) {
                        unsavedMessageIds += replyMessageId
                        resumeSavedContext = true
                    }
                    continuation = result.response.continuation
                    val saved = state.library.conversations.find { it.id == state.localConversationId }
                    if (saveReply && saved?.titleIsManual != true && saved?.titleIsGenerated != true) {
                        result.response.conversationTitle?.let { state = state.copy(title = it) }
                    }
                    val facts = result.response.memoryFacts
                    if (learnThisTurn && preferences.useAiMemory && requestMemoryRevision == memoryRevision &&
                        facts != null && !facts.isEmpty) {
                        val id = state.localConversationId!!
                        val previous = state.library.memories.firstOrNull { it.id == id }
                        val memoryTitle = if (saveReply) state.title.orEmpty() else saved?.title ?: "새 대화"
                        val incoming = AiMemoryEntry(id, memoryTitle, facts, System.currentTimeMillis())
                        val merged = mergeAiMemories(listOfNotNull(incoming, previous)) ?: facts
                        val entry = incoming.copy(facts = merged.copy(
                            categories = facts.categories + previous?.facts?.categories.orEmpty(),
                            weekdays = facts.weekdays + previous?.facts?.weekdays.orEmpty(),
                            beginner = facts.beginner || previous?.facts?.beginner == true,
                        ))
                        state = state.copy(library = state.library.copy(memories =
                            (state.library.memories.filterNot { it.id == id } + entry).takeLast(100)))
                    }
                    persistLocked(generatedTitle = saveReply && result.response.conversationTitle != null)
                }
                notifyObserverLocked()
            }
        }
    }

    fun newConversation() {
        client.cancelStreaming()
        synchronized(lock) {
            if (closed) return
            generation += 1
            conversationOrigin = null
            continuation = null
            referenceKey = null
            unsavedMessageIds.clear()
            resumeSavedContext = false
            state = AiConversationUiState(library = state.library, libraryLoaded = state.libraryLoaded,
                storageError = state.storageError)
            notifyObserverLocked()
        }
    }

    /**
     * Library mode only controls future turn persistence and preserves existing records.
     * The legacy snapshot-only path below retains its old compatibility behavior.
     */
    fun setHistoryPersistence(enabled: Boolean) {
        if (libraryStore != null) {
            synchronized(lock) {
                if (closed || persistHistory == enabled) return
                persistHistory = enabled
                // Neither disabling nor enabling retroactively persists an unsaved conversation.
            }
            return
        }
        val store = snapshotStore ?: return
        synchronized(lock) {
            if (closed || persistHistory == enabled) return
            persistHistory = enabled
            if (!enabled) {
                storageExecutor.execute { store.clear() }
                return
            }
            if (state.messages.isNotEmpty()) {
                persistLocked()
                return
            }
            val restoreGeneration = generation
            storageExecutor.execute {
                val restored = store.load()
                    ?.let { snapshot -> restoreAiConversation(snapshot, client::parseSuccess) }
                    ?: return@execute
                synchronized(lock) {
                    if (closed || restoreGeneration != generation || state.messages.isNotEmpty()) {
                        return@execute
                    }
                    nextEventId = (restored.messages.maxOfOrNull(AiConversationMessage::id) ?: 0L) + 1
                    state = restored
                    notifyObserverLocked()
                }
            }
        }
    }

    fun consumeClientAction(eventId: Long) {
        synchronized(lock) {
            if (closed || state.pendingClientAction?.id != eventId) return
            state = state.copy(pendingClientAction = null)
            notifyObserverLocked()
        }
    }

    private fun persistLocked(generatedTitle: Boolean = false) {
        if (libraryStore != null) {
            if (persistHistory == true && state.messages.isNotEmpty()) {
                val id = state.localConversationId ?: return
                val existing = state.library.conversations.find { it.id == id }
                val item = AiSavedConversation(id, state.title ?: "새 대화",
                    titleIsManual = existing?.titleIsManual ?: false,
                    titleIsGenerated = existing?.titleIsGenerated == true || generatedTitle,
                    archived = existing?.archived ?: false, updatedAt = System.currentTimeMillis(),
                    snapshot = state.copy(messages = state.messages.filterNot { it.id in unsavedMessageIds }).toAiConversationSnapshot(),
                    continuation = if (unsavedMessageIds.isEmpty()) continuation else null, referenceKey = referenceKey)
                if (item.snapshot.messages.isNotEmpty()) state = state.copy(library = state.library.copy(currentId = id,
                    conversations = state.library.conversations.filterNot { it.id == id } + item))
            }
            saveLibraryLocked()
            return
        }
        val store = snapshotStore ?: return
        if (persistHistory != true) return
        val snapshot = state.toAiConversationSnapshot()
        storageExecutor.execute { store.save(snapshot) }
    }

    private fun notifyObserverLocked() {
        // Streaming deltas can arrive faster than a frame. Render the newest state once per frame.
        if (notificationPending) return
        notificationPending = true
        mainHandler.postDelayed({
            val update = synchronized(lock) {
                notificationPending = false
                if (closed) null else observer?.let { it to state }
            }
            update?.let { (callback, snapshot) -> callback(snapshot) }
        }, 16L)
    }

    fun setPreferences(value: AiPreferences) {
        synchronized(lock) {
            val before = referencesLocked()
            val locationChanged = preferences.useApproximateRegion != value.useApproximateRegion
            if (preferences.useAiMemory != value.useAiMemory) memoryRevision++
            preferences = value
            if (before != referencesLocked() || locationChanged) invalidateReferencesLocked()
        }
        setHistoryPersistence(value.saveConversationHistory)
    }

    fun openConversation(id: String) = synchronized(lock) {
        // Stored conversations remain intact but inactive while history saving is off.
        if (closed || persistHistory != true) return@synchronized
        val item = state.library.conversations.find { it.id == id } ?: return@synchronized
        val restored = restoreAiConversation(item.snapshot, client::parseSuccess) ?: return@synchronized
        generation++
        client.cancelStreaming()
        unsavedMessageIds.clear()
        resumeSavedContext = false
        continuation = item.continuation
        referenceKey = item.referenceKey
        conversationOrigin = null
        nextEventId = maxOf(nextEventId, (restored.messages.maxOfOrNull { it.id } ?: 0) + 1)
        state = restored.copy(localConversationId = id, title = item.title, library = state.library)
        notifyObserverLocked()
    }

    fun renameConversation(id: String, title: String) = synchronized(lock) {
        if (!canModifyLibraryLocked()) return@synchronized
        val normalized = title.trim()
        if (normalized.isEmpty() || normalized.length > 80 || normalized.any(Char::isISOControl)) return@synchronized
        state = state.copy(title = if (state.localConversationId == id) normalized else state.title,
            library = state.library.copy(conversations = state.library.conversations.map {
                if (it.id == id) it.copy(title = normalized, titleIsManual = true) else it
            }, memories = state.library.memories.map { if (it.id == id) it.copy(title = normalized) else it }))
        saveLibraryLocked()
        notifyObserverLocked()
    }

    fun archiveConversation(id: String, archived: Boolean) = synchronized(lock) {
        if (!canModifyLibraryLocked()) return@synchronized
        state = state.copy(library = state.library.copy(conversations = state.library.conversations.map {
            if (it.id == id) it.copy(archived = archived) else it
        }))
        saveLibraryLocked()
        notifyObserverLocked()
    }

    fun deleteConversation(id: String) = synchronized(lock) {
        if (!canModifyLibraryLocked()) return@synchronized
        if (state.library.memories.any { it.id == id }) memoryRevision++
        val before = referencesLocked()
        val library = state.library.copy(conversations = state.library.conversations.filterNot { it.id == id },
            memories = state.library.memories.filterNot { it.id == id },
            currentId = state.library.currentId?.takeUnless { it == id })
        state = state.copy(library = library)
        if (state.localConversationId == id) newConversation()
        else if (before != referencesLocked()) invalidateReferencesLocked()
        saveLibraryLocked()
        notifyObserverLocked()
    }

    fun updateProfile(profile: AiLocalProfile) = synchronized(lock) {
        if (!canModifyLibraryLocked()) return@synchronized
        val before = referencesLocked()
        state = state.copy(library = state.library.copy(profile = profile))
        if (before != referencesLocked()) invalidateReferencesLocked()
        saveLibraryLocked()
        notifyObserverLocked()
    }

    fun updateMemory(id: String, facts: AiMemoryFacts) = synchronized(lock) {
        if (!canModifyLibraryLocked()) return@synchronized
        memoryRevision++
        val before = referencesLocked()
        // Apply the same allowlist used when reading a response, including edited local input.
        val validated = aiMemoryFactsFromJson(facts.toJson())
        state = state.copy(library = state.library.copy(memories = state.library.memories.map {
            if (it.id == id) it.copy(facts = validated, updatedAt = System.currentTimeMillis()) else it
        }))
        if (before != referencesLocked()) invalidateReferencesLocked()
        saveLibraryLocked()
        notifyObserverLocked()
    }

    fun deleteMemory(id: String) = synchronized(lock) {
        if (!canModifyLibraryLocked()) return@synchronized
        memoryRevision++
        val before = referencesLocked()
        state = state.copy(library = state.library.copy(memories = state.library.memories.filterNot { it.id == id }))
        if (before != referencesLocked()) invalidateReferencesLocked()
        saveLibraryLocked()
        notifyObserverLocked()
    }

    private fun canModifyLibraryLocked() = !closed && state.libraryLoaded && libraryWritable

    private fun invalidateReferencesLocked() {
        generation++
        client.cancelStreaming()
        state = state.copy(isLoading = false, pendingClientAction = null,
            errorCode = if (state.isLoading) "AI_REFERENCES_CHANGED" else state.errorCode,
            messages = state.messages.filterNot { it.isStreaming })
        referenceKey = null
        conversationOrigin = null
        notifyObserverLocked()
    }

    private fun referencesLocked(): AiReferenceContext {
        val profile = state.library.profile.takeIf { preferences.useBodyInformation }
        val memories = state.library.memories.filter { it.id != state.localConversationId }
            .sortedByDescending { it.updatedAt }.takeIf { preferences.useAiMemory }.orEmpty()
        return AiReferenceContext(memory = mergeAiMemories(memories), ageBand = profile?.ageBand,
            experience = profile?.experience, goal = profile?.goal)
    }

    private fun saveLibraryLocked() {
        val store = libraryStore ?: return
        if (!state.libraryLoaded || !libraryWritable) return
        val data = state.library
        storageExecutor.execute {
            val failed = runCatching { store.save(data) }.isFailure
            synchronized(lock) {
                if (!closed) {
                    state = state.copy(storageError = if (failed) "AI_LIBRARY_SAVE_FAILED" else null)
                    notifyObserverLocked()
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            observer = null
        }
        client.cancelStreaming()
        executor.shutdownNow()
        // Let queued saves and deletes finish so a cleared conversation is not restored later.
        storageExecutor.shutdown()
    }

}

fun interface AiConversationTransport {
    fun turn(id: String?, message: String, context: AiTurnRequestContext?, origin: AiRequestOrigin?,
        references: AiReferenceContext, onDelta: (String) -> Unit): AiTurnFetchResult
}

internal fun mergeAiMemories(memories: List<AiMemoryEntry>): AiMemoryFacts? {
    if (memories.isEmpty()) return null
    val facts = memories.map { it.facts }
    val time = facts.firstOrNull { it.earliestStart != null }
    return AiMemoryFacts(categories = facts.firstOrNull { it.categories.isNotEmpty() }?.categories.orEmpty(),
        weekdays = facts.firstOrNull { it.weekdays.isNotEmpty() }?.weekdays.orEmpty(),
        earliestStart = time?.earliestStart, latestStart = time?.latestStart,
        maxPriceWon = facts.firstNotNullOfOrNull { it.maxPriceWon },
        maxDistanceMeters = facts.firstNotNullOfOrNull { it.maxDistanceMeters }, beginner = facts.first().beginner)
}

private fun referenceKey(references: AiReferenceContext, location: Boolean): String =
    MessageDigest.getInstance("SHA-256").digest((references.toString() + ":" + location).toByteArray())
        .joinToString("") { "%02x".format(it) }

internal fun AiConversationUiState.withReplyDelta(messageId: Long, delta: String): AiConversationUiState {
    if (delta.isEmpty()) return this
    val existing = messages.firstOrNull { it.id == messageId && it.isStreaming }
    val updated = existing?.copy(text = existing.text + delta)
        ?: AiConversationMessage(messageId, AiMessageRole.ASSISTANT, delta, isStreaming = true)
    return copy(messages = if (existing == null) messages + updated
        else messages.map { if (it.id == messageId) updated else it })
}

internal fun AiConversationUiState.withoutPartialReply(messageId: Long): AiConversationUiState =
    copy(messages = messages.filterNot { it.id == messageId && it.isStreaming })

internal fun aiContextForTurn(
    conversationId: String?,
    context: AiTurnRequestContext,
): AiTurnRequestContext? = context.takeIf { conversationId == null }

/**
 * The origin is fixed when a conversation starts so map focus actions cannot move it. It is
 * never sent once location reference is off, and a restored conversation without a fixed
 * origin uses the current one.
 */
internal fun aiOriginForTurn(
    conversationId: String?,
    currentOrigin: AiRequestOrigin?,
    conversationOrigin: AiRequestOrigin?,
): AiRequestOrigin? = when {
    currentOrigin == null -> null
    conversationId == null -> currentOrigin
    else -> conversationOrigin ?: currentOrigin
}

internal fun shouldRestartExpiredConversation(
    requestConversationId: String?,
    result: AiTurnFetchResult,
): Boolean = requestConversationId != null &&
    result is AiTurnFetchResult.ApiFailure &&
    result.code == EXPIRED_CONVERSATION_CODE

private const val EXPIRED_CONVERSATION_CODE = "VALIDATION_AI_CONVERSATION_ID"

internal fun AiConversationUiState.toAiConversationSnapshot(): AiConversationSnapshot =
    AiConversationSnapshot(
        conversationId = conversationId,
        messages = messages.mapNotNull { message ->
            when (message.role) {
                AiMessageRole.USER -> AiStoredMessage(role = message.role.name, text = message.text)
                AiMessageRole.ASSISTANT -> message.responseBody?.let { body ->
                    AiStoredMessage(
                        role = message.role.name,
                        responseBody = body,
                        restartedConversation = message.restartedConversation,
                    )
                }
            }
        },
    )

/** Rebuilds saved turns through the response parser; a turn that no longer parses is dropped. */
internal fun restoreAiConversation(
    snapshot: AiConversationSnapshot,
    parseResponse: (String) -> AiTurnResponse,
): AiConversationUiState? {
    var lastVerifiedConversationId: String? = null
    val messages = snapshot.messages.mapIndexedNotNull { index, stored ->
        val id = index + 1L
        when (stored.role) {
            AiMessageRole.USER.name -> stored.text?.takeIf(String::isNotBlank)?.let { text ->
                AiConversationMessage(id = id, role = AiMessageRole.USER, text = text)
            }
            AiMessageRole.ASSISTANT.name -> stored.responseBody?.let { body ->
                runCatching { parseResponse(body) }.getOrNull()?.let { response ->
                    lastVerifiedConversationId = response.conversationId
                    AiConversationMessage(
                        id = id,
                        role = AiMessageRole.ASSISTANT,
                        text = response.reply,
                        cards = response.cards,
                        exerciseContents = response.exerciseContents,
                        fallback = response.fallback,
                        datasetVersion = response.datasetVersion,
                        asOf = response.asOf,
                        responseBody = body,
                        restartedConversation = stored.restartedConversation,
                    )
                }
            }
            else -> null
        }
    }
    if (messages.isEmpty()) return null
    // The file is app-private but still untrusted input: only reuse an id that the last
    // validated server response confirms, otherwise the next turn starts a fresh conversation.
    val conversationId = snapshot.conversationId?.takeIf { it == lastVerifiedConversationId }
    return AiConversationUiState(messages = messages, conversationId = conversationId)
}
