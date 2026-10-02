package io.github.chamsser.gymvi.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

enum class NativeTheme(val label: String) { SYSTEM("기기 설정"), LIGHT("밝게"), DARK("어둡게") }

@Immutable
data class NativeDesignStyle(
    val cornerDp: Int = 20,
    val bodySp: Int = 16,
    val titleSp: Int = 20,
    val motionMillis: Int = 220,
)

/** The app's single design: bottom tabs, tonal AI and MY screens and the map chrome. */
@Immutable
data class NativeDesign(
    val style: NativeDesignStyle = NativeDesignStyle(),
    val reduceMotion: Boolean = false,
    val theme: NativeTheme = NativeTheme.SYSTEM,
) {
    val durationMillis: Int get() = if (reduceMotion) 0 else style.motionMillis
}

val LocalNativeDesign = staticCompositionLocalOf { NativeDesign() }
