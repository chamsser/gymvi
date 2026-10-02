package io.github.chamsser.gymvi.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

enum class NativeDesignVariant(val label: String, val description: String) {
    COMBINED("통합안", "사용자가 고른 조합, 설치 직후 기본 화면"),
    ORIGINAL("원본", "현재 Gymvi"),
    CLAUDE_A("Claude A", "하단 탭, 선으로 구분하는 화면"),
    CLAUDE_B("Claude B", "상단 전환, 떠 있는 카드"),
    CLAUDE_C("Claude C", "대화 중심, 큰 면과 부드러운 전환"),
    GPT_A("GPT A", "하단 탭, 간결한 지도와 상세"),
    GPT_B("GPT B", "대화 중심, 가로로 비교하는 후보"),
    GPT_C("GPT C", "상단 탭, 촘촘한 지도와 비교 목록"),
}

enum class NativeNavigation { BOTTOM_TABS, FLOATING_SWITCH, CHAT_HOME, TOP_TABS }
enum class NativeSurface { LINES, FLOATING, TONAL, COMPACT }
enum class NativeActions { PAIR, DUAL_PRIMARY, SINGLE, COMPACT }
enum class NativeAccent(val label: String) { BLUE("파랑"), GREEN("초록"), VIOLET("보라"), BLACK("무채색") }
enum class NativeTheme(val label: String) { SYSTEM("기기 설정"), LIGHT("밝게"), DARK("어둡게") }

@Immutable
data class NativeDesignStyle(
    val navigation: NativeNavigation,
    val surface: NativeSurface,
    val actions: NativeActions,
    val cornerDp: Int,
    val bodySp: Int,
    val titleSp: Int,
    val motionMillis: Int,
)

@Immutable
data class NativeDesign(
    val variant: NativeDesignVariant = NativeDesignVariant.ORIGINAL,
    val style: NativeDesignStyle = nativeDesignStyle(variant),
    val reduceMotion: Boolean = false,
    val accent: NativeAccent = NativeAccent.BLUE,
    val theme: NativeTheme = NativeTheme.SYSTEM,
) {
    val isOriginal: Boolean get() = variant == NativeDesignVariant.ORIGINAL

    /**
     * The map keeps the original search bar, category chips, controls and facility sheet chrome.
     * The combined design takes only its bottom tabs, colors and AI and MY screens from the
     * candidates, so map code asks this instead of [isOriginal].
     */
    val usesOriginalMapChrome: Boolean get() = isOriginal || variant == NativeDesignVariant.COMBINED
    val durationMillis: Int get() = if (reduceMotion) 0 else style.motionMillis
    val homeMode: RootMode get() = if (!isOriginal && style.navigation == NativeNavigation.CHAT_HOME) RootMode.AI else RootMode.MAP
}

val LocalNativeDesign = staticCompositionLocalOf { NativeDesign() }

/** The design a fresh install shows: the combination the user picked from the comparison. */
internal val DefaultNativeDesignVariant = NativeDesignVariant.COMBINED

internal fun nativeDesignStyle(variant: NativeDesignVariant): NativeDesignStyle = when (variant) {
    // Claude A bottom tabs, Claude C tone steps for AI and MY, and the original map chrome.
    NativeDesignVariant.COMBINED -> NativeDesignStyle(NativeNavigation.BOTTOM_TABS, NativeSurface.TONAL, NativeActions.PAIR, 20, 16, 20, 220)
    NativeDesignVariant.ORIGINAL -> NativeDesignStyle(NativeNavigation.FLOATING_SWITCH, NativeSurface.FLOATING, NativeActions.PAIR, 28, 16, 22, 240)
    NativeDesignVariant.CLAUDE_A, NativeDesignVariant.CLAUDE_B, NativeDesignVariant.CLAUDE_C -> claudeNativeStyle(variant)
    NativeDesignVariant.GPT_A -> NativeDesignStyle(NativeNavigation.BOTTOM_TABS, NativeSurface.TONAL, NativeActions.COMPACT, 12, 16, 20, 210)
    NativeDesignVariant.GPT_B -> NativeDesignStyle(NativeNavigation.CHAT_HOME, NativeSurface.TONAL, NativeActions.SINGLE, 24, 17, 26, 360)
    NativeDesignVariant.GPT_C -> NativeDesignStyle(NativeNavigation.TOP_TABS, NativeSurface.COMPACT, NativeActions.COMPACT, 8, 14, 18, 280)
}
