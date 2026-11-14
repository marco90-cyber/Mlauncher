package com.marco.mlauncher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

private class Cat(val key: String, val title: String, val summary: String, val icon: ImageVector)

private val shapeNames = mapOf(
    "original" to "Predefinita", "ios" to "iOS", "circle" to "Cerchio", "square" to "Quadrato arrotondato"
) + EXPRESSIVE_SHAPES
private val orderOptions = listOf(
    "name" to "Per nome (A-Z)",
    "name_desc" to "Per nome (Z-A)",
    "usage" to "Più usate per prime",
    "recent" to "Installate di recente",
    "color" to "Per colore dell'icona"
)
private val smartModes = listOf(
    "genre_plus" to "Genere preciso (consigliato)",
    "genre" to "Genere",
    "color" to "Colore dell'icona",
    "letter" to "Lettera iniziale"
)
private val themeNames = mapOf("auto" to "Automatico", "light" to "Chiaro", "dark" to "Scuro")

private fun pageTitle(key: String?) = when (key) {
    "home" -> "Schermata Home"
    "dock" -> "Dock"
    "icons" -> "Notifiche e app nascoste"
    "theme" -> "Tema"
    "material" -> "Personalizza tema"
    "iconstyle" -> "Icone"
    "clock" -> "Orologio"
    "anim" -> "Animazioni"
    "folders" -> "Cartelle"
    "drawer" -> "Cassetto app"
    "widgets" -> "Widget e meteo"
    "minus" -> "Pagina \u22121"
    "search" -> "Ricerca"
    "gestures" -> "Gesti e vibrazione"
    "look" -> "Tema"
    "backup" -> "Backup e manutenzione"
    "wallpaper" -> "Sfondo"
    "info" -> "Informazioni"
    "log" -> "Registro log"
    null -> "Impostazioni"
    else -> if (key.startsWith("mat_")) MAT_ELEMENTS.firstOrNull { it.first == key.removePrefix("mat_") }?.second ?: "Stile" else "Impostazioni"
}

/** Voce delle impostazioni da evidenziare (arrivando dalla ricerca). */
private val LocalSettingsHighlight = androidx.compose.runtime.compositionLocalOf<String?> { null }

/** Lampeggia e porta in vista la voce cercata. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Modifier.hl(title: String): Modifier {
    if (LocalSettingsHighlight.current != title) return this
    val req = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val a = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(380)
        try { req.bringIntoView() } catch (e: Exception) { }
        repeat(2) {
            a.animateTo(1f, androidx.compose.animation.core.tween(260))
            a.animateTo(0.25f, androidx.compose.animation.core.tween(380))
        }
        a.animateTo(0f, androidx.compose.animation.core.tween(700))
    }
    val c = MaterialTheme.colorScheme.primary
    return this
        .bringIntoViewRequester(req)
        .drawBehind { drawRect(c.copy(alpha = 0.28f * a.value)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: LauncherViewModel, host: Host) {
    val cs = MaterialTheme.colorScheme
    val page = vm.settingsPage
    // ogni pagina riparte dall'orientamento reale del telefono
    androidx.compose.runtime.LaunchedEffect(page) { vm.settings.previewLand = null }
    androidx.compose.runtime.LaunchedEffect(vm.settingsHighlight) {
        if (vm.settingsHighlight != null) {
            kotlinx.coroutines.delay(3500)
            vm.settingsHighlight = null
        }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    androidx.compose.runtime.CompositionLocalProvider(LocalSettingsHighlight provides vm.settingsHighlight) {
    Scaffold(
        containerColor = cs.surface,
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(if (page == null && vm.settingsTab == 1) "Stile" else pageTitle(page), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (vm.settingsPage != null) vm.settingsBack() else vm.overlay = Overlay.NONE
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = cs.surface,
                    scrolledContainerColor = cs.surface
                )
            )
        }
    ) { pad ->
        AnimatedContent(
            targetState = page,
            modifier = Modifier.padding(pad),
            transitionSpec = {
                val inSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(Motion.SHEET, easing = Motion.EmphasizedDecelerate)
                val outSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(Motion.SHEET, easing = Motion.EmphasizedAccelerate)
                val fIn = androidx.compose.animation.core.tween<Float>(Motion.SHEET, easing = Motion.StandardDecelerate)
                val fOut = androidx.compose.animation.core.tween<Float>(Motion.FADE, easing = Motion.StandardAccelerate)
                if (targetState != null) {
                    (slideInHorizontally(inSpec) { it / 4 } + fadeIn(fIn)) togetherWith (slideOutHorizontally(outSpec) { -it / 4 } + fadeOut(fOut))
                } else {
                    (slideInHorizontally(inSpec) { -it / 4 } + fadeIn(fIn)) togetherWith (slideOutHorizontally(outSpec) { it / 4 } + fadeOut(fOut))
                }
            },
            label = "settingsPage"
        ) { p ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (p) {
                    null -> Dashboard(vm, host)
                    "home" -> PageHome(vm)
                    "dock" -> PageDock(vm)
                    "icons" -> PageIcons(vm, host)
                    "folders" -> PageFolders(vm)
                    "drawer" -> PageDrawer(vm)
                    "widgets" -> PageWidgets(vm, host)
                    "minus" -> PageMinus(vm, host)
                    "search" -> PageSearch(vm)
                    "gestures" -> PageGestures(vm, host)
                    "look", "theme" -> PageTheme(vm)
                    "material" -> PageMaterial(vm)
                    "mat_dock" -> PageMatDock(vm)
                    "mat_folder" -> PageMatFolder(vm)
                    "mat_indicator" -> PageMatIndicator(vm)
                    "mat_widgets" -> PageMatWidgets(vm)
                    "mat_panels" -> PageMatPanels(vm)
                    "mat_menus" -> PageMatMenus(vm)
                    "mat_drawer" -> PageMatDrawer(vm)
                    "iconstyle" -> PageIconStyle(vm)
                    "clock" -> PageClock(vm, host)
                    "anim" -> PageAnim(vm)
                    "wallpaper" -> PageWallpaper(vm, host)
                    "backup" -> PageBackup(vm, host)
                    "log" -> PageLog(vm)
                    else -> PageInfo(vm, host)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    // ricerca tra tutte le voci delle impostazioni (si può nascondere in Informazioni → Impostazioni)
    androidx.compose.runtime.LaunchedEffect(s.settingsSearch) { if (!s.settingsSearch) vm.settingsQuery = "" }
    if (s.settingsSearch) androidx.compose.material3.TextField(
        value = vm.settingsQuery,
        onValueChange = { vm.settingsQuery = it },
        singleLine = true,
        placeholder = { Text("Cerca nelle impostazioni") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (vm.settingsQuery.isNotEmpty()) IconButton(onClick = { vm.settingsQuery = "" }) {
                Icon(Icons.Default.Close, contentDescription = "Cancella")
            }
        },
        shape = RoundedCornerShape(if (LocalFluent.current) 8.dp else 28.dp),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainerHigh,
            unfocusedContainerColor = cs.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
    val q = vm.settingsQuery.trim()
    if (q.isNotEmpty()) {
        SettingsSearchResults(vm, q)
        return
    }
    if (!vm.isDefaultHome) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.primaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "MLauncher non è la Home predefinita",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onPrimaryContainer
                )
                Text(
                    "Impostala per usare i gesti e il tasto Home con MLauncher.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onPrimaryContainer,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Button(onClick = { host.requestHomeRole() }) { Text("Imposta come predefinita") }
            }
        }
    }
    Group(null) {
        ListItem(
            headlineContent = { Text("Imposta come Home") },
            trailingContent = { Chevron() },
            supportingContent = {
                Text(if (vm.isDefaultHome) "MLauncher è la Home predefinita" else "Tocca per scegliere MLauncher come Home")
            },
            colors = prefColors(),
            modifier = Modifier.clickable { host.requestHomeRole() }
        )
    }
    // due schede, come Nova: come funziona e come appare
    androidx.compose.material3.PrimaryTabRow(selectedTabIndex = vm.settingsTab, containerColor = cs.surface) {
        listOf("Impostazioni", "Stile").forEachIndexed { i, l ->
            androidx.compose.material3.Tab(selected = vm.settingsTab == i, onClick = { vm.settingsTab = i }, text = { Text(l) })
        }
    }
    val matName = { st: String -> MATERIALS.firstOrNull { it.first == st }?.second ?: "" }
    val sections = if (vm.settingsTab == 1) listOf(
        "Aspetto" to listOf(
            Cat("theme", "Tema", "${LauncherThemes.of(s.launcherTheme)?.title ?: "Personalizzato"} \u00b7 ${(themeNames[s.theme] ?: "").lowercase()} \u00b7 colori ${(ACCENTS.firstOrNull { it.first == s.accent }?.second ?: "").lowercase()} \u00b7 interruttori", Icons.Default.Create),
            Cat("wallpaper", "Sfondo", "Immagine, Crea il tuo sfondo, effetti, Android", Icons.Default.Create),
            Cat("material", "Personalizza tema", "${LauncherThemes.of(s.launcherTheme)?.title ?: "Personalizzato"} \u00b7 dock, cartelle, widget, menu e altro", Icons.Default.Star),
            Cat("iconstyle", "Icone", "Forma: ${shapeNames[s.iconShape] ?: ""} \u00b7 nomi" + (if (s.themedIcons) " \u00b7 Material You" else ""), Icons.Default.Star),
            Cat("clock", "Orologio", "Widget orologio: " + (CLOCK_STYLES.firstOrNull { it.first == s.clockStyle }?.second ?: ""), Icons.Default.DateRange),
            Cat("anim", "Animazioni", "Ritorno alla Home, cambio pagina, velocità", Icons.Default.Refresh)
        )
    ) else listOf(
        "Home" to listOf(
            Cat("home", "Schermata Home", "${s.cols}\u00d7${s.rows} \u00b7 " + (if (s.homeMode == "drawer") "con cassetto" else "tutte le app in Home"), Icons.Default.Home),
            Cat("dock", "Dock", "${s.dockSlots} icone \u00b7 grandezza ${(s.dockSize * 100).toInt()}%", Icons.Default.Menu),
            Cat("folders", "Cartelle", "Posizione, grandezza, colonne", Icons.AutoMirrored.Filled.List),
            *(if (s.homeMode == "drawer") arrayOf(
                Cat("drawer", "Cassetto app", "${if (s.drLayout == "list") "Elenco" else "Griglia"} \u00b7 ${s.drCols} colonne", Icons.Default.Menu)
            ) else arrayOf(
                Cat("search", "Ricerca", if (s.searchLook == "minus") "Come la pagina \u22121" else "Classica", Icons.Default.Search)
            )),
            Cat("minus", "Pagina \u22121", if (s.minusOn) "Saluto, ricerca e widget a sinistra della prima pagina" else "Spenta", Icons.Default.DateRange),
            Cat("widgets", "Widget e meteo", "Città del meteo, stack di widget", Icons.Default.Search)
        ),
        "Comportamento" to listOf(
            Cat("gestures", "Gesti e vibrazione", if (s.haptics) "Feedback tattile attivo" else "Feedback tattile spento", Icons.Default.Lock),
            Cat("icons", "Notifiche e app nascoste", "Badge \u00b7 ${vm.hidden.size} app nascoste", Icons.Default.Notifications)
        ),
        "Sistema" to listOf(
            Cat("backup", "Backup e manutenzione", (if (s.autoBackup) "Backup automatico attivo" else "Backup automatico spento") + " \u00b7 esporta, ripristina", Icons.Default.Build),
            Cat("log", "Registro log", if (vm.lastCrash != null) "C'è un errore registrato" else "Eventi ed errori del Launcher", Icons.Default.Warning),
            Cat("info", "Informazioni", vm.updateInfo?.let { "Aggiornamento ${it.version} disponibile" } ?: "MLauncher ${vm.currentVersionName} \u00b7 aggiornamenti", Icons.Default.Info)
        )
    )
    sections.forEach { (title, cats) ->
        Group(title) {
            cats.forEach { c ->
                ListItem(
                    headlineContent = { Text(c.title) },
                    supportingContent = { Text(c.summary) },
                    leadingContent = {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(cs.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Icon(c.icon, contentDescription = null, tint = cs.onPrimaryContainer) }
                    },
                    trailingContent = {
                        // come Android 16: freccia per il sottomenu, separatore e interruttore principale
                        when (c.key) {
                            "gestures" -> NavSwitch(s.haptics) { vm.setSetting("haptics", it) }
                            "minus" -> NavSwitch(s.minusOn) { vm.setSetting("minusOn", it) }
                            "backup" -> NavSwitch(s.autoBackup) {
                                vm.setSetting("autoBackup", it)
                                if (it && s.backupDir.isEmpty()) host.pickBackupFolder()
                            }
                            else -> Chevron()
                        }
                    },
                    colors = prefColors(),
                    modifier = Modifier.clickable { vm.settingsPage = c.key }
                )
            }
        }
    }
}

/** Voci che il generatore dell'indice non riesce a leggere (titolo calcolato). */
private val EXTRA_INDEX = listOf(
    SettingEntry("Cerca aggiornamenti", "info", "Aggiornamenti", "Scarica e installa la nuova versione"),
    SettingEntry("Versione installata", "info", "Aggiornamenti", "")
)

@Composable
private fun SettingsSearchResults(vm: LauncherViewModel, q: String) {
    val cs = MaterialTheme.colorScheme
    val words = q.lowercase().split(' ').filter { it.isNotBlank() }
    val drawerOn = vm.settings.homeMode == "drawer"
    val pages = listOf(
        "home", "dock", "folders", "drawer", "search", "minus", "widgets", "gestures", "icons", "backup", "log", "info",
        "theme", "wallpaper", "material", "iconstyle", "clock", "anim"
    ) + MAT_ELEMENTS.map { "mat_" + it.first }
    val results = remember(q, drawerOn) {
        val pageHits = pages.filter { p -> words.all { pageTitle(p).lowercase().contains(it) } }
            .map { SettingEntry(pageTitle(it), it, "", "Apri la sezione") }
        val hits = (SETTINGS_INDEX + EXTRA_INDEX).filter { e ->
            val hay = (e.title + " " + e.group + " " + e.hint + " " + pageTitle(e.page)).lowercase()
            words.all { hay.contains(it) }
        }.sortedBy { if (it.title.lowercase().startsWith(words.first())) 0 else 1 }
        (pageHits + hits).filter { (drawerOn || it.page != "drawer") && (!drawerOn || it.page != "search") }
    }
    if (results.isEmpty()) {
        Text(
            "Nessuna impostazione trovata",
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
        )
        return
    }
    Group("${results.size} risultati") {
        results.take(40).forEach { e ->
            ListItem(
                headlineContent = { Text(e.title) },
                supportingContent = {
                    Text(
                        pageTitle(e.page) + (if (e.group.isNotEmpty()) " \u203a " + e.group else "") +
                            (if (e.hint.isNotEmpty() && e.hint != "Apri la sezione") "\n" + e.hint else "")
                    )
                },
                colors = prefColors(),
                modifier = Modifier.clickable {
                    vm.settingsHighlight = if (e.hint == "Apri la sezione") null else e.title
                    vm.settingsTab = if (isStylePage(e.page)) 1 else 0
                    vm.settingsPage = e.page
                }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PageLog(vm: LauncherViewModel) {
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    var log by remember { mutableStateOf(AppLog.read()) }
    // cosa vedere: 0 tutto, 1 solo errori, 2 solo velocità
    var filter by remember { mutableStateOf(0) }
    var askClear by remember { mutableStateOf(false) }
    val crash = vm.lastCrash
    val head = remember(log) { MainWatchdog.deviceInfo(ctx) }
    val speed = remember(log) { Perf.todaySummary() }
    val allEntries = remember(log) { AppLog.entries(log) }
    val shown = remember(log, filter) {
        when (filter) {
            1 -> allEntries.filter { AppLog.isError(it) }
            2 -> allEntries.filter { AppLog.isSpeed(it) }
            else -> allEntries
        }
    }
    // Copia e Invia mandano quello che si sta guardando
    val text = "=== DISPOSITIVO ===\n$head\n\n" +
        (if (filter != 1) "=== VELOCITÀ DI OGGI ===\n$speed\n\n" else "") +
        (if (filter != 2 && crash != null) "=== ULTIMO ERRORE ===\n$crash\n\n" else "") +
        "=== REGISTRO" + (when (filter) { 1 -> " (solo errori)"; 2 -> " (solo velocità)"; else -> "" }) + " ===\n" +
        shown.joinToString("\n") { it.trimEnd() }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.FilledTonalButton(onClick = { log = AppLog.read(); vm.loadCrash() }) { Text("Aggiorna") }
        androidx.compose.material3.FilledTonalButton(onClick = {
            clip.setText(androidx.compose.ui.text.AnnotatedString(text))
            android.widget.Toast.makeText(ctx, "Copiato", android.widget.Toast.LENGTH_SHORT).show()
        }) { Text("Copia") }
        androidx.compose.material3.FilledTonalButton(onClick = {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_TEXT, text)
            try { ctx.startActivity(android.content.Intent.createChooser(i, "Condividi registro")) } catch (e: Exception) { AppLog.quiet("condivisione del registro", e) }
        }) { Text("Invia") }
        androidx.compose.material3.FilledTonalButton(
            onClick = { askClear = true },
            colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                containerColor = cs.errorContainer,
                contentColor = cs.onErrorContainer
            )
        ) { Text("Cancella") }
    }
    // scelta di cosa vedere
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        listOf("Tutto", "Errori", "Velocità").forEachIndexed { i, l ->
            SegmentedButton(
                selected = filter == i,
                onClick = { filter = i },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, 3)
            ) { Text(l) }
        }
    }
    Text(
        "Il registro tiene gli ultimi ${AppLog.KEEP_DAYS} giorni: gli eventi più vecchi si cancellano da soli.",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp)
    )
    if (filter == 2) Group("Velocità di oggi") {
        Text(
            speed,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = cs.onSurface,
            modifier = Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(12.dp)
        )
    }
    if (filter != 2 && crash != null) Group("Ultimo errore") {
        Text(
            crash.take(6000),
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color = cs.onErrorContainer,
            modifier = Modifier.fillMaxWidth().background(cs.errorContainer).padding(14.dp)
        )
    }
    val entries = remember(shown) { shown.reversed().take(400) }
    Group(
        if (entries.isEmpty()) (if (filter == 0) "Registro vuoto" else "Niente da mostrare")
        else "Eventi, dal più recente"
    ) {
        Column(Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(12.dp)) {
            if (entries.isEmpty()) Text(
                when (filter) {
                    1 -> "Nessun errore registrato."
                    2 -> "Nessuna misura di velocità registrata."
                    else -> "Ancora nessun evento registrato."
                },
                color = cs.onSurfaceVariant
            )
            entries.forEach { line ->
                val lvl = AppLog.level(line)
                Text(
                    line.trimEnd(),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = when (lvl) {
                        "E" -> cs.error
                        "W" -> cs.tertiary
                        else -> cs.onSurface
                    },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
    if (askClear) androidx.compose.material3.AlertDialog(
        onDismissRequest = { askClear = false },
        title = { Text("Cancellare il registro?") },
        text = { Text("Si svuotano tutti gli eventi e l'ultimo errore.") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                AppLog.clear()
                vm.clearCrash()
                log = ""
                askClear = false
            }) { Text("Cancella", color = cs.error) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askClear = false }) { Text("Annulla") } }
    )
}

@Composable
private fun PageWallpaper(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(vm.wallError) {
        if (vm.wallError > 0) android.widget.Toast.makeText(
            ctx, "Immagine non disponibile. Android a volte non lascia leggere il suo sfondo: sceglilo dalla galleria", android.widget.Toast.LENGTH_LONG
        ).show()
    }
    run {
        val bm = vm.wallBitmap
        if (bm != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Image(
                bm, contentDescription = "Sfondo attuale",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(120.dp, 250.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(22.dp))
            )
        }
        Group("Scegli l'immagine") {
            if (bm != null) ListItem(
                headlineContent = { Text("Modifica lo sfondo attuale") },
                trailingContent = { Chevron() },
                supportingContent = { Text("Ritaglia, ruota, regola luce e colori, filtri") },
                colors = prefColors(),
                modifier = Modifier.hl("Modifica lo sfondo attuale").clickable { vm.editCurrentWallpaper() }
            )
            ListItem(
                headlineContent = { Text(if (vm.wallBusy) "Preparo lo sfondo\u2026" else "Dalla galleria") },
                supportingContent = { Text("Una tua foto: poi la ritagli e la regoli") },
                colors = prefColors(),
                modifier = Modifier.clickable { host.pickWallpaperImage() }
            )
            ListItem(
                headlineContent = { Text("Importa lo sfondo di Android") },
                supportingContent = { Text("Copia quello che hai adesso, se il sistema lo permette") },
                colors = prefColors(),
                modifier = Modifier.clickable { vm.importSystemWallpaper() }
            )
            Column(Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(12.dp)) {
                Text("Sfondi inclusi", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Text(
                    "Tocca per usarlo, tieni premuto per personalizzarlo",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(8.dp))
                var menuFor by remember { mutableStateOf<GenWall?>(null) }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // crea
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { vm.newMyWall() }) {
                        Box(
                            Modifier.size(60.dp, 120.dp).clip(RoundedCornerShape(14.dp)).background(cs.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Add, contentDescription = null, tint = cs.onSecondaryContainer) }
                        Text("Crea", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                    // i tuoi
                    vm.myWalls.forEach { g ->
                        val thumb = remember(g) { genWall(g, 90, 180).asImageBitmap() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(60.dp).pointerInput(g.id) {
                                detectTapGestures(
                                    onTap = { vm.applyMyWall(g) },
                                    onLongPress = { menuFor = g }
                                )
                            }
                        ) {
                            androidx.compose.foundation.Image(
                                thumb, contentDescription = g.name,
                                modifier = Modifier.size(60.dp, 120.dp).clip(RoundedCornerShape(14.dp))
                                    .border(2.dp, cs.primary, RoundedCornerShape(14.dp))
                            )
                            Text(g.name, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    BUILTIN_WALLS.forEach { (k, l) ->
                        val thumb = remember(k) { builtinWall(k, 90, 180).asImageBitmap() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.pointerInput(k) {
                                detectTapGestures(
                                    onTap = { vm.builtinWallpaper(k) },
                                    onLongPress = { vm.genEdit = builtinGen(k, l) }
                                )
                            }
                        ) {
                            androidx.compose.foundation.Image(
                                thumb, contentDescription = l,
                                modifier = Modifier.size(60.dp, 120.dp).clip(RoundedCornerShape(14.dp))
                            )
                            Text(l, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                menuFor?.let { g ->
                    AlertDialog(
                        onDismissRequest = { menuFor = null },
                        title = { Text(g.name) },
                        text = { Text("Vuoi modificarlo o eliminarlo?") },
                        confirmButton = { TextButton(onClick = { menuFor = null; vm.genEdit = g }) { Text("Modifica") } },
                        dismissButton = {
                            TextButton(onClick = { menuFor = null; vm.deleteMyWall(g.id) }) {
                                Text("Elimina", color = cs.error)
                            }
                        }
                    )
                }
            }
        }
        run {
            Group("Movimento") { Dependent(bm != null) {
                SwitchRow("Parallasse", "Lo sfondo scorre piano quando cambi pagina", s.wallParallax) { vm.setSetting("wallParallax", it) }
                Text(
                    "Sfoca, scurisci e gli altri effetti sono in \u201cModifica lo sfondo attuale\u201d \u2192 Effetti.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp)
                )
            } }
            Group("Android") { Dependent(bm != null) {
                var askAndroid by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = { Text("Imposta come sfondo di Android") },
                    supportingContent = { Text("Usa questa immagine anche fuori da MLauncher: schermata di blocco, Home di sistema o entrambe") },
                    trailingContent = { Chevron() },
                    colors = prefColors(),
                    modifier = Modifier.hl("Imposta come sfondo di Android").clickable { askAndroid = true }
                )
                if (askAndroid) {
                    AlertDialog(
                        onDismissRequest = { askAndroid = false },
                        title = { Text("Dove vuoi usarlo?") },
                        text = {
                            Column {
                                listOf(
                                    "lock" to "Schermata di blocco",
                                    "home" to "Home di Android (dietro le altre app e launcher)",
                                    "both" to "Entrambe"
                                ).forEach { (k, l) ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                            askAndroid = false
                                            val ok = vm.wallpaperToAndroid(k)
                                            android.widget.Toast.makeText(ctx, if (ok) "Sfondo di Android impostato" else "Non riuscito", android.widget.Toast.LENGTH_SHORT).show()
                                        }.padding(vertical = 12.dp, horizontal = 4.dp)
                                    ) { Text(l) }
                                }
                                Text(
                                    "Con i colori \u201cDal sistema\u201d anche la tavolozza di Android si adeguer\u00e0 alla nuova immagine.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { askAndroid = false }) { Text("Annulla") } }
                    )
                }
            } }
        }
    }
}

/** Gruppo di voci che hanno un valore per il verticale e uno per l'orizzontale. */
@Composable
private fun OrientedGroup(title: String, s: LauncherSettings, content: @Composable () -> Unit) {
    Group(title) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(top = 12.dp, bottom = 6.dp)
        ) {
            Text(
                "Queste voci hanno valori separati per il telefono in verticale e in orizzontale",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
            OrientationTabs(s)
        }
        content()
    }
}

/** Scelta dell'orientazione di cui si stanno modificando i valori. */
@Composable
private fun OrientationTabs(s: LauncherSettings) {
    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.FilterChip(
            selected = !s.landscape,
            onClick = { s.previewLand = false },
            label = { Text("Verticale") },
            modifier = Modifier.weight(1f)
        )
        androidx.compose.material3.FilterChip(
            selected = s.landscape,
            onClick = { s.previewLand = true },
            label = { Text("Orizzontale") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PageHome(vm: LauncherViewModel) {
    val s = vm.settings
    Group("Pagine") {
        ChoiceRow(
            "Pagina principale",
            (0 until vm.pageCount).map { it.toString() to "Pagina " + (it + 1) },
            vm.homePageIdx.toString()
        ) { vm.setHomePage(it.toInt()) }
        ListItem(
            headlineContent = { Text("Anteprima pagine") },
            supportingContent = { Text("Pizzica la Home con due dita: sfondi, widget, nuove pagine e pagina principale") },
            trailingContent = { Chevron() },
            colors = prefColors(),
            modifier = Modifier.hl("Anteprima pagine").clickable { vm.overlay = Overlay.NONE; vm.showOverview = true }
        )
    }
    Group("Disposizione delle app") {
        SwitchRow(
            "Blocca la Home",
            "Icone, cartelle e widget non si possono spostare né togliere. Si sblocca dall'anteprima pagine (pizzica la Home)",
            s.lockHome
        ) { vm.setSetting("lockHome", it) }
        var askOrder by remember { mutableStateOf(false) }
        ChoiceRow(
            "Modalità Home",
            listOf("drawer" to "Home e cassetto app", "all" to "Tutte le app in Home"),
            s.homeMode
        ) {
            vm.setSetting("homeMode", it)
            if (it == "all") askOrder = true
        }
        if (s.homeMode == "all") {
            SwitchRow(
                "Cartelle intelligenti",
                "Raggruppa le app in cartelle. Le app che non rientrano in nessun gruppo restano in Home, senza cartelle generiche",
                s.autoFolders
            ) { vm.setSetting("autoFolders", it) }
            Dependent(s.autoFolders) { ChoiceRow("Raggruppa per", smartModes, s.smartMode) { vm.setSetting("smartMode", it) } }
            ChoiceRow(if (s.autoFolders) "Come disporre le app sciolte" else "Come disporre le app", orderOptions, s.homeOrder) { vm.setSetting("homeOrder", it) }
        } else {
            SwitchRow(
                "Aggiungi le nuove app in Home",
                "Altrimenti restano solo nel cassetto",
                s.autoAddNew
            ) { vm.setSetting("autoAddNew", it) }
        }
        if (askOrder) {
            AlertDialog(
                onDismissRequest = { askOrder = false },
                title = { Text("Come vuoi le app in Home?") },
                text = {
                    Column {
                        orderOptions.forEach { (k, l) ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    vm.setSetting("homeOrder", k)
                                    askOrder = false
                                }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = k == s.homeOrder, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(l)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { askOrder = false }) { Text("Chiudi") } }
            )
        }
    }
    OrientedGroup("Griglia e icone", s) {
        SliderRow("Colonne", "${s.cols}", s.cols.toFloat(), if (s.landscape) 5f..10f else 4f..6f, if (s.landscape) 4 else 1, preview = { GridPreview(s.cols, s.rows) }) {
            val v = it.roundToInt()
            if (v != s.cols) vm.setSetting("cols", v)
        }
        SliderRow("Righe", "${s.rows}", s.rows.toFloat(), if (s.landscape) 2f..5f else 5f..8f, 2, preview = { GridPreview(s.cols, s.rows) }) {
            val v = it.roundToInt()
            if (v != s.rows) vm.setSetting("rows", v)
        }
        SliderRow("Dimensione icone", "${(s.iconScale * 100).roundToInt()}%", s.iconScale, 0.8f..1.2f, 0, preview = { ShapePreview(s.iconShape, 30.dp * s.iconScale) }) {
            vm.setSetting("iconScale", (it * 20).roundToInt() / 20f)
        }
    }
    Group("Schermo") {
        SwitchRow(
            "Rotazione della Home",
            "In orizzontale icone e widget restano nei posti corrispondenti, con la griglia orizzontale; la dock passa a lato. Spento: la Home resta in verticale",
            s.allowRotation
        ) { vm.setSetting("allowRotation", it) }
        StyleLink(vm, "Nomi delle app, forma delle icone", "iconstyle")
        StyleLink(vm, "Indicatore di pagina", "mat_indicator")
    }
}

@Composable
private fun PageDock(vm: LauncherViewModel) {
    val s = vm.settings
    Group(null) { StyleLink(vm, "Aspetto della dock", "mat_dock") }
    OrientedGroup("Dimensioni", s) {
        SliderRow("Grandezza", "${(s.dockSize * 100).roundToInt()}%", s.dockSize, 0.8f..1.2f, 7) {
            vm.setSetting("dockSize", (it * 20).roundToInt() / 20f)
        }
        SliderRow("Icone nella dock", "${s.dockSlots}", s.dockSlots.toFloat(), 4f..6f, 1) {
            val v = it.roundToInt()
            if (v != s.dockSlots) vm.setSetting("dockSlots", v)
        }
    }
}

@Composable
private fun PageIcons(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    Group("Notifiche") {
        ChoiceRow(
            "Badge delle notifiche",
            listOf("off" to "Disattivati", "dot" to "Puntino", "count" to "Numero"),
            s.badges,
            preview = { BadgePreview(it) }
        ) {
            vm.setSetting("badges", it)
            if (it != "off" && !vm.listenerEnabled) host.openListenerSettings()
        }
        if (s.badges != "off" && !vm.listenerEnabled) {
            ListItem(
                headlineContent = { Text("Serve l'accesso alle notifiche") },
                trailingContent = { Chevron() },
                supportingContent = { Text("Tocca per attivarlo per MLauncher") },
                colors = prefColors(),
                modifier = Modifier.clickable { host.openListenerSettings() }
            )
        }
    }
    Group("App") {
        var hiddenOpen by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("App nascoste") },
            trailingContent = { Chevron() },
            supportingContent = { Text("${vm.hidden.size} nascoste") },
            colors = prefColors(),
            modifier = Modifier.clickable { hiddenOpen = true }
        )
        if (hiddenOpen) {
            AlertDialog(
                onDismissRequest = { hiddenOpen = false },
                title = { Text("App nascoste") },
                text = {
                    Column {
                        if (vm.hidden.isEmpty()) Text("Nessuna app nascosta")
                        vm.hidden.toList().forEach { id ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(vm.apps[id]?.label ?: id, Modifier.weight(1f))
                                TextButton(onClick = { vm.unhideApp(id) }) { Text("Mostra") }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { hiddenOpen = false }) { Text("Chiudi") } }
            )
        }
    }
}

@Composable
private fun PageDrawer(vm: LauncherViewModel) {
    val s = vm.settings
    Group("Disposizione") {
        ChoiceRow(
            "Disposizione",
            listOf("grid" to "Griglia", "list" to "Elenco"),
            s.drLayout
        ) { vm.setSetting("drLayout", it) }
        ChoiceRow(
            "Ordine delle app",
            listOf("name" to "Per nome (A-Z)", "name_desc" to "Per nome (Z-A)", "usage" to "Più usate", "recent" to "Installate di recente"),
            s.drSort
        ) { vm.setSetting("drSort", it) }
        SwitchRow("App suggerite", "Le 4 più usate in cima", s.drSuggest) { vm.setSetting("drSuggest", it) }
        SwitchRow("Ricorda la posizione", "Riapre il cassetto dove l'avevi lasciato", s.drRemember) { vm.setSetting("drRemember", it) }
    }
    Group("Ricerca e scorrimento") {
        SwitchRow("Barra di ricerca", "In cima al cassetto", s.drSearchBar) { vm.setSetting("drSearchBar", it) }
        Dependent(s.drSearchBar) { SwitchRow("Sfondo della barra di ricerca", null, s.drSearchBg) { vm.setSetting("drSearchBg", it) } }
        SwitchRow("Barra laterale A-Z", "Scorri veloce tra le lettere", s.drScrollbar) { vm.setSetting("drScrollbar", it) }
    }
    Group("Nomi delle app") {
        SwitchRow("Mostra i nomi", null, s.drLabels) { vm.setSetting("drLabels", it) }
        Dependent(s.drLabels) {
            SliderRow("Dimensione del testo", "${s.drLabelSize} sp", s.drLabelSize.toFloat(), 9f..16f, 6) {
                val v = it.roundToInt()
                if (v != s.drLabelSize) vm.setSetting("drLabelSize", v)
            }
            SwitchRow("Nome su due righe", null, s.drTwoLine) { vm.setSetting("drTwoLine", it) }
        }
        SwitchRow(
            "Puntino sulle app nuove",
            "Segna le app appena installate finché non le apri",
            s.newAppDot
        ) { vm.setSetting("newAppDot", it) }
    }
    Group("Sfondo") { StyleLink(vm, "Aspetto del cassetto", "mat_drawer") }
    OrientedGroup("Griglia e spaziatura", s) {
        Dependent(s.drLayout == "grid") {
            SliderRow("Colonne", "${s.drCols}", s.drCols.toFloat(), 3f..6f, 2) {
                val v = it.roundToInt()
                if (v != s.drCols) vm.setSetting("drCols", v)
            }
        }
        SliderRow("Dimensione icone", "${(s.drIcon * 100).roundToInt()}%", s.drIcon, 0.7f..1.4f, 13) {
            vm.setSetting("drIcon", (it * 20).roundToInt() / 20f)
        }
        SliderRow("Altezza delle righe", "${(s.drRowH * 100).roundToInt()}%", s.drRowH, 0.6f..1.6f, 9) {
            vm.setSetting("drRowH", (it * 20).roundToInt() / 20f)
        }
        SliderRow("Rientro laterale", "${s.drIndent} dp", s.drIndent.toFloat(), 0f..32f, 0) {
            val v = it.roundToInt()
            if (v != s.drIndent) vm.setSetting("drIndent", v)
        }
        SliderRow("Margine superiore", "${s.drTop} dp", s.drTop.toFloat(), 0f..48f, 0) {
            val v = it.roundToInt()
            if (v != s.drTop) vm.setSetting("drTop", v)
        }
    }
}

@Composable
private fun PageFolders(vm: LauncherViewModel) {
    val s = vm.settings
    Group(null) { StyleLink(vm, "Aspetto delle cartelle", "mat_folder") }
    Group("Cartella aperta") {
        ChoiceRow(
            "Posizione",
            listOf("center" to "Al centro dello schermo (HyperOS, iOS)", "icon" to "Vicino all'icona (Lawnchair, Pixel)"),
            s.folderPos
        ) { vm.setSetting("folderPos", it) }
        ChoiceRow(
            "Grandezza",
            listOf("compact" to "Compatta", "large" to "Grande (quasi tutto lo schermo)"),
            s.folderSize
        ) { vm.setSetting("folderSize", it) }
        SliderRow("Colonne", "${s.folderCols}", s.folderCols.toFloat(), 3f..5f, 1) {
            val v = it.roundToInt()
            if (v != s.folderCols) vm.setSetting("folderCols", v)
        }
        ListItem(
            headlineContent = { Text("Ordina A-Z") },
            supportingContent = { Text("Tieni premuta una cartella nella Home e scegli \"Ordina A-Z\". Trascina le icone per riordinarle a mano.") },
            colors = prefColors()
        )
    }
}

/** Anteprima della sola forma: grande, piena e bordata, così le forme si distinguono a colpo d'occhio. */
@Composable
private fun FolderShapePreview(shapeKey: String) {
    val cs = MaterialTheme.colorScheme
    val shape = folderShapeOf(shapeKey)
    Box(
        Modifier
            .size(40.dp)
            .clip(shape)
            .background(cs.primary)
            .border(2.dp, cs.primary, shape),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(2) { Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(cs.onPrimary.copy(alpha = 0.9f))) }
                }
            }
        }
    }
}

@Composable
private fun FolderPreview(style: String, bgKey: String, shapeKey: String = "squircle") {
    val cs = MaterialTheme.colorScheme
    val bg = when (bgKey) {
        "primary" -> cs.primaryContainer
        "dark" -> Color.Black.copy(alpha = 0.45f)
        "glass" -> Color.White.copy(alpha = 0.5f)
        else -> cs.secondaryContainer
    }
    val shp = folderShapeOf(shapeKey)
    Box(Modifier.size(34.dp).clip(shp).background(bg).border(1.dp, cs.primary.copy(alpha = 0.45f), shp).padding((34 * folderShapeInset(shapeKey)).dp)) {
        when (style) {
            "grid2" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                repeat(2) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(2) { Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(cs.primary)) }
                } }
            }
            "stack" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                repeat(3) { i ->
                    val k = 2 - i
                    Box(
                        Modifier.offset(x = (2 * k).dp, y = (-2 * k).dp).size(14.dp)
                            .graphicsLayer { rotationZ = k * 8f }
                            .clip(RoundedCornerShape(4.dp)).background(cs.primary.copy(alpha = 1f - 0.25f * k))
                    )
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                repeat(3) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(3) { Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(cs.primary)) }
                } }
            }
        }
    }
}

@Composable
private fun PageWidgets(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    var cityDlg by remember { mutableStateOf(false) }
    if (cityDlg) CityPickerDialog(vm, host) { cityDlg = false }
    Group("Meteo") {
        ListItem(
            headlineContent = { Text("Città del meteo") },
            trailingContent = { Chevron() },
            supportingContent = {
                Text(vm.weatherCity?.let { c -> c.name + (if (c.area.isNotEmpty()) " \u00b7 " + c.area else "") } ?: "Posizione del telefono")
            },
            colors = prefColors(),
            modifier = Modifier.clickable { cityDlg = true }
        )
    }
    Group("Orologio") { StyleLink(vm, "Stile del widget orologio", "clock") }
    Group("Ricerca") {
        Text(
            "La barra di ricerca è un widget: pizzica la Home → Widget → Barra di ricerca. Puoi spostarla e ridimensionarla come gli altri.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
    Group("Widget") {
        SwitchRow(
            "Rotazione automatica degli stack",
            "I widget impilati scorrono da soli",
            s.stackAutoRotate
        ) { vm.setSetting("stackAutoRotate", it) }
    }
}

@Composable
private fun PageGestures(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    Group("Gesti") {
        SwitchRow(
            "Scuoti per riordinare",
            "In modifica Home, scuoti il telefono per compattare la pagina dall'alto in basso",
            s.shakeTidy
        ) { vm.setSetting("shakeTidy", it) }
        SwitchRow(
            "Tasto Home apre il cassetto",
            "Se sei già sulla pagina principale della Home",
            s.homeOpensDrawer
        ) { vm.setSetting("homeOpensDrawer", it) }
        ChoiceRow(
            "Scorri giù nella Home",
            listOf("notifications" to "Apri le notifiche", "search" to "Cerca app"),
            s.swipeDown
        ) { vm.setSetting("swipeDown", it) }
        ChoiceRow(
            "Scorri su nella Home",
            if (s.homeMode == "drawer") listOf("open" to "Apri il cassetto app", "search" to "Cerca app (con la tastiera)", "none" to "Niente")
            else listOf("open" to "Apri la ricerca", "search" to "Apri la ricerca con la tastiera", "none" to "Niente"),
            s.swipeUp
        ) { vm.setSetting("swipeUp", it) }
        SwitchRow(
            "Doppio tocco per bloccare",
            "Tocca due volte uno spazio vuoto della Home",
            s.doubleTapLock
        ) {
            vm.setSetting("doubleTapLock", it)
            if (it && !vm.accessibilityEnabled) host.openAccessibilitySettings()
        }
        if (s.doubleTapLock && !vm.accessibilityEnabled) {
            ListItem(
                headlineContent = { Text("Serve il servizio di accessibilità") },
                trailingContent = { Chevron() },
                supportingContent = { Text("Tocca per attivare MLauncher") },
                colors = prefColors(),
                modifier = Modifier.clickable { host.openAccessibilitySettings() }
            )
        }
    }
    Group("Vibrazione") {
        SwitchRow(
            "Feedback tattile",
            "Interruttore generale",
            s.haptics
        ) { vm.setSetting("haptics", it) }
    }
    Group("Quando vibrare") { Dependent(s.haptics) {
        SwitchRow(
            "Pressione lunga e trascinamento",
            "Menu rapidi, inizio spostamento di icone e widget",
            s.hapPress
        ) { vm.setSetting("hapPress", it) }
        SwitchRow(
            "Rilascio e conferme",
            "Icona posata, cartella creata, widget aggiunto, azione non possibile",
            s.hapDrop
        ) { vm.setSetting("hapDrop", it) }
        SwitchRow(
            "A ogni pagina",
            "Un tocco leggero quando cambi pagina nella Home",
            s.hapPage
        ) { vm.setSetting("hapPage", it) }
        SwitchRow(
            "Scorrimento e selezione",
            "Ridimensionamento, lista del cassetto, gesti su e giù",
            s.hapTick
        ) { vm.setSetting("hapTick", it) }
    } }
}

/** Titolo di una sezione fuori dai gruppi (es. sopra le schede dei temi). */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 2.dp)
    )
}

/**
 * Stile → Tema, come la pagina «Temi» di Expressive Launcher: tre schede con la mini Home
 * (Liquid Glass, Vetro, Material 3 Expressive, Tonale, Material You Lite, Fluent, Nessuno), poi le impostazioni del tema scelto
 * e la modalità chiara o scura.
 */
@Composable
private fun PageTheme(vm: LauncherViewModel) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    val spec = LauncherThemes.of(s.launcherTheme)
    SectionTitle("Scegli il tema")
    ThemeCards(s.launcherTheme, s) { vm.applyLauncherTheme(it.key) }
    if (spec == null) {
        Text(
            "Ora usi una combinazione tua (materiale, icone e interruttori scelti a mano). Tocca un tema per applicarlo: " +
                "poi puoi cambiare ancora ogni dettaglio.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
    Group(if (spec != null) "Impostazioni del tema ${spec.title}" else "Colori") {
        ChoiceRow("Colori", ACCENTS, s.accent, preview = { AccentPreview(it) }) { vm.setSetting("accent", it) }
        when (spec?.key) {
            "liquid" -> {
                // si mostra la trasparenza, si salva la densità del velo
                SliderRow("Trasparenza del vetro", "${100 - s.matOpacity}%", s.matOpacity.toFloat(), 0f..100f, 19) {
                    val v = (it / 5f).roundToInt() * 5
                    if (v != s.matOpacity) vm.setSetting("matOpacity", v)
                }
                SliderRow("Forza dei riflessi", "${s.glassHighlight}%", s.glassHighlight.toFloat(), 20f..120f, 9) {
                    val v = (it / 10f).roundToInt() * 10
                    if (v != s.glassHighlight) vm.setSetting("glassHighlight", v)
                }
            }
            "expressive" -> {
                ChoiceRow("Forma delle icone", EXPRESSIVE_SHAPES, s.iconShape, preview = { IconShapeSwatch(it) }) { vm.setSetting("iconShape", it) }
                SwitchRow(
                    "Icone Material You",
                    "Tutte le icone con i colori del tema: il glifo monocromatico dove l'app lo ha, le altre ricolorate in due toni",
                    s.themedIcons
                ) { vm.setSetting("themedIcons", it) }
                Dependent(s.themedIcons) {
                    ChoiceRow("Colore delle icone", THEMED_ICON_COLORS, s.themedIconColor, preview = { IconShapeSwatch(s.iconShape, colorKey = it) }) {
                        vm.setSetting("themedIconColor", it)
                    }
                }
            }
            "lite" -> {
                SwitchRow("Icone Material You", "Icone con i colori del tema", s.themedIcons) { vm.setSetting("themedIcons", it) }
                Text(
                    "Material You Lite: pannelli tonali, angoli tutti uguali e nessun effetto di vetro. Il più leggero per la batteria.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            "glass" -> {
                SliderRow("Trasparenza del vetro", "${100 - s.matOpacity}%", s.matOpacity.toFloat(), 0f..100f, 19) {
                    val v = (it / 5f).roundToInt() * 5
                    if (v != s.matOpacity) vm.setSetting("matOpacity", v)
                }
                SwitchRow("Riflesso", "Velo luminoso in alto e bordo chiaro, come il vetro", s.matGlass) { vm.setSetting("matGlass", it) }
            }
            "tonal" -> {
                SwitchRow("Icone Material You", "Tutte le icone con i colori del tema", s.themedIcons) { vm.setSetting("themedIcons", it) }
                Dependent(s.themedIcons) {
                    ChoiceRow("Colore delle icone", THEMED_ICON_COLORS, s.themedIconColor, preview = { IconShapeSwatch(s.iconShape, colorKey = it) }) {
                        vm.setSetting("themedIconColor", it)
                    }
                }
            }
            "fluent" -> {
                SliderRow("Trasparenza (acrilico)", "${100 - s.matOpacity}%", s.matOpacity.toFloat(), 0f..100f, 19) {
                    val v = (it / 5f).roundToInt() * 5
                    if (v != s.matOpacity) vm.setSetting("matOpacity", v)
                }
                Text(
                    "Fluent: grigi neutri, bordo sottile e angoli piccoli come Windows 11 (e MLMeteo), anche qui nelle impostazioni.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            "none" -> {
                Text(
                    "Nessuno: dock, cartelle, indicatore e widget senza pannello, direttamente sullo sfondo. " +
                        "Puoi ridare un materiale a un singolo elemento in Personalizza tema.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
    if (spec?.key == "liquid") {
        Group("Icone") {
            ChoiceRow(
                "Riflessi sulle icone",
                listOf("off" to "Nessuno", "corners" to "Agli angoli (come iOS 26)", "full" to "Su tutto il bordo"),
                s.glassIconHighlight
            ) { vm.setSetting("glassIconHighlight", it) }
            ChoiceRow(
                "Stile delle icone",
                listOf("normal" to "Normali", "clear" to "Trasparenti (logo su vetro)", "tinted" to "Colorate (glifo su vetro scuro)"),
                s.glassIconStyle
            ) { vm.setSetting("glassIconStyle", it) }
            ChoiceRow("Maschera delle icone", ICON_SHAPES.filter { it.first != "original" }, s.iconShape, preview = { ShapePreview(it, 30.dp) }) {
                vm.setSetting("iconShape", it)
            }
            StyleLink(vm, "Pacchetto icone e nomi delle app", "iconstyle")
        }
    }
    Group("Modalità") {
        ChoiceRow(
            "Chiaro o scuro",
            listOf("auto" to "Automatico", "light" to "Chiaro", "dark" to "Scuro"),
            s.theme,
            preview = { ThemePreview(it) }
        ) { vm.setSetting("theme", it) }
    }
    Group("Altro") {
        ChoiceRow(
            "Stile degli interruttori",
            SWITCH_STYLES,
            s.switchStyle,
            preview = { SwitchStylePreview(it) }
        ) { vm.setSetting("switchStyle", it) }
        StyleLink(vm, "Personalizza tema", "material")
    }
}

@Composable
private fun PageAnim(vm: LauncherViewModel) {
    val s = vm.settings
    Group("Ritorno alla Home") {
        ChoiceRow("Animazione", RETURN_ANIMS, s.returnAnim) { vm.setSetting("returnAnim", it) }
        Dependent(s.returnAnim != "none") {
        SwitchRow("Quando torni da un'app", null, s.returnOnApp) { vm.setSetting("returnOnApp", it) }
        SwitchRow("Quando sblocchi il telefono", null, s.returnOnUnlock) { vm.setSetting("returnOnUnlock", it) }
        SwitchRow("Rimbalzo dell'icona", "L'icona dell'app appena chiusa fa un piccolo salto", s.returnBounce) { vm.setSetting("returnBounce", it) }
        SwitchRow("Zoom dello sfondo", "Lo sfondo si allontana leggermente mentre le icone si assestano", s.returnWallZoom) { vm.setSetting("returnWallZoom", it) }
        ListItem(
            headlineContent = { Text("Prova l'animazione") },
            supportingContent = { Text("Chiude le impostazioni e la mostra sulla Home") },
            colors = prefColors(),
            modifier = Modifier.clickable {
                vm.overlay = Overlay.NONE
                vm.playReturn(fromApp = false, force = true)
            }
        )
        }
    }
    Group("Pagine") {
        ChoiceRow(
            "Effetto cambio pagina",
            listOf(
                "slide" to "Scorrimento",
                "fade" to "Dissolvenza",
                "cube" to "Cubo",
                "cascade" to "Cascata"
            ),
            s.pageEffect,
            preview = { EffectPreview(it) }
        ) { vm.setSetting("pageEffect", it) }
    }
    Group("Animazioni") {
        ChoiceRow(
            "Velocità animazioni",
            listOf("0" to "Rilassata", "1" to "Bilanciata", "2" to "Veloce"),
            s.animSpeed.toString()
        ) { vm.setSetting("animSpeed", it.toInt()) }
        ChoiceRow(
            "Animazione di disinstallazione",
            listOf("boom" to "Esplosione", "fade" to "Dissolvenza", "none" to "Nessuna"),
            s.uninstallAnim
        ) { vm.setSetting("uninstallAnim", it) }
    }
}

@Composable
private fun PageSearch(vm: LauncherViewModel) {
    val s = vm.settings
    Group("Aspetto") {
        ChoiceRow(
            "Stile della ricerca",
            listOf("minus" to "Come la pagina \u22121 (Home sfocata e contenitori)", "classic" to "Classico (sfondo pieno)"),
            s.searchLook
        ) { vm.setSetting("searchLook", it) }
        StyleLink(vm, "Materiale, sfocatura e velo", "mat_panels")
    }
    Group("Suggerimenti") {
        SwitchRow("App cercate e più usate", "Prima di scrivere, mostra le app che cerchi e usi di più", s.searchSuggest) { vm.setSetting("searchSuggest", it) }
    }
}

@Composable
private fun PageMinus(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    Group(null) {
        SwitchRow("Pagina −1", "Scorri verso destra dalla prima pagina per aprirla", s.minusOn) { vm.setSetting("minusOn", it) }
    }
    Group("Intestazione") {
        Dependent(s.minusOn) {
            SwitchRow("Saluto", "Buongiorno, Buon pomeriggio, Buonasera con il tuo nome", s.minusGreet) { vm.setSetting("minusGreet", it) }
            Dependent(s.minusGreet) {
                var nameDlg by remember { mutableStateOf(false) }
                val auto = vm.autoName
                ListItem(
                    headlineContent = { Text("Il tuo nome") },
                    supportingContent = {
                        Text(
                            when {
                                s.minusName.isNotBlank() -> s.minusName
                                auto != null -> "$auto (dal telefono)"
                                else -> "Non trovato: tocca per scriverlo"
                            }
                        )
                    },
                    trailingContent = { Chevron() },
                    colors = prefColors(),
                    modifier = Modifier.hl("Il tuo nome").clickable { nameDlg = true }
                )
                if (nameDlg) {
                    var t by remember { mutableStateOf(s.minusName) }
                    AlertDialog(
                        onDismissRequest = { nameDlg = false },
                        title = { Text("Il tuo nome") },
                        text = {
                            Column {
                                androidx.compose.material3.OutlinedTextField(
                                    value = t,
                                    onValueChange = { t = it },
                                    singleLine = true,
                                    placeholder = { Text(auto ?: "Nome") }
                                )
                                Text(
                                    "Lascia vuoto per usare il nome del proprietario del telefono.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                TextButton(onClick = { host.requestContactsName() }) {
                                    Text("Prendi il nome dal profilo del telefono")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { vm.setSetting("minusName", t.trim()); nameDlg = false }) { Text("Salva") }
                        },
                        dismissButton = { TextButton(onClick = { nameDlg = false }) { Text("Annulla") } }
                    )
                }
            }
            SwitchRow("Meteo accanto alla data", null, s.minusWeather) { vm.setSetting("minusWeather", it) }
        }
    }
    Group("Aspetto") { StyleLink(vm, "Materiale, sfocatura e velo", "mat_panels") }
    Group("Widget") {
        Dependent(s.minusOn) {
            ListItem(
                headlineContent = { Text("Aggiungi un widget") },
                supportingContent = {
                    val n = vm.minusWidgets.size
                    Text(if (n == 0) "La pagina è vuota" else if (n == 1) "1 widget nella pagina" else "$n widget nella pagina")
                },
                trailingContent = { Chevron() },
                colors = prefColors(),
                modifier = Modifier.hl("Aggiungi un widget").clickable { vm.minusP = 1f; vm.openMinusPicker() }
            )
            ListItem(
                headlineContent = { Text("Apri la pagina −1") },
                supportingContent = { Text("Tieni premuto un widget per spostarlo, cambiarne la grandezza o toglierlo") },
                trailingContent = { Chevron() },
                colors = prefColors(),
                modifier = Modifier.clickable { vm.overlay = Overlay.NONE; vm.minusP = 1f }
            )
        }
    }
}

@Composable
private fun PageBackup(vm: LauncherViewModel, host: Host) {
    Group("Backup") {
        ListItem(
            headlineContent = { Text("Esporta layout e impostazioni") },
            supportingContent = { Text("Tutto: Home, cartelle, widget e impostazioni") },
            colors = prefColors(),
            modifier = Modifier.clickable { host.exportBackup() }
        )
        ListItem(
            headlineContent = { Text("Ripristina da file") },
            supportingContent = { Text("Rimette Home e impostazioni da un backup completo") },
            colors = prefColors(),
            modifier = Modifier.clickable { host.importBackup() }
        )
    }
    Group("Solo la disposizione") {
        ListItem(
            headlineContent = { Text("Salva la disposizione della Home") },
            supportingContent = { Text("Posizione di icone e cartelle, widget, pagine e pagina \u22121. Le impostazioni non vengono toccate") },
            colors = prefColors(),
            modifier = Modifier.hl("Salva la disposizione della Home").clickable { host.exportLayout() }
        )
        var confirmLayout by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("Ripristina la disposizione") },
            supportingContent = { Text("Da un file della disposizione o da un backup completo. I widget di altre app tornano se sono ancora disponibili") },
            trailingContent = { Chevron() },
            colors = prefColors(),
            modifier = Modifier.hl("Ripristina la disposizione").clickable { confirmLayout = true }
        )
        if (confirmLayout) {
            AlertDialog(
                onDismissRequest = { confirmLayout = false },
                title = { Text("Ripristinare la disposizione?") },
                text = { Text("Icone, cartelle e widget della Home verranno sostituiti con quelli del file. Tema, colori e tutte le altre impostazioni restano come sono.") },
                confirmButton = {
                    TextButton(onClick = { confirmLayout = false; host.importLayout() }) { Text("Scegli il file") }
                },
                dismissButton = { TextButton(onClick = { confirmLayout = false }) { Text("Annulla") } }
            )
        }
    }
    Group("Backup automatico") {
        val s = vm.settings
        SwitchRow(
            "Backup automatico",
            "Salva da solo Home, cartelle, note e impostazioni nella cartella che scegli",
            s.autoBackup
        ) {
            vm.setSetting("autoBackup", it)
            if (it && s.backupDir.isEmpty()) host.pickBackupFolder()
        }
        Dependent(s.autoBackup) {
        ListItem(
            headlineContent = { Text("Cartella del backup") },
            trailingContent = { Chevron() },
            supportingContent = {
                Text(
                    if (s.backupDir.isEmpty()) "Nessuna: tocca per sceglierla"
                    else android.net.Uri.decode(s.backupDir).substringAfterLast(':').ifEmpty { "Cartella scelta" }
                )
            },
            colors = prefColors(),
            modifier = Modifier.clickable { host.pickBackupFolder() }
        )
        ChoiceRow(
            "Ogni quanto",
            listOf("1" to "Ogni giorno", "3" to "Ogni 3 giorni", "7" to "Ogni settimana", "14" to "Ogni 2 settimane", "30" to "Ogni mese"),
            s.backupEvery.toString()
        ) { vm.setSetting("backupEvery", it.toInt()) }
        ChoiceRow(
            "Quanti backup tenere",
            listOf("3" to "Gli ultimi 3", "5" to "Gli ultimi 5", "10" to "Gli ultimi 10"),
            s.backupKeep.toString()
        ) { vm.setSetting("backupKeep", it.toInt()) }
        val last = vm.lastAutoBackup
        ListItem(
            headlineContent = { Text(if (vm.autoBackupBusy) "Backup in corso\u2026" else "Fai ora un backup") },
            supportingContent = {
                Text(
                    vm.autoBackupError?.let { "Ultimo tentativo non riuscito: $it" }
                        ?: if (last > 0) "Ultimo backup: " + java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.ITALY).format(java.util.Date(last))
                        else "Ancora nessun backup automatico"
                )
            },
            colors = prefColors(),
            modifier = Modifier.clickable {
                if (s.backupDir.isEmpty()) host.pickBackupFolder() else vm.runAutoBackup(force = true)
            }
        )
        }
    }
    Group("Manutenzione") {
        var confirmReset by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("Ripristina Home") },
            supportingContent = { Text("Riporta la Home come all'inizio: widget, icone predefinite e dock") },
            colors = prefColors(),
            modifier = Modifier.clickable { confirmReset = true }
        )
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("Ripristinare la Home?") },
                text = { Text("La disposizione attuale di icone, cartelle e widget verrà sostituita. Le impostazioni restano.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmReset = false
                        vm.resetHome()
                        vm.overlay = Overlay.NONE
                    }) { Text("Ripristina") }
                },
                dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annulla") } }
            )
        }
        var confirmAll by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("Ripristina Launcher") },
            supportingContent = { Text("Porta tutto come all'inizio, impostazioni predefinite comprese") },
            colors = prefColors(),
            modifier = Modifier.clickable { confirmAll = true }
        )
        if (confirmAll) {
            AlertDialog(
                onDismissRequest = { confirmAll = false },
                title = { Text("Ripristinare il Launcher?") },
                text = { Text("Home, cartelle, widget, app nascoste e tutte le impostazioni tornano ai valori iniziali. Il Launcher si riavvia.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmAll = false
                        vm.resetLauncher()
                        host.restartLauncher()
                    }) { Text("Ripristina tutto") }
                },
                dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Annulla") } }
            )
        }
        ListItem(
            headlineContent = { Text("Riavvia Launcher") },
            supportingContent = { Text("Riavvio completo dell'app, senza perdere nulla") },
            colors = prefColors(),
            modifier = Modifier.clickable { host.restartLauncher() }
        )
    }
}

@Composable
private fun PageInfo(vm: LauncherViewModel, host: Host) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Group(null) {
        ListItem(
            headlineContent = { Text("MLauncher") },
            supportingContent = { Text("Versione ${vm.currentVersionName}. Launcher in stile HyperOS con colori Material You. Android 12 o successivo.") },
            colors = prefColors()
        )
    }
    Group("Impostazioni") {
        SwitchRow(
            "Barra di ricerca",
            "\u00abCerca nelle impostazioni\u00bb in cima all'elenco",
            vm.settings.settingsSearch
        ) { vm.setSetting("settingsSearch", it) }
    }
    Group("Guida") {
        ListItem(
            headlineContent = { Text("Rivedi il benvenuto") },
            trailingContent = { Chevron() },
            supportingContent = { Text("Le schermate della prima installazione: modalità, stile, permessi, backup") },
            colors = prefColors(),
            modifier = Modifier.hl("Rivedi il benvenuto").clickable {
                vm.overlay = Overlay.NONE
                vm.showWelcome = true
            }
        )
    }
    Group("Aggiornamenti") {
        val up = vm.updateInfo
        ListItem(
            headlineContent = {
                Text(if (up != null) "Nuova versione ${up.version} disponibile" else "Cerca aggiornamenti")
            },
            supportingContent = {
                Text(
                    if (up != null) "Tocca per scaricarla e installarla sopra quella attuale"
                    else "Versione installata ${vm.currentVersionName}"
                )
            },
            colors = prefColors(),
            modifier = Modifier.hl("Cerca aggiornamenti").clickable { AppUpdate.open(ctx) }
        )
        SwitchRow(
            "Controlla all'avvio",
            "Al massimo una volta ogni 6 ore",
            vm.settings.autoUpdateCheck
        ) { vm.setSetting("autoUpdateCheck", it) }
    }
    Group("Realizzazione") {
        Credit("Marco", "Idea, richieste e collaudo")
        Credit("Claude (Anthropic)", "Progettazione e sviluppo del codice")
    }
    Group("Ispirazione") {
        Credit("Xiaomi HyperOS", "Aspetto di Home, dock, widget ed effetto di disinstallazione")
        Credit("Samsung One UI", "Widget impilati con rotazione, dock, gesti")
        Credit("Apple iOS", "Modalità modifica con icone che tremolano, forma delle icone, dock")
        Credit("Lawnchair", "Stile delle impostazioni e menu della Home, elenchi di app per categoria (Flowerpot) usati dalle cartelle intelligenti. Licenza Apache 2.0, github.com/lawnchairlauncher/lawnchair")
        Credit("Google Material Design 3", "Linguaggio grafico e colori dinamici (Material You)")
    }
    Group("Librerie e servizi") {
        Credit("Kotlin", "Linguaggio. JetBrains, licenza Apache 2.0")
        Credit("Jetpack Compose e AndroidX", "Interfaccia e componenti. Google, licenza Apache 2.0")
        Credit("Material 3 per Compose", "Componenti grafici. Google, licenza Apache 2.0")
        Credit("Open-Meteo", "Dati meteo gratuiti. open-meteo.com, licenza CC BY 4.0")
        Credit("Android Open Source Project", "Piattaforma, AppWidget, servizi di accessibilità e notifiche")
        Credit("GitHub Actions", "Compilazione automatica e pubblicazione degli APK")
    }
    Group("Note") {
        Credit("Marchi", "HyperOS, Xiaomi, Samsung, One UI, iOS, Apple, Lawnchair e Google sono marchi dei rispettivi proprietari. MLauncher non è affiliato a nessuno di essi.")
    }
}

@Composable
private fun Credit(title: String, text: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(text) },
        colors = prefColors()
    )
}

// ---------------------------------------------------------------------
//  Anteprime
// ---------------------------------------------------------------------

@Composable
internal fun ShapePreview(key: String, size: Dp) {
    val cs = MaterialTheme.colorScheme
    val shape = expressiveShape(key) ?: when (key) {
        "circle" -> CircleShape
        "square" -> RoundedCornerShape(size * 0.22f)
        "ios" -> IosShape
        else -> RoundedCornerShape(size * 0.32f)
    }
    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(shape).background(cs.primary),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Star, null, tint = cs.onPrimary, modifier = Modifier.size(size * 0.55f)) }
    }
}

@Composable
private fun GridPreview(cols: Int, rows: Int) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.size(if (cols > rows) 52.dp else 34.dp, 34.dp).clip(RoundedCornerShape(6.dp)).background(cs.surfaceContainerHighest).padding(3.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(cols) { Box(Modifier.size(3.dp).clip(CircleShape).background(cs.primary)) }
            }
        }
    }
}

private val previewWallpaper = Brush.linearGradient(
    listOf(Color(0xFF0B1B3A), Color(0xFF1E88C8), Color(0xFF0A2A52), Color(0xFF16A6E0))
)

@Composable
private fun EffectPreview(key: String) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(width = 44.dp, height = 34.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(18.dp, 26.dp).offset(x = (-8).dp).clip(RoundedCornerShape(4.dp))
                .background(cs.primary.copy(alpha = if (key == "fade") 0.35f else 0.9f))
                .graphicsLayer {
                    when (key) {
                        "cube" -> scaleX = 0.7f
                        "rotate" -> rotationZ = -14f
                        "cascade" -> { scaleX = 0.85f; scaleY = 0.85f }
                    }
                }
        )
        Box(
            Modifier.size(18.dp, 26.dp).offset(x = 8.dp).clip(RoundedCornerShape(4.dp))
                .background(cs.tertiary.copy(alpha = if (key == "fade") 0.6f else 0.9f))
                .graphicsLayer {
                    when (key) {
                        "cube" -> scaleX = 0.7f
                        "rotate" -> rotationZ = 14f
                        "cascade" -> { scaleX = 0.85f; scaleY = 0.85f }
                    }
                }
        )
    }
}

@Composable
internal fun AccentPreview(key: String) = com.marco.mltheme.AccentPreview(key)

@Composable
internal fun ThemePreview(key: String) = com.marco.mltheme.ThemePreview(key)

@Composable
internal fun IndicatorPreview(key: String) {
    val cs = MaterialTheme.colorScheme
    val on = cs.primary
    val off = cs.onSurfaceVariant.copy(alpha = 0.4f)
    Row(
        Modifier.width(56.dp).height(34.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (key) {
            "none" -> Text("\u2014", color = off)
            "ios" -> Row(
                Modifier.clip(CircleShape).background(off.copy(alpha = 0.25f)).padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = on, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text("Cerca", color = on, style = MaterialTheme.typography.labelSmall)
            }
            "number" -> Text("2 / 5", color = on, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            "progress" -> Box(Modifier.width(44.dp).height(4.dp).clip(CircleShape).background(off)) {
                Box(Modifier.width(18.dp).height(4.dp).clip(CircleShape).background(on))
            }
            else -> repeat(4) { i ->
                val sel = i == 1
                val w = when (key) {
                    "pill" -> if (sel) 16.dp else 6.dp
                    "lines" -> 10.dp
                    "worm" -> if (sel) 14.dp else 6.dp
                    else -> 6.dp
                }
                val h = if (key == "lines") 3.dp else 6.dp
                Box(
                    Modifier.padding(horizontal = 2.dp).size(w, h).clip(CircleShape)
                        .background(if (sel) on else off)
                )
            }
        }
    }
}

@Composable
private fun BadgePreview(key: String) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainerHighest))
        when (key) {
            "dot" -> Box(Modifier.align(Alignment.TopEnd).size(9.dp).clip(CircleShape).background(cs.error))
            "count" -> Box(
                Modifier.align(Alignment.TopEnd).size(14.dp).clip(CircleShape).background(cs.error),
                contentAlignment = Alignment.Center
            ) { Text("3", color = cs.onError, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

// ---------------------------------------------------------------------
//  Componenti
// ---------------------------------------------------------------------

// Componenti: arrivano dalla libreria MLTheme, condivisa da tutte le MLapps

@Composable
private fun prefColors(): androidx.compose.material3.ListItemColors = com.marco.mltheme.mlPrefColors()

/**
 * Voci che dipendono da un'altra impostazione: con [enabled] false restano visibili ma in grigio
 * e non si possono toccare (il loro valore resta quello di prima).
 */
@Composable
private fun Dependent(enabled: Boolean, content: @Composable () -> Unit) =
    // dentro un'altra voce spenta resta spenta anche questa
    com.marco.mltheme.MLDependent(enabled && LocalPrefEnabled.current, content)

/** Gruppo a elementi separati da un filo di spazio, con gli angoli esterni molto arrotondati. */
@Composable
private fun Group(title: String?, content: @Composable () -> Unit) {
    if (!LocalFluent.current) return com.marco.mltheme.MLGroup(title, content = content)
    // Fluent (come le Impostazioni di Windows 11): angoli piccoli e bordo sottile attorno al gruppo
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Column {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 8.dp)
            )
        }
        Column(
            Modifier.padding(horizontal = 12.dp).clip(shape).border(1.dp, cs.outlineVariant, shape),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) { content() }
    }
}

/** Freccia che indica un sottomenu (stile Android 16). */
@Composable
internal fun Chevron() = com.marco.mltheme.MLChevron()

/** Riga che apre un sottomenu e ha anche un interruttore: freccia | separatore | interruttore. */
@Composable
internal fun NavSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    if (SwitchLook.style != "fluent") return com.marco.mltheme.MLNavSwitch(checked, onChange)
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.marco.mltheme.MLChevron()
        com.marco.mltheme.MLSeparator()
        MLSwitch(checked, onChange)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    if (SwitchLook.style != "fluent") return com.marco.mltheme.MLSwitchRow(title, subtitle, checked, modifier = Modifier.hl(title), onChange = onChange)
    // l'interruttore Fluent è di MLauncher: la riga si ricompone qui, uguale a quella di MLTheme
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        trailingContent = { MLSwitch(checked, onChange) },
        colors = prefColors(),
        modifier = Modifier.hl(title).clickable { onChange(!checked) }
    )
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    preview: (@Composable () -> Unit)? = null,
    onChange: (Float) -> Unit
) = com.marco.mltheme.MLSliderRow(title, valueLabel, value, range, steps, modifier = Modifier.hl(title), preview = preview, onChange = onChange)

@Composable
private fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    current: String,
    preview: (@Composable (String) -> Unit)? = null,
    onSelect: (String) -> Unit
) = com.marco.mltheme.MLChoiceRow(title, options, current, modifier = Modifier.hl(title), preview = preview, onSelect = onSelect)

// =====================================================================
//  Stile: tema, materiale degli elementi, icone, orologio, animazioni
// =====================================================================

private val STYLE_PAGES = setOf("theme", "look", "wallpaper", "material", "iconstyle", "clock", "anim")

/** Pagina della scheda "Stile" (serve per tornare alla scheda giusta). */
internal fun isStylePage(page: String?) = page != null && (page in STYLE_PAGES || page.startsWith("mat_"))

/** Collegamento a una pagina di un'altra scheda (es. dall'Impostazione al suo aspetto in Stile). */
@Composable
private fun StyleLink(vm: LauncherViewModel, title: String, page: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(if (isStylePage(page)) "In Stile" else "In Impostazioni") },
        trailingContent = { Chevron() },
        colors = prefColors(),
        modifier = Modifier.clickable {
            vm.settingsTab = if (isStylePage(page)) 1 else 0
            vm.settingsPage = page
        }
    )
}

private fun matName(st: String) = when (st) {
    "standard" -> "Aspetto originale"
    else -> MATERIALS.firstOrNull { it.first == st }?.second ?: st
}

/** Sfondo vivace per le miniature: così vetro, tonale e Fluent si distinguono a colpo d'occhio. */
private val thumbWall = Brush.linearGradient(
    listOf(Color(0xFFFF8A3D), Color(0xFFE0357A), Color(0xFF3B2BD6), Color(0xFF0A1440))
)

/** Miniatura di un materiale: capsula con tre icone su uno sfondo colorato. */
@Composable
internal fun MaterialPreview(style: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(width = 66.dp, height = 42.dp).clip(RoundedCornerShape(10.dp)).background(thumbWall),
        contentAlignment = Alignment.Center
    ) {
        // una macchia chiara: la trasparenza si vede dove passa sopra
        Box(Modifier.align(Alignment.TopStart).offset(6.dp, 4.dp).size(26.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f)))
        val m = Mat(style, 60, true, 10)
        val look = when {
            style == "standard" -> Modifier.clip(RoundedCornerShape(9.dp)).background(cs.surfaceContainerHigh)
            m.plain -> Modifier
            else -> Modifier.matLook(m, 9.dp)
        }
        Row(
            Modifier.size(52.dp, 20.dp).then(look),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(Color.White)) }
        }
    }
}

/** Anteprima della Home con tutti gli elementi e il loro materiale; [focus] mette in risalto un elemento. */
@Composable
private fun MaterialLivePreview(vm: LauncherViewModel, focus: String?) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    fun a(e: String) = if (focus == null || focus == e) 1f else 0.35f
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(250.dp)
            .clip(RoundedCornerShape(28.dp)).background(previewWallpaper)
    ) {
        vm.wallBitmap?.let {
            androidx.compose.foundation.Image(it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // widget orologio
            val wm = s.matOf("widgets")
            Row(
                Modifier.fillMaxWidth().height(50.dp).alpha(a("widgets"))
                    .then(if (wm.plain) Modifier.clip(RoundedCornerShape(18.dp)).background(cs.primaryContainer) else Modifier.matLook(wm, 18.dp, minOpacity = 40))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fg = if (wm.plain) cs.onPrimaryContainer else matContentColor(wm)
                Text("12:30", color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Text("Lunedì", color = fg, style = MaterialTheme.typography.labelMedium)
            }
            // icone, cartella e menu
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                repeat(2) { Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.9f))) }
                val fm = s.matOf("folder")
                Box(
                    Modifier.size(38.dp).alpha(a("folder"))
                        .then(
                            if (fm.plain) Modifier.clip(folderShapeOf(s.folderShape)).background(cs.surfaceContainerHighest.copy(alpha = 0.6f))
                            else Modifier.matLook(fm, 11.dp, folderShapeOf(s.folderShape), minOpacity = 50)
                        )
                        .padding(7.dp)
                ) {
                    Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                        repeat(2) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                repeat(2) { Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.95f))) }
                            }
                        }
                    }
                }
                // menu rapido
                val mm = s.matOf("menus").let { if (it.style == "liquid") it.copy(style = "glass") else it }
                Column(
                    Modifier.width(92.dp).alpha(a("menus"))
                        .then(
                            if (mm.plain) Modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh)
                            else Modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh.copy(alpha = 0.72f)).matLook(mm, 14.dp, minOpacity = 40)
                        )
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val fg = if (mm.plain) cs.onSurface else matContentColor(mm)
                    Box(Modifier.fillMaxWidth(0.8f).height(5.dp).clip(CircleShape).background(fg.copy(alpha = 0.8f)))
                    Box(Modifier.fillMaxWidth(0.6f).height(5.dp).clip(CircleShape).background(fg.copy(alpha = 0.6f)))
                    Box(Modifier.fillMaxWidth(0.7f).height(5.dp).clip(CircleShape).background(fg.copy(alpha = 0.6f)))
                }
            }
            Spacer(Modifier.weight(1f))
            // indicatore di pagina
            val im = s.matOf("indicator")
            Row(
                Modifier.align(Alignment.CenterHorizontally).alpha(a("indicator"))
                    .then(if (im.plain) Modifier else Modifier.matLook(im, 50.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val fg = if (im.plain) Color.White else matContentColor(im)
                repeat(4) { i -> Box(Modifier.size(if (i == 0) 14.dp else 6.dp, 6.dp).clip(CircleShape).background(fg.copy(alpha = if (i == 0) 1f else 0.5f))) }
            }
            // dock (con la sua forma)
            val dm = s.matOf("dock")
            val bar = s.dockForm == "bar"
            val tiles = s.dockForm == "tiles"
            Row(
                Modifier.fillMaxWidth().height(54.dp).alpha(a("dock"))
                    .padding(horizontal = if (bar) 0.dp else 4.dp)
                    .then(if (tiles) Modifier else Modifier.matLook(dm, (dm.corner * 0.7f).dp, if (bar) RoundedCornerShape(topStart = (dm.corner * 0.7f).dp, topEnd = (dm.corner * 0.7f).dp) else null)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(4) {
                    Box(
                        Modifier.weight(1f).fillMaxHeight().padding(horizontal = 3.dp, vertical = 3.dp)
                            .then(if (tiles) Modifier.matLook(dm, 12.dp) else Modifier),
                        contentAlignment = Alignment.Center
                    ) { Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.92f))) }
                }
            }
        }
    }
}

/** Materiale di un elemento: segue quello generale oppure ne ha uno suo. */
@Composable
private fun MatEditor(vm: LauncherViewModel, el: String, standard: String?, showCorner: Boolean = true) {
    val s = vm.settings
    val follow = s.elFollow(el)
    Group("Materiale") {
        val general = "Tema: " + (LauncherThemes.of(s.launcherTheme)?.title ?: matName(s.matStyle))
        SwitchRow("Segue il tema", general, follow) { vm.setElFollow(el, it) }
        Dependent(!follow) {
            val st = s.elStyle(el)
            ChoiceRow(
                "Materiale di questo elemento",
                (if (standard != null) listOf("standard" to standard) else emptyList()) + MATERIALS,
                st,
                preview = { MaterialPreview(it) }
            ) { vm.setSetting("el_${el}_style", it) }
            Dependent(st != "standard" && st != "none") {
                SwitchRow("Effetto glass", "Riflesso e bordo luminoso come il vetro", s.elGlass(el)) { vm.setSetting("el_${el}_glass", it) }
                val op = s.elOpacity(el)
                SliderRow("Trasparenza", "${100 - op}%", op.toFloat(), 0f..100f, 19) {
                    val v = (it / 5f).roundToInt() * 5
                    if (v != op) vm.setSetting("el_${el}_opacity", v)
                }
                if (showCorner) {
                    val c = s.elCorner(el)
                    SliderRow("Arrotondamento", "$c dp", c.toFloat(), 0f..40f, 0) {
                        val v = it.roundToInt()
                        if (v != c) vm.setSetting("el_${el}_corner", v)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageMaterial(vm: LauncherViewModel) {
    val s = vm.settings
    val cs = MaterialTheme.colorScheme
    MaterialLivePreview(vm, null)
    Group("Elementi della Home") {
        Text(
            "Ognuno segue il tema scelto oppure ha un materiale suo: toccalo per cambiarlo. Il tema si sceglie in Stile \u2192 Tema.",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp)
        )
        MAT_ELEMENTS.forEach { (k, l) ->
            val m = s.matOf(k)
            ListItem(
                headlineContent = { Text(l) },
                supportingContent = { Text(if (s.elFollow(k)) "Segue il tema" else "Proprio: " + matName(s.elStyle(k))) },
                leadingContent = { MaterialPreview(m.style) },
                trailingContent = { Chevron() },
                colors = prefColors(),
                modifier = Modifier.clickable { vm.settingsPage = "mat_$k" }
            )
        }
    }
}

@Composable
private fun PageMatDock(vm: LauncherViewModel) {
    val s = vm.settings
    MaterialLivePreview(vm, "dock")
    MatEditor(vm, "dock", null)
    Group("Forma") {
        ChoiceRow(
            "Forma della dock",
            listOf("float" to "Fluttuante (staccata dai bordi)", "bar" to "Barra a tutta larghezza", "tiles" to "Tessere singole (una per icona)"),
            s.dockForm
        ) { vm.setSetting("dockForm", it) }
    }
}

@Composable
private fun PageMatFolder(vm: LauncherViewModel) {
    val s = vm.settings
    MaterialLivePreview(vm, "folder")
    MatEditor(vm, "folder", "Aspetto originale (sfondo qui sotto)")
    Group("Icona della cartella") {
        ChoiceRow(
            "Forma della cartella",
            FOLDER_SHAPES,
            s.folderShape,
            preview = { FolderShapePreview(it) }
        ) { vm.setSetting("folderShape", it) }
        ChoiceRow(
            "Anteprima della cartella",
            listOf("grid" to "Griglia 3×3", "grid2" to "Griglia 2×2 (icone grandi)", "stack" to "Ventaglio"),
            s.folderPreview,
            preview = { FolderPreview(it, s.folderBg, s.folderShape) }
        ) { vm.setSetting("folderPreview", it) }
        Dependent(s.matOf("folder").plain) {
            ChoiceRow(
                "Sfondo della cartella",
                listOf("tonal" to "Tonale", "primary" to "Colore principale", "dark" to "Scuro trasparente", "glass" to "Vetro chiaro"),
                s.folderBg,
                preview = { FolderPreview(s.folderPreview, it, s.folderShape) }
            ) { vm.setSetting("folderBg", it) }
        }
    }
    Group("Cartella aperta") {
        Text(
            "La cartella aperta ha lo stesso materiale di \u201cMenu, pulsanti e cartelle aperte\u201d.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp)
        )
        SliderRow("Sfocatura dello sfondo", if (s.folderBlur == 0) "Nessuna" else "${s.folderBlur} dp", s.folderBlur.toFloat(), 0f..40f, 0) {
            val v = (it / 2f).roundToInt() * 2
            if (v != s.folderBlur) vm.setSetting("folderBlur", v)
        }
    }
}

@Composable
private fun PageMatIndicator(vm: LauncherViewModel) {
    val s = vm.settings
    MaterialLivePreview(vm, "indicator")
    Group("Indicatore") {
        ChoiceRow(
            "Indicatore di pagina",
            listOf(
                "ios" to "iOS 27 (diventa \"Cerca\")",
                "pill" to "Puntini (attivo allungato)",
                "dots" to "Puntini semplici",
                "lines" to "Linee",
                "worm" to "Puntini con ovale scorrevole",
                "progress" to "Barra di avanzamento",
                "number" to "Numero (2 / 5)",
                "none" to "Nessuno"
            ),
            s.pageIndicator,
            preview = { IndicatorPreview(it) }
        ) { vm.setSetting("pageIndicator", it) }
        Text(
            "Con qualsiasi indicatore: tieni premuto sopra e scorri per cambiare pagina al volo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
    MatEditor(vm, "indicator", "Senza fondo (solo puntini)", showCorner = false)
}

@Composable
private fun PageMatWidgets(vm: LauncherViewModel) {
    MaterialLivePreview(vm, "widgets")
    MatEditor(vm, "widgets", "Aspetto originale dei widget")
    Group("Note") {
        Text(
            "Vale per orologio, meteo e barra di ricerca di MLauncher. Le note e i widget delle altre app mantengono il loro aspetto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp)
        )
        StyleLink(vm, "Stile del widget orologio", "clock")
    }
}

@Composable
private fun PageMatPanels(vm: LauncherViewModel) {
    val s = vm.settings
    MatEditor(vm, "panels", "Tonale (aspetto originale)")
    Group("Dietro la pagina") {
        SliderRow("Sfocatura della Home", if (s.minusBlur == 0) "No" else "${s.minusBlur} dp", s.minusBlur.toFloat(), 0f..40f, 0) {
            val v = it.roundToInt()
            if (v != s.minusBlur) vm.setSetting("minusBlur", v)
        }
        SliderRow("Velo del tema", "${s.minusDim}%", s.minusDim.toFloat(), 0f..70f, 13) {
            val v = (it / 5f).roundToInt() * 5
            if (v != s.minusDim) vm.setSetting("minusDim", v)
        }
        Text(
            "Valgono per la pagina −1, per la ricerca e per il cassetto quando ha un materiale.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun PageMatMenus(vm: LauncherViewModel) {
    MaterialLivePreview(vm, "menus")
    MatEditor(vm, "menus", "Aspetto originale")
    Group("Note") {
        Text(
            "I menu compaiono sopra la Home: con Liquid Glass diventano di vetro, perché da lì non possono piegare lo sfondo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun PageMatDrawer(vm: LauncherViewModel) {
    val s = vm.settings
    MatEditor(vm, "drawer", "Colore pieno (scegli qui sotto)", showCorner = false)
    Group("Colore pieno") {
        Dependent(s.matOf("drawer").plain) {
            ChoiceRow(
                "Colore di sfondo",
                listOf("surface" to "Superficie", "tonal" to "Tonale (colore principale)", "neutral" to "Neutro", "dark" to "Nero"),
                s.drBg
            ) { vm.setSetting("drBg", it) }
            SliderRow("Opacità dello sfondo", "${s.drOpacity}%", s.drOpacity.toFloat(), 0f..100f, 19) {
                val v = (it / 5f).roundToInt() * 5
                if (v != s.drOpacity) vm.setSetting("drOpacity", v)
            }
        }
    }
}

@Composable
private fun PageIconStyle(vm: LauncherViewModel) {
    val s = vm.settings
    Group("Icone") {
        ChoiceRow(
            "Forma",
            ICON_SHAPES,
            s.iconShape,
            preview = { ShapePreview(it, 34.dp) }
        ) { vm.setSetting("iconShape", it) }
        ChoiceRow(
            "Pacchetto icone",
            listOf("" to "Predefinito") + vm.iconPacks.map { it.first to it.second },
            s.iconPack
        ) { vm.setSetting("iconPack", it) }
        SwitchRow(
            "Icone Material You",
            "Tutte le icone con i colori del tema: il glifo monocromatico dove l'app lo ha, le altre ricolorate in due toni",
            s.themedIcons
        ) { vm.setSetting("themedIcons", it) }
        Dependent(s.themedIcons) {
            ChoiceRow("Colore delle icone", THEMED_ICON_COLORS, s.themedIconColor, preview = { IconShapeSwatch(s.iconShape, colorKey = it) }) {
                vm.setSetting("themedIconColor", it)
            }
        }
    }
    Group("Nomi delle app") {
        SwitchRow("Mostra i nomi delle app", null, s.showLabels) { vm.setSetting("showLabels", it) }
        Dependent(s.showLabels) {
            ChoiceRow(
                "Colore dei nomi",
                listOf("auto" to "Automatico (in base allo sfondo)", "white" to "Sempre bianchi", "dark" to "Sempre scuri"),
                s.labelColor
            ) { vm.setSetting("labelColor", it) }
        }
    }
    Group("Grandezza") {
        StyleLink(vm, "Dimensione delle icone e griglia", "home")
    }
}

@Composable
private fun PageClock(vm: LauncherViewModel, host: Host) {
    val s = vm.settings
    Group("Orologio") {
        // anteprima dal vivo del widget orologio
        Box(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val wb = vm.wallBitmap
            Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(24.dp))) {
                if (wb != null) androidx.compose.foundation.Image(
                    wb, contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                ClockWidget(vm, host, {}, Modifier.fillMaxSize().padding(10.dp))
            }
        }
        ChoiceRow("Stile", CLOCK_STYLES, s.clockStyle) { vm.setSetting("clockStyle", it) }
        ChoiceRow(
            "Spessore delle cifre",
            listOf("thin" to "Sottile", "normal" to "Normale", "bold" to "Grassetto"),
            s.clockWeight
        ) { vm.setSetting("clockWeight", it) }
        ChoiceRow(
            "Colore dei minuti",
            listOf("accent" to "Colore d'accento", "same" to "Come le ore"),
            s.clockMinutes
        ) { vm.setSetting("clockMinutes", it) }
        ChoiceRow(
            "Data",
            listOf("right" to "A destra dell'ora", "below" to "Sotto l'ora", "none" to "Nascosta"),
            s.clockDate
        ) { vm.setSetting("clockDate", it) }
        SwitchRow("Secondi", "Mostra anche i secondi accanto ai minuti", s.clockSeconds) { vm.setSetting("clockSeconds", it) }
    }
}
