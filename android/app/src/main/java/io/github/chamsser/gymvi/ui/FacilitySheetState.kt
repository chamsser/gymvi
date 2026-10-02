package io.github.chamsser.gymvi.ui

import kotlin.math.abs

enum class FacilitySheetStage {
    COLLAPSED,
    STAGE_1,
    STAGE_2,
    ;

    fun nextForTap(): FacilitySheetStage = when (this) {
        COLLAPSED -> STAGE_1
        STAGE_1 -> STAGE_2
        STAGE_2 -> STAGE_1
    }

    fun adjacentInDirection(velocityPxPerSecond: Float): FacilitySheetStage = when {
        velocityPxPerSecond < 0f -> when (this) {
            COLLAPSED -> STAGE_1
            STAGE_1 -> STAGE_2
            STAGE_2 -> STAGE_2
        }

        velocityPxPerSecond > 0f -> when (this) {
            COLLAPSED -> COLLAPSED
            STAGE_1 -> COLLAPSED
            STAGE_2 -> STAGE_1
        }

        else -> this
    }
}

data class FacilitySheetAnchors(
    val stage2OffsetPx: Float,
    val stage1OffsetPx: Float,
    val collapsedOffsetPx: Float,
) {
    init {
        require(stage2OffsetPx <= stage1OffsetPx) {
            "The expanded sheet anchor must be above stage 1."
        }
        require(stage1OffsetPx <= collapsedOffsetPx) {
            "Stage 1 must be above the collapsed sheet anchor."
        }
    }

    fun offsetFor(stage: FacilitySheetStage): Float = when (stage) {
        FacilitySheetStage.COLLAPSED -> collapsedOffsetPx
        FacilitySheetStage.STAGE_1 -> stage1OffsetPx
        FacilitySheetStage.STAGE_2 -> stage2OffsetPx
    }

    fun coerce(offsetPx: Float): Float = offsetPx.coerceIn(stage2OffsetPx, collapsedOffsetPx)
}

fun settleFacilitySheetStage(
    currentStage: FacilitySheetStage,
    offsetPx: Float,
    velocityPxPerSecond: Float,
    anchors: FacilitySheetAnchors,
    flingThresholdPxPerSecond: Float = DEFAULT_SHEET_FLING_THRESHOLD_PX_PER_SECOND,
): FacilitySheetStage {
    if (abs(velocityPxPerSecond) >= flingThresholdPxPerSecond) {
        return currentStage.adjacentInDirection(velocityPxPerSecond)
    }

    val candidates = buildList {
        add(currentStage)
        val above = currentStage.adjacentInDirection(-1f)
        val below = currentStage.adjacentInDirection(1f)
        if (above != currentStage) add(above)
        if (below != currentStage) add(below)
    }

    return candidates.minBy { stage ->
        abs(anchors.offsetFor(stage) - anchors.coerce(offsetPx))
    }
}

const val DEFAULT_SHEET_FLING_THRESHOLD_PX_PER_SECOND = 1_250f

/** Follow the visible sheet continuously, capped where the remaining map becomes too small. */
internal fun facilitySheetMapClearancePx(expandedHeightPx: Float, offsetPx: Float, stage1HeightPx: Float): Float =
    (expandedHeightPx - offsetPx).coerceIn(0f, stage1HeightPx)
