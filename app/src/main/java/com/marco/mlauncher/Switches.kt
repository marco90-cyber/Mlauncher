package com.marco.mlauncher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Stili degli interruttori: quelli della libreria MLTheme più Fluent (Windows 11), disegnato qui. */
val SWITCH_STYLES = com.marco.mltheme.SWITCH_STYLES + ("fluent" to "Fluent (Windows 11)") + ("sense" to "Sense (casella di spunta)")

/** Stili disegnati da MLauncher (non dalla libreria MLTheme). */
val OWN_SWITCHES = setOf("fluent", "sense")

/** false = la voce dipende da un'altra impostazione spenta: si vede in grigio e non si tocca. */
val LocalPrefEnabled = com.marco.mltheme.LocalPrefEnabled

/** Stile scelto, letto da tutti gli interruttori dell'app. */
object SwitchLook {
    var style by mutableStateOf("android16")
}

/**
 * Interruttore di MLauncher nello stile scelto (disegnato da MLTheme, Fluent disegnato qui).
 * [onCheckedChange] null = solo disegno (lo gestisce la riga che lo contiene).
 */
@Composable
fun MLSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    style: String = SwitchLook.style
) {
    if (style !in OWN_SWITCHES) return com.marco.mltheme.MLSwitch(checked, onCheckedChange, modifier, style)
    if (style == "sense") {
        if (!LocalPrefEnabled.current) SenseCheckDisabled(checked, modifier) else SenseCheck(checked, onCheckedChange, modifier)
        return
    }
    if (!LocalPrefEnabled.current) {
        Box(modifier.alpha(0.38f)) { FluentSwitch(checked, null, Modifier) }
        return
    }
    FluentSwitch(checked, onCheckedChange, modifier)
}

/**
 * Interruttore come quello di Windows 11: pillola 40×20, da spento solo il contorno con un pallino grigio,
 * da acceso pieno del colore d'accento con il pallino bianco che si allarga un poco.
 */
@Composable
fun FluentSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = fluentDark()
    val track by animateColorAsState(if (checked) cs.primary else Color.Transparent, tween(120), label = "fluentTrack")
    val thumbColor = if (checked) (if (dark) Color.Black else Color.White) else (if (dark) Color(0xFFCFCFCF) else Color(0xFF5F5F5F))
    val x by animateDpAsState(if (checked) 23.dp else 5.dp, tween(140), label = "fluentThumb")
    val thumb = if (checked) 12.dp else 10.dp
    var m = modifier.padding(horizontal = 4.dp)
    if (onCheckedChange != null) m = m.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    Box(
        m.size(40.dp, 20.dp).clip(CircleShape).background(track)
            .border(1.dp, if (checked) cs.primary else thumbColor, CircleShape),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(Modifier.offset(x = x - (thumb - 10.dp) / 2).size(thumb).clip(CircleShape).background(thumbColor))
    }
}

/** Anteprima nell'elenco delle scelte. */
@Composable
fun SwitchStylePreview(key: String) {
    when (key) {
        "fluent" -> FluentSwitch(true, null)
        "sense" -> SenseCheck(true, null)
        else -> com.marco.mltheme.SwitchStylePreview(key)
    }
}
