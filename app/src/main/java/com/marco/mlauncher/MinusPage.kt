package com.marco.mlauncher

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Animazione di apertura/chiusura della pagina −1 (una sola alla volta). */
object MinusAnim {
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun settle(scope: kotlinx.coroutines.CoroutineScope, vm: LauncherViewModel, open: Boolean) {
        cancel()
        val from = vm.minusP
        val to = if (open) 1f else 0f
        if (from == to) return
        job = scope.launch {
            animate(from, to, animationSpec = tween(((if (open) 320 else 260) * vm.settings.animFactor).toInt(), easing = Motion.EmphasizedDecelerate)) { v, _ ->
                vm.minusP = v
            }
        }
    }
}

private fun greeting(h: Int) = when (h) {
    in 5..11 -> "Buongiorno"
    in 12..17 -> "Buon pomeriggio"
    in 18..22 -> "Buonasera"
    else -> "Buonanotte"
}

/**
 * Pagina −1: si apre scorrendo verso destra dalla prima pagina.
 * Saluto, data e meteo, ricerca e un contenitore con i widget scelti, in colonna.
 */
@Composable
fun MinusPage(vm: LauncherViewModel, host: Host) {
    val p = vm.minusP
    val scope = rememberCoroutineScope()
    // chiusura chiesta da fuori (tasto Home, indietro)
    val firstSignal = remember { vm.minusCloseSignal }
    LaunchedEffect(vm.minusCloseSignal) {
        if (vm.minusCloseSignal != firstSignal) MinusAnim.settle(scope, vm, false)
    }
    if (p <= 0.001f) return
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val widthPx = vm.screenPx.width.toFloat().coerceAtLeast(1f)

    Box(Modifier.fillMaxSize()) {
        // velo del colore del tema (chiaro o scuro, come le impostazioni)
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = p }
                .background(cs.surface.copy(alpha = s.minusDim / 100f))
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = -(1f - p) * size.width }
                .pointerInput(Unit) { detectTapGestures { } }
                .pointerInput(Unit) {
                    var lastDx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { MinusAnim.cancel(); lastDx = 0f },
                        onDragEnd = { MinusAnim.settle(scope, vm, if (lastDx < -8f) false else vm.minusP > 0.6f) },
                        onDragCancel = { MinusAnim.settle(scope, vm, vm.minusP > 0.5f) },
                        onHorizontalDrag = { ch, dx ->
                            ch.consume()
                            lastDx = dx
                            vm.minusP = (vm.minusP + dx / widthPx).coerceIn(0f, 1f)
                        }
                    )
                }
        ) {
            MinusContent(vm, host)
        }
    }
}

@Composable
private fun MinusContent(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L)
            now = System.currentTimeMillis()
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 20.dp, bottom = bottom + 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "head") {
            Column(Modifier.padding(horizontal = 8.dp)) {
                if (s.minusGreet) {
                    val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
                    val name = vm.greetName()
                    Text(
                        greeting(h) + if (name.isNotEmpty()) ", $name" else "",
                        color = cs.onSurface,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 36.sp
                    )
                }
                val day = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now))
                    .replaceFirstChar { it.uppercase() }
                val w = vm.weather
                val line = if (s.minusWeather && w != null) "$day · ${w.temp}° ${weatherText(w.code)}" else day
                Text(
                    line,
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = if (s.minusGreet) 4.dp else 0.dp)
                )
            }
        }
        item(key = "search") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHigh.copy(alpha = 0.92f))
                    .clickable { vm.searchFocus = true; vm.overlay = Overlay.DRAWER }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text("Cerca app e web", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item(key = "box") {
            MinusContainer(vm) {
                if (vm.minusWidgets.isEmpty()) {
                    MinusEmpty(vm)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        vm.minusWidgets.forEachIndexed { i, m ->
                            androidx.compose.runtime.key(m.uid) { MinusWidgetCard(vm, host, m, i) }
                        }
                        AddRow(vm)
                    }
                }
            }
        }
    }
}

/** Il contenitore dei widget, con l'aspetto scelto nelle impostazioni. */
@Composable
private fun MinusContainer(vm: LauncherViewModel, content: @Composable () -> Unit) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    val dockOk = s.dockStyle != "none" && s.dockStyle != "tiles"
    val look = when {
        s.minusStyle == "dock" && dockOk -> Modifier.clip(shape)
            .background(if (s.dockStyle == "liquid") Color.Transparent else cs.surfaceContainerHigh.copy(alpha = 0.45f))
            .dockLook(if (s.dockStyle == "bar") "pill" else s.dockStyle, maxOf(s.dockOpacity, 40), s.dockGlass, 32.dp)
        s.minusStyle == "glass" -> Modifier.clip(shape)
            .background(cs.surface.copy(alpha = 0.30f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
        else -> Modifier.clip(shape).background(cs.surfaceContainer.copy(alpha = 0.94f))
    }
    Box(Modifier.fillMaxWidth().then(look).animateContentSize().padding(12.dp)) { content() }
}

@Composable
private fun MinusWidgetCard(vm: LauncherViewModel, host: Host, m: MinusW, index: Int) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val h = when (m.size) {
        1 -> 112.dp
        3 -> 320.dp
        else -> 196.dp
    }
    Box(Modifier.fillMaxWidth().height(h)) {
        val onLong = { if (!vm.blockedByLock()) menu = true }
        GlassWidgetFrame(m.ref.kind) { WidgetContent(m.ref, vm, host, onLong, Modifier.fillMaxSize()) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (index > 0) DropdownMenuItem(text = { Text("Sposta su") }, onClick = { menu = false; vm.moveMinus(m.uid, -1) })
            if (index < vm.minusWidgets.size - 1) DropdownMenuItem(text = { Text("Sposta giù") }, onClick = { menu = false; vm.moveMinus(m.uid, 1) })
            listOf(1 to "Piccolo", 2 to "Medio", 3 to "Grande").forEach { (k, l) ->
                if (k != m.size) DropdownMenuItem(text = { Text(l) }, onClick = { menu = false; vm.sizeMinus(m.uid, k) })
            }
            DropdownMenuItem(
                text = { Text("Rimuovi", color = cs.error) },
                onClick = { menu = false; vm.removeMinus(m.uid) }
            )
        }
    }
}

@Composable
private fun AddRow(vm: LauncherViewModel) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
        FilledTonalButton(onClick = { vm.openMinusPicker() }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Aggiungi widget")
        }
    }
}

/** Pagina vuota: illustrazione, invito e due posti tratteggiati dove andranno i widget. */
@Composable
private fun MinusEmpty(vm: LauncherViewModel) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val a = cs.primary
        val b = cs.tertiary
        val c = cs.secondaryContainer
        Canvas(Modifier.size(150.dp, 112.dp)) {
            val w = size.width
            val hh = size.height
            // tre schede sovrapposte e un più
            drawRoundRect(c, Offset(w * 0.18f, hh * 0.04f), Size(w * 0.64f, hh * 0.30f), CornerRadius(18f, 18f), alpha = 0.55f)
            drawRoundRect(b.copy(alpha = 0.55f), Offset(w * 0.08f, hh * 0.20f), Size(w * 0.84f, hh * 0.36f), CornerRadius(22f, 22f))
            drawRoundRect(a, Offset(0f, hh * 0.40f), Size(w, hh * 0.56f), CornerRadius(26f, 26f))
            drawRoundRect(cs.onPrimary.copy(alpha = 0.85f), Offset(w * 0.10f, hh * 0.52f), Size(w * 0.30f, hh * 0.10f), CornerRadius(12f, 12f))
            drawRoundRect(cs.onPrimary.copy(alpha = 0.55f), Offset(w * 0.10f, hh * 0.70f), Size(w * 0.48f, hh * 0.08f), CornerRadius(10f, 10f))
            drawCircle(cs.onPrimary, radius = hh * 0.13f, center = Offset(w * 0.80f, hh * 0.68f))
            val r = hh * 0.065f
            val cx = w * 0.80f
            val cy = hh * 0.68f
            drawLine(a, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 6f)
            drawLine(a, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 6f)
        }
        Spacer(Modifier.height(16.dp))
        Text("La tua pagina widget", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
        Text(
            "Orologio, meteo, note o i widget delle tue app: li trovi sempre qui, a sinistra della prima pagina.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        AddRow(vm)
        Spacer(Modifier.height(16.dp))
        GhostSlot(vm, 96.dp)
        Spacer(Modifier.height(12.dp))
        GhostSlot(vm, 150.dp)
    }
}

@Composable
private fun GhostSlot(vm: LauncherViewModel, h: androidx.compose.ui.unit.Dp) {
    val cs = MaterialTheme.colorScheme
    val line = cs.outline.copy(alpha = 0.55f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(h)
            .clip(RoundedCornerShape(24.dp))
            .clickable { vm.openMinusPicker() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val st = 2.dp.toPx()
            drawRoundRect(
                line,
                topLeft = Offset(st / 2, st / 2),
                size = Size(size.width - st, size.height - st),
                cornerRadius = CornerRadius(24.dp.toPx()),
                style = Stroke(st, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            )
        }
        Icon(Icons.Default.Add, contentDescription = "Aggiungi widget", tint = line)
    }
}
