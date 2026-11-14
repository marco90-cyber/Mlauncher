package com.marco.mlauncher

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Schermata «Tema»: tre temi con una mini Home di anteprima ciascuno, come in Expressive Launcher
 * (github.com/kotlalipopus/expressive-launcher, licenza MIT).
 */

/** Un tema della Home: come si chiama e come si disegna la sua anteprima. */
class ThemeSpec(
    val key: String,
    val title: String,
    val tagline: String,
    val icon: ImageVector,
    /** pannelli di vetro liquido */
    val glass: Boolean,
    /** forme che cambiano alla pressione, titoli marcati */
    val expressive: Boolean,
    /** grafica semplificata: angoli tutti uguali, niente effetti */
    val lite: Boolean
) {
    val uniformCorner: Dp? get() = if (lite) 16.dp else null
}

private fun glyph(name: String, d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        .build()

/** Goccia (Material Symbols "water_drop"). */
val WaterDropIcon = glyph("WaterDrop", "M12,2c-5.33,4.55 -8,8.48 -8,11.8c0,4.98 3.8,8.2 8,8.2s8,-3.22 8,-8.2C20,10.48 17.33,6.55 12,2z")

/** Scintille (Material Symbols "auto_awesome"). */
val SparkleIcon = glyph(
    "AutoAwesome",
    "M19,9l1.25,-2.75L23,5l-2.75,-1.25L19,1l-1.25,2.75L15,5l2.75,1.25L19,9zM11.5,9.5L9,4L6.5,9.5L1,12l5.5,2.5L9,20l2.5,-5.5L17,12L11.5,9.5zM19,15l-1.25,2.75L15,19l2.75,1.25L19,23l1.25,-2.75L23,19l-2.75,-1.25L19,15z"
)

/** Fulmine (Material Symbols "bolt"). */
val BoltIcon = glyph(
    "Bolt",
    "M11,21h-1l1,-7H7.5c-0.58,0 -0.57,-0.32 -0.38,-0.66c0.19,-0.34 0.05,-0.08 0.07,-0.12C8.48,10.94 10.42,7.54 13,3h1l-1,7h3.5c0.49,0 0.56,0.33 0.47,0.51l-0.07,0.15C12.96,17.55 11,21 11,21z"
)

object LauncherThemes {
    val LiquidGlass = ThemeSpec(
        "liquid", "Liquid Glass", "Vetro liquido come iOS 26: lente, bordi arcobaleno, riflessi",
        WaterDropIcon, glass = true, expressive = false, lite = false
    )
    val Expressive = ThemeSpec(
        "expressive", "Material 3 Expressive", "Forme vivaci, colori pieni e icone Material You",
        SparkleIcon, glass = false, expressive = true, lite = false
    )
    val Lite = ThemeSpec(
        "lite", "Material You Lite", "Semplice e leggero: pannelli tonali, niente effetti",
        BoltIcon, glass = false, expressive = false, lite = true
    )
    val all = listOf(LiquidGlass, Expressive, Lite)
    fun of(key: String): ThemeSpec? = all.firstOrNull { it.key == key }
}

/** Le schede dei tre temi, ognuna con la sua mini Home; quella scelta è evidenziata. */
@Composable
fun ThemeCards(selected: String, s: LauncherSettings, previewHeight: Dp = 150.dp, onSelect: (ThemeSpec) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (spec in LauncherThemes.all) {
            ThemeCard(spec, spec.key == selected, s, previewHeight) { onSelect(spec) }
        }
    }
}

@Composable
private fun ThemeCard(spec: ThemeSpec, selected: Boolean, s: LauncherSettings, previewHeight: Dp, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // la scheda cambia forma alla pressione e quando è scelta (tranne nel tema Lite)
    val morph = LauncherThemes.of(s.launcherTheme)?.lite != true
    val springDp = spring<Dp>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
    val corner by animateDpAsState(
        when {
            !morph -> 16.dp
            pressed -> 40.dp
            selected -> 32.dp
            else -> 24.dp
        },
        springDp, label = "themeCardCorner"
    )
    val scale by animateFloatAsState(
        if (pressed && morph) 0.97f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), label = "themeCardScale"
    )
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(if (selected) cs.primaryContainer else cs.surfaceContainerHigh)
            .border(if (selected) 3.dp else 0.dp, if (selected) cs.primary else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = ripple()) { onClick() }
            .padding(10.dp)
    ) {
        MiniDesktop(spec, s, Modifier.fillMaxWidth().height(previewHeight))
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(spec.icon, null, tint = if (selected) cs.onPrimaryContainer else cs.primary)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    spec.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = if (selected) cs.onPrimaryContainer else cs.onSurface
                )
                Text(
                    spec.tagline, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (selected) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant
                )
            }
            if (selected) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = cs.onPrimary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Mini Home nello stile del tema [spec]: "sfondo", orologio, una fila di icone e la dock. I colori sono
 * quelli del tema attuale, quindi l'anteprima cambia con chiaro/scuro e con i colori; griglia, nomi
 * e dock vengono dalle impostazioni [s].
 */
@Composable
fun MiniDesktop(spec: ThemeSpec, s: LauncherSettings, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val outer = when {
        spec.uniformCorner != null -> RoundedCornerShape(spec.uniformCorner!!)
        spec.glass -> RoundedCornerShape(28.dp)
        else -> RoundedCornerShape(24.dp)
    }
    val wallpaper = Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer))
    Box(modifier.clip(outer).background(wallpaper)) {
        if (spec.glass) {
            // macchie morbide dello "sfondo": sotto il vetro si vede che è trasparente
            Box(Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 10.dp).size(70.dp).clip(CircleShape).background(cs.tertiary.copy(alpha = 0.55f)))
            Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp).size(54.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.45f)))
        }
        MiniClock(spec, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 10.dp))
        val columns = s.cols.coerceIn(3, 7)
        val glyphs = listOf(
            Icons.Rounded.Call, Icons.Rounded.Email, Icons.Rounded.Place, Icons.Rounded.Favorite,
            Icons.Rounded.ShoppingCart, Icons.Rounded.Star, Icons.Rounded.Settings
        )
        val iconSize = (30f * s.iconScale * 5f / columns.coerceAtLeast(4)).coerceIn(18f, 36f).dp
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (i in 0 until columns) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MiniIcon(spec, i, glyphs[i % glyphs.size], iconSize)
                        if (s.showLabels) {
                            Spacer(Modifier.height(3.dp))
                            Box(Modifier.size(iconSize * 0.8f, 3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
                        }
                    }
                }
            }
            if (!s.hideDock) MiniDock(spec, columns.coerceAtMost(5), iconSize, glyphs)
        }
    }
}

@Composable
private fun MiniClock(spec: ThemeSpec, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    when {
        spec.glass -> Text("9:41", modifier, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
        spec.expressive -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(iconShapeOf("cookie9")).background(cs.primary), contentAlignment = Alignment.Center) {
                Text("9", color = cs.onPrimary, fontSize = 19.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.size(4.dp))
            Box(Modifier.size(34.dp).clip(iconShapeOf("clover")).background(cs.tertiary), contentAlignment = Alignment.Center) {
                Text("41", color = cs.onTertiary, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
        }
        else -> Text("9:41", modifier, color = cs.onPrimaryContainer, fontSize = 26.sp, fontWeight = FontWeight.Normal)
    }
}

/** Icona dell'anteprima: tessera di vetro, forma espressiva o semplice cerchio, secondo il tema. */
@Composable
private fun MiniIcon(spec: ThemeSpec, index: Int, glyph: ImageVector, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val expressiveShapes = remember { listOf("cookie9", "clover", "puffy", "flower", "sunny", "cookie12", "gem").map { iconShapeOf(it) } }
    val shape: Shape
    val bg: Color
    val fg: Color
    when {
        spec.glass -> { shape = RoundedCornerShape(size * 0.3f); bg = Color.White.copy(alpha = 0.22f); fg = Color.White }
        // Lite: come le icone di serie dei Pixel, cerchi bianchi con il disegno colorato
        spec.lite -> {
            shape = CircleShape; bg = Color.White
            fg = listOf(Color(0xFF4285F4), Color(0xFFEA4335), Color(0xFF34A853), Color(0xFFFBBC05))[index % 4]
        }
        else -> {
            shape = expressiveShapes[index % expressiveShapes.size]
            val pal = listOf(cs.primary to cs.onPrimary, cs.secondaryContainer to cs.onSecondaryContainer, cs.tertiary to cs.onTertiary)
            val (b, f) = pal[index % pal.size]
            bg = b; fg = f
        }
    }
    Box(
        Modifier.size(size).clip(shape).background(bg)
            .then(if (spec.glass) Modifier.border(1.dp, Color.White.copy(alpha = 0.45f), shape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(glyph, null, tint = fg, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun MiniDock(spec: ThemeSpec, count: Int, iconSize: Dp, glyphs: List<ImageVector>) {
    val cs = MaterialTheme.colorScheme
    val shape = spec.uniformCorner?.let { RoundedCornerShape(it) } ?: RoundedCornerShape(50)
    val bg = when {
        spec.glass -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.16f)))
        // Lite: la dock non ha un pannello, le icone stanno direttamente sullo sfondo
        spec.lite -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        else -> Brush.verticalGradient(listOf(cs.surfaceContainerHigh, cs.surfaceContainerHigh))
    }
    Row(
        Modifier.fillMaxWidth().clip(shape).background(bg)
            .then(if (spec.glass) Modifier.border(1.dp, Color.White.copy(alpha = 0.5f), shape) else Modifier)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (i in 0 until count) MiniIcon(spec, i + 3, glyphs[(i + 3) % glyphs.size], iconSize * 0.85f)
    }
}

/** Colori delle icone Material You. */
val THEMED_ICON_COLORS = listOf(
    "primary" to "Principale (come i Pixel)",
    "secondary" to "Secondario, più tenue",
    "tertiary" to "Terziario, il colore di contrasto"
)

/** Miniatura di una forma d'icona con i colori Material You ([colorKey]: quelli scelti se null). */
@Composable
fun IconShapeSwatch(key: String, size: Dp = 34.dp, colorKey: String? = null) {
    val (bg, fg) = if (colorKey != null) themedIconColors(colorKey) else themedIconColors()
    Box(Modifier.size(size).clip(iconShapeOf(key)).background(bg), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Star, null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}
