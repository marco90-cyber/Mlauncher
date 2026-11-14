package com.marco.mlauncher

import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil

@Composable
fun WidgetPicker(vm: LauncherViewModel, host: Host) {
    val cs = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = vm.overlay == Overlay.WIDGETS,
        enter = fadeIn(androidx.compose.animation.core.tween(Motion.SHEET, easing = Motion.StandardDecelerate)) +
            slideInVertically(androidx.compose.animation.core.tween(Motion.SHEET, easing = Motion.EmphasizedDecelerate)) { it / 2 },
        exit = fadeOut(androidx.compose.animation.core.tween(Motion.SHEET, easing = Motion.StandardAccelerate)) +
            slideOutVertically(androidx.compose.animation.core.tween(Motion.SHEET, easing = Motion.EmphasizedAccelerate)) { it / 2 }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    vm.overlay = Overlay.NONE
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = cs.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.74f)
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                PickerContent(vm, host)
            }
        }
    }
}

private class WidgetGroup(val pkg: String, val label: String, val list: List<AppWidgetProviderInfo>, val names: List<String>)

/** Widget di MLauncher: nome, tipo e grandezza. */
private val INTERNAL_WIDGETS = listOf(
    Triple("Orologio", "clock", 2 to 1), Triple("Orologio", "clock", 2 to 2), Triple("Orologio", "clock", 4 to 2),
    Triple("Orologio analogico", "analog", 2 to 2), Triple("Orologio analogico", "analog", 4 to 2),
    Triple("Orologio Sense", "senseClock", 2 to 2), Triple("Orologio Sense", "senseClock", 3 to 3),
    Triple("Orologio a cartellini Sense", "senseFlip", 4 to 2),
    Triple("Batteria", "battery", 1 to 1), Triple("Batteria", "battery", 2 to 1), Triple("Batteria", "battery", 2 to 2), Triple("Batteria", "battery", 4 to 2),
    Triple("Nota veloce", "notes", 2 to 2), Triple("Nota veloce", "notes", 4 to 2), Triple("Nota veloce", "notes", 4 to 3),
    Triple("Barra di ricerca", "search", 4 to 1), Triple("Barra di ricerca", "search", 3 to 1), Triple("Ricerca (icona)", "search", 1 to 1),
    Triple("Meteo", "weather", 2 to 2), Triple("Meteo", "weather", 4 to 2)
)

@Composable
internal fun PickerContent(vm: LauncherViewModel, host: Host, header: Boolean = true) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val groups = remember {
        val pm = ctx.packageManager
        try {
            vm.widgetMgr.installedProviders
                .filter { (it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0 }
                .groupBy { it.provider.packageName }
                .map { (pkg, list) ->
                    val label = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (e: Exception) {
                        pkg
                    }
                    val names = list.map { i -> try { i.loadLabel(pm) ?: "" } catch (e: Exception) { "" } }
                    WidgetGroup(pkg, label, list, names)
                }
                .sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    var expanded by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { vm.stackTarget = null; vm.minusPick = false }
    }
    // cerca widget: per nome del widget o dell'app
    val internal = INTERNAL_WIDGETS.filter { q.isEmpty() || it.first.lowercase().contains(q) }
    val stacks = !vm.minusPick && (q.isEmpty() || "stack".contains(q) || "pila".contains(q))
    val actions = MLA_ACTIONS.filter { q.isEmpty() || it.second.lowercase().contains(q) }
    val pageAction = q.isEmpty() || "vai a pagina".contains(q)
    val shown = if (q.isEmpty()) groups.map { it to it.list } else groups.mapNotNull { g ->
        if (g.label.lowercase().contains(q)) g to g.list
        else g.list.filterIndexed { i, _ -> g.names[i].lowercase().contains(q) }.takeIf { it.isNotEmpty() }?.let { g to it }
    }

    // il testo prende sempre il colore giusto sul fondo del pannello (anche dentro l'anteprima pagine)
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides cs.onSurface) {
    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        // nell'anteprima pagine maniglia e titolo li disegna il pannello che la contiene
        if (header) {
        Box(
            Modifier
                .padding(top = 10.dp)
                .align(Alignment.CenterHorizontally)
                .size(36.dp, 4.dp)
                .clip(CircleShape)
                .background(cs.outlineVariant)
        )
        Text(
            if (vm.minusPick) "Aggiungi alla pagina \u22121" else if (vm.stackTarget != null) "Aggiungi allo stack" else "Widget",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        }
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Cerca widget") },
            leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) androidx.compose.material3.IconButton(onClick = { query = "" }) {
                    androidx.compose.material3.Icon(Icons.Default.Close, "Cancella")
                }
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (internal.isNotEmpty() || stacks) item {
                SectionTitle("MLauncher")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    internal.forEach { (t, k, sz) -> item { InternalCard(vm, host, t, k, sz.first, sz.second) } }
                    if (stacks) item { EmptyStackCard(vm, 2, 2) }
                    if (stacks) item { EmptyStackCard(vm, 4, 2) }
                    if (stacks) item { EmptyStackCard(vm, 2, 1) }
                    if (stacks) item { EmptyStackCard(vm, 4, 4) }
                }
                Spacer(Modifier.height(12.dp))
            }
            // azioni di MLauncher (come le Azioni Nova): icone in Home che fanno qualcosa
            if (!vm.minusPick && vm.stackTarget == null && (actions.isNotEmpty() || pageAction)) item {
                SectionTitle("Azioni MLauncher")
                var pickPage by remember { mutableStateOf(false) }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    actions.forEach { (k, l) -> item { ActionCard(vm, k, l) { vm.addActionToHome(k) } } }
                    if (pageAction) item { ActionCard(vm, "page:1", "Vai a pagina\u2026") { pickPage = true } }
                }
                Text(
                    "\u201cApp recenti\u201d e \u201cBlocca schermo\u201d usano il servizio di accessibilità di MLauncher.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
                if (pickPage) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { pickPage = false },
                        title = { Text("Vai a quale pagina?") },
                        text = {
                            Column {
                                (1..ACTION_MAX_PAGE).chunked(3).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { n ->
                                            androidx.compose.material3.FilledTonalButton(
                                                onClick = { pickPage = false; vm.addActionToHome("page:$n") },
                                                modifier = Modifier.weight(1f)
                                            ) { Text("$n") }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { pickPage = false }) { Text("Annulla") } }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            if (q.isNotEmpty() && internal.isEmpty() && !stacks && actions.isEmpty() && !pageAction && shown.isEmpty()) item {
                Text(
                    "Nessun widget per \u201c" + query.trim() + "\u201d",
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
            }
            items(shown, key = { it.first.pkg }) { (g, list) ->
                // cercando, i gruppi trovati sono già aperti
                val open = expanded == g.pkg || q.isNotEmpty()
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { expanded = if (open) null else g.pkg }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val entry = vm.apps.values.firstOrNull { it.pkg == g.pkg }
                        if (entry != null) {
                            Image(entry.icon, null, Modifier.size(36.dp))
                        } else {
                            Spacer(Modifier.size(36.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.label, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${list.size} widget",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        Text(if (open) "−" else "+", fontSize = 22.sp, color = cs.primary)
                    }
                    if (open) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(list, key = { it.provider.flattenToString() }) { info ->
                                ExtCard(vm, host, info)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun CardFrame(width: Int, onClick: () -> Unit, title: String, size: String, preview: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(width.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(112.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            preview()
            // il tocco aggiunge il widget: non deve far partire l'app del widget
            val v = androidx.compose.ui.platform.LocalView.current
            Box(Modifier.matchParentSize().clickable { Haptics.confirm(v); onClick() })
        }
        Text(
            title,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(size, fontSize = 11.sp, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun InternalCard(vm: LauncherViewModel, host: Host, title: String, kind: String, sx: Int, sy: Int) {
    CardFrame(
        width = if (sx >= 4) 190 else 130,
        onClick = { vm.addWidgetFromPicker(kind, sx, sy) },
        title = title,
        size = "${sx}x$sy"
    ) {
        // anteprima: il vero widget, in piccolo, con la stessa proporzione
        val pw = (sx * 40).coerceAtMost(170)
        val ph = (sy * 40)
        Box(Modifier.size(pw.dp, ph.dp)) {
            WidgetContent(WidgetRef(kind), vm, host, {}, Modifier.fillMaxSize(), sample = true)
        }
    }
}

@Composable
private fun EmptyStackCard(vm: LauncherViewModel, sx: Int, sy: Int) {
    val cs = MaterialTheme.colorScheme
    CardFrame(
        width = if (sx >= 4) 190 else 130,
        onClick = { vm.addEmptyStack(sx, sy) },
        title = "Stack vuoto",
        size = "${sx}x$sy"
    ) {
        Box(
            Modifier
                .size((sx * 36).coerceAtMost(150).dp, (sy * 30).coerceAtMost(100).dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cs.secondaryContainer),
            contentAlignment = Alignment.Center
        ) { Text("+", color = cs.onSecondaryContainer, fontSize = 28.sp) }
    }
}

@Composable
private fun ExtCard(vm: LauncherViewModel, host: Host, info: AppWidgetProviderInfo) {
    val ctx = LocalContext.current
    val density = ctx.resources.displayMetrics.density
    val sx = ceil(((info.minWidth / density - 8f).coerceAtLeast(1f)) / vm.cellWdp.coerceAtLeast(40f)).toInt()
        .coerceIn(1, 4)
    val sy = ceil(((info.minHeight / density - 8f).coerceAtLeast(1f)) / vm.cellHdp.coerceAtLeast(40f)).toInt()
        .coerceIn(1, 4)
    val label = remember(info) {
        try {
            info.loadLabel(ctx.packageManager)
        } catch (e: Exception) {
            "Widget"
        }
    }
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.Default) {
            try {
                val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
                if (d == null) {
                    null
                } else {
                    val iw = d.intrinsicWidth.coerceAtLeast(1)
                    val ih = d.intrinsicHeight.coerceAtLeast(1)
                    val scale = 320f / maxOf(iw, ih)
                    val bw = (iw * scale).toInt().coerceAtLeast(1)
                    val bh = (ih * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, bw, bh)
                    d.draw(Canvas(bmp))
                    bmp.asImageBitmap()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    CardFrame(
        width = if (sx >= 3) 190 else 140,
        onClick = { host.addWidget(info) },
        title = label,
        size = "${sx}x$sy"
    ) {
        val p = preview
        if (p != null) {
            Image(p, null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit)
        } else {
            Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** Un'azione di MLauncher nel selettore: tocca per metterla in Home. */
@Composable
private fun ActionCard(vm: LauncherViewModel, spec: String, label: String, onClick: () -> Unit) {
    val entry = vm.apps.values.firstOrNull { it.isAction && it.cls == ACTION_PREFIX + spec }
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (entry != null) Image(entry.icon, null, Modifier.size(52.dp)) else Spacer(Modifier.size(52.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}
