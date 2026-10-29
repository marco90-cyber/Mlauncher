package com.marco.mlauncher

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.alpha
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.flow.drop
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.composed
import androidx.compose.foundation.indication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =====================================================================
//  Cartella aperta
// =====================================================================

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun FolderOverlay(vm: LauncherViewModel, host: Host) {
    val id = vm.openFolderId
    val folder = vm.items.firstOrNull { it is FolderItem && it.folderId == id } as? FolderItem
    var last by remember { mutableStateOf<FolderItem?>(null) }
    if (folder != null) last = folder
    val shown = folder ?: last
    val s = vm.settings
    val dur = (240 * s.animFactor).toInt()
    val ctx = LocalContext.current

    // animazione come in Lawnchair (FolderAnimationManager): 200 ms, curva standard Material,
    // la cartella cresce dall'icona. Un solo valore di avanzamento letto solo in fase di disegno.
    val prog = remember { androidx.compose.animation.core.Animatable(0f) }
    val opening = folder != null
    LaunchedEffect(opening) {
        prog.animateTo(
            if (opening) 1f else 0f,
            tween(
                ((if (opening) Motion.FOLDER else Motion.FOLDER_CLOSE) * s.animFactor).toInt(),
                easing = Motion.Standard
            )
        )
    }
    val geo = remember { floatArrayOf(0.5f, 0.5f) }
    run {
        if (shown != null && (folder != null || prog.value > 0.001f)) {
            var name by remember(shown.folderId) { mutableStateOf(shown.name) }
            val nameRef = rememberUpdatedState(name)
            val folderId = shown.folderId
            DisposableEffect(folderId) {
                onDispose { vm.renameFolder(folderId, nameRef.value) }
            }
            val focusManager = LocalFocusManager.current
            val haptic = LocalHapticFeedback.current
            val cellRects = remember(folderId) { mutableStateMapOf<String, Rect>() }
            var cardRect by remember(folderId) { mutableStateOf(Rect.Zero) }
            var dragApp by remember(folderId) { mutableStateOf<String?>(null) }
            var dragPos by remember(folderId) { mutableStateOf(Offset.Zero) }
            var menuApp by remember(folderId) { mutableStateOf<String?>(null) }
            var menuRect by remember(folderId) { mutableStateOf(Rect.Zero) }
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = 0.18f * prog.value)) }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { vm.openFolderId = null }
            ) {
                val dens = LocalDensity.current
                val conf = LocalConfiguration.current
                val anchor = vm.folderAnchor
                val cols = s.folderCols
                val cardW = minOf((cols * 72 + 24).dp, (conf.screenWidthDp - 32).dp)
                val cardWpx = with(dens) { cardW.toPx() }
                val sw = with(dens) { conf.screenWidthDp.dp.toPx() }
                val sh = with(dens) { conf.screenHeightDp.dp.toPx() }
                val m = with(dens) { 16.dp.toPx() }
                val hasAnchor = anchor != Rect.Zero
                val cx = if (hasAnchor) anchor.center.x else sw / 2f
                val cardX = (cx - cardWpx / 2f).coerceIn(m, maxOf(m, sw - cardWpx - m))
                val startScale = if (hasAnchor) (anchor.width / cardWpx).coerceIn(0.2f, 0.7f) else 0.85f
                Box(
                    Modifier.layout { measurable, c ->
                        val pl = measurable.measure(c.copy(minWidth = 0, minHeight = 0))
                        val y = when {
                            !hasAnchor -> (sh - pl.height) / 2f
                            else -> anchor.bottom + m * 0.75f - pl.height
                        }.coerceIn(m, maxOf(m, sh - pl.height - m))
                        geo[0] = ((cx - cardX) / cardWpx).coerceIn(0f, 1f)
                        geo[1] = if (hasAnchor) ((anchor.center.y - y) / pl.height).coerceIn(0f, 1f) else 0.5f
                        layout(c.maxWidth, c.maxHeight) { pl.place(cardX.roundToInt(), y.roundToInt()) }
                    }
                ) {
                val dockSpec = LocalFolderDock.current
                val followDock = dockSpec != null && dockSpec.style != "none" && dockSpec.style != "tiles"
                val csF = MaterialTheme.colorScheme
                val fgColor = if (followDock && dockSpec != null) {
                    val st = if (dockSpec.style == "bar") "pill" else dockSpec.style
                    val base = when (st) {
                        "tinted" -> csF.primaryContainer
                        "pill" -> csF.surfaceContainer
                        else -> Color.White
                    }
                    folderTextColor(st, dockSpec.opacity, base, csF.surfaceContainerHigh, csF.onSurface)
                } else csF.onSurface
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = if (followDock) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
                    shadowElevation = if (followDock) 0.dp else 12.dp,
                    modifier = Modifier
                        .width(cardW)
                        .then(
                            if (followDock && dockSpec != null)
                                // base opaca sotto il vetro: la cartella resta leggibile anche con la dock molto trasparente
                                Modifier
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
                                    .dockLook(
                                        if (dockSpec.style == "bar") "pill" else dockSpec.style,
                                        maxOf(dockSpec.opacity, 40), dockSpec.glass, 28.dp
                                    )
                            else Modifier
                        )
                        .graphicsLayer {
                            val p = prog.value
                            val sc = startScale + (1f - startScale) * p
                            scaleX = sc
                            scaleY = sc
                            transformOrigin = TransformOrigin(geo[0], geo[1])
                            alpha = minOf(1f, p * 4f)
                        }
                        .onGloballyPositioned { cardRect = it.boundsInRoot() }
                        .pointerInput(Unit) { detectTapGestures { } }
                ) {
                    Column(Modifier.padding(12.dp)) {
                        val fCols = s.folderCols
                        val isLandF = LocalConfiguration.current.orientation ==
                            android.content.res.Configuration.ORIENTATION_LANDSCAPE
                        val perPage = fCols * (if (isLandF) 2 else 3)
                        val ids = shown.appIds
                        val pageN = maxOf(1, (ids.size + perPage - 1) / perPage)
                        val fPager = androidx.compose.foundation.pager.rememberPagerState { pageN }
                        val cell: @Composable (String) -> Unit = { appId ->
                            run {
                                val app = vm.apps[appId]
                                if (app != null) {
                                    val b = remember { BoundsHolder() }
                                    val curId by rememberUpdatedState(appId)
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .alpha(if (dragApp == appId) 0f else 1f)
                                            .onGloballyPositioned {
                                                b.window = it.boundsInWindow()
                                                b.root = it.positionInRoot()
                                                cellRects[appId] = it.boundsInRoot()
                                            }
                                            .clip(RoundedCornerShape(16.dp))
                                            .pointerInput(appId) {
                                                detectTapGestures(onTap = {
                                                    vm.openFolderId = null
                                                    host.launch(app, b.window)
                                                })
                                            }
                                            .pointerInput(appId) {
                                                var started = false
                                                var lpPos = Offset.Zero
                                                val slop = viewConfiguration.touchSlop
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { pos ->
                                                        if (Haptics.pressOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        lpPos = pos
                                                        started = false
                                                    },
                                                    onDrag = { ch, _ ->
                                                        if (!started && (ch.position - lpPos).getDistance() > slop) {
                                                            started = true
                                                            dragApp = curId
                                                        }
                                                        if (started) dragPos = b.root + ch.position
                                                    },
                                                    onDragEnd = {
                                                        if (started) {
                                                            val pos = dragPos
                                                            val moving = curId
                                                            dragApp = null
                                                            if (cardRect.contains(pos)) {
                                                                val target = cellRects.entries
                                                                    .firstOrNull { it.value.contains(pos) }?.key
                                                                val list = vm.items
                                                                    .firstOrNull { it is FolderItem && it.folderId == folderId }
                                                                    .let { (it as? FolderItem)?.appIds ?: emptyList() }
                                                                val idx = if (target != null) list.indexOf(target) else list.size - 1
                                                                if (idx >= 0) vm.moveInFolder(folderId, moving, idx)
                                                            } else {
                                                                vm.removeFromFolder(folderId, moving)
                                                                vm.openFolderId = null
                                                                Toast.makeText(ctx, "Spostata nella Home", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } else {
                                                            menuApp = curId
                                                            menuRect = b.window
                                                        }
                                                        started = false
                                                    },
                                                    onDragCancel = {
                                                        dragApp = null
                                                        started = false
                                                    }
                                                )
                                            }
                                            .padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AppIcon(app, 52.dp, s.themedIcons)
                                        Text(
                                            app.label,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = fgColor,
                                            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(90.dp * ((minOf(ids.size, perPage) + fCols - 1) / fCols))) {
                            androidx.compose.foundation.pager.HorizontalPager(
                                state = fPager,
                                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                                    fPager, snapAnimationSpec = tween(Motion.PAGE_SNAP, easing = Motion.Standard)
                                ),
                                modifier = Modifier.fillMaxSize()
                            ) { pg ->
                                val slice = ids.drop(pg * perPage).take(perPage)
                                Column(Modifier.fillMaxSize()) {
                                    slice.chunked(fCols).forEach { rowIds ->
                                        Row(Modifier.fillMaxWidth().height(90.dp)) {
                                            rowIds.forEach { id -> Box(Modifier.weight(1f)) { cell(id) } }
                                            repeat(fCols - rowIds.size) { Spacer(Modifier.weight(1f)) }
                                        }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = name,
                                onValueChange = { name = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Start,
                                    color = fgColor
                                ),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    vm.renameFolder(folderId, name)
                                    focusManager.clearFocus()
                                }),
                                modifier = Modifier.weight(1f)
                            )
                            if (pageN > 1) {
                                Row(Modifier.padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    repeat(pageN) { d ->
                                        Box(
                                            Modifier.size(8.dp).clip(CircleShape).background(
                                                if (d == fPager.currentPage) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
            val da = dragApp?.let { vm.apps[it] }
            if (da != null) {
                val sz = with(androidx.compose.ui.platform.LocalDensity.current) { 52.dp.toPx() }
                Box(
                    Modifier
                        .offset { IntOffset((dragPos.x - sz / 2f).roundToInt(), (dragPos.y - sz / 2f).roundToInt()) }
                        .size(52.dp)
                ) { AppIcon(da, 52.dp, s.themedIcons) }
            }
            val ma = menuApp?.let { vm.apps[it] }
            if (ma != null) {
                QuickMenu(AppItem(ma.id, -2, 0, 0), menuRect, vm, host) { menuApp = null }
            }
        }
    }
}

// =====================================================================
//  Cassetto app / ricerca
// =====================================================================

@Composable
fun AppsOverlay(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val visible = vm.overlay == Overlay.DRAWER
    var query by remember { mutableStateOf("") }
    val dur = (260 * s.animFactor).toInt()
    LaunchedEffect(visible) { if (!visible) query = "" }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween((Motion.FADE * s.animFactor).toInt(), easing = Motion.StandardDecelerate)) +
            slideInVertically(tween((Motion.ALL_APPS_OPEN * s.animFactor).toInt(), easing = Motion.EmphasizedDecelerate)) { it / 3 } +
            androidx.compose.animation.scaleIn(
                tween((Motion.ALL_APPS_OPEN * s.animFactor).toInt(), easing = Motion.EmphasizedDecelerate),
                initialScale = 0.92f
            ),
        exit = fadeOut(tween((Motion.ALL_APPS_CLOSE * s.animFactor).toInt(), easing = Motion.StandardAccelerate)) +
            slideOutVertically(tween((Motion.ALL_APPS_CLOSE * s.animFactor).toInt(), easing = Motion.EmphasizedAccelerate)) { it / 3 } +
            androidx.compose.animation.scaleOut(
                tween((Motion.ALL_APPS_CLOSE * s.animFactor).toInt(), easing = Motion.EmphasizedAccelerate),
                targetScale = 0.92f
            )
    ) {
        // mentre un'icona viene portata sulla Home il cassetto sparisce ma resta vivo (il dito è ancora suo)
        var dragHidden by remember { mutableStateOf(false) }
        LaunchedEffect(vm.drawerDrag) { if (vm.drawerDrag != null) dragHidden = true }
        LaunchedEffect(visible) { if (visible) dragHidden = false }
        val a by androidx.compose.animation.core.animateFloatAsState(
            if (dragHidden) 0f else 1f,
            tween(Motion.FADE, easing = Motion.Standard),
            label = "drawerDrag"
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = a }) {
            DrawerContent(vm, host, query) { query = it }
        }
    }
}

/**
 * Tocco = apri, pressione lunga = menu, pressione lunga e trascina = porta l'icona sulla Home
 * (come Launcher3: il menu si chiude appena il dito si muove).
 */
private fun Modifier.drawerGestures(
    app: AppEntry,
    vm: LauncherViewModel,
    bounds: BoundsHolder,
    onTap: () -> Unit,
    onMenu: (Boolean) -> Unit
): Modifier = composed {
    val view = androidx.compose.ui.platform.LocalView.current
    val tap by rememberUpdatedState(onTap)
    val menu by rememberUpdatedState(onMenu)
    // effetto onda al tocco, come un normale pulsante
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    indication(interaction, androidx.compose.foundation.LocalIndication.current).pointerInput(app.id) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val press = androidx.compose.foundation.interaction.PressInteraction.Press(down.position)
            interaction.tryEmit(press)
            fun release() = interaction.tryEmit(androidx.compose.foundation.interaction.PressInteraction.Release(press))
            fun cancel() = interaction.tryEmit(androidx.compose.foundation.interaction.PressInteraction.Cancel(press))
            val slop = viewConfiguration.touchSlop
            var up = false
            var moved = false
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (!up && !moved) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull { it.id == down.id }
                    if (c == null || !c.pressed) up = c != null
                    else if (c.isConsumed || (c.position - down.position).getDistance() > slop) moved = true
                    if (c == null) moved = true
                }
            }
            if (up) {
                release()
                tap()
                return@awaitEachGesture
            }
            if (moved) {
                cancel()
                return@awaitEachGesture
            }
            Haptics.press(view)
            menu(true)
            var dragging = false
            var released = false
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) {
                    released = true
                    break
                }
                c.consume()
                val rootPos = bounds.root + c.position
                if (!dragging && (c.position - down.position).getDistance() > slop * 1.5f) {
                    dragging = true
                    cancel()
                    menu(false)
                    vm.startDrawerDrag(app.id, rootPos)
                }
                if (dragging) vm.drawerDrag?.pos = rootPos
            }
            if (dragging) {
                if (released) vm.endDrawerDrag() else vm.cancelDrawerDrag()
            } else {
                release()
            }
        }
    }
}

private fun letterOf(label: String): Char {
    val c = label.firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

@Composable
private fun DrawerContent(vm: LauncherViewModel, host: Host, query: String, onQuery: (String) -> Unit) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = if (s.drRemember) vm.drawerScroll else 0
    )
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { vm.drawerScroll = gridState.firstVisibleItemIndex }
    }
    val scope = rememberCoroutineScope()
    val searchOnly = s.homeMode == "all"
    val list = remember(query, vm.sortedApps, vm.hidden.toList(), s.drSort) {
        val base = when (s.drSort) {
            "name_desc" -> vm.sortedApps.reversed()
            "usage" -> vm.sortedApps.sortedByDescending { vm.useCount(it.id) }
            "recent" -> vm.sortedApps.sortedByDescending { vm.installTime(it.pkg) }
            else -> vm.sortedApps
        }
        val visible = base.filter { it.id !in vm.hidden }
        if (query.isBlank()) visible
        else visible.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    LaunchedEffect(Unit) {
        val wantFocus = searchOnly || vm.searchFocus
        vm.searchFocus = false
        if (wantFocus) {
            delay(150)
            try {
                focus.requestFocus()
            } catch (e: Exception) {
                // campo non ancora pronto
            }
        }
    }
    val bgColor = when (s.drBg) {
        "tonal" -> cs.primaryContainer
        "dark" -> Color.Black
        "neutral" -> cs.surfaceContainerHighest
        else -> cs.surface
    }
    val fg = if (s.drBg == "dark") Color.White else if (s.drBg == "tonal") cs.onPrimaryContainer else cs.onSurface
    Surface(Modifier.fillMaxSize(), color = bgColor.copy(alpha = s.drOpacity / 100f), contentColor = fg) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .imePadding()
        ) {
            if (s.drSearchBar || searchOnly) TextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(fontSize = 17.sp),
                placeholder = { Text("Cerca app e altro", fontSize = 17.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancella")
                        }
                    }
                },
                shape = RoundedCornerShape(32.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = if (s.drSearchBg) cs.surfaceContainerHigh else Color.Transparent,
                    unfocusedContainerColor = if (s.drSearchBg) cs.surfaceContainerHigh else Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    list.firstOrNull()?.let {
                        vm.overlay = Overlay.NONE
                        host.launch(it, null)
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .heightIn(min = 58.dp)
                    .focusRequester(focus)
            )
            if (query.isNotBlank()) {
                SearchResults(Modifier.weight(1f), list, query, vm, host)
            } else if (searchOnly) {
                EmptySearch(Modifier.weight(1f))
            }
            val showGrid = !searchOnly && query.isBlank()
            if (showGrid) {
                Box(Modifier.weight(1f)) {
                    val listMode = s.drLayout == "list"
                    val suggested = remember(list, s.drSuggest, vm.homeSignal) {
                        if (!s.drSuggest) emptyList()
                        else list.filter { vm.useCount(it.id) > 0 }.sortedByDescending { vm.useCount(it.id) }.take(4)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (listMode) 1 else s.drCols),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = s.drIndent.dp, end = s.drIndent.dp + (if (s.drScrollbar) 30.dp else 0.dp),
                            top = 8.dp + s.drTop.dp, bottom = 8.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (suggested.isNotEmpty()) {
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = cs.surfaceContainerHigh.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        suggested.forEach { a -> Box(Modifier.weight(1f)) { DrawerApp(a, vm, host, forceGrid = true) } }
                                        repeat(4 - suggested.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                        items(list, key = { it.id }) { app -> DrawerApp(app, vm, host) }
                    }
                    if (!searchOnly && query.isBlank() && list.isNotEmpty() && s.drScrollbar) {
                        DrawerScrollBar(list, gridState, s.drSort.startsWith("name"))
                    }
                }
            }
        }
    }
}

/** Barra laterale: indicatore di posizione, striscia A-Z con ingrandimento e bolla della lettera. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.DrawerScrollBar(
    list: List<AppEntry>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    byName: Boolean
) {
    val cs = MaterialTheme.colorScheme
    val view = androidx.compose.ui.platform.LocalView.current
    val dens = LocalDensity.current
    val scope = rememberCoroutineScope()
    val letters = remember(list, byName) { if (byName) list.map { letterOf(it.label) }.distinct() else emptyList() }
    val firstIndex = remember(list, byName) {
        val m = LinkedHashMap<Char, Int>()
        if (byName) list.forEachIndexed { i, a -> m.getOrPut(letterOf(a.label)) { i } }
        m
    }
    var barH by remember { mutableIntStateOf(1) }
    var touchY by remember { mutableStateOf<Float?>(null) }
    var active by remember { mutableStateOf<Char?>(null) }
    var lastKey by remember { mutableIntStateOf(-1) }
    var show by remember { mutableStateOf(false) }
    val touching = touchY != null
    LaunchedEffect(gridState.isScrollInProgress, touching) {
        if (gridState.isScrollInProgress || touching) show = true
        else {
            delay(1500)
            show = false
        }
    }
    val thumbAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (show) 1f else 0f, tween(200), label = "thumb"
    )
    // feedback tattile scorrendo la lista: un tick a ogni nuova lettera (o ogni 12 icone)
    LaunchedEffect(list, byName) {
        androidx.compose.runtime.snapshotFlow {
            val i = gridState.firstVisibleItemIndex
            if (byName) letterOf(list.getOrNull(i)?.label ?: "").code else i / 12
        }.drop(1).collect { if (touchY == null) Haptics.tick(view) }
    }
    val touch: (Float) -> Unit = { y ->
        val yy = y.coerceIn(0f, barH.toFloat())
        touchY = yy
        if (byName && letters.isNotEmpty()) {
            val idx = ((yy / barH) * letters.size).toInt().coerceIn(0, letters.size - 1)
            val l = letters[idx]
            if (l != active) {
                active = l
                Haptics.tick(view)
            }
            scope.launch { gridState.scrollToItem(firstIndex[l] ?: 0) }
        } else {
            val total = gridState.layoutInfo.totalItemsCount
            val target = ((yy / barH) * (total - 1)).toInt().coerceIn(0, maxOf(0, total - 1))
            if (target / 12 != lastKey) {
                lastKey = target / 12
                Haptics.tick(view)
            }
            scope.launch { gridState.scrollToItem(target) }
        }
    }
    val release: () -> Unit = {
        touchY = null
        active = null
    }
    val thumbH = 56.dp
    val thumbPx = with(dens) { thumbH.toPx() }
    val info = gridState.layoutInfo
    val denom = maxOf(1, info.totalItemsCount - info.visibleItemsInfo.size)
    val frac = (gridState.firstVisibleItemIndex.toFloat() / denom).coerceIn(0f, 1f)

    Box(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .padding(vertical = 12.dp)
            .width(30.dp)
            .onSizeChanged { barH = it.height.coerceAtLeast(1) }
            .pointerInput(letters, byName) {
                detectTapGestures(onPress = { o ->
                    touch(o.y)
                    tryAwaitRelease()
                    release()
                })
            }
            .pointerInput(letters, byName) {
                detectVerticalDragGestures(
                    onDragStart = { o -> touch(o.y) },
                    onVerticalDrag = { c, _ -> touch(c.position.y) },
                    onDragEnd = release,
                    onDragCancel = release
                )
            }
    ) {
        // binario sottile sempre visibile: suggerisce che la lista scorre
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 3.dp).fillMaxHeight().width(2.dp)
                .clip(CircleShape).background(cs.onSurface.copy(alpha = 0.14f))
        )
        // maniglia di posizione
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, ((barH - thumbPx) * frac).toInt().coerceAtLeast(0)) }
                .padding(end = 1.dp)
                .width(if (touching) 7.dp else 5.dp)
                .height(thumbH)
                .clip(CircleShape)
                .background(cs.primary.copy(alpha = thumbAlpha))
        )
        if (byName && letters.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(20.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                letters.forEachIndexed { i, l ->
                    val ty = touchY
                    val center = (i + 0.5f) / letters.size * barH
                    val sc = if (ty == null) 1f else {
                        val d = (ty - center) / (barH.toFloat() / letters.size * 2.2f)
                        1f + 1.1f * kotlin.math.exp(-d * d)
                    }
                    Text(
                        l.toString(),
                        fontSize = 11.sp,
                        fontWeight = if (l == active) FontWeight.Bold else FontWeight.Medium,
                        color = cs.primary,
                        modifier = Modifier.graphicsLayer {
                            scaleX = sc
                            scaleY = sc
                            translationX = -(sc - 1f) * 26f
                        }
                    )
                }
            }
        }
    }
    // bolla grande con la lettera attuale, a sinistra del dito
    val ty = touchY
    val al = active
    if (ty != null && al != null && byName) {
        val bubble = 72.dp
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        -(48.dp.roundToPx()),
                        (ty + 12.dp.toPx() - bubble.toPx() / 2f).toInt().coerceAtLeast(0)
                    )
                }
                .size(bubble)
                .clip(RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp, topEnd = 36.dp, bottomEnd = 10.dp))
                .background(cs.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(al.toString(), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerApp(app: AppEntry, vm: LauncherViewModel, host: Host, forceGrid: Boolean = false) {
    val st = vm.settings
    val listMode = st.drLayout == "list" && !forceGrid
    var menu by remember { mutableStateOf(false) }
    val bounds = remember { BoundsHolder() }
    val ctx = LocalContext.current
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    bounds.window = it.boundsInWindow()
                    bounds.root = it.positionInRoot()
                }
                .clip(RoundedCornerShape(16.dp))
                .drawerGestures(app, vm, bounds, onTap = {
                    vm.overlay = Overlay.NONE
                    host.launch(app, bounds.window)
                }, onMenu = { menu = it })
                .padding(vertical = (8f * st.drRowH).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val iconSz = 52.dp * st.drIcon
            if (listMode) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, iconSz * 0.8f, st.themedIcons)
                    Spacer(Modifier.width(16.dp))
                    Text(app.label, fontSize = (st.drLabelSize + 5).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Box {
                    AppIcon(app, iconSz, st.themedIcons)
                    // puntino "nuova": app installata da poco e mai aperta
                    if (st.newAppDot && app.id in vm.newApps) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                if (st.drLabels) {
                    Text(
                        app.label,
                        fontSize = st.drLabelSize.sp,
                        maxLines = if (st.drTwoLine) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
                    )
                }
            }
        }
        AppMenu(app, menu, { menu = false }, vm, host)
    }
}

// =====================================================================
//  Contenitore impostazioni
// =====================================================================

@Composable
fun SettingsOverlay(vm: LauncherViewModel, host: Host) {
    AnimatedVisibility(
        visible = vm.overlay == Overlay.SETTINGS,
        enter = slideInHorizontally(tween((Motion.SHEET * vm.settings.animFactor).toInt(), easing = Motion.EmphasizedDecelerate)) { it / 2 } +
            fadeIn(tween((Motion.SHEET * vm.settings.animFactor).toInt(), easing = Motion.StandardDecelerate)),
        exit = slideOutHorizontally(tween((Motion.SHEET * vm.settings.animFactor).toInt(), easing = Motion.EmphasizedAccelerate)) { it / 2 } +
            fadeOut(tween((Motion.SHEET * vm.settings.animFactor).toInt(), easing = Motion.StandardAccelerate))
    ) {
        SettingsScreen(vm, host)
    }
}


@Composable
private fun AppMenu(app: AppEntry, expanded: Boolean, onDismiss: () -> Unit, vm: LauncherViewModel, host: Host) {
    val ctx = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Aggiungi alla Home") },
            onClick = {
                onDismiss()
                if (vm.isOnHome(app.id)) {
                    Toast.makeText(ctx, "È già nella Home", Toast.LENGTH_SHORT).show()
                } else {
                    vm.addToHome(app.id)
                    Toast.makeText(ctx, "Aggiunta alla Home", Toast.LENGTH_SHORT).show()
                }
            }
        )
        DropdownMenuItem(
            text = { Text("Trova in Home") },
            onClick = {
                onDismiss()
                if (!vm.locateApp(app.id)) {
                    Toast.makeText(ctx, "Quest'app non è nella Home", Toast.LENGTH_SHORT).show()
                }
            }
        )
        DropdownMenuItem(
            text = { Text("Info app") },
            onClick = {
                onDismiss()
                host.appInfo(app)
            }
        )
        if (app.removable) {
            DropdownMenuItem(
                text = { Text("Disinstalla") },
                onClick = {
                    onDismiss()
                    vm.overlay = Overlay.NONE
                    host.uninstall(app)
                }
            )
        }
    }
}

private fun highlighted(label: String, query: String, color: Color): androidx.compose.ui.text.AnnotatedString {
    val q = query.trim()
    val i = if (q.isEmpty()) -1 else label.indexOf(q, ignoreCase = true)
    return androidx.compose.ui.text.buildAnnotatedString {
        if (i < 0) {
            append(label)
        } else {
            append(label.substring(0, i))
            pushStyle(androidx.compose.ui.text.SpanStyle(color = color, fontWeight = FontWeight.Bold))
            append(label.substring(i, i + q.length))
            pop()
            append(label.substring(i + q.length))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResultCard(app: AppEntry, query: String, vm: LauncherViewModel, host: Host, best: Boolean) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val bounds = remember { BoundsHolder() }
    val iconSize = if (best) 60.dp else 46.dp
    Box {
        Surface(
            shape = RoundedCornerShape(if (best) 28.dp else 22.dp),
            color = if (best) cs.primaryContainer else cs.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    bounds.window = it.boundsInWindow()
                    bounds.root = it.positionInRoot()
                }
                .drawerGestures(app, vm, bounds, onTap = {
                    vm.overlay = Overlay.NONE
                    host.launch(app, bounds.window)
                }, onMenu = { menu = it })
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = if (best) 16.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(app, iconSize, vm.settings.themedIcons)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        highlighted(app.label, query, if (best) cs.onPrimaryContainer else cs.primary),
                        color = if (best) cs.onPrimaryContainer else cs.onSurface,
                        style = if (best) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (best) {
                        Text(
                            "Tocca per aprire",
                            color = cs.onPrimaryContainer.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        AppMenu(app, menu, { menu = false }, vm, host)
    }
}

@Composable
private fun SearchResults(modifier: Modifier, list: List<AppEntry>, query: String, vm: LauncherViewModel, host: Host) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (list.isNotEmpty()) {
            item {
                Text(
                    "Migliore corrispondenza",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }
            item { ResultCard(list.first(), query, vm, host, true) }
            if (list.size > 1) {
                item {
                    Text(
                        "Altre app",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp)
                    )
                }
                listItems(list.drop(1), key = { it.id }) { app -> ResultCard(app, query, vm, host, false) }
            }
        } else {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Nessuna app trovata", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text(
                        "Prova con un altro nome",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cs.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable {
                        vm.overlay = Overlay.NONE
                        host.webSearch(query.trim())
                    }
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = cs.onSecondaryContainer)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "Cerca \u201c${query.trim()}\u201d sul web",
                        color = cs.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySearch(modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = cs.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("Cerca un'app", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant)
    }
}
