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

fun dockShape(style: String, corner: Dp): Shape =
    if (style == "bar") RoundedCornerShape(topStart = corner, topEnd = corner) else RoundedCornerShape(corner)

/** Aspetto della dock: usato sia dalla Home sia dalle anteprime nelle impostazioni. */
@Composable
fun Modifier.dockLook(style: String, opacity: Int, glass: Boolean, corner: Dp, shapeOverride: Shape? = null): Modifier {
    if (style == "none" || style == "tiles" || style == "standard") return this
    if (style == "liquid") return this.liquidGlass(corner, shapeOverride ?: RoundedCornerShape(corner), opacity = opacity / 100f)
    val cs = MaterialTheme.colorScheme
    val (base, a) = when (style) {
        "tinted" -> cs.primary to 0.50f // segue il colore scelto (o quello dello sfondo)
        "dark" -> Color.Black to 0.38f
        "pill" -> cs.surfaceContainer to 0.85f
        "bar" -> cs.surfaceContainerHigh to 0.70f
        "solid" -> cs.surfaceContainerHigh to 0.96f
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
    if (style == "none" || style == "standard") return Color.White
    if (style == "liquid") return if (LocalGlassDarkText.current) Color(0xFF15171C) else Color.White
    val (base, a0) = when (style) {
        "tinted" -> cs.primary to 0.50f
        "dark" -> Color.Black to 0.38f
        "pill" -> cs.surfaceContainer to 0.85f
        "bar" -> cs.surfaceContainerHigh to 0.70f
        "solid" -> cs.surfaceContainerHigh to 0.96f
        "tiles" -> cs.surfaceContainerHighest to 0.30f
        else -> Color.White to 0.28f
    }
    val a = (a0 * opacity / 60f).coerceIn(0f, 1f)
    // lo sfondo vero dietro (non un grigio immaginato): con il tema scuro e uno sfondo chiaro, o il contrario,
    // il testo resta leggibile
    return bestTextOn(overColor(base, a, backdropTone()))
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
    "square" -> RoundedCornerShape(4)
    "teardrop" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 6, bottomStartPercent = 50)
    "leaf" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 6, bottomEndPercent = 50, bottomStartPercent = 6)
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
 * Colore del testo leggibile sul pannello di una cartella che segue la dock: si calcola il colore
 * risultante (sfondo vero dietro la cartella + base opaca + tinta e riflesso del vetro) e si sceglie
 * chiaro o scuro, quello con più contrasto.
 */
fun folderPanelColor(style: String, opacity: Int, glass: Boolean, base: Color, surface: Color, backdrop: Color): Color {
    val tint = when (style) {
        "tinted" -> base to 0.50f
        "dark" -> Color.Black to 0.38f
        "pill" -> base to 0.85f
        "bar" -> base to 0.70f
        "solid" -> base to 0.96f
        else -> Color.White to 0.28f
    }
    val a = (tint.second * maxOf(opacity, 40) / 60f).coerceIn(0f, 1f)
    var c = overColor(tint.first, a, overColor(surface, 0.72f, backdrop))
    // riflesso del vetro (in media un velo bianco leggero)
    if (glass) c = overColor(Color.White, 0.12f * (0.4f + opacity / 100f).coerceIn(0.4f, 1.2f), c)
    return c
}

fun folderTextColor(style: String, opacity: Int, glass: Boolean, base: Color, surface: Color, backdrop: Color): Color =
    bestTextOn(folderPanelColor(style, opacity, glass, base, surface, backdrop))

/** Aspetto di un elemento con il suo materiale (niente se "standard" o "nessuno"). */
@Composable
fun Modifier.matLook(m: Mat, corner: Dp, shape: Shape? = null, minOpacity: Int = 0): Modifier =
    if (m.plain) this else this.dockLook(m.style, maxOf(m.opacity, minOpacity), m.glass, corner, shape)

/** Colore del testo leggibile sopra un elemento con questo materiale. */
@Composable
fun matContentColor(m: Mat): Color = when (m.style) {
    "solid" -> MaterialTheme.colorScheme.onSurface
    else -> dockContentColor(m.style, m.opacity)
}
