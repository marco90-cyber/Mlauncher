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
fun Modifier.dockLook(style: String, opacity: Int, glass: Boolean, corner: Dp, shapeOverride: Shape? = null): Modifier {
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
    val shape = shapeOverride ?: dockShape(style, corner)
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

/** Colore leggibile su un elemento con l'aspetto della dock appoggiato sullo sfondo (es. indicatore di pagina). */
@Composable
fun dockContentColor(style: String, opacity: Int): Color {
    val cs = MaterialTheme.colorScheme
    if (style == "none") return Color.White
    val (base, a0) = when (style) {
        "tinted" -> cs.primaryContainer to 0.55f
        "dark" -> Color.Black to 0.38f
        "pill" -> cs.surfaceContainer to 0.85f
        "bar" -> cs.surfaceContainerHigh to 0.70f
        "tiles" -> cs.surfaceContainerHighest to 0.30f
        else -> Color.White to 0.28f
    }
    val a = (a0 * opacity / 60f).coerceIn(0f, 1f)
    val bd = Color(0xFF3A3A3A)
    val r = base.red * a + bd.red * (1 - a)
    val g = base.green * a + bd.green * (1 - a)
    val b = base.blue * a + bd.blue * (1 - a)
    val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
    return if (lum > 0.5f) Color(0xFF1B1B1F) else Color.White
}

/** Forme disponibili per l'icona delle cartelle. */
val FOLDER_SHAPES = listOf(
    "squircle" to "Squircle",
    "circle" to "Cerchio",
    "rounded" to "Quadrato arrotondato",
    "square" to "Quadrato",
    "teardrop" to "Goccia",
    "leaf" to "Foglia"
)

fun folderShapeOf(key: String): Shape = when (key) {
    "circle" -> androidx.compose.foundation.shape.CircleShape
    "rounded" -> RoundedCornerShape(20)
    "square" -> RoundedCornerShape(8)
    "teardrop" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 12, bottomStartPercent = 50)
    "leaf" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 12, bottomEndPercent = 50, bottomStartPercent = 12)
    else -> RoundedCornerShape(32)
}

/** Margine interno perché le mini icone non vengano tagliate dalle forme più tonde. */
fun folderShapeInset(key: String): Float = when (key) {
    "circle" -> 0.17f
    "teardrop", "leaf" -> 0.13f
    else -> 0.10f
}

/** Tessera singola dietro a ogni icona (stile "Tessere singole"). */
@Composable
fun Modifier.dockTile(opacity: Int, glass: Boolean, corner: Dp, shapeOverride: Shape? = null): Modifier {
    val cs = MaterialTheme.colorScheme
    val shape = shapeOverride ?: RoundedCornerShape(corner)
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

/** Aspetto della dock da applicare anche alle cartelle (null = aspetto proprio delle cartelle). */
data class DockLookSpec(val style: String, val opacity: Int, val glass: Boolean, val corner: Int)

val LocalFolderDock = androidx.compose.runtime.compositionLocalOf<DockLookSpec?> { null }

/**
 * Colore del testo leggibile sul pannello di una cartella che segue la dock: si stima il colore
 * risultante (base opaca + tinto del vetro, su una Home sfocata mediamente scura) e si sceglie
 * bianco o nero in base alla luminosità.
 */
fun folderTextColor(style: String, opacity: Int, base: Color, surface: Color, fallback: Color): Color {
    fun over(top: Color, a: Float, bottom: Color) = Color(
        top.red * a + bottom.red * (1 - a),
        top.green * a + bottom.green * (1 - a),
        top.blue * a + bottom.blue * (1 - a),
        1f
    )
    val backdrop = Color(0xFF303030)
    val tint = when (style) {
        "tinted" -> base to 0.55f
        "dark" -> Color.Black to 0.38f
        "pill" -> base to 0.85f
        "bar" -> base to 0.70f
        else -> Color.White to 0.28f
    }
    val a = (tint.second * maxOf(opacity, 40) / 60f).coerceIn(0f, 1f)
    val c = over(tint.first, a, over(surface, 0.72f, backdrop))
    val lum = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
    return if (lum > 0.5f) Color(0xFF1B1B1F) else Color.White
}
