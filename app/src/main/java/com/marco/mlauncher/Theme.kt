package com.marco.mlauncher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.marco.mltheme.LocalSwitchStyle
import com.marco.mltheme.mlColorScheme

/**
 * Il tema vive nella libreria condivisa MLTheme (repo marco90-cyber/MLTheme), usata da tutte le MLapps.
 * Qui restano solo i collegamenti, così il resto di MLauncher non cambia.
 */
val ACCENTS = com.marco.mltheme.ACCENTS

/** Colore campione per le anteprime. */
fun accentSwatch(key: String): Color? = com.marco.mltheme.accentSwatch(key)

@Composable
fun MLauncherTheme(mode: String, accent: String = "system", seed: Int? = null, content: @Composable () -> Unit) {
    val scheme = mlColorScheme(mode, accent, seed)
    CompositionLocalProvider(LocalSwitchStyle provides SwitchLook.style) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
