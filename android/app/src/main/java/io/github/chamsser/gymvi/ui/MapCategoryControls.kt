package io.github.chamsser.gymvi.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.chamsser.gymvi.R
import io.github.chamsser.gymvi.data.FacilityMapItem

internal enum class FacilityCategory(
    val testTagSuffix: String,
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
    val markerColor: Int,
    private vararg val keywords: String,
) {
    GYM(
        "gym",
        R.string.facility_category_gym,
        R.drawable.ic_material_symbol_fitness_center_24,
        0xFF00796B.toInt(),
        "체육관",
        "체육센터",
        "체육시설",
        "체육도장",
        "체력단련",
        "헬스",
        "피트니스",
        "휘트니스",
        "GYM",
        "짐",
        "유도",
        "태권도",
        "합기도",
    ),
    POOL(
        "pool",
        R.string.facility_category_pool,
        R.drawable.ic_material_symbol_pool_24,
        0xFF0277BD.toInt(),
        "수영",
    ),
    FIELD(
        "field",
        R.string.facility_category_field,
        R.drawable.ic_material_symbol_stadium_24,
        0xFF2E7D32.toInt(),
        "운동장",
    ),
    FOOTBALL(
        "football",
        R.string.facility_category_football,
        R.drawable.ic_material_symbol_sports_soccer_24,
        0xFF2E7D32.toInt(),
        "축구",
        "풋살",
    ),
    BASEBALL(
        "baseball",
        R.string.facility_category_baseball,
        R.drawable.ic_material_symbol_sports_baseball_24,
        0xFFC62828.toInt(),
        "야구",
    ),
    GOLF(
        "golf",
        R.string.facility_category_golf,
        R.drawable.ic_material_symbol_golf_course_24,
        0xFF558B2F.toInt(),
        "골프",
    ),
    TENNIS(
        "tennis",
        R.string.facility_category_tennis,
        R.drawable.ic_material_symbol_sports_tennis_24,
        0xFF6A1B9A.toInt(),
        "테니스",
    ),
    BADMINTON(
        "badminton",
        R.string.facility_category_badminton,
        R.drawable.ic_material_symbol_sports_tennis_24,
        0xFF6A1B9A.toInt(),
        "배드민턴",
    ),
    BASKETBALL(
        "basketball",
        R.string.facility_category_basketball,
        R.drawable.ic_material_symbol_sports_basketball_24,
        0xFFD84315.toInt(),
        "농구",
    ),
    DANCE(
        "dance",
        R.string.facility_category_dance,
        R.drawable.ic_material_symbol_music_note_24,
        0xFFAD1457.toInt(),
        "댄스",
        "무도",
    ),
    ICE(
        "ice",
        R.string.facility_category_ice,
        R.drawable.ic_material_symbol_ice_skating_24,
        0xFF1565C0.toInt(),
        "빙상",
        "스케이트",
    ),
    OTHER(
        "other",
        R.string.facility_category_other,
        R.drawable.ic_material_symbol_fitness_center_24,
        0xFF455A64.toInt(),
    ),
    ;

    val isSearchCategory: Boolean
        get() = this != OTHER

    fun includes(facility: FacilityMapItem): Boolean {
        val searchableFields = listOfNotNull(facility.name, facility.facilityTypeName)
        return keywords.any { keyword ->
            searchableFields.any { field -> field.contains(keyword, ignoreCase = true) }
        }
    }

    fun matchesQuery(query: String): Boolean {
        val normalized = query.trim()
        if (normalized.isEmpty()) return false
        return queryAliases.any { alias -> alias.equals(normalized, ignoreCase = true) }
    }

    private val queryAliases: Set<String>
        get() = when (this) {
            GYM -> setOf("체육관", "헬스", "헬스장", "피트니스", "휘트니스", "GYM", "짐")
            POOL -> setOf("수영", "수영장")
            FIELD -> setOf("운동장")
            FOOTBALL -> setOf("축구", "축구장", "풋살", "풋살장")
            BASEBALL -> setOf("야구", "야구장")
            GOLF -> setOf("골프", "골프장")
            TENNIS -> setOf("테니스", "테니스장")
            BADMINTON -> setOf("배드민턴", "배드민턴장")
            BASKETBALL -> setOf("농구", "농구장")
            DANCE -> setOf("댄스", "무도")
            ICE -> setOf("빙상", "빙상장", "스케이트", "스케이트장")
            OTHER -> emptySet()
        }

    companion object {
        private val facilityClassificationPriority = listOf(
            POOL,
            FOOTBALL,
            BASEBALL,
            GOLF,
            TENNIS,
            BADMINTON,
            BASKETBALL,
            DANCE,
            ICE,
            FIELD,
            GYM,
        )

        fun fromQuery(query: String): FacilityCategory? =
            entries.firstOrNull { category -> category.matchesQuery(query) }

        fun fromFacility(facility: FacilityMapItem): FacilityCategory =
            facilityClassificationPriority.firstOrNull { category -> category.includes(facility) }
                ?: OTHER
    }
}

internal fun filterFacilitiesForSearch(
    facilities: List<FacilityMapItem>,
    query: String,
    selectedCategory: FacilityCategory?,
): List<FacilityMapItem> {
    val normalizedQuery = query.trim()
    val effectiveCategory = selectedCategory ?: FacilityCategory.fromQuery(normalizedQuery)
    return when {
        effectiveCategory != null -> facilities.filter(effectiveCategory::includes)
        normalizedQuery.isEmpty() -> facilities
        else -> facilities.filter { facility ->
            listOfNotNull(
                facility.name,
                facility.facilityTypeName,
                facility.facilityClassName,
                facility.roadAddress,
                facility.description,
            ).any { value -> value.contains(normalizedQuery, ignoreCase = true) }
        }
    }
}

@Composable
internal fun FacilityCategoryBar(
    onCategorySearch: (FacilityCategory, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val design = LocalNativeDesign.current
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (design.usesOriginalMapChrome) 34.dp else 48.dp) {
        LazyRow(
            modifier = modifier
                .fillMaxWidth()
                .testTag("facility-category-bar"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(
                items = FacilityCategory.entries.filter(FacilityCategory::isSearchCategory),
                key = FacilityCategory::name,
            ) { category ->
                val label = stringResource(category.labelRes)
                Surface(
                    onClick = { onCategorySearch(category, label) },
                    modifier = Modifier
                        .height(if (design.usesOriginalMapChrome) 34.dp else 48.dp)
                        .testTag("facility-category-" + category.testTagSuffix)
                        .semantics {
                            role = Role.Button
                        },
                    shape = RoundedCornerShape(if (design.usesOriginalMapChrome) 17.dp else design.style.cornerDp.dp),
                    color = if (design.usesOriginalMapChrome) MaterialTheme.colorScheme.surface.copy(alpha = 0.98f) else MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = gymviSubtleBorderColor(),
                    ),
                    shadowElevation = if (design.usesOriginalMapChrome) 3.dp else if (design.style.surface == NativeSurface.FLOATING) 2.dp else 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(category.iconRes),
                            contentDescription = null,
                            tint = if (design.usesOriginalMapChrome || design.style.surface == NativeSurface.FLOATING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(15.dp)
                                .testTag("facility-category-icon-" + category.testTagSuffix),
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (design.usesOriginalMapChrome) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchThisAreaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.search_this_area)
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(34.dp)
            .testTag("search-this-area"),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        contentColor = MaterialTheme.colorScheme.primary,
        border = BorderStroke(1.dp, gymviSubtleBorderColor()),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_material_symbol_refresh_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
