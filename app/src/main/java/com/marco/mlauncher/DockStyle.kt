package com.marco.mlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val DOCK_STYLES = listOf(
    "glass" to "Vetro",
    "tinted" to "Vetro colorato",
    "dark" to "Vetro scuro",
    "pill" to "Capsula tonale",
    "bar" to "Barra a tutta larghezza",
    "tiles" to "Tessere singole",
    "none" to "Nessuno"
)

fun dockShape(style: String, corner: Dp): Shape =
    if (style == "bar") RoundedCornerShape(topStart = corner, topEnd = corner) else RoundedCornerShape(corner)

/** Aspetto della dock: usato sia dalla Home sia dalle anteprime nelle impostazioni. */
@Composable
fun Modifier.dockLook(style: String, opacity: Int, glass: Boolean, corner: Dp): Modifier {
    if (style == "none" || style == "tiles") return this
    val cs = MaterialTheme.colorScheme
    val (base, a) = when (style) {
        "tinted" -> cs.primaryContainer to 0.55f
        "dark" -> Color.Black to 0.38f
        "pill" -> cs.surfaceContainer to 0.85f
        "bar" -> cs.surfaceContainerHigh to 0.70f
        else -> Color.White to 0.28f
    }
    val alpha = (a * opacity / 60f).coerceIn(0f, 1f)
    val shape = dockShape(style, corner)
    var m = this.clip(shape).background(base.copy(alpha = alpha))
    if (glass) {
        val k = (0.4f + opacity / 100f).coerceIn(0.4f, 1.2f)
        m = m
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.30f * k), Color.Transparent, Color.White.copy(alpha = 0.08f * k))
                )
            )
            .border(
                1.2.dp,
                Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.40f))
                ),
                shape
            )
    }
    return m
}

/** Tessera singola dietro a ogni icona (stile "Tessere singole"). */
@Composable
fun Modifier.dockTile(opacity: Int, glass: Boolean, corner: Dp): Modifier {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(corner)
    val alpha = (0.30f * opacity / 60f).coerceIn(0f, 1f)
    var m = this.clip(shape).background(cs.surfaceContainerHighest.copy(alpha = alpha))
    if (glass) {
        m = m.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.08f))),
                shape
            )
    }
    return m
}
