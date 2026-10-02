package io.github.chamsser.gymvi.review

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.LocalServerSocket
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsPropertyKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Debug-only, read-only UI inspection bridge reached through an ADB localabstract forward. */
class ReviewInspectorProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val appContext = context?.applicationContext ?: return false
        ReviewInspectorServer.start(appContext.filesDir)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}

private object ReviewInspectorServer {
    const val SOCKET_NAME = "gymvi-review-inspector"
    private const val MAX_REQUEST_BYTES = 16 * 1024
    private val started = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gymvi-review-inspector").apply { isDaemon = true }
    }

    fun start(filesDir: File) {
        if (!started.compareAndSet(false, true)) return
        val token = ByteArray(32).also(SecureRandom()::nextBytes)
            .let { Base64.encodeToString(it, Base64.NO_WRAP) }
        File(filesDir, "review-inspector-token").writeText(token, Charsets.UTF_8)
        executor.execute { serve(token) }
    }

    private fun serve(token: String) {
        val server = LocalServerSocket(SOCKET_NAME)
        while (true) {
            val socket = server.accept()
            try {
                val requestBytes = readBoundedLine(socket.inputStream.readBytesBounded())
                val request = JSONObject(requestBytes.toString(Charsets.UTF_8))
                val response = when {
                    request.optString("token") != token -> errorResponse("AUTHENTICATION_FAILED")
                    request.optString("action") != "tree" -> errorResponse("UNSUPPORTED_ACTION")
                    else -> inspectOnMainThread()
                }
                socket.outputStream.write((response.toString() + "\n").toByteArray(Charsets.UTF_8))
                socket.outputStream.flush()
            } catch (_: Exception) {
                runCatching {
                    socket.outputStream.write((errorResponse("INSPECTION_FAILED").toString() + "\n").toByteArray(Charsets.UTF_8))
                    socket.outputStream.flush()
                }
            } finally {
                socket.close()
            }
        }
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = ArrayList<Byte>()
        while (output.size < MAX_REQUEST_BYTES) {
            val value = read()
            if (value < 0 || value == '\n'.code) break
            output.add(value.toByte())
        }
        if (output.size >= MAX_REQUEST_BYTES) error("request too large")
        return output.toByteArray()
    }

    private fun readBoundedLine(bytes: ByteArray): ByteArray {
        if (bytes.isEmpty()) error("empty request")
        return bytes
    }

    private fun inspectOnMainThread(): JSONObject {
        if (Looper.myLooper() == Looper.getMainLooper()) return inspect()
        val latch = CountDownLatch(1)
        var result: JSONObject? = null
        mainHandler.post {
            result = runCatching(::inspect).getOrElse { errorResponse("INSPECTION_FAILED") }
            latch.countDown()
        }
        if (!latch.await(3, TimeUnit.SECONDS)) return errorResponse("MAIN_THREAD_TIMEOUT")
        return result ?: errorResponse("INSPECTION_FAILED")
    }

    private fun inspect(): JSONObject {
        val nodes = JSONArray()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val composeRoots = mutableListOf<ViewRootForTest>()
            WindowInspector.getGlobalWindowViews().forEach { collectComposeRoots(it, composeRoots) }
            composeRoots.distinctBy { it.view }.forEachIndexed { windowIndex, root ->
                val rootView = root.view
                appendNode(
                    node = root.semanticsOwner.unmergedRootSemanticsNode,
                    parentId = null,
                    depth = 0,
                    windowIndex = windowIndex,
                    windowFocused = rootView.hasWindowFocus(),
                    windowShown = rootView.isShown && rootView.isAttachedToWindow,
                    output = nodes,
                )
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(nodes.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return JSONObject()
            .put("schemaVersion", 1)
            .put("ok", true)
            .put("revision", digest)
            .put("nodes", nodes)
    }

    private fun collectComposeRoots(view: View, output: MutableList<ViewRootForTest>) {
        if (view is ViewRootForTest) output += view
        if (view is ViewGroup) {
            repeat(view.childCount) { index -> collectComposeRoots(view.getChildAt(index), output) }
        }
    }

    private fun appendNode(
        node: SemanticsNode,
        parentId: Int?,
        depth: Int,
        windowIndex: Int,
        windowFocused: Boolean,
        windowShown: Boolean,
        output: JSONArray,
    ) {
        val config = node.config
        val bounds = node.boundsInWindow
        val sensitive = config.has(SemanticsProperties.Password) ||
            config.value(SemanticsProperties.IsSensitiveData) == true
        val text = if (sensitive) {
            ""
        } else {
            config.value(SemanticsProperties.Text).orEmpty().joinToString("\n") { it.text }
        }
        val descriptions = if (sensitive) {
            emptyList()
        } else {
            config.value(SemanticsProperties.ContentDescription).orEmpty()
        }
        val actions = JSONArray().apply {
            if (config.has(SemanticsActions.OnClick)) put("click")
            if (config.has(SemanticsActions.OnLongClick)) put("longClick")
            if (config.has(SemanticsActions.ScrollBy) || config.has(SemanticsActions.ScrollByOffset)) put("scroll")
            if (config.has(SemanticsActions.SetText)) put("setText")
            if (config.has(SemanticsActions.Expand)) put("expand")
            if (config.has(SemanticsActions.Collapse)) put("collapse")
            if (config.has(SemanticsActions.Dismiss)) put("dismiss")
        }
        output.put(
            JSONObject()
                .put("runtimeId", node.id)
                .put("parentRuntimeId", parentId ?: JSONObject.NULL)
                .put("depth", depth)
                .put("window", windowIndex)
                .put("windowFocused", windowFocused)
                .put("windowShown", windowShown)
                .put("stableKey", config.value(SemanticsProperties.TestTag) ?: JSONObject.NULL)
                .put("text", text)
                .put("contentDescription", JSONArray(descriptions))
                .put("role", config.value(SemanticsProperties.Role)?.toString() ?: JSONObject.NULL)
                .put("stateDescription", config.value(SemanticsProperties.StateDescription) ?: JSONObject.NULL)
                .put("selected", config.value(SemanticsProperties.Selected) ?: JSONObject.NULL)
                .put("enabled", !config.has(SemanticsProperties.Disabled))
                .put("sensitive", sensitive)
                .put("actions", actions)
                .put(
                    "bounds",
                    JSONObject()
                        .put("left", bounds.left.toDouble())
                        .put("top", bounds.top.toDouble())
                        .put("right", bounds.right.toDouble())
                        .put("bottom", bounds.bottom.toDouble()),
                ),
        )
        node.children.forEach { child ->
            appendNode(child, node.id, depth + 1, windowIndex, windowFocused, windowShown, output)
        }
    }

    private fun <T> SemanticsConfiguration.value(key: SemanticsPropertyKey<T>): T? =
        if (contains(key)) get(key) else null

    private fun SemanticsConfiguration.has(key: SemanticsPropertyKey<*>): Boolean = contains(key)

    private fun errorResponse(code: String): JSONObject = JSONObject()
        .put("schemaVersion", 1)
        .put("ok", false)
        .put("error", code)
}
