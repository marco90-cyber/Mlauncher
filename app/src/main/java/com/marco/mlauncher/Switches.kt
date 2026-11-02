package com.marco.mlauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Stili degli interruttori (dalla libreria MLTheme). */
val SWITCH_STYLES = com.marco.mltheme.SWITCH_STYLES

/** false = la voce dipende da un'altra impostazione spenta: si vede in grigio e non si tocca. */
val LocalPrefEnabled = com.marco.mltheme.LocalPrefEnabled

/** Stile scelto, letto da tutti gli interruttori dell'app. */
object SwitchLook {
    var style by mutableStateOf("android16")
}

/**
 * Interruttore di MLauncher nello stile scelto (disegnato da MLTheme).
 * [onCheckedChange] null = solo disegno (lo gestisce la riga che lo contiene).
 */
@Composable
fun MLSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    style: String = SwitchLook.style
) = com.marco.mltheme.MLSwitch(checked, onCheckedChange, modifier, style)

/** Anteprima nell'elenco delle scelte. */
@Composable
fun SwitchStylePreview(key: String) = com.marco.mltheme.SwitchStylePreview(key)
