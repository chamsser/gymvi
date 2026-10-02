package io.github.chamsser.gymvi.ui

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.chamsser.gymvi.R
import androidx.core.view.WindowCompat

internal fun nativePageEnter(design: NativeDesign): EnterTransition = fadeIn(tween(design.durationMillis))

internal fun nativePageExit(design: NativeDesign): ExitTransition = fadeOut(tween(design.durationMillis))

/** Only appearance/accessibility preferences survive the retired comparison controls. */
internal fun resolveAppNativeDesign(theme: String?, reduceMotion: Boolean): NativeDesign =
    NativeDesign(
        theme = NativeTheme.entries.firstOrNull { it.name == theme } ?: NativeTheme.SYSTEM,
        reduceMotion = reduceMotion,
    )

@Composable
internal fun rememberAppNativeDesign(): NativeDesign {
    val context = LocalContext.current
    return remember(context) {
        // Legacy variant, accent and expansion keys deliberately have no runtime reader or writer.
        val preferences = context.getSharedPreferences("native-design-lab", Context.MODE_PRIVATE)
        resolveAppNativeDesign(
            theme = preferences.getString("theme", null),
            reduceMotion = preferences.getBoolean("reduceMotion", false),
        )
    }
}

internal fun nativeDesignColors(design: NativeDesign, dark: Boolean): ColorScheme {
    val accent = if (dark) Color(0xFF8FB2FF) else Color(0xFF2563EB)
    val background = if (dark) Color(0xFF111111) else Color.White
    val surface = if (dark) Color(0xFF191919) else Color.White
    val secondarySurface = if (dark) Color(0xFF252525) else Color(0xFFF5F5F5)
    val text = if (dark) Color(0xFFF5F5F5) else Color(0xFF171717)
    val secondaryText = if (dark) Color(0xFFB3B3B3) else Color(0xFF666666)
    val line = if (dark) Color(0xFF383838) else Color(0xFFE5E5E5)
    // Every surface stays neutral. Only the primary container is tinted, so the map's secondary
    // actions show in the accent.
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = accent, onPrimary = if (dark) Color(0xFF111111) else Color.White,
        primaryContainer = lerp(surface, accent, if (dark) .22f else .10f),
        onPrimaryContainer = lerp(accent, text, if (dark) .5f else .35f),
        secondary = secondaryText, onSecondary = background,
        secondaryContainer = secondarySurface, onSecondaryContainer = text,
        tertiary = accent, onTertiary = if (dark) Color(0xFF111111) else Color.White,
        tertiaryContainer = secondarySurface, onTertiaryContainer = text,
        background = background, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = secondarySurface, onSurfaceVariant = secondaryText,
        surfaceTint = Color.Transparent, outline = if (dark) Color(0xFFA3A3A3) else Color(0xFF737373),
        outlineVariant = line, inverseSurface = text, inverseOnSurface = background,
        surfaceDim = background, surfaceBright = surface,
        surfaceContainerLowest = background, surfaceContainerLow = surface,
        surfaceContainer = secondarySurface, surfaceContainerHigh = secondarySurface,
        surfaceContainerHighest = secondarySurface,
    )
}

internal fun nativeDesignTypography(design: NativeDesign): Typography {
    val defaults = Typography()
    return defaults.copy(
        titleLarge = defaults.titleLarge.copy(fontSize = design.style.titleSp.sp, lineHeight = (design.style.titleSp + 7).sp, fontWeight = FontWeight.SemiBold),
        titleMedium = defaults.titleMedium.copy(fontSize = (design.style.bodySp + 1).sp, lineHeight = (design.style.bodySp + 7).sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = defaults.bodyLarge.copy(fontSize = design.style.bodySp.sp, lineHeight = (design.style.bodySp + 8).sp),
        bodyMedium = defaults.bodyMedium.copy(fontSize = (design.style.bodySp - 1).sp, lineHeight = (design.style.bodySp + 6).sp),
        labelLarge = defaults.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelMedium = defaults.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    )
}

/** The bottom tabs as an inset, so content above them pads only for what reaches past them. */
@Stable
private class NativeBottomTabsInsets : WindowInsets {
    var heightPx by mutableIntStateOf(0)

    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
    override fun getTop(density: Density) = 0
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
    override fun getBottom(density: Density) = heightPx
}

/** Application frame. The selected product design cannot be switched at runtime. */
@Composable
internal fun NativeDesignFrame(
    design: NativeDesign,
    selectedMode: RootMode,
    onModeSelected: (RootMode) -> Unit,
    showNavigation: Boolean,
    content: @Composable () -> Unit,
) {
    val bottomTabs = remember { NativeBottomTabsInsets() }
    val navigationBars = WindowInsets.navigationBars
    val bottomTabsArea = remember(navigationBars) { bottomTabs.union(navigationBars) }
    val view = LocalView.current
    val lightSurface = MaterialTheme.colorScheme.surface.luminance() > .5f
    DisposableEffect(view, lightSurface) {
        var owner: Context? = view.context
        while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
        (owner as? Activity)?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = lightSurface
            controller.isAppearanceLightNavigationBars = lightSurface
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        }
        onDispose { }
    }
    // Keep tabs under the keyboard and consume their measured inset in the same layout pass.
    val showBottomTabs = showNavigation
    CompositionLocalProvider(LocalNativeDesign provides design) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .then(if (showBottomTabs) Modifier.consumeWindowInsets(bottomTabsArea) else Modifier),
            ) { content() }
            if (showBottomTabs) {
                // The column measures the tabs before the weighted content.
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.onSizeChanged { bottomTabs.heightPx = it.height },
                ) {
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NativeDesignNavigation(selectedMode, onModeSelected)
                    }
                }
            }
        }
    }
}

@Composable
internal fun NativeDesignNavigation(selectedMode: RootMode, onModeSelected: (RootMode) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup()) {
        RootMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            val label = stringResource(mode.labelRes)
            Surface(
                modifier = Modifier.weight(1f).height(60.dp)
                    .selectable(selected, onClick = { onModeSelected(mode) }, role = Role.Tab).testTag(mode.testTag),
                color = Color.Transparent,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(painterResource(mode.iconRes), null, Modifier.size(22.dp), tint = tint)
                    Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = tint, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}
