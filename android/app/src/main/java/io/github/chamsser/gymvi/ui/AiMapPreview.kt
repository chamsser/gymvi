package io.github.chamsser.gymvi.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapOptions
import com.naver.maps.map.app.LegalNoticeActivity
import com.naver.maps.map.app.OpenSourceLicenseActivity
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import io.github.chamsser.gymvi.BuildConfig
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AiTurnCard
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/** A parent may disable work while the AI page is leaving or covered. */
internal val LocalAiMapPreviewEnabled = compositionLocalOf { true }

private object AiMapSnapshots {
    val gate = AiMapPreviewRenderGate()
    val images = AiMapPreviewCache<AiMapPreviewKey, Bitmap>(8 * 1024 * 1024, 12) { it.allocationByteCount }
    // A timeout is usually slow tiles, so only repeated misses give up on a map for this process.
    val attempts = AiMapPreviewAttempts<AiMapPreviewKey>(128)
}

/** Each budget runs only while the preview stays visible; hiding it cancels the attempt uncounted. */
internal val AiMapPreviewAttemptBudgetsMs = listOf(30_000L, 30_000L, 30_000L)

/** At most one short-lived native map is rendered across all conversation previews. */
@Composable
internal fun AiMapPreview(
    cards: List<AiTurnCard>,
    onOpenMap: (AiTurnCard) -> Unit,
    modifier: Modifier = Modifier,
    attemptBudgetsMs: List<Long> = AiMapPreviewAttemptBudgetsMs,
) {
    val points = remember(cards) { aiMapPreviewPoints(cards) }
    if (points.isEmpty()) return
    val firstCard = cards.first { it.option.facilityId == points.first().facilityId }
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val lifecycle = remember(context) { context.previewActivity()?.lifecycle }
    var resumed by remember(lifecycle) {
        mutableStateOf(lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true)
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
    val enabled = LocalAiMapPreviewEnabled.current
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    var size by remember { mutableStateOf(IntSize.Zero) }
    var visible by remember { mutableStateOf(false) }
    val key = remember(points, size, dark) { AiMapPreviewKey(points, size.width, size.height, dark) }
    var bitmap by remember(key) { mutableStateOf(AiMapSnapshots.images[key]) }
    var failed by remember(key, attemptBudgetsMs) {
        mutableStateOf(AiMapSnapshots.attempts.budget(key, attemptBudgetsMs) == null)
    }
    var session by remember(key) { mutableStateOf<AiMapSnapshotSession?>(null) }
    var retryKey by remember(key) { mutableIntStateOf(0) }

    LaunchedEffect(key, visible, resumed, enabled, retryKey) {
        if (!visible || !resumed || !enabled || size.width <= 0 || size.height <= 0 || bitmap != null || failed) {
            return@LaunchedEffect
        }
        if (BuildConfig.NAVER_MAPS_CLIENT_ID.isBlank()) {
            failed = true
            return@LaunchedEffect
        }
        withContext(Dispatchers.Main.immediate) {
            // Each attempt takes the gate again, so another visible reply can render between retries.
            while (bitmap == null && !failed) {
                AiMapSnapshots.gate.render {
                    val cached = AiMapSnapshots.images[key]
                    if (cached != null) {
                        bitmap = cached
                        return@render
                    }
                    val budget = AiMapSnapshots.attempts.budget(key, attemptBudgetsMs)
                    if (budget == null) {
                        failed = true
                        return@render
                    }
                    val renderer = AiMapSnapshotSession(context, key, density)
                    session = renderer
                    try {
                        val image = withTimeoutOrNull(budget) { renderer.image.await() }
                        if (image != null) {
                            AiMapSnapshots.images.put(key, image)
                            AiMapSnapshots.attempts.succeeded(key)
                            bitmap = image
                        } else {
                            AiMapSnapshots.attempts.timedOut(key)
                        }
                    } finally {
                        // Close the native view before the next waiter gets the permit.
                        withContext(NonCancellable + Dispatchers.Main.immediate) {
                            renderer.close()
                            session = null
                        }
                    }
                }
            }
        }
    }

    val openLabel = stringResource(R.string.ai_map_preview_open)
    val mapDescription = stringResource(
        R.string.ai_map_preview_description,
        points.joinToString(", ") { point -> cards.first { it.option.facilityId == point.facilityId }.facilityName },
    )
    Column(modifier.fillMaxWidth().testTag("ai-map-preview")) {
        // Keep a stable frame even when offline, and cover the native renderer until ready.
        run {
            Box(
                Modifier.fillMaxWidth().aspectRatio(600f / 360f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .onSizeChanged { size = it }
                    // Unplaced or detached rows report false at once, and a fling past a reply opens no map.
                    .onVisibilityChanged(minDurationMs = 250, minFractionVisible = .5f) { visible = it }
                    .semantics { contentDescription = mapDescription }
                    .clickable(role = Role.Button, onClickLabel = openLabel) { onOpenMap(firstCard) }
                    .testTag("ai-map-preview-frame"),
                contentAlignment = Alignment.Center,
            ) {
                val currentSession = session
                val currentBitmap = bitmap
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().testTag("ai-map-preview-image"),
                    )
                } else if (currentSession != null) {
                    // A retry can replace the session before the next frame; only a new node runs the factory.
                    key(currentSession) {
                        AndroidView(
                            factory = { currentSession.view.also { currentSession.start() } },
                            modifier = Modifier.fillMaxSize().clearAndSetSemantics { }
                                .testTag("ai-map-preview-renderer"),
                        )
                    }
                }
                if (currentBitmap == null) {
                    Column(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(if (failed) R.string.ai_map_preview_failed else R.string.ai_map_preview_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (failed) TextButton(onClick = {
                            AiMapSnapshots.attempts.reset(key)
                            failed = false
                            retryKey += 1
                        }, modifier = Modifier.testTag("ai-map-preview-retry")) {
                            Text(stringResource(R.string.ai_map_preview_retry))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onOpenMap(firstCard) }, modifier = Modifier.testTag("ai-map-preview-open")) {
                Text(openLabel)
            }
            if (bitmap != null) PreviewMapNotices()
        }
    }
}

@Composable
private fun PreviewMapNotices() {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(R.string.ai_map_preview_notices), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.ai_map_preview_legal)) }, onClick = {
                expanded = false
                context.startActivity(Intent(context, LegalNoticeActivity::class.java))
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.ai_map_preview_licenses)) }, onClick = {
                expanded = false
                context.startActivity(Intent(context, OpenSourceLicenseActivity::class.java))
            })
        }
    }
}

/** No location source, routing query, or connection to the main map's camera or overlays. */
private class AiMapSnapshotSession(context: Context, key: AiMapPreviewKey, density: Float) {
    val image = CompletableDeferred<Bitmap>()
    private var closed = false
    private var started = false
    private var snapshotRequested = false
    private var map: NaverMap? = null
    private val markers = mutableListOf<Marker>()
    private val first = key.points.first()
    private val margin = (12 * density).toInt()
    val view = MapView(context, NaverMapOptions()
        .camera(CameraPosition(LatLng(first.latitude, first.longitude), 15.0))
        .locale(Locale.KOREA)
        .useTextureView(true)
        .fpsLimit(15)
        .nightModeEnabled(key.dark)
        .compassEnabled(false).scaleBarEnabled(true).zoomControlEnabled(false)
        .locationButtonEnabled(false).indoorLevelPickerEnabled(false)
        .scrollGesturesEnabled(false).zoomGesturesEnabled(false)
        .tiltGesturesEnabled(false).rotateGesturesEnabled(false)
        .logoClickEnabled(true).logoGravity(Gravity.BOTTOM or Gravity.START)
        .logoMargin(margin, margin, margin, margin)
    ).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        onCreate(null)
    }
    private val rendered = NaverMap.OnMapRenderedListener { fully, stable ->
        if (fully && stable && !closed && !snapshotRequested) {
            snapshotRequested = true
            map?.takeSnapshot(true) { bitmap ->
                if (!closed && bitmap.width > 0 && bitmap.height > 0) image.complete(bitmap)
            }
        }
    }

    init {
        view.getMapAsync { readyMap ->
            if (!closed) {
                map = readyMap
                readyMap.maxZoom = 16.0
                key.points.forEach { point ->
                    markers += Marker().apply {
                        position = LatLng(point.latitude, point.longitude)
                        icon = previewMarkerIcon(point.number, density)
                        this.map = readyMap
                    }
                }
                view.post {
                    if (!closed) {
                        if (key.points.size > 1) {
                            val bounds = LatLngBounds.Builder().apply {
                                key.points.forEach { include(LatLng(it.latitude, it.longitude)) }
                            }.build()
                            readyMap.moveCamera(CameraUpdate.fitBounds(bounds, (48 * density).toInt()))
                        }
                        readyMap.addOnMapRenderedListener(rendered)
                    }
                }
            }
        }
    }

    fun start() {
        if (started || closed) return
        started = true
        view.onStart()
        view.onResume()
    }

    fun close() {
        if (closed) return
        closed = true
        map?.removeOnMapRenderedListener(rendered)
        markers.forEach { it.map = null }
        markers.clear()
        if (started) {
            view.onPause()
            view.onStop()
        }
        view.onDestroy()
        map = null
    }
}

private fun Context.previewActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.previewActivity()
    else -> null
}

private fun previewMarkerIcon(number: Int, density: Float): OverlayImage {
    val bitmap = Bitmap.createBitmap((30 * density).toInt(), (36 * density).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2563EB.toInt() }
    canvas.drawCircle(15 * density, 15 * density, 14 * density, paint)
    val tip = Path().apply {
        moveTo(7 * density, 24 * density)
        lineTo(15 * density, 35 * density)
        lineTo(23 * density, 24 * density)
        close()
    }
    canvas.drawPath(tip, paint)
    paint.color = Color.WHITE
    paint.textSize = 14 * density
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true
    canvas.drawText(number.toString(), 15 * density, 15 * density - (paint.ascent() + paint.descent()) / 2, paint)
    return OverlayImage.fromBitmap(bitmap)
}
