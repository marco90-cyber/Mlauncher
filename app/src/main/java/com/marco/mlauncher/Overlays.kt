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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.draw.alpha
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
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

    AnimatedVisibility(
        visible = folder != null && shown != null,
        enter = fadeIn(tween(dur)),
        exit = fadeOut(tween(dur))
    ) {
        if (shown != null) {
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
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { vm.openFolderId = null },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    modifier = Modifier
                        .animateEnterExit(
                            enter = scaleIn(tween(dur), initialScale = 0.7f) + fadeIn(tween(dur)),
                            exit = scaleOut(tween(dur), targetScale = 0.7f) + fadeOut(tween(dur))
                        )
                        .padding(24.dp)
                        .fillMaxWidth()
                        .onGloballyPositioned { cardRect = it.boundsInRoot() }
                        .pointerInput(Unit) { detectTapGestures { } }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        TextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
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
                            modifier = Modifier.fillMaxWidth()
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.heightIn(max = 380.dp),
                            contentPadding = PaddingValues(top = 8.dp)
                        ) {
                            items(shown.appIds, key = { it }) { appId ->
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
                                                        if (Haptics.enabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "Tieni premuta e trascina per riordinare, o fuori per toglierla",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
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
        enter = fadeIn(tween(dur)) + slideInVertically(tween(dur)) { it / 4 },
        exit = fadeOut(tween(dur)) + slideOutVertically(tween(dur)) { it / 4 }
    ) {
        DrawerContent(vm, host, query) { query = it }
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
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val searchOnly = s.homeMode == "all"
    val list = remember(query, vm.sortedApps, vm.hidden.toList()) {
        val visible = vm.sortedApps.filter { it.id !in vm.hidden }
        if (query.isBlank()) visible
        else visible.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    LaunchedEffect(Unit) {
        if (searchOnly) {
            delay(150)
            try {
                focus.requestFocus()
            } catch (e: Exception) {
                // campo non ancora pronto
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = cs.surface.copy(alpha = 0.97f)) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding()
        ) {
            TextField(
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
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
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
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(s.cols),
                        state = gridState,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(list, key = { it.id }) { app -> DrawerApp(app, vm, host) }
                    }
                    if (!searchOnly && query.isBlank() && list.isNotEmpty()) {
                        val letters = remember(list) { list.map { letterOf(it.label) }.distinct() }
                        val firstIndex = remember(list) {
                            val m = LinkedHashMap<Char, Int>()
                            list.forEachIndexed { i, a -> m.getOrPut(letterOf(a.label)) { i } }
                            m
                        }
                        var barH by remember { mutableIntStateOf(1) }
                        val jump: (Float) -> Unit = { y ->
                            val idx = ((y / barH) * letters.size).toInt().coerceIn(0, letters.size - 1)
                            val target = firstIndex[letters[idx]] ?: 0
                            scope.launch { gridState.scrollToItem(target) }
                        }
                        Column(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 12.dp)
                                .width(26.dp)
                                .onSizeChanged { barH = it.height.coerceAtLeast(1) }
                                .pointerInput(letters) { detectTapGestures { o -> jump(o.y) } }
                                .pointerInput(letters) {
                                    detectVerticalDragGestures(
                                        onDragStart = { o -> jump(o.y) },
                                        onVerticalDrag = { c, _ -> jump(c.position.y) }
                                    )
                                },
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            letters.forEach {
                                Text(it.toString(), fontSize = 10.sp, color = cs.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerApp(app: AppEntry, vm: LauncherViewModel, host: Host) {
    var menu by remember { mutableStateOf(false) }
    val bounds = remember { BoundsHolder() }
    val ctx = LocalContext.current
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { bounds.window = it.boundsInWindow() }
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = {
                        vm.overlay = Overlay.NONE
                        host.launch(app, bounds.window)
                    },
                    onLongClick = { menu = true }
                )
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppIcon(app, 52.dp * vm.settings.iconScale, vm.settings.themedIcons)
            Text(
                app.label,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
            )
        }
        AppMenu(app, menu, { menu = false }, vm, host)
    }
}

// =====================================================================
//  Contenitore impostazioni
// =====================================================================

@Composable
fun SettingsOverlay(vm: LauncherViewModel, host: Host) {
    val dur = (280 * vm.settings.animFactor).toInt()
    AnimatedVisibility(
        visible = vm.overlay == Overlay.SETTINGS,
        enter = slideInHorizontally(tween(dur)) { it } + fadeIn(tween(dur)),
        exit = slideOutHorizontally(tween(dur)) { it } + fadeOut(tween(dur))
    ) {
        SettingsScreen(vm, host)
    }
}


@Composable
private fun AppMenu(app: AppEntry, expanded: Boolean, onDismiss: () -> Unit, vm: LauncherViewModel, host: Host) {
    val ctx = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
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
                .onGloballyPositioned { bounds.window = it.boundsInWindow() }
                .combinedClickable(
                    onClick = {
                        vm.overlay = Overlay.NONE
                        host.launch(app, bounds.window)
                    },
                    onLongClick = { menu = true }
                )
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
