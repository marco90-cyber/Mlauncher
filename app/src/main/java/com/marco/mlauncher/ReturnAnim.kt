package com.marco.mlauncher

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.hypot
import kotlin.math.sin

/** Animazioni del ritorno alla Home (da un'app o allo sblocco). */
val RETURN_ANIMS = listOf(
    "settle" to "Assestamento (Pixel / HyperOS)",
    "approach" to "Avvicinamento (iOS)",
    "cascade_down" to "Cascata dall'alto",
    "cascade_up" to "Cascata dal basso",
    "wave" to "Onda dall'app chiusa",
    "fade" to "Dissolvenza",
    "none" to "Nessuna"
)

/** Durata complessiva (ms) prima della velocità scelta. */
fun returnDuration(style: String): Int = when (style) {
    "cascade_down", "cascade_up", "wave" -> 620
    "fade" -> 260
    else -> 380
}

/** Trasformazione di tutta la Home per le animazioni "d'insieme". */
fun Modifier.returnWhole(vm: LauncherViewModel): Modifier = this.graphicsLayer {
    val p = vm.returnP
    if (p >= 1f) return@graphicsLayer
    val e = Motion.EmphasizedDecelerate.transform(p)
    when (vm.settings.returnAnim) {
        "settle" -> {
            val sc = 1.07f - 0.07f * e
            scaleX = sc; scaleY = sc
            alpha = 0.35f + 0.65f * e
        }
        "approach" -> {
            val sc = 0.86f + 0.14f * e
            scaleX = sc; scaleY = sc
            alpha = e
        }
        "fade" -> alpha = Motion.StandardDecelerate.transform(p)
    }
}

/**
 * Trasformazione di un singolo elemento per le animazioni "a cascata" e "a onda",
 * più il rimbalzo dell'icona dell'app appena chiusa.
 */
fun Modifier.returnItem(vm: LauncherViewModel, item: HomeItem): Modifier = this.graphicsLayer {
    val p = vm.returnP
    if (p >= 1f) return@graphicsLayer
    val style = vm.settings.returnAnim
    val rows = vm.gridRows.coerceAtLeast(1)
    val y = if (item.page == -1) rows else item.y
    val delay = when (style) {
        "cascade_down" -> y.toFloat() / (rows + 1) * 0.45f
        "cascade_up" -> (rows - y).toFloat() / (rows + 1) * 0.45f
        "wave" -> {
            val o = vm.returnOrigin
            val cx = if (item.page == -1) item.x.toFloat() * vm.gridCols / vm.dockCount.coerceAtLeast(1) else item.x + item.spanX / 2f
            val d = hypot(cx - o.first, y + item.spanY / 2f - o.second)
            (d / hypot(vm.gridCols.toFloat(), rows + 1f)).coerceIn(0f, 1f) * 0.45f
        }
        else -> -1f
    }
    if (delay >= 0f) {
        val t = ((p - delay) / 0.55f).coerceIn(0f, 1f)
        val e = Motion.EmphasizedDecelerate.transform(t)
        alpha = e
        val sc = 0.82f + 0.18f * e
        scaleX = sc; scaleY = sc
        if (style.startsWith("cascade")) translationY = (1f - e) * (if (style == "cascade_down") -24f else 24f) * density
    }
    // rimbalzo dell'app chiusa: grande-piccolo-normale verso la fine dell'animazione
    val rid = vm.returnAppId
    if (vm.settings.returnBounce && rid != null) {
        val hit = (item is AppItem && item.appId == rid) || (item is FolderItem && rid in item.appIds)
        if (hit) {
            val t = ((p - 0.35f) / 0.65f).coerceIn(0f, 1f)
            val b = 1f + 0.16f * sin(t * Math.PI.toFloat()) * (1f - t * 0.6f)
            scaleX *= b; scaleY *= b
        }
    }
}

/** Zoom leggero dello sfondo di MLauncher durante il ritorno (effetto profondità). */
fun Modifier.returnWall(vm: LauncherViewModel): Modifier = this.graphicsLayer {
    val p = vm.returnP
    if (p >= 1f || !vm.settings.returnWallZoom) return@graphicsLayer
    val e = Motion.EmphasizedDecelerate.transform(p)
    val sc = 1.06f - 0.06f * e
    scaleX = sc; scaleY = sc
}
