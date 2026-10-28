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
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
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

private class WidgetGroup(val pkg: String, val label: String, val list: List<AppWidgetProviderInfo>)

@Composable
private fun PickerContent(vm: LauncherViewModel, host: Host) {
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
                    WidgetGroup(pkg, label, list)
                }
                .sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    var expanded by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { vm.stackTarget = null }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(
            Modifier
                .padding(top = 10.dp)
                .align(Alignment.CenterHorizontally)
                .size(36.dp, 4.dp)
                .clip(CircleShape)
                .background(cs.outlineVariant)
        )
        Text(
            if (vm.stackTarget != null) "Aggiungi allo stack" else "Widget",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                SectionTitle("MLauncher")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { InternalCard(vm, host, "Orologio", "clock", 2, 1) }
                    item { InternalCard(vm, host, "Orologio", "clock", 2, 2) }
                    item { InternalCard(vm, host, "Orologio", "clock", 4, 2) }
                    item { InternalCard(vm, host, "Meteo", "weather", 2, 2) }
                    item { InternalCard(vm, host, "Meteo", "weather", 4, 2) }
                    item { EmptyStackCard(vm, 2, 2) }
                    item { EmptyStackCard(vm, 4, 2) }
                    item { EmptyStackCard(vm, 2, 1) }
                    item { EmptyStackCard(vm, 4, 4) }
                    item { StackCard(vm, host, "Stack", 2, 2, listOf("clock", "weather")) }
                    item { StackCard(vm, host, "Stack", 4, 2, listOf("clock", "weather")) }
                    item { StackCard(vm, host, "Stack meteo", 2, 2, listOf("weather", "clock")) }
                    item { StackCard(vm, host, "Stack", 2, 1, listOf("clock", "weather")) }
                }
                Spacer(Modifier.height(12.dp))
            }
            items(groups, key = { it.pkg }) { g ->
                val open = expanded == g.pkg
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
                                "${g.list.size} widget",
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
                            items(g.list, key = { it.provider.flattenToString() }) { info ->
                                ExtCard(vm, host, info)
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
        ) { preview() }
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
private fun StackCard(vm: LauncherViewModel, host: Host, title: String, sx: Int, sy: Int, kinds: List<String>) {
    CardFrame(
        width = if (sx >= 4) 190 else 130,
        onClick = { vm.addStackPreset(sx, sy, kinds) },
        title = title,
        size = "${sx}x$sy · scorrevole"
    ) {
        val pw = (sx * 40).coerceAtMost(170)
        val ph = sy * 40
        Box(Modifier.size(pw.dp, ph.dp)) {
            WidgetContent(WidgetRef(kinds.first()), vm, host, {}, Modifier.fillMaxSize(), sample = true)
        }
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
