package com.marco.mlauncher

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils

/** Colori del Launcher: "auto" = dallo sfondo (Material You), altrimenti un colore fisso. */
val ACCENTS = listOf(
    "auto" to "Dallo sfondo",
    "blue" to "Blu",
    "green" to "Verde",
    "purple" to "Viola",
    "red" to "Rosso",
    "orange" to "Arancione",
    "gray" to "Grigio"
)

/** Tonalità (gradi) e saturazione di ogni colore fisso. */
private fun accentHue(key: String): Pair<Float, Float>? = when (key) {
    "blue" -> 215f to 0.75f
    "green" -> 140f to 0.55f
    "purple" -> 270f to 0.55f
    "red" -> 355f to 0.70f
    "orange" -> 28f to 0.85f
    "gray" -> 220f to 0.06f
    else -> null
}

/** Colore campione per le anteprime. */
fun accentSwatch(key: String): Color? = accentHue(key)?.let { (h, s) -> hsl(h, s, 0.45f) }

private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))
    return Color(c)
}

/** Schema completo ricavato da un colore, chiaro o scuro (tonalità alla Material 3). */
private fun accentScheme(base: ColorScheme, h: Float, s: Float, dark: Boolean): ColorScheme {
    val s2 = s * 0.35f
    val s3 = s * 0.7f
    val h3 = h + 60f
    val ns = minOf(s, 0.10f) // superfici appena tinte
    return if (!dark) base.copy(
        primary = hsl(h, s, 0.40f), onPrimary = Color.White,
        primaryContainer = hsl(h, s, 0.90f), onPrimaryContainer = hsl(h, s, 0.12f),
        secondary = hsl(h, s2, 0.40f), onSecondary = Color.White,
        secondaryContainer = hsl(h, s2, 0.90f), onSecondaryContainer = hsl(h, s2, 0.12f),
        tertiary = hsl(h3, s3, 0.40f), onTertiary = Color.White,
        tertiaryContainer = hsl(h3, s3, 0.90f), onTertiaryContainer = hsl(h3, s3, 0.12f),
        background = hsl(h, ns, 0.98f), onBackground = hsl(h, ns, 0.10f),
        surface = hsl(h, ns, 0.98f), onSurface = hsl(h, ns, 0.10f),
        surfaceVariant = hsl(h, ns * 2, 0.90f), onSurfaceVariant = hsl(h, ns * 2, 0.30f),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = hsl(h, ns, 0.96f),
        surfaceContainer = hsl(h, ns, 0.94f),
        surfaceContainerHigh = hsl(h, ns, 0.92f),
        surfaceContainerHighest = hsl(h, ns, 0.90f),
        outline = hsl(h, ns, 0.50f), outlineVariant = hsl(h, ns, 0.80f),
        inversePrimary = hsl(h, s, 0.80f)
    ) else base.copy(
        primary = hsl(h, s, 0.78f), onPrimary = hsl(h, s, 0.18f),
        primaryContainer = hsl(h, s, 0.30f), onPrimaryContainer = hsl(h, s, 0.90f),
        secondary = hsl(h, s2, 0.78f), onSecondary = hsl(h, s2, 0.18f),
        secondaryContainer = hsl(h, s2, 0.28f), onSecondaryContainer = hsl(h, s2, 0.90f),
        tertiary = hsl(h3, s3, 0.78f), onTertiary = hsl(h3, s3, 0.18f),
        tertiaryContainer = hsl(h3, s3, 0.30f), onTertiaryContainer = hsl(h3, s3, 0.90f),
        background = hsl(h, ns, 0.07f), onBackground = hsl(h, ns, 0.90f),
        surface = hsl(h, ns, 0.07f), onSurface = hsl(h, ns, 0.90f),
        surfaceVariant = hsl(h, ns * 2, 0.28f), onSurfaceVariant = hsl(h, ns * 2, 0.80f),
        surfaceContainerLowest = hsl(h, ns, 0.04f),
        surfaceContainerLow = hsl(h, ns, 0.10f),
        surfaceContainer = hsl(h, ns, 0.12f),
        surfaceContainerHigh = hsl(h, ns, 0.16f),
        surfaceContainerHighest = hsl(h, ns, 0.21f),
        outline = hsl(h, ns, 0.55f), outlineVariant = hsl(h, ns, 0.28f),
        inversePrimary = hsl(h, s, 0.40f)
    )
}

@Composable
fun MLauncherTheme(mode: String, accent: String = "auto", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val dyn = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    val scheme = accentHue(accent)?.let { (h, s) -> accentScheme(dyn, h, s, dark) } ?: dyn
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
