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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AppIcon(app: AppEntry, iconSize: Dp, themed: Boolean, modifier: Modifier = Modifier) {
    val mono = app.mono
    if (themed && mono != null) {
        val cs = MaterialTheme.colorScheme
        Box(
            modifier
                .size(iconSize)
                .clip(RoundedCornerShape(iconSize * 0.32f))
                .background(cs.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = mono,
                contentDescription = null,
                colorFilter = ColorFilter.tint(cs.onSecondaryContainer),
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Image(bitmap = app.icon, contentDescription = null, modifier = modifier.size(iconSize))
    }
}

@Composable
fun FolderIcon(folder: FolderItem, apps: Map<String, AppEntry>, iconSize: Dp, themed: Boolean) {
    val cs = MaterialTheme.colorScheme
    val mini = iconSize * 0.24f
    Box(
        Modifier
            .size(iconSize)
            .clip(RoundedCornerShape(iconSize * 0.30f))
            .background(cs.surfaceContainerHighest.copy(alpha = 0.6f))
            .padding(iconSize * 0.10f)
    ) {
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
    if (!on) return this
    val t = rememberInfiniteTransition(label = "jiggle")
    val r by t.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            tween(110 + (seed and 0x3F), easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "jr"
    )
    return this.graphicsLayer { rotationZ = r }
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
