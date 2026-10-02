package io.github.chamsser.gymvi.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.location.LocationManager
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.LocationTrackingMode
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapOptions
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.LocationOverlay
import com.naver.maps.map.overlay.Overlay
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import com.naver.maps.map.util.FusedLocationSource
import com.naver.maps.map.util.MapConstants
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.AddressSearchItem
import io.github.chamsser.gymvi.data.FacilityBounds
import io.github.chamsser.gymvi.data.FacilityMapItem
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

internal val LocalMapRenderingActive = compositionLocalOf { true }

@Composable
fun NaverMapHost(
    facilities: List<FacilityMapItem>,
    selectedFacilityId: String?,
    routePath: List<RouteCoordinate> = emptyList(),
    searchTarget: AddressSearchItem? = null,
    facilitySearchFocus: MapSearchFocus? = null,
    isFacilitySearchActive: Boolean = false,
    accessibilityLabel: String,
    onMapLoaded: () -> Unit,
    onSearchAreaChanged: (FacilityBounds) -> Unit,
    onLocationTrackingStopped: () -> Unit,
    onLocationChanged: (Double, Double) -> Unit,
    onAuthFailed: (String) -> Unit,
    onFacilitySelected: (String) -> Unit,
    bottomLogoClearance: () -> Dp = { 12.dp },
    locationRequestKey: Int = 0,
    isInteractive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderingActive = LocalMapRenderingActive.current
    val designAccent = if (LocalNativeDesign.current.isOriginal) null else MaterialTheme.colorScheme.primary.toArgb()
    val activity = context.findComponentActivity()
    val lifecycle = activity.lifecycle
    val localDensity = LocalDensity.current
    val density = localDensity.density
    val currentOnMapLoaded = rememberUpdatedState(onMapLoaded)
    val currentOnSearchAreaChanged = rememberUpdatedState(onSearchAreaChanged)
    val currentOnLocationTrackingStopped = rememberUpdatedState(onLocationTrackingStopped)
    val currentOnLocationChanged = rememberUpdatedState(onLocationChanged)
    val currentOnAuthFailed = rememberUpdatedState(onAuthFailed)
    val currentOnFacilitySelected = rememberUpdatedState(onFacilitySelected)
    val recentSystemLocation = remember(context, locationRequestKey) {
        if (locationRequestKey > 0) context.recentLastKnownMapLocation() else null
    }

    LaunchedEffect(locationRequestKey, recentSystemLocation) {
        if (locationRequestKey > 0 && recentSystemLocation != null) {
            currentOnLocationChanged.value(
                recentSystemLocation.latitude,
                recentSystemLocation.longitude,
            )
        }
    }

    val mapView = remember(context) {
        val margin = (12 * density).toInt()
        val gesturePolicy = mapGesturePolicy(isInteractive = true)
        val options = applyKoreaCameraConstraints(
            NaverMapOptions()
                .camera(CameraPosition(INITIAL_CENTER, INITIAL_ZOOM)),
        )
            .tiltGesturesEnabled(gesturePolicy.tiltGesturesEnabled)
            .rotateGesturesEnabled(gesturePolicy.rotateGesturesEnabled)
            .compassEnabled(false)
            .scaleBarEnabled(true)
            .zoomControlEnabled(false)
            .indoorLevelPickerEnabled(false)
            .locationButtonEnabled(false)
            .logoClickEnabled(true)
            .logoGravity(Gravity.BOTTOM or Gravity.START)
            .logoMargin(margin, margin, margin, margin)
        MapView(context, options).apply { onCreate(null) }
    }
    val locationSource = remember(activity) {
        FusedLocationSource(activity, LOCATION_PERMISSION_REQUEST_CODE).apply {
            isCompassEnabled = true
        }
    }
    val bridge = remember(mapView, locationSource) {
        NaverMapBridge(
            applicationContext = context.applicationContext,
            density = density,
            locationSource = locationSource,
        )
    }
    val lifecycleObserver = remember(mapView) { MapViewLifecycleObserver(mapView, renderingActive) }
    SideEffect { lifecycleObserver.setVisible(renderingActive) }
    val sdk = remember(context) { NaverMapSdk.getInstance(context.applicationContext) }

    DisposableEffect(sdk) {
        val listener = NaverMapSdk.OnAuthFailedListener { exception ->
            currentOnAuthFailed.value(exception.errorCode)
        }
        sdk.onAuthFailedListener = listener
        onDispose {
            if (sdk.onAuthFailedListener === listener) {
                sdk.onAuthFailedListener = null
            }
        }
    }

    DisposableEffect(lifecycle, lifecycleObserver, bridge) {
        lifecycle.addObserver(lifecycleObserver)
        lifecycleObserver.synchronize(lifecycle.currentState)
        bridge.updateCallbacks(
            onMapLoaded = { currentOnMapLoaded.value() },
            onSearchAreaChanged = { currentOnSearchAreaChanged.value(it) },
            onLocationTrackingStopped = { currentOnLocationTrackingStopped.value() },
            onLocationChanged = { latitude, longitude ->
                currentOnLocationChanged.value(latitude, longitude)
            },
            onFacilitySelected = { currentOnFacilitySelected.value(it) },
        )
        bridge.updateViewport(
            bottomContentPaddingPx = with(localDensity) { bottomLogoClearance().roundToPx() },
            locationRequestKey = locationRequestKey,
            fallbackLatitude = recentSystemLocation?.latitude,
            fallbackLongitude = recentSystemLocation?.longitude,
            isInteractive = isInteractive,
        )
        mapView.getMapAsync(bridge::attach)
        onDispose {
            bridge.destroy()
            lifecycle.removeObserver(lifecycleObserver)
            lifecycleObserver.destroy()
        }
    }

    val mapSemantics = if (isInteractive) {
        Modifier.semantics { contentDescription = accessibilityLabel }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    AndroidView(
        factory = { mapView },
        modifier = modifier.then(mapSemantics),
        update = { view ->
            // Keep the surface attached while paused, avoiding GPU surface recreation on every tab.
            if (!renderingActive) return@AndroidView
            view.setMapAccessibilityEnabled(isInteractive)
            view.contentDescription = accessibilityLabel.takeIf { isInteractive }
            view.isEnabled = isInteractive
            bridge.updateCallbacks(
                onMapLoaded = { currentOnMapLoaded.value() },
                onSearchAreaChanged = { currentOnSearchAreaChanged.value(it) },
                onLocationTrackingStopped = { currentOnLocationTrackingStopped.value() },
                onLocationChanged = { latitude, longitude ->
                    currentOnLocationChanged.value(latitude, longitude)
                },
                onFacilitySelected = { currentOnFacilitySelected.value(it) },
            )
            bridge.updateViewport(
                // Read per-frame sheet movement here, not in composition. AndroidView can
                // update its viewport without rebuilding the surrounding screen or markers.
                bottomContentPaddingPx = with(localDensity) { bottomLogoClearance().roundToPx() },
                locationRequestKey = locationRequestKey,
                fallbackLatitude = recentSystemLocation?.latitude,
                fallbackLongitude = recentSystemLocation?.longitude,
                isInteractive = isInteractive,
            )
            bridge.render(
                facilities = facilities,
                selectedFacilityId = selectedFacilityId,
                routePath = routePath,
                searchTarget = searchTarget,
                facilitySearchFocus = facilitySearchFocus,
                isFacilitySearchActive = isFacilitySearchActive,
                designAccent = designAccent,
            )
        },
    )
}

private class NaverMapBridge(
    private val applicationContext: Context,
    private val density: Float,
    private val locationSource: FusedLocationSource,
) {
    private var naverMap: NaverMap? = null
    private var designAccent: Int? = null
    private var facilities: List<FacilityMapItem> = emptyList()
    private var selectedFacilityId: String? = null
    private var routePath: List<RouteCoordinate> = emptyList()
    private var searchTarget: AddressSearchItem? = null
    private var facilitySearchFocus: MapSearchFocus? = null
    private var isFacilitySearchActive = false
    private var markers: List<Marker> = emptyList()
    private val categoryMarkerImages = mutableMapOf<FacilityCategory, OverlayImage>()
    private val categoryBadgeImages = mutableMapOf<FacilityCategory, OverlayImage>()
    private val selectedMarkerImages = mutableMapOf<Int, OverlayImage>()
    private var searchTargetMarker: Marker? = null
    private var routeOverlay: PathOverlay? = null
    private var lastFocusedSelectedFacilityId: String? = null
    private var lastFocusedFacilitySearchKey: String? = null
    private var destroyed = false
    private var bottomContentPaddingPx = (12 * density).toInt()
    private var appliedBottomPaddingPx: Int? = null
    private var appliedInteractive: Boolean? = null
    private var locationRequestKey = 0
    private var handledLocationRequestKey = 0
    private var lastKnownLatitude: Double? = null
    private var lastKnownLongitude: Double? = null
    private var isInteractive = true
    private var hasPendingUserCameraMove = false
    private var onMapLoaded: () -> Unit = {}
    private var onSearchAreaChanged: (FacilityBounds) -> Unit = {}
    private var onLocationTrackingStopped: () -> Unit = {}
    private var onLocationChanged: (Double, Double) -> Unit = { _, _ -> }
    private var onFacilitySelected: (String) -> Unit = {}

    private val cameraIdleListener = NaverMap.OnCameraIdleListener {
        if (hasPendingUserCameraMove) {
            hasPendingUserCameraMove = false
            emitVisibleBounds()
        }
    }
    private val cameraChangeListener = NaverMap.OnCameraChangeListener { reason, _ ->
        if (
            isInteractive &&
            (reason == CameraUpdate.REASON_GESTURE || reason == CameraUpdate.REASON_CONTROL)
        ) {
            hasPendingUserCameraMove = true
            naverMap?.let { map ->
                val nextMode = locationTrackingModeAfterUserCameraMove(
                    map.locationTrackingMode,
                )
                locationSource.isCompassEnabled = nextMode != LocationTrackingMode.None
                map.locationTrackingMode = nextMode
                if (nextMode != LocationTrackingMode.None) {
                    map.locationOverlay.apply {
                        subIcon = LocationOverlay.DEFAULT_SUB_ICON_ARROW
                        subAnchor = LocationOverlay.DEFAULT_SUB_ANCHOR
                        if (lastKnownLatitude != null && lastKnownLongitude != null) {
                            isVisible = true
                        }
                    }
                }
            }
            onLocationTrackingStopped()
        }
    }
    private val mapLoadListener = NaverMap.OnLoadListener {
        onMapLoaded()
    }
    private val locationChangeListener = NaverMap.OnLocationChangeListener { location ->
        if (
            location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
            location.longitude.isFinite() && location.longitude in -180.0..180.0
        ) {
            lastKnownLatitude = location.latitude
            lastKnownLongitude = location.longitude
            naverMap?.locationOverlay?.apply {
                position = LatLng(location.latitude, location.longitude)
                if (location.bearing.isFinite()) bearing = location.bearing
                subIcon = LocationOverlay.DEFAULT_SUB_ICON_ARROW
                subAnchor = LocationOverlay.DEFAULT_SUB_ANCHOR
                isVisible = true
            }
            onLocationChanged(location.latitude, location.longitude)
        }
    }

    fun updateCallbacks(
        onMapLoaded: () -> Unit,
        onSearchAreaChanged: (FacilityBounds) -> Unit,
        onLocationTrackingStopped: () -> Unit,
        onLocationChanged: (Double, Double) -> Unit,
        onFacilitySelected: (String) -> Unit,
    ) {
        this.onMapLoaded = onMapLoaded
        this.onSearchAreaChanged = onSearchAreaChanged
        this.onLocationTrackingStopped = onLocationTrackingStopped
        this.onLocationChanged = onLocationChanged
        this.onFacilitySelected = onFacilitySelected
    }

    fun updateViewport(
        bottomContentPaddingPx: Int,
        locationRequestKey: Int,
        fallbackLatitude: Double?,
        fallbackLongitude: Double?,
        isInteractive: Boolean,
    ) {
        val bottomPaddingChanged = this.bottomContentPaddingPx != bottomContentPaddingPx
        val viewportChanged = bottomPaddingChanged || this.isInteractive != isInteractive ||
            locationRequestKey > this.locationRequestKey
        this.bottomContentPaddingPx = bottomContentPaddingPx
        if (
            locationRequestKey > handledLocationRequestKey &&
            fallbackLatitude != null && fallbackLongitude != null
        ) {
            lastKnownLatitude = fallbackLatitude
            lastKnownLongitude = fallbackLongitude
        }
        this.locationRequestKey = maxOf(this.locationRequestKey, locationRequestKey)
        this.isInteractive = isInteractive
        if (!viewportChanged) return
        applyViewportState()
        if (bottomPaddingChanged) {
            // Keep the whole route above the route sheet when its measured height changes.
            if (routeOverlay != null) {
                fitRoute()
            }
        }
    }

    fun attach(map: NaverMap) {
        if (destroyed) return
        naverMap = map
        map.extent = MapConstants.EXTENT_KOREA
        map.minZoom = MapConstants.MIN_ZOOM_KOREA
        map.locationSource = locationSource
        locationSource.isCompassEnabled = true
        map.locationOverlay.apply {
            subIcon = LocationOverlay.DEFAULT_SUB_ICON_ARROW
            subAnchor = LocationOverlay.DEFAULT_SUB_ANCHOR
            if (lastKnownLatitude != null && lastKnownLongitude != null) {
                position = LatLng(checkNotNull(lastKnownLatitude), checkNotNull(lastKnownLongitude))
                isVisible = true
            }
        }
        map.uiSettings.apply {
            isCompassEnabled = false
            isScaleBarEnabled = true
            isZoomControlEnabled = false
            isIndoorLevelPickerEnabled = false
            isLocationButtonEnabled = false
            isLogoClickEnabled = true
        }
        applyViewportState()
        map.addOnCameraChangeListener(cameraChangeListener)
        map.addOnCameraIdleListener(cameraIdleListener)
        map.addOnLoadListener(mapLoadListener)
        map.addOnLocationChangeListener(locationChangeListener)
        renderMarkers()
        renderSearchTarget()
        renderRoute()
        focusFacilitySearchResult()
    }

    private fun applyViewportState() {
        val map = naverMap ?: return
        if (appliedInteractive != isInteractive) {
            val gesturePolicy = mapGesturePolicy(isInteractive)
            map.uiSettings.setAllGesturesEnabled(isInteractive)
            map.uiSettings.isTiltGesturesEnabled = gesturePolicy.tiltGesturesEnabled
            map.uiSettings.isRotateGesturesEnabled = gesturePolicy.rotateGesturesEnabled
            map.uiSettings.isLogoClickEnabled = isInteractive
            val margin = (12 * density).toInt()
            map.uiSettings.setLogoMargin(margin, margin, margin, margin)
            appliedInteractive = isInteractive
        }
        if (appliedBottomPaddingPx != bottomContentPaddingPx) {
            // The SDK retains the focal coordinate while its visible center follows the sheet.
            // A second scrollTo here would cancel that continuous movement every frame.
            map.setContentPadding(0, (MAP_TOP_CONTENT_PADDING_DP * density).toInt(), 0, bottomContentPaddingPx, true)
            appliedBottomPaddingPx = bottomContentPaddingPx
        }
        val recenterPlan = locationRecenterPlan(
            requestKey = locationRequestKey,
            handledRequestKey = handledLocationRequestKey,
            lastKnownLatitude = lastKnownLatitude,
            lastKnownLongitude = lastKnownLongitude,
        )
        if (recenterPlan.shouldRestartTracking) {
            map.locationTrackingMode = LocationTrackingMode.None
            if (recenterPlan.latitude != null && recenterPlan.longitude != null) {
                map.locationOverlay.apply {
                    position = LatLng(recenterPlan.latitude, recenterPlan.longitude)
                    subIcon = LocationOverlay.DEFAULT_SUB_ICON_ARROW
                    subAnchor = LocationOverlay.DEFAULT_SUB_ANCHOR
                    isVisible = true
                }
                map.moveCamera(
                    CameraUpdate.scrollTo(
                        LatLng(recenterPlan.latitude, recenterPlan.longitude),
                    ),
                )
            }
            locationSource.isCompassEnabled = true
            map.locationTrackingMode = LocationTrackingMode.Face
            handledLocationRequestKey = locationRequestKey
        }
    }

    fun render(
        facilities: List<FacilityMapItem>,
        selectedFacilityId: String?,
        routePath: List<RouteCoordinate>,
        searchTarget: AddressSearchItem?,
        facilitySearchFocus: MapSearchFocus?,
        isFacilitySearchActive: Boolean,
        designAccent: Int? = null,
    ) {
        if (
            this.facilities == facilities &&
            this.selectedFacilityId == selectedFacilityId &&
            this.routePath == routePath &&
            this.searchTarget == searchTarget &&
            this.facilitySearchFocus == facilitySearchFocus &&
            this.isFacilitySearchActive == isFacilitySearchActive &&
            this.designAccent == designAccent
        ) return
        val selectionChanged = this.selectedFacilityId != selectedFacilityId
        val designChanged = this.designAccent != designAccent
        val markersChanged = this.facilities != facilities || selectionChanged || designChanged ||
            this.isFacilitySearchActive != isFacilitySearchActive
        val routeChanged = this.routePath != routePath
        val searchTargetChanged = this.searchTarget != searchTarget
        val facilitySearchFocusChanged = this.facilitySearchFocus != facilitySearchFocus
        this.facilities = facilities
        this.selectedFacilityId = selectedFacilityId
        this.routePath = routePath
        this.searchTarget = searchTarget
        this.facilitySearchFocus = facilitySearchFocus
        this.isFacilitySearchActive = isFacilitySearchActive
        this.designAccent = designAccent
        if (designChanged) {
            searchTargetMarker?.apply {
                captionColor = designAccent ?: facilityMarkerStyle(true).captionColor
                icon = designAccent?.let(::nativeSelectedMarkerImage) ?: OverlayImage.fromResource(facilityMarkerStyle(true).iconResource)
            }
            routeOverlay?.color = designAccent ?: ROUTE_COLOR
        }
        if (markersChanged) renderMarkers()
        if (selectionChanged) focusSelectedFacility()
        if (searchTargetChanged) renderSearchTarget()
        if (routeChanged) renderRoute()
        if (facilitySearchFocusChanged) focusFacilitySearchResult()
    }

    private fun renderSearchTarget() {
        val map = naverMap ?: return
        searchTargetMarker?.map = null
        searchTargetMarker = null
        val target = searchTarget ?: return
        val style = facilityMarkerStyle(selected = true)
        searchTargetMarker = Marker().apply {
            position = LatLng(target.latitude, target.longitude)
            icon = designAccent?.let(::nativeSelectedMarkerImage) ?: OverlayImage.fromResource(style.iconResource)
            width = (style.widthDp * density).toInt()
            height = (style.heightDp * density).toInt()
            anchor = PointF(0.5f, style.anchorY)
            captionText = target.displayLabel
            captionTextSize = style.captionTextSizeSp
            captionColor = designAccent ?: style.captionColor
            captionHaloColor = Color.WHITE
            captionRequestedWidth = (180 * density).toInt()
            captionOffset = (2 * density).toInt()
            isForceShowIcon = true
            isForceShowCaption = true
            zIndex = SEARCH_TARGET_MARKER_Z_INDEX
            this.map = map
        }
        map.moveCamera(
            CameraUpdate.scrollAndZoomTo(
                LatLng(target.latitude, target.longitude),
                ADDRESS_SEARCH_ZOOM,
            ).animate(CameraAnimation.Easing),
        )
    }

    private fun renderMarkers() {
        val map = naverMap ?: return
        markers.forEach { it.map = null }
        markers = buildList {
            facilities.forEachIndexed { index, facility ->
                val selected = facility.facilityId == selectedFacilityId
                val category = FacilityCategory.fromFacility(facility)
                facilityMarkerLayers(
                    selected = selected,
                    searchActive = isFacilitySearchActive,
                    category = category,
                    grossFloorAreaSquareMeters = facility.grossFloorAreaSquareMeters,
                ).forEach { layer ->
                    add(createFacilityMarker(map, facility, category, index, layer))
                }
            }
        }
    }

    private fun nativeSelectedMarkerImage(accent: Int): OverlayImage = selectedMarkerImages.getOrPut(accent) {
        val bitmap = android.graphics.Bitmap.createBitmap((32 * density).roundToInt(), (40 * density).roundToInt(), android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.scale(density, density)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.color = accent
        val path = android.graphics.Path().apply {
            moveTo(16f, 39f)
            cubicTo(12f, 31f, 2f, 23f, 2f, 15f)
            cubicTo(2f, -3f, 30f, -3f, 30f, 15f)
            cubicTo(30f, 23f, 20f, 31f, 16f, 39f)
            close()
        }
        canvas.drawPath(path, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(16f, 15f, 5f, paint)
        OverlayImage.fromBitmap(bitmap)
    }

    private fun createFacilityMarker(
        map: NaverMap,
        facility: FacilityMapItem,
        category: FacilityCategory,
        rankedIndex: Int,
        layer: FacilityMarkerLayer,
    ): Marker {
        val selected = layer.appearance == FacilityMarkerAppearance.SELECTED_PIN
        val searchResult = layer.appearance == FacilityMarkerAppearance.SEARCH_PIN
        val style = when (layer.appearance) {
            FacilityMarkerAppearance.SELECTED_PIN -> facilityMarkerStyle(selected = true)
            FacilityMarkerAppearance.SEARCH_PIN -> facilityCategoryMarkerStyle()
            FacilityMarkerAppearance.CATEGORY_BADGE -> facilityBrowseMarkerStyle()
        }
        val markerImage = when (layer.appearance) {
            FacilityMarkerAppearance.SEARCH_PIN ->
                categoryMarkerImages.getOrPut(category) { createCategoryMarkerImage(category) }
            FacilityMarkerAppearance.CATEGORY_BADGE ->
                categoryBadgeImages.getOrPut(category) { createCategoryBadgeImage(category) }
            FacilityMarkerAppearance.SELECTED_PIN -> designAccent?.let(::nativeSelectedMarkerImage) ?: OverlayImage.fromResource(style.iconResource)
        }
        val collisionPolicy = facilityMarkerCollisionPolicy(
            selected = selected,
            searchResult = searchResult,
            category = category,
            grossFloorAreaSquareMeters = facility.grossFloorAreaSquareMeters,
            rankedIndex = rankedIndex,
            facilityCount = facilities.size,
        )
        return Marker().apply {
            position = LatLng(facility.latitude, facility.longitude)
            icon = markerImage
            width = (style.widthDp * density).roundToInt()
            height = (style.heightDp * density).roundToInt()
            anchor = PointF(0.5f, style.anchorY)
            captionText = facility.name
            captionTextSize = style.captionTextSizeSp
            captionColor = if (designAccent == null) style.captionColor else if (selected) designAccent!! else 0xFF222222.toInt()
            captionHaloColor = Color.WHITE
            captionRequestedWidth = (132 * density).roundToInt()
            captionOffset = (2 * density).roundToInt()
            subCaptionText = ""
            subCaptionTextSize = 9.5f
            subCaptionColor = SELECTED_MARKER_COLOR
            isHideCollidedCaptions = true
            isHideCollidedSymbols = true
            isHideCollidedMarkers = collisionPolicy.hideCollidedMarkers
            isForceShowIcon = collisionPolicy.forceShowIcon
            isForceShowCaption = selected
            minZoom = layer.minZoom
            isMinZoomInclusive = true
            layer.maxZoom?.let { upperZoom ->
                maxZoom = upperZoom
                isMaxZoomInclusive = layer.isMaxZoomInclusive
            }
            captionMinZoom = layer.captionMinZoom
            subCaptionMinZoom = layer.captionMinZoom
            zIndex = collisionPolicy.zIndex
            tag = facility.facilityId
            onClickListener = Overlay.OnClickListener { overlay ->
                (overlay.tag as? String)?.let(onFacilitySelected)
                true
            }
            this.map = map
        }
    }

    private fun createCategoryMarkerImage(category: FacilityCategory): OverlayImage {
        val style = facilityCategoryMarkerStyle()
        val bitmapWidth = (style.widthDp * density).roundToInt()
        val bitmapHeight = (style.heightDp * density).roundToInt()
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pin = checkNotNull(applicationContext.getDrawable(style.iconResource)).mutate()
        val horizontalInset = (1 * density).roundToInt()
        pin.setBounds(horizontalInset, 0, bitmapWidth - horizontalInset, bitmapHeight)
        pin.draw(canvas)

        val categoryIcon = checkNotNull(applicationContext.getDrawable(category.iconRes)).mutate()
        categoryIcon.setTint(CATEGORY_MARKER_ICON_COLOR)
        val iconSize = (14 * density).roundToInt()
        val iconCenterX = bitmapWidth / 2
        val iconCenterY = (15 * density).roundToInt()
        categoryIcon.setBounds(
            iconCenterX - iconSize / 2,
            iconCenterY - iconSize / 2,
            iconCenterX + (iconSize + 1) / 2,
            iconCenterY + (iconSize + 1) / 2,
        )
        categoryIcon.draw(canvas)

        return OverlayImage.fromBitmap(bitmap)
    }

    private fun createCategoryBadgeImage(category: FacilityCategory): OverlayImage {
        val style = facilityBrowseMarkerStyle()
        val bitmapWidth = (style.widthDp * density).roundToInt()
        val bitmapHeight = (style.heightDp * density).roundToInt()
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = bitmapWidth / 2f
        val centerY = bitmapHeight / 2f
        val outerRadius = 12f * density
        val innerRadius = 9.5f * density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = 0x33000000
        canvas.drawCircle(centerX, centerY + density, outerRadius + density, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, outerRadius, paint)
        paint.color = category.markerColor
        canvas.drawCircle(centerX, centerY, innerRadius, paint)

        val categoryIcon = checkNotNull(applicationContext.getDrawable(category.iconRes)).mutate()
        categoryIcon.setTint(Color.WHITE)
        val iconSize = (12 * density).roundToInt()
        val iconCenterX = bitmapWidth / 2
        val iconCenterY = bitmapHeight / 2
        categoryIcon.setBounds(
            iconCenterX - iconSize / 2,
            iconCenterY - iconSize / 2,
            iconCenterX + (iconSize + 1) / 2,
            iconCenterY + (iconSize + 1) / 2,
        )
        categoryIcon.draw(canvas)
        return OverlayImage.fromBitmap(bitmap)
    }

    private fun focusSelectedFacility(
        force: Boolean = false,
        animate: Boolean = true,
    ) {
        val map = naverMap ?: return
        val selectedId = selectedFacilityId
        if (selectedId == null) {
            lastFocusedSelectedFacilityId = null
            return
        }
        if (!force && selectedId == lastFocusedSelectedFacilityId) return
        val facility = facilities.firstOrNull { it.facilityId == selectedId } ?: return
        lastFocusedSelectedFacilityId = selectedId
        val update = CameraUpdate.scrollTo(LatLng(facility.latitude, facility.longitude))
        map.moveCamera(if (animate) update.animate(CameraAnimation.Easing) else update)
    }

    private fun focusFacilitySearchResult() {
        val map = naverMap ?: return
        val focus = facilitySearchFocus
        if (focus == null) {
            lastFocusedFacilitySearchKey = null
            return
        }
        if (
            focus.key == lastFocusedFacilitySearchKey ||
            selectedFacilityId != null ||
            searchTarget != null ||
            routePath.size >= 2
        ) return
        lastFocusedFacilitySearchKey = focus.key
        map.moveCamera(
            CameraUpdate.scrollAndZoomTo(
                LatLng(focus.latitude, focus.longitude),
                FACILITY_SEARCH_ZOOM,
            ).animate(
                CameraAnimation.Fly,
                FACILITY_SEARCH_CAMERA_ANIMATION_DURATION_MILLIS,
            ),
        )
    }

    private fun renderRoute() {
        val map = naverMap ?: return
        routeOverlay?.map = null
        routeOverlay = null
        if (routePath.size < 2) return

        val directionPattern = OverlayImage.fromResource(
            R.drawable.ic_gymvi_route_direction_pattern,
        )
        val displayCoordinates = separateOpposingRoutePasses(routePath)
            .map { LatLng(it.latitude, it.longitude) }
        val overlay = PathOverlay(displayCoordinates).apply {
            width = (8 * density).toInt()
            outlineWidth = (2 * density).toInt()
            color = designAccent ?: ROUTE_COLOR
            outlineColor = Color.WHITE
            patternImage = directionPattern
            patternInterval = (56 * density).toInt()
            isHideCollidedSymbols = false
            zIndex = 50
            this.map = map
        }
        routeOverlay = overlay
        fitRoute()
    }

    private fun fitRoute() {
        val map = naverMap ?: return
        val overlay = routeOverlay ?: return
        map.moveCamera(
            // Wider side padding keeps the endpoint marker captions inside the visible map.
            CameraUpdate.fitBounds(
                overlay.bounds,
                (ROUTE_FIT_HORIZONTAL_PADDING_DP * density).toInt(),
                (ROUTE_FIT_VERTICAL_PADDING_DP * density).toInt(),
                (ROUTE_FIT_HORIZONTAL_PADDING_DP * density).toInt(),
                (ROUTE_FIT_VERTICAL_PADDING_DP * density).toInt(),
            ).animate(CameraAnimation.Easing),
        )
    }

    private fun emitVisibleBounds() {
        val bounds = naverMap?.contentBounds ?: return
        if (!bounds.isValid || bounds.isEmpty) return
        runCatching {
            FacilityBounds(
                minLongitude = bounds.westLongitude,
                minLatitude = bounds.southLatitude,
                maxLongitude = bounds.eastLongitude,
                maxLatitude = bounds.northLatitude,
            )
        }.getOrNull()?.let(onSearchAreaChanged)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        markers.forEach { it.map = null }
        markers = emptyList()
        categoryMarkerImages.clear()
        searchTargetMarker?.map = null
        searchTargetMarker = null
        routeOverlay?.map = null
        routeOverlay = null
        naverMap?.locationTrackingMode = LocationTrackingMode.None
        naverMap?.removeOnCameraChangeListener(cameraChangeListener)
        naverMap?.removeOnCameraIdleListener(cameraIdleListener)
        naverMap?.removeOnLoadListener(mapLoadListener)
        naverMap?.removeOnLocationChangeListener(locationChangeListener)
        naverMap = null
    }

    companion object {
        private val SELECTED_MARKER_COLOR = Color.rgb(0, 124, 131)
        private val CATEGORY_MARKER_ICON_COLOR = Color.rgb(0, 91, 99)
        private val ROUTE_COLOR = Color.rgb(36, 109, 219)
        private const val MAP_TOP_CONTENT_PADDING_DP = 124
        private const val ROUTE_FIT_HORIZONTAL_PADDING_DP = 72
        private const val ROUTE_FIT_VERTICAL_PADDING_DP = 40
        private const val ADDRESS_SEARCH_ZOOM = 16.0
        private const val FACILITY_SEARCH_ZOOM = 15.0
    }
}

/**
 * Separates only non-adjacent route segments that reuse the same road in opposite
 * directions. Ordinary route segments stay on the provider geometry.
 */
internal fun separateOpposingRoutePasses(
    path: List<RouteCoordinate>,
    laneOffsetMeters: Double = 3.0,
    overlapDistanceMeters: Double = 8.0,
): List<RouteCoordinate> {
    require(laneOffsetMeters >= 0.0)
    require(overlapDistanceMeters >= 0.0)
    if (path.size < 4 || laneOffsetMeters == 0.0) return path

    val originLatitude = path.map(RouteCoordinate::latitude).average()
    val originLongitude = path.first().longitude
    val originLatitudeRadians = Math.toRadians(originLatitude)
    val meterPoints = path.map { coordinate ->
        RouteMeterPoint(
            east = Math.toRadians(coordinate.longitude - originLongitude) *
                ROUTE_EARTH_RADIUS_METERS * cos(originLatitudeRadians),
            north = Math.toRadians(coordinate.latitude - originLatitude) *
                ROUTE_EARTH_RADIUS_METERS,
        )
    }
    val segments = meterPoints.zipWithNext(::RouteMeterSegment)
    val opposingSegments = BooleanArray(segments.size)

    for (firstIndex in segments.indices) {
        val first = segments[firstIndex]
        if (first.length < MIN_ROUTE_SEGMENT_METERS) continue
        for (secondIndex in firstIndex + 2 until segments.size) {
            val second = segments[secondIndex]
            if (second.length < MIN_ROUTE_SEGMENT_METERS) continue
            if (!first.canSpatiallyOverlap(second, overlapDistanceMeters)) continue
            if (first.directionDot(second) > OPPOSING_DIRECTION_DOT_LIMIT) continue

            val requiredOverlap = minOf(
                MAX_REQUIRED_OVERLAP_METERS,
                minOf(first.length, second.length) * REQUIRED_OVERLAP_RATIO,
            ).coerceAtLeast(MIN_REQUIRED_OVERLAP_METERS)
            if (first.projectedOverlapWith(second) < requiredOverlap) continue
            if (second.projectedOverlapWith(first) < requiredOverlap) continue
            if (first.distanceTo(second) > overlapDistanceMeters) continue

            opposingSegments[firstIndex] = true
            opposingSegments[secondIndex] = true
        }
    }
    if (opposingSegments.none { it }) return path

    val vertexWeights = DoubleArray(path.size) { index ->
        val previousOverlaps = index > 0 && opposingSegments[index - 1]
        val nextOverlaps = index < opposingSegments.size && opposingSegments[index]
        if (previousOverlaps || nextOverlaps) 1.0 else 0.0
    }
    val featheredWeights = vertexWeights.copyOf()
    for (index in vertexWeights.indices) {
        if (vertexWeights[index] > 0.0) continue
        val besideOverlap = vertexWeights.getOrElse(index - 1) { 0.0 } > 0.0 ||
            vertexWeights.getOrElse(index + 1) { 0.0 } > 0.0
        if (besideOverlap) featheredWeights[index] = 0.5
    }

    return path.mapIndexed { index, coordinate ->
        val offset = laneOffsetMeters * featheredWeights[index]
        if (offset == 0.0) return@mapIndexed coordinate

        val direction = routeDirectionAt(meterPoints, index)
        if (direction.length < MIN_ROUTE_SEGMENT_METERS) return@mapIndexed coordinate
        // Rotate the travel vector clockwise so reused road segments are drawn on
        // the right side of travel, matching Korea's right-hand traffic.
        val offsetEast = direction.north / direction.length * offset
        val offsetNorth = -direction.east / direction.length * offset
        val pointLatitudeRadians = Math.toRadians(coordinate.latitude)
        RouteCoordinate(
            latitude = coordinate.latitude +
                Math.toDegrees(offsetNorth / ROUTE_EARTH_RADIUS_METERS),
            longitude = coordinate.longitude + Math.toDegrees(
                offsetEast / (ROUTE_EARTH_RADIUS_METERS * cos(pointLatitudeRadians)),
            ),
        )
    }
}

private data class RouteMeterPoint(
    val east: Double,
    val north: Double,
) {
    operator fun minus(other: RouteMeterPoint): RouteMeterPoint = RouteMeterPoint(
        east = east - other.east,
        north = north - other.north,
    )

    fun dot(other: RouteMeterPoint): Double = east * other.east + north * other.north

    val length: Double
        get() = hypot(east, north)
}

private data class RouteMeterSegment(
    val start: RouteMeterPoint,
    val end: RouteMeterPoint,
) {
    val vector: RouteMeterPoint = end - start
    val length: Double = vector.length
    val unit: RouteMeterPoint = if (length < MIN_ROUTE_SEGMENT_METERS) {
        RouteMeterPoint(0.0, 0.0)
    } else {
        RouteMeterPoint(vector.east / length, vector.north / length)
    }
    private val midpoint = RouteMeterPoint(
        east = (start.east + end.east) / 2.0,
        north = (start.north + end.north) / 2.0,
    )

    fun directionDot(other: RouteMeterSegment): Double = unit.dot(other.unit)

    fun canSpatiallyOverlap(other: RouteMeterSegment, toleranceMeters: Double): Boolean {
        val maximumMidpointDistance = (length + other.length) / 2.0 + toleranceMeters
        return abs(midpoint.east - other.midpoint.east) <= maximumMidpointDistance &&
            abs(midpoint.north - other.midpoint.north) <= maximumMidpointDistance
    }

    fun projectedOverlapWith(other: RouteMeterSegment): Double {
        val firstProjection = (other.start - start).dot(unit)
        val secondProjection = (other.end - start).dot(unit)
        val candidateStart = minOf(firstProjection, secondProjection)
        val candidateEnd = maxOf(firstProjection, secondProjection)
        return (minOf(length, candidateEnd) - maxOf(0.0, candidateStart)).coerceAtLeast(0.0)
    }

    fun distanceTo(other: RouteMeterSegment): Double = minOf(
        start.distanceTo(other),
        end.distanceTo(other),
        other.start.distanceTo(this),
        other.end.distanceTo(this),
    )
}

private fun RouteMeterPoint.distanceTo(segment: RouteMeterSegment): Double {
    if (segment.length < MIN_ROUTE_SEGMENT_METERS) return (this - segment.start).length
    val progress = ((this - segment.start).dot(segment.unit) / segment.length).coerceIn(0.0, 1.0)
    val nearest = RouteMeterPoint(
        east = segment.start.east + segment.vector.east * progress,
        north = segment.start.north + segment.vector.north * progress,
    )
    return (this - nearest).length
}

private fun routeDirectionAt(points: List<RouteMeterPoint>, index: Int): RouteMeterPoint {
    val previous = if (index > 0) points[index] - points[index - 1] else null
    val next = if (index < points.lastIndex) points[index + 1] - points[index] else null
    val combined = RouteMeterPoint(
        east = (previous?.east ?: 0.0) + (next?.east ?: 0.0),
        north = (previous?.north ?: 0.0) + (next?.north ?: 0.0),
    )
    if (combined.length >= MIN_ROUTE_SEGMENT_METERS) return combined
    return next?.takeIf { it.length >= MIN_ROUTE_SEGMENT_METERS }
        ?: previous
        ?: RouteMeterPoint(0.0, 0.0)
}

private const val ROUTE_EARTH_RADIUS_METERS = 6_371_000.0
private const val MIN_ROUTE_SEGMENT_METERS = 0.25
private const val OPPOSING_DIRECTION_DOT_LIMIT = -0.94
private const val REQUIRED_OVERLAP_RATIO = 0.25
private const val MIN_REQUIRED_OVERLAP_METERS = 0.75
private const val MAX_REQUIRED_OVERLAP_METERS = 5.0

private class MapViewLifecycleObserver(private val mapView: MapView, private var visible: Boolean) : LifecycleEventObserver {
    private var started = false
    private var resumed = false
    private var destroyed = false
    private var hostState = Lifecycle.State.INITIALIZED

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) destroy() else synchronize(source.lifecycle.currentState)
    }

    fun synchronize(state: Lifecycle.State) {
        hostState = state
        if (state.isAtLeast(Lifecycle.State.STARTED)) start() else stop()
        if (visible && state.isAtLeast(Lifecycle.State.RESUMED)) resume() else pause()
    }

    fun setVisible(next: Boolean) {
        if (visible == next) return
        visible = next
        synchronize(hostState)
    }

    private fun start() {
        if (!started && !destroyed) {
            mapView.onStart()
            started = true
        }
    }

    private fun resume() {
        if (!resumed && !destroyed) {
            start()
            mapView.onResume()
            resumed = true
        }
    }

    private fun pause() {
        if (resumed && !destroyed) {
            mapView.onPause()
            resumed = false
        }
    }

    private fun stop() {
        pause()
        if (started && !destroyed) {
            mapView.onStop()
            started = false
        }
    }

    fun destroy() {
        if (destroyed) return
        stop()
        mapView.onDestroy()
        destroyed = true
    }
}

internal data class MapGesturePolicy(
    val rotateGesturesEnabled: Boolean,
    val tiltGesturesEnabled: Boolean,
)

internal fun mapGesturePolicy(isInteractive: Boolean): MapGesturePolicy = MapGesturePolicy(
    rotateGesturesEnabled = isInteractive,
    tiltGesturesEnabled = isInteractive,
)

internal fun locationTrackingModeAfterUserCameraMove(
    currentMode: LocationTrackingMode,
): LocationTrackingMode = when (currentMode) {
    LocationTrackingMode.None -> LocationTrackingMode.None
    else -> LocationTrackingMode.NoFollow
}

internal data class FacilityMarkerStyle(
    val iconResource: Int,
    val widthDp: Int,
    val heightDp: Int,
    val anchorY: Float,
    val captionTextSizeSp: Float,
    val captionColor: Int,
)

internal data class FacilityMarkerCollisionPolicy(
    val hideCollidedMarkers: Boolean,
    val forceShowIcon: Boolean,
    val zIndex: Int,
)

internal enum class FacilityMarkerAppearance {
    SELECTED_PIN,
    SEARCH_PIN,
    CATEGORY_BADGE,
}

internal enum class FacilityScaleTier {
    LARGE,
    MEDIUM,
    SMALL,
    UNKNOWN,
}

internal data class FacilityMarkerLayer(
    val appearance: FacilityMarkerAppearance,
    val minZoom: Double,
    val captionMinZoom: Double,
    val maxZoom: Double? = null,
    val isMaxZoomInclusive: Boolean = true,
)

internal fun facilityMarkerLayers(
    selected: Boolean,
    searchActive: Boolean,
    category: FacilityCategory,
    grossFloorAreaSquareMeters: Double?,
): List<FacilityMarkerLayer> = when {
    selected -> listOf(
        FacilityMarkerLayer(
            appearance = FacilityMarkerAppearance.SELECTED_PIN,
            minZoom = MapConstants.MIN_ZOOM_KOREA,
            captionMinZoom = MapConstants.MIN_ZOOM_KOREA,
        ),
    )
    searchActive -> listOf(
        FacilityMarkerLayer(
            appearance = FacilityMarkerAppearance.SEARCH_PIN,
            minZoom = MapConstants.MIN_ZOOM_KOREA,
            captionMinZoom = SEARCH_RESULT_CAPTION_MIN_ZOOM,
        ),
    )
    else -> listOf(
        FacilityMarkerLayer(
            appearance = FacilityMarkerAppearance.CATEGORY_BADGE,
            minZoom = facilityBrowseMinimumZoom(category, grossFloorAreaSquareMeters),
            captionMinZoom = facilityBrowseCaptionMinimumZoom(
                category,
                grossFloorAreaSquareMeters,
            ),
        ),
    )
}

internal data class FacilityAreaThresholds(
    val mediumMinimumSquareMeters: Double,
    val largeMinimumSquareMeters: Double,
)

internal fun facilityAreaThresholds(category: FacilityCategory): FacilityAreaThresholds =
    when (category) {
        FacilityCategory.GYM -> FacilityAreaThresholds(360.0, 1_320.0)
        FacilityCategory.POOL -> FacilityAreaThresholds(2_070.0, 8_140.0)
        FacilityCategory.FIELD -> FacilityAreaThresholds(3_000.0, 22_100.0)
        FacilityCategory.FOOTBALL -> FacilityAreaThresholds(7_140.0, 21_900.0)
        FacilityCategory.BASEBALL -> FacilityAreaThresholds(10_000.0, 30_000.0)
        FacilityCategory.GOLF -> FacilityAreaThresholds(570.0, 9_500.0)
        FacilityCategory.TENNIS -> FacilityAreaThresholds(3_210.0, 9_900.0)
        FacilityCategory.BADMINTON -> FacilityAreaThresholds(1_030.0, 2_730.0)
        FacilityCategory.BASKETBALL -> FacilityAreaThresholds(700.0, 2_470.0)
        FacilityCategory.DANCE -> FacilityAreaThresholds(220.0, 510.0)
        FacilityCategory.ICE -> FacilityAreaThresholds(4_410.0, 28_200.0)
        FacilityCategory.OTHER -> FacilityAreaThresholds(310.0, 1_030.0)
    }

internal fun facilityScaleTier(
    category: FacilityCategory,
    grossFloorAreaSquareMeters: Double?,
): FacilityScaleTier = when {
    grossFloorAreaSquareMeters == null -> FacilityScaleTier.UNKNOWN
    grossFloorAreaSquareMeters >= facilityAreaThresholds(category).largeMinimumSquareMeters ->
        FacilityScaleTier.LARGE
    grossFloorAreaSquareMeters >= facilityAreaThresholds(category).mediumMinimumSquareMeters ->
        FacilityScaleTier.MEDIUM
    else -> FacilityScaleTier.SMALL
}

internal fun facilityBrowseMinimumZoom(
    category: FacilityCategory,
    grossFloorAreaSquareMeters: Double?,
): Double =
    when (facilityScaleTier(category, grossFloorAreaSquareMeters)) {
        FacilityScaleTier.LARGE -> LARGE_FACILITY_MIN_ZOOM
        FacilityScaleTier.MEDIUM -> MEDIUM_FACILITY_MIN_ZOOM
        FacilityScaleTier.SMALL -> SMALL_FACILITY_MIN_ZOOM
        FacilityScaleTier.UNKNOWN -> UNKNOWN_SCALE_FACILITY_MIN_ZOOM
    }

internal fun facilityBrowseCaptionMinimumZoom(
    category: FacilityCategory,
    grossFloorAreaSquareMeters: Double?,
): Double =
    when (facilityScaleTier(category, grossFloorAreaSquareMeters)) {
        FacilityScaleTier.LARGE -> LARGE_FACILITY_CAPTION_MIN_ZOOM
        FacilityScaleTier.MEDIUM -> MEDIUM_FACILITY_CAPTION_MIN_ZOOM
        FacilityScaleTier.SMALL -> SMALL_FACILITY_CAPTION_MIN_ZOOM
        FacilityScaleTier.UNKNOWN -> UNKNOWN_SCALE_FACILITY_CAPTION_MIN_ZOOM
    }

internal fun facilityMarkerCollisionPolicy(
    selected: Boolean,
    searchResult: Boolean = false,
    category: FacilityCategory = FacilityCategory.OTHER,
    grossFloorAreaSquareMeters: Double? = null,
    rankedIndex: Int,
    facilityCount: Int,
): FacilityMarkerCollisionPolicy = FacilityMarkerCollisionPolicy(
    hideCollidedMarkers = true,
    forceShowIcon = selected,
    zIndex = if (selected) {
        SELECTED_FACILITY_MARKER_Z_INDEX
    } else if (searchResult) {
        SEARCH_FACILITY_MARKER_Z_INDEX_BASE +
            (facilityCount - rankedIndex).coerceIn(1, FACILITY_MARKER_RANK_PRIORITY_LIMIT)
    } else {
        facilityScalePriorityBase(category, grossFloorAreaSquareMeters) +
            (facilityCount - rankedIndex).coerceIn(1, FACILITY_MARKER_RANK_PRIORITY_LIMIT)
    },
)

private fun facilityScalePriorityBase(
    category: FacilityCategory,
    grossFloorAreaSquareMeters: Double?,
): Int =
    when (facilityScaleTier(category, grossFloorAreaSquareMeters)) {
        FacilityScaleTier.LARGE -> LARGE_FACILITY_MARKER_Z_INDEX_BASE
        FacilityScaleTier.MEDIUM -> MEDIUM_FACILITY_MARKER_Z_INDEX_BASE
        FacilityScaleTier.SMALL -> SMALL_FACILITY_MARKER_Z_INDEX_BASE
        FacilityScaleTier.UNKNOWN -> UNKNOWN_SCALE_FACILITY_MARKER_Z_INDEX_BASE
    }

internal fun facilityMarkerStyle(selected: Boolean): FacilityMarkerStyle =
    if (selected) {
        FacilityMarkerStyle(
            iconResource = R.drawable.ic_gymvi_facility_selected_pin,
            widthDp = 32,
            heightDp = 40,
            anchorY = 1f,
            captionTextSizeSp = 11.5f,
            captionColor = 0xFF005A5D.toInt(),
        )
    } else {
        FacilityMarkerStyle(
            iconResource = R.drawable.ic_gymvi_facility_unselected_dot,
            widthDp = UNSELECTED_FACILITY_MARKER_COLLISION_SIZE_DP,
            heightDp = UNSELECTED_FACILITY_MARKER_COLLISION_SIZE_DP,
            anchorY = 0.5f,
            captionTextSizeSp = 9.5f,
            captionColor = 0xFF1E293B.toInt(),
        )
    }

internal fun facilityCategoryMarkerStyle(): FacilityMarkerStyle = FacilityMarkerStyle(
    iconResource = R.drawable.ic_gymvi_facility_category_pin,
    widthDp = 32,
    heightDp = 39,
    anchorY = 1f,
    captionTextSizeSp = 10.5f,
    captionColor = 0xFF004D53.toInt(),
)

internal fun facilityBrowseMarkerStyle(): FacilityMarkerStyle = FacilityMarkerStyle(
    iconResource = R.drawable.ic_gymvi_facility_unselected_dot,
    widthDp = BROWSE_FACILITY_MARKER_COLLISION_SIZE_DP,
    heightDp = BROWSE_FACILITY_MARKER_COLLISION_SIZE_DP,
    anchorY = 0.5f,
    captionTextSizeSp = 9.5f,
    captionColor = 0xFF1E293B.toInt(),
)

internal const val UNSELECTED_FACILITY_MARKER_COLLISION_SIZE_DP = 40
internal const val BROWSE_FACILITY_MARKER_COLLISION_SIZE_DP = 46
internal const val SELECTED_FACILITY_MARKER_Z_INDEX = 1_000_000
internal const val SEARCH_FACILITY_MARKER_Z_INDEX_BASE = 500_000
internal const val LARGE_FACILITY_MARKER_Z_INDEX_BASE = 300_000
internal const val MEDIUM_FACILITY_MARKER_Z_INDEX_BASE = 200_000
internal const val SMALL_FACILITY_MARKER_Z_INDEX_BASE = 100_000
internal const val UNKNOWN_SCALE_FACILITY_MARKER_Z_INDEX_BASE = 0
internal const val FACILITY_MARKER_RANK_PRIORITY_LIMIT = 99_999
internal const val SEARCH_TARGET_MARKER_Z_INDEX = 2_000_000
internal const val LARGE_FACILITY_MIN_ZOOM = 11.0
internal const val MEDIUM_FACILITY_MIN_ZOOM = 15.5
internal const val SMALL_FACILITY_MIN_ZOOM = 16.5
internal const val UNKNOWN_SCALE_FACILITY_MIN_ZOOM = 17.0
internal const val LARGE_FACILITY_CAPTION_MIN_ZOOM = 12.5
internal const val MEDIUM_FACILITY_CAPTION_MIN_ZOOM = 15.5
internal const val SMALL_FACILITY_CAPTION_MIN_ZOOM = 16.5
internal const val UNKNOWN_SCALE_FACILITY_CAPTION_MIN_ZOOM = 17.0
internal const val SEARCH_RESULT_CAPTION_MIN_ZOOM = 13.5
internal const val FACILITY_SEARCH_CAMERA_ANIMATION_DURATION_MILLIS = 900L

internal data class LocationRecenterPlan(
    val shouldRestartTracking: Boolean,
    val latitude: Double?,
    val longitude: Double?,
)

internal fun locationRecenterPlan(
    requestKey: Int,
    handledRequestKey: Int,
    lastKnownLatitude: Double?,
    lastKnownLongitude: Double?,
): LocationRecenterPlan {
    if (requestKey <= handledRequestKey) {
        return LocationRecenterPlan(
            shouldRestartTracking = false,
            latitude = null,
            longitude = null,
        )
    }
    return LocationRecenterPlan(
        shouldRestartTracking = true,
        latitude = lastKnownLatitude.takeIf { lastKnownLongitude != null },
        longitude = lastKnownLongitude.takeIf { lastKnownLatitude != null },
    )
}

internal fun applyKoreaCameraConstraints(options: NaverMapOptions): NaverMapOptions = options
    .extent(MapConstants.EXTENT_KOREA)
    .minZoom(MapConstants.MIN_ZOOM_KOREA)

private fun Context.findComponentActivity(): ComponentActivity {
    var candidate: Context? = this
    while (candidate is ContextWrapper) {
        if (candidate is ComponentActivity) return candidate
        candidate = candidate.baseContext
    }
    error("NaverMapHost requires a ComponentActivity context.")
}

private data class LastKnownMapLocation(
    val latitude: Double,
    val longitude: Double,
)

private fun Context.recentLastKnownMapLocation(): LastKnownMapLocation? {
    val hasPermission =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return null
    val locationManager = getSystemService(LocationManager::class.java) ?: return null
    val nowNanos = SystemClock.elapsedRealtimeNanos()
    return runCatching {
        locationManager.getProviders(true)
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter { location ->
                location.latitude.isFinite() && location.latitude in -90.0..90.0 &&
                    location.longitude.isFinite() && location.longitude in -180.0..180.0 &&
                    nowNanos - location.elapsedRealtimeNanos in
                    0L..MAX_CACHED_LOCATION_AGE_NANOS
            }
            .maxByOrNull { location -> location.elapsedRealtimeNanos }
            ?.let { location ->
                LastKnownMapLocation(location.latitude, location.longitude)
            }
    }.getOrNull()
}

private fun View.setMapAccessibilityEnabled(enabled: Boolean) {
    if (enabled) {
        mapAccessibilitySnapshots.remove(this)?.let { snapshot ->
            importantForAccessibility = snapshot.importantForAccessibility
            contentDescription = snapshot.contentDescription
            isClickable = snapshot.isClickable
            isFocusable = snapshot.isFocusable
        }
    } else {
        mapAccessibilitySnapshots.getOrPut(this) {
            MapAccessibilitySnapshot(
                importantForAccessibility = importantForAccessibility,
                contentDescription = contentDescription,
                isClickable = isClickable,
                isFocusable = isFocusable,
            )
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        contentDescription = null
        isClickable = false
        isFocusable = false
    }
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).setMapAccessibilityEnabled(enabled)
        }
    }
}

private data class MapAccessibilitySnapshot(
    val importantForAccessibility: Int,
    val contentDescription: CharSequence?,
    val isClickable: Boolean,
    val isFocusable: Boolean,
)

private val mapAccessibilitySnapshots = WeakHashMap<View, MapAccessibilitySnapshot>()

private val INITIAL_CENTER = LatLng(37.4931, 127.1439)
private const val INITIAL_ZOOM = 15.0
private const val LOCATION_PERMISSION_REQUEST_CODE = 4_601
private const val MAX_CACHED_LOCATION_AGE_NANOS = 10L * 60L * 1_000_000_000L
