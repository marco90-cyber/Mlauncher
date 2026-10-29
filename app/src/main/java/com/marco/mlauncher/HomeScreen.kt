package com.marco.mlauncher

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

interface Host {
    fun launch(app: AppEntry, bounds: Rect?)
    fun uninstall(app: AppEntry)
    fun appInfo(app: AppEntry)
    fun expandNotifications()
    fun requestHomeRole()
    fun pickWallpaper()
    fun requestLocation()
    fun addWidget(info: AppWidgetProviderInfo)
    fun exportBackup()
    fun importBackup()
    fun openListenerSettings()
    fun openAccessibilitySettings()
    fun lockScreen(): Boolean
    fun openClockApp()
    fun requestBluetooth()
    fun webSearch(query: String)
    fun restartLauncher()
}

class DragState(val item: HomeItem, start: Offset) {
    var pos by mutableStateOf(start)
    /** id delle altre app raccolte toccandole con un secondo dito */
    val collected = androidx.compose.runtime.mutableStateListOf<String>()
}

class DragCallbacks(
    val start: (HomeItem, Offset) -> Unit,
    val move: (Offset) -> Unit,
    val end: () -> Unit,
    val collect: (HomeItem) -> Unit = {}
)

class BoundsHolder {
    var root: Offset = Offset.Zero
    var window: Rect = Rect.Zero
    var w: Float = 1f
    var h: Float = 1f
}


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

@Composable
fun HomeScreen(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current
    val pagerState = rememberPagerState { vm.pageCount }
    var gridRect by remember { mutableStateOf(Rect.Zero) }
    var dockRect by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<DragState?>(null) }
    var menuItem by remember { mutableStateOf<HomeItem?>(null) }
    var menuRect by remember { mutableStateOf(Rect.Zero) }
    val edit = vm.editMode
    val topPad by animateDpAsState(if (edit) 56.dp else 6.dp, androidx.compose.animation.core.tween(Motion.SHEET, easing = Motion.EmphasizedDecelerate), label = "topPad")
    val folderZonePx = with(density) { 48.dp.toPx() }
    val switchPx = with(density) { 60.dp.toPx() }
    val dragRef by rememberUpdatedState(drag)
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    fun targetOf(p: Offset, item: HomeItem): Triple<Int, Int, Int>? {
        if (item.spanX == 1 && item.spanY == 1 && dockRect != Rect.Zero && dockRect.contains(p)) {
            val x = if (landscape) {
                val h = dockRect.height / vm.dockCount
                ((p.y - dockRect.top) / h).toInt().coerceIn(0, vm.dockCount - 1)
            } else {
                val w = dockRect.width / vm.dockCount
                ((p.x - dockRect.left) / w).toInt().coerceIn(0, vm.dockCount - 1)
            }
            return Triple(-1, x, 0)
        }
        if (gridRect != Rect.Zero && gridRect.contains(p)) {
            val cw = gridRect.width / vm.gridCols
            val ch = gridRect.height / vm.gridRows
            val fx = (p.x - gridRect.left) / cw
            val fy = (p.y - gridRect.top) / ch
            val x = (fx - item.spanX / 2f).roundToInt().coerceIn(0, maxOf(0, vm.gridCols - item.spanX))
            val y = (fy - item.spanY / 2f).roundToInt().coerceIn(0, maxOf(0, vm.gridRows - item.spanY))
            return Triple(pagerState.currentPage, x, y)
        }
        return null
    }

    val hiddenIds: Set<String> = drag?.let { setOf(it.item.id) + it.collected } ?: emptySet()

    val cb = remember {
        DragCallbacks(
            start = { item, pos ->
                menuItem = null
                drag = DragState(item, pos)
            },
            move = { pos -> drag?.pos = pos },
            end = {
                val d = drag
                if (d != null) {
                    val main = d.item
                    val extra = d.collected.map { it.removePrefix("app:") }
                    val zone = gridRect != Rect.Zero && d.pos.y < gridRect.top + folderZonePx
                    if (extra.isNotEmpty() && zone && main is AppItem) {
                        vm.createFolderFrom(main, extra)
                        Haptics.confirm(view)
                    } else {
                        val t = targetOf(d.pos, main)
                        if (t != null) {
                            vm.dropItem(main, t.first, t.second, t.third)
                            Haptics.confirm(view)
                        } else {
                            Haptics.reject(view)
                        }
                        if (extra.isNotEmpty()) vm.gatherApps(extra, t?.first?.takeIf { it >= 0 } ?: vm.currentPage)
                    }
                }
                drag = null
            },
            collect = { it ->
                val d = drag
                if (d != null && d.item is AppItem && it is AppItem && it.id != d.item.id) {
                    if (it.id in d.collected) d.collected.remove(it.id) else d.collected.add(it.id)
                    Haptics.tick(view)
                }
            }
        )
    }
    var homeMenu by remember { mutableStateOf<Offset?>(null) }
    // come Launcher3: alla rotazione i trascinamenti e i menu aperti vengono annullati
    LaunchedEffect(landscape) {
        drag = null
        menuItem = null
        homeMenu = null
        vm.folderAnchor = Rect.Zero
        gridRect = Rect.Zero
        dockRect = Rect.Zero
    }
    val onMenu: (HomeItem, Rect) -> Unit = { item, rect ->
        menuItem = item
        menuRect = rect
    }

    LaunchedEffect(Unit) {
        var first = true
        snapshotFlow { pagerState.currentPage }.collect {
            vm.currentPage = it
            if (!first) Haptics.page(view)
            first = false
        }
    }

    // "Cerca nella pagina dov'è": porta alla pagina dell'app
    LaunchedEffect(vm.locateSignal) {
        if (vm.locateSignal > 0 && vm.locatePage >= 0 && vm.locatePage < vm.pageCount) {
            pagerState.animateScrollToPage(vm.locatePage)
        }
    }

    // torna alla prima pagina quando si preme Home
    LaunchedEffect(vm.homeSignal) {
        menuItem = null
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

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // mentre un'icona è tenuta premuta, un secondo dito che scorre cambia pagina
                awaitPointerEventScope {
                    val startX = HashMap<PointerId, Float>()
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        if (dragRef == null) {
                            startX.clear()
                            continue
                        }
                        for (c in ev.changes) {
                            if (c.changedToDown()) {
                                startX[c.id] = c.position.x
                            } else if (c.changedToUp()) {
                                startX.remove(c.id)
                            } else if (c.pressed) {
                                val x0 = startX[c.id] ?: continue
                                val dx = c.position.x - x0
                                if (abs(dx) > switchPx) {
                                    startX.remove(c.id)
                                    val target = (pagerState.currentPage + if (dx < 0) 1 else -1)
                                        .coerceIn(0, vm.pageCount - 1)
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        val pagerContent: @Composable (Modifier) -> Unit = { mod ->
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 8,
                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                    pagerState,
                    snapAnimationSpec = androidx.compose.animation.core.tween(
                        (Motion.PAGE_SNAP * s.animFactor).toInt(), easing = Motion.Standard
                    )
                ),
                userScrollEnabled = drag == null,
                modifier = mod
                    .onGloballyPositioned { gridRect = it.boundsInRoot() }
            ) { page ->
                HomePage(
                    page = page,
                    vm = vm,
                    host = host,
                    pagerState = pagerState,
                    dragId = hiddenIds,
                    cb = cb,
                    onMenu = onMenu,
                    onEmptyMenu = { homeMenu = it }
                )
            }
        }
        if (landscape) {
            val navL = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val seascape = androidx.compose.ui.platform.LocalView.current.display?.rotation ==
                android.view.Surface.ROTATION_270
            val dockContent: @Composable () -> Unit = {
                Dock(
                    vm = vm, host = host, dragId = hiddenIds, cb = cb, onMenu = onMenu,
                    onRect = { dockRect = it }, vertical = true
                )
            }
            Row(
                Modifier.fillMaxSize().windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
            ) {
                // con il telefono ruotato dall'altra parte la dock sta a sinistra, come in Launcher3
                if (seascape) dockContent()
                Column(Modifier.weight(1f).fillMaxHeight().padding(bottom = navL)) {
                    Spacer(Modifier.height(if (edit) 44.dp else 2.dp))
                    pagerContent(Modifier.weight(1f).fillMaxWidth())
                    PageDots(vm.pageCount, pagerState, vm.settings.pageIndicator, vm.settings) { vm.searchFocus = true; vm.overlay = Overlay.DRAWER }
                }
                if (!seascape) dockContent()
            }
        } else {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
        ) {
            Spacer(Modifier.height(topPad))
            pagerContent(Modifier.weight(1f).fillMaxWidth())
            PageDots(vm.pageCount, pagerState, vm.settings.pageIndicator, vm.settings) { vm.searchFocus = true; vm.overlay = Overlay.DRAWER }
            Dock(
                vm = vm,
                host = host,
                dragId = hiddenIds,
                cb = cb,
                onMenu = onMenu,
                onRect = { dockRect = it }
            )
        }

        }

        // barra modifica
        val showCreate = drag?.let { dd ->
            dd.collected.isNotEmpty() && gridRect != Rect.Zero &&
                dd.pos.y < gridRect.top + gridRect.height * 0.35f
        } == true
        AnimatedVisibility(
            visible = edit && !showCreate,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 6.dp),
            enter = fadeIn(androidx.compose.animation.core.tween(Motion.FADE, easing = Motion.StandardDecelerate)) +
                slideInVertically(androidx.compose.animation.core.tween(Motion.SHEET, easing = Motion.EmphasizedDecelerate)) { -it },
            exit = fadeOut(androidx.compose.animation.core.tween(Motion.FADE, easing = Motion.StandardAccelerate)) +
                slideOutVertically(androidx.compose.animation.core.tween(Motion.REORDER, easing = Motion.EmphasizedAccelerate)) { -it }
        ) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BarButton("Sfondo") { host.pickWallpaper() }
                BarButton("Widget") { vm.overlay = Overlay.WIDGETS }
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

        homeMenu?.let { o ->
            HomeMenu(o, vm, host) { homeMenu = null }
        }

        // menu rapido
        val mi = menuItem
        if (mi != null && !edit) {
            QuickMenu(mi, menuRect, vm, host) { menuItem = null }
        }

        // evidenzia la cella di destinazione e mostra l'elemento trascinato
        val d = drag
        if (d != null && d.collected.isNotEmpty() && gridRect != Rect.Zero &&
            d.pos.y < gridRect.top + gridRect.height * 0.35f
        ) {
            val inZone = d.pos.y < gridRect.top + folderZonePx
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(if (inZone) cs.primary else cs.secondaryContainer)
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text(
                    "Crea cartella (${d.collected.size + 1} app)",
                    color = if (inZone) cs.onPrimary else cs.onSecondaryContainer,
                    fontSize = 14.sp
                )
            }
        }
        if (d != null) {
            val t = targetOf(d.pos, d.item)
            if (t != null) {
                val rect = if (t.first < 0) {
                    if (landscape) {
                        val h = dockRect.height / vm.dockCount
                        Rect(dockRect.left, dockRect.top + t.second * h, dockRect.right, dockRect.top + (t.second + 1) * h)
                    } else {
                        val w = dockRect.width / vm.dockCount
                        Rect(dockRect.left + t.second * w, dockRect.top, dockRect.left + (t.second + 1) * w, dockRect.bottom)
                    }
                } else {
                    val w = gridRect.width / vm.gridCols
                    val h = gridRect.height / vm.gridRows
                    Rect(
                        gridRect.left + t.second * w, gridRect.top + t.third * h,
                        gridRect.left + (t.second + d.item.spanX) * w, gridRect.top + (t.third + d.item.spanY) * h
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
            val di = d.item
            val isBlock = di.spanX > 1 || di.spanY > 1
            val cellWpx = if (gridRect.width > 0f) gridRect.width / vm.gridCols else 200f
            val cellHpx = if (gridRect.height > 0f) gridRect.height / vm.gridRows else 200f
            val pw = if (isBlock) cellWpx * di.spanX else with(density) { (64.dp * s.iconScale).toPx() }
            val ph = if (isBlock) cellHpx * di.spanY else pw
            Box(
                Modifier
                    .offset { IntOffset((d.pos.x - pw / 2f).roundToInt(), (d.pos.y - ph / 2f).roundToInt()) }
                    .size(with(density) { pw.toDp() }, with(density) { ph.toDp() })
                    .graphicsLayer {
                        scaleX = 1.06f
                        scaleY = 1.06f
                    }
            ) {
                val iconDp = with(density) { pw.toDp() }
                when (di) {
                    is AppItem -> {
                        val extras = d.collected.take(3).mapNotNull { vm.apps[it.removePrefix("app:")] }
                        extras.forEachIndexed { i, a ->
                            Box(Modifier.offset((6 * (i + 1)).dp, (6 * (i + 1)).dp).alpha(0.85f)) {
                                AppIcon(a, iconDp, s.themedIcons)
                            }
                        }
                        vm.apps[di.appId]?.let { a -> AppIcon(a, iconDp, s.themedIcons) }
                        if (d.collected.isNotEmpty()) {
                            Box(
                                Modifier.align(Alignment.TopEnd).size(22.dp).clip(CircleShape).background(cs.primary),
                                contentAlignment = Alignment.Center
                            ) { Text("${d.collected.size + 1}", color = cs.onPrimary, fontSize = 12.sp) }
                        }
                    }
                    is FolderItem -> if (!di.big) FolderIcon(di, vm.apps, iconDp, s.themedIcons)
                    else Box(
                        Modifier.fillMaxSize().padding(6.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(cs.surfaceContainerHigh.copy(alpha = 0.8f))
                    )
                    else -> Box(
                        Modifier.fillMaxSize().padding(5.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(cs.primary.copy(alpha = 0.35f))
                            .border(2.dp, cs.primary, RoundedCornerShape(28.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = cs.surfaceContainerHigh.copy(alpha = 0.85f),
        modifier = Modifier.size(46.dp).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Settings, contentDescription = "Impostazioni", tint = cs.onSurfaceVariant)
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
private fun PageDots(count: Int, state: PagerState, style: String, st: LauncherSettings, onSearch: () -> Unit) {
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current
    var scrubbing by remember { mutableStateOf(false) }
    val ios = style == "ios"
    // stile iOS: dopo qualche secondo fermo i puntini diventano "Cerca"
    var showSearch by remember { mutableStateOf(false) }
    LaunchedEffect(ios, count, state.currentPage, state.isScrollInProgress, scrubbing) {
        showSearch = false
        if (ios && (count <= 1 || (!state.isScrollInProgress && !scrubbing))) {
            if (count > 1) delay(2500)
            showSearch = true
        }
    }
    val scale by animateFloatAsState(if (scrubbing) 1.25f else 1f, label = "scrub")
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (ios) 34.dp else 24.dp)
            .pointerInput(count) {
                if (count <= 1) return@pointerInput
                // tieni premuto e scorri sull'indicatore per cambiare pagina
                fun pageAt(x: Float) = ((x / size.width) * count).toInt().coerceIn(0, count - 1)
                detectDragGesturesAfterLongPress(
                    onDragStart = { o ->
                        scrubbing = true
                        Haptics.press(view)
                        val p = pageAt(o.x)
                        if (p != state.currentPage) scope.launch { state.scrollToPage(p) }
                    },
                    onDrag = { ch, _ ->
                        ch.consume()
                        val p = pageAt(ch.position.x)
                        if (p != state.currentPage) scope.launch { state.scrollToPage(p) }
                    },
                    onDragEnd = { scrubbing = false },
                    onDragCancel = { scrubbing = false }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (style == "none" || (count <= 1 && !ios)) return@Box
        // l'indicatore può prendere l'aspetto della dock (stile, trasparenza, vetro)
        val follow = st.indFollowDock && st.dockStyle != "none"
        val capsule = ios || scrubbing || follow
        val fg = if (follow) dockContentColor(st.dockStyle, st.dockOpacity) else Color.White
        val look = when {
            follow && st.dockStyle == "tiles" -> Modifier.dockTile(st.dockOpacity, st.dockGlass, 50.dp)
            follow -> Modifier.dockLook(if (st.dockStyle == "bar") "pill" else st.dockStyle, st.dockOpacity, st.dockGlass, 50.dp)
            else -> Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (capsule) 0.22f else 0f))
                .border(1.dp, Color.White.copy(alpha = if (ios) 0.28f else 0f), CircleShape)
        }
        Row(
            Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .then(look)
                .then(
                    if (ios) Modifier.pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            if (showSearch) onSearch() else showSearch = true
                        })
                    } else Modifier
                )
                .animateContentSize(tween(Motion.FOLDER, easing = Motion.EmphasizedDecelerate))
                .padding(horizontal = if (capsule) 12.dp else 0.dp, vertical = if (capsule) 7.dp else 0.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (ios && showSearch) {
                Icon(Icons.Default.Search, contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                // testo senza padding del font e centrato sulla riga: allineato alla lente
                Text(
                    "Cerca",
                    color = fg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
                        lineHeight = 17.sp,
                        lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                            alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                            trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                        )
                    )
                )
            } else {
                PageDotsContent(count, state, if (ios) "dots" else style, fg)
            }
        }
    }
}

@Composable
private fun PageDotsContent(count: Int, state: PagerState, style: String, fg: Color) {
    val current = state.currentPage
    val white = fg
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (style) {
            "dots" -> repeat(count) { i ->
                val sel = current == i
                val sz by animateDpAsState(if (sel) 8.dp else 6.dp, label = "d")
                Box(
                    Modifier.padding(horizontal = 3.dp).size(sz).clip(CircleShape)
                        .background(white.copy(alpha = if (sel) 0.95f else 0.45f))
                )
            }
            "lines" -> repeat(count) { i ->
                val sel = current == i
                Box(
                    Modifier.padding(horizontal = 2.dp).size(18.dp, 3.dp).clip(CircleShape)
                        .background(white.copy(alpha = if (sel) 0.95f else 0.35f))
                )
            }
            "worm" -> {
                // puntini fissi con un'ovale che scorre seguendo il dito
                val dot = 6.dp
                val gap = 8.dp
                Box(Modifier.width((dot + gap) * count - gap).height(dot)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(count) { Box(Modifier.size(dot).clip(CircleShape).background(white.copy(alpha = 0.4f))) }
                    }
                    val pos = (state.currentPage + state.currentPageOffsetFraction).coerceIn(0f, (count - 1).toFloat())
                    val stretch = 1f - kotlin.math.abs(state.currentPageOffsetFraction) * 1.0f
                    Box(
                        Modifier
                            .offset((dot + gap) * pos, 0.dp)
                            .size(dot + (dot * (1f - stretch) * 1.6f), dot)
                            .clip(CircleShape)
                            .background(white.copy(alpha = 0.95f))
                    )
                }
            }
            "progress" -> {
                val w = 96.dp
                Box(Modifier.size(w, 4.dp).clip(CircleShape).background(white.copy(alpha = 0.3f))) {
                    val seg = w / count
                    val pos = (state.currentPage + state.currentPageOffsetFraction).coerceIn(0f, (count - 1).toFloat())
                    Box(Modifier.offset(seg * pos, 0.dp).size(seg, 4.dp).clip(CircleShape).background(white.copy(alpha = 0.95f)))
                }
            }
            "number" -> Text(
                "${current + 1} / $count",
                color = white.copy(alpha = 0.9f),
                fontSize = 12.sp
            )
            else -> repeat(count) { i ->
                val sel = current == i
                val w by animateDpAsState(if (sel) 16.dp else 6.dp, label = "dot")
                Box(
                    Modifier.padding(horizontal = 3.dp).size(w, 6.dp).clip(CircleShape)
                        .background(white.copy(alpha = if (sel) 0.95f else 0.5f))
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
    dragId: Set<String>,
    cb: DragCallbacks,
    onMenu: (HomeItem, Rect) -> Unit,
    onEmptyMenu: (Offset) -> Unit
) {
    val s = vm.settings
    var origin by remember { mutableStateOf(Offset.Zero) }
    val haptic = LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    val swipePx = with(LocalDensity.current) { 56.dp.toPx() }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .pageEffect(s.pageEffect, pagerState, page)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { if (vm.editMode) vm.exitEdit() },
                    onDoubleTap = {
                        if (!vm.editMode && vm.settings.doubleTapLock) {
                            Haptics.confirm(view)
                            host.lockScreen()
                        }
                    },
                    onLongPress = { off ->
                        if (!vm.editMode) {
                            if (Haptics.pressOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEmptyMenu(origin + off)
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
                                Haptics.tick(view)
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
        val cw = maxWidth / vm.gridCols
        val ch = maxHeight / vm.gridRows
        SideEffectCells(vm, cw, ch)
        for (item in vm.items) {
            if (item.page == page) {
                key(item.id) {
                    val mod = Modifier.offset(cw * item.x, ch * item.y)
                    val hidden = item.id in dragId
                    when (item) {
                        is AppItem -> GridItem(item, vm, host, cw, ch, s.showLabels, hidden, cb, onMenu, mod)
                        is FolderItem ->
                            if (item.big) BigFolderCell(item, vm, host, cw, ch, hidden, cb, onMenu, mod)
                            else GridItem(item, vm, host, cw, ch, s.showLabels, hidden, cb, onMenu, mod)
                        is WidgetItem, is StackItem -> WidgetCell(item, vm, host, cw, ch, hidden, cb, mod)
                    }
                }
            }
        }
    }
}

@Composable
private fun SideEffectCells(vm: LauncherViewModel, cw: Dp, ch: Dp) {
    androidx.compose.runtime.SideEffect {
        vm.cellWdp = cw.value
        vm.cellHdp = ch.value
    }
}

@Composable
private fun Dock(
    vm: LauncherViewModel,
    host: Host,
    dragId: Set<String>,
    cb: DragCallbacks,
    onMenu: (HomeItem, Rect) -> Unit,
    onRect: (Rect) -> Unit,
    vertical: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val st = vm.settings
    val style = if (vertical && st.dockStyle == "bar") "pill" else st.dockStyle
    if (vertical) {
        val navB = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .padding(bottom = navB)
                .width(84.dp * st.dockSize)
                .dockLook(style, st.dockOpacity, st.dockGlass, st.dockCorner.dp)
                .onGloballyPositioned { onRect(it.boundsInRoot()) }
        ) {
            if (style == "tiles") {
                Column(Modifier.fillMaxSize()) {
                    repeat(vm.dockCount) {
                        Box(
                            Modifier.weight(1f).fillMaxSize().padding(horizontal = 2.dp, vertical = 3.dp)
                                .dockTile(st.dockOpacity, st.dockGlass, minOf(st.dockCorner.dp, 26.dp))
                        )
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val chV = maxHeight / vm.dockCount
                for (item in vm.items) {
                    if (item.page == -1) {
                        key(item.id) {
                            GridItem(
                                item = item, vm = vm, host = host,
                                cellW = maxWidth, cellH = chV,
                                labels = false, scale = st.dockSize,
                                hidden = item.id in dragId, cb = cb, onMenu = onMenu,
                                modifier = Modifier.offset(0.dp, chV * item.x)
                            )
                        }
                    }
                }
            }
        }
        return
    }
    val bar = style == "bar"
    val margin = if (bar) 0.dp else 16.dp
    val corner = st.dockCorner.dp
    // la barra a tutta larghezza arriva fino al bordo: sotto le icone c'è lo spazio dei gesti di sistema
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = margin, end = margin, top = 6.dp, bottom = if (bar) 0.dp else 6.dp + nav)
            .height(92.dp * st.dockSize + (if (bar) nav else 0.dp))
            .dockLook(style, st.dockOpacity, st.dockGlass, corner)
            .onGloballyPositioned { onRect(it.boundsInRoot()) }
    ) {
        if (style == "tiles") {
            Row(Modifier.fillMaxSize()) {
                repeat(vm.dockCount) {
                    Box(
                        Modifier.weight(1f).fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp)
                            .dockTile(st.dockOpacity, st.dockGlass, minOf(corner, 26.dp))
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = if (bar) nav else 0.dp)) {
            val cw = maxWidth / vm.dockCount
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
                            scale = st.dockSize,
                            hidden = item.id in dragId,
                            cb = cb,
                            onMenu = onMenu,
                            modifier = Modifier.offset(cw * item.x, 0.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Gesti comuni a icone e cartelle: tocco, menu rapido (pressione lunga) e trascinamento. */
@Composable
private fun Modifier.itemGestures(
    item: HomeItem,
    vm: LauncherViewModel,
    holder: BoundsHolder,
    cb: DragCallbacks,
    onMenu: (HomeItem, Rect) -> Unit,
    onTap: (Offset) -> Unit
): Modifier {
    val haptic = LocalHapticFeedback.current
    val curItem by rememberUpdatedState(item)
    val curCb by rememberUpdatedState(cb)
    val curMenu by rememberUpdatedState(onMenu)
    val curTap by rememberUpdatedState(onTap)
    return this
        .pointerInput(item.id) {
            detectTapGestures(onTap = { pos -> if (!vm.editMode) curTap(pos) else curCb.collect(curItem) })
        }
        .pointerInput(item.id) {
            var started = false
            var lpPos = Offset.Zero
            val slop = viewConfiguration.touchSlop
            detectDragGesturesAfterLongPress(
                onDragStart = { pos ->
                    if (Haptics.pressOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    lpPos = pos
                    started = false
                    if (vm.editMode) {
                        started = true
                        curCb.start(curItem, holder.root + pos)
                    }
                },
                onDrag = { ch, _ ->
                    if (!started && (ch.position - lpPos).getDistance() > slop) {
                        started = true
                        vm.editMode = true
                        curCb.start(curItem, holder.root + ch.position)
                    }
                    if (started) curCb.move(holder.root + ch.position)
                },
                onDragEnd = {
                    if (started) curCb.end() else curMenu(curItem, holder.window)
                    started = false
                },
                onDragCancel = {
                    if (started) curCb.end()
                    started = false
                }
            )
        }
}

@Composable
fun NotifBadge(count: Int, mode: String, modifier: Modifier) {
    if (count <= 0 || mode == "off") return
    val cs = MaterialTheme.colorScheme
    if (mode == "dot") {
        Box(modifier.size(11.dp).clip(CircleShape).background(cs.error))
    } else {
        Box(
            modifier.heightIn(min = 18.dp).widthIn(min = 18.dp).clip(CircleShape).background(cs.error),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (count > 99) "99+" else "$count",
                color = cs.onError,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
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
    onMenu: (HomeItem, Rect) -> Unit,
    modifier: Modifier,
    scale: Float = 1f
) {
    val s = vm.settings
    val holder = remember { BoundsHolder() }
    val curItem by rememberUpdatedState(item)
    val edit = vm.editMode
    // misure proporzionali alla cella: si adattano a qualsiasi schermo
    val base = if (labels) minOf(cellW * 0.62f, cellH * 0.50f) else minOf(cellW * 0.82f, cellH * 0.72f)
    val iconSize = minOf(base * s.iconScale * scale, 96.dp)
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
                .itemGestures(item, vm, holder, cb, onMenu) {
                    when (val cur = curItem) {
                        is AppItem -> vm.apps[cur.appId]?.let { a -> host.launch(a, holder.window) }
                        is FolderItem -> { vm.folderAnchor = holder.window; vm.openFolderId = cur.folderId }
                        else -> {}
                    }
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val hl = vm.highlightId == item.id
            val hlColor = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .size(iconSize)
                    .then(
                        if (hl) Modifier
                            .background(hlColor.copy(alpha = 0.30f), RoundedCornerShape(24.dp))
                            .border(3.dp, hlColor, RoundedCornerShape(24.dp))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
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
                    if (!edit) {
                        NotifBadge(BadgeStore.counts[app.pkg] ?: 0, s.badges, Modifier.align(Alignment.TopEnd))
                    }
                } else if (item is FolderItem) {
                    FolderIcon(item, vm.apps, iconSize, s.themedIcons)
                    if (!edit) {
                        val total = item.appIds.sumOf { id -> vm.apps[id]?.let { BadgeStore.counts[it.pkg] } ?: 0 }
                        NotifBadge(total, s.badges, Modifier.align(Alignment.TopEnd))
                    }
                }
            }
            if (labels) {
                val label = when (item) {
                    is AppItem -> app?.label ?: ""
                    is FolderItem -> item.name
                    else -> ""
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

/** Cartella grande 2x2 con 9 app toccabili direttamente. */
@Composable
private fun BigFolderCell(
    folder: FolderItem,
    vm: LauncherViewModel,
    host: Host,
    cellW: Dp,
    cellH: Dp,
    hidden: Boolean,
    cb: DragCallbacks,
    onMenu: (HomeItem, Rect) -> Unit,
    modifier: Modifier
) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val holder = remember { BoundsHolder() }
    val curFolder by rememberUpdatedState(folder)
    val edit = vm.editMode
    val iconSize = minOf(cellW * 0.64f, cellH * 0.64f) * s.iconScale
    Box(modifier.size(cellW * 2, cellH * 2).padding(6.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .alpha(if (hidden) 0f else 1f)
                .clip(RoundedCornerShape(32.dp))
                .background(cs.surfaceContainerHigh.copy(alpha = 0.6f))
                .onGloballyPositioned {
                    holder.root = it.positionInRoot()
                    holder.window = it.boundsInWindow()
                    holder.w = it.size.width.toFloat()
                    holder.h = it.size.height.toFloat()
                }
                .itemGestures(folder, vm, holder, cb, onMenu) { pos ->
                    val f = curFolder
                    val col = ((pos.x / holder.w) * 3f).toInt().coerceIn(0, 2)
                    val row = ((pos.y / holder.h) * 3f).toInt().coerceIn(0, 2)
                    val app = f.appIds.getOrNull(row * 3 + col)?.let { vm.apps[it] }
                    if (app != null) host.launch(app, holder.window) else { vm.folderAnchor = holder.window; vm.openFolderId = f.folderId }
                }
        ) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                for (r in 0 until 3) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (c in 0 until 3) {
                            val a = folder.appIds.getOrNull(r * 3 + c)?.let { vm.apps[it] }
                            Box(Modifier.size(iconSize * 0.78f), contentAlignment = Alignment.Center) {
                                if (a != null) AppIcon(a, iconSize * 0.78f, s.themedIcons)
                            }
                        }
                    }
                }
            }
            if (edit) {
                Text(
                    folder.name,
                    color = cs.onSurface,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                )
            }
        }
    }
}

/** Widget (o stack) sulla Home, con comandi di modifica. */
@Composable
private fun WidgetCell(
    item: HomeItem,
    vm: LauncherViewModel,
    host: Host,
    cellW: Dp,
    cellH: Dp,
    hidden: Boolean,
    cb: DragCallbacks,
    modifier: Modifier
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    val holder = remember { BoundsHolder() }
    val curItem by rememberUpdatedState(item)
    val curCb by rememberUpdatedState(cb)
    val edit = vm.editMode
    val enterEdit: () -> Unit = {
        if (!vm.editMode) {
            if (Haptics.pressOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.editMode = true
        }
    }
    val cwPx = with(density) { cellW.toPx() }
    val chPx = with(density) { cellH.toPx() }
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier
            .size(cellW * item.spanX, cellH * item.spanY)
            .padding(5.dp)
            .alpha(if (hidden) 0f else 1f)
    ) {
        Box(Modifier.fillMaxSize()) {
            when (item) {
                is WidgetItem -> WidgetContent(item.ref, vm, host, enterEdit, Modifier.fillMaxSize())
                is StackItem -> StackContent(item, vm, host, enterEdit, Modifier.fillMaxSize())
                else -> {}
            }
        }
        if (edit) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .border(2.dp, cs.primary, shape)
                    .onGloballyPositioned {
                        holder.root = it.positionInRoot()
                        holder.window = it.boundsInWindow()
                    }
                    .pointerInput(item.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                if (Haptics.pressOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                curCb.start(curItem, holder.root + pos)
                            },
                            onDrag = { ch, _ -> curCb.move(holder.root + ch.position) },
                            onDragEnd = { curCb.end() },
                            onDragCancel = { curCb.end() }
                        )
                    }
            )
            MinusBadge(Modifier.align(Alignment.TopStart).padding(6.dp)) { vm.removeWidgetItem(item) }
            if (item is StackItem) {
                StackManage(item, vm, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            // maniglia per ridimensionare
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(cs.primary)
                    .pointerInput(item.id) {
                        var startSx = 1
                        var startSy = 1
                        var acc = Offset.Zero
                        detectDragGestures(
                            onDragStart = {
                                startSx = curItem.spanX
                                startSy = curItem.spanY
                                acc = Offset.Zero
                            },
                            onDrag = { c, d ->
                                c.consume()
                                acc += d
                                val nx = (startSx + (acc.x / cwPx).roundToInt()).coerceIn(1, 4)
                                val ny = (startSy + (acc.y / chPx).roundToInt()).coerceIn(1, 4)
                                if (nx != curItem.spanX || ny != curItem.spanY) Haptics.tick(view)
                                vm.resizeItem(curItem, nx, ny)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("↘", color = cs.onPrimary, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun StackManage(stack: StackItem, vm: LauncherViewModel, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(cs.secondaryContainer)
            .clickable { open = true },
        contentAlignment = Alignment.Center
    ) {
        Text("${stack.refs.size}", color = cs.onSecondaryContainer, fontSize = 13.sp)
    }
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Stack di widget") },
            text = {
                Column {
                    stack.refs.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when (r.kind) {
                                    "clock" -> "Orologio"
                                    "analog" -> "Orologio analogico"
                                    "battery" -> "Batteria"
                                    "search" -> "Ricerca"
                                    "weather" -> "Meteo"
                                    else -> vm.widgetMgr.getAppWidgetInfo(r.widgetId)
                                        ?.provider?.packageName ?: "Widget"
                                },
                                Modifier.weight(1f)
                            )
                            androidx.compose.material3.TextButton(onClick = {
                                vm.removeFromStack(stack, r)
                                open = false
                            }) { Text("Rimuovi") }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { open = false }) { Text("Chiudi") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    open = false
                    vm.stackTarget = stack.uid
                    vm.overlay = Overlay.WIDGETS
                }) { Text("Aggiungi widget") }
            }
        )
    }
}

// =====================================================================
//  Menu rapido (pressione lunga su icona o cartella)
// =====================================================================

@Composable
internal fun QuickMenu(
    item: HomeItem,
    rect: Rect,
    vm: LauncherViewModel,
    host: Host,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val conf = LocalConfiguration.current
    val app = (item as? AppItem)?.let { vm.apps[it.appId] }
    val shortcuts by produceState<List<ShortcutEntry>>(emptyList(), item.id) {
        if (app != null) value = withContext(Dispatchers.IO) { vm.loadShortcuts(app.pkg) }
    }
    val rows = (if (app != null) 3 + (if (app.removable) 1 else 0) else 5) + shortcuts.size
    val menuW = 236.dp
    val menuWpx = with(density) { menuW.toPx() }
    val estH = with(density) { (rows * 50 + 16).dp.toPx() }
    val screenW = with(density) { conf.screenWidthDp.dp.toPx() }
    val screenH = with(density) { conf.screenHeightDp.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    val gap = with(density) { 8.dp.toPx() }
    val x = (rect.center.x - menuWpx / 2f).coerceIn(margin, maxOf(margin, screenW - menuWpx - margin))
    val below = rect.bottom + estH + gap + margin < screenH
    val y = if (below) rect.bottom + gap else maxOf(margin, rect.top - estH - gap)

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cs.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier.width(menuW)
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                if (app != null) {
                    shortcuts.forEach { sc ->
                        MenuRow(sc.label, sc.icon) {
                            onDismiss()
                            vm.launchShortcut(sc)
                        }
                    }
                    MenuRow("Informazioni app") {
                        onDismiss()
                        host.appInfo(app)
                    }
                    MenuRow("Modifica Home") {
                        onDismiss()
                        vm.editMode = true
                    }
                    MenuRow("Nascondi") {
                        onDismiss()
                        vm.hideApp(app.id)
                    }
                    if (app.removable) {
                        MenuRow("Disinstalla", danger = true) {
                            onDismiss()
                            host.uninstall(app)
                        }
                    }
                } else if (item is FolderItem) {
                    MenuRow("Rinomina") {
                        onDismiss()
                        vm.folderAnchor = rect
                        vm.openFolderId = item.folderId
                    }
                    MenuRow("Ordina A-Z") {
                        onDismiss()
                        vm.sortFolder(item.folderId)
                    }
                    MenuRow(if (item.big) "Riduci" else "Ingrandisci (2x2)") {
                        onDismiss()
                        vm.setFolderBig(item.folderId, !item.big)
                    }
                    MenuRow("Elimina cartella", danger = true) {
                        onDismiss()
                        vm.dissolveFolder(item.folderId)
                    }
                    MenuRow("Modifica Home") {
                        onDismiss()
                        vm.editMode = true
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMenu(at: Offset, vm: LauncherViewModel, host: Host, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val conf = LocalConfiguration.current
    val menuW = 250.dp
    val menuWpx = with(density) { menuW.toPx() }
    val estH = with(density) { (4 * 56 + 16).dp.toPx() }
    val screenW = with(density) { conf.screenWidthDp.dp.toPx() }
    val screenH = with(density) { conf.screenHeightDp.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    val x = (at.x - menuWpx / 2f).coerceIn(margin, maxOf(margin, screenW - menuWpx - margin))
    val y = if (at.y + estH + margin < screenH) at.y else maxOf(margin, at.y - estH)
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerHigh,
            shadowElevation = 8.dp,
            modifier = Modifier.width(menuW)
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                HomeMenuRow(Icons.Default.Create, "Sfondo e stile") { onDismiss(); host.pickWallpaper() }
                HomeMenuRow(Icons.Default.Add, "Widget") { onDismiss(); vm.overlay = Overlay.WIDGETS }
                HomeMenuRow(Icons.Default.Edit, "Modifica Home") { onDismiss(); vm.editMode = true }
                HomeMenuRow(Icons.Default.Settings, "Impostazioni Home") { onDismiss(); vm.overlay = Overlay.SETTINGS }
            }
        }
    }
}

@Composable
private fun HomeMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(14.dp))
        Text(text, color = cs.onSurface, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun MenuRow(
    text: String,
    icon: androidx.compose.ui.graphics.ImageBitmap? = null,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            androidx.compose.foundation.Image(icon, null, Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text,
            color = if (danger) cs.error else cs.onSurface,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
