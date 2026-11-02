package com.marco.mlauncher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stili degli interruttori nelle impostazioni e nel benvenuto. */
val SWITCH_STYLES = listOf(
    "android16" to "Android 16 (con simboli)",
    "material" to "Material classico",
    "ios" to "iOS",
    "hyperos" to "HyperOS"
)

/** Stile scelto, letto da tutti gli interruttori dell'app. */
object SwitchLook {
    var style by mutableStateOf("android16")
}

/**
 * Interruttore di MLauncher nello stile scelto.
 * [onCheckedChange] null = solo disegno (lo gestisce la riga che lo contiene).
 */
@Composable
fun MLSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    style: String = SwitchLook.style
) {
    val cs = MaterialTheme.colorScheme
    when (style) {
        "material" -> Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier)
        "ios" -> {
            val dark = cs.surface.luminance() < 0.4f
            SlideSwitch(
                checked, onCheckedChange, modifier,
                width = 51.dp, height = 31.dp, thumb = 27.dp,
                on = Color(0xFF34C759),
                off = if (dark) Color(0xFF39393D) else Color(0xFFE9E9EA),
                thumbColor = Color.White
            )
        }
        "hyperos" -> SlideSwitch(
            checked, onCheckedChange, modifier,
            width = 48.dp, height = 28.dp, thumb = 20.dp,
            on = cs.primary,
            off = cs.surfaceVariant,
            thumbColor = Color.White,
            shadow = false
        )
        else -> Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            thumbContent = {
                Icon(
                    if (checked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = cs.onPrimary,
                checkedTrackColor = cs.primary,
                checkedIconColor = cs.primary,
                checkedBorderColor = cs.primary,
                uncheckedThumbColor = cs.onSurfaceVariant,
                uncheckedTrackColor = cs.secondaryContainer,
                uncheckedIconColor = cs.secondaryContainer,
                uncheckedBorderColor = cs.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun SlideSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier,
    width: Dp,
    height: Dp,
    thumb: Dp,
    on: Color,
    off: Color,
    thumbColor: Color,
    shadow: Boolean = true
) {
    val inset = (height - thumb) / 2
    val x by animateDpAsState(
        if (checked) width - thumb - inset else inset,
        spring(dampingRatio = 0.75f, stiffness = 700f), label = "swX"
    )
    val track by animateColorAsState(if (checked) on else off, label = "swC")
    Box(
        modifier
            .size(width, height)
            .clip(CircleShape)
            .background(track)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(
                    value = checked, role = Role.Switch, onValueChange = onCheckedChange
                ) else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(thumb)
                .then(if (shadow) Modifier.shadow(2.dp, CircleShape) else Modifier)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

/** Anteprima nell'elenco delle scelte. */
@Composable
fun SwitchStylePreview(key: String) {
    Box(Modifier.padding(end = 4.dp)) { MLSwitch(checked = true, onCheckedChange = null, style = key) }
}
