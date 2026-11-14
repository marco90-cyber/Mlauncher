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

@Composable
fun MLauncherTheme(mode: String, accent: String = "system", seed: Int? = null, fluent: Boolean = false, content: @Composable () -> Unit) {
    // ogni testo deve leggersi sul suo fondo, qualunque tavolozza dia il sistema
    val raw = mlColorScheme(mode, accent, seed)
    val scheme = androidx.compose.runtime.remember(raw, fluent) { guardScheme(if (fluent) fluentScheme(raw) else raw) }
    val shapes = if (fluent) FluentShapes else androidx.compose.material3.Shapes()
    CompositionLocalProvider(LocalSwitchStyle provides SwitchLook.style, LocalFluent provides fluent) {
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
