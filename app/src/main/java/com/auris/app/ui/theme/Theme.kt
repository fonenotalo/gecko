package com.auris.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.auris.app.model.AlbumPalette
import com.auris.app.model.AppSettings
import com.auris.app.model.ThemeMode
import com.auris.app.model.ThemeStyle

val LocalThemeStyle = staticCompositionLocalOf { ThemeStyle.MODERN }
val LocalAlbumPalette = staticCompositionLocalOf<AlbumPalette?> { null }

@Composable
fun AurisTheme(
    settings: AppSettings,
    palette: AlbumPalette?,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val classic = settings.themeStyle == ThemeStyle.CLASSIC_IPOD

    val scheme = when {
        classic -> if (dark) ClassicDarkScheme else ClassicLightScheme
        settings.colorFromArtwork && palette != null -> paletteScheme(palette, dark)
        else -> if (dark) DefaultDarkScheme else DefaultLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalThemeStyle provides settings.themeStyle,
        LocalAlbumPalette provides palette,
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = scheme,
            typography = if (classic) ClassicTypography else AurisTypography,
            shapes = if (classic) ClassicShapes else AurisShapes,
            content = content,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context: Context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun paletteScheme(palette: AlbumPalette, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(palette.primary, hsl)
    val hue = hsl[0]
    val saturation = hsl[1].coerceIn(0.32f, 0.82f)

    val primary = if (dark) {
        Color.hsl(hue, saturation.coerceAtMost(0.78f), 0.74f)
    } else {
        Color.hsl(hue, saturation.coerceAtMost(0.86f), 0.34f)
    }
    val onPrimary = if (dark) Color.hsl(hue, 0.35f, 0.12f) else Color.White
    val primaryContainer = if (dark) {
        Color.hsl(hue, saturation.coerceAtMost(0.6f), 0.26f)
    } else {
        Color.hsl(hue, saturation.coerceAtMost(0.55f), 0.90f)
    }
    val onPrimaryContainer = if (dark) {
        Color.hsl(hue, 0.35f, 0.90f)
    } else {
        Color.hsl(hue, 0.5f, 0.18f)
    }

    val secondaryHue = (hue + 42f) % 360f
    val tertiaryHue = (hue + 190f) % 360f
    val secondary = if (dark) {
        Color.hsl(secondaryHue, 0.45f, 0.74f)
    } else {
        Color.hsl(secondaryHue, 0.55f, 0.36f)
    }
    val tertiary = if (dark) {
        Color.hsl(tertiaryHue, 0.5f, 0.76f)
    } else {
        Color.hsl(tertiaryHue, 0.5f, 0.38f)
    }

    val background = surfaceTone(hue, dark, 0)
    val surface = surfaceTone(hue, dark, 1)
    val surfaceContainer = surfaceTone(hue, dark, 3)
    val surfaceContainerHigh = surfaceTone(hue, dark, 4)
    val surfaceContainerHighest = surfaceTone(hue, dark, 5)
    val surfaceContainerLow = surfaceTone(hue, dark, 2)
    val surfaceContainerLowest = surfaceTone(hue, dark, -1)
    val surfaceVariant = surfaceTone(hue, dark, 3)
    val onSurface = if (dark) Color.hsl(hue, 0.08f, 0.94f) else Color.hsl(hue, 0.16f, 0.12f)
    val onSurfaceVariant = if (dark) Color.hsl(hue, 0.10f, 0.74f) else Color.hsl(hue, 0.12f, 0.38f)
    val outline = if (dark) Color.hsl(hue, 0.10f, 0.54f) else Color.hsl(hue, 0.10f, 0.52f)
    val outlineVariant = if (dark) Color.hsl(hue, 0.10f, 0.27f) else Color.hsl(hue, 0.12f, 0.82f)

    val secondaryContainer = if (dark) {
        Color.hsl(secondaryHue, 0.4f, 0.25f)
    } else {
        Color.hsl(secondaryHue, 0.45f, 0.90f)
    }
    val tertiaryContainer = if (dark) {
        Color.hsl(tertiaryHue, 0.4f, 0.26f)
    } else {
        Color.hsl(tertiaryHue, 0.4f, 0.90f)
    }

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = Color.hsl(hue, 0.5f, 0.42f),
            secondary = secondary,
            onSecondary = Color.hsl(secondaryHue, 0.3f, 0.12f),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color.hsl(secondaryHue, 0.35f, 0.90f),
            tertiary = tertiary,
            onTertiary = Color.hsl(tertiaryHue, 0.3f, 0.12f),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = Color.hsl(tertiaryHue, 0.35f, 0.90f),
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            inverseSurface = Color.hsl(hue, 0.08f, 0.92f),
            inverseOnSurface = Color.hsl(hue, 0.10f, 0.14f),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceTint = primary,
            surfaceBright = surfaceTone(hue, dark, 6),
            surfaceDim = surfaceTone(hue, dark, -1),
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = Color.hsl(hue, 0.45f, 0.72f),
            secondary = secondary,
            onSecondary = Color.White,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color.hsl(secondaryHue, 0.45f, 0.16f),
            tertiary = tertiary,
            onTertiary = Color.White,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = Color.hsl(tertiaryHue, 0.45f, 0.17f),
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            inverseSurface = Color.hsl(hue, 0.10f, 0.16f),
            inverseOnSurface = Color.hsl(hue, 0.08f, 0.94f),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceTint = primary,
            surfaceBright = surfaceTone(hue, dark, 7),
            surfaceDim = surfaceTone(hue, dark, 2),
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainerLowest = surfaceContainerLowest,
        )
    }
}

private fun surfaceTone(hue: Float, dark: Boolean, level: Int): Color {
    return if (dark) {
        val lightness = (0.060f + (level + 1) * 0.016f).coerceIn(0.04f, 0.20f)
        Color.hsl(hue, 0.14f, lightness)
    } else {
        val lightness = (0.985f - (level + 1) * 0.012f).coerceIn(0.88f, 0.99f)
        Color.hsl(hue, 0.07f, lightness)
    }
}

private val DefaultDarkScheme = darkColorScheme(
    primary = Color(0xFFE8C27D),
    onPrimary = Color(0xFF2B2006),
    primaryContainer = Color(0xFF463716),
    onPrimaryContainer = Color(0xFFF8E5BC),
    inversePrimary = Color(0xFF8A6A22),
    secondary = Color(0xFF9ED8CD),
    onSecondary = Color(0xFF08201C),
    secondaryContainer = Color(0xFF1B403A),
    onSecondaryContainer = Color(0xFFC6EFE8),
    tertiary = Color(0xFFCBB7FF),
    onTertiary = Color(0xFF24124D),
    tertiaryContainer = Color(0xFF3D2C69),
    onTertiaryContainer = Color(0xFFE9DEFF),
    background = Color(0xFF0B0B0F),
    onBackground = Color(0xFFF2EFE9),
    surface = Color(0xFF111116),
    onSurface = Color(0xFFF2EFE9),
    surfaceVariant = Color(0xFF26252C),
    onSurfaceVariant = Color(0xFFB9B5AD),
    inverseSurface = Color(0xFFEDE9E2),
    inverseOnSurface = Color(0xFF1A1A1F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF6F6B63),
    outlineVariant = Color(0xFF3A393F),
    scrim = Color.Black,
    surfaceTint = Color(0xFFE8C27D),
    surfaceBright = Color(0xFF303037),
    surfaceDim = Color(0xFF0B0B0F),
    surfaceContainer = Color(0xFF16161C),
    surfaceContainerHigh = Color(0xFF1D1D24),
    surfaceContainerHighest = Color(0xFF24242C),
    surfaceContainerLow = Color(0xFF121217),
    surfaceContainerLowest = Color(0xFF08080B),
)

private val DefaultLightScheme = lightColorScheme(
    primary = Color(0xFF7A5A16),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E1B4),
    onPrimaryContainer = Color(0xFF2B2006),
    inversePrimary = Color(0xFFE8C27D),
    secondary = Color(0xFF2F6B62),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6EFE8),
    onSecondaryContainer = Color(0xFF0B2E29),
    tertiary = Color(0xFF5D4A8A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE9DEFF),
    onTertiaryContainer = Color(0xFF24124D),
    background = Color(0xFFFAF8F4),
    onBackground = Color(0xFF16161A),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF16161A),
    surfaceVariant = Color(0xFFEDE8DF),
    onSurfaceVariant = Color(0xFF5D5A54),
    inverseSurface = Color(0xFF2E2E33),
    inverseOnSurface = Color(0xFFF4F1EB),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF807A70),
    outlineVariant = Color(0xFFD5CFC5),
    scrim = Color.Black,
    surfaceTint = Color(0xFF7A5A16),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE5E1D9),
    surfaceContainer = Color(0xFFF4F1EA),
    surfaceContainerHigh = Color(0xFFEEEBE4),
    surfaceContainerHighest = Color(0xFFE8E5DD),
    surfaceContainerLow = Color(0xFFFAF8F3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

private val ClassicLightScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E7FF),
    onPrimaryContainer = Color(0xFF00264D),
    secondary = Color(0xFF5856D6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E3FF),
    onSecondaryContainer = Color(0xFF18165C),
    tertiary = Color(0xFF2F7D3A),
    onTertiary = Color.White,
    background = Color(0xFFECEBE6),
    onBackground = Color(0xFF111113),
    surface = Color(0xFFFCFCFA),
    onSurface = Color(0xFF111113),
    surfaceVariant = Color(0xFFE1E0DA),
    onSurfaceVariant = Color(0xFF4E4E52),
    inverseSurface = Color(0xFF2C2C2F),
    inverseOnSurface = Color(0xFFF5F5F3),
    outline = Color(0xFF8A8A8D),
    outlineVariant = Color(0xFFC7C6C1),
    surfaceTint = Color(0xFF007AFF),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDAD9D4),
    surfaceContainer = Color(0xFFF1F0EC),
    surfaceContainerHigh = Color(0xFFEBEAE5),
    surfaceContainerHighest = Color(0xFFE4E3DE),
    surfaceContainerLow = Color(0xFFF7F6F2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

private val ClassicDarkScheme = darkColorScheme(
    primary = Color(0xFF4C9AFF),
    onPrimary = Color(0xFF002A55),
    primaryContainer = Color(0xFF003F7A),
    onPrimaryContainer = Color(0xFFD6E7FF),
    secondary = Color(0xFFBFC2FF),
    onSecondary = Color(0xFF1E1C63),
    tertiary = Color(0xFF7EDB8A),
    onTertiary = Color(0xFF00340B),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF0F0F2),
    surface = Color(0xFF161618),
    onSurface = Color(0xFFF0F0F2),
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = Color(0xFFB9B9BE),
    inverseSurface = Color(0xFFE7E7E9),
    inverseOnSurface = Color(0xFF1B1B1D),
    outline = Color(0xFF76767C),
    outlineVariant = Color(0xFF3D3D42),
    surfaceTint = Color(0xFF4C9AFF),
    surfaceBright = Color(0xFF2D2D31),
    surfaceDim = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF1D1D20),
    surfaceContainerHigh = Color(0xFF242427),
    surfaceContainerHighest = Color(0xFF2B2B2F),
    surfaceContainerLow = Color(0xFF18181B),
    surfaceContainerLowest = Color(0xFF08080A),
)

/** Helper used by screens that need the raw artwork seed color. */
fun AlbumPalette.accentColor(dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(primary, hsl)
    return if (dark) {
        Color.hsl(hsl[0], hsl[1].coerceAtMost(0.8f), 0.72f)
    } else {
        Color.hsl(hsl[0], hsl[1].coerceAtMost(0.85f), 0.38f)
    }
}

fun Color.toArgbCompat(): Int = toArgb()
