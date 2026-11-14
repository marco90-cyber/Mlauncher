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
import androidx.compose.foundation.systemGestureExclusion
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
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
    fun pickBackupFolder()
    fun pickWallpaperImage()
    fun openListenerSettings()
    fun openAccessibilitySettings()
    fun lockScreen(): Boolean
    fun openClockApp()
    fun requestBluetooth()
    fun webSearch(query: String)
    fun restartLauncher()
    fun requestContactsName()
    fun exportLayout()
    fun importLayout()
    fun reconfigureWidget(widgetId: Int)
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
            // le pagine non visibili tornano al loro posto (fuori schermo): così non prendono i tocchi
            if (a < 1f) {
                translationX = -o * size.width
                alpha = 1f - a
            } else alpha = 0f
        }
        "cube" -> {
            cameraDistance = 14f * density
            transformOrigin = TransformOrigin(if (o < 0f) 1f else 0f, 0.5f)
            rotationY = -90f * o.coerceIn(-1f, 1f)
        }
        "rotate" -> {
            transformOrigin = TransformOrigin(0.5f, 1.3f)
            rotationZ = -18f * o.coerceIn(-1f, 1f)
            // ruotate, le pagine lontane entrerebbero di sbieco nello schermo: spariscono del tutto
            alpha = if (a >= 1f) 0f else (1f - a * 0.7f)
        }
        "cascade" -> {
            if (o <= -1f) alpha = 0f
            else if (o < 0f) {
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
    // pulsanti della modifica e "Crea cartella": materiale di "Menu e pulsanti"
    val barMat = s.matOf("menus")
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val view = androidx.compose.ui.platform.LocalView.current
    val pagerState = rememberPagerState(initialPage = vm.homePageIdx) { vm.pageCount }
    var gridRect by remember { mutableStateOf(Rect.Zero) }
    var dockRect by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<DragState?>(null) }
    var menuItem by remember { mutableStateOf<HomeItem?>(null) }
    var menuFading by remember { mutableStateOf(false) }
    var menuRect by remember { mutableStateOf(Rect.Zero) }
    val edit = vm.editMode
    val topPad = 6.dp
    // anteprima pagine (come Nova): la Home si rimpicciolisce
    val ov by animateFloatAsState(
        if (vm.showOverview) 1f else 0f,
        tween((Motion.SHEET * s.animFactor).toInt().coerceAtLeast(1), easing = Motion.EmphasizedDecelerate),
        label = "overview"
    )
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

    /**
     * Come Launcher3: la cartella si crea solo se il dito è vicino al centro dell'icona di destinazione
     * (entro ~0,55 volte la grandezza dell'icona); sul bordo invece l'icona va tra le altre.
     */
    fun mergeAt(p: Offset, item: HomeItem, t: Triple<Int, Int, Int>): Boolean {
        if (item !is AppItem || item.spanX != 1 || item.spanY != 1) return false
        val hit = vm.hitAt(t.first, t.second, t.third, item.id) ?: return false
        if (!(hit is AppItem || (hit is FolderItem && hit.spanX == 1 && hit.spanY == 1))) return false
        // l'icona si è già spostata per far posto (anteprima): la cella è libera
        if (hit.id in vm.reorderPreview) return false
        val c: Offset
        val icon: Float
        if (t.first < 0) {
            if (dockRect == Rect.Zero) return false
            if (landscape) {
                val h = dockRect.height / vm.dockCount
                c = Offset(dockRect.center.x, dockRect.top + (t.second + 0.5f) * h)
                icon = minOf(dockRect.width, h) * 0.62f
            } else {
                val w = dockRect.width / vm.dockCount
                c = Offset(dockRect.left + (t.second + 0.5f) * w, dockRect.center.y)
                icon = minOf(w, dockRect.height) * 0.62f
            }
        } else {
            if (gridRect == Rect.Zero) return false
            val cw = gridRect.width / vm.gridCols
            val ch = gridRect.height / vm.gridRows
            val labels = s.showLabels
            val base = if (labels) minOf(cw * 0.62f, ch * 0.50f) else minOf(cw * 0.82f, ch * 0.72f)
            icon = minOf(base * s.iconScale, with(density) { 96.dp.toPx() })
            val lift = if (labels) with(density) { 9.dp.toPx() } else 0f
            c = Offset(gridRect.left + (t.second + 0.5f) * cw, gridRect.top + (t.third + 0.5f) * ch - lift)
        }
        return (p - c).getDistance() < icon * 0.55f
    }

    val hiddenIds: Set<String> = drag?.let { setOf(it.item.id) + it.collected } ?: emptySet()

    val cb = remember {
        DragCallbacks(
            start = { item, pos ->
                // il menu rapido non sparisce di colpo: si dissolve mentre l'icona si solleva
                if (menuItem != null) menuFading = true
                if (!vm.blockedByLock()) drag = DragState(item, pos)
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
                    } else if (extra.isNotEmpty() && main is AppItem &&
                        targetOf(d.pos, main)?.let { t -> mergeAt(d.pos, main, t) && vm.dropGroup(main, extra, t.first, t.second, t.third) } == true
                    ) {
                        // tutte le app raccolte sono entrate nella cartella (o ne hanno creata una)
                        Haptics.confirm(view)
                    } else {
                        val t = targetOf(d.pos, main)
                        if (t != null) {
                            vm.dropItem(main, t.first, t.second, t.third, merge = mergeAt(d.pos, main, t))
                            Haptics.confirm(view)
                        } else {
                            Haptics.reject(view)
                        }
                        if (extra.isNotEmpty()) vm.gatherApps(extra, t?.first?.takeIf { it >= 0 } ?: vm.currentPage)
                    }
                }
                // gli elementi sono già nelle posizioni dell'anteprima: tolgo l'anteprima senza scatti
                vm.clearReorderPreview()
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
    // come Launcher3: alla rotazione i trascinamenti e i menu aperti vengono annullati
    LaunchedEffect(landscape) {
        drag = null
        menuItem = null
        vm.folderAnchor = Rect.Zero
        gridRect = Rect.Zero
        dockRect = Rect.Zero
    }
    val onMenu: (HomeItem, Rect) -> Unit = { item, rect ->
        menuFading = false
        menuItem = item
        menuRect = rect
    }

    // in modifica: scuoti il telefono per compattare la pagina dall'alto in basso
    val ctxS = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(edit, s.shakeTidy) {
        val sm = ctxS.getSystemService(android.hardware.SensorManager::class.java)
        val acc = sm?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        if (!edit || !s.shakeTidy || sm == null || acc == null) return@DisposableEffect onDispose { }
        var last = 0L
        var hits = 0
        var firstHit = 0L
        val l = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(e: android.hardware.SensorEvent) {
                val g = kotlin.math.sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) /
                    android.hardware.SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (g < 2.3f) return
                // servono 3 scossoni in meno di un secondo, e almeno 1,5 s tra due riordini
                if (now - firstHit > 1000) {
                    firstHit = now
                    hits = 0
                }
                hits++
                if (hits >= 3 && now - last > 1500 && drag == null) {
                    last = now
                    hits = 0
                    vm.tidyPage(pagerState.currentPage)
                    Haptics.confirm(view)
                }
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor?, a: Int) {}
        }
        sm.registerListener(l, acc, android.hardware.SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(l) }
    }

    // Home bloccata: avviso quando si prova a cambiarla
    val ctxL = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(vm.lockedSignal) {
        if (vm.lockedSignal > 0) {
            Haptics.reject(view)
            android.widget.Toast.makeText(ctxL, "Home bloccata: sbloccala dall'anteprima pagine (pizzica la Home con due dita)", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // trascinamento iniziato nel cassetto: l'icona continua a seguire il dito sulla Home
    val dDrag = vm.drawerDrag
    LaunchedEffect(dDrag) {
        if (dDrag != null) {
            menuItem = null
            val st = DragState(AppItem(dDrag.appId, -3, 0, 0), dDrag.pos)
            dDrag.extras.forEach { st.collected.add("app:$it") }
            drag = st
            snapshotFlow { dDrag.pos }.collect { st.pos = it }
        } else if (drag?.item?.page == -3) {
            drag = null
        }
    }
    LaunchedEffect(vm.drawerDropSignal) {
        if (vm.drawerDropSignal == 0) return@LaunchedEffect
        val d = drag
        val e = vm.drawerDrag
        if (d != null && e != null) {
            val t = targetOf(d.pos, d.item)
            val merge = t != null && mergeAt(d.pos, d.item, t)
            if (e.fromFolder != null || e.extras.isNotEmpty()) {
                // app uscite da una cartella (anche più di una insieme)
                vm.dropAppsAt(listOf(e.appId) + e.extras, t?.first, t?.second ?: 0, t?.third ?: 0, e.fromFolder, merge)
            } else if (t != null) vm.dropFromDrawer(e.appId, t.first, t.second, t.third, merge) else vm.addToHome(e.appId)
            Haptics.confirm(view)
        }
        vm.clearReorderPreview()
        drag = null
        vm.overlay = Overlay.NONE
        vm.drawerDrag = null
    }

    // anteprima dello spostamento, come Launcher3: fermo su un posto occupato per 650 ms,
    // gli altri elementi scivolano dove finiranno; sopra un'app o una cartella invece compare l'anteprima cartella
    val hoverKey = drag?.let { dd -> targetOf(dd.pos, dd.item)?.let { t -> listOf(dd.item.id, t, dd.collected.size, mergeAt(dd.pos, dd.item, t)) } }
    val dragging = drag != null
    LaunchedEffect(dragging) { if (!dragging) vm.dragSnapshot = null }
    LaunchedEffect(hoverKey) {
        vm.clearReorderPreview()
        val dd = drag ?: return@LaunchedEffect
        @Suppress("UNCHECKED_CAST")
        val t = (hoverKey?.get(1) as? Triple<Int, Int, Int>) ?: return@LaunchedEffect
        // vicino al centro di un'app/cartella: anteprima cartella, niente spostamenti
        if (hoverKey[3] == true) return@LaunchedEffect
        vm.hitAt(t.first, t.second, t.third, dd.item.id) ?: return@LaunchedEffect
        delay(650)
        vm.showReorderPreview(dd.item, t.first, t.second, t.third)
        Haptics.tick(view)
    }

    // posizione delle pagine per la parallasse dello sfondo
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.currentPage + pagerState.currentPageOffsetFraction }.collect {
            vm.homeScroll = if (vm.pageCount > 1) it / (vm.pageCount - 1) else 0.5f
        }
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
        if (vm.homeSignal > 0) {
            vm.showOverview = false
            pagerState.animateScrollToPage(vm.homePageIdx)
        }
    }
    // azione "Pagina N" / "Pagina principale"
    // Pagine preparate a scaglioni: all'avvio e dopo una rotazione si costruiscono subito solo la pagina
    // visibile e le due vicine; le altre si aggiungono una alla volta quando la Home è ferma.
    var beyondPages by remember { mutableIntStateOf(1) }
    LaunchedEffect(vm.loaded, vm.layoutLand) {
        beyondPages = 1
        if (!vm.loaded) return@LaunchedEffect
        delay(900)
        while (beyondPages < 8) {
            if (!pagerState.isScrollInProgress && vm.overlay == Overlay.NONE) beyondPages++
            delay(150)
        }
    }

    // ---------- misure di velocità per il registro ----------
    LaunchedEffect(vm.loaded) {
        if (vm.loaded) {
            withFrameNanos { }
            Perf.homeReady()
        }
    }
    val prevFolder = remember { arrayOfNulls<String>(1) }
    LaunchedEffect(vm.openFolderId) {
        val f = vm.openFolderId
        if (f != null && prevFolder[0] == null) Perf.watch("Apertura cartella")
        else if (f == null && prevFolder[0] != null) Perf.watch("Chiusura cartella")
        prevFolder[0] = f
    }
    val prevOverlay = remember { arrayOf(Overlay.NONE) }
    LaunchedEffect(vm.overlay) {
        val o = vm.overlay
        if (o == Overlay.DRAWER && prevOverlay[0] != Overlay.DRAWER) Perf.watch("Apertura cassetto")
        else if (o == Overlay.NONE && prevOverlay[0] == Overlay.DRAWER) Perf.watch("Chiusura cassetto")
        prevOverlay[0] = o
    }
    val prevOv = remember { booleanArrayOf(false) }
    LaunchedEffect(vm.showOverview) {
        if (vm.showOverview != prevOv[0]) Perf.watch(if (vm.showOverview) "Apertura anteprima pagine" else "Chiusura anteprima pagine")
        prevOv[0] = vm.showOverview
    }
    LaunchedEffect(pagerState.isScrollInProgress) {
        if (pagerState.isScrollInProgress) Perf.begin("Cambio pagina") else Perf.end("Cambio pagina")
    }

    LaunchedEffect(vm.pageReqN) {
        val p = vm.pageReq
        if (vm.pageReqN > 0 && p >= 0) pagerState.animateScrollToPage(p.coerceIn(0, maxOf(0, vm.pageCount - 1)))
    }
    if (vm.showOverview) {
        androidx.activity.compose.BackHandler { vm.showOverview = false }
    }
    // entrando nell'anteprima si chiudono modifica e menu
    LaunchedEffect(vm.showOverview) {
        if (vm.showOverview) {
            menuItem = null
            if (vm.editMode) vm.exitEdit()
        }
    }
    val pinchOk = rememberUpdatedState(drag == null && vm.overlay == Overlay.NONE && vm.openFolderId == null && vm.minusP == 0f)

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

    // ritorno alla Home: anima l'avanzamento letto da icone, Home e sfondo
    LaunchedEffect(vm.returnSignal) {
        if (vm.returnSignal == 0) return@LaunchedEffect
        val style = vm.settings.returnAnim
        val dur = (returnDuration(if (style == "none") "settle" else style) * vm.settings.animFactor).toInt()
        androidx.compose.animation.core.animate(0f, 1f, animationSpec = tween(dur, easing = androidx.compose.animation.core.LinearEasing)) { v, _ ->
            vm.returnP = v
        }
        vm.returnP = 1f
    }

    // pagina −1: dalla prima pagina, lo scorrimento verso destra che il pager non usa la apre
    val minusEnabled = rememberUpdatedState(s.minusOn && !edit && drag == null && vm.overlay == Overlay.NONE)
    val minusScope = rememberCoroutineScope()
    val minusConn = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            fun w() = vm.screenPx.width.toFloat().coerceAtLeast(1f)
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (vm.minusP <= 0f || source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) return Offset.Zero
                MinusAnim.cancel()
                val before = vm.minusP
                vm.minusP = (before + available.x / w()).coerceIn(0f, 1f)
                return Offset((vm.minusP - before) * w(), 0f)
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (!minusEnabled.value || source != androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput ||
                    available.x <= 0f || pagerState.currentPage != 0
                ) return Offset.Zero
                MinusAnim.cancel()
                val before = vm.minusP
                vm.minusP = (before + available.x / w()).coerceIn(0f, 1f)
                return Offset((vm.minusP - before) * w(), 0f)
            }
            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (vm.minusP <= 0f || vm.minusP >= 1f) return androidx.compose.ui.unit.Velocity.Zero
                val open = if (abs(available.x) > 900f) available.x > 0f else vm.minusP > 0.35f
                MinusAnim.settle(minusScope, vm, open)
                return available
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .returnWhole(vm)
            .graphicsLayer {
                // la Home si sposta un po' mentre arriva la pagina −1
                val mp = vm.minusP
                if (mp > 0f) {
                    translationX = mp * 56.dp.toPx()
                    val sc = 1f - 0.05f * mp
                    scaleX = sc
                    scaleY = sc
                }
            }
            .pointerInput(Unit) {
                // pizzico con due dita: apre l'anteprima pagine (allargando le dita si chiude)
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var d0 = 0f
                    var fired = false
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val down = ev.changes.filter { it.pressed }
                        if (down.isEmpty()) break
                        if (down.size >= 2 && !fired) {
                            val dist = (down[0].position - down[1].position).getDistance()
                            if (d0 == 0f) d0 = dist
                            else if (pinchOk.value) {
                                if (!vm.showOverview && dist < d0 * 0.72f) {
                                    fired = true
                                    vm.showOverview = true
                                    Haptics.tick(view)
                                } else if (vm.showOverview && dist > d0 * 1.35f) {
                                    fired = true
                                    vm.showOverview = false
                                    Haptics.tick(view)
                                }
                            }
                        } else if (down.size < 2) d0 = 0f
                        if (fired) ev.changes.forEach { it.consume() }
                    }
                }
            }
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
        // velo sullo sfondo nell'anteprima pagine
        if (ov > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f * ov)))
        val sbTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val ovScale = if (landscape) 0.5f else 0.72f
        val ovLayer = Modifier.graphicsLayer {
            if (ov > 0f) {
                val sc = 1f - (1f - ovScale) * ov
                scaleX = sc
                scaleY = sc
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                translationY = ov * (sbTop.toPx() * (1f - ovScale) + 56.dp.toPx())
            }
        }
        // in anteprima la dock e i puntini spariscono e non si toccano
        val ovHide: @Composable (@Composable () -> Unit) -> Unit = { content ->
            Box(Modifier.alpha(1f - ov)) {
                content()
                if (vm.showOverview) Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures { vm.showOverview = false } })
            }
        }
        val pagerContent: @Composable (Modifier) -> Unit = { mod ->
            HorizontalPager(
                state = pagerState,
                // pagine pronte a scaglioni: prima quella visibile e le vicine, le altre una alla volta
                beyondViewportPageCount = beyondPages,
                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                    pagerState,
                    snapAnimationSpec = androidx.compose.animation.core.tween(
                        (Motion.PAGE_SNAP * s.animFactor).toInt(), easing = Motion.Standard
                    )
                ),
                userScrollEnabled = drag == null,
                modifier = mod
                    .then(
                        if (ov > 0f) Modifier
                            .border(2.dp, Color.White.copy(alpha = 0.55f * ov), RoundedCornerShape(28.dp))
                            .background(Color.White.copy(alpha = 0.08f * ov), RoundedCornerShape(28.dp))
                        else Modifier
                    )
                    .nestedScroll(minusConn)
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
                    onEmptyMenu = {
                        // Home bloccata: si apre l'anteprima, dove c'è "Sblocca"
                        if (vm.settings.lockHome) vm.showOverview = true else vm.editMode = true
                    }
                )
            }
        }
        if (landscape) {
            val navL = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val seascape = androidx.compose.ui.platform.LocalView.current.display?.rotation ==
                android.view.Surface.ROTATION_270
            val dockContent: @Composable () -> Unit = {
                // tema Sensation: la barra a tre tasti al posto della dock (non riceve icone)
                if (s.launcherTheme == "sensation") {
                    SideEffect { dockRect = Rect.Zero }
                    SenseBar(vm, host, vertical = true)
                } else Dock(
                    vm = vm, host = host, dragId = hiddenIds, cb = cb, onMenu = onMenu,
                    onRect = { dockRect = it }, vertical = true
                )
            }
            Row(
                Modifier.fillMaxSize().then(ovLayer).windowInsetsPadding(
                    WindowInsets.systemBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
            ) {
                // con il telefono ruotato dall'altra parte la dock sta a sinistra, come in Launcher3
                if (seascape && (!s.hideDock || edit)) ovHide { dockContent() }
                Column(Modifier.weight(1f).fillMaxHeight().padding(bottom = navL)) {
                    Spacer(Modifier.height(2.dp))
                    pagerContent(Modifier.weight(1f).fillMaxWidth())
                    // tema Sensation: niente indicatore di pagina né ricerca sopra la barra
                    if (s.launcherTheme != "sensation") ovHide { PageDots(vm.pageCount, pagerState, vm.settings.pageIndicator, vm.settings, vm.labelDark) { vm.searchFocus = true; vm.overlay = Overlay.DRAWER } }
                }
                if (!seascape && (!s.hideDock || edit)) ovHide { dockContent() }
            }
        } else {
        Column(
            Modifier.fillMaxSize().then(ovLayer).windowInsetsPadding(
                WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
        ) {
            Spacer(Modifier.height(topPad))
            pagerContent(Modifier.weight(1f).fillMaxWidth())
            // tema Sensation: niente indicatore di pagina né ricerca sopra la barra
                    if (s.launcherTheme != "sensation") ovHide { PageDots(vm.pageCount, pagerState, vm.settings.pageIndicator, vm.settings, vm.labelDark) { vm.searchFocus = true; vm.overlay = Overlay.DRAWER } }
            if ((!s.hideDock || edit) && s.launcherTheme == "sensation") ovHide {
                SideEffect { dockRect = Rect.Zero }
                SenseBar(vm, host)
            } else if (!s.hideDock || edit) ovHide {
                Dock(
                    vm = vm,
                    host = host,
                    dragId = hiddenIds,
                    cb = cb,
                    onMenu = onMenu,
                    onRect = { dockRect = it }
                )
            } else {
                dockRect = Rect.Zero
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(8.dp))
            }
        }

        }

        // anteprima delle pagine (pizzico o azione "Anteprima pagine"): comandi sopra e sotto la pagina
        if (ov > 0f) PagesOverviewControls(vm, host, pagerState, ov) {
            vm.addPage()
            scope.launch {
                delay(80)
                pagerState.animateScrollToPage(vm.pageCount - 1)
            }
        }


        // menu rapido
        val mi = menuItem
        if (mi != null && !edit) {
            QuickMenu(mi, menuRect, vm, host, fading = menuFading) { menuItem = null; menuFading = false }
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
                    .then(
                        if (inZone || barMat.plain) Modifier.clip(CircleShape).background(if (inZone) cs.primary else cs.secondaryContainer)
                        else Modifier.matLook(barMat, 50.dp, CircleShape, minOpacity = 40)
                    )
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Text(
                    "Crea cartella (${d.collected.size + 1} app)",
                    color = if (inZone) cs.onPrimary else if (barMat.plain) cs.onSecondaryContainer else matContentColor(barMat),
                    fontSize = 14.sp
                )
            }
        }
        val fhId = d?.let { dd ->
            targetOf(dd.pos, dd.item)?.let { t ->
                val h = vm.hitAt(t.first, t.second, t.third, dd.item.id)
                if (h != null && mergeAt(dd.pos, dd.item, t)) h.id else null
            }
        }
        LaunchedEffect(fhId) { vm.folderHoverId = fhId }
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
                val hit = vm.hitAt(t.first, t.second, t.third, d.item.id)
                val folderPreview = hit != null && mergeAt(d.pos, d.item, t)
                if (folderPreview) {
                    // anteprima cartella: il cerchio lo disegna l'icona di destinazione, dietro di sé (GridItem)
                } else {
                    Box(
                        Modifier
                            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                            .padding(4.dp)
                            .background(cs.primary.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
                    )
                }
            }
            val di = d.item
            val isBlock = di.spanX > 1 || di.spanY > 1
            val cellWpx = if (gridRect.width > 0f) gridRect.width / vm.gridCols else 200f
            val cellHpx = if (gridRect.height > 0f) gridRect.height / vm.gridRows else 200f
            val pw = if (isBlock) cellWpx * di.spanX else with(density) { (64.dp * s.iconScale).toPx() }
            val ph = if (isBlock) cellHpx * di.spanY else pw
            // l'elemento "si solleva": cresce con un piccolo rimbalzo e prende un'ombra
            val lift = remember(di.id) { androidx.compose.animation.core.Animatable(1f) }
            val liftTo = if (isBlock) 1.04f else 1.12f
            LaunchedEffect(di.id) {
                lift.animateTo(liftTo, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 520f))
            }
            Box(
                Modifier
                    .offset { IntOffset((d.pos.x - pw / 2f).roundToInt(), (d.pos.y - ph / 2f).roundToInt()) }
                    .size(with(density) { pw.toDp() }, with(density) { ph.toDp() })
                    .graphicsLayer {
                        scaleX = lift.value
                        scaleY = lift.value
                        shadowElevation = 14.dp.toPx() * ((lift.value - 1f) / (liftTo - 1f)).coerceIn(0f, 1f)
                        shape = if (isBlock) androidx.compose.foundation.shape.GenericShape { sz, _ ->
                            val inset = 5.dp.toPx()
                            addRoundRect(androidx.compose.ui.geometry.RoundRect(inset, inset, sz.width - inset, sz.height - inset, androidx.compose.ui.geometry.CornerRadius(28.dp.toPx())))
                        } else RoundedCornerShape(18.dp)
                        clip = false
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
                    else -> {
                        // il widget vero (fotografato all'inizio del trascinamento), sollevato con l'ombra
                        val snap = vm.dragSnapshot?.takeIf { it.first == di.id }?.second
                        if (snap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = snap,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize().padding(5.dp).clip(RoundedCornerShape(28.dp))
                            )
                        } else Box(
                            Modifier.fillMaxSize().padding(5.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(cs.surfaceContainerHigh.copy(alpha = 0.6f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, state: PagerState, style: String, st: LauncherSettings, darkText: Boolean = false, onSearch: () -> Unit) {
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
        val im = st.matOf("indicator")
        val follow = !im.plain
        val capsule = ios || scrubbing || follow
        val fg = if (follow) matContentColor(im) else if (darkText) Color(0xFF15171C) else Color.White
        val look = when {
            follow -> Modifier.matLook(im, 50.dp)
            else -> Modifier
                .clip(CircleShape)
                .background((if (darkText) Color.Black else Color.White).copy(alpha = if (capsule) (if (darkText) 0.10f else 0.22f) else 0f))
                .border(1.dp, (if (darkText) Color.Black else Color.White).copy(alpha = if (ios) 0.22f else 0f), CircleShape)
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
                    onTap = {
                        when {
                            vm.showOverview -> vm.showOverview = false
                            vm.selectedWidget != null -> { if (vm.selectionSettled()) vm.selectedWidget = null }
                            vm.editMode -> vm.exitEdit()
                        }
                    },
                    onDoubleTap = {
                        if (!vm.editMode && vm.settings.doubleTapLock) {
                            Haptics.confirm(view)
                            host.lockScreen()
                        }
                    },
                    onLongPress = { off ->
                        if (!vm.editMode && !vm.showOverview) {
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
                            if (total < -swipePx && vm.settings.swipeUp != "none") {
                                Haptics.tick(view)
                                if (vm.settings.swipeUp == "search") vm.searchFocus = true
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
                    // posizione animata: segue l'anteprima dello spostamento (150 ms, come Launcher3)
                    val rp = vm.reorderPreview[item.id]
                    val hidden = item.id in dragId
                    // l'elemento appena lasciato compare subito nel posto nuovo (niente viaggio dalla cella di partenza)
                    val wasHidden = remember { booleanArrayOf(false) }
                    val justDropped = wasHidden[0] && !hidden
                    androidx.compose.runtime.SideEffect { wasHidden[0] = hidden }
                    val goX = if (rp != null && rp.first == page) rp.second.toFloat() else item.x.toFloat()
                    val goY = if (rp != null && rp.first == page) rp.third.toFloat() else item.y.toFloat()
                    val moveSpec: androidx.compose.animation.core.AnimationSpec<Float> =
                        if (justDropped || hidden) androidx.compose.animation.core.snap()
                        else tween(Motion.REORDER, easing = Motion.Standard)
                    val tx by animateFloatAsState(goX, moveSpec, label = "rx")
                    val ty by animateFloatAsState(goY, moveSpec, label = "ry")
                    val leaving = rp != null && rp.first != page
                    // per il solo fotogramma prima dello scatto resta invisibile
                    val waiting = justDropped && (tx != goX || ty != goY)
                    val mod = Modifier.offset(cw * tx, ch * ty).alpha(if (waiting) 0f else if (leaving) 0.35f else 1f)
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
        // anteprima pagine: un tocco sulla pagina la apre (le icone non si avviano)
        if (vm.showOverview) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { vm.showOverview = false } })
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
    // materiale (generale o della dock) e forma della dock
    val m = st.matOf("dock")
    val tiles = st.dockForm == "tiles"
    if (vertical) {
        val navB = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Box(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .padding(bottom = navB)
                .width(84.dp * st.dockSize)
                .then(if (tiles) Modifier else Modifier.matLook(m, m.corner.dp))
                .onGloballyPositioned { onRect(it.boundsInRoot()) }
        ) {
            if (tiles) {
                Column(Modifier.fillMaxSize()) {
                    repeat(vm.dockCount) {
                        Box(
                            Modifier.weight(1f).fillMaxSize().padding(horizontal = 2.dp, vertical = 3.dp)
                                .matLook(m, minOf(m.corner.dp, 26.dp))
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
    val bar = st.dockForm == "bar"
    val margin = if (bar) 0.dp else 16.dp
    val corner = m.corner.dp
    // la barra a tutta larghezza arriva fino al bordo: sotto le icone c'è lo spazio dei gesti di sistema
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = margin, end = margin, top = 6.dp, bottom = if (bar) 0.dp else 6.dp + nav)
            .height(92.dp * st.dockSize + (if (bar) nav else 0.dp))
            .then(
                if (tiles) Modifier
                else Modifier.matLook(m, corner, if (bar) RoundedCornerShape(topStart = corner, topEnd = corner) else null)
            )
            .onGloballyPositioned { onRect(it.boundsInRoot()) }
    ) {
        if (tiles) {
            Row(Modifier.fillMaxSize()) {
                repeat(vm.dockCount) {
                    Box(
                        Modifier.weight(1f).fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp)
                            .matLook(m, minOf(corner, 26.dp))
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
        // cartella: appena il dito la tocca, il suo contenuto si prepara (nascosto), così all'apertura è già pronto
        .then(
            if (item is FolderItem) Modifier.pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val f = curItem as? FolderItem
                    if (f != null && !vm.editMode && vm.openFolderId == null) vm.folderPreload = f.folderId
                }
            } else Modifier
        )
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

    Box(modifier.size(cellW, cellH).returnItem(vm, item)) {
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
            // anteprima cartella (come Launcher3): cerchio ~1,3 volte l'icona, dietro di essa;
            // l'app di destinazione si fa un po' più piccola, la cartella un po' più grande
            val fh = vm.folderHoverId == item.id && !hidden
            val ring by animateFloatAsState(
                if (fh) 1f else 0f,
                tween(Motion.REORDER + 50, easing = Motion.EmphasizedDecelerate), label = "fring"
            )
            val ringFill = MaterialTheme.colorScheme.primaryContainer
            val ringEdge = MaterialTheme.colorScheme.primary
            val targetScale = if (item is FolderItem) 1.06f else 0.9f
            Box(
                Modifier
                    .size(iconSize)
                    .drawBehind {
                        if (ring > 0.001f) {
                            val r = size.minDimension / 2f * 1.3f * (0.75f + 0.25f * ring)
                            drawCircle(ringFill.copy(alpha = 0.7f * ring), r)
                            drawCircle(
                                ringEdge.copy(alpha = 0.55f * ring), r,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
                            )
                        }
                    }
                    .graphicsLayer {
                        val sc = 1f + (targetScale - 1f) * ring
                        scaleX = sc; scaleY = sc
                    }
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
                // nomi bianchi o scuri in base allo sfondo (o alla scelta nelle impostazioni)
                val darkText = vm.labelDark
                if (s.labelPills) {
                    // come HTC Sense: nome bianco su una pillola scura, si legge su qualunque sfondo
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 3.dp, start = 1.dp, end = 1.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.verticalGradient(listOf(Color(0xCC3A3C40), Color(0xCC17181A))))
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                } else
                Text(
                    text = label,
                    color = if (darkText) Color(0xFF15171C) else Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        shadow = if (darkText) Shadow(Color.White.copy(alpha = 0.55f), Offset(0f, 1f), 3f)
                        else Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f)
                    ),
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
    // pressione lunga sul widget: si entra in modifica con il widget già selezionato (come Nova)
    val enterEdit: () -> Unit = {
        if (!vm.editMode) {
            if (Haptics.pressOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.editMode = true
        }
        if (vm.editMode) vm.selectWidget(curItem.id)
    }
    val cwPx = with(density) { cellW.toPx() }
    val chPx = with(density) { cellH.toPx() }
    val shape = RoundedCornerShape(28.dp)
    // il contenuto viene registrato in un layer: quando parte il trascinamento ne facciamo una foto
    val snapLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val snapScope = rememberCoroutineScope()

    Box(
        modifier
            .returnItem(vm, item)
            .size(cellW * item.spanX, cellH * item.spanY)
            .padding(5.dp)
            .alpha(if (hidden) 0f else 1f)
    ) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                snapLayer.record { this@drawWithContent.drawContent() }
                drawLayer(snapLayer)
            }
        ) {
            when (item) {
                is WidgetItem -> GlassWidgetFrame(item.ref.kind) { WidgetContent(item.ref, vm, host, enterEdit, Modifier.fillMaxSize()) }
                is StackItem -> StackContent(item, vm, host, enterEdit, Modifier.fillMaxSize())
                else -> {}
            }
        }
        if (edit) {
            val sel = vm.selectedWidget == item.id
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .border(if (sel) 2.5.dp else 1.5.dp, if (sel) cs.primary else cs.primary.copy(alpha = 0.55f), shape)
                    .onGloballyPositioned {
                        holder.root = it.positionInRoot()
                        holder.window = it.boundsInWindow()
                    }
                    // tocco: seleziona il widget e compaiono i pallini per ridimensionarlo (come Nova).
                    // Letto nel primo passaggio, così nessun altro gesto se lo prende prima.
                    .pointerInput(item.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var moved = false
                            while (true) {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if ((c.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                if (!c.pressed) {
                                    if (!moved && c.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                                        vm.selectWidget(curItem.id)
                                        c.consume()
                                    }
                                    break
                                }
                            }
                        }
                    }
                    .pointerInput(item.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                if (Haptics.pressOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                vm.selectedWidget = null
                                val id = curItem.id
                                snapScope.launch {
                                    val bmp = try { snapLayer.toImageBitmap() } catch (e: Exception) { null }
                                    vm.dragSnapshot = bmp?.let { id to it }
                                }
                                curCb.start(curItem, holder.root + pos)
                            },
                            onDrag = { ch, _ -> curCb.move(holder.root + ch.position) },
                            // dopo lo spostamento il widget resta selezionato, con pallini, matita e cestino (come Nova)
                            onDragEnd = { val id = curItem.id; curCb.end(); if (vm.editMode && vm.items.any { it.id == id }) vm.selectWidget(id) },
                            onDragCancel = { val id = curItem.id; curCb.end(); if (vm.editMode && vm.items.any { it.id == id }) vm.selectWidget(id) }
                        )
                    }
            )
            MinusBadge(Modifier.align(Alignment.TopStart).padding(6.dp)) { vm.removeWidgetItem(item) }
            if (item is StackItem) {
                StackManage(item, vm, Modifier.align(Alignment.TopEnd).padding(6.dp))
            }
            if (sel) {
                // pallini a metà dei lati: 0 sinistra, 1 sopra, 2 destra, 3 sotto
                for (side in 0..3) {
                    val al = when (side) {
                        0 -> Alignment.CenterStart
                        1 -> Alignment.TopCenter
                        2 -> Alignment.CenterEnd
                        else -> Alignment.BottomCenter
                    }
                    // zona di presa grande (48 dp) e, sul bordo dello schermo, niente gesto Indietro
                    Box(
                        Modifier
                            .align(al)
                            .size(48.dp)
                            .systemGestureExclusion()
                            .pointerInput(item.id, side) {
                                var x0 = 0
                                var y0 = 0
                                var sx0 = 1
                                var sy0 = 1
                                var acc = Offset.Zero
                                detectDragGestures(
                                    onDragStart = {
                                        x0 = curItem.x; y0 = curItem.y
                                        sx0 = curItem.spanX; sy0 = curItem.spanY
                                        acc = Offset.Zero
                                    },
                                    onDrag = { c, d ->
                                        c.consume()
                                        acc += d
                                        val dx = (acc.x / cwPx).roundToInt()
                                        val dy = (acc.y / chPx).roundToInt()
                                        var nx = x0; var ny = y0; var nsx = sx0; var nsy = sy0
                                        when (side) {
                                            0 -> { val k = dx.coerceIn(-x0, sx0 - 1); nx = x0 + k; nsx = sx0 - k }
                                            1 -> { val k = dy.coerceIn(-y0, sy0 - 1); ny = y0 + k; nsy = sy0 - k }
                                            2 -> nsx = (sx0 + dx).coerceIn(1, maxOf(1, vm.gridCols - x0))
                                            else -> nsy = (sy0 + dy).coerceIn(1, maxOf(1, vm.gridRows - y0))
                                        }
                                        val cur = curItem
                                        if (nx != cur.x || ny != cur.y || nsx != cur.spanX || nsy != cur.spanY) {
                                            vm.moveResize(cur, nx, ny, nsx, nsy)
                                            Haptics.tick(view)
                                        }
                                    }
                                )
                            },
                        // il pallino si vede sul bordo del widget, la presa si estende verso l'interno
                        contentAlignment = al
                    ) {
                        Box(
                            Modifier.size(18.dp).shadow(3.dp, CircleShape).clip(CircleShape)
                                .background(cs.primary).border(2.dp, cs.onPrimary, CircleShape)
                        )
                    }
                }
                // pulsantini: riconfigura (se il widget lo permette) ed elimina
                val extId = (item as? WidgetItem)?.ref?.takeIf { it.kind == "ext" }?.widgetId
                val canConfig = extId != null && vm.widgetMgr.getAppWidgetInfo(extId)?.configure != null
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (extId != null && canConfig) {
                        Box(
                            Modifier.size(40.dp).shadow(3.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                                .background(cs.primary).clickable { host.reconfigureWidget(extId) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Edit, "Riconfigura", tint = cs.onPrimary, modifier = Modifier.size(20.dp)) }
                    }
                    Box(
                        Modifier.size(40.dp).shadow(3.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                            .background(cs.errorContainer).clickable {
                                vm.selectedWidget = null
                                vm.removeWidgetItem(item)
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Delete, "Elimina widget", tint = cs.onErrorContainer, modifier = Modifier.size(20.dp)) }
                }
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
                                    "senseClock" -> "Orologio Sense"
                                    "senseFlip" -> "Orologio a cartellini Sense"
                                    "battery" -> "Batteria"
                                    "search" -> "Ricerca"
                                    "notes" -> "Nota"
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
    fading: Boolean = false,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    // compare crescendo un poco; quando inizi a trascinare sparisce in dissolvenza (come Launcher3)
    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(Motion.FADE, easing = Motion.EmphasizedDecelerate)) }
    LaunchedEffect(fading) {
        if (fading) {
            appear.animateTo(0f, tween(Motion.FADE, easing = Motion.StandardAccelerate))
            onDismiss()
        }
    }
    val density = LocalDensity.current
    val conf = LocalConfiguration.current
    val app = (item as? AppItem)?.let { vm.apps[it.appId] }
    val shortcuts by produceState<List<ShortcutEntry>>(emptyList(), item.id) {
        if (app != null) value = withContext(Dispatchers.IO) { vm.loadShortcuts(app.pkg) }
    }
    val rows = (if (app != null) 4 + (if (app.removable) 1 else 0) else 5) + shortcuts.size
    val menuW = 240.dp
    val menuWpx = with(density) { menuW.toPx() }
    val estH = with(density) { (rows * 54 + 8 + 10).dp.toPx() }
    val screenW = with(density) { conf.screenWidthDp.dp.toPx() }
    val screenH = with(density) { conf.screenHeightDp.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    val gap = with(density) { 2.dp.toPx() }
    val x = (rect.center.x - menuWpx / 2f).coerceIn(margin, maxOf(margin, screenW - menuWpx - margin))
    val below = rect.bottom + estH + gap + margin < screenH
    val y = if (below) rect.bottom + gap else maxOf(margin, rect.top - estH - gap)
    // la freccetta punta al centro dell'icona premuta (come Nova)
    val arrowX = with(density) { (rect.center.x - x).toDp() }.coerceIn(28.dp, menuW - 28.dp)

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(x.roundToInt(), y.roundToInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            Modifier.width(menuW).graphicsLayer {
                alpha = appear.value
                val sc = 0.92f + 0.08f * appear.value
                scaleX = sc; scaleY = sc
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (below) 0f else 1f)
            }
        ) {
            if (below) MenuArrow(vm, arrowX, up = true)
            MenuSurface(vm, 20.dp) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    var first = true
                    @Composable
                    fun row(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, bmp: androidx.compose.ui.graphics.ImageBitmap? = null, danger: Boolean = false, click: () -> Unit) {
                        if (!first) MenuDivider()
                        first = false
                        MenuRow(text, icon, bmp, danger) { onDismiss(); click() }
                    }
                    if (app != null && app.isAction) {
                        row("Rimuovi", Icons.Default.Close) { vm.removeWidgetItem(item) }
                        row("Modifica", Icons.Default.Edit) { vm.editMode = true }
                    } else if (app != null) {
                        shortcuts.forEach { sc -> row(sc.label, bmp = sc.icon) { vm.launchShortcut(sc) } }
                        if (vm.settings.homeMode == "drawer") row("Rimuovi", Icons.Default.Close) { vm.removeWidgetItem(item) }
                        row("Informazioni app", Icons.Default.Info) { host.appInfo(app) }
                        row("Modifica", Icons.Default.Edit) { vm.editMode = true }
                        row("Nascondi", Icons.Default.Lock) { vm.hideApp(app.id) }
                        if (app.removable) row("Disinstalla", Icons.Default.Delete, danger = true) { host.uninstall(app) }
                    } else if (item is FolderItem) {
                        row("Rinomina", Icons.Default.Create) {
                            vm.folderAnchor = rect
                            vm.openFolderId = item.folderId
                        }
                        row("Ordina A-Z", Icons.AutoMirrored.Filled.List) { vm.sortFolder(item.folderId) }
                        row(if (item.big) "Riduci" else "Ingrandisci (2x2)", Icons.Default.Add) { vm.setFolderBig(item.folderId, !item.big) }
                        row("Elimina cartella", Icons.Default.Delete, danger = true) { vm.dissolveFolder(item.folderId) }
                        row("Modifica", Icons.Default.Edit) { vm.editMode = true }
                    } else {
                        row("Rimuovi", Icons.Default.Close) { vm.removeWidgetItem(item) }
                        row("Ridimensiona", Icons.Default.Edit) { vm.editMode = true; vm.selectWidget(item.id) }
                    }
                }
            }
            if (!below) MenuArrow(vm, arrowX, up = false)
        }
    }
}

/** Freccetta del menu verso l'elemento premuto, dello stesso colore del menu. */
@Composable
private fun MenuArrow(vm: LauncherViewModel, x: Dp, up: Boolean) {
    val cs = MaterialTheme.colorScheme
    val m = vm.settings.matOf("menus")
    val color = if (m.plain) cs.surfaceContainerHigh else when (m.style) {
        "fluent" -> fluentBase(fluentDark())
        "sense" -> SENSE_BASE
        "pill" -> cs.surfaceContainer
        else -> androidx.compose.ui.graphics.lerp(cs.surfaceContainerHigh, Color.White, 0.2f)
    }
    Box(
        Modifier.padding(start = x - 8.dp).size(16.dp, 8.dp)
            .clip(androidx.compose.foundation.shape.GenericShape { sz, _ ->
                if (up) { moveTo(0f, sz.height); lineTo(sz.width / 2f, 0f); lineTo(sz.width, sz.height) }
                else { moveTo(0f, 0f); lineTo(sz.width / 2f, sz.height); lineTo(sz.width, 0f) }
                close()
            })
            .background(color)
    )
}

/** Linea tra le voci del menu, che parte dopo l'icona (come Nova). */
@Composable
private fun MenuDivider() {
    Box(
        Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    )
}

/** Fondo dei menu rapidi con il loro materiale (generale o proprio). */
@Composable
internal fun MenuSurface(vm: LauncherViewModel, corner: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val m0 = vm.settings.matOf("menus")
    // dentro un menu a comparsa il vetro liquido non vede lo sfondo: diventa vetro
    val m = if (m0.style == "liquid") m0.copy(style = "glass") else m0
    val shape = RoundedCornerShape(corner)
    if (m.plain) {
        Surface(shape = shape, color = cs.surfaceContainerHigh, shadowElevation = 8.dp, modifier = modifier) { content() }
        return
    }
    // base sotto il vetro: il menu resta leggibile anche sopra icone colorate
    // colore risultante (fondo quasi pieno + materiale sopra lo sfondo vero): testo con il contrasto migliore
    val panel = matPanelColor(m.style, maxOf(m.opacity, 70), m.glass, overColor(cs.surfaceContainerHigh, 0.9f, backdropTone()))
    val fg = when (m.style) {
        "pill" -> readableOn(cs.onSurface, panel)
        else -> bestTextOn(panel)
    }
    // fondo quasi pieno: le icone sotto il menu non devono trasparire e confondere le voci
    Surface(shape = shape, color = cs.surfaceContainerHigh.copy(alpha = 0.9f), shadowElevation = 8.dp, modifier = modifier) {
        Box(Modifier.matLook(m, corner, shape, minOpacity = 70)) {
            // il rosso di "Disinstalla"/"Elimina" segue il testo: scuro su fondo chiaro, chiaro su fondo scuro
            val err = if (fg.luminance() < 0.5f) Color(0xFFB3261E) else Color(0xFFFFB4AB)
            MaterialTheme(colorScheme = cs.copy(onSurface = fg, onSurfaceVariant = fg.copy(alpha = 0.75f), error = err)) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides fg) { content() }
            }
        }
    }
}

@Composable
private fun MenuRow(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    bmp: androidx.compose.ui.graphics.ImageBitmap? = null,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(53.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // icona a sinistra di ogni voce (come Nova); spazio vuoto se manca, così i testi restano allineati
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when {
                bmp != null -> androidx.compose.foundation.Image(bmp, null, Modifier.size(24.dp))
                icon != null -> Icon(icon, null, tint = if (danger) cs.error else cs.onSurface, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text,
            color = if (danger) cs.error else cs.onSurface,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


/** Icona "Sfondi" (Material Wallpaper). */
private val WallpaperIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    pathIcon("Sfondi", "M4 4h7V2H4c-1.1 0-2 .9-2 2v7h2V4zm6 9l-4 5h12l-3-4-2.03 2.71L10 13zm7-4.5c0-.83-.67-1.5-1.5-1.5S14 7.67 14 8.5s.67 1.5 1.5 1.5S17 9.33 17 8.5zM20 2h-7v2h7v7h2V4c0-1.1-.9-2-2-2zm0 18h-7v2h7c1.1 0 2-.9 2-2v-7h-2v7zM4 13H2v7c0 1.1.9 2 2 2h7v-2H4v-7z")
}

/** Icona "Widget" (Material Widgets). */
internal val WidgetsIcon: androidx.compose.ui.graphics.vector.ImageVector by lazy {
    pathIcon("Widget", "M13 13v8h8v-8h-8zM3 21h8v-8H3v8zM3 3v8h8V3H3zm13.66-1.31L11 7.34 16.66 13l5.66-5.66-5.66-5.65z")
}

private fun pathIcon(name: String, d: String): androidx.compose.ui.graphics.vector.ImageVector =
    androidx.compose.ui.graphics.vector.ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = androidx.compose.ui.graphics.vector.PathParser().parsePathString(d).toNodes(),
        fill = androidx.compose.ui.graphics.SolidColor(Color.Black)
    ).build()

/**
 * Anteprima pagine come Nova: sopra la pagina rimpicciolita il pulsante della pagina principale
 * e quello per eliminarla; sotto Sfondi, Nuova pagina, Blocca e Impostazioni, e il pannello Widget
 * che si apre trascinandolo in su.
 */
@Composable
private fun PagesOverviewControls(vm: LauncherViewModel, host: Host, pagerState: PagerState, ov: Float, onAddPage: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current
    val page = pagerState.currentPage
    val isHome = vm.homePageIdx == page
    var askDelete by remember { mutableStateOf(-1) }
    val land = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Box(Modifier.fillMaxSize().alpha(ov)) {
        // sopra la pagina
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverviewRound(
                Icons.Default.Home,
                if (isHome) "Pagina principale" else "Rendi principale",
                filled = isHome
            ) {
                if (!isHome) {
                    vm.setHomePage(page)
                    Haptics.confirm(view)
                }
            }
            val canDelete = vm.pageCount > 1 && (vm.pageEmpty(page) || vm.settings.homeMode != "all")
            if (canDelete) OverviewRound(Icons.Default.Delete, "Elimina pagina", filled = false) {
                if (vm.pageEmpty(page)) vm.deletePage(page) else askDelete = page
            }
        }
        // sotto la pagina
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = if (land) 0.dp else SHEET_PEEK)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // in orizzontale non c'è spazio per il pannello: Widget diventa un pulsante
                if (land) OverviewButton(WidgetsIcon, "Widget") { if (!vm.blockedByLock()) vm.overlay = Overlay.WIDGETS }
                OverviewButton(WallpaperIcon, "Sfondi") {
                    vm.overlay = Overlay.SETTINGS; vm.settingsTab = 1; vm.settingsPage = "wallpaper"
                }
                OverviewButton(Icons.Default.Add, "Nuova pagina") { if (!vm.blockedByLock()) onAddPage() }
                OverviewButton(Icons.Default.Lock, if (vm.settings.lockHome) "Sblocca" else "Blocca") {
                    vm.setSetting("lockHome", !vm.settings.lockHome)
                    Haptics.confirm(view)
                }
                OverviewButton(Icons.Default.Settings, "Impostazioni") { vm.overlay = Overlay.SETTINGS }
            }
        }
        // pannello Widget: trascinandolo in su (o toccandolo) si allarga e mostra l'elenco dei widget
        if (!land) WidgetSheet(vm, host, Modifier.align(Alignment.BottomCenter))
    }
    if (askDelete >= 0) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askDelete = -1 },
            title = { Text("Eliminare la pagina?") },
            text = { Text("Spariscono anche le icone e i widget che contiene. Le app restano nel cassetto.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    vm.deletePage(askDelete)
                    askDelete = -1
                }) { Text("Elimina") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { askDelete = -1 }) { Text("Annulla") }
            }
        )
    }
}

/** Altezza del pannello Widget chiuso (sopra la barra di navigazione). */
private val SHEET_PEEK = 76.dp

/**
 * Pannello "Widget" in fondo all'anteprima pagine (come Nova): segue il dito mentre lo trascini in su
 * e diventa l'elenco dei widget; trascinandolo in giù (o con Indietro) torna piccolo.
 */
@Composable
private fun WidgetSheet(vm: LauncherViewModel, host: Host, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val navH = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val peekPx = with(density) { (SHEET_PEEK + navH).toPx() }
    val maxPx = with(density) { (LocalConfiguration.current.screenHeightDp.dp * 0.82f).toPx() }.coerceAtLeast(peekPx + 1f)
    val h = remember { androidx.compose.animation.core.Animatable(peekPx) }
    val frac = ((h.value - peekPx) / (maxPx - peekPx)).coerceIn(0f, 1f)
    fun settle(open: Boolean) {
        if (open) Haptics.tick(view)
        scope.launch {
            h.animateTo(
                if (open) maxPx else peekPx,
                tween((Motion.SHEET * vm.settings.animFactor).toInt().coerceAtLeast(1), easing = Motion.EmphasizedDecelerate)
            )
        }
    }
    // widget aggiunto: il pannello si richiude e il widget compare sulla pagina
    val count = vm.items.size
    LaunchedEffect(count) { if (h.value > peekPx + 1f) settle(false) }
    if (frac > 0f) androidx.activity.compose.BackHandler { settle(false) }

    val m = vm.settings.matOf("menus")
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val fgPeek = if (m.plain) cs.onSurface else matContentColor(m)
    val fg = androidx.compose.ui.graphics.lerp(fgPeek, cs.onSurface, frac)
    Box(
        modifier
            .fillMaxWidth()
            .height(with(density) { h.value.toDp() })
            .clip(shape)
            .then(if (m.plain) Modifier.background(cs.surfaceContainer) else Modifier.matLook(m, 28.dp, shape, minOpacity = 60))
            // aprendolo diventa pieno, così l'elenco si legge bene
            .background(cs.surfaceContainer.copy(alpha = if (m.plain) 0f else frac))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(Modifier.fillMaxSize()) {
            // maniglia e titolo: si trascinano
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var acc = 0f
                        val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = {
                                acc = 0f
                                tracker.resetTracking()
                                scope.launch { h.stop() }
                            },
                            onVerticalDrag = { c, dy ->
                                c.consume()
                                acc += dy
                                tracker.addPosition(c.uptimeMillis, Offset(0f, acc))
                                scope.launch { h.snapTo((h.value - dy).coerceIn(peekPx, maxPx)) }
                            },
                            onDragEnd = {
                                val v = tracker.calculateVelocity().y
                                val f = (h.value - peekPx) / (maxPx - peekPx)
                                settle(if (abs(v) > 900f) v < 0f else f > 0.3f)
                            },
                            onDragCancel = { settle((h.value - peekPx) / (maxPx - peekPx) > 0.5f) }
                        )
                    }
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                        if (!vm.blockedByLock()) settle(frac < 0.5f)
                    }
                    .padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.padding(top = 10.dp).size(36.dp, 4.dp).clip(CircleShape).background(fg.copy(alpha = 0.45f)))
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(WidgetsIcon, null, tint = cs.primary, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Widget", color = fg, fontSize = 20.sp)
                }
            }
            // l'elenco compare mentre il pannello sale
            if (frac > 0.02f) {
                Box(Modifier.weight(1f).fillMaxWidth().alpha(frac)) { PickerContent(vm, host, header = false) }
            }
        }
    }
}

/** Pulsante tondo sopra la pagina dell'anteprima. */
@Composable
private fun OverviewRound(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, filled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(44.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(if (filled) cs.primary else cs.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, desc, tint = if (filled) cs.onPrimary else cs.primary, modifier = Modifier.size(24.dp))
    }
}

/** Pulsante sotto la pagina dell'anteprima: icona col colore del tema e nome. */
@Composable
private fun OverviewButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(cs.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = cs.onPrimaryContainer, modifier = Modifier.size(26.dp)) }
        Text(
            label,
            color = Color.White,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), 4f)),
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
