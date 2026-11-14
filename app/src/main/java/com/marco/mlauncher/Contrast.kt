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

/** [top] con opacità [a] sopra [bottom] (risultato opaco). */
fun overColor(top: Color, a: Float, bottom: Color): Color {
    val k = a.coerceIn(0f, 1f)
    return Color(
        top.red * k + bottom.red * (1 - k),
        top.green * k + bottom.green * (1 - k),
        top.blue * k + bottom.blue * (1 - k),
        1f
    )
}

/** Testo chiaro o scuro: quello che si legge meglio su [bg]. */
fun bestTextOn(bg: Color, light: Color = Color.White, dark: Color = Color(0xFF1B1B1F)): Color {
    val b = bg.copy(alpha = 1f).toArgb()
    val cl = ColorUtils.calculateContrast(light.copy(alpha = 1f).toArgb(), b)
    val cd = ColorUtils.calculateContrast(dark.copy(alpha = 1f).toArgb(), b)
    return if (cl >= cd) light else dark
}

/** Contrasto tra due colori opachi (1..21). */
fun contrastOf(fg: Color, bg: Color): Double =
    ColorUtils.calculateContrast(fg.copy(alpha = 1f).toArgb(), bg.copy(alpha = 1f).toArgb())

/**
 * Opacità minima di un velo [veil] sopra [backdrop] perché [text] si legga (contrasto [min]).
 * Parte da [start] (quella scelta dall'utente) e la alza solo quanto serve.
 */
fun veilAlphaFor(text: Color, veil: Color, backdrop: Color, start: Float, min: Double = 4.5): Float {
    var a = start.coerceIn(0f, 1f)
    while (a < 1f && contrastOf(text, overColor(veil, a, backdrop)) < min) a = (a + 0.04f).coerceAtMost(1f)
    return a
}
