package com.marco.mlauncher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

interface Host {
    fun launch(app: AppEntry, bounds: Rect?)
    fun uninstall(app: AppEntry)
    fun appInfo(app: AppEntry)
    fun expandNotifications()
    fun requestHomeRole()
    fun pickWallpaper()
}

class DragState(val item: HomeItem, start: Offset) {
    var pos by mutableStateOf(start)
}

class DragCallbacks(
    val start: (HomeItem, Offset) -> Unit,
    val move: (Offset) -> Unit,
    val end: () -> Unit
)

class BoundsHolder {
    var root: Offset = Offset.Zero
    var window: Rect = Rect.Zero
}

private const val DOCK_SLOTS = 5

fun Modifier.pageEffect(effect: String, state: PagerState, page: Int): Modifier = graphicsLayer {
    val o = page - state.currentPage - state.currentPageOffsetFraction
    val a = abs(o)
    when (effect) {
        "fade" -> {
            translationX = -o * size.width
            alpha = (1f - a).coerceIn(0f, 1f)
        }
        "cube" -> {
            cameraDistance = 14f * density
            transformOrigin = TransformOrigin(if (o < 0f) 1f else 0f, 0.5f)
            rotationY = -90f * o.coerceIn(-1f, 1f)
        }
        "rotate" -> {
            transformOrigin = TransformOrigin(0.5f, 1.3f)
            rotationZ = -18f * o.coerceIn(-1f, 1f)
            alpha = (1f - a * 0.7f).coerceIn(0f, 1f)
        }
        "cascade" -> {
            if (o < 0f) {
                translationX = -o * size.width
                val sc = 1f - 0.18f * a.coerceAtMost(1f)
                scaleX = sc
                scaleY = sc
                alpha = (1f - a).coerceIn(0f, 1f)
            }
        }
        else -> {}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { vm.pageCount }
    var gridRect by remember { mutableStateOf(Rect.Zero) }
    var dockRect by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<DragState?>(null) }
    val edit = vm.editMode
    val topPad by animateDpAsState(if (edit) 56.dp else 6.dp, label = "topPad")

    fun targetOf(p: Offset): Triple<Int, Int, Int>? {
        if (dockRect != Rect.Zero && dockRect.contains(p)) {
            val w = dockRect.width / DOCK_SLOTS
            val x = ((p.x - dockRect.left) / w).toInt().coerceIn(0, DOCK_SLOTS - 1)
            return Triple(-1, x, 0)
        }
        if (gridRect != Rect.Zero && gridRect.contains(p)) {
            val cw = gridRect.width / s.cols
            val ch = gridRect.height / s.rows
            val x = ((p.x - gridRect.left) / cw).toInt().coerceIn(0, s.cols - 1)
            val y = ((p.y - gridRect.top) / ch).toInt().coerceIn(0, s.rows - 1)
            return Triple(pagerState.currentPage, x, y)
        }
        return null
    }

    val cb = remember {
        DragCallbacks(
            start = { item, pos -> drag = DragState(item, pos) },
            move = { pos -> drag?.pos = pos },
            end = {
                val d = drag
                if (d != null) {
                    val t = targetOf(d.pos)
                    if (t != null) vm.dropItem(d.item, t.first, t.second, t.third)
                }
                drag = null
            }
        )
    }

    // torna alla prima pagina quando si preme Home
    LaunchedEffect(vm.homeSignal) {
        if (vm.homeSignal > 0) pagerState.animateScrollToPage(0)
    }

    // cambio pagina trascinando vicino al bordo
    val edgePx = with(density) { 36.dp.toPx() }
    val zone by remember {
        derivedStateOf {
            val d = drag
            if (d == null || gridRect == Rect.Zero) 0
            else if (d.pos.x < gridRect.left + edgePx) -1
            else if (d.pos.x > gridRect.right - edgePx) 1
            else 0
        }
    }
    LaunchedEffect(zone) {
        while (zone != 0) {
            delay(650)
            if (zone == 0) break
            val target = pagerState.currentPage + zone
            if (target >= vm.pageCount) {
                vm.addPage()
                delay(80)
            }
            if (target >= 0 && target < vm.pageCount) pagerState.animateScrollToPage(target)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Spacer(Modifier.height(topPad))
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 8,
                userScrollEnabled = drag == null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { gridRect = it.boundsInRoot() }
            ) { page ->
                HomePage(
                    page = page,
                    vm = vm,
                    host = host,
                    pagerState = pagerState,
                    dragId = drag?.item?.id,
                    cb = cb
                )
            }
            PageDots(vm.pageCount, pagerState.currentPage)
            Dock(
                vm = vm,
                host = host,
                dragId = drag?.item?.id,
                cb = cb,
                onRect = { dockRect = it }
            )
        }

        // barra modifica
        AnimatedVisibility(
            visible = edit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 6.dp),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BarButton("Sfondo") { host.pickWallpaper() }
                BarButton("+ Pagina") {
                    vm.addPage()
                    scope.launch {
                        delay(80)
                        pagerState.animateScrollToPage(vm.pageCount - 1)
                    }
                }
                BarButton("Impostazioni") { vm.overlay = Overlay.SETTINGS }
                BarButton("Fine") { vm.exitEdit() }
            }
        }

        // evidenzia la cella di destinazione
        val d = drag
        if (d != null) {
            val t = targetOf(d.pos)
            if (t != null) {
                val rect = if (t.first < 0) {
                    val w = dockRect.width / DOCK_SLOTS
                    Rect(dockRect.left + t.second * w, dockRect.top, dockRect.left + (t.second + 1) * w, dockRect.bottom)
                } else {
                    val w = gridRect.width / s.cols
                    val h = gridRect.height / s.rows
                    Rect(
                        gridRect.left + t.second * w, gridRect.top + t.third * h,
                        gridRect.left + (t.second + 1) * w, gridRect.top + (t.third + 1) * h
                    )
                }
                Box(
                    Modifier
                        .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                        .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                        .padding(4.dp)
                        .background(cs.primary.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                )
            }
            // icona trascinata
            val previewSize = 64.dp * s.iconScale
            val half = with(density) { (previewSize / 2).toPx() }
            Box(
                Modifier
                    .offset { IntOffset((d.pos.x - half).roundToInt(), (d.pos.y - half).roundToInt()) }
                    .size(previewSize)
                    .graphicsLayer {
                        scaleX = 1.15f
                        scaleY = 1.15f
                    }
            ) {
                val it = d.item
                if (it is AppItem) {
                    vm.apps[it.appId]?.let { a -> AppIcon(a, previewSize, s.themedIcons) }
                } else if (it is FolderItem) {
                    FolderIcon(it, vm.apps, previewSize, s.themedIcons)
                }
            }
        }
    }
}

@Composable
private fun BarButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(36.dp)
    ) { Text(text, fontSize = 12.sp, maxLines = 1) }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().height(18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (count > 1) {
            repeat(count) { i ->
                val sel = current == i
                val w by animateDpAsState(if (sel) 16.dp else 6.dp, label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(w, 6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = if (sel) 0.95f else 0.5f))
                )
            }
        }
    }
}

@Composable
private fun HomePage(
    page: Int,
    vm: LauncherViewModel,
    host: Host,
    pagerState: PagerState,
    dragId: String?,
    cb: DragCallbacks
) {
    val s = vm.settings
    val haptic = LocalHapticFeedback.current
    val swipePx = with(LocalDensity.current) { 56.dp.toPx() }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pageEffect(s.pageEffect, pagerState, page)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (vm.editMode) vm.exitEdit() },
                    onLongPress = {
                        if (!vm.editMode) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.editMode = true
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = {
                        if (!vm.editMode && vm.openFolderId == null) {
                            if (total < -swipePx) {
                                vm.overlay = Overlay.DRAWER
                            } else if (total > swipePx) {
                                if (vm.settings.swipeDown == "search") vm.overlay = Overlay.DRAWER
                                else host.expandNotifications()
                            }
                        }
                    }
                )
            }
    ) {
        val cw = maxWidth / s.cols
        val ch = maxHeight / s.rows
        for (item in vm.items) {
            if (item.page == page) {
                key(item.id) {
                    GridItem(
                        item = item,
                        vm = vm,
                        host = host,
                        cellW = cw,
                        cellH = ch,
                        labels = s.showLabels,
                        hidden = dragId == item.id,
                        cb = cb,
                        modifier = Modifier.offset(cw * item.x, ch * item.y)
                    )
                }
            }
        }
    }
}

@Composable
private fun Dock(
    vm: LauncherViewModel,
    host: Host,
    dragId: String?,
    cb: DragCallbacks,
    onRect: (Rect) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(84.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(cs.surfaceContainer.copy(alpha = 0.5f))
            .onGloballyPositioned { onRect(it.boundsInRoot()) }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cw = maxWidth / DOCK_SLOTS
            for (item in vm.items) {
                if (item.page == -1) {
                    key(item.id) {
                        GridItem(
                            item = item,
                            vm = vm,
                            host = host,
                            cellW = cw,
                            cellH = maxHeight,
                            labels = false,
                            hidden = dragId == item.id,
                            cb = cb,
                            modifier = Modifier.offset(cw * item.x, 0.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GridItem(
    item: HomeItem,
    vm: LauncherViewModel,
    host: Host,
    cellW: Dp,
    cellH: Dp,
    labels: Boolean,
    hidden: Boolean,
    cb: DragCallbacks,
    modifier: Modifier
) {
    val s = vm.settings
    val haptic = LocalHapticFeedback.current
    val holder = remember { BoundsHolder() }
    val curItem by rememberUpdatedState(item)
    val curCb by rememberUpdatedState(cb)
    val edit = vm.editMode
    // misure proporzionali alla cella: si adattano a qualsiasi schermo
    val base = minOf(cellW * 0.66f, cellH * (if (labels) 0.52f else 0.62f))
    val iconSize = minOf(base * s.iconScale, 84.dp)
    val app = (item as? AppItem)?.let { vm.apps[it.appId] }
    if (item is AppItem && app == null) return

    Box(modifier.size(cellW, cellH)) {
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    holder.root = it.positionInRoot()
                    holder.window = it.boundsInWindow()
                }
                .alpha(if (hidden) 0f else 1f)
                .jiggle(edit && !hidden, item.id.hashCode())
                .pointerInput(item.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val lp = awaitLongPressOrCancellation(down.id)
                        if (lp != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!vm.editMode) vm.editMode = true
                            curCb.start(curItem, holder.root + lp.position)
                            drag(lp.id) { ch ->
                                ch.consume()
                                curCb.move(holder.root + ch.position)
                            }
                            curCb.end()
                        } else {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && !up.pressed && !vm.editMode) {
                                when (val cur = curItem) {
                                    is AppItem -> vm.apps[cur.appId]?.let { a -> host.launch(a, holder.window) }
                                    is FolderItem -> vm.openFolderId = cur.folderId
                                }
                            }
                        }
                    }
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                if (item is AppItem && app != null) {
                    ExplodeBox(
                        exploding = item.appId in vm.exploding,
                        mode = s.uninstallAnim,
                        speed = s.animFactor,
                        boxSize = iconSize,
                        onDone = { vm.finishExplode(item.appId) }
                    ) { AppIcon(app, iconSize, s.themedIcons) }
                    if (edit && app.removable && !hidden) {
                        MinusBadge(Modifier.align(Alignment.TopStart)) { host.uninstall(app) }
                    }
                } else if (item is FolderItem) {
                    FolderIcon(item, vm.apps, iconSize, s.themedIcons)
                }
            }
            if (labels) {
                val label = when (item) {
                    is AppItem -> app?.label ?: ""
                    is FolderItem -> item.name
                }
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f)),
                    modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
                )
            }
        }
    }
}
