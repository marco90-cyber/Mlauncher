package com.marco.mlauncher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.marco.mltheme.LocalSwitchStyle
import com.marco.mltheme.mlColorScheme

/**
 * Il tema vive nella libreria condivisa MLTheme (repo marco90-cyber/MLTheme), usata da tutte le MLapps.
 * Qui restano solo i collegamenti, così il resto di MLauncher non cambia.
 */
val ACCENTS = com.marco.mltheme.ACCENTS

/** Colore campione per le anteprime. */
fun accentSwatch(key: String): Color? = com.marco.mltheme.accentSwatch(key)

/** true = tema Fluent: impostazioni, menu e pannelli con la grafica di Windows 11 (come MLMeteo). */
val LocalFluent = androidx.compose.runtime.staticCompositionLocalOf { false }

/** true = tema Sensation: impostazioni e pannelli con la grafica dei vecchi telefoni HTC (grafite, verde lime). */
val LocalSense = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun MLauncherTheme(
    mode: String,
    accent: String = "system",
    seed: Int? = null,
    fluent: Boolean = false,
    sense: Boolean = false,
    content: @Composable () -> Unit
) {
    // ogni testo deve leggersi sul suo fondo, qualunque tavolozza dia il sistema
    val raw = mlColorScheme(mode, accent, seed)
    val scheme = androidx.compose.runtime.remember(raw, fluent, sense, accent) {
        guardScheme(
            when {
                sense -> senseScheme(raw, greenAccent = accent == "system")
                fluent -> fluentScheme(raw)
                else -> raw
            }
        )
    }
    val shapes = when {
        sense -> SenseShapes
        fluent -> FluentShapes
        else -> androidx.compose.material3.Shapes()
    }
    CompositionLocalProvider(LocalSwitchStyle provides SwitchLook.style, LocalFluent provides fluent, LocalSense provides sense) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), shapes = shapes, content = content)
    }
}

/** Angoli piccoli di Windows 11: 4 dp per i controlli, 8 dp per schede e finestre. */
private val FluentShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
)

/**
 * Tavolozza Fluent: gli stessi grigi neutri di MLMeteo (stile Fluent) al posto delle superfici
 * tinte di Material You; resta solo il colore d'accento.
 */
fun fluentScheme(c: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    val dark = c.surface.luminance() < 0.5f
    return if (dark) c.copy(
        background = Color(0xFF202020), surface = Color(0xFF202020), surfaceDim = Color(0xFF1C1C1C), surfaceBright = Color(0xFF3A3A3A),
        surfaceContainerLowest = Color(0xFF1C1C1C), surfaceContainerLow = Color(0xFF272727), surfaceContainer = Color(0xFF2B2B2B),
        surfaceContainerHigh = Color(0xFF2F2F2F), surfaceContainerHighest = Color(0xFF333333), surfaceVariant = Color(0xFF333333),
        onBackground = Color(0xFFF3F3F3), onSurface = Color(0xFFF3F3F3), onSurfaceVariant = Color(0xFFC5C5C5),
        outline = Color(0xFF5A5A5A), outlineVariant = Color(0xFF3D3D3D),
        secondaryContainer = Color(0xFF3A3A3A), onSecondaryContainer = Color(0xFFF3F3F3)
    ) else c.copy(
        background = Color(0xFFF3F3F3), surface = Color(0xFFF3F3F3), surfaceDim = Color(0xFFE6E6E6), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F7F7), surfaceContainer = Color(0xFFFDFDFD),
        surfaceContainerHigh = Color(0xFFF9F9F9), surfaceContainerHighest = Color(0xFFF0F0F0), surfaceVariant = Color(0xFFF0F0F0),
        onBackground = Color(0xFF1B1B1B), onSurface = Color(0xFF1B1B1B), onSurfaceVariant = Color(0xFF5F5F5F),
        outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFFE3E3E3),
        secondaryContainer = Color(0xFFEDEDED), onSecondaryContainer = Color(0xFF1B1B1B)
    )
}

/** Angoli dei vecchi telefoni HTC: piccoli e uguali, 4 dp per i controlli e 6 dp per pannelli e finestre. */
private val SenseShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
)

/**
 * Tavolozza Sensation: grafite e argento come i telefoni HTC con l'interfaccia Sense (scuro come Sense 2-3,
 * chiaro come i bianchi di Sense 1); con i colori "di sistema" l'accento diventa il verde lime di HTC.
 */
fun senseScheme(c: androidx.compose.material3.ColorScheme, greenAccent: Boolean): androidx.compose.material3.ColorScheme {
    val dark = c.surface.luminance() < 0.5f
    val base = if (dark) c.copy(
        background = Color(0xFF0C0C0D), surface = Color(0xFF0C0C0D), surfaceDim = Color(0xFF080808), surfaceBright = Color(0xFF2E3033),
        surfaceContainerLowest = Color(0xFF080808), surfaceContainerLow = Color(0xFF141516), surfaceContainer = Color(0xFF1A1B1D),
        surfaceContainerHigh = Color(0xFF212225), surfaceContainerHighest = Color(0xFF2A2C2F), surfaceVariant = Color(0xFF2A2C2F),
        onBackground = Color(0xFFF0F0F0), onSurface = Color(0xFFF0F0F0), onSurfaceVariant = Color(0xFFB4B7BB),
        outline = Color(0xFF5C5F64), outlineVariant = Color(0xFF34363A),
        secondaryContainer = Color(0xFF34363A), onSecondaryContainer = Color(0xFFF0F0F0)
    ) else c.copy(
        background = Color(0xFFEDEDED), surface = Color(0xFFEDEDED), surfaceDim = Color(0xFFDCDCDC), surfaceBright = Color(0xFFFFFFFF),
        surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F7F7), surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFFAFAFA), surfaceContainerHighest = Color(0xFFE6E6E6), surfaceVariant = Color(0xFFE6E6E6),
        onBackground = Color(0xFF111111), onSurface = Color(0xFF111111), onSurfaceVariant = Color(0xFF55585C),
        outline = Color(0xFF8C8F93), outlineVariant = Color(0xFFD2D2D2),
        secondaryContainer = Color(0xFFE2E2E2), onSecondaryContainer = Color(0xFF111111)
    )
    if (!greenAccent) return base
    return if (dark) base.copy(
        primary = SENSE_GREEN, onPrimary = Color(0xFF102000),
        primaryContainer = Color(0xFF2F4A10), onPrimaryContainer = Color(0xFFD6F2A8),
        secondary = Color(0xFFB9D88A), tertiary = Color(0xFF9CCB5A)
    ) else base.copy(
        primary = SENSE_GREEN_DARK, onPrimary = Color.White,
        primaryContainer = Color(0xFFD9F0B4), onPrimaryContainer = Color(0xFF1E3300),
        secondary = Color(0xFF55722E), tertiary = Color(0xFF3F7A12)
    )
}

/** Verde lime di HTC Sense (selezioni, segni di spunta, tasti premuti). */
val SENSE_GREEN = Color(0xFF8CC63E)
val SENSE_GREEN_DARK = Color(0xFF4E8A12)
