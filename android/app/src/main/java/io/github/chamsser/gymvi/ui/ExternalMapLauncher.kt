package io.github.chamsser.gymvi.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.chamsser.gymvi.BuildConfig
import io.github.chamsser.gymvi.R
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

internal enum class ExternalMapProvider {
    NAVER,
    GOOGLE,
    KAKAO,
}

internal data class ExternalRouteTarget(
    val coordinate: RouteCoordinate,
    val name: String,
) {
    init {
        require(name.isNotBlank())
    }
}

internal fun externalRouteUrl(
    provider: ExternalMapProvider,
    origin: ExternalRouteTarget,
    destination: ExternalRouteTarget,
    appName: String = BuildConfig.APPLICATION_ID,
): String = when (provider) {
    ExternalMapProvider.NAVER -> buildRouteUrl(
        base = "nmap://navigation",
        parameters = listOf(
            "slat" to origin.coordinate.latitude.asUriCoordinate(),
            "slng" to origin.coordinate.longitude.asUriCoordinate(),
            "sname" to origin.name,
            "dlat" to destination.coordinate.latitude.asUriCoordinate(),
            "dlng" to destination.coordinate.longitude.asUriCoordinate(),
            "dname" to destination.name,
            "appname" to appName,
        ),
    )

    ExternalMapProvider.GOOGLE -> buildRouteUrl(
        base = "https://www.google.com/maps/dir/",
        parameters = listOf(
            "api" to "1",
            "origin" to origin.coordinate.asLatLng(),
            "destination" to destination.coordinate.asLatLng(),
            "travelmode" to "driving",
            "dir_action" to "navigate",
        ),
    )

    ExternalMapProvider.KAKAO -> buildRouteUrl(
        base = "kakaomap://route",
        parameters = listOf(
            "sp" to origin.coordinate.asLatLng(),
            "ep" to destination.coordinate.asLatLng(),
            "by" to "car",
        ),
    )
}

internal fun openExternalRoute(
    context: Context,
    provider: ExternalMapProvider,
    origin: ExternalRouteTarget,
    destination: ExternalRouteTarget,
) {
    val primaryUri = Uri.parse(externalRouteUrl(provider, origin, destination))
    val primaryIntent = Intent(Intent.ACTION_VIEW, primaryUri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        when (provider) {
            ExternalMapProvider.NAVER -> setPackage("com.nhn.android.nmap")
            ExternalMapProvider.GOOGLE -> setPackage("com.google.android.apps.maps")
            ExternalMapProvider.KAKAO -> setPackage("net.daum.android.map")
        }
    }
    try {
        context.startActivity(primaryIntent)
        return
    } catch (_: ActivityNotFoundException) {
        // Provider-specific browser fallbacks remain user-initiated and contain no API key.
    }

    val fallbackUri = when (provider) {
        ExternalMapProvider.NAVER -> Uri.parse(
            "https://play.google.com/store/apps/details?id=com.nhn.android.nmap",
        )

        ExternalMapProvider.GOOGLE -> primaryUri
        ExternalMapProvider.KAKAO -> Uri.parse(
            buildRouteUrl(
                base = "https://m.map.kakao.com/scheme/route",
                parameters = listOf(
                    "sp" to origin.coordinate.asLatLng(),
                    "ep" to destination.coordinate.asLatLng(),
                    "by" to "car",
                ),
            ),
        )
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, fallbackUri).addCategory(Intent.CATEGORY_BROWSABLE),
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.external_map_unavailable, Toast.LENGTH_SHORT).show()
    }
}

private fun RouteCoordinate.asLatLng(): String =
    "${latitude.asUriCoordinate()},${longitude.asUriCoordinate()}"

private fun Double.asUriCoordinate(): String = String.format(Locale.ROOT, "%.7f", this)

private fun buildRouteUrl(base: String, parameters: List<Pair<String, String>>): String =
    parameters.joinToString(prefix = "$base?", separator = "&") { (name, value) ->
        "${name.urlEncoded()}=${value.urlEncoded()}"
    }

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")
