package com.opentasker.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import android.os.Build
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Quiet Workshop palette with a customizable purple accent and deep plum/graphite surfaces.
private val BrandPurple = Color(0xFFB69CFF)
private val BrandPurpleBright = Color(0xFFDCCBFF)
private val SignalAmber = Color(0xFFFFB42E)
private val SignalRed = Color(0xFFFF6B66)
private val Text = Color(0xFFF7FAFF)
private val TextSecondary = Color(0xFFA7B3C6)
private val WorkshopBackground = Color(0xFF090710)
private val WorkshopSurface = Color(0xFF100D19)
private val WorkshopNavy = Color(0xFF1A1426)
private val WorkshopElevated = Color(0xFF251C35)
private val WorkshopOutline = Color(0xFF514365)

private val Graphite = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color(0xFF211536),
    primaryContainer = Color(0xFF352451),
    onPrimaryContainer = Color(0xFFF0E5FF),
    secondary = SignalAmber,
    onSecondary = Color(0xFF2A1A00),
    secondaryContainer = Color(0xFF3C2A08),
    onSecondaryContainer = Color(0xFFFFE0A0),
    tertiary = BrandPurpleBright,
    onTertiary = Color(0xFF241638),
    tertiaryContainer = Color(0xFF3B2855),
    onTertiaryContainer = Color(0xFFF0E5FF),
    error = SignalRed,
    onError = Color(0xFF35000A),
    errorContainer = Color(0xFF47131D),
    onErrorContainer = Color(0xFFFFD9DE),
    background = WorkshopBackground,
    onBackground = Text,
    surface = WorkshopSurface,
    onSurface = Text,
    surfaceVariant = WorkshopNavy,
    onSurfaceVariant = TextSecondary,
    outline = WorkshopOutline,
    outlineVariant = Color(0xFF30243F),
    // AlertDialog uses surfaceContainerHigh and DropdownMenu uses surfaceContainer. Leaving these
    // unset fell back to Material's purple-tinted baseline tokens, so every editor dialog and menu
    // in this dialog-heavy app rendered off-palette.
    surfaceContainerLowest = WorkshopBackground,
    surfaceContainerLow = WorkshopNavy,
    surfaceContainer = WorkshopSurface,
    surfaceContainerHigh = WorkshopElevated,
    surfaceContainerHighest = Color(0xFF38294B),
    surfaceBright = Color(0xFF49365F),
    surfaceDim = WorkshopBackground,
)

/**
 * True-black variant of [Graphite].
 *
 * The scheme previously called "Amoled" used #101211, which lights every background pixel — the
 * saving an OLED user asks for only happens at #000000. Accent and text colours are unchanged, so
 * body text keeps 19:1 on black and secondary text ~8.8:1.
 */
private val Amoled = darkColorScheme(
    primary = BrandPurple,
    onPrimary = Color(0xFF211536),
    primaryContainer = Color(0xFF352451),
    onPrimaryContainer = Color(0xFFF0E5FF),
    secondary = SignalAmber,
    onSecondary = Color(0xFF2A1A00),
    secondaryContainer = Color(0xFF342507),
    onSecondaryContainer = Color(0xFFFFE0A0),
    tertiary = BrandPurpleBright,
    onTertiary = Color(0xFF241638),
    tertiaryContainer = Color(0xFF3B2855),
    onTertiaryContainer = Color(0xFFF0E5FF),
    error = SignalRed,
    onError = Color(0xFF35000A),
    errorContainer = Color(0xFF360E16),
    onErrorContainer = Color(0xFFFFD9DE),
    background = Color.Black,
    onBackground = Text,
    surface = Color.Black,
    onSurface = Text,
    // Cards and dialogs need to separate from the background; keep them as dark as legibility allows.
    surfaceVariant = WorkshopNavy,
    onSurfaceVariant = TextSecondary,
    outline = WorkshopOutline,
    // Raised from #1B1F1D: against pure black, alpha-washed at the call sites, the old value
    // landed near 1.07:1 and card and section boundaries simply vanished. Text was never the
    // problem here - the grouping cues were.
    outlineVariant = Color(0xFF30243F),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = WorkshopNavy,
    surfaceContainer = Color(0xFF080610),
    surfaceContainerHigh = WorkshopElevated,
    surfaceContainerHighest = Color(0xFF38294B),
    surfaceBright = Color(0xFF49365F),
    surfaceDim = Color.Black,
)

// Catppuccin Latte palette for the light theme. Mirrors the dark scheme's structure so every
// semantic token (including the *Container and outlineVariant slots the UI relies on) is defined
// and coherent in both modes, rather than falling back to mismatched Material defaults.
private val LightPurple = Color(0xFF7652A8)
private val LightAmber = Color(0xFF775500)
private val LightPurpleSecondary = Color(0xFF7652A8)
private val LightRed = Color(0xFFA34141)
private val LightText = Color(0xFF211A2A)
private val LightSubtext = Color(0xFF655A70)
private val LightBase = Color(0xFFF7F3FA)
private val LightSurface = Color(0xFFFFFFFF)
private val LightElevated = Color(0xFFEEE7F5)
private val LightOutline = Color(0xFF85768F)

private val Light = lightColorScheme(
    primary = LightPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8D9FF),
    onPrimaryContainer = Color(0xFF35204F),
    secondary = LightAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7AC),
    onSecondaryContainer = Color(0xFF3C2B00),
    tertiary = LightPurpleSecondary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8D9FF),
    onTertiaryContainer = Color(0xFF35204F),
    error = LightRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFDADD),
    onErrorContainer = Color(0xFF6D001A),
    background = LightBase,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightElevated,
    onSurfaceVariant = LightSubtext,
    outline = LightOutline,
    outlineVariant = Color(0xFFE2D8EB),
    // Without these, dialogs and menus fall back to Material's lavender baseline
    // containers, which read as clearly off-palette against the warm cream surfaces.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = LightSurface,
    surfaceContainer = Color(0xFFF3EDF8),
    surfaceContainerHigh = LightElevated,
    surfaceContainerHighest = Color(0xFFE7DDEE),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFEAE2F0),
)

private val HighContrast = darkColorScheme(
    primary = Color(0xFFFFFF00),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF303000),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFD0B5FF),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF352451),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF00E676),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF003A1D),
    onTertiaryContainer = Color.White,
    error = Color(0xFFFF5252),
    onError = Color.Black,
    errorContainer = Color(0xFF4A0000),
    onErrorContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF171717),
    onSurfaceVariant = Color(0xFFF5F5F5),
    outline = Color.White,
    outlineVariant = Color(0xFFE0E0E0),
    // High contrast keeps dialogs black rather than Material's #2B2930 baseline.
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color(0xFF0D0D0D),
    surfaceContainerHighest = Color(0xFF171717),
    surfaceBright = Color(0xFF171717),
    surfaceDim = Color.Black,
)

private val OpenTaskerTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 31.sp,
        lineHeight = 37.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
    ),
)

private val OpenTaskerShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
)

/**
 * Applies a persisted [ThemeMode].
 *
 * Every entry point took the mode apart itself, so each of the five copies had to be found and
 * updated whenever a mode was added — and adding one broke four activities at compile time. The
 * mapping lives here now.
 */
@Composable
fun OpenTaskerTheme(
    themeMode: ThemeMode,
    accentColorHex: String = ThemePreference.DEFAULT_ACCENT_HEX,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Dark, ThemeMode.Amoled, ThemeMode.HighContrast -> true
        ThemeMode.Light -> false
        ThemeMode.System, ThemeMode.Dynamic -> isSystemInDarkTheme()
    }
    OpenTaskerTheme(
        darkTheme = darkTheme,
        highContrast = themeMode == ThemeMode.HighContrast,
        amoled = themeMode == ThemeMode.Amoled,
        dynamicColor = themeMode == ThemeMode.Dynamic,
        accentColorHex = accentColorHex,
        content = content,
    )
}

@Composable
fun OpenTaskerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = false,
    amoled: Boolean = false,
    dynamicColor: Boolean = false,
    accentColorHex: String = ThemePreference.DEFAULT_ACCENT_HEX,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        highContrast -> HighContrast
        // Dynamic colour is opt-in and platform-gated; below API 31 it degrades to the static
        // scheme rather than silently doing nothing different.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        amoled && darkTheme -> Amoled
        darkTheme -> Graphite
        else -> Light
    }
    val accent = runCatching { Color(android.graphics.Color.parseColor(accentColorHex)) }.getOrDefault(BrandPurple)
    // High-contrast mode keeps its explicit accessibility palette; all other modes derive a
    // coordinated set of tones from the single user-selected accent.
    val adjustedColors = if (highContrast) colors else colors.withOpenTaskerAccent(accent, darkTheme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Under edge-to-edge (target SDK 35+) window.statusBarColor/navigationBarColor are
            // deprecated no-ops; only the appearance flags still control bar icon contrast.
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme && !highContrast
                isAppearanceLightNavigationBars = !darkTheme && !highContrast
            }
        }
    }
    MaterialTheme(
        colorScheme = adjustedColors,
        typography = OpenTaskerTypography,
        shapes = OpenTaskerShapes,
        content = content,
    )
}

/**
 * Opaque selected-state fill for chips and rows. The container tint is composited over the surface so
 * a selection reads as a distinct, solid fill in both the AMOLED and light themes, instead of the
 * alpha-on-alpha wash that left selected chips distinguishable only by their border.
 */
@Composable
fun selectedContainerColor(): Color =
    MaterialTheme.colorScheme.primary
        .copy(alpha = 0.22f)
        .compositeOver(MaterialTheme.colorScheme.surface)


private val AccentInk = Color(0xFF211536)

private data class AccentHsl(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
)

private fun Color.toAccentHsl(): AccentHsl {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    val lightness = (max + min) / 2f
    if (delta == 0f) return AccentHsl(0f, 0f, lightness)

    val saturation = delta / (1f - kotlin.math.abs(2f * lightness - 1f))
    val hue = when (max) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return AccentHsl(hue, saturation.coerceIn(0f, 1f), lightness)
}

private fun accentColorAt(hue: Float, saturation: Float, lightness: Float): Color {
    val safeHue = ((hue % 360f) + 360f) % 360f
    val safeSaturation = saturation.coerceIn(0f, 1f)
    val safeLightness = lightness.coerceIn(0f, 1f)
    val chroma = (1f - kotlin.math.abs(2f * safeLightness - 1f)) * safeSaturation
    val hueSector = safeHue / 60f
    val secondary = chroma * (1f - kotlin.math.abs((hueSector % 2f) - 1f))
    val (r, g, b) = when {
        hueSector < 1f -> Triple(chroma, secondary, 0f)
        hueSector < 2f -> Triple(secondary, chroma, 0f)
        hueSector < 3f -> Triple(0f, chroma, secondary)
        hueSector < 4f -> Triple(0f, secondary, chroma)
        hueSector < 5f -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    val offset = safeLightness - chroma / 2f
    return Color(r + offset, g + offset, b + offset)
}

private fun Color.bestContentColor(): Color {
    fun contrast(first: Color, second: Color): Float {
        val lighter = maxOf(first.luminance(), second.luminance())
        val darker = minOf(first.luminance(), second.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }
    return if (contrast(this, Color.White) >= contrast(this, AccentInk)) Color.White else AccentInk
}

/**
 * Derives Material's accent roles from one selected colour. The hue is retained while saturation
 * and lightness are adjusted for each role and for the active light/dark surface, so users never
 * have to configure separate shades themselves.
 */
private fun androidx.compose.material3.ColorScheme.withOpenTaskerAccent(
    accent: Color,
    darkTheme: Boolean,
) = run {
    val base = accent.toAccentHsl()
    fun tone(saturationScale: Float, lightness: Float) = accentColorAt(
        hue = base.hue,
        saturation = base.saturation * saturationScale,
        lightness = lightness,
    )

    val primary = tone(1f, if (darkTheme) 0.78f else 0.40f)
    val primaryContainer = tone(0.72f, if (darkTheme) 0.34f else 0.92f)
    val onPrimaryContainer = tone(0.65f, if (darkTheme) 0.94f else 0.22f)
    val tertiary = tone(0.82f, if (darkTheme) 0.84f else 0.46f)
    val tertiaryContainer = tone(0.62f, if (darkTheme) 0.39f else 0.90f)
    val onTertiaryContainer = tone(0.60f, if (darkTheme) 0.95f else 0.22f)

    copy(
        primary = primary,
        onPrimary = primary.bestContentColor(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer.bestContentColor(),
        tertiary = tertiary,
        onTertiary = tertiary.bestContentColor(),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer.bestContentColor(),
    )
}
