package io.github.chamsser.gymvi.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Styles of the three Claude comparison candidates. A is flat with labelled bottom tabs, B floats
 * controls over a light canvas and switches between AI and the map, C makes the conversation the
 * home and separates areas by tone alone.
 */
internal fun claudeNativeStyle(variant: NativeDesignVariant): NativeDesignStyle = when (variant) {
    NativeDesignVariant.CLAUDE_A -> NativeDesignStyle(
        navigation = NativeNavigation.BOTTOM_TABS,
        surface = NativeSurface.LINES,
        actions = NativeActions.PAIR,
        cornerDp = 12,
        bodySp = 15,
        titleSp = 20,
        motionMillis = 180,
    )

    NativeDesignVariant.CLAUDE_B -> NativeDesignStyle(
        navigation = NativeNavigation.FLOATING_SWITCH,
        surface = NativeSurface.FLOATING,
        actions = NativeActions.DUAL_PRIMARY,
        cornerDp = 20,
        bodySp = 16,
        titleSp = 20,
        motionMillis = 280,
    )

    NativeDesignVariant.CLAUDE_C -> NativeDesignStyle(
        navigation = NativeNavigation.CHAT_HOME,
        surface = NativeSurface.TONAL,
        actions = NativeActions.SINGLE,
        cornerDp = 24,
        bodySp = 17,
        titleSp = 22,
        motionMillis = 400,
    )

    else -> throw IllegalArgumentException("$variant is not a Claude candidate")
}

/** How an AI answer arranges its usage-option cards. */
internal enum class AiCardLayout {
    /** One full card per option, stacked in the answer. */
    STACK,

    /** Options side by side in a horizontal strip, with the chosen one's actions below. */
    HORIZONTAL,

    /** Dense numbered rows for comparison, with the chosen one's actions in a tray. */
    COMPARISON,
}

internal fun aiCardLayout(design: NativeDesign): AiCardLayout = when (design.variant) {
    NativeDesignVariant.GPT_B -> AiCardLayout.HORIZONTAL
    NativeDesignVariant.GPT_C -> AiCardLayout.COMPARISON
    else -> AiCardLayout.STACK
}

/** Decides which option in an answer is chosen: the saved choice if still present, else the first. */
internal fun resolveChosenOptionId(optionIds: List<String>, savedId: String?): String? =
    savedId?.takeIf { it in optionIds } ?: optionIds.firstOrNull()

internal fun <T> nativeMotionSpec(design: NativeDesign): AnimationSpec<T> =
    if (design.durationMillis == 0) snap() else tween(design.durationMillis)

/** Colors, shapes and type that a candidate design applies to the AI and MY screens. */
@Immutable
internal data class CandidateScreenTokens(
    val background: Color,
    val cardColor: Color,
    val cardBorder: BorderStroke?,
    val cardShape: Shape,
    val groupColor: Color,
    val composerColor: Color,
    val composerBorder: BorderStroke?,
    val composerElevation: Dp,
    val composerShape: Shape,
    val userBubbleColor: Color,
    val userBubbleContentColor: Color,
    val userBubbleShape: Shape,
    val chipColor: Color,
    val chipBorder: BorderStroke?,
    val buttonShape: Shape,
    val neutralButtonColor: Color,
    val sectionLabelColor: Color,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val support: TextStyle,
    val meta: TextStyle,
    val rowMinHeight: Dp,
)

@Composable
internal fun candidateScreenTokens(style: NativeDesignStyle): CandidateScreenTokens {
    val design = LocalNativeDesign.current
    val colors = MaterialTheme.colorScheme
    val corner = style.cornerDp.dp
    val divider = BorderStroke(1.dp, colors.outlineVariant)
    val tone = colors.surfaceVariant.copy(alpha = 0.6f)
    val body = MaterialTheme.typography.bodyLarge.copy(
        fontSize = style.bodySp.sp,
        lineHeight = (style.bodySp * 1.5f).sp,
        letterSpacing = 0.sp,
        // Phrase breaking keeps Korean words whole instead of splitting a syllable run across lines.
        // It follows the text locale, so the Korean copy declares it even on a device set to English.
        localeList = LocaleList("ko-KR"),
        lineBreak = LineBreak(
            strategy = LineBreak.Strategy.HighQuality,
            strictness = LineBreak.Strictness.Strict,
            wordBreak = LineBreak.WordBreak.Phrase,
        ),
    )
    val common = CandidateScreenTokens(
        background = colors.surface,
        cardColor = colors.surface,
        cardBorder = divider,
        cardShape = RoundedCornerShape(corner),
        groupColor = colors.surface,
        composerColor = colors.surface,
        composerBorder = BorderStroke(1.dp, colors.outline),
        composerElevation = 0.dp,
        composerShape = RoundedCornerShape(corner),
        userBubbleColor = colors.surfaceVariant,
        userBubbleContentColor = colors.onSurface,
        userBubbleShape = RoundedCornerShape(corner),
        chipColor = colors.surface,
        chipBorder = divider,
        buttonShape = RoundedCornerShape(percent = 50),
        neutralButtonColor = colors.surfaceVariant,
        sectionLabelColor = colors.onSurfaceVariant,
        title = MaterialTheme.typography.titleLarge.copy(
            fontSize = style.titleSp.sp,
            lineHeight = (style.titleSp * 1.4f).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        ),
        headline = MaterialTheme.typography.headlineMedium.copy(
            fontSize = (style.titleSp + 6).sp,
            lineHeight = ((style.titleSp + 6) * 1.3f).sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        ),
        body = body,
        support = body.copy(fontSize = (style.bodySp - 1).sp, lineHeight = ((style.bodySp - 1) * 1.45f).sp),
        meta = MaterialTheme.typography.labelMedium.copy(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
        ),
        rowMinHeight = 52.dp,
    )
    val tokens = when (style.surface) {
        // Flat: hairlines and space do the separating; nothing casts a shadow.
        NativeSurface.LINES -> common

        // A light canvas with borderless white cards; only the composer floats.
        NativeSurface.FLOATING -> common.copy(
            background = colors.background,
            cardBorder = null,
            composerBorder = null,
            composerElevation = 6.dp,
            composerShape = RoundedCornerShape(percent = 50),
            userBubbleColor = colors.primaryContainer,
            userBubbleContentColor = colors.onPrimaryContainer,
            chipBorder = null,
            sectionLabelColor = colors.primary,
        )

        // Tone steps only: no borders, no shadows, large soft corners.
        NativeSurface.TONAL -> common.copy(
            cardColor = tone,
            cardBorder = null,
            groupColor = tone,
            composerColor = colors.surfaceVariant,
            composerBorder = null,
            composerShape = RoundedCornerShape(percent = 50),
            userBubbleShape = RoundedCornerShape(corner),
            chipColor = tone,
            chipBorder = null,
        )

        // Dense rows with square-ish corners for side-by-side reading.
        NativeSurface.COMPACT -> common.copy(
            buttonShape = RoundedCornerShape(corner),
            composerShape = RoundedCornerShape(corner),
            rowMinHeight = 48.dp,
        )
    }
    return when (design.variant) {
        NativeDesignVariant.GPT_A -> tokens.copy(
            buttonShape = RoundedCornerShape(12.dp),
            composerShape = RoundedCornerShape(12.dp),
            userBubbleShape = RoundedCornerShape(12.dp),
        )

        // Claude C tone steps with Claude B's blue section labels.
        NativeDesignVariant.COMBINED -> tokens.copy(sectionLabelColor = colors.primary)
        else -> tokens
    }
}
