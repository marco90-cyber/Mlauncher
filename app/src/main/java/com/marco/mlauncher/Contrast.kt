package com.marco.mlauncher

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Restituisce [fg] se si legge su [bg] (contrasto almeno [min]: 4,5 per il testo normale,
 * 3 per quello grande), altrimenti la stessa tinta schiarita o scurita quanto basta.
 */
fun readableOn(fg: Color, bg: Color, min: Double = 4.5): Color {
    val b = bg.copy(alpha = 1f).toArgb()
    fun contrast(c: Int) = ColorUtils.calculateContrast(ColorUtils.compositeColors(c, b), b)
    val f = fg.toArgb()
    if (contrast(f) >= min) return fg
    val lighten = ColorUtils.calculateLuminance(b) < 0.4
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(ColorUtils.compositeColors(f, b), hsl)
    repeat(25) {
        hsl[2] = (hsl[2] + if (lighten) 0.035f else -0.035f).coerceIn(0f, 1f)
        val c = ColorUtils.HSLToColor(hsl)
        if (contrast(c) >= min) return Color(c)
    }
    return if (lighten) Color.White else Color(0xFF111316)
}

/** Controlla tutte le coppie testo/fondo del tema: con qualsiasi tavolozza di sistema si legge sempre. */
fun guardScheme(cs: ColorScheme): ColorScheme = cs.copy(
    onPrimary = readableOn(cs.onPrimary, cs.primary),
    onPrimaryContainer = readableOn(cs.onPrimaryContainer, cs.primaryContainer),
    onSecondary = readableOn(cs.onSecondary, cs.secondary),
    onSecondaryContainer = readableOn(cs.onSecondaryContainer, cs.secondaryContainer),
    onTertiary = readableOn(cs.onTertiary, cs.tertiary),
    onTertiaryContainer = readableOn(cs.onTertiaryContainer, cs.tertiaryContainer),
    onBackground = readableOn(cs.onBackground, cs.background),
    onSurface = readableOn(cs.onSurface, cs.surface),
    onSurfaceVariant = readableOn(cs.onSurfaceVariant, cs.surfaceContainerHigh),
    onError = readableOn(cs.onError, cs.error),
    onErrorContainer = readableOn(cs.onErrorContainer, cs.errorContainer)
)
