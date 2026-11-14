package com.marco.mlauncher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.animate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

val LocalIconShape = androidx.compose.runtime.compositionLocalOf { "original" }

/** Squircola in stile iOS (superellisse). */
val IosShape: androidx.compose.ui.graphics.Shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
    val a = size.width / 2f
    val b = size.height / 2f
    val n = 5.0
    val steps = 96
    for (i in 0..steps) {
        val t = 2.0 * Math.PI * i / steps
        val c = Math.cos(t)
        val sn = Math.sin(t)
        val x = a + a * Math.signum(c) * Math.pow(Math.abs(c), 2.0 / n)
        val y = b + b * Math.signum(sn) * Math.pow(Math.abs(sn), 2.0 / n)
        if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
    }
    close()
}

@Composable
fun iconShapeFor(iconSize: Dp, fallbackRadius: Float): androidx.compose.ui.graphics.Shape {
    val key = LocalIconShape.current
    expressiveShape(key)?.let { return it }
    return when (key) {
        "circle" -> CircleShape
        "square" -> RoundedCornerShape(iconSize * 0.22f)
        "ios" -> IosShape
        else -> RoundedCornerShape(iconSize * fallbackRadius)
    }
}

/** Icone nel tema Liquid Glass: stile ("normal", "clear", "tinted"), riflessi ("off", "corners", "full") e loro forza. */
data class IconGlass(val style: String, val highlight: String, val strength: Float)

/** Impostazioni delle icone di vetro; null fuori dal tema Liquid Glass. */
val LocalIconGlass = androidx.compose.runtime.staticCompositionLocalOf<IconGlass?> { null }

/** Colori delle icone Material You: "primary", "secondary", "tertiary". */
val LocalThemedIconColor = androidx.compose.runtime.staticCompositionLocalOf { "secondary" }

/** Fondo e disegno delle icone Material You (come quelle a tema di Pixel). */
@Composable
fun themedIconColors(key: String = LocalThemedIconColor.current): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (key) {
        "primary" -> cs.primaryContainer to cs.onPrimaryContainer
        "tertiary" -> cs.tertiaryContainer to cs.onTertiaryContainer
        else -> cs.secondaryContainer to cs.onSecondaryContainer
    }
}

/** Porta un'icona a due colori del tema: le parti scure diventano [dark], quelle chiare [light]. */
fun duotoneFilter(dark: Color, light: Color): ColorFilter {
    fun row(d: Float, l: Float): List<Float> {
        val k = l - d
        return listOf(k * 0.3f, k * 0.59f, k * 0.11f, 0f, d * 255f)
    }
    val m = row(dark.red, light.red) + row(dark.green, light.green) + row(dark.blue, light.blue) + listOf(0f, 0f, 0f, 1f, 0f)
    return ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(m.toFloatArray()))
}

private val lightIcons = java.util.WeakHashMap<ImageBitmap, Boolean>()

/** L'icona è in prevalenza chiara? (media di una griglia di punti; calcolata una volta per icona) */
fun isLightIcon(img: ImageBitmap): Boolean = synchronized(lightIcons) {
    lightIcons.getOrPut(img) {
        try {
            val bm = img.asAndroidBitmap()
            if (bm.config == android.graphics.Bitmap.Config.HARDWARE) return@getOrPut false
            var sum = 0f
            var n = 0f
            for (i in 0 until 10) for (j in 0 until 10) {
                val c = bm.getPixel((bm.width * (i + 0.5f) / 10f).toInt(), (bm.height * (j + 0.5f) / 10f).toInt())
                val a = ((c ushr 24) and 255) / 255f
                if (a < 0.5f) continue
                val l = (0.299f * ((c shr 16) and 255) + 0.587f * ((c shr 8) and 255) + 0.114f * (c and 255)) / 255f
                sum += l
                n += 1f
            }
            n > 0f && sum / n > 0.55f
        } catch (e: Exception) {
            false
        }
    }
}

/** Riflessi del vetro sul bordo dell'icona: agli angoli ("corners") oppure un filo su tutto il bordo ("full"). */
fun Modifier.iconGlints(shape: androidx.compose.ui.graphics.Shape, mode: String, strength: Float): Modifier =
    if (mode == "off") this else this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }.asAndroidPath()
        val stroke = 1.dp.toPx()
        val w = size.width
        val h = size.height
        val rim = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = stroke
            if (mode == "full") color = android.graphics.Color.argb((0.6f * strength * 255f).toInt().coerceIn(0, 255), 255, 255, 255)
            else shader = Specular.cornerRim(w, h, strength, rim = 0f)
        }
        val gloss = if (mode == "corners") android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            shader = Specular.innerGloss(w, h, strength)
        } else null
        val k = if (w > 0f) 1f - stroke / w else 1f
        onDrawWithContent {
            drawContent()
            drawIntoCanvas { cv ->
                val c = cv.nativeCanvas
                gloss?.let { c.drawPath(path, it) }
                // il filo resta tutto dentro l'icona
                c.save()
                c.scale(k, k, w / 2f, h / 2f)
                c.drawPath(path, rim)
                c.restore()
            }
        }
    }

@Composable
fun AppIcon(app: AppEntry, iconSize: Dp, themed: Boolean, modifier: Modifier = Modifier) {
    val mono = app.mono
    val mode = LocalIconShape.current
    val shape = iconShapeFor(iconSize, 0.32f)
    val cs = MaterialTheme.colorScheme
    val glass = LocalIconGlass.current
    val glints = if (glass != null) Modifier.iconGlints(shape, glass.highlight, glass.strength) else Modifier
    if (glass != null && glass.style != "normal") {
        // Liquid Glass: logo a colori su vetro chiaro ("clear") o glifo colorato su vetro scuro ("tinted"), come iOS 26
        val dark = cs.surface.luminance() < 0.5f
        val clear = glass.style == "clear"
        val plate = when {
            clear && dark -> Color.White.copy(alpha = 0.16f)
            clear -> Color.White.copy(alpha = 0.46f)
            dark -> androidx.compose.ui.graphics.lerp(Color.Black, cs.primary, 0.22f).copy(alpha = 0.9f)
            else -> cs.primaryContainer.copy(alpha = 0.92f)
        }
        val glyph = if (dark) cs.primary else cs.onPrimaryContainer
        Box(modifier.size(iconSize).clip(shape).background(plate).then(glints), contentAlignment = Alignment.Center) {
            when {
                clear -> Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.fillMaxSize().padding(iconSize * 0.14f))
                mono != null -> Image(bitmap = mono, contentDescription = null, colorFilter = ColorFilter.tint(glyph), modifier = Modifier.fillMaxSize())
                else -> {
                    val src = app.raw ?: app.icon
                    Image(
                        bitmap = src,
                        contentDescription = null,
                        colorFilter = if (isLightIcon(src)) duotoneFilter(glyph, plate.copy(alpha = 1f)) else duotoneFilter(plate.copy(alpha = 1f), glyph),
                        modifier = Modifier.fillMaxSize().padding(iconSize * 0.12f).clip(shape)
                    )
                }
            }
        }
        return
    }
    if (themed) {
        // Material You per tutte le app: il glifo monocromatico dove l'app lo ha, altrimenti l'icona in due toni
        val (bg, fg) = themedIconColors()
        Box(
            modifier
                .size(iconSize)
                .clip(shape)
                .background(bg)
                .then(glints),
            contentAlignment = Alignment.Center
        ) {
            if (mono != null) {
                Image(
                    bitmap = mono,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(fg),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val src = app.raw ?: app.icon
                // il fondo dell'icona diventa il fondo a tema, il disegno il colore del glifo
                val light = isLightIcon(src)
                Image(
                    bitmap = src,
                    contentDescription = null,
                    colorFilter = if (light) duotoneFilter(fg, bg) else duotoneFilter(bg, fg),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else if (mode != "original" && app.raw != null) {
        Image(
            bitmap = app.raw,
            contentDescription = null,
            modifier = modifier.size(iconSize).clip(shape).then(glints)
        )
    } else {
        Image(bitmap = app.icon, contentDescription = null, modifier = modifier.size(iconSize).then(glints))
    }
}

val LocalFolderShape = androidx.compose.runtime.compositionLocalOf { "squircle" }
val LocalFolderStyle = androidx.compose.runtime.compositionLocalOf { "grid" to "tonal" }

@Composable
fun FolderIcon(folder: FolderItem, apps: Map<String, AppEntry>, iconSize: Dp, themed: Boolean) {
    val cs = MaterialTheme.colorScheme
    val (preview, bgKey) = LocalFolderStyle.current
    val bg = when (bgKey) {
        "primary" -> cs.primaryContainer
        "dark" -> androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)
        "glass" -> androidx.compose.ui.graphics.Color.White.copy(alpha = 0.28f)
        else -> cs.surfaceContainerHighest.copy(alpha = 0.6f)
    }
    val dock = LocalFolderDock.current
    val shapeKey = LocalFolderShape.current
    val shape = folderShapeOf(shapeKey)
    val iconCorner = minOf((dock?.corner ?: 0).dp, iconSize * 0.38f)
    val base = Modifier.size(iconSize)
    val look = when {
        dock == null -> base.clip(shape).background(bg)
        dock.style == "none" || dock.style == "tiles" -> base.dockTile(maxOf(dock.opacity, 50), dock.glass, iconCorner, shape)
        else -> base.dockLook(if (dock.style == "bar") "pill" else dock.style, dock.opacity, dock.glass, iconCorner, shape)
    }
    Box(
        look.padding(iconSize * folderShapeInset(shapeKey))
    ) {
        when (preview) {
            "grid2" -> {
                val mini = iconSize * 0.38f
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    for (r in 0 until 2) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            for (c in 0 until 2) {
                                val a = folder.appIds.getOrNull(r * 2 + c)?.let { apps[it] }
                                if (a != null) AppIcon(a, mini, themed) else Spacer(Modifier.size(mini))
                            }
                        }
                    }
                }
            }
            "stack" -> {
                // ventaglio: le prime tre icone sovrapposte
                val big = iconSize * 0.56f
                val list = folder.appIds.take(3).mapNotNull { apps[it] }.reversed()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    list.forEachIndexed { i, a ->
                        val k = list.size - 1 - i
                        Box(
                            Modifier
                                .offset(x = iconSize * 0.07f * k, y = -iconSize * 0.07f * k)
                                .graphicsLayer { rotationZ = k * 8f; alpha = 1f - 0.18f * k }
                        ) { AppIcon(a, big, themed) }
                    }
                }
            }
            else -> {
                val mini = iconSize * 0.24f
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    for (r in 0 until 3) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            for (c in 0 until 3) {
                                val a = folder.appIds.getOrNull(r * 3 + c)?.let { apps[it] }
                                if (a != null) AppIcon(a, mini, themed) else Spacer(Modifier.size(mini))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MinusBadge(modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .size(22.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(cs.errorContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(10.dp, 2.dp).background(cs.onErrorContainer, RoundedCornerShape(1.dp)))
    }
}

/** Tremolio in modalità modifica. */
@Composable
fun Modifier.jiggle(on: Boolean, seed: Int): Modifier {
    // la catena dei modificatori deve restare identica: altrimenti i gesti a valle si interrompono
    var rot by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(on) {
        if (on) {
            animate(
                initialValue = -1.6f,
                targetValue = 1.6f,
                animationSpec = infiniteRepeatable(
                    tween(110 + (seed and 0x3F), easing = LinearEasing),
                    RepeatMode.Reverse
                )
            ) { v, _ -> rot = v }
        } else {
            rot = 0f
        }
    }
    return this.graphicsLayer { rotationZ = rot }
}

/** Avvolge l'icona: se [exploding] parte l'animazione scelta e alla fine chiama [onDone]. */
@Composable
fun ExplodeBox(
    exploding: Boolean,
    mode: String,
    speed: Float,
    boxSize: Dp,
    onDone: () -> Unit,
    content: @Composable () -> Unit
) {
    val p = remember { Animatable(0f) }
    val done = rememberUpdatedState(onDone)
    val cs = MaterialTheme.colorScheme
    val colors = listOf(cs.primary, cs.tertiary, cs.secondary, cs.primaryContainer)
    LaunchedEffect(exploding) {
        if (exploding) {
            val base = if (mode == "boom") 650f else 350f
            p.animateTo(1f, tween((base * speed).toInt(), easing = LinearEasing))
            done.value()
        }
    }
    Box(Modifier.size(boxSize)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val v = p.value
                    if (mode == "boom") {
                        alpha = (1f - v * 2f).coerceIn(0f, 1f)
                        val sc = 1f + 0.25f * v
                        scaleX = sc
                        scaleY = sc
                    } else {
                        alpha = 1f - v
                        val sc = 1f - 0.2f * v
                        scaleX = sc
                        scaleY = sc
                    }
                }
        ) { content() }
        if (exploding && mode == "boom") {
            Canvas(Modifier.fillMaxSize()) {
                val v = p.value
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                for (i in 0 until 16) {
                    val ang = (i * 2.0 * Math.PI / 16.0 + (i % 3) * 0.25).toFloat()
                    val dist = this.size.width * (0.3f + 0.6f * ((i * 37) % 10) / 10f) * v
                    val rad = this.size.width * 0.09f * (1f - v) * (0.6f + (i % 4) * 0.2f)
                    drawCircle(
                        color = colors[i % 4],
                        radius = rad,
                        center = Offset(cx + cos(ang) * dist, cy + sin(ang) * dist),
                        alpha = (1f - v).coerceIn(0f, 1f)
                    )
                }
            }
        }
    }
}
