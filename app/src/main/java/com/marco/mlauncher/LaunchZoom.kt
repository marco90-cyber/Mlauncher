package com.marco.mlauncher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

/**
 * Apertura app: l'icona toccata si ingrandisce e svanisce sopra la finestra che si apre,
 * con gli angoli tondi (il vero passaggio icona -> finestra del Pixel non e' permesso ai launcher normali).
 */
@Composable
fun LaunchZoomOverlay(vm: LauncherViewModel) {
    val z = vm.launchZoom ?: return
    val p = remember(z) { Animatable(0f) }
    val ms = (220 * vm.settings.animFactor).toInt().coerceAtLeast(1)
    LaunchedEffect(z) { p.animateTo(1f, tween(ms, easing = Motion.EmphasizedDecelerate)) }
    val (bmp, r) = z
    val d = LocalDensity.current
    Image(
        bitmap = bmp,
        contentDescription = null,
        modifier = Modifier
            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
            .size(with(d) { r.width.toDp() }, with(d) { r.height.toDp() })
            .graphicsLayer {
                val sc = 1f + 1.8f * p.value
                scaleX = sc
                scaleY = sc
                alpha = 1f - p.value
            }
            .clip(RoundedCornerShape(22))
    )
}
