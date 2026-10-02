package io.github.chamsser.gymvi.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.FacilityMapItem
import io.github.chamsser.gymvi.data.isTrustedNaverImageSearchUrl
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun naverFacilityImageSearchUrl(facility: FacilityMapItem): String {
    val query = listOfNotNull(
        facility.name,
        facility.roadAddress ?: facility.lotAddress,
        facility.facilityTypeName,
    ).map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(" ")
        .take(240)
    val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
    return "https://m.search.naver.com/search.naver?where=m_image&query=$encodedQuery"
}

internal fun openNaverImageSearch(
    context: Context,
    facility: FacilityMapItem,
    providerUrl: String?,
) {
    val targetUrl = providerUrl
        ?.takeIf(::isTrustedNaverImageSearchUrl)
        ?: naverFacilityImageSearchUrl(facility)
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                .addCategory(Intent.CATEGORY_BROWSABLE),
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.facility_media_browser_unavailable, Toast.LENGTH_SHORT)
            .show()
    }
}
